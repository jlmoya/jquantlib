/*
 Copyright (C) 2014 Jose Aparicio
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
 * Polar transformation Student-t random number generator.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/experimental/math/polarstudenttrng.hpp}.
 *
 * <p>See "Polar Generation of Random Variates With the t-Distribution",
 * Ralph W. Bailey, April 1994, in Mathematics of Computation, Vol 62-206,
 * page 779. The variant implemented here is from "Random Number Generation
 * and Monte Carlo Methods", Springer, 2003, page 185, which uses a
 * uniform RNG remapped to {@code [-1,1]} to avoid the explicit sign call.
 *
 * <p>Warning: do not use with a low-discrepancy sequence generator.
 */
public class PolarStudentTRng {

    private final RandomNumberGenerator uniformGenerator_;
    private final double degFreedom_;

    public PolarStudentTRng(final double degFreedom, final RandomNumberGenerator urng) {
        QL.require(degFreedom > 0.0, "Invalid degrees of freedom parameter.");
        this.uniformGenerator_ = urng;
        this.degFreedom_ = degFreedom;
    }

    /** Returns a Student-T sample with weight {@code 1.0}. */
    public Sample<Double> next() {
        double u;
        double v;
        double rSqr;
        do {
            // remap from (0,1) to (-1,1)
            v = 2.0 * uniformGenerator_.next().value() - 1.0;
            u = 2.0 * uniformGenerator_.next().value() - 1.0;
            rSqr = v * v + u * u;
        } while (rSqr >= 1.0);
        final double value = u
                * Math.sqrt(degFreedom_
                        * (Math.pow(rSqr, -2.0 / degFreedom_) - 1.0)
                        / rSqr);
        return new Sample<Double>(value, 1.0);
    }
}
