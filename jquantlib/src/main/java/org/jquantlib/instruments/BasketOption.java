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
 */

/*
 Copyright (C) 2003 Neil Firth
 Copyright (C) 2007 StatPro Italia srl
 Copyright (C) 2007 Joseph Wang

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.
 */

package org.jquantlib.instruments;

import org.jquantlib.exercise.Exercise;
import org.jquantlib.pricingengines.GenericEngine;

/**
 * Basket option on a number of assets.
 *
 * <p>A basket option's payoff is a function of multiple underlying asset
 * values at exercise, combined into a single scalar by a {@link BasketPayoff}
 * (min/max/average/spread) and then fed into a base single-asset payoff
 * (typically a {@link PlainVanillaPayoff}).</p>
 *
 * <p>Ported from C++ QuantLib v1.42.1
 * {@code ql/instruments/basketoption.hpp}.</p>
 *
 * @author Jose Moya
 */
public class BasketOption extends MultiAssetOption {

    public BasketOption(final BasketPayoff payoff, final Exercise exercise) {
        super(payoff, exercise);
    }

    /**
     * Engine base class for basket options.
     *
     * <p>Mirrors C++ {@code BasketOption::engine} which is a typedef of
     * {@code GenericEngine<BasketOption::arguments, BasketOption::results>}.
     * Concrete engines extend this to provide pricing.</p>
     */
    public static abstract class Engine
            extends GenericEngine<MultiAssetOption.ArgumentsImpl, MultiAssetOption.ResultsImpl>
            implements MultiAssetOption.Engine {

        public Engine() {
            super(new MultiAssetOption.ArgumentsImpl(), new MultiAssetOption.ResultsImpl());
        }
    }
}
