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
import org.jquantlib.cashflow.CmsLeg;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.SwapIndex;
import org.jquantlib.instruments.Bond;
import org.jquantlib.math.Constants;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.Schedule;

/**
 * Amortizing CMS-rate bond.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/instruments/bonds/amortizingcmsratebond.{hpp,cpp}}.
 *
 * <p>The notional vector encodes the amortization schedule. The redemptions
 * vector follows the same {@link Bond#addRedemptionsToCashflows(double[])}
 * tail-replication semantics as the other amortizing bonds in the family.
 *
 * @author Jose Moya
 */
public class AmortizingCmsRateBond extends Bond {

    /**
     * Mirror of C++ {@code AmortizingCmsRateBond(settlementDays, notionals, schedule, index, paymentDayCounter,
     * paymentConvention=Following, fixingDays=Null<Natural>(), gearings={1.0}, spreads={0.0}, caps={}, floors={},
     * inArrears=false, issueDate=Date(), redemptions={100.0})}
     * (ql/instruments/bonds/amortizingcmsratebond.hpp:37-51).
     */
    public AmortizingCmsRateBond(final /* @Natural */ int settlementDays, final double[] notionals,
            final Schedule schedule, final SwapIndex index, final DayCounter paymentDayCounter,
            final BusinessDayConvention paymentConvention, final /* @Natural */ int fixingDays, final Array gearings,
            final Array spreads, final Array caps, final Array floors, final boolean inArrears, final Date issueDate,
            final double[] redemptions) {
        super(settlementDays, schedule.calendar(), issueDate);
        maturityDate_ = schedule.endDate().clone();

        cashflows_ = new CmsLeg(schedule, index).withNotionals(new Array(notionals))
                .withPaymentDayCounter(paymentDayCounter).withPaymentAdjustment(paymentConvention)
                .withFixingDays(fixingDays).withGearings(gearings).withSpreads(spreads).withCaps(caps)
                .withFloors(floors).inArrears(inArrears).Leg();

        addRedemptionsToCashflows(redemptions);

        QL.ensure(!cashflows().isEmpty(), "bond with no cashflows!");
        index.addObserver(this);
    }

    /** Convenience overload mirroring the simplest C++ defaulting. */
    public AmortizingCmsRateBond(final /* @Natural */ int settlementDays, final double[] notionals,
            final Schedule schedule, final SwapIndex index, final DayCounter paymentDayCounter) {
        this(settlementDays, notionals, schedule, index, paymentDayCounter, BusinessDayConvention.Following,
                Constants.NULL_INTEGER, new Array(new double[] { 1.0 }), new Array(new double[] { 0.0 }), new Array(0),
                new Array(0), false, new Date(), new double[] { 100.0 });
    }
}
