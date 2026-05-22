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

package org.jquantlib.termstructures.yieldcurves;

import org.jquantlib.math.interpolations.Interpolation.Interpolator;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.Bootstrap;
import org.jquantlib.termstructures.IterativeBootstrap;
import org.jquantlib.termstructures.RateHelper;
import org.jquantlib.termstructures.YieldTermStructure;

/**
 * Piecewise spread yield term structure — a {@link PiecewiseYieldCurve} parameterised on
 * {@link SpreadBootstrapTraits} so that the bootstrapped pillars are discount-factor spreads applied multiplicatively
 * to a base {@link YieldTermStructure}.
 * <p>
 * Java port of QuantLib v1.42.1 {@code PiecewiseSpreadYieldCurve}
 * (see {@code ql/termstructures/yield/piecewisespreadyieldcurve.hpp}).
 * <p>
 * Mirrors C++:
 * <pre>{@code
 *   template <class Traits, class Interpolator, template <class> class Bootstrap = IterativeBootstrap>
 *   class PiecewiseSpreadYieldCurve
 *       : public PiecewiseYieldCurve<detail::SpreadTraits<Traits>, Interpolator, Bootstrap> { ... };
 * }</pre>
 * <p>
 * In the Java port {@code Traits} is fixed to {@link Discount}: the C++ codebase only ever specialises
 * {@code SpreadTraits<Discount>} and we use {@link SpreadBootstrapTraits} as the marker type.
 *
 * @param <I> interpolator factory type
 * @param <B> bootstrap algorithm
 * @author JQuantLib migration contributors (Phase 1.4 closure)
 */
public class PiecewiseSpreadYieldCurve< I extends Interpolator, B extends Bootstrap >
        extends PiecewiseYieldCurve< SpreadBootstrapTraits, I, B > {

    public PiecewiseSpreadYieldCurve(final Class< I > classI, final Class< B > classB,
            final Handle< YieldTermStructure > baseCurve, final RateHelper[] instruments,
            final Interpolator interpolator) {
        super(SpreadBootstrapTraits.class, classI, classB, baseCurve, instruments, interpolator);
    }

    public PiecewiseSpreadYieldCurve(final Class< I > classI, final Class< B > classB,
            final Handle< YieldTermStructure > baseCurve, final RateHelper[] instruments,
            final Interpolator interpolator, final Bootstrap bootstrap) {
        super(SpreadBootstrapTraits.class, classI, classB, baseCurve, instruments, 1.0e-12, interpolator, bootstrap);
    }

    public PiecewiseSpreadYieldCurve(final Class< I > classI, final Class< B > classB,
            final Handle< YieldTermStructure > baseCurve, final RateHelper[] instruments) {
        this(classI, classB, baseCurve, instruments, /*interpolator*/ null);
    }

    /**
     * Convenience constructor using the default {@link IterativeBootstrap}.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static < I extends Interpolator > PiecewiseSpreadYieldCurve< I, IterativeBootstrap >
            iterative(final Class< I > classI, final Handle< YieldTermStructure > baseCurve,
                    final RateHelper[] instruments, final Interpolator interpolator) {
        return new PiecewiseSpreadYieldCurve< I, IterativeBootstrap >(
                classI, IterativeBootstrap.class, baseCurve, instruments, interpolator);
    }

    /**
     * Accessor for the underlying base curve, mirroring C++ {@code curve->baseCurve()}.
     */
    public Handle< YieldTermStructure > baseCurve() {
        // The base-curve handle is held by the inner InterpolatedSpreadDiscountCurve. Expose it here so callers
        // can rebuild a raw SpreadDiscountCurve from {@code curve->baseCurve(), curve->dates(), curve->data()}
        // (mirrors test usage at piecewiseyieldcurve.cpp:1884).
        final Traits.Curve curve = baseTraitsCurve();
        if ( curve instanceof InterpolatedSpreadDiscountCurve ) {
            return ( (InterpolatedSpreadDiscountCurve< ? >) curve ).baseCurve();
        }
        throw new IllegalStateException(
                "PiecewiseSpreadYieldCurve.baseCurve() called on a curve whose underlying type is not InterpolatedSpreadDiscountCurve");
    }

}
