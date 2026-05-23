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

/**
 * Phase 5d skeleton port of {@code test-suite/amortizingbond.cpp} v1.42.1
 * (285 LOC, 3 cases).
 *
 * <p>Exercises the {@code AmortizingFixedRateBond} instrument — fixed-rate
 * bonds with a notional schedule that decreases over time, including the
 * Brazilian convention (sinkable / amortizing schedule) and draw-down
 * support.
 *
 * <p>Phase 5e.5b-CFC-d-197 — body-fills both deferred cases. Both reduce
 * to a generic {@link Bond} built on a {@link FixedRateLeg} with a
 * per-period notional vector, so no new instrument is required; the
 * existing {@link Bond#addRedemptionsToCashflows()} produces a redemption
 * cash flow on every notional step (positive ⇒ amortization, negative ⇒
 * draw-down) and the Brazilian variant just needs the existing
 * {@link Brazil} settlement calendar with {@link Business252} day-count.
 *
 * <p>Source: {@code test-suite/amortizingbond.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class AmortizingBondTest {

    private static final String REASON =
            "Phase 5d.5: AmortizingFixedRateBond now ported (commit d303b8bc); "
          + "test body is `fail(\"not implemented\")` — needs full port from "
          + "C++ amortizingbond.cpp::testAmortizingFixedRateBond.";

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

    /**
     * Mirror of C++ {@code testBrazilianAmortizingFixedRateBond}
     * (amortizingbond.cpp:100-221). Builds a 60-period Brazilian corporate
     * bond (SND code RISF11, ISIN BRRISFDBS005) with a per-period
     * amortizing notional vector, then verifies each coupon and each
     * amortization against the issuer's published schedule (within 1e-6).
     *
     * <p>Brazilian convention: {@link Brazil} settlement calendar with
     * {@link Business252} day-count, compounded annually at 6.75%.
     */
    @Test
    public void testBrazilianAmortizingFixedRateBond() {
        QL.info("Testing Brazilian amortizing fixed rate bond...");

        final double[] notionals = {
                1000.0,       983.33300000, 966.66648898, 950.00019204,
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
                // data changed as source (pentagonotrustee.com.br) does not include
                // newly introduced "Black Awareness Day" holiday
                0.31177086,
                            0.27290565, 0.19062560, 0.08662552
        };

        final int settlementDays = 0;
        final Date issueDate = new Date(2, Month.March, 2020);
        final Date maturityDate = new Date(2, Month.March, 2025);

        final Schedule schedule = new Schedule(
                issueDate, maturityDate,
                new Period(Frequency.Monthly),
                new Brazil(Brazil.Market.SETTLEMENT),
                BusinessDayConvention.Unadjusted,
                BusinessDayConvention.Unadjusted,
                DateGeneration.Rule.Backward, false);

        final InterestRate[] couponRates = new InterestRate[] {
                new InterestRate(0.0675,
                        new Business252(new Brazil()),
                        Compounding.Compounded, Frequency.Annual)
        };

        final Leg coupons = new FixedRateLeg(schedule, couponRates[0].dayCounter())
                .withNotionals(notionals)
                .withCouponRates(couponRates)
                .withPaymentAdjustment(BusinessDayConvention.Following)
                .Leg();

        final Bond risf11 = new Bond(settlementDays, schedule.calendar(),
                                     issueDate, coupons);

        final double tolerance = 1.0e-6;
        final Leg cashflows = risf11.cashflows();
        for (int k = 0; k < cashflows.size() / 2; ++k) {
            final double calculatedCoupon = cashflows.get(2 * k).amount();
            final double calculatedAmort  = cashflows.get(2 * k + 1).amount();

            if (Math.abs(expectedCoupons[k] - calculatedCoupon) > tolerance) {
                fail("k=" + k + ": expected coupon=" + expectedCoupons[k]
                        + " calculated=" + calculatedCoupon
                        + " diff=" + Math.abs(expectedCoupons[k] - calculatedCoupon));
            }
            if (Math.abs(expectedAmortizations[k] - calculatedAmort) > tolerance) {
                fail("k=" + k + ": expected amortization=" + expectedAmortizations[k]
                        + " calculated=" + calculatedAmort
                        + " diff=" + Math.abs(expectedAmortizations[k] - calculatedAmort));
            }
        }
    }

    /**
     * Mirror of C++ {@code testAmortizingFixedRateBondWithDrawDown}
     * (amortizingbond.cpp:223-281). Bond whose nominal schedule both grows
     * (draw-down) and shrinks (amortization); verifies the per-step
     * redemption cash flows produced by {@link Bond#addRedemptionsToCashflows()}
     * equal {@code nominals[i-1] - nominals[i]} (negative for a draw-down).
     */
    @Test
    public void testAmortizingFixedRateBondWithDrawDown() {
        QL.info("Testing amortizing fixed rate bond with draw-down...");

        final Date issueDate = new Date(19, Month.May, 2012);
        final Date maturityDate = new Date(25, Month.May, 2017);
        final Calendar calendar = new UnitedStates(UnitedStates.Market.GOVERNMENTBOND);
        final int settlementDays = 3;

        final Schedule schedule = new Schedule(
                issueDate, maturityDate,
                new Period(Frequency.Semiannual), calendar,
                BusinessDayConvention.Unadjusted,
                BusinessDayConvention.Unadjusted,
                DateGeneration.Rule.Backward, false);

        final double[] nominals = { 100.0, 100.0, 100.5, 100.5, 101.5, 101.5,
                                    90.0, 80.0, 70.0, 60.0 };
        final double[] rates = { 0.042 };

        final Leg leg = new FixedRateLeg(schedule, new Actual360())
                .withNotionals(nominals)
                .withCouponRates(rates)
                .withPaymentAdjustment(BusinessDayConvention.Unadjusted)
                .withPaymentCalendar(calendar)
                .Leg();

        final Bond bond = new Bond(settlementDays, calendar, issueDate, leg);

        final Leg cfs = bond.cashflows();
        final double tolerance = 1.0e-8;

        // first draw-down: cfs[2] = nominals[1] - nominals[2] = -0.5
        double calculated = cfs.get(2).amount();
        double expected = nominals[1] - nominals[2];
        if (Math.abs(calculated - expected) > tolerance) {
            fail("Failed to calculate first draw down: expected=" + expected
                    + " calculated=" + calculated);
        }

        // second draw-down: cfs[5] = nominals[3] - nominals[4] = -1.0
        calculated = cfs.get(5).amount();
        expected = nominals[3] - nominals[4];
        if (Math.abs(calculated - expected) > tolerance) {
            fail("Failed to calculate second draw down: expected=" + expected
                    + " calculated=" + calculated);
        }

        // first amortization: cfs[8] = nominals[5] - nominals[6] = 11.5
        calculated = cfs.get(8).amount();
        expected = nominals[5] - nominals[6];
        if (Math.abs(calculated - expected) > tolerance) {
            fail("Failed to calculate first amortization: expected=" + expected
                    + " calculated=" + calculated);
        }
    }
}
