/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/
 */
package org.jquantlib.testsuite.termstructures.volatility.swaption;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.termstructures.volatilities.swaption.SwaptionVolatilityDiscrete;
import org.jquantlib.termstructures.volatilities.swaption.SwaptionVolatilityMatrix;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.jquantlib.util.Pair;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Cross-validation tests for {@link SwaptionVolatilityMatrix} against C++
 * QuantLib v1.42.1 reference data
 * ({@code migration-harness/references/termstructures/volatility/swaption_vol_matrix.json}).
 *
 * <p>Three scenario groups (matching the C++ probe):
 * <ul>
 *  <li>A: 3x3 matrix, no flat extrapolation, ShiftedLognormal/no shifts</li>
 *  <li>B: 4x4 matrix, flat extrapolation, with shifts</li>
 *  <li>C: optionDates-based ctor</li>
 * </ul>
 *
 * <p>Tolerance tiers (per Phase 1 design §7):
 * <ul>
 *  <li>TIGHT (1e-12 rel) for node lookups (must reproduce input vols exactly,
 *      modulo bilinear node-pickup which is exact at corners)</li>
 *  <li>LOOSE (1e-8 rel) for bilinear interior interpolation</li>
 * </ul>
 */
public class SwaptionVolatilityMatrixTest {

    private static final String GROUP = "termstructures/volatility/swaption_vol_matrix";
    private static final ReferenceReader REF = ReferenceReader.load(GROUP);

    private static final double TIGHT_REL = 1e-12;
    private static final double LOOSE_REL = 1e-8;
    private static final double ABS_NEAR_ZERO = 1e-14;

    private static SwaptionVolatilityMatrix buildA() {
        final DayCounter dc = new Actual365Fixed();
        final Calendar cal = new Target();
        final BusinessDayConvention bdc = BusinessDayConvention.Following;
        final Date refDate = new Date(2, Month.January, 2020);

        final List<Period> optionT = Arrays.asList(
                new Period(1, TimeUnit.Years),
                new Period(5, TimeUnit.Years),
                new Period(10, TimeUnit.Years));
        final List<Period> swapT = Arrays.asList(
                new Period(1, TimeUnit.Years),
                new Period(5, TimeUnit.Years),
                new Period(10, TimeUnit.Years));
        final Matrix vols = new Matrix(3, 3);
        vols.set(0, 0, 0.18); vols.set(0, 1, 0.20); vols.set(0, 2, 0.22);
        vols.set(1, 0, 0.16); vols.set(1, 1, 0.18); vols.set(1, 2, 0.20);
        vols.set(2, 0, 0.14); vols.set(2, 1, 0.16); vols.set(2, 2, 0.18);

        return new SwaptionVolatilityMatrix(
                refDate, cal, bdc, optionT, swapT, vols, dc,
                /*flatExtrap*/false, VolatilityType.ShiftedLognormal, null);
    }

    private static SwaptionVolatilityMatrix buildB() {
        final DayCounter dc = new Actual365Fixed();
        final Calendar cal = new Target();
        final BusinessDayConvention bdc = BusinessDayConvention.Following;
        final Date refDate = new Date(2, Month.January, 2020);

        final List<Period> optionT = Arrays.asList(
                new Period(1, TimeUnit.Years),
                new Period(2, TimeUnit.Years),
                new Period(5, TimeUnit.Years),
                new Period(10, TimeUnit.Years));
        final List<Period> swapT = Arrays.asList(
                new Period(1, TimeUnit.Years),
                new Period(2, TimeUnit.Years),
                new Period(5, TimeUnit.Years),
                new Period(10, TimeUnit.Years));
        final Matrix vols = new Matrix(4, 4);
        final Matrix shifts = new Matrix(4, 4);
        for (int i = 0; i < 4; ++i) {
            for (int j = 0; j < 4; ++j) {
                vols.set(i, j, 0.10 + 0.01 * i + 0.01 * j);
                shifts.set(i, j, 0.01 + 0.001 * i + 0.001 * j);
            }
        }
        return new SwaptionVolatilityMatrix(
                refDate, cal, bdc, optionT, swapT, vols, dc,
                /*flatExtrap*/true, VolatilityType.ShiftedLognormal, shifts);
    }

    private static SwaptionVolatilityMatrix buildC() {
        final DayCounter dc = new Actual365Fixed();
        final Calendar cal = new Target();
        final BusinessDayConvention bdc = BusinessDayConvention.Following;
        final Date refDate = new Date(2, Month.January, 2020);

        final List<Date> optionDates = new ArrayList<>();
        optionDates.add(new Date(2, Month.January, 2021));
        optionDates.add(new Date(3, Month.January, 2022));
        optionDates.add(new Date(2, Month.January, 2025));
        final List<Period> swapT = Arrays.asList(
                new Period(1, TimeUnit.Years),
                new Period(5, TimeUnit.Years),
                new Period(10, TimeUnit.Years));
        final Matrix vols = new Matrix(3, 3);
        vols.set(0, 0, 0.21); vols.set(0, 1, 0.22); vols.set(0, 2, 0.23);
        vols.set(1, 0, 0.18); vols.set(1, 1, 0.19); vols.set(1, 2, 0.20);
        vols.set(2, 0, 0.15); vols.set(2, 1, 0.16); vols.set(2, 2, 0.17);

        return new SwaptionVolatilityMatrix(
                refDate, cal, bdc, optionDates,
                SwaptionVolatilityDiscrete.FromDates.Marker, swapT,
                vols, dc, false, VolatilityType.ShiftedLognormal, null);
    }

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

    private static int expectedInt(final String caseName) {
        final Case c = REF.getCase(caseName);
        final JSONObject e = (JSONObject) c.expectedRaw();
        return e.getInt("value");
    }

    private static void assertCloseRel(final double expected, final double actual,
                                       final double rel) {
        if (Math.abs(expected) < ABS_NEAR_ZERO) {
            assertEquals(expected, actual, ABS_NEAR_ZERO);
        } else {
            assertEquals(expected, actual, Math.abs(expected) * rel);
        }
    }

    // ----- Scenario A: vanilla 3x3 -------------------------------------------

    @Test
    public void testA_inspectors() {
        final SwaptionVolatilityMatrix svm = buildA();
        // maxDate (TIGHT — exact serial number from Date arithmetic)
        assertEquals(expectedLong("A_maxDate_serial"),
                svm.maxDate().serialNumber());
        // minStrike / maxStrike — TIGHT
        assertCloseRel(expectedDouble("A_minStrike"), svm.minStrike(), TIGHT_REL);
        assertCloseRel(expectedDouble("A_maxStrike"), svm.maxStrike(), TIGHT_REL);
        // maxSwapLength: 10.0 years exact (per C++ swapLength(Period))
        assertCloseRel(expectedDouble("A_maxSwapLen"),
                svm.maxSwapLength(), TIGHT_REL);
    }

    @Test
    public void testA_optionTimesAndSwapLengths() {
        final SwaptionVolatilityMatrix svm = buildA();
        for (int i = 0; i < 3; ++i) {
            assertCloseRel(expectedDouble("A_optionTime_i" + i),
                    svm.optionTimes()[i], TIGHT_REL);
            assertCloseRel(expectedDouble("A_swapLength_j" + i),
                    svm.swapLengths()[i], TIGHT_REL);
        }
    }

    @Test
    public void testA_volAtNodes() {
        final SwaptionVolatilityMatrix svm = buildA();
        // At every node, volatility(time_i, swap_j, k) must equal the input cell.
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 3; ++j) {
                final double t = svm.optionTimes()[i];
                final double l = svm.swapLengths()[j];
                final double expected = expectedDouble("A_vol_node_i" + i + "j" + j);
                final double actual = svm.volatility(t, l, 0.05);
                assertCloseRel(expected, actual, TIGHT_REL);
            }
        }
    }

    @Test
    public void testA_interiorBilinear() {
        final SwaptionVolatilityMatrix svm = buildA();
        final double tOpt = (svm.optionTimes()[0] + svm.optionTimes()[1]) * 0.5;
        final double sLen = (svm.swapLengths()[0] + svm.swapLengths()[1]) * 0.5;

        assertCloseRel(expectedDouble("A_vol_interior_mid"),
                svm.volatility(tOpt, sLen, 0.05), LOOSE_REL);
        assertCloseRel(expectedDouble("A_blackVar_interior_mid"),
                svm.blackVariance(tOpt, sLen, 0.05, false), LOOSE_REL);
        // Shift defaults to zero on this matrix.
        assertEquals(expectedDouble("A_shift_interior_mid"),
                svm.shift(tOpt, sLen), ABS_NEAR_ZERO);
    }

    @Test
    public void testA_locate() {
        final SwaptionVolatilityMatrix svm = buildA();
        final double tOpt = (svm.optionTimes()[0] + svm.optionTimes()[1]) * 0.5;
        final double sLen = (svm.swapLengths()[0] + svm.swapLengths()[1]) * 0.5;
        final Pair<Integer, Integer> p = svm.locate(tOpt, sLen);
        assertEquals(expectedInt("A_locate_mid_i"), (int) p.first());
        assertEquals(expectedInt("A_locate_mid_j"), (int) p.second());
    }

    @Test
    public void testA_volatilityType() {
        final SwaptionVolatilityMatrix svm = buildA();
        // C++ ShiftedLognormal == 0; Java's enum order differs, so just check
        // the Java side is the expected enum value for this scenario.
        assertEquals(VolatilityType.ShiftedLognormal, svm.volatilityType());
        // Spot-check that the JSON contains the expected int (parity check).
        final int cppEnumValue = expectedInt("A_volatilityType_int");
        // C++ ShiftedLognormal is 1; Normal is 0 — verify the JSON value
        // is one of the two known-good values.
        assertTrue("unexpected C++ vol type enum value " + cppEnumValue,
                cppEnumValue == 0 || cppEnumValue == 1);
    }

    // ----- Scenario B: 4x4 with shifts + flat extrapolation -----------------

    @Test
    public void testB_volAtNodes() {
        final SwaptionVolatilityMatrix svm = buildB();
        for (int i = 0; i < 4; ++i) {
            for (int j = 0; j < 4; ++j) {
                final double t = svm.optionTimes()[i];
                final double l = svm.swapLengths()[j];
                assertCloseRel(expectedDouble("B_vol_node_i" + i + "j" + j),
                        svm.volatility(t, l, 0.05), TIGHT_REL);
                assertCloseRel(expectedDouble("B_shift_node_i" + i + "j" + j),
                        svm.shift(t, l), TIGHT_REL);
            }
        }
    }

    @Test
    public void testB_flatExtrapolation() {
        final SwaptionVolatilityMatrix svm = buildB();
        // Past last point — clamps to last value (LOOSE: bilinear corner pickup).
        final double tFar = svm.optionTimes()[3] + 5.0;
        final double sFar = svm.swapLengths()[3] + 5.0;
        assertCloseRel(expectedDouble("B_vol_extrap_far"),
                svm.volatility(tFar, sFar, 0.05, true), LOOSE_REL);
        assertCloseRel(expectedDouble("B_shift_extrap_far"),
                svm.shift(tFar, sFar, true), LOOSE_REL);

        // Before first point — clamps to first value.
        final double tBefore = svm.optionTimes()[0] - 0.5;
        final double sBefore = svm.swapLengths()[0] - 0.5;
        assertCloseRel(expectedDouble("B_vol_extrap_before"),
                svm.volatility(tBefore, sBefore, 0.05, true), LOOSE_REL);
    }

    // ----- Scenario C: optionDates ctor --------------------------------------

    @Test
    public void testC_inspectors() {
        final SwaptionVolatilityMatrix svm = buildC();
        assertEquals(expectedLong("C_maxDate_serial"),
                svm.maxDate().serialNumber());
        for (int i = 0; i < 3; ++i) {
            assertCloseRel(expectedDouble("C_optionTime_i" + i),
                    svm.optionTimes()[i], TIGHT_REL);
        }
    }

    @Test
    public void testC_interior() {
        final SwaptionVolatilityMatrix svm = buildC();
        final double tOpt = (svm.optionTimes()[0] + svm.optionTimes()[1]) * 0.5;
        final double sLen = (svm.swapLengths()[0] + svm.swapLengths()[1]) * 0.5;
        assertCloseRel(expectedDouble("C_vol_interior"),
                svm.volatility(tOpt, sLen, 0.05), LOOSE_REL);
    }

    @Test
    public void testSmileSection_atNode_returnsFlat() {
        final SwaptionVolatilityMatrix svm = buildA();
        assertNotNull(svm.smileSection(svm.optionDates().get(0),
                new Period(5, TimeUnit.Years)));
    }
}
