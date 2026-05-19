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
package org.jquantlib.methods.finitedifferences.meshers;

import org.jquantlib.QL;
import org.jquantlib.math.Ops;
import org.jquantlib.math.distributions.GammaFunction;
import org.jquantlib.math.distributions.IncompleteGamma;
import org.jquantlib.math.integrals.GaussLobattoIntegral;

/**
 * Mesher for an exponential-jump process with high mean reversion rate and low jump intensity.
 * <p>
 * Java port of v1.42.1 {@code ql/methods/finitedifferences/meshers/exponentialjump1dmesher.{hpp,cpp}}.
 * <p>
 * Mesh for
 * <pre>
 *   dY_t = -beta Y_{t-} dt + J_t dN_t
 *   omega(J) = (1/eta_u) exp(-(1/eta_u) J)
 * </pre>
 * Locations are inverse-CDF samples of the exponential jump-size density, with an asymptotic-stationary
 * {@code scale = 1 / (1 - exp(-beta/jumpIntensity))}.
 * <p>
 * Reference: B. Hambly, S. Howison, T. Kluge, "Modelling spikes and pricing swing options in electricity markets".
 *
 * @author Phase 5e.5b-CFC-d-161 port
 */
public class ExponentialJump1dMesher extends Fdm1dMesher {

    private final double beta_;
    private final double jumpIntensity_;
    private final double eta_;

    public ExponentialJump1dMesher(final int steps, final double beta, final double jumpIntensity, final double eta) {
        this(steps, beta, jumpIntensity, eta, 1e-3);
    }

    public ExponentialJump1dMesher(final int steps, final double beta, final double jumpIntensity, final double eta,
            final double eps) {
        super(steps);
        QL.require(eps > 0.0 && eps < 1.0, "eps > 0.0 and eps < 1.0");
        QL.require(steps > 1, "minimum number of steps is two");

        this.beta_ = beta;
        this.jumpIntensity_ = jumpIntensity;
        this.eta_ = eta;

        final double start = 0.0;
        final double end = 1.0 - eps;
        final double dx = (end - start) / (steps - 1);
        final double scale = 1.0 / (1.0 - Math.exp(-beta / jumpIntensity));

        for ( int i = 0; i < steps; ++i ) {
            final double p = start + i * dx;
            locations[i] = scale * (-1.0 / eta * Math.log(1.0 - p));
        }

        for ( int i = 0; i < steps - 1; ++i ) {
            dminus[i + 1] = dplus[i] = locations[i + 1] - locations[i];
        }
        dplus[steps - 1] = Double.NaN; // Null<Real>() — no forward step at last node
        dminus[0] = Double.NaN; // Null<Real>() — no backward step at first node
    }

    /**
     * Time-dependent jump-size density (Hambly et al. approximation).
     */
    public double jumpSizeDensity(final double x, final double t) {
        final double a = 1.0 - jumpIntensity_ / beta_;
        final double norm = 1.0 - Math.exp(-jumpIntensity_ * t);
        final double gammaValue = Math.exp(new GammaFunction().logValue(1.0 - jumpIntensity_ / beta_));
        final IncompleteGamma ig = new IncompleteGamma();
        final double accuracy = 1.0e-10;
        final int maxIteration = 100;
        return jumpIntensity_ * gammaValue / norm * (
                ig.incompleteGammaFunction(a, x * Math.exp(beta_ * t) * eta_, accuracy, maxIteration)
                        - ig.incompleteGammaFunction(a, x * eta_, accuracy, maxIteration)) * Math.pow(eta_,
                jumpIntensity_ / beta_) / (beta_ * Math.pow(x, a));
    }

    /**
     * Stationary jump-size density ({@code t -> infinity}).
     */
    public double jumpSizeDensity(final double x) {
        final double a = 1.0 - jumpIntensity_ / beta_;
        final double gammaValue = Math.exp(new GammaFunction().logValue(jumpIntensity_ / beta_));
        return Math.exp(-x * eta_) * Math.pow(x, -a) * Math.pow(eta_, 1.0 - a) / gammaValue;
    }

    /**
     * Time-dependent jump-size distribution function.
     */
    public double jumpSizeDistribution(final double x, final double t) {
        final double xmin = Math.min(x, 1.0e-100);
        final GaussLobattoIntegral gli = new GaussLobattoIntegral(1000000, 1.0e-12);
        return gli.op(new Ops.DoubleOp() {
            @Override
            public double op(final double xx) {
                return jumpSizeDensity(xx, t);
            }
        }, xmin, Math.max(x, xmin));
    }

    /**
     * Stationary jump-size distribution function ({@code t -> infinity}).
     */
    public double jumpSizeDistribution(final double x) {
        final double a = jumpIntensity_ / beta_;
        final double xmin = Math.min(x, org.jquantlib.math.Constants.QL_EPSILON);
        final double gammaValue = Math.exp(new GammaFunction().logValue(jumpIntensity_ / beta_));

        final double lowerEps = (Math.pow(xmin, a) / a - Math.pow(xmin, a + 1) / (a + 1)) / gammaValue;

        final GaussLobattoIntegral gli = new GaussLobattoIntegral(10000, 1.0e-12);
        return lowerEps + gli.op(new Ops.DoubleOp() {
            @Override
            public double op(final double xx) {
                return jumpSizeDensity(xx);
            }
        }, xmin / eta_, Math.max(x, xmin / eta_));
    }
}
