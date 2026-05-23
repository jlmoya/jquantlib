/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2018 Matthias Groncki

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
import org.jquantlib.currencies.Asia.THBCurrency;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Thailand;

/**
 * Bangkok Interbank Offered Rate (BIBOR) index, fixed by the Bank of Thailand.
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/indexes/ibor/bibor.hpp/cpp}.
 *
 * @author JQuantLib migration team
 * @category indexes
 */
public class Bibor extends IborIndex {

    public Bibor(final Period tenor, final Handle< YieldTermStructure > h) {
        super("Bibor", tenor,
                2, // settlement days
                new THBCurrency(), new Thailand(),
                biborConvention(tenor), biborEOM(tenor),
                new Actual365Fixed(), h);
        QL.require(tenor().units() != TimeUnit.Days,
                "for daily tenors dedicated DailyTenor constructor must be used");
    }

    public Bibor(final Period tenor) {
        this(tenor, new Handle< YieldTermStructure >());
    }

    private static BusinessDayConvention biborConvention(final Period p) {
        return switch (p.units()) {
            case Days, Weeks -> BusinessDayConvention.Following;
            case Months, Years -> BusinessDayConvention.ModifiedFollowing;
            default -> throw new LibraryException("invalid time units");
        };
    }

    private static boolean biborEOM(final Period p) {
        return switch (p.units()) {
            case Days, Weeks -> false;
            case Months, Years -> true;
            default -> throw new LibraryException("invalid time units");
        };
    }
}
