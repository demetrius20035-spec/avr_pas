package io.github.avrpas.ast;

import io.github.avrpas.diag.SourceLocation;

/** Base class for statement nodes. */
public abstract class Stmt extends Node {
    protected Stmt(SourceLocation loc) {
        super(loc);
    }
}
