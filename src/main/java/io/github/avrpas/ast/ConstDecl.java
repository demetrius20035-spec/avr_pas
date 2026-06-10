package io.github.avrpas.ast;

import io.github.avrpas.diag.SourceLocation;
import io.github.avrpas.sema.Symbol;

/** A constant declaration: {@code const NAME = value;}. */
public final class ConstDecl extends Node {
    public final String name;
    public final Expr value;
    public Symbol symbol;

    public ConstDecl(String name, Expr value, SourceLocation loc) {
        super(loc);
        this.name = name;
        this.value = value;
    }

    @Override public <R> R accept(AstVisitor<R> v) { return v.visitConstDecl(this); }
}
