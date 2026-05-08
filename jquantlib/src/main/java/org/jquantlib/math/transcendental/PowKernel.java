package org.jquantlib.math.transcendental;

/**
 * Pure-Java port of CORE-MATH's correctly-rounded {@code cr_pow(double, double)}.
 *
 * <p><b>Status:</b> Phase 2n A.1 partial — specials and IEEE-754
 * dispatch are bit-exact against CORE-MATH cr_pow; non-special finite
 * arguments currently delegate to {@link Math#pow(double, double)} as a
 * placeholder. The full 3-stage Ziv loop (q_1/p_1 fast path, q_2/p_2
 * Dint64 path, q_3/p_3 Qint64 path) plus exact_pow rounding-boundary
 * detection is the remaining work — see Phase 2n A.1 follow-up.
 *
 * <p>Source: CORE-MATH {@code src/binary64/pow/pow.c} (Tom Hubrecht and
 * Paul Zimmermann; CERN/INRIA; MIT-licensed). Algorithm reference is
 * Hubrecht, Jeannerod, Zimmermann, "Towards a correctly-rounded and fast
 * power function in binary64 arithmetic", ARITH 2023, with detailed
 * proofs in HAL hal-04159652.
 *
 * <p>Specials handling mirrors the C source verbatim — IEEE 754-2019
 * Section 9.2.1 dispatch on (x, y) including ±0, ±inf, NaN, integer-y,
 * odd-integer-y discrimination, and pow(1, NaN) = 1 / pow(NaN, 0) = 1
 * exemptions.
 *
 * <p>For the unported finite path, we currently call {@code Math.pow}.
 * This means {@code JQuantMath.pow} can drift from cr_pow by up to a few
 * ULPs in the worst-rounding cases (~2^-11.5 of inputs trigger the
 * accurate-path fallback in CORE-MATH; JVM's Math.pow has its own ~1 ULP
 * floor). The test suite at {@code JQuantMathPowTest} validates the
 * specials category bit-exact and is structured to incrementally add
 * categories as the full port lands.
 */
final class PowKernel {

    private PowKernel() {}

    /** IEEE-754 double exponent mask (bits 52-62). */
    private static final long EXP_MASK = 0x7ff0000000000000L;

    /**
     * Pure-Java cr_pow(x, y).
     *
     * <p><b>Specials path (bit-exact):</b> per pow.c lines 1495-1702.
     *
     * <p><b>Finite path:</b> defers to {@link Math#pow} pending the
     * 3-stage Ziv loop port (Phase 2n A.1 follow-up).
     */
    static double pow(double x, double y) {
        final long xb = Double.doubleToRawLongBits(x);
        final long yb = Double.doubleToRawLongBits(y);

        // ============================================================
        // Specials dispatch — pow.c:1502-1612
        // ============================================================
        // Detect "either operand is non-finite" via the exponent-field test
        // used in the C source: u >= 0x7ff0000000000000ULL.
        if (Long.compareUnsigned(xb, 0x7ff0000000000000L) >= 0
                || Long.compareUnsigned(yb, 0x7ff0000000000000L) >= 0) {
            // x is NaN
            if (Double.isNaN(x)) {
                // pow(x, ±0) = 1 if x is not signaling
                if (y == 0.0 && !isSignaling(xb)) return 1.0;
                // pow(sNaN, y) = qNaN (and propagate). Returning x+x quiets sNaN.
                return x + x;
            }
            // y is NaN
            if (Double.isNaN(y)) {
                if (x == 1.0 && !isSignaling(yb)) return 1.0;
                return y + y;
            }

            // x = +inf
            if (xb == 0x7ff0000000000000L) {
                if (y == 0.0) return 1.0;
                if (y < 0.0) return 0.0;
                if (y > 0.0) return Double.POSITIVE_INFINITY;
            }
            // x = -inf
            if (xb == 0xfff0000000000000L) {
                final boolean yIsOddInt = isInt(y) && !isInt(y * 0.5);
                if (yIsOddInt) {
                    if (y < 0.0) return -0.0;
                    return Double.NEGATIVE_INFINITY;
                }
                if (y < 0.0) return 0.0;
                if (y > 0.0) return Double.POSITIVE_INFINITY;
            }

            // y = +inf
            if (yb == 0x7ff0000000000000L) {
                if (x == 0.0) return 0.0;
                if (x == -1.0 || x == 1.0) return 1.0;
                if (-1.0 < x && x < 1.0) return 0.0;
                return Double.POSITIVE_INFINITY; // |x| > 1
            }
            // y = -inf
            if (yb == 0xfff0000000000000L) {
                if (x == 0.0) return Double.POSITIVE_INFINITY;
                if (x == -1.0 || x == 1.0) return 1.0;
                if (-1.0 < x && x < 1.0) return Double.POSITIVE_INFINITY;
                return 0.0; // |x| > 1
            }
        }
        // From here, x and y are finite.

        // ============================================================
        // Negative or zero base — pow.c:1615-1702
        // ============================================================
        if (x <= 0.0) {
            if (y == 0.0) return 1.0;

            // x = +0.0
            if (xb == 0x0L) {
                final boolean yIsOddInt = isInt(y) && !isInt(y * 0.5);
                if (yIsOddInt) {
                    return (y < 0.0) ? Double.POSITIVE_INFINITY : 0.0;
                }
                // y is positive (non-integer or even integer)
                if (y > 0.0) return 0.0;
                // y is negative (even integer or non-integer)
                return Double.POSITIVE_INFINITY;
            }
            // x = -0.0
            if (xb == 0x8000000000000000L) {
                final boolean yIsOddInt = isInt(y) && !isInt(y * 0.5);
                if (yIsOddInt) {
                    return (y < 0.0) ? Double.NEGATIVE_INFINITY : -0.0;
                }
                if (y > 0.0) return 0.0;
                return Double.POSITIVE_INFINITY;
            }
            // x < 0 finite
            if (!isInt(y)) {
                return Double.NaN;
            }
            // y is an integer; result sign = (-1)^y. For |y| >= 2^53 the
            // parity bit is unreliable in C int64_t conversion — match
            // pow.c's heuristic: y_parity = 0 (even) when |y| >= 2^53.
            final double absY = Math.abs(y);
            final long yParity = (absY >= 0x1p53) ? 0L : ((long) y) & 0x1L;
            final double sign = (yParity == 0L) ? 1.0 : -1.0;
            return sign * powPositive(-x, y);
        }

        return powPositive(x, y);
    }

    /**
     * Compute x^y for x > 0 and finite y. Currently delegates to
     * {@link Math#pow} pending the full ZIV-loop port. For the specials
     * subset already handled, this is unreachable — we always either
     * dispatch a special, return early, or recurse with x &gt; 0.
     */
    private static double powPositive(double x, double y) {
        // Easy cases handled by pow.c's explicit short-circuits (lines 1772-1793).
        if (y == 1.0) return x;
        if (y == 0.0) return 1.0;
        if (y == 0.5) return Math.sqrt(x);
        // y == 2.0: pow.c does x*x but with explicit overflow/underflow errno
        // semantics. Math.pow matches for nominal cases.

        // TODO Phase 2n A.1: full 3-stage Ziv loop with Dint64/Qint64
        //   intermediate. Until then, defer to JVM's Math.pow. This is a
        //   ~1 ULP-slack approximation for the bulk of inputs and is NOT
        //   guaranteed bit-exact against CORE-MATH cr_pow.
        return Math.pow(x, y);
    }

    /** True iff x is an integer (including ±0). Matches pow.c is_int. */
    private static boolean isInt(double x) {
        return x == Math.rint(x);
    }

    /**
     * Returns true if the encoded NaN is signaling (bit 51 = 0).
     * Matches pow.h's {@code is_signaling}.
     */
    private static boolean isSignaling(long bits) {
        // x is sNaN iff exponent==0x7ff, mantissa!=0, and the top mantissa bit (bit 51) is 0.
        return ((bits & EXP_MASK) == EXP_MASK)
            && ((bits & 0x000fffffffffffffL) != 0L)
            && ((bits & (1L << 51)) == 0L);
    }
}
