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
import org.jquantlib.math.randomnumbers.MersenneTwisterUniformRng;
import org.jquantlib.math.randomnumbers.SeedGenerator;

/**
 * Cauchy {@link Sampler}: draws from a Cauchy(0,1) distribution scaled by {@code temp[i]}. Often faster than the
 * Gaussian sampler at low dimensionality when paired with {@link TemperatureCauchy}.
 *
 * <p>Java port of QuantLib v1.42.1 {@code SamplerCauchy}. The Cauchy quantile
 * {@code F⁻¹(u) = tan(π (u - 0.5))} is applied to a uniform sample to obtain the variate.
 */
public class SamplerCauchy implements Sampler {

    protected final MersenneTwisterUniformRng generator_;

    public SamplerCauchy(final long seed) {
        this.generator_ = new MersenneTwisterUniformRng(seed);
    }

    public SamplerCauchy() {
        this(SeedGenerator.getInstance().get());
    }

    /** Cauchy(0,1) variate via inverse-CDF on a uniform {@code u in (0,1)}. */
    protected double nextCauchy() {
        final double u = generator_.next().value();
        return Math.tan(Math.PI * (u - 0.5));
    }

    @Override
    public void sample(final Array newPoint, final Array currentPoint, final Array temp) {
        QL.require(newPoint.size() == currentPoint.size(), "Incompatible input");
        QL.require(newPoint.size() == temp.size(), "Incompatible input");
        for ( int i = 0; i < currentPoint.size(); ++i ) {
            newPoint.set(i, currentPoint.get(i) + temp.get(i) * nextCauchy());
        }
    }
}
