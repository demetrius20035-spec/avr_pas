package io.github.avrpas.parser;

import io.github.avrpas.ast.*;
import io.github.avrpas.ast.Operators.BinOp;
import io.github.avrpas.ast.Operators.UnOp;
import io.github.avrpas.diag.Diagnostic;
import io.github.avrpas.diag.DiagnosticReporter;
import io.github.avrpas.diag.SourceLocation;
import io.github.avrpas.diag.TranspilerException;
import io.github.avrpas.lexer.Token;
import io.github.avrpas.lexer.TokenType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A recursive-descent parser for the supported PASCAL subset. It produces a
 * {@link Program} AST and reports syntax errors through the shared
 * {@link DiagnosticReporter}. The parser uses panic-mode recovery at statement
 * boundaries so that a single mistake does not abort the whole file.
 */
public final class Parser {

    private final List<Token> tokens;
    private final DiagnosticReporter reporter;
    private int idx;

    public Parser(List<Token> tokens, DiagnosticReporter reporter) {
        this.tokens = tokens;
        this.reporter = reporter;
    }

    public Program parseProgram() {
        SourceLocation loc = peek().location;
        String name = "Main";
        if (match(TokenType.PROGRAM)) {
            name = expect(TokenType.IDENT, "program name").text;
            // optional ( ... ) parameter list (legacy)
            if (match(TokenType.LPAREN)) {
                while (!check(TokenType.RPAREN) && !atEnd()) advance();
                expect(TokenType.RPAREN, "')'");
            }
            expect(TokenType.SEMICOLON, "';' after program header");
        }

        List<ConstDecl> consts = new ArrayList<>();
        List<VarDecl> globals = new ArrayList<>();
        List<ProcDecl> routines = new ArrayList<>();

        parseDeclarations(consts, globals, routines);

        CompoundStmt main = parseCompound();
        expect(TokenType.DOT, "'.' at end of program");
        return new Program(name, consts, globals, routines, main, loc);
    }

    // ----------------------------------------------------------- declarations

    private void parseDeclarations(List<ConstDecl> consts, List<VarDecl> globals,
                                   List<ProcDecl> routines) {
        while (true) {
            if (check(TokenType.CONST)) {
                parseConstSection(consts);
            } else if (check(TokenType.VAR)) {
                parseVarSection(globals);
            } else if (check(TokenType.TYPE)) {
                // type declarations are parsed and skipped (aliases not yet used)
                skipTypeSection();
            } else if (check(TokenType.PROCEDURE) || check(TokenType.FUNCTION)) {
                ProcDecl p = parseRoutine();
                if (p != null) routines.add(p);
            } else if (check(TokenType.USES)) {
                skipUsesClause();
            } else {
                break;
            }
        }
    }

    private void skipUsesClause() {
        advance(); // uses
        while (!check(TokenType.SEMICOLON) && !atEnd()) advance();
        match(TokenType.SEMICOLON);
        reporter.warning(Diagnostic.Kind.UNSUPPORTED_PASCAL_FEATURE,
                "'uses' clause ignored: units are not supported", previous().location);
    }

    private void skipTypeSection() {
        advance(); // type
        while (check(TokenType.IDENT)) {
            while (!check(TokenType.SEMICOLON) && !atEnd()) advance();
            match(TokenType.SEMICOLON);
        }
        reporter.warning(Diagnostic.Kind.UNSUPPORTED_PASCAL_FEATURE,
                "'type' declarations are parsed but not yet supported", previous().location);
    }

    private void parseConstSection(List<ConstDecl> out) {
        advance(); // const
        while (check(TokenType.IDENT)) {
            Token name = advance();
            // optional ': type' typed constant — type is ignored, value drives it
            if (match(TokenType.COLON)) {
                parseTypeRef();
            }
            expect(TokenType.EQ, "'=' in const declaration");
            Expr value = parseExpression();
            expect(TokenType.SEMICOLON, "';' after const declaration");
            out.add(new ConstDecl(name.text, value, name.location));
        }
    }

    private void parseVarSection(List<VarDecl> out) {
        advance(); // var
        while (check(TokenType.IDENT)) {
            List<Token> names = parseIdentList();
            expect(TokenType.COLON, "':' in var declaration");
            TypeRef type = parseTypeRef();
            expect(TokenType.SEMICOLON, "';' after var declaration");
            for (Token n : names) {
                out.add(new VarDecl(n.text, type, n.location));
            }
        }
    }

    private List<Token> parseIdentList() {
        List<Token> names = new ArrayList<>();
        names.add(expect(TokenType.IDENT, "identifier"));
        while (match(TokenType.COMMA)) {
            names.add(expect(TokenType.IDENT, "identifier"));
        }
        return names;
    }

    private TypeRef parseTypeRef() {
        SourceLocation loc = peek().location;
        if (match(TokenType.ARRAY)) {
            expect(TokenType.LBRACKET, "'[' in array type");
            int low = (int) expect(TokenType.INT_LITERAL, "array lower bound").intValue;
            expect(TokenType.DOTDOT, "'..' in array range");
            int high = (int) expect(TokenType.INT_LITERAL, "array upper bound").intValue;
            expect(TokenType.RBRACKET, "']' in array type");
            expect(TokenType.OF, "'of' in array type");
            TypeRef element = parseTypeRef();
            return new TypeRef(low, high, element, loc);
        }
        Token name = expect(TokenType.IDENT, "type name");
        return new TypeRef(name.text, loc);
    }

    private ProcDecl parseRoutine() {
        SourceLocation loc = peek().location;
        boolean isFunction = check(TokenType.FUNCTION);
        advance(); // procedure | function
        Token name = expect(TokenType.IDENT, "routine name");

        List<Param> params = new ArrayList<>();
        if (check(TokenType.LPAREN)) {
            params = parseParams();
        }

        TypeRef returnType = null;
        if (isFunction) {
            expect(TokenType.COLON, "':' before function return type");
            returnType = parseTypeRef();
        }
        expect(TokenType.SEMICOLON, "';' after routine header");

        // optional interrupt binding: 'interrupt VECTOR;'
        String interruptVector = null;
        if (match(TokenType.INTERRUPT)) {
            interruptVector = expect(TokenType.IDENT, "interrupt vector name").text;
            expect(TokenType.SEMICOLON, "';' after interrupt clause");
        }

        // forward declaration: 'procedure foo; forward;' would land here, but
        // 'forward' is not a keyword; treat a missing body gracefully.
        List<ConstDecl> localConsts = new ArrayList<>();
        List<VarDecl> locals = new ArrayList<>();
        while (check(TokenType.CONST) || check(TokenType.VAR)) {
            if (check(TokenType.CONST)) parseConstSection(localConsts);
            else parseVarSection(locals);
        }

        CompoundStmt body = parseCompound();
        expect(TokenType.SEMICOLON, "';' after routine body");
        return new ProcDecl(name.text, params, returnType, localConsts, locals, body,
                interruptVector, loc);
    }

    private List<Param> parseParams() {
        List<Param> params = new ArrayList<>();
        expect(TokenType.LPAREN, "'('");
        if (!check(TokenType.RPAREN)) {
            do {
                boolean byRef = match(TokenType.VAR);
                List<Token> names = parseIdentList();
                expect(TokenType.COLON, "':' in parameter list");
                TypeRef type = parseTypeRef();
                for (Token n : names) {
                    params.add(new Param(n.text, type, byRef, n.location));
                }
            } while (match(TokenType.SEMICOLON));
        }
        expect(TokenType.RPAREN, "')' after parameters");
        return params;
    }

    // ------------------------------------------------------------- statements

    private CompoundStmt parseCompound() {
        SourceLocation loc = peek().location;
        expect(TokenType.BEGIN, "'begin'");
        List<Stmt> stmts = parseStatementList();
        expect(TokenType.END, "'end'");
        return new CompoundStmt(stmts, loc);
    }

    private List<Stmt> parseStatementList() {
        List<Stmt> stmts = new ArrayList<>();
        if (check(TokenType.END) || check(TokenType.UNTIL)) {
            return stmts;
        }
        stmts.add(parseStatement());
        while (match(TokenType.SEMICOLON)) {
            if (check(TokenType.END) || check(TokenType.UNTIL)) break;
            stmts.add(parseStatement());
        }
        return stmts;
    }

    private Stmt parseStatement() {
        try {
            return parseStatementInner();
        } catch (ParseSync sync) {
            // panic-mode recovery: skip to the next statement boundary
            synchronize();
            return new EmptyStmt(peek().location);
        }
    }

    private Stmt parseStatementInner() {
        Token t = peek();
        switch (t.type) {
            case BEGIN:   return parseCompound();
            case IF:      return parseIf();
            case WHILE:   return parseWhile();
            case REPEAT:  return parseRepeat();
            case FOR:     return parseFor();
            case ASM:     return parseAsm();
            case CASE:    return parseUnsupportedCase();
            case SEMICOLON:
            case END:
            case UNTIL:
                return new EmptyStmt(t.location);
            case IDENT:
                return parseAssignOrCall();
            default:
                error(t, "Unexpected token '" + t.text + "' at start of statement");
                throw new ParseSync();
        }
    }

    private Stmt parseUnsupportedCase() {
        SourceLocation loc = peek().location;
        reporter.warning(Diagnostic.Kind.UNSUPPORTED_PASCAL_FEATURE,
                "'case' statement is not supported yet; emitting a no-op stub", loc);
        // skip until matching 'end'
        int depth = 0;
        while (!atEnd()) {
            if (check(TokenType.CASE)) depth++;
            if (check(TokenType.END)) {
                if (depth == 0) { advance(); break; }
                depth--;
            }
            advance();
        }
        return new EmptyStmt(loc);
    }

    private Stmt parseAssignOrCall() {
        Expr lhs = parsePostfix(parsePrimary());
        if (match(TokenType.ASSIGN)) {
            SourceLocation loc = previous().location;
            Expr value = parseExpression();
            return new AssignStmt(lhs, value, loc);
        }
        if (lhs instanceof CallExpr) {
            return new CallStmt((CallExpr) lhs, lhs.loc);
        }
        if (lhs instanceof VarExpr) {
            // a bare identifier statement = parameterless procedure call
            VarExpr ve = (VarExpr) lhs;
            CallExpr call = new CallExpr(ve.name, new ArrayList<>(), ve.loc);
            return new CallStmt(call, ve.loc);
        }
        error(peek(), "Expected ':=' or a procedure call");
        throw new ParseSync();
    }

    private Stmt parseIf() {
        SourceLocation loc = advance().location; // if
        Expr cond = parseExpression();
        expect(TokenType.THEN, "'then'");
        Stmt thenBranch = parseStatement();
        Stmt elseBranch = null;
        if (match(TokenType.ELSE)) {
            elseBranch = parseStatement();
        }
        return new IfStmt(cond, thenBranch, elseBranch, loc);
    }

    private Stmt parseWhile() {
        SourceLocation loc = advance().location; // while
        Expr cond = parseExpression();
        expect(TokenType.DO, "'do'");
        Stmt body = parseStatement();
        return new WhileStmt(cond, body, loc);
    }

    private Stmt parseRepeat() {
        SourceLocation loc = advance().location; // repeat
        List<Stmt> body = parseStatementList();
        expect(TokenType.UNTIL, "'until'");
        Expr cond = parseExpression();
        return new RepeatStmt(body, cond, loc);
    }

    private Stmt parseFor() {
        SourceLocation loc = advance().location; // for
        Token var = expect(TokenType.IDENT, "loop variable");
        expect(TokenType.ASSIGN, "':=' in for statement");
        Expr from = parseExpression();
        boolean downto;
        if (match(TokenType.TO)) {
            downto = false;
        } else if (match(TokenType.DOWNTO)) {
            downto = true;
        } else {
            error(peek(), "Expected 'to' or 'downto'");
            throw new ParseSync();
        }
        Expr to = parseExpression();
        expect(TokenType.DO, "'do'");
        Stmt body = parseStatement();
        return new ForStmt(var.text, from, to, downto, body, loc);
    }

    private Stmt parseAsm() {
        Token asm = advance(); // ASM token carries the raw body
        expect(TokenType.END, "'end' after asm block");
        List<String> lines = new ArrayList<>();
        for (String line : asm.text.split("\n", -1)) {
            lines.add(line);
        }
        // drop a leading/trailing empty line for tidiness
        while (!lines.isEmpty() && lines.get(0).trim().isEmpty()) lines.remove(0);
        while (!lines.isEmpty() && lines.get(lines.size() - 1).trim().isEmpty()) lines.remove(lines.size() - 1);
        return new AsmStmt(lines, asm.location);
    }

    // ------------------------------------------------------------ expressions

    private Expr parseExpression() {
        return parseRelational();
    }

    private Expr parseRelational() {
        Expr left = parseAdditive();
        while (checkAny(TokenType.EQ, TokenType.NEQ, TokenType.LT, TokenType.LTE,
                TokenType.GT, TokenType.GTE)) {
            Token op = advance();
            Expr right = parseAdditive();
            left = new BinaryExpr(relOp(op.type), left, right, op.location);
        }
        return left;
    }

    private Expr parseAdditive() {
        Expr left = parseMultiplicative();
        while (checkAny(TokenType.PLUS, TokenType.MINUS, TokenType.OR, TokenType.XOR)) {
            Token op = advance();
            Expr right = parseMultiplicative();
            left = new BinaryExpr(addOp(op.type), left, right, op.location);
        }
        return left;
    }

    private Expr parseMultiplicative() {
        Expr left = parseUnary();
        while (checkAny(TokenType.STAR, TokenType.SLASH, TokenType.DIV, TokenType.MOD,
                TokenType.AND, TokenType.SHL, TokenType.SHR)) {
            Token op = advance();
            Expr right = parseUnary();
            left = new BinaryExpr(mulOp(op.type), left, right, op.location);
        }
        return left;
    }

    private Expr parseUnary() {
        if (check(TokenType.MINUS)) {
            Token op = advance();
            return new UnaryExpr(UnOp.NEG, parseUnary(), op.location);
        }
        if (check(TokenType.PLUS)) {
            Token op = advance();
            return new UnaryExpr(UnOp.POS, parseUnary(), op.location);
        }
        if (check(TokenType.NOT)) {
            Token op = advance();
            return new UnaryExpr(UnOp.NOT, parseUnary(), op.location);
        }
        return parsePostfix(parsePrimary());
    }

    private Expr parsePostfix(Expr base) {
        while (true) {
            if (check(TokenType.LBRACKET)) {
                Token br = advance();
                Expr index = parseExpression();
                expect(TokenType.RBRACKET, "']'");
                base = new IndexExpr(base, index, br.location);
            } else {
                return base;
            }
        }
    }

    private Expr parsePrimary() {
        Token t = peek();
        switch (t.type) {
            case INT_LITERAL:
                advance();
                return new IntLiteral(t.intValue, t.location);
            case REAL_LITERAL:
                advance();
                reporter.warning(Diagnostic.Kind.UNSUPPORTED_PASCAL_FEATURE,
                        "Real literals are not supported; treated as 0", t.location);
                IntLiteral zero = new IntLiteral(0, t.location);
                return zero;
            case CHAR_LITERAL:
                advance();
                return new CharLiteral((char) t.intValue, t.location);
            case STRING_LITERAL:
                advance();
                return new StringLiteral(t.text, t.location);
            case TRUE:
                advance();
                return new BoolLiteral(true, t.location);
            case FALSE:
                advance();
                return new BoolLiteral(false, t.location);
            case LPAREN: {
                advance();
                Expr e = parseExpression();
                expect(TokenType.RPAREN, "')'");
                return e;
            }
            case IDENT: {
                advance();
                if (check(TokenType.LPAREN)) {
                    List<Expr> args = parseArgs();
                    return new CallExpr(t.text, args, t.location);
                }
                return new VarExpr(t.text, t.location);
            }
            default:
                error(t, "Unexpected token '" + t.text + "' in expression");
                throw new ParseSync();
        }
    }

    private List<Expr> parseArgs() {
        List<Expr> args = new ArrayList<>();
        expect(TokenType.LPAREN, "'('");
        if (!check(TokenType.RPAREN)) {
            args.add(parseExpression());
            while (match(TokenType.COMMA)) {
                args.add(parseExpression());
            }
        }
        expect(TokenType.RPAREN, "')'");
        return args;
    }

    private BinOp relOp(TokenType t) {
        switch (t) {
            case EQ: return BinOp.EQ;
            case NEQ: return BinOp.NEQ;
            case LT: return BinOp.LT;
            case LTE: return BinOp.LTE;
            case GT: return BinOp.GT;
            case GTE: return BinOp.GTE;
            default: throw new IllegalStateException();
        }
    }

    private BinOp addOp(TokenType t) {
        switch (t) {
            case PLUS: return BinOp.ADD;
            case MINUS: return BinOp.SUB;
            case OR: return BinOp.OR;
            case XOR: return BinOp.XOR;
            default: throw new IllegalStateException();
        }
    }

    private BinOp mulOp(TokenType t) {
        switch (t) {
            case STAR: return BinOp.MUL;
            case SLASH: return BinOp.FDIV;
            case DIV: return BinOp.DIV;
            case MOD: return BinOp.MOD;
            case AND: return BinOp.AND;
            case SHL: return BinOp.SHL;
            case SHR: return BinOp.SHR;
            default: throw new IllegalStateException();
        }
    }

    // ---------------------------------------------------------------- helpers

    private static final class ParseSync extends RuntimeException {
        ParseSync() { super(null, null, false, false); }
    }

    private void synchronize() {
        while (!atEnd()) {
            if (previous().type == TokenType.SEMICOLON) return;
            switch (peek().type) {
                case BEGIN: case END: case IF: case WHILE: case REPEAT:
                case FOR: case PROCEDURE: case FUNCTION: case VAR: case CONST:
                    return;
                default:
                    advance();
            }
        }
    }

    private boolean atEnd() { return peek().type == TokenType.EOF; }
    private Token peek() { return tokens.get(idx); }
    private Token previous() { return tokens.get(idx - 1); }

    private Token advance() {
        if (!atEnd()) idx++;
        return previous();
    }

    private boolean check(TokenType type) { return peek().type == type; }

    private boolean checkAny(TokenType... types) {
        for (TokenType t : types) if (check(t)) return true;
        return false;
    }

    private boolean match(TokenType type) {
        if (check(type)) { advance(); return true; }
        return false;
    }

    private Token expect(TokenType type, String what) {
        if (check(type)) return advance();
        error(peek(), "Expected " + what + " but found '" + peek().text + "'");
        // fabricate a token so callers can proceed; recovery happens upstream
        throw new ParseSync();
    }

    private void error(Token t, String message) {
        reporter.error(Diagnostic.Kind.SYNTAX, message, t.location);
    }

    /** Convenience for callers that want a hard stop on catastrophic input. */
    public static void requireNoFatal(DiagnosticReporter r) {
        if (r.errorCount() > 200) {
            throw new TranspilerException("Too many syntax errors; aborting.");
        }
    }
}
