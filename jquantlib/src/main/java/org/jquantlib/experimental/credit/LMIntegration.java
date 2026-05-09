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

import java.util.function.Function;

/**
 * Common interface for the {@link LatentModel} integration backends.
 *
 * <p>Java port of QuantLib v1.42.1 {@code LMIntegration} (declared in
 * {@code ql/experimental/math/latentmodel.hpp}). Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>Unifies the two C++ multi-dimensional integration families
 * ({@code GaussianQuadMultidimIntegrator}, {@code MultidimIntegral}) under a
 * common factory-friendly interface so that {@link LatentModel} can choose
 * the integration algorithm at runtime.
 */
public interface LMIntegration {

    /**
     * Integrate a scalar function {@code f: R^d → R} over the integration
     * domain (typically {@code R^d} or a bounded hyper-rectangle, depending
     * on the concrete backend).
     */
    double integrate(Function<double[], Double> f);

    /**
     * Integrate a vector function {@code f: R^d → R^k} over the integration
     * domain. The default throws; backends supporting vector integration
     * override this method.
     */
    default double[] integrateV(Function<double[], double[]> f) {
        throw new UnsupportedOperationException("No vector integration provided");
    }
}
