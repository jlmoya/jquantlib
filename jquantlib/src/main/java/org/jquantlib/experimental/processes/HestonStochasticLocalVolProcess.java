/*
 Copyright (C) 2015 Johannes Goettker-Schnetmann
 Copyright (C) 2015 Klaus Spanderen

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.experimental.processes;

import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.processes.StochasticProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.LocalVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;

/**
 * Heston Stochastic-Local-Volatility (HSLV) process — Heston with an extra
 * leverage function {@code L(t, S)} multiplying the volatility.
 * <p>
 * Java port of v1.42.1 {@code ql/processes/hestonslvprocess.{hpp,cpp}}
 * (Phase 5h.5-SLV WI-3).
 * <p>
 * The process satisfies:
 * <pre>
 *   dS/S = (r-q) dt + L(t, S) * sqrt(v) dW1
 *   dv   = kappa*(theta - v) dt + (eta*sigma) * sqrt(v) dW2
 *   dW1 dW2 = rho dt
 * </pre>
 * where {@code eta = mixingFactor} blends the diffusion of the variance
 * factor against the leverage function.
 *
 * @author Phase 5h.5-SLV port
 */
public class HestonStochasticLocalVolProcess extends StochasticProcess {

    private double kappa, theta, sigma, rho, v0, mixingFactor, mixedSigma;
    private final HestonProcess hestonProcess;
    private final LocalVolTermStructure leverageFct;

    public HestonStochasticLocalVolProcess(final HestonProcess hestonProcess,
                                           final LocalVolTermStructure leverageFct) {
        this(hestonProcess, leverageFct, 1.0);
    }

    public HestonStochasticLocalVolProcess(final HestonProcess hestonProcess,
                                           final LocalVolTermStructure leverageFct,
                                           final double mixingFactor) {
        this.hestonProcess = hestonProcess;
        this.leverageFct = leverageFct;
        this.mixingFactor = mixingFactor;
        hestonProcess.addObserver(this);
        // Force the underlying HestonProcess to warm its s0v_/v0v_ caches
        // before our setParameters() reads them. In C++ HestonProcess::initialValues()
        // reads the Quote values directly each call (no cache); the Java port caches
        // them in s0v_/v0v_ and only refreshes via update(). Our addObserver(this)
        // above registers for future notifications but does not fire one synchronously,
        // so the cache would otherwise stay at its default 0.0 until the first
        // notification — which can leave initialValues() returning (0, 0) and produce
        // degenerate S=0 trajectories through evolve(). Mirrors what would happen on
        // any subsequent notification while staying within our process file.
        hestonProcess.update();
        setParameters();
    }

    @Override
    public int size() { return 2; }

    @Override
    public int factors() { return 2; }

    @Override
    public void update() {
        setParameters();
        super.update();
    }

    @Override
    public Array initialValues() {
        return hestonProcess.initialValues();
    }

    @Override
    public Array apply(final Array x0, final Array dx) {
        return hestonProcess.apply(x0, dx);
    }

    @Override
    public Array drift(final double t, final Array x) {
        final double vol = Math.max(1e-8,
                Math.sqrt(x.get(1)) * leverageFct.localVol(t, x.get(0), true));

        final double r = riskFreeRate().currentLink()
                                .forwardRate(t, t, Compounding.Continuous).rate();
        final double q = dividendYield().currentLink()
                                .forwardRate(t, t, Compounding.Continuous).rate();

        final Array tmp = new Array(2);
        tmp.set(0, r - q - 0.5 * vol * vol);
        tmp.set(1, kappa * (theta - x.get(1)));
        return tmp;
    }

    @Override
    public Matrix diffusion(final double t, final Array x) {
        final double vol = Math.max(1e-8,
                Math.sqrt(x.get(1)) * leverageFct.localVol(t, x.get(0), true));
        final double sigma2 = mixedSigma * Math.sqrt(x.get(1));
        final double sqrhov = Math.sqrt(1.0 - rho * rho);

        final Matrix m = new Matrix(2, 2);
        m.set(0, 0, vol);          m.set(0, 1, 0.0);
        m.set(1, 0, rho * sigma2); m.set(1, 1, sqrhov * sigma2);
        return m;
    }

    /**
     * QE+martingale variance scheme — direct port of the C++ evolve()
     * method that drops the Andersen quadratic-exponential (QE) variance
     * scheme together with a leverage-aware Euler step on log-S.
     */
    @Override
    public Array evolve(final double t0, final Array x0,
                        final double dt, final Array dw) {
        final Array retVal = new Array(2);

        final double ex = Math.exp(-kappa * dt);
        final double m  = theta + (x0.get(1) - theta) * ex;
        final double s2 = x0.get(1) * mixedSigma * mixedSigma * ex / kappa * (1 - ex)
                        + theta * mixedSigma * mixedSigma / (2 * kappa) * (1 - ex) * (1 - ex);
        final double psi = s2 / (m * m);

        if (psi < 1.5) {
            final double b2 = 2 / psi - 1 + Math.sqrt(2 / psi * (2 / psi - 1));
            final double b  = Math.sqrt(b2);
            final double a  = m / (1 + b2);
            retVal.set(1, a * (b + dw.get(1)) * (b + dw.get(1)));
        } else {
            final double p = (psi - 1) / (psi + 1);
            final double beta = (1 - p) / m;
            final double u = new CumulativeNormalDistribution().op(dw.get(1));
            retVal.set(1, (u <= p) ? 0.0 : Math.log((1 - p) / (1 - u)) / beta);
        }

        final double mu = riskFreeRate().currentLink()
                                .forwardRate(t0, t0 + dt, Compounding.Continuous).rate()
                        - dividendYield().currentLink()
                                .forwardRate(t0, t0 + dt, Compounding.Continuous).rate();
        final double rho1 = Math.sqrt(1 - rho * rho);

        final double l0  = leverageFct.localVol(t0, x0.get(0), true);
        final double v0_ = 0.5 * (x0.get(1) + retVal.get(1)) * l0 * l0;

        retVal.set(0, x0.get(0) * Math.exp(
                mu * dt - 0.5 * v0_ * dt
                + rho / mixedSigma * l0 * (
                        retVal.get(1) - kappa * theta * dt
                        + 0.5 * (x0.get(1) + retVal.get(1)) * kappa * dt
                        - x0.get(1))
                + rho1 * Math.sqrt(v0_ * dt) * dw.get(0)));

        return retVal;
    }

    public double v0() { return v0; }
    public double rho() { return rho; }
    public double kappa() { return kappa; }
    public double theta() { return theta; }
    public double sigma() { return sigma; }
    public double mixingFactor() { return mixingFactor; }

    public LocalVolTermStructure leverageFct() {
        return leverageFct;
    }

    public Handle<Quote> s0() {
        return hestonProcess.s0();
    }

    public Handle<YieldTermStructure> dividendYield() {
        return hestonProcess.dividendYield();
    }

    public Handle<YieldTermStructure> riskFreeRate() {
        return hestonProcess.riskFreeRate();
    }

    @Override
    public double time(final Date d) {
        return hestonProcess.time(d);
    }

    private void setParameters() {
        v0    = hestonProcess.v0().currentLink().value();
        kappa = hestonProcess.kappa().currentLink().value();
        theta = hestonProcess.theta().currentLink().value();
        sigma = hestonProcess.sigma().currentLink().value();
        rho   = hestonProcess.rho().currentLink().value();
        mixedSigma = mixingFactor * sigma;
    }
}
