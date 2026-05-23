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

import org.jquantlib.daycounters.Actual360;
import org.jquantlib.indexes.Euribor6M;
import org.jquantlib.math.Rounding;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.NullCalendar;

/**
 * Italian CCTEU (Certificato di Credito del Tesoro) Euribor6M-indexed
 * floating-rate bond.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/instruments/bonds/btp.{hpp,cpp}} (CCTEU class).
 *
 * <p>CCTEUs settle on a 2-business-day cycle, use a {@link NullCalendar}
 * schedule (Unadjusted), a 6-month tenor, {@link Actual360} day-count,
 * Following payment convention, and Euribor6M as the floating index.
 * {@link #accruedAmount(Date)} is rounded to 5 decimal places (closest)
 * to match the C++ convention.
 *
 * @author Jose Moya
 */
public class CCTEU extends FloatingRateBond {

    /**
     * Mirror of C++ {@code CCTEU(maturityDate, spread, fwdCurve=Handle<YieldTermStructure>(),
     * startDate=Date(), issueDate=Date())} (btp.cpp:33-53).
     */
    public CCTEU(final Date maturityDate, final /* @Spread */ double spread,
            final Handle< YieldTermStructure > fwdCurve, final Date startDate, final Date issueDate) {
        super(/* settlementDays */ 2,
              /* faceAmount */ 100.0,
              new Schedule(startDate, maturityDate, new Period(6, TimeUnit.Months), new NullCalendar(),
                      BusinessDayConvention.Unadjusted, BusinessDayConvention.Unadjusted,
                      DateGeneration.Rule.Backward, /* endOfMonth */ true),
              new Euribor6M(fwdCurve),
              new Actual360(),
              /* paymentConvention */ BusinessDayConvention.Following,
              new Euribor6M().fixingDays(),
              /* gearings */ new Array(new double[] { 1.0 }),
              /* spreads */ new Array(new double[] { spread }),
              /* caps */ new Array(0),
              /* floors */ new Array(0),
              /* inArrears */ false,
              /* redemption */ 100.0,
              issueDate);
    }

    /** Convenience: empty forward curve. */
    public CCTEU(final Date maturityDate, final /* @Spread */ double spread, final Date startDate,
            final Date issueDate) {
        this(maturityDate, spread, new Handle< YieldTermStructure >(), startDate, issueDate);
    }

    /** Minimal convenience: empty forward curve + default start/issue dates. */
    public CCTEU(final Date maturityDate, final /* @Spread */ double spread) {
        this(maturityDate, spread, new Handle< YieldTermStructure >(), new Date(), new Date());
    }

    /**
     * Accrued amount at a given date, rounded to 5 decimal places (closest).
     * Mirrors C++ inline override (btp.hpp:193-196) — wraps
     * {@link FloatingRateBond#accruedAmount(Date)} with {@link Rounding.ClosestRounding}(5).
     */
    @Override
    public double accruedAmount(final Date d) {
        final double result = super.accruedAmount(d);
        return new Rounding.ClosestRounding(5).operator(result);
    }
}
