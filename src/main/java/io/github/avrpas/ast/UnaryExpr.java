package io.github.avrpas.ast;

import io.github.avrpas.ast.Operators.UnOp;
import io.github.avrpas.diag.SourceLocation;

public final class UnaryExpr extends Expr {
    public final UnOp op;
    public final Expr operand;

    public UnaryExpr(UnOp op, Expr operand, SourceLocation loc) {
        super(loc);
        this.op = op;
        this.operand = operand;
    }

    @Override public <R> R accept(AstVisitor<R> v) { return v.visitUnary(this); }
}
