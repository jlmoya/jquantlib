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
 Copyright (C) 2014 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */

package org.jquantlib.experimental.coupons;

import org.jquantlib.QL;
import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.FixedRateCoupon;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.Constants;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Schedule;

/**
 * Helper class building a sequence of capped/floored CMS-spread coupons.
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/experimental/coupons/cmsspreadcoupon.hpp} (the {@code CmsSpreadLeg} class
 * lines 106-137) and {@code cmsspreadcoupon.cpp} lines 48-145.
 *
 * <p>Java port note: rather than going through the reflection-based
 * {@link org.jquantlib.cashflow.FloatingLeg} (which is hard-wired to {@code IborIndex.class} in its constructor
 * lookup), we construct the coupons directly here, mirroring the same control flow as
 * {@code FloatingLeg<SwapSpreadIndex, CmsSpreadCoupon, CappedFlooredCmsSpreadCoupon>} in C++.
 *
 * @author Peter Caspers (C++ original)
 */
public class CmsSpreadLeg {

    private final Schedule schedule_;
    private final SwapSpreadIndex swapSpreadIndex_;
    private Array notionals_ = new Array(0);
    private DayCounter paymentDayCounter_ = new DayCounter();
    private BusinessDayConvention paymentAdjustment_ = BusinessDayConvention.Following;
    private Array fixingDays_ = new Array(0);
    private Array gearings_ = new Array(0);
    private Array spreads_ = new Array(0);
    private Array caps_ = new Array(0);
    private Array floors_ = new Array(0);
    private boolean inArrears_ = false;
    private boolean zeroPayments_ = false;

    //
    // public constructor
    //

    public CmsSpreadLeg(final Schedule schedule, final SwapSpreadIndex swapSpreadIndex) {
        QL.require(swapSpreadIndex != null, "no index provided");
        this.schedule_ = schedule;
        this.swapSpreadIndex_ = swapSpreadIndex;
    }

    //
    // builder methods (matching C++ withXxx fluent API)
    //

    private static double get(final Array v, final int i, final double defaultValue) {
        if ( v == null || v.empty() ) {
            return defaultValue;
        }
        if ( i < v.size() ) {
            return v.get(i);
        }
        return v.get(v.size() - 1);
    }

    private static double effectiveFixedRate(final Array spreads, final Array caps, final Array floors, final int i) {
        double result = get(spreads, i, 0.0);
        final double floor = get(floors, i, Constants.NULL_REAL);
        if ( floor != Constants.NULL_REAL ) {
            result = Math.max(floor, result);
        }
        final double cap = get(caps, i, Constants.NULL_REAL);
        if ( cap != Constants.NULL_REAL ) {
            result = Math.min(cap, result);
        }
        return result;
    }

    private static boolean noOption(final Array caps, final Array floors, final int i) {
        return get(caps, i, Constants.NULL_REAL) == Constants.NULL_REAL
                && get(floors, i, Constants.NULL_REAL) == Constants.NULL_REAL;
    }

    public CmsSpreadLeg withNotionals(final double notional) {
        notionals_ = new Array(1).fill(notional);
        return this;
    }

    public CmsSpreadLeg withNotionals(final Array notionals) {
        notionals_ = notionals.clone();
        return this;
    }

    public CmsSpreadLeg withPaymentDayCounter(final DayCounter dayCounter) {
        paymentDayCounter_ = dayCounter;
        return this;
    }

    public CmsSpreadLeg withPaymentAdjustment(final BusinessDayConvention convention) {
        paymentAdjustment_ = convention;
        return this;
    }

    public CmsSpreadLeg withFixingDays(final int fixingDays) {
        fixingDays_ = new Array(1).fill(fixingDays);
        return this;
    }

    public CmsSpreadLeg withFixingDays(final Array fixingDays) {
        fixingDays_ = fixingDays.clone();
        return this;
    }

    public CmsSpreadLeg withGearings(final double gearing) {
        gearings_ = new Array(1).fill(gearing);
        return this;
    }

    public CmsSpreadLeg withGearings(final Array gearings) {
        gearings_ = gearings.clone();
        return this;
    }

    public CmsSpreadLeg withSpreads(final double spread) {
        spreads_ = new Array(1).fill(spread);
        return this;
    }

    public CmsSpreadLeg withSpreads(final Array spreads) {
        spreads_ = spreads.clone();
        return this;
    }

    public CmsSpreadLeg withCaps(final double cap) {
        caps_ = new Array(1).fill(cap);
        return this;
    }

    public CmsSpreadLeg withCaps(final Array caps) {
        caps_ = caps.clone();
        return this;
    }

    public CmsSpreadLeg withFloors(final double floor) {
        floors_ = new Array(1).fill(floor);
        return this;
    }

    //
    // build the leg
    //

    public CmsSpreadLeg withFloors(final Array floors) {
        floors_ = floors.clone();
        return this;
    }

    //
    // helpers (mirror FloatingLeg.get / effectiveFixedRate / noOption)
    //

    public CmsSpreadLeg inArrears(final boolean flag) {
        inArrears_ = flag;
        return this;
    }

    public CmsSpreadLeg withZeroPayments(final boolean flag) {
        zeroPayments_ = flag;
        return this;
    }

    /**
     * Materialise the leg.
     * <p>
     * Mirrors C++ {@code CmsSpreadLeg::operator Leg()}, which dispatches to
     * {@code FloatingLeg<SwapSpreadIndex, CmsSpreadCoupon, CappedFlooredCmsSpreadCoupon>}.
     */
    public Leg Leg() {
        final int n = schedule_.size() - 1;
        QL.require(notionals_ != null && notionals_.size() <= n,
                "too many nominals (" + notionals_.size() + "), only " + n + " required");
        QL.require(gearings_ != null && gearings_.size() <= n,
                "too many gearings (" + gearings_.size() + "), only " + n + " required");
        QL.require(spreads_ != null && spreads_.size() <= n,
                "too many spreads (" + spreads_.size() + "), only " + n + " required");
        QL.require(caps_ != null && caps_.size() <= n, "too many caps (" + caps_.size() + "), only " + n + " required");
        QL.require(floors_ != null && floors_.size() <= n,
                "too many floors (" + floors_.size() + "), only " + n + " required");
        QL.require(!zeroPayments_ || !inArrears_, "in-arrears and zero features are not compatible");

        final Leg leg = new Leg(n);
        final Calendar calendar = schedule_.calendar();
        final Date lastPaymentDate = calendar.adjust(schedule_.date(n), paymentAdjustment_);

        for ( int i = 0; i < n; ++i ) {
            Date refStart = schedule_.date(i);
            Date start = refStart;
            Date refEnd = schedule_.date(i + 1);
            Date end = refEnd;
            final Date paymentDate = zeroPayments_ ? lastPaymentDate : calendar.adjust(end, paymentAdjustment_);
            if ( i == 0 && !schedule_.isRegular(i + 1) ) {
                final BusinessDayConvention bdc = schedule_.businessDayConvention();
                refStart = calendar.adjust(end.sub(schedule_.tenor()), bdc);
            }
            if ( i == n - 1 && !schedule_.isRegular(i + 1) ) {
                final BusinessDayConvention bdc = schedule_.businessDayConvention();
                refEnd = calendar.adjust(start.add(schedule_.tenor()), bdc);
            }

            final double gearing = get(gearings_, i, 1.0);
            if ( gearing == 0.0 ) {
                // fixed coupon
                leg.add(new FixedRateCoupon(get(notionals_, i, 1.0), paymentDate,
                        effectiveFixedRate(spreads_, caps_, floors_, i), paymentDayCounter_, start, end, refStart,
                        refEnd));
            } else if ( noOption(caps_, floors_, i) ) {
                leg.add(new CmsSpreadCoupon(paymentDate, get(notionals_, i, 1.0), start, end,
                        (int) get(fixingDays_, i, swapSpreadIndex_.fixingDays()), swapSpreadIndex_, gearing,
                        get(spreads_, i, 0.0), refStart, refEnd, paymentDayCounter_, inArrears_));
            } else {
                final double cap = get(caps_, i, Constants.NULL_REAL);
                final double floor = get(floors_, i, Constants.NULL_REAL);
                leg.add(new CappedFlooredCmsSpreadCoupon(paymentDate, get(notionals_, i, 1.0), start, end,
                        (int) get(fixingDays_, i, swapSpreadIndex_.fixingDays()), swapSpreadIndex_, gearing,
                        get(spreads_, i, 0.0), cap, floor, refStart, refEnd, paymentDayCounter_, inArrears_));
            }
        }
        return leg;
    }
}
