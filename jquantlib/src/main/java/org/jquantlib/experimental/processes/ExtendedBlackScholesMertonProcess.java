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

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
/*
 Copyright (C) 2008 Frank Hoevermann
 */
package org.jquantlib.experimental.processes;

import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.processes.StochasticProcess1D;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Frequency;

/**
 * Extended Black-Scholes-Merton process with selectable evolution
 * discretization (Euler / Milstein / Predictor-Corrector).
 * <p>
 * Java port of v1.42.1
 * {@code ql/experimental/processes/extendedblackscholesprocess.{hpp,cpp}}.
 *
 * @author Phase 4n WI port
 */
public class ExtendedBlackScholesMertonProcess extends GeneralizedBlackScholesProcess {

    public enum Discretization { Euler, Milstein, PredictorCorrector }

    private final Discretization discretization_;

    public ExtendedBlackScholesMertonProcess(
            final Handle<? extends Quote> x0,
            final Handle<YieldTermStructure> dividendTS,
            final Handle<YieldTermStructure> riskFreeTS,
            final Handle<BlackVolTermStructure> blackVolTS) {
        this(x0, dividendTS, riskFreeTS, blackVolTS, Discretization.Milstein);
    }

    public ExtendedBlackScholesMertonProcess(
            final Handle<? extends Quote> x0,
            final Handle<YieldTermStructure> dividendTS,
            final Handle<YieldTermStructure> riskFreeTS,
            final Handle<BlackVolTermStructure> blackVolTS,
            final Discretization evolDisc) {
        super(x0, dividendTS, riskFreeTS, blackVolTS);
        this.discretization_ = evolDisc;
    }

    public ExtendedBlackScholesMertonProcess(
            final Handle<? extends Quote> x0,
            final Handle<YieldTermStructure> dividendTS,
            final Handle<YieldTermStructure> riskFreeTS,
            final Handle<BlackVolTermStructure> blackVolTS,
            final StochasticProcess1D.Discretization1D d,
            final Discretization evolDisc) {
        super(x0, dividendTS, riskFreeTS, blackVolTS, d);
        this.discretization_ = evolDisc;
    }

    @Override
    public double drift(final double t, final double x) {
        final double sigma = diffusion(t, x);
        final double t1 = t + 0.0001;
        return riskFreeRate().currentLink()
                .forwardRate(t, t1, Compounding.Continuous, Frequency.NoFrequency, true).rate()
             - dividendYield().currentLink()
                .forwardRate(t, t1, Compounding.Continuous, Frequency.NoFrequency, true).rate()
             - 0.5 * sigma * sigma;
    }

    @Override
    public double diffusion(final double t, final double x) {
        return blackVolatility().currentLink().blackVol(t, x, true);
    }

    @Override
    public double evolve(final double t0, final double x0, final double dt, final double dw) {
        switch (discretization_) {
        case Milstein: {
            final double sig0 = diffusion(t0, x0);
            return apply(x0, drift(t0, x0) * dt
                    + 0.5 * sig0 * sig0 * (dw * dw - 1) * dt
                    + sig0 * Math.sqrt(dt) * dw);
        }
        case Euler:
            return apply(expectation(t0, x0, dt), stdDeviation(t0, x0, dt) * dw);
        case PredictorCorrector: {
            final double predictor = apply(expectation(t0, x0, dt),
                    stdDeviation(t0, x0, dt) * dw);
            final double t1 = t0 + 0.0001;
            final double sigma0 = diffusion(t0, x0);
            final double sigma1 = diffusion(t0 + dt, predictor);
            final double rate0 = riskFreeRate().currentLink()
                    .forwardRate(t0, t1, Compounding.Continuous, Frequency.NoFrequency, true).rate()
                  - dividendYield().currentLink()
                    .forwardRate(t0, t1, Compounding.Continuous, Frequency.NoFrequency, true).rate()
                  - 0.5 * sigma0 * sigma0;
            final double rate1 = riskFreeRate().currentLink()
                    .forwardRate(t0 + dt, t1 + dt, Compounding.Continuous, Frequency.NoFrequency, true).rate()
                  - dividendYield().currentLink()
                    .forwardRate(t0 + dt, t1 + dt, Compounding.Continuous, Frequency.NoFrequency, true).rate()
                  - 0.5 * sigma1 * sigma1;
            final double driftterm = 0.5 * rate1 + 0.5 * rate0;
            final double diffusionterm = 0.5 * (sigma1 + sigma0);
            return apply(x0, driftterm * dt + diffusionterm * Math.sqrt(dt) * dw);
        }
        default:
            throw new IllegalStateException("unknown discretization scheme");
        }
    }
}
