/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2009 Roland Lichters
 Copyright (C) 2009 Ferdinando Ametrano
 Copyright (C) 2014 Peter Caspers
 Copyright (C) 2017 Joseph Jeisman
 Copyright (C) 2017 Fabrice Lecuyer

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
*/

package org.jquantlib.cashflow;

import org.jquantlib.QL;
import org.jquantlib.math.Closeness;
import org.jquantlib.math.Constants;
import org.jquantlib.time.Date;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;

/**
 * Capped/floored overnight-indexed coupon. Wraps an {@link OvernightIndexedCoupon}
 * and applies a cap and/or floor to either the effective period rate (default)
 * or to each daily fixing.
 * <p>
 * Port of C++ QuantLib v1.42.1
 * {@code ql/cashflows/overnightindexedcoupon.{hpp,cpp}}
 * {@code CappedFlooredOvernightIndexedCoupon}.
 * <p>
 * <b>Phase 5e.5b-CFC:</b> initial port. The naked-option behaviour and
 * effective-cap/floor logic mirror C++ exactly. Pricing is delegated to an
 * {@link OvernightIndexedCouponPricer} (typically a Black variant) via the
 * inherited {@link FloatingRateCoupon#setPricer(FloatingRateCouponPricer)}
 * mechanism.
 *
 * @category cashflows
 *
 * @author JQuantLib migration team
 */
public class CappedFlooredOvernightIndexedCoupon extends FloatingRateCoupon {

    private final OvernightIndexedCoupon underlying_;
    private double cap_;
    private double floor_;
    private final boolean nakedOption_;
    private final boolean dailyCapFloor_;
    private double effectiveCapletVolatility_ = Constants.NULL_REAL;
    private double effectiveFloorletVolatility_ = Constants.NULL_REAL;

    /**
     * Convenience constructor — no naked option, global (period) cap/floor.
     */
    public CappedFlooredOvernightIndexedCoupon(final OvernightIndexedCoupon underlying,
                                               final double cap,
                                               final double floor) {
        this(underlying, cap, floor, false, false);
    }

    /**
     * Full constructor mirroring C++
     * {@code CappedFlooredOvernightIndexedCoupon::CappedFlooredOvernightIndexedCoupon}.
     */
    public CappedFlooredOvernightIndexedCoupon(final OvernightIndexedCoupon underlying,
                                               final double cap,
                                               final double floor,
                                               final boolean nakedOption,
                                               final boolean dailyCapFloor) {
        super(underlying.date(), underlying.nominal(),
              underlying.accrualStartDate(), underlying.accrualEndDate(),
              underlying.fixingDays(), underlying.index(),
              underlying.gearing(), underlying.spread(),
              underlying.referencePeriodStart(), underlying.referencePeriodEnd(),
              underlying.dayCounter(), false /* isInArrears */);
        this.underlying_ = underlying;
        this.nakedOption_ = nakedOption;
        this.dailyCapFloor_ = dailyCapFloor;

        QL.require(!underlying_.compoundSpreadDaily()
                   || Closeness.isCloseEnough(underlying_.gearing(), 1.0),
            "CappedFlooredOvernightIndexedCoupon: if include spread = true, only "
          + "a gearing 1.0 is allowed - scale the notional in this case instead.");

        // C++ swaps cap/floor for negative gearing only when global (not daily).
        if (!dailyCapFloor) {
            if (gearing_ > 0.0) {
                cap_ = cap;
                floor_ = floor;
            } else {
                cap_ = floor;
                floor_ = cap;
            }
        } else {
            cap_ = cap;
            floor_ = floor;
        }
        // Match C++ exactly: the check uses cap_ (post-swap) >= floor (input,
        // pre-swap). This means for positive gearing the check is cap >= floor,
        // and for negative gearing the check is floor >= floor i.e. trivially true.
        // See ql/cashflows/overnightindexedcoupon.cpp v1.42.1 line 300.
        if (cap_ != Constants.NULL_REAL && floor_ != Constants.NULL_REAL) {
            QL.require(cap_ >= floor,
                "cap level (" + cap_ + ") less than floor level (" + floor_ + ")");
        }
        this.underlying_.addObserver(this);
    }

    //
    // public inspectors
    //

    /** Mirrors C++ {@code cap()} — returns the cap on the period rate. */
    public double cap() {
        return gearing_ > 0.0 ? cap_ : floor_;
    }

    /** Mirrors C++ {@code floor()} — returns the floor on the period rate. */
    public double floor() {
        return gearing_ > 0.0 ? floor_ : cap_;
    }

    public boolean isCapped() {
        return cap_ != Constants.NULL_REAL;
    }

    public boolean isFloored() {
        return floor_ != Constants.NULL_REAL;
    }

    public OvernightIndexedCoupon underlying() {
        return underlying_;
    }

    public boolean nakedOption() {
        return nakedOption_;
    }

    public boolean dailyCapFloor() {
        return dailyCapFloor_;
    }

    public boolean compoundSpreadDaily() {
        return underlying_.compoundSpreadDaily();
    }

    public RateAveraging.Type averagingMethod() {
        return underlying_.averagingMethod();
    }

    /**
     * Effective cap of the underlying fixing — depends on
     * {@link #dailyCapFloor()}, {@link OvernightIndexedCoupon#compoundSpreadDaily()},
     * gearing, and spread. See C++
     * {@code CappedFlooredOvernightIndexedCoupon::effectiveCap()} for the four
     * algebraic cases.
     */
    public double effectiveCap() {
        if (cap_ == Constants.NULL_REAL) {
            return Constants.NULL_REAL;
        }
        if (dailyCapFloor_) {
            if (underlying_.compoundSpreadDaily()) {
                return cap_ - underlying_.spread();
            }
            return cap_;
        }
        // global (period-rate) cap
        if (underlying_.compoundSpreadDaily()) {
            // C++: (cap_ / gearing - underlying_->effectiveSpread())
            return (cap_ / gearing()) - underlying_.spread();
        }
        // C++: (cap_ - effectiveSpread) / gearing
        return (cap_ - underlying_.spread()) / gearing();
    }

    public double effectiveFloor() {
        if (floor_ == Constants.NULL_REAL) {
            return Constants.NULL_REAL;
        }
        if (dailyCapFloor_) {
            if (underlying_.compoundSpreadDaily()) {
                return floor_ - underlying_.spread();
            }
            return floor_;
        }
        if (underlying_.compoundSpreadDaily()) {
            return floor_ - underlying_.spread();
        }
        return (floor_ - underlying_.spread()) / gearing();
    }

    public double effectiveCapletVolatility() {
        // ensure rate() has been computed at least once
        rate();
        return effectiveCapletVolatility_;
    }

    public double effectiveFloorletVolatility() {
        rate();
        return effectiveFloorletVolatility_;
    }

    //
    // FloatingRateCoupon overrides
    //

    @Override
    public double rate() {
        QL.require(underlying_.pricer() != null, "underlying coupon pricer not set");

        // C++: nakedOption => swapletRate = 0, otherwise underlying.rate()
        final double swapletRate = nakedOption_ ? 0.0 : underlying_.rate();

        QL.require(pricer_ instanceof OvernightIndexedCouponPricer,
            "coupon pricer not an instance of OvernightIndexedCouponPricer");
        final OvernightIndexedCouponPricer cfPricer =
                (OvernightIndexedCouponPricer) pricer_;

        if (floor_ != Constants.NULL_REAL || cap_ != Constants.NULL_REAL) {
            cfPricer.initialize(this);
        }

        double floorletRate = 0.0;
        if (floor_ != Constants.NULL_REAL) {
            floorletRate = cfPricer.floorletRate(effectiveFloor(), dailyCapFloor_);
        }
        double capletRate = 0.0;
        if (cap_ != Constants.NULL_REAL) {
            final double sign = (nakedOption_ && floor_ == Constants.NULL_REAL)
                    ? -1.0 : 1.0;
            capletRate = sign * cfPricer.capletRate(effectiveCap(), dailyCapFloor_);
        }

        // refresh effective vols (Black variants set them; base returns NULL_REAL).
        effectiveCapletVolatility_ = cfPricer.effectiveCapletVolatility();
        effectiveFloorletVolatility_ = cfPricer.effectiveFloorletVolatility();

        return swapletRate + floorletRate - capletRate;
    }

    @Override
    public double convexityAdjustment() {
        return underlying_.convexityAdjustment();
    }

    @Override
    public Date fixingDate() {
        return underlying_.fixingDate();
    }

    @Override
    public void setPricer(final FloatingRateCouponPricer pricer) {
        QL.require(pricer == null || pricer instanceof OvernightIndexedCouponPricer,
            "The pricer is required to be an instance of OvernightIndexedCouponPricer");
        super.setPricer(pricer);
    }

    @Override
    public void update() {
        notifyObservers();
    }

    @Override
    public void accept(final PolymorphicVisitor pv) {
        final Visitor<CappedFlooredOvernightIndexedCoupon> v =
            (pv != null) ? pv.visitor(this.getClass()) : null;
        if (v != null) {
            v.visit(this);
        } else {
            super.accept(pv);
        }
    }
}
