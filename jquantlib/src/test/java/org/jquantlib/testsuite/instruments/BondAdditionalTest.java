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
 * (1,896 LOC, 18 cases) — gap-fill for cases not in
 * {@link BondTest}.
 *
 * <p>{@link BondTest} already covers 7 of the 18 cases:
 * {@code testYield}, {@code testTheoretical}, {@code testCached},
 * {@code testCachedZero}, {@code testCachedFixed}, {@code testCachedFloating},
 * {@code testBrazilianCached}.
 *
 * <p>This companion file adds the 11 missing cases:
 * <ul>
 *   <li>{@code testAtmRate} — ATM par-yield consistency;
 *   <li>{@code testZspread} — Z-spread / OAS computation;
 *   <li>{@code testExCouponGilt} / {@code testExCouponAustralianBond} —
 *       UK gilt and Australian-style ex-coupon date conventions;
 *   <li>{@code testBondFromScheduleWithDateVector} —
 *       constructor-from-date-vector schedule path;
 *   <li>{@code testFixedBondWithGivenDates} /
 *       {@code testRiskyBondWithGivenDates} —
 *       date-vector-given variants of {@code FixedRateBond};
 *   <li>{@code testFixedRateBondWithArbitrarySchedule} —
 *       arbitrary (non-regular) schedule support;
 *   <li>{@code testThirty360BondWithSettlementOn31st} —
 *       Thirty-360 day-count regression for settlement on the 31st;
 *   <li>{@code testBasisPointValue} — DV01 / BPV;
 *   <li>{@code testFixingConvention} — fixing-day convention path.
 * </ul>
 *
 * <p><strong>10 cases still deferred to Phase 5d.5</strong> — they
 * exercise existing Java {@code FixedRateBond} / {@code FloatingRateBond}
 * machinery, but require:
 * <ul>
 *   <li>Cross-validated probe reference values (most assertions are
 *       numeric NPV / yield / spread / DV01);
 *   <li>{@code testExCoupon*} — the {@code BondHelper} +
 *       {@code DiscountingBondEngine} ex-coupon date branch must be
 *       audited against C++ for the gilt / Australian conventions;
 *   <li>{@code testZspread} — needs the
 *       {@code BondFunctions::zSpread} static helper exposed in Java.
 * </ul>
 *
 * <p>{@code testFixedRateBondWithArbitrarySchedule} was bodied in
 * Phase 5e.5b-CFC-d-5 alongside the
 * {@code FixedRateBond}/{@code FixedRateLeg} arbitrary-schedule
 * alignment and the new {@code Schedule.hasTenor()} /
 * {@code hasIsRegular()} / {@code fullInterface()} accessors.
 *
 * <p>Phase 5d.5 carry-forward: body the remaining 10 cases against
 * {@code migration-harness/cpp/probes/instruments/bonds/} probes once
 * authored.
 *
 * <p>Source: {@code test-suite/bonds.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class BondAdditionalTest {

    private static final String REASON_NUMERIC =
            "Phase 5d.5 — requires C++ probe reference values for ATM "
          + "rate / Z-spread / DV01 / theoretical-yield assertions";

    private static final String REASON_EX_COUPON =
            "Phase 5d.5 — requires audit of ex-coupon date branch in "
          + "DiscountingBondEngine against gilt / Australian conventions";

    private static final String REASON_GIVEN_DATES =
            "Phase 5d.5 — requires FixedRateBond date-vector constructor "
          + "branch + reference-value cross-validation";

    private static final String REASON_FIXING =
            "Phase 5d.5 — requires audit of FloatingRateBond fixing-day "
          + "convention path + reference values";

    @Ignore(REASON_NUMERIC) @Test public void testAtmRate() { fail("not implemented"); }
    @Ignore(REASON_NUMERIC) @Test public void testZspread() { fail("not implemented"); }
    @Ignore(REASON_EX_COUPON) @Test public void testExCouponGilt() { fail("not implemented"); }
    @Ignore(REASON_EX_COUPON) @Test public void testExCouponAustralianBond() { fail("not implemented"); }
    @Ignore(REASON_GIVEN_DATES) @Test public void testBondFromScheduleWithDateVector() { fail("not implemented"); }
    @Ignore(REASON_GIVEN_DATES) @Test public void testFixedBondWithGivenDates() { fail("not implemented"); }
    @Ignore(REASON_GIVEN_DATES) @Test public void testRiskyBondWithGivenDates() { fail("not implemented"); }
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
    @Ignore(REASON_NUMERIC) @Test public void testBasisPointValue() { fail("not implemented"); }
    @Ignore(REASON_FIXING) @Test public void testFixingConvention() { fail("not implemented"); }
}
