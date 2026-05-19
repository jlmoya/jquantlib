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

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
*/

package org.jquantlib.math.interpolations;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.EndCriteria;
import org.jquantlib.math.optimization.OptimizationMethod;

/**
 * Outer wrapper for XABR-family interpolations.
 *
 * <p>Mirrors the {@code XABRInterpolation<I1, I2, Model>} class in C++
 * v1.42.1 {@code ql/math/interpolations/xabrinterpolation.hpp}; the C++ version is a thin facade around an
 * {@code XABRInterpolationImpl} held via {@code shared_ptr}. The Java port composes a {@link XABRInterpolationImpl} via
 * the {@link #impl()} accessor.
 *
 * <p>This wrapper is the structural placeholder for Phase 2d C.6+ when
 * {@code SABRInterpolation} (and future XABR variants) refactor to subclass it.
 *
 * <p><b>ParameterTransformation API hook (Phase 5e.5b-CFC-d-245):</b>
 * mirrors the implicit {@code Model::direct} / {@code Model::inverse} pair that {@code XABRCoeffHolder<Model>} uses
 * internally (C++ {@code xabrinterpolation.hpp} lines 198-217). Exposing the transformation via
 * {@link ParameterTransformation} lets callers (in particular the {@code testTransformations} round-trip test)
 * round-trip the constrained SABR parameters {alpha,beta,nu,rho} through the unconstrained-space map without
 * instantiating the calibration impl. The hook is read-only and stateless — both methods take the
 * constrained/unconstrained vector, paramIsFixed flags, and forward, and delegate to the bound {@link XABRSpecs}
 * instance.
 *
 * @param <S> XABR specs type
 */
public class XABRInterpolation< S extends XABRSpecs > {

    protected final XABRInterpolationImpl< S > impl_;

    public XABRInterpolation(final double[] x, final double[] y, final double t, final double forward,
            final double[] params, final boolean[] paramIsFixed, final boolean vegaWeighted,
            final EndCriteria endCriteria, final OptimizationMethod optMethod, final double errorAccept,
            final boolean useMaxError, final int maxGuesses, final double[] addParams, final S specs) {
        this.impl_ = new XABRInterpolationImpl< S >(x, y, t, forward, params, paramIsFixed, vegaWeighted, endCriteria,
                optMethod, errorAccept, useMaxError, maxGuesses, addParams, specs);
    }

    /**
     * Static factory that wraps any {@link XABRSpecs} instance in a {@link ParameterTransformation} adapter, without
     * requiring an {@link XABRInterpolation} to be constructed first. Useful for tests that probe the direct/inverse
     * maps in isolation (cf. {@code testTransformations}).
     */
    public static ParameterTransformation parameterTransformation(final XABRSpecs specs) {
        return new ParameterTransformation() {
            @Override
            public Array inverse(final Array y, final boolean[] paramIsFixed, final double[] params,
                    final double forward) {
                return specs.inverse(y, paramIsFixed, params, forward);
            }

            @Override
            public Array direct(final Array x, final boolean[] paramIsFixed, final double[] params,
                    final double forward) {
                return specs.direct(x, paramIsFixed, params, forward);
            }
        };
    }

    /** Accessor for the underlying impl (calibration loop + state). */
    public XABRInterpolationImpl< S > impl() {
        return impl_;
    }

    /**
     * Returns a {@link ParameterTransformation} view bound to the held {@link XABRSpecs}. The returned object is a thin
     * adapter — it forwards both methods to {@link XABRSpecs#direct(Array, boolean[], double[], double)} /
     * {@link XABRSpecs#inverse(Array, boolean[], double[], double)}.
     */
    public ParameterTransformation parameterTransformation() {
        return parameterTransformation(impl_.specs());
    }

    /**
     * Bidirectional parameter transformation between constrained (model-meaningful) and unconstrained
     * (optimizer-friendly) space.
     *
     * <p>Mirrors the implicit {@code Model::direct(x, ...)} /
     * {@code Model::inverse(y, ...)} pair used inside the C++ {@code XABRInterpolationImpl::update()} calibration loop
     * ({@code xabrinterpolation.hpp} lines 198-217). The C++ template never surfaces these as a standalone object; the
     * Java port introduces the {@code ParameterTransformation} type so unit tests can round-trip the SABR /
     * no-arbitrage SABR parameter vectors without spinning up a full calibration impl.
     *
     * <p>Contract: for any constrained vector {@code y} produced by
     * {@link #direct(Array, boolean[], double[], double)} from some unconstrained {@code x}, the round trip
     * {@code direct(inverse(y)) == y} should hold to numerical precision. Equality on the reverse direction
     * ({@code inverse(direct(x)) == x}) generally does NOT hold because the transformations clamp unconstrained values
     * that exceed the SABR feasibility radius.
     */
    public interface ParameterTransformation {
        /**
         * Constrained → unconstrained: maps a SABR-meaningful parameter vector to the optimizer's unconstrained space.
         */
        Array inverse(Array y, boolean[] paramIsFixed, double[] params, double forward);

        /**
         * Unconstrained → constrained: maps an optimizer-space vector back to a SABR-meaningful parameter vector
         * (respecting feasibility bounds).
         */
        Array direct(Array x, boolean[] paramIsFixed, double[] params, double forward);
    }
}
