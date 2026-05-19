/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is released under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2009 Chris Kenyon

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/
package org.jquantlib.testsuite.termstructures.volatility.inflation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.experimental.inflation.InterpolatedYoYCapFloorTermPriceSurface;
import org.jquantlib.experimental.inflation.InterpolatedYoYOptionletStripper;
import org.jquantlib.experimental.inflation.KInterpolatedYoYOptionletVolatilitySurface;
import org.jquantlib.experimental.inflation.YoYCapFloorTermPriceSurfaceLike;
import org.jquantlib.indexes.CPI;
import org.jquantlib.indexes.YoYInflationIndex;
import org.jquantlib.indexes.inflation.EUHICP;
import org.jquantlib.indexes.inflation.UKRPI;
import org.jquantlib.math.interpolations.factories.BicubicSpline;
import org.jquantlib.math.interpolations.factories.Cubic;
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.pricingengines.inflation.YoYInflationUnitDisplacedBlackCapFloorEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.InflationTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.YoYInflationTermStructure;
import org.jquantlib.termstructures.inflation.InterpolatedYoYInflationCurve;
import org.jquantlib.termstructures.volatility.inflation.YoYOptionletVolatilitySurface;
import org.jquantlib.termstructures.yieldcurves.InterpolatedZeroCurve;
import org.jquantlib.testsuite.util.Tolerance;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.jquantlib.util.Pair;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.fail;

/**
 * Port of QuantLib v1.42.1 {@code test-suite/inflationvolatility.cpp}
 * (Phase 2v Track C).
 *
 * <p>Each {@code BOOST_AUTO_TEST_CASE} block in the C++ source is mirrored
 * by a {@code @Test} method here. The C++ file shares a setup helper
 * {@code setup()} and a price-surface helper {@code setupPriceSurface()};
 * we mirror those as private helpers.
 *
 * <p>Two test cases are present in C++:
 * <ul>
 *   <li>{@code testYoYPriceSurfaceToVol} — exercises
 *       {@link KInterpolatedYoYOptionletVolatilitySurface} +
 *       {@link InterpolatedYoYOptionletStripper}. The C++ headers
 *       ({@code kinterpolatedyoyoptionletvolatilitysurface.hpp:43} and
 *       {@code interpolatedyoyoptionletstripper.hpp:41}) explicitly note
 *       {@code \bug Tests currently fail}, so this test is faithfully
 *       ported but {@code @Ignore}d.</li>
 *   <li>{@code testYoYPriceSurfaceToATM} — exercises ATM YoY swap-rate
 *       extraction via {@code atmYoYRate()} which internally bootstraps a
 *       {@code PiecewiseYoYInflationCurve}. Phase 2y A.1 fixed the
 *       {@code CashFlows.bps} npvDate bug that was the prior blocker;
 *       however the bootstrap still fails: a ratio-based {@code YoYInflationIndex}
 *       enters the past-fixing branch for a date before {@code todayMinusLag}
 *       and calls {@code ZeroInflationIndex.fixing()} which returns {@code null}
 *       (no EUHICP fixings seeded, NPE). Root cause: Java's past-branch
 *       threshold for ratio-based indices does not fully mirror C++
 *       {@code YoYInflationIndex::needsForecast(ratio)} which delegates to
 *       {@code underlyingIndex::needsForecast}. {@code @Ignore}d pending
 *       that alignment (Phase 2z or later).</li>
 * </ul>
 *
 * <p>Notes on Java vs C++ divergence in this fixture:
 * <ul>
 *   <li>C++ uses {@code InterpolatedZeroCurve<Cubic>} for the nominal
 *       yield curve. Phase 2x A.1 removed the stale {@code yields[0]==1.0}
 *       assertion from Java's {@link
 *       org.jquantlib.termstructures.yieldcurves.InterpolatedZeroCurve}, so
 *       this test now uses the actual C++ EUR/GBP zero-rate arrays directly
 *       (no FlatForward substitution needed for the nominal curve).</li>
 *   <li>Concrete {@link InterpolatedYoYCapFloorTermPriceSurface} does not
 *       implement {@link YoYCapFloorTermPriceSurfaceLike}; we wrap it in
 *       an inline adapter for the K-surface integration test (see
 *       {@code adapt(...)}).</li>
 * </ul>
 */
public class InflationVolatilityTest {

    // ------------------------------------------------------------------
    // shared fixture state (mirrors C++ file-scope globals)
    // ------------------------------------------------------------------

    private Handle<YieldTermStructure> nominalEUR;
    @SuppressWarnings("unused") // Mirrors C++ globals; kept for fidelity
    private Handle<YieldTermStructure> nominalGBP;

    private Handle<YoYInflationTermStructure> yoyEU;
    @SuppressWarnings("unused") // Mirrors C++ globals; kept for fidelity
    private Handle<YoYInflationTermStructure> yoyUK;

    private List<Double> cStrikesEU;
    private List<Double> fStrikesEU;
    private List<Period> cfMaturitiesEU;
    private Matrix cPriceEU;
    private Matrix fPriceEU;

    @SuppressWarnings("unused") // Mirrors C++ globals; kept for fidelity
    private YoYInflationIndex yoyIndexUK;
    private YoYInflationIndex yoyIndexEU;

    private InterpolatedYoYCapFloorTermPriceSurface<BicubicSpline, Cubic> priceSurfEU;

    /**
     * Mirrors C++ {@code setup()} (lines 91–240): builds nominal yield
     * curves (EUR + GBP), YoY inflation indexes, the YoY EU rates curve,
     * and loads the cap/floor price matrices into class state.
     */
    private void setup() {
        // make sure of the evaluation date
        final Date eval = new Date(23, Month.November, 2007);
        new Settings().setEvaluationDate(eval);

        // YoY indexes — ratio-based (mirrors C++
        // YoYInflationIndex(make_shared<UKRPI>(), yoyUK), which uses the
        // ZII-based ratio constructor).
        // YoY term structures are built later; for now the index is built
        // with an empty handle and re-linked indirectly via the
        // PiecewiseYoYInflationCurve construction below.
        yoyEU = new Handle<>();
        yoyUK = new Handle<>();
        yoyIndexUK = new YoYInflationIndex(
                new UKRPI(Frequency.Monthly, false, false), yoyUK);
        yoyIndexEU = new YoYInflationIndex(
                new EUHICP(Frequency.Monthly, false, false), yoyEU);

        // Nominal yield curves — mirror C++ inflationvolatility.cpp:101-155
        // InterpolatedZeroCurve<Cubic> construction. The Phase 2x A.1 fix
        // removes the stale yields[0]==1.0 assertion that previously forced
        // a FlatForward substitution.
        final DayCounter dc = new Actual365Fixed();

        final double[] timesEUR = {0.0109589, 0.0684932, 0.263014, 0.317808,
                0.567123, 0.816438, 1.06575, 1.31507, 1.56438, 2.0137,
                3.01918, 4.01644, 5.01644, 6.01644, 7.01644, 8.01644,
                9.02192, 10.0192, 12.0192, 15.0247, 20.0301, 25.0356,
                30.0329, 40.0384, 50.0466};
        final double[] ratesEUR = {0.0415600, 0.0426840, 0.0470980, 0.0458506,
                0.0449550, 0.0439784, 0.0431887, 0.0426604, 0.0422925,
                0.0424591, 0.0421477, 0.0421853, 0.0424016, 0.0426969,
                0.0430804, 0.0435011, 0.0439368, 0.0443825, 0.0452589,
                0.0463389, 0.0472636, 0.0473401, 0.0470629, 0.0461092,
                0.0450794};

        final double[] timesGBP = {0.008219178, 0.010958904, 0.01369863,
                0.019178082, 0.073972603, 0.323287671, 0.57260274,
                0.821917808, 1.071232877, 1.320547945, 1.506849315,
                2.002739726, 3.002739726, 4.002739726, 5.005479452,
                6.010958904, 7.008219178, 8.005479452, 9.008219178,
                10.00821918, 12.01369863, 15.0109589, 20.01369863,
                25.01917808, 30.02191781, 40.03287671, 50.03561644,
                60.04109589, 70.04931507};
        final double[] ratesGBP = {0.0577363, 0.0582314, 0.0585265, 0.0587165,
                0.0596598, 0.0612506, 0.0589676, 0.0570512, 0.0556147,
                0.0546082, 0.0549492, 0.053801, 0.0529333, 0.0524068,
                0.0519712, 0.0516615, 0.0513711, 0.0510433, 0.0507974,
                0.0504833, 0.0498998, 0.0490464, 0.04768, 0.0464862,
                0.045452, 0.0437699, 0.0425311, 0.0420073, 0.041151};

        final Date[] dEUR = new Date[timesEUR.length];
        for (int i = 0; i < timesEUR.length; i++) {
            final int ys = (int) Math.floor(timesEUR[i]);
            final int ds = (int) ((timesEUR[i] - ys) * 365);
            dEUR[i] = eval.add(new Period(ys, TimeUnit.Years))
                    .add(new Period(ds, TimeUnit.Days));
        }
        final InterpolatedZeroCurve<Cubic> euriborTS =
                new InterpolatedZeroCurve<>(Cubic.class, dEUR, ratesEUR, dc);
        nominalEUR = new Handle<>(euriborTS);

        final Date[] dGBP = new Date[timesGBP.length];
        for (int i = 0; i < timesGBP.length; i++) {
            final int ys = (int) Math.floor(timesGBP[i]);
            final int ds = (int) ((timesGBP[i] - ys) * 365);
            dGBP[i] = eval.add(new Period(ys, TimeUnit.Years))
                    .add(new Period(ds, TimeUnit.Days));
        }
        final InterpolatedZeroCurve<Cubic> gbpLiborTS =
                new InterpolatedZeroCurve<>(Cubic.class, dGBP, ratesGBP, dc);
        nominalGBP = new Handle<>(gbpLiborTS);

        // YoY EU rates curve (mirrors C++ yoyEUrates[] — 31 values from
        // base period out 30 years on the cap-start observation lag).
        // Note that these are NOT swap rates; the first value MUST be
        // in the base period (i.e. the first rate is for a negative
        // time).
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
        // base date = first day of the inflation period containing
        // (eval - 1 month).
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

        final InterpolatedYoYInflationCurve<Linear> pYTSEU =
                new InterpolatedYoYInflationCurve<>(Linear.class, eval,
                        curveDates, curveRates, Frequency.Monthly,
                        new Actual365Fixed());
        // Re-create the EU index with the freshly built YoY curve so
        // downstream consumers see a properly linked term structure.
        // (Java has no RelinkableHandle equivalent of yoyEU.linkTo here.)
        yoyEU = new Handle<>(pYTSEU);
        yoyIndexEU = new YoYInflationIndex(
                new EUHICP(Frequency.Monthly, false, false), yoyEU);

        // ----- Cap/floor price data -----
        final double[] capStrikesEU = {0.02, 0.025, 0.03, 0.035, 0.04, 0.05};
        final Period[] capMaturitiesEU = {
                new Period(3, TimeUnit.Years),  new Period(5, TimeUnit.Years),
                new Period(7, TimeUnit.Years),  new Period(10, TimeUnit.Years),
                new Period(15, TimeUnit.Years), new Period(20, TimeUnit.Years),
                new Period(30, TimeUnit.Years)
        };
        final double[][] capPricesEU = {
                {116.225, 204.945, 296.285, 434.29, 654.47, 844.775, 1132.33},
                {34.305, 71.575, 114.1, 184.33, 307.595, 421.395, 602.35},
                {6.37, 19.085, 35.635, 66.42, 127.69, 189.685, 296.195},
                {1.325, 5.745, 12.585, 26.945, 58.95, 94.08, 158.985},
                {0.501, 2.37, 5.38, 13.065, 31.91, 53.95, 96.97},
                {0.501, 0.695, 1.47, 4.415, 12.86, 23.75, 46.7}
        };

        final double[] floorStrikesEU = {-0.01, 0.0, 0.005, 0.01, 0.015, 0.02};
        final double[][] floorPricesEU = {
                {0.501, 0.851, 2.44, 6.645, 16.23, 26.85, 46.365},
                {0.501, 2.236, 5.555, 13.075, 28.46, 44.525, 73.08},
                {1.025, 3.935, 9.095, 19.64, 39.93, 60.375, 96.02},
                {2.465, 7.885, 16.155, 31.6, 59.34, 86.21, 132.045},
                {6.9, 17.92, 32.085, 56.08, 95.95, 132.85, 194.18},
                {23.52, 47.625, 74.085, 114.355, 175.72, 229.565, 316.285}
        };

        cStrikesEU = new ArrayList<>();
        for (final double k : capStrikesEU) cStrikesEU.add(k);
        fStrikesEU = new ArrayList<>();
        for (final double k : floorStrikesEU) fStrikesEU.add(k);
        cfMaturitiesEU = new ArrayList<>(Arrays.asList(capMaturitiesEU));

        final Matrix tcPriceEU = new Matrix(capStrikesEU.length, capMaturitiesEU.length);
        final Matrix tfPriceEU = new Matrix(floorStrikesEU.length, capMaturitiesEU.length);
        for (int i = 0; i < capStrikesEU.length; i++) {
            for (int j = 0; j < capMaturitiesEU.length; j++) {
                tcPriceEU.set(i, j, capPricesEU[i][j]);
            }
        }
        for (int i = 0; i < floorStrikesEU.length; i++) {
            for (int j = 0; j < capMaturitiesEU.length; j++) {
                tfPriceEU.set(i, j, floorPricesEU[i][j]);
            }
        }
        cPriceEU = tcPriceEU;
        fPriceEU = tfPriceEU;
    }

    /**
     * Mirrors C++ {@code setupPriceSurface()} (lines 243–268): build the
     * EU YoY cap/floor term-price surface using the matrices loaded by
     * {@link #setup()}. Constructed with {@code <BicubicSpline,Cubic>}
     * to match the C++ template parameters
     * {@code InterpolatedYoYCapFloorTermPriceSurface<Bicubic,Cubic>}.
     */
    private void setupPriceSurface() {
        final int fixingDays = 0;
        final int lag = 3;  // must be 3 because we use an interpolated index (EU)
        final Period yyLag = new Period(lag, TimeUnit.Months);
        final DayCounter dc = new Actual365Fixed();
        final Target cal = new Target();
        final BusinessDayConvention bdc = BusinessDayConvention.ModifiedFollowing;

        final double[] cStrikes = new double[cStrikesEU.size()];
        for (int i = 0; i < cStrikesEU.size(); i++) cStrikes[i] = cStrikesEU.get(i);
        final double[] fStrikes = new double[fStrikesEU.size()];
        for (int i = 0; i < fStrikesEU.size(); i++) fStrikes[i] = fStrikesEU.get(i);
        final Period[] cfMaturities = cfMaturitiesEU.toArray(new Period[0]);

        priceSurfEU = new InterpolatedYoYCapFloorTermPriceSurface<>(
                BicubicSpline.class, Cubic.class,
                fixingDays, yyLag, yoyIndexEU, CPI.InterpolationType.Linear,
                nominalEUR, dc, cal, bdc,
                cStrikes, fStrikes, cfMaturities, cPriceEU, fPriceEU);
    }

    // ------------------------------------------------------------------
    // BOOST_AUTO_TEST_CASE(testYoYPriceSurfaceToVol)
    // ------------------------------------------------------------------

    /**
     * Mirrors C++ {@code testYoYPriceSurfaceToVol} (lines 271–352):
     * conversion from YoY price surface to YoY volatility surface
     * (K-interpolated, via {@link InterpolatedYoYOptionletStripper}).
     *
     * <p>{@code @Ignore}d because both
     * {@link KInterpolatedYoYOptionletVolatilitySurface}
     * ({@code kinterpolatedyoyoptionletvolatilitysurface.hpp:43}: "{@code
     * \bug Tests currently fail.}") and
     * {@link InterpolatedYoYOptionletStripper}
     * ({@code interpolatedyoyoptionletstripper.hpp:41}: same note) are
     * documented as {@code \bug Tests currently fail} in C++ v1.42.1.
     * Per binding directive 2026-05-08 (do not relax tolerance for
     * documented-buggy tests), this is ported faithfully but ignored
     * pending Phase 2x or upstream fix.
     */
    @Ignore("Phase 2x or upstream: C++ headers document "
            + "kinterpolatedyoyoptionletvolatilitysurface.hpp:43 and "
            + "interpolatedyoyoptionletstripper.hpp:41 \\bug Tests currently fail")
    @Test
    public void testYoYPriceSurfaceToVol() {
        setup();

        // first get the price surface set up
        setupPriceSurface();

        // caplet pricer, recall that
        //   setCapletVolatility(Handle<YoYOptionletVolatilitySurface>)
        // exists ... we'll use it with the -Curve variant of the surface.
        // test UNIT DISPLACED pricer.
        final Handle<YoYOptionletVolatilitySurface> hVS = new Handle<>();
        final YoYInflationUnitDisplacedBlackCapFloorEngine yoyPricerUD =
                new YoYInflationUnitDisplacedBlackCapFloorEngine(
                        yoyIndexEU, hVS, nominalEUR);
        // N.B. the vol gets set in the stripper ... else no point!

        // cap stripper
        final InterpolatedYoYOptionletStripper<Linear> yoyOptionletStripper =
                new InterpolatedYoYOptionletStripper<>(Linear.class);

        // now set up all the variables for the stripping
        final int settlementDays = 0;
        final Target cal = new Target();
        final BusinessDayConvention bdc = BusinessDayConvention.ModifiedFollowing;
        final DayCounter dc = new Actual365Fixed();

        final YoYCapFloorTermPriceSurfaceLike capFloorPrices = adapt(priceSurfEU);
        final Period lag = priceSurfEU.observationLag();

        // when you have bad data, i.e. very low/constant prices for
        // short-dated extreme strikes, then you cannot assume constant
        // caplet vol (else arbitrage). N.B. if this is too extreme then
        // you can't get a no-arbitrage solution anyway. The way the
        // slope is used means that the slope is proportional to the
        // level so higher slopes at the edges when things are more
        // volatile.
        final double slope = -0.5;

        // Actually it doesn't matter what the interpolation is because
        // we only intend to use the K values that correspond to quotes
        // ... for model fitting.
        final KInterpolatedYoYOptionletVolatilitySurface<Linear> yoySurf =
                new KInterpolatedYoYOptionletVolatilitySurface<>(Linear.class,
                        settlementDays, cal, bdc, dc, lag,
                        capFloorPrices, yoyPricerUD, yoyOptionletStripper,
                        slope);

        // now use it for something ... like stating what the T=const
        // lines look like
        final double[] volATyear1 = {
                0.0129, 0.0094, 0.0083, 0.0073, 0.0064,
                0.0058, 0.0042, 0.0046, 0.0053, 0.0064,
                0.0098
        };
        final double[] volATyear3 = {
                0.0080, 0.0058, 0.0051, 0.0045, 0.0040,
                0.0035, 0.0026, 0.0028, 0.0033, 0.0040,
                0.0061
        };

        final double eps = 0.0001;

        Date d = yoySurf.baseDate().add(new Period(1, TimeUnit.Years));
        Pair<List<Double>, List<Double>> someSlice = yoySurf.Dslice(d);
        final int n1 = someSlice.first().size();
        final List<String> mismatches = new ArrayList<>();
        for (int i = 0; i < n1; i++) {
            if (i >= volATyear1.length) {
                mismatches.add("year1 slice has more entries than expected ("
                        + n1 + " vs " + volATyear1.length + ")");
                break;
            }
            final double java = someSlice.second().get(i);
            final double cpp = volATyear1[i];
            if (Math.abs(java - cpp) >= eps) {
                mismatches.add(String.format(
                        "could not recover 1yr vol at K=%d: java=%.6f vs cpp=%.6f",
                        i, java, cpp));
            }
        }

        d = yoySurf.baseDate().add(new Period(3, TimeUnit.Years));
        someSlice = yoySurf.Dslice(d);
        final int n3 = someSlice.first().size();
        for (int i = 0; i < n3; i++) {
            if (i >= volATyear3.length) {
                mismatches.add("year3 slice has more entries than expected ("
                        + n3 + " vs " + volATyear3.length + ")");
                break;
            }
            final double java = someSlice.second().get(i);
            final double cpp = volATyear3[i];
            if (Math.abs(java - cpp) >= eps) {
                mismatches.add(String.format(
                        "could not recover 3yr vol at K=%d: java=%.6f vs cpp=%.6f",
                        i, java, cpp));
            }
        }

        if (!mismatches.isEmpty()) {
            fail(mismatches.size() + " mismatch(es):\n"
                    + String.join("\n", mismatches));
        }
    }

    // ------------------------------------------------------------------
    // BOOST_AUTO_TEST_CASE(testYoYPriceSurfaceToATM)
    // ------------------------------------------------------------------

    /**
     * Mirrors C++ {@code testYoYPriceSurfaceToATM} (lines 354–391):
     * conversion from YoY cap/floor surface to YoY inflation term
     * structure. Verifies the cached {@code crv}/{@code swaps}/{@code ayoy}
     * arrays the C++ test stores.
     *
     * <p>Tier: {@link Tolerance#within} with {@code 2e-5} per C++
     * {@code eps = 2e-5}.
     *
     * <p>Phase 5e.5b-CFC-d-289 fix: the surface's
     * {@code calculateYoYTermStructure()} now forwards its nominal yield
     * handle {@code nominalTS_} to each {@link
     * org.jquantlib.termstructures.inflation.YearOnYearInflationSwapHelper},
     * mirroring C++ {@code yoycapfloortermpricesurface.hpp:540}. This
     * eliminates the prior FlatForward(0) discount substitution inside the
     * helpers' internal YYIIS and aligns the bootstrapped
     * {@link org.jquantlib.termstructures.inflation.PiecewiseYoYInflationCurve}
     * with C++ reference values within the C++ {@code eps = 2e-5}.
     */
    @Test
    public void testYoYPriceSurfaceToATM() {
        setup();
        setupPriceSurface();

        final Pair<double[], double[]> yyATMt = priceSurfEU.atmYoYSwapTimeRates();
        final Pair<Date[], double[]> yyATMd = priceSurfEU.atmYoYSwapDateRates();

        final double[] crv = {0.024586, 0.0247575, 0.0249396, 0.0252596,
                              0.0258498, 0.0262883, 0.0267915};
        final double[] swaps = {0.024586, 0.0247575, 0.0249396, 0.0252596,
                                0.0258498, 0.0262883, 0.0267915};
        final double[] ayoy = {0.0247659, 0.0251437, 0.0255945, 0.0265015,
                               0.0280457, 0.0285534, 0.0295884};
        final double eps = 2e-5;
        final List<String> mismatches = new ArrayList<>();

        for (int i = 0; i < yyATMt.first().length && i < crv.length; i++) {
            final double java = yyATMt.second()[i];
            final double cpp = crv[i];
            // Tier: per-test tolerance 2e-5 per C++ eps (cap/floor parity
            // intersection rounding); the surface's nominal-curve discount
            // path now matches C++ since Phase 5e.5b-CFC-d-289 forwarded
            // nominalTS_ to the helpers.
            if (!Tolerance.within(java, cpp, eps, "C++ test eps=2e-5")) {
                mismatches.add(String.format(
                        "could not recover cached yoy swap curve at i=%d: "
                        + "java=%.7f vs cpp=%.7f",
                        i, java, cpp));
            }
        }

        for (int i = 0; i < yyATMd.first().length && i < swaps.length; i++) {
            final double java = priceSurfEU.atmYoYSwapRate(yyATMd.first()[i]);
            final double cpp = swaps[i];
            if (!Tolerance.within(java, cpp, eps, "C++ test eps=2e-5")) {
                mismatches.add(String.format(
                        "could not recover yoy swap curve at i=%d: "
                        + "java=%.7f vs cpp=%.7f",
                        i, java, cpp));
            }
        }

        for (int i = 0; i < yyATMd.first().length && i < ayoy.length; i++) {
            final double java = priceSurfEU.atmYoYRate(yyATMd.first()[i]);
            final double cpp = ayoy[i];
            if (!Tolerance.within(java, cpp, eps, "C++ test eps=2e-5")) {
                mismatches.add(String.format(
                        "could not recover cached yoy curve at i=%d: "
                        + "java=%.7f vs cpp=%.7f at date=%s",
                        i, java, cpp, yyATMd.first()[i]));
            }
        }

        if (!mismatches.isEmpty()) {
            fail(mismatches.size() + " mismatch(es):\n"
                    + String.join("\n", mismatches));
        }
    }

    // ------------------------------------------------------------------
    // Inline adapter — wraps InterpolatedYoYCapFloorTermPriceSurface to
    // satisfy the YoYCapFloorTermPriceSurfaceLike interface contract
    // expected by KInterpolatedYoYOptionletVolatilitySurface and
    // InterpolatedYoYOptionletStripper.
    //
    // Phase 2x: the concrete YoYCapFloorTermPriceSurface should
    // implement YoYCapFloorTermPriceSurfaceLike directly, removing the
    // need for this adapter.
    // ------------------------------------------------------------------

    private static YoYCapFloorTermPriceSurfaceLike adapt(
            final InterpolatedYoYCapFloorTermPriceSurface<?, ?> s) {
        return new YoYCapFloorTermPriceSurfaceLike() {
            @Override public Date referenceDate() { return s.referenceDate(); }
            @Override public DayCounter dayCounter() { return s.dayCounter(); }
            @Override public Calendar calendar() { return s.calendar(); }
            @Override public BusinessDayConvention businessDayConvention() {
                return s.businessDayConvention();
            }
            @Override public double timeFromReference(final Date d) {
                return s.timeFromReference(d);
            }
            @Override public YoYInflationIndex yoyIndex() { return s.yoyIndex(); }
            @Override public YoYInflationTermStructure YoYTS() { return s.yoyTS(); }
            @Override public Period observationLag() { return s.observationLag(); }
            @Override public Frequency frequency() { return s.frequency(); }
            @Override public boolean indexIsInterpolated() {
                return s.indexIsInterpolated();
            }
            @Override public int fixingDays() { return s.fixingDays(); }
            @Override public Date baseDate() { return s.baseDate(); }
            @Override public List<Double> capStrikes() {
                return toList(s.capStrikes());
            }
            @Override public List<Double> floorStrikes() {
                return toList(s.floorStrikes());
            }
            @Override public List<Double> strikes() {
                return toList(s.strikes());
            }
            @Override public List<Period> maturities() {
                return Arrays.asList(s.maturities());
            }
            @Override public Period minMaturity() {
                return s.maturities()[0];
            }
            @Override public Date yoyOptionDateFromTenor(final Period p) {
                return s.yoyOptionDateFromTenor(p);
            }
            @Override public double capPrice(final Period p, final double k) {
                return s.capPrice(p, k);
            }
            @Override public double floorPrice(final Period p, final double k) {
                return s.floorPrice(p, k);
            }
            @Override public double capPrice(final Date d, final double k) {
                return s.capPrice(d, k);
            }
            @Override public double floorPrice(final Date d, final double k) {
                return s.floorPrice(d, k);
            }
        };
    }

    private static List<Double> toList(final double[] xs) {
        final List<Double> r = new ArrayList<>(xs.length);
        for (final double x : xs) r.add(x);
        return r;
    }
}
