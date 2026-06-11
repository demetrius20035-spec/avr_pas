package io.github.avrpas.ast;

import io.github.avrpas.diag.SourceLocation;

/** {@code target := value}. The target is a variable or array element. */
public final class AssignStmt extends Stmt {
    public final Expr target;
    public final Expr value;

    public AssignStmt(Expr target, Expr value, SourceLocation loc) {
        super(loc);
        this.target = target;
        this.value = value;
    }

    @Override public <R> R accept(AstVisitor<R> v) { return v.visitAssign(this); }
}
