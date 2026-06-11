package io.github.avrpas.sema;

/**
 * The (small) type system supported by the transpiler. The target is an 8-bit
 * AVR so the integer types map directly onto 1- or 2-byte storage.
 *
 * <ul>
 *   <li>{@code byte}     - unsigned 8-bit</li>
 *   <li>{@code shortint} - signed 8-bit</li>
 *   <li>{@code word}     - unsigned 16-bit</li>
 *   <li>{@code integer}  - signed 16-bit (PASCAL default integer)</li>
 *   <li>{@code boolean}  - 1 byte, 0/1</li>
 *   <li>{@code char}     - 1 byte</li>
 * </ul>
 *
 * Real numbers are recognised by the parser but flagged as unsupported by the
 * code generator (a stub is emitted) because software floating point is out of
 * scope for the first release.
 */
public final class Type {

    public enum Kind { BYTE, SHORTINT, WORD, INTEGER, BOOLEAN, CHAR, REAL, ARRAY, VOID, ERROR }

    public final Kind kind;
    public final boolean signed;
    public final int sizeBytes;

    // array element type and length (only meaningful when kind == ARRAY)
    public final Type element;
    public final int length;

    private Type(Kind kind, boolean signed, int sizeBytes, Type element, int length) {
        this.kind = kind;
        this.signed = signed;
        this.sizeBytes = sizeBytes;
        this.element = element;
        this.length = length;
    }

    public static final Type BYTE     = new Type(Kind.BYTE, false, 1, null, 0);
    public static final Type SHORTINT = new Type(Kind.SHORTINT, true, 1, null, 0);
    public static final Type WORD     = new Type(Kind.WORD, false, 2, null, 0);
    public static final Type INTEGER  = new Type(Kind.INTEGER, true, 2, null, 0);
    public static final Type BOOLEAN  = new Type(Kind.BOOLEAN, false, 1, null, 0);
    public static final Type CHAR     = new Type(Kind.CHAR, false, 1, null, 0);
    public static final Type REAL     = new Type(Kind.REAL, true, 4, null, 0);
    public static final Type VOID     = new Type(Kind.VOID, false, 0, null, 0);
    public static final Type ERROR    = new Type(Kind.ERROR, false, 0, null, 0);

    public static Type array(Type element, int length) {
        return new Type(Kind.ARRAY, false, element.sizeBytes * length, element, length);
    }

    public boolean isInteger() {
        return kind == Kind.BYTE || kind == Kind.SHORTINT || kind == Kind.WORD
                || kind == Kind.INTEGER || kind == Kind.CHAR || kind == Kind.BOOLEAN;
    }

    public boolean isOrdinal() {
        return isInteger();
    }

    /** Common type used for arithmetic between two integer operands. */
    public static Type promote(Type a, Type b) {
        if (a.kind == Kind.ERROR || b.kind == Kind.ERROR) return ERROR;
        int size = Math.max(a.sizeBytes, b.sizeBytes);
        boolean signed = a.signed || b.signed;
        if (size <= 1) {
            return signed ? SHORTINT : BYTE;
        }
        return signed ? INTEGER : WORD;
    }

    public String pascalName() {
        switch (kind) {
            case BYTE: return "Byte";
            case SHORTINT: return "ShortInt";
            case WORD: return "Word";
            case INTEGER: return "Integer";
            case BOOLEAN: return "Boolean";
            case CHAR: return "Char";
            case REAL: return "Real";
            case VOID: return "void";
            case ARRAY: return "array of " + (element == null ? "?" : element.pascalName());
            default: return "<error>";
        }
    }

    @Override
    public String toString() { return pascalName(); }
}
