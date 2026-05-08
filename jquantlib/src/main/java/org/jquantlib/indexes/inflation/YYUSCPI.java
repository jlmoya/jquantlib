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
import org.jquantlib.indexes.YoYInflationIndex;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YoYInflationTermStructure;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;

/**
 * Quoted year-on-year US CPI (i.e. NOT a ratio of USCPI).
 *
 * <p>Mirrors C++ v1.42.1 {@code QuantLib::YYUSCPI}
 * ({@code ql/indexes/inflation/uscpi.hpp:48-78}).
 *
 * <p>Family name is {@code "YY_CPI"}, region USA, currency USD, frequency
 * Monthly, availability lag 1 month, {@code ratio = false}.
 */
public class YYUSCPI extends YoYInflationIndex {

    public YYUSCPI(final boolean interpolated) {
        this(interpolated, new Handle<YoYInflationTermStructure>());
    }

    public YYUSCPI(final boolean interpolated,
                   final Handle<YoYInflationTermStructure> termStructure) {
        super("YY_CPI",
                new USRegion(),
                false,
                interpolated,
                false,
                Frequency.Monthly,
                new Period(1, TimeUnit.Months),
                new USDCurrency(),
                termStructure);
    }
}
