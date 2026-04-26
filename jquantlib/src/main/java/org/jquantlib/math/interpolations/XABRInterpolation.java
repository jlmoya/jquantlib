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

import org.jquantlib.math.optimization.EndCriteria;
import org.jquantlib.math.optimization.OptimizationMethod;

/**
 * Outer wrapper for XABR-family interpolations.
 *
 * <p>Mirrors the {@code XABRInterpolation<I1, I2, Model>} class in C++
 * v1.42.1 {@code ql/math/interpolations/xabrinterpolation.hpp}; the C++
 * version is a thin facade around an {@code XABRInterpolationImpl} held via
 * {@code shared_ptr}. The Java port composes a
 * {@link XABRInterpolationImpl} via the {@link #impl()} accessor.
 *
 * <p>This wrapper is the structural placeholder for Phase 2d C.6+ when
 * {@code SABRInterpolation} (and future XABR variants) refactor to subclass
 * it.
 *
 * @param <S> XABR specs type
 */
public class XABRInterpolation<S extends XABRSpecs> {

    protected final XABRInterpolationImpl<S> impl_;

    public XABRInterpolation(
            final double[] x, final double[] y, final double t, final double forward,
            final double[] params, final boolean[] paramIsFixed,
            final boolean vegaWeighted, final EndCriteria endCriteria,
            final OptimizationMethod optMethod, final double errorAccept,
            final boolean useMaxError, final int maxGuesses,
            final double[] addParams, final S specs) {
        this.impl_ = new XABRInterpolationImpl<S>(x, y, t, forward, params,
                paramIsFixed, vegaWeighted, endCriteria, optMethod,
                errorAccept, useMaxError, maxGuesses, addParams, specs);
    }

    /** Accessor for the underlying impl (calibration loop + state). */
    public XABRInterpolationImpl<S> impl() {
        return impl_;
    }
}
