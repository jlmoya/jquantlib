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
 * Log-normal {@link Sampler}: multiplies each component by {@code exp(sqrt(temp[i]) * z)} where {@code z ~ N(0,1)}.
 * Parameter space must be on the positive real line.
 *
 * <p>Java port of QuantLib v1.42.1 {@code SamplerLogNormal}.
 */
public final class SamplerLogNormal implements Sampler {

    private final BoxMullerGaussianRng< MersenneTwisterUniformRng > rng_;

    public SamplerLogNormal(final long seed) {
        this.rng_ = new BoxMullerGaussianRng<>(new MersenneTwisterUniformRng(seed));
    }

    public SamplerLogNormal() {
        this(SeedGenerator.getInstance().get());
    }

    @Override
    public void sample(final Array newPoint, final Array currentPoint, final Array temp) {
        QL.require(newPoint.size() == currentPoint.size(), "Incompatible input");
        QL.require(newPoint.size() == temp.size(), "Incompatible input");
        for ( int i = 0; i < currentPoint.size(); ++i ) {
            newPoint.set(i, currentPoint.get(i) * Math.exp(Math.sqrt(temp.get(i)) * rng_.next().value()));
        }
    }
}
