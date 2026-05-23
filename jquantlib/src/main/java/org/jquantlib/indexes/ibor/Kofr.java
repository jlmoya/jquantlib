/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2024 Jongbong An

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

import org.jquantlib.currencies.Asia.KRWCurrency;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.indexes.OvernightIndex;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.calendars.SouthKorea;

/**
 * KOFR (Korea Overnight Financing Repo Rate) index.
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/indexes/ibor/kofr.hpp/cpp}. Published by the Korea Securities Depository (KSD).
 *
 * @author JQuantLib migration team
 * @category indexes
 */
public class Kofr extends OvernightIndex {

    public Kofr(final Handle< YieldTermStructure > h) {
        super("KOFR", 0, new KRWCurrency(), new SouthKorea(SouthKorea.Market.Settlement), new Actual365Fixed(), h);
    }

    public Kofr() {
        this(new Handle< YieldTermStructure >());
    }
}
