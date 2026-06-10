package io.github.avrpas.codegen;

import java.util.ArrayList;
import java.util.List;

/**
 * Accumulates the generated assembly in logical sections and renders the final
 * GNU avr-as compatible source. Comment emission can be disabled to produce a
 * leaner file.
 */
public final class AsmBuilder {

    private final boolean comments;

    private final List<String> header = new ArrayList<>();
    private final List<String> vectors = new ArrayList<>();
    private final List<String> text = new ArrayList<>();
    private final List<String> runtime = new ArrayList<>();
    private final List<String> rodata = new ArrayList<>();
    private final List<String> bss = new ArrayList<>();

    public AsmBuilder(boolean comments) {
        this.comments = comments;
    }

    public boolean commentsEnabled() { return comments; }

    // --- raw section access ---
    public void header(String line) { header.add(line); }
    public void vector(String line) { vectors.add(line); }
    public void runtime(String line) { runtime.add(line); }
    public void rodata(String line) { rodata.add(line); }
    public void bss(String line) { bss.add(line); }

    // --- code (.text) emission helpers ---
    public void label(String name) { text.add(name + ":"); }
    public void insn(String mnemonic) { text.add("\t" + mnemonic); }

    public void insn(String mnemonic, String comment) {
        if (comments && comment != null) {
            text.add("\t" + pad(mnemonic) + "; " + comment);
        } else {
            text.add("\t" + mnemonic);
        }
    }

    public void blank() { text.add(""); }

    public void comment(String text) {
        if (comments) this.text.add("\t; " + text);
    }

    public void lineComment(String text) {
        if (comments) this.text.add("; " + text);
    }

    private static String pad(String s) {
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < 24) sb.append(' ');
        return sb.toString();
    }

    public String render() {
        StringBuilder sb = new StringBuilder();
        for (String h : header) sb.append(h).append('\n');
        sb.append('\n');

        if (!vectors.isEmpty()) {
            sb.append("\t.section .text\n");
            sb.append("\t.global\t__vectors\n");
            sb.append("__vectors:\n");
            for (String v : vectors) sb.append('\t').append(v).append('\n');
            sb.append('\n');
        }

        sb.append("\t.text\n");
        for (String t : text) sb.append(t).append('\n');
        sb.append('\n');

        if (!runtime.isEmpty()) {
            if (comments) sb.append("; ---- runtime support routines ----\n");
            for (String r : runtime) sb.append(r).append('\n');
            sb.append('\n');
        }

        if (!rodata.isEmpty()) {
            sb.append("\t.section .progmem.data,\"a\",@progbits\n");
            for (String r : rodata) sb.append(r).append('\n');
            sb.append('\n');
        }

        if (!bss.isEmpty()) {
            sb.append("\t.section .bss\n");
            for (String b : bss) sb.append(b).append('\n');
            sb.append('\n');
        }
        return sb.toString();
    }
}
