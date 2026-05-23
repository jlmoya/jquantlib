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
import org.jquantlib.experimental.math.HybridSimulatedAnnealing.Temperature;
import org.jquantlib.math.matrixutilities.Array;

/**
 * Boltzmann logarithmic cooling: {@code T_i(k) = T0_i / log(k_i)}. For use with {@link SamplerGaussian}.
 *
 * <p>Java port of QuantLib v1.42.1 {@code TemperatureBoltzmann}.
 */
public final class TemperatureBoltzmann implements Temperature {

    private final Array initialTemp_;

    public TemperatureBoltzmann(final double initialTemp, final int dimension) {
        this.initialTemp_ = new Array(dimension, initialTemp, 0.0);
    }

    @Override
    public void update(final Array newTemp, final Array currTemp, final Array steps) {
        QL.require(currTemp.size() == initialTemp_.size(), "Incompatible input");
        QL.require(currTemp.size() == newTemp.size(), "Incompatible input");
        for ( int i = 0; i < initialTemp_.size(); ++i ) {
            newTemp.set(i, initialTemp_.get(i) / Math.log(steps.get(i)));
        }
    }
}
