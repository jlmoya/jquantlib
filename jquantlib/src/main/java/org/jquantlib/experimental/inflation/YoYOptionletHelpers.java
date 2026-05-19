/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is released under the BSD License.

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
package org.jquantlib.experimental.inflation;

import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.CPI;
import org.jquantlib.indexes.YoYInflationIndex;
import org.jquantlib.instruments.InflationCapFloor;
import org.jquantlib.pricingengines.inflation.InflationCapFloorEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Period;

import java.util.ArrayList;
import java.util.List;

/**
 * Static helper namespace for the {@link YoYOptionletHelper} family.
 *
 * <p>Mirrors C++ v1.42.1 {@code ql/experimental/inflation/yoyoptionlethelpers.{hpp,cpp}}
 * which currently contains the single class {@code YoYOptionletHelper}. This Java class collects convenience builders
 * for that helper, mirroring the intent of the C++ header file (and providing a stable hook for future helpers if/when
 * QuantLib adds them).
 *
 * @author JQuantLib migration team (Phase 2s Track B)
 */
public final class YoYOptionletHelpers {

    private YoYOptionletHelpers() {
        // static utility
    }

    /**
     * Direct constructor convenience: alias for {@code new YoYOptionletHelper(...)} in a more grep-friendly call site.
     */
    public static YoYOptionletHelper makeHelper(final Handle< Quote > price, final double notional,
            final InflationCapFloor.Type capFloorType, final Period lag, final DayCounter yoyDayCounter,
            final Calendar paymentCalendar, final int fixingDays, final YoYInflationIndex index,
            final CPI.InterpolationType interpolation, final double strike, final int n,
            final InflationCapFloorEngine pricer) {
        return new YoYOptionletHelper(price, notional, capFloorType, lag, yoyDayCounter, paymentCalendar, fixingDays,
                index, interpolation, strike, n, pricer);
    }

    /**
     * Build a list of helpers for a single strike across a maturity grid.
     *
     * <p>Mirrors the inner loop of
     * {@code InterpolatedYoYOptionletStripper::initialize} where one helper is constructed per maturity in the price
     * surface.
     */
    public static List< YoYOptionletHelper > makeHelpers(final List< Handle< Quote > > prices,
            final List< Integer > maturityIndices, final double notional, final InflationCapFloor.Type capFloorType,
            final Period lag, final DayCounter yoyDayCounter, final Calendar paymentCalendar, final int fixingDays,
            final YoYInflationIndex index, final CPI.InterpolationType interpolation, final double strike,
            final InflationCapFloorEngine pricer) {
        if ( prices.size() != maturityIndices.size() ) {
            throw new IllegalArgumentException("prices.size != maturityIndices.size");
        }
        final List< YoYOptionletHelper > result = new ArrayList<>(prices.size());
        for ( int i = 0; i < prices.size(); ++i ) {
            result.add(
                    new YoYOptionletHelper(prices.get(i), notional, capFloorType, lag, yoyDayCounter, paymentCalendar,
                            fixingDays, index, interpolation, strike, maturityIndices.get(i), pricer));
        }
        return result;
    }
}
