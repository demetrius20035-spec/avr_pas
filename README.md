# pascal2asm-avr

A console transpiler that converts a practical subset of PASCAL into GNU
`avr-as` / `avr-gcc`-compatible AVR assembly for Atmel AT90/ATtiny/ATmega
8-bit micro-controllers.

The transpiler is chip-aware: it knows each target's memory sizes, instruction
set capabilities (hardware multiplier, `movw`, `jmp`/`call`), special-function
register (SFR) map, and interrupt vector table, and tailors the generated
assembly accordingly.

## Features

- PASCAL-to-AVR-assembly translation producing `avr-as`/`avr-gcc`-compatible `.s` output.
- 19 supported chips across the ATmega, ATtiny and classic AT90S families (see [docs/CHIPS.md](docs/CHIPS.md)).
- Per-chip code generation: hardware-multiply inlining, `movw`, and `jmp`/`call` vs `rjmp`/`rcall` are selected from the target's capabilities.
- SFRs (`PORTB`, `UCSRA`, `ADMUX`, ...) exposed as predefined byte variables, so idiomatic register code such as `PORTB := PORTB or (1 shl 3)` works directly.
- A library of peripheral built-ins: GPIO bit helpers, delays, UART/USART, ADC, SPI, TWI/I2C, timers, PWM and watchdog.
- Interrupt service routines written as ordinary PASCAL procedures bound to a named vector.
- Inline `asm ... end` blocks for hand-written assembly.
- Conditional compilation via `{$IFDEF}`/`{$IFNDEF}`/`{$ELSE}`/`{$ENDIF}`/`{$DEFINE}`/`{$UNDEF}` with predefined chip/family/`AVR` symbols.
- Optional listing (`.lst`) and memory-map (`.map`) outputs.
- JSON configuration files and a JSON chip database for adding chips without recompiling.
- Zero runtime dependencies; a single runnable jar.

## Requirements

- JDK 8 or newer (the project is built with Java 8 source/target).
- Apache Maven (to build the jar).
- Optional, to assemble and link the generated assembly: an AVR toolchain
  (`avr-gcc`, `avr-libc`, `binutils-avr`). On Debian/Ubuntu:

  ```sh
  sudo apt-get install gcc-avr avr-libc binutils-avr
  ```

## Building

```sh
mvn package
```

This produces a runnable jar at `target/pascal2asm-avr.jar` (Main-Class
`io.github.avrpas.Main`). There are no runtime dependencies.

## Quick start

Transpile a PASCAL program to AVR assembly:

```sh
java -jar target/pascal2asm-avr.jar \
     --chip ATMega8 \
     --input program.pas \
     --output program.s \
     --clock 8MHz \
     --optimize speed \
     --fuses lfuse=0xE1,hfuse=0xD9
```

Then assemble and link the generated `.s` with the AVR toolchain:

```sh
avr-gcc -mmcu=atmega8 -nostartfiles -o out.elf program.s
```

The generated assembly defines its own reset/startup code and interrupt vector
table, which is why `-nostartfiles` is used. The header comment of every
generated file contains the matching `avr-gcc` command line for its target.

## CLI reference

```
pascal2asm-avr - PASCAL to AVR assembly transpiler

Usage:
  java -jar pascal2asm-avr.jar --chip <CHIP> --input <file.pas> [options]

Required:
  --chip <name>        Target MCU (e.g. ATmega8, ATtiny85, AT90S2313)
  --input <file>       PASCAL source file (.pas)

Output:
  --output <file>      Assembly output (.s); default: <input>.s
  --listing [file]     Emit a listing (.lst)
  --map [file]         Emit a memory map (.map)

Target configuration:
  --clock <freq>       CPU clock, e.g. 8MHz, 16000000, 1.0MHz (sets F_CPU)
  --fuses <spec>       Fuse settings, e.g. lfuse=0xE1,hfuse=0xD9
  --uart-baud <n>      Default UART baud rate (default 9600)
  --adc-bits <n>       ADC resolution in bits (default 10)

Code generation:
  --optimize <goal>    none | size | speed (default none)
  --align-data         Word-align data section
  --no-comments        Omit comments from the generated assembly
  --debug              Emit debug annotations (source lines)
  --define <NAME>      Define a symbol for {$IFDEF} conditional compilation
  --no-stubs           Treat unsupported features as errors (no stubs)

Misc:
  --config <file>      Load options from a JSON config file
  --chip-db <file>     Load additional chip definitions (JSON)
  --list-chips         List all supported chips and exit
  --chip-info <name>   Print hardware details for a chip and exit
  --verbose, -v        Verbose transformation log
  --help               Show this help
  --version            Show version
```

Notes:

- Both `--opt value` and `--opt=value` forms are accepted.
- A bare positional argument is treated as the input file (or, if input is
  already set, the output file).
- Command-line options override any values loaded from a `--config` file.
- `--comments` is also accepted (the inverse of `--no-comments`); comments are
  on by default.

## Configuration file

Options can be supplied in a JSON file and loaded with `--config`. Recognised
keys mirror the long-form CLI options; any value can still be overridden on the
command line.

```json
{
  "chip": "ATmega8",
  "input": "program.pas",
  "output": "program.s",
  "clock": "8MHz",
  "optimize": "speed",
  "fuses": { "lfuse": "0xE1", "hfuse": "0xD9" },
  "uartBaud": 19200,
  "adcBits": 10,
  "comments": true,
  "defines": ["DEBUG", "BOARD_V2"]
}
```

Other accepted keys: `debug` (boolean), `alignData` (boolean), `listing`
(boolean), `map` (boolean), `chipDb` (path to a JSON chip database).

## Supported PASCAL subset

- Program header: `program Name;` (with an optional, ignored parameter list).
- Declaration sections: `const`, `var`, and `type` (the latter is parsed but
  not yet applied). A `uses` clause is parsed and ignored with a warning.
- Types: `byte` (unsigned 8-bit), `shortint` (signed 8-bit), `word` (unsigned
  16-bit), `integer` (signed 16-bit), `boolean`, `char`, and
  `array[a..b] of T`.
- Procedures and functions, with value parameters and by-reference (`var`)
  parameters.
- Statements: assignment; `if`/`then`/`else`; `while`/`do`; `repeat`/`until`;
  `for`/`to`/`downto`/`do`; `begin`/`end` compound statements; procedure/function
  calls.
- Expressions:
  - Arithmetic: `+`, `-`, `*`, `div`, `mod`.
  - Bitwise: `and`, `or`, `xor`, `not`, `shl`, `shr`.
  - Comparisons: `=`, `<>`, `<`, `<=`, `>`, `>=`.
  - Literals: integer, char (`'A'`), string (placed in flash), `True`/`False`.
- Inline assembly: `asm ... end` blocks, emitted verbatim.
- Interrupt routines: `procedure Name; interrupt VECTOR;` binds a procedure to a
  named interrupt vector of the target chip.

Real numbers and `case` statements are recognised by the parser but produce an
"unsupported" warning and emit a stub (or `0`). See [Limitations](#limitations).

For a detailed mapping of each construct to the AVR instructions it produces,
see [docs/PASCAL_MAPPING.md](docs/PASCAL_MAPPING.md).

## Peripheral built-ins

Each built-in requires its peripheral to exist on the target chip; otherwise a
clear diagnostic and a marked stub are emitted (use `--no-stubs` to make these
errors). SFR register names are *not* built-ins — they are exposed as predefined
byte variables.

| Name             | Signature                                   | Required peripheral |
| ---------------- | ------------------------------------------- | ------------------- |
| `Sei`            | `procedure`                                 | -                   |
| `Cli`            | `procedure`                                 | -                   |
| `Nop`            | `procedure`                                 | -                   |
| `Sleep`          | `procedure`                                 | -                   |
| `Wdr`            | `procedure`                                 | WDT                 |
| `SetBit`         | `procedure(reg, bit: byte)`                 | -                   |
| `ClearBit`       | `procedure(reg, bit: byte)`                 | -                   |
| `ToggleBit`      | `procedure(reg, bit: byte)`                 | -                   |
| `TestBit`        | `function(reg, bit: byte): boolean`         | -                   |
| `DelayMs`        | `procedure(ms: word)`                       | -                   |
| `DelayUs`        | `procedure(us: word)`                       | -                   |
| `UartInit`       | `procedure`                                 | UART                |
| `UartTransmit`   | `procedure(b: byte)`                        | UART                |
| `UartReceive`    | `function: byte`                            | UART                |
| `UartWriteStr`   | `procedure(ptr: word)`                      | UART                |
| `UartReady`      | `function: boolean`                         | UART                |
| `AdcInit`        | `procedure`                                 | ADC                 |
| `AdcRead`        | `function(channel: byte): word`             | ADC                 |
| `SpiMasterInit`  | `procedure`                                 | SPI                 |
| `SpiTransfer`    | `function(b: byte): byte`                   | SPI                 |
| `TwiInit`        | `procedure`                                 | TWI                 |
| `TwiStart`       | `procedure`                                 | TWI                 |
| `TwiStop`        | `procedure`                                 | TWI                 |
| `TwiWrite`       | `procedure(b: byte)`                        | TWI                 |
| `TwiReadAck`     | `function: byte`                            | TWI                 |
| `TwiReadNack`    | `function: byte`                            | TWI                 |
| `Timer0Init`     | `procedure(prescaler: byte)`                | TIMER               |
| `Timer1Init`     | `procedure(prescaler: byte)`                | TIMER               |
| `PwmInit`        | `procedure(prescaler: byte)`                | PWM                 |
| `WdtEnable`      | `procedure(timeout: byte)`                  | WDT                 |
| `WdtDisable`     | `procedure`                                 | WDT                 |
| `WdtReset`       | `procedure`                                 | WDT                 |

`SetBit`/`ClearBit`/`ToggleBit`/`TestBit` expect their first argument to be an
SFR (e.g. `PORTB`); for constant bit numbers on bit-addressable registers they
compile to single `sbi`/`cbi`/`sbis` instructions, otherwise to a
read-modify-write sequence. `UartWriteStr` takes the flash address of a string
(pass a string literal). `UartInit`/`AdcInit`/`TwiInit` derive register values
from `--clock`, `--uart-baud` and `--adc-bits`.

## Conditional compilation

The preprocessor implements PASCAL-style conditional compilation inside braces:

```pascal
{$DEFINE NAME}     {$UNDEF NAME}
{$IFDEF NAME} ... {$ELSE} ... {$ENDIF}
{$IFNDEF NAME} ... {$ENDIF}
```

Inactive lines are blanked (not removed) so that reported line numbers stay
aligned with the original source.

Predefined symbols always include:

- The target chip name, upper-cased (e.g. `ATMEGA8`, `ATTINY85`, `AT90S2313`).
- The target family: `ATMEGA`, `ATTINY` or `AT90`.
- `AVR` (always defined).

Plus any symbols supplied with `--define NAME` or the `defines` config key.
Symbol matching is case-insensitive.

## Examples

The [`examples/`](examples) directory contains runnable programs; the included
`examples/build_examples.sh` script transpiles all of them and, when an AVR
toolchain is present, assembles and links each one.

| File                          | Demonstrates |
| ----------------------------- | ------------ |
| `blink.pas`                   | Classic LED blink on PB0: direct SFR access, `DelayMs`, a `while` loop and bitwise GPIO. |
| `uart_echo.pas`               | UART receive/transmit built-ins, a value-returning function, and 8-bit arithmetic. |
| `timer_interrupt.pas`         | A Timer0 overflow interrupt service routine, global state, and the `SetBit`/`ToggleBit` built-ins. |
| `adc_read.pas`                | ADC sampling into an `array`, 16-bit arithmetic, and unsigned `div`. |
| `conditional.pas`             | `{$IFDEF}`/`{$IFNDEF}` conditional compilation using the predefined chip/family/`AVR` symbols. |

Run the whole set:

```sh
./examples/build_examples.sh
```

## Supported chips

19 chips are supported across three families. See [docs/CHIPS.md](docs/CHIPS.md)
for the full table (flash/SRAM/EEPROM sizes, hardware multiply, `jmp`/`call`,
notable peripherals) and instructions for adding a new chip.

ATmega: ATmega8, ATmega16, ATmega32, ATmega48, ATmega88, ATmega168, ATmega328P,
ATmega644, ATmega2560.
ATtiny: ATtiny13, ATtiny25, ATtiny45, ATtiny85, ATtiny84, ATtiny2313.
AT90S (classic): AT90S1200, AT90S2313, AT90S4433, AT90S8515.

Use `--list-chips` to print the list, or `--chip-info <name>` for a single
chip's details.

## Project layout

```
io.github.avrpas
├── Main            CLI entry point (argument handling, --help/--version/--list-chips/--chip-info)
├── Transpiler      Pipeline driver (preprocess -> lex -> parse -> sema -> codegen -> output)
├── lexer           Tokenizer (Lexer, Token, TokenType)
├── parser          Preprocessor (conditional compilation) and recursive-descent Parser
├── ast             AST node types, Operators and the AstVisitor
├── sema            SemanticAnalyzer, symbol tables, the Type system and Builtins
├── chip            Chip model, ChipRegistry, ExtraChips, peripherals and families
├── codegen         CodeGenerator, BuiltinCodegen and the AsmBuilder
├── config          CliParser, Config, ConfigLoader and OptimizationGoal
├── diag            Diagnostic, DiagnosticReporter, SourceLocation, TranspilerException
└── util            A small dependency-free JSON parser
```

The pipeline stages are: Preprocessor -> Lexer -> Parser (which also acts as the
syntax validator) -> SemanticAnalyzer -> CodeGenerator -> output (`.s`, optional
`.lst` listing and `.map` memory map).

## Limitations

- **Static, non-reentrant storage.** Local variables and parameters are
  allocated to fixed SRAM addresses (`.lcomm`), not a stack frame. As a result,
  **recursion is not supported** and routines are not reentrant.
- **16-bit maximum integers.** The largest integer types are `word`/`integer`
  (16-bit). Expressions evaluate through a 16-bit `r25:r24` accumulator.
- **No real/floating-point.** `real` is parsed but unsupported; literals become
  `0` and a warning is emitted.
- **No `case` statement yet.** It is parsed but emits a no-op stub with a warning.
- `type` declarations and `uses` clauses are parsed but not applied.
- Only simple array variables can be indexed (not arbitrary expressions).

With `--no-stubs`, unsupported features become hard errors instead of stubs.

## Contributing

Contributions are welcome. See [docs/DEVELOPER.md](docs/DEVELOPER.md) for the
architecture, how to add a built-in or a chip, the SFR-as-variable and
16-bit accumulator conventions, the static storage model, and coding/PR
conventions.

## License

MIT. See [LICENSE](LICENSE).
