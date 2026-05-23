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
 * Generic Gauss-quadrature {@link Integrator} wrapper that maps the canonical
 * domain of the underlying {@link GaussianQuadrature} to an arbitrary
 * {@code [a, b]} via the linear change of variables {@code x = c1*t + c2}
 * with {@code c1 = 0.5*(b-a)} and {@code c2 = 0.5*(a+b)}.
 *
 * <p>Faithful Java port of {@code QuantLib::detail::GaussianQuadratureIntegrator<Integration>}
 * (v1.42.1 {@code ql/math/integrals/gaussianquadratures.{hpp,cpp}}, pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}).
 *
 * <p>C++ uses three explicit instantiations:
 * {@code GaussianQuadratureIntegrator<GaussLegendreIntegration>} (typedef
 * {@code GaussLegendreIntegrator} — already provided by
 * {@link GaussLegendreIntegrator}), {@code <GaussChebyshevIntegration>}
 * (typedef {@code GaussChebyshevIntegrator}), and
 * {@code <GaussChebyshev2ndIntegration>} (typedef {@code GaussChebyshev2ndIntegrator}).
 *
 * <p>Java erases the template; pass any {@link GaussianQuadrature} to this
 * generic class to produce the equivalent integrator.
 *
 * <p>Phase 2 L1-D port.
 *
 * @param <Q> the concrete {@link GaussianQuadrature} subtype
 */
public class GaussianQuadratureIntegrator<Q extends GaussianQuadrature> extends Integrator {

    private final Q integration_;

    public GaussianQuadratureIntegrator(final Q integration) {
        // C++: Integrator(Null<Real>(), n) where n = order
        super(Constants.NULL_REAL, integration.order());
        this.integration_ = integration;
    }

    /**
     * Access to the underlying canonical-domain quadrature object (abscissae
     * / weights), mirroring C++ {@code getIntegration()} accessor.
     */
    public Q getIntegration() {
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
