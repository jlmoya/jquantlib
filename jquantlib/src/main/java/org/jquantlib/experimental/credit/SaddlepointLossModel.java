/*
 Copyright (C) 2014 Jose Aparicio
 Copyright (C) 2026 JQuantLib migration contributors.

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
package org.jquantlib.experimental.credit;

import org.jquantlib.QL;
import org.jquantlib.experimental.math.CopulaPolicy;

/**
 * Saddle-point portfolio credit-default loss model — analytic kernels and
 * basic Newton-Raphson saddle-point solver.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code template <class CP> class SaddlePointLossModel}
 * (declared in {@code ql/experimental/credit/saddlepointlossmodel.hpp}).
 *
 * <p>Implements the saddle-point expansion approach to portfolio credit
 * loss. Codependence is dealt with through a latent model. The model
 * conditions on the systemic factor and integrates indirectly. References:
 * Martin/Thompson/Browne "Taking to the Saddle" (Risk 2001), Antonov et al
 * (NumeriX 2005), Annaert et al (Gent 2006), Huang/Oosterlee/Mesters (JCR
 * 2007/JCF 2007).
 *
 * <p>This Phase 4m.7 port covers:
 * <ul>
 *   <li>Static analytic kernels: cumulant generating function K(s) and its
 *       1st-4th derivatives (the heart of every saddle-point method).</li>
 *   <li>Newton-Raphson saddle-point solver
 *       {@link #findSaddleNewton(double[], double[], double, double, int)}.</li>
 *   <li>Combined evaluator {@link #cgf0234DerivCond(double[], double[], double)}
 *       returning K, K2, K3, K4 in a single sweep (matches C++
 *       {@code CumGen0234DerivCond}).</li>
 * </ul>
 *
 * <p>Deferred to Phase 4m.7b: probOverLossPortfCond1stOrder, probDensityCond,
 * splitLossCond, expectedShortfall*, splitVaRLevel, expectedTrancheLoss
 * Basket-driven overrides, and base-correlation calibration helpers. Total
 * remaining surface ≈ 700 LOC of C++; out of the WI-5 time slot.
 *
 * @param <P> the {@link CopulaPolicy} bound through the underlying
 *            {@link ConstantLossLatentModel}
 */
public class SaddlepointLossModel<P extends CopulaPolicy> extends DefaultLossModel {

    private final ConstantLossLatentModel<P> copula_;

    public SaddlepointLossModel(final ConstantLossLatentModel<P> copula) {
        this.copula_ = copula;
    }

    @Override
    protected void resetModel() {
        if (basket != null) {
            copula_.resetBasket(basket);
        }
    }

    /** Read-only access to the underlying constant-loss latent model. */
    public ConstantLossLatentModel<P> copula() {
        return copula_;
    }

    // ------------------------------------------------------------------------
    // Static analytic kernels (testable without a Basket)
    // ------------------------------------------------------------------------

    /**
     * Cumulant Generating Function (zero-th order):
     * {@code K(s) = Σ log(1 - p_j + p_j exp(N_j (1-rr_j) s / N_total))}.
     *
     * <p>The {@code lossInDef[j]} input is the per-name fractional LGD,
     * i.e. {@code N_j * (1 - rr_j) / N_total}. The cond-prob inputs are
     * already conditional on the market factor.
     *
     * <p>Direct port of C++ {@code SaddlePointLossModel::CumulantGeneratingCond}.
     */
    public static double cumulantGeneratingCond(final double[] condProbs,
                                                final double[] lossInDef,
                                                final double saddle) {
        QL.require(condProbs.length == lossInDef.length, "size mismatch");
        double sum = 0.0;
        for (int i = 0; i < condProbs.length; ++i) {
            final double p = condProbs[i];
            sum += Math.log(1.0 - p + p * Math.exp(lossInDef[i] * saddle));
        }
        return sum;
    }

    /**
     * 1st derivative of CGF: {@code K'(s) = Σ lossInDef[i] m / (1-p+m)}
     * where {@code m = p exp(lossInDef[i] s)}. At {@code s=0} this is the
     * portfolio expected loss in fractional units.
     *
     * <p>Direct port of C++ {@code CumGen1stDerivativeCond}.
     */
    public static double cumGen1stDerivativeCond(final double[] condProbs,
                                                 final double[] lossInDef,
                                                 final double saddle) {
        QL.require(condProbs.length == lossInDef.length, "size mismatch");
        double sum = 0.0;
        for (int i = 0; i < condProbs.length; ++i) {
            final double p = condProbs[i];
            final double mid = p * Math.exp(lossInDef[i] * saddle);
            sum += lossInDef[i] * mid / (1.0 - p + mid);
        }
        return sum;
    }

    /** 2nd derivative of CGF. Direct port of C++ {@code CumGen2ndDerivativeCond}. */
    public static double cumGen2ndDerivativeCond(final double[] condProbs,
                                                 final double[] lossInDef,
                                                 final double saddle) {
        QL.require(condProbs.length == lossInDef.length, "size mismatch");
        double sum = 0.0;
        for (int i = 0; i < condProbs.length; ++i) {
            final double p = condProbs[i];
            final double mid = p * Math.exp(lossInDef[i] * saddle);
            final double denom = 1.0 - p + mid;
            sum += lossInDef[i] * lossInDef[i] * mid / denom
                    - Math.pow(lossInDef[i] * mid / denom, 2.0);
        }
        return sum;
    }

    /** 3rd derivative of CGF. Direct port of C++ {@code CumGen3rdDerivativeCond}. */
    public static double cumGen3rdDerivativeCond(final double[] condProbs,
                                                 final double[] lossInDef,
                                                 final double saddle) {
        QL.require(condProbs.length == lossInDef.length, "size mismatch");
        double sum = 0.0;
        for (int i = 0; i < condProbs.length; ++i) {
            final double p = condProbs[i];
            final double mid = p * Math.exp(lossInDef[i] * saddle);
            final double denom = 1.0 - p + mid;
            final double s0 = denom;
            final double s1 = lossInDef[i] * mid;
            final double s2 = lossInDef[i] * s1;
            final double s3 = lossInDef[i] * s2;
            sum += (s3 + (2.0 * Math.pow(s1, 3.0) / s0 - 3.0 * s1 * s2) / s0) / s0;
        }
        return sum;
    }

    /** 4th derivative of CGF. Direct port of C++ {@code CumGen4thDerivativeCond}. */
    public static double cumGen4thDerivativeCond(final double[] condProbs,
                                                 final double[] lossInDef,
                                                 final double saddle) {
        QL.require(condProbs.length == lossInDef.length, "size mismatch");
        double sum = 0.0;
        for (int i = 0; i < condProbs.length; ++i) {
            final double p = condProbs[i];
            final double mid = p * Math.exp(lossInDef[i] * saddle);
            final double denom = 1.0 - p + mid;
            final double s0 = denom;
            final double s1 = lossInDef[i] * mid;
            final double s2 = lossInDef[i] * s1;
            final double s3 = lossInDef[i] * s2;
            final double s4 = lossInDef[i] * s3;
            sum += (s4 + (-4.0 * s1 * s3 - 3.0 * s2 * s2
                    + (12.0 * s1 * s1 * s2 - 6.0 * Math.pow(s1, 4.0) / s0) / s0) / s0) / s0;
        }
        return sum;
    }

    /**
     * Combined kernel returning K, K2, K3, K4 in a single sweep through the
     * names — matches C++ {@code CumGen0234DerivCond}. K1 is intentionally
     * omitted as in the C++ code (callers requesting K1 separately should
     * use {@link #cumGen1stDerivativeCond}).
     *
     * @return array of length 4: {@code {deriv0, deriv2, deriv3, deriv4}}
     */
    public static double[] cgf0234DerivCond(final double[] condProbs,
                                            final double[] lossInDef,
                                            final double saddle) {
        QL.require(condProbs.length == lossInDef.length, "size mismatch");
        double d0 = 0.0, d2 = 0.0, d3 = 0.0, d4 = 0.0;
        for (int i = 0; i < condProbs.length; ++i) {
            final double p = condProbs[i];
            final double mid = p * Math.exp(lossInDef[i] * saddle);
            final double denom = 1.0 - p + mid;
            final double s0 = denom;
            final double s1 = lossInDef[i] * mid;
            final double s2 = lossInDef[i] * s1;
            final double s3 = lossInDef[i] * s2;
            final double s4 = lossInDef[i] * s3;
            d0 += Math.log(s0);
            d2 += s2 / s0 - Math.pow(s1 / s0, 2.0);
            d3 += (s3 + (2.0 * Math.pow(s1, 3.0) / s0 - 3.0 * s1 * s2) / s0) / s0;
            d4 += (s4 + (-4.0 * s1 * s3 - 3.0 * s2 * s2
                    + (12.0 * s1 * s1 * s2 - 6.0 * Math.pow(s1, 4.0) / s0) / s0) / s0) / s0;
        }
        return new double[]{d0, d2, d3, d4};
    }

    /**
     * Returns K and K2 in a single sweep — matches C++ {@code CumGen02DerivCond}.
     *
     * @return array of length 2: {@code {deriv0, deriv2}}
     */
    public static double[] cgf02DerivCond(final double[] condProbs,
                                          final double[] lossInDef,
                                          final double saddle) {
        QL.require(condProbs.length == lossInDef.length, "size mismatch");
        double d0 = 0.0, d2 = 0.0;
        for (int i = 0; i < condProbs.length; ++i) {
            final double p = condProbs[i];
            final double mid = p * Math.exp(lossInDef[i] * saddle);
            final double denom = 1.0 - p + mid;
            final double s0 = denom;
            final double s1 = lossInDef[i] * mid;
            final double s2 = lossInDef[i] * s1;
            d0 += Math.log(s0);
            d2 += s2 / s0 - Math.pow(s1 / s0, 2.0);
        }
        return new double[]{d0, d2};
    }

    // ------------------------------------------------------------------------
    // Saddle-point solver
    // ------------------------------------------------------------------------

    /**
     * Find the saddle point {@code s*} satisfying {@code K'(s*) = lossLevel}
     * via Newton-Raphson, using {@link #cumGen2ndDerivativeCond} as the
     * derivative.
     *
     * <p>Mirrors {@code SaddlePointLossModel::findSaddle} except this
     * static variant does Newton-Raphson directly rather than dispatching
     * through QuantLib's {@code Newton} 1D solver.
     *
     * @param condProbs    per-name conditional default probabilities
     * @param lossInDef    per-name fractional LGD weights
     * @param lossLevel    target loss level (in fractional portfolio loss units)
     * @param accuracy     absolute convergence threshold on |K'(s)−lossLevel|
     * @param maxEvals     maximum Newton iterations
     * @return saddle-point estimate {@code s*}
     */
    public static double findSaddleNewton(final double[] condProbs,
                                          final double[] lossInDef,
                                          final double lossLevel,
                                          final double accuracy,
                                          final int maxEvals) {
        // Initial guess: s0 = 0 (portfolio expected loss). For lossLevel
        // > expected loss (typical pricing case) the saddle point is positive;
        // the K' is monotone increasing in s, so Newton converges from any
        // bracketed start.
        double s = 0.0;
        for (int it = 0; it < maxEvals; ++it) {
            final double k1 = cumGen1stDerivativeCond(condProbs, lossInDef, s);
            final double err = k1 - lossLevel;
            if (Math.abs(err) < accuracy) {
                return s;
            }
            final double k2 = cumGen2ndDerivativeCond(condProbs, lossInDef, s);
            QL.require(k2 > 0.0, "Saddle-point Newton: K'' non-positive (degeneracy?)");
            s -= err / k2;
        }
        QL.require(false, "Saddle-point Newton failed to converge in " + maxEvals + " evals");
        return Double.NaN;
    }

    /** Default-tolerance overload matching the C++ {@code findSaddle} defaults. */
    public static double findSaddleNewton(final double[] condProbs,
                                          final double[] lossInDef,
                                          final double lossLevel) {
        return findSaddleNewton(condProbs, lossInDef, lossLevel, 1.0e-3, 50);
    }
}
