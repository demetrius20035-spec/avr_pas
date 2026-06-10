package io.github.avrpas.ast;

import io.github.avrpas.diag.SourceLocation;

/**
 * Base class for every AST node. Carries the source location so that semantic
 * and code-generation diagnostics can point back at the original line/column.
 */
public abstract class Node {
    public final SourceLocation loc;

    protected Node(SourceLocation loc) {
        this.loc = loc == null ? SourceLocation.UNKNOWN : loc;
    }

    public abstract <R> R accept(AstVisitor<R> v);
}
