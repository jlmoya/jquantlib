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

import org.jquantlib.currencies.Europe.RONCurrency;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Romania;

/**
 * ROBOR (Romanian Interbank Offered Rate) index, fixed by BNR.
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/indexes/ibor/robor.hpp}.
 *
 * @author JQuantLib migration team
 * @category indexes
 */
public class Robor extends IborIndex {

    public Robor(final Period tenor, final Handle< YieldTermStructure > h) {
        super("ROBOR", tenor,
                tenor.units() == TimeUnit.Days && tenor.length() == 1 ? 0 : 2,
                new RONCurrency(), new Romania(),
                BusinessDayConvention.ModifiedFollowing, false,
                new Actual360(), h);
    }

    public Robor(final Period tenor) {
        this(tenor, new Handle< YieldTermStructure >());
    }
}
