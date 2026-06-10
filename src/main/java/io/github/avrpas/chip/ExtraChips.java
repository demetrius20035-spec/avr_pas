package io.github.avrpas.chip;

import java.util.function.Consumer;

/**
 * Registration point for additional chips beyond the hand-verified priority
 * set in {@link ChipRegistry}. This is the easiest place for community
 * contributions: add a {@code build...()} method and register it here.
 *
 * <p>Each chip only needs enough detail for the features you intend to use.
 * Missing SFRs simply cause a clear "unsupported on this chip" diagnostic
 * rather than incorrect code.</p>
 */
final class ExtraChips {

    private ExtraChips() {}

    static void register(Consumer<Chip> sink) {
        sink.accept(buildAtmega168());
        sink.accept(buildAtmega48());
        sink.accept(buildAtmega88());
        sink.accept(buildAtmega644());
        sink.accept(buildAtmega2560());
        sink.accept(buildAttiny84());
        sink.accept(buildAttiny45());
        sink.accept(buildAttiny25());
        sink.accept(buildAt90s1200());
    }

    // The 48/88/168/328 share the same register layout; only memory sizes differ.
    private static Chip megaPinCompatible(String name, int flash, int sram, int eeprom, long clk) {
        Chip.Builder b = new Chip.Builder(name)
                .family(ChipFamily.ATMEGA)
                .flash(flash).sram(sram).eeprom(eeprom).ramStart(0x100).maxClock(clk)
                .mul(true).movw(true).enhancedMul(true).jmpCall(flash > 8192)
                .peripherals(Peripheral.GPIO, Peripheral.UART, Peripheral.SPI, Peripheral.TWI,
                        Peripheral.ADC, Peripheral.TIMER, Peripheral.PWM, Peripheral.WDT,
                        Peripheral.EEPROM, Peripheral.ANALOG_COMP);
        b.sfr("SREG", 0x5F).sfr("SPL", 0x5D).sfr("SPH", 0x5E).sfr("MCUCR", 0x55);
        b.sfr("PINB", 0x23).sfr("DDRB", 0x24).sfr("PORTB", 0x25);
        b.sfr("PINC", 0x26).sfr("DDRC", 0x27).sfr("PORTC", 0x28);
        b.sfr("PIND", 0x29).sfr("DDRD", 0x2A).sfr("PORTD", 0x2B);
        b.sfr("UDR0", 0xC6).sfr("UCSR0A", 0xC0).sfr("UCSR0B", 0xC1).sfr("UCSR0C", 0xC2)
         .sfr("UBRR0L", 0xC4).sfr("UBRR0H", 0xC5)
         .sfr("UDR", 0xC6).sfr("UCSRA", 0xC0).sfr("UCSRB", 0xC1).sfr("UCSRC", 0xC2)
         .sfr("UBRRL", 0xC4).sfr("UBRRH", 0xC5);
        b.sfr("SPCR", 0x4C).sfr("SPSR", 0x4D).sfr("SPDR", 0x4E);
        b.sfr("TWBR", 0xB8).sfr("TWSR", 0xB9).sfr("TWAR", 0xBA).sfr("TWDR", 0xBB).sfr("TWCR", 0xBC);
        b.sfr("ADMUX", 0x7C).sfr("ADCSRA", 0x7A).sfr("ADCSRB", 0x7B).sfr("ADCH", 0x79).sfr("ADCL", 0x78);
        b.sfr("TCCR0A", 0x44).sfr("TCCR0B", 0x45).sfr("TCNT0", 0x46).sfr("OCR0A", 0x47).sfr("OCR0B", 0x48)
         .sfr("TCCR1A", 0x80).sfr("TCCR1B", 0x81).sfr("TCNT1L", 0x84).sfr("TCNT1H", 0x85)
         .sfr("OCR1AL", 0x88).sfr("OCR1AH", 0x89).sfr("OCR1BL", 0x8A).sfr("OCR1BH", 0x8B)
         .sfr("TCCR2A", 0xB0).sfr("TCCR2B", 0xB1).sfr("TCNT2", 0xB2).sfr("OCR2A", 0xB3).sfr("OCR2B", 0xB4);
        b.sfr("WDTCSR", 0x60).sfr("EEARH", 0x42).sfr("EEARL", 0x41).sfr("EEDR", 0x40).sfr("EECR", 0x3F);
        int v = 0;
        b.vector("RESET", v++).vector("INT0", v++).vector("INT1", v++)
         .vector("PCINT0", v++).vector("PCINT1", v++).vector("PCINT2", v++).vector("WDT", v++)
         .vector("TIMER2_COMPA", v++).vector("TIMER2_COMPB", v++).vector("TIMER2_OVF", v++)
         .vector("TIMER1_CAPT", v++).vector("TIMER1_COMPA", v++).vector("TIMER1_COMPB", v++)
         .vector("TIMER1_OVF", v++).vector("TIMER0_COMPA", v++).vector("TIMER0_COMPB", v++)
         .vector("TIMER0_OVF", v++).vector("SPI_STC", v++).vector("USART_RX", v++)
         .vector("USART_UDRE", v++).vector("USART_TX", v++).vector("ADC", v++)
         .vector("EE_READY", v++).vector("ANALOG_COMP", v++).vector("TWI", v++).vector("SPM_READY", v++);
        return b.build();
    }

    private static Chip buildAtmega48()  { return megaPinCompatible("ATmega48",  4096,  512,  256, 20_000_000L); }
    private static Chip buildAtmega88()  { return megaPinCompatible("ATmega88",  8192,  1024, 512, 20_000_000L); }
    private static Chip buildAtmega168() { return megaPinCompatible("ATmega168", 16384, 1024, 512, 20_000_000L); }

    private static Chip buildAtmega644() {
        Chip.Builder b = new Chip.Builder("ATmega644")
                .family(ChipFamily.ATMEGA)
                .flash(65536).sram(4096).eeprom(2048).ramStart(0x100).maxClock(20_000_000L)
                .mul(true).movw(true).enhancedMul(true).jmpCall(true)
                .peripherals(Peripheral.GPIO, Peripheral.UART, Peripheral.SPI, Peripheral.TWI,
                        Peripheral.ADC, Peripheral.TIMER, Peripheral.PWM, Peripheral.WDT,
                        Peripheral.EEPROM, Peripheral.ANALOG_COMP);
        b.sfr("SREG", 0x5F).sfr("SPL", 0x5D).sfr("SPH", 0x5E);
        b.sfr("PINA", 0x20).sfr("DDRA", 0x21).sfr("PORTA", 0x22);
        b.sfr("PINB", 0x23).sfr("DDRB", 0x24).sfr("PORTB", 0x25);
        b.sfr("PINC", 0x26).sfr("DDRC", 0x27).sfr("PORTC", 0x28);
        b.sfr("PIND", 0x29).sfr("DDRD", 0x2A).sfr("PORTD", 0x2B);
        b.sfr("UDR0", 0xC6).sfr("UCSR0A", 0xC0).sfr("UCSR0B", 0xC1).sfr("UCSR0C", 0xC2)
         .sfr("UBRR0L", 0xC4).sfr("UBRR0H", 0xC5)
         .sfr("UDR", 0xC6).sfr("UCSRA", 0xC0).sfr("UCSRB", 0xC1).sfr("UCSRC", 0xC2)
         .sfr("UBRRL", 0xC4).sfr("UBRRH", 0xC5);
        b.sfr("ADMUX", 0x7C).sfr("ADCSRA", 0x7A).sfr("ADCSRB", 0x7B).sfr("ADCH", 0x79).sfr("ADCL", 0x78);
        b.sfr("WDTCSR", 0x60).sfr("EEARH", 0x42).sfr("EEARL", 0x41).sfr("EEDR", 0x40).sfr("EECR", 0x3F);
        b.vector("RESET", 0).vector("TIMER0_OVF", 11).vector("USART0_RX", 18);
        return b.build();
    }

    private static Chip buildAtmega2560() {
        Chip.Builder b = new Chip.Builder("ATmega2560")
                .family(ChipFamily.ATMEGA)
                .flash(262144).sram(8192).eeprom(4096).ramStart(0x200).maxClock(16_000_000L)
                .mul(true).movw(true).enhancedMul(true).jmpCall(true).elpm(true)
                .peripherals(Peripheral.GPIO, Peripheral.UART, Peripheral.SPI, Peripheral.TWI,
                        Peripheral.ADC, Peripheral.TIMER, Peripheral.PWM, Peripheral.WDT,
                        Peripheral.EEPROM, Peripheral.ANALOG_COMP);
        b.sfr("SREG", 0x5F).sfr("SPL", 0x5D).sfr("SPH", 0x5E);
        b.sfr("PINA", 0x20).sfr("DDRA", 0x21).sfr("PORTA", 0x22);
        b.sfr("PINB", 0x23).sfr("DDRB", 0x24).sfr("PORTB", 0x25);
        b.sfr("UDR0", 0xC6).sfr("UCSR0A", 0xC0).sfr("UCSR0B", 0xC1).sfr("UCSR0C", 0xC2)
         .sfr("UBRR0L", 0xC4).sfr("UBRR0H", 0xC5)
         .sfr("UDR", 0xC6).sfr("UCSRA", 0xC0).sfr("UCSRB", 0xC1).sfr("UCSRC", 0xC2)
         .sfr("UBRRL", 0xC4).sfr("UBRRH", 0xC5);
        b.vector("RESET", 0);
        return b.build();
    }

    private static Chip tinyAdcCore(String name, int flash, int sram, int eeprom) {
        Chip.Builder b = new Chip.Builder(name)
                .family(ChipFamily.ATTINY)
                .flash(flash).sram(sram).eeprom(eeprom).ramStart(0x60).maxClock(20_000_000L)
                .mul(false).movw(true).enhancedMul(false).jmpCall(false)
                .peripherals(Peripheral.GPIO, Peripheral.ADC, Peripheral.SPI, Peripheral.TIMER,
                        Peripheral.PWM, Peripheral.WDT, Peripheral.EEPROM, Peripheral.ANALOG_COMP);
        b.sfr("SREG", 0x5F).sfr("SPL", 0x5D).sfr("SPH", 0x5E).sfr("MCUCR", 0x55);
        b.sfr("PINB", 0x36).sfr("DDRB", 0x37).sfr("PORTB", 0x38);
        b.sfr("ADMUX", 0x27).sfr("ADCSRA", 0x26).sfr("ADCSRB", 0x23).sfr("ADCH", 0x25).sfr("ADCL", 0x24);
        b.sfr("TCCR0A", 0x4A).sfr("TCCR0B", 0x53).sfr("TCNT0", 0x52).sfr("OCR0A", 0x49).sfr("OCR0B", 0x48);
        b.sfr("WDTCR", 0x41).sfr("EEARL", 0x3E).sfr("EEDR", 0x3D).sfr("EECR", 0x3C);
        b.vector("RESET", 0).vector("INT0", 1).vector("TIMER0_OVF", 5);
        return b.build();
    }

    private static Chip buildAttiny25() { return tinyAdcCore("ATtiny25", 2048, 128, 128); }
    private static Chip buildAttiny45() { return tinyAdcCore("ATtiny45", 4096, 256, 256); }

    private static Chip buildAttiny84() {
        Chip.Builder b = new Chip.Builder("ATtiny84")
                .family(ChipFamily.ATTINY)
                .flash(8192).sram(512).eeprom(512).ramStart(0x60).maxClock(20_000_000L)
                .mul(false).movw(true).enhancedMul(false).jmpCall(false)
                .peripherals(Peripheral.GPIO, Peripheral.ADC, Peripheral.SPI, Peripheral.TIMER,
                        Peripheral.PWM, Peripheral.WDT, Peripheral.EEPROM, Peripheral.ANALOG_COMP);
        b.sfr("SREG", 0x5F).sfr("SPL", 0x5D).sfr("SPH", 0x5E).sfr("MCUCR", 0x55);
        b.sfr("PINA", 0x39).sfr("DDRA", 0x3A).sfr("PORTA", 0x3B);
        b.sfr("PINB", 0x36).sfr("DDRB", 0x37).sfr("PORTB", 0x38);
        b.sfr("ADMUX", 0x27).sfr("ADCSRA", 0x26).sfr("ADCSRB", 0x23).sfr("ADCH", 0x25).sfr("ADCL", 0x24);
        b.sfr("TCCR0A", 0x50).sfr("TCCR0B", 0x53).sfr("TCNT0", 0x52).sfr("OCR0A", 0x56).sfr("OCR0B", 0x5C);
        b.sfr("WDTCSR", 0x41).sfr("EEARL", 0x3E).sfr("EEDR", 0x3D).sfr("EECR", 0x3C);
        b.vector("RESET", 0).vector("INT0", 1).vector("TIMER0_OVF", 6);
        return b.build();
    }

    private static Chip buildAt90s1200() {
        // The original AVR: no SRAM data space for variables, reduced core.
        Chip.Builder b = new Chip.Builder("AT90S1200")
                .family(ChipFamily.AT90)
                .flash(1024).sram(0).eeprom(64).ramStart(0x60).maxClock(12_000_000L)
                .mul(false).movw(false).enhancedMul(false).jmpCall(false)
                .peripherals(Peripheral.GPIO, Peripheral.TIMER, Peripheral.WDT,
                        Peripheral.EEPROM, Peripheral.ANALOG_COMP);
        b.sfr("SREG", 0x5F);
        b.sfr("PINB", 0x36).sfr("DDRB", 0x37).sfr("PORTB", 0x38);
        b.sfr("PIND", 0x30).sfr("DDRD", 0x31).sfr("PORTD", 0x32);
        b.sfr("MCUCR", 0x55).sfr("TCCR0", 0x53).sfr("TCNT0", 0x52).sfr("WDTCR", 0x41);
        b.sfr("EEAR", 0x3E).sfr("EEDR", 0x3D).sfr("EECR", 0x3C);
        b.vector("RESET", 0).vector("INT0", 1).vector("TIMER0_OVF", 2).vector("ANA_COMP", 3);
        return b.build();
    }
}
