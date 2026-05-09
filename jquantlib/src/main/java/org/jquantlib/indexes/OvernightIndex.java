/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2009 Roland Lichters
 Copyright (C) 2009 Ferdinando Ametrano

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
*/

package org.jquantlib.indexes;

import org.jquantlib.currencies.Currency;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;

/**
 * Base class for overnight rate indexes (e.g. Eonia, Sonia, SOFR, Fed Funds).
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/indexes/iborindex.hpp/cpp}
 * {@code OvernightIndex}. An overnight index is a one-business-day-tenor
 * IborIndex with {@link BusinessDayConvention#Following} and
 * {@code endOfMonth=false}. It is the foundation of OIS swaps and overnight-
 * compounded coupons.
 *
 * @category indexes
 *
 * @author JQuantLib migration team
 */
public class OvernightIndex extends IborIndex {

    /**
     * Constructs an overnight index.
     *
     * @param familyName     short identifier (e.g. "Eonia", "Sofr")
     * @param settlementDays usually 0 for an overnight rate
     * @param currency       the currency in which the rate is fixed
     * @param fixingCalendar fixing calendar
     * @param dayCounter     day-count convention used for accrual
     * @param h              forecasting yield curve handle (may be empty)
     */
    public OvernightIndex(
            final String familyName,
            final /*@Natural*/ int settlementDays,
            final Currency currency,
            final Calendar fixingCalendar,
            final DayCounter dayCounter,
            final Handle<YieldTermStructure> h) {
        super(familyName, new Period(1, TimeUnit.Days), settlementDays, currency,
              fixingCalendar, BusinessDayConvention.Following, false, dayCounter, h);
    }

    /**
     * Constructs an overnight index without a forwarding curve.
     */
    public OvernightIndex(
            final String familyName,
            final /*@Natural*/ int settlementDays,
            final Currency currency,
            final Calendar fixingCalendar,
            final DayCounter dayCounter) {
        this(familyName, settlementDays, currency, fixingCalendar, dayCounter,
             new Handle<YieldTermStructure>());
    }


    //
    // Override IborIndex.clone() so the type stays OvernightIndex
    //

    @Override
    public Handle<IborIndex> clone(final Handle<YieldTermStructure> h) {
        final OvernightIndex clone = new OvernightIndex(
                this.familyName(),
                this.fixingDays(),
                this.currency(),
                this.fixingCalendar(),
                this.dayCounter(),
                h);
        return new Handle<IborIndex>(clone);
    }
}
