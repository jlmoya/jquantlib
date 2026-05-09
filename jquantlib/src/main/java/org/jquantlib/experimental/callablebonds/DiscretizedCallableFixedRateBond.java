/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2008 Allen Kuo
 Copyright (C) 2021 Ralf Konrad Eckel

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */
package org.jquantlib.experimental.callablebonds;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.cashflow.Callability;
import org.jquantlib.experimental.callablebonds.CallableBond.CallableBondArgumentsImpl;
import org.jquantlib.instruments.DiscretizedAsset;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;

/**
 * Discretized callable fixed-rate bond used by tree pricing.
 * <p>
 * Port of C++ v1.42.1
 * {@code ql/experimental/callablebonds/discretizedcallablefixedratebond.{hpp,cpp}}.
 */
public class DiscretizedCallableFixedRateBond extends DiscretizedAsset {

    private enum CouponAdjustment { pre, post }

    private final CallableBondArgumentsImpl arguments_;
    private final double redemptionTime_;
    private final List<Double> couponTimes_;
    private final CouponAdjustment[] couponAdjustments_;
    private final List<Double> callabilityTimes_;
    private final List<Double> adjustedCallabilityPrices_;

    public DiscretizedCallableFixedRateBond(final CallableBondArgumentsImpl args,
            final Handle<YieldTermStructure> termStructure) {
        this.arguments_ = args;
        this.adjustedCallabilityPrices_ = new ArrayList<Double>(args.callabilityPrices);

        final org.jquantlib.daycounters.DayCounter dc = termStructure.currentLink().dayCounter();
        final Date referenceDate = termStructure.currentLink().referenceDate();

        this.redemptionTime_ = dc.yearFraction(referenceDate, args.redemptionDate);

        // By default the coupon adjustment should take place in postAdjustValuesImpl().
        this.couponAdjustments_ = new CouponAdjustment[args.couponDates.size()];
        for (int i = 0; i < couponAdjustments_.length; i++) {
            couponAdjustments_[i] = CouponAdjustment.post;
        }

        this.couponTimes_ = new ArrayList<Double>(args.couponDates.size());
        for (int i = 0; i < args.couponDates.size(); i++) {
            couponTimes_.add(dc.yearFraction(referenceDate, args.couponDates.get(i)));
        }

        this.callabilityTimes_ = new ArrayList<Double>(args.callabilityDates.size());
        for (int i = 0; i < args.callabilityDates.size(); i++) {
            final Date callabilityDate = args.callabilityDates.get(i);
            double callabilityTime = dc.yearFraction(referenceDate, callabilityDate);

            // Snap exercise dates to the closest coupon date (within next week)
            // to avoid mispricing.
            for (int j = 0; j < couponTimes_.size(); j++) {
                final double couponTime = couponTimes_.get(j);
                final Date couponDate = args.couponDates.get(j);

                if (withinNextWeek(callabilityTime, couponTime) && callabilityDate.lt(couponDate)) {
                    callabilityTime = couponTime;

                    /* The order of events must be changed here. The callability
                       is normally done before adding of the coupon. However from
                       the DiscretizedAsset.rollback(to) perspective the coupon
                       must be added before the callability as it is later in time. */
                    couponAdjustments_[j] = CouponAdjustment.pre;

                    /* We snapped the callabilityTime so we need to take into
                       account the missing discount factor including any possible
                       spread (set in the OAS calculation). */
                    final double spread = arguments_.spread;
                    final YieldTermStructure ts = termStructure.currentLink();
                    final double dfTillCallDate = calcDiscountFactorInclSpread(ts, callabilityDate,
                            spread);
                    final double dfTillCouponDate = calcDiscountFactorInclSpread(ts, couponDate,
                            spread);
                    adjustedCallabilityPrices_.set(i,
                            adjustedCallabilityPrices_.get(i) * dfTillCallDate / dfTillCouponDate);

                    break;
                }
            }

            adjustedCallabilityPrices_.set(i,
                    adjustedCallabilityPrices_.get(i) * arguments_.faceAmount / 100.0);
            callabilityTimes_.add(callabilityTime);
        }
    }

    private static double calcDiscountFactorInclSpread(final YieldTermStructure ts, final Date date,
            final double spread) {
        final double time = ts.timeFromReference(date);
        final double zeroRateInclSpread = ts.zeroRate(date, ts.dayCounter(), Compounding.Continuous,
                Frequency.NoFrequency).rate() + spread;
        return Math.exp(-zeroRateInclSpread * time);
    }

    private static boolean withinNextWeek(final double t1, final double t2) {
        final double dt = 1.0 / 52;
        return t1 <= t2 && t2 <= t1 + dt;
    }

    @Override
    public void reset(final int size) {
        values_ = new Array(size);
        for (int i = 0; i < size; i++) {
            values_.set(i, arguments_.redemption);
        }
        adjustValues();
    }

    @Override
    public List<Double> mandatoryTimes() {
        final List<Double> times = new ArrayList<Double>();
        if (redemptionTime_ >= 0.0) {
            times.add(redemptionTime_);
        }
        for (final double t : couponTimes_) {
            if (t >= 0.0) {
                times.add(t);
            }
        }
        for (final double t : callabilityTimes_) {
            if (t >= 0.0) {
                times.add(t);
            }
        }
        return times;
    }

    @Override
    protected void preAdjustValuesImpl() {
        for (int i = 0; i < couponTimes_.size(); i++) {
            if (couponAdjustments_[i] == CouponAdjustment.pre) {
                final double t = couponTimes_.get(i);
                if (t >= 0.0 && isOnTime(t)) {
                    addCoupon(i);
                }
            }
        }
    }

    @Override
    protected void postAdjustValuesImpl() {
        for (int i = 0; i < callabilityTimes_.size(); i++) {
            final double t = callabilityTimes_.get(i);
            if (t >= 0.0 && isOnTime(t)) {
                applyCallability(i);
            }
        }
        for (int i = 0; i < couponTimes_.size(); i++) {
            if (couponAdjustments_[i] == CouponAdjustment.post) {
                final double t = couponTimes_.get(i);
                if (t >= 0.0 && isOnTime(t)) {
                    addCoupon(i);
                }
            }
        }
    }

    private void applyCallability(final int i) {
        final Callability c = arguments_.putCallSchedule.get(i);
        final double price = adjustedCallabilityPrices_.get(i);
        switch (c.type()) {
            case Call:
                for (int j = 0; j < values_.size(); j++) {
                    values_.set(j, Math.min(price, values_.get(j)));
                }
                break;
            case Put:
                for (int j = 0; j < values_.size(); j++) {
                    values_.set(j, Math.max(values_.get(j), price));
                }
                break;
            default:
                QL.error("unknown callability type");
        }
    }

    private void addCoupon(final int i) {
        // values_ += couponAmount
        final double amount = arguments_.couponAmounts.get(i);
        for (int j = 0; j < values_.size(); j++) {
            values_.set(j, values_.get(j) + amount);
        }
    }
}
