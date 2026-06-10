package io.github.avrpas.parser;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.TreeSet;

/**
 * A minimal source preprocessor implementing PASCAL-style conditional
 * compilation directives inside comments:
 *
 * <pre>
 *   {$DEFINE NAME}     {$UNDEF NAME}
 *   {$IFDEF NAME} ... {$ELSE} ... {$ENDIF}
 *   {$IFNDEF NAME} ... {$ENDIF}
 * </pre>
 *
 * Predefined symbols always include the target chip name and family (e.g.
 * {@code ATMEGA8}, {@code ATMEGA}) plus any symbols supplied via
 * {@code --define}. Inactive lines are blanked rather than removed so that
 * reported line numbers stay aligned with the original file.
 */
public final class Preprocessor {

    private final Set<String> defines = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    public Preprocessor(Set<String> initialDefines) {
        if (initialDefines != null) defines.addAll(initialDefines);
    }

    public Set<String> defines() { return defines; }

    public String process(String source) {
        String[] lines = source.split("\n", -1);
        StringBuilder out = new StringBuilder();

        // each stack frame: whether the enclosing context is currently emitting
        Deque<Boolean> active = new ArrayDeque<>();
        Deque<Boolean> taken = new ArrayDeque<>();
        boolean emitting = true;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String directive = extractDirective(line);
            if (directive != null) {
                String[] parts = directive.split("\\s+", 2);
                String key = parts[0].toUpperCase();
                String arg = parts.length > 1 ? parts[1].trim() : "";
                switch (key) {
                    case "DEFINE":
                        if (emitting) defines.add(arg);
                        break;
                    case "UNDEF":
                        if (emitting) defines.remove(arg);
                        break;
                    case "IFDEF": {
                        active.push(emitting);
                        boolean cond = emitting && defines.contains(arg);
                        taken.push(cond);
                        emitting = cond;
                        break;
                    }
                    case "IFNDEF": {
                        active.push(emitting);
                        boolean cond = emitting && !defines.contains(arg);
                        taken.push(cond);
                        emitting = cond;
                        break;
                    }
                    case "ELSE": {
                        boolean parent = active.isEmpty() ? true : active.peek();
                        boolean wasTaken = taken.isEmpty() ? false : taken.peek();
                        emitting = parent && !wasTaken;
                        if (!taken.isEmpty()) { taken.pop(); taken.push(true); }
                        break;
                    }
                    case "ENDIF":
                        if (!active.isEmpty()) emitting = active.pop();
                        if (!taken.isEmpty()) taken.pop();
                        break;
                    default:
                        // unknown directive: keep the line as-is if emitting
                        out.append(emitting ? line : "");
                        if (i < lines.length - 1) out.append('\n');
                        continue;
                }
                // directive lines themselves are blanked
                if (i < lines.length - 1) out.append('\n');
                continue;
            }
            out.append(emitting ? line : "");
            if (i < lines.length - 1) out.append('\n');
        }
        return out.toString();
    }

    /** Returns the directive text (without {$ ... }) or null if the line has none. */
    private String extractDirective(String line) {
        int open = line.indexOf("{$");
        if (open < 0) return null;
        int close = line.indexOf('}', open);
        if (close < 0) return null;
        String inner = line.substring(open + 2, close).trim();
        // only treat known conditional directives as preprocessor directives
        String upper = inner.toUpperCase();
        if (upper.startsWith("DEFINE") || upper.startsWith("UNDEF")
                || upper.startsWith("IFDEF") || upper.startsWith("IFNDEF")
                || upper.equals("ELSE") || upper.equals("ENDIF")) {
            return inner;
        }
        return null;
    }
}
