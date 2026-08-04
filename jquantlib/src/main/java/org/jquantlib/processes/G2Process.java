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
 Copyright (C) 2006 Banca Profilo S.p.A.

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <http://quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
*/

package org.jquantlib.processes;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Frequency;

/**
 * G2 stochastic process.
 * <p>
 * Two-factor Gaussian short-rate process built from two correlated
 * Ornstein-Uhlenbeck factors {@latex$ x } and {@latex$ y } with mean-reversion
 * speeds {@latex$ a, b }, volatilities {@latex$ \sigma, \eta } and correlation
 * {@latex$ \rho }.
 * <p>
 * Simulates the two-factor G2++ process with state shifted so that the two simulated components sum to the
 * short rate, i.e. the state is {@latex$ (z_1, z_2) = (x + \varphi(t),\, y) }, where {@latex$ x } and
 * {@latex$ y } are the underlying zero-mean OU factors and {@latex$ \varphi(t) } is the deterministic offset
 * that fits the initial term structure. As a consequence, sample paths produced by a path generator built on
 * this process satisfy {@latex$ r(t_i) = state[0]_i + state[1]_i } and have curve-consistent expectation
 * {@latex$ \varphi(t_i) }.
 * <p>
 * If an empty term-structure handle is passed, the process degenerates to a pair of zero-mean OU processes
 * ({@latex$ \varphi \equiv 0 }), reproducing the pre-v1.43 behaviour exactly.
 * <p>
 * Mirrors C++ v1.43 {@code ql/processes/g2process.{hpp,cpp}}.
 *
 * @author Banca Profilo S.p.A. (C++); JQuantLib migration contributors (Java)
 * @category processes
 */
public class G2Process extends StochasticProcess {

    protected /* @Real */ double x0_ = 0.0;
    protected /* @Real */ double y0_ = 0.0;
    protected final /* @Real */ double a_;
    protected final /* @Real */ double sigma_;
    protected final /* @Real */ double b_;
    protected final /* @Real */ double eta_;
    protected final /* @Real */ double rho_;
    protected final OrnsteinUhlenbeckProcess xProcess_;
    protected final OrnsteinUhlenbeckProcess yProcess_;
    protected final Handle< YieldTermStructure > termStructure_;

    /**
     * Parameter-only constructor: no term structure, {@latex$ \varphi \equiv 0 }.
     * <p>
     * Mirrors the C++ v1.43 default argument {@code const Handle<YieldTermStructure>& termStructure = {}}.
     */
    public G2Process(final /* @Real */ double a, final /* @Real */ double sigma,
            final /* @Real */ double b, final /* @Real */ double eta, final /* @Real */ double rho) {
        this(a, sigma, b, eta, rho, new Handle< YieldTermStructure >());
    }

    public G2Process(final /* @Real */ double a, final /* @Real */ double sigma,
            final /* @Real */ double b, final /* @Real */ double eta, final /* @Real */ double rho,
            final Handle< YieldTermStructure > termStructure) {
        super();
        this.a_ = a;
        this.sigma_ = sigma;
        this.b_ = b;
        this.eta_ = eta;
        this.rho_ = rho;
        this.xProcess_ = new OrnsteinUhlenbeckProcess(a, sigma, 0.0);
        this.yProcess_ = new OrnsteinUhlenbeckProcess(b, eta, 0.0);
        this.termStructure_ = termStructure;
        // C++ registerWith(termStructure_): the handle is observed even when empty, so a later linkTo()
        // still propagates.
        this.termStructure_.addObserver(this);
    }

    //
    // implements StochasticProcess
    //

    @Override
    public int size() {
        return 2;
    }

    @Override
    public Array initialValues() {
        final /* @Real */ double z1_0 = termStructure_.empty() ? x0_ : phi(0.0);
        return new Array(new double[] { z1_0, y0_ });
    }

    @Override
    public Array drift(final /* @Time */ double t, final Array z) {
        // Drift in shifted coordinates z1 = x + phi(t), z2 = y:
        //   dz1 = (-a*z1 + a*phi(t) + phi'(t)) dt + sigma dW1
        //   dz2 = -b*z2 dt + eta dW2
        double /* @Real */ shiftDrift = 0.0;
        if ( !termStructure_.empty() ) {
            final /* @Real */ double h = 1.0e-4;
            final /* @Real */ double phi_t = phi(t);
            final /* @Real */ double phi_th = phi(t + h);
            final /* @Real */ double phiPrime = (phi_th - phi_t) / h;
            shiftDrift = a_ * phi_t + phiPrime;
        }
        return new Array(new double[] {
                xProcess_.drift(t, z.get(0)) + shiftDrift,
                yProcess_.drift(t, z.get(1))
        });
    }

    @Override
    public Matrix diffusion(final /* @Time */ double t, final Array x) {
        /* the correlation matrix is
           |  1   rho |
           | rho   1  |
           whose square root (which is used here) is
           |  1          0       |
           | rho   sqrt(1-rho^2) |
        */
        final Matrix tmp = new Matrix(2, 2);
        final double sigma1 = sigma_;
        final double sigma2 = eta_;
        tmp.set(0, 0, sigma1);
        tmp.set(0, 1, 0.0);
        tmp.set(1, 0, rho_ * sigma1);
        tmp.set(1, 1, Math.sqrt(1.0 - rho_ * rho_) * sigma2);
        return tmp;
    }

    @Override
    public Array expectation(final /* @Time */ double t0, final Array z0, final /* @Time */ double dt) {
        // E[z1(t0+dt) | z1(t0)] = z1(t0)*exp(-a*dt) + phi(t0+dt) - phi(t0)*exp(-a*dt)
        // E[z2(t0+dt) | z2(t0)] = z2(t0)*exp(-b*dt)
        double /* @Real */ shiftExp = 0.0;
        if ( !termStructure_.empty() ) {
            shiftExp = phi(t0 + dt) - phi(t0) * Math.exp(-a_ * dt);
        }
        return new Array(new double[] {
                xProcess_.expectation(t0, z0.get(0), dt) + shiftExp,
                yProcess_.expectation(t0, z0.get(1), dt)
        });
    }

    @Override
    public Matrix stdDeviation(final /* @Time */ double t0, final Array x0, final /* @Time */ double dt) {
        /* the correlation matrix is
           |  1   rho |
           | rho   1  |
           whose square root (which is used here) is
           |  1          0       |
           | rho   sqrt(1-rho^2) |
        */
        final Matrix tmp = new Matrix(2, 2);
        final double sigma1 = xProcess_.stdDeviation(t0, x0.get(0), dt);
        final double sigma2 = yProcess_.stdDeviation(t0, x0.get(1), dt);
        final double expa = Math.exp(-a_ * dt);
        final double expb = Math.exp(-b_ * dt);
        final double H = (rho_ * sigma_ * eta_) / (a_ + b_) * (1 - expa * expb);
        final double den = (0.5 * sigma_ * eta_) * Math.sqrt((1 - expa * expa) * (1 - expb * expb) / (a_ * b_));
        final double newRho = H / den;
        tmp.set(0, 0, sigma1);
        tmp.set(0, 1, 0.0);
        tmp.set(1, 0, newRho * sigma2);
        tmp.set(1, 1, Math.sqrt(1.0 - newRho * newRho) * sigma2);
        return tmp;
    }

    @Override
    public Matrix covariance(final /* @Time */ double t0, final Array x0, final /* @Time */ double dt) {
        final Matrix sigma = stdDeviation(t0, x0, dt);
        return sigma.mul(sigma.transpose());
    }

    //
    // inspectors
    //

    public /* @Real */ double x0() {
        return termStructure_.empty() ? x0_ : phi(0.0);
    }

    public /* @Real */ double y0() {
        return y0_;
    }

    public /* @Real */ double a() {
        return a_;
    }

    public /* @Real */ double sigma() {
        return sigma_;
    }

    public /* @Real */ double b() {
        return b_;
    }

    public /* @Real */ double eta() {
        return eta_;
    }

    public /* @Real */ double rho() {
        return rho_;
    }

    public Handle< YieldTermStructure > termStructure() {
        return termStructure_;
    }

    /**
     * Deterministic offset {@latex$ \varphi(t) } that fits the initial term structure.
     * <p>
     * {@latex[ \varphi(t) = f(0,t) + \frac{1}{2}\left(\frac{\sigma(1-e^{-at})}{a}\right)^2
     * + \frac{1}{2}\left(\frac{\eta(1-e^{-bt})}{b}\right)^2
     * + \rho\frac{\sigma(1-e^{-at})}{a}\frac{\eta(1-e^{-bt})}{b} }
     * <p>
     * Identical to {@code G2.FittingParameter} so that process and model agree.
     *
     * @throws org.jquantlib.lang.exceptions.LibraryException if no term structure was supplied
     */
    public /* @Real */ double phi(final /* @Time */ double t) {
        QL.require(!termStructure_.empty(), "no term structure given to G2Process");
        final /* @Rate */ double forward = termStructure_.currentLink()
                .forwardRate(t, t, Compounding.Continuous, Frequency.NoFrequency).rate();
        final /* @Real */ double temp1 = sigma_ * (1.0 - Math.exp(-a_ * t)) / a_;
        final /* @Real */ double temp2 = eta_ * (1.0 - Math.exp(-b_ * t)) / b_;
        return 0.5 * temp1 * temp1 + 0.5 * temp2 * temp2 + rho_ * temp1 * temp2 + forward;
    }

    /**
     * Short rate implied by the simulated state.
     * <p>
     * The simulated state already includes {@latex$ \varphi(t) } in {@code z1}, so {@latex$ r = z_1 + z_2 }.
     * The {@code t} argument is unused; it is kept to mirror the C++ signature.
     */
    @SuppressWarnings("unused")
    public /* @Rate */ double shortRate(final /* @Time */ double t,
            final /* @Real */ double z1, final /* @Real */ double z2) {
        return z1 + z2;
    }

}
