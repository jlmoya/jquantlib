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

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
/*
 Copyright (C) 2014 Fabien Le Floc'h

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.
*/
package org.jquantlib.pricingengines.vanilla;

/**
 * Interface representing a Heston-implied-volatility expansion formula.
 *
 * <p>Phase 5h.5 port of {@code QuantLib::HestonExpansion}
 * (v1.42.1 ql/pricingengines/vanilla/hestonexpansionengine.{hpp,cpp}). Pinned
 * commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>During calibration, an instance is initialized once per implied-volatility
 * surface slice (i.e., per expiry), then queried for each strike via
 * {@link #impliedVolatility(double, double)}.
 */
public interface HestonExpansion {

    /**
     * Compute the Heston-model implied volatility at the supplied strike,
     * given the forward.
     *
     * @param strike  strike price
     * @param forward forward price (= spot * dividendDiscount / riskFreeDiscount)
     * @return implied (Black) volatility at this strike
     */
    double impliedVolatility(double strike, double forward);
}
