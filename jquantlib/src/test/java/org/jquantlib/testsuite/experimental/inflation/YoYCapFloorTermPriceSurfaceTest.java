/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Cross-validated tests for InterpolatedYoYCapFloorTermPriceSurface against
 QuantLib v1.42.1 via
 migration-harness/references/experimental/inflation/yoy_cap_floor_term_price_surface.json
 (Phase 2s C.1).
*/
package org.jquantlib.testsuite.experimental.inflation;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.currencies.Europe.EURCurrency;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.experimental.inflation.InterpolatedYoYCapFloorTermPriceSurface;
import org.jquantlib.indexes.CPI;
import org.jquantlib.indexes.EURegion;
import org.jquantlib.indexes.YoYInflationIndex;
import org.jquantlib.math.interpolations.factories.BicubicSpline;
import org.jquantlib.math.interpolations.factories.Cubic;
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.InflationTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.YoYInflationTermStructure;
import org.jquantlib.termstructures.inflation.InterpolatedYoYInflationCurve;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.testsuite.util.Tolerance;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.jquantlib.util.Pair;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.fail;

/**
 * Probe-driven tests for {@link InterpolatedYoYCapFloorTermPriceSurface}.
 *
 * <p>Replicates the EU YoY surface fixture from C++ test-suite/inflationvolatility.cpp.
 * Uses {@link BicubicSpline} 2D interpolator and {@link Cubic} 1D interpolator,
 * matching the C++ probe configuration.
 *
 * <p>Tier rationale:
 * <ul>
 *   <li>Grid points: TIGHT — surface reproduces input data exactly.</li>
 *   <li>ATM YoY swap rates from put/call parity: LOOSE — root-finding tolerance.</li>
 *   <li>Metadata: EXACT.</li>
 * </ul>
 */
public class YoYCapFloorTermPriceSurfaceTest {

    private static final String REF_GROUP =
            "experimental/inflation/yoy_cap_floor_term_price_surface";

    @Test
    public void yoyCapFloorTermPriceSurface_matchesCpp() {
        final Date eval = new Date(23, Month.November, 2007);
        new Settings().setEvaluationDate(eval);

        // Nominal yield curve — FlatForward 4.5%
        final DayCounter dc = new Actual365Fixed();
        final FlatForward euriborTS = new FlatForward(eval, 0.045, dc,
                Compounding.Continuous, Frequency.Annual);
        final var nominalEUR = new Handle<YieldTermStructure>(euriborTS);

        // YoY index on EU HICP — built directly via YoYInflationIndex
        // (Java has no YYEUHICP wrapper; mirrors C++ YoYInflationIndex(make_shared<EUHICP>())
        // which builds a ratio-based YoY).
        final YoYInflationIndex yoyIndexEU = new YoYInflationIndex(
                "YY_EUHICP", new EURegion(), false, false, /*ratio*/ false,
                Frequency.Monthly, new Period(3, TimeUnit.Months),
                new EURCurrency());

        // YoY rates curve
        final double[] yoyEUrates = {
                0.0237951,
                0.0238749, 0.0240334, 0.0241934, 0.0243567, 0.0245323,
                0.0247213, 0.0249348, 0.0251768, 0.0254337, 0.0257258,
                0.0260217, 0.0263006, 0.0265538, 0.0267803, 0.0269378,
                0.0270608, 0.0271363, 0.0272, 0.0272512, 0.0272927,
                0.027317, 0.0273615, 0.0273811, 0.0274063, 0.0274307,
                0.0274625, 0.027527, 0.0275952, 0.0276734, 0.027794
        };
        final List<Date> dList = new ArrayList<>();
        final List<Double> rList = new ArrayList<>();
        final Date baseDate = InflationTermStructure.inflationPeriod(
                eval.sub(new Period(1, TimeUnit.Months)),
                yoyIndexEU.frequency()).first();
        dList.add(baseDate);
        rList.add(yoyEUrates[0]);
        final Target cal = new Target();
        final BusinessDayConvention bdc = BusinessDayConvention.ModifiedFollowing;
        final Date capStartDate = cal.advance(eval,
                new Period(-2, TimeUnit.Months), bdc);
        for (int i = 1; i < yoyEUrates.length; i++) {
            final Date dd = cal.advance(capStartDate,
                    new Period(i, TimeUnit.Years), bdc);
            dList.add(dd);
            rList.add(yoyEUrates[i]);
        }
        final Date[] curveDates = dList.toArray(new Date[0]);
        final double[] curveRates = new double[rList.size()];
        for (int i = 0; i < rList.size(); i++) curveRates[i] = rList.get(i);
        final var pYTSEU = new InterpolatedYoYInflationCurve<Linear>(Linear.class, eval,
                        curveDates, curveRates, Frequency.Monthly,
                        new Actual365Fixed());
        // Re-create yoyIndex with this curve (cannot relink Handle without
        // RelinkableHandle, but we need to build the index linked to the
        // curve before passing to the surface).
        final var yoyEU = new Handle<YoYInflationTermStructure>(pYTSEU);
        final YoYInflationIndex yoyIndexEUlinked = new YoYInflationIndex(
                "YY_EUHICP", new EURegion(), false, false, /*ratio*/ false,
                Frequency.Monthly, new Period(3, TimeUnit.Months),
                new EURCurrency(), yoyEU);

        // Cap/floor data
        final double[] cStrikes = {0.02, 0.025, 0.03, 0.035, 0.04, 0.05};
        final double[] fStrikes = {-0.01, 0.0, 0.005, 0.01, 0.015, 0.02};
        final Period[] cfMaturities = {
                new Period(3, TimeUnit.Years),  new Period(5, TimeUnit.Years),
                new Period(7, TimeUnit.Years),  new Period(10, TimeUnit.Years),
                new Period(15, TimeUnit.Years), new Period(20, TimeUnit.Years),
                new Period(30, TimeUnit.Years)
        };
        final double[][] cPriceArr = {
                {116.225, 204.945, 296.285, 434.29, 654.47, 844.775, 1132.33},
                {34.305, 71.575, 114.1, 184.33, 307.595, 421.395, 602.35},
                {6.37, 19.085, 35.635, 66.42, 127.69, 189.685, 296.195},
                {1.325, 5.745, 12.585, 26.945, 58.95, 94.08, 158.985},
                {0.501, 2.37, 5.38, 13.065, 31.91, 53.95, 96.97},
                {0.501, 0.695, 1.47, 4.415, 12.86, 23.75, 46.7}
        };
        final double[][] fPriceArr = {
                {0.501, 0.851, 2.44, 6.645, 16.23, 26.85, 46.365},
                {0.501, 2.236, 5.555, 13.075, 28.46, 44.525, 73.08},
                {1.025, 3.935, 9.095, 19.64, 39.93, 60.375, 96.02},
                {2.465, 7.885, 16.155, 31.6, 59.34, 86.21, 132.045},
                {6.9, 17.92, 32.085, 56.08, 95.95, 132.85, 194.18},
                {23.52, 47.625, 74.085, 114.355, 175.72, 229.565, 316.285}
        };
        final Matrix cPrice = new Matrix(cStrikes.length, cfMaturities.length);
        final Matrix fPrice = new Matrix(fStrikes.length, cfMaturities.length);
        for (int i = 0; i < cStrikes.length; i++)
            for (int j = 0; j < cfMaturities.length; j++)
                cPrice.set(i, j, cPriceArr[i][j]);
        for (int i = 0; i < fStrikes.length; i++)
            for (int j = 0; j < cfMaturities.length; j++)
                fPrice.set(i, j, fPriceArr[i][j]);

        final InterpolatedYoYCapFloorTermPriceSurface<BicubicSpline, Cubic> surf;
        try {
            surf = new InterpolatedYoYCapFloorTermPriceSurface<>(
                    BicubicSpline.class, Cubic.class,
                    /* fixingDays */ 0, new Period(3, TimeUnit.Months),
                    yoyIndexEUlinked, CPI.InterpolationType.Linear,
                    nominalEUR, new Actual365Fixed(), cal, bdc,
                    cStrikes, fStrikes, cfMaturities, cPrice, fPrice);
        } catch (final Exception e) {
            // The full bootstrap path requires PiecewiseYoYInflationCurve
            // to converge from the parity rates; if it fails the test
            // surfaces the issue rather than silently passing.
            fail("Surface construction failed: " + e.getClass().getSimpleName()
                    + " " + e.getMessage());
            return;
        }

        // Run cases
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
            final InterpolatedYoYCapFloorTermPriceSurface<BicubicSpline, Cubic> surf,
            final double[] cStrikes, final double[] fStrikes,
            final Period[] cfMaturities, final List<String> mismatches) {
        if (name.startsWith("cap_grid_")) {
            final int us1 = name.indexOf('s');
            final int us2 = name.indexOf("_t");
            final int i = Integer.parseInt(name.substring(us1 + 1, us2));
            final int j = Integer.parseInt(name.substring(us2 + 2));
            final double cpp = c.expectedDouble();
            final Date d = surf.yoyOptionDateFromTenor(cfMaturities[j]);
            final double java = surf.capPrice(d, cStrikes[i]);
            if (!Tolerance.tight(java, cpp)) {
                mismatches.add(fmt(name, cpp, java, "TIGHT"));
            }
        } else if (name.startsWith("floor_grid_")) {
            final int us1 = name.indexOf('s');
            final int us2 = name.indexOf("_t");
            final int i = Integer.parseInt(name.substring(us1 + 1, us2));
            final int j = Integer.parseInt(name.substring(us2 + 2));
            final double cpp = c.expectedDouble();
            final Date d = surf.yoyOptionDateFromTenor(cfMaturities[j]);
            final double java = surf.floorPrice(d, fStrikes[i]);
            if (!Tolerance.tight(java, cpp)) {
                mismatches.add(fmt(name, cpp, java, "TIGHT"));
            }
        } else if (name.startsWith("atm_swap_rate_t")) {
            final int idx = Integer.parseInt(name.substring("atm_swap_rate_t".length()));
            final double cpp = c.expectedDouble();
            final Pair<double[], double[]> rates = surf.atmYoYSwapTimeRates();
            if (idx >= rates.first().length) {
                mismatches.add(name + ": index out of range");
                return;
            }
            final double java = rates.second()[idx];
            if (!Tolerance.loose(java, cpp)) {
                mismatches.add(fmt(name, cpp, java, "LOOSE"));
            }
        } else if (name.equals("metadata")) {
            final JSONObject exp = (JSONObject) c.expectedRaw();
            if (!Tolerance.exact(surf.minStrike(), exp.getDouble("min_strike"))) {
                mismatches.add(fmt(name + ".min_strike",
                        exp.getDouble("min_strike"), surf.minStrike(), "EXACT"));
            }
            if (!Tolerance.exact(surf.maxStrike(), exp.getDouble("max_strike"))) {
                mismatches.add(fmt(name + ".max_strike",
                        exp.getDouble("max_strike"), surf.maxStrike(), "EXACT"));
            }
            if (surf.strikes().length != exp.getInt("num_strikes")) {
                mismatches.add(name + ".num_strikes: expected="
                        + exp.getInt("num_strikes")
                        + " actual=" + surf.strikes().length);
            }
            if (surf.observationLag().length() != exp.getInt("observation_lag_months")) {
                mismatches.add(name + ".observation_lag_months: expected="
                        + exp.getInt("observation_lag_months")
                        + " actual=" + surf.observationLag().length());
            }
        } else {
            mismatches.add(name + ": UNRECOGNIZED case");
        }
    }

    private static String fmt(final String name, final double expected,
            final double actual, final String tier) {
        return String.format("%s: expected=%.17e actual=%.17e diff=%.3e (%s)",
                name, expected, actual, Math.abs(actual - expected), tier);
    }
}
