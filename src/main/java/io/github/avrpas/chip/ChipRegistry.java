package io.github.avrpas.chip;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Central database of supported chips. Definitions for the priority/old chips
 * are hand-verified here; additional chips can be registered through
 * {@link ExtraChips} (community contributions) or loaded from a JSON file via
 * {@link #registerFromJson}.
 *
 * <p>SFR addresses are data-space addresses. For classic AVR I/O registers
 * this equals the datasheet I/O address + 0x20.</p>
 */
public final class ChipRegistry {

    private static final Map<String, Chip> CHIPS = new LinkedHashMap<>();

    static {
        registerAtmega();
        registerAttiny();
        registerAt90();
        ExtraChips.register(ChipRegistry::register);
    }

    private ChipRegistry() {}

    public static void register(Chip chip) {
        CHIPS.put(chip.name().toUpperCase(), chip);
    }

    /** Case-insensitive lookup; returns null if unknown. */
    public static Chip get(String name) {
        if (name == null) return null;
        return CHIPS.get(name.toUpperCase());
    }

    public static boolean isSupported(String name) {
        return get(name) != null;
    }

    public static List<String> names() {
        List<String> list = new ArrayList<>(CHIPS.keySet());
        Collections.sort(list);
        return list;
    }

    public static List<Chip> all() {
        return new ArrayList<>(CHIPS.values());
    }

    // -------- helpers -------------------------------------------------------

    private static Chip.Builder ports(Chip.Builder b, String letter, int pin, int ddr, int port) {
        return b.sfr("PIN" + letter, pin).sfr("DDR" + letter, ddr).sfr("PORT" + letter, port);
    }

    private static void core(Chip.Builder b, int sreg, int spl, int sph) {
        b.sfr("SREG", sreg).sfr("SPL", spl);
        if (sph >= 0) b.sfr("SPH", sph);
    }

    // ===================================================================== //
    //  ATmega family                                                        //
    // ===================================================================== //

    private static void registerAtmega() {
        register(buildAtmega8());
        register(buildAtmega16());
        register(buildAtmega32());
        register(buildAtmega328p());
    }

    private static Chip buildAtmega8() {
        Chip.Builder b = new Chip.Builder("ATmega8")
                .family(ChipFamily.ATMEGA)
                .flash(8192).sram(1024).eeprom(512).ramStart(0x60).maxClock(16_000_000L)
                .mul(true).movw(true).enhancedMul(true).jmpCall(false)
                .peripherals(Peripheral.GPIO, Peripheral.UART, Peripheral.SPI, Peripheral.TWI,
                        Peripheral.ADC, Peripheral.TIMER, Peripheral.PWM, Peripheral.WDT,
                        Peripheral.EEPROM, Peripheral.ANALOG_COMP);
        core(b, 0x5F, 0x5D, 0x5E);
        ports(b, "B", 0x36, 0x37, 0x38);
        ports(b, "C", 0x33, 0x34, 0x35);
        ports(b, "D", 0x30, 0x31, 0x32);
        b.sfr("MCUCR", 0x55).sfr("GICR", 0x5B).sfr("GIFR", 0x5A);
        // USART
        b.sfr("UDR", 0x2C).sfr("UCSRA", 0x2B).sfr("UCSRB", 0x2A).sfr("UCSRC", 0x40)
         .sfr("UBRRL", 0x29).sfr("UBRRH", 0x40);
        // SPI
        b.sfr("SPCR", 0x2D).sfr("SPSR", 0x2E).sfr("SPDR", 0x2F);
        // TWI
        b.sfr("TWBR", 0x20).sfr("TWSR", 0x21).sfr("TWAR", 0x22).sfr("TWDR", 0x23).sfr("TWCR", 0x56);
        // ADC
        b.sfr("ADMUX", 0x27).sfr("ADCSRA", 0x26).sfr("ADCH", 0x25).sfr("ADCL", 0x24);
        // Timers
        b.sfr("TCCR0", 0x53).sfr("TCNT0", 0x52)
         .sfr("TCCR1A", 0x4F).sfr("TCCR1B", 0x4E).sfr("TCNT1H", 0x4D).sfr("TCNT1L", 0x4C)
         .sfr("OCR1AH", 0x4B).sfr("OCR1AL", 0x4A).sfr("OCR1BH", 0x49).sfr("OCR1BL", 0x48)
         .sfr("TCCR2", 0x45).sfr("TCNT2", 0x44).sfr("OCR2", 0x43)
         .sfr("TIMSK", 0x59).sfr("TIFR", 0x58);
        // WDT, EEPROM
        b.sfr("WDTCR", 0x41)
         .sfr("EEARH", 0x3F).sfr("EEARL", 0x3E).sfr("EEDR", 0x3D).sfr("EECR", 0x3C);
        // Vectors (datasheet order)
        int v = 0;
        b.vector("RESET", v++).vector("INT0", v++).vector("INT1", v++)
         .vector("TIMER2_COMP", v++).vector("TIMER2_OVF", v++)
         .vector("TIMER1_CAPT", v++).vector("TIMER1_COMPA", v++).vector("TIMER1_COMPB", v++)
         .vector("TIMER1_OVF", v++).vector("TIMER0_OVF", v++).vector("SPI_STC", v++)
         .vector("USART_RXC", v++).vector("USART_UDRE", v++).vector("USART_TXC", v++)
         .vector("ADC", v++).vector("EE_RDY", v++).vector("ANA_COMP", v++)
         .vector("TWI", v++).vector("SPM_RDY", v++);
        return b.build();
    }

    private static Chip buildAtmega16() {
        Chip.Builder b = new Chip.Builder("ATmega16")
                .family(ChipFamily.ATMEGA)
                .flash(16384).sram(1024).eeprom(512).ramStart(0x60).maxClock(16_000_000L)
                .mul(true).movw(true).enhancedMul(true).jmpCall(true)
                .peripherals(Peripheral.GPIO, Peripheral.UART, Peripheral.SPI, Peripheral.TWI,
                        Peripheral.ADC, Peripheral.TIMER, Peripheral.PWM, Peripheral.WDT,
                        Peripheral.EEPROM, Peripheral.ANALOG_COMP);
        core(b, 0x5F, 0x5D, 0x5E);
        ports(b, "A", 0x39, 0x3A, 0x3B);
        ports(b, "B", 0x36, 0x37, 0x38);
        ports(b, "C", 0x33, 0x34, 0x35);
        ports(b, "D", 0x30, 0x31, 0x32);
        b.sfr("MCUCR", 0x55).sfr("GICR", 0x5B).sfr("GIFR", 0x5A);
        b.sfr("UDR", 0x2C).sfr("UCSRA", 0x2B).sfr("UCSRB", 0x2A).sfr("UCSRC", 0x40)
         .sfr("UBRRL", 0x29).sfr("UBRRH", 0x40);
        b.sfr("SPCR", 0x2D).sfr("SPSR", 0x2E).sfr("SPDR", 0x2F);
        b.sfr("TWBR", 0x20).sfr("TWSR", 0x21).sfr("TWAR", 0x22).sfr("TWDR", 0x23).sfr("TWCR", 0x56);
        b.sfr("ADMUX", 0x27).sfr("ADCSRA", 0x26).sfr("ADCH", 0x25).sfr("ADCL", 0x24);
        b.sfr("TCCR0", 0x53).sfr("TCNT0", 0x52).sfr("OCR0", 0x5C)
         .sfr("TCCR1A", 0x4F).sfr("TCCR1B", 0x4E).sfr("TCNT1H", 0x4D).sfr("TCNT1L", 0x4C)
         .sfr("OCR1AH", 0x4B).sfr("OCR1AL", 0x4A).sfr("OCR1BH", 0x49).sfr("OCR1BL", 0x48)
         .sfr("TCCR2", 0x45).sfr("TCNT2", 0x44).sfr("OCR2", 0x43)
         .sfr("TIMSK", 0x59).sfr("TIFR", 0x58);
        b.sfr("WDTCR", 0x41)
         .sfr("EEARH", 0x3F).sfr("EEARL", 0x3E).sfr("EEDR", 0x3D).sfr("EECR", 0x3C);
        int v = 0;
        b.vector("RESET", v++).vector("INT0", v++).vector("INT1", v++).vector("TIMER2_COMP", v++)
         .vector("TIMER2_OVF", v++).vector("TIMER1_CAPT", v++).vector("TIMER1_COMPA", v++)
         .vector("TIMER1_COMPB", v++).vector("TIMER1_OVF", v++).vector("TIMER0_OVF", v++)
         .vector("SPI_STC", v++).vector("USART_RXC", v++).vector("USART_UDRE", v++)
         .vector("USART_TXC", v++).vector("ADC", v++).vector("EE_RDY", v++)
         .vector("ANA_COMP", v++).vector("TWI", v++).vector("INT2", v++)
         .vector("TIMER0_COMP", v++).vector("SPM_RDY", v++);
        return b.build();
    }

    private static Chip buildAtmega32() {
        // Register layout identical to ATmega16; larger flash/sram.
        Chip.Builder b = new Chip.Builder("ATmega32")
                .family(ChipFamily.ATMEGA)
                .flash(32768).sram(2048).eeprom(1024).ramStart(0x60).maxClock(16_000_000L)
                .mul(true).movw(true).enhancedMul(true).jmpCall(true)
                .peripherals(Peripheral.GPIO, Peripheral.UART, Peripheral.SPI, Peripheral.TWI,
                        Peripheral.ADC, Peripheral.TIMER, Peripheral.PWM, Peripheral.WDT,
                        Peripheral.EEPROM, Peripheral.ANALOG_COMP);
        core(b, 0x5F, 0x5D, 0x5E);
        ports(b, "A", 0x39, 0x3A, 0x3B);
        ports(b, "B", 0x36, 0x37, 0x38);
        ports(b, "C", 0x33, 0x34, 0x35);
        ports(b, "D", 0x30, 0x31, 0x32);
        b.sfr("MCUCR", 0x55).sfr("GICR", 0x5B).sfr("GIFR", 0x5A);
        b.sfr("UDR", 0x2C).sfr("UCSRA", 0x2B).sfr("UCSRB", 0x2A).sfr("UCSRC", 0x40)
         .sfr("UBRRL", 0x29).sfr("UBRRH", 0x40);
        b.sfr("SPCR", 0x2D).sfr("SPSR", 0x2E).sfr("SPDR", 0x2F);
        b.sfr("TWBR", 0x20).sfr("TWSR", 0x21).sfr("TWAR", 0x22).sfr("TWDR", 0x23).sfr("TWCR", 0x56);
        b.sfr("ADMUX", 0x27).sfr("ADCSRA", 0x26).sfr("ADCH", 0x25).sfr("ADCL", 0x24);
        b.sfr("TCCR0", 0x53).sfr("TCNT0", 0x52).sfr("OCR0", 0x5C)
         .sfr("TCCR1A", 0x4F).sfr("TCCR1B", 0x4E).sfr("TCNT1H", 0x4D).sfr("TCNT1L", 0x4C)
         .sfr("OCR1AH", 0x4B).sfr("OCR1AL", 0x4A).sfr("OCR1BH", 0x49).sfr("OCR1BL", 0x48)
         .sfr("TCCR2", 0x45).sfr("TCNT2", 0x44).sfr("OCR2", 0x43)
         .sfr("TIMSK", 0x59).sfr("TIFR", 0x58);
        b.sfr("WDTCR", 0x41)
         .sfr("EEARH", 0x3F).sfr("EEARL", 0x3E).sfr("EEDR", 0x3D).sfr("EECR", 0x3C);
        int v = 0;
        b.vector("RESET", v++).vector("INT0", v++).vector("INT1", v++).vector("INT2", v++)
         .vector("TIMER2_COMP", v++).vector("TIMER2_OVF", v++).vector("TIMER1_CAPT", v++)
         .vector("TIMER1_COMPA", v++).vector("TIMER1_COMPB", v++).vector("TIMER1_OVF", v++)
         .vector("TIMER0_COMP", v++).vector("TIMER0_OVF", v++).vector("SPI_STC", v++)
         .vector("USART_RXC", v++).vector("USART_UDRE", v++).vector("USART_TXC", v++)
         .vector("ADC", v++).vector("EE_RDY", v++).vector("ANA_COMP", v++).vector("TWI", v++)
         .vector("SPM_RDY", v++);
        return b.build();
    }

    private static Chip buildAtmega328p() {
        // Extended I/O: most peripheral registers live above 0x5F and need lds/sts.
        Chip.Builder b = new Chip.Builder("ATmega328P")
                .family(ChipFamily.ATMEGA)
                .flash(32768).sram(2048).eeprom(1024).ramStart(0x100).maxClock(20_000_000L)
                .mul(true).movw(true).enhancedMul(true).jmpCall(true)
                .peripherals(Peripheral.GPIO, Peripheral.UART, Peripheral.SPI, Peripheral.TWI,
                        Peripheral.ADC, Peripheral.TIMER, Peripheral.PWM, Peripheral.WDT,
                        Peripheral.EEPROM, Peripheral.ANALOG_COMP);
        core(b, 0x5F, 0x5D, 0x5E);
        ports(b, "B", 0x23, 0x24, 0x25);
        ports(b, "C", 0x26, 0x27, 0x28);
        ports(b, "D", 0x29, 0x2A, 0x2B);
        b.sfr("MCUCR", 0x55);
        // USART0 (extended I/O)
        b.sfr("UDR0", 0xC6).sfr("UCSR0A", 0xC0).sfr("UCSR0B", 0xC1).sfr("UCSR0C", 0xC2)
         .sfr("UBRR0L", 0xC4).sfr("UBRR0H", 0xC5)
         // Aliases without the trailing 0 so generic peripheral code resolves them.
         .sfr("UDR", 0xC6).sfr("UCSRA", 0xC0).sfr("UCSRB", 0xC1).sfr("UCSRC", 0xC2)
         .sfr("UBRRL", 0xC4).sfr("UBRRH", 0xC5);
        b.sfr("SPCR", 0x4C).sfr("SPSR", 0x4D).sfr("SPDR", 0x4E);
        b.sfr("TWBR", 0xB8).sfr("TWSR", 0xB9).sfr("TWAR", 0xBA).sfr("TWDR", 0xBB).sfr("TWCR", 0xBC);
        b.sfr("ADMUX", 0x7C).sfr("ADCSRA", 0x7A).sfr("ADCSRB", 0x7B).sfr("ADCH", 0x79).sfr("ADCL", 0x78);
        b.sfr("TCCR0A", 0x44).sfr("TCCR0B", 0x45).sfr("TCNT0", 0x46).sfr("OCR0A", 0x47).sfr("OCR0B", 0x48)
         .sfr("TCCR1A", 0x80).sfr("TCCR1B", 0x81).sfr("TCNT1L", 0x84).sfr("TCNT1H", 0x85)
         .sfr("OCR1AL", 0x88).sfr("OCR1AH", 0x89).sfr("OCR1BL", 0x8A).sfr("OCR1BH", 0x8B)
         .sfr("TCCR2A", 0xB0).sfr("TCCR2B", 0xB1).sfr("TCNT2", 0xB2).sfr("OCR2A", 0xB3).sfr("OCR2B", 0xB4)
         .sfr("TIMSK0", 0x6E).sfr("TIMSK1", 0x6F).sfr("TIMSK2", 0x70);
        b.sfr("WDTCSR", 0x60)
         .sfr("EEARH", 0x42).sfr("EEARL", 0x41).sfr("EEDR", 0x40).sfr("EECR", 0x3F);
        int v = 0;
        b.vector("RESET", v++).vector("INT0", v++).vector("INT1", v++)
         .vector("PCINT0", v++).vector("PCINT1", v++).vector("PCINT2", v++)
         .vector("WDT", v++).vector("TIMER2_COMPA", v++).vector("TIMER2_COMPB", v++)
         .vector("TIMER2_OVF", v++).vector("TIMER1_CAPT", v++).vector("TIMER1_COMPA", v++)
         .vector("TIMER1_COMPB", v++).vector("TIMER1_OVF", v++).vector("TIMER0_COMPA", v++)
         .vector("TIMER0_COMPB", v++).vector("TIMER0_OVF", v++).vector("SPI_STC", v++)
         .vector("USART_RX", v++).vector("USART_UDRE", v++).vector("USART_TX", v++)
         .vector("ADC", v++).vector("EE_READY", v++).vector("ANALOG_COMP", v++)
         .vector("TWI", v++).vector("SPM_READY", v++);
        return b.build();
    }

    // ===================================================================== //
    //  ATtiny family                                                        //
    // ===================================================================== //

    private static void registerAttiny() {
        register(buildAttiny2313());
        register(buildAttiny13());
        register(buildAttiny85());
    }

    private static Chip buildAttiny2313() {
        Chip.Builder b = new Chip.Builder("ATtiny2313")
                .family(ChipFamily.ATTINY)
                .flash(2048).sram(128).eeprom(128).ramStart(0x60).maxClock(20_000_000L)
                .mul(false).movw(true).enhancedMul(false).jmpCall(false)
                .peripherals(Peripheral.GPIO, Peripheral.UART, Peripheral.SPI,
                        Peripheral.TIMER, Peripheral.PWM, Peripheral.WDT,
                        Peripheral.EEPROM, Peripheral.ANALOG_COMP);
        core(b, 0x5F, 0x5D, 0x5E);
        ports(b, "A", 0x39, 0x3A, 0x3B);
        ports(b, "B", 0x36, 0x37, 0x38);
        ports(b, "D", 0x30, 0x31, 0x32);
        b.sfr("MCUCR", 0x55);
        // USART
        b.sfr("UDR", 0x2C).sfr("UCSRA", 0x2B).sfr("UCSRB", 0x2A).sfr("UCSRC", 0x23)
         .sfr("UBRRL", 0x29).sfr("UBRRH", 0x22);
        b.sfr("TCCR0A", 0x50).sfr("TCCR0B", 0x53).sfr("TCNT0", 0x52).sfr("OCR0A", 0x56).sfr("OCR0B", 0x3C)
         .sfr("TCCR1A", 0x4F).sfr("TCCR1B", 0x4E).sfr("TCNT1H", 0x4D).sfr("TCNT1L", 0x4C)
         .sfr("OCR1AH", 0x4B).sfr("OCR1AL", 0x4A).sfr("TIMSK", 0x59).sfr("TIFR", 0x58);
        b.sfr("WDTCR", 0x41)
         .sfr("EEAR", 0x3E).sfr("EEDR", 0x3D).sfr("EECR", 0x3C);
        int v = 0;
        b.vector("RESET", v++).vector("INT0", v++).vector("INT1", v++)
         .vector("TIMER1_CAPT", v++).vector("TIMER1_COMPA", v++).vector("TIMER1_OVF", v++)
         .vector("TIMER0_OVF", v++).vector("USART_RX", v++).vector("USART_UDRE", v++)
         .vector("USART_TX", v++).vector("ANA_COMP", v++).vector("PCINT", v++)
         .vector("TIMER1_COMPB", v++).vector("TIMER0_COMPA", v++).vector("TIMER0_COMPB", v++)
         .vector("USI_START", v++).vector("USI_OVF", v++).vector("EE_READY", v++)
         .vector("WDT_OVERFLOW", v++);
        return b.build();
    }

    private static Chip buildAttiny13() {
        Chip.Builder b = new Chip.Builder("ATtiny13")
                .family(ChipFamily.ATTINY)
                .flash(1024).sram(64).eeprom(64).ramStart(0x60).maxClock(20_000_000L)
                .mul(false).movw(true).enhancedMul(false).jmpCall(false)
                .peripherals(Peripheral.GPIO, Peripheral.ADC, Peripheral.TIMER,
                        Peripheral.PWM, Peripheral.WDT, Peripheral.EEPROM, Peripheral.ANALOG_COMP);
        core(b, 0x5F, 0x5D, -1); // no SPH on tiny13
        ports(b, "B", 0x36, 0x37, 0x38);
        b.sfr("MCUCR", 0x55);
        b.sfr("ADMUX", 0x27).sfr("ADCSRA", 0x26).sfr("ADCSRB", 0x23).sfr("ADCH", 0x25).sfr("ADCL", 0x24);
        b.sfr("TCCR0A", 0x4F).sfr("TCCR0B", 0x53).sfr("TCNT0", 0x52).sfr("OCR0A", 0x56).sfr("OCR0B", 0x49)
         .sfr("TIMSK0", 0x59).sfr("TIFR0", 0x58);
        b.sfr("WDTCR", 0x41).sfr("EEARL", 0x3E).sfr("EEDR", 0x3D).sfr("EECR", 0x3C);
        int v = 0;
        b.vector("RESET", v++).vector("INT0", v++).vector("PCINT0", v++)
         .vector("TIMER0_OVF", v++).vector("EE_RDY", v++).vector("ANA_COMP", v++)
         .vector("TIMER0_COMPA", v++).vector("TIMER0_COMPB", v++).vector("WDT", v++)
         .vector("ADC", v++);
        return b.build();
    }

    private static Chip buildAttiny85() {
        Chip.Builder b = new Chip.Builder("ATtiny85")
                .family(ChipFamily.ATTINY)
                .flash(8192).sram(512).eeprom(512).ramStart(0x60).maxClock(20_000_000L)
                .mul(false).movw(true).enhancedMul(false).jmpCall(false)
                .peripherals(Peripheral.GPIO, Peripheral.ADC, Peripheral.SPI, Peripheral.TIMER,
                        Peripheral.PWM, Peripheral.WDT, Peripheral.EEPROM, Peripheral.ANALOG_COMP);
        core(b, 0x5F, 0x5D, 0x5E);
        ports(b, "B", 0x36, 0x37, 0x38);
        b.sfr("MCUCR", 0x55);
        b.sfr("ADMUX", 0x27).sfr("ADCSRA", 0x26).sfr("ADCSRB", 0x23).sfr("ADCH", 0x25).sfr("ADCL", 0x24);
        b.sfr("TCCR0A", 0x4A).sfr("TCCR0B", 0x53).sfr("TCNT0", 0x52).sfr("OCR0A", 0x49).sfr("OCR0B", 0x48)
         .sfr("TCCR1", 0x50).sfr("TCNT1", 0x4F).sfr("OCR1A", 0x4E).sfr("OCR1B", 0x4B).sfr("OCR1C", 0x4D)
         .sfr("TIMSK", 0x59).sfr("TIFR", 0x58);
        b.sfr("WDTCR", 0x41).sfr("EEARL", 0x3E).sfr("EEDR", 0x3D).sfr("EECR", 0x3C);
        int v = 0;
        b.vector("RESET", v++).vector("INT0", v++).vector("PCINT0", v++)
         .vector("TIMER1_COMPA", v++).vector("TIMER1_OVF", v++).vector("TIMER0_OVF", v++)
         .vector("EE_RDY", v++).vector("ANA_COMP", v++).vector("ADC", v++)
         .vector("TIMER1_COMPB", v++).vector("TIMER0_COMPA", v++).vector("TIMER0_COMPB", v++)
         .vector("WDT", v++).vector("USI_START", v++).vector("USI_OVF", v++);
        return b.build();
    }

    // ===================================================================== //
    //  AT90S (classic) family                                               //
    // ===================================================================== //

    private static void registerAt90() {
        register(buildAt90s2313());
        register(buildAt90s8515());
        register(buildAt90s4433());
    }

    private static Chip buildAt90s2313() {
        Chip.Builder b = new Chip.Builder("AT90S2313")
                .family(ChipFamily.AT90)
                .flash(2048).sram(128).eeprom(128).ramStart(0x60).maxClock(10_000_000L)
                .mul(false).movw(false).enhancedMul(false).jmpCall(false)
                .peripherals(Peripheral.GPIO, Peripheral.UART, Peripheral.TIMER,
                        Peripheral.PWM, Peripheral.WDT, Peripheral.EEPROM, Peripheral.ANALOG_COMP);
        core(b, 0x5F, 0x5D, -1); // no SPH
        ports(b, "B", 0x36, 0x37, 0x38);
        ports(b, "D", 0x30, 0x31, 0x32);
        b.sfr("MCUCR", 0x55);
        // Classic UART register names
        b.sfr("UDR", 0x2C).sfr("USR", 0x2B).sfr("UCR", 0x2A).sfr("UBRR", 0x29)
         .sfr("UCSRA", 0x2B).sfr("UCSRB", 0x2A).sfr("UBRRL", 0x29); // aliases
        b.sfr("TCCR0", 0x53).sfr("TCNT0", 0x52)
         .sfr("TCCR1A", 0x4F).sfr("TCCR1B", 0x4E).sfr("TCNT1H", 0x4D).sfr("TCNT1L", 0x4C)
         .sfr("OCR1AH", 0x4B).sfr("OCR1AL", 0x4A).sfr("TIMSK", 0x59).sfr("TIFR", 0x58);
        b.sfr("WDTCR", 0x41).sfr("EEAR", 0x3E).sfr("EEDR", 0x3D).sfr("EECR", 0x3C);
        int v = 0;
        b.vector("RESET", v++).vector("INT0", v++).vector("INT1", v++)
         .vector("TIMER1_CAPT", v++).vector("TIMER1_COMPA", v++).vector("TIMER1_OVF", v++)
         .vector("TIMER0_OVF", v++).vector("UART_RX", v++).vector("UART_UDRE", v++)
         .vector("UART_TX", v++).vector("ANA_COMP", v++);
        return b.build();
    }

    private static Chip buildAt90s8515() {
        Chip.Builder b = new Chip.Builder("AT90S8515")
                .family(ChipFamily.AT90)
                .flash(8192).sram(512).eeprom(512).ramStart(0x60).maxClock(8_000_000L)
                .mul(false).movw(false).enhancedMul(false).jmpCall(false)
                .peripherals(Peripheral.GPIO, Peripheral.UART, Peripheral.SPI, Peripheral.TIMER,
                        Peripheral.PWM, Peripheral.WDT, Peripheral.EEPROM, Peripheral.ANALOG_COMP);
        core(b, 0x5F, 0x5D, 0x5E);
        ports(b, "A", 0x39, 0x3A, 0x3B);
        ports(b, "B", 0x36, 0x37, 0x38);
        ports(b, "C", 0x33, 0x34, 0x35);
        ports(b, "D", 0x30, 0x31, 0x32);
        b.sfr("MCUCR", 0x55);
        b.sfr("UDR", 0x2C).sfr("USR", 0x2B).sfr("UCR", 0x2A).sfr("UBRR", 0x29)
         .sfr("UCSRA", 0x2B).sfr("UCSRB", 0x2A).sfr("UBRRL", 0x29);
        b.sfr("SPCR", 0x2D).sfr("SPSR", 0x2E).sfr("SPDR", 0x2F);
        b.sfr("TCCR0", 0x53).sfr("TCNT0", 0x52)
         .sfr("TCCR1A", 0x4F).sfr("TCCR1B", 0x4E).sfr("TCNT1H", 0x4D).sfr("TCNT1L", 0x4C)
         .sfr("OCR1AH", 0x4B).sfr("OCR1AL", 0x4A).sfr("OCR1BH", 0x49).sfr("OCR1BL", 0x48)
         .sfr("TIMSK", 0x59).sfr("TIFR", 0x58);
        b.sfr("WDTCR", 0x41).sfr("EEARH", 0x3F).sfr("EEARL", 0x3E).sfr("EEDR", 0x3D).sfr("EECR", 0x3C);
        int v = 0;
        b.vector("RESET", v++).vector("INT0", v++).vector("INT1", v++)
         .vector("TIMER1_CAPT", v++).vector("TIMER1_COMPA", v++).vector("TIMER1_COMPB", v++)
         .vector("TIMER1_OVF", v++).vector("TIMER0_OVF", v++).vector("SPI_STC", v++)
         .vector("UART_RX", v++).vector("UART_UDRE", v++).vector("UART_TX", v++)
         .vector("ANA_COMP", v++);
        return b.build();
    }

    private static Chip buildAt90s4433() {
        Chip.Builder b = new Chip.Builder("AT90S4433")
                .family(ChipFamily.AT90)
                .flash(4096).sram(128).eeprom(256).ramStart(0x60).maxClock(8_000_000L)
                .mul(false).movw(false).enhancedMul(false).jmpCall(false)
                .peripherals(Peripheral.GPIO, Peripheral.UART, Peripheral.SPI, Peripheral.ADC,
                        Peripheral.TIMER, Peripheral.PWM, Peripheral.WDT, Peripheral.EEPROM,
                        Peripheral.ANALOG_COMP);
        core(b, 0x5F, 0x5D, -1);
        ports(b, "B", 0x36, 0x37, 0x38);
        ports(b, "C", 0x33, 0x34, 0x35);
        ports(b, "D", 0x30, 0x31, 0x32);
        b.sfr("MCUCR", 0x55);
        b.sfr("UDR", 0x2C).sfr("UCSRA", 0x2B).sfr("UCSRB", 0x2A).sfr("UBRR", 0x29).sfr("UBRRHI", 0x40)
         .sfr("UBRRL", 0x29);
        b.sfr("SPCR", 0x2D).sfr("SPSR", 0x2E).sfr("SPDR", 0x2F);
        b.sfr("ADMUX", 0x27).sfr("ADCSRA", 0x26).sfr("ADCH", 0x25).sfr("ADCL", 0x24);
        b.sfr("TCCR0", 0x53).sfr("TCNT0", 0x52)
         .sfr("TCCR1A", 0x4F).sfr("TCCR1B", 0x4E).sfr("TCNT1H", 0x4D).sfr("TCNT1L", 0x4C)
         .sfr("OCR1AH", 0x4B).sfr("OCR1AL", 0x4A).sfr("TIMSK", 0x59).sfr("TIFR", 0x58);
        b.sfr("WDTCR", 0x41).sfr("EEARL", 0x3E).sfr("EEDR", 0x3D).sfr("EECR", 0x3C);
        int v = 0;
        b.vector("RESET", v++).vector("INT0", v++).vector("INT1", v++)
         .vector("TIMER1_CAPT", v++).vector("TIMER1_COMPA", v++).vector("TIMER1_OVF", v++)
         .vector("TIMER0_OVF", v++).vector("SPI_STC", v++).vector("UART_RX", v++)
         .vector("UART_UDRE", v++).vector("UART_TX", v++).vector("ADC", v++)
         .vector("EE_RDY", v++).vector("ANA_COMP", v++);
        return b.build();
    }

    /** Load and register additional chips from a JSON document. */
    @SuppressWarnings("unchecked")
    public static void registerFromJson(Object root) {
        if (!(root instanceof Map)) return;
        Object chipsNode = ((Map<String, Object>) root).get("chips");
        if (!(chipsNode instanceof List)) return;
        for (Object o : (List<Object>) chipsNode) {
            if (!(o instanceof Map)) continue;
            Map<String, Object> m = (Map<String, Object>) o;
            String name = String.valueOf(m.get("name"));
            Chip.Builder b = new Chip.Builder(name);
            String fam = String.valueOf(m.getOrDefault("family", "ATMEGA")).toUpperCase();
            try { b.family(ChipFamily.valueOf(fam)); } catch (IllegalArgumentException ignore) {}
            b.flash(intOf(m.get("flash"))).sram(intOf(m.get("sram")))
             .eeprom(intOf(m.get("eeprom"))).ramStart(m.containsKey("ramStart") ? intOf(m.get("ramStart")) : 0x60);
            Object sfrs = m.get("sfr");
            if (sfrs instanceof Map) {
                for (Map.Entry<String, Object> e : ((Map<String, Object>) sfrs).entrySet()) {
                    b.sfr(e.getKey(), intOf(e.getValue()));
                }
            }
            Object vecs = m.get("vectors");
            if (vecs instanceof Map) {
                for (Map.Entry<String, Object> e : ((Map<String, Object>) vecs).entrySet()) {
                    b.vector(e.getKey(), intOf(e.getValue()));
                }
            }
            register(b.build());
        }
    }

    private static int intOf(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        if (o instanceof String) {
            String s = ((String) o).trim();
            try {
                if (s.startsWith("0x") || s.startsWith("0X")) return Integer.parseInt(s.substring(2), 16);
                return Integer.parseInt(s);
            } catch (NumberFormatException e) { return 0; }
        }
        return 0;
    }
}
