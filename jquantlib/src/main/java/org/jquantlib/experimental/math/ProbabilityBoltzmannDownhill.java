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

import org.jquantlib.experimental.math.HybridSimulatedAnnealing.Probability;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.randomnumbers.MersenneTwisterUniformRng;
import org.jquantlib.math.randomnumbers.SeedGenerator;

/**
 * Boltzmann acceptance rule with a "downhill" short-circuit: if {@code newValue < currentValue} the point is always
 * accepted; otherwise the standard Boltzmann probability is used.
 *
 * <p>Java port of QuantLib v1.42.1 {@code ProbabilityBoltzmannDownhill}.
 */
public final class ProbabilityBoltzmannDownhill implements Probability {

    private final MersenneTwisterUniformRng generator_;

    public ProbabilityBoltzmannDownhill(final long seed) {
        this.generator_ = new MersenneTwisterUniformRng(seed);
    }

    public ProbabilityBoltzmannDownhill() {
        this(SeedGenerator.getInstance().get());
    }

    @Override
    public boolean accept(final double currentValue, final double newValue, final Array temp) {
        if ( newValue < currentValue ) {
            return true;
        }
        double tMax = temp.get(0);
        for ( int i = 1; i < temp.size(); ++i ) {
            if ( temp.get(i) > tMax ) {
                tMax = temp.get(i);
            }
        }
        final double u = generator_.next().value();
        return (1.0 / (1.0 + Math.exp((newValue - currentValue) / tMax))) > u;
    }
}
