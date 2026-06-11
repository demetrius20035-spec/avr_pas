package io.github.avrpas.sema;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A lexically-scoped symbol table. PASCAL identifiers are case-insensitive, so
 * lookups are keyed on the lower-cased name.
 */
public final class SymbolTable {

    private final SymbolTable parent;
    private final Map<String, Symbol> symbols = new LinkedHashMap<>();

    public SymbolTable(SymbolTable parent) {
        this.parent = parent;
    }

    /** Define a symbol in this scope. Returns false if already defined here. */
    public boolean define(Symbol s) {
        String key = s.name.toLowerCase();
        if (symbols.containsKey(key)) {
            return false;
        }
        symbols.put(key, s);
        return true;
    }

    /** Look up a symbol in this scope or any enclosing scope. */
    public Symbol resolve(String name) {
        String key = name.toLowerCase();
        Symbol s = symbols.get(key);
        if (s != null) return s;
        return parent != null ? parent.resolve(name) : null;
    }

    /** Look up only in the current scope. */
    public Symbol resolveLocal(String name) {
        return symbols.get(name.toLowerCase());
    }

    public SymbolTable parent() {
        return parent;
    }

    public Iterable<Symbol> localSymbols() {
        return symbols.values();
    }
}
