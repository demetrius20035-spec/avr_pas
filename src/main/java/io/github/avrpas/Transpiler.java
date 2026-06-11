package io.github.avrpas;

import io.github.avrpas.ast.Program;
import io.github.avrpas.chip.Chip;
import io.github.avrpas.chip.ChipRegistry;
import io.github.avrpas.codegen.CodeGenerator;
import io.github.avrpas.config.Config;
import io.github.avrpas.diag.Diagnostic;
import io.github.avrpas.diag.DiagnosticReporter;
import io.github.avrpas.diag.TranspilerException;
import io.github.avrpas.lexer.Lexer;
import io.github.avrpas.lexer.Token;
import io.github.avrpas.parser.Parser;
import io.github.avrpas.parser.Preprocessor;
import io.github.avrpas.sema.SemanticAnalyzer;
import io.github.avrpas.sema.Symbol;
import io.github.avrpas.util.Json;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Drives the full transpilation pipeline: preprocess -> lex -> parse ->
 * semantic analysis -> code generation -> output. Each stage feeds a shared
 * {@link DiagnosticReporter}; the pipeline stops before a stage that depends on
 * the (now invalid) output of a stage that produced errors.
 */
public final class Transpiler {

    private final Config config;
    private final PrintStream out;
    private final PrintStream err;

    public Transpiler(Config config, PrintStream out, PrintStream err) {
        this.config = config;
        this.out = out;
        this.err = err;
    }

    /** @return process exit code (0 = success). */
    public int run() {
        validateConfig();

        if (config.chipDbPath != null) {
            loadChipDb(config.chipDbPath);
        }

        Chip chip = ChipRegistry.get(config.chip);
        if (chip == null) {
            err.println("error: unknown chip '" + config.chip + "'. Use --list-chips to see options.");
            return 2;
        }

        String fileName = config.inputPath;
        String source = readSource(fileName);

        DiagnosticReporter reporter = new DiagnosticReporter(fileName);

        // 1) preprocess (conditional compilation)
        Preprocessor pp = new Preprocessor(config.defines);
        pp.defines().add(chip.name().toUpperCase());
        pp.defines().add(chip.family().name());
        pp.defines().add("AVR");
        String processed = pp.process(source);

        // 2) lex
        log("Lexing " + fileName);
        List<Token> tokens = new Lexer(processed, reporter).tokenize();

        // 3) parse  (also acts as the basic syntax validator)
        log("Parsing");
        Program program;
        try {
            program = new Parser(tokens, reporter).parseProgram();
        } catch (TranspilerException e) {
            reporter.error(Diagnostic.Kind.SYNTAX, e.getMessage(), null);
            printDiagnostics(reporter);
            return 1;
        }
        if (reporter.hasErrors()) {
            printDiagnostics(reporter);
            err.println("Transpilation aborted: syntax errors.");
            return 1;
        }

        // 4) semantic analysis
        log("Analyzing (chip = " + chip.name() + ")");
        SemanticAnalyzer sema = new SemanticAnalyzer(program, chip, config, reporter);
        sema.analyze();
        if (reporter.hasErrors()) {
            printDiagnostics(reporter);
            err.println("Transpilation aborted: semantic errors.");
            return 1;
        }

        // 5) code generation
        log("Generating assembly for " + chip.name());
        String asm = new CodeGenerator(program, chip, config, reporter, sema).generate();
        if (reporter.hasErrors()) {
            printDiagnostics(reporter);
            err.println("Transpilation aborted: code generation errors.");
            return 1;
        }

        // 6) outputs
        String outputPath = resolveOutputPath(fileName);
        writeFile(outputPath, asm);
        out.println("Wrote " + outputPath);

        if (config.emitListing) {
            String lst = config.listingPath != null ? config.listingPath : replaceExt(outputPath, ".lst");
            writeFile(lst, buildListing(fileName, source, asm));
            out.println("Wrote " + lst);
        }
        if (config.emitMap) {
            String map = config.mapPath != null ? config.mapPath : replaceExt(outputPath, ".map");
            writeFile(map, buildMap(chip, sema));
            out.println("Wrote " + map);
        }

        printDiagnostics(reporter);
        out.println("Done: " + reporter.errorCount() + " error(s), "
                + reporter.warningCount() + " warning(s).");
        return 0;
    }

    // --------------------------------------------------------------- helpers

    private void validateConfig() {
        if (config.chip == null) throw new TranspilerException("No target chip specified (use --chip).");
        if (config.inputPath == null) throw new TranspilerException("No input file specified (use --input).");
    }

    private void loadChipDb(String path) {
        try {
            String text = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
            ChipRegistry.registerFromJson(Json.parse(text));
            log("Loaded chip database: " + path);
        } catch (IOException e) {
            throw new TranspilerException("Cannot read chip database '" + path + "': " + e.getMessage());
        }
    }

    private String readSource(String path) {
        try {
            return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new TranspilerException("Cannot read input '" + path + "': " + e.getMessage());
        }
    }

    private String resolveOutputPath(String input) {
        if (config.outputPath != null) return config.outputPath;
        return replaceExt(input, ".s");
    }

    private static String replaceExt(String path, String newExt) {
        int dot = path.lastIndexOf('.');
        int sep = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        if (dot > sep) return path.substring(0, dot) + newExt;
        return path + newExt;
    }

    private void writeFile(String path, String content) {
        try {
            Path p = Paths.get(path);
            if (p.getParent() != null) Files.createDirectories(p.getParent());
            Files.write(p, content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new TranspilerException("Cannot write '" + path + "': " + e.getMessage());
        }
    }

    private String buildListing(String fileName, String source, String asm) {
        StringBuilder sb = new StringBuilder();
        sb.append("; Listing for ").append(fileName).append("\n");
        sb.append("; ==== PASCAL source ====\n");
        String[] lines = source.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            sb.append(String.format("; %4d | %s%n", i + 1, lines[i]));
        }
        sb.append("\n; ==== Generated assembly ====\n");
        sb.append(asm);
        return sb.toString();
    }

    private String buildMap(Chip chip, SemanticAnalyzer sema) {
        StringBuilder sb = new StringBuilder();
        sb.append("Memory map for ").append(chip.name()).append('\n');
        sb.append("Flash: ").append(chip.flashBytes()).append(" B   SRAM: ")
          .append(chip.sramBytes()).append(" B   EEPROM: ").append(chip.eepromBytes()).append(" B\n");
        sb.append("SRAM range: ").append(CodeGeneratorHex(chip.ramStart()))
          .append(" .. ").append(CodeGeneratorHex(chip.ramEnd())).append("\n\n");
        sb.append(String.format("%-28s %-8s %s%n", "SYMBOL", "SIZE", "ADDR(approx)"));
        int addr = chip.ramStart();
        int total = 0;
        for (Symbol s : sema.storage) {
            int size = s.byRef ? 2 : Math.max(1, s.type.sizeBytes);
            sb.append(String.format("%-28s %-8d %s%n", s.label, size, CodeGeneratorHex(addr)));
            addr += size;
            total += size;
        }
        sb.append("\nStatic data total: ").append(total).append(" B of ")
          .append(chip.sramBytes()).append(" B SRAM\n");
        return sb.toString();
    }

    private static String CodeGeneratorHex(int v) {
        return "0x" + Integer.toHexString(v).toUpperCase();
    }

    private void printDiagnostics(DiagnosticReporter reporter) {
        for (Diagnostic d : reporter.all()) {
            PrintStream s = d.severity == Diagnostic.Severity.ERROR ? err : out;
            s.println(d.format(reporter.fileName()));
        }
    }

    private void log(String msg) {
        if (config.verboseLog) out.println("[log] " + msg);
    }
}
