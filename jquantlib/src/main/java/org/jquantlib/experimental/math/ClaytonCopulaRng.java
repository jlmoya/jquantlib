/*
 Copyright (C) 2010 Hachemi Benyahia
 Copyright (C) 2010 DeriveXperts SAS
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
import org.jquantlib.math.randomnumbers.RandomNumberGenerator;
import org.jquantlib.methods.montecarlo.Sample;

/**
 * Clayton copula random-number generator.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/experimental/math/claytoncopularng.hpp}.
 *
 * <p>Given a uniform RNG, generates a 2-tuple {@code (u1, u2)} sampled from
 * the bivariate Clayton copula
 * <pre>
 *   C_theta(u1, u2) = max((u1^{-theta} + u2^{-theta} - 1)^{-1/theta}, 0)
 * </pre>
 * via the conditional inversion technique. The conditional CDF reads
 * {@code F(u2|u1) = u1^{-(theta+1)} (u1^{-theta} + u2^{-theta} - 1)^{-(1+1/theta)}}.
 */
public final class ClaytonCopulaRng {

    private final RandomNumberGenerator uniformGenerator_;
    private final double theta_;

    public ClaytonCopulaRng(final RandomNumberGenerator uniformGenerator, final double theta) {
        QL.require(theta >= -1.0, "theta (" + theta + ") must be greater or equal to -1");
        QL.require(theta != 0.0, "theta (" + theta + ") must be different from 0");
        this.uniformGenerator_ = uniformGenerator;
        this.theta_ = theta;
    }

    /** Returns a 2-dim sample drawn from the Clayton copula. */
    public Sample<double[]> next() {
        final Sample<Double> v1 = uniformGenerator_.next();
        final Sample<Double> v2 = uniformGenerator_.next();
        final double u1 = v1.value();
        final double u2 = Math.pow(
                Math.pow(v1.value(), -theta_)
                        * (Math.pow(v2.value(), -theta_ / (theta_ + 1.0)) - 1.0)
                        + 1.0,
                -1.0 / theta_);
        return new Sample<double[]>(new double[] { u1, u2 }, v1.weight() * v2.weight());
    }
}
