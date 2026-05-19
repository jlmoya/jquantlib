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
 Copyright (C) 2014 Master IMAFA - Polytech'Nice Sophia - Universite de Nice Sophia Antipolis

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.experimental.exoticoptions;

import org.jquantlib.instruments.ComplexChooserOption;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.math.distributions.BivariateCumulativeNormalDistributionDr78;
import org.jquantlib.pricingengines.BlackScholesCalculator;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.time.Frequency;

/**
 * Analytic engine for complex chooser option (Rubinstein 1991 closed-form, from Haug "Option Pricing Formulas").
 * <p>
 * Mirrors C++ QuantLib v1.42.1 {@code AnalyticComplexChooserEngine} in
 * {@code ql/pricingengines/exotic/analyticcomplexchooserengine.{hpp,cpp}}.
 *
 * @author Jose Moya
 */
public class AnalyticComplexChooserEngine extends OneAssetOption.EngineImpl {

    private final GeneralizedBlackScholesProcess process;
    private final ComplexChooserOption.ArgumentsImpl a;
    private final OneAssetOption.ResultsImpl r;

    public AnalyticComplexChooserEngine(final GeneralizedBlackScholesProcess process) {
        super(new ComplexChooserOption.ArgumentsImpl(), new OneAssetOption.ResultsImpl());
        this.a = (ComplexChooserOption.ArgumentsImpl) arguments_;
        this.r = (OneAssetOption.ResultsImpl) results_;
        this.process = process;
        this.process.addObserver(this);
    }

    @Override
    public void calculate() {
        final double S = process.x0();
        double b;
        double v;
        final double Xc = a.strikeCall;
        final double Xp = a.strikePut;
        final double T = choosingTime();
        final double Tc = callMaturity() - T;
        final double Tp = putMaturity() - T;

        final double i = criticalValue();

        b = riskFreeRate(T) - dividendYield(T);
        v = volatility(T);
        final double d1 = (Math.log(S / i) + (b + (v * v) / 2.0) * T) / (v * Math.sqrt(T));
        final double d2 = d1 - v * Math.sqrt(T);

        b = riskFreeRate(T + Tc) - dividendYield(T + Tc);
        v = volatility(Tc);
        final double y1 = (Math.log(S / Xc) + (b + (v * v) / 2.0) * Tc) / (v * Math.sqrt(Tc));

        b = riskFreeRate(T + Tp) - dividendYield(T + Tp);
        v = volatility(Tp);
        final double y2 = (Math.log(S / Xp) + (b + (v * v) / 2.0) * Tp) / (v * Math.sqrt(Tp));

        final double rho1 = Math.sqrt(T / Tc);
        final double rho2 = Math.sqrt(T / Tp);

        b = riskFreeRate(T + Tc) - dividendYield(T + Tc);
        double r1 = riskFreeRate(T + Tc);
        // NOTE: mirrors C++ reference exactly - C++ reuses the last-assigned `v`
        // (volatility(Tp)) for both Bivar(rho1)(d2, y1 - v*sqrt(Tc)) and
        // Bivar(rho2)(-d2, -y2 + v*sqrt(Tp)). Replicated here for parity.
        double complexChooser =
                S * Math.exp((b - r1) * Tc) * new BivariateCumulativeNormalDistributionDr78(rho1).op(d1, y1)
                        - Xc * Math.exp(-r1 * Tc) * new BivariateCumulativeNormalDistributionDr78(rho1).op(d2,
                        y1 - v * Math.sqrt(Tc));

        b = riskFreeRate(T + Tp) - dividendYield(T + Tp);
        r1 = riskFreeRate(T + Tp);
        complexChooser -=
                S * Math.exp((b - r1) * Tp) * new BivariateCumulativeNormalDistributionDr78(rho2).op(-d1, -y2);
        complexChooser += Xp * Math.exp(-r1 * Tp) * new BivariateCumulativeNormalDistributionDr78(rho2).op(-d2,
                -y2 + v * Math.sqrt(Tp));

        r.value = complexChooser;
    }

    private BlackScholesCalculator bsCalculator(final double spot, final Option.Type optionType) {
        final double vol;
        final double growth;
        final double discount;
        final double T = choosingTime();
        final double t;
        final PlainVanillaPayoff vanillaPayoff;

        if ( optionType == Option.Type.Call ) {
            t = callMaturity() - 2.0 * T;
            vanillaPayoff = new PlainVanillaPayoff(Option.Type.Call, strike(Option.Type.Call));
        } else {
            t = putMaturity() - 2.0 * T;
            vanillaPayoff = new PlainVanillaPayoff(Option.Type.Put, strike(Option.Type.Put));
        }
        // BlackScholesCalculator expects sigma*sqrt(t), not sigma alone.
        vol = volatility(t) * Math.sqrt(t);
        growth = dividendDiscount(t);
        discount = riskFreeDiscount(t);

        return new BlackScholesCalculator(vanillaPayoff, spot, growth, vol, discount);
    }

    private double criticalValue() {
        double Sv = process.x0();

        BlackScholesCalculator bs = bsCalculator(Sv, Option.Type.Call);
        double ci = bs.value();
        double dc = bs.delta();

        bs = bsCalculator(Sv, Option.Type.Put);
        double Pi = bs.value();
        double dp = bs.delta();

        double yi = ci - Pi;
        double di = dc - dp;
        final double epsilon = 0.001;

        // Newton-Raphson process
        while ( Math.abs(yi) > epsilon ) {
            Sv = Sv - yi / di;

            bs = bsCalculator(Sv, Option.Type.Call);
            ci = bs.value();
            dc = bs.delta();

            bs = bsCalculator(Sv, Option.Type.Put);
            Pi = bs.value();
            dp = bs.delta();

            yi = ci - Pi;
            di = dc - dp;
        }
        return Sv;
    }

    private double strike(final Option.Type optionType) {
        if ( optionType == Option.Type.Call ) {
            return a.strikeCall;
        }
        return a.strikePut;
    }

    private double choosingTime() {
        return process.time(a.choosingDate);
    }

    private double putMaturity() {
        return process.time(a.exercisePut.lastDate());
    }

    private double callMaturity() {
        return process.time(a.exerciseCall.lastDate());
    }

    private double volatility(final double t) {
        return process.blackVolatility().currentLink().blackVol(t, a.strikeCall);
    }

    private double dividendYield(final double t) {
        return process.dividendYield().currentLink().zeroRate(t, Compounding.Continuous, Frequency.NoFrequency, false)
                .rate();
    }

    private double dividendDiscount(final double t) {
        return process.dividendYield().currentLink().discount(t);
    }

    private double riskFreeRate(final double t) {
        return process.riskFreeRate().currentLink().zeroRate(t, Compounding.Continuous, Frequency.NoFrequency, false)
                .rate();
    }

    private double riskFreeDiscount(final double t) {
        return process.riskFreeRate().currentLink().discount(t);
    }
}
