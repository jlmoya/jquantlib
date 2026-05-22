/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.basismodels;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.cashflow.IborCoupon;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.daycounters.Thirty360;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.experimental.basismodels.SwaptionCashFlows;
import org.jquantlib.experimental.basismodels.TenorOptionletVTS;
import org.jquantlib.experimental.basismodels.TenorOptionletVTS.TwoParameterCorrelation;
import org.jquantlib.experimental.basismodels.TenorSwaptionVTS;
import org.jquantlib.indexes.Euribor6M;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.instruments.Settlement;
import org.jquantlib.instruments.Swaption;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.math.interpolations.factories.Cubic;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.SwaptionVolatilityStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.optionlet.OptionletVolatilityStructure;
import org.jquantlib.termstructures.volatilities.optionlet.StrippedOptionlet;
import org.jquantlib.termstructures.volatilities.optionlet.StrippedOptionletAdapter;
import org.jquantlib.termstructures.volatilities.optionlet.StrippedOptionletBase;
import org.jquantlib.termstructures.volatilities.swaption.SwaptionVolatilityMatrix;
import org.jquantlib.termstructures.yieldcurves.InterpolatedZeroCurve;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.MakeSchedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.NullCalendar;
import org.jquantlib.time.calendars.Target;
import org.junit.Before;
import org.junit.Test;

/**
 * Port of C++ {@code test-suite/basismodels.cpp} v1.42.1 @ {@code 099987f0ca}.
 *
 * <p>Cross-validated against C++ market data block (terms, discRates,
 * proj3mRates, proj6mRates, capletVols, swaptionVols). Tolerances mirror C++:
 * <ul>
 *   <li>SwaptionCashFlows: {@code 1e-8} (TIGHT).</li>
 *   <li>TenorOptionletVTS: {@code 0.0001} (1bp) for the de-correlation
 *       inequality test and {@code 0.001}/{@code 0.0001} (10bp/1bp) for the
 *       perfect-correlation reconstruction test (per C++).</li>
 *   <li>TenorSwaptionVTS: {@code 1e-8} (TIGHT) for reconstruction tests.</li>
 * </ul>
 *
 * <p>Phase 5e.5b-CFC-d-78 — bodies wired up against the now-ported
 * {@link SwaptionCashFlows}, {@link TenorOptionletVTS}, and
 * {@link TenorSwaptionVTS} classes.
 */
public class BasisModelsTest {

    // -------------------------------------------------------------------------
    // Auxiliary data — mirrors C++ basismodels.cpp lines 48-91, 132-156
    // -------------------------------------------------------------------------

    private static final Period[] TERMS = new Period[] {
            new Period(0, TimeUnit.Days),
            new Period(1, TimeUnit.Years),
            new Period(2, TimeUnit.Years),
            new Period(3, TimeUnit.Years),
            new Period(5, TimeUnit.Years),
            new Period(7, TimeUnit.Years),
            new Period(10, TimeUnit.Years),
            new Period(15, TimeUnit.Years),
            new Period(20, TimeUnit.Years),
            new Period(61, TimeUnit.Years)
    };

    private static final double[] DISC_RATES = new double[] {
            -0.00147407, -0.001761684, -0.001736745, -0.00119244,  0.000896055,
             0.003537077, 0.007213824,  0.011391278,  0.013334611,  0.013982809
    };

    private static final double[] PROJ_3M_RATES = new double[] {
            -0.000483439, -0.000578569, -0.000383832, 0.000272656, 0.002478699,
             0.005100113,  0.008750643,  0.012788095, 0.014534052, 0.014942896
    };

    private static final double[] PROJ_6M_RATES = new double[] {
             0.000233608, 0.000218862, 0.000504018, 0.001240556, 0.003554415,
             0.006153921, 0.009688264, 0.013521628, 0.015136391, 0.015377704
    };

    private static final Period[] CAPLET_TERMS = new Period[] {
            new Period(1,  TimeUnit.Years),
            new Period(2,  TimeUnit.Years),
            new Period(3,  TimeUnit.Years),
            new Period(5,  TimeUnit.Years),
            new Period(7,  TimeUnit.Years),
            new Period(10, TimeUnit.Years),
            new Period(15, TimeUnit.Years),
            new Period(20, TimeUnit.Years),
            new Period(25, TimeUnit.Years),
            new Period(30, TimeUnit.Years)
    };

    private static final double[] CAPLET_STRIKES = new double[] {
            -0.0050, 0.0000, 0.0050, 0.0100, 0.0150, 0.0200, 0.0300, 0.0500
    };

    private static final Period[] SWAPTION_VTS_TERMS = new Period[] {
            new Period( 1, TimeUnit.Years),
            new Period( 5, TimeUnit.Years),
            new Period(10, TimeUnit.Years),
            new Period(20, TimeUnit.Years),
            new Period(30, TimeUnit.Years)
    };

    /** Use a fixed date in the past so all swap cash-flows are forward-dated. */
    private static final Date TODAY = new Date(2, Month.January, 2018);

    @Before
    public void setUp() {
        new Settings().setEvaluationDate(TODAY);
    }

    // -------------------------------------------------------------------------
    // Test harness helpers — mirror C++ getYTS / getOptionletTS / getSwaptionVTS
    // -------------------------------------------------------------------------

    private static Handle<YieldTermStructure> getYTS(final Period[] terms,
                                                     final double[] rates,
                                                     final double spread) {
        final Date today = new Settings().evaluationDate();
        final NullCalendar nullCal = new NullCalendar();
        final Date[] dates = new Date[terms.length];
        final double[] ratesPlusSpread = new double[rates.length];
        for (int i = 0; i < terms.length; i++) {
            dates[i] = nullCal.advance(today, terms[i], BusinessDayConvention.Unadjusted);
            ratesPlusSpread[i] = rates[i] + spread;
        }
        final InterpolatedZeroCurve<Cubic> ts =
                new InterpolatedZeroCurve<Cubic>(
                        Cubic.class, dates, ratesPlusSpread,
                        new Actual365Fixed(), nullCal);
        return new Handle<YieldTermStructure>(ts);
    }

    private static Handle<YieldTermStructure> getYTS(final Period[] terms, final double[] rates) {
        return getYTS(terms, rates, 0.0);
    }

    /** Build the 3m optionlet VTS used by {@code testTenoroptionletvts}. */
    private static Handle<OptionletVolatilityStructure> getOptionletTS() {
        final Date today = new Settings().evaluationDate();
        final Target target = new Target();
        final List<Date> dates = new ArrayList<>(CAPLET_TERMS.length);
        for (Period t : CAPLET_TERMS) {
            dates.add(target.advance(today, t, BusinessDayConvention.Following));
        }
        final double[][] capletVolsData = new double[][] {
            { 0.003010094, 0.002628065, 0.00456118,  0.006731268, 0.008678572, 0.010570881, 0.014149552, 0.021000638 },
            { 0.004173715, 0.003727039, 0.004180263, 0.005726083, 0.006905876, 0.008263514, 0.010555395, 0.014976523 },
            { 0.005870143, 0.005334526, 0.005599775, 0.006633987, 0.007773317, 0.009036581, 0.011474391, 0.016277549 },
            { 0.007458597, 0.007207522, 0.007263995, 0.007308727, 0.007813586, 0.008274858, 0.009743988, 0.012555171 },
            { 0.007711531, 0.007608826, 0.007572816, 0.007684107, 0.007971932, 0.008283118, 0.009268828, 0.011574083 },
            { 0.007619605, 0.007639059, 0.007719825, 0.007823373, 0.00800813,  0.008113384, 0.008616374, 0.009785436 },
            { 0.007312199, 0.007352993, 0.007369116, 0.007468333, 0.007515657, 0.00767695,  0.008020447, 0.009072769 },
            { 0.006905851, 0.006966315, 0.007056413, 0.007116494, 0.007259661, 0.00733308,  0.007667563, 0.008419696 },
            { 0.006529553, 0.006630731, 0.006749022, 0.006858027, 0.007001959, 0.007139097, 0.007390404, 0.008036255 },
            { 0.006225482, 0.006404012, 0.00651594,  0.006642273, 0.006640887, 0.006885713, 0.007093024, 0.00767373  }
        };
        final List<List<Handle<? extends Quote>>> capletVolQuotes =
                new ArrayList<List<Handle<? extends Quote>>>(capletVolsData.length);
        for (double[] row : capletVolsData) {
            final List<Handle<? extends Quote>> rowH =
                    new ArrayList<>(row.length);
            for (double v : row) {
                rowH.add(new Handle<Quote>(new SimpleQuote(v)));
            }
            capletVolQuotes.add(rowH);
        }
        // Same strikes for all maturities
        final List<List<Double>> strikesAll = new ArrayList<>(dates.size());
        final List<Double> strikeRow = new ArrayList<>(CAPLET_STRIKES.length);
        for (double s : CAPLET_STRIKES) {
            strikeRow.add(s);
        }
        for (int i = 0; i < dates.size(); i++) {
            strikesAll.add(strikeRow);
        }
        final Handle<YieldTermStructure> curve3m = getYTS(TERMS, PROJ_3M_RATES);
        final IborIndex idx = new Euribor6M(curve3m); // C++ uses Euribor6M wrapping 3m curve (typo preserved)
        // Note: C++ constructs new Euribor6M(curve3m) and treats it as the
        // 3m base index. We preserve the typo because changing it would change
        // the test data signature relative to v1.42.1.
        final StrippedOptionletBase tmp1 = new StrippedOptionlet(
                2, new Target(), BusinessDayConvention.Following, idx,
                dates, strikesAll, capletVolQuotes,
                new Actual365Fixed(), VolatilityType.Normal, 0.0);
        final StrippedOptionletAdapter tmp2 = new StrippedOptionletAdapter(tmp1);
        return new Handle<OptionletVolatilityStructure>(tmp2);
    }

    private static Handle<SwaptionVolatilityStructure> getSwaptionVTS() {
        final double[][] swaptionVolsData = new double[][] {
            { 0.002616, 0.00468,  0.0056,   0.005852, 0.005823 },
            { 0.006213, 0.00643,  0.006622, 0.006124, 0.005958 },
            { 0.006658, 0.006723, 0.006602, 0.005802, 0.005464 },
            { 0.005728, 0.005814, 0.005663, 0.004689, 0.004276 },
            { 0.005041, 0.005059, 0.004746, 0.003927, 0.003608 }
        };
        final List<List<Handle<? extends Quote>>> volH =
                new ArrayList<List<Handle<? extends Quote>>>(swaptionVolsData.length);
        for (double[] row : swaptionVolsData) {
            final List<Handle<? extends Quote>> rowH =
                    new ArrayList<>(row.length);
            for (double v : row) {
                rowH.add(new Handle<Quote>(new SimpleQuote(v)));
            }
            volH.add(rowH);
        }
        final List<Period> optT = new ArrayList<>(SWAPTION_VTS_TERMS.length);
        final List<Period> swpT = new ArrayList<>(SWAPTION_VTS_TERMS.length);
        for (Period p : SWAPTION_VTS_TERMS) {
            optT.add(p);
            swpT.add(p);
        }
        final SwaptionVolatilityMatrix mat = new SwaptionVolatilityMatrix(
                new Target(), BusinessDayConvention.Following,
                optT, swpT, volH,
                new Actual365Fixed(),
                true, // flatExtrapolation
                VolatilityType.Normal,
                null /* shifts */);
        return new Handle<SwaptionVolatilityStructure>(mat);
    }

    // -------------------------------------------------------------------------
    // SwaptionCashFlows core — mirrors C++ testSwaptioncfs(bool contTenorSpread)
    // -------------------------------------------------------------------------

    private void runSwaptioncfs(final boolean contTenorSpread) {
        final boolean usingAtParCoupons =
                IborCoupon.Settings.getInstance().usingAtParCoupons();

        final Handle<YieldTermStructure> discYTS = getYTS(TERMS, DISC_RATES);
        final Handle<YieldTermStructure> proj6mYTS = getYTS(TERMS, PROJ_6M_RATES);

        final IborIndex euribor6m = new Euribor6M(proj6mYTS);

        // Vanilla swap details
        final Target target = new Target();
        final Date today = new Settings().evaluationDate();
        final Date swapStart = target.advance(today, new Period(5, TimeUnit.Years),
                BusinessDayConvention.Following);
        final Date swapEnd = target.advance(swapStart, new Period(10, TimeUnit.Years),
                BusinessDayConvention.Following);
        final Date exerciseDate = target.advance(swapStart, -2, TimeUnit.Days,
                BusinessDayConvention.Preceding, false);

        final Schedule fixedSchedule = new MakeSchedule(
                swapStart, swapEnd, new Period(1, TimeUnit.Years),
                target, BusinessDayConvention.ModifiedFollowing)
                .backwards()
                .schedule();

        final Schedule floatSchedule = new MakeSchedule(
                swapStart, swapEnd, new Period(6, TimeUnit.Months),
                target, BusinessDayConvention.ModifiedFollowing)
                .backwards()
                .schedule();

        final VanillaSwap swap = new VanillaSwap(
                VanillaSwap.Type.Payer, 10000.0,
                fixedSchedule, 0.03,
                new Thirty360(Thirty360.Convention.BondBasis),
                floatSchedule, euribor6m, 0.0,
                euribor6m.dayCounter());
        swap.setPricingEngine(new DiscountingSwapEngine(discYTS));

        final Swaption swaption = new Swaption(
                swap, new EuropeanExercise(exerciseDate), Settlement.Type.Physical);

        // SwaptionCashFlows under test
        final SwaptionCashFlows cashFlows =
                new SwaptionCashFlows(swaption, discYTS, contTenorSpread);

        // Exercise time check (exact match)
        final double exerciseTime = new Actual365Fixed().yearFraction(
                discYTS.currentLink().referenceDate(),
                swaption.exercise().dates().get(0));
        assertEquals("Swaption cash flow exercise time must match manual calc",
                exerciseTime, cashFlows.exerciseTimes().get(0), 0.0);

        final double tol = 1.0e-8;

        // fixed leg NPV: (discounted) fixed coupons must match -fixedLegNPV (payer)
        double fixedLeg = 0.0;
        for (int k = 0; k < cashFlows.fixedTimes().size(); k++) {
            fixedLeg += cashFlows.fixedWeights().get(k)
                    * discYTS.currentLink().discount(cashFlows.fixedTimes().get(k));
        }
        assertEquals("Swaption fixed leg NPV mismatch",
                -swap.fixedLegNPV(), fixedLeg, tol);

        // floating leg NPV
        double floatLeg = 0.0;
        for (int k = 0; k < cashFlows.floatTimes().size(); k++) {
            floatLeg += cashFlows.floatWeights().get(k)
                    * discYTS.currentLink().discount(cashFlows.floatTimes().get(k));
        }
        assertEquals("Swaption floating leg NPV mismatch",
                swap.floatingLegNPV(), floatLeg, tol);

        // Single-curve: float spread coupons (interior) should vanish.
        // Relax tolerance for indexed-coupon case (mirrors C++ tol2 logic).
        final double tol2 = usingAtParCoupons ? tol : 0.02;
        final SwaptionCashFlows singleCurveCashFlows =
                new SwaptionCashFlows(swaption, proj6mYTS, contTenorSpread);
        for (int k = 1; k < singleCurveCashFlows.floatWeights().size() - 1; k++) {
            final double w = singleCurveCashFlows.floatWeights().get(k);
            assertTrue("Single-curve float spread coupon (k=" + k + ") = " + w
                            + " exceeds tol " + tol2,
                    Math.abs(w) <= tol2);
        }
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    public void testSwaptioncfsContCompSpread() {
        QL.info("::::: BasisModelsTest::testSwaptioncfsContCompSpread :::::");
        runSwaptioncfs(true);
    }

    @Test
    public void testSwaptioncfsSimpleCompSpread() {
        QL.info("::::: BasisModelsTest::testSwaptioncfsSimpleCompSpread :::::");
        runSwaptioncfs(false);
    }

    @Test
    public void testTenoroptionletvts() {
        QL.info("::::: BasisModelsTest::testTenoroptionletvts :::::");

        final double spread = 0.01;
        final Handle<YieldTermStructure> proj3mYTS = getYTS(TERMS, PROJ_3M_RATES);
        final Handle<YieldTermStructure> proj6mYTS = getYTS(TERMS, PROJ_3M_RATES, spread);
        final IborIndex euribor3m = new Euribor6M(proj3mYTS); // C++ typo preserved
        final IborIndex euribor6m = new Euribor6M(proj6mYTS);

        final Handle<OptionletVolatilityStructure> optionletVTS3m = getOptionletTS();

        // Block 1: rhoInf=0.3, beta=0.9 — de-correlation case.
        // Shifted 6m vols should not exceed 3m vols by more than 1bp.
        {
            final TwoParameterCorrelation corr = new TwoParameterCorrelation(
                    t -> 0.3, t -> 0.9);
            final OptionletVolatilityStructure optionletVTS6m =
                    new TenorOptionletVTS(optionletVTS3m, euribor3m, euribor6m, corr);
            for (Period capletTerm : CAPLET_TERMS) {
                for (double strike : CAPLET_STRIKES) {
                    final double vol3m = optionletVTS3m.currentLink()
                            .volatility(capletTerm, strike, true);
                    final double vol6mShifted = optionletVTS6m
                            .volatility(capletTerm, strike + spread, true);
                    assertTrue("Shifted 6m vol significantly larger than 3m vol at term="
                                    + capletTerm + ", strike=" + strike
                                    + ": vol3m=" + vol3m + ", vol6mShifted=" + vol6mShifted,
                            vol6mShifted - vol3m <= 0.0001);
                }
            }
        }

        // Block 2: rhoInf=0, beta=0 → rho(t1,t2) = 1 (perfect correlation).
        // Shifted 6m vols should coincide with 3m vols (10bp tol smaller tenors,
        // 1bp tol larger tenors per C++).
        {
            final TwoParameterCorrelation corr = new TwoParameterCorrelation(
                    t -> 0.0, t -> 0.0);
            final OptionletVolatilityStructure optionletVTS6m =
                    new TenorOptionletVTS(optionletVTS3m, euribor3m, euribor6m, corr);
            for (int i = 0; i < CAPLET_TERMS.length; i++) {
                final double tol = (i < 3) ? 0.001 : 0.0001;
                for (double strike : CAPLET_STRIKES) {
                    final double vol3m = optionletVTS3m.currentLink()
                            .volatility(CAPLET_TERMS[i], strike, true);
                    final double vol6mShifted = optionletVTS6m
                            .volatility(CAPLET_TERMS[i], strike + spread, true);
                    assertTrue("Shifted 6m vol does not match 3m vol (perfect corr) at term="
                                    + CAPLET_TERMS[i] + ", strike=" + strike
                                    + ": vol3m=" + vol3m + ", vol6mShifted=" + vol6mShifted
                                    + " (tol=" + tol + ")",
                            Math.abs(vol6mShifted - vol3m) <= tol);
                }
            }
        }
    }

    @Test
    public void testTenorswaptionvts() {
        QL.info("::::: BasisModelsTest::testTenorswaptionvts :::::");

        final double spread = 0.01;
        final Handle<YieldTermStructure> discYTS = getYTS(TERMS, DISC_RATES);
        final Handle<YieldTermStructure> proj3mYTS = getYTS(TERMS, PROJ_3M_RATES);
        final Handle<YieldTermStructure> proj6mYTS = getYTS(TERMS, PROJ_3M_RATES, spread);
        final IborIndex euribor3m = new Euribor6M(proj3mYTS); // C++ typo preserved
        final IborIndex euribor6m = new Euribor6M(proj6mYTS);

        final Handle<SwaptionVolatilityStructure> euribor6mSwVTS = getSwaptionVTS();
        final DayCounter th30 = new Thirty360(Thirty360.Convention.BondBasis);
        final Period oneYr = new Period(1, TimeUnit.Years);

        // Block 1: 6m vol should be >= 3m vol due to basis.
        {
            final TenorSwaptionVTS euribor3mSwVTS = new TenorSwaptionVTS(
                    euribor6mSwVTS, discYTS, euribor6m, euribor3m,
                    oneYr, oneYr, th30, th30);
            for (int i = 0; i < SWAPTION_VTS_TERMS.length; i++) {
                for (int j = 0; j < SWAPTION_VTS_TERMS.length; j++) {
                    final double vol6m = euribor6mSwVTS.currentLink().volatility(
                            SWAPTION_VTS_TERMS[i], SWAPTION_VTS_TERMS[j], 0.01, true);
                    final double vol3m = euribor3mSwVTS.volatility(
                            SWAPTION_VTS_TERMS[i], SWAPTION_VTS_TERMS[j], 0.01, true);
                    assertTrue("Euribor 6m must be >= 3m vol at expiry="
                                    + SWAPTION_VTS_TERMS[i] + ", swap="
                                    + SWAPTION_VTS_TERMS[j]
                                    + ": vol3m=" + vol3m + ", vol6m=" + vol6m,
                            vol3m <= vol6m);
                }
            }
        }

        // Block 2: 6m → 6m must reproduce input vols (tol 1e-8).
        {
            final TenorSwaptionVTS euribor6mSwVTS2 = new TenorSwaptionVTS(
                    euribor6mSwVTS, discYTS, euribor6m, euribor6m,
                    oneYr, oneYr, th30, th30);
            for (int i = 0; i < SWAPTION_VTS_TERMS.length; i++) {
                for (int j = 0; j < SWAPTION_VTS_TERMS.length; j++) {
                    final double vol6m = euribor6mSwVTS.currentLink().volatility(
                            SWAPTION_VTS_TERMS[i], SWAPTION_VTS_TERMS[j], 0.01, true);
                    final double vol6m2 = euribor6mSwVTS2.volatility(
                            SWAPTION_VTS_TERMS[i], SWAPTION_VTS_TERMS[j], 0.01, true);
                    assertEquals("Euribor 6m to 6m vols must reproduce input at expiry="
                                    + SWAPTION_VTS_TERMS[i] + ", swap=" + SWAPTION_VTS_TERMS[j],
                            vol6m, vol6m2, 1.0e-8);
                }
            }
        }

        // Block 3: 6m → 3m → 6m must reproduce input vols (tol 1e-8).
        {
            final TenorSwaptionVTS euribor3mSwVTS = new TenorSwaptionVTS(
                    euribor6mSwVTS, discYTS, euribor6m, euribor3m,
                    oneYr, oneYr, th30, th30);
            final TenorSwaptionVTS euribor6mSwVTS2 = new TenorSwaptionVTS(
                    new Handle<SwaptionVolatilityStructure>(euribor3mSwVTS),
                    discYTS, euribor3m, euribor6m,
                    oneYr, oneYr, th30, th30);
            for (int i = 0; i < SWAPTION_VTS_TERMS.length; i++) {
                for (int j = 0; j < SWAPTION_VTS_TERMS.length; j++) {
                    final double vol6m = euribor6mSwVTS.currentLink().volatility(
                            SWAPTION_VTS_TERMS[i], SWAPTION_VTS_TERMS[j], 0.01, true);
                    final double vol6m2 = euribor6mSwVTS2.volatility(
                            SWAPTION_VTS_TERMS[i], SWAPTION_VTS_TERMS[j], 0.01, true);
                    assertEquals("Euribor 6m to 3m to 6m vols must reproduce input at expiry="
                                    + SWAPTION_VTS_TERMS[i] + ", swap=" + SWAPTION_VTS_TERMS[j],
                            vol6m, vol6m2, 1.0e-8);
                }
            }
        }
    }
}
