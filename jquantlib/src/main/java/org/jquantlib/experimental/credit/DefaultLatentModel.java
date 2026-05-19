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
import org.jquantlib.time.Date;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Default event Latent Model.
 *
 * <p>Java port of QuantLib v1.42.1 template
 * {@code template <class copulaPolicy> class DefaultLatentModel} (declared in
 * {@code ql/experimental/credit/defaultprobabilitylatentmodel.hpp}). Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>Models joint default events in a portfolio based on a generic Latent
 * Model. It models solely the default events in a portfolio, not making any reference to severities, exposures, etc. An
 * implicit correspondence is established between the variables modelled and the names in the basket given by the basket
 * and model variable access indices.
 *
 * <p>The class is parametric on the Latent Model copula.
 *
 * @param <P> the {@link CopulaPolicy} subtype controlling distributions
 */
public class DefaultLatentModel< P extends CopulaPolicy > extends LatentModel< P > {

    /** Integration backend (one per latent model instance). */
    protected final LMIntegration integration_;
    /** Optional basket reference used by name-based queries. */
    protected Basket basket_;

    /**
     * Constructs a {@link DefaultLatentModel} with arbitrary number of latent variables and factors given by the
     * dimensions of the passed matrix.
     *
     * @param factorWeights ordering is {@code factorWeights[iVariable].get(iFactor)}
     * @param copula        copula-policy instance
     * @param integralType  integration backend selection
     */
    public DefaultLatentModel(final List< List< Double > > factorWeights, final P copula,
            final IntegrationType integralType) {
        super(factorWeights, copula);
        this.integration_ = LatentModel.createLMIntegration(factorWeights.get(0).size(), integralType);
    }

    /** Single-factor uniform-weight constructor (mirrors C++ {@code Handle<Quote>} ctor). */
    public DefaultLatentModel(final double correlSqr, final int nVariables, final P copula,
            final IntegrationType integralType) {
        super(correlSqr, nVariables, copula);
        this.integration_ = LatentModel.createLMIntegration(1, integralType);
    }

    /** Helper: build a single-row factor matrix from a flat array of weights. */
    public static List< List< Double > > singleRow(final double... w) {
        final List< List< Double > > out = new ArrayList<>(1);
        out.add(new ArrayList<>(toBoxed(w)));
        return out;
    }

    private static List< Double > toBoxed(final double[] w) {
        final List< Double > b = new ArrayList<>(w.length);
        for ( final double d : w ) {
            b.add(d);
        }
        return b;
    }

    /** Suppressed-warning helper — keeps Arrays import live for API consumers. */
    @SuppressWarnings( "unused" )
    private static void keepArrays() {
        List.of();
    }

    /**
     * Reset the basket reference. Mirrors C++ {@code resetBasket()}.
     *
     * @throws RuntimeException if {@code basket.size() != factorWeights_.size()}
     */
    public void resetBasket(final Basket basket) {
        QL.require(basket.size() == factorWeights_.size(), "Incompatible new basket and model sizes.");
        this.basket_ = basket;
    }

    /**
     * Returns the probability of default of a given name conditional on the realisation of a given set of values of the
     * model independent factors. The date at which the probability is given is implicit in the probability since
     * there's no other time dependence in this model.
     *
     * @param prob       unconditional probability of default
     * @param iName      desired name
     * @param mktFactors value of LM independent factors
     */
    public final double conditionalDefaultProbability(final double prob, final int iName, final double[] mktFactors) {
        if ( prob < 1.0e-10 ) {
            return 0.0;
        }
        return conditionalDefaultProbabilityInvP(inverseCumulativeY(prob, iName), iName, mktFactors);
    }

    /**
     * Performance-oriented version that takes the inverse cumulative directly. Used in tight integration loops to avoid
     * recomputing the inverse on every call.
     *
     * @param invCumYProb inverse cumul of unconditional default prob
     * @param iName       desired name
     * @param m           value of LM independent factors
     */
    public final double conditionalDefaultProbabilityInvP(final double invCumYProb, final int iName, final double[] m) {
        QL.require(m.length == nFactors_, "factor count mismatch");
        double sumMs = 0.0;
        final List< Double > wRow = factorWeights_.get(iName);
        for ( int k = 0; k < nFactors_; ++k ) {
            sumMs += wRow.get(k) * m[k];
        }
        return cumulativeZ((invCumYProb - sumMs) / idiosyncFctrs_[iName]);
    }

    /**
     * Date-conditional default probability: looks up the unconditional probability from the basket pool / default key
     * for the given date, then delegates to {@link #conditionalDefaultProbability(double, int, double[])}.
     */
    public final double conditionalDefaultProbability(final Date date, final int iName, final double[] mktFactors) {
        QL.require(basket_ != null, "No portfolio basket set.");
        final Pool pool = basket_.pool();
        final String name = pool.names().get(iName);
        final double pDefUncond = pool.get(name).defaultProbability(basket_.defaultKeys().get(iName)).currentLink()
                .defaultProbability(date);
        return conditionalDefaultProbability(pDefUncond, iName, mktFactors);
    }

    /** Conditional default probability product, intermediate step in correlation. */
    public final double condProbProduct(final double invCumYProb1, final double invCumYProb2, final int iName1,
            final int iName2, final double[] mktFactors) {
        return conditionalDefaultProbabilityInvP(invCumYProb1, iName1, mktFactors) * conditionalDefaultProbabilityInvP(
                invCumYProb2, iName2, mktFactors);
    }

    /**
     * Computes the unconditional probability of default of a given name. Trivial method for testing.
     */
    public final double probOfDefault(final int iName, final Date d) {
        QL.require(basket_ != null, "No portfolio basket set.");
        final Pool pool = basket_.pool();
        final String name = pool.names().get(iName);
        final double pUncond = pool.get(name).defaultProbability(basket_.defaultKeys().get(iName)).currentLink()
                .defaultProbability(d);
        if ( pUncond < 1.0e-10 ) {
            return 0.0;
        }
        final double invY = inverseCumulativeY(pUncond, iName);
        return integratedExpectedValue((double[] v1) -> conditionalDefaultProbabilityInvP(invY, iName, v1));
    }

    /**
     * Pearsons' default probability correlation. Returns the correlation between two names' default events at date
     * {@code d}.
     */
    public final double defaultCorrelation(final Date d, final int iNamei, final int iNamej) {
        QL.require(basket_ != null, "No portfolio basket set.");
        final Pool pool = basket_.pool();
        final double pi = pool.get(pool.names().get(iNamei)).defaultProbability(basket_.defaultKeys().get(iNamei))
                .currentLink().defaultProbability(d);
        final double pj = pool.get(pool.names().get(iNamej)).defaultProbability(basket_.defaultKeys().get(iNamej))
                .currentLink().defaultProbability(d);
        final double pipj = pi * pj;
        final double invPi = inverseCumulativeY(pi, iNamei);
        final double invPj = inverseCumulativeY(pj, iNamej);
        final double e1i1j;
        if ( iNamei != iNamej ) {
            e1i1j = integratedExpectedValue((double[] v1) -> condProbProduct(invPi, invPj, iNamei, iNamej, v1));
        } else {
            e1i1j = pi;
        }
        return (e1i1j - pipj) / Math.sqrt(pipj * (1.0 - pi) * (1.0 - pj));
    }

    /**
     * Conditional probability of n default events or more in the basket portfolio at {@code date}, given the
     * realisation of the systemic factors {@code mktFactors}.
     *
     * <p>Mirrors C++ {@code DefaultLatentModel::conditionalProbAtLeastNEvents}.
     * Iterates through all subsets of size n via bit-masks; quadratic in pool size in the worst case (intended for
     * small NTD-style baskets).
     */
    public final double conditionalProbAtLeastNEvents(final int n, final Date date, final double[] mktFactors) {
        QL.require(basket_ != null, "No portfolio basket set.");
        final int poolSize = basket_.size();
        QL.require(poolSize <= 30, "conditionalProbAtLeastNEvents: pool too large for bitmask");
        final Pool pool = basket_.pool();

        final long limit = 1L << poolSize;
        final double[] pDefCond = new double[poolSize];
        for ( int i = 0; i < poolSize; ++i ) {
            final double pUncond = pool.get(pool.names().get(i)).defaultProbability(basket_.defaultKeys().get(i))
                    .currentLink().defaultProbability(date);
            pDefCond[i] = conditionalDefaultProbability(pUncond, i, mktFactors);
        }

        double probNEventsOrMore = 0.0;
        long mask = (1L << n) - 1L;
        long bitsSet = Long.bitCount(mask);
        long currentMask = mask;
        while ( currentMask < limit ) {
            if ( bitsSet >= n ) {
                double pConfig = 1.0;
                for ( int i = 0; i < poolSize; ++i ) {
                    final boolean defaulted = (currentMask & (1L << i)) != 0L;
                    pConfig *= defaulted ? pDefCond[i] : (1.0 - pDefCond[i]);
                }
                probNEventsOrMore += pConfig;
            }
            currentMask++;
            bitsSet = Long.bitCount(currentMask);
        }
        return probNEventsOrMore;
    }

    /**
     * Probability of having a given or larger number of defaults in the basket portfolio at {@code date}.
     */
    public final double probAtLeastNEvents(final int n, final Date date) {
        return integratedExpectedValue((double[] v1) -> conditionalProbAtLeastNEvents(n, date, v1));
    }

    @Override
    protected final LMIntegration integration() {
        return integration_;
    }

    /** Convenience adapter: the C++ kernels operate on {@code std::vector<Real>}. */
    private double[] copyOf(final List< Double > in) {
        final double[] out = new double[in.size()];
        for ( int i = 0; i < in.size(); ++i ) {
            out[i] = in.get(i);
        }
        return out;
    }

    /** Fluent overload for tests / callers that prefer arrays. */
    public final double conditionalDefaultProbability(final double prob, final int iName,
            final List< Double > mktFactors) {
        return conditionalDefaultProbability(prob, iName, copyOf(mktFactors));
    }

    /** Fluent overload mirroring the C++ {@code std::vector} signature. */
    public final double conditionalDefaultProbabilityInvP(final double invCumYProb, final int iName,
            final List< Double > m) {
        return conditionalDefaultProbabilityInvP(invCumYProb, iName, copyOf(m));
    }
}
