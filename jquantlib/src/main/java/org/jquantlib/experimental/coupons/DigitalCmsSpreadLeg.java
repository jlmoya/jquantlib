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
 Copyright (C) 2015 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */

package org.jquantlib.experimental.coupons;

import org.jquantlib.QL;
import org.jquantlib.cashflow.DigitalReplication;
import org.jquantlib.cashflow.FixedRateCoupon;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.Position;
import org.jquantlib.math.Constants;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Schedule;

/**
 * Helper class building a sequence of digital CMS-spread coupons.
 * <p>
 * Mirrors C++ QuantLib v1.42.1 {@code DigitalCmsSpreadLeg}
 * (digitalcmsspreadcoupon.hpp lines 57-105 and digitalcmsspreadcoupon.cpp
 * lines 52-202).
 *
 * <p>Java port note: like {@link CmsSpreadLeg}, this avoids the reflection-
 * based {@code FloatingDigitalLeg} infrastructure (which doesn't exist in the
 * Java port today) and constructs the digital coupons directly.
 *
 * @author Peter Caspers (C++ original)
 */
public class DigitalCmsSpreadLeg {

    private final Schedule schedule_;
    private final SwapSpreadIndex index_;
    private Array notionals_ = new Array(0);
    private DayCounter paymentDayCounter_ = new DayCounter();
    private BusinessDayConvention paymentAdjustment_ = BusinessDayConvention.Following;
    private Array fixingDays_ = new Array(0);
    private Array gearings_ = new Array(0);
    private Array spreads_ = new Array(0);
    private boolean inArrears_ = false;
    private Array callStrikes_ = new Array(0);
    private Array callPayoffs_ = new Array(0);
    private Position longCallOption_ = Position.Long;
    private boolean callATM_ = false;
    private Array putStrikes_ = new Array(0);
    private Array putPayoffs_ = new Array(0);
    private Position longPutOption_ = Position.Long;
    private boolean putATM_ = false;
    private DigitalReplication replication_;
    private boolean nakedOption_ = false;


    //
    // public constructor
    //

    public DigitalCmsSpreadLeg(final Schedule schedule, final SwapSpreadIndex index) {
        this.schedule_ = schedule;
        this.index_ = index;
    }


    //
    // builder methods
    //

    public DigitalCmsSpreadLeg withNotionals(final double notional) {
        notionals_ = new Array(1).fill(notional);
        return this;
    }

    public DigitalCmsSpreadLeg withNotionals(final Array notionals) {
        notionals_ = notionals.clone();
        return this;
    }

    public DigitalCmsSpreadLeg withPaymentDayCounter(final DayCounter dc) {
        paymentDayCounter_ = dc;
        return this;
    }

    public DigitalCmsSpreadLeg withPaymentAdjustment(final BusinessDayConvention bdc) {
        paymentAdjustment_ = bdc;
        return this;
    }

    public DigitalCmsSpreadLeg withFixingDays(final int fixingDays) {
        fixingDays_ = new Array(1).fill(fixingDays);
        return this;
    }

    public DigitalCmsSpreadLeg withFixingDays(final Array fixingDays) {
        fixingDays_ = fixingDays.clone();
        return this;
    }

    public DigitalCmsSpreadLeg withGearings(final double gearing) {
        gearings_ = new Array(1).fill(gearing);
        return this;
    }

    public DigitalCmsSpreadLeg withGearings(final Array gearings) {
        gearings_ = gearings.clone();
        return this;
    }

    public DigitalCmsSpreadLeg withSpreads(final double spread) {
        spreads_ = new Array(1).fill(spread);
        return this;
    }

    public DigitalCmsSpreadLeg withSpreads(final Array spreads) {
        spreads_ = spreads.clone();
        return this;
    }

    public DigitalCmsSpreadLeg inArrears(final boolean flag) {
        inArrears_ = flag;
        return this;
    }

    public DigitalCmsSpreadLeg withCallStrikes(final double strike) {
        callStrikes_ = new Array(1).fill(strike);
        return this;
    }

    public DigitalCmsSpreadLeg withCallStrikes(final Array strikes) {
        callStrikes_ = strikes.clone();
        return this;
    }

    public DigitalCmsSpreadLeg withLongCallOption(final Position position) {
        longCallOption_ = position;
        return this;
    }

    public DigitalCmsSpreadLeg withCallATM(final boolean flag) {
        callATM_ = flag;
        return this;
    }

    public DigitalCmsSpreadLeg withCallPayoffs(final double payoff) {
        callPayoffs_ = new Array(1).fill(payoff);
        return this;
    }

    public DigitalCmsSpreadLeg withCallPayoffs(final Array payoffs) {
        callPayoffs_ = payoffs.clone();
        return this;
    }

    public DigitalCmsSpreadLeg withPutStrikes(final double strike) {
        putStrikes_ = new Array(1).fill(strike);
        return this;
    }

    public DigitalCmsSpreadLeg withPutStrikes(final Array strikes) {
        putStrikes_ = strikes.clone();
        return this;
    }

    public DigitalCmsSpreadLeg withLongPutOption(final Position position) {
        longPutOption_ = position;
        return this;
    }

    public DigitalCmsSpreadLeg withPutATM(final boolean flag) {
        putATM_ = flag;
        return this;
    }

    public DigitalCmsSpreadLeg withPutPayoffs(final double payoff) {
        putPayoffs_ = new Array(1).fill(payoff);
        return this;
    }

    public DigitalCmsSpreadLeg withPutPayoffs(final Array payoffs) {
        putPayoffs_ = payoffs.clone();
        return this;
    }

    public DigitalCmsSpreadLeg withReplication(final DigitalReplication replication) {
        replication_ = replication;
        return this;
    }

    public DigitalCmsSpreadLeg withNakedOption(final boolean nakedOption) {
        nakedOption_ = nakedOption;
        return this;
    }


    //
    // build the leg
    //

    /**
     * Materialise the leg.
     * <p>
     * Mirrors C++ {@code DigitalCmsSpreadLeg::operator Leg()}, which dispatches
     * to {@code FloatingDigitalLeg<SwapSpreadIndex, CmsSpreadCoupon, DigitalCmsSpreadCoupon>}.
     */
    public Leg Leg() {
        final int n = schedule_.size() - 1;
        QL.require(notionals_ != null && notionals_.size() <= n,
                "too many nominals (" + notionals_.size() + "), only " + n + " required");
        QL.require(gearings_ != null && gearings_.size() <= n,
                "too many gearings (" + gearings_.size() + "), only " + n + " required");
        QL.require(spreads_ != null && spreads_.size() <= n,
                "too many spreads (" + spreads_.size() + "), only " + n + " required");
        QL.require(callStrikes_ != null && callStrikes_.size() <= n,
                "too many callStrikes (" + callStrikes_.size() + "), only " + n + " required");
        QL.require(putStrikes_ != null && putStrikes_.size() <= n,
                "too many putStrikes (" + putStrikes_.size() + "), only " + n + " required");

        final Leg leg = new Leg(n);
        final Calendar calendar = schedule_.calendar();
        final Date lastPaymentDate = calendar.adjust(schedule_.date(n), paymentAdjustment_);

        for (int i = 0; i < n; ++i) {
            Date refStart = schedule_.date(i);
            Date start = refStart;
            Date refEnd = schedule_.date(i + 1);
            Date end = refEnd;
            final Date paymentDate = calendar.adjust(end, paymentAdjustment_);
            if (i == 0 && !schedule_.isRegular(i + 1)) {
                final BusinessDayConvention bdc = schedule_.businessDayConvention();
                refStart = calendar.adjust(end.sub(schedule_.tenor()), bdc);
            }
            if (i == n - 1 && !schedule_.isRegular(i + 1)) {
                final BusinessDayConvention bdc = schedule_.businessDayConvention();
                refEnd = calendar.adjust(start.add(schedule_.tenor()), bdc);
            }

            final double gearing = get(gearings_, i, 1.0);
            if (gearing == 0.0) {
                // fixed coupon
                leg.add(new FixedRateCoupon(get(notionals_, i, 1.0),
                        paymentDate,
                        get(spreads_, i, 0.0),
                        paymentDayCounter_,
                        start, end, refStart, refEnd));
            } else {
                final CmsSpreadCoupon underlying = new CmsSpreadCoupon(
                        paymentDate,
                        get(notionals_, i, 1.0),
                        start, end,
                        (int) get(fixingDays_, i, index_.fixingDays()),
                        index_,
                        gearing,
                        get(spreads_, i, 0.0),
                        refStart, refEnd,
                        paymentDayCounter_,
                        inArrears_);

                leg.add(new DigitalCmsSpreadCoupon(
                        underlying,
                        get(callStrikes_, i, Constants.NULL_REAL),
                        longCallOption_,
                        callATM_,
                        get(callPayoffs_, i, Constants.NULL_REAL),
                        get(putStrikes_, i, Constants.NULL_REAL),
                        longPutOption_,
                        putATM_,
                        get(putPayoffs_, i, Constants.NULL_REAL),
                        replication_,
                        nakedOption_));
            }
        }
        return leg;
    }


    //
    // helpers
    //

    private static double get(final Array v, final int i, final double defaultValue) {
        if (v == null || v.empty()) {
            return defaultValue;
        }
        if (i < v.size()) {
            return v.get(i);
        }
        return v.get(v.size() - 1);
    }
}
