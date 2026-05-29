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
 Copyright (C) 2007 Cristina Duminuco
 Copyright (C) 2007 Giorgio Facchinetti
 Copyright (C) 2007 StatPro Italia srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */

package org.jquantlib.cashflow;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.SwapIndex;
import org.jquantlib.instruments.Position;
import org.jquantlib.math.Constants;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Schedule;

/**
 * Helper class building a sequence of digital CMS-rate coupons.
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/cashflows/digitalcmscoupon.hpp/cpp} ({@code class DigitalCmsLeg}).
 *
 * <p>Java port note: like {@link CmsLeg}, this avoids the reflection-based
 * {@code FloatingDigitalLeg} template (which does not exist in the Java port) and constructs the {@link CmsCoupon}
 * underlying + {@link DigitalCmsCoupon} wrapper directly, mirroring C++
 * {@code FloatingDigitalLeg<SwapIndex, CmsCoupon, DigitalCmsCoupon>} (cashflowvectors.hpp:224-315). This is the same
 * approach used by the existing experimental {@code DigitalCmsSpreadLeg}.
 *
 * @author Cristina Duminuco (C++ original)
 * @author Giorgio Facchinetti (C++ original)
 */
public class DigitalCmsLeg {

    private final Schedule schedule_;
    private final SwapIndex index_;
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

    public DigitalCmsLeg(final Schedule schedule, final SwapIndex index) {
        this.schedule_ = schedule;
        this.index_ = index;
    }

    //
    // builder methods (mirror digitalcmscoupon.cpp:57-191)
    //

    public DigitalCmsLeg withNotionals(final double notional) {
        notionals_ = new Array(1).fill(notional);
        return this;
    }

    public DigitalCmsLeg withNotionals(final Array notionals) {
        notionals_ = notionals.clone();
        return this;
    }

    public DigitalCmsLeg withPaymentDayCounter(final DayCounter dc) {
        paymentDayCounter_ = dc;
        return this;
    }

    public DigitalCmsLeg withPaymentAdjustment(final BusinessDayConvention bdc) {
        paymentAdjustment_ = bdc;
        return this;
    }

    public DigitalCmsLeg withFixingDays(final int fixingDays) {
        fixingDays_ = new Array(1).fill(fixingDays);
        return this;
    }

    public DigitalCmsLeg withFixingDays(final Array fixingDays) {
        fixingDays_ = fixingDays.clone();
        return this;
    }

    public DigitalCmsLeg withGearings(final double gearing) {
        gearings_ = new Array(1).fill(gearing);
        return this;
    }

    public DigitalCmsLeg withGearings(final Array gearings) {
        gearings_ = gearings.clone();
        return this;
    }

    public DigitalCmsLeg withSpreads(final double spread) {
        spreads_ = new Array(1).fill(spread);
        return this;
    }

    public DigitalCmsLeg withSpreads(final Array spreads) {
        spreads_ = spreads.clone();
        return this;
    }

    public DigitalCmsLeg inArrears(final boolean flag) {
        inArrears_ = flag;
        return this;
    }

    /** Convenience overload mirroring C++ default {@code inArrears(bool flag = true)}. */
    public DigitalCmsLeg inArrears() {
        return inArrears(true);
    }

    public DigitalCmsLeg withCallStrikes(final double strike) {
        callStrikes_ = new Array(1).fill(strike);
        return this;
    }

    public DigitalCmsLeg withCallStrikes(final Array strikes) {
        callStrikes_ = strikes.clone();
        return this;
    }

    public DigitalCmsLeg withLongCallOption(final Position position) {
        longCallOption_ = position;
        return this;
    }

    public DigitalCmsLeg withCallATM(final boolean flag) {
        callATM_ = flag;
        return this;
    }

    public DigitalCmsLeg withCallATM() {
        return withCallATM(true);
    }

    public DigitalCmsLeg withCallPayoffs(final double payoff) {
        callPayoffs_ = new Array(1).fill(payoff);
        return this;
    }

    public DigitalCmsLeg withCallPayoffs(final Array payoffs) {
        callPayoffs_ = payoffs.clone();
        return this;
    }

    public DigitalCmsLeg withPutStrikes(final double strike) {
        putStrikes_ = new Array(1).fill(strike);
        return this;
    }

    public DigitalCmsLeg withPutStrikes(final Array strikes) {
        putStrikes_ = strikes.clone();
        return this;
    }

    public DigitalCmsLeg withLongPutOption(final Position position) {
        longPutOption_ = position;
        return this;
    }

    public DigitalCmsLeg withPutATM(final boolean flag) {
        putATM_ = flag;
        return this;
    }

    public DigitalCmsLeg withPutATM() {
        return withPutATM(true);
    }

    public DigitalCmsLeg withPutPayoffs(final double payoff) {
        putPayoffs_ = new Array(1).fill(payoff);
        return this;
    }

    public DigitalCmsLeg withPutPayoffs(final Array payoffs) {
        putPayoffs_ = payoffs.clone();
        return this;
    }

    public DigitalCmsLeg withReplication(final DigitalReplication replication) {
        replication_ = replication;
        return this;
    }

    public DigitalCmsLeg withNakedOption(final boolean nakedOption) {
        nakedOption_ = nakedOption;
        return this;
    }

    public DigitalCmsLeg withNakedOption() {
        return withNakedOption(true);
    }

    //
    // build the leg
    //

    /**
     * Materialise the leg. Mirror of C++ {@code DigitalCmsLeg::operator Leg()} (digitalcmscoupon.cpp:193-203), which
     * dispatches to {@code FloatingDigitalLeg<SwapIndex, CmsCoupon, DigitalCmsCoupon>}.
     */
    public Leg Leg() {
        final int n = schedule_.size() - 1;
        QL.require(notionals_ != null && !notionals_.empty(), "no notional given");
        QL.require(notionals_.size() <= n, "too many nominals (" + notionals_.size() + "), only " + n + " required");
        QL.require(gearings_ == null || gearings_.size() <= n,
                "too many gearings (" + gearings_.size() + "), only " + n + " required");
        QL.require(spreads_ == null || spreads_.size() <= n,
                "too many spreads (" + spreads_.size() + "), only " + n + " required");
        QL.require(callStrikes_ == null || callStrikes_.size() <= n,
                "too many call rates (" + callStrikes_.size() + "), only " + n + " required");
        QL.require(putStrikes_ == null || putStrikes_.size() <= n,
                "too many put rates (" + putStrikes_.size() + "), only " + n + " required");

        final Leg leg = new Leg(n);
        final Calendar calendar = schedule_.calendar();

        for ( int i = 0; i < n; ++i ) {
            Date refStart = schedule_.date(i);
            Date start = refStart;
            Date refEnd = schedule_.date(i + 1);
            final Date end = refEnd;
            final Date paymentDate = calendar.adjust(end, paymentAdjustment_);
            if ( i == 0 && !schedule_.isRegular(i + 1) ) {
                final BusinessDayConvention bdc = schedule_.businessDayConvention();
                refStart = calendar.adjust(end.sub(schedule_.tenor()), bdc);
            }
            if ( i == n - 1 && !schedule_.isRegular(i + 1) ) {
                final BusinessDayConvention bdc = schedule_.businessDayConvention();
                refEnd = calendar.adjust(start.add(schedule_.tenor()), bdc);
            }

            final double gearing = DigitalLegUtil.get(gearings_, i, 1.0);
            if ( gearing == 0.0 ) {
                // fixed coupon: C++ FloatingDigitalLeg (cashflowvectors.hpp:286) defaults the
                // fixed RATE to 1.0 when `spreads` is empty (the floating branch below separately
                // defaults the underlying spread to 0.0). Faithful port of v1.42.1, incl. this quirk.
                leg.add(new FixedRateCoupon(DigitalLegUtil.get(notionals_, i, 1.0), paymentDate,
                        DigitalLegUtil.get(spreads_, i, 1.0), paymentDayCounter_, start, end, refStart, refEnd));
            } else {
                final CmsCoupon underlying = new CmsCoupon(paymentDate, DigitalLegUtil.get(notionals_, i, 1.0), start,
                        end, (int) DigitalLegUtil.get(fixingDays_, i, index_.fixingDays()), index_, gearing,
                        DigitalLegUtil.get(spreads_, i, 0.0), refStart, refEnd, paymentDayCounter_, inArrears_);

                leg.add(new DigitalCmsCoupon(underlying, DigitalLegUtil.get(callStrikes_, i, Constants.NULL_REAL),
                        longCallOption_, callATM_, DigitalLegUtil.get(callPayoffs_, i, Constants.NULL_REAL),
                        DigitalLegUtil.get(putStrikes_, i, Constants.NULL_REAL), longPutOption_, putATM_,
                        DigitalLegUtil.get(putPayoffs_, i, Constants.NULL_REAL), replication_, nakedOption_));
            }
        }
        return leg;
    }

}
