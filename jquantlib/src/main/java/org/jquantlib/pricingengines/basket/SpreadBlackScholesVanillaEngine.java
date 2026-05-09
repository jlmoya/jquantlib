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
 Copyright (C) 2024 Klaus Spanderen

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.
 */

package org.jquantlib.pricingengines.basket;

import org.jquantlib.QL;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.BasketOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.SpreadBasketPayoff;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;

/**
 * Base class for 2D spread pricing engines using the Black-Scholes model.
 *
 * <p>Concrete subclasses (e.g. {@link KirkEngine},
 * {@link BjerksundStenslandSpreadEngine}) implement the
 * {@link #calculateSpread(double, double, double, Option.Type, double, double, double)}
 * hook to provide the actual closed-form spread-option formula.</p>
 *
 * <p>Ported from C++ QuantLib v1.42.1
 * {@code ql/pricingengines/basket/spreadblackscholesvanillaengine.{hpp,cpp}}.</p>
 *
 * @author Jose Moya
 */
public abstract class SpreadBlackScholesVanillaEngine extends BasketOption.Engine {

    protected final GeneralizedBlackScholesProcess process1;
    protected final GeneralizedBlackScholesProcess process2;
    protected final double rho;

    public SpreadBlackScholesVanillaEngine(
            final GeneralizedBlackScholesProcess process1,
            final GeneralizedBlackScholesProcess process2,
            final double correlation) {
        this.process1 = process1;
        this.process2 = process2;
        this.rho = correlation;
        this.process1.addObserver(this);
        this.process2.addObserver(this);
    }

    @Override
    public final void calculate() {
        QL.require(arguments_.exercise.type() == Exercise.Type.European,
                "not a European exercise");

        QL.require(arguments_.payoff instanceof SpreadBasketPayoff,
                "spread payoff expected");
        final SpreadBasketPayoff spreadPayoff = (SpreadBasketPayoff) arguments_.payoff;

        QL.require(spreadPayoff.basePayoff() instanceof PlainVanillaPayoff,
                "non-plain payoff given");
        final PlainVanillaPayoff payoff =
                (PlainVanillaPayoff) spreadPayoff.basePayoff();
        final double strike = payoff.strike();
        final Option.Type optionType = payoff.optionType();

        // Forwards: F = S * dividend_discount / risk_free_discount
        final double f1 = process1.stateVariable().currentLink().value()
                / process1.riskFreeRate().currentLink().discount(arguments_.exercise.lastDate())
                * process1.dividendYield().currentLink().discount(arguments_.exercise.lastDate());

        final double f2 = process2.stateVariable().currentLink().value()
                / process2.riskFreeRate().currentLink().discount(arguments_.exercise.lastDate())
                * process2.dividendYield().currentLink().discount(arguments_.exercise.lastDate());

        final double variance1 = process1.blackVolatility().currentLink()
                .blackVariance(arguments_.exercise.lastDate(), f1);
        final double variance2 = process2.blackVolatility().currentLink()
                .blackVariance(arguments_.exercise.lastDate(), f2);

        final double df = process1.riskFreeRate().currentLink()
                .discount(arguments_.exercise.lastDate());

        results_.value = calculateSpread(
                f1, f2, strike, optionType, variance1, variance2, df);
    }

    /**
     * Concrete spread-option formula.
     *
     * @param f1 forward of asset 1
     * @param f2 forward of asset 2
     * @param strike strike price
     * @param optionType call or put
     * @param variance1 total variance of asset 1 to maturity
     * @param variance2 total variance of asset 2 to maturity
     * @param df risk-free discount factor to maturity
     * @return spread option value
     */
    protected abstract double calculateSpread(
            double f1, double f2, double strike, Option.Type optionType,
            double variance1, double variance2, double df);
}
