package io.github.avrpas.ast;

import io.github.avrpas.ast.Operators.BinOp;
import io.github.avrpas.diag.SourceLocation;

public final class BinaryExpr extends Expr {
    public final BinOp op;
    public final Expr left;
    public final Expr right;

    public BinaryExpr(BinOp op, Expr left, Expr right, SourceLocation loc) {
        super(loc);
        this.op = op;
        this.left = left;
        this.right = right;
    }

    @Override public <R> R accept(AstVisitor<R> v) { return v.visitBinary(this); }
}
