package io.github.avrpas.ast;

import io.github.avrpas.diag.SourceLocation;
import io.github.avrpas.sema.Type;

/**
 * A syntactic reference to a type in a declaration, e.g. {@code Integer} or
 * {@code array[0..7] of Byte}. Resolved to a {@link Type} during semantic
 * analysis.
 */
public final class TypeRef extends Node {
    public final String name;      // base type name (null for array)
    public final boolean isArray;
    public final int arrayLow;
    public final int arrayHigh;
    public final TypeRef elementType; // for arrays

    public Type resolved = Type.ERROR;

    public TypeRef(String name, SourceLocation loc) {
        super(loc);
        this.name = name;
        this.isArray = false;
        this.arrayLow = 0;
        this.arrayHigh = 0;
        this.elementType = null;
    }

    public TypeRef(int low, int high, TypeRef element, SourceLocation loc) {
        super(loc);
        this.name = null;
        this.isArray = true;
        this.arrayLow = low;
        this.arrayHigh = high;
        this.elementType = element;
    }

    @Override public <R> R accept(AstVisitor<R> v) {
        throw new UnsupportedOperationException("TypeRef is not visited directly");
    }
}
