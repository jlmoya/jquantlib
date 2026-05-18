/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2001, 2002, 2003 Sadruddin Rejeb
 Copyright (C) 2004, 2007 StatPro Italia srl
 Copyright (C) 2022 Ralf Konrad Eckel

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */
package org.jquantlib.pricingengines.swap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.cashflow.Coupon;
import org.jquantlib.cashflow.FixedRateCoupon;
import org.jquantlib.cashflow.FloatingRateCoupon;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.DiscretizedAsset;
import org.jquantlib.instruments.DiscretizedDiscountBond;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.math.Constants;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.time.Date;

/**
 * Discretized swap asset for use with tree-based pricing engines.
 * <p>
 * Port of C++ v1.42.1 {@code ql/pricingengines/swap/discretizedswap.{hpp,cpp}}.
 * <p>
 * Reads fixed/floating leg structure directly from the underlying
 * {@link VanillaSwap} (rather than the {@code Swap.arguments} propagation,
 * which is broken for {@code Swaption.arguments} in the Java port — see the
 * note on {@code Swaption.java}).
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 * <li>C++ takes a {@code VanillaSwap::arguments} struct populated via
 *     {@code setupArguments}; the Java port takes a {@link VanillaSwap}
 *     reference and reads {@code fixedLeg()} / {@code floatingLeg()}
 *     directly. This sidesteps the inverted {@code isAssignableFrom} check
 *     in {@code VanillaSwap.setupArguments} (a Phase-1 leftover bug) without
 *     having to touch every caller.
 * <li>{@code Settings.includeTodaysCashFlows} is C++17 {@code optional}; the
 *     Java {@code Settings} doesn't expose it. The Java port assumes
 *     {@code true} (matches the common case in the C++ test suite).
 * <li>{@code CouponAdjustment::pre} / {@code post} is encoded as a small
 *     enum {@link CouponAdjustment} in the same package.
 * </ul>
 */
public class DiscretizedSwap extends DiscretizedAsset {

    /**
     * Coupon adjustment timing — pre-rollback (default) or post-rollback.
     * Mirrors C++ v1.42.1 {@code ql/cashflows/couponadjustment.hpp}.
     */
    public enum CouponAdjustment {
        pre, post
    }

    private final VanillaSwap swap_;
    private final VanillaSwap.Type type_;
    private final double nominal_;
    private final double[] fixedResetTimes_;
    private final double[] fixedPayTimes_;
    private final double[] fixedCoupons_;
    private final CouponAdjustment[] fixedCouponAdjustments_;
    private final boolean[] fixedResetTimeIsInPast_;
    private final double[] floatingResetTimes_;
    private final double[] floatingPayTimes_;
    private final double[] floatingAccrualTimes_;
    private final double[] floatingSpreads_;
    private final double[] floatingCoupons_;
    private final CouponAdjustment[] floatingCouponAdjustments_;
    private final boolean[] floatingResetTimeIsInPast_;

    public DiscretizedSwap(final VanillaSwap swap, final Date referenceDate,
            final DayCounter dayCounter) {
        this(swap, referenceDate, dayCounter,
                fillAdjustments(swap.fixedLeg().size()),
                fillAdjustments(swap.floatingLeg().size()));
    }

    public DiscretizedSwap(final VanillaSwap swap, final Date referenceDate,
            final DayCounter dayCounter,
            final CouponAdjustment[] fixedCouponAdjustments,
            final CouponAdjustment[] floatingCouponAdjustments) {
        this(swap, referenceDate, dayCounter, fixedCouponAdjustments,
                floatingCouponAdjustments, null, null);
    }

    /**
     * Full constructor with optional snapped reset-date overrides.
     * <p>
     * If {@code snappedFixedResetDates} (resp. {@code snappedFloatingResetDates})
     * is non-null, those dates replace each coupon's accrualStartDate when
     * computing the discretized reset times. The original {@link VanillaSwap}
     * is still used for nominal, type, fixed coupons, floating spreads, and
     * pay times — so day-count conventions, payment-day shifts, and accrual
     * fractions on the leg side stay aligned with the original schedule.
     * <p>
     * Used by {@link org.jquantlib.pricingengines.swaption.DiscretizedSwaption}
     * to mirror C++ {@code prepareSwaptionWithSnappedDates} without having
     * to rebuild a new VanillaSwap on a partial-Schedule.
     */
    public DiscretizedSwap(final VanillaSwap swap, final Date referenceDate,
            final DayCounter dayCounter,
            final CouponAdjustment[] fixedCouponAdjustments,
            final CouponAdjustment[] floatingCouponAdjustments,
            final List<Date> snappedFixedResetDates,
            final List<Date> snappedFloatingResetDates) {
        super();
        this.swap_ = swap;
        this.type_ = swap.type();
        this.nominal_ = swap.nominal();
        final Leg fixedLeg = swap.fixedLeg();
        final Leg floatingLeg = swap.floatingLeg();
        QL.require(fixedCouponAdjustments.length == fixedLeg.size(),
                "fixed coupon adjustments size mismatch");
        QL.require(floatingCouponAdjustments.length == floatingLeg.size(),
                "floating coupon adjustments size mismatch");
        QL.require(snappedFixedResetDates == null
                        || snappedFixedResetDates.size() == fixedLeg.size(),
                "snapped fixed reset dates size mismatch");
        QL.require(snappedFloatingResetDates == null
                        || snappedFloatingResetDates.size() == floatingLeg.size(),
                "snapped floating reset dates size mismatch");

        // Java port deviation: assume includeTodaysCashFlows = true (C++ default
        // in the typical test setup; the Settings.optional<bool> is not yet
        // mirrored in the Java Settings).
        final boolean includeTodaysCashFlows = true;

        final int nFixed = fixedLeg.size();
        this.fixedResetTimes_ = new double[nFixed];
        this.fixedPayTimes_ = new double[nFixed];
        this.fixedCoupons_ = new double[nFixed];
        this.fixedCouponAdjustments_ = Arrays.copyOf(fixedCouponAdjustments, nFixed);
        this.fixedResetTimeIsInPast_ = new boolean[nFixed];
        for (int i = 0; i < nFixed; i++) {
            final FixedRateCoupon c = (FixedRateCoupon) fixedLeg.get(i);
            final Date resetDate = (snappedFixedResetDates != null)
                    ? snappedFixedResetDates.get(i) : c.accrualStartDate();
            final double resetTime = dayCounter.yearFraction(referenceDate, resetDate);
            final double payTime = dayCounter.yearFraction(referenceDate, c.date());
            fixedResetTimes_[i] = resetTime;
            fixedPayTimes_[i] = payTime;
            // When the reset date was snapped (≠ original accrual start),
            // recompute the coupon amount on the snapped accrual period —
            // matches C++ which builds a fresh VanillaSwap on the snapped
            // Schedule and reads the recomputed coupons.
            if (snappedFixedResetDates != null
                    && !resetDate.eq(c.accrualStartDate())) {
                final double snappedAccrual = swap.fixedDayCount()
                        .yearFraction(resetDate, c.date());
                fixedCoupons_[i] = c.rate() * snappedAccrual * c.nominal();
            } else {
                fixedCoupons_[i] = c.amount();
            }
            final boolean inPast = isResetTimeInPast(resetTime, payTime, includeTodaysCashFlows);
            fixedResetTimeIsInPast_[i] = inPast;
            if (inPast) {
                fixedCouponAdjustments_[i] = CouponAdjustment.post;
            }
        }

        final int nFloat = floatingLeg.size();
        this.floatingResetTimes_ = new double[nFloat];
        this.floatingPayTimes_ = new double[nFloat];
        this.floatingAccrualTimes_ = new double[nFloat];
        this.floatingSpreads_ = new double[nFloat];
        this.floatingCoupons_ = new double[nFloat];
        this.floatingCouponAdjustments_ = Arrays.copyOf(floatingCouponAdjustments, nFloat);
        this.floatingResetTimeIsInPast_ = new boolean[nFloat];
        for (int i = 0; i < nFloat; i++) {
            final FloatingRateCoupon c = (FloatingRateCoupon) floatingLeg.get(i);
            final Date resetDate = (snappedFloatingResetDates != null)
                    ? snappedFloatingResetDates.get(i) : c.accrualStartDate();
            final double resetTime = dayCounter.yearFraction(referenceDate, resetDate);
            final double payTime = dayCounter.yearFraction(referenceDate, c.date());
            floatingResetTimes_[i] = resetTime;
            floatingPayTimes_[i] = payTime;
            // Recompute the accrual period when the reset is snapped (mirrors
            // C++ rebuilt-on-snapped-Schedule semantics).
            if (snappedFloatingResetDates != null
                    && !resetDate.eq(c.accrualStartDate())) {
                floatingAccrualTimes_[i] = swap.floatingDayCount()
                        .yearFraction(resetDate, c.date());
            } else {
                floatingAccrualTimes_[i] = c.accrualPeriod();
            }
            floatingSpreads_[i] = c.spread();
            // Pre-fixed coupons need their amount; for forward-starting periods
            // the amount is computed on the fly.
            double amt = Double.NaN;
            try {
                amt = c.amount();
            } catch (final Exception e) {
                amt = Double.NaN;
            }
            floatingCoupons_[i] = amt;
            final boolean inPast = isResetTimeInPast(resetTime, payTime, includeTodaysCashFlows);
            floatingResetTimeIsInPast_[i] = inPast;
            if (inPast) {
                floatingCouponAdjustments_[i] = CouponAdjustment.post;
            }
        }
    }

    /**
     * Arguments-based constructor mirroring C++ v1.42.1
     * {@code DiscretizedSwap(const VanillaSwap::arguments&, const Date&, const DayCounter&)}
     * verbatim. Used by {@link org.jquantlib.pricingengines.swap.TreeVanillaSwapEngine}
     * which only sees {@code arguments_} (no live {@link VanillaSwap} handle).
     *
     * <p>Populates {@link #underlyingSwap()} with {@code null}; callers that
     * need the underlying swap (e.g. {@link org.jquantlib.pricingengines.swaption.DiscretizedSwaption})
     * must continue to use the swap-based ctor.
     *
     * <p>Java port deviations from C++ v1.42.1:
     * <ul>
     * <li>{@code Settings.includeTodaysCashFlows} is C++17 {@code optional};
     *     the Java port assumes {@code true} (matches the common case in the
     *     C++ test suite), mirroring the swap-based ctor above.
     * </ul>
     */
    public DiscretizedSwap(final VanillaSwap.ArgumentsImpl args,
            final Date referenceDate, final DayCounter dayCounter) {
        super();
        this.swap_ = null;
        this.type_ = args.type;
        this.nominal_ = args.nominal;

        final boolean includeTodaysCashFlows = true;

        final int nFixed = args.fixedResetDates.size();
        QL.require(args.fixedPayDates.size() == nFixed,
                "fixed reset/pay date size mismatch");
        QL.require(args.fixedCoupons.size() == nFixed,
                "fixed coupon size mismatch");
        this.fixedResetTimes_ = new double[nFixed];
        this.fixedPayTimes_ = new double[nFixed];
        this.fixedCoupons_ = new double[nFixed];
        this.fixedCouponAdjustments_ = new CouponAdjustment[nFixed];
        this.fixedResetTimeIsInPast_ = new boolean[nFixed];
        for (int i = 0; i < nFixed; i++) {
            final double resetTime = dayCounter.yearFraction(referenceDate,
                    args.fixedResetDates.get(i));
            final double payTime = dayCounter.yearFraction(referenceDate,
                    args.fixedPayDates.get(i));
            fixedResetTimes_[i] = resetTime;
            fixedPayTimes_[i] = payTime;
            fixedCoupons_[i] = args.fixedCoupons.get(i);
            final boolean inPast = isResetTimeInPast(resetTime, payTime,
                    includeTodaysCashFlows);
            fixedResetTimeIsInPast_[i] = inPast;
            fixedCouponAdjustments_[i] = inPast
                    ? CouponAdjustment.post : CouponAdjustment.pre;
        }

        final int nFloat = args.floatingResetDates.size();
        QL.require(args.floatingPayDates.size() == nFloat,
                "floating reset/pay date size mismatch");
        QL.require(args.floatingAccrualTimes.size() == nFloat,
                "floating accrual times size mismatch");
        QL.require(args.floatingSpreads.size() == nFloat,
                "floating spreads size mismatch");
        QL.require(args.floatingCoupons.size() == nFloat,
                "floating coupons size mismatch");
        this.floatingResetTimes_ = new double[nFloat];
        this.floatingPayTimes_ = new double[nFloat];
        this.floatingAccrualTimes_ = new double[nFloat];
        this.floatingSpreads_ = new double[nFloat];
        this.floatingCoupons_ = new double[nFloat];
        this.floatingCouponAdjustments_ = new CouponAdjustment[nFloat];
        this.floatingResetTimeIsInPast_ = new boolean[nFloat];
        for (int i = 0; i < nFloat; i++) {
            final double resetTime = dayCounter.yearFraction(referenceDate,
                    args.floatingResetDates.get(i));
            final double payTime = dayCounter.yearFraction(referenceDate,
                    args.floatingPayDates.get(i));
            floatingResetTimes_[i] = resetTime;
            floatingPayTimes_[i] = payTime;
            floatingAccrualTimes_[i] = args.floatingAccrualTimes.get(i);
            floatingSpreads_[i] = args.floatingSpreads.get(i);
            // Pre-fixed coupons come through as a populated amount; forward-
            // starting periods may be sentinel NULL_REAL. Treat NULL_REAL as
            // NaN so postAdjustValuesImpl's "current floating coupon not given"
            // require fires for in-past resets that weren't fixed.
            final double amt = args.floatingCoupons.get(i);
            floatingCoupons_[i] = (amt == Constants.NULL_REAL) ? Double.NaN : amt;
            final boolean inPast = isResetTimeInPast(resetTime, payTime,
                    includeTodaysCashFlows);
            floatingResetTimeIsInPast_[i] = inPast;
            floatingCouponAdjustments_[i] = inPast
                    ? CouponAdjustment.post : CouponAdjustment.pre;
        }
    }

    /**
     * @return the underlying {@link VanillaSwap} held by this discretized
     *     asset, or {@code null} when this instance was built from
     *     {@code VanillaSwap.ArgumentsImpl} (arguments-only path used by
     *     {@link org.jquantlib.pricingengines.swap.TreeVanillaSwapEngine}).
     *     Surface used by {@code DiscretizedSwaption} to read the swap's
     *     terminal pay date after snapping.
     */
    public VanillaSwap underlyingSwap() {
        return swap_;
    }

    @Override
    public void reset(final int size) {
        values_ = new Array(size).fill(0.0);
        adjustValues();
    }

    @Override
    public List</*@Time*/ Double> mandatoryTimes() {
        final List<Double> times = new ArrayList<Double>();
        for (final double t : fixedResetTimes_) {
            if (t >= 0.0) {
                times.add(t);
            }
        }
        for (final double t : fixedPayTimes_) {
            if (t >= 0.0) {
                times.add(t);
            }
        }
        for (final double t : floatingResetTimes_) {
            if (t >= 0.0) {
                times.add(t);
            }
        }
        for (final double t : floatingPayTimes_) {
            if (t >= 0.0) {
                times.add(t);
            }
        }
        return times;
    }

    @Override
    protected void preAdjustValuesImpl() {
        for (int i = 0; i < floatingResetTimes_.length; i++) {
            final double t = floatingResetTimes_[i];
            if (floatingCouponAdjustments_[i] == CouponAdjustment.pre && t >= 0.0
                    && isOnTime(t)) {
                addFloatingCoupon(i);
            }
        }
        for (int i = 0; i < fixedResetTimes_.length; i++) {
            final double t = fixedResetTimes_[i];
            if (fixedCouponAdjustments_[i] == CouponAdjustment.pre && t >= 0.0
                    && isOnTime(t)) {
                addFixedCoupon(i);
            }
        }
    }

    @Override
    protected void postAdjustValuesImpl() {
        for (int i = 0; i < floatingResetTimes_.length; i++) {
            final double t = floatingResetTimes_[i];
            if (floatingCouponAdjustments_[i] == CouponAdjustment.post && t >= 0.0
                    && isOnTime(t)) {
                addFloatingCoupon(i);
            }
        }
        for (int i = 0; i < fixedResetTimes_.length; i++) {
            final double t = fixedResetTimes_[i];
            if (fixedCouponAdjustments_[i] == CouponAdjustment.post && t >= 0.0
                    && isOnTime(t)) {
                addFixedCoupon(i);
            }
        }

        // Fixed coupons whose reset is in the past — accrue at pay time.
        for (int i = 0; i < fixedPayTimes_.length; i++) {
            final double t = fixedPayTimes_[i];
            if (fixedResetTimeIsInPast_[i] && isOnTime(t)) {
                final double coupon = fixedCoupons_[i];
                if (type_ == VanillaSwap.Type.Payer) {
                    for (int j = 0; j < values_.size(); j++) {
                        values_.set(j, values_.get(j) - coupon);
                    }
                } else {
                    for (int j = 0; j < values_.size(); j++) {
                        values_.set(j, values_.get(j) + coupon);
                    }
                }
            }
        }

        // Same for floating payments whose rate is already fixed.
        for (int i = 0; i < floatingPayTimes_.length; i++) {
            final double t = floatingPayTimes_[i];
            if (floatingResetTimeIsInPast_[i] && isOnTime(t)) {
                final double coupon = floatingCoupons_[i];
                QL.require(!Double.isNaN(coupon), "current floating coupon not given");
                if (type_ == VanillaSwap.Type.Payer) {
                    for (int j = 0; j < values_.size(); j++) {
                        values_.set(j, values_.get(j) + coupon);
                    }
                } else {
                    for (int j = 0; j < values_.size(); j++) {
                        values_.set(j, values_.get(j) - coupon);
                    }
                }
            }
        }
    }

    //
    // private helpers
    //

    private static CouponAdjustment[] fillAdjustments(final int n) {
        final CouponAdjustment[] arr = new CouponAdjustment[n];
        Arrays.fill(arr, CouponAdjustment.pre);
        return arr;
    }

    private static boolean isResetTimeInPast(final double resetTime,
            final double payTime, final boolean includeTodaysCashFlows) {
        return (resetTime < 0.0)
                && ((payTime > 0.0) || (includeTodaysCashFlows && payTime == 0.0));
    }

    private void addFixedCoupon(final int i) {
        final DiscretizedDiscountBond bond = new DiscretizedDiscountBond();
        bond.initialize(method(), fixedPayTimes_[i]);
        bond.rollback(time);

        final double fixedCoupon = fixedCoupons_[i];
        final Array bv = bond.values();
        if (type_ == VanillaSwap.Type.Payer) {
            for (int j = 0; j < values_.size(); j++) {
                values_.set(j, values_.get(j) - fixedCoupon * bv.get(j));
            }
        } else {
            for (int j = 0; j < values_.size(); j++) {
                values_.set(j, values_.get(j) + fixedCoupon * bv.get(j));
            }
        }
    }

    private void addFloatingCoupon(final int i) {
        final DiscretizedDiscountBond bond = new DiscretizedDiscountBond();
        bond.initialize(method(), floatingPayTimes_[i]);
        bond.rollback(time);

        final double nominal = nominal_;
        final double T = floatingAccrualTimes_[i];
        final double spread = floatingSpreads_[i];
        final double accruedSpread = nominal * T * spread;
        final Array bv = bond.values();
        if (type_ == VanillaSwap.Type.Payer) {
            for (int j = 0; j < values_.size(); j++) {
                final double coupon = nominal * (1.0 - bv.get(j))
                        + accruedSpread * bv.get(j);
                values_.set(j, values_.get(j) + coupon);
            }
        } else {
            for (int j = 0; j < values_.size(); j++) {
                final double coupon = nominal * (1.0 - bv.get(j))
                        + accruedSpread * bv.get(j);
                values_.set(j, values_.get(j) - coupon);
            }
        }
    }
}
