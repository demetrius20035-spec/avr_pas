package io.github.avrpas.lexer;

import io.github.avrpas.diag.SourceLocation;

/**
 * A lexical token. {@link #text} carries the raw spelling; {@link #intValue}
 * is populated for integer literals.
 */
public final class Token {
    public final TokenType type;
    public final String text;
    public final long intValue;
    public final SourceLocation location;

    public Token(TokenType type, String text, long intValue, SourceLocation location) {
        this.type = type;
        this.text = text;
        this.intValue = intValue;
        this.location = location;
    }

    public boolean is(TokenType t) {
        return type == t;
    }

    @Override
    public String toString() {
        return type + "(" + text + ")@" + location;
    }
}
