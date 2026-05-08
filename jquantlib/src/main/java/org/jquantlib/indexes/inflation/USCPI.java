/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.indexes.inflation;

import org.jquantlib.currencies.America.USDCurrency;
import org.jquantlib.indexes.USRegion;
import org.jquantlib.indexes.ZeroInflationIndex;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.ZeroInflationTermStructure;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;

/**
 * US CPI (Consumer Price Index) zero-inflation index.
 *
 * <p>Mirrors C++ v1.42.1 {@code QuantLib::USCPI}
 * ({@code ql/indexes/inflation/uscpi.hpp:34-44}).
 *
 * <p>Family name is {@code "CPI"}, region USA, currency USD, frequency
 * Monthly, availability lag 1 month, {@code revised = false}.
 */
public class USCPI extends ZeroInflationIndex {

    public USCPI(final boolean interpolated) {
        this(interpolated, new Handle<ZeroInflationTermStructure>());
    }

    public USCPI(final boolean interpolated,
                 final Handle<ZeroInflationTermStructure> termStructure) {
        super("CPI",
                new USRegion(),
                false,
                interpolated,
                Frequency.Monthly,
                new Period(1, TimeUnit.Months),
                new USDCurrency(),
                termStructure);
    }
}
