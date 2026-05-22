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

/**
 * Spread Discount-curve traits.
 * <p>
 * Java port of QuantLib v1.42.1 {@code detail::SpreadTraits<Discount>}
 * (see {@code ql/termstructures/yield/spreadbootstraptraits.hpp}).
 * <p>
 * In C++ this is a template specialization that inherits all of
 * {@link Discount}'s static trait methods (initialValue, initialGuess, guess,
 * minValueAfter, maxValueAfter, updateGuess, maxIterations, initialDate,
 * dummyInitialValue) and only overrides the associated curve type from
 * {@link InterpolatedDiscountCurve} to {@link InterpolatedSpreadDiscountCurve}.
 * <p>
 * In Java the curve type is dispatched explicitly by
 * {@link PiecewiseYieldCurve#constructBaseClass(Class, Class, java.util.function.Supplier, org.jquantlib.time.Date, org.jquantlib.daycounters.DayCounter, org.jquantlib.math.interpolations.Interpolation.Interpolator)}
 * (and the settlement-days overload) — so this class exists purely as a
 * type-marker that the factory recognises and routes to
 * {@link InterpolatedSpreadDiscountCurve}. All trait semantics are inherited
 * verbatim from {@link Discount}.
 *
 * @author JQuantLib migration contributors (Phase 1.4 closure)
 */
public class SpreadBootstrapTraits extends Discount {

    public SpreadBootstrapTraits() {
        super();
    }

}
