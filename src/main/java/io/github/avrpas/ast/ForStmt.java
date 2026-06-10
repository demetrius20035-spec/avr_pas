package io.github.avrpas.ast;

import io.github.avrpas.diag.SourceLocation;
import io.github.avrpas.sema.Symbol;

public final class ForStmt extends Stmt {
    public final String varName;
    public final Expr from;
    public final Expr to;
    public final boolean downto;
    public final Stmt body;
    /** Resolved loop-variable symbol. */
    public Symbol symbol;

    public ForStmt(String varName, Expr from, Expr to, boolean downto, Stmt body, SourceLocation loc) {
        super(loc);
        this.varName = varName;
        this.from = from;
        this.to = to;
        this.downto = downto;
        this.body = body;
    }

    @Override public <R> R accept(AstVisitor<R> v) { return v.visitFor(this); }
}
