/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.termstructures;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.Thirty360;
import org.jquantlib.experimental.termstructures.ConstNotionalCrossCurrencyBasisSwapRateHelper;
import org.jquantlib.experimental.termstructures.ConstNotionalCrossCurrencySwapRateHelper;
import org.jquantlib.experimental.termstructures.MtMCrossCurrencyBasisSwapRateHelper;
import org.jquantlib.indexes.Euribor3M;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.indexes.ibor.Eonia;
import org.jquantlib.indexes.ibor.Sofr;
import org.jquantlib.indexes.ibor.USDLibor;
import org.jquantlib.math.interpolations.factories.LogLinear;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.InterestRate;
import org.jquantlib.termstructures.IterativeBootstrap;
import org.jquantlib.termstructures.RateHelper;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.Discount;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.termstructures.yieldcurves.PiecewiseYieldCurve;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Phase 5e.5b-CFC-d-81 / activated Phase 5e.5b-CFC-d-87 port of
 * {@code test-suite/crosscurrencyratehelpers.cpp} v1.42.1 (755 LOC,
 * 16 test cases).
 *
 * <p>Cross-validated against C++ QuantLib v1.42.1
 * via the {@code crosscurrency_probe}.
 */
public class CrossCurrencyRateHelpersTest {

    private static final double BASIS_POINT = 1.0e-4;
    private static final double FX_SPOT = 1.25;
    private static final double TIGHT_TOL = 1.0e-12;
    private static final double LOOSE_TOL = 5.0e-4;

    private Date savedEvalDate;

    @Before
    public void setUp() {
        savedEvalDate = new Settings().evaluationDate();
    }

    @After
    public void tearDown() {
        new Settings().setEvaluationDate(savedEvalDate);
    }

    private static final class XccyTestDatum {
        final int n;
        final TimeUnit units;
        final double basis;

        XccyTestDatum(final int n, final TimeUnit units, final double basis) {
            this.n = n;
            this.units = units;
            this.basis = basis;
        }
    }

    private static final class CommonVars {
        final int curveSettlementDays = 0;
        final int instrumentSettlementDays = 2;
        final BusinessDayConvention businessConvention = BusinessDayConvention.Following;
        final Calendar calendar = new Target();
        final Actual365Fixed dayCount = new Actual365Fixed();
        final boolean endOfMonth = false;

        final Date today;
        final Date instrumentSettlementDt;
        final Date curveSettlementDt;

        final IborIndex baseCcyIdx;
        final IborIndex quoteCcyIdx;
        final IborIndex baseOvernightIndex;
        final IborIndex quoteOvernightIndex;

        final RelinkableHandle<YieldTermStructure> baseCcyIdxHandle =
                new RelinkableHandle<YieldTermStructure>();
        final RelinkableHandle<YieldTermStructure> quoteCcyIdxHandle =
                new RelinkableHandle<YieldTermStructure>();

        final List<XccyTestDatum> basisData;

        CommonVars() {
            today = calendar.adjust(new Date(6, Month.September, 2013));
            new Settings().setEvaluationDate(today);

            instrumentSettlementDt =
                    calendar.advance(today, instrumentSettlementDays, TimeUnit.Days);
            curveSettlementDt =
                    calendar.advance(today, curveSettlementDays, TimeUnit.Days);

            baseCcyIdxHandle.linkTo(new FlatForward(curveSettlementDt, 0.007, dayCount));
            quoteCcyIdxHandle.linkTo(new FlatForward(curveSettlementDt, 0.015, dayCount));

            baseCcyIdx = new Euribor3M(baseCcyIdxHandle);
            quoteCcyIdx = new USDLibor(new Period(3, TimeUnit.Months), quoteCcyIdxHandle);
            baseOvernightIndex = new Eonia(baseCcyIdxHandle);
            quoteOvernightIndex = new Sofr(quoteCcyIdxHandle);

            basisData = new ArrayList<XccyTestDatum>();
            basisData.add(new XccyTestDatum( 1, TimeUnit.Years,  -14.5));
            basisData.add(new XccyTestDatum(18, TimeUnit.Months, -18.5));
            basisData.add(new XccyTestDatum( 2, TimeUnit.Years,  -20.5));
            basisData.add(new XccyTestDatum( 3, TimeUnit.Years,  -23.75));
            basisData.add(new XccyTestDatum( 4, TimeUnit.Years,  -25.5));
            basisData.add(new XccyTestDatum( 5, TimeUnit.Years,  -26.5));
            basisData.add(new XccyTestDatum( 7, TimeUnit.Years,  -26.75));
            basisData.add(new XccyTestDatum(10, TimeUnit.Years,  -26.25));
            basisData.add(new XccyTestDatum(15, TimeUnit.Years,  -24.75));
            basisData.add(new XccyTestDatum(20, TimeUnit.Years,  -23.25));
            basisData.add(new XccyTestDatum(30, TimeUnit.Years,  -20.50));
        }

        RateHelper constantNotionalXccyRateHelper(
                final XccyTestDatum q,
                final Handle<YieldTermStructure> collateralHandle,
                final boolean isFxBaseCurrencyCollateralCurrency,
                final boolean isBasisOnFxBaseCurrencyLeg) {
            final Handle<Quote> qh =
                    new Handle<Quote>(new SimpleQuote(q.basis * BASIS_POINT));
            final Period tenor = new Period(q.n, q.units);
            return new ConstNotionalCrossCurrencyBasisSwapRateHelper(
                    qh, tenor, instrumentSettlementDays, calendar, businessConvention,
                    endOfMonth, baseCcyIdx, quoteCcyIdx, collateralHandle,
                    isFxBaseCurrencyCollateralCurrency, isBasisOnFxBaseCurrencyLeg);
        }

        List<RateHelper> buildConstantNotionalXccyRateHelpers(
                final List<XccyTestDatum> data,
                final Handle<YieldTermStructure> collateralHandle,
                final boolean isFxBaseCurrencyCollateralCurrency,
                final boolean isBasisOnFxBaseCurrencyLeg) {
            final List<RateHelper> out = new ArrayList<RateHelper>(data.size());
            for (final XccyTestDatum d : data) {
                out.add(constantNotionalXccyRateHelper(
                        d, collateralHandle, isFxBaseCurrencyCollateralCurrency,
                        isBasisOnFxBaseCurrencyLeg));
            }
            return out;
        }

        RateHelper resettingXccyRateHelper(
                final XccyTestDatum q,
                final Handle<YieldTermStructure> collateralHandle,
                final boolean isFxBaseCurrencyCollateralCurrency,
                final boolean isBasisOnFxBaseCurrencyLeg,
                final boolean isFxBaseCurrencyLegResettable,
                final Frequency paymentFrequency,
                final int paymentLag,
                final boolean useOvernightIndex) {
            final Handle<Quote> qh =
                    new Handle<Quote>(new SimpleQuote(q.basis * BASIS_POINT));
            final Period tenor = new Period(q.n, q.units);
            final IborIndex baseIndex = useOvernightIndex ? baseOvernightIndex : baseCcyIdx;
            final IborIndex quoteIndex = useOvernightIndex ? quoteOvernightIndex : quoteCcyIdx;
            return new MtMCrossCurrencyBasisSwapRateHelper(
                    qh, tenor, instrumentSettlementDays, calendar, businessConvention,
                    endOfMonth, baseIndex, quoteIndex, collateralHandle,
                    isFxBaseCurrencyCollateralCurrency, isBasisOnFxBaseCurrencyLeg,
                    isFxBaseCurrencyLegResettable, paymentFrequency, paymentLag);
        }

        List<RateHelper> buildResettingXccyRateHelpers(
                final List<XccyTestDatum> data,
                final Handle<YieldTermStructure> collateralHandle,
                final boolean isFxBaseCurrencyCollateralCurrency,
                final boolean isBasisOnFxBaseCurrencyLeg,
                final boolean isFxBaseCurrencyLegResettable,
                final Frequency paymentFrequency,
                final int paymentLag,
                final boolean useOvernightIndex) {
            final List<RateHelper> out = new ArrayList<RateHelper>(data.size());
            for (final XccyTestDatum d : data) {
                out.add(resettingXccyRateHelper(
                        d, collateralHandle, isFxBaseCurrencyCollateralCurrency,
                        isBasisOnFxBaseCurrencyLeg, isFxBaseCurrencyLegResettable,
                        paymentFrequency, paymentLag, useOvernightIndex));
            }
            return out;
        }
    }

    private static double[] zerosConstNotional_collatQuote_basisBase() {
        return new double[] {
                0.0055321624869434085,
                0.00512905061223035,
                0.004926447677144322,
                0.004596476529631574,
                0.004418376552817699,
                0.004316472293678898,
                0.004291081890475354,
                0.004342601979651568,
                0.004497767089780301,
                0.004654232788838423,
                0.004944597716245266
        };
    }

    private static double[] zerosConstNotional_collatBase_basisQuote() {
        return new double[] {
                0.01353509590794998,
                0.013131159637570548,
                0.012928164296551737,
                0.012597023075099129,
                0.012418169910622995,
                0.012315878353772972,
                0.012291719788299403,
                0.01234605311610331,
                0.012508047720112641,
                0.012672988610117756,
                0.012984124121856925
        };
    }

    private static double[] zerosConstNotional_collatBase_basisBase() {
        return new double[] {
                0.016473202260682385,
                0.016884691676573336,
                0.017095219951356593,
                0.01744423931214682,
                0.017641231033752404,
                0.017761505390648347,
                0.017816747720839264,
                0.017804506035844037,
                0.017701878115005117,
                0.017587415145462238,
                0.01735411552440846
        };
    }

    private static double[] zerosConstNotional_collatQuote_basisQuote() {
        return new double[] {
                0.008461219924304285,
                0.008860366439168774,
                0.009059213261790263,
                0.009381409481877908,
                0.009551944690879019,
                0.00964634110405072,
                0.009656974295932149,
                0.009584085440503522,
                0.009395651149932982,
                0.009208812301538341,
                0.0088672018557587
        };
    }

    private static YieldTermStructure buildBootstrap(
            final CommonVars vars,
            final List<RateHelper> instruments) {
        final RateHelper[] arr = instruments.toArray(new RateHelper[0]);
        final PiecewiseYieldCurve<Discount, LogLinear, IterativeBootstrap> curve =
                new PiecewiseYieldCurve<Discount, LogLinear, IterativeBootstrap>(
                        Discount.class, LogLinear.class, IterativeBootstrap.class,
                        vars.curveSettlementDt, arr, vars.dayCount);
        curve.enableExtrapolation();
        return curve;
    }

    private static double zeroAtMaturity(
            final YieldTermStructure curve,
            final RateHelper helper,
            final org.jquantlib.daycounters.DayCounter dc) {
        final Date mat = helper.latestDate();
        final InterestRate ir = curve.zeroRate(mat, dc, Compounding.Continuous);
        return ir.rate();
    }

    private void runConstNotionalCase(
            final boolean isFxBaseCurrencyCollateralCurrency,
            final boolean isBasisOnFxBaseCurrencyLeg,
            final double[] expectedZeros) {
        final CommonVars vars = new CommonVars();
        final Handle<YieldTermStructure> collateralHandle =
                isFxBaseCurrencyCollateralCurrency ? vars.baseCcyIdxHandle : vars.quoteCcyIdxHandle;
        final List<RateHelper> instruments = vars.buildConstantNotionalXccyRateHelpers(
                vars.basisData, collateralHandle,
                isFxBaseCurrencyCollateralCurrency, isBasisOnFxBaseCurrencyLeg);
        final YieldTermStructure curve = buildBootstrap(vars, instruments);

        assertEquals("number of helpers",
                expectedZeros.length, instruments.size());
        for (int i = 0; i < instruments.size(); i++) {
            final double actual = zeroAtMaturity(curve, instruments.get(i), vars.dayCount);
            assertEquals("zero rate at helper #" + i
                            + " (" + vars.basisData.get(i).n + " " + vars.basisData.get(i).units + ")",
                    expectedZeros[i], actual, TIGHT_TOL);
        }
    }

    private void runResettingCase(
            final boolean isFxBaseCurrencyCollateralCurrency,
            final boolean isBasisOnFxBaseCurrencyLeg,
            final boolean isFxBaseCurrencyLegResettable,
            final Frequency paymentFrequency,
            final int paymentLag,
            final boolean useOvernightIndex) {
        final CommonVars vars = new CommonVars();
        final Handle<YieldTermStructure> collateralHandle =
                isFxBaseCurrencyCollateralCurrency ? vars.baseCcyIdxHandle : vars.quoteCcyIdxHandle;

        final List<RateHelper> resettingInstruments = vars.buildResettingXccyRateHelpers(
                vars.basisData, collateralHandle, isFxBaseCurrencyCollateralCurrency,
                isBasisOnFxBaseCurrencyLeg, isFxBaseCurrencyLegResettable,
                paymentFrequency, paymentLag, useOvernightIndex);

        final List<RateHelper> constNotionalInstruments = vars.buildConstantNotionalXccyRateHelpers(
                vars.basisData, collateralHandle, isFxBaseCurrencyCollateralCurrency,
                isBasisOnFxBaseCurrencyLeg);

        final YieldTermStructure resettingCurve = buildBootstrap(vars, resettingInstruments);
        final YieldTermStructure constNotionalCurve = buildBootstrap(vars, constNotionalInstruments);

        for (int i = 0; i < resettingInstruments.size(); i++) {
            final double zR = zeroAtMaturity(resettingCurve, resettingInstruments.get(i), vars.dayCount);
            final double zC = zeroAtMaturity(constNotionalCurve, constNotionalInstruments.get(i), vars.dayCount);
            assertTrue("too large difference between resetting and constant notional curve at i=" + i
                            + ": zR=" + zR + ", zC=" + zC,
                    Math.abs(zR - zC) <= LOOSE_TOL);
        }
    }

    // -------------------------------------------------------------------------
    // Tests (all 16 active in Phase 5e.5b-CFC-d-87 after fixing the production
    // scale bug in CrossCurrencyBasisSwapRateHelperBase.npvbpsConstNotionalLeg).
    // -------------------------------------------------------------------------

    @Test
    public void testConstNotionalBasisSwapsWithCollateralInQuoteAndBasisInBaseCcy() {
        QL.info("::::: CrossCurrencyRateHelpersTest::"
                + "testConstNotionalBasisSwapsWithCollateralInQuoteAndBasisInBaseCcy :::::");
        runConstNotionalCase(false, true, zerosConstNotional_collatQuote_basisBase());
    }

    @Test
    public void testConstNotionalBasisSwapsWithCollateralInBaseAndBasisInQuoteCcy() {
        QL.info("::::: CrossCurrencyRateHelpersTest::"
                + "testConstNotionalBasisSwapsWithCollateralInBaseAndBasisInQuoteCcy :::::");
        runConstNotionalCase(true, false, zerosConstNotional_collatBase_basisQuote());
    }

    @Test
    public void testConstNotionalBasisSwapsWithCollateralAndBasisInBaseCcy() {
        QL.info("::::: CrossCurrencyRateHelpersTest::"
                + "testConstNotionalBasisSwapsWithCollateralAndBasisInBaseCcy :::::");
        runConstNotionalCase(true, true, zerosConstNotional_collatBase_basisBase());
    }

    @Test
    public void testConstNotionalBasisSwapsWithCollateralAndBasisInQuoteCcy() {
        QL.info("::::: CrossCurrencyRateHelpersTest::"
                + "testConstNotionalBasisSwapsWithCollateralAndBasisInQuoteCcy :::::");
        runConstNotionalCase(false, false, zerosConstNotional_collatQuote_basisQuote());
    }

    @Test
    public void testResettingBasisSwapsWithCollateralInQuoteAndBasisInBaseCcy() {
        QL.info("::::: CrossCurrencyRateHelpersTest::"
                + "testResettingBasisSwapsWithCollateralInQuoteAndBasisInBaseCcy :::::");
        runResettingCase(false, true, false, Frequency.NoFrequency, 0, false);
    }

    @Test
    public void testResettingBasisSwapsWithCollateralInBaseAndBasisInQuoteCcy() {
        QL.info("::::: CrossCurrencyRateHelpersTest::"
                + "testResettingBasisSwapsWithCollateralInBaseAndBasisInQuoteCcy :::::");
        runResettingCase(true, false, true, Frequency.NoFrequency, 0, false);
    }

    @Test
    public void testResettingBasisSwapsWithCollateralAndBasisInBaseCcy() {
        QL.info("::::: CrossCurrencyRateHelpersTest::"
                + "testResettingBasisSwapsWithCollateralAndBasisInBaseCcy :::::");
        runResettingCase(true, true, true, Frequency.NoFrequency, 0, false);
    }

    @Test
    public void testResettingBasisSwapsWithCollateralAndBasisInQuoteCcy() {
        QL.info("::::: CrossCurrencyRateHelpersTest::"
                + "testResettingBasisSwapsWithCollateralAndBasisInQuoteCcy :::::");
        runResettingCase(false, false, false, Frequency.NoFrequency, 0, false);
    }

    @Test
    public void testResettingBasisSwapsWithArbitraryFreq() {
        QL.info("::::: CrossCurrencyRateHelpersTest::"
                + "testResettingBasisSwapsWithArbitraryFreq :::::");
        runResettingCase(false, true, false, Frequency.Weekly, 0, false);
    }

    @Test
    public void testResettingBasisSwapsWithPaymentLag() {
        QL.info("::::: CrossCurrencyRateHelpersTest::"
                + "testResettingBasisSwapsWithPaymentLag :::::");
        runResettingCase(false, true, false, Frequency.NoFrequency, 2, false);
    }

    @Test
    public void testResettingBasisSwapsWithOvernightIndex() {
        QL.info("::::: CrossCurrencyRateHelpersTest::"
                + "testResettingBasisSwapsWithOvernightIndex :::::");
        runResettingCase(false, true, false, Frequency.Quarterly, 0, true);
    }

    @Test
    public void testResettingBasisSwapsWithOvernightIndexException() {
        QL.info("::::: CrossCurrencyRateHelpersTest::"
                + "testResettingBasisSwapsWithOvernightIndexException :::::");
        try {
            runResettingCase(false, true, false, Frequency.NoFrequency, 0, true);
            fail("expected an exception (overnight index requires payment frequency)");
        } catch (RuntimeException expected) {
            // ok
        }
    }

    @Test
    public void testExceptionWhenInstrumentTenorShorterThanIndexFrequency() {
        QL.info("::::: CrossCurrencyRateHelpersTest::"
                + "testExceptionWhenInstrumentTenorShorterThanIndexFrequency :::::");
        final CommonVars vars = new CommonVars();
        final List<XccyTestDatum> data = new ArrayList<XccyTestDatum>();
        data.add(new XccyTestDatum(1, TimeUnit.Months, 10.0));
        final Handle<YieldTermStructure> collateralHandle =
                new Handle<YieldTermStructure>();
        try {
            vars.buildConstantNotionalXccyRateHelpers(data, collateralHandle, true, true);
            fail("expected an exception (1M tenor < 3M Euribor frequency)");
        } catch (RuntimeException expected) {
            // ok
        }
    }

    @Test
    public void testConstNotionalCrossCurrencySwapRateHelperRelinking() {
        QL.info("::::: CrossCurrencyRateHelpersTest::"
                + "testConstNotionalCrossCurrencySwapRateHelperRelinking :::::");

        final Date savedToday = new Settings().evaluationDate();
        try {
            final Date today = new Date(15, Month.January, 2026);
            new Settings().setEvaluationDate(today);

            final Actual365Fixed a365f = new Actual365Fixed();
            final Actual360 a360 = new Actual360();

            final RelinkableHandle<YieldTermStructure> usdCollat =
                    new RelinkableHandle<YieldTermStructure>();
            usdCollat.linkTo(new FlatForward(today, 0.02, a365f));

            final Handle<YieldTermStructure> eurFwd =
                    new Handle<YieldTermStructure>(new FlatForward(today, 0.017, a365f));

            final IborIndex euribor3m = new Euribor3M(eurFwd);
            final Handle<Quote> q = new Handle<Quote>(new SimpleQuote(0.018));

            final ConstNotionalCrossCurrencySwapRateHelper h =
                    new ConstNotionalCrossCurrencySwapRateHelper(
                            q, new Period(5, TimeUnit.Years), 2, new Target(),
                            BusinessDayConvention.Following, true,
                            Frequency.Annual,
                            new Thirty360(Thirty360.Convention.BondBasis),
                            euribor3m, usdCollat, true);

            final RelinkableHandle<YieldTermStructure> bootstrapCurve =
                    new RelinkableHandle<YieldTermStructure>();
            bootstrapCurve.linkTo(new FlatForward(today, 0.02, a360));
            h.setTermStructure(bootstrapCurve.currentLink());

            final double oldQuote = h.impliedQuote();

            usdCollat.linkTo(new FlatForward(today, 0.03, a365f));
            final double newQuote = h.impliedQuote();

            assertTrue("oldQuote must be finite", !Double.isNaN(oldQuote) && !Double.isInfinite(oldQuote));
            assertTrue("newQuote must be finite", !Double.isNaN(newQuote) && !Double.isInfinite(newQuote));
            assertNotEquals("implied quote must react to collateral relink",
                    oldQuote, newQuote, 0.0);
        } finally {
            new Settings().setEvaluationDate(savedToday);
        }
    }

    @Test
    public void testConstNotionalHelperCollateralOnFixedLeg() {
        QL.info("::::: CrossCurrencyRateHelpersTest::"
                + "testConstNotionalHelperCollateralOnFixedLeg :::::");

        final Date savedToday = new Settings().evaluationDate();
        try {
            final Date today = new Date(20, Month.March, 2030);
            new Settings().setEvaluationDate(today);

            final Actual365Fixed a365f = new Actual365Fixed();
            final Handle<YieldTermStructure> usdCollat =
                    new Handle<YieldTermStructure>(new FlatForward(today, 0.02, a365f));
            final Handle<YieldTermStructure> eurFwd =
                    new Handle<YieldTermStructure>(new FlatForward(today, 0.017, a365f));

            final IborIndex euribor3m = new Euribor3M(eurFwd);

            final int fixingDays = 5;
            final Calendar cal = new Target();
            final BusinessDayConvention bdc = BusinessDayConvention.Following;
            final boolean endOfMonth = true;
            final Frequency fixedFreq = Frequency.Annual;
            final Thirty360 fixedDC = new Thirty360(Thirty360.Convention.BondBasis);

            final double[][] quotes = {
                    {5,  0.018},
                    {7,  0.019},
                    {10, 0.022},
                    {15, 0.024},
                    {20, 0.028}
            };

            final List<RateHelper> helpers = new ArrayList<RateHelper>(quotes.length);
            for (final double[] q : quotes) {
                final Period tenor = new Period((int) q[0], TimeUnit.Years);
                final Handle<Quote> qh = new Handle<Quote>(new SimpleQuote(q[1]));
                helpers.add(new ConstNotionalCrossCurrencySwapRateHelper(
                        qh, tenor, fixingDays, cal, bdc, endOfMonth,
                        fixedFreq, fixedDC, euribor3m, usdCollat, true));
            }

            Date prev = null;
            for (int i = 0; i < helpers.size(); i++) {
                final Date mat = helpers.get(i).latestDate();
                assertTrue("helper #" + i + " latestDate must be after today",
                        mat.gt(today));
                if (prev != null) {
                    assertTrue("helper #" + i + " latestDate must be after helper #"
                                    + (i - 1) + " (tenors are monotone)",
                            mat.gt(prev));
                }
                prev = mat;
            }
        } finally {
            new Settings().setEvaluationDate(savedToday);
        }
    }

    @Test
    public void testConstNotionalHelperCollateralOnFloatingLeg() {
        QL.info("::::: CrossCurrencyRateHelpersTest::"
                + "testConstNotionalHelperCollateralOnFloatingLeg :::::");

        final Date savedToday = new Settings().evaluationDate();
        try {
            final Date today = new Date(20, Month.March, 2030);
            new Settings().setEvaluationDate(today);

            final Actual365Fixed a365f = new Actual365Fixed();
            final Handle<YieldTermStructure> usdCollat =
                    new Handle<YieldTermStructure>(new FlatForward(today, 0.02, a365f));
            final Handle<YieldTermStructure> eurFwd =
                    new Handle<YieldTermStructure>(new FlatForward(today, 0.017, a365f));

            final IborIndex euribor3m = new Euribor3M(eurFwd);

            final int fixingDays = 5;
            final Calendar cal = new Target();
            final BusinessDayConvention bdc = BusinessDayConvention.Following;
            final boolean endOfMonth = true;
            final Frequency fixedFreq = Frequency.Annual;
            final Thirty360 fixedDC = new Thirty360(Thirty360.Convention.BondBasis);
            final int paymentLag = 5;

            final double[][] quotes = {
                    {5,  0.018},
                    {7,  0.019},
                    {10, 0.022},
                    {15, 0.024},
                    {20, 0.028}
            };

            final List<RateHelper> helpers = new ArrayList<RateHelper>(quotes.length);
            for (final double[] q : quotes) {
                final Period tenor = new Period((int) q[0], TimeUnit.Years);
                final Handle<Quote> qh = new Handle<Quote>(new SimpleQuote(q[1]));
                helpers.add(new ConstNotionalCrossCurrencySwapRateHelper(
                        qh, tenor, fixingDays, cal, bdc, endOfMonth,
                        fixedFreq, fixedDC, euribor3m, usdCollat, false, paymentLag));
            }

            Date prev = null;
            for (int i = 0; i < helpers.size(); i++) {
                final Date mat = helpers.get(i).latestDate();
                assertTrue("helper #" + i + " latestDate must be after today",
                        mat.gt(today));
                if (prev != null) {
                    assertTrue("helper #" + i + " latestDate must be after helper #"
                                    + (i - 1) + " (tenors are monotone)",
                            mat.gt(prev));
                }
                prev = mat;
            }
        } finally {
            new Settings().setEvaluationDate(savedToday);
        }
    }
}
