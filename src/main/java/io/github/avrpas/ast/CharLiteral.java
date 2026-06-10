package io.github.avrpas.ast;

import io.github.avrpas.diag.SourceLocation;

public final class CharLiteral extends Expr {
    public final char value;

    public CharLiteral(char value, SourceLocation loc) {
        super(loc);
        this.value = value;
        this.constValue = (long) value;
    }

    @Override public <R> R accept(AstVisitor<R> v) { return v.visitCharLiteral(this); }
}
