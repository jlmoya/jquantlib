/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2016 Fabrice Lecuyer

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

package org.jquantlib.indexes.ibor;

import org.jquantlib.QL;
import org.jquantlib.currencies.Oceania.AUDCurrency;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Australia;

/**
 * Bbsw (Bank Bill Swap Rate) index fixed by AFMA.
 * <p>
 * See <a href="http://www.afma.com.au/data/BBSW">AFMA BBSW</a>.
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/indexes/ibor/bbsw.hpp}.
 *
 * @author JQuantLib migration team
 * @category indexes
 */
public class Bbsw extends IborIndex {

    public Bbsw(final Period tenor, final Handle<YieldTermStructure> h) {
        super("Bbsw", tenor,
              0, // settlement days
              new AUDCurrency(), new Australia(),
              BusinessDayConvention.HalfMonthModifiedFollowing, true,
              new Actual365Fixed(), h);
        QL.require(tenor.units() != TimeUnit.Days,
                   "for daily tenors (" + tenor + ") dedicated DailyTenor constructor must be used");
    }

    public Bbsw(final Period tenor) {
        this(tenor, new Handle<YieldTermStructure>());
    }
}
