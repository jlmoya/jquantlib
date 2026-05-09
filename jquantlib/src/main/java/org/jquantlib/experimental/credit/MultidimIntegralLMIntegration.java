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
package org.jquantlib.experimental.credit;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import org.jquantlib.QL;
import org.jquantlib.math.integrals.Integrator;
import org.jquantlib.math.integrals.MultidimIntegral;

/**
 * {@link LMIntegration} backed by {@link MultidimIntegral}, a tensor product of
 * arbitrary 1D integrators (typically trapezoid) over a hyper-rectangle.
 *
 * <p>Java port of QuantLib v1.42.1 specialisation
 * {@code IntegrationBase<MultidimIntegral>} (declared inline in
 * {@code ql/experimental/math/latentmodel.hpp}). Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>The C++ specialisation participates in a CRTP-style hierarchy via
 * multiple inheritance; Java composes the underlying integrator instead.
 */
public final class MultidimIntegralLMIntegration implements LMIntegration {

    private final MultidimIntegral integrator_;
    private final double[] a_;
    private final double[] b_;

    /**
     * @param integrators per-dimension 1D integrators
     * @param a           lower bound applied to every dimension
     * @param b           upper bound applied to every dimension
     */
    public MultidimIntegralLMIntegration(final List<Integrator> integrators, final double a, final double b) {
        QL.require(integrators != null && !integrators.isEmpty(), "integrators required");
        this.integrator_ = new MultidimIntegral(integrators);
        final int n = integrators.size();
        this.a_ = new double[n];
        this.b_ = new double[n];
        Arrays.fill(this.a_, a);
        Arrays.fill(this.b_, b);
    }

    @Override
    public double integrate(final Function<double[], Double> f) {
        return integrator_.op(f, a_, b_);
    }

    /** Vector integration not implemented for this backend. */
    // integrateV intentionally inherits the throwing default until needed.
}
