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

    // ------------------------------------------------------------------------
    //  Phase 4m.7c WI-6 — high-order saddle-point evaluators (static kernels)
    // ------------------------------------------------------------------------

    /** Quasi-epsilon equivalent of QL_EPSILON. */
    private static final double QL_EPSILON_LOCAL = 2.2204460492503131e-16;

    /**
     * Probability of portfolio loss exceeding the threshold {@code relativeLoss}
     * conditional on a market-factor sample. Mirrors C++
     * {@code SaddlePointLossModel::probOverLossPortfCond} with the higher-
     * order corrections {@code (1 - s^3 K3 / 6 + s^4 K4 / 24 + s^6 K3^2 / 72)}.
     *
     * <p>Static-kernel form: the caller is responsible for converting
     * unconditional probabilities and per-name LGD weights into the
     * {@code condProbs} and {@code lossInDef} arrays.
     *
     * <p>Numerical robustness: returns 0 if the saddle-evaluator exponent
     * exceeds 700 (would otherwise overflow {@code exp}); returns 0 / 1 at
     * the loss-fraction extremes; returns 0.5 at the saddle-zero pivot.
     *
     * @param condProbs    per-name conditional default probabilities
     * @param lossInDef    per-name fractional LGD weights
     * @param relativeLoss target loss as a fraction of the (sub-)portfolio
     *                     notional (in [0, 1])
     * @return P(L > L0 | systemic factor) using the saddle-point expansion
     */
    public static double probOverLossPortfCond(final double[] condProbs,
                                                final double[] lossInDef,
                                                final double relativeLoss) {
        if (relativeLoss <= QL_EPSILON_LOCAL) return 1.0;
        if (relativeLoss >= 1.0 - QL_EPSILON_LOCAL) return 0.0;

        final double saddlePt = findSaddleNewton(condProbs, lossInDef, relativeLoss);
        final double[] cgf = cgf0234DerivCond(condProbs, lossInDef, saddlePt);
        final double K0 = cgf[0];
        final double K2 = cgf[1];
        final double K3 = cgf[2];
        final double K4 = cgf[3];

        final double s2 = saddlePt * saddlePt;
        final double s3 = s2 * saddlePt;
        final double s4 = s3 * saddlePt;
        final double s6 = s4 * s2;
        final double K3Sq = K3 * K3;

        if (saddlePt > 0.0) {
            final double exponent = K0 - relativeLoss * saddlePt + 0.5 * s2 * K2;
            if (Math.abs(exponent) > 700.0) return 0.0;
            return Math.exp(exponent)
                    * standardNormalCdf(-Math.abs(saddlePt) * Math.sqrt(K2))
                    * (1.0 - s3 * K3 / 6.0 + s4 * K4 / 24.0 + s6 * K3Sq / 72.0);
        } else if (saddlePt == 0.0) {
            return 0.5;
        } else {
            final double exponent = K0 - relativeLoss * saddlePt + 0.5 * s2 * K2;
            if (Math.abs(exponent) > 700.0) return 0.0;
            return 1.0 - Math.exp(exponent)
                    * standardNormalCdf(-Math.abs(saddlePt) * Math.sqrt(K2))
                    * (1.0 - s3 * K3 / 6.0 + s4 * K4 / 24.0 + s6 * K3Sq / 72.0);
        }
    }

    /**
     * Probability density at portfolio loss {@code relativeLoss} conditional
     * on a market-factor sample. Mirrors C++
     * {@code SaddlePointLossModel::probDensityCond} with the saddle-point
     * higher-order density correction
     * {@code 1 + K4/(8 K2^2) - 5 K3^2/(24 K2^3)}.
     *
     * @return f(L | systemic factor)
     */
    public static double probDensityCond(final double[] condProbs,
                                          final double[] lossInDef,
                                          final double relativeLoss) {
        if (relativeLoss <= QL_EPSILON_LOCAL) return 0.0;
        final double saddlePt = findSaddleNewton(condProbs, lossInDef, relativeLoss);
        final double[] cgf = cgf0234DerivCond(condProbs, lossInDef, saddlePt);
        final double K0 = cgf[0];
        final double K2 = cgf[1];
        final double K3 = cgf[2];
        final double K4 = cgf[3];
        final double K2Sq = K2 * K2;
        final double K2Cb = K2Sq * K2;
        return (1.0
                + K4 / (8.0 * K2Sq)
                - 5.0 * K3 * K3 / (24.0 * K2Cb))
                * Math.exp(K0 - saddlePt * relativeLoss)
                / Math.sqrt(2.0 * Math.PI * K2);
    }

    /**
     * Per-name loss contribution at the requested loss level. Mirrors C++
     * {@code SaddlePointLossModel::splitLossCond}: returns sensitivities
     * (untranched) such that summing the result yields the requested loss.
     *
     * <p>See "VAR: who contributes and how much?" by R.Martin, K.Thompson,
     * C.Browne, Risk August 2001 eq 8.
     *
     * <p>Static-kernel form: takes raw conditional probabilities, per-name
     * absolute LGD ({@code lossInDefAbs[i]} = name's exposure × (1 − R)) and
     * the {@code remainingNotional} for the sensitivity-saddle scaling.
     *
     * @param condProbs        per-name conditional default probabilities
     * @param lossInDefAbs     per-name absolute LGD (in notional units)
     * @param remainingNotional total remaining notional (sum of survivors)
     * @param loss             absolute loss level
     * @return per-name absolute loss contributions; {@code sum(result) == loss}
     */
    public static double[] splitLossCond(final double[] condProbs,
                                         final double[] lossInDefAbs,
                                         final double remainingNotional,
                                         final double loss) {
        final int n = condProbs.length;
        final double[] contrib = new double[n];
        if (loss <= QL_EPSILON_LOCAL) return contrib;

        final double[] lossInDefRel = new double[n];
        for (int i = 0; i < n; ++i) lossInDefRel[i] = lossInDefAbs[i] / remainingNotional;
        final double saddlePt = findSaddleNewton(condProbs, lossInDefRel,
                loss / remainingNotional);
        for (int i = 0; i < n; ++i) {
            final double pBuf = condProbs[i];
            final double midFactor = pBuf * Math.exp(lossInDefAbs[i] * saddlePt / remainingNotional);
            final double denom = 1.0 - pBuf + midFactor;
            contrib[i] = lossInDefAbs[i] * midFactor / denom;
        }
        return contrib;
    }

    /**
     * Conditional expected portfolio loss (untranched). Mirrors C++
     * {@code SaddlePointLossModel::conditionalExpectedLoss}: a basic
     * weighted sum {@code Σ p_i × LGD_i} with no saddle-point machinery.
     */
    public static double conditionalExpectedLoss(final double[] condProbs,
                                                  final double[] lossInDefAbs) {
        double e = 0.0;
        for (int i = 0; i < condProbs.length; ++i) {
            e += condProbs[i] * lossInDefAbs[i];
        }
        return e;
    }

    /**
     * Conditional expected tranche loss. Mirrors C++
     * {@code SaddlePointLossModel::conditionalExpectedTrancheLoss}.
     */
    public static double conditionalExpectedTrancheLoss(final double[] condProbs,
                                                         final double[] lossInDefAbs,
                                                         final double attachAmount,
                                                         final double detachAmount) {
        final double e = conditionalExpectedLoss(condProbs, lossInDefAbs);
        return Math.min(Math.max(e - attachAmount, 0.0), detachAmount - attachAmount);
    }

    // -- helpers --

    private static final org.jquantlib.math.distributions.CumulativeNormalDistribution PHI =
            new org.jquantlib.math.distributions.CumulativeNormalDistribution();

    private static double standardNormalCdf(final double x) {
        return PHI.op(x);
    }
}
