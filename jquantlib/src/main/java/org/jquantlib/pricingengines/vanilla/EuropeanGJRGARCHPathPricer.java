/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2008 Yee Man Chan
*/

package org.jquantlib.pricingengines.vanilla;

import org.jquantlib.instruments.Option;
import org.jquantlib.methods.montecarlo.EuropeanHestonPathPricer;

/**
 * Path pricer for European vanilla payoffs on a multi-path whose first sub-path carries the GJR-GARCH asset trajectory.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/pricingengines/vanilla/mceuropeangjrgarchengine.hpp}
 * {@code EuropeanGJRGARCHPathPricer} (Phase 2 L3-D). The C++ class is binary-equivalent to {@code EuropeanHestonPathPricer}:
 * both evaluate the payoff at the terminal value of sub-path 0 and discount by the precomputed factor. Sub-path 1
 * (variance / squared-residuals process) is unused by the payoff; it only affects path generation.
 *
 * <p>Implemented as a trivial subclass of
 * {@link EuropeanHestonPathPricer} to preserve the C++ class identity while reusing the existing implementation.
 *
 * @see MCEuropeanGjrGarchEngine
 */
public class EuropeanGJRGARCHPathPricer extends EuropeanHestonPathPricer {

    public EuropeanGJRGARCHPathPricer(final Option.Type type, final double strike, final double discount) {
        super(type, strike, discount);
    }
}
