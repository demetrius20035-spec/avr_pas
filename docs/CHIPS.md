# Supported chips

`pascal2asm-avr` ships with 19 hand-verified chip definitions across three
families. Each definition records the chip's memory sizes, instruction-set
capabilities (hardware multiplier, `movw`, `jmp`/`call`), special-function
register (SFR) map and interrupt vector table; the code generator reads these to
tailor its output.

Run `--list-chips` to print the names, or `--chip-info <name>` for one chip's
details.

## Chip table

| Chip       | Family | Flash    | SRAM   | EEPROM | HW multiply | jmp/call | Notable peripherals |
| ---------- | ------ | -------- | ------ | ------ | ----------- | -------- | ------------------- |
| ATmega8    | ATMEGA | 8192 B   | 1024 B | 512 B  | yes         | no       | UART, SPI, TWI, ADC, timers, PWM, WDT, EEPROM, analog comparator |
| ATmega16   | ATMEGA | 16384 B  | 1024 B | 512 B  | yes         | yes      | UART, SPI, TWI, ADC, timers, PWM, WDT, EEPROM, analog comparator |
| ATmega32   | ATMEGA | 32768 B  | 2048 B | 1024 B | yes         | yes      | UART, SPI, TWI, ADC, timers, PWM, WDT, EEPROM, analog comparator |
| ATmega48   | ATMEGA | 4096 B   | 512 B  | 256 B  | yes         | no       | UART, SPI, TWI, ADC, timers, PWM, WDT, EEPROM, analog comparator |
| ATmega88   | ATMEGA | 8192 B   | 1024 B | 512 B  | yes         | no       | UART, SPI, TWI, ADC, timers, PWM, WDT, EEPROM, analog comparator |
| ATmega168  | ATMEGA | 16384 B  | 1024 B | 512 B  | yes         | yes      | UART, SPI, TWI, ADC, timers, PWM, WDT, EEPROM, analog comparator |
| ATmega328P | ATMEGA | 32768 B  | 2048 B | 1024 B | yes         | yes      | UART, SPI, TWI, ADC, timers, PWM, WDT, EEPROM, analog comparator (extended I/O) |
| ATmega644  | ATMEGA | 65536 B  | 4096 B | 2048 B | yes         | yes      | UART, SPI, TWI, ADC, timers, PWM, WDT, EEPROM, analog comparator |
| ATmega2560 | ATMEGA | 262144 B | 8192 B | 4096 B | yes         | yes      | UART, SPI, TWI, ADC, timers, PWM, WDT, EEPROM, analog comparator (ELPM, >128 KB flash) |
| ATtiny2313 | ATTINY | 2048 B   | 128 B  | 128 B  | no          | no       | UART, SPI, timers, PWM, WDT, EEPROM, analog comparator |
| ATtiny13   | ATTINY | 1024 B   | 64 B   | 64 B   | no          | no       | ADC, timer, PWM, WDT, EEPROM, analog comparator (no SPH) |
| ATtiny85   | ATTINY | 8192 B   | 512 B  | 512 B  | no          | no       | ADC, SPI, timers, PWM, WDT, EEPROM, analog comparator |
| ATtiny84   | ATTINY | 8192 B   | 512 B  | 512 B  | no          | no       | ADC, SPI, timer, PWM, WDT, EEPROM, analog comparator |
| ATtiny45   | ATTINY | 4096 B   | 256 B  | 256 B  | no          | no       | ADC, SPI, timer, PWM, WDT, EEPROM, analog comparator |
| ATtiny25   | ATTINY | 2048 B   | 128 B  | 128 B  | no          | no       | ADC, SPI, timer, PWM, WDT, EEPROM, analog comparator |
| AT90S2313  | AT90   | 2048 B   | 128 B  | 128 B  | no          | no       | UART, timer, PWM, WDT, EEPROM, analog comparator (no SPH) |
| AT90S8515  | AT90   | 8192 B   | 512 B  | 512 B  | no          | no       | UART, SPI, timer, PWM, WDT, EEPROM, analog comparator |
| AT90S4433  | AT90   | 4096 B   | 128 B  | 256 B  | no          | no       | UART, SPI, ADC, timer, PWM, WDT, EEPROM, analog comparator (no SPH) |
| AT90S1200  | AT90   | 1024 B   | 0 B    | 64 B   | no          | no       | timer, WDT, EEPROM, analog comparator (no SRAM data space, reduced core) |

Notes:

- **HW multiply** corresponds to the presence of the `mul` instruction. The
  ATmega chips listed here also have the enhanced multiply set; the ATtiny and
  AT90S chips do not have a hardware multiplier at all.
- **jmp/call** indicates whether the part supports the 22-bit `jmp`/`call`
  instructions (used for the interrupt vector table and larger flash). Parts
  without it use `rjmp`/`rcall`. For the pin-compatible ATmega48/88/168 group
  this is enabled automatically when flash exceeds 8 KB.
- The classic AT90S parts (and ATtiny2313) lack `mul`; the AT90S parts also lack
  `movw`. The code generator falls back accordingly (see
  [PASCAL_MAPPING.md](PASCAL_MAPPING.md)).
- `AT90S1200` has no SRAM data space, so programs that need RAM-backed variables
  cannot target it.

## Adding a new chip

There are two ways to add a chip; neither requires editing the core generator.

### 1. Compile a definition into the build

Add a `build...()` method and register it in
[`ExtraChips.java`](../src/main/java/io/github/avrpas/chip/ExtraChips.java).
This is the easiest place for contributions. A chip only needs enough detail for
the features you intend to use — missing SFRs produce a clear "unsupported on
this chip" diagnostic rather than wrong code. Use the `Chip.Builder` API:

```java
Chip.Builder b = new Chip.Builder("ATmegaXX")
        .family(ChipFamily.ATMEGA)
        .flash(16384).sram(1024).eeprom(512).ramStart(0x100).maxClock(20_000_000L)
        .mul(true).movw(true).enhancedMul(true).jmpCall(true)
        .peripherals(Peripheral.GPIO, Peripheral.UART, /* ... */);
b.sfr("SREG", 0x5F).sfr("SPL", 0x5D).sfr("SPH", 0x5E);
b.sfr("PORTB", 0x25).sfr("DDRB", 0x24).sfr("PINB", 0x23);
b.vector("RESET", 0).vector("INT0", 1) /* ... */;
return b.build();
```

The hand-verified priority chips live in
[`ChipRegistry.java`](../src/main/java/io/github/avrpas/chip/ChipRegistry.java)
and serve as worked examples.

### 2. Load chips from a JSON database at runtime

Point `--chip-db <file>` at a JSON document. The definitions are parsed by
`ChipRegistry.registerFromJson` and registered alongside the built-in chips, so
you can add or override chips without recompiling.

The understood schema is:

```json
{
  "chips": [
    {
      "name": "ATmegaXX",
      "family": "ATMEGA",
      "flash": 16384,
      "sram": 1024,
      "eeprom": 512,
      "ramStart": "0x100",
      "sfr": {
        "PORTB": "0x25",
        "DDRB":  "0x24",
        "PINB":  "0x23",
        "UDR":   "0xC6"
      },
      "vectors": {
        "RESET": 0,
        "INT0":  1,
        "TIMER0_OVF": 16
      }
    }
  ]
}
```

Schema details:

- `name` (string) — the chip name used with `--chip` (matched case-insensitively).
- `family` (string) — one of `ATMEGA`, `ATTINY`, `AT90`. Defaults to `ATMEGA`
  if missing or unrecognised.
- `flash`, `sram`, `eeprom` — sizes in bytes.
- `ramStart` — first usable SRAM data address; defaults to `0x60` when omitted.
- `sfr` — a map of register name to data-space address. For classic AVR I/O
  registers this equals the datasheet I/O address + `0x20`.
- `vectors` — a map of vector name to vector number (RESET is 0).

Numbers may be given as JSON integers or as strings, in decimal or `0x` hex.
