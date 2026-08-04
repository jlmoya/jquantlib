/*
 Copyright (C) 2026 Jose Moya

 This source code is release under the BSD License.

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

package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.Coupon;
import org.jquantlib.cashflow.FixedRateLeg;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.cashflow.RateAveraging;
import org.jquantlib.cashflow.SimpleCashFlow;
import org.jquantlib.currencies.America.USDCurrency;
import org.jquantlib.currencies.Currency;
import org.jquantlib.currencies.Europe.EURCurrency;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.indexes.OvernightIndex;
import org.jquantlib.instruments.ConstNotionalCrossCurrencyBasisSwap;
import org.jquantlib.instruments.ConstNotionalCrossCurrencyFixedVsFloatingSwap;
import org.jquantlib.instruments.ConstNotionalCrossCurrencySwap;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.math.Constants;
import org.jquantlib.pricingengines.swap.DiscountingConstNotionalCrossCurrencySwapEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.math.interpolations.factories.LogLinear;
import org.jquantlib.termstructures.yieldcurves.InterpolatedDiscountCurve;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Cross-validates the constant-notional cross-currency swap family introduced in C++ QuantLib v1.43 —
 * {@link ConstNotionalCrossCurrencySwap}, {@link ConstNotionalCrossCurrencyBasisSwap},
 * {@link ConstNotionalCrossCurrencyFixedVsFloatingSwap} and
 * {@link DiscountingConstNotionalCrossCurrencySwapEngine} — against the {@code instruments/v143_xccy_swaps} probe.
 * <p>
 * Every input is built explicitly from literal discount-factor tables and inline index definitions, matching the probe
 * exactly, so a failure localises to the swap or engine rather than to some index or curve definition shared with
 * other tests.
 * <p>
 * Besides the NPV the test pins each leg's NPV, BPS, in-currency NPV/BPS and the three discount factors, plus the full
 * cashflow listing. The listing is the part that actually catches leg-construction bugs: an NPV alone can match while
 * two errors cancel, and notional-exchange placement is easy to get subtly wrong.
 *
 * @author Jose Moya
 */
public class ConstNotionalCrossCurrencySwapTest {

    /** Relative tolerance. LOOSE tier: these are 5Y swaps on 125M notionals discounted through interpolated curves. */
    private static final double REL_TOL = 1.0e-8;
    /** Absolute tolerance for quantities that legitimately pass through zero. */
    private static final double ABS_TOL = 1.0e-8;

    private static final Date TODAY = new Date(11, Month.September, 2018);
    private static final double USD_NOMINAL = 125_000_000.0;
    private static final double SPOT_FX = 1.22; // USD per EUR

    private Date savedEvaluationDate;

    @Before
    public void setUp() {
        savedEvaluationDate = new Settings().evaluationDate();
        new Settings().setEvaluationDate(TODAY);
    }

    @After
    public void tearDown() {
        new Settings().setEvaluationDate(savedEvaluationDate);
    }

    //
    // reference plumbing
    //

    private static JSONObject expected(final String caseName) {
        return (JSONObject) ReferenceReader.load("instruments/v143_xccy_swaps").getCase(caseName).expectedRaw();
    }

    private static void assertClose(final String what, final double expected, final double actual) {
        final double tol = Math.max(ABS_TOL, REL_TOL * Math.abs(expected));
        assertEquals(what, expected, actual, tol);
    }

    //
    // market data — mirrors the probe's tables exactly
    //

    private static Date[] curveDates() {
        return new Date[] {
                new Date(11, Month.September, 2018), new Date(11, Month.December, 2018),
                new Date(11, Month.March, 2019), new Date(11, Month.September, 2019),
                new Date(11, Month.September, 2020), new Date(13, Month.September, 2021),
                new Date(12, Month.September, 2022), new Date(11, Month.September, 2023),
                new Date(11, Month.September, 2028), };
    }

    private static Handle< YieldTermStructure > discountCurve(final double[] dfs) {
        return new Handle< YieldTermStructure >(
                new InterpolatedDiscountCurve< LogLinear >(LogLinear.class, curveDates(), dfs, new Actual365Fixed()));
    }

    private static Handle< YieldTermStructure > usdDiscount() {
        return discountCurve(new double[] { 1.0, 0.9941, 0.9888, 0.9757, 0.9486, 0.9228, 0.8983, 0.8747, 0.7630 });
    }

    private static Handle< YieldTermStructure > eurDiscount() {
        return discountCurve(new double[] { 1.0, 0.9998, 0.9995, 0.9986, 0.9955, 0.9910, 0.9850, 0.9775, 0.9210 });
    }

    private static Handle< YieldTermStructure > usdProjection() {
        return discountCurve(new double[] { 1.0, 0.9935, 0.9871, 0.9727, 0.9433, 0.9148, 0.8876, 0.8615, 0.7386 });
    }

    private static Handle< YieldTermStructure > eurProjection() {
        return discountCurve(new double[] { 1.0, 0.9996, 0.9991, 0.9978, 0.9938, 0.9881, 0.9808, 0.9720, 0.9040 });
    }

    private static IborIndex usdIbor3M() {
        return new IborIndex("USD-XCCY-3M", new Period(3, TimeUnit.Months), 2, new USDCurrency(), new Target(),
                BusinessDayConvention.ModifiedFollowing, false, new Actual360(), usdProjection());
    }

    private static IborIndex eurIbor3M() {
        return new IborIndex("EUR-XCCY-3M", new Period(3, TimeUnit.Months), 2, new EURCurrency(), new Target(),
                BusinessDayConvention.ModifiedFollowing, false, new Actual360(), eurProjection());
    }

    private static OvernightIndex usdOn() {
        return new OvernightIndex("USD-XCCY-ON", 0, new USDCurrency(), new Target(), new Actual360(), usdProjection());
    }

    private static OvernightIndex eurOn() {
        return new OvernightIndex("EUR-XCCY-ON", 0, new EURCurrency(), new Target(), new Actual360(), eurProjection());
    }

    private static Schedule quarterly(final Date start, final Date end) {
        return new Schedule(start, end, new Period(3, TimeUnit.Months), new Target(),
                BusinessDayConvention.ModifiedFollowing, BusinessDayConvention.ModifiedFollowing,
                DateGeneration.Rule.Forward, false, new Date(), new Date());
    }

    private static Schedule annual(final Date start, final Date end) {
        return new Schedule(start, end, new Period(1, TimeUnit.Years), new Target(),
                BusinessDayConvention.ModifiedFollowing, BusinessDayConvention.ModifiedFollowing,
                DateGeneration.Rule.Forward, false, new Date(), new Date());
    }

    private static Date startDate() {
        return new Target().advance(TODAY, new Period(2, TimeUnit.Days));
    }

    private static Date endDate() {
        return new Target().advance(TODAY, new Period(5, TimeUnit.Years));
    }

    private static DiscountingConstNotionalCrossCurrencySwapEngine engine(final Date npvDate,
            final Date spotFXSettleDate) {
        final Handle< Quote > fx = new Handle< Quote >(new SimpleQuote(SPOT_FX));
        return new DiscountingConstNotionalCrossCurrencySwapEngine(new USDCurrency(), usdDiscount(),
                new EURCurrency(), eurDiscount(), fx, null, new Date(), npvDate, spotFXSettleDate);
    }

    //
    // instrument builders — mirror the probe's builders exactly
    //

    private static ConstNotionalCrossCurrencySwap makeFixFix() {
        final Calendar cal = new Target();
        final Schedule sched = quarterly(startDate(), endDate());
        final DayCounter dc = new Actual365Fixed();
        final double eurNominal = USD_NOMINAL / SPOT_FX;

        final Leg usdLeg = new FixedRateLeg(sched, dc)
                .withNotionals(USD_NOMINAL)
                .withCouponRates(0.0575)
                .withPaymentAdjustment(BusinessDayConvention.ModifiedFollowing)
                .withPaymentCalendar(cal)
                .Leg();
        final Date first = cal.adjust(sched.date(0), BusinessDayConvention.ModifiedFollowing);
        usdLeg.add(0, new SimpleCashFlow(-USD_NOMINAL, first));
        usdLeg.add(new SimpleCashFlow(USD_NOMINAL, usdLeg.last().date()));

        final Leg eurLeg = new FixedRateLeg(sched, dc)
                .withNotionals(eurNominal)
                .withCouponRates(0.0201)
                .withPaymentAdjustment(BusinessDayConvention.ModifiedFollowing)
                .withPaymentCalendar(cal)
                .Leg();
        eurLeg.add(0, new SimpleCashFlow(-eurNominal, first));
        eurLeg.add(new SimpleCashFlow(eurNominal, eurLeg.last().date()));

        return new ConstNotionalCrossCurrencySwap(usdLeg, new USDCurrency(), eurLeg, new EURCurrency());
    }

    private static ConstNotionalCrossCurrencyBasisSwap makeBasis(final boolean overnight) {
        final Schedule sched = quarterly(startDate(), endDate());
        final double eurNominal = USD_NOMINAL / SPOT_FX;

        if ( overnight ) {
            return new ConstNotionalCrossCurrencyBasisSwap(USD_NOMINAL, new USDCurrency(), sched, usdOn(), 0.0010, 1.0,
                    eurNominal, new EURCurrency(), sched, eurOn(), 0.0025, 1.0, 2, 2, true, Constants.NULL_NATURAL,
                    false, 0, RateAveraging.Type.Compound, false, Constants.NULL_NATURAL, false, 0,
                    RateAveraging.Type.Compound, false);
        }
        return new ConstNotionalCrossCurrencyBasisSwap(USD_NOMINAL, new USDCurrency(), sched, usdIbor3M(), 0.0010, 1.0,
                eurNominal, new EURCurrency(), sched, eurIbor3M(), 0.0025, 1.0);
    }

    private static ConstNotionalCrossCurrencyFixedVsFloatingSwap makeFixFloat(final VanillaSwap.Type type) {
        final Calendar cal = new Target();
        return new ConstNotionalCrossCurrencyFixedVsFloatingSwap(type, USD_NOMINAL, new USDCurrency(),
                annual(startDate(), endDate()), 0.0325, new Actual365Fixed(),
                BusinessDayConvention.ModifiedFollowing, 0, cal, USD_NOMINAL / SPOT_FX, new EURCurrency(),
                quarterly(startDate(), endDate()), eurIbor3M(), 0.0015, BusinessDayConvention.ModifiedFollowing, 0,
                cal);
    }

    //
    // shared assertions
    //

    private static void checkSwap(final String caseName, final ConstNotionalCrossCurrencySwap swap) {
        final JSONObject e = expected(caseName);

        assertClose(caseName + ": NPV", e.getDouble("npv"), swap.NPV());
        assertEquals(caseName + ": valuationDate", e.getLong("valuationDateSerial"),
                swap.valuationDate().serialNumber());
        assertEquals(caseName + ": startDate", e.getLong("startDateSerial"), swap.startDate().serialNumber());
        assertEquals(caseName + ": maturityDate", e.getLong("maturityDateSerial"),
                swap.maturityDate().serialNumber());

        final JSONArray legs = e.getJSONArray("legs");
        for ( int i = 0; i < legs.length(); ++i ) {
            final JSONObject le = legs.getJSONObject(i);
            final String tag = caseName + ": leg " + i;

            assertEquals(tag + " currency", le.getString("currency"), swap.legCurrency(i).code());
            assertClose(tag + " legNPV", le.getDouble("legNPV"), swap.legNPV(i));
            assertClose(tag + " legBPS", le.getDouble("legBPS"), swap.legBPS(i));
            assertClose(tag + " inCcyLegNPV", le.getDouble("inCcyLegNPV"), swap.inCcyLegNPV(i));
            assertClose(tag + " inCcyLegBPS", le.getDouble("inCcyLegBPS"), swap.inCcyLegBPS(i));
            assertClose(tag + " npvDateDiscounts", le.getDouble("npvDateDiscounts"), swap.npvDateDiscounts(i));
            assertClose(tag + " startDiscounts", le.getDouble("startDiscounts"), swap.startDiscounts(i));
            assertClose(tag + " endDiscounts", le.getDouble("endDiscounts"), swap.endDiscounts(i));

            checkCashflows(tag, le.getJSONArray("cashflows"), swap.leg(i));
        }
    }

    private static void checkCashflows(final String tag, final JSONArray expectedFlows, final Leg leg) {
        assertEquals(tag + " cashflow count", expectedFlows.length(), leg.size());
        for ( int k = 0; k < expectedFlows.length(); ++k ) {
            final JSONObject fe = expectedFlows.getJSONObject(k);
            final CashFlow cf = leg.get(k);
            final String ftag = tag + " flow " + k;

            assertEquals(ftag + " date", fe.getLong("dateSerial"), cf.date().serialNumber());
            assertClose(ftag + " amount", fe.getDouble("amount"), cf.amount());

            final boolean isCoupon = fe.getBoolean("isCoupon");
            assertEquals(ftag + " isCoupon", isCoupon, cf instanceof Coupon);
            if ( isCoupon ) {
                final Coupon c = (Coupon) cf;
                assertClose(ftag + " nominal", fe.getDouble("nominal"), c.nominal());
                assertEquals(ftag + " accrualStart", fe.getLong("accrualStartSerial"),
                        c.accrualStartDate().serialNumber());
                assertEquals(ftag + " accrualEnd", fe.getLong("accrualEndSerial"),
                        c.accrualEndDate().serialNumber());
                assertClose(ftag + " accrualPeriod", fe.getDouble("accrualPeriod"), c.accrualPeriod());
                assertClose(ftag + " rate", fe.getDouble("rate"), c.rate());
            }
        }
    }

    //
    // tests
    //

    @Test
    public void testFixedVsFixed() {
        QL.info("Testing fixed/fixed cross-currency swap against C++ v1.43...");
        final ConstNotionalCrossCurrencySwap swap = makeFixFix();
        swap.setPricingEngine(engine(new Date(), new Date()));
        checkSwap("fix_fix", swap);
    }

    @Test
    public void testFixedVsFixedWithForwardFxSettlement() {
        QL.info("Testing the engine's forward-FX adjustment against C++ v1.43...");
        final ConstNotionalCrossCurrencySwap swap = makeFixFix();
        swap.setPricingEngine(engine(new Date(), new Date(11, Month.September, 2019)));
        checkSwap("fix_fix_fwd_fx_settle", swap);
    }

    @Test
    public void testFixedVsFixedWithForwardNpvDate() {
        QL.info("Testing a forward NPV date against C++ v1.43...");
        final ConstNotionalCrossCurrencySwap swap = makeFixFix();
        swap.setPricingEngine(engine(new Date(11, Month.March, 2019), new Date()));
        checkSwap("fix_fix_forward_npv_date", swap);
    }

    @Test
    public void testBasisSwapWithIborLegs() {
        QL.info("Testing the IBOR cross-currency basis swap against C++ v1.43...");
        final ConstNotionalCrossCurrencyBasisSwap swap = makeBasis(false);
        swap.setPricingEngine(engine(new Date(), new Date()));
        checkSwap("basis_ibor", swap);

        final JSONObject e = expected("basis_ibor");
        assertClose("basis_ibor: fairPaySpread", e.getDouble("fairPaySpread"), swap.fairPaySpread());
        assertClose("basis_ibor: fairRecSpread", e.getDouble("fairRecSpread"), swap.fairRecSpread());
    }

    @Test
    public void testBasisSwapWithOvernightLegs() {
        QL.info("Testing the overnight cross-currency basis swap against C++ v1.43...");
        final ConstNotionalCrossCurrencyBasisSwap swap = makeBasis(true);
        swap.setPricingEngine(engine(new Date(), new Date()));
        checkSwap("basis_overnight_compound_spread", swap);

        final JSONObject e = expected("basis_overnight_compound_spread");
        assertClose("basis_on: fairPaySpread", e.getDouble("fairPaySpread"), swap.fairPaySpread());
        assertClose("basis_on: fairRecSpread", e.getDouble("fairRecSpread"), swap.fairRecSpread());
    }

    @Test
    public void testFixedVsFloatingPayer() {
        QL.info("Testing the payer fixed-vs-floating cross-currency swap against C++ v1.43...");
        final ConstNotionalCrossCurrencyFixedVsFloatingSwap swap = makeFixFloat(VanillaSwap.Type.Payer);
        swap.setPricingEngine(engine(new Date(), new Date()));
        checkSwap("fixed_vs_floating_payer", swap);

        final JSONObject e = expected("fixed_vs_floating_payer");
        assertClose("fxf payer: fairRate", e.getDouble("fairRate"), swap.fairRate());
        assertClose("fxf payer: fairSpread", e.getDouble("fairSpread"), swap.fairSpread());
    }

    @Test
    public void testFixedVsFloatingReceiver() {
        QL.info("Testing the receiver fixed-vs-floating cross-currency swap against C++ v1.43...");
        final ConstNotionalCrossCurrencyFixedVsFloatingSwap swap = makeFixFloat(VanillaSwap.Type.Receiver);
        swap.setPricingEngine(engine(new Date(), new Date()));
        checkSwap("fixed_vs_floating_receiver", swap);

        final JSONObject e = expected("fixed_vs_floating_receiver");
        assertClose("fxf receiver: fairRate", e.getDouble("fairRate"), swap.fairRate());
        assertClose("fxf receiver: fairSpread", e.getDouble("fairSpread"), swap.fairSpread());
    }

    /**
     * The multi-leg constructor is not reachable through the probe cases above, which all go through either the
     * two-leg constructor or a derived class. Building the same fixed/fixed swap through it must give an identical
     * price — otherwise the payer-flag or currency wiring differs between the two entry points.
     */
    @Test
    public void testMultiLegConstructorMatchesTwoLegConstructor() {
        QL.info("Testing the multi-leg cross-currency swap constructor against the two-leg one...");
        final ConstNotionalCrossCurrencySwap twoLeg = makeFixFix();
        twoLeg.setPricingEngine(engine(new Date(), new Date()));

        final ConstNotionalCrossCurrencySwap reference = makeFixFix();
        final List< Leg > legs = new ArrayList<>();
        legs.add(reference.leg(0));
        legs.add(reference.leg(1));
        final List< Currency > ccys = new ArrayList<>();
        ccys.add(new USDCurrency());
        ccys.add(new EURCurrency());

        final ConstNotionalCrossCurrencySwap multiLeg = new ConstNotionalCrossCurrencySwap(legs,
                new boolean[] { true, false }, ccys);
        multiLeg.setPricingEngine(engine(new Date(), new Date()));

        assertClose("multi-leg NPV", twoLeg.NPV(), multiLeg.NPV());
        assertClose("multi-leg leg 0 NPV", twoLeg.legNPV(0), multiLeg.legNPV(0));
        assertClose("multi-leg leg 1 NPV", twoLeg.legNPV(1), multiLeg.legNPV(1));
        assertTrue("multi-leg leg 0 currency", multiLeg.legCurrency(0).eq(new USDCurrency()));
        assertTrue("multi-leg leg 1 currency", multiLeg.legCurrency(1).eq(new EURCurrency()));
    }
}
