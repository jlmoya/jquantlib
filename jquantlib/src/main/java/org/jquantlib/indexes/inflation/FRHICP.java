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

import org.jquantlib.currencies.Europe.EURCurrency;
import org.jquantlib.indexes.FranceRegion;
import org.jquantlib.indexes.ZeroInflationIndex;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.ZeroInflationTermStructure;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;

/**
 * French HICP (Harmonised Index of Consumer Prices) zero-inflation index.
 *
 * <p>Mirrors C++ v1.42.1 {@code QuantLib::FRHICP}
 * ({@code ql/indexes/inflation/frhicp.hpp:34-39}).
 *
 * <p>Family name is {@code "HICP"}, region France, currency EUR, frequency
 * Monthly, availability lag 1 month, {@code revised = false}.
 */
public class FRHICP extends ZeroInflationIndex {

    public FRHICP(final boolean interpolated) {
        this(interpolated, new Handle< ZeroInflationTermStructure >());
    }

    public FRHICP(final boolean interpolated, final Handle< ZeroInflationTermStructure > termStructure) {
        super("HICP", new FranceRegion(), false, interpolated, Frequency.Monthly, new Period(1, TimeUnit.Months),
                new EURCurrency(), termStructure);
    }
}
