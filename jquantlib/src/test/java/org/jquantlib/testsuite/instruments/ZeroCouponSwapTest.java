/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.CompoundingMultipleResetsPricer;
import org.jquantlib.cashflow.MultipleResetsCoupon;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.Euribor6M;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.instruments.ZeroCouponSwap;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.testsuite.util.Utilities;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.junit.Test;

/**
 * Faithful port of {@code test-suite/zerocouponswap.cpp} v1.42.1 (311 LOC,
 * 6 cases).
 *
 * <p>Exercises the {@link ZeroCouponSwap} instrument end-to-end against
 * the same flat-Euribor6M setup the C++ test uses. The expected
 * fixed/float leg NPVs are reconstructed from the swap's own internals
 * (discount at payment date times the published fixed payment / a
 * separately-built {@link MultipleResetsCoupon} amount), so no
 * cross-validation probe is required.
 *
 * <p>Phase Body-Fill (2026-05-14) — un-ignores all 6 cases. Companion
 * smoke tests live in {@link ZeroCouponSwapBasicTest}.
 *
 * <p>Source: {@code test-suite/zerocouponswap.cpp} v1.42.1 @
 * {@code 099987f0ca}.
 */
public class ZeroCouponSwapTest {

    /** Mirrors C++ {@code zerocouponswap.cpp::CommonVars}. */
    private static final class CommonVars {
        final Date today;
        final Date settlement;
        final Calendar calendar;
        final int settlementDays;
        final int paymentDelay;
        final DayCounter dayCount;
        final BusinessDayConvention businessConvention;
        final double baseNominal;
        final double finalPayment;
        final IborIndex euribor;
        final RelinkableHandle<YieldTermStructure> euriborHandle;
        final PricingEngine discountEngine;

        CommonVars() {
            settlementDays = 2;
            paymentDelay = 1;
            calendar = new Target();
            dayCount = new Actual365Fixed();
            businessConvention = BusinessDayConvention.ModifiedFollowing;
            baseNominal = 1.0e6;
            finalPayment = 1.2e6;

            euriborHandle = new RelinkableHandle<YieldTermStructure>();
            euribor = new Euribor6M(euriborHandle);
            euribor.addFixing(new Date(10, Month.February, 2021), 0.0085);

            today = calendar.adjust(new Date(15, Month.March, 2021));
            new Settings().setEvaluationDate(today);
            settlement = calendar.advance(today, settlementDays, TimeUnit.Days);

            euriborHandle.linkTo(Utilities.flatRate(settlement, 0.007, dayCount));
            discountEngine = new DiscountingSwapEngine(euriborHandle);
        }

        /**
         * Mirrors C++ helper {@code CommonVars::createMultipleResetsCoupon}.
         */
        MultipleResetsCoupon createMultipleResetsCoupon(final Date start, final Date end) {
            final Date paymentDate = calendar.advance(end,
                    new Period(paymentDelay, TimeUnit.Days), businessConvention);
            // C++ uses MakeSchedule().from(start).to(end).withTenor(euribor.tenor())
            //   .withCalendar(euribor.fixingCalendar()).
            // Java's MakeSchedule builder lacks fluent .from()/.to(); use the
            // Schedule ctor directly with equivalent inputs (defaults to
            // Backward + endOfMonth=index.endOfMonth()).
            final Schedule schedule = new Schedule(
                    start, end,
                    euribor.tenor(),
                    euribor.fixingCalendar(),
                    euribor.businessDayConvention(),
                    euribor.businessDayConvention(),
                    DateGeneration.Rule.Backward,
                    euribor.endOfMonth(),
                    new Date(), new Date());
            final MultipleResetsCoupon cpn = new MultipleResetsCoupon(
                    paymentDate, baseNominal, schedule, settlementDays, euribor);
            cpn.setPricer(new CompoundingMultipleResetsPricer());
            return cpn;
        }

        ZeroCouponSwap createZCSwap(final VanillaSwap.Type type,
                                    final Date start, final Date end,
                                    final double baseNominal,
                                    final double finalPayment) {
            final ZeroCouponSwap swap = new ZeroCouponSwap(type, baseNominal,
                    start, end, finalPayment, euribor, calendar,
                    businessConvention, paymentDelay);
            swap.setPricingEngine(discountEngine);
            return swap;
        }

        ZeroCouponSwap createZCSwap(final VanillaSwap.Type type,
                                    final Date start, final Date end,
                                    final double finalPayment) {
            return createZCSwap(type, start, end, baseNominal, finalPayment);
        }

        ZeroCouponSwap createZCSwap(final VanillaSwap.Type type,
                                    final Date start, final Date end) {
            return createZCSwap(type, start, end, finalPayment);
        }

        ZeroCouponSwap createZCSwap(final Date start, final Date end,
                                    final double fixedRate) {
            final ZeroCouponSwap swap = new ZeroCouponSwap(VanillaSwap.Type.Receiver,
                    baseNominal, start, end, fixedRate, dayCount, euribor,
                    calendar, businessConvention, paymentDelay);
            swap.setPricingEngine(discountEngine);
            return swap;
        }
    }

    /** Maps C++ {@code Swap::Type} (-1 / +1) onto JQuantLib {@code VanillaSwap.Type}. */
    private static int sign(final VanillaSwap.Type type) {
        return type == VanillaSwap.Type.Payer ? +1 : -1;
    }

    /**
     * Mirrors C++ free helper {@code checkReplicationOfZeroCouponSwapNPV}.
     */
    private static void checkReplicationOfZeroCouponSwapNPV(final Date start,
                                                            final Date end,
                                                            final VanillaSwap.Type type) {
        final CommonVars vars = new CommonVars();
        final double tolerance = 1.0e-8;

        final ZeroCouponSwap zcSwap = vars.createZCSwap(type, start, end);

        final double actualNPV = zcSwap.NPV();
        final double actualFixedLegNPV = zcSwap.fixedLegNPV();
        final double actualFloatLegNPV = zcSwap.floatingLegNPV();

        final Date paymentDate = vars.calendar.advance(end,
                new Period(vars.paymentDelay, TimeUnit.Days), vars.businessConvention);
        final double discountAtPayment =
                paymentDate.lt(vars.settlement)
                        ? 0.0
                        : vars.euriborHandle.currentLink().discount(paymentDate);
        final double expectedFixedLegNPV =
                -sign(type) * discountAtPayment * vars.finalPayment;

        final MultipleResetsCoupon subPeriodCpn =
                vars.createMultipleResetsCoupon(start, end);
        final double expectedFloatLegNPV =
                paymentDate.lt(vars.settlement)
                        ? 0.0
                        : sign(type) * discountAtPayment * subPeriodCpn.amount();

        final double expectedNPV = expectedFloatLegNPV + expectedFixedLegNPV;

        if (Math.abs(actualNPV - expectedNPV) > tolerance
                || Math.abs(actualFixedLegNPV - expectedFixedLegNPV) > tolerance
                || Math.abs(actualFloatLegNPV - expectedFloatLegNPV) > tolerance) {
            fail("unable to replicate NPVs of zero coupon swap and its legs\n"
                    + "    actual NPV:              " + actualNPV + "\n"
                    + "    expected NPV:            " + expectedNPV + "\n"
                    + "    actual fixed leg NPV:    " + actualFixedLegNPV + "\n"
                    + "    expected fixed leg NPV:  " + expectedFixedLegNPV + "\n"
                    + "    actual float leg NPV:    " + actualFloatLegNPV + "\n"
                    + "    expected float leg NPV:  " + expectedFloatLegNPV + "\n"
                    + "    start: " + start + " end: " + end + " type: " + type);
        }
    }

    /**
     * Mirrors C++ free helper {@code checkFairFixedPayment}.
     */
    private static void checkFairFixedPayment(final Date start, final Date end,
                                              final VanillaSwap.Type type) {
        final CommonVars vars = new CommonVars();
        final double tolerance = 1.0e-8;

        final ZeroCouponSwap zcSwap = vars.createZCSwap(type, start, end);
        final double fairFixedPayment = zcSwap.fairFixedPayment();
        final ZeroCouponSwap parZCSwap = vars.createZCSwap(type, start, end, fairFixedPayment);
        final double parZCSwapNPV = parZCSwap.NPV();

        if (Math.abs(parZCSwapNPV) > tolerance) {
            fail("unable to replicate fair fixed payment\n"
                    + "    actual NPV:           " + parZCSwapNPV + "\n"
                    + "    expected NPV:         0.0\n"
                    + "    fair fixed payment:   " + fairFixedPayment + "\n"
                    + "    start: " + start + " end: " + end + " type: " + type);
        }
    }

    /**
     * Mirrors C++ free helper {@code checkFairFixedRate}.
     */
    private static void checkFairFixedRate(final Date start, final Date end,
                                           final VanillaSwap.Type type) {
        final CommonVars vars = new CommonVars();
        final double tolerance = 1.0e-8;

        final ZeroCouponSwap zcSwap = vars.createZCSwap(type, start, end);
        final double fairFixedRate = zcSwap.fairFixedRate(vars.dayCount);
        final ZeroCouponSwap parZCSwap = vars.createZCSwap(start, end, fairFixedRate);
        final double parZCSwapNPV = parZCSwap.NPV();

        if (Math.abs(parZCSwapNPV) > tolerance) {
            fail("unable to replicate fair fixed rate\n"
                    + "    actual NPV:        " + parZCSwapNPV + "\n"
                    + "    expected NPV:      0.0\n"
                    + "    fair fixed rate:   " + fairFixedRate + "\n"
                    + "    start: " + start + " end: " + end + " type: " + type);
        }
    }

    @Test
    public void testInstrumentValuation() {
        QL.info("Testing zero coupon swap valuation...");

        // Ongoing instrument
        checkReplicationOfZeroCouponSwapNPV(
                new Date(12, Month.February, 2021),
                new Date(12, Month.February, 2041),
                VanillaSwap.Type.Receiver);
        // Forward starting instrument
        checkReplicationOfZeroCouponSwapNPV(
                new Date(15, Month.April, 2021),
                new Date(12, Month.February, 2041),
                VanillaSwap.Type.Payer);

        // Expired instrument (default Receiver)
        checkReplicationOfZeroCouponSwapNPV(
                new Date(12, Month.February, 2000),
                new Date(12, Month.February, 2020),
                VanillaSwap.Type.Receiver);
    }

    @Test
    public void testFairFixedPayment() {
        QL.info("Testing fair fixed payment...");

        // Ongoing instrument
        checkFairFixedPayment(
                new Date(12, Month.February, 2021),
                new Date(12, Month.February, 2041),
                VanillaSwap.Type.Receiver);

        // Spot starting instrument
        checkFairFixedPayment(
                new Date(17, Month.March, 2021),
                new Date(12, Month.February, 2041),
                VanillaSwap.Type.Payer);
    }

    @Test
    public void testFairFixedRate() {
        QL.info("Testing fair fixed rate...");

        // Ongoing instrument
        checkFairFixedRate(
                new Date(12, Month.February, 2021),
                new Date(12, Month.February, 2041),
                VanillaSwap.Type.Receiver);

        // Spot starting instrument
        checkFairFixedRate(
                new Date(17, Month.March, 2021),
                new Date(12, Month.February, 2041),
                VanillaSwap.Type.Payer);
    }

    @Test
    public void testFixedPaymentFromRate() {
        QL.info("Testing fixed payment calculation from rate...");

        final CommonVars vars = new CommonVars();
        final double tolerance = 1.0e-8;
        final double fixedRate = 0.01;

        final Date start = new Date(12, Month.February, 2021);
        final Date end   = new Date(12, Month.February, 2041);

        final ZeroCouponSwap zcSwap = vars.createZCSwap(start, end, fixedRate);
        final double actualFxdPmt = zcSwap.fixedPayment();

        final double T = vars.dayCount.yearFraction(start, end);
        final double expectedFxdPmt = zcSwap.baseNominal()
                * (Math.pow(1.0 + fixedRate, T) - 1.0);

        if (Math.abs(actualFxdPmt - expectedFxdPmt) > tolerance) {
            fail("unable to replicate fixed payment from rate\n"
                    + "    actual fixed payment:   " + actualFxdPmt + "\n"
                    + "    expected fixed payment: " + expectedFxdPmt + "\n"
                    + "    start: " + start + " end: " + end);
        }
    }

    @Test
    public void testArgumentsValidation() {
        QL.info("Testing arguments validation...");

        final CommonVars vars = new CommonVars();

        final Date start = new Date(12, Month.February, 2021);
        final Date end   = new Date(12, Month.February, 2041);

        // Negative base nominal
        boolean threwOnNegativeNominal = false;
        try {
            vars.createZCSwap(VanillaSwap.Type.Payer, start, end, -1.0e6, 1.0e6);
        } catch (final RuntimeException expected) {
            threwOnNegativeNominal = true;
        }
        if (!threwOnNegativeNominal) {
            fail("expected ZeroCouponSwap to reject negative base nominal");
        }

        // Start date after end date
        boolean threwOnReversedDates = false;
        try {
            vars.createZCSwap(end, start, 0.01);
        } catch (final RuntimeException expected) {
            threwOnReversedDates = true;
        }
        if (!threwOnReversedDates) {
            fail("expected ZeroCouponSwap to reject start > end");
        }
    }

    @Test
    public void testExpectedCashFlowsInLegs() {
        QL.info("Testing expected cash flows in legs...");

        final CommonVars vars = new CommonVars();
        final double tolerance = 1.0e-8;

        final Date start = new Date(12, Month.February, 2021);
        final Date end   = new Date(12, Month.February, 2041);

        final ZeroCouponSwap zcSwap = vars.createZCSwap(start, end, 0.01);
        final CashFlow fixedCashFlow = zcSwap.fixedLeg().get(0);
        final CashFlow floatingCashFlow = zcSwap.floatingLeg().get(0);

        final Date paymentDate = vars.calendar.advance(end,
                new Period(vars.paymentDelay, TimeUnit.Days), vars.businessConvention);
        final MultipleResetsCoupon subPeriodCpn =
                vars.createMultipleResetsCoupon(start, end);

        if (Math.abs(fixedCashFlow.amount() - zcSwap.fixedPayment()) > tolerance
                || !fixedCashFlow.date().eq(paymentDate)) {
            fail("unable to replicate fixed leg\n"
                    + "    actual amount:          " + fixedCashFlow.amount() + "\n"
                    + "    expected amount:        " + zcSwap.fixedPayment() + "\n"
                    + "    actual payment date:    " + fixedCashFlow.date() + "\n"
                    + "    expected payment date:  " + paymentDate);
        }

        if (Math.abs(floatingCashFlow.amount() - subPeriodCpn.amount()) > tolerance
                || !floatingCashFlow.date().eq(paymentDate)) {
            fail("unable to replicate floating leg\n"
                    + "    actual amount:          " + floatingCashFlow.amount() + "\n"
                    + "    expected amount:        " + subPeriodCpn.amount() + "\n"
                    + "    actual payment date:    " + floatingCashFlow.date() + "\n"
                    + "    expected payment date:  " + paymentDate);
        }

        // belt-and-suspenders: assertions on size as well — leg should have one
        // cashflow each.
        assertTrue("fixed leg expected size 1 actual " + zcSwap.fixedLeg().size(),
                zcSwap.fixedLeg().size() == 1);
        assertTrue("floating leg expected size 1 actual " + zcSwap.floatingLeg().size(),
                zcSwap.floatingLeg().size() == 1);
    }
}
