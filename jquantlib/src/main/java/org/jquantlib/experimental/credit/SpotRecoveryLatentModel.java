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

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.experimental.math.CopulaPolicy;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.DefaultProbabilityTermStructure;
import org.jquantlib.time.Date;

/**
 * Random spot-recovery latent variable portfolio model.
 *
 * <p>Java port of QuantLib v1.42.1 template
 * {@code template <class copulaPolicy> class SpotRecoveryLatentModel}
 * (declared in {@code ql/experimental/credit/spotlosslatentmodel.hpp}). Pinned
 * commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>The model adds a stochastic-recovery layer on top of the default-only
 * Latent Model. References:
 * <ul>
 *   <li><b>A Spot Stochastic Recovery Extension of the Gaussian Copula</b>,
 *       N.Bennani &amp; J.Maetz, MPRA July 2009.</li>
 *   <li><b>Extension of Spot Recovery model for Gaussian Copula</b>,
 *       H.Li, October 2009, MPRA.</li>
 * </ul>
 *
 * <p>The factor-weight matrix has size {@code 2 N x F}: rows {@code [0,N)} are
 * the default-event variables; rows {@code [N, 2N)} are the recovery-rate
 * variables. The constructor enforces the equal-split convention.
 *
 * <p>Java vs C++ differences:
 * <ul>
 *   <li>The C++ {@code conditionalRecoveryInvPinvRR} symbol referenced
 *       inside {@code conditionalExpLossRRInv} is undefined in v1.42.1
 *       (apparent stale ToDo — never compiles, never instantiates). The
 *       Java port resolves this by routing through the existing
 *       {@link #expCondRecoveryInvPinvRR(double, double, int, double[])}
 *       which IS defined and matches the C++ formula in eq. 44 (Li 2009).
 *       Justification: the missing C++ method was clearly intended to be
 *       this one — both take the same {@code (invP, invRR, iName, mkt)}
 *       signature and both yield the conditional recovery rate. Phase
 *       4m.7c-b WI-3.</li>
 *   <li>The C++ {@code Basket} link uses {@code shared_ptr}; Java uses a
 *       direct reference. {@code resetBasket} mirrors C++ semantics.</li>
 * </ul>
 *
 * <p>Phase 4m.7c WI-4 foundation; Phase 4m.7c-b WI-3 added
 * {@code conditionalExpLossRR*} and {@code expectedLoss}.
 *
 * @param <P> the {@link CopulaPolicy} subtype controlling distributions
 */
public class SpotRecoveryLatentModel<P extends CopulaPolicy> extends LatentModel<P> {

    private final double[] recoveries_;
    private final double modelA_;
    /** {@code crossIdiosyncFctrs_[i] = Σ_k a_{i,k}^2 a_{N+i,k}^2}. */
    private final double[] crossIdiosyncFctrs_;
    private final int numNames_;
    private final LMIntegration integration_;
    /** Optional basket reference set via {@link #resetBasket(Basket)}. */
    private Basket basket_;

    /**
     * @param factorWeights  size {@code 2 N x F} factor matrix (rows
     *                       {@code [0,N)} default; rows {@code [N,2N)} recovery)
     * @param recoveries     unconditional per-name recovery rates (length {@code N})
     * @param modelA         model parameter "A" (eq. 42 in Li 2009)
     * @param copula         copula-policy instance
     * @param integralType   integration backend selection
     */
    public SpotRecoveryLatentModel(final List<List<Double>> factorWeights,
                                   final List<Double> recoveries,
                                   final double modelA,
                                   final P copula,
                                   final IntegrationType integralType) {
        super(factorWeights, copula);
        QL.require(factorWeights.size() % 2 == 0,
                "Number of RR variables must be equal to number of default variables");
        this.numNames_ = factorWeights.size() / 2;
        QL.require(recoveries.size() == numNames_,
                "Number of recoveries does not match number of defaultable entities.");
        this.recoveries_ = new double[numNames_];
        for (int i = 0; i < numNames_; ++i) recoveries_[i] = recoveries.get(i);
        this.modelA_ = modelA;
        this.crossIdiosyncFctrs_ = new double[numNames_];
        for (int i = 0; i < numNames_; ++i) {
            double cumul = 0.0;
            final List<Double> rowI = factorWeights.get(i);
            final List<Double> rowI_RR = factorWeights.get(i + numNames_);
            for (int k = 0; k < rowI.size(); ++k) {
                final double a_ik = rowI.get(k);
                final double a_NRk = rowI_RR.get(k);
                cumul += a_ik * a_ik * a_NRk * a_NRk;
            }
            crossIdiosyncFctrs_[i] = cumul;
        }
        this.integration_ = LatentModel.createLMIntegration(
                factorWeights.get(0).size(), integralType);
    }

    /** Number of names ({@code factorWeights.size() / 2}). */
    public int numNames() {
        return numNames_;
    }

    /** Per-name model parameter "A" (eq. 42 in Li 2009). */
    public double modelA() {
        return modelA_;
    }

    /** Read-only view of unconditional recoveries. */
    public double[] recoveries() {
        return recoveries_.clone();
    }

    /** Cached cross-idiosyncratic factor for name {@code iName}. */
    public double crossIdiosyncFactor(final int iName) {
        return crossIdiosyncFctrs_[iName];
    }

    /**
     * Bind a basket (mirrors C++ {@code resetBasket}). The basket size
     * must equal {@code numNames}.
     */
    public void resetBasket(final Basket basket) {
        QL.require(basket.size() == numNames_,
                "Incompatible new basket and model sizes.");
        this.basket_ = basket;
    }

    @Override
    protected LMIntegration integration() {
        return integration_;
    }

    /**
     * Conditional default probability given an unconditional probability and
     * a market-factor sample.
     *
     * <p>Mirrors C++ {@code conditionalDefaultProbability(prob, iName, mkt)}.
     */
    public double conditionalDefaultProbability(final double prob, final int iName,
                                                final double[] mktFactors) {
        if (prob < 1.0e-10) return 0.0;
        return conditionalDefaultProbabilityInvP(
                inverseCumulativeY(prob, iName), iName, mktFactors);
    }

    /**
     * Performance variant taking the inverse-cumulative directly. Mirrors C++
     * {@code conditionalDefaultProbabilityInvP(invCumYProb, iName, m)}.
     */
    public double conditionalDefaultProbabilityInvP(final double invCumYProb, final int iName,
                                                    final double[] m) {
        final List<Double> w = factorWeights_.get(iName);
        double sumMs = 0.0;
        for (int k = 0; k < w.size(); ++k) {
            sumMs += w.get(k) * m[k];
        }
        return cumulativeZ((invCumYProb - sumMs) / idiosyncFctrs_[iName]);
    }

    /**
     * Conditional default probability at date {@code d} given a market-factor
     * sample. Mirrors C++ {@code conditionalDefaultProbability(date, iName, m)}.
     *
     * <p>Requires a basket to be bound via {@link #resetBasket(Basket)}.
     */
    public double conditionalDefaultProbability(final Date date, final int iName,
                                                 final double[] mktFactors) {
        QL.require(basket_ != null, "No portfolio basket set.");
        final Pool pool = basket_.pool();
        final List<DefaultProbKey> dks = basket_.defaultKeys();
        final List<String> names = basket_.names();
        final Handle<DefaultProbabilityTermStructure> dts =
                pool.get(names.get(iName)).defaultProbability(dks.get(iName));
        final double pDefUncond = dts.currentLink().defaultProbability(date);
        return conditionalDefaultProbability(pDefUncond, iName, mktFactors);
    }

    /**
     * Expected conditional spot recovery rate. Mirrors C++
     * {@code expCondRecoveryInvPinvRR(invUncondDefP, invUncondRR, iName, m)}.
     */
    public double expCondRecoveryInvPinvRR(final double invUncondDefP,
                                            final double invUncondRR,
                                            final int iName,
                                            final double[] mktFactors) {
        final List<Double> w_def = factorWeights_.get(iName);
        final List<Double> w_RR  = factorWeights_.get(iName + numNames_);
        double sumMs = 0.0;
        for (int k = 0; k < w_def.size(); ++k) {
            sumMs += w_def.get(k) * mktFactors[k];
        }
        double sumBetaLoss = 0.0;
        for (int k = 0; k < w_RR.size(); ++k) {
            final double v = w_RR.get(k);
            sumBetaLoss += v * v;
        }
        final double cross = crossIdiosyncFctrs_[iName];
        final double a2 = modelA_ * modelA_;
        final double numerator =
                sumMs
                + Math.sqrt(1.0 - cross) * Math.sqrt(1.0 + a2) * invUncondRR
                - Math.sqrt(cross) * invUncondDefP;
        final double denominator = Math.sqrt(1.0 - sumBetaLoss + a2 * (1.0 - cross));
        return cumulativeZ(numerator / denominator);
    }

    /**
     * Convenience wrapper: takes an unconditional probability {@code uncondDefP}
     * and recovers the inv-cumul before delegating to {@link #expCondRecoveryInvPinvRR}.
     */
    public double expCondRecoveryP(final double uncondDefP, final int iName,
                                   final double[] mktFactors) {
        return expCondRecoveryInvPinvRR(
                inverseCumulativeY(uncondDefP, iName),
                inverseCumulativeY(recoveries_[iName], iName + numNames_),
                iName,
                mktFactors);
    }

    /**
     * Date-driven variant. Mirrors C++ {@code expCondRecovery(d, iName, mkt)}.
     *
     * <p>Requires a basket to be bound via {@link #resetBasket(Basket)}.
     */
    public double expCondRecovery(final Date d, final int iName, final double[] mktFactors) {
        QL.require(basket_ != null, "No portfolio basket set.");
        final Pool pool = basket_.pool();
        final List<DefaultProbKey> dks = basket_.defaultKeys();
        final List<String> names = basket_.names();
        final Handle<DefaultProbabilityTermStructure> dts =
                pool.get(names.get(iName)).defaultProbability(dks.get(iName));
        final double pDefUncond = dts.currentLink().defaultProbability(d);
        return expCondRecoveryP(pDefUncond, iName, mktFactors);
    }

    /**
     * Implements eq. 42 on p.14 of Li 2009: the realised conditional recovery
     * rate sample given a latent-variable sample and a date.
     *
     * <p>Mirrors C++ {@code conditionalRecovery(latentVarSample, iName, d)}.
     * Requires a basket bound. Designed to be called inside a Monte-Carlo
     * loop only when the corresponding sample led to a default.
     */
    public double conditionalRecovery(final double latentVarSample, final int iName,
                                      final Date d) {
        QL.require(basket_ != null, "No portfolio basket set.");
        final Pool pool = basket_.pool();
        final List<DefaultProbKey> dks = basket_.defaultKeys();
        final List<String> names = basket_.names();
        final Handle<DefaultProbabilityTermStructure> dts =
                pool.get(names.get(iName)).defaultProbability(dks.get(iName));
        final double pdef = dts.currentLink().defaultProbability(d, true);
        if (pdef < 1.0e-10) return 0.0;
        final int iRecovery = iName + numNames_;
        final double cross = crossIdiosyncFctrs_[iName];
        final double term = (latentVarSample - Math.sqrt(cross) * inverseCumulativeY(pdef, iName))
                / (modelA_ * Math.sqrt(1.0 - cross))
                + Math.sqrt(1.0 + 1.0 / (modelA_ * modelA_))
                * inverseCumulativeY(recoveries_[iName], iRecovery);
        return cumulativeY(term, iRecovery);
    }

    /**
     * Returns the recovery sample for name {@code iName}. Mirrors C++
     * {@code latentRRVarValue}. Convention: factor index {@code iName +
     * numNames_} addresses the recovery latent variable.
     */
    public double latentRRVarValue(final double[] allFactors, final int iName) {
        return latentVarValue(allFactors, iName + numNames_);
    }

    // ------------------------------------------------------------------------
    //  Phase 4m.7c-b WI-3 — conditionalExpLossRR family + expectedLoss
    // ------------------------------------------------------------------------

    /**
     * Conditional expected loss for name {@code iName} given pre-inverted
     * unconditional default probability and recovery rate. Mirrors C++
     * {@code conditionalExpLossRRInv}, with {@code conditionalRecoveryInvPinvRR}
     * resolved to {@link #expCondRecoveryInvPinvRR}.
     *
     * <p>Returns {@code pdef × (1 − E[RR | …])}.
     */
    public double conditionalExpLossRRInv(final double invP,
                                           final double invRR,
                                           final int iName,
                                           final double[] mktFactors) {
        return conditionalDefaultProbabilityInvP(invP, iName, mktFactors)
                * (1.0 - expCondRecoveryInvPinvRR(invP, invRR, iName, mktFactors));
    }

    /**
     * Date-driven conditional expected loss for name {@code iName}. Mirrors
     * C++ {@code conditionalExpLossRR}.
     */
    public double conditionalExpLossRR(final Date d, final int iName,
                                        final double[] mktFactors) {
        QL.require(basket_ != null, "No portfolio basket set.");
        final Pool pool = basket_.pool();
        final List<DefaultProbKey> dks = basket_.defaultKeys();
        final List<String> names = basket_.names();
        final Handle<DefaultProbabilityTermStructure> dts =
                pool.get(names.get(iName)).defaultProbability(dks.get(iName));
        final double pDefUncond = dts.currentLink().defaultProbability(d);
        final double invP = inverseCumulativeY(pDefUncond, iName);
        final double invRR = inverseCumulativeY(recoveries_[iName], iName + numNames_);
        return conditionalExpLossRRInv(invP, invRR, iName, mktFactors);
    }

    /**
     * Single-name expected loss at date {@code d}. Mirrors C++
     * {@code expectedLoss}: integrates {@link #conditionalExpLossRRInv} over
     * the systemic-factor density. Used for testing model coherence —
     * preserves the marginal loss {@code pdef × (1 − R̄)} of the input single-
     * name CDS calibration.
     *
     * <p>Requires a basket bound via {@link #resetBasket(Basket)}.
     */
    public double expectedLoss(final Date d, final int iName) {
        QL.require(basket_ != null, "No portfolio basket set.");
        final Pool pool = basket_.pool();
        final List<DefaultProbKey> dks = basket_.defaultKeys();
        final List<String> names = basket_.names();
        final Handle<DefaultProbabilityTermStructure> dts =
                pool.get(names.get(iName)).defaultProbability(dks.get(iName));
        final double pDefUncond = dts.currentLink().defaultProbability(d);
        final double invP = inverseCumulativeY(pDefUncond, iName);
        final double invRR = inverseCumulativeY(recoveries_[iName], iName + numNames_);
        return integratedExpectedValue((double[] v) ->
                conditionalExpLossRRInv(invP, invRR, iName, v));
    }
}
