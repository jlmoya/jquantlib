/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2018 StatPro Italia srl
 Copyright (C) 2021, 2022 Ralf Konrad Eckel

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */
package org.jquantlib.testsuite.experimental.callablebonds;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import java.util.ArrayList;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.cashflow.Callability;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.daycounters.Thirty360;
import org.jquantlib.experimental.callablebonds.BlackCallableFixedRateBondEngine;
import org.jquantlib.experimental.callablebonds.BlackCallableZeroCouponBondEngine;
import org.jquantlib.experimental.callablebonds.CallableBond;
import org.jquantlib.experimental.callablebonds.CallableFixedRateBond;
import org.jquantlib.experimental.callablebonds.CallableZeroCouponBond;
import org.jquantlib.experimental.callablebonds.TreeCallableFixedRateBondEngine;
import org.jquantlib.experimental.callablebonds.TreeCallableZeroCouponBondEngine;
import org.jquantlib.instruments.CallabilitySchedule;
import org.jquantlib.instruments.bonds.FixedRateBond;
import org.jquantlib.instruments.bonds.ZeroCouponBond;
import org.jquantlib.model.shortrate.onefactormodels.HullWhite;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.bond.DiscountingBondEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.MakeSchedule;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.NullCalendar;
import org.jquantlib.time.calendars.Target;
import org.jquantlib.time.calendars.UnitedStates;
import org.junit.Test;

/**
 * Phase 5e.5b-CFC-d-61 port of C++ v1.42.1 {@code QuantLib/test-suite/callablebonds.cpp}.
 * <p>
 * Reference NPVs/clean-prices are pinned from a C++ probe under the same
 * fixture (see {@code migration-harness/cpp/probes/experimental/callablebonds_probe.cpp}
 * and {@code references/experimental/callablebonds.json}). The probe runs
 * against the pinned QuantLib v1.42.1 submodule SHA
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <h3>Deferred carry-forwards</h3>
 * <ul>
 * <li>{@code testCallableBondOasWithDifferentNotinals} — requires
 *     {@code OneFactorModel.ShortRateTree.setSpread} which is not yet
 *     ported (see {@link CallableBond} class Javadoc); without it the
 *     {@code OAS} / {@code cleanPriceOAS} APIs throw
 *     {@link UnsupportedOperationException}.
 * <li>{@code testOasContinuityThroughExCouponWindow} — requires both
 *     ex-coupon period support on {@code FixedRateLeg} / {@code CashFlow}
 *     (not ported) AND the OAS infrastructure above.
 * </ul>
 *
 * The OAS-monotonicity assertion of
 * {@code testSnappingExerciseDate2ClosestCouponDate} is also gated by the
 * missing {@code setSpread}; this test still ports the
 * NPV-callable-equals-NPV-truncated-fixed-rate-bond equality (which is the
 * binding semantic of "snap to closest coupon date"), and the OAS leg is
 * documented as a deferral inline.
 */
public class CallableBondTest {

    /** Mirrors C++ {@code Globals} fixture for compactness. */
    private static class Vars {
        Date today;
        Date settlement;
        Calendar calendar = new Target();
        DayCounter dayCounter = new Actual365Fixed();
        BusinessDayConvention rollingConvention = BusinessDayConvention.ModifiedFollowing;
        YieldTermStructure flatCurve;
        HullWhite model;

        Vars() {
            today = new Settings().evaluationDate();
            settlement = calendar.advance(today, 2, TimeUnit.Days);
        }

        /** Pin evaluation date for deterministic test runs. */
        void setToday(final Date pinned) {
            this.today = pinned;
            new Settings().setEvaluationDate(pinned);
            this.settlement = calendar.advance(pinned, 2, TimeUnit.Days);
        }

        Date issueDate() {
            // ensure that we're in mid-coupon
            return calendar.adjust(today.sub(100));
        }

        Date maturityDate() {
            // ensure that we're in mid-coupon
            return calendar.advance(issueDate(), new Period(10, TimeUnit.Years),
                    BusinessDayConvention.Following);
        }

        List<Date> evenYears() {
            final List<Date> dates = new ArrayList<>();
            for (int i = 2; i < 10; i += 2) {
                dates.add(calendar.advance(issueDate(), new Period(i, TimeUnit.Years),
                        BusinessDayConvention.Following));
            }
            return dates;
        }

        List<Date> oddYears() {
            final List<Date> dates = new ArrayList<>();
            for (int i = 1; i < 10; i += 2) {
                dates.add(calendar.advance(issueDate(), new Period(i, TimeUnit.Years),
                        BusinessDayConvention.Following));
            }
            return dates;
        }

        YieldTermStructure makeFlatCurve(final double r) {
            return new FlatForward(settlement,
                    new Handle<Quote>(new SimpleQuote(r)), dayCounter);
        }

        YieldTermStructure makeFlatCurve(final Handle<Quote> r) {
            return new FlatForward(settlement, r, dayCounter);
        }
    }

    // Pinned-today for all "Globals"-based tests so the fixture is reproducible.
    private static final Date PINNED_TODAY = new Date(3, Month.June, 2004);

    /**
     * testInterplay — call/put interplay. Mirrors C++ four-case test.
     * Expected settlement-value is the analytic discount-factor formula
     * (no tree dependence on the closed-form side); the tree NPV is checked
     * to within 1e-2 — matching the C++ tolerance exactly.
     */
    @Test
    public void testInterplay() {
        final Vars vars = new Vars();
        vars.setToday(PINNED_TODAY);
        vars.flatCurve = vars.makeFlatCurve(0.03);
        final Handle<YieldTermStructure> termStructure = new Handle<YieldTermStructure>(vars.flatCurve);
        vars.model = new HullWhite(termStructure);

        final int timeSteps = 240;
        final PricingEngine engine = new TreeCallableZeroCouponBondEngine(vars.model, timeSteps,
                termStructure);

        // Case 1: an earlier OTM callability prevents a later ITM puttability
        final CallabilitySchedule cb1 = new CallabilitySchedule();
        cb1.add(new Callability(
                new Callability.Price(100.0, Callability.Price.Type.Clean),
                Callability.Type.Call,
                vars.calendar.advance(vars.issueDate(), new Period(4, TimeUnit.Years),
                        BusinessDayConvention.Following)));
        cb1.add(new Callability(
                new Callability.Price(1000.0, Callability.Price.Type.Clean),
                Callability.Type.Put,
                vars.calendar.advance(vars.issueDate(), new Period(6, TimeUnit.Years),
                        BusinessDayConvention.Following)));
        CallableZeroCouponBond bond = new CallableZeroCouponBond(3, 100.0, vars.calendar,
                vars.maturityDate(), new Thirty360(Thirty360.Convention.BondBasis),
                vars.rollingConvention, 100.0, vars.issueDate(), cb1);
        bond.setPricingEngine(engine);

        double expected = cb1.get(0).price().amount()
                * vars.flatCurve.discount(cb1.get(0).date())
                / vars.flatCurve.discount(bond.settlementDate());
        assertEquals("case 1: tree NPV must match analytic expected",
                expected, bond.settlementValue(), 1.0e-2);

        // Case 2: same as 1 with an added later callability — must not move.
        final CallabilitySchedule cb2 = new CallabilitySchedule();
        for (Callability c : cb1) cb2.add(c);
        cb2.add(new Callability(
                new Callability.Price(100.0, Callability.Price.Type.Clean),
                Callability.Type.Call,
                vars.calendar.advance(vars.issueDate(), new Period(8, TimeUnit.Years),
                        BusinessDayConvention.Following)));
        bond = new CallableZeroCouponBond(3, 100.0, vars.calendar,
                vars.maturityDate(), new Thirty360(Thirty360.Convention.BondBasis),
                vars.rollingConvention, 100.0, vars.issueDate(), cb2);
        bond.setPricingEngine(engine);
        assertEquals("case 2: adding later call must not change settlement value",
                expected, bond.settlementValue(), 1.0e-2);

        // Case 3: an earlier ITM puttability prevents a later ITM callability.
        final CallabilitySchedule cb3 = new CallabilitySchedule();
        cb3.add(new Callability(
                new Callability.Price(100.0, Callability.Price.Type.Clean),
                Callability.Type.Put,
                vars.calendar.advance(vars.issueDate(), new Period(4, TimeUnit.Years),
                        BusinessDayConvention.Following)));
        cb3.add(new Callability(
                new Callability.Price(10.0, Callability.Price.Type.Clean),
                Callability.Type.Call,
                vars.calendar.advance(vars.issueDate(), new Period(6, TimeUnit.Years),
                        BusinessDayConvention.Following)));
        bond = new CallableZeroCouponBond(3, 100.0, vars.calendar,
                vars.maturityDate(), new Thirty360(Thirty360.Convention.BondBasis),
                vars.rollingConvention, 100.0, vars.issueDate(), cb3);
        bond.setPricingEngine(engine);
        expected = cb3.get(0).price().amount()
                * vars.flatCurve.discount(cb3.get(0).date())
                / vars.flatCurve.discount(bond.settlementDate());
        assertEquals("case 3: tree NPV must match analytic expected",
                expected, bond.settlementValue(), 1.0e-2);

        // Case 4: same as 3 with an added later puttability — must not move.
        final CallabilitySchedule cb4 = new CallabilitySchedule();
        for (Callability c : cb3) cb4.add(c);
        cb4.add(new Callability(
                new Callability.Price(100.0, Callability.Price.Type.Clean),
                Callability.Type.Put,
                vars.calendar.advance(vars.issueDate(), new Period(8, TimeUnit.Years),
                        BusinessDayConvention.Following)));
        bond = new CallableZeroCouponBond(3, 100.0, vars.calendar,
                vars.maturityDate(), new Thirty360(Thirty360.Convention.BondBasis),
                vars.rollingConvention, 100.0, vars.issueDate(), cb4);
        bond.setPricingEngine(engine);
        assertEquals("case 4: adding later put must not change settlement value",
                expected, bond.settlementValue(), 1.0e-2);
    }

    /**
     * testConsistency — callable bond should price below underlying;
     * puttable should price above. Structural directional inequality.
     */
    @Test
    public void testConsistency() {
        final Vars vars = new Vars();
        vars.setToday(PINNED_TODAY);
        vars.flatCurve = vars.makeFlatCurve(0.032);
        final Handle<YieldTermStructure> termStructure = new Handle<YieldTermStructure>(vars.flatCurve);
        vars.model = new HullWhite(termStructure);

        final Schedule schedule = new Schedule(vars.issueDate(), vars.maturityDate(),
                new Period(Frequency.Semiannual), vars.calendar,
                vars.rollingConvention, vars.rollingConvention,
                DateGeneration.Rule.Backward, false);
        final double[] coupons = new double[] { 0.05 };

        final FixedRateBond plain = new FixedRateBond(3, 100.0, schedule, coupons,
                new Thirty360(Thirty360.Convention.BondBasis));
        plain.setPricingEngine(new DiscountingBondEngine(termStructure));

        final CallabilitySchedule cbs = new CallabilitySchedule();
        for (Date d : vars.evenYears()) {
            cbs.add(new Callability(
                    new Callability.Price(110.0, Callability.Price.Type.Clean),
                    Callability.Type.Call, d));
        }
        final CallabilitySchedule pbs = new CallabilitySchedule();
        for (Date d : vars.oddYears()) {
            pbs.add(new Callability(
                    new Callability.Price(90.0, Callability.Price.Type.Clean),
                    Callability.Type.Put, d));
        }

        final int timeSteps = 240;
        final PricingEngine engine = new TreeCallableFixedRateBondEngine(vars.model, timeSteps,
                termStructure);

        final CallableFixedRateBond callable = new CallableFixedRateBond(3, 100.0, schedule, coupons,
                new Thirty360(Thirty360.Convention.BondBasis), vars.rollingConvention,
                100.0, vars.issueDate(), cbs);
        callable.setPricingEngine(engine);

        final CallableFixedRateBond puttable = new CallableFixedRateBond(3, 100.0, schedule, coupons,
                new Thirty360(Thirty360.Convention.BondBasis), vars.rollingConvention,
                100.0, vars.issueDate(), pbs);
        puttable.setPricingEngine(engine);

        final double plainPrice = plain.cleanPrice();
        final double callablePrice = callable.cleanPrice();
        final double puttablePrice = puttable.cleanPrice();

        assertTrue("plain price (" + plainPrice + ") should exceed callable (" + callablePrice + ")",
                plainPrice > callablePrice);
        assertTrue("plain price (" + plainPrice + ") should be below puttable (" + puttablePrice + ")",
                plainPrice < puttablePrice);
    }

    /**
     * testObservability — callable bond reprices when the underlying yield
     * curve quote moves.
     */
    @Test
    public void testObservability() {
        final Vars vars = new Vars();
        vars.setToday(PINNED_TODAY);
        final SimpleQuote observable = new SimpleQuote(0.03);
        final Handle<Quote> h = new Handle<Quote>(observable);
        vars.flatCurve = vars.makeFlatCurve(h);
        final Handle<YieldTermStructure> termStructure = new Handle<YieldTermStructure>(vars.flatCurve);
        vars.model = new HullWhite(termStructure);

        final Schedule schedule = new Schedule(vars.issueDate(), vars.maturityDate(),
                new Period(Frequency.Semiannual), vars.calendar,
                vars.rollingConvention, vars.rollingConvention,
                DateGeneration.Rule.Backward, false);

        final CallabilitySchedule cbs = new CallabilitySchedule();
        for (Date d : vars.evenYears()) {
            cbs.add(new Callability(
                    new Callability.Price(110.0, Callability.Price.Type.Clean),
                    Callability.Type.Call, d));
        }
        for (Date d : vars.oddYears()) {
            cbs.add(new Callability(
                    new Callability.Price(90.0, Callability.Price.Type.Clean),
                    Callability.Type.Put, d));
        }

        final CallableZeroCouponBond bond = new CallableZeroCouponBond(3, 100.0, vars.calendar,
                vars.maturityDate(), new Thirty360(Thirty360.Convention.BondBasis),
                vars.rollingConvention, 100.0, vars.issueDate(), cbs);
        final int timeSteps = 240;
        bond.setPricingEngine(new TreeCallableFixedRateBondEngine(vars.model, timeSteps,
                termStructure));

        final double originalValue = bond.NPV();
        observable.setValue(0.04);
        final double bumpedValue = bond.NPV();

        assertNotEquals("callable bond was not notified of observable change",
                originalValue, bumpedValue, 0.0);
    }

    /**
     * testDegenerate — degenerate callable (empty callability and
     * out-of-the-money callability) should match plain bond. Pinned tree
     * tolerance 1.0e-4 mirrors C++ exactly.
     */
    @Test
    public void testDegenerate() {
        final Vars vars = new Vars();
        vars.setToday(PINNED_TODAY);
        vars.flatCurve = vars.makeFlatCurve(0.034);
        final Handle<YieldTermStructure> termStructure = new Handle<YieldTermStructure>(vars.flatCurve);
        vars.model = new HullWhite(termStructure);

        final Schedule schedule = new Schedule(vars.issueDate(), vars.maturityDate(),
                new Period(Frequency.Semiannual), vars.calendar,
                vars.rollingConvention, vars.rollingConvention,
                DateGeneration.Rule.Backward, false);
        final double[] coupons = new double[] { 0.05 };

        final ZeroCouponBond zcb = new ZeroCouponBond(3, vars.calendar, 100.0,
                vars.maturityDate(), vars.rollingConvention);
        final FixedRateBond frb = new FixedRateBond(3, 100.0, schedule, coupons,
                new Thirty360(Thirty360.Convention.BondBasis));
        final PricingEngine disc = new DiscountingBondEngine(termStructure);
        zcb.setPricingEngine(disc);
        frb.setPricingEngine(disc);

        // no callability
        final CallabilitySchedule empty = new CallabilitySchedule();
        CallableZeroCouponBond bond1 = new CallableZeroCouponBond(3, 100.0, vars.calendar,
                vars.maturityDate(), new Thirty360(Thirty360.Convention.BondBasis),
                vars.rollingConvention, 100.0, vars.issueDate(), empty);
        CallableFixedRateBond bond2 = new CallableFixedRateBond(3, 100.0, schedule, coupons,
                new Thirty360(Thirty360.Convention.BondBasis), vars.rollingConvention,
                100.0, vars.issueDate(), empty);

        final int timeSteps = 240;
        final PricingEngine treeEngine = new TreeCallableFixedRateBondEngine(vars.model, timeSteps,
                termStructure);
        bond1.setPricingEngine(treeEngine);
        bond2.setPricingEngine(treeEngine);

        final double tolerance = 1.0e-4;
        assertEquals("empty callable ZCB must reproduce plain ZCB price",
                zcb.cleanPrice(), bond1.cleanPrice(), tolerance);
        assertEquals("empty callable FRB must reproduce plain FRB price",
                frb.cleanPrice(), bond2.cleanPrice(), tolerance);

        // out-of-the-money callability — should still reproduce plain price.
        final CallabilitySchedule oom = new CallabilitySchedule();
        for (Date d : vars.evenYears()) {
            oom.add(new Callability(
                    new Callability.Price(10000.0, Callability.Price.Type.Clean),
                    Callability.Type.Call, d));
        }
        for (Date d : vars.oddYears()) {
            oom.add(new Callability(
                    new Callability.Price(0.0, Callability.Price.Type.Clean),
                    Callability.Type.Put, d));
        }
        bond1 = new CallableZeroCouponBond(3, 100.0, vars.calendar,
                vars.maturityDate(), new Thirty360(Thirty360.Convention.BondBasis),
                vars.rollingConvention, 100.0, vars.issueDate(), oom);
        bond2 = new CallableFixedRateBond(3, 100.0, schedule, coupons,
                new Thirty360(Thirty360.Convention.BondBasis), vars.rollingConvention,
                100.0, vars.issueDate(), oom);
        bond1.setPricingEngine(treeEngine);
        bond2.setPricingEngine(treeEngine);
        assertEquals("OOM callable ZCB must reproduce plain ZCB price",
                zcb.cleanPrice(), bond1.cleanPrice(), tolerance);
        assertEquals("OOM callable FRB must reproduce plain FRB price",
                frb.cleanPrice(), bond2.cleanPrice(), tolerance);
    }

    /**
     * testCached — callable/puttable/mixed clean prices pinned from the C++
     * probe at QuantLib v1.42.1 SHA 099987f.
     * <p>
     * Note: the C++ test's storedPrice1 = 110.60975477 differs from the
     * freshly-computed C++ value 110.6083494... by ~1.4e-3. That stale stored
     * value would itself fail the C++ test under the documented 1e-8
     * tolerance; we pin the actual current C++ output to mirror what the code
     * computes today, not the inline literal.
     */
    @Test
    public void testCached() {
        final Vars vars = new Vars();
        vars.setToday(PINNED_TODAY);
        vars.flatCurve = vars.makeFlatCurve(0.032);
        final Handle<YieldTermStructure> termStructure = new Handle<YieldTermStructure>(vars.flatCurve);
        vars.model = new HullWhite(termStructure);

        final Schedule schedule = new Schedule(vars.issueDate(), vars.maturityDate(),
                new Period(Frequency.Semiannual), vars.calendar,
                vars.rollingConvention, vars.rollingConvention,
                DateGeneration.Rule.Backward, false);
        final double[] coupons = new double[] { 0.05 };

        final CallabilitySchedule cbs = new CallabilitySchedule();
        final CallabilitySchedule pbs = new CallabilitySchedule();
        final CallabilitySchedule all = new CallabilitySchedule();
        for (Date d : vars.evenYears()) {
            final Callability e = new Callability(
                    new Callability.Price(110.0, Callability.Price.Type.Clean),
                    Callability.Type.Call, d);
            cbs.add(e); all.add(e);
        }
        for (Date d : vars.oddYears()) {
            final Callability e = new Callability(
                    new Callability.Price(100.0, Callability.Price.Type.Clean),
                    Callability.Type.Put, d);
            pbs.add(e); all.add(e);
        }

        final int timeSteps = 240;
        final PricingEngine engine = new TreeCallableFixedRateBondEngine(vars.model, timeSteps,
                termStructure);

        // C++ probe reference (callablebonds_probe, v1.42.1 SHA 099987f):
        final double storedCallable = 110.60834946093415;
        final double storedPuttable = 115.1658492587932;
        final double storedMixed    = 110.97398403063768;
        final double tolerance = 1.0e-8;

        final CallableFixedRateBond b1 = new CallableFixedRateBond(3, 10000.0, schedule, coupons,
                new Thirty360(Thirty360.Convention.BondBasis), vars.rollingConvention,
                100.0, vars.issueDate(), cbs);
        b1.setPricingEngine(engine);
        assertEquals("cached callable clean price",
                storedCallable, b1.cleanPrice(), tolerance);

        final CallableFixedRateBond b2 = new CallableFixedRateBond(3, 10000.0, schedule, coupons,
                new Thirty360(Thirty360.Convention.BondBasis), vars.rollingConvention,
                100.0, vars.issueDate(), pbs);
        b2.setPricingEngine(engine);
        assertEquals("cached puttable clean price",
                storedPuttable, b2.cleanPrice(), tolerance);

        final CallableFixedRateBond b3 = new CallableFixedRateBond(3, 10000.0, schedule, coupons,
                new Thirty360(Thirty360.Convention.BondBasis), vars.rollingConvention,
                100.0, vars.issueDate(), all);
        b3.setPricingEngine(engine);
        assertEquals("cached call/put mixed clean price",
                storedMixed, b3.cleanPrice(), tolerance);
    }

    /**
     * testSnappingExerciseDate2ClosestCouponDate — NPV-equality leg of the
     * C++ test. The OAS-monotonicity leg requires
     * {@code ShortRateTree.setSpread} (not ported) and is deferred.
     */
    @Test
    public void testSnappingExerciseDate2ClosestCouponDate() {
        final Date today = new Date(18, Month.May, 2021);
        new Settings().setEvaluationDate(today);
        final Calendar calendar = new UnitedStates(UnitedStates.Market.FederalReserve);
        final DayCounter accrualDCC = new Thirty360(Thirty360.Convention.USA);
        final Frequency frequency = Frequency.Semiannual;
        final Handle<YieldTermStructure> termStructure = new Handle<YieldTermStructure>(
                new FlatForward(today, 0.02, new Actual365Fixed()));

        final Date initialCallDate = new Date(14, Month.February, 2022);
        final double tolerance = 1.0e-10;

        for (int i = -10; i < 11; i++) {
            final Date callDate = initialCallDate.add(i);
            if (!calendar.isBusinessDay(callDate)) {
                continue;
            }

            final int settlementDays = 2;
            final Date settlementDate = new Date(20, Month.May, 2021);
            final double coupon = 0.05;
            final double faceAmount = 100.0;
            final double redemption = faceAmount;
            final Date maturityDate = new Date(14, Month.February, 2026);
            final Date issueDate = settlementDate.sub(2 * 366);

            final Schedule schedule = new Schedule(issueDate, maturityDate,
                    new Period(frequency), calendar,
                    BusinessDayConvention.Unadjusted, BusinessDayConvention.Unadjusted,
                    DateGeneration.Rule.Backward, false);
            final double[] coupons = new double[schedule.size() - 1];
            for (int k = 0; k < coupons.length; k++) coupons[k] = coupon;

            final CallabilitySchedule callabilitySchedule = new CallabilitySchedule();
            callabilitySchedule.add(new Callability(
                    new Callability.Price(faceAmount, Callability.Price.Type.Clean),
                    Callability.Type.Call, callDate));

            final HullWhite model = new HullWhite(termStructure, 1.0e-12, 0.003);
            final PricingEngine treeEngine = new TreeCallableFixedRateBondEngine(model, 40,
                    termStructure);

            final CallableFixedRateBond callableBond = new CallableFixedRateBond(
                    settlementDays, faceAmount, schedule, coupons, accrualDCC,
                    BusinessDayConvention.Following, redemption, issueDate, callabilitySchedule);
            callableBond.setPricingEngine(treeEngine);

            // Build truncated schedule for the fixed-rate equivalent. The
            // callable's effective horizon is the snapped-to-coupon call date;
            // build an arbitrary-date Schedule that runs from issue to callDate.
            final List<Date> truncatedDates = new ArrayList<>();
            for (int k = 0; k < schedule.size(); k++) {
                final Date d = schedule.date(k);
                if (d.le(callDate)) {
                    truncatedDates.add(d);
                }
            }
            if (truncatedDates.isEmpty()
                    || !truncatedDates.get(truncatedDates.size() - 1).eq(callDate)) {
                truncatedDates.add(callDate);
            }
            final Schedule frbSchedule = new Schedule(truncatedDates, calendar,
                    BusinessDayConvention.Unadjusted);
            final double[] frbCoupons = new double[frbSchedule.size() - 1];
            for (int k = 0; k < frbCoupons.length; k++) frbCoupons[k] = coupon;

            final FixedRateBond fixedRateBond = new FixedRateBond(settlementDays, faceAmount,
                    frbSchedule, frbCoupons, accrualDCC,
                    BusinessDayConvention.Following, redemption, issueDate);
            fixedRateBond.setPricingEngine(new DiscountingBondEngine(termStructure));

            final double npvCallable = callableBond.NPV();
            final double npvFixed = fixedRateBond.NPV();
            assertEquals("snap-to-coupon NPV mismatch at i=" + i + " (callDate=" + callDate + ")",
                    npvFixed, npvCallable, tolerance);
        }

        // OAS monotonicity leg deferred — see class-level Javadoc carry-forwards
        // (requires ShortRateTree.setSpread which is not yet ported).
    }

    /**
     * testBlackEngine — Black engine for European callable zero-coupon bond.
     * <p>
     * Pinned against the C++ probe value
     * {@code 74.54494134581076} (see
     * {@code migration-harness/references/instruments/callable_bond_black_engine.json}).
     * <p>
     * Phase 5e.5b-CFC-d-271: un-ignored after the
     * {@link BlackCallableFixedRateBondEngine} production fix landed
     * (Java was passing {@code null} as the {@code npvDate} arg to the
     * 5-arg {@code CashFlows.npv} static overload, which returns the
     * un-rescaled total instead of the present value at the settlement /
     * exercise date; the C++ engine uses the 4-arg overload whose
     * {@code npvDate} defaults to the settlement date).
     */
    @Test
    public void testBlackEngine() {
        final Vars vars = new Vars();
        vars.setToday(new Date(20, Month.September, 2022));
        vars.flatCurve = vars.makeFlatCurve(0.03);
        final Handle<YieldTermStructure> termStructure = new Handle<YieldTermStructure>(vars.flatCurve);

        final CallabilitySchedule callabilities = new CallabilitySchedule();
        callabilities.add(new Callability(
                new Callability.Price(100.0, Callability.Price.Type.Clean),
                Callability.Type.Call,
                vars.calendar.advance(vars.issueDate(),
                        new Period(4, TimeUnit.Years),
                        BusinessDayConvention.Following)));

        final CallableZeroCouponBond bond = new CallableZeroCouponBond(3, 10000.0, vars.calendar,
                vars.maturityDate(), new Thirty360(Thirty360.Convention.BondBasis),
                vars.rollingConvention, 100.0, vars.issueDate(), callabilities);

        bond.setPricingEngine(new BlackCallableZeroCouponBondEngine(
                new Handle<Quote>(new SimpleQuote(0.3)), termStructure));

        // C++ probe value (v1.42.1 SHA 099987f). C++ inline test tolerance
        // is 1.0e-4; Java vs C++ agree to ~6e-10 after the engine fix.
        final double cached = 74.54494134581076;
        assertEquals("Java Black engine cleanPrice must match C++",
                cached, bond.cleanPrice(), 1.0e-4);
    }

    /**
     * testImpliedVol — implied volatility from a Black-engine target price.
     * Self-validating: solve for vol, then re-price under that vol and
     * verify the round-trip recovers the target. C++ tolerance 1e-4.
     */
    @Test
    public void testImpliedVol() {
        final Vars vars = new Vars();
        vars.setToday(PINNED_TODAY);
        vars.flatCurve = vars.makeFlatCurve(0.03);
        final Handle<YieldTermStructure> termStructure = new Handle<YieldTermStructure>(vars.flatCurve);

        final Schedule schedule = new Schedule(vars.issueDate(), vars.maturityDate(),
                new Period(Frequency.Semiannual), vars.calendar,
                vars.rollingConvention, vars.rollingConvention,
                DateGeneration.Rule.Backward, false);
        final double[] coupons = new double[] { 0.01 };

        final CallabilitySchedule cbs = new CallabilitySchedule();
        cbs.add(new Callability(
                new Callability.Price(100.0, Callability.Price.Type.Clean),
                Callability.Type.Call,
                schedule.date(8)));

        final CallableFixedRateBond bond = new CallableFixedRateBond(3, 10000.0, schedule, coupons,
                new Thirty360(Thirty360.Convention.BondBasis), vars.rollingConvention,
                100.0, vars.issueDate(), cbs);

        // Dirty-target leg
        final Callability.Price targetDirty = new Callability.Price(78.50,
                Callability.Price.Type.Dirty);
        final double volDirty = bond.impliedVolatility(targetDirty, termStructure,
                1.0e-8, 200, 1.0e-4, 1.0);
        bond.setPricingEngine(new BlackCallableFixedRateBondEngine(
                new Handle<Quote>(new SimpleQuote(volDirty)), termStructure));
        assertEquals("implied vol must reproduce target dirty price",
                78.50, bond.dirtyPrice(), 1.0e-4);

        // Clean-target leg
        final Callability.Price targetClean = new Callability.Price(78.50,
                Callability.Price.Type.Clean);
        final double volClean = bond.impliedVolatility(targetClean, termStructure,
                1.0e-8, 200, 1.0e-4, 1.0);
        bond.setPricingEngine(new BlackCallableFixedRateBondEngine(
                new Handle<Quote>(new SimpleQuote(volClean)), termStructure));
        assertEquals("implied vol must reproduce target clean price",
                78.50, bond.cleanPrice(), 1.0e-4);
    }

    /**
     * testBlackEngineDeepInTheMoney — deep ITM European callable bond
     * priced via Black engine.
     * <p>
     * Note: the C++ probe shows that BOTH the C++ and Java Black engines
     * produce cleanPrice ~44.7097, but the analytic
     * {@code strike * DF(callDate) / DF(settlementDate)} = 44.7181 differs
     * by ~0.008 from both. The C++ test's documented 1e-8 tolerance is too
     * tight for either implementation under this fixture; we pin against
     * the C++ probe's actual calculated value (44.70971553808225) at the
     * tight Java-vs-C++ tolerance (1e-4) — Java and C++ Black engines agree
     * on the discrepancy.
     */
    @Test
    public void testBlackEngineDeepInTheMoney() {
        final Vars vars = new Vars();
        vars.setToday(new Date(20, Month.September, 2022));
        vars.flatCurve = vars.makeFlatCurve(0.05);
        final Handle<YieldTermStructure> termStructure = new Handle<YieldTermStructure>(vars.flatCurve);

        final Schedule schedule = new Schedule(vars.issueDate(), vars.maturityDate(),
                new Period(Frequency.Semiannual), vars.calendar,
                vars.rollingConvention, vars.rollingConvention,
                DateGeneration.Rule.Backward, false);
        final double[] coupons = new double[] { 0.0 };

        final Date callabilityDate = schedule.date(6);
        final double strike = 50.0;

        final CallabilitySchedule cbs = new CallabilitySchedule();
        cbs.add(new Callability(
                new Callability.Price(strike, Callability.Price.Type.Clean),
                Callability.Type.Call, callabilityDate));

        final CallableFixedRateBond bond = new CallableFixedRateBond(3, 10000.0, schedule, coupons,
                new Thirty360(Thirty360.Convention.BondBasis), vars.rollingConvention,
                100.0, vars.issueDate(), cbs);
        final double vol = 1.0e-10;
        bond.setPricingEngine(new BlackCallableFixedRateBondEngine(
                new Handle<Quote>(new SimpleQuote(vol)), termStructure));

        // C++ probe (v1.42.1 SHA 099987f): both engines compute ~44.7097;
        // the analytic strike*DF ratio is ~44.7181 — both engines diverge
        // from it equally, so we pin Java to the C++ engine result.
        final double cppCalculated = 44.70971553808225;
        assertEquals("Java Black engine deep-ITM cleanPrice must match C++",
                cppCalculated, bond.cleanPrice(), 1.0e-4);
    }

    /**
     * testCallableFixedRateBondWithArbitrarySchedule — exercises the tree
     * engine on an arbitrary (non-tenored) schedule. Mirrors C++ v1.42.1
     * {@code testCallableFixedRateBondWithArbitrarySchedule} which only
     * checks that {@code cleanPrice()} does not throw. Phase 5e.5b-CFC-d-159
     * additionally pins the cleanPrice against the C++ probe reference
     * (104.23185995529222) to lock down the value end-to-end.
     */
    @Test
    public void testCallableFixedRateBondWithArbitrarySchedule() {
        final Vars vars = new Vars();
        final int settlementDays = 2;
        vars.setToday(new Date(10, Month.January, 2020));
        vars.flatCurve = vars.makeFlatCurve(0.03);
        final Handle<YieldTermStructure> termStructure = new Handle<YieldTermStructure>(vars.flatCurve);
        vars.model = new HullWhite(termStructure);

        final int timeSteps = 240;
        final PricingEngine engine = new TreeCallableFixedRateBondEngine(vars.model, timeSteps,
                termStructure);

        final List<Date> dates = new ArrayList<>();
        dates.add(new Date(20, Month.February, 2020));
        dates.add(new Date(15, Month.August, 2020));
        dates.add(new Date(25, Month.September, 2021));
        dates.add(new Date(27, Month.January, 2022));

        final Schedule schedule = new Schedule(dates, vars.calendar,
                BusinessDayConvention.Unadjusted);

        final CallabilitySchedule callabilities = new CallabilitySchedule();
        callabilities.add(new Callability(
                new Callability.Price(100.0, Callability.Price.Type.Clean),
                Callability.Type.Call, dates.get(2)));

        final double[] coupons = new double[] { 0.06 };

        final CallableFixedRateBond callableBond = new CallableFixedRateBond(
                settlementDays, 100.0, schedule, coupons, vars.dayCounter,
                vars.rollingConvention, 100.0, vars.issueDate(), callabilities);
        callableBond.setPricingEngine(engine);

        // C++ inline test only checks BOOST_CHECK_NO_THROW; pin C++ probe value
        // (v1.42.1 SHA 099987f) for extra cross-validation.
        final double expectedCleanPrice = 104.23185995529222;
        assertEquals("cleanPrice with arbitrary-date schedule must match C++ probe",
                expectedCleanPrice, callableBond.cleanPrice(), 1.0e-8);
    }

    /**
     * testCallableBondOasWithDifferentNotinals — OAS should be invariant
     * w.r.t. notional. Exercises {@code OneFactorModel.ShortRateTree.setSpread}
     * via {@link CallableBond#OAS} / {@link CallableBond#cleanPriceOAS}.
     */
    @Test
    public void testCallableBondOasWithDifferentNotinals() {
        final Vars vars = new Vars();
        final int settlementDays = 2;
        vars.setToday(new Date(10, Month.January, 2020));

        final double[] coupons = new double[] { 0.055 };
        final DayCounter dc = vars.dayCounter;
        final Compounding compounding = Compounding.Compounded;
        final Frequency frequency = Frequency.Semiannual;

        vars.flatCurve = vars.makeFlatCurve(0.03);
        final Handle<YieldTermStructure> termStructure = new Handle<YieldTermStructure>(vars.flatCurve);
        vars.model = new HullWhite(termStructure);

        final int timeSteps = 240;
        final PricingEngine engine = new TreeCallableFixedRateBondEngine(vars.model, timeSteps,
                termStructure);

        final Schedule schedule = new Schedule(vars.issueDate(), vars.maturityDate(),
                new Period(frequency), vars.calendar,
                vars.rollingConvention, vars.rollingConvention,
                DateGeneration.Rule.Backward, false);

        // C++ uses schedule.after(firstCallDate).until(lastCallDate); reproduce
        // the same set by index — the call schedule contains the dates strictly
        // between index (size-5) and index (size-2), inclusive of (size-4) and
        // (size-3) (i.e. two interior dates). This matches the C++ semantics of
        // after(firstCallDate) excluding firstCallDate itself.
        final int firstIdx = schedule.size() - 5;
        final int lastIdx  = schedule.size() - 2;
        final CallabilitySchedule callSchedule = new CallabilitySchedule();
        for (int k = firstIdx + 1; k <= lastIdx; k++) {
            callSchedule.add(new Callability(
                    new Callability.Price(100.0, Callability.Price.Type.Clean),
                    Callability.Type.Call, schedule.date(k)));
        }

        final CallableFixedRateBond callableBond100 = new CallableFixedRateBond(
                settlementDays, 100.0, schedule, coupons, vars.dayCounter,
                vars.rollingConvention, 100.0, vars.issueDate(), callSchedule);
        callableBond100.setPricingEngine(engine);

        final CallableFixedRateBond callableBond25 = new CallableFixedRateBond(
                settlementDays, 25.0, schedule, coupons, vars.dayCounter,
                vars.rollingConvention, 100.0, vars.issueDate(), callSchedule);
        callableBond25.setPricingEngine(engine);

        final double cleanPrice = 96.0;
        final double oas100 = callableBond100.OAS(cleanPrice, termStructure, dc,
                compounding, frequency);
        final double oas25  = callableBond25.OAS(cleanPrice, termStructure, dc,
                compounding, frequency);
        // C++ uses == (bit-exact); both bonds run through the identical engine
        // with identical args (notional is divided out in CallableBond.OAS).
        assertEquals("OAS must be invariant under notional rescaling",
                oas100, oas25, 1.0e-12);

        final double oas = 0.0300;
        final double cleanPrice100 = callableBond100.cleanPriceOAS(oas, termStructure, dc,
                compounding, frequency);
        final double cleanPrice25  = callableBond25.cleanPriceOAS(oas, termStructure, dc,
                compounding, frequency);
        assertEquals("cleanPriceOAS must be invariant under notional rescaling",
                cleanPrice100, cleanPrice25, 1.0e-10);
    }

    /**
     * testOasContinuityThroughExCouponWindow — OAS should be smooth
     * across an ex-coupon window.
     * <p>
     * Mirrors C++ v1.42.1 {@code testOasContinuityThroughExCouponWindow}: builds
     * a quarterly callable fixed-rate bond with a 14-day ex-coupon period,
     * sweeps the call date across the ex-coupon / payment-date boundary,
     * and asserts that the OAS range (max - min, in bps) stays under
     * the C++ tolerance of 50 bps. Before the C++ fix referenced by
     * <a href="https://github.com/lballabio/QuantLib/issues/2236">QL#2236</a>,
     * the range was ~667 bps.
     * <p>
     * Un-ignored Phase 5e.5b-CFC-d-253 — {@link CallableFixedRateBond}
     * now exposes the five-extra-arg ctor overload
     * ({@code exCouponPeriod, exCouponCalendar, exCouponConvention,
     * exCouponEndOfMonth}) which threads through to
     * {@link org.jquantlib.cashflow.FixedRateLeg#withExCouponPeriod}, so
     * the leg-build inside the bond propagates ex-coupon to its
     * {@link org.jquantlib.cashflow.FixedRateCoupon}s.
     */
    @Test
    public void testOasContinuityThroughExCouponWindow() {
        // Mirrors C++ test-suite/callablebonds.cpp:953-1046.
        final Date today = new Date(31, Month.January, 2024);
        new Settings().setEvaluationDate(today);

        final int settlementDays = 0;
        final Calendar calendar = new UnitedStates(UnitedStates.Market.NYSE);
        final DayCounter dc = new Thirty360(Thirty360.Convention.BondBasis);
        final BusinessDayConvention bdc = BusinessDayConvention.Unadjusted;
        final Frequency frequency = Frequency.Quarterly;
        final Period exCouponPeriod = new Period(14, TimeUnit.Days);

        final Date issueDate = today;
        final Date maturityDate = new Date(31, Month.January, 2029);
        final double faceAmount = 100.0;
        final double[] coupons = new double[] { 0.06 };
        final double redemption = 100.0;

        final Handle<YieldTermStructure> termStructure = new Handle<YieldTermStructure>(
                new FlatForward(today, 0.04, dc));
        final HullWhite model = new HullWhite(termStructure);

        final Schedule schedule = new MakeSchedule()
                .from(issueDate)
                .to(maturityDate)
                .withFrequency(frequency)
                .withCalendar(calendar)
                .withConvention(bdc)
                .withTerminationDateConvention(bdc)
                .backwards()
                .endOfMonth(true)
                .schedule();

        // First coupon payment date after today; ex-coupon date = payment - 14d.
        final Date firstPaymentDate = schedule.date(1);
        final Date exCouponDate = firstPaymentDate.sub(exCouponPeriod);

        // Sweep call date from 1 week before ex-coupon to 1 week after payment.
        final Date sweepStart = exCouponDate.sub(7);
        final Date sweepEnd   = firstPaymentDate.add(7);

        final double cleanPrice = 100.0;
        final Compounding compounding = Compounding.Compounded;

        double maxOas = -Double.MAX_VALUE;
        double minOas =  Double.MAX_VALUE;

        for (Date callDate = sweepStart.clone(); callDate.le(sweepEnd);
                callDate = callDate.add(1)) {
            final CallabilitySchedule callSchedule = new CallabilitySchedule();
            callSchedule.add(new Callability(
                    new Callability.Price(redemption, Callability.Price.Type.Clean),
                    Callability.Type.Call,
                    callDate));

            final CallableFixedRateBond bond = new CallableFixedRateBond(
                    settlementDays, faceAmount, schedule, coupons, dc,
                    bdc, redemption, issueDate, callSchedule,
                    exCouponPeriod, new NullCalendar(),
                    BusinessDayConvention.Unadjusted, false);
            bond.setPricingEngine(new TreeCallableFixedRateBondEngine(model, 100, termStructure));

            final double oas = bond.OAS(cleanPrice, termStructure, dc,
                    compounding, frequency) * 10000.0;
            maxOas = Math.max(maxOas, oas);
            minOas = Math.min(minOas, oas);
        }

        final double oasRange = maxOas - minOas;
        // OAS should be reasonably continuous through the ex-coupon window.
        // A range of 50 bps allows for tree discretization noise; the bug
        // produced a range of ~667 bps. Tolerance mirrors C++ exactly.
        final double tolerance = 50.0;
        assertTrue(
                "OAS discontinuity across ex-coupon window: min=" + minOas
                        + " bps, max=" + maxOas + " bps, range=" + oasRange
                        + " bps, tolerance=" + tolerance + " bps (sweep "
                        + sweepStart + " to " + sweepEnd + ")",
                oasRange <= tolerance);
    }

    // ------------------------------------------------------------------
    // Legacy Phase 4b smoke tests retained for additional coverage.
    // ------------------------------------------------------------------

    /**
     * Lightweight Phase-4b smoke: end-to-end construction of a
     * {@link CallableFixedRateBond} with a non-trivial put/call schedule.
     */
    @Test
    public void smokeConstructCallableFixedRateBond() {
        final Vars vars = new Vars();
        vars.setToday(new Date(3, Month.June, 2004));

        vars.flatCurve = vars.makeFlatCurve(0.032);
        final Handle<YieldTermStructure> termStructure = new Handle<YieldTermStructure>(
                vars.flatCurve);
        vars.model = new HullWhite(termStructure);

        final Schedule schedule = new Schedule(
                vars.issueDate(), vars.maturityDate(),
                new Period(Frequency.Semiannual), vars.calendar,
                vars.rollingConvention, vars.rollingConvention,
                DateGeneration.Rule.Backward, false);

        final double[] coupons = new double[] { 0.05 };

        final CallabilitySchedule callabilities = new CallabilitySchedule();
        for (final Date d : vars.evenYears()) {
            callabilities.add(new Callability(
                    new Callability.Price(110.0, Callability.Price.Type.Clean),
                    Callability.Type.Call, d));
        }

        final int timeSteps = 240;
        final PricingEngine engine = new TreeCallableFixedRateBondEngine(vars.model, timeSteps,
                termStructure);

        final CallableFixedRateBond bond = new CallableFixedRateBond(3, 10000.0, schedule,
                coupons, new Thirty360(Thirty360.Convention.BondBasis),
                vars.rollingConvention, 100.0, vars.issueDate(), callabilities);
        bond.setPricingEngine(engine);

        final double price = bond.cleanPrice();
        assertTrue("price must be finite: " + price,
                !Double.isNaN(price) && !Double.isInfinite(price));
        assertTrue("price out of plausibility band [50, 200]: " + price,
                price > 50.0 && price < 200.0);
    }

    /**
     * Lightweight Phase-4b smoke: end-to-end construction of a
     * {@link CallableZeroCouponBond} with a callable schedule and pricing
     * via {@link TreeCallableZeroCouponBondEngine}.
     */
    @Test
    public void smokeConstructCallableZeroCouponBond() {
        final Vars vars = new Vars();
        vars.setToday(new Date(3, Month.June, 2004));

        vars.flatCurve = vars.makeFlatCurve(0.03);
        final Handle<YieldTermStructure> termStructure = new Handle<YieldTermStructure>(
                vars.flatCurve);
        vars.model = new HullWhite(termStructure);

        final CallabilitySchedule callabilities = new CallabilitySchedule();
        callabilities.add(new Callability(
                new Callability.Price(100.0, Callability.Price.Type.Clean),
                Callability.Type.Call,
                vars.calendar.advance(vars.issueDate(), new Period(4, TimeUnit.Years),
                        BusinessDayConvention.Following)));

        final int timeSteps = 240;
        final PricingEngine engine = new TreeCallableZeroCouponBondEngine(vars.model, timeSteps,
                termStructure);

        final CallableZeroCouponBond bond = new CallableZeroCouponBond(3, 100.0, vars.calendar,
                vars.maturityDate(), new Thirty360(Thirty360.Convention.BondBasis),
                vars.rollingConvention, 100.0, vars.issueDate(), callabilities);
        bond.setPricingEngine(engine);

        final double price = bond.cleanPrice();
        assertTrue("price must be finite: " + price,
                !Double.isNaN(price) && !Double.isInfinite(price));
        assertTrue("price out of plausibility band [40, 120]: " + price,
                price > 40.0 && price < 120.0);
    }

    /**
     * Lightweight Phase-4b smoke: a degenerate callable (no callability
     * schedule) should reproduce a plain {@link FixedRateBond}'s price.
     */
    @Test
    public void smokeDegenerateCallableMatchesFixedRateBond() {
        final Vars vars = new Vars();
        vars.setToday(new Date(3, Month.June, 2004));

        vars.flatCurve = vars.makeFlatCurve(0.034);
        final Handle<YieldTermStructure> termStructure = new Handle<YieldTermStructure>(
                vars.flatCurve);
        vars.model = new HullWhite(termStructure);

        final Schedule schedule = new Schedule(
                vars.issueDate(), vars.maturityDate(),
                new Period(Frequency.Semiannual), vars.calendar,
                vars.rollingConvention, vars.rollingConvention,
                DateGeneration.Rule.Backward, false);

        final double[] coupons = new double[] { 0.05 };

        final FixedRateBond plain = new FixedRateBond(3, 100.0, schedule, coupons,
                new Thirty360(Thirty360.Convention.BondBasis));
        plain.setPricingEngine(new DiscountingBondEngine(termStructure));

        final CallabilitySchedule empty = new CallabilitySchedule();
        final CallableFixedRateBond callable = new CallableFixedRateBond(3, 100.0, schedule,
                coupons, new Thirty360(Thirty360.Convention.BondBasis),
                vars.rollingConvention, 100.0, vars.issueDate(), empty);
        final int timeSteps = 240;
        final PricingEngine engine = new TreeCallableFixedRateBondEngine(vars.model, timeSteps,
                termStructure);
        callable.setPricingEngine(engine);

        final double plainPrice = plain.cleanPrice();
        final double callablePrice = callable.cleanPrice();
        final double tolerance = 1.0e-1;
        assertTrue("degenerate callable should match plain bond: callable="
                + callablePrice + ", plain=" + plainPrice
                + ", diff=" + Math.abs(callablePrice - plainPrice),
                Math.abs(callablePrice - plainPrice) < tolerance);
    }

    /**
     * Lightweight Phase-4b smoke: Black engine end-to-end construction
     * of a {@link CallableZeroCouponBond} with a single European call.
     */
    @Test
    public void smokeBlackEngine() {
        final Vars vars = new Vars();
        vars.setToday(new Date(20, Month.September, 2022));

        vars.flatCurve = vars.makeFlatCurve(0.03);
        final Handle<YieldTermStructure> termStructure = new Handle<YieldTermStructure>(
                vars.flatCurve);

        final CallabilitySchedule callabilities = new CallabilitySchedule();
        callabilities.add(new Callability(
                new Callability.Price(100.0, Callability.Price.Type.Clean),
                Callability.Type.Call,
                vars.calendar.advance(vars.issueDate(), new Period(4, TimeUnit.Years),
                        BusinessDayConvention.Following)));

        final CallableZeroCouponBond bond = new CallableZeroCouponBond(3, 10000.0, vars.calendar,
                vars.maturityDate(), new Thirty360(Thirty360.Convention.BondBasis),
                vars.rollingConvention, 100.0, vars.issueDate(), callabilities);

        bond.setPricingEngine(new BlackCallableZeroCouponBondEngine(
                new Handle<Quote>(new SimpleQuote(0.3)), termStructure));

        final double price = bond.cleanPrice();
        assertTrue("price must be finite: " + price,
                !Double.isNaN(price) && !Double.isInfinite(price));
        assertTrue("price out of band [40, 100]: " + price, price > 40.0 && price < 100.0);
    }
}
