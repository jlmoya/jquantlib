/*
 Copyright (C) 2026 JQuantLib migration

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
 Copyright (C) 2012 Master IMAFA - Polytech'Nice Sophia - Université de Nice Sophia Antipolis
*/

package org.jquantlib.pricingengines.barrier;

import org.jquantlib.QL;
import org.jquantlib.experimental.exoticoptions.TwoAssetBarrierOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.distributions.BivariateNormalDistribution;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.time.Frequency;

/**
 * Analytic engine for barrier option on two assets.
 * <p>
 * Mirrors {@code QuantLib::AnalyticTwoAssetBarrierEngine} from
 * {@code ql/pricingengines/barrier/analytictwoassetbarrierengine.cpp} (v1.42.1).
 * Formulas by Heynen and Kat are taken from Haug, "Option pricing formulas".
 *
 * @author JQuantLib migration
 */
public class AnalyticTwoAssetBarrierEngine extends TwoAssetBarrierOption.EngineImpl {

    private final GeneralizedBlackScholesProcess process1_;
    private final GeneralizedBlackScholesProcess process2_;
    private final Handle<Quote> rho_;

    public AnalyticTwoAssetBarrierEngine(final GeneralizedBlackScholesProcess process1,
                                         final GeneralizedBlackScholesProcess process2,
                                         final Handle<Quote> rho) {
        this.process1_ = process1;
        this.process2_ = process2;
        this.rho_ = rho;
        this.process1_.addObserver(this);
        this.process2_.addObserver(this);
        this.rho_.addObserver(this);
    }

    @Override
    public void calculate() {
        final TwoAssetBarrierOption.ArgumentsImpl a = args();
        final TwoAssetBarrierOption.ResultsImpl r = (TwoAssetBarrierOption.ResultsImpl) results_;

        QL.require(a.payoff instanceof PlainVanillaPayoff, "non-plain payoff given");
        final PlainVanillaPayoff payoff = (PlainVanillaPayoff) a.payoff;
        QL.require(payoff.strike() > 0.0, "strike must be positive");

        final double spot2 = process2_.x0();
        // option is triggered by S2
        QL.require(spot2 > 0.0, "negative or null underlying given");
        QL.require(!triggered(spot2), "barrier touched");

        switch (payoff.optionType()) {
          case Call:
            switch (a.barrierType) {
              case DownOut:
                r.value = A(1, -1) + B(1, -1);
                break;
              case UpOut:
                r.value = A(1, 1) + B(1, 1);
                break;
              case DownIn:
                r.value = call() - (A(1, -1) + B(1, -1));
                break;
              case UpIn:
                r.value = call() - (A(1, 1) + B(1, 1));
                break;
              default:
                throw new LibraryException("unknown barrier type");
            }
            break;
          case Put:
            switch (a.barrierType) {
              case DownOut:
                r.value = A(-1, -1) + B(-1, -1);
                break;
              case UpOut:
                r.value = A(-1, 1) + B(-1, 1);
                break;
              case DownIn:
                r.value = put() - (A(-1, -1) + B(-1, -1));
                break;
              case UpIn:
                r.value = put() - (A(-1, 1) + B(-1, 1));
                break;
              default:
                throw new LibraryException("unknown barrier type");
            }
            break;
          default:
            throw new LibraryException("unknown type");
        }
    }


    //
    // helpers (mirror C++ private members)
    //

    private double underlying1() { return process1_.x0(); }

    private double underlying2() { return process2_.x0(); }

    private double strike() {
        final PlainVanillaPayoff payoff = (PlainVanillaPayoff) args().payoff;
        return payoff.strike();
    }

    private double residualTime() {
        return process1_.time(args().exercise.lastDate());
    }

    private double volatility1() {
        return process1_.blackVolatility().currentLink().blackVol(residualTime(), strike());
    }

    private double volatility2() {
        return process2_.blackVolatility().currentLink().blackVol(residualTime(), strike());
    }

    private double barrier() { return args().barrier; }

    private double rho() { return rho_.currentLink().value(); }

    private double riskFreeRate() {
        return process1_.riskFreeRate().currentLink().zeroRate(
                residualTime(), Compounding.Continuous, Frequency.NoFrequency, false).rate();
    }

    private double dividendYield1() {
        return process1_.dividendYield().currentLink().zeroRate(
                residualTime(), Compounding.Continuous, Frequency.NoFrequency, false).rate();
    }

    private double dividendYield2() {
        return process2_.dividendYield().currentLink().zeroRate(
                residualTime(), Compounding.Continuous, Frequency.NoFrequency, false).rate();
    }

    private double costOfCarry1() { return riskFreeRate() - dividendYield1(); }

    private double costOfCarry2() { return riskFreeRate() - dividendYield2(); }

    private double mu(final double b, final double vol) {
        return b - (vol * vol) / 2;
    }

    private double call() {
        final CumulativeNormalDistribution nd = new CumulativeNormalDistribution();
        return underlying1() * nd.op(d1())
                - strike() * Math.exp(-riskFreeRate() * residualTime()) * nd.op(d2());
    }

    private double put() {
        final CumulativeNormalDistribution nd = new CumulativeNormalDistribution();
        return strike() * Math.exp(-riskFreeRate() * residualTime()) * nd.op(-d2())
                - underlying1() * nd.op(-d1());
    }

    private double d1() {
        final double T = residualTime();
        final double v1 = volatility1();
        return (Math.log(underlying1() / strike())
                + (mu(costOfCarry1(), v1) + v1 * v1) * T) / (v1 * Math.sqrt(T));
    }

    private double d2() {
        return d1() - volatility1() * Math.sqrt(residualTime());
    }

    /**
     * Mirrors C++ {@code A(eta, phi)} — Heynen-Kat closed form for two-asset barrier.
     * The C++ code recomputes d1..d4 and e1..e4 here (does not call the d1/d2/d3/d4/e1/e2/e3/e4
     * helpers), so we do the same.
     */
    private double A(final double eta, final double phi) {
        final double S1 = underlying1();
        final double S2 = underlying2();
        final double b1 = costOfCarry1();
        final double b2 = costOfCarry2();
        final double r = riskFreeRate();
        final double T = residualTime();
        final double H = barrier();
        final double X = strike();
        final double sigma1 = volatility1();
        final double sigma2 = volatility2();
        final double rho = rho_.currentLink().value();

        final double mu1 = b1 - sigma1 * sigma1 / 2.0;
        final double mu2 = b2 - sigma2 * sigma2 / 2.0;

        final double dd1 = (Math.log(S1 / X) + (mu1 + sigma1 * sigma1) * T)
                / (sigma1 * Math.sqrt(T));
        final double dd2 = dd1 - sigma1 * Math.sqrt(T);
        final double dd3 = dd1 + (2 * rho * Math.log(H / S2)) / (sigma2 * Math.sqrt(T));
        final double dd4 = dd2 + (2 * rho * Math.log(H / S2)) / (sigma2 * Math.sqrt(T));

        final double ee1 = (Math.log(H / S2) - (mu2 + rho * sigma1 * sigma2) * T)
                / (sigma2 * Math.sqrt(T));
        final double ee2 = ee1 + rho * sigma1 * Math.sqrt(T);
        final double ee3 = ee1 - (2 * Math.log(H / S2)) / (sigma2 * Math.sqrt(T));
        final double ee4 = ee2 - (2 * Math.log(H / S2)) / (sigma2 * Math.sqrt(T));

        final double w =
            eta * S1 * Math.exp((b1 - r) * T) *
            (M(eta * dd1, phi * ee1, -eta * phi * rho)
                - Math.exp((2 * (mu2 + rho * sigma1 * sigma2) * Math.log(H / S2)) / (sigma2 * sigma2))
                  * M(eta * dd3, phi * ee3, -eta * phi * rho))

            - eta * X * Math.exp(-r * T) *
              (M(eta * dd2, phi * ee2, -eta * phi * rho)
               - Math.exp((2 * mu2 * Math.log(H / S2)) / (sigma2 * sigma2))
                 * M(eta * dd4, phi * ee4, -eta * phi * rho));

        return w;
    }

    /** Mirrors C++ B(eta, phi) — returns 0 in v1.42.1. */
    private double B(final double eta, final double phi) {
        return 0.0;
    }

    private double M(final double mA, final double mB, final double rho) {
        final BivariateNormalDistribution f = new BivariateNormalDistribution(rho);
        return f.op(mA, mB);
    }
}
