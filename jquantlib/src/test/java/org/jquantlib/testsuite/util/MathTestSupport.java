package org.jquantlib.testsuite.util;

/**
 * Helpers for bit-pattern equality checks on {@code double} values.
 * Used by transcendental EXACT-tier tests where 1-ULP differences propagate
 * through downstream code in ways that can flip later branches.
 *
 * <p>NaN handling: IEEE-754 specifies NaN-ness but not payload bits;
 * libc++/JVM/libm produce different NaN payloads for the same arithmetic.
 * {@link #assertBitsEqual} canonicalises both operands to a single NaN
 * bit pattern before comparison so payload divergence (per Phase 2i A18)
 * does not cause spurious failures.
 *
 * <p>±0 distinction: preserved — {@code -0.0} and {@code +0.0} have
 * different bit patterns and are reported as a mismatch.
 */
public final class MathTestSupport {

    private MathTestSupport() {}

    /** Canonical NaN bits used after payload normalisation. */
    private static final long CANONICAL_NAN_BITS = 0x7ff8000000000000L;

    /**
     * Assert that {@code actual} has the same IEEE-754 bit pattern as
     * {@code expected}, after NaN-payload normalisation.
     *
     * @throws AssertionError on mismatch with hex bits in the message
     */
    public static void assertBitsEqual(double expected, double actual) {
        final long e = canonicalise(Double.doubleToRawLongBits(expected));
        final long a = canonicalise(Double.doubleToRawLongBits(actual));
        if (e != a) {
            throw new AssertionError(String.format(
                "bit mismatch: expected=%s (0x%016x) actual=%s (0x%016x)",
                expected, e, actual, a));
        }
    }

    /**
     * Variant taking the expected value as a raw bit pattern (the form
     * stored in probe JSON). Equivalent to
     * {@code assertBitsEqual(Double.longBitsToDouble(expectedBits), actual)}
     * but avoids NaN-payload loss through the {@code double} round-trip.
     */
    public static void assertBitsEqual(long expectedBits, double actual) {
        final long e = canonicalise(expectedBits);
        final long a = canonicalise(Double.doubleToRawLongBits(actual));
        if (e != a) {
            throw new AssertionError(String.format(
                "bit mismatch: expectedBits=0x%016x actualBits=0x%016x (actual=%s)",
                e, a, actual));
        }
    }

    /** Map any NaN bit pattern to {@link #CANONICAL_NAN_BITS}; pass through otherwise. */
    private static long canonicalise(long bits) {
        // NaN if exponent == 0x7ff and mantissa != 0
        if ((bits & 0x7ff0000000000000L) == 0x7ff0000000000000L
            && (bits & 0x000fffffffffffffL) != 0L) {
            return CANONICAL_NAN_BITS;
        }
        return bits;
    }

    /**
     * Non-throwing variant: returns {@code true} iff {@code actual} has the
     * same IEEE-754 bit pattern as {@code expectedBits} after NaN-payload
     * normalisation.  Use this instead of {@link #assertBitsEqual(long,double)}
     * when you want to collect failures rather than throw on the first one.
     */
    public static boolean bitsEqual(long expectedBits, double actual) {
        final long e = canonicalise(expectedBits);
        final long a = canonicalise(Double.doubleToRawLongBits(actual));
        return e == a;
    }

    /**
     * Parse a probe-JSON hex bit string ({@code "0x..."}) into a {@code long}.
     */
    public static long parseHexBits(String hex) {
        if (hex == null || !hex.startsWith("0x")) {
            throw new IllegalArgumentException("expected hex bits like '0x...': " + hex);
        }
        return Long.parseUnsignedLong(hex.substring(2), 16);
    }
}
