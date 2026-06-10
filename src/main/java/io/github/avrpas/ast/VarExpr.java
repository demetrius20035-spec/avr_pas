package io.github.avrpas.ast;

import io.github.avrpas.diag.SourceLocation;
import io.github.avrpas.sema.Symbol;

/** A reference to a named variable, constant or (zero-argument) function. */
public final class VarExpr extends Expr {
    public final String name;
    /** Resolved during semantic analysis. */
    public Symbol symbol;
    /** Non-null when a bare identifier resolves to a zero-argument built-in. */
    public String builtin;

    public VarExpr(String name, SourceLocation loc) {
        super(loc);
        this.name = name;
    }

    @Override public <R> R accept(AstVisitor<R> v) { return v.visitVarExpr(this); }
}
