/*
 Copyright (C) 2026 Jose Moya

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
 Copyright (C) 2010, 2011 Ferdinando Ametrano

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.
*/

package org.jquantlib.instruments.bonds;

import org.jquantlib.quotes.Quote;

/**
 * {@link Quote} adapter exposing the equivalent-swap spread
 * ({@code yield - swapRate}) of a {@link RendistatoCalculator}.
 *
 * <p>Java port of QuantLib v1.42.1 {@code RendistatoEquivalentSwapSpreadQuote}
 * (ql/instruments/bonds/btp.{hpp,cpp}).
 *
 * @author Jose Moya
 */
public class RendistatoEquivalentSwapSpreadQuote extends Quote {

    private final RendistatoCalculator r_;

    public RendistatoEquivalentSwapSpreadQuote(final RendistatoCalculator r) {
        this.r_ = r;
    }

    @Override
    public double value() {
        return r_.equivalentSwapSpread();
    }

    @Override
    public boolean isValid() {
        try {
            value();
            return true;
        } catch (final Exception e) {
            return false;
        }
    }
}
