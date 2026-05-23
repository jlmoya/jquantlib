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

/*
 Copyright (C) 2009 Chris Kenyon
*/

package org.jquantlib.instruments;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.cashflow.Leg;

/**
 * Concrete year-on-year inflation floor.
 *
 * <p>Mirrors C++ v1.42.1 inline class {@code QuantLib::YoYInflationFloor}
 * ({@code ql/instruments/inflationcapfloor.hpp:112-118}). Calls the
 * {@link YoYInflationCapFloor} constructor with {@code type = Floor} and empty
 * cap-rates vector.
 *
 * @author JQuantLib migration team (Phase 2 L3-A)
 */
public class YoYInflationFloor extends YoYInflationCapFloor {
    public YoYInflationFloor(final Leg yoyLeg, final List< Double > exerciseRates) {
        super(Type.Floor, yoyLeg, new ArrayList< Double >(), exerciseRates);
    }
}
