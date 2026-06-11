package io.github.avrpas.diag;

/**
 * A single error, warning or informational message produced during any stage
 * of transpilation (lexing, parsing, semantic analysis, code generation).
 */
public final class Diagnostic {

    public enum Severity { ERROR, WARNING, INFO }

    /** Coarse classification used in reports and to satisfy the
     *  "type of problem" requirement (unsupported op, out of memory, ...). */
    public enum Kind {
        SYNTAX,
        SEMANTIC,
        UNSUPPORTED_CHIP_FEATURE,
        UNSUPPORTED_PASCAL_FEATURE,
        MEMORY,
        CODEGEN,
        CONFIG,
        GENERAL
    }

    public final Severity severity;
    public final Kind kind;
    public final String message;
    public final SourceLocation location;

    public Diagnostic(Severity severity, Kind kind, String message, SourceLocation location) {
        this.severity = severity;
        this.kind = kind;
        this.message = message;
        this.location = location == null ? SourceLocation.UNKNOWN : location;
    }

    public String format(String fileName) {
        String file = fileName == null ? "<input>" : fileName;
        String loc = location.line > 0 ? (file + ":" + location) : file;
        return loc + ": " + severity.name().toLowerCase() + " [" + kind.name().toLowerCase() + "]: " + message;
    }
}
