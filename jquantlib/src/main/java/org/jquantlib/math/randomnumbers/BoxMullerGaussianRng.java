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
 * Gaussian random-number generator using the Box-Muller polar transformation.
 * <p>
 * Faithful Java port of QuantLib v1.42.1 {@code ql/math/randomnumbers/boxmullergaussianrng.hpp}.
 * Generates pairs of independent N(0,1) samples from uniform pairs in {@code (-1,1)^2}, retrying
 * draws falling outside the unit disk. Caches the second sample of each pair.
 * <p>
 * <b>Cross-validation:</b> with {@link MersenneTwisterUniformRng} seeded at 42, the first 8 outputs
 * match the C++ reference to 1e-15 (see {@code BoxMullerGaussianRngTest}).
 *
 * @author Jose Moya
 */
public final class BoxMullerGaussianRng< RNG extends RandomNumberGenerator > {

    private final RNG uniformGenerator_;
    private boolean returnFirst_ = true;
    private double firstValue_;
    private double secondValue_;
    private double firstWeight_;
    private double secondWeight_;
    private double weight_ = 0.0;

    public BoxMullerGaussianRng(final RNG uniformGenerator) {
        this.uniformGenerator_ = uniformGenerator;
    }

    /** @return a Gaussian sample (mean 0, std-dev 1) with caller-aggregated weight. */
    public Sample< Double > next() {
        if ( returnFirst_ ) {
            double x1, x2, r, ratio;
            do {
                final Sample< Double > s1 = uniformGenerator_.next();
                x1 = s1.value() * 2.0 - 1.0;
                firstWeight_ = s1.weight();
                final Sample< Double > s2 = uniformGenerator_.next();
                x2 = s2.value() * 2.0 - 1.0;
                secondWeight_ = s2.weight();
                r = x1 * x1 + x2 * x2;
            } while ( r >= 1.0 || r == 0.0 );

            ratio = Math.sqrt(-2.0 * Math.log(r) / r);
            firstValue_ = x1 * ratio;
            secondValue_ = x2 * ratio;
            weight_ = firstWeight_ * secondWeight_;

            returnFirst_ = false;
            return new Sample< Double >(firstValue_, weight_);
        } else {
            returnFirst_ = true;
            return new Sample< Double >(secondValue_, weight_);
        }
    }
}
