package io.github.avrpas.lexer;

/**
 * All token categories recognised by the PASCAL lexer.
 */
public enum TokenType {
    // literals & identifiers
    IDENT, INT_LITERAL, REAL_LITERAL, STRING_LITERAL, CHAR_LITERAL,

    // keywords
    PROGRAM, UNIT, INTERFACE, IMPLEMENTATION, USES,
    CONST, VAR, TYPE, ARRAY, OF, RECORD,
    PROCEDURE, FUNCTION, BEGIN, END,
    IF, THEN, ELSE, WHILE, DO, REPEAT, UNTIL, FOR, TO, DOWNTO, CASE,
    AND, OR, XOR, NOT, DIV, MOD, SHL, SHR,
    TRUE, FALSE, NIL,
    ASM, INTERRUPT,

    // symbols
    ASSIGN,        // :=
    PLUS, MINUS, STAR, SLASH,
    EQ, NEQ, LT, LTE, GT, GTE,
    LPAREN, RPAREN, LBRACKET, RBRACKET,
    SEMICOLON, COLON, COMMA, DOT, DOTDOT, CARET, AT,

    EOF;
}
