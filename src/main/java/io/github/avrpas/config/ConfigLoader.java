package io.github.avrpas.config;

import io.github.avrpas.diag.TranspilerException;
import io.github.avrpas.util.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Loads a JSON configuration file into a {@link Config}. Recognised keys mirror
 * the long-form CLI options. Any value present here can still be overridden on
 * the command line.
 *
 * <pre>
 * {
 *   "chip": "ATmega8",
 *   "input": "program.pas",
 *   "output": "program.s",
 *   "clock": "8MHz",
 *   "optimize": "speed",
 *   "fuses": { "lfuse": "0xE1", "hfuse": "0xD9" },
 *   "uartBaud": 19200,
 *   "adcBits": 10,
 *   "comments": true,
 *   "defines": ["DEBUG", "BOARD_V2"]
 * }
 * </pre>
 */
public final class ConfigLoader {

    private ConfigLoader() {}

    @SuppressWarnings("unchecked")
    public static void loadInto(Config c, String path) {
        String text;
        try {
            text = new String(Files.readAllBytes(Paths.get(path)), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new TranspilerException("Cannot read config file '" + path + "': " + e.getMessage());
        }
        Map<String, Object> root;
        try {
            root = Json.parseObject(text);
        } catch (RuntimeException e) {
            throw new TranspilerException("Invalid JSON in config '" + path + "': " + e.getMessage());
        }

        if (root.containsKey("chip")) c.chip = Json.asString(root.get("chip"), c.chip);
        if (root.containsKey("input")) c.inputPath = Json.asString(root.get("input"), c.inputPath);
        if (root.containsKey("output")) c.outputPath = Json.asString(root.get("output"), c.outputPath);
        if (root.containsKey("clock")) c.fCpuHz = Config.parseClock(Json.asString(root.get("clock"), "8MHz"));
        if (root.containsKey("optimize")) c.optimize = OptimizationGoal.parse(Json.asString(root.get("optimize"), "none"));
        if (root.containsKey("uartBaud")) c.uartBaud = Json.asInt(root.get("uartBaud"), c.uartBaud);
        if (root.containsKey("adcBits")) c.adcResolution = Json.asInt(root.get("adcBits"), c.adcResolution);
        if (root.containsKey("comments")) c.emitComments = Json.asBool(root.get("comments"), c.emitComments);
        if (root.containsKey("debug")) c.emitDebugInfo = Json.asBool(root.get("debug"), c.emitDebugInfo);
        if (root.containsKey("alignData")) c.alignData = Json.asBool(root.get("alignData"), c.alignData);
        if (root.containsKey("listing")) c.emitListing = Json.asBool(root.get("listing"), c.emitListing);
        if (root.containsKey("map")) c.emitMap = Json.asBool(root.get("map"), c.emitMap);
        if (root.containsKey("chipDb")) c.chipDbPath = Json.asString(root.get("chipDb"), c.chipDbPath);

        Object fuses = root.get("fuses");
        if (fuses instanceof Map) {
            for (Map.Entry<String, Object> e : ((Map<String, Object>) fuses).entrySet()) {
                c.fuses.put(e.getKey(), String.valueOf(e.getValue()));
            }
        }
        Object defines = root.get("defines");
        if (defines instanceof List) {
            for (Object d : (List<Object>) defines) c.defines.add(String.valueOf(d));
        }
    }
}
