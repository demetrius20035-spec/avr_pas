package io.github.avrpas.ast;

import io.github.avrpas.diag.SourceLocation;

/** Array element access: {@code base[index]}. */
public final class IndexExpr extends Expr {
    public final Expr base;
    public final Expr index;

    public IndexExpr(Expr base, Expr index, SourceLocation loc) {
        super(loc);
        this.base = base;
        this.index = index;
    }

    @Override public <R> R accept(AstVisitor<R> v) { return v.visitIndexExpr(this); }
}
