program Blink;

{ Classic LED blink on PB0. Demonstrates GPIO via direct SFR access,
  a delay built-in, and a simple loop. }

const
  LED = 0;          { PB0 }

var
  i: byte;

begin
  { configure PB0 as output }
  DDRB := DDRB or (1 shl LED);

  while True do
  begin
    PORTB := PORTB or (1 shl LED);    { LED on }
    DelayMs(500);
    PORTB := PORTB and not (1 shl LED); { LED off }
    DelayMs(500);
  end;
end.
