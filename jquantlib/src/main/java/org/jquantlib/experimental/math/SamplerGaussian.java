/*
 Copyright (C) 2015 Andres Hernandez
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
import org.jquantlib.experimental.math.HybridSimulatedAnnealing.Sampler;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.randomnumbers.BoxMullerGaussianRng;
import org.jquantlib.math.randomnumbers.MersenneTwisterUniformRng;
import org.jquantlib.math.randomnumbers.SeedGenerator;

/**
 * Gaussian {@link Sampler}: draws each component of {@code newPoint} from a normal distribution centred on
 * {@code currentPoint[i]} with standard deviation {@code sqrt(temp[i])}.
 *
 * <p>Java port of QuantLib v1.42.1 {@code SamplerGaussian} (in
 * {@code ql/experimental/math/hybridsimulatedannealingfunctors.hpp}). The C++ uses {@code std::mt19937} +
 * {@code std::normal_distribution<Real>}; this port uses {@link MersenneTwisterUniformRng} + {@link BoxMullerGaussianRng}
 * which is the closest in-library equivalent. Because of the algorithm difference the per-draw sequence does not match
 * C++ bit-for-bit, but the resulting empirical distribution is the same.
 *
 * <p>The parameter space must support the entire real line for each dimension.
 */
public final class SamplerGaussian implements Sampler {

    private final BoxMullerGaussianRng< MersenneTwisterUniformRng > rng_;

    public SamplerGaussian(final long seed) {
        this.rng_ = new BoxMullerGaussianRng<>(new MersenneTwisterUniformRng(seed));
    }

    public SamplerGaussian() {
        this(SeedGenerator.getInstance().get());
    }

    @Override
    public void sample(final Array newPoint, final Array currentPoint, final Array temp) {
        QL.require(newPoint.size() == currentPoint.size(), "Incompatible input");
        QL.require(newPoint.size() == temp.size(), "Incompatible input");
        for ( int i = 0; i < currentPoint.size(); ++i ) {
            newPoint.set(i, currentPoint.get(i) + Math.sqrt(temp.get(i)) * rng_.next().value());
        }
    }
}
