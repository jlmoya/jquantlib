/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/
 */
package org.jquantlib.testsuite.termstructures.volatility.capfloor;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.termstructures.volatilities.capfloor.CapFloorTermVolCurve;
import org.jquantlib.termstructures.volatilities.capfloor.CapFloorTermVolSurface;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Cross-validation tests for {@link CapFloorTermVolCurve} and
 * {@link CapFloorTermVolSurface} against C++ QuantLib v1.42.1 reference data
 * ({@code migration-harness/references/termstructures/volatility/capfloor_term_vol.json}).
 *
 * <p>Two scenarios:
 * <ul>
 *  <li>curve_A: 6-tenor curve, cubic spline w/ natural BC</li>
 *  <li>surf_B:  4x4 surface, bilinear over (option-time, strike)</li>
 * </ul>
 *
 * <p>Tolerance tiers:
 * <ul>
 *  <li>TIGHT (1e-12 rel) for at-node lookups (must reproduce input exactly)</li>
 *  <li>LOOSE (1e-8 rel) for interior interpolation</li>
 * </ul>
 */
public class CapFloorTermVolTest {

    private static final String GROUP = "termstructures/volatility/capfloor_term_vol";
    private static final ReferenceReader REF = ReferenceReader.load(GROUP);

    private static final double TIGHT_REL = 1e-12;
    private static final double LOOSE_REL = 1e-8;
    private static final double ABS_NEAR_ZERO = 1e-14;

    private static double expectedDouble(final String caseName) {
        final Case c = REF.getCase(caseName);
        final JSONObject e = (JSONObject) c.expectedRaw();
        return e.getDouble("value");
    }

    private static long expectedLong(final String caseName) {
        final Case c = REF.getCase(caseName);
        final JSONObject e = (JSONObject) c.expectedRaw();
        return e.getLong("value");
    }

    private static void assertCloseRel(final double expected, final double actual,
                                       final double rel) {
        if (Math.abs(expected) < ABS_NEAR_ZERO) {
            assertEquals(expected, actual, ABS_NEAR_ZERO);
        } else {
            assertEquals(expected, actual, Math.abs(expected) * rel);
        }
    }

    private static CapFloorTermVolCurve buildCurveA() {
        final DayCounter dc = new Actual365Fixed();
        final Calendar cal = new Target();
        final BusinessDayConvention bdc = BusinessDayConvention.Following;
        final Date refDate = new Date(2, Month.January, 2020);

        final List<Period> optionT = Arrays.asList(
                new Period(1, TimeUnit.Years),
                new Period(2, TimeUnit.Years),
                new Period(3, TimeUnit.Years),
                new Period(5, TimeUnit.Years),
                new Period(7, TimeUnit.Years),
                new Period(10, TimeUnit.Years));
        final double[] vols = { 0.18, 0.17, 0.16, 0.15, 0.145, 0.14 };

        return new CapFloorTermVolCurve(refDate, cal, bdc, optionT, vols, dc);
    }

    private static CapFloorTermVolSurface buildSurfB() {
        final DayCounter dc = new Actual365Fixed();
        final Calendar cal = new Target();
        final BusinessDayConvention bdc = BusinessDayConvention.Following;
        final Date refDate = new Date(2, Month.January, 2020);

        final List<Period> optionT = Arrays.asList(
                new Period(1, TimeUnit.Years),
                new Period(2, TimeUnit.Years),
                new Period(5, TimeUnit.Years),
                new Period(10, TimeUnit.Years));
        final double[] strikes = { 0.02, 0.04, 0.06, 0.08 };
        final Matrix vols = new Matrix(4, 4);
        for (int i = 0; i < 4; ++i) {
            for (int j = 0; j < 4; ++j) {
                vols.set(i, j, 0.20 + 0.005 * i - 0.005 * j);
            }
        }
        return new CapFloorTermVolSurface(refDate, cal, bdc, optionT, strikes, vols, dc);
    }

    // ----- Curve A -----------------------------------------------------------

    @Test
    public void testCurveA_inspectors() {
        final CapFloorTermVolCurve curve = buildCurveA();
        assertEquals(expectedLong("curve_A_maxDate_serial"),
                curve.maxDate().serialNumber());
        // QL_MIN_REAL / QL_MAX_REAL — TIGHT
        assertCloseRel(expectedDouble("curve_A_minStrike"), curve.minStrike(), TIGHT_REL);
        assertCloseRel(expectedDouble("curve_A_maxStrike"), curve.maxStrike(), TIGHT_REL);
    }

    @Test
    public void testCurveA_optionTimesAndDates() {
        final CapFloorTermVolCurve curve = buildCurveA();
        for (int i = 0; i < 6; ++i) {
            assertCloseRel(expectedDouble("curve_A_optionTime_i" + i),
                    curve.optionTimes()[i], TIGHT_REL);
            assertEquals(expectedLong("curve_A_optionDate_serial_i" + i),
                    curve.optionDates().get(i).serialNumber());
        }
    }

    @Test
    public void testCurveA_volAtNodes() {
        final CapFloorTermVolCurve curve = buildCurveA();
        for (int i = 0; i < 6; ++i) {
            final double t = curve.optionTimes()[i];
            assertCloseRel(expectedDouble("curve_A_vol_node_i" + i),
                    curve.volatility(t, 0.05), TIGHT_REL);
        }
    }

    @Test
    public void testCurveA_volBetweenNodes() {
        final CapFloorTermVolCurve curve = buildCurveA();
        for (int i = 0; i + 1 < 6; ++i) {
            final double t = (curve.optionTimes()[i] + curve.optionTimes()[i + 1]) * 0.5;
            assertCloseRel(expectedDouble("curve_A_vol_mid_i" + i),
                    curve.volatility(t, 0.05), LOOSE_REL);
        }
    }

    // ----- Surface B ---------------------------------------------------------

    @Test
    public void testSurfB_inspectors() {
        final CapFloorTermVolSurface surf = buildSurfB();
        assertEquals(expectedLong("surf_B_maxDate_serial"),
                surf.maxDate().serialNumber());
        assertCloseRel(expectedDouble("surf_B_minStrike"), surf.minStrike(), TIGHT_REL);
        assertCloseRel(expectedDouble("surf_B_maxStrike"), surf.maxStrike(), TIGHT_REL);
    }

    @Test
    public void testSurfB_optionTimes() {
        final CapFloorTermVolSurface surf = buildSurfB();
        for (int i = 0; i < 4; ++i) {
            assertCloseRel(expectedDouble("surf_B_optionTime_i" + i),
                    surf.optionTimes()[i], TIGHT_REL);
        }
    }

    @Test
    public void testSurfB_volAtNodes() {
        final CapFloorTermVolSurface surf = buildSurfB();
        final double[] strikes = { 0.02, 0.04, 0.06, 0.08 };
        for (int i = 0; i < 4; ++i) {
            for (int j = 0; j < 4; ++j) {
                final double t = surf.optionTimes()[i];
                final double k = strikes[j];
                assertCloseRel(expectedDouble("surf_B_vol_node_i" + i + "j" + j),
                        surf.volatility(t, k), TIGHT_REL);
            }
        }
    }

    @Test
    public void testSurfB_volInterior() {
        final CapFloorTermVolSurface surf = buildSurfB();
        final double[] strikes = { 0.02, 0.04, 0.06, 0.08 };
        // Lo midpoint
        {
            final double t = (surf.optionTimes()[0] + surf.optionTimes()[1]) * 0.5;
            final double k = (strikes[0] + strikes[1]) * 0.5;
            assertCloseRel(expectedDouble("surf_B_vol_mid_lo"),
                    surf.volatility(t, k), LOOSE_REL);
        }
        // Hi midpoint
        {
            final double t = (surf.optionTimes()[2] + surf.optionTimes()[3]) * 0.5;
            final double k = (strikes[2] + strikes[3]) * 0.5;
            assertCloseRel(expectedDouble("surf_B_vol_mid_hi"),
                    surf.volatility(t, k), LOOSE_REL);
        }
    }
}
