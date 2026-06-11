package io.github.avrpas.ast;

import io.github.avrpas.diag.SourceLocation;

public final class BoolLiteral extends Expr {
    public final boolean value;

    public BoolLiteral(boolean value, SourceLocation loc) {
        super(loc);
        this.value = value;
        this.constValue = value ? 1L : 0L;
    }

    @Override public <R> R accept(AstVisitor<R> v) { return v.visitBoolLiteral(this); }
}
