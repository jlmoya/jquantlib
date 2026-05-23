/*
 Copyright (C) 2010 Ferdinando Ametrano (C++)

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

package org.jquantlib.experimental.coupons;

import org.jquantlib.currencies.Currency;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;

/**
 * IborIndex calculated as a proxy of some other IborIndex.
 *
 * <p>Port of {@code ql/experimental/coupons/proxyibor.hpp} from C++ QuantLib v1.42.1.
 *
 * <p>Given a gearing g and spread s (both observable {@link Quote}s) and an underlying
 * {@link IborIndex}, the proxy forecasts a fixing as
 * {@code g * underlying.fixing(date) * s}, mirroring the C++ implementation.
 *
 * @author Jose Moya
 */
public class ProxyIbor extends IborIndex {

    private final Handle<Quote> gearing;
    private final IborIndex iborIndex;
    private final Handle<Quote> spread;

    public ProxyIbor(final String familyName,
                     final Period tenor,
                     final int settlementDays,
                     final Currency currency,
                     final Calendar fixingCalendar,
                     final BusinessDayConvention convention,
                     final boolean endOfMonth,
                     final DayCounter dayCounter,
                     final Handle<Quote> gearing,
                     final IborIndex iborIndex,
                     final Handle<Quote> spread) {
        super(familyName, tenor, settlementDays, currency, fixingCalendar,
              convention, endOfMonth, dayCounter);
        this.gearing = gearing;
        this.iborIndex = iborIndex;
        this.spread = spread;
        // observe the underlying so we re-fix when it changes
        if (iborIndex != null) {
            iborIndex.addObserver(this);
        }
    }

    @Override
    protected double forecastFixing(final Date fixingDate) {
        final double proxy = iborIndex.fixing(fixingDate);
        return gearing.currentLink().value() * proxy * spread.currentLink().value();
    }
}
