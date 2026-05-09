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
 Copyright (C) 2004 Ferdinando Ametrano
 Copyright (C) 2004 Neil Firth
 Copyright (C) 2007 StatPro Italia srl

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
import org.jquantlib.instruments.BasketPayoff;
import org.jquantlib.instruments.MaxBasketPayoff;
import org.jquantlib.instruments.MinBasketPayoff;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.distributions.BivariateNormalDistribution;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.pricingengines.BlackFormula;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;

/**
 * Pricing engine for 2D European basket options (min/max of two assets).
 *
 * <p>This class implements formulae from "Options on the Minimum or the
 * Maximum of Two Risky Assets", René Stulz, Journal of Financial Economics
 * (1982) 10, 161-185.</p>
 *
 * <p>Ported from C++ QuantLib v1.42.1
 * {@code ql/pricingengines/basket/stulzengine.{hpp,cpp}}.</p>
 *
 * @author Jose Moya
 */
public class StulzEngine extends BasketOption.Engine {

    private final GeneralizedBlackScholesProcess process1;
    private final GeneralizedBlackScholesProcess process2;
    private final double rho;

    public StulzEngine(
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
    public void calculate() {
        QL.require(arguments_.exercise.type() == Exercise.Type.European,
                "not a European Option");

        final BasketPayoff basketPayoff = (BasketPayoff) arguments_.payoff;
        QL.require(basketPayoff != null, "non-basket payoff given");

        final boolean isMin = basketPayoff instanceof MinBasketPayoff;
        final boolean isMax = basketPayoff instanceof MaxBasketPayoff;
        QL.require(isMin || isMax, "unknown basket type");

        QL.require(basketPayoff.basePayoff() instanceof PlainVanillaPayoff,
                "non-plain payoff given");
        final PlainVanillaPayoff payoff =
                (PlainVanillaPayoff) basketPayoff.basePayoff();
        final double strike = payoff.strike();

        final double variance1 = process1.blackVolatility().currentLink()
                .blackVariance(arguments_.exercise.lastDate(), strike);
        final double variance2 = process2.blackVolatility().currentLink()
                .blackVariance(arguments_.exercise.lastDate(), strike);

        final double riskFreeDiscount = process1.riskFreeRate().currentLink()
                .discount(arguments_.exercise.lastDate());

        final double dividendDiscount1 = process1.dividendYield().currentLink()
                .discount(arguments_.exercise.lastDate());
        final double dividendDiscount2 = process2.dividendYield().currentLink()
                .discount(arguments_.exercise.lastDate());

        final double forward1 = process1.stateVariable().currentLink().value()
                * dividendDiscount1 / riskFreeDiscount;
        final double forward2 = process2.stateVariable().currentLink().value()
                * dividendDiscount2 / riskFreeDiscount;

        if (isMax) {
            switch (payoff.optionType()) {
                case Call:
                    results_.value = euroTwoAssetMaxBasketCall(
                            forward1, forward2, strike, riskFreeDiscount,
                            variance1, variance2, rho);
                    break;
                case Put:
                    // put-call parity for max basket
                    results_.value = strike * riskFreeDiscount
                            - euroTwoAssetMaxBasketCall(forward1, forward2, 0.0,
                                    riskFreeDiscount, variance1, variance2, rho)
                            + euroTwoAssetMaxBasketCall(forward1, forward2, strike,
                                    riskFreeDiscount, variance1, variance2, rho);
                    break;
                default:
                    throw new LibraryException("unknown option type");
            }
        } else {
            switch (payoff.optionType()) {
                case Call:
                    results_.value = euroTwoAssetMinBasketCall(
                            forward1, forward2, strike, riskFreeDiscount,
                            variance1, variance2, rho);
                    break;
                case Put:
                    // put-call parity for min basket
                    results_.value = strike * riskFreeDiscount
                            - euroTwoAssetMinBasketCall(forward1, forward2, 0.0,
                                    riskFreeDiscount, variance1, variance2, rho)
                            + euroTwoAssetMinBasketCall(forward1, forward2, strike,
                                    riskFreeDiscount, variance1, variance2, rho);
                    break;
                default:
                    throw new LibraryException("unknown option type");
            }
        }
    }

    /**
     * Stulz 1982 closed-form value of a European call on the minimum of two
     * risky assets.
     */
    private static double euroTwoAssetMinBasketCall(
            final double forward1, final double forward2, final double strike,
            final double riskFreeDiscount,
            final double variance1, final double variance2,
            final double rho) {

        final double stdDev1 = Math.sqrt(variance1);
        final double stdDev2 = Math.sqrt(variance2);

        final double variance = variance1 + variance2 - 2.0 * rho * stdDev1 * stdDev2;
        final double stdDev = Math.sqrt(variance);

        final double modRho1 = (rho * stdDev2 - stdDev1) / stdDev;
        final double modRho2 = (rho * stdDev1 - stdDev2) / stdDev;

        final double D1 = (Math.log(forward1 / forward2) + 0.5 * variance) / stdDev;

        final double alpha;
        final double beta;
        final double gamma;
        if (strike != 0.0) {
            final BivariateNormalDistribution bivCNorm =
                    new BivariateNormalDistribution(rho);
            final BivariateNormalDistribution bivCNormMod1 =
                    new BivariateNormalDistribution(modRho1);
            final BivariateNormalDistribution bivCNormMod2 =
                    new BivariateNormalDistribution(modRho2);

            final double D1_1 =
                    (Math.log(forward1 / strike) + 0.5 * variance1) / stdDev1;
            final double D1_2 =
                    (Math.log(forward2 / strike) + 0.5 * variance2) / stdDev2;
            alpha = bivCNormMod1.op(D1_1, -D1);
            beta = bivCNormMod2.op(D1_2, D1 - stdDev);
            gamma = bivCNorm.op(D1_1 - stdDev1, D1_2 - stdDev2);
        } else {
            final CumulativeNormalDistribution cum = new CumulativeNormalDistribution();
            alpha = cum.op(-D1);
            beta = cum.op(D1 - stdDev);
            gamma = 1.0;
        }

        return riskFreeDiscount
                * (forward1 * alpha + forward2 * beta - strike * gamma);
    }

    /**
     * Stulz 1982 closed-form value of a European call on the maximum of two
     * risky assets.
     *
     * <p>Computed via decomposition: max-call = call1 + call2 - min-call.</p>
     */
    private static double euroTwoAssetMaxBasketCall(
            final double forward1, final double forward2, final double strike,
            final double riskFreeDiscount,
            final double variance1, final double variance2,
            final double rho) {

        final double black1 = BlackFormula.blackFormula(
                Option.Type.Call, strike, forward1,
                Math.sqrt(variance1)) * riskFreeDiscount;
        final double black2 = BlackFormula.blackFormula(
                Option.Type.Call, strike, forward2,
                Math.sqrt(variance2)) * riskFreeDiscount;

        return black1 + black2
                - euroTwoAssetMinBasketCall(forward1, forward2, strike,
                        riskFreeDiscount, variance1, variance2, rho);
    }
}
