package io.github.avrpas;

import io.github.avrpas.chip.Chip;
import io.github.avrpas.chip.ChipRegistry;
import io.github.avrpas.config.CliParser;
import io.github.avrpas.diag.TranspilerException;

import java.util.Map;

/**
 * Command-line entry point for the PASCAL -> AVR assembly transpiler.
 */
public final class Main {

    public static final String VERSION = "0.1.0";

    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        if (args.length == 0) {
            System.out.println(CliParser.usage());
            return 1;
        }
        CliParser.Result r;
        try {
            r = new CliParser().parse(args);
        } catch (TranspilerException e) {
            System.err.println("error: " + e.getMessage());
            return 2;
        }

        if (r.showHelp) {
            System.out.println(CliParser.usage());
            return 0;
        }
        if (r.showVersion) {
            System.out.println("pascal2asm-avr " + VERSION);
            return 0;
        }
        if (r.config.chipDbPath != null) {
            try {
                java.nio.file.Path p = java.nio.file.Paths.get(r.config.chipDbPath);
                ChipRegistry.registerFromJson(io.github.avrpas.util.Json.parse(
                        new String(java.nio.file.Files.readAllBytes(p),
                                java.nio.charset.StandardCharsets.UTF_8)));
            } catch (Exception e) {
                System.err.println("warning: could not load chip db: " + e.getMessage());
            }
        }
        if (r.listChips) {
            printChipList();
            return 0;
        }
        if (r.chipInfo != null) {
            return printChipInfo(r.chipInfo);
        }

        try {
            return new Transpiler(r.config, System.out, System.err).run();
        } catch (TranspilerException e) {
            System.err.println("error: " + e.getMessage());
            return 2;
        }
    }

    private static void printChipList() {
        System.out.println("Supported chips (" + ChipRegistry.names().size() + "):");
        for (Chip c : ChipRegistry.all()) {
            System.out.printf("  %-14s %-8s flash=%6dB sram=%5dB eeprom=%5dB%n",
                    c.name(), c.family(), c.flashBytes(), c.sramBytes(), c.eepromBytes());
        }
    }

    private static int printChipInfo(String name) {
        Chip c = ChipRegistry.get(name);
        if (c == null) {
            System.err.println("error: unknown chip '" + name + "'");
            return 2;
        }
        System.out.println("Chip:        " + c.name());
        System.out.println("Family:      " + c.family());
        System.out.println("Flash:       " + c.flashBytes() + " B");
        System.out.println("SRAM:        " + c.sramBytes() + " B (0x"
                + Integer.toHexString(c.ramStart()) + " .. 0x" + Integer.toHexString(c.ramEnd()) + ")");
        System.out.println("EEPROM:      " + c.eepromBytes() + " B");
        System.out.println("Max clock:   " + (c.maxClockHz() / 1_000_000) + " MHz");
        System.out.println("HW multiply: " + (c.hasMul() ? "yes" : "no")
                + ",  movw: " + (c.hasMovw() ? "yes" : "no")
                + ",  jmp/call: " + (c.hasJmpCall() ? "yes" : "no"));
        System.out.println("Peripherals: " + c.peripherals());
        System.out.println("Vectors:     " + c.vectorCount());
        System.out.println("SFRs known:  " + c.sfrMap().size());
        return 0;
    }
}
