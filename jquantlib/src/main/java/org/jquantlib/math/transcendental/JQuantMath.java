package org.jquantlib.math.transcendental;

/**
 * Bit-exact transcendental functions matching libc++/msun (FreeBSD libm).
 * Static facade — mirrors {@link java.lang.Math} API surface but produces
 * the same {@code long} bit pattern as {@code std::exp} etc. on Linux x86-64.
 *
 * <p>Why: JVM {@code Math.exp} carries up to ~1 ULP of slack relative to
 * libc++; that slack propagates through the QuantLib port and limits the
 * EXACT tolerance tier across transcendental-bearing tests (Phase 2f A13).
 * Phase 2i ports the underlying msun algorithms to remove the floor.
 *
 * <p>Algorithm sources are FreeBSD msun {@code e_*.c} / {@code s_*.c}
 * (BSD-licensed). Transcription is at the algorithm/sequence level —
 * Java's IEEE-754 binary64 arithmetic matches C's on basic ops, so the
 * same operation sequence produces the same bit pattern.
 */
public final class JQuantMath {

    private JQuantMath() {}

    /** Bit-exact {@code std::exp(x)} on Linux x86-64. */
    public static double exp(double x) {
        return ExpKernel.exp(x);
    }
}
