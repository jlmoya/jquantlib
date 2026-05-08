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

import org.jquantlib.currencies.Africa.ZARCurrency;
import org.jquantlib.indexes.YoYInflationIndex;
import org.jquantlib.indexes.ZARegion;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YoYInflationTermStructure;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;

/**
 * Quoted year-on-year South African CPI (i.e. NOT a ratio of ZACPI).
 *
 * <p>Mirrors C++ v1.42.1 {@code QuantLib::YYZACPI}
 * ({@code ql/indexes/inflation/zacpi.hpp:43-73}).
 *
 * <p>Family name is {@code "YY_CPI"}, region South Africa, currency ZAR,
 * frequency Monthly, availability lag 1 month, {@code ratio = false}.
 */
public class YYZACPI extends YoYInflationIndex {

    public YYZACPI(final boolean interpolated) {
        this(interpolated, new Handle<YoYInflationTermStructure>());
    }

    public YYZACPI(final boolean interpolated,
                   final Handle<YoYInflationTermStructure> termStructure) {
        super("YY_CPI",
                new ZARegion(),
                false,
                interpolated,
                false,
                Frequency.Monthly,
                new Period(1, TimeUnit.Months),
                new ZARCurrency(),
                termStructure);
    }
}
