/*
 Copyright (C) 2026 Jose Moya

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

/*
 Copyright (C) 2007 Ferdinando Ametrano
 Copyright (C) 2007 Cristina Duminuco
 Copyright (C) 2007 Giorgio Facchinetti
*/

package org.jquantlib.math.interpolations;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.EndCriteria;
import org.jquantlib.math.optimization.OptimizationMethod;

/**
 * Abcd interpolation factory and traits.
 *
 * <p>Java port of QuantLib v1.42.1 {@code ql/math/interpolations/abcdinterpolation.hpp::Abcd} (lines 211-245). Produces
 * {@link AbcdInterpolation} instances with a shared set of parameter guesses / constraints. Useful for plugging into a
 * curve/surface that takes an {@link Interpolation.Interpolator}.
 *
 * <p>Mirrors C++ {@code Abcd::global = true} (the fit is non-local) and {@code Abcd::requiredPoints = 2}.
 */
public class Abcd implements Interpolation.Interpolator {

    private final double a_, b_, c_, d_;
    private final boolean aIsFixed_, bIsFixed_, cIsFixed_, dIsFixed_;
    private final boolean vegaWeighted_;
    private final EndCriteria endCriteria_;
    private final OptimizationMethod optMethod_;

    /**
     * Full-arity constructor mirroring C++ {@code Abcd::Abcd}
     * (abcdinterpolation.hpp lines 213-226).
     */
    public Abcd(final double a, final double b, final double c, final double d,
            final boolean aIsFixed, final boolean bIsFixed,
            final boolean cIsFixed, final boolean dIsFixed,
            final boolean vegaWeighted,
            final EndCriteria endCriteria,
            final OptimizationMethod optMethod) {
        this.a_ = a;
        this.b_ = b;
        this.c_ = c;
        this.d_ = d;
        this.aIsFixed_ = aIsFixed;
        this.bIsFixed_ = bIsFixed;
        this.cIsFixed_ = cIsFixed;
        this.dIsFixed_ = dIsFixed;
        this.vegaWeighted_ = vegaWeighted;
        this.endCriteria_ = endCriteria;
        this.optMethod_ = optMethod;
    }

    /**
     * Convenience constructor: no vega weighting, no end-criteria / method overrides.
     */
    public Abcd(final double a, final double b, final double c, final double d,
            final boolean aIsFixed, final boolean bIsFixed,
            final boolean cIsFixed, final boolean dIsFixed) {
        this(a, b, c, d, aIsFixed, bIsFixed, cIsFixed, dIsFixed, false, null, null);
    }

    @Override
    public Interpolation interpolate(final Array vx, final Array vy) {
        return new AbcdInterpolation(vx, vy,
                a_, b_, c_, d_,
                aIsFixed_, bIsFixed_, cIsFixed_, dIsFixed_,
                vegaWeighted_, endCriteria_, optMethod_);
    }

    /** C++ {@code Abcd::global = true}. */
    @Override
    public boolean global() {
        return true;
    }

    /** C++ {@code Abcd::requiredPoints = 2}. */
    @Override
    public int requiredPoints() {
        return 2;
    }
}
