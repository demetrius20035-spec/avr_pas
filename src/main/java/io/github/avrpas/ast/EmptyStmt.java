package io.github.avrpas.ast;

import io.github.avrpas.diag.SourceLocation;

/** An empty statement (e.g. a stray semicolon, or a stubbed-out construct). */
public final class EmptyStmt extends Stmt {
    public EmptyStmt(SourceLocation loc) {
        super(loc);
    }

    @Override public <R> R accept(AstVisitor<R> v) { return v.visitEmpty(this); }
}
