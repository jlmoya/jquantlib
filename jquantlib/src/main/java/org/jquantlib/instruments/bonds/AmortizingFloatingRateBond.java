/*
 Copyright (C) 2026 Jose Moya

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
 Copyright (C) 2008 Simon Ibbotson

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.
*/

package org.jquantlib.instruments.bonds;

import org.jquantlib.QL;
import org.jquantlib.cashflow.IborLeg;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.instruments.Bond;
import org.jquantlib.math.Constants;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.Schedule;

/**
 * Amortizing floating-rate bond (possibly capped and/or floored).
 *
 * Java port of QuantLib v1.42.1
 * {@code ql/instruments/bonds/amortizingfloatingratebond.{hpp,cpp}}.
 *
 * <p>The notional vector encodes the amortization schedule. The full C++
 * constructor surface (paymentLag, ex-coupon-period vector, redemptions
 * vector) is exposed insofar as the existing Java {@link IborLeg} builder
 * supports it; the un-supported builder methods (withPaymentLag,
 * withExCouponPeriod) are deferred to Phase 5d.5-Bonds-b. The simplest
 * 5-arg overload mirrors the {@code FloatingRateBond} convention used
 * elsewhere in the Java tree.
 *
 * @author Jose Moya
 */
public class AmortizingFloatingRateBond extends Bond {

    /**
     * Primary constructor — mirrors the simplest overload, parameterized
     * by an explicit gearings/spreads/caps/floors/inArrears tuple.
     */
    public AmortizingFloatingRateBond(final /* @Natural */ int settlementDays,
                                        final double[] notionals,
                                        final Schedule schedule,
                                        final IborIndex index,
                                        final DayCounter accrualDayCounter,
                                        final BusinessDayConvention paymentConvention,
                                        final /* @Natural */ int fixingDays,
                                        final Array gearings,
                                        final Array spreads,
                                        final Array caps,
                                        final Array floors,
                                        final boolean inArrears,
                                        final double[] redemptions,
                                        final Date issueDate) {
        super(settlementDays, schedule.calendar(), issueDate);
        maturityDate_ = schedule.endDate().clone();

        cashflows_ = new IborLeg(schedule, index)
                        .withNotionals(new Array(notionals))
                        .withPaymentDayCounter(accrualDayCounter)
                        .withPaymentAdjustment(paymentConvention)
                        .withFixingDays(fixingDays)
                        .withGearings(gearings)
                        .withSpreads(spreads)
                        .withCaps(caps)
                        .withFloors(floors)
                        .inArrears(inArrears)
                        .Leg();

        addRedemptionsToCashflows(redemptions);

        QL.ensure(!cashflows().isEmpty(), "bond with no cashflows!");
        index.addObserver(this);
    }

    /** Convenience overload mirroring the simplest C++ defaulting. */
    public AmortizingFloatingRateBond(final /* @Natural */ int settlementDays,
                                        final double[] notionals,
                                        final Schedule schedule,
                                        final IborIndex index,
                                        final DayCounter accrualDayCounter) {
        this(settlementDays, notionals, schedule, index, accrualDayCounter,
             BusinessDayConvention.Following, Constants.NULL_INTEGER,
             new Array(new double[] { 1.0 }),
             new Array(new double[] { 0.0 }),
             new Array(0), new Array(0),
             false, new double[] { 100.0 }, new Date());
    }
}
