/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2023 Aditya Narayan Das

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

import org.jquantlib.currencies.America.CADCurrency;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.indexes.OvernightIndex;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.calendars.Canada;

/**
 * CORRA (Canadian Overnight Repo Rate Average) index.
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/indexes/ibor/corra.hpp/cpp}.
 *
 * @author JQuantLib migration team
 * @category indexes
 */
public class Corra extends OvernightIndex {

    public Corra(final Handle< YieldTermStructure > h) {
        super("CORRA", 0, new CADCurrency(), new Canada(), new Actual365Fixed(), h);
    }

    public Corra() {
        this(new Handle< YieldTermStructure >());
    }
}
