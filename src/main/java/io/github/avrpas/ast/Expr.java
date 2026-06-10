package io.github.avrpas.ast;

import io.github.avrpas.diag.SourceLocation;
import io.github.avrpas.sema.Type;

/**
 * Base class for expression nodes. {@link #type} is filled in by the semantic
 * analyzer and consumed by the code generator.
 */
public abstract class Expr extends Node {
    /** Resolved type, set during semantic analysis. */
    public Type type = Type.ERROR;

    /** Constant value when the expression folds to a compile-time integer. */
    public Long constValue;

    protected Expr(SourceLocation loc) {
        super(loc);
    }

    public boolean isConstant() {
        return constValue != null;
    }
}
