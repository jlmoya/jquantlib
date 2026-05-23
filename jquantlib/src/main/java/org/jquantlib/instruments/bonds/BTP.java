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
 Copyright (C) 2010, 2011 Ferdinando Ametrano

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.
*/

package org.jquantlib.instruments.bonds;

import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.math.Rounding;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.NullCalendar;
import org.jquantlib.time.calendars.Target;
import org.jquantlib.pricingengines.bond.BondFunctions;

/**
 * Italian BTP (Buono Poliennali del Tesoro) fixed-rate bond.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/instruments/bonds/btp.{hpp,cpp}}.
 *
 * <p>BTPs settle on a 2-business-day cycle, use a {@link NullCalendar}
 * schedule (Unadjusted), a 6-month tenor, {@link ActualActual.Convention#ISMA}
 * day-count, modified-following payment, and TARGET as the payment calendar.
 * {@link #accruedAmount(Date)} is rounded to 5 decimal places (closest)
 * to match the C++ convention.
 *
 * @author Jose Moya
 */
public class BTP extends FixedRateBond {

    /**
     * Standard BTP — par redemption (100.0).
     *
     * Mirror of C++ {@code BTP(maturityDate, fixedRate, startDate=Date(), issueDate=Date())}
     * (btp.cpp:55-66).
     */
    public BTP(final Date maturityDate, final /* @Rate */ double fixedRate, final Date startDate,
            final Date issueDate) {
        this(maturityDate, fixedRate, 100.0, startDate, issueDate);
    }

    /**
     * Legacy non-par redemption BTP — needed for IT123456789012 (redeems 99.999 on xx-may-2037).
     *
     * Mirror of C++ {@code BTP(maturityDate, fixedRate, redemption, startDate=Date(), issueDate=Date())}
     * (btp.cpp:68-80).
     */
    public BTP(final Date maturityDate, final /* @Rate */ double fixedRate, final /* @Real */ double redemption,
            final Date startDate, final Date issueDate) {
        super(/* settlementDays */ 2,
              /* faceAmount */ 100.0,
              new Schedule(startDate, maturityDate, new Period(6, TimeUnit.Months), new NullCalendar(),
                      BusinessDayConvention.Unadjusted, BusinessDayConvention.Unadjusted,
                      DateGeneration.Rule.Backward, /* endOfMonth */ true),
              /* coupons */ new double[] { fixedRate },
              new ActualActual(ActualActual.Convention.ISMA),
              /* paymentConvention */ BusinessDayConvention.ModifiedFollowing,
              redemption,
              issueDate,
              /* paymentCalendar */ new Target(),
              /* exCouponPeriod */ new Period(),
              /* exCouponCalendar */ null,
              /* exCouponConvention */ BusinessDayConvention.Following,
              /* exCouponEndOfMonth */ false);
    }

    /** Convenience: par redemption + default empty start/issue dates. */
    public BTP(final Date maturityDate, final /* @Rate */ double fixedRate) {
        this(maturityDate, fixedRate, 100.0, new Date(), new Date());
    }

    /**
     * Accrued amount at a given date, rounded to 5 decimal places (closest).
     * Mirrors C++ inline override (btp.hpp:198-201) — wraps
     * {@link FixedRateBond#accruedAmount(Date)} with {@link Rounding.ClosestRounding}(5).
     */
    @Override
    public double accruedAmount(final Date d) {
        final double result = super.accruedAmount(d);
        return new Rounding.ClosestRounding(5).operator(result);
    }

    /**
     * BTP yield given a (clean) price and settlement date.
     *
     * <p>Default conventions: {@link ActualActual.Convention#ISMA},
     * {@link Compounding#Compounded}, {@link Frequency#Annual}.
     * Default bond settlement date is used if {@code settlementDate} is null.
     *
     * Mirrors C++ {@code BTP::yield(cleanPrice, settlementDate=Date(), accuracy=1.0e-8, maxEvaluations=100)}
     * (btp.cpp:82-89).
     */
    public /* @Rate */ double yield(final /* @Real */ double cleanPrice, final Date settlementDate,
            final /* @Real */ double accuracy, final /* @Size */ int maxEvaluations) {
        return BondFunctions.yield(this,
                new BondFunctions.Price(cleanPrice, BondFunctions.Price.Type.Clean),
                new ActualActual(ActualActual.Convention.ISMA),
                Compounding.Compounded, Frequency.Annual,
                (settlementDate == null) ? new Date() : settlementDate,
                accuracy, maxEvaluations, /* guess */ 0.05);
    }

    /** Convenience overload mirroring C++ defaults: accuracy=1e-8, maxEvaluations=100, settlementDate=Date(). */
    public /* @Rate */ double yield(final /* @Real */ double cleanPrice) {
        return this.yield(cleanPrice, new Date(), 1.0e-8, 100);
    }
}
