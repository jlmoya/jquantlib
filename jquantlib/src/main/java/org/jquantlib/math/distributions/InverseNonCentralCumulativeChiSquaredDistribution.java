/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.math.distributions;

import org.jquantlib.math.Ops;
import org.jquantlib.math.solvers1D.Brent;

/**
 * Inverse non-central chi-squared cumulative distribution.
 * <p>
 * Direct port of {@code QuantLib::InverseNonCentralCumulativeChiSquareDistribution}
 * (v1.42.1, ql/math/distributions/chisquaredistribution.{hpp,cpp}). Uses a
 * Brent root-finder against {@link NonCentralCumulativeChiSquaredDistribution}.
 * <p>
 * The constructor mirrors v1.42.1's defaults: {@code maxEvaluations = 10}
 * and {@code accuracy = 1e-8}. These are intentionally tight bounds and
 * may need to be raised for large {@code ncp} (the upper-bracket search
 * doubles the guess until the CDF reaches the target, consuming
 * evaluations from the same budget that Brent later uses).
 */
public class InverseNonCentralCumulativeChiSquaredDistribution implements Ops.DoubleOp {

    private final NonCentralCumulativeChiSquaredDistribution nonCentralDist_;
    private final double guess_;
    private final int maxEvaluations_;
    private final double accuracy_;

    public InverseNonCentralCumulativeChiSquaredDistribution(final double df, final double ncp) {
        this(df, ncp, 10, 1.0e-8);
    }

    public InverseNonCentralCumulativeChiSquaredDistribution(
            final double df, final double ncp,
            final int maxEvaluations, final double accuracy) {
        this.nonCentralDist_ = new NonCentralCumulativeChiSquaredDistribution(df, ncp);
        this.guess_ = df + ncp;
        this.maxEvaluations_ = maxEvaluations;
        this.accuracy_ = accuracy;
    }

    @Override
    public double op(final double x) {
        // First, find the right side of the interval. Mirrors C++:
        //   while (nonCentralDist_(upper) < x && evaluations > 0) { upper*=2; --evaluations; }
        double upper = guess_;
        int evaluations = maxEvaluations_;
        while (nonCentralDist_.op(upper) < x && evaluations > 0) {
            upper *= 2.0;
            --evaluations;
        }

        final boolean noBracketExpansion = (evaluations == maxEvaluations_);
        // C++ then runs a Brent solver on f(y) = nonCentralDist_(y) - x,
        // with maxEvaluations set to the *remaining* budget,
        // accuracy = accuracy_, guess = 0.75*upper, and
        // bracket = [(noBracketExpansion ? 0.0 : 0.5*upper), upper].
        final Brent solver = new Brent();
        solver.setMaxEvaluations(evaluations);
        final double xMin = noBracketExpansion ? 0.0 : 0.5 * upper;
        final double xMax = upper;
        final double guess = 0.75 * upper;
        final Ops.DoubleOp f = new Ops.DoubleOp() {
            @Override
            public double op(final double y) {
                return nonCentralDist_.op(y) - x;
            }
        };
        return solver.solve(f, accuracy_, guess, xMin, xMax);
    }
}
