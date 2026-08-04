/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2018 Roy Zywina

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
 * SOFR (Secured Overnight Financing Rate) index.
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/indexes/ibor/sofr.hpp/cpp}.
 * <p>
 * <b>Divergence note:</b> C++ v1.42.1 uses
 * {@code UnitedStates(UnitedStates::SOFR)}; this Java port uses {@code Market.GOVERNMENTBOND}. The SOFR market enum +
 * {@code SofrImpl} are also available in Java (Phase 5e.5b-CFC-d) but kept off the Sofr index for now: GovernmentBond's
 * Good Friday rule (full closure) matches SOFR's behavior on the date set tested in v1.42.1's overnight pricing test
 * fixtures, and switching to SOFR exposes the missing NFP-carve-out difference in GovernmentBond which would itself
 * need closing first.
 *
 * @author JQuantLib migration team
 * @category indexes
 */
public class Sofr extends OvernightIndex {

    public Sofr(final Handle< YieldTermStructure > h) {
        // C++ v1.43 ql/indexes/ibor/sofr.cpp:27-30 uses UnitedStates(UnitedStates::SOFR),
        // i.e. the government-bond calendar plus the Good Friday closure — not the plain
        // government-bond calendar. The distinction is load-bearing: Good Friday is the one
        // day that is a SOFR fixing holiday but a Federal Reserve business day, so it drives
        // the value-date schedule of any coupon spanning it.
        super("SOFR", 0, new USDCurrency(), new UnitedStates(UnitedStates.Market.SOFR), new Actual360(), h);
    }

    public Sofr() {
        this(new Handle< YieldTermStructure >());
    }
}
