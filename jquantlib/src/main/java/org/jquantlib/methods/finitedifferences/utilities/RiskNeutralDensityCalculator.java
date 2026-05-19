/*
 Copyright (C) 2015 Johannes Göttker-Schnetmann
 Copyright (C) 2015 Klaus Spanderen
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

package org.jquantlib.methods.finitedifferences.utilities;

import org.jquantlib.math.Ops;
import org.jquantlib.math.solvers1D.Brent;

/**
 * Interface for a single-asset risk-neutral terminal density calculator.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/methods/finitedifferences/utilities/riskneutraldensitycalculator.{hpp,cpp}}.
 *
 * <p>Mirrors the C++ abstract base + nested {@code InvCDFHelper}; the
 * {@link InvCDFHelper#inverseCDF(double, double)} method uses Brent root-finding to invert {@link #cdf(double, double)}
 * starting from a caller-supplied guess.
 *
 * <p>NOTE: We use a Java {@code abstract class} rather than an interface so we can
 * embed the protected nested {@code InvCDFHelper} (matching C++) without forcing it onto the public API. Subclasses
 * extend this class.
 *
 * @author Phase 5h.5-RND port
 */
public abstract class RiskNeutralDensityCalculator {

    /** Probability density at log-spot/spot {@code x} and time {@code t}. */
    public abstract double pdf(final double x, final double t);

    /** Cumulative distribution at {@code x} and time {@code t}. */
    public abstract double cdf(final double x, final double t);

    /** Inverse CDF (quantile): returns {@code x} such that CDF(x, t) = q. */
    public abstract double invcdf(final double q, final double t);

    /**
     * Helper to invert the CDF via Brent root-finding around a starting guess. Mirrors C++
     * {@code RiskNeutralDensityCalculator::InvCDFHelper}.
     */
    protected static class InvCDFHelper {
        private final RiskNeutralDensityCalculator calculator_;
        private final double guess_;
        private final double accuracy_;
        private final int maxEvaluations_;
        private final double stepSize_;

        public InvCDFHelper(final RiskNeutralDensityCalculator calculator, final double guess, final double accuracy,
                final int maxEvaluations) {
            this(calculator, guess, accuracy, maxEvaluations, 0.01);
        }

        public InvCDFHelper(final RiskNeutralDensityCalculator calculator, final double guess, final double accuracy,
                final int maxEvaluations, final double stepSize) {
            this.calculator_ = calculator;
            this.guess_ = guess;
            this.accuracy_ = accuracy;
            this.maxEvaluations_ = maxEvaluations;
            this.stepSize_ = stepSize;
        }

        public double inverseCDF(final double p, final double t) {
            final Brent solver = new Brent();
            solver.setMaxEvaluations(maxEvaluations_);
            final Ops.DoubleOp f = new Ops.DoubleOp() {
                @Override
                public double op(final double x) {
                    return calculator_.cdf(x, t) - p;
                }
            };
            return solver.solve(f, accuracy_, guess_, stepSize_);
        }
    }
}
