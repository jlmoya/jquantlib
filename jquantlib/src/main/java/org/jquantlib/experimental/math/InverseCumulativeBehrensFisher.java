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

import java.util.List;

import org.jquantlib.math.Ops;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.solvers1D.Brent;

/**
 * Inverse of the cumulative of the convolution of odd-T distributions.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/experimental/math/convolvedstudentt.{hpp,cpp}}.
 *
 * <p>Finds the inverse through a Brent root solver. The convolved distribution
 * is bounded above by the normalised gaussian; symmetric in {@code x}.
 */
public class InverseCumulativeBehrensFisher {

    private static final InverseCumulativeNormal INV_CN = new InverseCumulativeNormal();

    private final double normSqr_;
    private final double accuracy_;
    private final CumulativeBehrensFisher distrib_;

    public InverseCumulativeBehrensFisher(final List<Integer> degreesFreedom,
                                          final List<Double> factors) {
        this(degreesFreedom, factors, 1.0e-6);
    }

    public InverseCumulativeBehrensFisher(final List<Integer> degreesFreedom,
                                          final List<Double> factors,
                                          final double accuracy) {
        double n2 = 0.0;
        for (final Double v : factors) {
            n2 += v * v;
        }
        this.normSqr_ = n2;
        this.accuracy_ = accuracy;
        this.distrib_ = new CumulativeBehrensFisher(degreesFreedom, factors);
    }

    /** Returns the cumulative inverse value for cumulative probability {@code q}. */
    public double op(final double q) {
        if (q == 0.5) {
            return 0.0;
        }
        final double sign;
        final double effectiveq;
        if (q < 0.5) {
            sign = -1.0;
            effectiveq = 1.0 - q;
        } else {
            sign = 1.0;
            effectiveq = q;
        }
        final double xMin = INV_CN.op(effectiveq) * normSqr_;
        // Brent fails at the bounds-check if this is not enough; q very close
        // to 1 in a bad combination breaks around 1 - 1e-7
        final double xMax = 1.0e6;
        final Ops.DoubleOp f = new Ops.DoubleOp() {
            @Override
            public double op(final double x) {
                return distrib_.op(x) - effectiveq;
            }
        };
        final Brent solver = new Brent();
        return sign * solver.solve(f, accuracy_, (xMin + xMax) / 2.0, xMin, xMax);
    }
}
