package io.github.avrpas.diag;

/**
 * Thrown when a stage cannot continue (e.g. unrecoverable parse error or a
 * fatal configuration problem). The pipeline catches it, prints the collected
 * diagnostics and exits with a non-zero status.
 */
public class TranspilerException extends RuntimeException {

    private final transient Diagnostic diagnostic;

    public TranspilerException(String message) {
        super(message);
        this.diagnostic = null;
    }

    public TranspilerException(Diagnostic diagnostic) {
        super(diagnostic.message);
        this.diagnostic = diagnostic;
    }

    public Diagnostic diagnostic() {
        return diagnostic;
    }
}
