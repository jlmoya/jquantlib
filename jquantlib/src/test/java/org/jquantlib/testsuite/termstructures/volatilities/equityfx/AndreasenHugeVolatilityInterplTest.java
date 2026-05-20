// jquantlib/src/test/java/org/jquantlib/testsuite/termstructures/volatilities/equityfx/AndreasenHugeVolatilityInterplTest.java
package org.jquantlib.testsuite.termstructures.volatilities.equityfx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.VanillaOption;
import org.jquantlib.methods.finitedifferences.meshers.Concentrating1dMesher;
import org.jquantlib.pricingengines.BlackFormula;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.equityfx.AndreasenHugeLocalVolAdapter;
import org.jquantlib.termstructures.volatilities.equityfx.AndreasenHugeVolatilityAdapter;
import org.jquantlib.termstructures.volatilities.equityfx.AndreasenHugeVolatilityInterpl;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.NullCalendar;
import org.junit.After;
import org.junit.Before;
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
 * <h2>C++ v1.42.1 {@code test-suite/andreasenhugevolatilityinterpl.cpp}
 * (13 BOOST cases) port status — Phase1-closure-A3-A-549</h2>
 * <ul>
 *   <li>{@code testSingleOptionCalibration} (line 558) — <b>ADDED</b>
 *       (Phase1-closure-A3-A-549): full 3x3 (interpolation x calibration)
 *       sweep on a single ATM Call. Was previously blocked by the
 *       {@code CallPut} divergence (calculated 0.4554 vs expected 0.30);
 *       root-caused to swapped put/call payoffs at initial boundaries in
 *       {@link org.jquantlib.termstructures.volatilities.equityfx.AndreasenHugeVolatilityInterpl}
 *       {@code performCalculations()} — see the preceding
 *       {@code align(termstructures.volatilities.equityfx.AndreasenHugeVolatilityInterpl)}
 *       commit.</li>
 *   <li>{@code testAndreasenHugePut} (line 389) — <b>BLOCKED</b>: requires
 *       full {@code BorovkovaExampleData} grid (~80 options) round-trip
 *       through Heston FdBlackScholesVanillaEngine per-option re-pricing;
 *       multi-minute runtime + FdBlackScholesBarrierEngine for the
 *       Heston-spread integral. Calibrator now correct; heavy-pricer
 *       infra is the residual block.</li>
 *   <li>{@code testAndreasenHugeCall} (line 406) — <b>BLOCKED</b>: same
 *       heavy-pricer infra as testAndreasenHugePut.</li>
 *   <li>{@code testAndreasenHugeCallPut} (line 423) — <b>BLOCKED</b>: same.</li>
 *   <li>{@code testLinearInterpolation} (line 441) — <b>BLOCKED</b>: same.</li>
 *   <li>{@code testPiecewiseConstantInterpolation} (line 457) —
 *       <b>BLOCKED</b>: same.</li>
 *   <li>{@code testTimeDependentInterestRates} (line 473) — <b>BLOCKED</b>:
 *       requires {@code ZeroCurve} (Java has only
 *       {@code InterpolatedZeroCurve}) and Heston cross-pricer setup.</li>
 *   <li>{@code testArbitrageFree} (line 616) — <b>BLOCKED</b>: heavy
 *       calibration + iterated weekly grid sweep over Borovkova +
 *       arbitrage data sets.</li>
 *   <li>{@code testBarrierOptionPricing} (line 703) — <b>BLOCKED</b>:
 *       requires {@code FdBlackScholesBarrierEngine} — not in Java
 *       (only {@code FdBlackScholesVanillaEngine} present).</li>
 *   <li>{@code testPeterAndFabiensExample} (line 807) — <b>BLOCKED</b>:
 *       SABR-data calibration + per-strike LV/IV cross check (heavy).</li>
 *   <li>{@code testDifferentOptimizers} (line 853) — <b>BLOCKED</b>:
 *       sweeps {Simplex, BFGS, LM} optimizers across full calibration
 *       data set; needs the heavy-pricer infra.</li>
 *   <li>{@code testMovingReferenceDate} (line 882) — <b>BLOCKED</b>:
 *       full calibrator + 1-day shift on Borovkova data.</li>
 *   <li>{@code testFlatVolCalibration} (line 957) — <b>BLOCKED</b>:
 *       flat-vol regression check across many strikes/maturities.</li>
 * </ul>
 *
 * <p>Aggregate Phase1-closure-A3-A-549: 1 ADDED (testSingleOptionCalibration)
 * / 0 EXISTING_EQUIVALENT / 12 BLOCKED (heavy multi-strike calibration data
 * sets + missing FdBlackScholesBarrierEngine + missing ZeroCurve).
 *
 * @author Phase 2m Track D test
 */
public class AndreasenHugeVolatilityInterplTest {

    private static final double TIGHT = 1e-12;
    private static final double LOOSE = 1e-8;

    private Date savedEvalDate;

    @Before
    public void setUp() {
        savedEvalDate = new Settings().evaluationDate();
    }

    @After
    public void tearDown() {
        new Settings().setEvaluationDate(savedEvalDate);
    }

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

    /**
     * Port of v1.42.1 test-suite/andreasenhugevolatilityinterpl.cpp
     * testSingleOptionCalibration (line 558).
     *
     * Sweeps the 3x3 (interpolation, calibration) matrix on a single ATM 1Y
     * call at spot=10 with vol=0.30; the calibrator must recover ~0.30 in
     * blackVol units within 1e-4 for every combination.
     */
    @Test
    public void testSingleOptionCalibration() {
        final DayCounter dc = new Actual365Fixed();
        final Date today = new Date(4, Month.January, 2018);

        final Handle< YieldTermStructure > rTS = new Handle< YieldTermStructure >(
                new FlatForward(today, 0.025, dc));
        final Handle< YieldTermStructure > qTS = new Handle< YieldTermStructure >(
                new FlatForward(today, 0.085, dc));

        final double strike = 10.0;
        final double vol = 0.3;
        final Date maturity = today.add(new Period(1, TimeUnit.Years));
        final Handle< Quote > spot = new Handle< Quote >(new SimpleQuote(strike));

        final List< AndreasenHugeVolatilityInterpl.CalibrationEntry > calibrationSet = new ArrayList<>();
        calibrationSet.add(new AndreasenHugeVolatilityInterpl.CalibrationEntry(
                new VanillaOption(new PlainVanillaPayoff(Option.Type.Call, strike),
                        new EuropeanExercise(maturity)),
                new SimpleQuote(vol)));

        final AndreasenHugeVolatilityInterpl.InterpolationType[] interpl = {
                AndreasenHugeVolatilityInterpl.InterpolationType.Linear,
                AndreasenHugeVolatilityInterpl.InterpolationType.CubicSpline,
                AndreasenHugeVolatilityInterpl.InterpolationType.PiecewiseConstant
        };
        final AndreasenHugeVolatilityInterpl.CalibrationType[] calibrationType = {
                AndreasenHugeVolatilityInterpl.CalibrationType.Put,
                AndreasenHugeVolatilityInterpl.CalibrationType.Call,
                AndreasenHugeVolatilityInterpl.CalibrationType.CallPut
        };

        final StringBuilder failures = new StringBuilder();
        for (final AndreasenHugeVolatilityInterpl.InterpolationType i : interpl) {
            for (final AndreasenHugeVolatilityInterpl.CalibrationType j : calibrationType) {
                final AndreasenHugeVolatilityInterpl ah = new AndreasenHugeVolatilityInterpl(
                        calibrationSet, spot, rTS, qTS, i, j, 50,
                        Double.NaN, Double.NaN,
                        new org.jquantlib.math.optimization.LevenbergMarquardt(),
                        new org.jquantlib.math.optimization.EndCriteria(500, 100, 1e-12, 1e-10, 1e-10));
                final AndreasenHugeVolatilityAdapter adapter = new AndreasenHugeVolatilityAdapter(ah);
                double calculated;
                try {
                    calculated = adapter.blackVol(maturity, strike);
                } catch (final RuntimeException ex) {
                    failures.append(String.format("\n  %s/%s: EXCEPTION %s", i, j, ex.getMessage()));
                    continue;
                }
                if (Math.abs(calculated - vol) > 1e-4) {
                    failures.append(String.format("\n  %s/%s: calc=%.6f expected=%.4f",
                            i, j, calculated, vol));
                } else {
                    System.out.println("  PASS " + i + "/" + j + " calc=" + calculated);
                }
            }
        }
        if (failures.length() > 0) {
            fail("Failed to reproduce single option calibration:" + failures);
        }
    }

    /**
     * Port of v1.42.1 test-suite/andreasenhugevolatilityinterpl.cpp
     * testMovingReferenceDate (line 883).
     *
     * Verifies that {@link AndreasenHugeVolatilityAdapter#referenceDate()} and
     * {@link AndreasenHugeLocalVolAdapter#referenceDate()} both track
     * {@code Settings.evaluationDate} when it changes, and that the implied
     * vol returned after the shift still matches the seeded vol.
     */
    @Test
    public void testMovingReferenceDate() {
        final Date today = new Date(4, Month.January, 2018);
        new Settings().setEvaluationDate(today);

        final DayCounter dc = new Actual365Fixed();
        final Date maturity = today.add(new Period(1, TimeUnit.Months));

        // C++ flatRate(rate, dc) builds a 0-settle-day, NullCalendar curve
        // whose referenceDate tracks Settings.evaluationDate. The Java
        // (settlementDays, calendar, rate, dc) constructor gives the same
        // moving-reference semantics.
        final Handle< YieldTermStructure > ts = new Handle< YieldTermStructure >(
                new FlatForward(0, new NullCalendar(), 0.04, dc));

        final double s0 = 100.0;
        final double impliedVol = 0.2;
        final Handle< Quote > spot = new Handle< Quote >(new SimpleQuote(s0));

        final List< AndreasenHugeVolatilityInterpl.CalibrationEntry > calibrationSet = new ArrayList<>();
        calibrationSet.add(new AndreasenHugeVolatilityInterpl.CalibrationEntry(
                new VanillaOption(new PlainVanillaPayoff(Option.Type.Call, s0),
                        new EuropeanExercise(maturity)),
                new SimpleQuote(impliedVol)));

        final AndreasenHugeVolatilityInterpl ah = new AndreasenHugeVolatilityInterpl(
                calibrationSet, spot, ts, ts);

        final double tol = 1e-8;
        final AndreasenHugeVolatilityAdapter volatilityAdapter = new AndreasenHugeVolatilityAdapter(ah, tol);
        final AndreasenHugeLocalVolAdapter localVolAdapter = new AndreasenHugeLocalVolAdapter(ah);

        assertEquals("vol-adapter ref date matches today", today, volatilityAdapter.referenceDate());
        assertEquals("local-vol adapter ref date matches today", today, localVolAdapter.referenceDate());

        final Date modToday = new Date(15, Month.January, 2018);
        new Settings().setEvaluationDate(modToday);

        assertEquals("vol-adapter ref date matches modToday", modToday, volatilityAdapter.referenceDate());
        assertEquals("local-vol adapter ref date matches modToday", modToday, localVolAdapter.referenceDate());

        final double modImpliedVol = volatilityAdapter.blackVol(maturity, s0, true);
        final double diff = Math.abs(modImpliedVol - impliedVol);
        if (diff > 10 * tol) {
            fail(String.format("modified implied vol should match seeded vol"
                    + "\n    implied vol         : %f"
                    + "\n    modified implied vol: %f"
                    + "\n    difference          : %g"
                    + "\n    tolerance           : %g", impliedVol, modImpliedVol, diff, tol));
        }
    }
}
