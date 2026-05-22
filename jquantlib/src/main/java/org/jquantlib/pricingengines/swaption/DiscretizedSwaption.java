/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2001, 2002, 2003 Sadruddin Rejeb
 Copyright (C) 2004, 2007 StatPro Italia srl
 Copyright (C) 2021, 2022 Ralf Konrad Eckel

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */
package org.jquantlib.pricingengines.swaption;

import org.jquantlib.cashflow.FixedRateCoupon;
import org.jquantlib.cashflow.FloatingRateCoupon;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.DiscretizedOption;
import org.jquantlib.instruments.Swaption;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.pricingengines.swap.DiscretizedSwap;
import org.jquantlib.pricingengines.swap.DiscretizedSwap.CouponAdjustment;
import org.jquantlib.time.Date;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Discretized swaption helper for tree-based pricing.
 * <p>
 * Port of C++ v1.42.1 {@code ql/pricingengines/swaption/discretizedswaption.{hpp,cpp}}.
 * <p>
 * Mirrors the C++ "date snapping" pre-processing step which re-aligns coupon reset/pay dates within ±7 days of any
 * exercise date so that the time vectors line up cleanly on the lattice grid (avoids mispricing).
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 * <li>C++ {@code prepareSwaptionWithSnappedDates} rebuilds a brand-new
 *     {@link VanillaSwap} on snapped {@link org.jquantlib.time.Schedule}s and
 *     hands its arguments to {@link DiscretizedSwap}. Java's
 *     {@code Schedule(List<Date>)} ctor doesn't carry tenor / DateGeneration
 *     metadata, so {@code FixedRateLeg.isRegular()} fails. The Java port
 *     therefore keeps the original swap and passes the snapped reset dates
 *     directly to {@link DiscretizedSwap} via the optional override
 *     constructor — equivalent net effect for the lattice pricing.
 * <li>{@code DiscretizedSwap} reads the underlying swap from
 *     {@link Swaption.ArgumentsImpl#swap} (Java's {@code Swaption.arguments}
 *     does not propagate the leg-level fields because
 *     {@code VanillaSwap.ArgumentsImpl} is non-static — see
 *     {@code Swaption.java} class-level note).
 * </ul>
 */
public class DiscretizedSwaption extends DiscretizedOption {

    private final double lastPayment_;

    public DiscretizedSwaption(final Swaption.ArgumentsImpl args, final Date referenceDate,
            final DayCounter dayCounter) {
        super(buildSnappedSwap(args, referenceDate, dayCounter), args.exercise.type(),
                buildExerciseTimes(args.exercise, referenceDate, dayCounter));
        // Compute the swaption's terminal payment time from the underlying
        // swap (snapping doesn't move the last pay date).
        final List< Date > fixedDates = args.swap.fixedSchedule().dates();
        final List< Date > floatDates = args.swap.floatingSchedule().dates();
        final double lastFixed = dayCounter.yearFraction(referenceDate, fixedDates.get(fixedDates.size() - 1));
        final double lastFloat = dayCounter.yearFraction(referenceDate, floatDates.get(floatDates.size() - 1));
        this.lastPayment_ = Math.max(lastFixed, lastFloat);
    }

    private static Array buildExerciseTimes(final Exercise exercise, final Date referenceDate, final DayCounter dc) {
        final int n = exercise.dates().size();
        final Array out = new Array(n);
        for ( int i = 0; i < n; i++ ) {
            out.set(i, dc.yearFraction(referenceDate, exercise.date(i)));
        }
        return out;
    }

    /**
     * Mirrors C++ {@code prepareSwaptionWithSnappedDates}: collect the unadjusted coupon dates, snap any within ±7 days
     * of an exercise date to the exercise date itself, and pass the snapped reset dates to {@link DiscretizedSwap}.
     */
    private static DiscretizedSwap buildSnappedSwap(final Swaption.ArgumentsImpl args, final Date referenceDate,
            final DayCounter dayCounter) {

        final VanillaSwap orig = args.swap;
        final Leg fixedLeg = orig.fixedLeg();
        final Leg floatLeg = orig.floatingLeg();
        final int nFixed = fixedLeg.size();
        final int nFloat = floatLeg.size();

        // Start from each coupon's accrualStartDate (a.k.a. reset date).
        final List< Date > snappedFixed = new ArrayList<>(nFixed);
        for ( int i = 0; i < nFixed; i++ ) {
            snappedFixed.add(((FixedRateCoupon) fixedLeg.get(i)).accrualStartDate());
        }
        final List< Date > snappedFloat = new ArrayList<>(nFloat);
        for ( int i = 0; i < nFloat; i++ ) {
            snappedFloat.add(((FloatingRateCoupon) floatLeg.get(i)).accrualStartDate());
        }

        final CouponAdjustment[] fixedAdj = new CouponAdjustment[nFixed];
        final CouponAdjustment[] floatAdj = new CouponAdjustment[nFloat];
        Arrays.fill(fixedAdj, CouponAdjustment.pre);
        Arrays.fill(floatAdj, CouponAdjustment.pre);

        // C++ iterates schedule dates excluding the LAST one; in Java we map
        // that to "the reset date of every coupon" (each coupon's accrualStart
        // corresponds to a schedule date that's not the terminal pay date).
        for ( final Date exerciseDate : args.exercise.dates() ) {
            for ( int j = 0; j < nFixed; j++ ) {
                final Date u = snappedFixed.get(j);
                if ( !exerciseDate.eq(u) && withinOneWeek(exerciseDate, u) ) {
                    snappedFixed.set(j, exerciseDate);
                    if ( withinPreviousWeek(exerciseDate, u) ) {
                        fixedAdj[j] = CouponAdjustment.post;
                    }
                }
            }
            for ( int j = 0; j < nFloat; j++ ) {
                final Date u = snappedFloat.get(j);
                if ( !exerciseDate.eq(u) && withinOneWeek(exerciseDate, u) ) {
                    snappedFloat.set(j, exerciseDate);
                    if ( withinPreviousWeek(exerciseDate, u) ) {
                        floatAdj[j] = CouponAdjustment.post;
                    }
                }
            }
        }

        return new DiscretizedSwap(orig, referenceDate, dayCounter, fixedAdj, floatAdj, snappedFixed, snappedFloat);
    }

    private static boolean withinPreviousWeek(final Date d1, final Date d2) {
        // d2 is in [d1 - 7, d1].
        final long diff = d1.serialNumber() - d2.serialNumber();
        return diff >= 0 && diff <= 7;
    }

    private static boolean withinNextWeek(final Date d1, final Date d2) {
        // d2 is in [d1, d1 + 7].
        final long diff = d2.serialNumber() - d1.serialNumber();
        return diff >= 0 && diff <= 7;
    }

    private static boolean withinOneWeek(final Date d1, final Date d2) {
        return withinPreviousWeek(d1, d2) || withinNextWeek(d1, d2);
    }

    @Override
    public void reset(final int size) {
        underlying.initialize(method(), lastPayment_);
        super.reset(size);
    }
}
