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
*/

/*
 Copyright (C) 2014 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.experimental.volatility;

import org.jquantlib.QL;
import org.jquantlib.math.Ops;
import org.jquantlib.math.distributions.IncompleteGamma;
import org.jquantlib.math.solvers1D.Brent;

/**
 * Five-linear interpolator for the no-arbitrage SABR absorption
 * probability {@code d0(beta, nu, rho, sigmaI, tau)}.
 *
 * <p>Line-by-line port of QuantLib v1.42.1
 * {@code ql/experimental/volatility/noarbsabr.cpp} lines 184-329
 * (the {@code detail::D0Interpolator} class). Reads the absorption-count
 * table from {@link NoArbSabrAbsorptions} and applies the multilinear
 * weights described in Doust 2012.
 *
 * <p>The C++ implementation uses Boost's {@code boost::math::gamma_q}
 * (regularized upper incomplete gamma {@code Q(a,x) = 1 - P(a,x)}) and
 * its inverse {@code boost::math::gamma_q_inv(a, q)}. The Java port
 * computes {@code Q} via {@link IncompleteGamma#incompleteGammaFunction}
 * (which returns {@code P}) and inverts numerically with a {@link Brent}
 * solver — {@code gamma_q_inv} is otherwise unavailable in the JQuantLib
 * math layer.
 *
 * <p>The class is package-visible so it can also be unit-tested in
 * isolation against the C++ reference values.
 */
final class D0Interpolator {

    // grid axes (must match C++ verbatim, noarbsabr.cpp 194-217)
    private static final double[] TAU_G = {
            0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0, 2.25, 2.5, 2.75, 3.0,
            3.25, 3.5, 3.75, 4.0, 4.25, 4.5, 4.75, 5.0, 5.25, 5.5, 5.75, 6.0, 6.25,
            6.5, 6.75, 7.0, 7.25, 7.5, 7.75, 8.0, 8.25, 8.5, 8.75, 9.0, 9.25, 9.5,
            9.75, 10.0, 10.25, 10.5, 10.75, 11.0, 11.25, 11.5, 11.75, 12.0, 12.25,
            12.5, 12.75, 13.0, 13.25, 13.5, 13.75, 14.0, 14.25, 14.5, 14.75, 15.0,
            15.25, 15.5, 15.75, 16.0, 16.25, 16.5, 16.75, 17.0, 17.25, 17.5, 17.75,
            18.0, 18.25, 18.5, 18.75, 19.0, 19.25, 19.5, 19.75, 20.0, 20.25, 20.5,
            20.75, 21.0, 21.25, 21.5, 21.75, 22.0, 22.25, 22.5, 22.75, 23.0, 23.25,
            23.5, 23.75, 24.0, 24.25, 24.5, 24.75, 25.0, 25.25, 25.5, 25.75, 26.0,
            26.25, 26.5, 26.75, 27.0, 27.25, 27.5, 27.75, 28.0, 28.25, 28.5, 28.75,
            29.0, 29.25, 29.5, 29.75, 30.0
    };

    /** sigmaI grid — NOTE descending (matches C++; index lookup uses reverse iterators). */
    private static final double[] SIGMA_I_G = {
            1.0, 0.8, 0.7, 0.6, 0.5, 0.45, 0.4, 0.35, 0.3, 0.27, 0.24, 0.21,
            0.18, 0.15, 0.125, 0.1, 0.075, 0.05
    };

    /** rho grid — NOTE descending (matches C++; index lookup uses reverse iterators). */
    private static final double[] RHO_G = { 0.75, 0.50, 0.25, 0.00, -0.25, -0.50, -0.75 };

    private static final double[] NU_G = { 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8 };

    private static final double[] BETA_G = { 0.01, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9 };

    // accuracy / max iterations for the gamma_q_inv Brent inversion.
    // Boost's tolerance is ~1e-12; we match.
    private static final double GAMMA_Q_INV_ACCURACY = 1.0e-12;
    private static final int    GAMMA_Q_INV_MAX_ITER = 200;
    // IncompleteGamma series/cf tolerances
    private static final double GAMMA_ACCURACY = 1.0e-15;
    private static final int    GAMMA_MAX_ITER = 1000;

    private final double forward_;
    private final double expiryTime_;
    private final double alpha_;
    private final double beta_;
    private final double nu_;
    private final double rho_;
    private final double gamma_;
    private final double sigmaI_;

    /**
     * Constructor (noarbsabr.cpp 186-218).
     */
    D0Interpolator(final double forward, final double expiryTime, final double alpha,
            final double beta, final double nu, final double rho) {
        this.forward_ = forward;
        this.expiryTime_ = expiryTime;
        this.alpha_ = alpha;
        this.beta_ = beta;
        this.nu_ = nu;
        this.rho_ = rho;
        this.gamma_ = 1.0 / (2.0 * (1.0 - beta));
        this.sigmaI_ = alpha * Math.pow(forward, beta - 1.0);
    }

    /**
     * Returns the interpolated absorption probability {@code d0} for the
     * configured parameters. Mirrors C++ {@code D0Interpolator::operator()}.
     */
    double value() {
        // tau index ------------------------------------------------------
        int tauInd = upperBound(TAU_G, expiryTime_);
        if (tauInd == TAU_G.length) {
            tauInd--; // tau at upper bound
        }
        double expiryTimeTmp = expiryTime_;
        if (tauInd == 0) {
            tauInd++;
            expiryTimeTmp = TAU_G[0];
        }
        final double tauL = (expiryTimeTmp - TAU_G[tauInd - 1])
                / (TAU_G[tauInd] - TAU_G[tauInd - 1]);

        // sigmaI index (C++ uses reverse-iterator upper_bound because
        // SIGMA_I_G is descending) --------------------------------------
        int sigmaIInd = SIGMA_I_G.length - upperBoundReverse(SIGMA_I_G, sigmaI_);
        if (sigmaIInd == 0) {
            sigmaIInd++; // sigmaI at upper bound
        }
        final double sigmaIL = (sigmaI_ - SIGMA_I_G[sigmaIInd - 1])
                / (SIGMA_I_G[sigmaIInd] - SIGMA_I_G[sigmaIInd - 1]);

        // rho index (C++ uses reverse-iterator upper_bound because
        // RHO_G is descending) ------------------------------------------
        int rhoInd = RHO_G.length - upperBoundReverse(RHO_G, rho_);
        if (rhoInd == 0) {
            rhoInd++;
        }
        if (rhoInd == RHO_G.length) {
            rhoInd--;
        }
        final double rhoL = (rho_ - RHO_G[rhoInd - 1])
                / (RHO_G[rhoInd] - RHO_G[rhoInd - 1]);

        // nu index -------------------------------------------------------
        // for nu = 0 we know phi = 0.5 * z_F^2
        int nuInd = upperBound(NU_G, nu_);
        if (nuInd == NU_G.length) {
            nuInd--; // nu at upper bound
        }
        final double tmpNuG = nuInd > 0 ? NU_G[nuInd - 1] : 0.0;
        final double nuL = (nu_ - tmpNuG) / (NU_G[nuInd] - tmpNuG);

        // beta index -----------------------------------------------------
        // for beta = 1 we know phi = 0.0
        int betaInd = upperBound(BETA_G, beta_);
        final double tmpBetaG;
        if (betaInd == BETA_G.length) {
            tmpBetaG = 1.0;
        } else {
            tmpBetaG = BETA_G[betaInd];
        }
        final double betaL = (beta_ - BETA_G[betaInd - 1])
                / (tmpBetaG - BETA_G[betaInd - 1]);

        // 5-linear interpolation in phi space ----------------------------
        double phiRes = 0.0;
        for (int iTau = -1; iTau <= 0; ++iTau) {
            for (int iSigma = -1; iSigma <= 0; ++iSigma) {
                for (int iRho = -1; iRho <= 0; ++iRho) {
                    for (int iNu = -1; iNu <= 0; ++iNu) {
                        for (int iBeta = -1; iBeta <= 0; ++iBeta) {
                            final double phiTmp;
                            if (iNu == -1 && nuInd == 0) {
                                phiTmp = 0.5 / (sigmaI_ * sigmaI_
                                        * (1.0 - beta_) * (1.0 - beta_));
                            } else {
                                if (iBeta == 0 && betaInd == BETA_G.length) {
                                    phiTmp = phi(NoArbSabrModel.Constants.TINY_PROB);
                                } else {
                                    final int ind = tauInd + iTau
                                            + (sigmaIInd + iSigma
                                                    + (rhoInd + iRho
                                                            + (nuInd + iNu
                                                                    + ((betaInd + iBeta)
                                                                            * NU_G.length))
                                                                    * RHO_G.length)
                                                            * SIGMA_I_G.length)
                                                    * TAU_G.length;
                                    QL.require(ind >= 0 && ind < NoArbSabrAbsorptions.SIZE,
                                            "absorption matrix index (" + ind + ") invalid");
                                    phiTmp = phi(
                                            NoArbSabrAbsorptions.get(ind)
                                                    / NoArbSabrModel.Constants.NSIM);
                                }
                            }
                            phiRes += phiTmp
                                    * (iTau == -1 ? (1.0 - tauL) : tauL)
                                    * (iSigma == -1 ? (1.0 - sigmaIL) : sigmaIL)
                                    * (iRho == -1 ? (1.0 - rhoL) : rhoL)
                                    * (iNu == -1 ? (1.0 - nuL) : nuL)
                                    * (iBeta == -1 ? (1.0 - betaL) : betaL);
                        }
                    }
                }
            }
        }
        return d0(phiRes);
    }

    /**
     * {@code phi(d0) = gamma_q_inv(gamma_, d0) * expiryTime_}.
     * For tiny {@code d0} the C++ short-circuits to the
     * {@code phiByTau_cutoff} flat extrapolation.
     */
    private double phi(final double d0In) {
        if (d0In < 1.0e-14) {
            return NoArbSabrModel.Constants.PHI_BY_TAU_CUTOFF * expiryTime_;
        }
        return gammaQInv(gamma_, d0In) * expiryTime_;
    }

    /**
     * {@code d0(phi) = gamma_q(gamma_, max(0, phi/expiryTime_))}.
     */
    private double d0(final double phiIn) {
        return gammaQ(gamma_, Math.max(0.0, phiIn / expiryTime_));
    }

    // --- gamma_q + gamma_q_inv helpers -----------------------------------

    /** Regularized upper incomplete gamma: {@code Q(a,x) = 1 - P(a,x)}. */
    private static double gammaQ(final double a, final double x) {
        if (x <= 0.0) {
            return 1.0;
        }
        return 1.0 - new IncompleteGamma()
                .incompleteGammaFunction(a, x, GAMMA_ACCURACY, GAMMA_MAX_ITER);
    }

    /**
     * Numerical inverse of {@link #gammaQ}: find {@code x} such that
     * {@code Q(a, x) = q}. Brent root-find on {@code Q(a,x) - q}.
     *
     * <p>This mirrors the role of Boost's {@code gamma_q_inv}. Boost uses
     * an asymptotic seed + Halley refinement; we use Brent on an interval
     * that brackets the root, derived by doubling the upper bound until
     * {@code Q(a,upper) < q}.
     */
    private static double gammaQInv(final double a, final double q) {
        // q in (0, 1). Q is monotonically decreasing in x; Q(a,0)=1, Q(a,inf)=0.
        QL.require(q > 0.0 && q < 1.0,
                "gamma_q_inv argument q (" + q + ") must lie in (0,1)");
        // Bracket: start at x = max(1, a) and bisect outwards.
        double lo = 0.0;
        double hi = Math.max(1.0, a);
        double qHi = gammaQ(a, hi);
        // expand upward until Q(a, hi) < q
        int expandIters = 0;
        while (qHi >= q) {
            lo = hi;
            hi *= 2.0;
            qHi = gammaQ(a, hi);
            if (++expandIters > 200) {
                throw new ArithmeticException("gamma_q_inv: failed to bracket");
            }
        }

        // Brent root-find
        final Ops.DoubleOp f = new Ops.DoubleOp() {
            @Override public double op(final double x) {
                return gammaQ(a, x) - q;
            }
        };
        final Brent brent = new Brent();
        brent.setMaxEvaluations(GAMMA_Q_INV_MAX_ITER);
        return brent.solve(f, GAMMA_Q_INV_ACCURACY, 0.5 * (lo + hi), lo, hi);
    }

    // --- std::upper_bound port ------------------------------------------

    /**
     * Mirrors C++ {@code std::upper_bound(begin, end, val) - begin} on an
     * ascending sorted array: returns the index of the first element strictly
     * greater than {@code val}, or {@code arr.length} if none.
     */
    private static int upperBound(final double[] arr, final double val) {
        int lo = 0, hi = arr.length;
        while (lo < hi) {
            final int mid = (lo + hi) >>> 1;
            if (val < arr[mid]) {
                hi = mid;
            } else {
                lo = mid + 1;
            }
        }
        return lo;
    }

    /**
     * Mirrors C++ {@code std::upper_bound(rbegin, rend, val) - rbegin} on an
     * ascending REVERSE iteration of {@code arr} (i.e. the array is treated as
     * descending). Returns the count of leading descending-order entries that
     * are {@code <= val}. The expression we replicate is
     * {@code arr.size() - (std::upper_bound(rbegin, rend, val) - rbegin)},
     * which the caller subtracts from {@code arr.length} to obtain the
     * forward index — see {@link #value()}.
     */
    private static int upperBoundReverse(final double[] arr, final double val) {
        // Treat reversed view: arr[size-1-i] for i = 0..size-1.
        // upper_bound on reverse view returns index of first reversed element
        // strictly greater than val.
        int lo = 0, hi = arr.length;
        while (lo < hi) {
            final int mid = (lo + hi) >>> 1;
            // reversed view element at position mid is arr[arr.length - 1 - mid]
            if (val < arr[arr.length - 1 - mid]) {
                hi = mid;
            } else {
                lo = mid + 1;
            }
        }
        return lo;
    }
}
