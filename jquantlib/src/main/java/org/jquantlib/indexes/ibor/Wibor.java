/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2018 Matthias Lungwitz

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.
*/

package org.jquantlib.indexes.ibor;

import org.jquantlib.currencies.Europe.PLNCurrency;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Poland;

/**
 * WIBOR (Warsaw Interbank Offered Rate) index, fixed by ACI.
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/indexes/ibor/wibor.hpp}.
 * <p>
 * <b>Divergence note:</b> C++ uses {@code Poland(Poland::Settlement)}; the JQuantLib {@code Poland} calendar exposes
 * only one (settlement) impl through its no-arg constructor, so {@code new Poland()} is the equivalent.
 *
 * @author JQuantLib migration team
 * @category indexes
 */
public class Wibor extends IborIndex {

    public Wibor(final Period tenor, final Handle< YieldTermStructure > h) {
        super("WIBOR", tenor,
                tenor.units() == TimeUnit.Days && tenor.length() == 1 ? 0 : 2,
                new PLNCurrency(), new Poland(),
                BusinessDayConvention.ModifiedFollowing, false,
                new Actual365Fixed(), h);
    }

    public Wibor(final Period tenor) {
        this(tenor, new Handle< YieldTermStructure >());
    }
}
