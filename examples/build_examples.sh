#!/usr/bin/env bash
#
# Transpiles every example and (when an AVR toolchain is available) assembles
# and links the result to prove the generated assembly is valid.
#
# Usage:  ./examples/build_examples.sh
#
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR="$ROOT/target/pascal2asm-avr.jar"
OUT="$ROOT/examples/out"
mkdir -p "$OUT"

if [ ! -f "$JAR" ]; then
  echo "Building jar..."
  (cd "$ROOT" && mvn -q package -DskipTests) || exit 1
fi

have_avr=0
command -v avr-gcc >/dev/null 2>&1 && have_avr=1

run() {
  local pas="$1" chip="$2"; shift 2
  local base; base="$(basename "${pas%.pas}")_${chip}"
  local s="$OUT/$base.s"
  echo "== $pas  ->  $chip"
  java -jar "$JAR" --chip "$chip" --input "$ROOT/examples/$pas" \
       --output "$s" "$@" || { echo "  transpile FAILED"; return 1; }
  if [ "$have_avr" = 1 ]; then
    if avr-gcc -mmcu="$(echo "$chip" | tr 'A-Z' 'a-z')" -nostartfiles \
        -o "$OUT/$base.elf" "$s" 2>"$OUT/$base.log"; then
      echo "  link OK: $(avr-size "$OUT/$base.elf" | tail -1)"
    else
      echo "  link FAILED (see $OUT/$base.log)"
    fi
  fi
}

run blink.pas            ATmega8   --clock 8MHz  --optimize speed
run blink.pas            ATtiny85  --clock 1MHz
run blink.pas            AT90S2313 --clock 4MHz
run uart_echo.pas        ATmega8   --clock 16MHz --uart-baud 9600
run uart_echo.pas        ATmega328P --clock 16MHz --uart-baud 115200
run timer_interrupt.pas  ATmega8   --clock 8MHz
run adc_read.pas         ATmega8   --clock 8MHz  --map --listing
run conditional.pas      ATmega8   --clock 8MHz
run conditional.pas      ATtiny85  --clock 8MHz

echo "Done. Artifacts in $OUT"
