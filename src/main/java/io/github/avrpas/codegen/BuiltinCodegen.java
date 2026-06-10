package io.github.avrpas.codegen;

import io.github.avrpas.ast.CallExpr;
import io.github.avrpas.ast.Expr;
import io.github.avrpas.ast.VarExpr;
import io.github.avrpas.chip.Chip;
import io.github.avrpas.diag.Diagnostic;
import io.github.avrpas.sema.Symbol;

import java.util.List;

/**
 * Emits inline AVR code for the built-in peripheral/runtime routines. All
 * register addresses are resolved through the {@link Chip} SFR map, and every
 * routine checks that the registers it needs actually exist on the target —
 * otherwise it emits a diagnostic and a clearly-marked stub.
 *
 * <p>Standard AVR bit positions are used (they are consistent across the
 * classic and mega cores for the registers handled here).</p>
 */
final class BuiltinCodegen {

    // common bit positions
    private static final int UDRE = 5, RXC = 7, TXC = 6, RXEN = 4, TXEN = 3,
            UCSZ0 = 1, UCSZ1 = 2, URSEL = 7;
    private static final int ADEN = 7, ADSC = 6, REFS0 = 6;
    private static final int SPE = 6, MSTR = 4, SPIF = 7;
    private static final int TWINT = 7, TWEA = 6, TWSTA = 5, TWSTO = 4, TWEN = 2;
    private static final int WDCE = 4, WDE = 3;

    private BuiltinCodegen() {}

    static void emit(CodeGenerator cg, CallExpr c) {
        String name = c.builtin.toLowerCase();
        AsmBuilder a = cg.asm();
        switch (name) {
            case "sei":  a.insn("sei");  return;
            case "cli":  a.insn("cli");  return;
            case "nop":  a.insn("nop");  return;
            case "sleep": a.insn("sleep"); return;
            case "wdr":  a.insn("wdr");  return;

            case "setbit":    bitOp(cg, c, BitOp.SET);    return;
            case "clearbit":  bitOp(cg, c, BitOp.CLEAR);  return;
            case "togglebit": bitOp(cg, c, BitOp.TOGGLE); return;
            case "testbit":   testBit(cg, c);             return;

            case "delayms": cg.evalPublic(c.args.get(0)); cg.markDelayMs(); a.insn("rcall __delay_ms"); return;
            case "delayus": cg.evalPublic(c.args.get(0)); cg.markDelayUs(); a.insn("rcall __delay_us"); return;

            case "uartinit":     uartInit(cg);          return;
            case "uarttransmit": uartTransmit(cg, c);   return;
            case "uartreceive":  uartReceive(cg);       return;
            case "uartready":    uartReady(cg);         return;
            case "uartwritestr": uartWriteStr(cg, c);   return;

            case "adcinit": adcInit(cg);        return;
            case "adcread": adcRead(cg, c);     return;

            case "spimasterinit": spiInit(cg);      return;
            case "spitransfer":   spiTransfer(cg, c); return;

            case "twiinit":     twiInit(cg);        return;
            case "twistart":    twiCtrl(cg, (1 << TWINT) | (1 << TWSTA) | (1 << TWEN), true); return;
            case "twistop":     twiCtrl(cg, (1 << TWINT) | (1 << TWSTO) | (1 << TWEN), false); return;
            case "twiwrite":    twiWrite(cg, c);    return;
            case "twireadack":  twiRead(cg, true);  return;
            case "twireadnack": twiRead(cg, false); return;

            case "timer0init": timerInit(cg, c, "TCCR0", "TCCR0B"); return;
            case "timer1init": timerInit(cg, c, "TCCR1B", "TCCR1B"); return;
            case "pwminit":    pwmInit(cg, c);      return;

            case "wdtenable":  wdtEnable(cg, c);    return;
            case "wdtdisable": wdtDisable(cg);      return;
            case "wdtreset":   a.insn("wdr");       return;

            default:
                stub(cg, c, "built-in '" + c.builtin + "' has no code generator yet");
        }
    }

    // ----------------------------------------------------------- bit helpers

    private enum BitOp { SET, CLEAR, TOGGLE }

    private static void bitOp(CodeGenerator cg, CallExpr c, BitOp op) {
        AsmBuilder a = cg.asm();
        Symbol sfr = sfrArg(c);
        if (sfr == null) { stub(cg, c, "bit op target is not a register"); return; }
        Chip chip = cg.chip();
        Expr bitExpr = c.args.get(1);
        if (bitExpr.isConstant()) {
            int bit = (int) (bitExpr.constValue & 7);
            if (chip.isBitAddressable(sfr.sfrAddress) && op != BitOp.TOGGLE) {
                int io = chip.ioAddress(sfr.sfrAddress);
                a.insn((op == BitOp.SET ? "sbi  " : "cbi  ") + CodeGenerator.hex(io) + ", " + bit, sfr.name);
                return;
            }
            int mask = 1 << bit;
            readSfr(cg, sfr, "r18");
            switch (op) {
                case SET:    a.insn("ori  r18, " + mask); break;
                case CLEAR:  a.insn("andi r18, " + (~mask & 0xFF)); break;
                case TOGGLE: a.insn("ldi  r19, " + mask); a.insn("eor  r18, r19"); break;
            }
            writeSfr(cg, sfr, "r18");
        } else {
            // dynamic bit: build mask = 1 << bit
            cg.evalPublic(bitExpr);              // r24 = bit number
            a.insn("mov  r20, r24");
            a.insn("ldi  r19, 1");
            String top = cg.newLabel("mask");
            String end = cg.newLabel("maske");
            a.insn("tst  r20");
            a.insn("breq " + end);
            a.label(top);
            a.insn("lsl  r19");
            a.insn("dec  r20");
            a.insn("brne " + top);
            a.label(end);
            readSfr(cg, sfr, "r18");
            switch (op) {
                case SET:    a.insn("or   r18, r19"); break;
                case CLEAR:  a.insn("com  r19"); a.insn("and  r18, r19"); break;
                case TOGGLE: a.insn("eor  r18, r19"); break;
            }
            writeSfr(cg, sfr, "r18");
        }
    }

    private static void testBit(CodeGenerator cg, CallExpr c) {
        AsmBuilder a = cg.asm();
        Symbol sfr = sfrArg(c);
        if (sfr == null) { stub(cg, c, "TestBit target is not a register"); cg.loadImm16(0); return; }
        Chip chip = cg.chip();
        Expr bitExpr = c.args.get(1);
        String done = cg.newLabel("tbit");
        if (bitExpr.isConstant() && chip.isBitAddressable(sfr.sfrAddress)) {
            int bit = (int) (bitExpr.constValue & 7);
            int io = chip.ioAddress(sfr.sfrAddress);
            a.insn("ldi  r24, 0");
            a.insn("sbis " + CodeGenerator.hex(io) + ", " + bit, "skip if set");
            a.insn("rjmp " + done);
            a.insn("ldi  r24, 1");
            a.label(done);
            a.insn("clr  r25");
            return;
        }
        // generic: mask & reg
        if (bitExpr.isConstant()) {
            int mask = 1 << (int) (bitExpr.constValue & 7);
            readSfr(cg, sfr, "r24");
            a.insn("andi r24, " + mask);
        } else {
            cg.evalPublic(bitExpr);
            a.insn("mov  r20, r24");
            a.insn("ldi  r19, 1");
            String top = cg.newLabel("tmask");
            String end = cg.newLabel("tmaske");
            a.insn("tst  r20");
            a.insn("breq " + end);
            a.label(top);
            a.insn("lsl  r19");
            a.insn("dec  r20");
            a.insn("brne " + top);
            a.label(end);
            readSfr(cg, sfr, "r24");
            a.insn("and  r24, r19");
        }
        a.insn("breq " + done);
        a.insn("ldi  r24, 1");
        a.label(done);
        a.insn("clr  r25");
    }

    // ------------------------------------------------------------------ UART

    private static boolean modernUart(Chip chip) {
        return chip.hasSfr("UCSRB") || chip.hasSfr("UCSR0B");
    }

    private static void uartInit(CodeGenerator cg) {
        AsmBuilder a = cg.asm();
        Chip chip = cg.chip();
        long baud = cg.config().uartBaud;
        long ubrr = (cg.config().fCpuHz / (16 * baud)) - 1;
        a.comment("UART init: " + baud + " baud @ " + cg.config().fCpuPretty() + " (UBRR=" + ubrr + ")");
        if (chip.hasSfr("UBRRH")) {
            ldiWrite(cg, "UBRRH", (int) ((ubrr >> 8) & 0xFF));
        }
        if (chip.hasSfr("UBRRL")) {
            ldiWrite(cg, "UBRRL", (int) (ubrr & 0xFF));
        } else if (chip.hasSfr("UBRR")) {
            ldiWrite(cg, "UBRR", (int) (ubrr & 0xFF));
        }
        // enable RX + TX
        int ucsrb = (1 << RXEN) | (1 << TXEN);
        String ben = chip.hasSfr("UCSRB") ? "UCSRB" : (chip.hasSfr("UCR") ? "UCR" : null);
        if (ben != null) ldiWrite(cg, ben, ucsrb);
        // frame format 8N1
        if (chip.hasSfr("UCSRC")) {
            int ucsrc = (1 << UCSZ1) | (1 << UCSZ0);
            // classic mega8/16/32 share UCSRC with UBRRH -> URSEL select bit needed
            if (chip.sfrAddress("UCSRC") == chip.sfrAddress("UBRRH")) ucsrc |= (1 << URSEL);
            ldiWrite(cg, "UCSRC", ucsrc);
        }
    }

    private static void uartTransmit(CodeGenerator cg, CallExpr c) {
        AsmBuilder a = cg.asm();
        Chip chip = cg.chip();
        cg.evalPublic(c.args.get(0));      // byte in r24
        a.insn("mov  r23, r24", "save byte");
        String wait = cg.newLabel("utx");
        a.label(wait);
        readSfr(cg, statusReg(chip), "r18");
        a.insn("sbrs r18, " + UDRE, "wait for empty transmit buffer");
        a.insn("rjmp " + wait);
        a.insn("mov  r24, r23");
        writeSfr(cg, dataReg(chip), "r24");
    }

    private static void uartReceive(CodeGenerator cg) {
        AsmBuilder a = cg.asm();
        Chip chip = cg.chip();
        String wait = cg.newLabel("urx");
        a.label(wait);
        readSfr(cg, statusReg(chip), "r18");
        a.insn("sbrs r18, " + RXC, "wait for received data");
        a.insn("rjmp " + wait);
        readSfr(cg, dataReg(chip), "r24");
        a.insn("clr  r25");
    }

    private static void uartReady(CodeGenerator cg) {
        AsmBuilder a = cg.asm();
        Chip chip = cg.chip();
        String done = cg.newLabel("urdy");
        a.insn("ldi  r24, 0");
        readSfr(cg, statusReg(chip), "r18");
        a.insn("sbrc r18, " + RXC);
        a.insn("ldi  r24, 1");
        a.label(done);
        a.insn("clr  r25");
    }

    private static void uartWriteStr(CodeGenerator cg, CallExpr c) {
        AsmBuilder a = cg.asm();
        Chip chip = cg.chip();
        cg.evalPublic(c.args.get(0));     // r25:r24 = flash address of string
        a.insn("movw r30, r24", "Z = string ptr");
        String top = cg.newLabel("strtx");
        String done = cg.newLabel("strdone");
        a.label(top);
        a.insn("lpm  r23, Z+", "load char from flash");
        a.insn("tst  r23");
        a.insn("breq " + done);
        String wait = cg.newLabel("strwait");
        a.label(wait);
        readSfr(cg, statusReg(chip), "r18");
        a.insn("sbrs r18, " + UDRE);
        a.insn("rjmp " + wait);
        writeSfr(cg, dataReg(chip), "r23");
        a.insn("rjmp " + top);
        a.label(done);
    }

    private static String statusReg(Chip chip) {
        if (chip.hasSfr("UCSRA")) return "UCSRA";
        if (chip.hasSfr("UCSR0A")) return "UCSR0A";
        if (chip.hasSfr("USR")) return "USR";
        return "UCSRA";
    }

    private static String dataReg(Chip chip) {
        if (chip.hasSfr("UDR")) return "UDR";
        if (chip.hasSfr("UDR0")) return "UDR0";
        return "UDR";
    }

    // ------------------------------------------------------------------- ADC

    private static void adcInit(CodeGenerator cg) {
        AsmBuilder a = cg.asm();
        a.comment("ADC init: AVcc reference, prescaler /128");
        ldiWrite(cg, "ADMUX", 1 << REFS0);
        int adcsra = (1 << ADEN) | 0x07; // enable + prescaler 128
        ldiWrite(cg, "ADCSRA", adcsra);
    }

    private static void adcRead(CodeGenerator cg, CallExpr c) {
        AsmBuilder a = cg.asm();
        cg.evalPublic(c.args.get(0));         // channel in r24
        a.insn("andi r24, 0x07", "channel 0..7");
        readSfr(cg, sym(cg, "ADMUX"), "r18");
        a.insn("andi r18, 0xF8", "keep reference bits");
        a.insn("or   r18, r24");
        writeSfr(cg, sym(cg, "ADMUX"), "r18");
        // start conversion
        readSfr(cg, sym(cg, "ADCSRA"), "r18");
        a.insn("ori  r18, " + (1 << ADSC));
        writeSfr(cg, sym(cg, "ADCSRA"), "r18");
        String wait = cg.newLabel("adc");
        a.label(wait);
        readSfr(cg, sym(cg, "ADCSRA"), "r18");
        a.insn("sbrc r18, " + ADSC, "wait for conversion");
        a.insn("rjmp " + wait);
        readSfr(cg, sym(cg, "ADCL"), "r24");
        readSfr(cg, sym(cg, "ADCH"), "r25");
    }

    // ------------------------------------------------------------------- SPI

    private static void spiInit(CodeGenerator cg) {
        AsmBuilder a = cg.asm();
        a.comment("SPI master init (MOSI/SCK/SS as output on PORTB)");
        if (cg.chip().hasSfr("DDRB")) {
            // PB2(SS), PB3(MOSI), PB5(SCK) for classic mega; best-effort
            ldiOrWrite(cg, "DDRB", (1 << 2) | (1 << 3) | (1 << 5));
        }
        ldiWrite(cg, "SPCR", (1 << SPE) | (1 << MSTR));
    }

    private static void spiTransfer(CodeGenerator cg, CallExpr c) {
        AsmBuilder a = cg.asm();
        cg.evalPublic(c.args.get(0));
        writeSfr(cg, sym(cg, "SPDR"), "r24");
        String wait = cg.newLabel("spi");
        a.label(wait);
        readSfr(cg, sym(cg, "SPSR"), "r18");
        a.insn("sbrs r18, " + SPIF, "wait for transfer complete");
        a.insn("rjmp " + wait);
        readSfr(cg, sym(cg, "SPDR"), "r24");
        a.insn("clr  r25");
    }

    // ------------------------------------------------------------------- TWI

    private static void twiInit(CodeGenerator cg) {
        AsmBuilder a = cg.asm();
        long twbr = ((cg.config().fCpuHz / 100000L) - 16) / 2; // 100 kHz, prescaler 1
        if (twbr < 0) twbr = 2;
        a.comment("TWI init: 100 kHz (TWBR=" + twbr + ")");
        ldiWrite(cg, "TWSR", 0);
        ldiWrite(cg, "TWBR", (int) (twbr & 0xFF));
        ldiWrite(cg, "TWCR", 1 << TWEN);
    }

    private static void twiCtrl(CodeGenerator cg, int value, boolean wait) {
        ldiWrite(cg, "TWCR", value);
        twiWait(cg);
    }

    private static void twiWrite(CodeGenerator cg, CallExpr c) {
        cg.evalPublic(c.args.get(0));
        writeSfr(cg, sym(cg, "TWDR"), "r24");
        ldiWrite(cg, "TWCR", (1 << TWINT) | (1 << TWEN));
        twiWait(cg);
    }

    private static void twiRead(CodeGenerator cg, boolean ack) {
        AsmBuilder a = cg.asm();
        int v = (1 << TWINT) | (1 << TWEN) | (ack ? (1 << TWEA) : 0);
        ldiWrite(cg, "TWCR", v);
        twiWait(cg);
        readSfr(cg, sym(cg, "TWDR"), "r24");
        a.insn("clr  r25");
    }

    private static void twiWait(CodeGenerator cg) {
        AsmBuilder a = cg.asm();
        String wait = cg.newLabel("twi");
        a.label(wait);
        readSfr(cg, sym(cg, "TWCR"), "r18");
        a.insn("sbrs r18, " + TWINT, "wait for TWINT");
        a.insn("rjmp " + wait);
    }

    // ----------------------------------------------------------------- Timer

    private static void timerInit(CodeGenerator cg, CallExpr c, String classic, String modern) {
        Chip chip = cg.chip();
        String reg = chip.hasSfr(classic) ? classic : (chip.hasSfr(modern) ? modern : classic);
        cg.evalPublic(c.args.get(0));            // prescaler select in r24
        a(cg).insn("andi r24, 0x07", "clock select bits");
        writeSfr(cg, sym(cg, reg), "r24");
    }

    private static void pwmInit(CodeGenerator cg, CallExpr c) {
        AsmBuilder a = cg.asm();
        a.comment("PWM init (Timer1 fast PWM, 8-bit) - basic configuration");
        if (cg.chip().hasSfr("TCCR1A")) {
            // WGM10=1, COM1A1=1 -> 0x81 ; prescaler via arg in TCCR1B
            ldiWrite(cg, "TCCR1A", 0x81);
            cg.evalPublic(c.args.get(0));
            a.insn("andi r24, 0x07");
            a.insn("ori  r24, 0x08", "WGM12 (fast PWM 8-bit)");
            writeSfr(cg, sym(cg, "TCCR1B"), "r24");
        } else {
            stub(cg, c, "PWM not configured for this chip");
        }
    }

    // ------------------------------------------------------------------- WDT

    private static void wdtEnable(CodeGenerator cg, CallExpr c) {
        AsmBuilder a = cg.asm();
        String reg = cg.chip().hasSfr("WDTCSR") ? "WDTCSR" : "WDTCR";
        cg.evalPublic(c.args.get(0));        // timeout select (WDP bits) in r24
        a.insn("andi r24, 0x07");
        a.insn("ori  r24, " + ((1 << WDE)), "enable + timeout");
        a.insn("mov  r23, r24");
        // timed sequence
        a.insn("ldi  r18, " + ((1 << WDCE) | (1 << WDE)));
        writeSfr(cg, sym(cg, reg), "r18");
        writeSfr(cg, sym(cg, reg), "r23");
    }

    private static void wdtDisable(CodeGenerator cg) {
        AsmBuilder a = cg.asm();
        String reg = cg.chip().hasSfr("WDTCSR") ? "WDTCSR" : "WDTCR";
        a.insn("wdr");
        a.insn("ldi  r18, " + ((1 << WDCE) | (1 << WDE)));
        writeSfr(cg, sym(cg, reg), "r18");
        a.insn("ldi  r18, 0");
        writeSfr(cg, sym(cg, reg), "r18");
    }

    // --------------------------------------------------------------- helpers

    private static AsmBuilder a(CodeGenerator cg) { return cg.asm(); }

    private static Symbol sfrArg(CallExpr c) {
        Expr first = c.args.get(0);
        if (first instanceof VarExpr && ((VarExpr) first).symbol != null
                && ((VarExpr) first).symbol.isSfr) {
            return ((VarExpr) first).symbol;
        }
        return null;
    }

    /** Resolve an SFR symbol by name, or null (with a diagnostic) if absent. */
    private static Symbol sym(CodeGenerator cg, String name) {
        Chip chip = cg.chip();
        if (!chip.hasSfr(name)) {
            cg.reporter().error(Diagnostic.Kind.UNSUPPORTED_CHIP_FEATURE,
                    "Register " + name + " is not available on " + chip.name(), null);
            return null;
        }
        Symbol s = new Symbol(name, Symbol.Kind.VARIABLE, io.github.avrpas.sema.Type.BYTE);
        s.isSfr = true;
        s.sfrAddress = chip.sfrAddress(name);
        s.label = name;
        return s;
    }

    private static void readSfr(CodeGenerator cg, Symbol s, String dest) {
        if (s == null) { cg.asm().insn("clr  " + dest, "stub: missing register"); return; }
        Chip chip = cg.chip();
        if (chip.isDirectIo(s.sfrAddress)) {
            cg.asm().insn("in   " + dest + ", " + CodeGenerator.hex(chip.ioAddress(s.sfrAddress)), s.name);
        } else {
            cg.asm().insn("lds  " + dest + ", " + CodeGenerator.hex(s.sfrAddress), s.name);
        }
    }

    private static void readSfr(CodeGenerator cg, String name, String dest) {
        readSfr(cg, sym(cg, name), dest);
    }

    private static void writeSfr(CodeGenerator cg, Symbol s, String src) {
        if (s == null) { cg.asm().comment("stub: missing register, write ignored"); return; }
        Chip chip = cg.chip();
        if (chip.isDirectIo(s.sfrAddress)) {
            cg.asm().insn("out  " + CodeGenerator.hex(chip.ioAddress(s.sfrAddress)) + ", " + src, s.name);
        } else {
            cg.asm().insn("sts  " + CodeGenerator.hex(s.sfrAddress) + ", " + src, s.name);
        }
    }

    private static void writeSfr(CodeGenerator cg, String name, String src) {
        writeSfr(cg, sym(cg, name), src);
    }

    /** {@code reg := imm} via a scratch register (r24). */
    private static void ldiWrite(CodeGenerator cg, String name, int imm) {
        cg.asm().insn("ldi  r24, " + (imm & 0xFF));
        writeSfr(cg, name, "r24");
    }

    /** {@code reg := reg | imm}. */
    private static void ldiOrWrite(CodeGenerator cg, String name, int imm) {
        readSfr(cg, name, "r18");
        cg.asm().insn("ori  r18, " + (imm & 0xFF));
        writeSfr(cg, name, "r18");
    }

    private static void stub(CodeGenerator cg, CallExpr c, String why) {
        if (cg.config().stubUnsupported) {
            cg.reporter().warning(Diagnostic.Kind.UNSUPPORTED_PASCAL_FEATURE,
                    why + " (stub emitted)", c.loc);
            cg.asm().comment("STUB: " + why);
            cg.asm().insn("nop");
        } else {
            cg.reporter().error(Diagnostic.Kind.UNSUPPORTED_PASCAL_FEATURE, why, c.loc);
        }
    }
}
