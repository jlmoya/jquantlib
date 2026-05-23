/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2014 Cheng Li

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.
*/

package org.jquantlib.indexes.ibor;

import org.jquantlib.currencies.Asia.CNYCurrency;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.China;

/**
 * SHIBOR (Shanghai Interbank Offered Rate) index.
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/indexes/ibor/shibor.hpp/cpp}.
 *
 * @author JQuantLib migration team
 * @category indexes
 */
public class Shibor extends IborIndex {

    public Shibor(final Period tenor, final Handle< YieldTermStructure > h) {
        super("Shibor", tenor,
                tenor.units() == TimeUnit.Days && tenor.length() == 1 ? 0 : 1,
                new CNYCurrency(), new China(China.Market.IB),
                shiborConvention(tenor), false,
                new Actual360(), h);
    }

    public Shibor(final Period tenor) {
        this(tenor, new Handle< YieldTermStructure >());
    }

    @Override
    public Handle< IborIndex > clone(final Handle< YieldTermStructure > h) {
        return new Handle< IborIndex >(new Shibor(tenor(), h));
    }

    private static BusinessDayConvention shiborConvention(final Period p) {
        return switch (p.units()) {
            case Days, Weeks -> BusinessDayConvention.Following;
            case Months, Years -> BusinessDayConvention.ModifiedFollowing;
            default -> throw new LibraryException("invalid time units");
        };
    }
}
