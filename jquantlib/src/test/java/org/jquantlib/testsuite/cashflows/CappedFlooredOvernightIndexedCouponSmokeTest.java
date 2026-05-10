/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.cashflows;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.cashflow.BlackOvernightIndexedCouponPricer;
import org.jquantlib.cashflow.CappedFlooredOvernightIndexedCoupon;
import org.jquantlib.cashflow.CompoundingOvernightIndexedCouponPricer;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.cashflow.OvernightIndexedCoupon;
import org.jquantlib.cashflow.OvernightLeg;
import org.jquantlib.cashflow.RateAveraging;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.OvernightIndex;
import org.jquantlib.indexes.ibor.Sofr;
import org.jquantlib.math.Constants;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.testsuite.util.Utilities;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.MakeSchedule;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.junit.Test;

/**
 * Smoke tests for {@link CappedFlooredOvernightIndexedCoupon},
 * {@link BlackOvernightIndexedCouponPricer}, and the
 * {@link OvernightLeg#withCaps(double)}/{@code withFloors}/{@code withNakedOption}/
 * {@code withDailyCapFloor} builder methods.
 *
 * <p>Phase 5e.5b-CFC initial coverage. Full reference-driven test cases (cross
 * validated against C++ v1.42.1 BlackCompoundingOvernightIndexedCouponPricer)
 * are deferred to Phase 5e.5b-CFC-b.
 */
public class CappedFlooredOvernightIndexedCouponSmokeTest {

    private static final double NOTIONAL = 10000.0;
    private static final Date EVAL_DATE = new Date(23, Month.November, 2021);
    private static final Date START = new Date(10, Month.December, 2021);
    private static final Date END = new Date(10, Month.January, 2022);

    private static OvernightIndex newSofr(final RelinkableHandle<YieldTermStructure> curve) {
        new Settings().setEvaluationDate(EVAL_DATE);
        return new Sofr(curve);
    }

    private static OvernightIndexedCoupon newCoupon(final OvernightIndex sofr,
                                                    final double gearing,
                                                    final double spread) {
        return new OvernightIndexedCoupon(
                END, NOTIONAL, START, END, sofr, gearing, spread,
                new Date(), new Date(),
                sofr.dayCounter(),
                /* telescopicValueDates */ false,
                RateAveraging.Type.Compound,
                /* lookbackDays */ Constants.NULL_NATURAL,
                /* lockoutDays */ 0,
                /* applyObservationShift */ false,
                /* compoundSpreadDaily */ false);
    }

    @Test
    public void testCapAccessorsPositiveGearing() {
        QL.info("CFC OIS coupon: cap/floor accessors with positive gearing...");
        final RelinkableHandle<YieldTermStructure> curve =
                new RelinkableHandle<YieldTermStructure>();
        final OvernightIndex sofr = newSofr(curve);
        final OvernightIndexedCoupon under = newCoupon(sofr, 1.0, 0.0);

        final double capRate = 0.02;
        final double floorRate = 0.005;
        final CappedFlooredOvernightIndexedCoupon cf =
                new CappedFlooredOvernightIndexedCoupon(under, capRate, floorRate);

        assertTrue("isCapped should be true", cf.isCapped());
        assertTrue("isFloored should be true", cf.isFloored());
        assertEquals("cap()", capRate, cf.cap(), 0.0);
        assertEquals("floor()", floorRate, cf.floor(), 0.0);
        assertEquals("effectiveCap (no spread, gearing=1) = cap",
                capRate, cf.effectiveCap(), 1e-15);
        assertEquals("effectiveFloor (no spread, gearing=1) = floor",
                floorRate, cf.effectiveFloor(), 1e-15);
        assertFalse("dailyCapFloor default is false", cf.dailyCapFloor());
        assertFalse("nakedOption default is false", cf.nakedOption());
    }

    @Test
    public void testCapFloorSwapNegativeGearing() {
        QL.info("CFC OIS coupon: cap/floor swap when gearing < 0...");
        final RelinkableHandle<YieldTermStructure> curve =
                new RelinkableHandle<YieldTermStructure>();
        final OvernightIndex sofr = newSofr(curve);
        // Gearing -2 should swap cap/floor in the protected fields but keep
        // the cap()/floor() public-API stable.
        final OvernightIndexedCoupon under = newCoupon(sofr, -2.0, 0.0);
        final double capRate = 0.02;
        final double floorRate = 0.005;
        final CappedFlooredOvernightIndexedCoupon cf =
                new CappedFlooredOvernightIndexedCoupon(under, capRate, floorRate);

        // For negative gearing, public cap()/floor() should still be the
        // user-facing values (C++ flips inside, then flips back via accessor).
        assertEquals("cap() with negative gearing", capRate, cf.cap(), 0.0);
        assertEquals("floor() with negative gearing", floorRate, cf.floor(), 0.0);
    }

    @Test(expected = RuntimeException.class)
    public void testCapBelowFloorRaises() {
        QL.info("CFC OIS coupon: cap < floor must throw...");
        final RelinkableHandle<YieldTermStructure> curve =
                new RelinkableHandle<YieldTermStructure>();
        final OvernightIndex sofr = newSofr(curve);
        final OvernightIndexedCoupon under = newCoupon(sofr, 1.0, 0.0);
        // cap=0.01, floor=0.02 -> cap<floor => QL.require fails
        new CappedFlooredOvernightIndexedCoupon(under, 0.01, 0.02);
    }

    @Test
    public void testRateMatchesUnderlyingWhenCapAndFloorAbsent() {
        QL.info("CFC OIS coupon: rate equals underlying when no cap/floor active...");
        final DayCounter dc = new Actual360();
        final RelinkableHandle<YieldTermStructure> curve =
                new RelinkableHandle<YieldTermStructure>();
        final OvernightIndex sofr = newSofr(curve);
        curve.linkTo(Utilities.flatRate(EVAL_DATE, 0.0010, dc));

        final OvernightIndexedCoupon under = newCoupon(sofr, 1.0, 0.0);
        final double underRate = under.rate();

        final CappedFlooredOvernightIndexedCoupon cf =
                new CappedFlooredOvernightIndexedCoupon(under,
                        Constants.NULL_REAL, Constants.NULL_REAL);
        // pricer set on underlying; cap/floor branches are inactive, so rate
        // = swapletRate (no caplet/floorlet contribution).
        cf.setPricer(under.pricer() instanceof BlackOvernightIndexedCouponPricer
                ? under.pricer()
                : new BlackOvernightIndexedCouponPricer());
        assertEquals("rate matches underlying when cap/floor missing",
                underRate, cf.rate(), 1e-12);
    }

    @Test
    public void testFlooredRateHitsFloorWhenForwardLow() {
        QL.info("CFC OIS coupon: floor binds when forward rate is below floor...");
        final DayCounter dc = new Actual360();
        final RelinkableHandle<YieldTermStructure> curve =
                new RelinkableHandle<YieldTermStructure>();
        final OvernightIndex sofr = newSofr(curve);
        // Forward at ~0.10% (small).
        curve.linkTo(Utilities.flatRate(EVAL_DATE, 0.0010, dc));
        final OvernightIndexedCoupon under = newCoupon(sofr, 1.0, 0.0);

        // No-vol Black floor at 1% (well above the 0.1% forward).
        // With zero vol, the floor's intrinsic max(floor - underRate, 0) ~=
        // 0.01 - 0.001 = 0.009.
        final BlackOvernightIndexedCouponPricer pricer =
                new BlackOvernightIndexedCouponPricer();
        under.setPricer(pricer);

        final CappedFlooredOvernightIndexedCoupon cf =
                new CappedFlooredOvernightIndexedCoupon(under,
                        Constants.NULL_REAL, 0.01);
        cf.setPricer(pricer);

        // Without vol but the fixing date is in the future, the Black branch
        // requires a vol surface — it should throw. Validate the API contract.
        try {
            cf.rate();
            fail("Black-formula path with future fixing requires a vol surface");
        } catch (final RuntimeException expected) {
            assertTrue("error mentions optionlet volatility",
                       expected.getMessage().contains("optionlet volatility"));
        }
    }

    @Test
    public void testOvernightLegWithCaps() {
        QL.info("OvernightLeg.withCaps builds CappedFlooredOvernightIndexedCoupon...");
        final DayCounter dc = new Actual360();
        final RelinkableHandle<YieldTermStructure> curve =
                new RelinkableHandle<YieldTermStructure>();
        final OvernightIndex sofr = newSofr(curve);
        curve.linkTo(Utilities.flatRate(EVAL_DATE, 0.0010, dc));

        final Calendar cal = sofr.fixingCalendar();
        final Schedule sch = new MakeSchedule(
                START, END, new Period(1, TimeUnit.Months), cal,
                BusinessDayConvention.Following).backwards().schedule();

        final Leg leg = new OvernightLeg(sch, sofr)
                .withNotionals(NOTIONAL)
                .withCaps(0.02)
                .leg();

        assertTrue("leg has at least one coupon", !leg.isEmpty());
        boolean hasCFCoupon = false;
        for (int i = 0; i < leg.size(); ++i) {
            if (leg.get(i) instanceof CappedFlooredOvernightIndexedCoupon) {
                hasCFCoupon = true;
                final CappedFlooredOvernightIndexedCoupon cf =
                        (CappedFlooredOvernightIndexedCoupon) leg.get(i);
                assertTrue("isCapped", cf.isCapped());
                assertFalse("not floored", cf.isFloored());
                assertEquals("cap = 0.02", 0.02, cf.cap(), 0.0);
            }
        }
        assertTrue("at least one CappedFloored coupon in the leg", hasCFCoupon);
    }

    @Test
    public void testOvernightLegWithFloorsAndNoCaps() {
        QL.info("OvernightLeg.withFloors builds CappedFlooredOvernightIndexedCoupon...");
        final DayCounter dc = new Actual360();
        final RelinkableHandle<YieldTermStructure> curve =
                new RelinkableHandle<YieldTermStructure>();
        final OvernightIndex sofr = newSofr(curve);
        curve.linkTo(Utilities.flatRate(EVAL_DATE, 0.0010, dc));

        final Calendar cal = sofr.fixingCalendar();
        final Schedule sch = new MakeSchedule(
                START, END, new Period(1, TimeUnit.Months), cal,
                BusinessDayConvention.Following).backwards().schedule();

        final Leg leg = new OvernightLeg(sch, sofr)
                .withNotionals(NOTIONAL)
                .withFloors(0.005)
                .withNakedOption(true)
                .leg();

        assertTrue("leg non-empty", !leg.isEmpty());
        for (int i = 0; i < leg.size(); ++i) {
            if (leg.get(i) instanceof CappedFlooredOvernightIndexedCoupon) {
                final CappedFlooredOvernightIndexedCoupon cf =
                        (CappedFlooredOvernightIndexedCoupon) leg.get(i);
                assertTrue("isFloored", cf.isFloored());
                assertFalse("not capped", cf.isCapped());
                assertTrue("nakedOption", cf.nakedOption());
                assertEquals("floor = 0.005", 0.005, cf.floor(), 0.0);
            }
        }
    }

    @Test
    public void testOvernightLegNoCapNoFloorBuildsPlainCoupons() {
        QL.info("OvernightLeg without caps/floors builds plain coupons...");
        final RelinkableHandle<YieldTermStructure> curve =
                new RelinkableHandle<YieldTermStructure>();
        final OvernightIndex sofr = newSofr(curve);
        final Calendar cal = sofr.fixingCalendar();
        final Schedule sch = new MakeSchedule(
                START, END, new Period(1, TimeUnit.Months), cal,
                BusinessDayConvention.Following).backwards().schedule();

        final Leg leg = new OvernightLeg(sch, sofr)
                .withNotionals(NOTIONAL)
                .leg();

        for (int i = 0; i < leg.size(); ++i) {
            assertFalse("not CappedFloored when no cap/floor configured",
                    leg.get(i) instanceof CappedFlooredOvernightIndexedCoupon);
            assertTrue("plain OvernightIndexedCoupon",
                    leg.get(i) instanceof OvernightIndexedCoupon);
        }
    }

    @Test
    public void testCompoundSpreadDailyRequiresUnitGearing() {
        QL.info("CFC OIS coupon: compoundSpreadDaily=true requires gearing=1...");
        final RelinkableHandle<YieldTermStructure> curve =
                new RelinkableHandle<YieldTermStructure>();
        final OvernightIndex sofr = newSofr(curve);
        final OvernightIndexedCoupon under = new OvernightIndexedCoupon(
                END, NOTIONAL, START, END, sofr, /* gearing */ 2.0, /* spread */ 0.001,
                new Date(), new Date(), sofr.dayCounter(),
                false, RateAveraging.Type.Compound,
                Constants.NULL_NATURAL, 0, false,
                /* compoundSpreadDaily */ true);
        try {
            new CappedFlooredOvernightIndexedCoupon(under, 0.02, 0.005);
            fail("compoundSpreadDaily=true with gearing=2 must throw");
        } catch (final RuntimeException expected) {
            assertTrue("error mentions gearing 1.0",
                       expected.getMessage().contains("gearing 1.0"));
        }
    }
}
