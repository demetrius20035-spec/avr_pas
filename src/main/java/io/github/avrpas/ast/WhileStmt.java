package io.github.avrpas.ast;

import io.github.avrpas.diag.SourceLocation;

public final class WhileStmt extends Stmt {
    public final Expr condition;
    public final Stmt body;

    public WhileStmt(Expr condition, Stmt body, SourceLocation loc) {
        super(loc);
        this.condition = condition;
        this.body = body;
    }

    @Override public <R> R accept(AstVisitor<R> v) { return v.visitWhile(this); }
}
