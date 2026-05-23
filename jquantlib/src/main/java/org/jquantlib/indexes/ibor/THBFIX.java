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
*/

package org.jquantlib.indexes.ibor;

import org.jquantlib.currencies.Asia.THBCurrency;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Period;
import org.jquantlib.time.calendars.Thailand;

/**
 * THBFIX (Thai Baht Fixing Rate) index.
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/indexes/ibor/thbfix.hpp}. THB interest rate implied by USD/THB FX swaps.
 *
 * @author JQuantLib migration team
 * @category indexes
 */
public class THBFIX extends IborIndex {

    public THBFIX(final Period tenor, final Handle< YieldTermStructure > h) {
        super("THBFIX", tenor,
                2,
                new THBCurrency(),
                new Thailand(),
                BusinessDayConvention.ModifiedFollowing, true,
                new Actual365Fixed(), h);
    }

    public THBFIX(final Period tenor) {
        this(tenor, new Handle< YieldTermStructure >());
    }
}
