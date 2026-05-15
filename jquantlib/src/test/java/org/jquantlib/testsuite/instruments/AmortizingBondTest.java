/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.instruments.bonds.AmortizingFixedRateBond;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.NullCalendar;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5d skeleton port of {@code test-suite/amortizingbond.cpp} v1.42.1
 * (285 LOC, 3 cases).
 *
 * <p>Exercises the {@code AmortizingFixedRateBond} instrument — fixed-rate
 * bonds with a notional schedule that decreases over time, including the
 * Brazilian convention (sinkable / amortizing schedule) and draw-down
 * support.
 *
 * <p><strong>All 3 cases deferred to Phase 5d.5</strong> — Java has no
 * {@code AmortizingFixedRateBond} class:
 * <ul>
 *   <li>No {@code AmortizingFixedRateBond} instrument
 *       (C++ {@code ql/instruments/bonds/amortizingfixedratebond.hpp});
 *   <li>No {@code AmortizingPayoff} / amortizing-leg builder helpers
 *       in {@code org.jquantlib.cashflow};
 *   <li>No Brazilian sinkable-bond convention helpers
 *       ({@code BrazilianAmortizingFixedRateBond} test variant);
 *   <li>No draw-down schedule wrapper for partial bond issuance.
 * </ul>
 *
 * <p>Phase 5d.5 carry-forward: the entire amortizing bond family
 * (instrument + leg builder + Brazilian helpers) belongs to a future
 * production-code phase.
 *
 * <p>Source: {@code test-suite/amortizingbond.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class AmortizingBondTest {

    private static final String REASON =
            "Phase 5d.5: AmortizingFixedRateBond now ported (commit d303b8bc); "
          + "test body is `fail(\"not implemented\")` — needs full port from "
          + "C++ amortizingbond.cpp::testAmortizingFixedRateBond.";

    private static final String REASON_BRAZIL =
            "Phase 5d.5: AmortizingFixedRateBond ported; Brazilian sinkable-bond "
          + "schedule helpers still needed; test body is `fail(\"not implemented\")` — "
          + "needs full port + Brazilian helpers.";

    private static final String REASON_DRAW_DOWN =
            "Phase 5d.5: AmortizingFixedRateBond ported; draw-down schedule wrapper "
          + "still needed; test body is `fail(\"not implemented\")` — needs full port + "
          + "draw-down helpers.";

    /**
     * Mirror of C++ {@code testAmortizingFixedRateBond} (amortizingbond.cpp:38-98).
     * For each of 13 coupon rates, builds a 30-year monthly-amortizing bond
     * via {@link AmortizingFixedRateBond#sinkingSchedule} +
     * {@link AmortizingFixedRateBond#sinkingNotionals}, and verifies that
     * each (coupon + principal) sub-period payment equals the Excel-PMT
     * value within 1e-6.
     */
    @Test
    public void testAmortizingFixedRateBond() {
        QL.info("Testing amortizing fixed rate bond...");

        // Generated from Excel using PMT(rate/12, 360, -100).
        final double[] rates = { 0.0, 0.01, 0.02, 0.03, 0.04, 0.05, 0.06,
                0.07, 0.08, 0.09, 0.10, 0.11, 0.12 };
        final double[] amounts = { 0.277777778, 0.321639520, 0.369619473,
                0.421604034, 0.477415295, 0.536821623, 0.599550525,
                0.665302495, 0.733764574, 0.804622617,
                0.877571570, 0.952323396, 1.028612597 };

        final Frequency freq = Frequency.Monthly;
        // Avoid Settings::evaluationDate (which defaults to today and varies
        // across runs); pin a deterministic 1-Jan-2024.
        final Date refDate = new Date(1, Month.January, 2024);
        new Settings().setEvaluationDate(refDate);
        final double tolerance = 1.0e-6;

        for (int i = 0; i < rates.length; ++i) {
            final Schedule schedule = AmortizingFixedRateBond.sinkingSchedule(
                    refDate, new Period(30, TimeUnit.Years), freq, new NullCalendar());
            final double[] notionals = AmortizingFixedRateBond.sinkingNotionals(
                    new Period(30, TimeUnit.Years), freq, rates[i], 100.0);
            final AmortizingFixedRateBond bond = new AmortizingFixedRateBond(
                    0, notionals, schedule, new double[]{ rates[i] },
                    new ActualActual(ActualActual.Convention.ISMA));

            final Leg cashflows = bond.cashflows();
            // Each sinking period contributes 2 cashflows (coupon + principal).
            for (int k = 0; k < cashflows.size() / 2; ++k) {
                final CashFlow couponCf = cashflows.get(2 * k);
                final CashFlow principalCf = cashflows.get(2 * k + 1);
                final double coupon = couponCf.amount();
                final double principal = principalCf.amount();
                final double totalAmount = coupon + principal;

                if (Math.abs(totalAmount - amounts[i]) > tolerance) {
                    fail("Rate=" + rates[i] + " period=" + k
                            + ": expected total amount=" + amounts[i]
                            + " calculated=" + totalAmount
                            + " diff=" + Math.abs(totalAmount - amounts[i]));
                }
                final double expectedCoupon = notionals[k] * rates[i] / freq.toInteger();
                if (Math.abs(coupon - expectedCoupon) > tolerance) {
                    fail("Rate=" + rates[i] + " period=" + k
                            + ": expected coupon=" + expectedCoupon
                            + " calculated=" + coupon
                            + " diff=" + Math.abs(coupon - expectedCoupon));
                }
            }
        }
    }

    @Ignore(REASON_BRAZIL)
    @Test
    public void testBrazilianAmortizingFixedRateBond() { fail("not implemented"); }

    @Ignore(REASON_DRAW_DOWN)
    @Test
    public void testAmortizingFixedRateBondWithDrawDown() { fail("not implemented"); }
}
