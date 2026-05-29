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

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;

/**
 * G2 stochastic process.
 * <p>
 * Two-factor Gaussian short-rate process built from two correlated
 * Ornstein-Uhlenbeck factors {@latex$ x } and {@latex$ y } with mean-reversion
 * speeds {@latex$ a, b }, volatilities {@latex$ \sigma, \eta } and correlation
 * {@latex$ \rho }. Mirrors C++ v1.42.1 {@code ql/processes/g2process.cpp}.
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

    public G2Process(final /* @Real */ double a, final /* @Real */ double sigma,
            final /* @Real */ double b, final /* @Real */ double eta, final /* @Real */ double rho) {
        super();
        this.a_ = a;
        this.sigma_ = sigma;
        this.b_ = b;
        this.eta_ = eta;
        this.rho_ = rho;
        this.xProcess_ = new OrnsteinUhlenbeckProcess(a, sigma, 0.0);
        this.yProcess_ = new OrnsteinUhlenbeckProcess(b, eta, 0.0);
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
        return new Array(new double[] { x0_, y0_ });
    }

    @Override
    public Array drift(final /* @Time */ double t, final Array x) {
        return new Array(new double[] {
                xProcess_.drift(t, x.get(0)),
                yProcess_.drift(t, x.get(1))
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
    public Array expectation(final /* @Time */ double t0, final Array x0, final /* @Time */ double dt) {
        return new Array(new double[] {
                xProcess_.expectation(t0, x0.get(0), dt),
                yProcess_.expectation(t0, x0.get(1), dt)
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
        return x0_;
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

}
