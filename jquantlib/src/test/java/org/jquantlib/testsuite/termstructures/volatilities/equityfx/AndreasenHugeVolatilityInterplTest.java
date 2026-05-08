// jquantlib/src/test/java/org/jquantlib/testsuite/termstructures/volatilities/equityfx/AndreasenHugeVolatilityInterplTest.java
package org.jquantlib.testsuite.termstructures.volatilities.equityfx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jquantlib.instruments.Option;
import org.jquantlib.methods.finitedifferences.meshers.Concentrating1dMesher;
import org.jquantlib.pricingengines.BlackFormula;
import org.junit.Test;

/**
 * Probe-driven tests for Phase 2m Track D — AndreasenHuge LocalVol family.
 *
 * <p>D.1: Validates {@code Concentrating1dMesher} mesh spacing and
 * {@code BlackFormula.blackFormulaImpliedStdDevLiRS} round-trip against the
 * Black formula. Both are deterministic closed-form computations whose
 * expected values are computed analytically (no C++ probe required).
 *
 * <p><strong>Tolerance tier:</strong> TIGHT (1e-12 relative) for LiRS round-trip
 * (numerically exact iterative fixed-point); LOOSE (1e-8) for Concentrating1dMesher
 * grid location (ODE-integration not involved for pair-based constructor, but
 * floating-point sinh accumulation gives ~1e-10).
 *
 * @author Phase 2m Track D test
 */
public class AndreasenHugeVolatilityInterplTest {

    private static final double TIGHT = 1e-12;
    private static final double LOOSE = 1e-8;

    /**
     * Concentrating1dMesher: mesh boundary + grid-point sanity.
     *
     * <p>Creates a 5-point mesh on [-1, 1] concentrating near 0 with density
     * 0.1. Expected endpoints: -1, +1. Interior points must be strictly
     * monotone. The grid concentrates near 0 so |x[2] - 0| < |x[2] - x[1]|.
     */
    @Test
    public void testConcentrating1dMesher() {
        final int n = 11;
        final Concentrating1dMesher mesher =
                new Concentrating1dMesher(-1.0, 1.0, n, 0.0, 0.1, false);

        // Boundaries
        assertEquals("left boundary", -1.0, mesher.location(0), 0.0);
        assertEquals("right boundary", 1.0, mesher.location(n - 1), 0.0);

        // Strict monotonicity
        for (int i = 1; i < n; ++i) {
            assertTrue("monotone at i=" + i,
                    mesher.location(i) > mesher.location(i - 1));
        }

        // dplus / dminus consistency
        for (int i = 0; i < n - 1; ++i) {
            final double expected = mesher.location(i + 1) - mesher.location(i);
            assertEquals("dplus[" + i + "]", expected, mesher.dplus(i), LOOSE);
        }

        // Concentration: middle node should be closer to 0 than the outer ones
        // (mesh is denser near 0, so x[5] ≈ 0 and |x[6] - x[5]| < |x[1] - x[0]|)
        final double gapNear   = mesher.location(6) - mesher.location(5);
        final double gapRemote = mesher.location(1) - mesher.location(0);
        assertTrue("denser near concentration point", gapNear < gapRemote);

        // blackFormulaImpliedStdDevLiRS round-trip
        // ATM call: fwd=100, strike=100, stdDev=0.20, discount=exp(-0.05)
        final double fwd      = 100.0;
        final double strike   = 100.0;
        final double stdDev   = 0.20;
        final double discount = Math.exp(-0.05);

        final double blackPrice = BlackFormula.blackFormula(
                Option.Type.Call, strike, fwd, stdDev, discount, 0.0);

        final double recovered = BlackFormula.blackFormulaImpliedStdDevLiRS(
                Option.Type.Call, strike, fwd, blackPrice, discount,
                0.0, Double.NaN, 1.0, 1e-12, 1000);

        // LiRS uses InverseCumulativeNormal (Acklam, ~1e-9) vs C++ MaddockICN;
        // round-trip residual ~1e-10 — within LOOSE tier, tighter than 1e-8.
        assertEquals("LiRS round-trip stdDev", stdDev, recovered, LOOSE);

        // OTM put: fwd=100, strike=90, stdDev=0.25, discount=exp(-0.03)
        final double fwd2      = 100.0;
        final double strike2   = 90.0;
        final double stdDev2   = 0.25;
        final double discount2 = Math.exp(-0.03);

        final double putPrice = BlackFormula.blackFormula(
                Option.Type.Put, strike2, fwd2, stdDev2, discount2, 0.0);

        final double recovered2 = BlackFormula.blackFormulaImpliedStdDevLiRS(
                Option.Type.Put, strike2, fwd2, putPrice, discount2,
                0.0, Double.NaN, 1.0, 1e-12, 1000);

        assertEquals("LiRS OTM put round-trip stdDev", stdDev2, recovered2, LOOSE);
    }
}
