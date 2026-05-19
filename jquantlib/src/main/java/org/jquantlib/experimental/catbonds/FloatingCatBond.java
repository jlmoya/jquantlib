/*
 Copyright (C) 2012, 2013 Grzegorz Andruszkiewicz
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
*/

package org.jquantlib.experimental.catbonds;

import org.jquantlib.QL;
import org.jquantlib.cashflow.IborLeg;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.math.Constants;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.time.*;

/**
 * Floating-rate catastrophe bond (possibly capped and/or floored).
 *
 * <p>Port of {@code ql/experimental/catbonds/catbond.hpp/.cpp}
 * {@code FloatingCatBond}.
 *
 * @category instruments
 */
public class FloatingCatBond extends CatBond {

    /**
     * Constructor taking an already-built {@link Schedule}.
     */
    public FloatingCatBond(final int settlementDays, final double faceAmount, final Schedule schedule,
            final IborIndex iborIndex, final DayCounter accrualDayCounter, final NotionalRisk notionalRisk,
            final BusinessDayConvention paymentConvention, final int fixingDays, final Array gearings,
            final Array spreads, final Array caps, final Array floors, final boolean inArrears, final double redemption,
            final Date issueDate) {

        super(settlementDays, schedule.calendar(), issueDate, notionalRisk);

        maturityDate_ = schedule.endDate().clone();

        cashflows_ = new IborLeg(schedule, iborIndex).withNotionals(faceAmount).withPaymentDayCounter(accrualDayCounter)
                .withPaymentAdjustment(paymentConvention).withFixingDays(fixingDays).withGearings(gearings)
                .withSpreads(spreads).withCaps(caps).withFloors(floors).inArrears(inArrears).Leg();

        addRedemptionsToCashflows(new double[] { redemption });

        QL.ensure(!cashflows().isEmpty(), "bond with no cashflows!");
        QL.ensure(redemptions_.size() == 1, "multiple redemptions created");

        iborIndex.addObserver(this);
    }

    /**
     * Convenience constructor with default gearings/spreads/caps/floors.
     */
    public FloatingCatBond(final int settlementDays, final double faceAmount, final Schedule schedule,
            final IborIndex iborIndex, final DayCounter accrualDayCounter, final NotionalRisk notionalRisk) {

        this(settlementDays, faceAmount, schedule, iborIndex, accrualDayCounter, notionalRisk,
                BusinessDayConvention.Following, Constants.NULL_INTEGER, new Array(new double[] { 1.0 }),
                new Array(new double[] { 0.0 }), new Array(0), new Array(0), false, 100.0, new Date());
    }

    /**
     * Constructor that builds the schedule from start/maturity parameters.
     */
    public FloatingCatBond(final int settlementDays, final double faceAmount, final Date startDate,
            final Date maturityDate, final Frequency couponFrequency, final Calendar calendar,
            final IborIndex iborIndex, final DayCounter accrualDayCounter, final NotionalRisk notionalRisk,
            final BusinessDayConvention accrualConvention, final BusinessDayConvention paymentConvention,
            final int fixingDays, final Array gearings, final Array spreads, final Array caps, final Array floors,
            final boolean inArrears, final double redemption, final Date issueDate, final Date stubDate,
            final DateGeneration.Rule rule, final boolean endOfMonth) {

        super(settlementDays, calendar, issueDate, notionalRisk);

        maturityDate_ = maturityDate.clone();

        Date firstDate, nextToLastDate;
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
            throw new IllegalArgumentException(
                    "stub date (" + stubDate + ") not allowed with " + rule + " DateGeneration.Rule");
        default:
            throw new IllegalArgumentException("unknown DateGeneration.Rule (" + rule + ")");
        }

        final Schedule schedule = new Schedule(startDate, maturityDate_, new Period(couponFrequency), calendar,
                accrualConvention, accrualConvention, rule, endOfMonth, firstDate, nextToLastDate);

        cashflows_ = new IborLeg(schedule, iborIndex).withNotionals(faceAmount).withPaymentDayCounter(accrualDayCounter)
                .withPaymentAdjustment(paymentConvention).withFixingDays(fixingDays).withGearings(gearings)
                .withSpreads(spreads).withCaps(caps).withFloors(floors).inArrears(inArrears).Leg();

        addRedemptionsToCashflows(new double[] { redemption });

        QL.ensure(!cashflows().isEmpty(), "bond with no cashflows!");
        QL.ensure(redemptions_.size() == 1, "multiple redemptions created");

        iborIndex.addObserver(this);
    }
}
