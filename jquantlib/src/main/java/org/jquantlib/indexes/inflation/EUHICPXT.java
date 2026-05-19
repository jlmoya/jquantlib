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
import org.jquantlib.indexes.EURegion;
import org.jquantlib.indexes.ZeroInflationIndex;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.ZeroInflationTermStructure;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;

/**
 * EU HICPXT (Harmonised Index of Consumer Prices, eXcluding Tobacco) zero-inflation index.
 *
 * <p>Mirrors C++ v1.42.1 {@code QuantLib::EUHICPXT}
 * ({@code ql/indexes/inflation/euhicp.hpp:48-58}).
 *
 * <p>Family name is {@code "HICPXT"}, region EU, currency EUR, frequency
 * Monthly, availability lag 1 month, {@code revised = false}. Sibling to {@link EUHICP}; the difference is the
 * {@code "HICP"} vs {@code "HICPXT"} family-name suffix and the underlying basket excludes tobacco.
 */
public class EUHICPXT extends ZeroInflationIndex {

    public EUHICPXT(final boolean interpolated) {
        this(interpolated, new Handle< ZeroInflationTermStructure >());
    }

    public EUHICPXT(final boolean interpolated, final Handle< ZeroInflationTermStructure > termStructure) {
        super("HICPXT", new EURegion(), false, interpolated, Frequency.Monthly, new Period(1, TimeUnit.Months),
                new EURCurrency(), termStructure);
    }
}
