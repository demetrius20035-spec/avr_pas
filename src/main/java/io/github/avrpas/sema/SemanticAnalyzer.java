package io.github.avrpas.sema;

import io.github.avrpas.ast.*;
import io.github.avrpas.ast.Operators.BinOp;
import io.github.avrpas.ast.Operators.UnOp;
import io.github.avrpas.chip.Chip;
import io.github.avrpas.config.Config;
import io.github.avrpas.diag.Diagnostic;
import io.github.avrpas.diag.DiagnosticReporter;
import io.github.avrpas.diag.SourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Walks the AST to resolve names, check types, lay out static storage and
 * validate that every used feature is available on the target chip. Diagnostics
 * are accumulated; analysis continues past recoverable errors to surface as
 * many problems as possible in a single run.
 *
 * <p>Storage allocation is static (see {@link Symbol}). The analyzer assigns a
 * unique assembly label to every variable/parameter/local and tallies SRAM
 * usage so the "out of memory" requirement can be enforced.</p>
 */
public final class SemanticAnalyzer implements AstVisitor<Type> {

    private final Program program;
    private final Chip chip;
    private final Config config;
    private final DiagnosticReporter reporter;

    private final SymbolTable globals;
    private SymbolTable scope;
    private ProcDecl currentRoutine;

    /** Every storage symbol, in declaration order, for the code generator. */
    public final List<Symbol> storage = new ArrayList<>();
    private int staticBytes;

    public SemanticAnalyzer(Program program, Chip chip, Config config, DiagnosticReporter reporter) {
        this.program = program;
        this.chip = chip;
        this.config = config;
        this.reporter = reporter;
        this.globals = new SymbolTable(null);
        this.scope = globals;
    }

    public int staticBytes() { return staticBytes; }

    public void analyze() {
        injectPredefined();
        program.accept(this);
        checkMemory();
    }

    // -------------------------------------------------------- predefined env

    private void injectPredefined() {
        // memory-mapped special-function registers as predefined byte variables
        for (Map.Entry<String, Integer> e : chip.sfrMap().entrySet()) {
            Symbol s = new Symbol(e.getKey(), Symbol.Kind.VARIABLE, Type.BYTE);
            s.isSfr = true;
            s.sfrAddress = e.getValue();
            s.label = e.getKey();
            globals.define(s);
        }
        // F_CPU and target identification constants for use in expressions
        defineConst("F_CPU", config.fCpuHz);
        defineConst("MAXINT", 32767);
        // boolean aliases
        defineConst("True", 1);
        defineConst("False", 0);
    }

    private void defineConst(String name, long value) {
        Symbol s = Symbol.constant(name, typeForConst(value), value);
        globals.define(s);
    }

    private Type typeForConst(long v) {
        // Non-negative literals prefer the smallest unsigned type so that mixing
        // them with unsigned variables (word/byte) keeps arithmetic unsigned.
        if (v >= 0 && v <= 255) return Type.BYTE;
        if (v >= 0 && v <= 65535) return Type.WORD;
        if (v >= -128 && v <= 127) return Type.SHORTINT;
        if (v >= -32768 && v <= 32767) return Type.INTEGER;
        return Type.INTEGER; // larger values are kept as longs but typed as integer
    }

    // ----------------------------------------------------------- declarations

    @Override
    public Type visitProgram(Program n) {
        // 1) global consts
        for (ConstDecl c : n.consts) c.accept(this);
        // 2) global variables
        for (VarDecl v : n.globals) v.accept(this);
        // 3) routine signatures (so they can call each other / be forward-referenced)
        for (ProcDecl p : n.routines) declareRoutineSignature(p);
        // 4) routine bodies
        for (ProcDecl p : n.routines) p.accept(this);
        // 5) main body
        n.main.accept(this);
        return Type.VOID;
    }

    @Override
    public Type visitConstDecl(ConstDecl n) {
        Type t = n.value.accept(this);
        if (!n.value.isConstant()) {
            reporter.error(Diagnostic.Kind.SEMANTIC,
                    "Constant '" + n.name + "' initialiser is not a constant expression", n.loc);
        }
        long value = n.value.isConstant() ? n.value.constValue : 0;
        Symbol s = Symbol.constant(n.name, t.kind == Type.Kind.ERROR ? typeForConst(value) : t, value);
        n.symbol = s;
        if (!scope.define(s)) {
            reporter.error(Diagnostic.Kind.SEMANTIC, "Duplicate identifier '" + n.name + "'", n.loc);
        }
        return Type.VOID;
    }

    @Override
    public Type visitVarDecl(VarDecl n) {
        Type t = resolveType(n.typeRef);
        n.type = t;
        Symbol.Kind kind = (scope == globals) ? Symbol.Kind.VARIABLE : Symbol.Kind.LOCAL;
        Symbol s = new Symbol(n.name, kind, t);
        s.label = allocLabel(n.name);
        n.symbol = s;
        if (!scope.define(s)) {
            reporter.error(Diagnostic.Kind.SEMANTIC, "Duplicate identifier '" + n.name + "'", n.loc);
        } else {
            allocStorage(s, t.sizeBytes);
        }
        return Type.VOID;
    }

    private void declareRoutineSignature(ProcDecl p) {
        Symbol s = new Symbol(p.name, p.isFunction() ? Symbol.Kind.FUNCTION : Symbol.Kind.PROCEDURE,
                p.isFunction() ? resolveType(p.returnTypeRef) : Type.VOID);
        s.decl = p;
        s.label = "fn_" + sanitize(p.name);
        p.symbol = s;
        p.returnType = s.type;
        for (Param par : p.params) {
            par.type = resolveType(par.typeRef);
        }
        if (Builtins.isBuiltin(p.name)) {
            reporter.warning(Diagnostic.Kind.SEMANTIC,
                    "Routine '" + p.name + "' shadows a built-in of the same name", p.loc);
        }
        if (!globals.define(s)) {
            reporter.error(Diagnostic.Kind.SEMANTIC, "Duplicate routine '" + p.name + "'", p.loc);
        }
    }

    @Override
    public Type visitProcDecl(ProcDecl n) {
        SymbolTable routineScope = new SymbolTable(globals);
        SymbolTable saved = scope;
        ProcDecl savedRoutine = currentRoutine;
        scope = routineScope;
        currentRoutine = n;

        // parameters
        for (Param par : n.params) {
            Symbol s = new Symbol(par.name, Symbol.Kind.PARAM, par.type);
            s.byRef = par.byRef;
            s.label = "p_" + sanitize(n.name) + "_" + sanitize(par.name);
            par.symbol = s;
            if (!scope.define(s)) {
                reporter.error(Diagnostic.Kind.SEMANTIC, "Duplicate parameter '" + par.name + "'", par.loc);
            } else {
                // by-ref parameters store a 2-byte pointer
                allocStorage(s, par.byRef ? 2 : par.type.sizeBytes);
            }
        }
        // function result slot
        if (n.isFunction()) {
            Symbol res = new Symbol(n.name, Symbol.Kind.LOCAL, n.returnType);
            res.label = "res_" + sanitize(n.name);
            n.resultSymbol = res;
            allocStorage(res, n.returnType.sizeBytes);
        }
        // local consts and vars
        for (ConstDecl c : n.localConsts) c.accept(this);
        for (VarDecl v : n.locals) v.accept(this);

        if (n.isInterrupt() && !chip.hasVector(n.interruptVector)) {
            reporter.error(Diagnostic.Kind.UNSUPPORTED_CHIP_FEATURE,
                    "Interrupt vector '" + n.interruptVector + "' does not exist on " + chip.name(), n.loc);
        }
        if (n.isInterrupt() && !n.params.isEmpty()) {
            reporter.error(Diagnostic.Kind.SEMANTIC,
                    "Interrupt routine '" + n.name + "' must not take parameters", n.loc);
        }

        n.body.accept(this);

        scope = saved;
        currentRoutine = savedRoutine;
        return Type.VOID;
    }

    // ------------------------------------------------------------- statements

    @Override
    public Type visitCompound(CompoundStmt n) {
        for (Stmt s : n.statements) s.accept(this);
        return Type.VOID;
    }

    @Override
    public Type visitAssign(AssignStmt n) {
        Type rhs = n.value.accept(this);
        Type lhs = n.target.accept(this);
        if (!isLValue(n.target)) {
            reporter.error(Diagnostic.Kind.SEMANTIC, "Left side of ':=' is not assignable", n.loc);
            return Type.VOID;
        }
        checkAssignable(lhs, rhs, n.loc);
        return Type.VOID;
    }

    @Override
    public Type visitIf(IfStmt n) {
        Type c = n.condition.accept(this);
        requireBooleanish(c, n.condition.loc, "if");
        n.thenBranch.accept(this);
        if (n.elseBranch != null) n.elseBranch.accept(this);
        return Type.VOID;
    }

    @Override
    public Type visitWhile(WhileStmt n) {
        requireBooleanish(n.condition.accept(this), n.condition.loc, "while");
        n.body.accept(this);
        return Type.VOID;
    }

    @Override
    public Type visitRepeat(RepeatStmt n) {
        for (Stmt s : n.body) s.accept(this);
        requireBooleanish(n.condition.accept(this), n.condition.loc, "until");
        return Type.VOID;
    }

    @Override
    public Type visitFor(ForStmt n) {
        Symbol s = scope.resolve(n.varName);
        if (s == null) {
            reporter.error(Diagnostic.Kind.SEMANTIC, "Undeclared loop variable '" + n.varName + "'", n.loc);
        } else if (!s.isStorage() || !s.type.isOrdinal()) {
            reporter.error(Diagnostic.Kind.SEMANTIC, "Loop variable '" + n.varName + "' must be an ordinal variable", n.loc);
        }
        n.symbol = s;
        requireInteger(n.from.accept(this), n.from.loc);
        requireInteger(n.to.accept(this), n.to.loc);
        n.body.accept(this);
        return Type.VOID;
    }

    @Override
    public Type visitCallStmt(CallStmt n) {
        n.call.accept(this);
        return Type.VOID;
    }

    @Override
    public Type visitAsm(AsmStmt n) {
        return Type.VOID; // verbatim; nothing to check
    }

    @Override
    public Type visitEmpty(EmptyStmt n) {
        return Type.VOID;
    }

    // ------------------------------------------------------------ expressions

    @Override
    public Type visitBinary(BinaryExpr n) {
        Type l = n.left.accept(this);
        Type r = n.right.accept(this);
        Type result;
        if (n.op.isComparison()) {
            result = Type.BOOLEAN;
        } else if (n.op == BinOp.FDIV) {
            reporter.warning(Diagnostic.Kind.UNSUPPORTED_PASCAL_FEATURE,
                    "Real division '/' is not supported; using integer div", n.loc);
            result = Type.promote(l, r);
        } else {
            if (!l.isInteger() || !r.isInteger()) {
                reporter.error(Diagnostic.Kind.SEMANTIC,
                        "Operator '" + n.op.text + "' requires integer operands", n.loc);
                result = Type.ERROR;
            } else {
                result = Type.promote(l, r);
            }
        }
        foldBinary(n);
        n.type = result;
        return result;
    }

    private void foldBinary(BinaryExpr n) {
        if (!n.left.isConstant() || !n.right.isConstant()) return;
        long a = n.left.constValue, b = n.right.constValue;
        Long v = null;
        switch (n.op) {
            case ADD: v = a + b; break;
            case SUB: v = a - b; break;
            case MUL: v = a * b; break;
            case DIV: case FDIV: v = (b == 0) ? 0 : a / b; break;
            case MOD: v = (b == 0) ? 0 : a % b; break;
            case AND: v = a & b; break;
            case OR:  v = a | b; break;
            case XOR: v = a ^ b; break;
            case SHL: v = a << b; break;
            case SHR: v = a >>> b; break;
            case EQ:  v = (a == b) ? 1L : 0L; break;
            case NEQ: v = (a != b) ? 1L : 0L; break;
            case LT:  v = (a < b) ? 1L : 0L; break;
            case LTE: v = (a <= b) ? 1L : 0L; break;
            case GT:  v = (a > b) ? 1L : 0L; break;
            case GTE: v = (a >= b) ? 1L : 0L; break;
        }
        if (n.op == BinOp.DIV && n.right.constValue == 0) {
            reporter.warning(Diagnostic.Kind.SEMANTIC, "Division by constant zero", n.loc);
        }
        n.constValue = v;
    }

    @Override
    public Type visitUnary(UnaryExpr n) {
        Type t = n.operand.accept(this);
        if (n.op == UnOp.NOT) {
            n.type = t;
        } else {
            if (!t.isInteger()) {
                reporter.error(Diagnostic.Kind.SEMANTIC, "Unary '" + n.op.text + "' requires a numeric operand", n.loc);
            }
            n.type = t.signed ? t : (t.sizeBytes <= 1 ? Type.SHORTINT : Type.INTEGER);
        }
        if (n.operand.isConstant()) {
            long o = n.operand.constValue;
            switch (n.op) {
                case NEG: n.constValue = -o; break;
                case POS: n.constValue = o; break;
                case NOT: n.constValue = ~o; break;
            }
        }
        return n.type;
    }

    @Override public Type visitIntLiteral(IntLiteral n)  { n.type = typeForConst(n.value); return n.type; }
    @Override public Type visitBoolLiteral(BoolLiteral n) { n.type = Type.BOOLEAN; return n.type; }
    @Override public Type visitCharLiteral(CharLiteral n) { n.type = Type.CHAR; return n.type; }

    @Override
    public Type visitStringLiteral(StringLiteral n) {
        n.type = Type.array(Type.CHAR, n.value.length());
        return n.type;
    }

    @Override
    public Type visitVarExpr(VarExpr n) {
        // a function's own name inside its body denotes the result variable
        if (currentRoutine != null && currentRoutine.isFunction()
                && n.name.equalsIgnoreCase(currentRoutine.name)) {
            n.symbol = currentRoutine.resultSymbol;
            n.type = currentRoutine.returnType;
            return n.type;
        }
        Symbol s = scope.resolve(n.name);
        if (s == null) {
            // a bare zero-argument built-in function (e.g. UartReceive)
            if (Builtins.isBuiltin(n.name)) {
                Builtins.Descriptor d = Builtins.get(n.name);
                if (d.params.isEmpty()) {
                    if (d.requiredPeripheral != null && !chip.hasPeripheral(d.requiredPeripheral)) {
                        reporter.error(Diagnostic.Kind.UNSUPPORTED_CHIP_FEATURE,
                                "Built-in '" + d.name + "' needs the " + d.requiredPeripheral
                                        + " peripheral, which " + chip.name() + " does not provide", n.loc);
                    }
                    n.builtin = d.name;
                    n.type = d.returnType;
                    return n.type;
                }
                reporter.error(Diagnostic.Kind.SEMANTIC,
                        "Built-in '" + n.name + "' requires arguments", n.loc);
                n.type = Type.ERROR;
                return n.type;
            }
            reporter.error(Diagnostic.Kind.SEMANTIC, "Undeclared identifier '" + n.name + "'", n.loc);
            n.type = Type.ERROR;
            return n.type;
        }
        n.symbol = s;
        if (s.kind == Symbol.Kind.CONST) {
            n.constValue = s.constValue;
        }
        if (s.kind == Symbol.Kind.FUNCTION) {
            // zero-argument function call by bare name
            if (!s.decl.params.isEmpty()) {
                reporter.error(Diagnostic.Kind.SEMANTIC,
                        "Function '" + n.name + "' requires arguments", n.loc);
            }
        }
        n.type = s.type;
        return n.type;
    }

    @Override
    public Type visitCallExpr(CallExpr n) {
        // evaluate argument types first
        List<Type> argTypes = new ArrayList<>();
        for (Expr a : n.args) argTypes.add(a.accept(this));

        Symbol s = scope.resolve(n.name);
        if (s != null && s.isCallable()) {
            n.symbol = s;
            if (s.kind == Symbol.Kind.BUILTIN) {
                return checkBuiltin(n, Builtins.get(n.name), argTypes);
            }
            return checkUserCall(n, s, argTypes);
        }
        if (Builtins.isBuiltin(n.name)) {
            n.builtin = Builtins.get(n.name).name;
            return checkBuiltin(n, Builtins.get(n.name), argTypes);
        }
        reporter.error(Diagnostic.Kind.SEMANTIC, "Call to undeclared routine '" + n.name + "'", n.loc);
        n.type = Type.ERROR;
        return n.type;
    }

    private Type checkUserCall(CallExpr n, Symbol s, List<Type> argTypes) {
        ProcDecl p = s.decl;
        if (p.params.size() != n.args.size()) {
            reporter.error(Diagnostic.Kind.SEMANTIC,
                    "Routine '" + n.name + "' expects " + p.params.size()
                            + " argument(s) but got " + n.args.size(), n.loc);
        } else {
            for (int i = 0; i < p.params.size(); i++) {
                Param par = p.params.get(i);
                if (par.byRef && !isLValue(n.args.get(i))) {
                    reporter.error(Diagnostic.Kind.SEMANTIC,
                            "Argument " + (i + 1) + " of '" + n.name + "' must be a variable (var parameter)", n.loc);
                }
                checkAssignable(par.type, argTypes.get(i), n.args.get(i).loc);
            }
        }
        n.type = p.returnType;
        return n.type;
    }

    private Type checkBuiltin(CallExpr n, Builtins.Descriptor d, List<Type> argTypes) {
        n.builtin = d.name;
        if (d.requiredPeripheral != null && !chip.hasPeripheral(d.requiredPeripheral)) {
            reporter.error(Diagnostic.Kind.UNSUPPORTED_CHIP_FEATURE,
                    "Built-in '" + d.name + "' needs the " + d.requiredPeripheral
                            + " peripheral, which " + chip.name() + " does not provide", n.loc);
        }
        if (d.params.size() != n.args.size()) {
            reporter.error(Diagnostic.Kind.SEMANTIC,
                    "Built-in '" + d.name + "' expects " + d.params.size()
                            + " argument(s) but got " + n.args.size(), n.loc);
        } else if (d.firstArgIsSfr) {
            Expr first = n.args.get(0);
            if (!(first instanceof VarExpr) || ((VarExpr) first).symbol == null
                    || !((VarExpr) first).symbol.isSfr) {
                reporter.error(Diagnostic.Kind.SEMANTIC,
                        "First argument of '" + d.name + "' must be a hardware register (SFR)", n.loc);
            }
        }
        n.type = d.returnType;
        return n.type;
    }

    @Override
    public Type visitIndexExpr(IndexExpr n) {
        Type base = n.base.accept(this);
        requireInteger(n.index.accept(this), n.index.loc);
        if (base.kind != Type.Kind.ARRAY) {
            reporter.error(Diagnostic.Kind.SEMANTIC, "Indexed value is not an array", n.loc);
            n.type = Type.ERROR;
        } else {
            n.type = base.element;
        }
        return n.type;
    }

    // ---------------------------------------------------------------- helpers

    private Type resolveType(TypeRef ref) {
        if (ref == null) return Type.VOID;
        if (ref.isArray) {
            Type elem = resolveType(ref.elementType);
            int len = ref.arrayHigh - ref.arrayLow + 1;
            if (len <= 0) {
                reporter.error(Diagnostic.Kind.SEMANTIC, "Array has non-positive length", ref.loc);
                len = 1;
            }
            ref.resolved = Type.array(elem, len);
            return ref.resolved;
        }
        Type t = byName(ref.name);
        if (t == null) {
            reporter.error(Diagnostic.Kind.SEMANTIC, "Unknown type '" + ref.name + "'", ref.loc);
            t = Type.ERROR;
        }
        ref.resolved = t;
        return t;
    }

    private Type byName(String name) {
        switch (name.toLowerCase()) {
            case "byte": return Type.BYTE;
            case "shortint": return Type.SHORTINT;
            case "word": case "cardinal": return Type.WORD;
            case "integer": case "smallint": return Type.INTEGER;
            case "longint": case "longword": case "dword":
                reporter.warning(Diagnostic.Kind.UNSUPPORTED_PASCAL_FEATURE,
                        "32-bit type '" + name + "' is narrowed to 16-bit", SourceLocation.UNKNOWN);
                return Type.INTEGER;
            case "boolean": return Type.BOOLEAN;
            case "char": return Type.CHAR;
            case "real": case "single": case "double":
                return Type.REAL;
            default: return null;
        }
    }

    private boolean isLValue(Expr e) {
        if (e instanceof IndexExpr) return true;
        if (e instanceof VarExpr) {
            Symbol s = ((VarExpr) e).symbol;
            return s != null && (s.isStorage());
        }
        return false;
    }

    private void checkAssignable(Type target, Type value, SourceLocation loc) {
        if (target.kind == Type.Kind.ERROR || value.kind == Type.Kind.ERROR) return;
        if (target.kind == Type.Kind.REAL || value.kind == Type.Kind.REAL) {
            reporter.warning(Diagnostic.Kind.UNSUPPORTED_PASCAL_FEATURE,
                    "Real values are not supported in code generation; result will be 0", loc);
            return;
        }
        if (target.isInteger() && value.isInteger()) {
            if (value.sizeBytes > target.sizeBytes) {
                reporter.warning(Diagnostic.Kind.SEMANTIC,
                        "Assigning " + value.pascalName() + " to " + target.pascalName()
                                + " may lose data (narrowing)", loc);
            }
            return;
        }
        if (target.kind == value.kind) return;
        reporter.error(Diagnostic.Kind.SEMANTIC,
                "Cannot assign " + value.pascalName() + " to " + target.pascalName(), loc);
    }

    private void requireBooleanish(Type t, SourceLocation loc, String ctx) {
        if (t.kind == Type.Kind.ERROR) return;
        if (!t.isInteger()) {
            reporter.error(Diagnostic.Kind.SEMANTIC, "Condition of '" + ctx + "' must be boolean", loc);
        }
    }

    private void requireInteger(Type t, SourceLocation loc) {
        if (t.kind != Type.Kind.ERROR && !t.isInteger()) {
            reporter.error(Diagnostic.Kind.SEMANTIC, "Expected an integer expression", loc);
        }
    }

    private int labelCounter;

    private String allocLabel(String name) {
        String base = sanitize(name);
        if (currentRoutine != null) {
            return "v_" + sanitize(currentRoutine.name) + "_" + base;
        }
        return "g_" + base;
    }

    private void allocStorage(Symbol s, int bytes) {
        s.kind = s.kind; // keep
        storage.add(s);
        staticBytes += Math.max(bytes, 0);
    }

    private void checkMemory() {
        if (chip.sramBytes() == 0 && staticBytes > 0) {
            reporter.error(Diagnostic.Kind.MEMORY,
                    chip.name() + " has no SRAM data space; " + staticBytes
                            + " byte(s) of variables cannot be allocated", SourceLocation.UNKNOWN);
            return;
        }
        int reserveForStack = 32;
        int available = chip.sramBytes() - reserveForStack;
        if (staticBytes > available) {
            reporter.error(Diagnostic.Kind.MEMORY,
                    "Static data (" + staticBytes + " B) exceeds available SRAM on "
                            + chip.name() + " (" + chip.sramBytes() + " B, ~" + reserveForStack
                            + " B reserved for stack)", SourceLocation.UNKNOWN);
        } else if (staticBytes > available * 3 / 4) {
            reporter.warning(Diagnostic.Kind.MEMORY,
                    "Static data uses " + staticBytes + " of " + chip.sramBytes()
                            + " B SRAM; little headroom left for the stack", SourceLocation.UNKNOWN);
        }
    }

    private static String sanitize(String name) {
        StringBuilder sb = new StringBuilder();
        for (char c : name.toCharArray()) {
            sb.append(Character.isLetterOrDigit(c) ? c : '_');
        }
        return sb.toString();
    }
}
