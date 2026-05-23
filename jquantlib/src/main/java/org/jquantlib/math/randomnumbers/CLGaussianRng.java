/*
 Copyright (C) 2026 Jose Moya

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

package org.jquantlib.math.randomnumbers;

import org.jquantlib.methods.montecarlo.Sample;

/**
 * Central-limit Gaussian random-number generator.
 * <p>
 * Faithful Java port of QuantLib v1.42.1 {@code ql/math/randomnumbers/centrallimitgaussianrng.hpp}.
 * Returns the sum of 12 uniform draws in (0,1) minus 6 — an approximation to N(0,1) via the
 * Central Limit Theorem (the sum of 12 U[0,1) variates has mean 6 and variance 1).
 * <p>
 * <b>Cross-validation:</b> with {@link MersenneTwisterUniformRng} seeded at 42, the first 6 outputs
 * match the C++ reference to 1e-15 (see {@code CLGaussianRngTest}).
 *
 * @author Jose Moya
 */
public final class CLGaussianRng< RNG extends RandomNumberGenerator > {

    private final RNG uniformGenerator_;

    public CLGaussianRng(final RNG uniformGenerator) {
        this.uniformGenerator_ = uniformGenerator;
    }

    /** @return a Gaussian sample (mean 0, std-dev 1 by CLT). */
    public Sample< Double > next() {
        double gaussPoint = -6.0;
        double gaussWeight = 1.0;
        for ( int i = 1; i <= 12; ++i ) {
            final Sample< Double > sample = uniformGenerator_.next();
            gaussPoint += sample.value();
            gaussWeight *= sample.weight();
        }
        return new Sample< Double >(gaussPoint, gaussWeight);
    }
}
