package io.github.avrpas.ast;

import io.github.avrpas.diag.SourceLocation;

import java.util.List;

public final class RepeatStmt extends Stmt {
    public final List<Stmt> body;
    public final Expr condition; // loop exits when condition is true

    public RepeatStmt(List<Stmt> body, Expr condition, SourceLocation loc) {
        super(loc);
        this.body = body;
        this.condition = condition;
    }

    @Override public <R> R accept(AstVisitor<R> v) { return v.visitRepeat(this); }
}
