/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2018 Matthias Lungwitz

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.
*/

package org.jquantlib.indexes.ibor;

import org.jquantlib.currencies.Europe.RUBCurrency;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Russia;

/**
 * MOSPRIME (Moscow Prime Offered Rate) index, fixed by NFEA.
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/indexes/ibor/mosprime.hpp}.
 *
 * @author JQuantLib migration team
 * @category indexes
 */
public class Mosprime extends IborIndex {

    public Mosprime(final Period tenor, final Handle< YieldTermStructure > h) {
        super("MOSPRIME", tenor,
                tenor.units() == TimeUnit.Days && tenor.length() == 1 ? 0 : 1,
                new RUBCurrency(), new Russia(),
                BusinessDayConvention.ModifiedFollowing, false,
                new ActualActual(ActualActual.Convention.ISDA), h);
    }

    public Mosprime(final Period tenor) {
        this(tenor, new Handle< YieldTermStructure >());
    }
}
