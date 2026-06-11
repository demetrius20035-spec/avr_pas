program TimerBlink;

{ Toggles an LED from a Timer0 overflow interrupt service routine.
  Demonstrates interrupt routines, global state and bit built-ins. }

var
  ticks: word;

procedure OnTick; interrupt TIMER0_OVF;
begin
  ticks := ticks + 1;
  if ticks >= 30 then
  begin
    ToggleBit(PORTB, 0);
    ticks := 0;
  end;
end;

begin
  ticks := 0;
  SetBit(DDRB, 0);          { PB0 output }
  Timer0Init(5);            { prescaler /1024 }
  SetBit(TIMSK, 0);         { enable Timer0 overflow interrupt (TOIE0) }
  Sei;
  while True do
    Nop;
end.
