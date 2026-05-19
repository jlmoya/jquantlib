/*
 Copyright (C) 2009 John Nichol

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
 Copyright (C) 2007 Ferdinando Ametrano
 Copyright (C) 2007 Chiara Fornarola

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <http://quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
 */

package org.jquantlib.instruments.bonds;

import org.jquantlib.QL;
import org.jquantlib.cashflow.IborLeg;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.instruments.Bond;
import org.jquantlib.math.Constants;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.quotes.Handle;
import org.jquantlib.time.*;

/**
 * floating-rate bond (possibly capped and/or floored)
 *
 * @author John Nichol
 * @author Zahid Hussain
 * @category instruments
 */
//TEST: calculations are tested by checking results against cached values.
public class FloatingRateBond extends Bond {
    public FloatingRateBond(final int settlementDays, final double faceAmount, final Schedule schedule,
            final IborIndex index, final DayCounter paymentDayCounter, final BusinessDayConvention paymentConvention,
            final int fixingDays, final Array gearings, final Array spreads, final Array caps, final Array floors,
            final boolean inArrears, final double redemption, final Date issueDate) {
        // Phase 5e.5b-CFC-d-179 — delegate to the extended (C++ v1.42.1
        // signature) ctor with default fixingConvention=Preceding so
        // every existing call-site is bit-identical.
        this(settlementDays, faceAmount, schedule, index, paymentDayCounter, paymentConvention, fixingDays, gearings,
                spreads, caps, floors, inArrears, redemption, issueDate, BusinessDayConvention.Preceding);
    }

    /**
     * Phase 5e.5b-CFC-d-179 — mirror of C++
     * {@code FloatingRateBond(Natural settlementDays, Real faceAmount, Schedule, ext::shared_ptr<IborIndex>,
     * DayCounter, BusinessDayConvention paymentConvention=Following, Natural fixingDays=Null<Natural>(),
     * std::vector<Real> gearings={1.0}, std::vector<Spread> spreads={0.0}, std::vector<Rate> caps={}, std::vector<Rate>
     * floors={}, bool inArrears=false, Real redemption=100.0, Date issueDate=Date(), Period exCouponPeriod=Period(),
     * Calendar exCouponCalendar=Calendar(), BusinessDayConvention exCouponConvention=Unadjusted, bool
     * exCouponEndOfMonth=false, BusinessDayConvention fixingConvention=Preceding)}
     * (ql/instruments/bonds/floatingratebond.hpp:42-63 v1.42.1).
     *
     * <p>This commit threads the trailing {@code fixingConvention} alone;
     * the {@code exCoupon*} arguments are still serviced via the existing {@link IborLeg#withExCouponPeriod} fluent
     * setter on the FixedRateBond side (a parallel agent owns the FloatingRateCoupon ex-coupon field). Mirroring the
     * full C++ signature here keeps the API surface aligned for future ex-coupon threading without breaking existing
     * callers.
     */
    public FloatingRateBond(final int settlementDays, final double faceAmount, final Schedule schedule,
            final IborIndex index, final DayCounter paymentDayCounter, final BusinessDayConvention paymentConvention,
            final int fixingDays, final Array gearings, final Array spreads, final Array caps, final Array floors,
            final boolean inArrears, final double redemption, final Date issueDate,
            final BusinessDayConvention fixingConvention) {

        super(settlementDays, schedule.calendar(), issueDate);
        maturityDate_ = schedule.endDate().clone();

        cashflows_ = new IborLeg(schedule, index).withNotionals(faceAmount).withPaymentDayCounter(paymentDayCounter)
                .withPaymentAdjustment(paymentConvention).withFixingDays(fixingDays).withGearings(gearings)
                .withSpreads(spreads).withCaps(caps).withFloors(floors).inArrears(inArrears)
                .withFixingConvention(fixingConvention).Leg();

        addRedemptionsToCashflows(new double[] { redemption });

        QL.ensure(!cashflows().isEmpty(), "bond with no cashflows!");
        QL.ensure(redemptions_.size() == 1, "multiple redemptions created");
        index.addObserver(this);
    }

    public FloatingRateBond(final int settlementDays, final double faceAmount, final Schedule schedule,
            final IborIndex index, final DayCounter accrualDayCounter) {
        this(settlementDays, faceAmount, schedule, index, accrualDayCounter, BusinessDayConvention.Following,
                Constants.NULL_INTEGER, new Array(new double[] { 1.0 }), new Array(new double[] { 0.0 }), new Array(0),
                new Array(0), false, 100.0, new Date());

    }

    public FloatingRateBond(final int settlementDays, final double faceAmount, final Date startDate,
            final Date maturityDate, final Frequency couponFrequency, final Calendar calendar,
            final Handle< IborIndex > index, final DayCounter accrualDayCounter,
            final BusinessDayConvention accrualConvention, final BusinessDayConvention paymentConvention,
            final int fixingDays, final Array gearings, final Array spreads, final Array caps, final Array floors,
            final boolean inArrears, final double redemption, final Date issueDate, final Date stubDate,
            final DateGeneration.Rule rule, final boolean endOfMonth) {
        super(settlementDays, calendar, issueDate);

        maturityDate_ = maturityDate.clone();

        Date firstDate = null, nextToLastDate = null;
        switch ( rule ) {
        case Backward:
            firstDate = new Date();
            nextToLastDate = stubDate;
            break;
        case Forward:
            firstDate = stubDate;
            nextToLastDate = new Date();
            break;
        case Zero:
        case ThirdWednesday:
        case Twentieth:
        case TwentiethIMM:
            QL.error("stub date (" + stubDate + ") not allowed with " + rule + " DateGeneration::Rule");
        default:
            QL.error("unknown DateGeneration::Rule (" + rule + ")");
        }

        Schedule schedule = new Schedule(startDate, maturityDate_, new Period(couponFrequency), calendar_,
                accrualConvention, accrualConvention, rule, endOfMonth, firstDate, nextToLastDate);

        cashflows_ = new IborLeg(schedule, index.currentLink()).withNotionals(faceAmount)
                .withPaymentDayCounter(accrualDayCounter).withPaymentAdjustment(paymentConvention)
                .withFixingDays(fixingDays).withGearings(gearings).withSpreads(spreads).withCaps(caps)
                .withFloors(floors).inArrears(inArrears).Leg();

        addRedemptionsToCashflows(new double[] { redemption });

        QL.ensure(!cashflows().isEmpty(), "bond with no cashflows!");
        QL.ensure(redemptions_.size() == 1, "multiple redemptions created");

        index.addObserver(this);
    }

    public FloatingRateBond(final int settlementDays, final double faceAmount, final Date startDate,
            final Date maturityDate, final Frequency couponFrequency, final Calendar calendar,
            final Handle< IborIndex > index, final DayCounter accrualDayCounter) {
        this(settlementDays, faceAmount, startDate, maturityDate, couponFrequency, calendar, index, accrualDayCounter,
                BusinessDayConvention.Following, BusinessDayConvention.Following, Constants.NULL_INTEGER,
                new Array(new double[] { 1.0 }), new Array(new double[] { 0.0 }), new Array(0), new Array(0), false,
                100.0, new Date(), new Date(), DateGeneration.Rule.Backward, false);
    }
}
