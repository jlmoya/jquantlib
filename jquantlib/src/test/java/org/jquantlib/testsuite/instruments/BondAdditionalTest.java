/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.cashflow.CashFlows;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.daycounters.Thirty360;
import org.jquantlib.instruments.bonds.FixedRateBond;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.bond.BondFunctions;
import org.jquantlib.pricingengines.bond.DiscountingBondEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.InterestRate;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.testsuite.util.Utilities;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.calendars.NullCalendar;
import org.jquantlib.time.calendars.UnitedStates;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5d additional skeleton port of {@code test-suite/bonds.cpp} v1.42.1
 * (1,896 LOC, 18 cases) — gap-fill for cases not in {@link BondTest}.
 *
 * <p>As of Phase 5e.5b-CFC-d-50, the following are body-filled against
 * C++ v1.42.1 reference values via {@code BondFunctions}:
 * <ul>
 *   <li>{@code testFixedBondWithGivenDates} — verifies that
 *       {@link FixedRateBond} built on a regular {@link Schedule} and on
 *       its date-vector clone produces identical clean prices for plain,
 *       multi-coupon, and stub-date variants;
 *   <li>{@code testBasisPointValue} — DV01 + YV01 from
 *       {@link BondFunctions#basisPointValue} /
 *       {@link BondFunctions#yieldValueBasisPoint};
 *   <li>{@code testFixedRateBondWithArbitrarySchedule} (Phase 5e.5b-CFC-d-5);
 *   <li>{@code testThirty360BondWithSettlementOn31st} (Phase 5e.5b-CFC-d-12).
 * </ul>
 *
 * <p>The remaining cases stay deferred — each requires Java production
 * code that is not yet ported (see per-test {@code @Ignore} reason).
 *
 * <p>Source: {@code test-suite/bonds.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class BondAdditionalTest {

    @Ignore("Phase 5d.5 — Java CashFlows.atmRate(leg, handle, settle, "
          + "npvDate, exDiv, npv) returns basisPoint_*npv/bps without the "
          + "non-sens NPV (redemption) split C++ CashFlows::atmRate v1.42.1 "
          + "performs (cashflows.cpp:509-551). Body-fill blocked on "
          + "CashFlows.java rewrite (owned by another in-flight agent).")
    @Test public void testAtmRate() { fail("not implemented"); }

    @Ignore("Phase 5d.5 — requires CashFlows.zSpread + BondFunctions.zSpread "
          + "(root-finder over dirty-price-from-z-spread; not yet ported)")
    @Test public void testZspread() { fail("not implemented"); }

    @Ignore("Phase 5d.5 — requires FixedRateCoupon.exCouponDate field "
          + "(FixedRateLeg.withExCouponPeriod currently records but discards "
          + "the date; accrued amount cannot go negative on ex-coupon trades)")
    @Test public void testExCouponGilt() { fail("not implemented"); }

    @Ignore("Phase 5d.5 — requires FixedRateCoupon.exCouponDate field "
          + "(same as testExCouponGilt; AUD ex-coupon period = 7 days)")
    @Test public void testExCouponAustralianBond() { fail("not implemented"); }

    @Ignore("Phase 5d.5 — needs Schedule(dates,..,tenor,rule,eom,isRegular) "
          + "ctor + FixedRateCoupon.exCouponDate (ex-coupon period = 10 days)")
    @Test public void testBondFromScheduleWithDateVector() { fail("not implemented"); }

    /**
     * Faithful Java port of {@code testFixedBondWithGivenDates} from
     * {@code test-suite/bonds.cpp:1493-1611} (v1.42.1). Verifies that two
     * {@link FixedRateBond}s built from the same coupon dates but via
     * different {@link Schedule} ctors (rule-based vs. date-vector) produce
     * identical clean prices for: plain semiannual, varying coupons, and
     * stub-date trailing schedule. Tolerance {@code 1e-6} matches C++.
     *
     * <p>The Java date-vector clone uses the existing simple {@link Schedule}
     * ctor (with {@code hasTenor()==false}). The {@link FixedRateBond}
     * arbitrary-schedule branch (Phase 5e.5b-CFC-d-5) handles this case by
     * falling back to {@code (start,end)} as the reference period, which for
     * {@link Actual360} and {@link ActualActual.Convention#ISMA} on regular
     * schedules yields the same year-fractions as the rule-based variant.
     *
     * <p>Phase 5e.5b-CFC-d-50.
     */
    @Test
    public void testFixedBondWithGivenDates() {
        final Date today = new Date(22, Month.November, 2004);
        new Settings().setEvaluationDate(today);

        final int settlementDays = 1;
        final double faceAmount = 1000000.0;
        final double tolerance = 1.0e-6;

        final Handle<YieldTermStructure> discountCurve =
                new Handle<YieldTermStructure>(Utilities.flatRate(today, 0.03, new Actual360()));
        final PricingEngine bondEngine = new DiscountingBondEngine(discountCurve);

        // ---------------- plain semiannual ----------------
        final Schedule sch1 = new Schedule(new Date(30, Month.November, 2004),
                new Date(30, Month.November, 2008),
                new Period(Frequency.Semiannual),
                new UnitedStates(UnitedStates.Market.GOVERNMENTBOND),
                BusinessDayConvention.Unadjusted, BusinessDayConvention.Unadjusted,
                DateGeneration.Rule.Backward, false);
        final FixedRateBond bond1 = new FixedRateBond(settlementDays, faceAmount, sch1,
                new double[] { 0.02875 },
                new ActualActual(ActualActual.Convention.ISMA),
                BusinessDayConvention.ModifiedFollowing,
                100.0, new Date(30, Month.November, 2004));
        bond1.setPricingEngine(bondEngine);
        final Schedule sch1Copy = new Schedule(sch1.dates(),
                new UnitedStates(UnitedStates.Market.GOVERNMENTBOND),
                BusinessDayConvention.Unadjusted);
        final FixedRateBond bond1Copy = new FixedRateBond(settlementDays, faceAmount, sch1Copy,
                new double[] { 0.02875 },
                new ActualActual(ActualActual.Convention.ISMA),
                BusinessDayConvention.ModifiedFollowing,
                100.0, new Date(30, Month.November, 2004));
        bond1Copy.setPricingEngine(bondEngine);
        double expected = bond1.cleanPrice();
        double calculated = bond1Copy.cleanPrice();
        assertEquals("plain: failed to reproduce cached price",
                expected, calculated, tolerance);

        // ---------------- varying coupons ----------------
        final double[] couponRates = { 0.02875, 0.03, 0.03125, 0.0325 };
        final FixedRateBond bond2 = new FixedRateBond(settlementDays, faceAmount, sch1,
                couponRates,
                new ActualActual(ActualActual.Convention.ISMA),
                BusinessDayConvention.ModifiedFollowing,
                100.0, new Date(30, Month.November, 2004));
        bond2.setPricingEngine(bondEngine);
        final FixedRateBond bond2Copy = new FixedRateBond(settlementDays, faceAmount, sch1Copy,
                couponRates,
                new ActualActual(ActualActual.Convention.ISMA),
                BusinessDayConvention.ModifiedFollowing,
                100.0, new Date(30, Month.November, 2004));
        bond2Copy.setPricingEngine(bondEngine);
        expected = bond2.cleanPrice();
        calculated = bond2Copy.cleanPrice();
        assertEquals("varying coupons: failed to reproduce cached price",
                expected, calculated, tolerance);

        // ---------------- stub date ----------------
        final Schedule sch3 = new Schedule(new Date(30, Month.November, 2004),
                new Date(30, Month.March, 2009),
                new Period(Frequency.Semiannual),
                new UnitedStates(UnitedStates.Market.GOVERNMENTBOND),
                BusinessDayConvention.Unadjusted, BusinessDayConvention.Unadjusted,
                DateGeneration.Rule.Backward, false,
                new Date(), new Date(30, Month.November, 2008));
        final FixedRateBond bond3 = new FixedRateBond(settlementDays, faceAmount, sch3,
                couponRates,
                new Actual360(),
                BusinessDayConvention.ModifiedFollowing,
                100.0, new Date(30, Month.November, 2004));
        bond3.setPricingEngine(bondEngine);
        final Schedule sch3Copy = new Schedule(sch3.dates(),
                new UnitedStates(UnitedStates.Market.GOVERNMENTBOND),
                BusinessDayConvention.Unadjusted);
        final FixedRateBond bond3Copy = new FixedRateBond(settlementDays, faceAmount, sch3Copy,
                couponRates,
                new Actual360(),
                BusinessDayConvention.ModifiedFollowing,
                100.0, new Date(30, Month.November, 2004));
        bond3Copy.setPricingEngine(bondEngine);
        expected = bond3.cleanPrice();
        calculated = bond3Copy.cleanPrice();
        assertEquals("stub date: failed to reproduce cached price",
                expected, calculated, tolerance);
    }

    @Ignore("Phase 5d.5 — requires RiskyBondEngine + FlatHazardRate "
          + "(credit infrastructure not yet ported)")
    @Test public void testRiskyBondWithGivenDates() { fail("not implemented"); }

    /**
     * Faithful Java port of {@code testFixedRateBondWithArbitrarySchedule}
     * from {@code test-suite/bonds.cpp:1677-1713} (v1.42.1). Verifies that
     * {@link FixedRateBond} accepts a {@link Schedule} built from an
     * arbitrary date vector (no tenor / EOM / regularity meta-info), reports
     * {@link Frequency#NoFrequency}, and prices without throwing under a
     * {@link DiscountingBondEngine}. Phase 5e.5b-CFC-d-5.
     */
    @Test
    public void testFixedRateBondWithArbitrarySchedule() {
        final Calendar calendar = new NullCalendar();

        final int settlementDays = 3;

        final Date today = new Date(1, Month.January, 2019);
        new Settings().setEvaluationDate(today);

        // Mirrors C++ bonds.cpp:1687-1691 — irregular date vector.
        final List<Date> dates = new ArrayList<Date>(4);
        dates.add(new Date(1, Month.February, 2019));
        dates.add(new Date(7, Month.February, 2019));
        dates.add(new Date(1, Month.April, 2019));
        dates.add(new Date(27, Month.May, 2019));

        final Schedule schedule = new Schedule(dates, calendar,
                BusinessDayConvention.Unadjusted);

        final double coupon = 0.01;
        final DayCounter dc = new Actual365Fixed();

        final FixedRateBond bond = new FixedRateBond(
                settlementDays,
                100.0,
                schedule,
                new double[] {coupon},
                dc,
                BusinessDayConvention.Following,
                100.0);

        // C++: BOOST_ERROR if bond.frequency() != NoFrequency.
        assertEquals("unexpected frequency",
                Frequency.NoFrequency, bond.frequency());

        final Handle<YieldTermStructure> discountCurve =
                new Handle<YieldTermStructure>(
                        Utilities.flatRate(today, 0.03, new Actual360()));
        final PricingEngine engine = new DiscountingBondEngine(discountCurve);
        bond.setPricingEngine(engine);

        // C++: BOOST_CHECK_NO_THROW(bond.cleanPrice());
        try {
            bond.cleanPrice();
        } catch (final RuntimeException e) {
            fail("cleanPrice() threw: " + e.getMessage());
        }
    }

    /**
     * Faithful Java port of {@code testThirty360BondWithSettlementOn31st}
     * from {@code test-suite/bonds.cpp:1715-1757} (v1.42.1). Verifies the
     * Bloomberg-cusip-3130A0X70 USD government bond reconciles yield,
     * Macaulay duration, convexity and accrued amount under a
     * {@link Thirty360}{@code (USA)} day count when settled on the 31st of
     * the month (a Thirty/360 edge case where the day argument collapses
     * to 30). Exercises {@link BondFunctions#yield},
     * {@link BondFunctions#duration}, {@link BondFunctions#convexity} and
     * {@link BondFunctions#accruedAmount}. Phase 5e.5b-CFC-d-12.
     */
    @Test
    public void testThirty360BondWithSettlementOn31st() {
        // cusip 3130A0X70, data is from Bloomberg (mirrors C++ comment).
        new Settings().setEvaluationDate(new Date(28, Month.July, 2017));

        final Date datedDate = new Date(13, Month.February, 2014);
        final Date settlement = new Date(31, Month.July, 2017);
        final Date maturity = new Date(13, Month.August, 2018);

        final DayCounter dayCounter = new Thirty360(Thirty360.Convention.USA);
        final Compounding compounding = Compounding.Compounded;

        final Schedule fixedBondSchedule = new Schedule(datedDate,
                maturity,
                new Period(Frequency.Semiannual),
                new UnitedStates(UnitedStates.Market.GOVERNMENTBOND),
                BusinessDayConvention.Unadjusted,
                BusinessDayConvention.Unadjusted,
                DateGeneration.Rule.Forward,
                false);

        final FixedRateBond fixedRateBond = new FixedRateBond(
                1,
                100.0,
                fixedBondSchedule,
                new double[] {0.015},
                dayCounter,
                BusinessDayConvention.Unadjusted,
                100.0);

        final BondFunctions.Price cleanPrice =
                new BondFunctions.Price(100.0, BondFunctions.Price.Type.Clean);

        final double yield = BondFunctions.yield(fixedRateBond, cleanPrice,
                dayCounter, compounding, Frequency.Semiannual, settlement);
        assertEquals("yield", 0.015, yield, 1e-4);

        final InterestRate ir = new InterestRate(yield, dayCounter, compounding,
                Frequency.Semiannual);

        final double duration = BondFunctions.duration(fixedRateBond, ir,
                CashFlows.Duration.Macaulay, settlement);
        assertEquals("duration", 1.022, duration, 1e-3);

        final double convexity = BondFunctions.convexity(fixedRateBond, ir, settlement)
                / 100.0;
        assertEquals("convexity", 0.015, convexity, 1e-3);

        final double accrued = BondFunctions.accruedAmount(fixedRateBond, settlement);
        assertEquals("accrued", 0.7, accrued, 1e-6);
    }

    /**
     * Faithful Java port of {@code testBasisPointValue} from
     * {@code test-suite/bonds.cpp:1759-1825} (v1.42.1). For a 10y 4.5%
     * semiannual UST priced at 102.890625 (clean), verifies that the
     * yield recovers {@code 4.1301%}, {@link BondFunctions#basisPointValue}
     * returns the cached DV01 ({@code -795.459834} on default settlement /
     * {@code -793.149033} on the deferred {@code 12-Feb-2024} settlement),
     * and {@link BondFunctions#yieldValueBasisPoint} (scaled by face) the
     * cached YV01. Both {@code (yield, dc, comp, freq, settlement)} and
     * {@code (InterestRate, settlement)} overloads are exercised.
     * Tolerance {@code 1e-6} matches C++. Phase 5e.5b-CFC-d-50.
     */
    @Test
    public void testBasisPointValue() {
        final double faceAmount = 1000000.0;
        final Date today = new Date(29, Month.January, 2024);
        new Settings().setEvaluationDate(today);

        final Date datedDate = new Date(15, Month.November, 2023);
        final Date maturity = new Date(15, Month.August, 2033);

        final DayCounter dayCounter = new Thirty360(Thirty360.Convention.USA);
        final Compounding compounding = Compounding.Compounded;
        final Frequency frequency = Frequency.Semiannual;
        final Period period = new Period(frequency);

        final Schedule fixedBondSchedule = new Schedule(datedDate, maturity, period,
                new UnitedStates(UnitedStates.Market.GOVERNMENTBOND),
                BusinessDayConvention.Unadjusted, BusinessDayConvention.Unadjusted,
                DateGeneration.Rule.Forward, false);

        final FixedRateBond fixedRateBond = new FixedRateBond(
                1, faceAmount, fixedBondSchedule,
                new double[] { 0.045 }, dayCounter,
                BusinessDayConvention.Unadjusted, 100.0);

        final Date defaultSettlement = fixedRateBond.settlementDate();
        final BondFunctions.Price cleanPrice =
                new BondFunctions.Price(102.890625, BondFunctions.Price.Type.Clean);

        final double tolerance = 1.0e-6;

        final double yield = BondFunctions.yield(fixedRateBond, cleanPrice,
                dayCounter, compounding, frequency);
        assertEquals("yield", 0.041301, yield, tolerance);

        // C++ cases array: { settlement, bpv, yvbp }. The first entry uses
        // Date() which the helpers below normalize to defaultSettlement.
        final Date[] settlements = {
                new Date(),
                defaultSettlement,
                new Date(12, Month.February, 2024),
        };
        final double[] expectedBpv = {
                -795.459834, -795.459834, -793.149033,
        };
        final double[] expectedYvbp = {
                -0.0012571287, -0.0012571287, -0.0012607913,
        };

        for (int i = 0; i < settlements.length; ++i) {
            final Date s = settlements[i];
            final double bvp1 = BondFunctions.basisPointValue(fixedRateBond, yield,
                    dayCounter, compounding, frequency, s);
            assertEquals("basisPointValue from yield @" + s,
                    expectedBpv[i], bvp1, tolerance);
            final double bvp2 = BondFunctions.basisPointValue(fixedRateBond,
                    new InterestRate(yield, dayCounter, compounding, frequency), s);
            assertEquals("basisPointValue from InterestRate @" + s,
                    expectedBpv[i], bvp2, tolerance);
            final double yvbp1 = BondFunctions.yieldValueBasisPoint(fixedRateBond, yield,
                    dayCounter, compounding, frequency, s) * faceAmount;
            assertEquals("yieldValueBasisPoint from yield @" + s,
                    expectedYvbp[i], yvbp1, tolerance);
            final double yvbp2 = BondFunctions.yieldValueBasisPoint(fixedRateBond,
                    new InterestRate(yield, dayCounter, compounding, frequency), s) * faceAmount;
            assertEquals("yieldValueBasisPoint from InterestRate @" + s,
                    expectedYvbp[i], yvbp2, tolerance);
        }
    }

    @Ignore("Phase 5d.5 — requires FloatingRateBond ctor with fixingConvention "
          + "parameter and IborLeg.withFixingConvention support")
    @Test public void testFixingConvention() { fail("not implemented"); }
}
