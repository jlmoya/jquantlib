// jquantlib/src/test/java/org/jquantlib/testsuite/util/Tolerance.java
package org.jquantlib.testsuite.util;

/**
 * Tolerance tiers for Phase 1 cross-validation checks, per
 * docs/migration/phase1-design.md §4.2. Use the static helpers from JUnit
 * tests to assert Java-vs-C++ agreement.
 *
 * <p>All comparisons use the additive hybrid form
 * {@code |a - b| < abs_tol + rel_tol * |cpp|}, which is continuous across
 * the near-zero regime and matches the canonical scientific-computing
 * pattern (numpy.isclose, BOOST_CHECK_CLOSE, etc.). Near zero the absolute
 * term dominates; for large references the relative term dominates.
 *
 * <p>Tiers:
 * <ul>
 *   <li>{@link #exact} — integer/date arithmetic, calendar logic. Bit-exact.</li>
 *   <li>{@link #tight} — closed-form formulas. abs 1e-14 + rel 1e-12.</li>
 *   <li>{@link #loose} — Monte Carlo, numerical integration, root-finding,
 *       PDE solvers. abs 1e-8 + rel 1e-8.</li>
 * </ul>
 *
 * <p>Per-test exceptions are permitted via {@link #within} but must be
 * inline-justified with a code comment citing the reason (per design G1
 * quality gate).
 */
public final class Tolerance {

    public static final double TIGHT_REL = 1.0e-12;
    public static final double TIGHT_ABS = 1.0e-14;
    public static final double LOOSE_REL = 1.0e-8;
    public static final double LOOSE_ABS = 1.0e-8;

    private Tolerance() {}

    /** Bit-exact check — for integers, dates, enums, and anything Boolean. */
    public static boolean exact(long javaValue, long cppValue) {
        return javaValue == cppValue;
    }

    /** Bit-exact double check — only for values that are mathematically exact. */
    public static boolean exact(double javaValue, double cppValue) {
        return Double.compare(javaValue, cppValue) == 0;
    }

    /** Tight tier: absolute 1e-14 + relative 1e-12 (hybrid). */
    public static boolean tight(double javaValue, double cppValue) {
        return Math.abs(javaValue - cppValue)
                < TIGHT_ABS + TIGHT_REL * Math.abs(cppValue);
    }

    /** Loose tier: absolute 1e-8 + relative 1e-8 (hybrid). */
    public static boolean loose(double javaValue, double cppValue) {
        return Math.abs(javaValue - cppValue)
                < LOOSE_ABS + LOOSE_REL * Math.abs(cppValue);
    }

    /**
     * Per-test tier loosening — use when an algorithm's inherent error
     * forces a tolerance weaker than {@link #loose}. Uses the same additive
     * hybrid form {@code |a - b| < tol + tol * |cpp|}.
     *
     * <p>The {@code justification} string is not enforced at runtime; it
     * exists to remind test authors that looser tolerances require an
     * inline explanation.
     */
    public static boolean within(double javaValue, double cppValue,
                                 double tol, String justification) {
        return Math.abs(javaValue - cppValue) < tol + tol * Math.abs(cppValue);
    }
}
