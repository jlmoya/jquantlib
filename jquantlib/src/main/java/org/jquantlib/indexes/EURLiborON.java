/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2011 Tim Blackler

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/
 */
package org.jquantlib.indexes;

import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;

/**
 * Overnight EUR LIBOR index.
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/indexes/ibor/eurlibor.hpp} ({@code EURLiborON}). Extends
 * {@link DailyTenorEURLibor} with 0 settlement days (overnight tenor).
 *
 * @author JQuantLib migration team
 * @category indexes
 */
public class EURLiborON extends DailyTenorEURLibor {

    public EURLiborON() {
        this(new Handle< YieldTermStructure >());
    }

    public EURLiborON(final Handle< YieldTermStructure > h) {
        super(0, h);
    }
}
