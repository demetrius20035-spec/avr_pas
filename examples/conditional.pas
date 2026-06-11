program ConditionalDemo;

{ Demonstrates conditional compilation. The transpiler predefines a symbol
  for the target chip name and family, so code can adapt per chip.
  Try: --chip ATmega8   vs   --chip ATtiny85 }

var
  x: byte;

begin
  {$IFDEF ATMEGA}
  DDRB := $FF;          { ATmega family has a full PORTB }
  x := 8;
  {$ENDIF}

  {$IFDEF ATTINY}
  DDRB := $1F;          { ATtiny85 PORTB is 5 bits wide }
  x := 5;
  {$ENDIF}

  {$IFNDEF AVR}
  x := 0;              { never compiled: AVR is always defined }
  {$ENDIF}

  PORTB := x;
end.
