/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Cross-validated tests for InterpolatedCPICapFloorTermPriceSurface against
 QuantLib v1.42.1 via
 migration-harness/references/experimental/inflation/cpi_cap_floor_term_price_surface.json
 (Phase 2s C.1).
*/
package org.jquantlib.testsuite.experimental.inflation;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.experimental.inflation.InterpolatedCPICapFloorTermPriceSurface;
import org.jquantlib.indexes.CPI;
import org.jquantlib.indexes.inflation.UKRPI;
import org.jquantlib.math.interpolations.factories.Bilinear;
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.ZeroInflationTermStructure;
import org.jquantlib.termstructures.inflation.PiecewiseZeroInflationCurve;
import org.jquantlib.termstructures.inflation.ZeroCouponInflationSwapHelper;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.testsuite.util.Tolerance;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.MakeSchedule;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.UnitedKingdom;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.fail;

/**
 * Probe-driven tests for {@link InterpolatedCPICapFloorTermPriceSurface}.
 *
 * <p>Replicates the UK RPI fixture from C++ test-suite/inflationcpicapfloor.cpp.
 * Uses {@link Bilinear} 2D interpolator, matching the C++ probe configuration.
 *
 * <p>Tier rationale:
 * <ul>
 *   <li>Grid points (cap/floor at known strike+maturity): TIGHT — the surface
 *       reproduces the input data exactly (no interpolation involved).</li>
 *   <li>Interior interpolation points: LOOSE — cubic/bilinear evaluation.</li>
 *   <li>ATM rate (computed from CPI fixings via inflation curve): LOOSE
 *       (depends on bootstrap chain).</li>
 *   <li>Metadata (strike count, lag): EXACT.</li>
 * </ul>
 */
public class CPICapFloorTermPriceSurfaceTest {

    private static final String REF_GROUP =
            "experimental/inflation/cpi_cap_floor_term_price_surface";

    @Test
    public void cpiCapFloorTermPriceSurface_matchesCpp() {
        // ===========================================================
        // Setup mirroring C++ test-suite/inflationcpicapfloor.cpp::CommonVars
        // ===========================================================
        final Calendar cal = new UnitedKingdom();
        final BusinessDayConvention bdc = BusinessDayConvention.ModifiedFollowing;
        final Date today = new Date(1, Month.June, 2010);
        final Date evaluationDate = cal.adjust(today);
        new Settings().setEvaluationDate(evaluationDate);
        final DayCounter dcZCIIS = new ActualActual(ActualActual.Convention.ISDA);
        final DayCounter dcNominal = new ActualActual(ActualActual.Convention.ISDA);

        // UK RPI index fixing data
        final Schedule rpiSchedule = new MakeSchedule(
                new Date(1, Month.July, 2007),
                new Date(1, Month.April, 2010),
                new Period(1, TimeUnit.Months), cal, bdc)
                .schedule();
        final double[] fixData = {
                206.1, 207.3, 208.0, 208.9, 209.7, 210.9,
                209.8, 211.4, 212.1, 214.0, 215.1, 216.8,
                216.5, 217.2, 218.4, 217.7, 216.0, 212.9,
                210.1, 211.4, 211.3, 211.5, 212.8, 213.4,
                213.4, 214.4, 215.3, 216.0, 216.6, 218.0,
                217.9, 219.2, 220.7, 222.8
        };

        // Build the index without a curve handle for bootstrapping.
        // (Using a Handle that is later relinked produces a cyclic
        //  observer chain in Java's weak-reference observable model — see
        //  PiecewiseZeroInflationCurveTest comment.)
        final UKRPI ii = new UKRPI(Frequency.Monthly, false, false);
        for (int i = 0; i < rpiSchedule.size(); i++) {
            ii.addFixing(rpiSchedule.date(i), fixData[i], true);
        }

        // Nominal yield curve — FlatForward 5% (matches simplified C++ probe).
        final FlatForward nominalTSimpl = new FlatForward(evaluationDate, 0.05,
                dcNominal, Compounding.Continuous, Frequency.Annual);
        final Handle<YieldTermStructure> nominalUK = new Handle<>(nominalTSimpl);

        // ZCIIS data and zero inflation curve
        final Period observationLag = new Period(2, TimeUnit.Months);
        final Date[] zciisDates = {
                new Date(1, Month.June, 2011), new Date(1, Month.June, 2012),
                new Date(1, Month.June, 2013), new Date(1, Month.June, 2014),
                new Date(1, Month.June, 2015), new Date(1, Month.June, 2016),
                new Date(1, Month.June, 2017), new Date(1, Month.June, 2018),
                new Date(1, Month.June, 2019), new Date(1, Month.June, 2020),
                new Date(1, Month.June, 2022), new Date(1, Month.June, 2025),
                new Date(1, Month.June, 2030), new Date(1, Month.June, 2035),
                new Date(1, Month.June, 2040), new Date(1, Month.June, 2050),
                new Date(1, Month.June, 2060)
        };
        final double[] zciisRates = {
                3.087, 3.12, 3.059, 3.11, 3.15, 3.207, 3.253, 3.288, 3.314,
                3.401, 3.458, 3.52, 3.655, 3.668, 3.695, 3.634, 3.629
        };

        final List<ZeroCouponInflationSwapHelper> helpers = new ArrayList<>();
        for (int i = 0; i < zciisRates.length; i++) {
            final Quote q = new SimpleQuote(zciisRates[i] / 100.0);
            final Handle<Quote> qh = new Handle<>(q);
            helpers.add(new ZeroCouponInflationSwapHelper(qh, observationLag,
                    zciisDates[i], cal, bdc, dcZCIIS, ii));
        }

        final double baseZeroRate = zciisRates[0] / 100.0;
        // Mirror C++ ZeroInflationIndex::lastFixingDate
        // (= inflationPeriod(fixings.lastDate(), frequency).first)
        final Date lastFixDate = ii.timeSeries().lastKey();
        final Date baseDate = org.jquantlib.termstructures.InflationTermStructure
                .inflationPeriod(lastFixDate, ii.frequency()).first();
        final PiecewiseZeroInflationCurve<Linear> pCPIts =
                new PiecewiseZeroInflationCurve<>(Linear.class, evaluationDate,
                        baseDate, ii.frequency(), dcZCIIS, helpers);
        pCPIts.dates();   // trigger bootstrap

        // Now create a fresh ii2 linked to the bootstrapped curve, for the
        // surface to consume. ii2 inherits the same fixings from IndexManager
        // (UKRPI shares state across instances by family/region).
        final UKRPI ii2 = new UKRPI(Frequency.Monthly, false, false,
                new Handle<>(pCPIts));

        // ===========================================================
        // Cap/floor surface data
        // ===========================================================
        final Period[] cfMaturities = {
                new Period(3, TimeUnit.Years),  new Period(5, TimeUnit.Years),
                new Period(7, TimeUnit.Years),  new Period(10, TimeUnit.Years),
                new Period(15, TimeUnit.Years), new Period(20, TimeUnit.Years),
                new Period(30, TimeUnit.Years)
        };
        final double[] cStrikes = {0.03, 0.04, 0.05, 0.06};
        final double[] fStrikes = {-0.01, 0.0, 0.01, 0.02};
        final int ncStrikes = 4, nfStrikes = 4, ncfMaturities = 7;

        final double[][] cPriceData = {
                {227.6, 100.27, 38.8, 14.94},
                {345.32, 127.9, 40.59, 14.11},
                {477.95, 170.19, 50.62, 16.88},
                {757.81, 303.95, 107.62, 43.61},
                {1140.73, 481.89, 168.4, 63.65},
                {1537.6, 607.72, 172.27, 54.87},
                {2211.67, 839.24, 184.75, 45.03}
        };
        final double[][] fPriceData = {
                {15.62, 28.38, 53.61, 104.6},
                {21.45, 36.73, 66.66, 129.6},
                {24.45, 42.08, 77.04, 152.24},
                {39.25, 63.52, 109.2, 203.44},
                {36.82, 63.62, 116.97, 232.73},
                {39.7, 67.47, 121.79, 238.56},
                {41.48, 73.9, 139.75, 286.75}
        };

        final Matrix cPrice = new Matrix(ncStrikes, ncfMaturities);
        final Matrix fPrice = new Matrix(nfStrikes, ncfMaturities);
        for (int i = 0; i < ncStrikes; i++)
            for (int j = 0; j < ncfMaturities; j++)
                cPrice.set(i, j, cPriceData[j][i] / 10000.0);
        for (int i = 0; i < nfStrikes; i++)
            for (int j = 0; j < ncfMaturities; j++)
                fPrice.set(i, j, fPriceData[j][i] / 10000.0);

        final InterpolatedCPICapFloorTermPriceSurface<Bilinear> surf =
                new InterpolatedCPICapFloorTermPriceSurface<>(Bilinear.class,
                        1.0, baseZeroRate, observationLag, cal, bdc, dcZCIIS,
                        ii2, CPI.InterpolationType.Flat, nominalUK,
                        cStrikes, fStrikes, cfMaturities, cPrice, fPrice);

        // ===========================================================
        // Run cases
        // ===========================================================
        final ReferenceReader ref = ReferenceReader.load(REF_GROUP);
        final List<String> mismatches = new ArrayList<>();

        for (final String name : ref.caseNames()) {
            final Case c = ref.getCase(name);
            try {
                checkCase(name, c, surf, cStrikes, fStrikes, cfMaturities, mismatches);
            } catch (final Exception e) {
                mismatches.add(name + ": EXCEPTION " + e.getClass().getSimpleName()
                        + " " + e.getMessage());
            }
        }
        if (!mismatches.isEmpty()) {
            fail(mismatches.size() + " mismatch(es):\n" + String.join("\n", mismatches));
        }
    }

    private static void checkCase(final String name, final Case c,
            final InterpolatedCPICapFloorTermPriceSurface<Bilinear> surf,
            final double[] cStrikes, final double[] fStrikes,
            final Period[] cfMaturities, final List<String> mismatches) {
        if (name.startsWith("cap_grid_")) {
            // cap_grid_s{i}_t{j}
            final int us1 = name.indexOf('s');
            final int us2 = name.indexOf("_t");
            final int i = Integer.parseInt(name.substring(us1 + 1, us2));
            final int j = Integer.parseInt(name.substring(us2 + 2));
            final double cpp = c.expectedDouble();
            final double java = surf.capPrice(cfMaturities[j], cStrikes[i]);
            if (!Tolerance.tight(java, cpp)) {
                mismatches.add(fmt(name, cpp, java, "TIGHT"));
            }
        } else if (name.startsWith("floor_grid_")) {
            final int us1 = name.indexOf('s');
            final int us2 = name.indexOf("_t");
            final int i = Integer.parseInt(name.substring(us1 + 1, us2));
            final int j = Integer.parseInt(name.substring(us2 + 2));
            final double cpp = c.expectedDouble();
            final double java = surf.floorPrice(cfMaturities[j], fStrikes[i]);
            if (!Tolerance.tight(java, cpp)) {
                mismatches.add(fmt(name, cpp, java, "TIGHT"));
            }
        } else if (name.startsWith("cap_interior_")) {
            final double strike = c.inputs().getDouble("strike");
            final int yrs = c.inputs().getInt("maturity_period_years");
            final double cpp = c.expectedDouble();
            final double java = surf.capPrice(new Period(yrs, TimeUnit.Years), strike);
            if (!Tolerance.loose(java, cpp)) {
                mismatches.add(fmt(name, cpp, java, "LOOSE"));
            }
        } else if (name.startsWith("floor_interior_")) {
            final double strike = c.inputs().getDouble("strike");
            final int yrs = c.inputs().getInt("maturity_period_years");
            final double cpp = c.expectedDouble();
            final double java = surf.floorPrice(new Period(yrs, TimeUnit.Years), strike);
            if (!Tolerance.loose(java, cpp)) {
                mismatches.add(fmt(name, cpp, java, "LOOSE"));
            }
        } else if (name.startsWith("price_floor_")) {
            final double strike = c.inputs().getDouble("strike");
            final int yrs = c.inputs().getInt("maturity_period_years");
            final double cpp = c.expectedDouble();
            final double java = surf.price(new Period(yrs, TimeUnit.Years), strike);
            if (!Tolerance.tight(java, cpp)) {
                mismatches.add(fmt(name, cpp, java, "TIGHT"));
            }
        } else if (name.startsWith("atm_rate_")) {
            final int yrs = c.inputs().getInt("maturity_period_years");
            final double cpp = c.expectedDouble();
            final double java = surf.atmRate(surf.cpiOptionDateFromTenor(
                    new Period(yrs, TimeUnit.Years)));
            if (!Tolerance.loose(java, cpp)) {
                mismatches.add(fmt(name, cpp, java, "LOOSE"));
            }
        } else if (name.equals("metadata")) {
            final JSONObject exp = (JSONObject) c.expectedRaw();
            checkExact(name + ".min_strike", exp.getDouble("min_strike"),
                    surf.minStrike(), mismatches);
            checkExact(name + ".max_strike", exp.getDouble("max_strike"),
                    surf.maxStrike(), mismatches);
            checkInt(name + ".num_strikes", exp.getInt("num_strikes"),
                    surf.strikes().length, mismatches);
            checkInt(name + ".observation_lag_months", exp.getInt("observation_lag_months"),
                    surf.observationLag().length(), mismatches);
        } else {
            mismatches.add(name + ": UNRECOGNIZED case");
        }
    }

    private static void checkExact(final String name, final double cpp,
            final double java, final List<String> mismatches) {
        if (!Tolerance.exact(java, cpp)) {
            mismatches.add(fmt(name, cpp, java, "EXACT"));
        }
    }

    private static void checkInt(final String name, final int cpp,
            final int java, final List<String> mismatches) {
        if (cpp != java) {
            mismatches.add(name + ": expected=" + cpp + " actual=" + java);
        }
    }

    private static String fmt(final String name, final double expected,
            final double actual, final String tier) {
        return String.format("%s: expected=%.17e actual=%.17e diff=%.3e (%s)",
                name, expected, actual, Math.abs(actual - expected), tier);
    }
}
