/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2022 Jonghee Lee

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

import org.jquantlib.currencies.Asia.JPYCurrency;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.indexes.OvernightIndex;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.calendars.Japan;

/**
 * TONAR (Tokyo Overnight Average Rate) index.
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/indexes/ibor/tonar.hpp}. Fixed by the Bank of Japan (BOJ).
 *
 * @author JQuantLib migration team
 * @category indexes
 */
public class Tonar extends OvernightIndex {

    public Tonar(final Handle< YieldTermStructure > h) {
        super("Tonar", 0, new JPYCurrency(), new Japan(), new Actual365Fixed(), h);
    }

    public Tonar() {
        this(new Handle< YieldTermStructure >());
    }
}
