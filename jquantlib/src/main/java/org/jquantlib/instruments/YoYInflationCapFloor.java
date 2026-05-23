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

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.instruments;

import java.util.List;

import org.jquantlib.cashflow.Leg;

/**
 * Base class for year-on-year inflation cap-like instruments.
 *
 * <p>Top-level alias matching the v1.42.1 C++ class name {@code QuantLib::YoYInflationCapFloor}
 * ({@code ql/instruments/inflationcapfloor.{hpp,cpp}}). All functionality is inherited from
 * the existing {@link InflationCapFloor} port (which is the YoY-only inflation cap/floor base
 * class in v1.42.1).
 *
 * <p>This class exists so that callers using the v1.42.1 type name resolve to the same
 * implementation; the {@link YoYInflationCap}, {@link YoYInflationFloor} and {@link YoYInflationCollar}
 * convenience subclasses below mirror the C++ inline subclasses verbatim.
 *
 * @author JQuantLib migration team (Phase 2 L3-A)
 */
public class YoYInflationCapFloor extends InflationCapFloor {

    public YoYInflationCapFloor(final Type type, final Leg yoyLeg,
            final List< Double > capRates, final List< Double > floorRates) {
        super(type, yoyLeg, capRates, floorRates);
    }

    public YoYInflationCapFloor(final Type type, final Leg yoyLeg, final List< Double > strikes) {
        super(type, yoyLeg, strikes);
    }
}
