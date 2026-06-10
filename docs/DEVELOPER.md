# Developer Guide

This document describes the internal architecture of **pascal2asm-avr** for
contributors. The project is intentionally modular so that new chips, built-in
peripherals and language features can be added without touching unrelated code.

## Build & test

```sh
mvn package        # compile + run tests + build target/pascal2asm-avr.jar
mvn test           # run the JUnit suite only
./examples/build_examples.sh   # transpile + (if avr-gcc present) link all examples
```

Java 8 source/target. The only dependency is JUnit (test scope); the runtime
has **zero** dependencies — JSON parsing, CLI parsing and the chip database are
all implemented in-tree.

## Pipeline

The transpiler is a classic multi-stage compiler. Each stage feeds a shared
`DiagnosticReporter`; a stage that depends on the output of a stage that
produced errors is skipped.

```
source.pas
   │
   ▼  parser.Preprocessor        {$IFDEF}/{$DEFINE} conditional compilation
   ▼  lexer.Lexer                text → List<Token>
   ▼  parser.Parser              tokens → ast.Program   (also the syntax validator)
   ▼  sema.SemanticAnalyzer      name resolution, type checks, storage layout,
   │                             chip-feature validation, constant folding
   ▼  codegen.CodeGenerator      AST → AVR assembly (with codegen.BuiltinCodegen)
   ▼
program.s  (+ optional .lst listing, .map memory map)
```

`Transpiler` orchestrates the stages; `Main` handles the CLI (`--list-chips`,
`--chip-info`, `--help`, `--version`) and delegates to `Transpiler`.

## Package responsibilities

| Package | Responsibility |
|---------|----------------|
| `io.github.avrpas` | `Main` (CLI dispatch), `Transpiler` (pipeline orchestration, output writers) |
| `lexer` | `Token`, `TokenType`, `Lexer` (case-insensitive keywords, `{ }`/`(* *)`/`//` comments, raw `asm` capture) |
| `parser` | `Parser` (recursive descent, panic-mode recovery), `Preprocessor` (conditional compilation) |
| `ast` | Node classes + the `AstVisitor<R>` interface and `Operators` enums |
| `sema` | `Type`, `Symbol`, `SymbolTable`, `Builtins` (built-in table), `SemanticAnalyzer` |
| `chip` | `Chip` (+`Builder`), `ChipFamily`, `Peripheral`, `ChipRegistry`, `ExtraChips` |
| `codegen` | `AsmBuilder` (section assembler), `CodeGenerator`, `BuiltinCodegen` |
| `config` | `Config`, `CliParser`, `ConfigLoader`, `OptimizationGoal` |
| `diag` | `Diagnostic`, `DiagnosticReporter`, `SourceLocation`, `TranspilerException` |
| `util` | `Json` (minimal dependency-free JSON reader/writer) |

## AST and the visitor

Every node extends `ast.Node` (which carries a `SourceLocation`). Expressions
extend `ast.Expr` (they carry a resolved `Type` and an optional folded
`constValue`); statements extend `ast.Stmt`. Traversal is via
`AstVisitor<R>`: `SemanticAnalyzer implements AstVisitor<Type>`. The code
generator uses `instanceof` dispatch rather than the visitor because it needs
fine-grained control over evaluation order and register usage.

## Design: SFRs as predefined variables

Rather than inventing register-access syntax, the analyzer injects every chip
special-function register from `Chip.sfrMap()` as a predefined `byte` variable
(`Symbol.isSfr = true`, with `sfrAddress`). This makes idiomatic register code
work directly:

```pascal
PORTB := PORTB or (1 shl 3);
```

The code generator chooses `in`/`out` for addresses in the direct I/O range
(`0x20`–`0x5F`), `sbi`/`cbi` for bit-addressable addresses (`0x20`–`0x3F`), and
`lds`/`sts` for extended I/O — see `Chip.isDirectIo` / `isBitAddressable`.

## Code-generation conventions

- **Accumulator.** Expressions evaluate to a 16-bit value in `r25:r24` (low
  byte in `r24`). Binary operators evaluate the left operand, `push` it,
  evaluate the right into `r23:r22` (via `movw`, or two `mov`s on classic
  cores), then `pop` the left back into `r25:r24`.
- **Multiply / divide.** Multiply inlines `mul` when the chip has a hardware
  multiplier *and* `--optimize speed`; otherwise it calls libgcc's `__mulhi3`.
  Division/modulo call `__divmodhi4` (signed) or `__udivmodhi4` (unsigned).
  These helpers are provided by avr-gcc at link time.
- **Static storage.** Variables, parameters and function results are given
  fixed labels in `.bss` (`.lcomm`). There is **no stack frame**, so routines
  are *not* re-entrant (no recursion). This keeps generated code tiny and
  predictable. `var` (by-reference) parameters hold a 2-byte pointer that is
  dereferenced through `Z`.
- **Vector table.** Built from `Chip.vectorMap()`. Entry 0 is `__reset`; bound
  ISRs jump to `isr_<name>`; the rest go to `__bad_interrupt` (a bare `reti`).
  `jmp`/`rjmp` is chosen from `Chip.hasJmpCall()`.

## Adding a built-in peripheral routine

1. Add a descriptor in `sema.Builtins` (name, return type, required
   `Peripheral`, parameter types). Use `defSfr(...)` if the first argument must
   be an SFR (like `SetBit`).
2. Add a `case` in `codegen.BuiltinCodegen.emit(...)` that emits the AVR code.
   Use the `readSfr`/`writeSfr`/`ldiWrite` helpers (they resolve register
   addresses via the `Chip` and pick `in`/`out` vs `lds`/`sts`). Always emit a
   `stub(...)` + diagnostic when a needed register is absent on the target.

The semantic analyzer automatically validates argument counts/types and that
the chip provides the required peripheral.

## Adding a chip

**In code:** add a `build<Name>()` method (use the `Chip.Builder` fluent API)
and register it in `chip.ExtraChips.register(...)`. Provide `flash`, `sram`,
`eeprom`, `ramStart`, the capability flags (`mul`, `movw`, `jmpCall`, ...),
the peripheral set, the SFR map (data-space addresses), and the vector map.

**Without recompiling:** supply a JSON chip database via `--chip-db` (see
`examples/chips.example.json` and `ChipRegistry.registerFromJson`).

Only define the registers and vectors you intend to use — a missing SFR
produces a clear "unsupported on this chip" diagnostic rather than wrong code.

## Diagnostics

`DiagnosticReporter` collects `Diagnostic`s with a `Severity`
(ERROR/WARNING/INFO), a `Kind` (SYNTAX, SEMANTIC, UNSUPPORTED_CHIP_FEATURE,
UNSUPPORTED_PASCAL_FEATURE, MEMORY, CODEGEN, CONFIG, GENERAL) and a
`SourceLocation`. Prefer the most specific `Kind`, and attach the source
location so messages point at the offending line/column.

## Contributing

- Keep the runtime dependency-free.
- Match the surrounding code style; every public type has a short Javadoc.
- Add or extend a `PipelineTest` case for new language features, built-ins or
  chips. For codegen changes, prefer an assertion on the emitted assembly and,
  where practical, verify the output links with `avr-gcc`.
- One focused change per pull request; describe the chip(s)/feature(s) affected.
