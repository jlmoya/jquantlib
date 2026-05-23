/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License. See LICENSE.TXT in the
 project root for licence terms.
*/

package org.jquantlib.testsuite.termstructures.volatilities;

import org.jquantlib.QL;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.instruments.Option;
import org.jquantlib.math.Constants;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.termstructures.volatilities.AtmAdjustedSmileSection;
import org.jquantlib.termstructures.volatilities.FlatSmileSection;
import org.jquantlib.termstructures.volatilities.SmileSection;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link AtmAdjustedSmileSection}.
 *
 * <p>Because the class is a pure coordinate-translation wrapper over an
 * already-tested {@link SmileSection}, these tests cross-validate the
 * wrapper by checking <b>algebraic equivalence</b> against the source
 * smile at the adjusted strike — no per-test JSON probe is needed.
 * Equality is exact (bit-exact) because all wrapper methods are
 * straight delegations to the source after a simple additive shift.
 *
 * <p>The four scenarios mirror the C++ usage patterns of
 * {@code AtmAdjustedSmileSection}:
 * <ul>
 *   <li>A — null ATM, no recenter ⇒ {@code f_ = source.atmLevel()}, adjustment=0</li>
 *   <li>B — explicit ATM, no recenter ⇒ {@code f_ = atm}, adjustment=0</li>
 *   <li>C — explicit ATM, recenter   ⇒ adjustment = source.atmLevel() − atm</li>
 *   <li>D — null ATM, recenter on shifted source — adjustment=0 (still
 *       inherits source ATM, recenter has no effect when both are equal)</li>
 * </ul>
 *
 * <p>L2-C Phase 2 forward closure.
 */
public class AtmAdjustedSmileSectionTest {

    private static final double T = 367.0 / 365.0;
    private static final Actual365Fixed DC = new Actual365Fixed();
    /** Tight tier — wrapper is exact algebra over source delegation. */
    private static final double TOL_EXACT = 0.0;

    public AtmAdjustedSmileSectionTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    // ------------------------------------------------------------------
    // Scenario builders
    // ------------------------------------------------------------------

    /** Source: flat 20% vol, ATM=5%. */
    private static FlatSmileSection sourceA() {
        return new FlatSmileSection(T, 0.20, DC, 0.05);
    }

    /** Source: flat 18% vol, ATM=3%, shift=0.01. */
    private static FlatSmileSection sourceD() {
        return new FlatSmileSection(1.0, 0.18, DC, 0.03, VolatilityType.ShiftedLognormal, 0.01);
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    public void testScenarioA_inheritedAtm_noRecenter() {
        final FlatSmileSection src = sourceA();
        final AtmAdjustedSmileSection sec = new AtmAdjustedSmileSection(src);

        assertEquals(src.atmLevel(), sec.atmLevel(), TOL_EXACT);
        assertEquals(0.0, sec.adjustment(), TOL_EXACT);
        assertSame(src, sec.source());

        // Exact delegation at multiple strikes.
        for (final double k : new double[] {0.01, 0.03, 0.05, 0.07, 0.10}) {
            assertEquals("vol@" + k, src.volatility(k), sec.volatility(k), TOL_EXACT);
            assertEquals("var@" + k, src.variance(k), sec.variance(k), TOL_EXACT);
        }
    }

    @Test
    public void testScenarioB_explicitAtm_noRecenter() {
        final FlatSmileSection src = sourceA();
        // explicit atm = 0.06, no recenter → adjustment = 0, f_ = 0.06
        final AtmAdjustedSmileSection sec = new AtmAdjustedSmileSection(src, 0.06, false);

        assertEquals(0.06, sec.atmLevel(), TOL_EXACT);
        assertEquals(0.0, sec.adjustment(), TOL_EXACT);

        // No recenter → strike not translated, source still queried at k.
        for (final double k : new double[] {0.01, 0.03, 0.06, 0.10}) {
            assertEquals(src.volatility(k), sec.volatility(k), TOL_EXACT);
        }
    }

    @Test
    public void testScenarioC_explicitAtm_recenter() {
        final FlatSmileSection src = sourceA(); // ATM=0.05
        // explicit atm = 0.06, recenter → adjustment = 0.05 − 0.06 = −0.01
        final AtmAdjustedSmileSection sec = new AtmAdjustedSmileSection(src, 0.06, true);

        assertEquals(0.06, sec.atmLevel(), TOL_EXACT);
        assertEquals(-0.01, sec.adjustment(), 1e-15);

        // Recenter ⇒ source queried at k + adjustment = k − 0.01.
        for (final double k : new double[] {0.03, 0.05, 0.07, 0.10}) {
            assertEquals("vol@" + k, src.volatility(k - 0.01), sec.volatility(k), TOL_EXACT);
            assertEquals("var@" + k, src.variance(k - 0.01), sec.variance(k), TOL_EXACT);
        }
    }

    @Test
    public void testScenarioD_shiftedSource_inheritedAtm() {
        final FlatSmileSection src = sourceD(); // ATM=0.03, shift=0.01
        final AtmAdjustedSmileSection sec = new AtmAdjustedSmileSection(src);

        // f_ inherits source.atmLevel() (since atm is NULL_REAL).
        assertEquals(src.atmLevel(), sec.atmLevel(), TOL_EXACT);
        assertEquals(0.0, sec.adjustment(), TOL_EXACT);

        // Inherits volatility type and shift from source.
        assertEquals(src.volatilityType(), sec.volatilityType());
        assertEquals(src.shift(), sec.shift(), TOL_EXACT);

        // Strike grid covers a few in-bounds points (shifted lognormal
        // permits strike + shift > 0; tests at 0.02, 0.04 still meet that).
        for (final double k : new double[] {0.02, 0.03, 0.04, 0.05}) {
            assertEquals(src.volatility(k), sec.volatility(k), TOL_EXACT);
        }
    }

    @Test
    public void testRecenterIneffectiveWhenAtmEqualsSource() {
        final FlatSmileSection src = sourceA(); // ATM=0.05
        // Recenter requested but atm = source.atmLevel() → adjustment = 0.
        final AtmAdjustedSmileSection sec = new AtmAdjustedSmileSection(src, 0.05, true);
        assertEquals(0.0, sec.adjustment(), TOL_EXACT);
        assertEquals(src.volatility(0.04), sec.volatility(0.04), TOL_EXACT);
    }

    @Test
    public void testNullSentinelHandling() {
        final FlatSmileSection src = sourceA();
        // NaN sentinel
        final AtmAdjustedSmileSection s1 = new AtmAdjustedSmileSection(src, Double.NaN, false);
        assertEquals(src.atmLevel(), s1.atmLevel(), TOL_EXACT);

        // NULL_REAL sentinel
        final AtmAdjustedSmileSection s2 = new AtmAdjustedSmileSection(src, Constants.NULL_REAL, false);
        assertEquals(src.atmLevel(), s2.atmLevel(), TOL_EXACT);
    }

    @Test
    public void testInspectorForwarding() {
        final FlatSmileSection src = sourceA();
        final AtmAdjustedSmileSection sec = new AtmAdjustedSmileSection(src);

        // minStrike / maxStrike / exerciseTime / dayCounter all match.
        assertEquals(src.minStrike(), sec.minStrike(), TOL_EXACT);
        assertEquals(src.maxStrike(), sec.maxStrike(), TOL_EXACT);
        assertEquals(src.exerciseTime(), sec.exerciseTime(), TOL_EXACT);
        assertNotNull(sec.dayCounter());
    }

    @Test
    public void testOptionAndDigitalPriceForwarding() {
        final FlatSmileSection src = sourceA(); // ATM=0.05
        final AtmAdjustedSmileSection sec = new AtmAdjustedSmileSection(src, 0.06, true);
        // adjustment = −0.01
        // The wrapper's optionPrice delegates to source.optionPrice at the
        // adjusted strike (k + adjustment). The two call paths differ only in
        // the order of additions performed in floating-point arithmetic:
        //   direct:   source.optionPrice(k - 0.01, ...)
        //   wrapper:  source.optionPrice(k + (-0.01), ...)
        // These can differ by 1-2 ULP; for digitalOptionPrice this gets
        // amplified by 1/gap (~1e5) because of the centred FD. Use loose
        // tier tolerances (1e-7 abs) — this still demonstrates the
        // wrapper delegates correctly to the source.
        final double k = 0.07;
        final double TOL_LOOSE = 1.0e-7;
        for (final Option.Type t : new Option.Type[] {Option.Type.Call, Option.Type.Put}) {
            assertEquals(
                    "optionPrice@" + k + "/" + t,
                    src.optionPrice(k - 0.01, t, 1.0),
                    sec.optionPrice(k, t, 1.0),
                    TOL_LOOSE);
            assertEquals(
                    "digital@" + k + "/" + t,
                    src.digitalOptionPrice(k - 0.01, t, 1.0, 1e-5),
                    sec.digitalOptionPrice(k, t, 1.0, 1e-5),
                    TOL_LOOSE);
        }
    }

    @Test
    public void testIsSmileSection() {
        final FlatSmileSection src = sourceA();
        final AtmAdjustedSmileSection sec = new AtmAdjustedSmileSection(src);
        assertTrue(sec instanceof SmileSection);
        assertFalse(sec.adjustment() != 0.0); // sanity
    }
}
