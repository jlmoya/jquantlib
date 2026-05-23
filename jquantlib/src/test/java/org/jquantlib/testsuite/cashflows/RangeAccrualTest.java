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

package org.jquantlib.testsuite.cashflows;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.cashflow.RangeAccrualFloatersCoupon;
import org.jquantlib.cashflow.RangeAccrualPricer;
import org.jquantlib.cashflow.RangeAccrualPricerByBgm;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.Euribor6M;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.FlatSmileSection;
import org.jquantlib.termstructures.volatilities.SmileSection;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/rangeaccrual.cpp (Phase 5e.5b-CFC-d-28).
 *
 * <p>3 BOOST_AUTO_TEST_CASE methods exercising
 * {@code RangeAccrualFloatersCoupon} pricing through the
 * {@code RangeAccrualPricerByBgm} (Brace-Gatarek-Musiela) pricer.
 *
 * <p>Body-fill rationale: production code
 * (RangeAccrualFloatersCoupon + RangeAccrualPricerByBgm + RangeAccrualLeg)
 * landed in commit efa0330a. C++ test setup uses a hard-coded zero curve
 * (46 dates) and InterpolatedSmileSection with ~1000 stdDev points; we
 * substitute a flat-forward curve and a single FlatSmileSection. The
 * three invariants under test (rate convergence on infinite range,
 * monotonicity in lower strike, monotonicity in upper strike) are
 * properties of the BGM pricer and hold regardless of yield-curve /
 * smile-section shape, matching C++ rangeaccrual.cpp:584-744.
 */
public class RangeAccrualTest {

    public RangeAccrualTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    /** Common-vars fixture mirroring C++ CommonVars (rangeaccrual.cpp:47-578). */
    private static final class Vars {
        final Calendar calendar = new Target();
        final DayCounter dayCounter = new Actual365Fixed();
        final Date today = new Date(6, Month.March, 2007);
        final Handle<YieldTermStructure> termStructure;
        final IborIndex iborIndex;
        final Date startDate = new Date(6, Month.March, 2017);
        final Date endDate = new Date(6, Month.September, 2017);
        final Date paymentDate = endDate;
        final int fixingDays = 2;
        final DayCounter rangeCouponDayCount;
        final Schedule observationSchedule;
        final double flatVol = 0.1;
        final double infiniteLowerStrike = 1.0e-9;
        final double infiniteUpperStrike = 1.0;
        final double gearing = 1.0;
        final double spread = 0.0;
        final double correlation = 1.0;
        final SmileSection smileOnExpiry;
        final SmileSection smileOnPayment;
        final boolean[] byCallSpread = new boolean[]{true, false};
        // C++ rangeaccrual.cpp:575 — rateTolerance = 2.0e-8.
        final double rateTolerance = 2.0e-8;

        Vars() {
            new Settings().setEvaluationDate(today);
            // Flat-forward 3% — simpler than C++ ZeroCurve(46 dates).
            // Invariants under test are curve-shape-independent.
            termStructure = new Handle<YieldTermStructure>(
                new FlatForward(today, 0.03, dayCounter,
                                Compounding.Continuous, Frequency.Annual));
            iborIndex = new Euribor6M(termStructure);
            rangeCouponDayCount = iborIndex.dayCounter();

            // Observation schedule — daily, ModifiedFollowing.
            observationSchedule = new Schedule(
                startDate, endDate,
                new Period(Frequency.Daily), calendar,
                BusinessDayConvention.ModifiedFollowing,
                BusinessDayConvention.ModifiedFollowing,
                DateGeneration.Rule.Forward, false);

            // Flat smile-section at flatVol on both expiry (startDate) and
            // payment (endDate). C++ uses Flat + Interpolated; we use Flat
            // only (Interpolated requires 1000-point stdDev vectors).
            smileOnExpiry = new FlatSmileSection(
                startDate, flatVol, rangeCouponDayCount, today);
            smileOnPayment = new FlatSmileSection(
                endDate, flatVol, rangeCouponDayCount, today);
        }

        RangeAccrualFloatersCoupon makeCoupon(final double lower, final double upper) {
            return new RangeAccrualFloatersCoupon(
                paymentDate, 1.0, iborIndex,
                startDate, endDate,
                fixingDays, rangeCouponDayCount,
                gearing, spread,
                startDate, endDate,
                observationSchedule,
                lower, upper);
        }
    }

    /**
     * As the strike range expands to (-inf, +inf) the range-accrual coupon
     * rate should converge to the plain ibor-coupon fixing. Mirrors C++
     * rangeaccrual.cpp:584-634.
     */
    @Test
    public void testInfiniteRange() {
        QL.info("Testing infinite range accrual floaters...");
        final Vars vars = new Vars();

        final RangeAccrualFloatersCoupon coupon = vars.makeCoupon(
            vars.infiniteLowerStrike, vars.infiniteUpperStrike);
        final Date fixingDate = coupon.fixingDate();

        for (final boolean cs : vars.byCallSpread) {
            final RangeAccrualPricer bgmPricer = new RangeAccrualPricerByBgm(
                vars.correlation,
                vars.smileOnExpiry, vars.smileOnPayment,
                true /* withSmile */, cs /* byCallSpread */);
            coupon.setPricer(bgmPricer);

            final double rate = coupon.rate();
            final double indexFixing = vars.iborIndex.fixing(fixingDate);
            final double difference = rate - indexFixing;

            if (Math.abs(difference) > vars.rateTolerance) {
                fail("\nbyCallSpread:\t" + cs
                   + "\nfixingDate:\t" + fixingDate
                   + "\nstartDate:\t" + vars.startDate
                   + "\nrange accrual rate:\t" + rate
                   + "\nindex fixing:\t" + indexFixing
                   + "\ndifference:\t" + difference
                   + "\ntolerance: \t" + vars.rateTolerance);
            }
        }
        // Sanity: rate ~ index fixing — keep an assertion visible.
        assertEquals(vars.iborIndex.fixing(fixingDate), coupon.rate(),
                     vars.rateTolerance);
    }

    /**
     * Range-accrual coupon price is monotonically non-increasing as the
     * lower strike sweeps upward (range shrinks from below). Mirrors C++
     * rangeaccrual.cpp:636-689.
     */
    @Test
    public void testPriceMonotonicityWithRespectToLowerStrike() {
        QL.info("Testing price monotonicity with respect to the lower strike...");
        final Vars vars = new Vars();

        for (final boolean cs : vars.byCallSpread) {
            final RangeAccrualPricer bgmPricer = new RangeAccrualPricerByBgm(
                vars.correlation,
                vars.smileOnExpiry, vars.smileOnPayment,
                true /* withSmile */, cs /* byCallSpread */);

            double previousPrice = 100.0;
            for (int k = 1; k < 100; k++) {
                final double lower = 0.005 + k * 0.001;
                final RangeAccrualFloatersCoupon coupon =
                    vars.makeCoupon(lower, vars.infiniteUpperStrike);
                coupon.setPricer(bgmPricer);
                final double price = coupon.price(vars.termStructure);

                if (previousPrice <= price) {
                    fail("\nbyCallSpread:\t" + cs
                       + "\nk:\t" + k
                       + "\nPrice at lower strike " + (lower - 0.001) + ": " + previousPrice
                       + "\nPrice at lower strike " + lower + ": " + price);
                }
                previousPrice = price;
            }
        }
    }

    /**
     * Range-accrual coupon price is monotonically non-decreasing as the
     * upper strike sweeps upward (range grows from above). Mirrors C++
     * rangeaccrual.cpp:691-744.
     */
    @Test
    public void testPriceMonotonicityWithRespectToUpperStrike() {
        QL.info("Testing price monotonicity with respect to the upper strike...");
        final Vars vars = new Vars();

        for (final boolean cs : vars.byCallSpread) {
            final RangeAccrualPricer bgmPricer = new RangeAccrualPricerByBgm(
                vars.correlation,
                vars.smileOnExpiry, vars.smileOnPayment,
                true /* withSmile */, cs /* byCallSpread */);

            double previousPrice = 0.0;
            for (int k = 1; k < 95; k++) {
                final double upper = 0.006 + k * 0.001;
                final RangeAccrualFloatersCoupon coupon =
                    vars.makeCoupon(0.004, upper);
                coupon.setPricer(bgmPricer);
                final double price = coupon.price(vars.termStructure);

                if (previousPrice > price) {
                    fail("\nbyCallSpread:\t" + cs
                       + "\nk:\t" + k
                       + "\nPrice at upper strike " + (upper - 0.001) + ": " + previousPrice
                       + "\nPrice at upper strike " + upper + ": " + price);
                }
                previousPrice = price;
            }
        }
    }
}
