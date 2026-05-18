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
import org.jquantlib.math.optimization.Constraint;
import org.jquantlib.model.VolatilityType;

/**
 * Java representative of C++ {@code template<class Model>} parameter used in
 * {@code ql/math/interpolations/xabrinterpolation.hpp}. Each XABR-style
 * interpolation (SABR, no-arbitrage SABR, ZABR) provides a concrete impl of
 * this interface, supplying the model-specific dimension, default values,
 * guess synthesis, parameter transformations, and volatility evaluation.
 *
 * <p>Mirrors the implicit "Model" concept from C++ v1.42.1
 * {@code xabrinterpolation.hpp} lines 51-321.
 */
public interface XABRSpecs {

    /** Number of free model parameters (e.g. SABR = 4). */
    int dimension();

    /**
     * Fill {@code params} with default values where {@code paramIsFixed[i]} is
     * false. Mirrors C++ {@code Model().defaultValues(...)}.
     */
    void defaultValues(double[] params, boolean[] paramIsFixed,
            double forward, double t, double[] addParams);

    /**
     * Per-restart guess synthesis using the next Halton sample. Mirrors C++
     * {@code Model().guess(guess, paramIsFixed, forward, t, sampleValue, addParams)}
     * (xabrinterpolation.hpp line 191).
     *
     * @param values        guess vector to mutate in place
     * @param paramIsFixed  per-parameter fixed flags
     * @param forward       forward rate
     * @param t             expiry
     * @param sampleValue   raw Halton sample value array (length = freeParameters)
     * @param addParams     model-specific extra parameters
     */
    void guess(double[] values, boolean[] paramIsFixed,
            double forward, double t, double[] sampleValue, double[] addParams);

    /**
     * Inverse parameter transformation (constrained → unconstrained). Mirrors
     * C++ {@code Model().inverse(y, paramIsFixed, params, forward)}.
     */
    Array inverse(Array y, boolean[] paramIsFixed, double[] params, double forward);

    /**
     * Direct parameter transformation (unconstrained → constrained). Mirrors
     * C++ {@code Model().direct(x, paramIsFixed, params, forward)}.
     */
    Array direct(Array x, boolean[] paramIsFixed, double[] params, double forward);

    /**
     * Volatility from a strike given calibrated params. The C++ template
     * routes this through {@code Model::type::volatility}; the Java port
     * passes {@code t} directly for cleaner plumbing.
     */
    double volatility(double strike, double forward, double t, double[] params);

    /**
     * Volatility from a strike, plus the {@code addParams} (shift, etc.) and
     * the {@link VolatilityType} carried by the parent interpolator. Mirrors
     * C++ {@code SABRWrapper::volatility(x, volatilityType)} which routes
     * through {@code shiftedSabrVolatility(..., shift_, volatilityType)}
     * (sabrinterpolation.hpp lines 53-56).
     *
     * <p>Default delegates to the 4-arg overload so existing specs that do
     * not honour {@code volatilityType} continue to work unchanged. SABR (and
     * other vol-type-aware specs) override this to dispatch on {@code vt}.
     */
    default double volatility(final double strike, final double forward,
            final double t, final double[] params,
            final double[] addParams, final VolatilityType vt) {
        return volatility(strike, forward, t, params);
    }

    /** Constraint shape passed to the optimizer (typically {@code NoConstraint}). */
    Constraint constraint(double forward);

    /**
     * Optional vega weight per strike. Mirrors C++
     * {@code Model().weight(strike, forward, stdDev, addParams)}.
     */
    double weight(double strike, double forward, double stdDev, double[] addParams);
}
