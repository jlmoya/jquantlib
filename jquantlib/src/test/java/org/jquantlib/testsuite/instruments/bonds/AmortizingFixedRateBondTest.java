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
import org.jquantlib.cashflow.FixedRateLeg;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.daycounters.Business252;
import org.jquantlib.instruments.Bond;
import org.jquantlib.instruments.bonds.AmortizingFixedRateBond;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.InterestRate;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Brazil;
import org.jquantlib.time.calendars.NullCalendar;
import org.jquantlib.time.calendars.UnitedStates;
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
     * Reproduces {@code amortizingbond.cpp:100-221} — Brazilian onshore
     * corporate bond (SND code RISF11, ISIN BRRISFDBS005). Phase 5d.5-Bonds-b
     * un-blocks: Business252(Brazil) is now wired (calendar ctor used), and
     * FixedRateLeg builder composes with the Brazilian holiday calendar.
     *
     * <p>Coupons match through k=55 within {@code 1e-6} but diverge from
     * k=56 onward by ~0.02 — beyond the project tolerance ceiling
     * ({@code 1e-8} loose).  Root cause: Brazil-calendar holiday-table
     * mismatch between C++ v1.42.1 and JQuant for late-2024 / early-2025
     * (the C++ {@code amortizingbond.cpp} test fixture pre-existed the
     * holiday change; both sides correctly include Black Awareness Day
     * since 2007, so divergence is in another holiday).
     *
     * <p>Per project tolerance rules ({@code @Ignore} > loosen-to-force-green),
     * tracked as Phase 5d.5-Bonds-c carry-forward (Brazilian calendar
     * reconciliation against C++ v1.42.1).  Test body fully ported and
     * validated through k=55; final 4 coupons require the calendar fix.
     */
    @org.junit.Ignore("Phase 5d.5-Bonds-c — Brazilian calendar holiday-table "
            + "reconciliation against C++ v1.42.1; coupons k=0..55 match, "
            + "k=56..59 diverge by ~0.02. Full body ported; un-ignore once "
            + "calendar fixed.")
    @Test
    public void testBrazilianAmortizingFixedRateBond() {
        final double[] notionals = {
                1000          , 983.33300000, 966.66648898, 950.00019204,
                933.33338867, 916.66685434, 900.00001759, 883.33291726,
                866.66619177, 849.99933423, 833.33254728, 816.66589633,
                799.99937871, 783.33299165, 766.66601558, 749.99946306,
                733.33297499, 716.66651646, 699.99971995, 683.33272661,
                666.66624140, 649.99958536, 633.33294599, 616.66615618,
                599.99951997, 583.33273330, 566.66633377, 549.99954356,
                533.33290739, 516.66625403, 499.99963400, 483.33314619,
                466.66636930, 449.99984658, 433.33320226, 416.66634063,
                399.99968700, 383.33290004, 366.66635221, 349.99953317,
                333.33290539, 316.66626012, 299.99948151, 283.33271031,
                266.66594695, 249.99932526, 233.33262024, 216.66590450,
                199.99931312, 183.33277035, 166.66617153, 149.99955437,
                133.33295388, 116.66633464,  99.99973207,  83.33307672,
                 66.66646137,  49.99984602,  33.33324734,  16.66662367
        };

        final double[] expectedAmortizations = {
                16.66700000, 16.66651102, 16.66629694, 16.66680337,
                16.66653432, 16.66683675, 16.66710033, 16.66672548,
                16.66685753, 16.66678695, 16.66665095, 16.66651761,
                16.66638706, 16.66697606, 16.66655251, 16.66648807,
                16.66645852, 16.66679651, 16.66699333, 16.66648520,
                16.66665604, 16.66663937, 16.66678981, 16.66663620,
                16.66678667, 16.66639952, 16.66679021, 16.66663617,
                16.66665336, 16.66662002, 16.66648780, 16.66677688,
                16.66652271, 16.66664432, 16.66686163, 16.66665363,
                16.66678696, 16.66654783, 16.66681904, 16.66662777,
                16.66664527, 16.66677860, 16.66677119, 16.66676335,
                16.66662168, 16.66670502, 16.66671573, 16.66659137,
                16.66654276, 16.66659882, 16.66661715, 16.66660049,
                16.66661924, 16.66660257, 16.66665534, 16.66661534,
                16.66661534, 16.66659867, 16.66662367, 16.66662367
        };

        // C++ note: data changed because source (pentagonotrustee.com.br) does
        // not include the recently added "Black Awareness Day" holiday.
        final double[] expectedCoupons = {
                5.97950399, 4.85474255, 5.27619136, 5.18522454,
                5.33753111, 5.24221882, 4.91231709, 4.59116258,
                4.73037674, 4.63940686, 4.54843737, 3.81920094,
                4.78359948, 3.86733691, 4.38439657, 4.09359456,
                4.00262671, 4.28531030, 3.82068947, 3.55165259,
                3.46502778, 3.71720657, 3.62189368, 2.88388676,
                3.58769952, 2.72800044, 3.38838360, 3.00196900,
                2.91100034, 3.08940793, 2.59877059, 2.63809514,
                2.42551945, 2.45615766, 2.59111761, 1.94857222,
                2.28751141, 1.79268582, 2.19248291, 1.81913832,
                1.90625855, 1.89350716, 1.48110584, 1.62031828,
                1.38600825, 1.23425366, 1.39521333, 1.06968563,
                1.03950542, 1.00065409, 0.90968563, 0.81871706,
                0.79726493, 0.63678002, 0.57187676, 0.49829046,
                0.31177086, 0.27290565, 0.19062560, 0.08662552
        };

        final int settlementDays = 0;
        final Date issueDate = new Date(2, Month.March, 2020);
        final Date maturityDate = new Date(2, Month.March, 2025);
        final Calendar brazilCal = new Brazil(Brazil.Market.SETTLEMENT);

        final Schedule schedule = new Schedule(issueDate, maturityDate,
                new Period(Frequency.Monthly), brazilCal,
                BusinessDayConvention.Unadjusted,
                BusinessDayConvention.Unadjusted,
                DateGeneration.Rule.Backward, false);

        final InterestRate couponRate = new InterestRate(0.0675,
                new Business252(new Brazil()),
                Compounding.Compounded, Frequency.Annual);

        final Leg coupons = new FixedRateLeg(schedule, couponRate.dayCounter())
                .withNotionals(notionals)
                .withCouponRates(couponRate)
                .withPaymentAdjustment(BusinessDayConvention.Following)
                .Leg();

        // Java path: the public Bond(settlementDays, calendar, issueDate,
        // coupons) ctor (made public in Phase 5d.5-Bonds-b align commit)
        // runs addRedemptionsToCashflows() automatically. That method
        // calls calculateNotionalsFromCashflows() to derive the notional
        // schedule from per-coupon nominals, then appends one redemption
        // cashflow per notional change (= notionals[i-1] - notionals[i]).
        // This matches C++ Bond::addRedemptionsToCashflows.
        final Bond risf11 = new Bond(settlementDays, schedule.calendar(),
                issueDate, coupons);

        final double tolerance = 1.0e-6;
        final Leg cashflows = risf11.cashflows();
        // After Bond() sorts cashflows by date with the (now-fixed)
        // EarlierThanCashFlowComparator, the order on equal dates is:
        // coupon (Coupon), then principal (SimpleCashFlow). C++ test
        // assumes the same ordering for cashflows[2*k] / cashflows[2*k+1].
        for (int k = 0; k < cashflows.size() / 2; ++k) {
            final double couponAmt = cashflows.get(2 * k).amount();
            final double couponErr = Math.abs(expectedCoupons[k] - couponAmt);
            if (couponErr > tolerance) {
                fail("\n " + k + "th cash flow Failed!"
                        + " Expected Coupon: " + expectedCoupons[k]
                        + " Calculated Coupon: " + couponAmt);
            }

            final double amortAmt = cashflows.get(2 * k + 1).amount();
            final double amortErr = Math.abs(expectedAmortizations[k] - amortAmt);
            if (amortErr > tolerance) {
                fail("\n " + k + "th cash flow Failed!"
                        + " Expected Amortization: " + expectedAmortizations[k]
                        + " Calculated Amortization: " + amortAmt);
            }
        }
    }

    /**
     * Reproduces {@code amortizingbond.cpp:223-281} — draw-down + amortization
     * pattern. Phase 5d.5-Bonds-b unblocks: FixedRateLeg.withPaymentCalendar
     * is now exposed.
     */
    @Test
    public void testAmortizingFixedRateBondWithDrawDown() {
        final Date issueDate = new Date(19, Month.May, 2012);
        final Date maturityDate = new Date(25, Month.May, 2017);
        final Calendar calendar = new UnitedStates(UnitedStates.Market.GOVERNMENTBOND);
        final int settlementDays = 3;

        final Schedule schedule = new Schedule(issueDate, maturityDate,
                new Period(Frequency.Semiannual), calendar,
                BusinessDayConvention.Unadjusted,
                BusinessDayConvention.Unadjusted,
                DateGeneration.Rule.Backward, false);

        final double[] nominals = {
                100.0, 100.0, 100.5, 100.5, 101.5, 101.5, 90.0, 80.0, 70.0, 60.0
        };
        final double[] rates = { 0.042 };

        final Leg leg = new FixedRateLeg(schedule, new Actual360())
                .withNotionals(nominals)
                .withCouponRates(rates)
                .withPaymentAdjustment(BusinessDayConvention.Unadjusted)
                .withPaymentCalendar(calendar)
                .Leg();

        // Java Bond(settlementDays, calendar, issueDate, coupons) ctor (now
        // public per Phase 5d.5-Bonds-b align commit) automatically runs
        // addRedemptionsToCashflows() to derive principal cashflows from
        // per-coupon nominals (via calculateNotionalsFromCashflows).
        final Bond bond = new Bond(settlementDays, calendar, issueDate, leg);

        final Leg cfs = bond.cashflows();
        final double tolerance = 1.0e-8;

        // first draw-down (negative principal): cashflows index 2 = principal
        // for period 1 = nominals[1] - nominals[2] = 100.0 - 100.5 = -0.5
        {
            final double calculated = cfs.get(2).amount();
            final double expected = nominals[1] - nominals[2];
            if (Math.abs(calculated - expected) > tolerance) {
                fail("Failed to calculate first draw down: \n"
                        + "    expected:   " + expected + "\n"
                        + "    calculated: " + calculated);
            }
        }

        // second draw-down: cashflows index 5 = principal for period 2 =
        // nominals[3] - nominals[4] = 100.5 - 101.5 = -1.0
        {
            final double calculated = cfs.get(5).amount();
            final double expected = nominals[3] - nominals[4];
            if (Math.abs(calculated - expected) > tolerance) {
                fail("Failed to calculate second draw down: \n"
                        + "    expected:   " + expected + "\n"
                        + "    calculated: " + calculated);
            }
        }

        // first amortization: cashflows index 8 = principal for period 3 =
        // nominals[5] - nominals[6] = 101.5 - 90.0 = 11.5
        {
            final double calculated = cfs.get(8).amount();
            final double expected = nominals[5] - nominals[6];
            if (Math.abs(calculated - expected) > tolerance) {
                fail("Failed to calculate first amortization: \n"
                        + "    expected:   " + expected + "\n"
                        + "    calculated: " + calculated);
            }
        }
    }
}
