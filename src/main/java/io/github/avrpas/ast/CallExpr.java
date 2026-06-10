package io.github.avrpas.ast;

import io.github.avrpas.diag.SourceLocation;
import io.github.avrpas.sema.Symbol;

import java.util.List;

/**
 * A function or built-in call used as an expression. The same node type is
 * wrapped by {@link CallStmt} when a procedure is invoked as a statement.
 */
public final class CallExpr extends Expr {
    public final String name;
    public final List<Expr> args;
    /** Resolved callee (user routine or built-in) during semantic analysis. */
    public Symbol symbol;
    /** Non-null when this call resolves to a peripheral/runtime built-in. */
    public String builtin;

    public CallExpr(String name, List<Expr> args, SourceLocation loc) {
        super(loc);
        this.name = name;
        this.args = args;
    }

    @Override public <R> R accept(AstVisitor<R> v) { return v.visitCallExpr(this); }
}
