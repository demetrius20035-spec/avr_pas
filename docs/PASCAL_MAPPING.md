# PASCAL-to-AVR instruction mapping

This reference describes how the code generator
([`CodeGenerator.java`](../src/main/java/io/github/avrpas/codegen/CodeGenerator.java)
and
[`BuiltinCodegen.java`](../src/main/java/io/github/avrpas/codegen/BuiltinCodegen.java))
lowers PASCAL constructs to GNU `avr-as`-compatible assembly. Every row reflects
what the generator actually emits.

## Evaluation convention

Expressions evaluate to a 16-bit value held in **`r25:r24`** (low byte in `r24`).
For a binary operator the generator:

1. evaluates the left operand into `r25:r24`,
2. pushes it (`push r24` / `push r25`),
3. evaluates the right operand into `r25:r24`,
4. moves it to the right-operand pair `r23:r22` (`movw r22, r24`, or two `mov`
   instructions on cores without `movw`),
5. pops the left operand back into `r25:r24`,
6. applies the operator with the result left in `r25:r24`.

8-bit operands are widened to 16 bits on load: unsigned values are zero-extended
(`clr r25`), signed values sign-extended (`clr r25` / `sbrc r24,7` / `ser r25`).

## Core-family differences

| Capability             | Classic AT90S          | ATtiny / enhanced              | ATmega (enhanced)        |
| ---------------------- | ---------------------- | ------------------------------ | ------------------------ |
| `mul` (hardware mult.) | no — uses `__mulhi3`   | no — uses `__mulhi3`           | yes — inlined under `--optimize speed`, else `__mulhi3` |
| `movw`                 | no — paired `mov`      | yes — `movw`                   | yes — `movw`             |
| `jmp`/`call`           | no — `rjmp`/`rcall`    | depends on flash size          | `jmp`/`rjmp` per part (see [CHIPS.md](CHIPS.md)) |

`movw` availability only affects how the 16-bit accumulator is copied to the
right-operand pair; the AT90S path emits `mov r22,r24` + `mov r23,r25` instead of
`movw r22,r24`.

## SFR access (`in`/`out` vs `lds`/`sts`)

SFRs are exposed as predefined byte variables. The address determines the
instructions used:

| SFR data-space address | Read           | Write           | Bit set/clear        |
| ---------------------- | -------------- | --------------- | -------------------- |
| `0x20`–`0x5F`          | `in r24, io`   | `out io, r24`   | `sbi`/`cbi` if `0x20`–`0x3F` |
| above `0x5F` (extended I/O) | `lds r24, addr` | `sts addr, r24` | read-modify-write    |

`io` is the data address minus `0x20`. Reading an 8-bit SFR clears `r25`.
Extended-I/O parts such as the ATmega328P and the ATmega48/88/168 group place
most peripheral registers above `0x5F`, so they are reached with `lds`/`sts`.

## Assignment

| PASCAL                  | Emits |
| ----------------------- | ----- |
| `v := expr;` (RAM var)  | evaluate `expr`, then `sts v, r24` (+ `sts v+1, r25` for 16-bit) |
| `PORTB := expr;` (low SFR) | evaluate `expr`, then `out io, r24` |
| `UDR0 := expr;` (extended SFR) | evaluate `expr`, then `sts addr, r24` |
| `v := expr;` (`var` parameter) | load pointer from the var's slot into `Z`, then `st Z, r24` (+ `std Z+1, r25`) |
| `a[i] := expr;`         | evaluate value, compute element address into `Z`, `st Z, r24` (+ `std Z+1, r25` for 2-byte elements) |

## Arithmetic

| PASCAL    | Emits |
| --------- | ----- |
| `a + b`   | `add r24, r22` / `adc r25, r23` |
| `a - b`   | `sub r24, r22` / `sbc r25, r23` |
| `a * b`   | `rcall __mulhi3`; on a chip with `mul` and `--optimize speed`, an inline `mul`-based 16×16→16 sequence (`mul`, `movw`, `add`, ...) |
| `a div b` | `rcall __divmodhi4` (signed) or `__udivmodhi4` (unsigned), then `movw r24, r22` to move the quotient into the accumulator |
| `a mod b` | `rcall __divmodhi4` / `__udivmodhi4`; the remainder is already in `r25:r24` |
| `a / b`   | treated as `div` (real division is unsupported) |
| unary `-` | `com r25` / `neg r24` / `sbci r25, 0xff` (16-bit two's-complement negate) |
| unary `+` | no-op |

The libgcc helpers `__mulhi3`, `__divmodhi4` and `__udivmodhi4` are resolved at
link time by `avr-gcc`/`avr-libc`, which is why linking goes through `avr-gcc`.
Signedness is taken from the operand types (signed if either operand is signed).

## Bitwise and shifts

| PASCAL          | Emits |
| --------------- | ----- |
| `a and b`       | `and r24, r22` / `and r25, r23` |
| `a or b`        | `or  r24, r22` / `or  r25, r23` |
| `a xor b`       | `eor r24, r22` / `eor r25, r23` |
| `not a` (integer) | `com r24` / `com r25` (bitwise complement) |
| `not a` (boolean) | `eor r24, r18` with `r18=1`, `clr r25` (logical not) |
| `a shl b`       | counted loop: `lsl r24` / `rol r25`, decrement `r22`, repeat (`tst`/`breq` guard for zero count) |
| `a shr b`       | counted loop: `lsr r25` (unsigned) or `asr r25` (signed) / `ror r24`, decrement `r22`, repeat |

Shift counts are dynamic by default (a loop driven by `r22`), with a zero-count
guard so a shift of 0 leaves the value unchanged.

## Comparisons

Comparisons compute `cp r24, r22` / `cpc r25, r23` (or the operands swapped) and
then branch. Branch mnemonics depend on signedness: signed comparisons use
`brlt`/`brge`, unsigned use `brlo`/`brsh`; equality uses `breq`/`brne`.

| PASCAL | True branch (value context) | False branch (control flow) |
| ------ | --------------------------- | --------------------------- |
| `a = b`  | `breq` | `brne` |
| `a <> b` | `brne` | `breq` |
| `a < b`  | `brlt`/`brlo` | `brge`/`brsh` |
| `a >= b` | `brge`/`brsh` | `brlt`/`brlo` |
| `a > b`  | (swapped) `brlt`/`brlo` | (swapped) `brge`/`brsh` |
| `a <= b` | (swapped) `brge`/`brsh` | (swapped) `brlt`/`brlo` |

In a value context (e.g. `b := a < c`) the result is materialised as `0` or `1`
in `r24` with `r25` cleared. In a control-flow context the comparison is fused
directly into a conditional branch to the loop/if exit label, avoiding the
0/1 materialisation.

## Control flow

| PASCAL                       | Emits |
| ---------------------------- | ----- |
| `if c then s`                | evaluate `c`, branch-on-false to the end label, then `s` |
| `if c then s1 else s2`       | branch-on-false to the else label, `s1`, `rjmp end`, else label, `s2`, end label |
| `while c do s`               | top label, branch-on-false to end, `s`, `rjmp top`, end label |
| `repeat s until c`           | top label, `s`, evaluate `c`, branch-to-top while `c` is false |
| `for i := a to b do s`       | `i := a`; loop: compare `i` with `b` (`cp`/`cpc`), exit via `brlt`/`brlo` when out of range, body, `adiw r24,1`, store, `rjmp top` |
| `for i := a downto b do s`   | as above but with `sbiw r24,1` and the comparison reversed |
| generic boolean condition    | evaluate to `r25:r24`, `or r24, r25`, `breq falseLabel` |

Local jumps use `rjmp`. The **interrupt vector table** uses `jmp` on parts that
support `jmp`/`call` and `rjmp` otherwise (see [CHIPS.md](CHIPS.md)). Procedure
and function calls always use `rcall`.

## Calls, functions and procedures

Arguments are passed by writing them into each parameter's fixed storage slot
before the `rcall`:

- **Value parameters:** evaluate the argument, then `sts slot, r24` (+ high byte).
- **`var` (by-reference) parameters:** compute the argument's address and store
  the 2-byte pointer into the parameter slot; inside the routine, loads/stores go
  through `Z` after `lds`-ing the pointer.

A function returns its value in `r25:r24` (loaded from the result slot at the
`ret`). Routines end with `ret`.

## Interrupt service routines

A `procedure Name; interrupt VECTOR;` is emitted as `isr_Name`. The prologue
pushes `r0`, `r1`, `SREG` (via `in r0, 0x3f`) and the call-clobbered registers
`r18`–`r27`, `r30`, `r31`; the epilogue restores them in reverse and ends with
`reti`. The vector table entry for `VECTOR` jumps to the handler; unbound vectors
go to `__bad_interrupt` (a bare `reti`).

## Reset / startup

The generated file is self-contained (use `avr-gcc -nostartfiles`). The
`__reset` routine clears `r1` (the fixed zero register), clears `SREG`, sets the
stack pointer to `RAMEND` (writing `SPL`, and `SPH` when present), then runs the
main block, ending in an `rjmp __halt` busy loop.

## Bit built-ins

`SetBit`/`ClearBit`/`ToggleBit`/`TestBit` take an SFR as their first argument:

| Case | Emits |
| ---- | ----- |
| constant bit, bit-addressable register (`0x20`–`0x3F`), Set/Clear | single `sbi`/`cbi io, bit` |
| constant bit, bit-addressable register, TestBit | `sbis io, bit` skip + 0/1 result |
| Toggle, or non-bit-addressable register, or dynamic bit | read-modify-write: read SFR, apply `ori`/`andi`/`eor` (or a runtime-built mask), write back |

`Toggle` always uses read-modify-write because AVR has no atomic bit-toggle I/O
instruction.

## Strings and arrays

String literals are placed in flash as a `.byte` sequence (NUL-terminated). Using
a string yields its flash address in `r25:r24`; `UartWriteStr` reads it with
`lpm`. Array element addresses are computed as `base + index*elemSize` into the
`Z` pointer (the index is scaled with `lsl`/`rol` for 2-byte elements). Only
simple array variables can be indexed.

## Delays

`DelayMs`/`DelayUs` evaluate their argument into `r25:r24` and `rcall` a runtime
loop (`__delay_ms` / `__delay_us`) whose inner-loop count is computed from
`F_CPU` (set by `--clock`). These routines are appended only when used.
