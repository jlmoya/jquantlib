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

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <http://quantlib.org/license.shtml>.
*/

package org.jquantlib.experimental.exoticoptions;

import org.jquantlib.QL;
import org.jquantlib.instruments.HolderExtensibleOption;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.math.distributions.BivariateCumulativeNormalDistributionDr78;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.pricingengines.BlackScholesCalculator;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.time.Frequency;

/**
 * Analytic engine for holder-extensible options.
 * <p>
 * Formulas from Haug, "Option Pricing Formulas".
 * <p>
 * Mirrors C++ QuantLib v1.42.1 {@code AnalyticHolderExtensibleOptionEngine} in
 * {@code ql/pricingengines/exotic/analyticholderextensibleoptionengine.{hpp,cpp}}.
 *
 * @author Jose Moya
 */
public class AnalyticHolderExtensibleOptionEngine extends OneAssetOption.EngineImpl {

    private final GeneralizedBlackScholesProcess process;
    private final HolderExtensibleOption.ArgumentsImpl a;
    private final OneAssetOption.ResultsImpl r;

    public AnalyticHolderExtensibleOptionEngine(final GeneralizedBlackScholesProcess process) {
        super(new HolderExtensibleOption.ArgumentsImpl(), new OneAssetOption.ResultsImpl());
        this.a = (HolderExtensibleOption.ArgumentsImpl) arguments_;
        this.r = (OneAssetOption.ResultsImpl) results_;
        this.process = process;
        this.process.addObserver(this);
    }

    @Override
    public void calculate() {
        // Spot
        final double S = process.stateVariable().currentLink().value();
        final double r0 = riskFreeRate();
        final double b = r0 - dividendYield();
        final double X1 = strike();
        final double X2 = a.secondStrike;
        final double T2 = secondExpiryTime();
        final double t1 = firstExpiryTime();
        final double A = a.premium;

        final double z1 = z1();
        final double z2 = z2();

        final double rho = Math.sqrt(t1 / T2);

        QL.require(a.payoff instanceof PlainVanillaPayoff, "non-plain payoff given");
        final PlainVanillaPayoff payoff = (PlainVanillaPayoff) a.payoff;

        // Quantlib requires sigma * sqrt(T) rather than just sigma/volatility
        final double vol = volatility();

        // Calculate dividend discount factor assuming continuous compounding (e^-rt)
        final double growth = dividendDiscount(t1);
        // Calculate payoff discount factor assuming continuous compounding
        final double discount = riskFreeDiscount(t1);

        double result;
        final double minusInf = Double.NEGATIVE_INFINITY;

        final double y1 = y1(payoff.optionType());
        final double y2 = y2(payoff.optionType());

        if (payoff.optionType() == Option.Type.Call) {
            // Instantiate payoff function for a call
            final PlainVanillaPayoff vanillaCallPayoff = new PlainVanillaPayoff(Option.Type.Call, X1);
            final double bsm = new BlackScholesCalculator(vanillaCallPayoff, S, growth,
                                                          vol * Math.sqrt(t1), discount).value();
            result = bsm
                    + S * Math.exp((b - r0) * T2) * M2(y1, y2, minusInf, z1, rho)
                    - X2 * Math.exp(-r0 * T2) * M2(y1 - vol * Math.sqrt(t1),
                                                   y2 - vol * Math.sqrt(t1),
                                                   minusInf,
                                                   z1 - vol * Math.sqrt(T2), rho)
                    - S * Math.exp((b - r0) * t1) * N2(y1, z2)
                    + X1 * Math.exp(-r0 * t1) * N2(y1 - vol * Math.sqrt(t1), z2 - vol * Math.sqrt(t1))
                    - A * Math.exp(-r0 * t1) * N2(y1 - vol * Math.sqrt(t1), y2 - vol * Math.sqrt(t1));
        } else {
            // Instantiate payoff function for a put
            final PlainVanillaPayoff vanillaPutPayoff = new PlainVanillaPayoff(Option.Type.Put, X1);
            result = new BlackScholesCalculator(vanillaPutPayoff, S, growth,
                                                vol * Math.sqrt(t1), discount).value()
                    - S * Math.exp((b - r0) * T2) * M2(y1, y2, minusInf, -z1, rho)
                    + X2 * Math.exp(-r0 * T2) * M2(y1 - vol * Math.sqrt(t1),
                                                   y2 - vol * Math.sqrt(t1),
                                                   minusInf,
                                                   -z1 + vol * Math.sqrt(T2), rho)
                    + S * Math.exp((b - r0) * t1) * N2(z2, y2)
                    - X1 * Math.exp(-r0 * t1) * N2(z2 - vol * Math.sqrt(t1), y2 - vol * Math.sqrt(t1))
                    - A * Math.exp(-r0 * t1) * N2(y1 - vol * Math.sqrt(t1), y2 - vol * Math.sqrt(t1));
        }
        r.value = result;
    }

    private double I1Call() {
        double sv = process.stateVariable().currentLink().value();
        final double A = a.premium;
        if (A == 0.0) {
            return 0.0;
        }
        BlackScholesCalculator bs = bsCalculator(sv, Option.Type.Call);
        double ci = bs.value();
        double dc = bs.delta();

        double yi = ci - A;
        // da/ds = 0
        double di = dc - 0;
        final double epsilon = 0.001;

        // Newton-Raphson process
        while (Math.abs(yi) > epsilon) {
            sv = sv - yi / di;

            bs = bsCalculator(sv, Option.Type.Call);
            ci = bs.value();
            dc = bs.delta();

            yi = ci - A;
            di = dc - 0;
        }
        return sv;
    }

    private double I2Call() {
        double sv = process.stateVariable().currentLink().value();
        final double X1 = strike();
        final double X2 = a.secondStrike;
        final double A = a.premium;
        final double T2 = secondExpiryTime();
        final double t1 = firstExpiryTime();
        final double r0 = riskFreeRate();

        final double val = X1 - X2 * Math.exp(-r0 * (T2 - t1));
        if (A < val) {
            return Double.POSITIVE_INFINITY;
        }
        BlackScholesCalculator bs = bsCalculator(sv, Option.Type.Call);
        double ci = bs.value();
        double dc = bs.delta();

        double yi = ci - A - sv + X1;
        // da/ds = 1
        double di = dc - 1;
        final double epsilon = 0.001;

        // Newton-Raphson process
        while (Math.abs(yi) > epsilon) {
            sv = sv - yi / di;

            bs = bsCalculator(sv, Option.Type.Call);
            ci = bs.value();
            dc = bs.delta();

            yi = ci - A - sv + X1;
            di = dc - 1;
        }
        return sv;
    }

    private double I1Put() {
        double sv = process.stateVariable().currentLink().value();
        // Strike
        final double X1 = strike();
        // Premium
        final double A = a.premium;

        BlackScholesCalculator bs = bsCalculator(sv, Option.Type.Put);
        double pi = bs.value();
        double dc = bs.delta();

        double yi = pi - A + sv - X1;
        // da/ds = 1
        double di = dc - 1;
        final double epsilon = 0.001;

        // Newton-Raphson process
        while (Math.abs(yi) > epsilon) {
            sv = sv - yi / di;

            bs = bsCalculator(sv, Option.Type.Put);
            pi = bs.value();
            dc = bs.delta();

            yi = pi - A + sv - X1;
            di = dc - 1;
        }
        return sv;
    }

    private double I2Put() {
        double sv = process.stateVariable().currentLink().value();
        final double A = a.premium;
        if (A == 0.0) {
            return Double.POSITIVE_INFINITY;
        }
        BlackScholesCalculator bs = bsCalculator(sv, Option.Type.Put);
        double pi = bs.value();
        double dc = bs.delta();

        double yi = pi - A;
        // da/ds = 0
        double di = dc - 0;
        final double epsilon = 0.001;

        // Newton-Raphson process
        while (Math.abs(yi) > epsilon) {
            sv = sv - yi / di;

            bs = bsCalculator(sv, Option.Type.Put);
            pi = bs.value();
            dc = bs.delta();

            yi = pi - A;
            di = dc - 0;
        }
        return sv;
    }

    private BlackScholesCalculator bsCalculator(final double spot, final Option.Type optionType) {
        final double X2 = a.secondStrike;
        final double T2 = secondExpiryTime();
        final double t1 = firstExpiryTime();
        final double t = T2 - t1;

        // payoff
        final PlainVanillaPayoff vanillaPayoff = new PlainVanillaPayoff(optionType, X2);

        // QuantLib requires sigma * sqrt(T) rather than just sigma/volatility
        final double vol = volatility() * Math.sqrt(t);
        // Calculate dividend discount factor assuming continuous compounding (e^-rt)
        final double growth = dividendDiscount(t);
        // Calculate payoff discount factor assuming continuous compounding
        final double discount = riskFreeDiscount(t);

        return new BlackScholesCalculator(vanillaPayoff, spot, growth, vol, discount);
    }

    private double M2(final double a, final double b, final double c, final double d, final double rho) {
        final BivariateCumulativeNormalDistributionDr78 cmlNormDist =
                new BivariateCumulativeNormalDistributionDr78(rho);
        return cmlNormDist.op(b, d) - cmlNormDist.op(a, d)
                - cmlNormDist.op(b, c) + cmlNormDist.op(a, c);
    }

    private double N2(final double a, final double b) {
        final CumulativeNormalDistribution normDist = new CumulativeNormalDistribution();
        return normDist.op(b) - normDist.op(a);
    }

    private double strike() {
        QL.require(a.payoff instanceof PlainVanillaPayoff, "non-plain payoff given");
        final PlainVanillaPayoff payoff = (PlainVanillaPayoff) a.payoff;
        return payoff.strike();
    }

    private double firstExpiryTime() {
        return process.time(a.exercise.lastDate());
    }

    private double secondExpiryTime() {
        return process.time(a.secondExpiryDate);
    }

    private double volatility() {
        return process.blackVolatility().currentLink().blackVol(firstExpiryTime(), strike());
    }

    private double riskFreeRate() {
        return process.riskFreeRate().currentLink().zeroRate(
                a.exercise.lastDate(),
                process.riskFreeRate().currentLink().dayCounter(),
                Compounding.Continuous, Frequency.NoFrequency).rate();
    }

    private double dividendYield() {
        return process.dividendYield().currentLink().zeroRate(
                a.exercise.lastDate(),
                process.dividendYield().currentLink().dayCounter(),
                Compounding.Continuous, Frequency.NoFrequency).rate();
    }

    private double dividendDiscount(final double t) {
        return process.dividendYield().currentLink().discount(t);
    }

    private double riskFreeDiscount(final double t) {
        return process.riskFreeRate().currentLink().discount(t);
    }

    private double y1(final Option.Type type) {
        final double S = process.stateVariable().currentLink().value();
        final double I2 = (type == Option.Type.Call) ? I2Call() : I2Put();

        final double b = riskFreeRate() - dividendYield();
        final double vol = volatility();
        final double t1 = firstExpiryTime();

        return (Math.log(S / I2) + (b + Math.pow(vol, 2) / 2) * t1) / (vol * Math.sqrt(t1));
    }

    private double y2(final Option.Type type) {
        final double S = process.stateVariable().currentLink().value();
        final double I1 = (type == Option.Type.Call) ? I1Call() : I1Put();

        final double b = riskFreeRate() - dividendYield();
        final double vol = volatility();
        final double t1 = firstExpiryTime();

        return (Math.log(S / I1) + (b + Math.pow(vol, 2) / 2) * t1) / (vol * Math.sqrt(t1));
    }

    private double z1() {
        final double S = process.stateVariable().currentLink().value();
        final double X2 = a.secondStrike;
        final double b = riskFreeRate() - dividendYield();
        final double vol = volatility();
        final double T2 = secondExpiryTime();

        return (Math.log(S / X2) + (b + Math.pow(vol, 2) / 2) * T2) / (vol * Math.sqrt(T2));
    }

    private double z2() {
        final double S = process.stateVariable().currentLink().value();
        final double X1 = strike();

        final double b = riskFreeRate() - dividendYield();
        final double vol = volatility();
        final double t1 = firstExpiryTime();

        return (Math.log(S / X1) + (b + Math.pow(vol, 2) / 2) * t1) / (vol * Math.sqrt(t1));
    }
}
