package io.github.avrpas.ast;

import io.github.avrpas.diag.SourceLocation;

public final class IfStmt extends Stmt {
    public final Expr condition;
    public final Stmt thenBranch;
    public final Stmt elseBranch; // may be null

    public IfStmt(Expr condition, Stmt thenBranch, Stmt elseBranch, SourceLocation loc) {
        super(loc);
        this.condition = condition;
        this.thenBranch = thenBranch;
        this.elseBranch = elseBranch;
    }

    @Override public <R> R accept(AstVisitor<R> v) { return v.visitIf(this); }
}
