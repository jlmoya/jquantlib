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
 * Gaussian {@link Sampler} reflected at upper/lower bounds: out-of-bounds draws are mirrored back into the box.
 *
 * <p>Java port of QuantLib v1.42.1 {@code SamplerMirrorGaussian}.
 */
public final class SamplerMirrorGaussian implements Sampler {

    private final Array lower_;
    private final Array upper_;
    private final BoxMullerGaussianRng< MersenneTwisterUniformRng > rng_;

    public SamplerMirrorGaussian(final Array lower, final Array upper, final long seed) {
        QL.require(lower.size() == upper.size(), "Incompatible input");
        this.lower_ = lower.clone();
        this.upper_ = upper.clone();
        this.rng_ = new BoxMullerGaussianRng<>(new MersenneTwisterUniformRng(seed));
    }

    public SamplerMirrorGaussian(final Array lower, final Array upper) {
        this(lower, upper, SeedGenerator.getInstance().get());
    }

    @Override
    public void sample(final Array newPoint, final Array currentPoint, final Array temp) {
        QL.require(newPoint.size() == currentPoint.size(), "Incompatible input");
        QL.require(newPoint.size() == temp.size(), "Incompatible input");
        for ( int i = 0; i < currentPoint.size(); ++i ) {
            double v = currentPoint.get(i) + Math.sqrt(temp.get(i)) * rng_.next().value();
            while ( v < lower_.get(i) || v > upper_.get(i) ) {
                if ( v < lower_.get(i) ) {
                    v = lower_.get(i) + lower_.get(i) - v;
                } else {
                    v = upper_.get(i) + upper_.get(i) - v;
                }
            }
            newPoint.set(i, v);
        }
    }
}
