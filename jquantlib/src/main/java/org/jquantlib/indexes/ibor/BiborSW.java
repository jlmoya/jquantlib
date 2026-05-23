/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2018 Matthias Groncki

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.
*/

package org.jquantlib.indexes.ibor;

import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;

/**
 * 1-week BIBOR index.
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/indexes/ibor/bibor.hpp} ({@code BiborSW}).
 */
public class BiborSW extends Bibor {

    public BiborSW(final Handle< YieldTermStructure > h) {
        super(new Period(1, TimeUnit.Weeks), h);
    }

    public BiborSW() {
        this(new Handle< YieldTermStructure >());
    }
}
