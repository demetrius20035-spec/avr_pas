program AdcToPwm;

{ Reads ADC channel 0 and uses the high byte to drive an array-based
  lookup. Demonstrates 16-bit arithmetic, division and arrays. }

const
  SAMPLES = 4;

var
  acc: word;
  i: byte;
  table: array[0..3] of word;

begin
  AdcInit;
  for i := 0 to SAMPLES - 1 do
    table[i] := AdcRead(0);

  acc := 0;
  for i := 0 to SAMPLES - 1 do
    acc := acc + table[i];

  acc := acc div SAMPLES;     { average -> 16-bit unsigned division }

  DDRB := $FF;
  PORTB := acc shr 2;
end.
