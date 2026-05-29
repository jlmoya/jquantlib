/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/
 */
package org.jquantlib.testsuite.termstructures.volatility;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.termstructures.volatilities.capfloor.ConstantCapFloorTermVolatility;
import org.jquantlib.termstructures.volatilities.equityfx.GridModelLocalVolSurface;
import org.jquantlib.termstructures.volatilities.inflation.ConstantCPIVolatility;
import org.jquantlib.termstructures.volatilities.swaption.CmsMarketCalibration;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Cross-validation tests for the 6 gap-ported volatility term-structure classes against C++ QuantLib v1.42.1
 * reference data
 * ({@code migration-harness/references/termstructures/gap_vol_surfaces.json}, emitted by
 * {@code gap_vol_surfaces_probe.cpp}).
 *
 * <p>Classes / pieces covered (all DETERMINISTIC):
 * <ul>
 *   <li>{@link ConstantCapFloorTermVolatility} — flat (t, strike) lookups, min/max strike, maxDate.</li>
 *   <li>{@link ConstantCPIVolatility} (+ its abstract base {@code CPIVolatilitySurface}) — flat vol,
 *       totalVariance, baseDate, timeFromBase.</li>
 *   <li>{@link GridModelLocalVolSurface} — localVol(t, strike) on a fixed grid, both default params
 *       (all 1.0) and a set params() vector (at-node + interior).</li>
 *   <li>{@link CmsMarketCalibration} static transform functions
 *       (beta/reversion transform direct/inverse).</li>
 * </ul>
 *
 * <p><b>Not cross-validated here:</b> the optimizer-driven {@code CmsMarketCalibration.compute()}/
 * {@code computeParametric()} and the full {@code CmsMarket.reprice()}/{@code weightedError()} pricing stack.
 * Those depend on the CMS pricing stack (SwapIndex/IborIndex conventions, Hagan pricer) plus an optimizer path,
 * which cannot be reproduced bit-for-bit against C++. They are ported structurally/faithfully; only the
 * deterministic transform math shared by the calibration is locked against C++ reference values.
 *
 * <p>Tolerance tier: TIGHT (1e-12 rel, 1e-14 abs near zero) for every case — all values are flat lookups,
 * fixed-grid linear interpolation, or closed-form transforms with no optimizer in the loop.
 */
public class GapVolSurfacesTest {

    private static final String GROUP = "termstructures/gap_vol_surfaces";
    private static final ReferenceReader REF = ReferenceReader.load(GROUP);

    private static final double TIGHT_REL = 1e-12;
    private static final double ABS_NEAR_ZERO = 1e-14;

    private static final DayCounter DC = new Actual365Fixed();
    private static final Calendar CAL = new Target();
    private static final BusinessDayConvention BDC = BusinessDayConvention.Following;
    private static final Date REF_DATE = new Date(2, Month.January, 2020);

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

    private static void assertTight(final String caseName, final double actual) {
        final double expected = expectedDouble(caseName);
        if (Math.abs(expected) < ABS_NEAR_ZERO) {
            assertEquals(caseName, expected, actual, ABS_NEAR_ZERO);
        } else {
            assertEquals(caseName, expected, actual, Math.abs(expected) * TIGHT_REL);
        }
    }

    // ----- ConstantCapFloorTermVolatility -----------------------------------

    @Test
    public void testConstantCapFloorTermVolatility() {
        final double v = 0.18;
        final ConstantCapFloorTermVolatility cfv =
                new ConstantCapFloorTermVolatility(REF_DATE, CAL, BDC, v, DC);

        assertEquals(expectedLong("cf_maxDate_serial"), cfv.maxDate().serialNumber());
        assertTight("cf_minStrike", cfv.minStrike());
        assertTight("cf_maxStrike", cfv.maxStrike());

        final double[] ts = { 0.5, 1.0, 2.5, 5.0, 10.0 };
        final double[] ks = { 0.01, 0.03, 0.05 };
        int idx = 0;
        for (final double t : ts) {
            for (final double k : ks) {
                assertTight("cf_vol_" + idx, cfv.volatility(t, k, true));
                idx++;
            }
        }
    }

    // ----- ConstantCPIVolatility (+ CPIVolatilitySurface base) --------------

    @Test
    public void testConstantCPIVolatility() {
        new Settings().setEvaluationDate(REF_DATE);

        final double v = 0.045;
        final int settlementDays = 0;
        final Period obsLag = new Period(3, TimeUnit.Months);
        final Frequency freq = Frequency.Monthly;
        final boolean interp = false;
        final ConstantCPIVolatility cpi =
                new ConstantCPIVolatility(v, settlementDays, CAL, BDC, DC, obsLag, freq, interp);

        assertEquals(expectedLong("cpi_maxDate_serial"), cpi.maxDate().serialNumber());
        assertTight("cpi_minStrike", cpi.minStrike());
        assertTight("cpi_maxStrike", cpi.maxStrike());
        assertEquals(expectedLong("cpi_baseDate_serial"), cpi.baseDate().serialNumber());

        final double[] ts = { 0.25, 1.0, 3.0 };
        int idx = 0;
        for (final double t : ts) {
            assertTight("cpi_vol_time_" + idx, cpi.volatility(t, 0.02));
            idx++;
        }

        final Period[] tenors = {
                new Period(1, TimeUnit.Years),
                new Period(2, TimeUnit.Years),
                new Period(5, TimeUnit.Years) };
        final Period sentinel = new Period(-1, TimeUnit.Days);
        idx = 0;
        for (final Period tenor : tenors) {
            final Date mat = REF_DATE.add(tenor);
            assertTight("cpi_vol_date_" + idx, cpi.volatility(mat, 0.02, sentinel, true));
            assertTight("cpi_totVar_date_" + idx, cpi.totalVariance(mat, 0.02, sentinel, true));
            assertTight("cpi_timeFromBase_" + idx, cpi.timeFromBase(mat));
            idx++;
        }
    }

    // ----- GridModelLocalVolSurface -----------------------------------------

    private static GridModelLocalVolSurface buildGrid() {
        new Settings().setEvaluationDate(REF_DATE);
        final List<Date> dates = Arrays.asList(
                REF_DATE.add(new Period(1, TimeUnit.Years)),
                REF_DATE.add(new Period(2, TimeUnit.Years)),
                REF_DATE.add(new Period(3, TimeUnit.Years)));
        final double[] strikeVec = { 80.0, 90.0, 100.0, 110.0, 120.0 };
        final List<double[]> strikes = new ArrayList<>();
        for (int i = 0; i < dates.size(); ++i) {
            strikes.add(strikeVec.clone());
        }
        return new GridModelLocalVolSurface(REF_DATE, dates, strikes, DC);
    }

    @Test
    public void testGridModelLocalVolSurfaceDefaultParams() {
        final GridModelLocalVolSurface surf = buildGrid();

        assertTight("grid_default_minStrike", surf.minStrike());
        assertTight("grid_default_maxStrike", surf.maxStrike());
        assertTight("grid_default_maxTime", surf.maxTime());
        assertEquals(expectedLong("grid_default_maxDate_serial"), surf.maxDate().serialNumber());

        final double[] tq = { 0.5, 1.0, 1.5, 2.0, 2.5 };
        final double[] kq = { 85.0, 100.0, 115.0 };
        int idx = 0;
        for (final double t : tq) {
            for (final double k : kq) {
                assertTight("grid_default_lv_" + idx, surf.localVol(t, k, true));
                idx++;
            }
        }
    }

    @Test
    public void testGridModelLocalVolSurfaceSetParams() {
        final GridModelLocalVolSurface surf = buildGrid();

        // Parameter layout: row-major over (nStrikes rows x nTimes cols):
        // param[r*nTimes + c] = vol at (strike r, time c) = 0.20 + 0.01*r - 0.005*c.
        final int nStrikes = 5;
        final int nTimes = 3;
        final Array p = new Array(nStrikes * nTimes);
        for (int r = 0; r < nStrikes; ++r) {
            for (int c = 0; c < nTimes; ++c) {
                p.set(r * nTimes + c, 0.20 + 0.01 * r - 0.005 * c);
            }
        }
        surf.setParams(p);

        // Grid times = year-fractions of the +1Y/+2Y/+3Y dates.
        final List<Date> dates = Arrays.asList(
                REF_DATE.add(new Period(1, TimeUnit.Years)),
                REF_DATE.add(new Period(2, TimeUnit.Years)),
                REF_DATE.add(new Period(3, TimeUnit.Years)));
        final double[] gridTimes = new double[dates.size()];
        for (int i = 0; i < dates.size(); ++i) {
            gridTimes[i] = DC.yearFraction(REF_DATE, dates.get(i));
        }

        final double[] kNodes = { 80.0, 100.0, 120.0 };
        int idx = 0;
        for (final double t : gridTimes) {
            for (final double k : kNodes) {
                assertTight("grid_set_node_" + idx, surf.localVol(t, k, true));
                idx++;
            }
        }

        final double[] ti = { 0.5, 1.5, 2.5 };
        final double[] ki = { 85.0, 105.0 };
        idx = 0;
        for (final double t : ti) {
            for (final double k : ki) {
                assertTight("grid_set_interior_" + idx, surf.localVol(t, k, true));
                idx++;
            }
        }
    }

    // ----- CmsMarketCalibration transform functions -------------------------

    @Test
    public void testCmsMarketCalibrationTransforms() {
        final double[] ys = { -3.0, -1.0, -0.5, 0.0, 0.5, 1.0, 2.0, 3.0, 15.0 };
        int idx = 0;
        for (final double y : ys) {
            assertTight("betaTransformDirect_" + idx, CmsMarketCalibration.betaTransformDirect(y));
            idx++;
        }

        final double[] yrev = { 0.0, 0.01, 0.25, 1.0, 4.0, 9.0 };
        idx = 0;
        for (final double y : yrev) {
            assertTight("reversionTransformDirect_" + idx, CmsMarketCalibration.reversionTransformDirect(y));
            idx++;
        }

        final double[] betas = { 0.1, 0.3, 0.5, 0.7, 0.9 };
        idx = 0;
        for (final double b : betas) {
            assertTight("betaTransformInverse_" + idx, CmsMarketCalibration.betaTransformInverse(b));
            idx++;
        }

        final double[] revs = { 0.0, 0.01, 0.05, 0.5, 2.0 };
        idx = 0;
        for (final double rv : revs) {
            assertTight("reversionTransformInverse_" + idx, CmsMarketCalibration.reversionTransformInverse(rv));
            idx++;
        }
    }
}
