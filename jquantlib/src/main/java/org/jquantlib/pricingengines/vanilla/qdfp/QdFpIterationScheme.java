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
package org.jquantlib.pricingengines.vanilla.qdfp;

import org.jquantlib.math.integrals.Integrator;

/**
 * Iteration scheme strategy for the QD fixed-point American engine.
 *
 * <p>Phase 1 closure A1 port of {@code QuantLib::QdFpIterationScheme}
 * (v1.42.1 ql/pricingengines/vanilla/qdfpamericanengine.hpp; pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}).
 *
 * <p>The QD fixed-point American option engine (Andersen-Lake-Offengenden
 * 2015; Andersen-Lake 2021) parameterises the early-exercise boundary via
 * a Chebyshev interpolation and refines it through a fixed-point iteration
 * combining one Jacobi-Newton step with several naive Richardson steps.
 * This interface lets the engine swap between Gauss-Legendre, tanh-sinh,
 * and hybrid quadrature strategies without changing the algorithm core.
 *
 * <p>References:
 * <ul>
 *   <li>L. Andersen, M. Lake, D. Offengenden, "High Performance American
 *       Option Pricing", SSRN abstract id 2547027 (2015).</li>
 *   <li>L. Andersen, M. Lake, "Fast American Option Pricing: The
 *       Double-Boundary Case", Wilmott (2021).</li>
 * </ul>
 */
public interface QdFpIterationScheme {

    /** Number of Chebyshev nodes used to interpolate the exercise boundary. */
    int getNumberOfChebyshevInterpolationNodes();

    /**
     * Number of naive (Richardson) fixed-point steps after the initial
     * Jacobi-Newton refinement.
     */
    int getNumberOfNaiveFixedPointSteps();

    /** Number of partial Jacobi-Newton fixed-point steps (always 1 in v1.42.1). */
    int getNumberOfJacobiNewtonFixedPointSteps();

    /**
     * Integrator used inside each fixed-point iteration step over the
     * exercise-boundary integrals.
     */
    Integrator getFixedPointIntegrator();

    /**
     * Integrator used for the final conversion of the converged exercise
     * boundary into option prices.
     */
    Integrator getExerciseBoundaryToPriceIntegrator();
}
