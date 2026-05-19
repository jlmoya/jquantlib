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
 Copyright (C) 2014 Master IMAFA - Polytech'Nice Sophia - Université de Nice Sophia Antipolis

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.experimental.exoticoptions;

import org.jquantlib.QL;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.MultiAssetOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.distributions.BivariateCumulativeNormalDistributionDr78;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.time.Frequency;

/**
 * Analytic engine for two-asset correlation option (Zhang 1995 closed-form, from Haug "Option Pricing Formulas").
 * <p>
 * Mirrors C++ QuantLib v1.42.1 {@code AnalyticTwoAssetCorrelationEngine} in
 * {@code ql/pricingengines/exotic/analytictwoassetcorrelationengine.{hpp,cpp}}.
 *
 * @author Jose Moya
 */
public class AnalyticTwoAssetCorrelationEngine extends MultiAssetOption.EngineImpl {

    private final GeneralizedBlackScholesProcess p1;
    private final GeneralizedBlackScholesProcess p2;
    private final Handle< ? extends Quote > correlation;
    private final TwoAssetCorrelationOption.ArgumentsImpl a;
    private final MultiAssetOption.ResultsImpl r;

    public AnalyticTwoAssetCorrelationEngine(final GeneralizedBlackScholesProcess p1,
            final GeneralizedBlackScholesProcess p2, final Handle< ? extends Quote > correlation) {
        super(new TwoAssetCorrelationOption.ArgumentsImpl(), new MultiAssetOption.ResultsImpl());
        this.a = (TwoAssetCorrelationOption.ArgumentsImpl) arguments_;
        this.r = results_;
        this.p1 = p1;
        this.p2 = p2;
        this.correlation = correlation;
        this.p1.addObserver(this);
        this.p2.addObserver(this);
        this.correlation.addObserver(this);
    }

    @Override
    public void calculate() {
        final double rho = correlation.currentLink().value();
        final BivariateCumulativeNormalDistributionDr78 M = new BivariateCumulativeNormalDistributionDr78(rho);

        QL.require(a.payoff instanceof PlainVanillaPayoff, "non-plain payoff given");
        final PlainVanillaPayoff payoff = (PlainVanillaPayoff) a.payoff;
        QL.require(payoff.strike() > 0.0, "strike must be positive");
        final Exercise exercise = a.exercise;
        final double strike = payoff.strike(); // X1
        final double spot = p1.x0();
        QL.require(spot > 0.0, "negative or null underlying given");

        final double sigma1 = p1.blackVolatility().currentLink()
                .blackVol(p1.time(exercise.lastDate()), payoff.strike());
        final double sigma2 = p2.blackVolatility().currentLink()
                .blackVol(p2.time(exercise.lastDate()), payoff.strike());

        final double T = p2.time(a.exercise.lastDate());

        final double s1 = p1.x0();
        final double s2 = p2.x0();
        final double q1 = p1.dividendYield().currentLink()
                .zeroRate(T, Compounding.Continuous, Frequency.NoFrequency, false).rate();
        final double q2 = p2.dividendYield().currentLink()
                .zeroRate(T, Compounding.Continuous, Frequency.NoFrequency, false).rate();
        final double rRate = p1.riskFreeRate().currentLink()
                .zeroRate(T, Compounding.Continuous, Frequency.NoFrequency, false).rate();
        final double b1 = rRate - q1;
        final double b2 = rRate - q2;

        final double sqrtT = Math.sqrt(T);

        final double y1 = (Math.log(s1 / strike) + (b1 - (sigma1 * sigma1) / 2.0) * T) / (sigma1 * sqrtT);
        final double y2 = (Math.log(s2 / a.X2) + (b2 - (sigma2 * sigma2) / 2.0) * T) / (sigma2 * sqrtT);

        switch ( payoff.optionType() ) {
        case Call:
            r.value = s2 * Math.exp((b2 - rRate) * T) * M.op(y2 + sigma2 * sqrtT, y1 + rho * sigma2 * sqrtT)
                    - a.X2 * Math.exp(-rRate * T) * M.op(y2, y1);
            break;
        case Put:
            r.value = a.X2 * Math.exp(-rRate * T) * M.op(-y2, -y1) - s2 * Math.exp((b2 - rRate) * T) * M.op(
                    -y2 - sigma2 * sqrtT, -y1 - rho * sigma2 * sqrtT);
            break;
        default:
            throw new LibraryException(Option.Type.UNKNOWN_OPTION_TYPE);
        }
    }
}
