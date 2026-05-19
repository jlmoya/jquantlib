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

package org.jquantlib.experimental.math;

import org.jquantlib.QL;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.distributions.NormalDistribution;

import java.util.List;

/**
 * Gaussian Latent Model's copula policy.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/experimental/math/gaussiancopulapolicy.{hpp,cpp}}.
 *
 * <p>Its simplicity is a result of the convolution stability of the Gaussian
 * distribution.
 */
public class GaussianCopulaPolicy implements CopulaPolicy {

    private static final NormalDistribution DENSITY = new NormalDistribution();
    private static final CumulativeNormalDistribution CUMULATIVE = new CumulativeNormalDistribution();
    private static final InverseCumulativeNormal INV_CUMULATIVE = new InverseCumulativeNormal();
    private final int numFactors_;

    public GaussianCopulaPolicy() {
        this.numFactors_ = 0;
    }

    public GaussianCopulaPolicy(final List< List< Double > > factorWeights) {
        this(factorWeights, new InitTraits());
    }

    public GaussianCopulaPolicy(final List< List< Double > > factorWeights, final InitTraits dummy) {
        QL.require(factorWeights != null && !factorWeights.isEmpty(), "factorWeights must contain at least one row");
        // Check factors are normalised: inner product < 1
        for ( final List< Double > row : factorWeights ) {
            double norm = 0.0;
            for ( final Double v : row ) {
                norm += v * v;
            }
            QL.require(norm < 1.0, "Non normal random factor combination.");
        }
        this.numFactors_ = factorWeights.size() + factorWeights.get(0).size();
    }

    /** Number of independent random factors. */
    public int numFactors() {
        return numFactors_;
    }

    /** Returns a copy of the initialisation arguments. */
    public InitTraits getInitTraits() {
        return new InitTraits();
    }

    /**
     * Cumulative probability of a given latent variable.
     *
     * @param val       argument
     * @param iVariable index of the requested variable (ignored for Gaussian)
     */
    public double cumulativeY(final double val, final int iVariable) {
        return CUMULATIVE.op(val);
    }

    /** Cumulative probability of the idiosyncratic factor. */
    public double cumulativeZ(final double z) {
        return CUMULATIVE.op(z);
    }

    /**
     * Probability density of a given realisation of values of the systemic factors. In the Gaussian case all factors
     * share the same law so this is a trivial product.
     */
    public double density(final List< Double > m) {
        double prod = 1.0;
        for ( final Double v : m ) {
            prod *= DENSITY.op(v);
        }
        return prod;
    }

    /** Inverse of the cumulative distribution of the modelled latent variable. */
    public double inverseCumulativeY(final double p, final int iVariable) {
        return INV_CUMULATIVE.op(p);
    }

    /** Inverse of the cumulative distribution of the idiosyncratic factor. */
    public double inverseCumulativeZ(final double p) {
        return INV_CUMULATIVE.op(p);
    }

    /** Inverse of the cumulative distribution of the systemic factor iFactor. */
    public double inverseCumulativeDensity(final double p, final int iFactor) {
        return INV_CUMULATIVE.op(p);
    }

    /**
     * Maps a vector of uniform variates to the underlying factor distribution via inverse-cumulative transformation. To
     * use this version the generator must be a uniform one.
     */
    public double[] allFactorCumulInverter(final double[] probs) {
        final double[] result = new double[probs.length];
        for ( int i = 0; i < probs.length; ++i ) {
            result[i] = INV_CUMULATIVE.op(probs[i]);
        }
        return result;
    }

    /**
     * Initialisation traits placeholder. The Gaussian copula policy does not carry any extra parameters beyond the
     * factor weights, but a placeholder keeps the API symmetric with {@link TCopulaPolicy}.
     */
    public static final class InitTraits {
        public InitTraits() {
        }
    }
}
