package io.github.avrpas.ast;

import io.github.avrpas.diag.SourceLocation;
import io.github.avrpas.sema.Symbol;
import io.github.avrpas.sema.Type;

import java.util.List;

/**
 * A procedure or function declaration. A function has a non-void
 * {@link #returnTypeRef}. When {@link #interruptVector} is non-null the routine
 * is an interrupt service routine bound to the named vector.
 */
public final class ProcDecl extends Node {
    public final String name;
    public final List<Param> params;
    public final TypeRef returnTypeRef; // null for procedures
    public final List<VarDecl> locals;
    public final List<ConstDecl> localConsts;
    public final CompoundStmt body;
    public final String interruptVector; // e.g. "TIMER0_OVF", or null

    public Type returnType = Type.VOID;
    public Symbol symbol;
    /** Storage symbol holding a function's return value. */
    public Symbol resultSymbol;

    public ProcDecl(String name, List<Param> params, TypeRef returnTypeRef,
                    List<ConstDecl> localConsts, List<VarDecl> locals,
                    CompoundStmt body, String interruptVector, SourceLocation loc) {
        super(loc);
        this.name = name;
        this.params = params;
        this.returnTypeRef = returnTypeRef;
        this.localConsts = localConsts;
        this.locals = locals;
        this.body = body;
        this.interruptVector = interruptVector;
    }

    public boolean isFunction() {
        return returnTypeRef != null;
    }

    public boolean isInterrupt() {
        return interruptVector != null;
    }

    @Override public <R> R accept(AstVisitor<R> v) { return v.visitProcDecl(this); }
}
