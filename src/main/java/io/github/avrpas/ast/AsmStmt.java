package io.github.avrpas.ast;

import io.github.avrpas.diag.SourceLocation;

import java.util.List;

/**
 * An inline assembly block: {@code asm ... end;}. The contained lines are
 * emitted verbatim into the generated assembly, allowing hand-written AVR code
 * to be embedded directly.
 */
public final class AsmStmt extends Stmt {
    public final List<String> lines;

    public AsmStmt(List<String> lines, SourceLocation loc) {
        super(loc);
        this.lines = lines;
    }

    @Override public <R> R accept(AstVisitor<R> v) { return v.visitAsm(this); }
}
