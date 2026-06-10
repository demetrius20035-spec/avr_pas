package io.github.avrpas.sema;

import io.github.avrpas.ast.ProcDecl;

/**
 * A named entity in a scope: variable, constant, parameter, loop variable or
 * routine. Storage information needed by the code generator is attached here.
 *
 * <p>The compiler uses <em>static</em> storage allocation: every variable,
 * parameter and local is given a fixed label in SRAM. This means routines are
 * not re-entrant (no recursion), which is the conventional trade-off for tiny
 * AVR targets and keeps generated code small and predictable.</p>
 */
public final class Symbol {

    public enum Kind { VARIABLE, PARAM, LOCAL, CONST, FUNCTION, PROCEDURE, BUILTIN, LOOPVAR }

    public final String name;
    public Kind kind;
    public Type type;

    /** Assembly label backing this symbol's storage (variables/params/locals). */
    public String label;

    /** Compile-time value for constants. */
    public long constValue;

    /** For parameters passed by reference ('var'). */
    public boolean byRef;

    /** Backing declaration for routines. */
    public ProcDecl decl;

    /** Identifier of a built-in (peripheral/runtime) routine. */
    public String builtin;

    /** True when this variable is a memory-mapped special-function register. */
    public boolean isSfr;
    /** Data-space address of the SFR (valid when {@link #isSfr}). */
    public int sfrAddress = -1;

    public Symbol(String name, Kind kind, Type type) {
        this.name = name;
        this.kind = kind;
        this.type = type;
    }

    public static Symbol constant(String name, Type type, long value) {
        Symbol s = new Symbol(name, Kind.CONST, type);
        s.constValue = value;
        return s;
    }

    public static Symbol builtin(String name) {
        Symbol s = new Symbol(name, Kind.BUILTIN, Type.VOID);
        s.builtin = name;
        return s;
    }

    public boolean isCallable() {
        return kind == Kind.FUNCTION || kind == Kind.PROCEDURE || kind == Kind.BUILTIN;
    }

    public boolean isStorage() {
        return kind == Kind.VARIABLE || kind == Kind.PARAM || kind == Kind.LOCAL || kind == Kind.LOOPVAR;
    }
}
