/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jquantlib.Settings;
import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.currencies.America;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.EquityIndex;
import org.jquantlib.indexes.ibor.Sofr;
import org.jquantlib.indexes.ibor.USDLibor;
import org.jquantlib.instruments.EquityTotalReturnSwap;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.NullCalendar;
import org.jquantlib.time.calendars.UnitedStates;
import org.junit.Test;

/**
 * Cross-validated tests for {@link EquityTotalReturnSwap} ported from
 * {@code test-suite/equitytotalreturnswap.cpp} v1.42.1 ({@code 099987f0ca}).
 *
 * <p>Phase 5d.5-EQ — un-ignores the 5 skeleton cases now that the
 * {@code EquityTotalReturnSwap} family exists.
 *
 * <p><strong>Phase 5d.5-EQb carry-forward:</strong> the C++ test exercises
 * cases with {@code paymentDelay=2}; the Java IborLeg builder lacks
 * {@code withPaymentLag}/{@code withPaymentCalendar} so those sub-cases
 * are skipped here for the IBOR variant. The OvernightLeg variant
 * (which does support paymentLag/paymentCalendar) still runs all cases.
 *
 * <p>Tier: TIGHT for fairMargin / equityLegNPV (1e-8 abs); 1e-2 abs for
 * full TRS NPV consistency (mirrors C++ test tolerance for floating-leg
 * compounding noise).
 */
public class EquityTotalReturnSwapTest {

    private static final double TOL_TIGHT = 1.0e-8;
    private static final double TOL_NPV = 1.0e-2;

    private static final class CommonVars {
        final Date today;
        final Calendar calendar;
        final DayCounter dayCount;
        final EquityIndex equityIndex;
        final USDLibor usdLibor;
        final Sofr sofr;
        final RelinkableHandle<YieldTermStructure> interestHandle;
        final RelinkableHandle<YieldTermStructure> dividendHandle;
        final SimpleQuote spot;
        final RelinkableHandle<Quote> spotHandle;
        final PricingEngine discountEngine;

        CommonVars() {
            calendar = new UnitedStates(UnitedStates.Market.GOVERNMENTBOND);
            dayCount = new Actual365Fixed();

            today = calendar.adjust(new Date(27, Month.January, 2023));
            new Settings().setEvaluationDate(today);

            interestHandle = new RelinkableHandle<YieldTermStructure>();
            dividendHandle = new RelinkableHandle<YieldTermStructure>();
            spotHandle = new RelinkableHandle<Quote>();

            equityIndex = new EquityIndex("eqIndex", calendar, new America.USDCurrency(),
                    interestHandle, dividendHandle, spotHandle);
            equityIndex.clearFixings();
            equityIndex.addFixing(new Date(5, Month.January, 2023), 9010.0);
            equityIndex.addFixing(today, 8690.0);

            sofr = new Sofr(interestHandle);
            sofr.clearFixings();
            // SOFR fixings — Jan 3..26 2023 (business days only)
            sofr.addFixing(new Date(3,  Month.January, 2023), 0.030);
            sofr.addFixing(new Date(4,  Month.January, 2023), 0.031);
            sofr.addFixing(new Date(5,  Month.January, 2023), 0.031);
            sofr.addFixing(new Date(6,  Month.January, 2023), 0.031);
            sofr.addFixing(new Date(9,  Month.January, 2023), 0.032);
            sofr.addFixing(new Date(10, Month.January, 2023), 0.033);
            sofr.addFixing(new Date(11, Month.January, 2023), 0.033);
            sofr.addFixing(new Date(12, Month.January, 2023), 0.033);
            sofr.addFixing(new Date(13, Month.January, 2023), 0.033);
            sofr.addFixing(new Date(17, Month.January, 2023), 0.033);
            sofr.addFixing(new Date(18, Month.January, 2023), 0.034);
            sofr.addFixing(new Date(19, Month.January, 2023), 0.034);
            sofr.addFixing(new Date(20, Month.January, 2023), 0.034);
            sofr.addFixing(new Date(23, Month.January, 2023), 0.034);
            sofr.addFixing(new Date(24, Month.January, 2023), 0.034);
            sofr.addFixing(new Date(25, Month.January, 2023), 0.034);
            sofr.addFixing(new Date(26, Month.January, 2023), 0.034);

            usdLibor = new USDLibor(new Period(3, TimeUnit.Months), interestHandle);
            usdLibor.clearFixings();
            usdLibor.addFixing(new Date(3, Month.January, 2023), 0.035);

            interestHandle.linkTo(flatRate(0.0375, dayCount));
            dividendHandle.linkTo(flatRate(0.005, dayCount));

            discountEngine = new DiscountingSwapEngine(interestHandle);

            spot = new SimpleQuote(8700.0);
            spotHandle.linkTo(spot);
        }

        EquityTotalReturnSwap createTRS(final VanillaSwap.Type type,
                                        final Schedule schedule,
                                        final boolean useOvernightIndex,
                                        final double margin,
                                        final double nominal,
                                        final double gearing,
                                        final int paymentDelay) {
            final EquityTotalReturnSwap swap;
            if (useOvernightIndex) {
                swap = new EquityTotalReturnSwap(type, nominal, schedule, equityIndex, sofr,
                        dayCount, margin, gearing, schedule.calendar(),
                        BusinessDayConvention.Following, paymentDelay);
            } else {
                swap = new EquityTotalReturnSwap(type, nominal, schedule, equityIndex, usdLibor,
                        dayCount, margin, gearing, schedule.calendar(),
                        BusinessDayConvention.Following, paymentDelay);
            }
            swap.setPricingEngine(discountEngine);
            return swap;
        }

        EquityTotalReturnSwap createTRS(final VanillaSwap.Type type,
                                        final Date start,
                                        final Date end,
                                        final boolean useOvernightIndex,
                                        final double margin,
                                        final double nominal,
                                        final double gearing,
                                        final int paymentDelay) {
            final Schedule schedule = new Schedule(start, end, new Period(3, TimeUnit.Months),
                    calendar, BusinessDayConvention.Following, BusinessDayConvention.Following,
                    DateGeneration.Rule.Backward, false);
            return createTRS(type, schedule, useOvernightIndex, margin, nominal, gearing,
                    paymentDelay);
        }
    }

    private static YieldTermStructure flatRate(final double rate, final DayCounter dc) {
        return new FlatForward(0, new NullCalendar(), rate, dc);
    }

    private static double legNPVOf(final Leg leg, final Handle<YieldTermStructure> ts) {
        double npv = 0.0;
        for (final CashFlow cf : leg) {
            npv += cf.amount() * ts.currentLink().discount(cf.date());
        }
        return npv;
    }

    private static void checkFairMarginCalculation(final VanillaSwap.Type type,
                                                   final Date start,
                                                   final Date end,
                                                   final boolean useOvernightIndex,
                                                   final double margin,
                                                   final double gearing,
                                                   final int paymentDelay) {
        final CommonVars vars = new CommonVars();
        final double nominal = 1.0e7;
        final EquityTotalReturnSwap trs = vars.createTRS(type, start, end, useOvernightIndex,
                margin, nominal, gearing, paymentDelay);
        final double fairMargin = trs.fairMargin();
        final EquityTotalReturnSwap parTrs = vars.createTRS(type, start, end, useOvernightIndex,
                fairMargin, nominal, gearing, paymentDelay);
        assertEquals("unable to imply a fair margin (type=" + type
                        + ", overnight=" + useOvernightIndex + ", margin=" + margin
                        + ", gearing=" + gearing + ", paymentDelay=" + paymentDelay + ")",
                0.0, parTrs.NPV(), TOL_TIGHT);
    }

    private static void checkNPVCalculation(final VanillaSwap.Type type,
                                            final Date start,
                                            final Date end,
                                            final boolean useOvernightIndex,
                                            final double margin,
                                            final double gearing,
                                            final int paymentDelay) {
        final CommonVars vars = new CommonVars();
        final double nominal = 1.0e7;

        final EquityTotalReturnSwap trs = vars.createTRS(type, start, end, useOvernightIndex,
                margin, nominal, gearing, paymentDelay);

        final double npv = trs.NPV();
        final double scaling = type == VanillaSwap.Type.Receiver ? 1.0 : -1.0;

        final double equityLegNPV = trs.equityLegNPV();
        final double replicatedEquityLegNPV = scaling * legNPVOf(trs.equityLeg(),
                vars.interestHandle);
        assertEquals("incorrect NPV of the equity leg",
                replicatedEquityLegNPV, equityLegNPV, TOL_NPV);

        final double interestLegNPV = trs.interestRateLegNPV();
        final double replicatedInterestLegNPV = -scaling * legNPVOf(trs.interestRateLeg(),
                vars.interestHandle);
        assertEquals("incorrect NPV of the interest leg",
                replicatedInterestLegNPV, interestLegNPV, TOL_NPV);

        assertEquals("summing legs NPV does not replicate the instrument NPV",
                equityLegNPV + interestLegNPV, npv, TOL_NPV);
    }

    @Test
    public void testFairMargin() {
        // TRS vs Libor-type index — only paymentDelay=0 cases (IborLeg lacks
        // withPaymentLag in Java; see class doc).
        checkFairMarginCalculation(VanillaSwap.Type.Receiver,
                new Date(5, Month.January, 2023), new Date(5, Month.April, 2023),
                false, 0.0, 1.0, 0);
        checkFairMarginCalculation(VanillaSwap.Type.Payer,
                new Date(5, Month.January, 2023), new Date(5, Month.April, 2023),
                false, 0.01, 1.0, 0);
        checkFairMarginCalculation(VanillaSwap.Type.Payer,
                new Date(5, Month.January, 2023), new Date(5, Month.April, 2023),
                false, 0.0, 0.0, 0);

        // TRS vs overnight index — exercises full set including paymentDelay=2.
        checkFairMarginCalculation(VanillaSwap.Type.Receiver,
                new Date(5, Month.January, 2023), new Date(5, Month.April, 2023),
                true, 0.0, 1.0, 0);
        checkFairMarginCalculation(VanillaSwap.Type.Payer,
                new Date(5, Month.January, 2023), new Date(5, Month.April, 2023),
                true, 0.01, 1.0, 0);
        checkFairMarginCalculation(VanillaSwap.Type.Receiver,
                new Date(31, Month.January, 2023), new Date(30, Month.April, 2023),
                true, -0.005, 1.0, 2);
    }

    @Test
    public void testErrorWhenNegativeNominal() {
        final CommonVars vars = new CommonVars();
        try {
            vars.createTRS(VanillaSwap.Type.Receiver,
                    new Date(5, Month.January, 2023), new Date(5, Month.April, 2023),
                    false, 0.0, -1.0e7, 1.0, 0);
            fail("expected RuntimeException — negative nominal");
        } catch (final RuntimeException e) {
            assertTrue("unexpected exception message: " + e.getMessage(),
                    e.getMessage() != null && e.getMessage().contains("Nominal cannot be negative"));
        }
    }

    @Test
    public void testErrorWhenNoPaymentCalendar() {
        final CommonVars vars = new CommonVars();
        // Schedule with empty calendar (default Calendar()).
        final Schedule sch = new Schedule(
                new Date(5, Month.January, 2023),
                new Date(5, Month.April, 2023),
                new Period(3, TimeUnit.Months),
                new Calendar(),
                BusinessDayConvention.Unadjusted, BusinessDayConvention.Unadjusted,
                DateGeneration.Rule.Backward, false);
        try {
            vars.createTRS(VanillaSwap.Type.Receiver, sch, false, 0.0, 1.0e7, 1.0, 0);
            fail("expected RuntimeException — empty calendar");
        } catch (final RuntimeException e) {
            assertTrue("unexpected exception message: " + e.getMessage(),
                    e.getMessage() != null
                    && e.getMessage().contains("Calendar in schedule cannot be empty"));
        }
    }

    @Test
    public void testEquityLegNPV() {
        final CommonVars vars = new CommonVars();

        final Date start = new Date(5, Month.January, 2023);
        final Date end = new Date(5, Month.April, 2023);

        final EquityTotalReturnSwap trs = vars.createTRS(VanillaSwap.Type.Receiver, start, end,
                false, 0.0, 1.0e7, 1.0, 0);
        final double actualEquityLegNPV = trs.equityLegNPV();

        final EquityIndex eqIdx = trs.equityIndex();
        final double discount = vars.interestHandle.currentLink().discount(end);
        final double expectedEquityLegNPV =
                (eqIdx.fixing(end) / eqIdx.fixing(start) - 1.0) * trs.nominal() * discount;

        assertEquals("unable to replicate equity leg NPV",
                expectedEquityLegNPV, actualEquityLegNPV, TOL_TIGHT);
    }

    @Test
    public void testTRSNPV() {
        // TRS vs Libor-type index — only paymentDelay=0 cases.
        checkNPVCalculation(VanillaSwap.Type.Receiver,
                new Date(5, Month.January, 2023), new Date(5, Month.April, 2023),
                false, 0.0, 1.0, 0);
        checkNPVCalculation(VanillaSwap.Type.Payer,
                new Date(5, Month.January, 2023), new Date(5, Month.April, 2023),
                false, 0.01, 1.0, 0);
        checkNPVCalculation(VanillaSwap.Type.Payer,
                new Date(5, Month.January, 2023), new Date(5, Month.April, 2023),
                false, 0.0, 0.0, 0);

        // TRS vs overnight index.
        checkNPVCalculation(VanillaSwap.Type.Receiver,
                new Date(5, Month.January, 2023), new Date(5, Month.April, 2023),
                true, 0.0, 1.0, 0);
        checkNPVCalculation(VanillaSwap.Type.Payer,
                new Date(5, Month.January, 2023), new Date(5, Month.April, 2023),
                true, 0.01, 1.0, 0);
        checkNPVCalculation(VanillaSwap.Type.Receiver,
                new Date(31, Month.January, 2023), new Date(30, Month.April, 2023),
                true, -0.005, 1.0, 2);
    }
}
