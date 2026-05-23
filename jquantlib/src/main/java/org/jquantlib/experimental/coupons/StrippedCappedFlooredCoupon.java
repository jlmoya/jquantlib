/*
 Copyright (C) 2014 Peter Caspers (C++)

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

package org.jquantlib.experimental.coupons;

import org.jquantlib.QL;
import org.jquantlib.cashflow.CappedFlooredCoupon;
import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.FloatingRateCoupon;
import org.jquantlib.cashflow.FloatingRateCouponPricer;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;

/**
 * Strips the embedded option from cap floored coupons.
 *
 * <p>Port of {@code ql/experimental/coupons/strippedcapflooredcoupon.{hpp,cpp}}
 * from C++ QuantLib v1.42.1.
 *
 * <p>Given a {@link CappedFlooredCoupon}, returns the value of just the embedded
 * option(s): floor-cap collar when both are present, or the long floor / long cap
 * when only one is present.
 *
 * @author Jose Moya
 */
public class StrippedCappedFlooredCoupon extends FloatingRateCoupon {

    protected final CappedFlooredCoupon underlying_;

    public StrippedCappedFlooredCoupon(final CappedFlooredCoupon underlying) {
        super(underlying.date(), underlying.nominal(), underlying.accrualStartDate(), underlying.accrualEndDate(),
              underlying.fixingDays(), underlying.index(), underlying.gearing(), underlying.spread(),
              underlying.referencePeriodStart(), underlying.referencePeriodEnd(), underlying.dayCounter(),
              underlying.isInArrears());
        this.underlying_ = underlying;
        underlying.addObserver(this);
    }

    @Override
    public double rate() {
        QL.require(underlying_.underlying().pricer() != null, "pricer not set");
        underlying_.underlying().pricer().initialize(underlying_.underlying());
        double floorletRate = 0.0;
        if (underlying_.isFloored()) {
            floorletRate = underlying_.underlying().pricer().floorletRate(underlying_.effectiveFloor());
        }
        double capletRate = 0.0;
        if (underlying_.isCapped()) {
            capletRate = underlying_.underlying().pricer().capletRate(underlying_.effectiveCap());
        }
        // If the underlying is collared, return the embedded collar; otherwise the
        // value of a long floor (or long cap) respectively.
        return (underlying_.isFloored() && underlying_.isCapped())
                ? (floorletRate - capletRate)
                : (floorletRate + capletRate);
    }

    @Override
    public double convexityAdjustment() {
        return underlying_.convexityAdjustment();
    }

    public double cap() {
        return underlying_.cap();
    }

    public double floor() {
        return underlying_.floor();
    }

    public double effectiveCap() {
        return underlying_.effectiveCap();
    }

    public double effectiveFloor() {
        return underlying_.effectiveFloor();
    }

    public boolean isCap() {
        return underlying_.isCapped();
    }

    public boolean isFloor() {
        return underlying_.isFloored();
    }

    public boolean isCollar() {
        return isCap() && isFloor();
    }

    @Override
    public void setPricer(final FloatingRateCouponPricer pricer) {
        super.setPricer(pricer);
        underlying_.setPricer(pricer);
    }

    public CappedFlooredCoupon underlying() {
        return underlying_;
    }

    @Override
    public void accept(final PolymorphicVisitor pv) {
        underlying_.accept(pv);
        final Visitor<StrippedCappedFlooredCoupon> v = (pv != null) ? pv.visitor(this.getClass()) : null;
        if (v != null) {
            v.visit(this);
        } else {
            super.accept(pv);
        }
    }

    /**
     * Helper that converts a {@link Leg} of arbitrary cash flows, replacing each
     * {@link CappedFlooredCoupon} with its stripped counterpart and leaving
     * other cash flows untouched.
     */
    public static final class StrippedCappedFlooredCouponLeg {
        private final Leg underlyingLeg_;

        public StrippedCappedFlooredCouponLeg(final Leg underlyingLeg) {
            this.underlyingLeg_ = underlyingLeg;
        }

        public Leg toLeg() {
            final Leg resultLeg = new Leg(underlyingLeg_.size());
            for (final CashFlow cf : underlyingLeg_) {
                if (cf instanceof CappedFlooredCoupon) {
                    resultLeg.add(new StrippedCappedFlooredCoupon((CappedFlooredCoupon) cf));
                } else {
                    resultLeg.add(cf);
                }
            }
            return resultLeg;
        }
    }
}
