package io.github.avrpas.ast;

import io.github.avrpas.diag.SourceLocation;

public final class StringLiteral extends Expr {
    public final String value;

    public StringLiteral(String value, SourceLocation loc) {
        super(loc);
        this.value = value;
    }

    @Override public <R> R accept(AstVisitor<R> v) { return v.visitStringLiteral(this); }
}
