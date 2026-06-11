package io.github.avrpas.ast;

import io.github.avrpas.diag.SourceLocation;

import java.util.List;

/** A {@code begin ... end} block. */
public final class CompoundStmt extends Stmt {
    public final List<Stmt> statements;

    public CompoundStmt(List<Stmt> statements, SourceLocation loc) {
        super(loc);
        this.statements = statements;
    }

    @Override public <R> R accept(AstVisitor<R> v) { return v.visitCompound(this); }
}
