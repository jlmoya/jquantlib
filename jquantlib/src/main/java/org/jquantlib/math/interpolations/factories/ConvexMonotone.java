/*
 Copyright (C) 2026 JQuantLib Migration

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

package org.jquantlib.math.interpolations.factories;

import org.jquantlib.math.interpolations.ConvexMonotoneInterpolation;
import org.jquantlib.math.interpolations.Interpolation;
import org.jquantlib.math.matrixutilities.Array;

/**
 * Convex-monotone interpolation factory and traits.
 * <p>
 * Mirrors the QuantLib v1.42.1 C++ {@code ConvexMonotone} traits/factory class
 * from {@code ql/math/interpolations/convexmonotoneinterpolation.hpp}.
 * <p>
 * Default constructor values match C++: {@code quadraticity=0.3},
 * {@code monotonicity=0.7}, {@code forcePositive=true}.
 *
 * @see ConvexMonotoneInterpolation
 */
public class ConvexMonotone implements Interpolation.Interpolator {

    /** C++ trait — see {@code dataSizeAdjustment} in C++ class. */
    public static final int dataSizeAdjustment = 1;

    private final double quadraticity;
    private final double monotonicity;
    private final boolean forcePositive;

    public ConvexMonotone() {
        this(0.3, 0.7, true);
    }

    public ConvexMonotone(final double quadraticity,
                          final double monotonicity,
                          final boolean forcePositive) {
        this.quadraticity = quadraticity;
        this.monotonicity = monotonicity;
        this.forcePositive = forcePositive;
    }

    @Override
    public boolean global() {
        return true;
    }

    @Override
    public int requiredPoints() {
        return 2;
    }

    @Override
    public Interpolation interpolate(final Array vx, final Array vy) {
        return new ConvexMonotoneInterpolation(vx, vy, quadraticity, monotonicity,
                forcePositive, false);
    }

    @Override
    public int dataSizeAdjustment() {
        return dataSizeAdjustment;
    }

    /**
     * Faithful port of v1.42.1
     * {@code ConvexMonotone::localInterpolate(...)} from
     * {@code ql/math/interpolations/convexmonotoneinterpolation.hpp:109-152}.
     * <p>
     * Two regimes:
     * <ul>
     *   <li>First call ({@code length - localisation == 1}): build the
     *       interpolation from scratch on the {@code [xBegin,xEnd)} slice,
     *       with {@code flatFinalPeriod = (length != finalSize)}.</li>
     *   <li>Subsequent calls: copy the existing {@link
     *       ConvexMonotoneInterpolation#getExistingHelpers()} map from
     *       {@code prevInterpolation} into the new interpolation, again with
     *       {@code flatFinalPeriod = (length != finalSize)}.</li>
     * </ul>
     */
    @Override
    public Interpolation localInterpolate(final Array vx, final Array vy,
            final int localisation, final Interpolation prevInterpolation,
            final int finalSize) {
        final int length = vx.size();
        final boolean flatFinalPeriod = (length != finalSize);
        if (length - localisation == 1) {
            // First call — no pre-existing helpers.
            return new ConvexMonotoneInterpolation(vx, vy, quadraticity, monotonicity,
                    forcePositive, flatFinalPeriod);
        }
        // Subsequent calls — extract helpers from the previous interpolation.
        final java.util.Map<Double, ConvexMonotoneInterpolation.SectionHelper> existing;
        if (prevInterpolation instanceof ConvexMonotoneInterpolation) {
            existing = ((ConvexMonotoneInterpolation) prevInterpolation).getExistingHelpers();
        } else {
            existing = new java.util.TreeMap<>();
        }
        return new ConvexMonotoneInterpolation(vx, vy, quadraticity, monotonicity,
                forcePositive, flatFinalPeriod, existing);
    }

}
