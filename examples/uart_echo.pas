program UartEcho;

{ Reads bytes from the UART and echoes them back, incremented by one.
  Demonstrates the UART built-ins, a function call and 8-bit arithmetic. }

function NextChar(c: byte): byte;
begin
  NextChar := c + 1;
end;

var
  ch: byte;

begin
  UartInit;
  while True do
  begin
    ch := UartReceive;
    UartTransmit(NextChar(ch));
  end;
end.
