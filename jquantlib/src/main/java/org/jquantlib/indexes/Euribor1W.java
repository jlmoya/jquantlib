/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2008 Srinivas Hasti

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/
 */
package org.jquantlib.indexes;

import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;

/**
 * 1-week Euribor index.
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/indexes/ibor/euribor.hpp} ({@code Euribor1W}).
 *
 * @author JQuantLib migration team
 * @category indexes
 */
public class Euribor1W extends Euribor {

    public Euribor1W() {
        this(new Handle< YieldTermStructure >());
    }

    public Euribor1W(final Handle< YieldTermStructure > h) {
        super(new Period(1, TimeUnit.Weeks), h);
    }
}
