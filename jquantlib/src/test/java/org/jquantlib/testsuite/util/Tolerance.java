package org.jquantlib.testsuite.util;

/**
 * Tolerance tiers for Phase 1 cross-validation checks, per
 * docs/migration/phase1-design.md §4.2. Use the static helpers from JUnit
 * tests to assert Java-vs-C++ agreement.
 *
 * <p>Tiers:
 * <ul>
 *   <li>{@link #exact(long, long)} — integer/date arithmetic, calendar logic. Bit-exact.</li>
 *   <li>{@link #tight(double, double)} — closed-form formulas. 1e-12 relative; 1e-14 absolute
 *       when the reference is below 1e-2.</li>
 *   <li>{@link #loose(double, double)} — Monte Carlo, numerical integration, root-finding,
 *       PDE solvers. 1e-8 relative.</li>
 * </ul>
 *
 * <p>Per-test exceptions are permitted but must be inline-justified with a
 * code comment citing the reason (per design G1 quality gate).
 */
public final class Tolerance {

    public static final double TIGHT_REL = 1.0e-12;
    public static final double TIGHT_ABS_NEAR_ZERO = 1.0e-14;
    public static final double TIGHT_NEAR_ZERO_THRESHOLD = 1.0e-2;
    public static final double LOOSE_REL = 1.0e-8;

    private Tolerance() {}

    /** Bit-exact check — for integers, dates, enums, and anything Boolean. */
    public static boolean exact(long javaValue, long cppValue) {
        return javaValue == cppValue;
    }

    /** Bit-exact double check — only for values that are mathematically exact. */
    public static boolean exact(double javaValue, double cppValue) {
        return Double.compare(javaValue, cppValue) == 0;
    }

    /** Tight tier: 1e-12 relative, 1e-14 absolute when reference near zero. */
    public static boolean tight(double javaValue, double cppValue) {
        final double absRef = Math.abs(cppValue);
        if (absRef < TIGHT_NEAR_ZERO_THRESHOLD) {
            return Math.abs(javaValue - cppValue) < TIGHT_ABS_NEAR_ZERO;
        }
        return Math.abs(javaValue - cppValue) / absRef < TIGHT_REL;
    }

    /** Loose tier: 1e-8 relative (with absolute fallback at the same level). */
    public static boolean loose(double javaValue, double cppValue) {
        final double absRef = Math.abs(cppValue);
        if (absRef < LOOSE_REL) {
            return Math.abs(javaValue - cppValue) < LOOSE_REL;
        }
        return Math.abs(javaValue - cppValue) / absRef < LOOSE_REL;
    }

    /**
     * Per-test tier loosening — use when an algorithm's inherent error
     * forces a tolerance weaker than {@link #loose}. The justification string
     * is not enforced at runtime; it exists to remind test authors that
     * looser tolerances require an inline explanation.
     */
    public static boolean within(double javaValue, double cppValue,
                                 double relTol, String justification) {
        final double absRef = Math.abs(cppValue);
        if (absRef < relTol) {
            return Math.abs(javaValue - cppValue) < relTol;
        }
        return Math.abs(javaValue - cppValue) / absRef < relTol;
    }
}
