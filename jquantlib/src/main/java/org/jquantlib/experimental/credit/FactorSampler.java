/*
 Copyright (C) 2008 Roland Lichters
 Copyright (C) 2009, 2014 Jose Aparicio
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

import org.jquantlib.experimental.math.CopulaPolicy;
import org.jquantlib.math.randomnumbers.UniformRandomSequenceGenerator;
import org.jquantlib.methods.montecarlo.Sample;

/**
 * Samples factors {@code (M_k, Z_i)} for the Monte-Carlo path of a
 * {@link LatentModel}-based credit MC simulation. Generic implementation:
 * draws a uniform sequence from {@code rsg_} and inverts the copula's
 * cumulative distributions via {@link CopulaPolicy#allFactorCumulInverter}.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code LatentModel<TC>::FactorSampler<USNG, dummy>}
 * (declared in {@code ql/experimental/math/latentmodel.hpp}). Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>The C++ template specializes on the underlying generator type
 * (e.g. {@code BoxMullerGaussianRng}, {@code PolarStudentTRng}) to use a
 * native sampler bypassing inversion. In the Java port the
 * {@code UniformRandomSequenceGenerator} interface decouples the algorithm,
 * so client code can pass a Box-Muller / polar-T -based generator directly
 * if desired (Phase 4m.7b base implementation: generic copula-inversion path
 * only; specialized samplers may be added in a follow-up phase if profiling
 * shows them necessary).
 *
 * <p>Phase 4m.7b foundation.
 */
public final class FactorSampler<P extends CopulaPolicy> {

    private final UniformRandomSequenceGenerator rsg_;
    private final P copula_;

    /**
     * @param rsg     uniform sequence generator with dimension equal to
     *                {@code copula.numFactors()}
     * @param copula  copula policy providing {@code allFactorCumulInverter}
     */
    public FactorSampler(final UniformRandomSequenceGenerator rsg, final P copula) {
        if (rsg.dimension() != copula.numFactors()) {
            throw new IllegalArgumentException(
                    "FactorSampler: rsg dimension (" + rsg.dimension()
                    + ") must match copula.numFactors() (" + copula.numFactors() + ")");
        }
        this.rsg_ = rsg;
        this.copula_ = copula;
    }

    /** Number of factors per sample. */
    public int dimension() {
        return copula_.numFactors();
    }

    /**
     * Draw the next factor sample {@code (M_k, Z_i)} by inverting the copula
     * cumulative distributions of the next uniform sample.
     *
     * <p>Mirrors C++ {@code FactorSampler::nextSequence()}:
     * <pre>
     * sample = sequenceGen_.nextSequence();
     * x_.value = copula_.allFactorCumulInverter(sample.value);
     * return x_;
     * </pre>
     */
    public Sample<double[]> nextSequence() {
        final Sample<double[]> u = rsg_.nextSequence();
        final double[] inv = copula_.allFactorCumulInverter(u.value());
        return new Sample<>(inv, u.weight());
    }
}
