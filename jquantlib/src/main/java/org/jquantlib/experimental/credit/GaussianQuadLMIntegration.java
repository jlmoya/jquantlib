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

import org.jquantlib.math.integrals.GaussianQuadMultidimIntegrator;

import java.util.function.Function;

/**
 * {@link LMIntegration} backed by N-dimensional Gauss-Hermite quadrature.
 *
 * <p>Java port of QuantLib v1.42.1 specialisation
 * {@code IntegrationBase<GaussianQuadMultidimIntegrator>} (declared inline in
 * {@code ql/experimental/math/latentmodel.hpp}). Pinned commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>The C++ specialisation participates in a CRTP-style hierarchy via
 * multiple inheritance; Java composes the underlying integrator instead.
 */
public final class GaussianQuadLMIntegration implements LMIntegration {

    private final GaussianQuadMultidimIntegrator integrator_;

    /**
     * @param dimension number of integration dimensions
     * @param order     Gauss-Hermite quadrature order applied to each dimension
     */
    public GaussianQuadLMIntegration(final int dimension, final int order) {
        this.integrator_ = new GaussianQuadMultidimIntegrator(dimension, order);
    }

    @Override
    public double integrate(final Function< double[], Double > f) {
        return integrator_.integrate(f);
    }

    @Override
    public double[] integrateV(final Function< double[], double[] > f) {
        return integrator_.integrateV(f);
    }
}
