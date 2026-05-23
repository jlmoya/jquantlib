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
 * Very-Fast-Annealing temperature schedule:
 * {@code T_i(k) = T0_i * exp(-c_i * k_i^{1/N})} with {@code c_i = -log(finalTemp_i / initialTemp_i) * maxSteps^{-1/N}}.
 * Paired with {@link SamplerVeryFastAnnealing}.
 *
 * <p>Java port of QuantLib v1.42.1 {@code TemperatureVeryFastAnnealing}.
 */
public final class TemperatureVeryFastAnnealing implements Temperature {

    private final double inverseN_;
    private final Array initialTemp_;
    private final Array exponent_;

    public TemperatureVeryFastAnnealing(final double initialTemp, final double finalTemp, final double maxSteps,
            final int dimension) {
        this.inverseN_ = 1.0 / dimension;
        this.initialTemp_ = new Array(dimension, initialTemp, 0.0);
        this.exponent_ = new Array(dimension);
        final double coeff = Math.pow(maxSteps, -inverseN_);
        for ( int i = 0; i < initialTemp_.size(); ++i ) {
            this.exponent_.set(i, -Math.log(finalTemp / initialTemp) * coeff);
        }
    }

    @Override
    public void update(final Array newTemp, final Array currTemp, final Array steps) {
        QL.require(currTemp.size() == initialTemp_.size(), "Incompatible input");
        QL.require(currTemp.size() == newTemp.size(), "Incompatible input");
        for ( int i = 0; i < initialTemp_.size(); ++i ) {
            newTemp.set(i, initialTemp_.get(i) * Math.exp(-exponent_.get(i) * Math.pow(steps.get(i), inverseN_)));
        }
    }
}
