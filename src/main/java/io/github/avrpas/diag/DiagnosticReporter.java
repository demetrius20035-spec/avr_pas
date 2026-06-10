package io.github.avrpas.diag;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects diagnostics from every compilation stage. Errors do not necessarily
 * abort transpilation immediately; stages decide when to stop based on
 * {@link #hasErrors()}.
 */
public final class DiagnosticReporter {

    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private final String fileName;

    public DiagnosticReporter(String fileName) {
        this.fileName = fileName;
    }

    public void report(Diagnostic d) {
        diagnostics.add(d);
    }

    public void error(Diagnostic.Kind kind, String message, SourceLocation loc) {
        report(new Diagnostic(Diagnostic.Severity.ERROR, kind, message, loc));
    }

    public void warning(Diagnostic.Kind kind, String message, SourceLocation loc) {
        report(new Diagnostic(Diagnostic.Severity.WARNING, kind, message, loc));
    }

    public void info(Diagnostic.Kind kind, String message, SourceLocation loc) {
        report(new Diagnostic(Diagnostic.Severity.INFO, kind, message, loc));
    }

    public boolean hasErrors() {
        for (Diagnostic d : diagnostics) {
            if (d.severity == Diagnostic.Severity.ERROR) {
                return true;
            }
        }
        return false;
    }

    public int errorCount() {
        int n = 0;
        for (Diagnostic d : diagnostics) {
            if (d.severity == Diagnostic.Severity.ERROR) n++;
        }
        return n;
    }

    public int warningCount() {
        int n = 0;
        for (Diagnostic d : diagnostics) {
            if (d.severity == Diagnostic.Severity.WARNING) n++;
        }
        return n;
    }

    public List<Diagnostic> all() {
        return diagnostics;
    }

    public String fileName() {
        return fileName;
    }
}
