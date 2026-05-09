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

/*
 Copyright (C) 2014 Cheng Li

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.testsuite.instruments.bonds;

import org.jquantlib.Settings;
import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.Coupon;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.instruments.bonds.AmortizingFixedRateBond;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.NullCalendar;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Faithful port of {@code migration-harness/cpp/quantlib/test-suite/amortizingbond.cpp}
 * (QuantLib v1.42.1). Phase 5d.5-Bonds — un-blocks the AmortizingBondTest skeleton
 * landed in Phase 5d.
 *
 * <p>Per the binding rigor directive (2026-05-08) every C++
 * {@code BOOST_AUTO_TEST_CASE} is mirrored as a {@code @Test public void}
 * method with the same name. Tests that exercise constructors or production
 * paths the Java port does not yet provide are marked {@code @Ignore} with
 * a documented Phase 5d.5-Bonds-b follow-up reason.
 *
 * <p>Reference: {@code migration-harness/references/instruments/amortizing_fixed_rate_bond.json}
 * (probe {@code amortizing-bond/amortizing_fixed_rate_bond_probe.cpp}).
 */
public class AmortizingFixedRateBondTest {

    @Test
    public void testAmortizingFixedRateBond() {
        // Mirror amortizingbond.cpp:38 — Excel-derived monthly payment per rate.
        final double[] rates = {
                0.0, 0.01, 0.02, 0.03, 0.04, 0.05, 0.06, 0.07,
                0.08, 0.09, 0.10, 0.11, 0.12
        };
        final double[] amounts = {
                0.277777778, 0.321639520, 0.369619473, 0.421604034,
                0.477415295, 0.536821623, 0.599550525,
                0.665302495, 0.733764574, 0.804622617,
                0.877571570, 0.952323396, 1.028612597
        };
        final Frequency freq = Frequency.Monthly;
        final Date refDate = new Settings().evaluationDate();
        final double tolerance = 1.0e-6;

        for (int i = 0; i < rates.length; ++i) {
            final Schedule schedule = AmortizingFixedRateBond.sinkingSchedule(
                    refDate, new Period(30, TimeUnit.Years), freq, new NullCalendar());
            final double[] notionals = AmortizingFixedRateBond.sinkingNotionals(
                    new Period(30, TimeUnit.Years), freq, rates[i], 100.0);
            final AmortizingFixedRateBond bond = new AmortizingFixedRateBond(
                    0, notionals, schedule, new double[] { rates[i] },
                    new ActualActual(ActualActual.Convention.ISMA));

            // Identify (coupon, principal) pairs by class because the Java
            // EarlierThanCashFlowComparator is unstable on equal dates
            // (returns -1 for both <= cases, breaking the sort's stability
            // contract). We pair by coupon-period index instead of relying
            // on the cashflows index parity that C++'s std::stable_sort
            // guarantees.
            final Leg cfs = bond.cashflows();
            int couponIdx = 0;
            int totalCoupons = 0;
            for (int j = 0; j < cfs.size(); ++j) {
                if (cfs.get(j) instanceof Coupon) totalCoupons++;
            }
            int j = 0;
            while (couponIdx < totalCoupons) {
                // pair = adjacent (Coupon, SimpleCashFlow) on the same date,
                // any order — locate them.
                Coupon cp = null;
                CashFlow principal = null;
                while (j < cfs.size() && (cp == null || principal == null)) {
                    final CashFlow cf = (CashFlow) cfs.get(j);
                    if (cp == null && cf instanceof Coupon) cp = (Coupon) cf;
                    else if (principal == null && !(cf instanceof Coupon)) principal = cf;
                    ++j;
                    // Stop after consuming the pair (both classes captured at
                    // adjacent indices) to keep alignment in case of multiple
                    // cashflows on the same date.
                    if (cp != null && principal != null) break;
                }
                if (cp == null) break;
                final double coupon = cp.amount();
                final double principalAmount = principal != null ? principal.amount() : 0.0;
                final double total = coupon + principalAmount;

                if (Math.abs(total - amounts[i]) > tolerance) {
                    fail("rate=" + rates[i] + " k=" + couponIdx
                            + " expected total=" + amounts[i]
                            + " calculated=" + total);
                }
                final double expectedCoupon = notionals[couponIdx] * rates[i] / freq.toInteger();
                if (Math.abs(coupon - expectedCoupon) > tolerance) {
                    fail("rate=" + rates[i] + " k=" + couponIdx
                            + " expected coupon=" + expectedCoupon
                            + " calculated=" + coupon);
                }
                ++couponIdx;
            }
            assertEquals("coupon count for rate=" + rates[i],
                    notionals.length - 1, couponIdx);
        }
    }

    /**
     * Reproduces {@code amortizingbond.cpp:100} — Brazilian onshore corporate
     * bond. Requires {@code Business252} day counter wired to the Brazilian
     * holiday calendar, plus per-coupon {@link org.jquantlib.termstructures.InterestRate}
     * overloads on {@link org.jquantlib.cashflow.FixedRateLeg} that accept a
     * {@code Compounded/Annual} rate. Java {@code Business252} ignores the
     * {@code Calendar} ctor argument as of Phase 5d, and the FixedRateLeg
     * builder does not yet compose with the Brazilian holiday calendar; both
     * are deferred to Phase 5d.5-Bonds-b.
     */
    @Ignore("Phase 5d.5-Bonds-b: needs Business252(Brazil) wiring + "
            + "FixedRateLeg.withCouponRates(InterestRate) Brazil holiday composition")
    @Test
    public void testBrazilianAmortizingFixedRateBond() {
        fail("not yet implemented — see @Ignore reason (Phase 5d.5-Bonds-b)");
    }

    /**
     * Reproduces {@code amortizingbond.cpp:223} — draw-down + amortization.
     * Requires {@code FixedRateLeg.withPaymentCalendar(Calendar)} which
     * Java does not yet expose. Once added, the assertion structure
     * trivially mirrors the C++ test (notional differences = cashflow
     * amounts at indices 2/5/8). Deferred to Phase 5d.5-Bonds-b.
     */
    @Ignore("Phase 5d.5-Bonds-b: FixedRateLeg.withPaymentCalendar(Calendar) "
            + "missing on the Java side")
    @Test
    public void testAmortizingFixedRateBondWithDrawDown() {
        fail("not yet implemented — see @Ignore reason (Phase 5d.5-Bonds-b)");
    }
}
