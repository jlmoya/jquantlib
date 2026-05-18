/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2025 Sotirios Papathanasopoulos

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.
*/

package org.jquantlib.indexes.ibor;

import org.jquantlib.QL;
import org.jquantlib.currencies.America.BRLCurrency;
import org.jquantlib.daycounters.Business252;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.indexes.OvernightIndex;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;
import org.jquantlib.time.calendars.Brazil;

/**
 * BRL-CDI overnight index.
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/indexes/ibor/cdi.hpp/cpp}
 * ({@code Cdi}). Relevant for Brazilian swaps; see
 * <a href="https://en.wikipedia.org/wiki/Brazilian_Swap">Brazilian Swap</a>.
 * <p>
 * The forecast-fixing formula differs from the standard {@link OvernightIndex}:
 * the discount-factor ratio is compounded with an exponent of
 * {@code 1/yearFraction} rather than divided by the year fraction, to match
 * the Brazilian-market 252-business-day compounding convention.
 * <p>
 * Reference: Zine-eddine, Arroub. "OpenGamma Quantitative research Brazilian
 * Swaps", London, December 2013, paragraph 5.
 *
 * @category indexes
 *
 * @author JQuantLib migration team
 */
public class Brlcdi extends OvernightIndex {

    public Brlcdi(final Handle<YieldTermStructure> h) {
        super("CDI", 0, new BRLCurrency(),
              new Brazil(Brazil.Market.SETTLEMENT),
              new Business252(new Brazil(Brazil.Market.SETTLEMENT)), h);
    }

    public Brlcdi() {
        this(new Handle<YieldTermStructure>());
    }

    /**
     * Overrides the base IborIndex forecast to use the BRL-CDI 252-business-day
     * compounding convention: {@code (Df_start / Df_end)^(1/yf) - 1}.
     * <p>
     * C++ reference: {@code Cdi::forecastFixing} in
     * {@code ql/indexes/ibor/cdi.cpp}.
     */
    @Override
    protected double forecastFixing(final Date fixingDate) {
        final Date startDate = valueDate(fixingDate);
        final Date endDate = maturityDate(startDate);
        final double yf = dayCounter().yearFraction(startDate, endDate);

        QL.require(yf > 0.0, "year fraction (" + yf + ") must be positive");

        final Handle<YieldTermStructure> ts = termStructure();
        QL.require(!ts.empty(),
                "null term structure set to this instance of " + name());

        final double discountStart = ts.currentLink().discount(startDate);
        final double discountEnd = ts.currentLink().discount(endDate);
        return Math.pow(discountStart / discountEnd, 1.0 / yf) - 1.0;
    }

    @Override
    public Handle<IborIndex> clone(final Handle<YieldTermStructure> h) {
        return new Handle<IborIndex>(new Brlcdi(h));
    }
}
