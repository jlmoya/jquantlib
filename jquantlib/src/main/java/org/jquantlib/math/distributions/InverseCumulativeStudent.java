/*
 Copyright (C) 2008 Roland Lichters
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

package org.jquantlib.math.distributions;

import org.jquantlib.QL;
import org.jquantlib.math.Ops;

/**
 * Inverse cumulative Student t-distribution.
 *
 * <p>Java port of QuantLib v1.42.1 {@code QuantLib::InverseCumulativeStudent}
 * ({@code ql/math/distributions/studenttdistribution.{hpp,cpp}}).
 *
 * <p>Newton-Raphson iteration on the cumulative-CDF / density pair.
 */
public class InverseCumulativeStudent implements Ops.DoubleOp {

    private final StudentDistribution d_;
    private final CumulativeStudentDistribution f_;
    private final double accuracy_;
    private final int maxIterations_;

    public InverseCumulativeStudent(final int n) {
        this(n, 1.0e-6, 50);
    }

    public InverseCumulativeStudent(final int n, final double accuracy, final int maxIterations) {
        this.d_ = new StudentDistribution(n);
        this.f_ = new CumulativeStudentDistribution(n);
        this.accuracy_ = accuracy;
        this.maxIterations_ = maxIterations;
    }

    @Override
    public double op(final double y) {
        QL.require(y >= 0 && y <= 1, "argument out of range [0, 1]");

        double x = 0;
        int count = 0;
        do {
            x -= (f_.op(x) - y) / d_.op(x);
            count++;
        } while ( Math.abs(f_.op(x) - y) > accuracy_ && count < maxIterations_ );

        QL.require(count < maxIterations_,
                "maximum number of iterations " + maxIterations_ + " reached in InverseCumulativeStudent, y=" + y
                        + ", x=" + x);
        return x;
    }
}
