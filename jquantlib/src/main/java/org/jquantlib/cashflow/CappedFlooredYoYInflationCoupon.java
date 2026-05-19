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
 Copyright (C) 2009 Chris Kenyon

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.cashflow;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.CPI;
import org.jquantlib.indexes.YoYInflationIndex;
import org.jquantlib.math.Constants;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;

/**
 * Capped or floored year-on-year inflation coupon.
 *
 * <p>Java port of QuantLib v1.42.1 {@code CappedFlooredYoYInflationCoupon}
 * ({@code ql/cashflows/capflooredinflationcoupon.{hpp,cpp}}).
 *
 * <p>The payoff {@latex$ P } of a capped inflation-rate coupon with
 * paysWithin = true is: {@latex[ P = N \times T \times \min(a L + b, C). } where {@latex$ N } is the notional,
 * {@latex$ T } is the accrual time, {@latex$ L } is the inflation rate, {@latex$ a } is its gearing, {@latex$ b } is
 * the spread, and {@latex$ C } and {@latex$ F } are the strikes.
 *
 * <p>The payoff of a floored inflation-rate coupon is:
 * {@latex[ P = N \times T \times \max(a L + b, F). }
 *
 * <p>The payoff of a collared inflation-rate coupon is:
 * {@latex[ P = N \times T \times \min(\max(a L + b, F), C). }
 *
 * <h3>Implementation note</h3>
 * <p>This class extends {@link YoYInflationCoupon} and may either watch an
 * underlying coupon (passed in via the underlying-form constructor) or be stand-alone. Per the C++ pattern, when
 * wrapping an underlying coupon, the pricer is shared: {@link #setPricer(InflationCouponPricer)} forwards to the
 * underlying coupon's pricer slot too.
 *
 * <p>The cap/floor adjustment is performed in {@link #rate()} via the
 * pricer's {@code capletRate} / {@code floorletRate}. The non-vol-dependent pricer ({@link YoYInflationCouponPricer})
 * does not implement those methods (Phase 2r); pass-through callers (no cap, no floor active) work today.
 *
 * @author JQuantLib migration team (Phase 2q D.1)
 * @see YoYInflationCoupon
 * @see CappedFlooredCoupon
 */
public class CappedFlooredYoYInflationCoupon extends YoYInflationCoupon {

    //
    // protected fields — visible to descendants per C++ pattern
    //

    /** Underlying coupon when constructed via the underlying-form ctor; otherwise null. */
    protected YoYInflationCoupon underlying_;
    protected boolean isFloored_;
    protected boolean isCapped_;
    protected double cap_;
    protected double floor_;

    //
    // public constructors
    //

    /**
     * Underlying-form constructor — wrap an existing {@link YoYInflationCoupon} and apply a cap and/or floor. Mirrors
     * the C++ first form.
     *
     * @param underlying the wrapped YoY coupon
     * @param cap        cap level, or {@link Constants#NULL_REAL} for "no cap"
     * @param floor      floor level, or {@link Constants#NULL_REAL} for "no floor"
     */
    public CappedFlooredYoYInflationCoupon(final YoYInflationCoupon underlying, final double cap, final double floor) {
        super(underlying.nominal(), underlying.date(), underlying.accrualStartDate(), underlying.accrualEndDate(),
                underlying.fixingDays(), underlying.yoyIndex(), underlying.observationLag(), underlying.interpolation(),
                underlying.dayCounter(), underlying.gearing(), underlying.spread(), underlying.referencePeriodStart(),
                underlying.referencePeriodEnd());
        this.underlying_ = underlying;
        this.isFloored_ = false;
        this.isCapped_ = false;
        setCommon(cap, floor);
        // Match C++ registerWith(underlying).
        underlying.addObserver(this);
    }

    /** Convenience: wrap an underlying with no cap/floor (pure pass-through). */
    public CappedFlooredYoYInflationCoupon(final YoYInflationCoupon underlying) {
        this(underlying, Constants.NULL_REAL, Constants.NULL_REAL);
    }

    /**
     * Stand-alone constructor — no underlying coupon. Mirrors the C++ second form. Argument order follows the JQuantLib
     * {@link YoYInflationCoupon} convention {@code (nominal, paymentDate, startDate, endDate, ...)}.
     */
    public CappedFlooredYoYInflationCoupon(final double nominal, final Date paymentDate, final Date startDate,
            final Date endDate, final int fixingDays, final YoYInflationIndex index, final Period observationLag,
            final CPI.InterpolationType interpolation, final DayCounter dayCounter, final double gearing,
            final double spread, final double cap, final double floor, final Date refPeriodStart,
            final Date refPeriodEnd) {
        super(nominal, paymentDate, startDate, endDate, fixingDays, index, observationLag, interpolation, dayCounter,
                gearing, spread, refPeriodStart, refPeriodEnd);
        this.underlying_ = null;
        this.isFloored_ = false;
        this.isCapped_ = false;
        setCommon(cap, floor);
    }

    //
    // private helpers
    //

    /**
     * Mirrors C++ {@code setCommon}: validates and stashes cap/floor, swapping them when {@code gearing < 0} (a cap on
     * a negatively-geared rate becomes a floor and vice versa). {@code Null<Rate>()} sentinel detection accepts both
     * NaN and {@link Constants#NULL_REAL} (which equals {@link Constants#NULL_RATE}) — same approach as
     * {@link CappedFlooredCoupon}.
     */
    private void setCommon(final double cap, final double floor) {
        this.isCapped_ = false;
        this.isFloored_ = false;

        final boolean capPresent = !Double.isNaN(cap) && cap != Constants.NULL_REAL;
        final boolean floorPresent = !Double.isNaN(floor) && floor != Constants.NULL_REAL;

        if ( gearing_ > 0 ) {
            if ( capPresent ) {
                isCapped_ = true;
                cap_ = cap;
            }
            if ( floorPresent ) {
                floor_ = floor;
                isFloored_ = true;
            }
        } else {
            // gearing < 0 — cap and floor swap roles per C++.
            if ( capPresent ) {
                floor_ = cap;
                isFloored_ = true;
            }
            if ( floorPresent ) {
                isCapped_ = true;
                cap_ = floor;
            }
        }
        if ( isCapped_ && isFloored_ ) {
            QL.require(cap >= floor, "cap level (" + cap + ") less than floor level (" + floor + ")");
        }
    }

    //
    // public accessors — augmented YoYInflationCoupon interface
    //

    /**
     * Mirrors C++ {@code setPricer}: forwards the pricer to {@code this} via
     * {@link YoYInflationCoupon#setPricer(InflationCouponPricer)} and also (when wrapping an underlying coupon) to the
     * underlying's pricer slot.
     */
    public void setPricer(final YoYInflationCouponPricer pricer) {
        super.setPricer(pricer);
        if ( underlying_ != null ) {
            underlying_.setPricer(pricer);
        }
    }

    /**
     * Returns the expected rate before cap and floor are applied. When wrapping an underlying coupon this is
     * {@code underlying.rate()}; otherwise the inherited {@link YoYInflationCoupon#rate()}.
     */
    public double underlyingRate() {
        return underlying_ != null ? underlying_.rate() : super.rate();
    }

    /**
     * Coupon rate after cap/floor adjustment. Mirrors C++ {@code rate()}:
     * <pre>
     *   rate = underlyingRate + floorletRate(effectiveFloor) - capletRate(effectiveCap)
     * </pre>
     * Pure pass-through (no cap, no floor) avoids the pricer call entirely; any active cap/floor requires a pricer that
     * implements the optionlet methods (deferred to Phase 2r).
     */
    @Override
    public double rate() {
        final double swapletRate = underlyingRate();

        // Pick which pricer's optionlet methods to call: underlying's if we
        // wrap one, else our own.
        final InflationCouponPricer couponPricer = (underlying_ != null) ? underlying_.pricer() : pricer();

        if ( isFloored_ || isCapped_ ) {
            QL.require(couponPricer != null, "pricer not set");
        }

        final double floorletRate;
        if ( isFloored_ ) {
            QL.require(couponPricer instanceof YoYInflationCouponPricer,
                    "YoYInflationCouponPricer required for cap/floor adjustment");
            floorletRate = couponPricer.floorletRate(effectiveFloor());
        } else {
            floorletRate = 0.0;
        }

        final double capletRate;
        if ( isCapped_ ) {
            QL.require(couponPricer instanceof YoYInflationCouponPricer,
                    "YoYInflationCouponPricer required for cap/floor adjustment");
            capletRate = couponPricer.capletRate(effectiveCap());
        } else {
            capletRate = 0.0;
        }

        return swapletRate + floorletRate - capletRate;
    }

    /**
     * The cap as seen by the (positive-gearing) underlying. Returns {@link Constants#NULL_REAL} if no cap is active for
     * the effective direction.
     */
    public double cap() {
        if ( gearing_ > 0 && isCapped_ ) {
            return cap_;
        }
        if ( gearing_ < 0 && isFloored_ ) {
            return floor_;
        }
        return Constants.NULL_REAL;
    }

    /**
     * The floor as seen by the (positive-gearing) underlying. Returns {@link Constants#NULL_REAL} if no floor is
     * active.
     */
    public double floor() {
        if ( gearing_ > 0 && isFloored_ ) {
            return floor_;
        }
        if ( gearing_ < 0 && isCapped_ ) {
            return cap_;
        }
        return Constants.NULL_REAL;
    }

    /**
     * Effective cap of fixing — the cap rescaled from rate space to fixing space: {@code (cap_ - spread) / gearing}.
     * Mirrors C++ inline.
     */
    public double effectiveCap() {
        return (cap_ - spread()) / gearing();
    }

    /** Effective floor of fixing: {@code (floor_ - spread) / gearing}. */
    public double effectiveFloor() {
        return (floor_ - spread()) / gearing();
    }

    public boolean isCapped() {
        return isCapped_;
    }

    public boolean isFloored() {
        return isFloored_;
    }

    //
    // implements Observer
    //

    @Override
    public void update() {
        notifyObservers();
    }

    //
    // implements PolymorphicVisitable
    //

    @Override
    public void accept(final PolymorphicVisitor pv) {
        final Visitor< CappedFlooredYoYInflationCoupon > v = (pv != null) ? pv.visitor(this.getClass()) : null;
        if ( v != null ) {
            v.visit(this);
        } else {
            super.accept(pv);
        }
    }
}
