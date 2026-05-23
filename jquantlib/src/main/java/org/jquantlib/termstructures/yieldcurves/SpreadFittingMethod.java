/*
 Copyright (C) 2026 JQuantLib migration contributors

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
 Copyright (C) 2015 Andres Hernandez
*/

package org.jquantlib.termstructures.yieldcurves;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;

/**
 * Spread fitting method helper.
 * <p>
 * Fits a spread curve on top of a discount function according to the given
 * parametric method. The discount factor at time {@code t} is:
 * <pre>
 * d(t) = method.discount(x, t) * discountingCurve.discount(t) / rebase
 * </pre>
 * where {@code rebase} corrects for a possibly different reference date of the
 * underlying discounting curve.
 *
 * <p>Faithful port of QuantLib v1.42.1 {@code SpreadFittingMethod}
 * (ql/termstructures/yield/nonlinearfittingmethods.{hpp,cpp}).
 *
 * <p>Phase 2 forward closure L2-B.
 */
public class SpreadFittingMethod extends FittingMethod {

    /** Underlying parametric method. */
    private final FittingMethod method_;
    /** Discount curve from on top of which the spread will be calculated. */
    private final Handle< YieldTermStructure > discountingCurve_;
    /** Adjustment in case underlying discount curve has different reference date. Lazily set in {@link #init()}. */
    private double rebase_ = 1.0;

    public SpreadFittingMethod(final FittingMethod method, final Handle< YieldTermStructure > discountCurve) {
        this(method, discountCurve, 0.0, Double.MAX_VALUE);
    }

    public SpreadFittingMethod(final FittingMethod method, final Handle< YieldTermStructure > discountCurve,
            final double minCutoffTime, final double maxCutoffTime) {
        // C++: passes constrainAtZero / weights / optimizer / l2 from the wrapped method
        // when present, otherwise true / Array() / null / Array(). The base ctor here uses the
        // 7-arg overload but FittingMethod's 6-arg ctor does not exist, so route via the full ctor
        // with NoConstraint (mirrors C++ which doesn't pass a constraint either — relies on
        // base-class default initialization to a default-constructed Constraint).
        super(method != null ? method.constrainAtZero() : true,
                method != null ? method.weights() : new Array(0),
                method != null ? method.optimizationMethod() : null,
                method != null ? method.l2() : new Array(0),
                minCutoffTime, maxCutoffTime, new org.jquantlib.math.optimization.NoConstraint());

        QL.require(method != null, "Fitting method is empty");
        QL.require(discountCurve != null && !discountCurve.empty(), "Discounting curve cannot be empty");

        this.method_ = method;
        this.discountingCurve_ = discountCurve;
    }

    @Override
    public SpreadFittingMethod clone() {
        return new SpreadFittingMethod(method_.clone(), discountingCurve_);
    }

    @Override
    public int size() {
        return method_.size();
    }

    @Override
    protected double discountFunction(final Array x, final double t) {
        return method_.discount(x, t) * discountingCurve_.currentLink().discount(t, true) / rebase_;
    }

    @Override
    protected void init() {
        // In case discount curve has a different reference date,
        // discount to this curve's reference date.
        if ( !curve_.referenceDate().equals(discountingCurve_.currentLink().referenceDate()) ) {
            rebase_ = discountingCurve_.currentLink().discount(curve_.referenceDate());
        } else {
            rebase_ = 1.0;
        }
        // Call regular init.
        super.init();
    }
}
