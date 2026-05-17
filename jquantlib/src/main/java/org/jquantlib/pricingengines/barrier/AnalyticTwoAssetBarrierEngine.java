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
 Copyright (C) 2012 Master IMAFA - Polytech'Nice Sophia - Université de Nice Sophia Antipolis

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.pricingengines.barrier;

import org.jquantlib.QL;
import org.jquantlib.instruments.BarrierType;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.TwoAssetBarrierOption;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.distributions.BivariateCumulativeNormalDistributionDr78;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.time.Frequency;

/**
 * Analytic engine for barrier option on two assets (Heynen-Kat 1994 closed-form,
 * from Haug "Option Pricing Formulas").
 * <p>
 * The first asset drives the strike-based payoff; the second asset is monitored
 * for barrier touch. The {@code rho} {@link Handle} is the correlation between
 * the two driving Brownian motions.
 * <p>
 * Mirrors C++ QuantLib v1.42.1 {@code AnalyticTwoAssetBarrierEngine} in
 * {@code ql/pricingengines/barrier/analytictwoassetbarrierengine.{hpp,cpp}}.
 *
 * @author Jose Moya
 */
public class AnalyticTwoAssetBarrierEngine extends TwoAssetBarrierOption.EngineImpl {

    private static final String UNKNOWN_TYPE = "unknown type";

    private final GeneralizedBlackScholesProcess process1_;
    private final GeneralizedBlackScholesProcess process2_;
    private final Handle<? extends Quote> rho_;

    private final TwoAssetBarrierOption.ArgumentsImpl a;
    private final TwoAssetBarrierOption.ResultsImpl   r;

    public AnalyticTwoAssetBarrierEngine(final GeneralizedBlackScholesProcess process1,
                                         final GeneralizedBlackScholesProcess process2,
                                         final Handle<? extends Quote> rho) {
        super();
        this.process1_ = process1;
        this.process2_ = process2;
        this.rho_      = rho;
        this.a = (TwoAssetBarrierOption.ArgumentsImpl) arguments_;
        this.r = (TwoAssetBarrierOption.ResultsImpl)   results_;
        this.process1_.addObserver(this);
        this.process2_.addObserver(this);
        this.rho_.addObserver(this);
    }


    //
    // implements PricingEngine
    //

    @Override
    public void calculate() {
        QL.require(a.payoff instanceof PlainVanillaPayoff, "non-plain payoff given");
        final PlainVanillaPayoff payoff = (PlainVanillaPayoff) a.payoff;
        QL.require(payoff.strike() > 0.0, "strike must be positive");

        final double spot2 = process2_.x0();
        // option is triggered by S2
        QL.require(spot2 > 0.0, "negative or null underlying given");
        QL.require(!triggered(spot2), "barrier touched");

        final BarrierType barrierType = a.barrierType;

        switch (payoff.optionType()) {
          case Call:
            switch (barrierType) {
              case DownOut:
                r.value = A( 1, -1) + B( 1, -1);
                break;
              case UpOut:
                r.value = A( 1,  1) + B( 1,  1);
                break;
              case DownIn:
                r.value = call() - (A( 1, -1) + B( 1, -1));
                break;
              case UpIn:
                r.value = call() - (A( 1,  1) + B( 1,  1));
                break;
              default:
                throw new LibraryException(UNKNOWN_TYPE);
            }
            break;
          case Put:
            switch (barrierType) {
              case DownOut:
                r.value = A(-1, -1) + B(-1, -1);
                break;
              case UpOut:
                r.value = A(-1,  1) + B(-1,  1);
                break;
              case DownIn:
                r.value = put() - (A(-1, -1) + B(-1, -1));
                break;
              case UpIn:
                r.value = put() - (A(-1,  1) + B(-1,  1));
                break;
              default:
                throw new LibraryException(UNKNOWN_TYPE);
            }
            break;
          default:
            throw new LibraryException(UNKNOWN_TYPE);
        }
    }


    //
    // private helpers (mirror C++ engine helpers)
    //

    private double underlying1() { return process1_.x0(); }
    private double underlying2() { return process2_.x0(); }

    private double strike() {
        QL.require(a.payoff instanceof PlainVanillaPayoff, "non-plain payoff given");
        return ((PlainVanillaPayoff) a.payoff).strike();
    }

    private double residualTime() {
        return process1_.time(a.exercise.lastDate());
    }

    private double volatility1() {
        return process1_.blackVolatility().currentLink().blackVol(residualTime(), strike());
    }

    private double volatility2() {
        return process2_.blackVolatility().currentLink().blackVol(residualTime(), strike());
    }

    private double barrier() { return a.barrier; }

    private double rho() { return rho_.currentLink().value(); }

    private double riskFreeRate() {
        return process1_.riskFreeRate().currentLink()
                .zeroRate(residualTime(), Compounding.Continuous, Frequency.NoFrequency, false)
                .rate();
    }

    private double dividendYield1() {
        return process1_.dividendYield().currentLink()
                .zeroRate(residualTime(), Compounding.Continuous, Frequency.NoFrequency, false)
                .rate();
    }

    private double dividendYield2() {
        return process2_.dividendYield().currentLink()
                .zeroRate(residualTime(), Compounding.Continuous, Frequency.NoFrequency, false)
                .rate();
    }

    private double costOfCarry1() { return riskFreeRate() - dividendYield1(); }
    private double costOfCarry2() { return riskFreeRate() - dividendYield2(); }

    private double mu(final double b, final double vol) {
        return b - (vol * vol) / 2.0;
    }

    private double call() {
        final CumulativeNormalDistribution nd = new CumulativeNormalDistribution();
        final double T = residualTime();
        final double sqrtT = Math.sqrt(T);
        final double sigma1 = volatility1();
        final double S1 = underlying1();
        final double X  = strike();
        final double r  = riskFreeRate();
        final double b1 = costOfCarry1();
        final double mu1 = mu(b1, sigma1);
        final double d1 = (Math.log(S1 / X) + (mu1 + sigma1 * sigma1) * T) / (sigma1 * sqrtT);
        final double d2 = d1 - sigma1 * sqrtT;
        return S1 * nd.op(d1) - X * Math.exp(-r * T) * nd.op(d2);
    }

    private double put() {
        final CumulativeNormalDistribution nd = new CumulativeNormalDistribution();
        final double T = residualTime();
        final double sqrtT = Math.sqrt(T);
        final double sigma1 = volatility1();
        final double S1 = underlying1();
        final double X  = strike();
        final double r  = riskFreeRate();
        final double b1 = costOfCarry1();
        final double mu1 = mu(b1, sigma1);
        final double d1 = (Math.log(S1 / X) + (mu1 + sigma1 * sigma1) * T) / (sigma1 * sqrtT);
        final double d2 = d1 - sigma1 * sqrtT;
        return X * Math.exp(-r * T) * nd.op(-d2) - S1 * nd.op(-d1);
    }

    /**
     * Heynen-Kat closed-form contribution {@code A(eta, phi)}.
     * <p>
     * Mirrors C++ {@code AnalyticTwoAssetBarrierEngine::A(eta, phi)}.
     */
    @SuppressWarnings("PMD.MethodNamingConventions")
    private double A(final double eta, final double phi) {
        final double S1 = underlying1();
        final double S2 = underlying2();
        final double b1 = costOfCarry1();
        final double b2 = costOfCarry2();
        final double r  = riskFreeRate();
        final double T  = residualTime();
        final double H  = barrier();
        final double X  = strike();
        final double sigma1 = volatility1();
        final double sigma2 = volatility2();
        final double rho    = rho();

        final double sqrtT = Math.sqrt(T);

        final double mu1 = b1 - sigma1 * sigma1 / 2.0;
        final double mu2 = b2 - sigma2 * sigma2 / 2.0;

        final double d1 = (Math.log(S1 / X) + (mu1 + sigma1 * sigma1) * T) / (sigma1 * sqrtT);
        final double d2 = d1 - sigma1 * sqrtT;
        final double d3 = d1 + (2.0 * rho * Math.log(H / S2)) / (sigma2 * sqrtT);
        final double d4 = d2 + (2.0 * rho * Math.log(H / S2)) / (sigma2 * sqrtT);

        final double e1 = (Math.log(H / S2) - (mu2 + rho * sigma1 * sigma2) * T) / (sigma2 * sqrtT);
        final double e2 = e1 + rho * sigma1 * sqrtT;
        final double e3 = e1 - (2.0 * Math.log(H / S2)) / (sigma2 * sqrtT);
        final double e4 = e2 - (2.0 * Math.log(H / S2)) / (sigma2 * sqrtT);

        final double w =
            eta * S1 * Math.exp((b1 - r) * T) *
              (M(eta * d1, phi * e1, -eta * phi * rho)
               - Math.exp((2.0 * (mu2 + rho * sigma1 * sigma2) * Math.log(H / S2))
                          / (sigma2 * sigma2))
                 * M(eta * d3, phi * e3, -eta * phi * rho))
          - eta * X * Math.exp(-r * T) *
              (M(eta * d2, phi * e2, -eta * phi * rho)
               - Math.exp((2.0 * mu2 * Math.log(H / S2)) / (sigma2 * sigma2))
                 * M(eta * d4, phi * e4, -eta * phi * rho));

        return w;
    }

    /**
     * Heynen-Kat closed-form contribution {@code B(eta, phi)}.
     * <p>
     * Currently returns 0, matching C++ v1.42.1 placeholder for the partial
     * barrier surface.
     */
    @SuppressWarnings("PMD.MethodNamingConventions")
    private double B(final double eta, final double phi) {
        return 0.0;
    }

    /**
     * Bivariate cumulative normal CDF at {@code (m_a, m_b)} with correlation
     * {@code rho}.
     */
    @SuppressWarnings("PMD.MethodNamingConventions")
    private double M(final double mA, final double mB, final double rho) {
        final BivariateCumulativeNormalDistributionDr78 f =
                new BivariateCumulativeNormalDistributionDr78(rho);
        return f.op(mA, mB);
    }
}
