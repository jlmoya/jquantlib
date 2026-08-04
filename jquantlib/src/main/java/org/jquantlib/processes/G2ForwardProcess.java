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
 * Forward G2 stochastic process.
 * <p>
 * The {@link G2Process} expressed under the {@code T_}-forward measure: each
 * factor's drift is shifted by a deterministic forward-measure adjustment, and
 * the expectation is corrected by the {@code Mx_T} / {@code My_T} convexity
 * terms.
 * <p>
 * As in {@link G2Process}, the simulated state is shifted so that
 * {@latex$ state[0] + state[1] = r(t) }, on top of the usual T-forward convexity adjustments to the drift and
 * the conditional expectation. With an empty term-structure handle the process degenerates to the pre-v1.43
 * behaviour exactly.
 * <p>
 * Mirrors C++ v1.43 {@code ql/processes/g2process.{hpp,cpp}}.
 *
 * @author Banca Profilo S.p.A. (C++); JQuantLib migration contributors (Java)
 * @category processes
 */
public class G2ForwardProcess extends ForwardMeasureProcess {

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
    public G2ForwardProcess(final /* @Real */ double a, final /* @Real */ double sigma,
            final /* @Real */ double b, final /* @Real */ double eta, final /* @Real */ double rho) {
        this(a, sigma, b, eta, rho, new Handle< YieldTermStructure >());
    }

    public G2ForwardProcess(final /* @Real */ double a, final /* @Real */ double sigma,
            final /* @Real */ double b, final /* @Real */ double eta, final /* @Real */ double rho,
            final Handle< YieldTermStructure > termStructure) {
        // C++ default-constructs the base; G2ForwardProcess overrides every
        // discretization consumer, so the supplied EulerDiscretization is
        // never exercised (matches C++ including <eulerdiscretization.hpp>).
        super(new EulerDiscretization());
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
        double /* @Real */ shiftDrift = 0.0;
        if ( !termStructure_.empty() ) {
            final /* @Real */ double h = 1.0e-4;
            final /* @Real */ double phi_t = phi(t);
            final /* @Real */ double phi_th = phi(t + h);
            final /* @Real */ double phiPrime = (phi_th - phi_t) / h;
            shiftDrift = a_ * phi_t + phiPrime;
        }
        return new Array(new double[] {
                xProcess_.drift(t, z.get(0)) + xForwardDrift(t, T_) + shiftDrift,
                yProcess_.drift(t, z.get(1)) + yForwardDrift(t, T_)
        });
    }

    @Override
    public Matrix diffusion(final /* @Time */ double t, final Array x) {
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
        double /* @Real */ shiftExp = 0.0;
        if ( !termStructure_.empty() ) {
            shiftExp = phi(t0 + dt) - phi(t0) * Math.exp(-a_ * dt);
        }
        return new Array(new double[] {
                xProcess_.expectation(t0, z0.get(0), dt) - Mx_T(t0, t0 + dt, T_) + shiftExp,
                yProcess_.expectation(t0, z0.get(1), dt) - My_T(t0, t0 + dt, T_)
        });
    }

    @Override
    public Matrix stdDeviation(final /* @Time */ double t0, final Array x0, final /* @Time */ double dt) {
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
    // protected forward-measure adjustment helpers (C++ g2process.cpp:186-222)
    //

    protected /* @Real */ double xForwardDrift(final /* @Time */ double t, final /* @Time */ double T) {
        final double expatT = Math.exp(-a_ * (T - t));
        final double expbtT = Math.exp(-b_ * (T - t));
        return -(sigma_ * sigma_ / a_) * (1 - expatT)
                - (rho_ * sigma_ * eta_ / b_) * (1 - expbtT);
    }

    protected /* @Real */ double yForwardDrift(final /* @Time */ double t, final /* @Time */ double T) {
        final double expatT = Math.exp(-a_ * (T - t));
        final double expbtT = Math.exp(-b_ * (T - t));
        return -(eta_ * eta_ / b_) * (1 - expbtT)
                - (rho_ * sigma_ * eta_ / a_) * (1 - expatT);
    }

    protected /* @Real */ double Mx_T(final /* @Real */ double s, final /* @Real */ double t, final /* @Real */ double T) {
        double M;
        M = ((sigma_ * sigma_) / (a_ * a_) + (rho_ * sigma_ * eta_) / (a_ * b_))
                * (1 - Math.exp(-a_ * (t - s)));
        M += -(sigma_ * sigma_) / (2 * a_ * a_)
                * (Math.exp(-a_ * (T - t)) - Math.exp(-a_ * (T + t - 2 * s)));
        M += -(rho_ * sigma_ * eta_) / (b_ * (a_ + b_))
                * (Math.exp(-b_ * (T - t)) - Math.exp(-b_ * T - a_ * t + (a_ + b_) * s));
        return M;
    }

    protected /* @Real */ double My_T(final /* @Real */ double s, final /* @Real */ double t, final /* @Real */ double T) {
        double M;
        M = ((eta_ * eta_) / (b_ * b_) + (rho_ * sigma_ * eta_) / (a_ * b_))
                * (1 - Math.exp(-b_ * (t - s)));
        M += -(eta_ * eta_) / (2 * b_ * b_)
                * (Math.exp(-b_ * (T - t)) - Math.exp(-b_ * (T + t - 2 * s)));
        M += -(rho_ * sigma_ * eta_) / (a_ * (a_ + b_))
                * (Math.exp(-a_ * (T - t)) - Math.exp(-a_ * T - b_ * t + (a_ + b_) * s));
        return M;
    }

    //
    // inspectors (C++ v1.43 additions)
    //

    public Handle< YieldTermStructure > termStructure() {
        return termStructure_;
    }

    /**
     * Deterministic offset {@latex$ \varphi(t) } that fits the initial term structure.
     * <p>
     * Identical to {@link G2Process#phi(double)}; duplicated here exactly as C++ v1.43 does, because
     * {@code G2ForwardProcess} does not derive from {@code G2Process}.
     *
     * @throws org.jquantlib.lang.exceptions.LibraryException if no term structure was supplied
     */
    public /* @Real */ double phi(final /* @Time */ double t) {
        QL.require(!termStructure_.empty(), "no term structure given to G2ForwardProcess");
        final /* @Rate */ double forward = termStructure_.currentLink()
                .forwardRate(t, t, Compounding.Continuous, Frequency.NoFrequency).rate();
        final /* @Real */ double temp1 = sigma_ * (1.0 - Math.exp(-a_ * t)) / a_;
        final /* @Real */ double temp2 = eta_ * (1.0 - Math.exp(-b_ * t)) / b_;
        return 0.5 * temp1 * temp1 + 0.5 * temp2 * temp2 + rho_ * temp1 * temp2 + forward;
    }

    /**
     * Short rate implied by the simulated state: {@latex$ r = z_1 + z_2 }.
     * <p>
     * The {@code t} argument is unused; it is kept to mirror the C++ signature.
     */
    @SuppressWarnings("unused")
    public /* @Rate */ double shortRate(final /* @Time */ double t,
            final /* @Real */ double z1, final /* @Real */ double z2) {
        return z1 + z2;
    }

}
