package io.github.avrpas.ast;

import io.github.avrpas.diag.SourceLocation;
import io.github.avrpas.sema.Type;

/** A formal parameter of a procedure or function. */
public final class Param extends Node {
    public final String name;
    public final TypeRef typeRef;
    public final boolean byRef; // 'var' parameter
    public Type type = Type.ERROR;
    public io.github.avrpas.sema.Symbol symbol;

    public Param(String name, TypeRef typeRef, boolean byRef, SourceLocation loc) {
        super(loc);
        this.name = name;
        this.typeRef = typeRef;
        this.byRef = byRef;
    }

    @Override public <R> R accept(AstVisitor<R> v) {
        throw new UnsupportedOperationException("Param is not visited directly");
    }
}
