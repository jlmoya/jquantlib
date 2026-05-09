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
 Copyright (C) 2011 Klaus Spanderen
 */
package org.jquantlib.experimental.processes;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.randomnumbers.MersenneTwisterUniformRng;
import org.jquantlib.processes.EulerDiscretization;
import org.jquantlib.processes.StochasticProcess1D;

/**
 * Geman-Roncoroni process.
 * <p>
 * Java port of v1.42.1
 * {@code ql/experimental/processes/gemanroncoroniprocess.{hpp,cpp}}.
 * <p>
 * Models a power-spot process with seasonal mean reversion plus signed jumps.
 * The drift incorporates a periodic mean {@code mu(t) = alpha + beta t +
 * gamma cos(eps + 2 pi t) + delta cos(zeta + 4 pi t)} and the jumps are
 * conditional on the relation between the state and the mean (positive jump
 * if {@code x0 <= mu+d}, otherwise negative).
 *
 * @author Phase 4n WI port
 */
public class GemanRoncoroniProcess extends StochasticProcess1D {

    private final double x0_;
    private final double alpha_, beta_, gamma_, delta_;
    private final double eps_, zeta_, d_;
    private final double k_, tau_;
    private final double sig2_, a_, b_;
    private final double theta1_, theta2_, theta3_;
    private final double psi_;
    private MersenneTwisterUniformRng urng_;

    public GemanRoncoroniProcess(
            final double x0,
            final double alpha, final double beta,
            final double gamma, final double delta,
            final double eps, final double zeta, final double d,
            final double k, final double tau,
            final double sig2, final double a, final double b,
            final double theta1, final double theta2, final double theta3,
            final double psi) {
        super(new EulerDiscretization());
        this.x0_ = x0;
        this.alpha_ = alpha;
        this.beta_ = beta;
        this.gamma_ = gamma;
        this.delta_ = delta;
        this.eps_ = eps;
        this.zeta_ = zeta;
        this.d_ = d;
        this.k_ = k;
        this.tau_ = tau;
        this.sig2_ = sig2;
        this.a_ = a;
        this.b_ = b;
        this.theta1_ = theta1;
        this.theta2_ = theta2;
        this.theta3_ = theta3;
        this.psi_ = psi;
    }

    @Override
    public double x0() {
        return x0_;
    }

    @Override
    public double drift(final double t, final double x) {
        final double mu = alpha_ + beta_ * t
                + gamma_ * Math.cos(eps_ + 2 * Math.PI * t)
                + delta_ * Math.cos(zeta_ + 4 * Math.PI * t);
        final double muPrime = beta_
                - gamma_ * 2 * Math.PI * Math.sin(eps_ + 2 * Math.PI * t)
                - delta_ * 4 * Math.PI * Math.sin(zeta_ + 4 * Math.PI * t);
        return muPrime + theta1_ * (mu - x);
    }

    @Override
    public double diffusion(final double t, final double x) {
        final double c = Math.cos(Math.PI * t + b_);
        return Math.sqrt(sig2_ + a_ * c * c);
    }

    @Override
    public double stdDeviation(final double t0, final double x0, final double dt) {
        final double c = Math.cos(Math.PI * t0 + b_);
        final double sig2t = sig2_ + a_ * c * c;
        return Math.sqrt(sig2t / (2 * theta1_) * (1.0 - Math.exp(-2 * theta1_ * dt)));
    }

    @Override
    public double evolve(final double t0, final double x0, final double dt, final double dw) {
        // random number generator for the jump part
        if (urng_ == null) {
            urng_ = new MersenneTwisterUniformRng((long) (1234L * dw + 56789L));
        }
        final Array du = new Array(3);
        du.set(0, urng_.next().value());
        du.set(1, urng_.next().value());
        return evolve(t0, x0, dt, dw, du);
    }

    public double evolve(final double t0, final double x0, final double dt,
                         final double dw, final Array du) {
        double retVal;
        final double t = t0 + 0.5 * dt;
        final double mu = alpha_ + beta_ * t
                + gamma_ * Math.cos(eps_ + 2 * Math.PI * t)
                + delta_ * Math.cos(zeta_ + 4 * Math.PI * t);

        final double j = -1.0 / theta3_
                * Math.log(1.0 + du.get(1) * (Math.exp(-theta3_ * psi_) - 1.0));

        if (x0 <= mu + d_) {
            retVal = super.evolve(t, x0, dt, dw);
            final double jumpIntensity = theta2_
                    * (2.0 / (1 + Math.abs(Math.sin(Math.PI * (t - tau_) / k_))) - 1);
            final double interarrival = -1.0 / jumpIntensity * Math.log(du.get(0));
            if (interarrival < dt) {
                retVal += j;
            }
        } else {
            retVal = x0 - j;
        }

        return retVal;
    }
}
