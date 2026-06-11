package io.github.avrpas.lexer;

import io.github.avrpas.diag.Diagnostic;
import io.github.avrpas.diag.DiagnosticReporter;
import io.github.avrpas.diag.SourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts PASCAL source text into a token stream. PASCAL is case-insensitive
 * for keywords and identifiers, which is handled here by lower-casing keyword
 * lookups while preserving original identifier spelling.
 *
 * <p>Supported comment forms: {@code { ... }}, {@code (* ... *)} and
 * {@code // ...} line comments (the latter is a Free Pascal extension that is
 * convenient for embedded code).</p>
 */
public final class Lexer {

    private static final Map<String, TokenType> KEYWORDS = new HashMap<>();
    static {
        KEYWORDS.put("program", TokenType.PROGRAM);
        KEYWORDS.put("unit", TokenType.UNIT);
        KEYWORDS.put("interface", TokenType.INTERFACE);
        KEYWORDS.put("implementation", TokenType.IMPLEMENTATION);
        KEYWORDS.put("uses", TokenType.USES);
        KEYWORDS.put("const", TokenType.CONST);
        KEYWORDS.put("var", TokenType.VAR);
        KEYWORDS.put("type", TokenType.TYPE);
        KEYWORDS.put("array", TokenType.ARRAY);
        KEYWORDS.put("of", TokenType.OF);
        KEYWORDS.put("record", TokenType.RECORD);
        KEYWORDS.put("procedure", TokenType.PROCEDURE);
        KEYWORDS.put("function", TokenType.FUNCTION);
        KEYWORDS.put("begin", TokenType.BEGIN);
        KEYWORDS.put("end", TokenType.END);
        KEYWORDS.put("if", TokenType.IF);
        KEYWORDS.put("then", TokenType.THEN);
        KEYWORDS.put("else", TokenType.ELSE);
        KEYWORDS.put("while", TokenType.WHILE);
        KEYWORDS.put("do", TokenType.DO);
        KEYWORDS.put("repeat", TokenType.REPEAT);
        KEYWORDS.put("until", TokenType.UNTIL);
        KEYWORDS.put("for", TokenType.FOR);
        KEYWORDS.put("to", TokenType.TO);
        KEYWORDS.put("downto", TokenType.DOWNTO);
        KEYWORDS.put("case", TokenType.CASE);
        KEYWORDS.put("and", TokenType.AND);
        KEYWORDS.put("or", TokenType.OR);
        KEYWORDS.put("xor", TokenType.XOR);
        KEYWORDS.put("not", TokenType.NOT);
        KEYWORDS.put("div", TokenType.DIV);
        KEYWORDS.put("mod", TokenType.MOD);
        KEYWORDS.put("shl", TokenType.SHL);
        KEYWORDS.put("shr", TokenType.SHR);
        KEYWORDS.put("true", TokenType.TRUE);
        KEYWORDS.put("false", TokenType.FALSE);
        KEYWORDS.put("nil", TokenType.NIL);
        KEYWORDS.put("asm", TokenType.ASM);
        KEYWORDS.put("interrupt", TokenType.INTERRUPT);
    }

    private final String src;
    private final DiagnosticReporter reporter;
    private int pos;
    private int line = 1;
    private int col = 1;
    private Token pending; // synthetic token to emit next (used by asm capture)

    public Lexer(String src, DiagnosticReporter reporter) {
        this.src = src;
        this.reporter = reporter;
    }

    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        Token t;
        do {
            t = next();
            tokens.add(t);
        } while (t.type != TokenType.EOF);
        return tokens;
    }

    private Token next() {
        if (pending != null) {
            Token p = pending;
            pending = null;
            return p;
        }
        skipTrivia();
        SourceLocation loc = here();
        if (atEnd()) {
            return new Token(TokenType.EOF, "<eof>", 0, loc);
        }
        char c = peek();

        if (isIdentStart(c)) {
            return identifier(loc);
        }
        if (Character.isDigit(c)) {
            return number(loc);
        }
        if (c == '$') {
            return hexNumber(loc);
        }
        if (c == '\'') {
            return stringLiteral(loc);
        }
        return symbol(loc);
    }

    private Token identifier(SourceLocation loc) {
        StringBuilder sb = new StringBuilder();
        while (!atEnd() && isIdentPart(peek())) {
            sb.append(advance());
        }
        String text = sb.toString();
        TokenType kw = KEYWORDS.get(text.toLowerCase());
        if (kw == TokenType.ASM) {
            return captureAsm(loc);
        }
        if (kw != null) {
            return new Token(kw, text, 0, loc);
        }
        return new Token(TokenType.IDENT, text, 0, loc);
    }

    /**
     * Captures the raw body of an {@code asm ... end} block. Everything between
     * the {@code asm} keyword and the next standalone {@code end} word is kept
     * verbatim (newlines preserved) so that hand-written AVR assembly survives
     * untouched. The closing {@code end} is emitted as a synthetic token.
     */
    private Token captureAsm(SourceLocation loc) {
        int start = pos;
        while (!atEnd()) {
            char c = peek();
            if (isIdentStart(c)) {
                int wordStart = pos;
                StringBuilder w = new StringBuilder();
                while (!atEnd() && isIdentPart(peek())) w.append(advance());
                if (w.toString().equalsIgnoreCase("end")) {
                    String raw = src.substring(start, wordStart);
                    pending = new Token(TokenType.END, "end", 0, here());
                    return new Token(TokenType.ASM, raw, 0, loc);
                }
            } else {
                advance();
            }
        }
        reporter.error(Diagnostic.Kind.SYNTAX, "Unterminated 'asm' block (missing 'end')", loc);
        return new Token(TokenType.ASM, src.substring(start), 0, loc);
    }

    private Token number(SourceLocation loc) {
        StringBuilder sb = new StringBuilder();
        boolean isReal = false;
        while (!atEnd() && Character.isDigit(peek())) {
            sb.append(advance());
        }
        // real number: a dot NOT followed by another dot (range operator '..')
        if (!atEnd() && peek() == '.' && peekAt(1) != '.') {
            isReal = true;
            sb.append(advance());
            while (!atEnd() && Character.isDigit(peek())) {
                sb.append(advance());
            }
        }
        if (!atEnd() && (peek() == 'e' || peek() == 'E')) {
            isReal = true;
            sb.append(advance());
            if (!atEnd() && (peek() == '+' || peek() == '-')) sb.append(advance());
            while (!atEnd() && Character.isDigit(peek())) sb.append(advance());
        }
        String text = sb.toString();
        if (isReal) {
            return new Token(TokenType.REAL_LITERAL, text, 0, loc);
        }
        long value;
        try {
            value = Long.parseLong(text);
        } catch (NumberFormatException e) {
            reporter.error(Diagnostic.Kind.SYNTAX, "Integer literal out of range: " + text, loc);
            value = 0;
        }
        return new Token(TokenType.INT_LITERAL, text, value, loc);
    }

    private Token hexNumber(SourceLocation loc) {
        advance(); // consume '$'
        StringBuilder sb = new StringBuilder();
        while (!atEnd() && isHex(peek())) {
            sb.append(advance());
        }
        if (sb.length() == 0) {
            reporter.error(Diagnostic.Kind.SYNTAX, "Malformed hexadecimal literal", loc);
            return new Token(TokenType.INT_LITERAL, "$0", 0, loc);
        }
        long value = Long.parseLong(sb.toString(), 16);
        return new Token(TokenType.INT_LITERAL, "$" + sb, value, loc);
    }

    private Token stringLiteral(SourceLocation loc) {
        advance(); // opening quote
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (atEnd()) {
                reporter.error(Diagnostic.Kind.SYNTAX, "Unterminated string literal", loc);
                break;
            }
            char c = advance();
            if (c == '\'') {
                // doubled quote -> literal quote
                if (!atEnd() && peek() == '\'') {
                    advance();
                    sb.append('\'');
                } else {
                    break;
                }
            } else {
                sb.append(c);
            }
        }
        String text = sb.toString();
        if (text.length() == 1) {
            return new Token(TokenType.CHAR_LITERAL, text, text.charAt(0), loc);
        }
        return new Token(TokenType.STRING_LITERAL, text, 0, loc);
    }

    private Token symbol(SourceLocation loc) {
        char c = advance();
        switch (c) {
            case '+': return tok(TokenType.PLUS, "+", loc);
            case '-': return tok(TokenType.MINUS, "-", loc);
            case '*': return tok(TokenType.STAR, "*", loc);
            case '/': return tok(TokenType.SLASH, "/", loc);
            case '=': return tok(TokenType.EQ, "=", loc);
            case '(': return tok(TokenType.LPAREN, "(", loc);
            case ')': return tok(TokenType.RPAREN, ")", loc);
            case '[': return tok(TokenType.LBRACKET, "[", loc);
            case ']': return tok(TokenType.RBRACKET, "]", loc);
            case ';': return tok(TokenType.SEMICOLON, ";", loc);
            case ',': return tok(TokenType.COMMA, ",", loc);
            case '^': return tok(TokenType.CARET, "^", loc);
            case '@': return tok(TokenType.AT, "@", loc);
            case ':':
                if (match('=')) return tok(TokenType.ASSIGN, ":=", loc);
                return tok(TokenType.COLON, ":", loc);
            case '<':
                if (match('=')) return tok(TokenType.LTE, "<=", loc);
                if (match('>')) return tok(TokenType.NEQ, "<>", loc);
                return tok(TokenType.LT, "<", loc);
            case '>':
                if (match('=')) return tok(TokenType.GTE, ">=", loc);
                return tok(TokenType.GT, ">", loc);
            case '.':
                if (match('.')) return tok(TokenType.DOTDOT, "..", loc);
                return tok(TokenType.DOT, ".", loc);
            default:
                reporter.error(Diagnostic.Kind.SYNTAX, "Unexpected character '" + c + "'", loc);
                return next();
        }
    }

    // ---------------------------------------------------------------- trivia

    private void skipTrivia() {
        while (!atEnd()) {
            char c = peek();
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                advance();
            } else if (c == '{') {
                blockComment('}', null);
            } else if (c == '(' && peekAt(1) == '*') {
                advance(); advance();
                blockComment('*', ')');
            } else if (c == '/' && peekAt(1) == '/') {
                while (!atEnd() && peek() != '\n') advance();
            } else {
                break;
            }
        }
    }

    private void blockComment(char end1, Character end2) {
        if (end2 == null) {
            advance(); // consume '{'
            while (!atEnd() && peek() != end1) advance();
            if (!atEnd()) advance();
        } else {
            while (!atEnd()) {
                if (peek() == end1 && peekAt(1) == end2) {
                    advance(); advance();
                    return;
                }
                advance();
            }
        }
    }

    // -------------------------------------------------------------- char ops

    private boolean isIdentStart(char c) { return Character.isLetter(c) || c == '_'; }
    private boolean isIdentPart(char c) { return Character.isLetterOrDigit(c) || c == '_'; }
    private boolean isHex(char c) {
        return Character.isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private boolean atEnd() { return pos >= src.length(); }
    private char peek() { return src.charAt(pos); }
    private char peekAt(int n) { return (pos + n) < src.length() ? src.charAt(pos + n) : '\0'; }

    private char advance() {
        char c = src.charAt(pos++);
        if (c == '\n') { line++; col = 1; } else { col++; }
        return c;
    }

    private boolean match(char expected) {
        if (atEnd() || peek() != expected) return false;
        advance();
        return true;
    }

    private SourceLocation here() { return new SourceLocation(line, col); }

    private Token tok(TokenType type, String text, SourceLocation loc) {
        return new Token(type, text, 0, loc);
    }
}
