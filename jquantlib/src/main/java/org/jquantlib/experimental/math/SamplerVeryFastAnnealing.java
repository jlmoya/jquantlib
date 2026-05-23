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
 * Very-Fast-Annealing {@link Sampler}. Designed for use with {@link TemperatureVeryFastAnnealing}; requires the
 * parameter space to be bounded above and below.
 *
 * <p>Java port of QuantLib v1.42.1 {@code SamplerVeryFastAnnealing}. The
 * draw rule is
 * {@code y = sign · T · ((1 + 1/T)^|2u-1| - 1)}, {@code x_new = x + y·(upper - lower)},
 * rejecting any out-of-bounds draw.
 */
public final class SamplerVeryFastAnnealing implements Sampler {

    private final Array lower_;
    private final Array upper_;
    private final MersenneTwisterUniformRng generator_;

    public SamplerVeryFastAnnealing(final Array lower, final Array upper, final long seed) {
        QL.require(lower.size() == upper.size(), "Incompatible input");
        this.lower_ = lower.clone();
        this.upper_ = upper.clone();
        this.generator_ = new MersenneTwisterUniformRng(seed);
    }

    public SamplerVeryFastAnnealing(final Array lower, final Array upper) {
        this(lower, upper, SeedGenerator.getInstance().get());
    }

    @Override
    public void sample(final Array newPoint, final Array currentPoint, final Array temp) {
        QL.require(newPoint.size() == currentPoint.size(), "Incompatible input");
        QL.require(newPoint.size() == lower_.size(), "Incompatible input");
        QL.require(newPoint.size() == temp.size(), "Incompatible input");
        for ( int i = 0; i < currentPoint.size(); ++i ) {
            double v = lower_.get(i) - 1.0;
            while ( v < lower_.get(i) || v > upper_.get(i) ) {
                final double draw = generator_.next().value();
                final double sign = (0.5 < draw ? 1.0 : 0.0) - (draw < 0.5 ? 1.0 : 0.0);
                final double y = sign * temp.get(i) * (Math.pow(1.0 + 1.0 / temp.get(i), Math.abs(2.0 * draw - 1.0))
                        - 1.0);
                v = currentPoint.get(i) + y * (upper_.get(i) - lower_.get(i));
            }
            newPoint.set(i, v);
        }
    }
}
