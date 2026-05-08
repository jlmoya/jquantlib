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

import org.jquantlib.currencies.Oceania.AUDCurrency;
import org.jquantlib.indexes.AustraliaRegion;
import org.jquantlib.indexes.YoYInflationIndex;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YoYInflationTermStructure;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;

/**
 * Quoted year-on-year Australian CPI (i.e. NOT a ratio of AUCPI).
 *
 * <p>Mirrors C++ v1.42.1 {@code QuantLib::YYAUCPI}
 * ({@code ql/indexes/inflation/aucpi.hpp:45-78}).
 *
 * <p>Family name is {@code "YY_CPI"}, region Australia, currency AUD,
 * availability lag 2 months. {@code ratio = false} since this is a quoted YY
 * index, not a derived ratio.
 */
public class YYAUCPI extends YoYInflationIndex {

    public YYAUCPI(final Frequency frequency,
                   final boolean revised,
                   final boolean interpolated) {
        this(frequency, revised, interpolated,
                new Handle<YoYInflationTermStructure>());
    }

    public YYAUCPI(final Frequency frequency,
                   final boolean revised,
                   final boolean interpolated,
                   final Handle<YoYInflationTermStructure> termStructure) {
        super("YY_CPI",
                new AustraliaRegion(),
                revised,
                interpolated,
                false,
                frequency,
                new Period(2, TimeUnit.Months),
                new AUDCurrency(),
                termStructure);
    }
}
