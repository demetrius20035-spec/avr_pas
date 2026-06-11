package io.github.avrpas.chip;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Hardware description of a single AVR micro-controller. This is the heart of
 * the chip-awareness requirement: memory sizes, instruction-set capabilities,
 * the special-function-register (SFR) map, the interrupt vector table and the
 * set of available peripherals.
 *
 * <p>SFR addresses are stored as <em>data-space</em> addresses (the value used
 * with {@code lds}/{@code sts}). The code generator derives the I/O-space
 * address for {@code in}/{@code out}/{@code sbi}/{@code cbi} by subtracting the
 * I/O register offset (0x20).</p>
 *
 * <p>Instances are immutable and produced through {@link Builder}.</p>
 */
public final class Chip {

    /** I/O registers are mapped into the data space starting at this address. */
    public static final int IO_BASE = 0x20;
    /** Highest data-space address still reachable by {@code in}/{@code out}. */
    public static final int IO_DIRECT_MAX = 0x5F;
    /** Highest address reachable by the bit instructions {@code sbi}/{@code cbi}. */
    public static final int IO_BIT_MAX = 0x3F;

    private final String name;
    private final ChipFamily family;
    private final int flashBytes;
    private final int sramBytes;
    private final int eepromBytes;
    private final int ramStart;
    private final long maxClockHz;

    private final boolean hasMul;
    private final boolean hasMovw;
    private final boolean hasJmpCall;
    private final boolean hasElpm;
    private final boolean hasMultiplyEnhanced; // muls/mulsu/fmul family

    private final Set<Peripheral> peripherals;
    private final Map<String, Integer> sfr;       // name -> data-space address
    private final Map<String, Integer> vectors;   // name -> vector number (0 = reset)

    private Chip(Builder b) {
        this.name = b.name;
        this.family = b.family;
        this.flashBytes = b.flashBytes;
        this.sramBytes = b.sramBytes;
        this.eepromBytes = b.eepromBytes;
        this.ramStart = b.ramStart;
        this.maxClockHz = b.maxClockHz;
        this.hasMul = b.hasMul;
        this.hasMovw = b.hasMovw;
        this.hasJmpCall = b.hasJmpCall;
        this.hasElpm = b.hasElpm;
        this.hasMultiplyEnhanced = b.hasMultiplyEnhanced;
        this.peripherals = Collections.unmodifiableSet(EnumSet.copyOf(
                b.peripherals.isEmpty() ? EnumSet.of(Peripheral.GPIO) : b.peripherals));
        this.sfr = Collections.unmodifiableMap(new LinkedHashMap<>(b.sfr));
        this.vectors = Collections.unmodifiableMap(new LinkedHashMap<>(b.vectors));
    }

    // ------------------------------------------------------------- accessors

    public String name() { return name; }
    public ChipFamily family() { return family; }
    public int flashBytes() { return flashBytes; }
    public int sramBytes() { return sramBytes; }
    public int eepromBytes() { return eepromBytes; }
    public int ramStart() { return ramStart; }
    public int ramEnd() { return ramStart + sramBytes - 1; }
    public long maxClockHz() { return maxClockHz; }

    public boolean hasMul() { return hasMul; }
    public boolean hasMovw() { return hasMovw; }
    public boolean hasJmpCall() { return hasJmpCall; }
    public boolean hasElpm() { return hasElpm; }
    public boolean hasMultiplyEnhanced() { return hasMultiplyEnhanced; }

    public Set<Peripheral> peripherals() { return peripherals; }
    public boolean hasPeripheral(Peripheral p) { return peripherals.contains(p); }

    public Map<String, Integer> sfrMap() { return sfr; }
    public Map<String, Integer> vectorMap() { return vectors; }

    /** Data-space address of a named SFR, or -1 if the chip does not define it. */
    public int sfrAddress(String regName) {
        Integer a = sfr.get(regName);
        return a == null ? -1 : a;
    }

    public boolean hasSfr(String regName) {
        return sfr.containsKey(regName);
    }

    /** Vector number for a named interrupt, or -1 if unknown. */
    public int vectorNumber(String vectorName) {
        Integer v = vectors.get(vectorName);
        return v == null ? -1 : v;
    }

    public boolean hasVector(String vectorName) {
        return vectors.containsKey(vectorName);
    }

    /** Number of interrupt vectors (table size), at least 1 for reset. */
    public int vectorCount() {
        int max = 0;
        for (int v : vectors.values()) max = Math.max(max, v);
        return max + 1;
    }

    /** True when an SFR can be reached with {@code in}/{@code out}. */
    public boolean isDirectIo(int dataAddress) {
        return dataAddress >= IO_BASE && dataAddress <= IO_DIRECT_MAX;
    }

    /** True when an SFR bit can be reached with {@code sbi}/{@code cbi}. */
    public boolean isBitAddressable(int dataAddress) {
        return dataAddress >= IO_BASE && dataAddress <= IO_BIT_MAX;
    }

    public int ioAddress(int dataAddress) {
        return dataAddress - IO_BASE;
    }

    @Override
    public String toString() {
        return name + " [" + family + ", flash=" + flashBytes + "B, sram=" + sramBytes + "B]";
    }

    // --------------------------------------------------------------- builder

    public static final class Builder {
        private final String name;
        private ChipFamily family = ChipFamily.ATMEGA;
        private int flashBytes;
        private int sramBytes;
        private int eepromBytes;
        private int ramStart = 0x60;
        private long maxClockHz = 16_000_000L;

        private boolean hasMul = false;
        private boolean hasMovw = false;
        private boolean hasJmpCall = false;
        private boolean hasElpm = false;
        private boolean hasMultiplyEnhanced = false;

        private final Set<Peripheral> peripherals = EnumSet.noneOf(Peripheral.class);
        private final Map<String, Integer> sfr = new LinkedHashMap<>();
        private final Map<String, Integer> vectors = new LinkedHashMap<>();

        public Builder(String name) { this.name = name; }

        public Builder family(ChipFamily f) { this.family = f; return this; }
        public Builder flash(int bytes) { this.flashBytes = bytes; return this; }
        public Builder sram(int bytes) { this.sramBytes = bytes; return this; }
        public Builder eeprom(int bytes) { this.eepromBytes = bytes; return this; }
        public Builder ramStart(int addr) { this.ramStart = addr; return this; }
        public Builder maxClock(long hz) { this.maxClockHz = hz; return this; }

        public Builder mul(boolean v) { this.hasMul = v; return this; }
        public Builder movw(boolean v) { this.hasMovw = v; return this; }
        public Builder jmpCall(boolean v) { this.hasJmpCall = v; return this; }
        public Builder elpm(boolean v) { this.hasElpm = v; return this; }
        public Builder enhancedMul(boolean v) { this.hasMultiplyEnhanced = v; return this; }

        public Builder peripherals(Peripheral... ps) {
            for (Peripheral p : ps) peripherals.add(p);
            return this;
        }

        public Builder sfr(String name, int dataAddress) {
            this.sfr.put(name, dataAddress);
            return this;
        }

        public Builder vector(String name, int number) {
            this.vectors.put(name, number);
            return this;
        }

        public Chip build() {
            if (vectors.isEmpty()) {
                vectors.put("RESET", 0);
            }
            return new Chip(this);
        }
    }
}
