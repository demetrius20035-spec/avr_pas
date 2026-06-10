package io.github.avrpas.diag;

/**
 * A position in the source file. Lines and columns are 1-based.
 */
public final class SourceLocation {
    public final int line;
    public final int column;

    public SourceLocation(int line, int column) {
        this.line = line;
        this.column = column;
    }

    public static final SourceLocation UNKNOWN = new SourceLocation(0, 0);

    @Override
    public String toString() {
        if (line <= 0) {
            return "<unknown>";
        }
        return line + ":" + column;
    }
}
