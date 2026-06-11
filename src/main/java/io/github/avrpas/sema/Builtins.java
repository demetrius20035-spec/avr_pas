package io.github.avrpas.sema;

import io.github.avrpas.chip.Peripheral;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The built-in procedure/function library. These map onto inline AVR code
 * emitted by the code generator (peripheral drivers, delays, bit helpers and
 * interrupt control). Each descriptor records the return type, the expected
 * parameter types and the peripheral a chip must provide.
 *
 * <p>Chip SFRs (PORTB, UCSRA, ADMUX, ...) are <em>not</em> listed here; they
 * are injected as predefined byte variables so that idiomatic register code
 * such as {@code PORTB := PORTB or (1 shl 3)} works directly.</p>
 */
public final class Builtins {

    public static final class Descriptor {
        public final String name;
        public final Type returnType;
        public final List<Type> params;
        public final Peripheral requiredPeripheral; // null when none
        public final boolean firstArgIsSfr;         // SetBit/ClearBit/ToggleBit

        Descriptor(String name, Type ret, Peripheral req, boolean firstArgIsSfr, Type... params) {
            this.name = name;
            this.returnType = ret;
            this.requiredPeripheral = req;
            this.firstArgIsSfr = firstArgIsSfr;
            this.params = Arrays.asList(params);
        }
    }

    private static final Map<String, Descriptor> TABLE = new LinkedHashMap<>();

    private static void def(String name, Type ret, Peripheral req, Type... params) {
        TABLE.put(name.toLowerCase(), new Descriptor(name, ret, req, false, params));
    }

    private static void defSfr(String name, Type ret, Peripheral req, Type... params) {
        TABLE.put(name.toLowerCase(), new Descriptor(name, ret, req, true, params));
    }

    static {
        // --- interrupt / core control ---
        def("Sei", Type.VOID, null);
        def("Cli", Type.VOID, null);
        def("Nop", Type.VOID, null);
        def("Sleep", Type.VOID, null);
        def("Wdr", Type.VOID, Peripheral.WDT);

        // --- bit helpers (chip-specific sbi/cbi when possible) ---
        defSfr("SetBit",    Type.VOID, null, Type.BYTE, Type.BYTE);
        defSfr("ClearBit",  Type.VOID, null, Type.BYTE, Type.BYTE);
        defSfr("ToggleBit", Type.VOID, null, Type.BYTE, Type.BYTE);
        defSfr("TestBit",   Type.BOOLEAN, null, Type.BYTE, Type.BYTE);

        // --- delays (computed from F_CPU) ---
        def("DelayMs", Type.VOID, null, Type.WORD);
        def("DelayUs", Type.VOID, null, Type.WORD);

        // --- UART / USART ---
        def("UartInit",     Type.VOID, Peripheral.UART);
        def("UartTransmit", Type.VOID, Peripheral.UART, Type.BYTE);
        def("UartReceive",  Type.BYTE, Peripheral.UART);
        def("UartWriteStr", Type.VOID, Peripheral.UART, Type.WORD); // pointer to flash string
        def("UartReady",    Type.BOOLEAN, Peripheral.UART);

        // --- ADC ---
        def("AdcInit", Type.VOID, Peripheral.ADC);
        def("AdcRead", Type.WORD, Peripheral.ADC, Type.BYTE);

        // --- SPI ---
        def("SpiMasterInit", Type.VOID, Peripheral.SPI);
        def("SpiTransfer",   Type.BYTE, Peripheral.SPI, Type.BYTE);

        // --- TWI / I2C ---
        def("TwiInit",     Type.VOID, Peripheral.TWI);
        def("TwiStart",    Type.VOID, Peripheral.TWI);
        def("TwiStop",     Type.VOID, Peripheral.TWI);
        def("TwiWrite",    Type.VOID, Peripheral.TWI, Type.BYTE);
        def("TwiReadAck",  Type.BYTE, Peripheral.TWI);
        def("TwiReadNack", Type.BYTE, Peripheral.TWI);

        // --- Timer ---
        def("Timer0Init", Type.VOID, Peripheral.TIMER, Type.BYTE); // prescaler select
        def("Timer1Init", Type.VOID, Peripheral.TIMER, Type.BYTE);
        def("PwmInit",    Type.VOID, Peripheral.PWM, Type.BYTE);

        // --- Watchdog ---
        def("WdtEnable",  Type.VOID, Peripheral.WDT, Type.BYTE); // timeout select
        def("WdtDisable", Type.VOID, Peripheral.WDT);
        def("WdtReset",   Type.VOID, Peripheral.WDT);
    }

    private Builtins() {}

    public static boolean isBuiltin(String name) {
        return TABLE.containsKey(name.toLowerCase());
    }

    public static Descriptor get(String name) {
        return TABLE.get(name.toLowerCase());
    }

    public static Iterable<Descriptor> all() {
        return TABLE.values();
    }
}
