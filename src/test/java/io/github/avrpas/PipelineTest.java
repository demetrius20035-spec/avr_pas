package io.github.avrpas;

import io.github.avrpas.ast.Program;
import io.github.avrpas.chip.Chip;
import io.github.avrpas.chip.ChipRegistry;
import io.github.avrpas.codegen.CodeGenerator;
import io.github.avrpas.config.Config;
import io.github.avrpas.diag.DiagnosticReporter;
import io.github.avrpas.lexer.Lexer;
import io.github.avrpas.lexer.Token;
import io.github.avrpas.lexer.TokenType;
import io.github.avrpas.parser.Parser;
import io.github.avrpas.parser.Preprocessor;
import io.github.avrpas.sema.SemanticAnalyzer;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.*;

/**
 * End-to-end and unit coverage for the transpiler pipeline.
 */
public class PipelineTest {

    private String compile(String src, String chipName) {
        Config cfg = new Config();
        cfg.chip = chipName;
        cfg.fCpuHz = 8_000_000L;
        Chip chip = ChipRegistry.get(chipName);
        assertNotNull("chip " + chipName + " must exist", chip);
        DiagnosticReporter rep = new DiagnosticReporter("test.pas");
        List<Token> tokens = new Lexer(src, rep).tokenize();
        Program prog = new Parser(tokens, rep).parseProgram();
        assertFalse("no syntax errors: " + rep.all(), rep.hasErrors());
        SemanticAnalyzer sema = new SemanticAnalyzer(prog, chip, cfg, rep);
        sema.analyze();
        assertFalse("no semantic errors: " + rep.all(), rep.hasErrors());
        String asm = new CodeGenerator(prog, chip, cfg, rep, sema).generate();
        assertFalse("no codegen errors: " + rep.all(), rep.hasErrors());
        return asm;
    }

    @Test
    public void lexerHandlesOperatorsAndComments() {
        DiagnosticReporter rep = new DiagnosticReporter("t");
        List<Token> t = new Lexer("x := 1 + 2; { comment } // line\n y <= 3", rep).tokenize();
        assertEquals(TokenType.IDENT, t.get(0).type);
        assertEquals(TokenType.ASSIGN, t.get(1).type);
        assertFalse(rep.hasErrors());
    }

    @Test
    public void chipRegistryHasPriorityChips() {
        for (String name : new String[]{"ATmega8", "ATmega16", "ATmega32", "ATmega328P",
                "ATtiny2313", "ATtiny13", "ATtiny85", "AT90S2313", "AT90S8515", "AT90S4433"}) {
            assertTrue("missing chip " + name, ChipRegistry.isSupported(name));
        }
        assertTrue(ChipRegistry.names().size() >= 19);
    }

    @Test
    public void blinkCompilesForAtmega8() {
        String src = "program B; var i: byte; begin DDRB := DDRB or 1; "
                + "while True do begin PORTB := PORTB xor 1; DelayMs(100); end; end.";
        String asm = compile(src, "ATmega8");
        assertTrue(asm.contains("__reset"));
        assertTrue(asm.contains("__vectors"));
        assertTrue(asm.contains("rcall __delay_ms"));
        assertTrue("PORTB out instruction", asm.contains("out  0x18"));
    }

    @Test
    public void multiplyUsesLibgccOnClassicCore() {
        String src = "program M; var a,b,c: integer; begin a := 3; b := 5; c := a * b; end.";
        String asm = compile(src, "AT90S2313"); // no hardware multiplier
        assertTrue("classic core should call __mulhi3", asm.contains("rcall __mulhi3"));
    }

    @Test
    public void divisionUsesUnsignedHelperForWord() {
        String src = "program D; var a,b: word; begin a := 1000; b := a div 7; end.";
        String asm = compile(src, "ATmega8");
        assertTrue(asm.contains("rcall __udivmodhi4"));
    }

    @Test
    public void bitBuiltinUsesSbiWhenBitAddressable() {
        String src = "program S; begin SetBit(PORTB, 3); end.";
        String asm = compile(src, "ATmega8"); // PORTB at 0x38 -> io 0x18, bit-addressable
        assertTrue("should use sbi", asm.contains("sbi  0x18, 3"));
    }

    @Test
    public void interruptRoutineGetsVectorEntry() {
        String src = "program I; procedure T; interrupt TIMER0_OVF; begin Nop; end; "
                + "begin Sei; while True do Nop; end.";
        String asm = compile(src, "ATmega8");
        assertTrue(asm.contains("isr_T"));
        assertTrue(asm.contains("reti"));
    }

    @Test
    public void preprocessorSelectsBranchByDefine() {
        Preprocessor pp = new Preprocessor(new HashSet<>(Arrays.asList("ATMEGA")));
        String out = pp.process("{$IFDEF ATMEGA}\nyes\n{$ELSE}\nno\n{$ENDIF}\n");
        assertTrue(out.contains("yes"));
        assertFalse(out.contains("no"));
    }

    @Test
    public void unsupportedPeripheralIsReported() {
        Config cfg = new Config();
        cfg.chip = "ATtiny13";
        Chip chip = ChipRegistry.get("ATtiny13"); // no UART
        DiagnosticReporter rep = new DiagnosticReporter("t");
        String src = "program U; begin UartInit; end.";
        List<Token> tokens = new Lexer(src, rep).tokenize();
        Program prog = new Parser(tokens, rep).parseProgram();
        new SemanticAnalyzer(prog, chip, cfg, rep).analyze();
        assertTrue("UART on ATtiny13 must error", rep.hasErrors());
    }
}
