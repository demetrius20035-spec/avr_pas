package io.github.avrpas.config;

/** Code-generation optimisation preference. */
public enum OptimizationGoal {
    NONE,
    SIZE,
    SPEED;

    public static OptimizationGoal parse(String s) {
        if (s == null) return NONE;
        switch (s.trim().toLowerCase()) {
            case "size": case "s": return SIZE;
            case "speed": case "fast": return SPEED;
            case "none": case "0": return NONE;
            default: return NONE;
        }
    }
}
