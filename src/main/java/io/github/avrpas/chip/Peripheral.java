package io.github.avrpas.chip;

/**
 * Peripheral modules a chip may expose. The code generator and the built-in
 * runtime routines query {@link Chip#hasPeripheral(Peripheral)} before emitting
 * any peripheral-specific code, so that targeting a chip without (say) an ADC
 * produces a clear diagnostic instead of broken assembly.
 */
public enum Peripheral {
    GPIO,
    UART,   // UART or USART
    SPI,
    TWI,    // I2C / Two-Wire Interface
    ADC,
    TIMER,  // timer/counter, including PWM output compare
    PWM,
    WDT,    // watchdog timer
    EEPROM,
    ANALOG_COMP
}
