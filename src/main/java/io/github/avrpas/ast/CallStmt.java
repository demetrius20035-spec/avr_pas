package io.github.avrpas.ast;

import io.github.avrpas.diag.SourceLocation;

/** A procedure / built-in invocation used as a statement. */
public final class CallStmt extends Stmt {
    public final CallExpr call;

    public CallStmt(CallExpr call, SourceLocation loc) {
        super(loc);
        this.call = call;
    }

    @Override public <R> R accept(AstVisitor<R> v) { return v.visitCallStmt(this); }
}
