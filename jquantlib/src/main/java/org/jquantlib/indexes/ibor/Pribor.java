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

import org.jquantlib.currencies.Europe.CZKCurrency;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.CzechRepublic;

/**
 * PRIBOR (Prague Interbank Offered Rate) index, fixed by CFBF.
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/indexes/ibor/pribor.hpp}.
 *
 * @author JQuantLib migration team
 * @category indexes
 */
public class Pribor extends IborIndex {

    public Pribor(final Period tenor, final Handle< YieldTermStructure > h) {
        super("PRIBOR", tenor,
                tenor.units() == TimeUnit.Days && tenor.length() == 1 ? 0 : 2,
                new CZKCurrency(), new CzechRepublic(),
                BusinessDayConvention.ModifiedFollowing, false,
                new Actual360(), h);
    }

    public Pribor(final Period tenor) {
        this(tenor, new Handle< YieldTermStructure >());
    }
}
