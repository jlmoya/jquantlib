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

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.math.integrals;

import org.jquantlib.math.Constants;
import org.jquantlib.math.Ops;

/**
 * Fixed-grid composite Simpson's rule {@link Integrator} on a uniform grid.
 *
 * <p>Faithful Java port of {@code QuantLib::DiscreteSimpsonIntegrator}
 * (v1.42.1 {@code ql/math/integrals/discreteintegrals.{hpp,cpp}}, pinned
 * commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}). Always consumes
 * exactly {@code maxEvaluations} integrand samples regardless of the
 * requested accuracy.
 *
 * <p>Sibling of {@link DiscreteTrapezoidIntegrator}, complementing the existing
 * {@link DiscreteSimpsonIntegral} functor form.
 *
 * <p>Phase 2 L1-D port.
 */
public class DiscreteSimpsonIntegrator extends Integrator {

    public DiscreteSimpsonIntegrator(final int evaluations) {
        super(Constants.NULL_REAL, evaluations);
    }

    @Override
    protected double integrate(final Ops.DoubleOp f, final double a, final double b) {
        // Mirrors C++ DiscreteSimpsonIntegrator::integrate
        final int n = maxEvaluations() - 1;
        final double d = (b - a) / n;
        final double d2 = d * 2.0;

        double sum = 0.0;
        double x = a + d;
        for ( int i = 1; i < n; i += 2 ) {
            sum += f.op(x);
            x += d2;
        }
        sum *= 2.0;

        x = a + d2;
        for ( int i = 2; i < n - 1; i += 2 ) {
            sum += f.op(x);
            x += d2;
        }
        sum *= 2.0;

        sum += f.op(a);
        if ( (n & 1) != 0 ) {
            sum += 1.5 * f.op(b) + 2.5 * f.op(b - d);
        } else {
            sum += f.op(b);
        }

        increaseNumberOfEvaluations(maxEvaluations());
        return d / 3.0 * sum;
    }
}
