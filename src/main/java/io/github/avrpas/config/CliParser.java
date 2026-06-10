package io.github.avrpas.config;

import io.github.avrpas.diag.TranspilerException;

/**
 * Parses command-line arguments into a {@link Config}. Command-line options
 * take precedence over values loaded from a {@code --config} file.
 *
 * <pre>
 * java -jar pascal2asm-avr.jar --chip ATMega8 --input program.pas \
 *      --output program.s --clock 8MHz --optimize speed \
 *      --fuses lfuse=0xE1,hfuse=0xD9
 * </pre>
 */
public final class CliParser {

    public static final class Result {
        public Config config = new Config();
        public boolean showHelp;
        public boolean showVersion;
        public boolean listChips;
        public String chipInfo;   // print details for one chip
    }

    public Result parse(String[] args) {
        Result r = new Result();
        // First pass: load a config file if specified so CLI flags can override.
        for (int i = 0; i < args.length; i++) {
            if (eq(args[i], "--config", "-c") && i + 1 < args.length) {
                ConfigLoader.loadInto(r.config, args[i + 1]);
            }
        }

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (canonical(a)) {
                case "--help":      r.showHelp = true; break;
                case "--version":   r.showVersion = true; break;
                case "--list-chips": r.listChips = true; break;
                case "--chip-info": r.chipInfo = next(args, ++i, a); break;
                case "--config":    i++; break; // already handled
                case "--chip":      r.config.chip = next(args, ++i, a); break;
                case "--input":     r.config.inputPath = next(args, ++i, a); break;
                case "--output":    r.config.outputPath = next(args, ++i, a); break;
                case "--clock":
                    r.config.fCpuHz = Config.parseClock(next(args, ++i, a));
                    break;
                case "--optimize":
                    r.config.optimize = OptimizationGoal.parse(next(args, ++i, a));
                    break;
                case "--fuses":     parseFuses(r.config, next(args, ++i, a)); break;
                case "--define":    r.config.defines.add(next(args, ++i, a)); break;
                case "--uart-baud": r.config.uartBaud = intArg(next(args, ++i, a)); break;
                case "--adc-bits":  r.config.adcResolution = intArg(next(args, ++i, a)); break;
                case "--chip-db":   r.config.chipDbPath = next(args, ++i, a); break;
                case "--listing":
                    r.config.emitListing = true;
                    r.config.listingPath = optionalValue(args, i);
                    if (r.config.listingPath != null) i++;
                    break;
                case "--map":
                    r.config.emitMap = true;
                    r.config.mapPath = optionalValue(args, i);
                    if (r.config.mapPath != null) i++;
                    break;
                case "--no-comments": r.config.emitComments = false; break;
                case "--comments":    r.config.emitComments = true; break;
                case "--debug":       r.config.emitDebugInfo = true; break;
                case "--align-data":  r.config.alignData = true; break;
                case "--no-stubs":    r.config.stubUnsupported = false; break;
                case "--verbose": case "-v": r.config.verboseLog = true; break;
                default:
                    if (a.startsWith("-")) {
                        throw new TranspilerException("Unknown option: " + a);
                    }
                    // bare positional = input file if not yet set
                    if (r.config.inputPath == null) r.config.inputPath = a;
                    else if (r.config.outputPath == null) r.config.outputPath = a;
            }
        }
        return r;
    }

    private void parseFuses(Config c, String spec) {
        for (String pair : spec.split(",")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                c.fuses.put(kv[0].trim(), kv[1].trim());
            }
        }
    }

    private int intArg(String s) {
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { throw new TranspilerException("Expected an integer, got: " + s); }
    }

    private String next(String[] args, int i, String opt) {
        if (i >= args.length) throw new TranspilerException("Missing value for option " + opt);
        return args[i];
    }

    /** A value that follows the flag only if it is not itself another flag. */
    private String optionalValue(String[] args, int i) {
        if (i + 1 < args.length && !args[i + 1].startsWith("-")) return args[i + 1];
        return null;
    }

    private boolean eq(String a, String... opts) {
        for (String o : opts) if (a.equals(o)) return true;
        return false;
    }

    private String canonical(String a) {
        // accept both --opt=value and --opt value forms by stripping '=value'
        int eq = a.indexOf('=');
        return eq > 0 && a.startsWith("--") ? a.substring(0, eq) : a;
    }

    public static String usage() {
        return String.join("\n",
            "pascal2asm-avr - PASCAL to AVR assembly transpiler",
            "",
            "Usage:",
            "  java -jar pascal2asm-avr.jar --chip <CHIP> --input <file.pas> [options]",
            "",
            "Required:",
            "  --chip <name>        Target MCU (e.g. ATmega8, ATtiny85, AT90S2313)",
            "  --input <file>       PASCAL source file (.pas)",
            "",
            "Output:",
            "  --output <file>      Assembly output (.s); default: <input>.s",
            "  --listing [file]     Emit a listing (.lst)",
            "  --map [file]         Emit a memory map (.map)",
            "",
            "Target configuration:",
            "  --clock <freq>       CPU clock, e.g. 8MHz, 16000000, 1.0MHz (sets F_CPU)",
            "  --fuses <spec>       Fuse settings, e.g. lfuse=0xE1,hfuse=0xD9",
            "  --uart-baud <n>      Default UART baud rate (default 9600)",
            "  --adc-bits <n>       ADC resolution in bits (default 10)",
            "",
            "Code generation:",
            "  --optimize <goal>    none | size | speed (default none)",
            "  --align-data         Word-align data section",
            "  --no-comments        Omit comments from the generated assembly",
            "  --debug              Emit debug annotations (source lines)",
            "  --define <NAME>      Define a symbol for {$IFDEF} conditional compilation",
            "  --no-stubs           Treat unsupported features as errors (no stubs)",
            "",
            "Misc:",
            "  --config <file>      Load options from a JSON config file",
            "  --chip-db <file>     Load additional chip definitions (JSON)",
            "  --list-chips         List all supported chips and exit",
            "  --chip-info <name>   Print hardware details for a chip and exit",
            "  --verbose, -v        Verbose transformation log",
            "  --help               Show this help",
            "  --version            Show version");
    }
}
