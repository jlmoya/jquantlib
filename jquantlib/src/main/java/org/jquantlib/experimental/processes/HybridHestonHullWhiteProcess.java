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

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2007, 2008 Klaus Spanderen
*/
package org.jquantlib.experimental.processes;

import org.jquantlib.QL;
import org.jquantlib.math.Constants;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.shortrate.onefactormodels.HullWhite;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.processes.HullWhiteForwardProcess;
import org.jquantlib.processes.StochasticProcess;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;

/**
 * Hybrid Heston Hull-White stochastic process.
 *
 * <p>Phase 5h.5-HHW WI-1 port of {@code QuantLib::HybridHestonHullWhiteProcess}
 * (v1.42.1 ql/processes/hybridhestonhullwhiteprocess.{hpp,cpp}). Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>Three-factor model:
 * <ul>
 *   <li>Equity {@code S} — Heston-driven (lognormal forward).</li>
 *   <li>Variance {@code v} — Heston CIR-style.</li>
 *   <li>Short-rate {@code r} — Hull-White (forward measure for the discrete
 *       evolve step; note that the underlying process must be a
 *       {@link HullWhiteForwardProcess}).</li>
 * </ul>
 *
 * <p>Two discretizations are supported:
 * <ul>
 *   <li>{@code BSMHullWhite} (default) — Andersen-Piterbarg-style scheme that
 *       matches first and second moments of the discrete jump exactly,
 *       reproducing the BSM-HW closed form in the deterministic-vol limit.</li>
 *   <li>{@code Euler} — naive Euler-Maruyama, primarily for benchmarking.</li>
 * </ul>
 *
 * <p>Note that {@link #factors()} returns 3, matching the three independent
 * Brownian drivers; {@link #size()} also returns 3 because the joint state is
 * {@code (S, v, r)}.
 *
 * <p>{@link #numeraire(double, Array)} returns the change-of-numeraire
 * factor relative to the {@code T}-forward measure, used by hybrid path
 * pricers.
 *
 * @category processes
 */
public class HybridHestonHullWhiteProcess extends StochasticProcess {

    private final HestonProcess hestonProcess_;
    private final HullWhiteForwardProcess hullWhiteProcess_;
    /** model is used to calculate P(t,T) in the numeraire. */
    private final HullWhite hullWhiteModel_;
    private final double corrEquityShortRate_;
    private final Discretization discretization_;
    private final double maxRho_;
    private final double T_;
    private double endDiscount_;
    public HybridHestonHullWhiteProcess(final HestonProcess hestonProcess,
            final HullWhiteForwardProcess hullWhiteProcess, final double corrEquityShortRate) {
        this(hestonProcess, hullWhiteProcess, corrEquityShortRate, Discretization.BSMHullWhite);
    }

    public HybridHestonHullWhiteProcess(final HestonProcess hestonProcess,
            final HullWhiteForwardProcess hullWhiteProcess, final double corrEquityShortRate,
            final Discretization discretization) {
        this.hestonProcess_ = hestonProcess;
        this.hullWhiteProcess_ = hullWhiteProcess;
        this.hullWhiteModel_ = new HullWhite(hestonProcess.riskFreeRate(), hullWhiteProcess.a(),
                hullWhiteProcess.sigma());
        this.corrEquityShortRate_ = corrEquityShortRate;
        this.discretization_ = discretization;
        final double rho = hestonProcess.rho().currentLink().value();
        this.maxRho_ = Math.sqrt(1.0 - rho * rho) - Math.sqrt(Constants.QL_EPSILON);
        this.T_ = hullWhiteProcess.getForwardMeasureTime();
        this.endDiscount_ = hestonProcess.riskFreeRate().currentLink().discount(T_);

        QL.require(corrEquityShortRate * corrEquityShortRate + rho * rho <= 1.0,
                "correlation matrix is not positive definite");
        QL.require(hullWhiteProcess.sigma() > 0.0, "positive vol of Hull White process is required");
    }

    @Override
    public int size() {
        return 3;
    }

    @Override
    public int factors() {
        return 3;
    }

    @Override
    public Array initialValues() {
        return new Array(
                new double[] { hestonProcess_.s0().currentLink().value(), hestonProcess_.v0().currentLink().value(),
                        hullWhiteProcess_.x0() });
    }

    @Override
    public Array drift(final double t, final Array x) {
        final Array x0 = new Array(new double[] { x.get(0), x.get(1) });
        final Array y0 = hestonProcess_.drift(t, x0);
        return new Array(new double[] { y0.get(0), y0.get(1), hullWhiteProcess_.drift(t, x.get(2)) });
    }

    @Override
    public Array apply(final Array x0, final Array dx) {
        final Array xt = new Array(new double[] { x0.get(0), x0.get(1) });
        final Array dxt = new Array(new double[] { dx.get(0), dx.get(1) });
        final Array yt = hestonProcess_.apply(xt, dxt);
        return new Array(new double[] { yt.get(0), yt.get(1), hullWhiteProcess_.apply(x0.get(2), dx.get(2)) });
    }

    @Override
    public Matrix diffusion(final double t, final Array x) {
        final Matrix retVal = new Matrix(3, 3);
        final Array xt = new Array(new double[] { x.get(0), x.get(1) });
        final Matrix m = hestonProcess_.diffusion(t, xt);
        retVal.set(0, 0, m.get(0, 0));
        retVal.set(0, 1, 0.0);
        retVal.set(0, 2, 0.0);
        retVal.set(1, 0, m.get(1, 0));
        retVal.set(1, 1, m.get(1, 1));
        retVal.set(1, 2, 0.0);

        final double sigma = hullWhiteProcess_.sigma();
        retVal.set(2, 0, corrEquityShortRate_ * sigma);
        retVal.set(2, 1, -retVal.get(2, 0) * retVal.get(1, 0) / retVal.get(1, 1));
        retVal.set(2, 2,
                Math.sqrt(sigma * sigma - retVal.get(2, 1) * retVal.get(2, 1) - retVal.get(2, 0) * retVal.get(2, 0)));
        return retVal;
    }

    @Override
    public Array evolve(final double t0, final Array x0, final double dt, final Array dw) {
        final double r = x0.get(2);
        final double a = hullWhiteProcess_.a();
        final double sigma = hullWhiteProcess_.sigma();
        final double rho = corrEquityShortRate_;
        final double xi = hestonProcess_.rho().currentLink().value();
        final double eta = (x0.get(1) > 0.0) ? Math.sqrt(x0.get(1)) : 0.0;
        final double s = t0;
        final double t = t0 + dt;
        final double T = T_;
        final double dy = hestonProcess_.dividendYield().currentLink()
                .forwardRate(s, t, Compounding.Continuous, Frequency.NoFrequency).rate();

        final double df = Math.log(
                hestonProcess_.riskFreeRate().currentLink().discount(t) / hestonProcess_.riskFreeRate().currentLink()
                        .discount(s));

        final double eaT = Math.exp(-a * T);
        final double eat = Math.exp(-a * t);
        final double eas = Math.exp(-a * s);
        final double iat = 1.0 / eat;
        final double ias = 1.0 / eas;

        final double m1 = -(dy + 0.5 * eta * eta) * dt - df;
        final double m2 = -rho * sigma * eta / a * (dt - 1.0 / a * eaT * (iat - ias));
        final double m3 = (r - hullWhiteProcess_.alpha(s)) * hullWhiteProcess_.B(s, t);
        final double m4 = sigma * sigma / (2.0 * a * a) * (dt + 2.0 / a * (eat - eas) - 1.0 / (2.0 * a) * (eat * eat
                - eas * eas));
        final double m5 =
                -sigma * sigma / (a * a) * (dt - 1.0 / a * (1.0 - eat * ias) - 1.0 / (2.0 * a) * eaT * (iat - 2.0 * ias
                        + eat * ias * ias));

        final double mu = m1 + m2 + m3 + m4 + m5;

        final Array retVal = new Array(3);
        final double sigmaHeston = hestonProcess_.sigma().currentLink().value();
        final double eta2 = sigmaHeston * eta;
        final double kappa = hestonProcess_.kappa().currentLink().value();
        final double theta = hestonProcess_.theta().currentLink().value();
        final double nu = kappa * (theta - eta * eta);

        retVal.set(1,
                x0.get(1) + nu * dt + eta2 * Math.sqrt(dt) * (xi * dw.get(0) + Math.sqrt(1.0 - xi * xi) * dw.get(1)));

        if ( discretization_ == Discretization.BSMHullWhite ) {
            final double v1 =
                    eta * eta * dt + sigma * sigma / (a * a) * (dt - 2.0 / a * (1.0 - eat * ias) + 1.0 / (2.0 * a) * (
                            1.0 - eat * eat * ias * ias)) + 2.0 * sigma * eta / a * rho * (dt - 1.0 / a * (1.0
                            - eat * ias));
            final double v2 = hullWhiteProcess_.variance(t0, r, dt);
            final double v12 = (1.0 - eat * ias) * (sigma * eta / a * rho + sigma * sigma / (a * a))
                    - sigma * sigma / (2.0 * a * a) * (1.0 - eat * eat * ias * ias);

            QL.require(v1 > 0.0 && v2 > 0.0, "zero or negative variance given");

            // terminal rho must be between -maxRho and +maxRho
            final double rhoT = Math.min(maxRho_, Math.max(-maxRho_, v12 / Math.sqrt(v1 * v2)));
            QL.require(rhoT <= 1.0 && rhoT >= -1.0 && 1.0 - rhoT * rhoT / (1.0 - xi * xi) >= 0.0,
                    "invalid terminal correlation");

            final double dw0 = dw.get(0);
            final double dw2 = rhoT * dw.get(0) - rhoT * xi / Math.sqrt(1.0 - xi * xi) * dw.get(1)
                    + Math.sqrt(1.0 - rhoT * rhoT / (1.0 - xi * xi)) * dw.get(2);

            retVal.set(2, hullWhiteProcess_.evolve(t0, r, dt, dw2));
            final double vol = Math.sqrt(v1) * dw0;
            retVal.set(0, x0.get(0) * Math.exp(mu + vol));
        } else if ( discretization_ == Discretization.Euler ) {
            final double dw2 = rho * dw.get(0) - rho * xi / Math.sqrt(1.0 - xi * xi) * dw.get(1)
                    + Math.sqrt(1.0 - rho * rho / (1.0 - xi * xi)) * dw.get(2);

            retVal.set(2, hullWhiteProcess_.evolve(t0, r, dt, dw2));
            final double vol = eta * Math.sqrt(dt) * dw.get(0);
            retVal.set(0, x0.get(0) * Math.exp(mu + vol));
        } else {
            QL.error("unknown discretization scheme");
        }

        return retVal;
    }

    /**
     * Numeraire ratio P(t,T,r)/P(0,T): hybrid-process pricers divide payoff by this factor to discount under the
     * {@code T}-forward measure.
     */
    public double numeraire(final double t, final Array x) {
        return hullWhiteModel_.discountBond(t, T_, x.get(2)) / endDiscount_;
    }

    public HestonProcess hestonProcess() {
        return hestonProcess_;
    }

    public HullWhiteForwardProcess hullWhiteProcess() {
        return hullWhiteProcess_;
    }

    public double eta() {
        return corrEquityShortRate_;
    }

    public Discretization discretization() {
        return discretization_;
    }

    @Override
    public double time(final Date date) {
        return hestonProcess_.time(date);
    }

    @Override
    public void update() {
        endDiscount_ = hestonProcess_.riskFreeRate().currentLink().discount(T_);
        super.update();
    }

    public enum Discretization {
        Euler, BSMHullWhite
    }
}
