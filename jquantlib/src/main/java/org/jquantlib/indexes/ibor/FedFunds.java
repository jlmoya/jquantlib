/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2014 StatPro Italia srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
*/

package org.jquantlib.indexes.ibor;

import org.jquantlib.currencies.America.USDCurrency;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.indexes.OvernightIndex;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.calendars.UnitedStates;

/**
 * Federal Funds rate fixed by the FED (for balances held at the Federal Reserve).
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/indexes/ibor/fedfunds.hpp/cpp}.
 * <p>
 * <b>Divergence note:</b> C++ v1.42.1 uses
 * {@code UnitedStates(UnitedStates::FederalReserve)}; this Java port uses {@code Market.GOVERNMENTBOND} as the Java
 * {@code UnitedStates} calendar does not yet expose a FederalReserve-specific market enum value.
 *
 * @author JQuantLib migration team
 * @category indexes
 */
public class FedFunds extends OvernightIndex {

    public FedFunds(final Handle< YieldTermStructure > h) {
        super("FedFunds", 0, new USDCurrency(), new UnitedStates(UnitedStates.Market.GOVERNMENTBOND), new Actual360(),
                h);
    }

    public FedFunds() {
        this(new Handle< YieldTermStructure >());
    }
}
