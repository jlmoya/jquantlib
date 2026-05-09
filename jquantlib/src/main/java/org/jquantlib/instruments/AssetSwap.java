/*
 Copyright (C) 2026 JQuantLib contributors

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
 Copyright (C) 2006, 2007 Chiara Fornarola
 Copyright (C) 2007, 2009, 2011 Ferdinando Ametrano
 Copyright (C) 2007, 2009 StatPro Italia srl
 */

package org.jquantlib.instruments;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.Coupon;
import org.jquantlib.cashflow.FixedRateCoupon;
import org.jquantlib.cashflow.FloatingRateCoupon;
import org.jquantlib.cashflow.IborLeg;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.cashflow.OvernightLeg;
import org.jquantlib.cashflow.SimpleCashFlow;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.indexes.OvernightIndex;
import org.jquantlib.math.Constants;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Schedule;

/**
 * Bullet bond vs %Libor swap (asset swap).
 *
 * <p>Phase 5e.5-ASW port. Mirrors C++ v1.42.1
 * {@code ql/instruments/assetswap.{hpp,cpp}}.
 *
 * <p>For mechanics of par asset swap and market asset swap, refer to
 * "Introduction to Asset Swap", Lehman Brothers European Fixed Income
 * Research - January 2000, D. O'Kane.
 *
 * <p><b>Carry-forwards (Phase 5e.5b):</b>
 * <ul>
 *   <li>{@code Schedule.until()} is not yet ported, so a non-trivial
 *       {@code dealMaturity} (i.e. one earlier than {@code schedule.endDate()})
 *       is rejected. The most common asset-swap usage —
 *       {@code dealMaturity = bond.maturityDate()} — is fully supported.
 *   </li>
 *   <li>{@code npvDateDiscount} is currently approximated as {@code 1.0} in
 *       {@link #fairCleanPrice()} / {@link #fairNonParRepayment()} (exact when
 *       the discount curve's reference date equals the npv date, which is the
 *       common case). Wiring through Java's {@code Swap.results} is a Phase
 *       5e.5b align task.
 *   </li>
 * </ul>
 *
 * <p>Warning: bondCleanPrice must be the (forward) price at the
 * floatSchedule start date.
 *
 * @category instruments
 */
public class AssetSwap extends Swap {

    private static final /*@Spread*/ double basisPoint = 1.0e-4;

    //
    // private fields
    //

    private final Bond bond_;
    private final /*@Real*/ double bondCleanPrice_;
    private /*@Real*/ double nonParRepayment_;
    private final /*@Spread*/ double spread_;
    private final boolean parSwap_;
    private final Date upfrontDate_;

    // results (mutable, populated lazily)
    private /*@Spread*/ double fairSpread_;
    private /*@Real*/ double fairCleanPrice_;
    private /*@Real*/ double fairNonParRepayment_;


    //
    // public constructors
    //

    /**
     * Convenience constructor with default {@code parAssetSwap = true},
     * {@code gearing = 1.0}, no {@code nonParRepayment}, and no
     * {@code dealMaturity} truncation.
     */
    public AssetSwap(final boolean payBondCoupon,
                     final Bond bond,
                     final /*@Real*/ double bondCleanPrice,
                     final IborIndex iborIndex,
                     final /*@Spread*/ double spread) {
        this(payBondCoupon, bond, bondCleanPrice, iborIndex, spread,
                null, new DayCounter(), true, 1.0,
                Constants.NULL_REAL, new Date());
    }

    /**
     * Full constructor mirroring C++ v1.42.1
     * {@code AssetSwap::AssetSwap}.
     *
     * @param payBondCoupon if {@code true}, pay the bond leg (receive
     *                      floating); else receive bond (pay floating).
     * @param bond          the underlying bullet bond.
     * @param bondCleanPrice forward clean price at floating-schedule start.
     * @param iborIndex     index for the floating leg.
     * @param spread        spread on the floating leg.
     * @param floatSchedule optional floating schedule (null/empty triggers
     *                      auto-generation from the bond settlement to maturity).
     * @param floatingDayCount day-counter for the floating leg.
     * @param parAssetSwap  if {@code true}, par asset swap (notional = bond
     *                      notional); if {@code false}, market asset swap
     *                      (notional scaled by dirty price / 100).
     * @param gearing       leverage on the floating leg.
     * @param nonParRepayment  optional repayment value (use {@link Constants#NULL_REAL}
     *                         to default to bond redemption).
     * @param dealMaturity  optional truncation date (use {@link Date#isNull()
     *                      a null Date} to default to {@code schedule.back()}).
     */
    public AssetSwap(final boolean payBondCoupon,
                     final Bond bond,
                     final /*@Real*/ double bondCleanPrice,
                     final IborIndex iborIndex,
                     final /*@Spread*/ double spread,
                     Schedule floatSchedule,
                     final DayCounter floatingDayCount,
                     final boolean parAssetSwap,
                     final /*@Real*/ double gearing,
                     /*@Real*/ double nonParRepayment,
                     Date dealMaturity) {

        super(2);

        this.bond_ = bond;
        this.bondCleanPrice_ = bondCleanPrice;
        this.nonParRepayment_ = nonParRepayment;
        this.spread_ = spread;
        this.parSwap_ = parAssetSwap;

        // Mirrors C++ assetswap.cpp:50-54 — overnight indices need an
        // explicit floatSchedule.
        final OvernightIndex overnight =
                (iborIndex instanceof OvernightIndex)
                        ? (OvernightIndex) iborIndex : null;
        if (overnight != null) {
            QL.require(floatSchedule != null && !floatSchedule.empty(),
                    "floating schedule is needed when using an overnight index");
        }

        Schedule schedule;
        if (floatSchedule == null || floatSchedule.empty()) {
            schedule = new Schedule(
                    bond.settlementDate(),
                    bond.maturityDate(),
                    iborIndex.tenor(),
                    iborIndex.fixingCalendar(),
                    iborIndex.businessDayConvention(),
                    iborIndex.businessDayConvention(),
                    DateGeneration.Rule.Backward,
                    false /* endOfMonth */);
        } else {
            schedule = floatSchedule;
        }

        if (dealMaturity == null || dealMaturity.isNull()) {
            dealMaturity = schedule.endDate();
        }
        QL.require(dealMaturity.le(schedule.endDate()),
                "deal maturity " + dealMaturity
                + " cannot be later than (adjusted) bond maturity "
                + schedule.endDate());
        QL.require(dealMaturity.gt(schedule.startDate()),
                "deal maturity " + dealMaturity
                + " must be later than swap start date "
                + schedule.startDate());

        // Phase 5e.5-ASW carry-forward: Schedule.until() not ported.
        QL.require(dealMaturity.eq(schedule.endDate()),
                "Phase 5e.5-ASW: dealMaturity != schedule.endDate() requires"
                + " Schedule.until() truncation, deferred to Phase 5e.5b.");

        // the following might become an input parameter
        final BusinessDayConvention paymentAdjustment =
                BusinessDayConvention.Following;

        final Date finalDate = schedule.calendar().adjust(
                dealMaturity, paymentAdjustment);

        // bondCleanPrice must be the (forward) clean price at the
        // floating schedule start date.
        this.upfrontDate_ = schedule.startDate();
        final /*@Real*/ double dirtyPrice =
                bondCleanPrice_ + bond.accruedAmount(upfrontDate_);

        /*@Real*/ double notional = bond.notional(upfrontDate_);
        // Market asset swap: notional scaled by full price.
        if (!parSwap_) {
            notional *= dirtyPrice / 100.0;
        }

        /******** Bond leg ********/

        final Leg bondLeg = bond.cashflows();
        QL.require(!bondLeg.isEmpty(), "no cashflows from bond");

        final boolean includeOnUpfrontDate = false; // a cashflow on the
                                                    // upfront date must be
                                                    // discarded
        final Leg leg0 = new Leg();

        // Add coupons (skip the redemption, which is the last cashflow).
        int i = 0;
        for (; i < bondLeg.size() - 1
                && bondLeg.get(i).date().le(dealMaturity); ++i) {
            final CashFlow cf = bondLeg.get(i);
            if (!cf.hasOccurred(upfrontDate_, includeOnUpfrontDate)) {
                leg0.add(cf);
            }
        }

        // If we're skipping a cashflow before the redemption and it's a
        // coupon, then add the accrued coupon. Mirrors C++ assetswap.cpp:114.
        if (i < bondLeg.size() - 1) {
            final CashFlow skipped = bondLeg.get(i);
            if (skipped instanceof Coupon) {
                final Coupon c = (Coupon) skipped;
                final /*@Real*/ double accruedAmt = c.accruedAmount(dealMaturity);
                final SimpleCashFlow accruedCoupon =
                        new SimpleCashFlow(accruedAmt, finalDate);
                leg0.add(accruedCoupon);
            }
        }

        // Add the redemption (or the user-supplied non-par repayment).
        if (nonParRepayment_ == Constants.NULL_REAL) {
            final CashFlow redemption = bondLeg.last();
            final SimpleCashFlow finalFlow =
                    new SimpleCashFlow(redemption.amount(), finalDate);
            leg0.add(finalFlow);
            nonParRepayment_ = 100.0;
        } else {
            final SimpleCashFlow finalFlow =
                    new SimpleCashFlow(nonParRepayment_, finalDate);
            leg0.add(finalFlow);
        }

        /******** Floating leg ********/

        final Leg leg1;
        if (overnight != null) {
            leg1 = new OvernightLeg(schedule, overnight)
                    .withNotionals(notional)
                    .withPaymentAdjustment(paymentAdjustment)
                    .withGearings(gearing)
                    .withSpreads(spread)
                    .withPaymentDayCounter(floatingDayCount)
                    .leg();
        } else {
            leg1 = new IborLeg(schedule, iborIndex)
                    .withNotionals(notional)
                    .withPaymentAdjustment(paymentAdjustment)
                    .withGearings(gearing)
                    .withSpreads(spread)
                    .withPaymentDayCounter(floatingDayCount)
                    .Leg();
        }

        if (parSwap_) {
            // upfront
            final /*@Real*/ double upfront =
                    (dirtyPrice - 100.0) / 100.0 * notional;
            final SimpleCashFlow upfrontCashFlow =
                    new SimpleCashFlow(upfront, upfrontDate_);
            leg1.add(0, upfrontCashFlow);
            // backpayment (accounts for non-par redemption, if any)
            final SimpleCashFlow backPaymentCashFlow =
                    new SimpleCashFlow(notional, finalDate);
            leg1.add(backPaymentCashFlow);
        } else {
            // final notional exchange
            final SimpleCashFlow finalCashFlow =
                    new SimpleCashFlow(notional, finalDate);
            leg1.add(finalCashFlow);
        }

        /******** registration and sides ********/

        legs.add(leg0);
        legs.add(leg1);

        for (final Leg leg : legs) {
            for (final CashFlow cf : leg) {
                cf.addObserver(this);
            }
        }

        if (payBondCoupon) {
            payer[0] = -1.0;
            payer[1] = +1.0;
        } else {
            payer[0] = +1.0;
            payer[1] = -1.0;
        }

        // Initialize result fields to null until calculate runs.
        this.fairSpread_ = Constants.NULL_REAL;
        this.fairCleanPrice_ = Constants.NULL_REAL;
        this.fairNonParRepayment_ = Constants.NULL_REAL;
    }


    //
    // public inspectors
    //

    public boolean parSwap() {
        return parSwap_;
    }

    public /*@Spread*/ double spread() {
        return spread_;
    }

    public /*@Real*/ double cleanPrice() {
        return bondCleanPrice_;
    }

    public /*@Real*/ double nonParRepayment() {
        return nonParRepayment_;
    }

    public Bond bond() {
        return bond_;
    }

    public boolean payBondCoupon() {
        return payer[0] == -1.0;
    }

    public final Leg bondLeg() {
        return legs.get(0);
    }

    public final Leg floatingLeg() {
        return legs.get(1);
    }


    //
    // public results
    //

    public /*@Spread*/ double fairSpread() {
        calculate();
        if (fairSpread_ != Constants.NULL_REAL) {
            return fairSpread_;
        } else if (legBPS.length > 1 && legBPS[1] != Constants.NULL_REAL
                && !Double.isNaN(legBPS[1])) {
            fairSpread_ = spread_ - NPV / legBPS[1] * basisPoint;
            return fairSpread_;
        } else {
            QL.error("fair spread not available");
            return Constants.NULL_REAL; // unreachable
        }
    }

    public /*@Real*/ double floatingLegBPS() {
        calculate();
        QL.require(legBPS.length > 1
                        && legBPS[1] != Constants.NULL_REAL
                        && !Double.isNaN(legBPS[1]),
                "floating-leg BPS not available");
        return legBPS[1];
    }

    public /*@Real*/ double floatingLegNPV() {
        calculate();
        QL.require(legNPV.length > 1
                        && legNPV[1] != Constants.NULL_REAL
                        && !Double.isNaN(legNPV[1]),
                "floating-leg NPV not available");
        return legNPV[1];
    }

    public /*@Real*/ double fairCleanPrice() {
        calculate();
        if (fairCleanPrice_ != Constants.NULL_REAL) {
            return fairCleanPrice_;
        } else {
            QL.require(startDiscounts.length > 1
                            && startDiscounts[1] != Constants.NULL_REAL
                            && !Double.isNaN(startDiscounts[1]),
                    "fair clean price not available for seasoned deal");
            final /*@Real*/ double notional = bond_.notional(upfrontDate_);
            if (parSwap_) {
                // Mirrors C++ assetswap.cpp:271-273. NPV / startDiscount[1]
                // gives the NPV expressed at the floating leg start date,
                // which is then divided by notional/100 to give a
                // clean-price unit. Note: npvDateDiscount is not yet plumbed
                // through Java's Swap.results, so we approximate
                // npvDateDiscount = 1.0 (which is exact when the discount
                // curve's reference date equals the npv date).
                final /*@Real*/ double npvDateDiscount = 1.0;
                fairCleanPrice_ = bondCleanPrice_ - payer[1]
                        * NPV * npvDateDiscount / startDiscounts[1]
                        / (notional / 100.0);
            } else {
                final /*@Real*/ double accruedAmt =
                        bond_.accruedAmount(upfrontDate_);
                final /*@Real*/ double dirtyPrice =
                        bondCleanPrice_ + accruedAmt;
                final /*@Real*/ double fairDirtyPrice =
                        -legNPV[0] / legNPV[1] * dirtyPrice;
                fairCleanPrice_ = fairDirtyPrice - accruedAmt;
            }
            return fairCleanPrice_;
        }
    }

    public /*@Real*/ double fairNonParRepayment() {
        calculate();
        if (fairNonParRepayment_ != Constants.NULL_REAL) {
            return fairNonParRepayment_;
        } else {
            QL.require(endDiscounts.length > 1
                            && endDiscounts[1] != Constants.NULL_REAL
                            && !Double.isNaN(endDiscounts[1]),
                    "fair non par repayment not available for expired leg");
            final /*@Real*/ double notional = bond_.notional(upfrontDate_);
            // Mirrors C++ assetswap.cpp:293-295. Same npvDateDiscount = 1.0
            // approximation as above.
            final /*@Real*/ double npvDateDiscount = 1.0;
            fairNonParRepayment_ = nonParRepayment_ - payer[0]
                    * NPV * npvDateDiscount / endDiscounts[1]
                    / (notional / 100.0);
            return fairNonParRepayment_;
        }
    }


    //
    // overrides Swap
    //

    @Override
    protected void setupExpired() {
        super.setupExpired();
        fairSpread_ = Constants.NULL_REAL;
        fairCleanPrice_ = Constants.NULL_REAL;
        fairNonParRepayment_ = Constants.NULL_REAL;
    }

    @Override
    public void setupArguments(final PricingEngine.Arguments args) {
        super.setupArguments(args);

        if (!(args instanceof AssetSwap.Arguments)) {
            // It's a swap engine — nothing more to populate.
            return;
        }
        final AssetSwap.ArgumentsImpl a = (AssetSwap.ArgumentsImpl) args;

        final Leg fixedCoupons = bondLeg();
        final int nFixed = fixedCoupons.size();
        a.fixedResetDates =
                new ArrayList<Date>(Collections.nCopies(nFixed, (Date) null));
        a.fixedPayDates =
                new ArrayList<Date>(Collections.nCopies(nFixed, (Date) null));
        a.fixedCoupons =
                new ArrayList<Double>(Collections.nCopies(nFixed, (Double) null));

        for (int k = 0; k < nFixed; ++k) {
            // The bondLeg may contain SimpleCashFlow (accrued / redemption)
            // entries — only FixedRateCoupons get fixedResetDates etc.
            final CashFlow cf = fixedCoupons.get(k);
            if (cf instanceof FixedRateCoupon) {
                final FixedRateCoupon coupon = (FixedRateCoupon) cf;
                a.fixedPayDates.set(k, coupon.date());
                a.fixedResetDates.set(k, coupon.accrualStartDate());
                a.fixedCoupons.set(k, coupon.amount());
            } else {
                a.fixedPayDates.set(k, cf.date());
                a.fixedResetDates.set(k, cf.date());
                a.fixedCoupons.set(k, cf.amount());
            }
        }

        final Leg floatingCoupons = floatingLeg();
        final int nFloat = floatingCoupons.size();
        a.floatingResetDates =
                new ArrayList<Date>(Collections.nCopies(nFloat, (Date) null));
        a.floatingPayDates =
                new ArrayList<Date>(Collections.nCopies(nFloat, (Date) null));
        a.floatingFixingDates =
                new ArrayList<Date>(Collections.nCopies(nFloat, (Date) null));
        a.floatingAccrualTimes =
                new ArrayList<Double>(Collections.nCopies(nFloat, (Double) null));
        a.floatingSpreads =
                new ArrayList<Double>(Collections.nCopies(nFloat, (Double) null));

        for (int k = 0; k < nFloat; ++k) {
            final CashFlow cf = floatingCoupons.get(k);
            if (cf instanceof FloatingRateCoupon) {
                final FloatingRateCoupon coupon = (FloatingRateCoupon) cf;
                a.floatingResetDates.set(k, coupon.accrualStartDate());
                a.floatingPayDates.set(k, coupon.date());
                a.floatingFixingDates.set(k, coupon.fixingDate());
                a.floatingAccrualTimes.set(k, coupon.accrualPeriod());
                a.floatingSpreads.set(k, coupon.spread());
            } else {
                // SimpleCashFlow entries (upfront / backpayment).
                a.floatingResetDates.set(k, cf.date());
                a.floatingPayDates.set(k, cf.date());
                a.floatingFixingDates.set(k, cf.date());
                a.floatingAccrualTimes.set(k, 0.0);
                a.floatingSpreads.set(k, 0.0);
            }
        }
    }

    @Override
    public void fetchResults(final PricingEngine.Results results) {
        super.fetchResults(results);
        if (results instanceof AssetSwap.Results) {
            final AssetSwap.ResultsImpl r = (AssetSwap.ResultsImpl) results;
            fairSpread_ = r.fairSpread;
            fairCleanPrice_ = r.fairCleanPrice;
            fairNonParRepayment_ = r.fairNonParRepayment;
        } else {
            fairSpread_ = Constants.NULL_REAL;
            fairCleanPrice_ = Constants.NULL_REAL;
            fairNonParRepayment_ = Constants.NULL_REAL;
        }
    }


    //
    // public inner interfaces
    //

    public interface Arguments extends Swap.Arguments { /* marker */ }

    public interface Results extends Swap.Results { /* marker */ }


    //
    // public inner classes
    //

    /** Arguments for asset-swap calculation. */
    public static class ArgumentsImpl extends Swap.ArgumentsImpl
            implements AssetSwap.Arguments {

        public List<Date> fixedResetDates;
        public List<Date> fixedPayDates;
        public List<Double> fixedCoupons;
        public List<Double> floatingAccrualTimes;
        public List<Date> floatingResetDates;
        public List<Date> floatingFixingDates;
        public List<Date> floatingPayDates;
        public List<Double> floatingSpreads;

        @Override
        public void validate() {
            super.validate();
            QL.require(fixedResetDates.size() == fixedPayDates.size(),
                    "number of fixed start dates different from"
                    + " number of fixed payment dates");
            QL.require(fixedPayDates.size() == fixedCoupons.size(),
                    "number of fixed payment dates different from"
                    + " number of fixed coupon amounts");
            QL.require(floatingResetDates.size() == floatingPayDates.size(),
                    "number of floating start dates different from"
                    + " number of floating payment dates");
            QL.require(floatingFixingDates.size() == floatingPayDates.size(),
                    "number of floating fixing dates different from"
                    + " number of floating payment dates");
            QL.require(floatingAccrualTimes.size() == floatingPayDates.size(),
                    "number of floating accrual times different from"
                    + " number of floating payment dates");
            QL.require(floatingSpreads.size() == floatingPayDates.size(),
                    "number of floating spreads different from"
                    + " number of floating payment dates");
        }
    }

    /** Results from asset-swap calculation. */
    public static class ResultsImpl extends Swap.ResultsImpl
            implements AssetSwap.Results {
        public /*@Spread*/ double fairSpread;
        public /*@Real*/ double fairCleanPrice;
        public /*@Real*/ double fairNonParRepayment;

        @Override
        public void reset() {
            super.reset();
            fairSpread = Constants.NULL_REAL;
            fairCleanPrice = Constants.NULL_REAL;
            fairNonParRepayment = Constants.NULL_REAL;
        }
    }
}
