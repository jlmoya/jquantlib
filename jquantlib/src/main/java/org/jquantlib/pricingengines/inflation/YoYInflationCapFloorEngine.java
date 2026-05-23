/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2009 Chris Kenyon
*/

package org.jquantlib.pricingengines.inflation;

import org.jquantlib.indexes.YoYInflationIndex;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatility.inflation.YoYOptionletVolatilitySurface;

/**
 * C++-named alias for {@link InflationCapFloorEngine}.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/pricingengines/inflation/inflationcapfloorengines.hpp}
 * {@code YoYInflationCapFloorEngine} (Phase 2 L3-D). The Java port previously named this base
 * {@link InflationCapFloorEngine} (file name {@code InflationCapFloorEngine.java}) to align with the C++ file name; this
 * top-level alias matches the C++ class identifier so call sites that look up {@code YoYInflationCapFloorEngine} resolve
 * correctly.
 *
 * <p>Cannot be instantiated directly — subclass via
 * {@link YoYInflationBlackCapFloorEngine}, {@link YoYInflationUnitDisplacedBlackCapFloorEngine}, or
 * {@link YoYInflationBachelierCapFloorEngine}.
 */
public abstract class YoYInflationCapFloorEngine extends InflationCapFloorEngine {

    protected YoYInflationCapFloorEngine(final YoYInflationIndex index,
            final Handle< YoYOptionletVolatilitySurface > volatility,
            final Handle< YieldTermStructure > nominalTermStructure) {
        super(index, volatility, nominalTermStructure);
    }
}
