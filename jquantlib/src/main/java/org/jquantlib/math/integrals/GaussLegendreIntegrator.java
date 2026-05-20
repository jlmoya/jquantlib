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
package org.jquantlib.math.integrals;

import org.jquantlib.math.Constants;
import org.jquantlib.math.Ops;

/**
 * Gauss-Legendre quadrature {@link Integrator} wrapper that maps the canonical
 * {@code [-1, 1]} {@link GaussLegendreIntegration} domain to an arbitrary
 * {@code [a, b]} via the standard linear change of variables
 * {@code x = c1*t + c2} with {@code c1 = 0.5*(b-a)}, {@code c2 = 0.5*(a+b)}.
 *
 * <p>Phase 1 closure A1 port of {@code QuantLib::GaussLegendreIntegrator}
 * (v1.42.1 ql/math/integrals/gaussianquadratures.{hpp,cpp}). Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>C++ defines this via the {@code detail::GaussianQuadratureIntegrator<>}
 * class template; Java erases the template into a thin concrete class
 * exposing the underlying {@link GaussLegendreIntegration} via
 * {@link #getIntegration()} (matching C++ {@code getIntegration()} accessor
 * used by {@code QdFp*} engines to extract abscissae/weights).
 *
 * <p>Required by Phase 1 closure A1 ({@code QdFpAmericanEngine} scheme
 * classes) and other downstream consumers needing {@code [a,b]}-mapped
 * Gauss-Legendre integration.
 */
public class GaussLegendreIntegrator extends Integrator {

    private final GaussLegendreIntegration integration_;

    public GaussLegendreIntegrator(final int n) {
        // C++: Integrator(Null<Real>(), n)
        // Here Null<Real>() maps to NULL_REAL (Double.MAX_VALUE), matching the
        // Constants convention used elsewhere in this codebase.
        super(Constants.NULL_REAL, n);
        this.integration_ = new GaussLegendreIntegration(n);
    }

    /**
     * Access to the underlying canonical {@code [-1,1]} Gauss-Legendre
     * integration object (abscissae/weights), mirroring C++
     * {@code GaussLegendreIntegrator::getIntegration()}.
     */
    public GaussLegendreIntegration getIntegration() {
        return integration_;
    }

    @Override
    protected double integrate(final Ops.DoubleOp f, final double a, final double b) {
        final double c1 = 0.5 * (b - a);
        final double c2 = 0.5 * (a + b);

        return c1 * integration_.op(new Ops.DoubleOp() {
            @Override
            public double op(final double x) {
                return f.op(c1 * x + c2);
            }
        });
    }
}
