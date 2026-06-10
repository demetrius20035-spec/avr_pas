package io.github.avrpas.ast;

import io.github.avrpas.diag.SourceLocation;
import io.github.avrpas.sema.Symbol;
import io.github.avrpas.sema.Type;

/** A single variable declaration (one name). */
public final class VarDecl extends Node {
    public final String name;
    public final TypeRef typeRef;
    public Type type = Type.ERROR;
    public Symbol symbol;

    public VarDecl(String name, TypeRef typeRef, SourceLocation loc) {
        super(loc);
        this.name = name;
        this.typeRef = typeRef;
    }

    @Override public <R> R accept(AstVisitor<R> v) { return v.visitVarDecl(this); }
}
