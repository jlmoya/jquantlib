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
import org.jquantlib.indexes.YoYInflationIndex;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YoYInflationTermStructure;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;

/**
 * Quoted year-on-year UK HICP (i.e. NOT a ratio of UKHICP).
 *
 * <p>Sibling YY index for {@link UKHICP}. Family name is {@code "YY_HICP"},
 * region UK, currency GBP, frequency Monthly, availability lag 1 month,
 * {@code ratio = false}.
 *
 * <p>Note: the C++ source-of-truth file {@code ukhicp.hpp} defines only the
 * zero-inflation {@code UKHICP} class (no {@code YYUKHICP}). This Java class
 * is added as a sibling for symmetry with the other CPI/HICP families
 * (FRHICP/YYFRHICP, EUHICP/YYEUHICP, etc.) — required for any YoY-bootstrap
 * test that uses UK HICP rather than UK RPI.
 */
public class YYUKHICP extends YoYInflationIndex {

    public YYUKHICP(final boolean interpolated) {
        this(interpolated, new Handle<YoYInflationTermStructure>());
    }

    public YYUKHICP(final boolean interpolated,
                    final Handle<YoYInflationTermStructure> termStructure) {
        super("YY_HICP",
                new UKRegion(),
                false,
                interpolated,
                false,
                Frequency.Monthly,
                new Period(1, TimeUnit.Months),
                new GBPCurrency(),
                termStructure);
    }
}
