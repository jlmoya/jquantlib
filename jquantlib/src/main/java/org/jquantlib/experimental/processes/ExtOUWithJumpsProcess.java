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

import org.jquantlib.QL;
import org.jquantlib.math.Constants;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.processes.StochasticProcess;

/**
 * Ornstein Uhlenbeck plus exponential jumps process (Kluge model).
 * <p>
 * Java port of v1.42.1
 * {@code ql/experimental/processes/extouwithjumpsprocess.{hpp,cpp}}.
 * <p>
 * The process state is {@code (X, Y)} with {@code S = exp(X + Y)} where
 * {@code X} is the {@link ExtendedOrnsteinUhlenbeckProcess} component and
 * {@code Y} is a mean-reverting (rate {@code beta}) jump component with
 * Poisson arrivals (intensity {@code jumpIntensity}) and exponentially-
 * distributed jump sizes (rate {@code eta}).
 *
 * @author Phase 4n WI port
 */
public class ExtOUWithJumpsProcess extends StochasticProcess {

    private final double Y0_;
    private final double beta_;
    private final double jumpIntensity_;
    private final double eta_;
    private final ExtendedOrnsteinUhlenbeckProcess ouProcess_;
    private final CumulativeNormalDistribution cumNormalDist_;

    public ExtOUWithJumpsProcess(
            final ExtendedOrnsteinUhlenbeckProcess process,
            final double Y0,
            final double beta,
            final double jumpIntensity,
            final double eta) {
        super();
        QL.require(process != null, "null Ornstein/Uhlenbeck process");
        this.Y0_ = Y0;
        this.beta_ = beta;
        this.jumpIntensity_ = jumpIntensity;
        this.eta_ = eta;
        this.ouProcess_ = process;
        this.cumNormalDist_ = new CumulativeNormalDistribution();
    }

    @Override
    public int size() {
        return 2;
    }

    @Override
    public int factors() {
        return 3;
    }

    public ExtendedOrnsteinUhlenbeckProcess getExtendedOrnsteinUhlenbeckProcess() {
        return ouProcess_;
    }

    public double beta() {
        return beta_;
    }

    public double jumpIntensity() {
        return jumpIntensity_;
    }

    public double eta() {
        return eta_;
    }

    @Override
    public Array initialValues() {
        final Array a = new Array(2);
        a.set(0, ouProcess_.x0());
        a.set(1, Y0_);
        return a;
    }

    @Override
    public Array drift(final double t, final Array x) {
        final Array a = new Array(2);
        a.set(0, ouProcess_.drift(t, x.get(0)));
        a.set(1, -beta_ * x.get(1));
        return a;
    }

    @Override
    public Matrix diffusion(final double t, final Array x) {
        final Matrix m = new Matrix(2, 2);
        m.set(0, 0, ouProcess_.diffusion(t, x.get(0)));
        return m;
    }

    @Override
    public Array evolve(final double t0, final Array x0, final double dt, final Array dw) {
        final Array retVal = new Array(2);
        retVal.set(0, ouProcess_.evolve(t0, x0.get(0), dt, dw.get(0)));
        retVal.set(1, x0.get(1) * Math.exp(-beta_ * dt));

        final double u1 = Math.max(Constants.QL_EPSILON,
                Math.min(cumNormalDist_.op(dw.get(1)), 1.0 - Constants.QL_EPSILON));

        final double interarrival = -1.0 / jumpIntensity_ * Math.log(u1);
        if (interarrival < dt) {
            final double u2 = Math.max(Constants.QL_EPSILON,
                    Math.min(cumNormalDist_.op(dw.get(2)), 1.0 - Constants.QL_EPSILON));
            final double jumpSize = -1.0 / eta_ * Math.log(u2);
            retVal.set(1, retVal.get(1) + jumpSize);
        }
        return retVal;
    }
}
