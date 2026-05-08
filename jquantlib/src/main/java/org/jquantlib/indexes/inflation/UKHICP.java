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

import org.jquantlib.currencies.Europe.GBPCurrency;
import org.jquantlib.indexes.UKRegion;
import org.jquantlib.indexes.ZeroInflationIndex;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.ZeroInflationTermStructure;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;

/**
 * UK HICP (Harmonised Index of Consumer Prices) zero-inflation index.
 *
 * <p>Mirrors C++ v1.42.1 {@code QuantLib::UKHICP}
 * ({@code ql/indexes/inflation/ukhicp.hpp:33-38}).
 *
 * <p>Family name is {@code "HICP"}, region UK, currency GBP, frequency Monthly,
 * availability lag 1 month, {@code revised = false}.
 *
 * <p>The Java port retains the ({@code interpolated}) constructor parameter
 * required by the {@link ZeroInflationIndex} base class (the C++ class drops
 * this parameter as of v1.38, but the Java base class is still pre-merge).
 */
public class UKHICP extends ZeroInflationIndex {

    public UKHICP(final boolean interpolated) {
        this(interpolated, new Handle<ZeroInflationTermStructure>());
    }

    public UKHICP(final boolean interpolated,
                  final Handle<ZeroInflationTermStructure> termStructure) {
        super("HICP",
                new UKRegion(),
                false,
                interpolated,
                Frequency.Monthly,
                new Period(1, TimeUnit.Months),
                new GBPCurrency(),
                termStructure);
    }
}
