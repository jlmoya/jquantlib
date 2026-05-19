package org.jquantlib.math.transcendental;

/**
 * Bit-exact transcendental functions matching libc++ on Linux x86-64 and macOS arm64 (Apple libm). Static facade —
 * mirrors {@link java.lang.Math} API surface but produces the same {@code long} bit pattern as {@code std::exp} etc.
 *
 * <p>Why: JVM {@code Math.exp} carries up to ~1 ULP of slack relative to
 * libc++; that slack propagates through the QuantLib port and limits the EXACT tolerance tier across
 * transcendental-bearing tests (Phase 2f A13). Phase 2i ports correctly-rounded transcendental algorithms to remove the
 * floor.
 *
 * <p>Algorithm source: <a href="https://core-math.gitlabpages.inria.fr/">CORE-MATH</a>
 * (Sibidanov et al., Inria; MIT-licensed). CORE-MATH provides correctly-rounded implementations of double-precision
 * transcendental functions across all IEEE-754 rounding modes. Per-primitive attribution and per-file licensing lives
 * in the corresponding kernel class Javadoc (e.g. {@link ExpKernel}).
 *
 * <p>Note: an earlier port draft used FreeBSD msun ({@code e_*.c} / {@code s_*.c},
 * BSD-licensed) — that algorithm family was discarded after Phase 2i WI-1.1 surfaced that msun has the same ~1 ULP
 * slack as JVM {@code Math.exp} and does NOT match libc++/Apple-libm. See {@code docs/migration/phase2i-design.md}
 * Addendum for the Option D pivot.
 */
public final class JQuantMath {

    private JQuantMath() {
    }

    /** Bit-exact {@code std::exp(x)} on Linux x86-64. */
    public static double exp(double x) {
        return ExpKernel.exp(x);
    }

    /**
     * Bit-exact correctly-rounded {@code cos(x)} matching CORE-MATH {@code cr_cos} across all IEEE-754 binary64 inputs
     * in round-to-nearest-even mode.
     */
    public static double cos(double x) {
        return SinCosKernel.cos(x);
    }

    /**
     * Bit-exact correctly-rounded {@code sin(x)} matching CORE-MATH {@code cr_sin} across all IEEE-754 binary64 inputs
     * in round-to-nearest-even mode.
     */
    public static double sin(double x) {
        return SinCosKernel.sin(x);
    }

    /**
     * Bit-exact correctly-rounded {@code log(x)} matching CORE-MATH {@code cr_log} across all IEEE-754 binary64 inputs
     * in round-to-nearest-even mode.
     */
    public static double log(double x) {
        return LogKernel.log(x);
    }

    /**
     * Pure-Java port of CORE-MATH {@code cr_pow(x, y)}.
     *
     * <p><b>Phase 2n A.1 status — partial:</b> specials and IEEE-754
     * dispatch (NaN, ±inf, ±0, sign-prop, integer-y discrimination) are bit-exact against cr_pow. The non-special
     * finite path currently delegates to {@link Math#pow}, pending the 3-stage Ziv loop port (q_1/p_1, q_2/p_2 dint64,
     * q_3/p_3 qint64) plus exact_pow rounding-boundary detection. See {@link PowKernel} Javadoc for the staging plan.
     */
    public static double pow(double x, double y) {
        return PowKernel.pow(x, y);
    }
}
