/*
 Copyright (C) 2026 JQuantLib migration contributors.

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

package org.jquantlib.testsuite.cashflows;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.cashflow.BlackIborCouponPricer;
import org.jquantlib.cashflow.CmsCoupon;
import org.jquantlib.cashflow.DigitalCmsCoupon;
import org.jquantlib.cashflow.DigitalIborCoupon;
import org.jquantlib.cashflow.DigitalReplication;
import org.jquantlib.cashflow.IborCoupon;
import org.jquantlib.cashflow.LinearTsrPricer;
import org.jquantlib.cashflow.Replication;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.Euribor6M;
import org.jquantlib.indexes.EuriborSwapIsdaFixA;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.indexes.SwapIndex;
import org.jquantlib.instruments.Position;
import org.jquantlib.math.Constants;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.termstructures.SwaptionVolatilityStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.optionlet.ConstantOptionletVolatility;
import org.jquantlib.termstructures.volatilities.optionlet.OptionletVolatilityStructure;
import org.jquantlib.termstructures.volatilities.swaption.ConstantSwaptionVolatility;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.junit.Test;

/**
 * Cross-validation tests for {@link DigitalIborCoupon} and {@link DigitalCmsCoupon} against C++ QuantLib v1.42.1.
 *
 * <p>Expected values come from the harness probe
 * {@code migration-harness/cpp/probes/cashflows/digital_coupons_probe.cpp} (reference JSON {@code cashflows/digital_coupons}). The
 * Java setup reproduces the probe's object graph exactly: Euribor6M on a flat 5% curve (Ibor section) and
 * EuriborSwapIsdaFixA(10Y) + LinearTsrPricer over a ConstantSwaptionVolatility (CMS section).
 *
 * <p>Both classes are thin {@link org.jquantlib.cashflow.DigitalCoupon}
 * subclasses; the cross-check targets the {@code rate()}, {@code callOptionRate()}, {@code putOptionRate()} and
 * {@code amount()} outputs that flow through the shared call/put-spread replication.
 *
 * <p>TIGHT tier: abs 1e-14, rel 1e-12.
 */
public class DigitalCmsIborCouponTest {

    private static final double ABS = 1.0e-14;
    private static final double REL = 1.0e-12;

    public DigitalCmsIborCouponTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    /** Assert {@code actual} matches {@code expected} at TIGHT (abs 1e-14 OR rel 1e-12). */
    private static void assertTight(final String what, final double expected, final double actual) {
        final double diff = Math.abs(expected - actual);
        final double tol = Math.max(ABS, REL * Math.abs(expected));
        assertTrue(what + ": expected=" + expected + " actual=" + actual + " diff=" + diff + " tol=" + tol,
                diff <= tol);
    }

    // ==================================================================
    // Section A: DigitalIborCoupon (Euribor6M, flat 5%)
    // ==================================================================

    private static final class IborVars {
        final int fixingDays = 2;
        final double nominal = 1.0e6;
        final Calendar calendar;
        final IborIndex index;
        final RelinkableHandle<YieldTermStructure> termStructure = new RelinkableHandle<YieldTermStructure>();
        final RelinkableHandle<OptionletVolatilityStructure> vol = new RelinkableHandle<OptionletVolatilityStructure>();
        final Date startDate;
        final Date endDate;

        IborVars() {
            this.index = new Euribor6M(termStructure);
            this.calendar = index.fixingCalendar();
            final Date today = calendar.adjust(new Date(13, Month.February, 2026));
            new Settings().setEvaluationDate(today);
            final Date settlement = calendar.advance(today, fixingDays, TimeUnit.Days);
            termStructure.linkTo(new FlatForward(settlement, 0.05, new Actual365Fixed()));
            vol.linkTo(new ConstantOptionletVolatility(today, calendar, BusinessDayConvention.Following, 0.15,
                    new Actual360()));
            this.startDate = calendar.advance(settlement, new Period(10, TimeUnit.Years));
            this.endDate = calendar.advance(settlement, new Period(11, TimeUnit.Years));
        }

        IborCoupon underlying() {
            return new IborCoupon(endDate, nominal, startDate, endDate, fixingDays, index, 1.0, 0.0);
        }
    }

    @Test
    public void testDigitalIborCashCallLong() {
        final ReferenceReader ref = ReferenceReader.load("cashflows/digital_coupons");
        final ReferenceReader.Case c = ref.getCase("ibor_cash_call_long");

        final IborVars v = new IborVars();
        final DigitalReplication replication = new DigitalReplication(Replication.Type.Central, 1.0e-4);
        final IborCoupon underlying = v.underlying();
        final DigitalIborCoupon digital = new DigitalIborCoupon(underlying, 0.05, Position.Long, false, 0.04,
                Constants.NULL_REAL, Position.Long, false, Constants.NULL_REAL, replication, false);
        digital.setPricer(new BlackIborCouponPricer(v.vol));

        final org.json.JSONObject exp = (org.json.JSONObject) c.expectedRaw();
        assertTight("underlyingRate", exp.getDouble("underlyingRate"), underlying.rate());
        assertTight("rate", exp.getDouble("rate"), digital.rate());
        assertTight("callOptionRate", exp.getDouble("callOptionRate"), digital.callOptionRate());
        assertTight("putOptionRate", exp.getDouble("putOptionRate"), digital.putOptionRate());
        assertTight("amount", exp.getDouble("amount"), digital.amount());
        assertTight("accrualPeriod", exp.getDouble("accrualPeriod"), underlying.accrualPeriod());
    }

    @Test
    public void testDigitalIborAssetPutLong() {
        final ReferenceReader ref = ReferenceReader.load("cashflows/digital_coupons");
        final ReferenceReader.Case c = ref.getCase("ibor_asset_put_long");

        final IborVars v = new IborVars();
        final DigitalReplication replication = new DigitalReplication(Replication.Type.Central, 1.0e-4);
        final IborCoupon underlying = v.underlying();
        final DigitalIborCoupon digital = new DigitalIborCoupon(underlying, Constants.NULL_REAL, Position.Long, false,
                Constants.NULL_REAL, 0.06, Position.Long, false, Constants.NULL_REAL, replication, false);
        digital.setPricer(new BlackIborCouponPricer(v.vol));

        final org.json.JSONObject exp = (org.json.JSONObject) c.expectedRaw();
        assertTight("underlyingRate", exp.getDouble("underlyingRate"), underlying.rate());
        assertTight("rate", exp.getDouble("rate"), digital.rate());
        assertTight("callOptionRate", exp.getDouble("callOptionRate"), digital.callOptionRate());
        assertTight("putOptionRate", exp.getDouble("putOptionRate"), digital.putOptionRate());
        assertTight("amount", exp.getDouble("amount"), digital.amount());
    }

    @Test
    public void testDigitalIborCashCollar() {
        final ReferenceReader ref = ReferenceReader.load("cashflows/digital_coupons");
        final ReferenceReader.Case c = ref.getCase("ibor_cash_collar");

        final IborVars v = new IborVars();
        final DigitalReplication replication = new DigitalReplication(Replication.Type.Central, 1.0e-4);
        final IborCoupon underlying = v.underlying();
        // cash call long @0.05 + cash put short @0.03, both cashRate 0.04
        final DigitalIborCoupon digital = new DigitalIborCoupon(underlying, 0.05, Position.Long, false, 0.04, 0.03,
                Position.Short, false, 0.04, replication, false);
        digital.setPricer(new BlackIborCouponPricer(v.vol));

        final org.json.JSONObject exp = (org.json.JSONObject) c.expectedRaw();
        assertTight("underlyingRate", exp.getDouble("underlyingRate"), underlying.rate());
        assertTight("rate", exp.getDouble("rate"), digital.rate());
        assertTight("callOptionRate", exp.getDouble("callOptionRate"), digital.callOptionRate());
        assertTight("putOptionRate", exp.getDouble("putOptionRate"), digital.putOptionRate());
        assertTight("amount", exp.getDouble("amount"), digital.amount());
    }

    // ==================================================================
    // Section B: DigitalCmsCoupon (EuriborSwapIsdaFixA 10Y, flat 2%)
    // ==================================================================

    private static final class CmsVars {
        final Date refDate = new Date(23, Month.February, 2018);
        final Handle<YieldTermStructure> yts2;
        final LinearTsrPricer cmsPricer;
        final SwapIndex cms10y;
        final Date startDate = new Date(23, Month.February, 2028);
        final Date endDate = new Date(23, Month.February, 2029);
        final double nominal = 10000.0;

        CmsVars() {
            new Settings().setEvaluationDate(refDate);
            this.yts2 = new Handle<YieldTermStructure>(new FlatForward(refDate, 0.02, new Actual365Fixed()));
            final Handle<SwaptionVolatilityStructure> swLn = new Handle<SwaptionVolatilityStructure>(
                    new ConstantSwaptionVolatility(refDate, new Target(), BusinessDayConvention.Following,
                            new Handle<Quote>(new SimpleQuote(0.20)), new Actual365Fixed(),
                            VolatilityType.ShiftedLognormal, 0.0));
            final Handle<Quote> reversion = new Handle<Quote>(new SimpleQuote(0.01));
            // C++ probe used the default LinearTsrPricer::Settings() — defaultBounds_=true.
            this.cmsPricer = new LinearTsrPricer(swLn, reversion, yts2, new LinearTsrPricer.Settings(), null);
            this.cms10y = new EuriborSwapIsdaFixA(new Period(10, TimeUnit.Years), yts2);
        }

        CmsCoupon underlying() {
            return new CmsCoupon(endDate, nominal, startDate, endDate, 2, cms10y, 1.0, 0.0, new Date(), new Date(),
                    new Actual360(), false);
        }
    }

    @Test
    public void testDigitalCmsCashCallLong() {
        final ReferenceReader ref = ReferenceReader.load("cashflows/digital_coupons");
        final ReferenceReader.Case c = ref.getCase("cms_cash_call_long");

        final CmsVars v = new CmsVars();
        final DigitalReplication replication = new DigitalReplication(Replication.Type.Central, 1.0e-4);
        final CmsCoupon underlying = v.underlying();
        final DigitalCmsCoupon digital = new DigitalCmsCoupon(underlying, 0.03, Position.Long, false, 0.02,
                Constants.NULL_REAL, Position.Long, false, Constants.NULL_REAL, replication, false);
        underlying.setPricer(v.cmsPricer);
        digital.setPricer(v.cmsPricer);

        final org.json.JSONObject exp = (org.json.JSONObject) c.expectedRaw();
        assertTight("underlyingRate", exp.getDouble("underlyingRate"), underlying.rate());
        assertTight("rate", exp.getDouble("rate"), digital.rate());
        assertTight("callOptionRate", exp.getDouble("callOptionRate"), digital.callOptionRate());
        assertTight("putOptionRate", exp.getDouble("putOptionRate"), digital.putOptionRate());
        assertTight("amount", exp.getDouble("amount"), digital.amount());
        assertTight("accrualPeriod", exp.getDouble("accrualPeriod"), underlying.accrualPeriod());
    }

    @Test
    public void testDigitalCmsAssetPutLong() {
        final ReferenceReader ref = ReferenceReader.load("cashflows/digital_coupons");
        final ReferenceReader.Case c = ref.getCase("cms_asset_put_long");

        final CmsVars v = new CmsVars();
        final DigitalReplication replication = new DigitalReplication(Replication.Type.Central, 1.0e-4);
        final CmsCoupon underlying = v.underlying();
        final DigitalCmsCoupon digital = new DigitalCmsCoupon(underlying, Constants.NULL_REAL, Position.Long, false,
                Constants.NULL_REAL, 0.05, Position.Long, false, Constants.NULL_REAL, replication, false);
        underlying.setPricer(v.cmsPricer);
        digital.setPricer(v.cmsPricer);

        final org.json.JSONObject exp = (org.json.JSONObject) c.expectedRaw();
        assertTight("underlyingRate", exp.getDouble("underlyingRate"), underlying.rate());
        assertTight("rate", exp.getDouble("rate"), digital.rate());
        assertTight("callOptionRate", exp.getDouble("callOptionRate"), digital.callOptionRate());
        assertTight("putOptionRate", exp.getDouble("putOptionRate"), digital.putOptionRate());
        assertTight("amount", exp.getDouble("amount"), digital.amount());
    }

    // ==================================================================
    // Leg builders — structural smoke (DigitalIborLeg / DigitalCmsLeg)
    // ==================================================================

    @Test
    public void testDigitalIborLegBuildsDigitalCoupons() {
        final IborVars v = new IborVars();
        final DayCounter dc = new Actual360();
        final org.jquantlib.time.Schedule schedule = new org.jquantlib.time.Schedule(
                v.calendar.advance(new Settings().evaluationDate(), new Period(2, TimeUnit.Days)),
                v.calendar.advance(new Settings().evaluationDate(), new Period(2, TimeUnit.Years)),
                new Period(6, TimeUnit.Months), v.calendar, BusinessDayConvention.ModifiedFollowing,
                BusinessDayConvention.ModifiedFollowing, org.jquantlib.time.DateGeneration.Rule.Forward, false);

        final org.jquantlib.cashflow.Leg leg = new org.jquantlib.cashflow.DigitalIborLeg(schedule, v.index)
                .withNotionals(v.nominal).withPaymentDayCounter(dc).withFixingDays(v.fixingDays)
                .withCallStrikes(0.05).withLongCallOption(Position.Long).withCallPayoffs(0.04)
                .withReplication(new DigitalReplication(Replication.Type.Central, 1.0e-4)).Leg();

        assertEquals(schedule.size() - 1, leg.size());
        for (int i = 0; i < leg.size(); i++) {
            assertTrue("element " + i + " is a DigitalIborCoupon",
                    leg.get(i) instanceof DigitalIborCoupon);
        }
    }

    @Test
    public void testDigitalIborLegFixedCouponDefaultsRateToOnePerCpp() {
        // gearing == 0 selects the fixed-coupon branch; with empty spreads the C++
        // FloatingDigitalLeg (cashflowvectors.hpp:286) defaults the fixed RATE to 1.0.
        // Locks that parity (the floating branch separately defaults spread to 0.0).
        final IborVars v = new IborVars();
        final DayCounter dc = new Actual360();
        final Date effective = v.calendar.advance(new Settings().evaluationDate(), new Period(2, TimeUnit.Days));
        final Date termination = v.calendar.advance(effective, new Period(6, TimeUnit.Months));
        final org.jquantlib.time.Schedule schedule = new org.jquantlib.time.Schedule(
                effective, termination, new Period(6, TimeUnit.Months), v.calendar,
                BusinessDayConvention.ModifiedFollowing, BusinessDayConvention.ModifiedFollowing,
                org.jquantlib.time.DateGeneration.Rule.Forward, false);

        final org.jquantlib.cashflow.Leg leg = new org.jquantlib.cashflow.DigitalIborLeg(schedule, v.index)
                .withNotionals(v.nominal).withPaymentDayCounter(dc).withFixingDays(v.fixingDays)
                .withGearings(0.0)
                .withReplication(new DigitalReplication(Replication.Type.Central, 1.0e-4)).Leg();

        assertEquals(1, leg.size());
        assertTrue("gearing==0 -> FixedRateCoupon", leg.get(0) instanceof org.jquantlib.cashflow.FixedRateCoupon);
        assertEquals("C++-faithful default fixed rate (cashflowvectors.hpp:286)", 1.0,
                ((org.jquantlib.cashflow.FixedRateCoupon) leg.get(0)).rate(), 0.0);
    }

    @Test
    public void testDigitalCmsLegBuildsDigitalCoupons() {
        final CmsVars v = new CmsVars();
        final DayCounter dc = new Actual360();
        final org.jquantlib.time.Schedule schedule = new org.jquantlib.time.Schedule(
                new Date(23, Month.February, 2028), new Date(23, Month.February, 2030),
                new Period(1, TimeUnit.Years), new Target(), BusinessDayConvention.ModifiedFollowing,
                BusinessDayConvention.ModifiedFollowing, org.jquantlib.time.DateGeneration.Rule.Forward, false);

        final org.jquantlib.cashflow.Leg leg = new org.jquantlib.cashflow.DigitalCmsLeg(schedule, v.cms10y)
                .withNotionals(v.nominal).withPaymentDayCounter(dc).withFixingDays(2)
                .withCallStrikes(0.03).withLongCallOption(Position.Long).withCallPayoffs(0.02)
                .withReplication(new DigitalReplication(Replication.Type.Central, 1.0e-4)).Leg();

        assertEquals(schedule.size() - 1, leg.size());
        for (int i = 0; i < leg.size(); i++) {
            assertTrue("element " + i + " is a DigitalCmsCoupon",
                    leg.get(i) instanceof DigitalCmsCoupon);
        }
    }
}
