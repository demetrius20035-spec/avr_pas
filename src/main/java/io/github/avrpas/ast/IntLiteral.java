package io.github.avrpas.ast;

import io.github.avrpas.diag.SourceLocation;

public final class IntLiteral extends Expr {
    public final long value;

    public IntLiteral(long value, SourceLocation loc) {
        super(loc);
        this.value = value;
        this.constValue = value;
    }

    @Override public <R> R accept(AstVisitor<R> v) { return v.visitIntLiteral(this); }
}
