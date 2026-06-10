package io.github.avrpas.ast;

import io.github.avrpas.diag.SourceLocation;

import java.util.List;

/**
 * The root of the AST: a complete PASCAL program with global declarations and
 * a main {@code begin ... end.} block.
 */
public final class Program extends Node {
    public final String name;
    public final List<ConstDecl> consts;
    public final List<VarDecl> globals;
    public final List<ProcDecl> routines;
    public final CompoundStmt main;

    public Program(String name, List<ConstDecl> consts, List<VarDecl> globals,
                   List<ProcDecl> routines, CompoundStmt main, SourceLocation loc) {
        super(loc);
        this.name = name;
        this.consts = consts;
        this.globals = globals;
        this.routines = routines;
        this.main = main;
    }

    @Override public <R> R accept(AstVisitor<R> v) { return v.visitProgram(this); }
}
