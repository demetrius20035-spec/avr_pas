package io.github.avrpas.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * All transpiler settings, populated from defaults, an optional JSON/XML config
 * file and command-line overrides (in that order of increasing precedence).
 */
public final class Config {

    // Required I/O
    public String chip;
    public String inputPath;
    public String outputPath;

    // Clock / fuses
    public long fCpuHz = 8_000_000L;
    public final Map<String, String> fuses = new LinkedHashMap<>();

    // Code generation
    public OptimizationGoal optimize = OptimizationGoal.NONE;
    public boolean emitComments = true;
    public boolean emitDebugInfo = false;
    public boolean alignData = false;

    // Peripheral defaults (can be overridden inside the program too)
    public int uartBaud = 9600;
    public int adcResolution = 10;

    // Conditional compilation
    public final Set<String> defines = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    // Extra outputs
    public boolean emitListing = false;
    public String listingPath;
    public boolean emitMap = false;
    public String mapPath;
    public boolean verboseLog = false;
    public boolean stubUnsupported = true;

    // Extra chip database
    public String chipDbPath;

    public boolean hasFuse(String name) {
        return fuses.containsKey(name);
    }

    /** Parse a clock string such as "8MHz", "16000000", "1.0 MHz", "12kHz". */
    public static long parseClock(String s) {
        if (s == null) return 0;
        String t = s.trim().toLowerCase().replace("hz", "").trim();
        double mult = 1;
        if (t.endsWith("m")) { mult = 1_000_000d; t = t.substring(0, t.length() - 1); }
        else if (t.endsWith("k")) { mult = 1_000d; t = t.substring(0, t.length() - 1); }
        else if (t.endsWith("g")) { mult = 1_000_000_000d; t = t.substring(0, t.length() - 1); }
        t = t.trim();
        try {
            return Math.round(Double.parseDouble(t) * mult);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public String fCpuPretty() {
        if (fCpuHz % 1_000_000 == 0) return (fCpuHz / 1_000_000) + " MHz";
        if (fCpuHz % 1_000 == 0) return (fCpuHz / 1_000) + " kHz";
        return fCpuHz + " Hz";
    }
}
