package io.github.avrpas.ast;

/**
 * Binary and unary operators shared by the expression nodes.
 */
public final class Operators {
    private Operators() {}

    public enum BinOp {
        ADD("+"), SUB("-"), MUL("*"), DIV("div"), MOD("mod"), FDIV("/"),
        AND("and"), OR("or"), XOR("xor"), SHL("shl"), SHR("shr"),
        EQ("="), NEQ("<>"), LT("<"), LTE("<="), GT(">"), GTE(">=");

        public final String text;
        BinOp(String t) { this.text = t; }

        public boolean isComparison() {
            return this == EQ || this == NEQ || this == LT || this == LTE || this == GT || this == GTE;
        }

        public boolean isBitwise() {
            return this == AND || this == OR || this == XOR || this == SHL || this == SHR;
        }
    }

    public enum UnOp {
        NEG("-"), NOT("not"), POS("+");

        public final String text;
        UnOp(String t) { this.text = t; }
    }
}
