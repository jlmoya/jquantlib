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

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.experimental.math.CopulaPolicy;
import org.jquantlib.math.Ops;
import org.jquantlib.math.solvers1D.Brent;
import org.jquantlib.time.Date;

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
 * <p>Phase 4m.7c-b adds: basket-driven public dispatchers
 * ({@link #probOverLoss(Date, double)}, {@link #expectedTrancheLoss(Date)},
 * {@link #probOverPortfLoss(Date, double)}, {@link #probDensity(Date, double)},
 * {@link #splitVaRLevel(Date, double)}, {@link #percentile(Date, double)},
 * {@link #expectedShortfall(Date, double)}, {@link #lossDistribution(Date)}),
 * Brent-based saddle-point solver, and {@link SaddleObjectiveFunction} /
 * {@link SaddlePercObjFunction} inner Newton wrappers.
 *
 * <p>Deferred: {@code expectedShortfallSplitCond} (full ESF-split partition),
 * {@code conditionalExpectedTrancheLossCond} integration variant, and base-
 * correlation calibration helpers (small surface; out of time-cap).
 *
 * @param <P> the {@link CopulaPolicy} bound through the underlying
 *            {@link ConstantLossLatentModel}
 */
public class SaddlepointLossModel<P extends CopulaPolicy> extends DefaultLossModel {

    private final ConstantLossLatentModel<P> copula_;

    /** Cached basket {@code remainingNotionals()}; synced in {@link #resetModel()}. */
    private double[] remainingNotionals_;
    /** Cached basket {@code remainingNotional()}. */
    private double remainingNotional_;
    /** Cached attachment-amount / remainingNotional, capped at 1. */
    private double attachRatio_;
    /** Cached detachment-amount / remainingNotional, capped at 1. */
    private double detachRatio_;
    /** Number of names in the cached basket (== {@code remainingNotionals_.length}). */
    private int remainingSize_;

    public SaddlepointLossModel(final ConstantLossLatentModel<P> copula) {
        this.copula_ = copula;
    }

    @Override
    protected void resetModel() {
        if (basket != null) {
            final List<Double> rn = basket.remainingNotionals();
            remainingSize_ = rn.size();
            remainingNotionals_ = new double[remainingSize_];
            for (int i = 0; i < remainingSize_; ++i) {
                remainingNotionals_[i] = rn.get(i);
            }
            remainingNotional_ = basket.remainingNotional();
            attachRatio_ = Math.min(basket.remainingAttachmentAmount() / remainingNotional_, 1.0);
            detachRatio_ = Math.min(basket.remainingDetachmentAmount() / remainingNotional_, 1.0);
            copula_.resetBasket(basket);
        }
    }

    /** Read-only access to the underlying constant-loss latent model. */
    public ConstantLossLatentModel<P> copula() {
        return copula_;
    }

    /** Cached attach-ratio (remaining attachment over remaining notional). */
    public final double attachRatio() {
        return attachRatio_;
    }

    /** Cached detach-ratio (remaining detachment over remaining notional). */
    public final double detachRatio() {
        return detachRatio_;
    }

    /** Cached remaining notional (sum of survivor notionals). */
    public final double remainingNotional() {
        return remainingNotional_;
    }

    /** Cached remaining notional vector. */
    public final double[] remainingNotionalsArray() {
        return remainingNotionals_.clone();
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

    // ========================================================================
    //  Phase 4m.7c-b — Basket-driven dispatchers + Brent saddle solver
    // ========================================================================

    /**
     * Build the {@code invUncondProbs} vector from the basket's remaining
     * unconditional default probabilities at date {@code d} via the copula's
     * {@code inverseCumulativeY} on each per-name probability — the same
     * pre-integration step used in every C++ basket dispatcher.
     */
    private double[] buildInvUncondProbs(final Date d) {
        QL.require(basket != null, "Basket not set on SaddlepointLossModel");
        final List<Double> rp = basket.remainingProbabilities(d);
        final double[] inv = new double[rp.size()];
        for (int i = 0; i < inv.length; ++i) {
            inv[i] = copula_.inverseCumulativeY(rp.get(i), i);
        }
        return inv;
    }

    /**
     * Build the per-name conditional default-probability vector at the given
     * market-factor sample, from pre-inverted unconditional probabilities.
     * Mirrors the inner loop of every {@code XxxCond} method in C++.
     */
    private double[] buildCondProbs(final double[] invUncondProbs, final double[] mktFactor) {
        final int n = remainingSize_;
        final double[] cp = new double[n];
        for (int i = 0; i < n; ++i) {
            cp[i] = copula_.conditionalDefaultProbabilityInvP(invUncondProbs[i], i, mktFactor);
        }
        return cp;
    }

    /**
     * Build the per-name fractional LGD vector (loss-in-default / total
     * remaining notional) given the market-factor sample. Mirrors the
     * {@code lossInDef} expression repeated inline in every C++ Cond method.
     */
    private double[] buildLossInDefRel(final double[] invUncondProbs, final double[] mktFactor) {
        final int n = remainingSize_;
        final double[] lid = new double[n];
        for (int i = 0; i < n; ++i) {
            final double rr = copula_.conditionalRecoveryInvP(invUncondProbs[i], i, mktFactor);
            lid[i] = remainingNotionals_[i] * (1.0 - rr) / remainingNotional_;
        }
        return lid;
    }

    /**
     * Per-name absolute LGD vector (notional × (1 − recovery)) — used by
     * {@code splitLossCond} and ESF helpers.
     */
    private double[] buildLossInDefAbs(final double[] invUncondProbs, final double[] mktFactor) {
        final int n = remainingSize_;
        final double[] lgd = new double[n];
        for (int i = 0; i < n; ++i) {
            final double rr = copula_.conditionalRecoveryInvP(invUncondProbs[i], i, mktFactor);
            lgd[i] = remainingNotionals_[i] * (1.0 - rr);
        }
        return lgd;
    }

    /**
     * Brent-based mkt-fct-conditional saddle-point search. Mirrors the C++
     * {@code SaddlePointLossModel::findSaddle}: brackets the saddle between
     * {@code saddleMin} and {@code saddleMax} (derived from the most-exposed
     * name's logistic-inversion limits) and uses Brent to solve
     * {@code K'(s) = lossLevel}.
     *
     * @param invUncondProbs pre-inverted unconditional probabilities
     * @param lossLevel      target relative loss (in fractional portfolio units)
     * @param mktFactor      systemic factor sample
     * @param accuracy       Brent absolute accuracy on K'(s) − lossLevel
     * @param maxEvaluations max Brent evaluations
     */
    public final double findSaddle(final double[] invUncondProbs,
                                   final double lossLevel,
                                   final double[] mktFactor,
                                   final double accuracy,
                                   final int maxEvaluations) {
        QL.require(remainingSize_ > 0, "remainingSize_ must be > 0 — basket not set?");
        // Build LGDs (absolute) for limit derivation.
        final double[] lgds = buildLossInDefAbs(invUncondProbs, mktFactor);

        // Largest relative-LGD name index.
        int iNamMax = 0;
        for (int i = 1; i < lgds.length; ++i) {
            if (lgds[i] > lgds[iNamMax]) iNamMax = i;
        }
        final double deltaMin = 1.0e-5;
        final double pMaxName = copula_.conditionalDefaultProbabilityInvP(
                invUncondProbs[iNamMax], iNamMax, mktFactor);
        final double maxRelLgd = lgds[iNamMax] / remainingNotional_;
        final double saddleMin = (1.0 / maxRelLgd)
                * Math.log(deltaMin * (1.0 - pMaxName)
                        / (pMaxName * maxRelLgd - pMaxName * deltaMin));

        // Pre-integration cond-probs and rel-LGD vectors for kernel calls.
        final double[] cp = buildCondProbs(invUncondProbs, mktFactor);
        final double[] lid = buildLossInDefRel(invUncondProbs, mktFactor);

        final double minLoss = cumGen1stDerivativeCond(cp, lid, saddleMin);
        if (lossLevel < minLoss) return saddleMin;

        final double saddleMax = (1.0 / maxRelLgd)
                * Math.log((maxRelLgd - deltaMin) * (1.0 - pMaxName)
                        / (pMaxName * deltaMin));
        final double maxLoss = cumGen1stDerivativeCond(cp, lid, saddleMax);
        if (lossLevel > maxLoss) return saddleMax;

        final SaddleObjectiveFunction f = new SaddleObjectiveFunction(this, lossLevel,
                invUncondProbs, mktFactor);
        final Brent brent = new Brent();
        brent.setMaxEvaluations(maxEvaluations);
        final double guess = (saddleMin + saddleMax) / 2.0;
        return brent.solve(f, accuracy, guess, saddleMin, saddleMax);
    }

    /** Default-tolerance overload matching the C++ {@code findSaddle} defaults. */
    public final double findSaddle(final double[] invUncondProbs,
                                   final double lossLevel,
                                   final double[] mktFactor) {
        return findSaddle(invUncondProbs, lossLevel, mktFactor, 1.0e-3, 50);
    }

    // ------------------------------------------------------------------------
    //  Conditional helpers wrapping the static kernels with basket plumbing
    // ------------------------------------------------------------------------

    /**
     * Conditional probability of the (untranched) portfolio loss exceeding
     * {@code loss} (in absolute units). Mirrors C++
     * {@code probOverLossPortfCond}.
     */
    public final double probOverLossPortfCond(final double[] invUncondProbs,
                                               final double loss,
                                               final double[] mktFactor) {
        if (loss <= QL_EPSILON_LOCAL) return 1.0;
        final double relativeLoss = loss / remainingNotional_;
        if (relativeLoss >= 1.0 - QL_EPSILON_LOCAL) return 0.0;

        // Average recovery; cap at maximum attainable loss fraction.
        double avgRR = 0.0;
        for (int i = 0; i < remainingSize_; ++i) {
            avgRR += copula_.conditionalRecoveryInvP(invUncondProbs[i], i, mktFactor);
        }
        avgRR /= remainingSize_;
        if (relativeLoss > 1.0 - avgRR) return 0.0;

        final double[] cp = buildCondProbs(invUncondProbs, mktFactor);
        final double[] lid = buildLossInDefRel(invUncondProbs, mktFactor);
        final double saddlePt = findSaddle(invUncondProbs, relativeLoss, mktFactor);
        return probOverLossPortfCondCore(cp, lid, relativeLoss, saddlePt);
    }

    /**
     * Conditional probability of the tranche losing more than the fractional
     * amount {@code trancheLossFract} (in [0, 1] of tranche notional).
     * Mirrors C++ {@code probOverLossCond}.
     */
    public final double probOverLossCond(final double[] invUncondProbs,
                                          final double trancheLossFract,
                                          final double[] mktFactor) {
        final double portfFract = attachRatio_
                + trancheLossFract * (detachRatio_ - attachRatio_);
        return probOverLossPortfCond(invUncondProbs,
                portfFract * basket.basketNotional(), mktFactor);
    }

    /** Conditional probability density at absolute loss {@code loss}. */
    public final double probDensityCond(final double[] invUncondProbs,
                                         final double loss,
                                         final double[] mktFactor) {
        if (loss <= QL_EPSILON_LOCAL) return 0.0;
        final double relativeLoss = loss / remainingNotional_;
        final double saddlePt = findSaddle(invUncondProbs, relativeLoss, mktFactor);
        final double[] cp = buildCondProbs(invUncondProbs, mktFactor);
        final double[] lid = buildLossInDefRel(invUncondProbs, mktFactor);
        final double[] cgf = cgf0234DerivCond(cp, lid, saddlePt);
        final double K0 = cgf[0];
        final double K2 = cgf[1];
        final double K3 = cgf[2];
        final double K4 = cgf[3];
        final double K2Sq = K2 * K2;
        final double K2Cb = K2Sq * K2;
        return (1.0 + K4 / (8.0 * K2Sq) - 5.0 * K3 * K3 / (24.0 * K2Cb))
                * Math.exp(K0 - saddlePt * relativeLoss)
                / Math.sqrt(2.0 * Math.PI * K2);
    }

    /**
     * Conditional total expected portfolio loss (no tranching). Basket-driven
     * variant — distinguished from the static
     * {@link #conditionalExpectedLoss(double[], double[])} by needing the
     * cached basket plumbing (recoveries via copula).
     */
    public final double conditionalExpectedLossBasket(final double[] invUncondProbs,
                                                       final double[] mktFactor) {
        double eloss = 0.0;
        for (int i = 0; i < remainingSize_; ++i) {
            final double pBuf = copula_.conditionalDefaultProbabilityInvP(
                    invUncondProbs[i], i, mktFactor);
            final double rr = copula_.conditionalRecoveryInvP(invUncondProbs[i], i, mktFactor);
            eloss += pBuf * remainingNotionals_[i] * (1.0 - rr);
        }
        return eloss;
    }

    /**
     * Conditional expected tranche loss (basket-driven). Mirrors C++
     * {@code conditionalExpectedTrancheLoss}.
     */
    public final double conditionalExpectedTrancheLossBasket(final double[] invUncondProbs,
                                                              final double[] mktFactor) {
        final double eloss = conditionalExpectedLossBasket(invUncondProbs, mktFactor);
        return Math.min(
                Math.max(eloss - attachRatio_ * remainingNotional_, 0.0),
                (detachRatio_ - attachRatio_) * remainingNotional_);
    }

    /**
     * Conditional split-loss contributions for {@link #splitVaRLevel}. Mirrors
     * the C++ {@code splitLossCond} basket variant.
     */
    public final double[] splitLossCondBasket(final double[] invUncondProbs,
                                               final double loss,
                                               final double[] mktFactor) {
        final int n = remainingSize_;
        final double[] contrib = new double[n];
        if (loss <= QL_EPSILON_LOCAL) return contrib;
        final double saddlePt = findSaddle(invUncondProbs, loss / remainingNotional_, mktFactor);
        for (int i = 0; i < n; ++i) {
            final double pBuf = copula_.conditionalDefaultProbabilityInvP(
                    invUncondProbs[i], i, mktFactor);
            final double rr = copula_.conditionalRecoveryInvP(invUncondProbs[i], i, mktFactor);
            final double lossInDef = remainingNotionals_[i] * (1.0 - rr);
            final double midFactor = pBuf * Math.exp(lossInDef * saddlePt / remainingNotional_);
            final double denom = 1.0 - pBuf + midFactor;
            contrib[i] = lossInDef * midFactor / denom;
        }
        return contrib;
    }

    /**
     * Expected-shortfall on the full portfolio conditional on the market
     * factor sample. Martin-2006 expression:
     * {@code ES(L) = EL × P(L>l) + (l − EL) × density(l)/saddle}.
     */
    public final double expectedShortfallFullPortfolioCond(final double[] invUncondProbs,
                                                            final double lossPerc,
                                                            final double[] mktFactor) {
        final double lossPercRatio = lossPerc / remainingNotional_;
        double elCond = 0.0;
        for (int i = 0; i < remainingSize_; ++i) {
            final double pBuf = copula_.conditionalDefaultProbabilityInvP(
                    invUncondProbs[i], i, mktFactor);
            final double rr = copula_.conditionalRecoveryInvP(invUncondProbs[i], i, mktFactor);
            elCond += pBuf * remainingNotionals_[i] * (1.0 - rr);
        }
        final double saddlePt = findSaddle(invUncondProbs, lossPercRatio, mktFactor);
        if (saddlePt == 0.0) {
            // ESF degenerate at saddle == 0: density / saddle blows up.
            // C++ relies on the same expression; handle defensively.
            return elCond * probOverLossPortfCond(invUncondProbs, lossPerc, mktFactor);
        }
        return elCond * probOverLossPortfCond(invUncondProbs, lossPerc, mktFactor)
                + (lossPerc - elCond) * probDensityCond(invUncondProbs, lossPerc, mktFactor)
                  / saddlePt;
    }

    // ------------------------------------------------------------------------
    //  Public basket-driven dispatchers (override DefaultLossModel surface)
    // ------------------------------------------------------------------------

    /**
     * Probability that the tranche loses {@code trancheLossFract} or more
     * (fraction of tranche notional). Mirrors C++ {@code probOverLoss}.
     */
    @Override
    public double probOverLoss(final Date d, final double trancheLossFract) {
        QL.require(basket != null, "Basket not set on SaddlepointLossModel");
        if (trancheLossFract >= basket.detachmentAmount()) return 0.0;
        final double[] inv = buildInvUncondProbs(d);
        return copula_.integratedExpectedValue(
                (final double[] v1) -> probOverLossCond(inv, trancheLossFract, v1));
    }

    /** Untranched portfolio: {@code P(L > loss | t = d)}. */
    public double probOverPortfLoss(final Date d, final double loss) {
        QL.require(basket != null, "Basket not set on SaddlepointLossModel");
        final double[] inv = buildInvUncondProbs(d);
        return copula_.integratedExpectedValue(
                (final double[] v1) -> probOverLossPortfCond(inv, loss, v1));
    }

    /** Loss-density of the untranched portfolio at absolute loss {@code loss}. */
    public double probDensity(final Date d, final double loss) {
        QL.require(basket != null, "Basket not set on SaddlepointLossModel");
        final double[] inv = buildInvUncondProbs(d);
        return copula_.integratedExpectedValue(
                (final double[] v1) -> probDensityCond(inv, loss, v1));
    }

    /**
     * Expected tranche loss at date {@code d}. Mirrors C++
     * {@code expectedTrancheLoss}.
     */
    @Override
    public double expectedTrancheLoss(final Date d) {
        QL.require(basket != null, "Basket not set on SaddlepointLossModel");
        final double[] inv = buildInvUncondProbs(d);
        return copula_.integratedExpectedValue(
                (final double[] v1) -> conditionalExpectedTrancheLossBasket(inv, v1));
    }

    /**
     * Sensitivities of individual names to a given portfolio loss value.
     * Mirrors C++ {@code splitVaRLevel}.
     */
    @Override
    public List<Double> splitVaRLevel(final Date date, final double loss) {
        QL.require(basket != null, "Basket not set on SaddlepointLossModel");
        final double[] inv = buildInvUncondProbs(date);
        final double[] out = copula_.integratedExpectedValueV(
                (final double[] v1) -> splitLossCondBasket(inv, loss, v1));
        final java.util.ArrayList<Double> result = new java.util.ArrayList<>(out.length);
        for (final double v : out) result.add(v);
        return result;
    }

    /**
     * Loss percentile (Value-at-Risk) on the tranche at date {@code d}.
     * Brent-solves {@code probOverLoss(d, x) = 1 − percentile} with x in
     * [QL_EPSILON, 1 − QL_EPSILON] of tranche notional.
     */
    @Override
    public double percentile(final Date d, final double percentile) {
        QL.require(percentile >= 0.0 && percentile <= 1.0, "Incorrect percentile value.");
        QL.require(basket != null, "Basket not set on SaddlepointLossModel");
        if (d.le(new Settings().evaluationDate())) return 0.0;
        if (percentile <= 1.0 - probOverLoss(d, 0.0)) return 0.0;
        if (percentile >= 1.0 - probOverLoss(d, 1.0)) {
            return basket.remainingTrancheNotional();
        }
        final SaddlePercObjFunction f = new SaddlePercObjFunction(this, percentile, d);
        final Brent solver = new Brent();
        solver.setMaxEvaluations(100);
        final double minVal = QL_EPSILON_LOCAL;
        final double maxVal = 1.0 - QL_EPSILON_LOCAL;
        final double guess = 0.5;
        final double soln = solver.solve(f, 1.0e-4, guess, minVal, maxVal);
        return basket.remainingTrancheNotional() * soln;
    }

    /**
     * Expected shortfall at the requested percentile. Mirrors C++
     * {@code expectedShortfall(d, percProb)}.
     */
    @Override
    public double expectedShortfall(final Date d, final double percProb) {
        QL.require(basket != null, "Basket not set on SaddlepointLossModel");
        final double lossPerc = percentile(d, percProb);
        final double trancheAmount = basket.trancheNotional() * (detachRatio_ - attachRatio_);
        if (lossPerc >= trancheAmount) return trancheAmount;
        final double[] inv = buildInvUncondProbs(d);
        return copula_.integratedExpectedValue(
                (final double[] v1) -> expectedShortfallFullPortfolioCond(inv, lossPerc, v1))
                / (1.0 - percProb);
    }

    /**
     * Loss distribution map for the untranched portfolio. Mirrors C++
     * {@code lossDistribution}: 500 sample points between 1/500 and 0.45
     * fractional loss.
     */
    @Override
    public Map<Double, Double> lossDistribution(final Date d) {
        final TreeMap<Double, Double> distrib = new TreeMap<>();
        final double numPts = 500.0;
        for (double lossFraction = 1.0 / numPts; lossFraction < 0.45;
             lossFraction += 1.0 / numPts) {
            final double absLoss = lossFraction * remainingNotional_;
            distrib.put(absLoss, 1.0 - probOverPortfLoss(d, absLoss));
        }
        return distrib;
    }

    // ========================================================================
    //  Inner objective-function classes for Brent solvers
    // ========================================================================

    /**
     * Newton-style 1-D objective: {@code f(s) = K'(s) − target}. Used by
     * {@link Brent} inside {@link SaddlepointLossModel#findSaddle} to locate
     * the saddle point. Mirrors C++ inner class
     * {@code SaddlePointLossModel::SaddleObjectiveFunction}.
     */
    public static final class SaddleObjectiveFunction implements Ops.DoubleOp {
        private final SaddlepointLossModel<?> me_;
        private final double targetValue_;
        private final double[] mktFactor_;
        private final double[] invUncondProbs_;
        private double[] cpCache_;
        private double[] lidCache_;

        public SaddleObjectiveFunction(final SaddlepointLossModel<?> me,
                                        final double target,
                                        final double[] invUncondProbs,
                                        final double[] mktFactor) {
            this.me_ = me;
            this.targetValue_ = target;
            this.invUncondProbs_ = invUncondProbs;
            this.mktFactor_ = mktFactor;
        }

        private void ensureCache() {
            if (cpCache_ == null) {
                cpCache_ = me_.buildCondProbs(invUncondProbs_, mktFactor_);
                lidCache_ = me_.buildLossInDefRel(invUncondProbs_, mktFactor_);
            }
        }

        @Override
        public double op(final double x) {
            ensureCache();
            return cumGen1stDerivativeCond(cpCache_, lidCache_, x) - targetValue_;
        }

        /** First derivative of the objective: {@code f'(s) = K''(s)}. */
        public double derivative(final double x) {
            ensureCache();
            return cumGen2ndDerivativeCond(cpCache_, lidCache_, x);
        }
    }

    /**
     * Newton-style objective for the tranche-loss percentile lookup:
     * {@code f(x) = probOverLoss(d, x) − (1 − target)}. Mirrors C++
     * {@code SaddlePercObjFunction}.
     */
    public static final class SaddlePercObjFunction implements Ops.DoubleOp {
        private final SaddlepointLossModel<?> me_;
        private final double targetValue_;
        private final Date date_;

        public SaddlePercObjFunction(final SaddlepointLossModel<?> me,
                                      final double target,
                                      final Date date) {
            this.me_ = me;
            this.targetValue_ = 1.0 - target;
            this.date_ = date;
        }

        @Override
        public double op(final double x) {
            return me_.probOverLoss(date_, x) - targetValue_;
        }
    }

    // ------------------------------------------------------------------------
    //  Static-kernel helper (reused inside basket dispatchers)
    // ------------------------------------------------------------------------

    /**
     * Static evaluator for {@link #probOverLossPortfCond(double[], double, double[])}
     * given pre-computed cond-probs / lossInDef arrays and a saddle point.
     * Refactored out to avoid duplicate work across basket and static kernels.
     */
    private static double probOverLossPortfCondCore(final double[] cp,
                                                     final double[] lid,
                                                     final double relativeLoss,
                                                     final double saddlePt) {
        final double[] cgf = cgf0234DerivCond(cp, lid, saddlePt);
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
}
