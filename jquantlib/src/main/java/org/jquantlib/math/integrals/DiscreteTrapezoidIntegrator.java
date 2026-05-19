/*
 Copyright (C) 2014 Klaus Spanderen
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

package org.jquantlib.math.integrals;

import org.jquantlib.math.Constants;
import org.jquantlib.math.Ops;

/**
 * Fixed-grid trapezoidal-rule integrator on a uniform grid.
 *
 * <p>Java port of v1.42.1
 * {@code ql/math/integrals/discreteintegrals.{hpp,cpp} :: DiscreteTrapezoidIntegrator}. Samples the integrand at
 * {@code n = maxEvaluations - 1} equally-spaced sub-intervals between {@code a} and {@code b}, then applies the
 * composite trapezoidal rule
 * <pre>
 *   d * ( 0.5*f(a) + f(a+d) + f(a+2d) + ... + f(a+(n-1)d) + 0.5*f(b) )
 * </pre>
 * where {@code d = (b - a)/n}.
 *
 * <p>Unlike {@link TrapezoidIntegral} this is a fixed-budget integrator: it
 * always consumes exactly {@code maxEvaluations} integrand evaluations, regardless of {@code absoluteAccuracy}. Used by
 * {@code AnalyticHestonEngine.Integration.discreteTrapezoid(n)} to mirror C++.
 *
 * <p>Reference: Levy, D. <i>Numerical Integration</i>
 * (https://www2.math.umd.edu/~dlevy/classes/amsc466/lecture-notes/integration-chap.pdf).
 *
 * @author Phase 5e.5b-CFC-d-136 port
 */
public class DiscreteTrapezoidIntegrator extends Integrator {

    /**
     * @param evaluations number of integrand samples (equals {@link #maxEvaluations()}); must be {@code >= 2}.
     */
    public DiscreteTrapezoidIntegrator(final int evaluations) {
        // C++ passes Null<Real>() as absoluteAccuracy. The Java Integrator
        // base class enforces accuracy > QL_EPSILON; NULL_REAL (Double.MAX_VALUE)
        // trivially satisfies that, and the discrete trapezoid rule does not
        // use absoluteAccuracy at all (fixed-budget).
        super(Constants.NULL_REAL, evaluations);
    }

    @Override
    protected double integrate(final Ops.DoubleOp f, double a, final double b) {
        // C++ uses `Size n = maxEvaluations() - 1`. With evaluations=1 the
        // step `d = (b-a)/0` would NaN; in practice callers always pass >= 2.
        final int n = maxEvaluations() - 1;
        final double d = (b - a) / n;

        double sum = f.op(a) * 0.5;

        for ( int i = 0; i < n - 1; ++i ) {
            a += d;
            sum += f.op(a);
        }
        sum += f.op(b) * 0.5;

        increaseNumberOfEvaluations(maxEvaluations());

        return d * sum;
    }
}
