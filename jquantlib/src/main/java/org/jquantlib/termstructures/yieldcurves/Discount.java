/*
Copyright (C) 2008 Richard Gomes
Copyright (C) 2009 John Martin

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
Copyright (C) 2005, 2006, 2007 StatPro Italia srl

This file is part of QuantLib, a free-software/open-source library
for financial quantitative analysts and developers - http://quantlib.org/

QuantLib is free software: you can redistribute it and/or modify it
under the terms of the QuantLib license.  You should have received a
copy of the license along with this program; if not, please email
<quantlib-dev@lists.sf.net>. The license is also available online at
<http://quantlib.org/license.shtml>.

This program is distributed in the hope that it will be useful, but WITHOUT
ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
FOR A PARTICULAR PURPOSE.  See the license for more details.
 */

package org.jquantlib.termstructures.yieldcurves;

import java.util.Arrays;

import org.jquantlib.QL;
import org.jquantlib.math.Constants;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;

/**
 * Discount-curve traits
 *
 * @author Richard Gomes
 * @author John Martin
 */
public class Discount implements Traits {

    private static final double averageRate = .05;

    //TODO: think how constructor must look like
    public Discount() {
    }

    @Override
    public double initialValue(final YieldTermStructure curve) {
        return 1.0;
    }

    @Override
    public double initialGuess() {
        return 1.0 / (1.0 + averageRate * 0.25);
    }

    @Override
    public double guess(final YieldTermStructure c, final Date d) {
        return c.discount(d,true);
    }

    @Override
    public double minValueAfter(final int i, final double[] data) {
        return Constants.QL_EPSILON;
    }

    @Override
    public double maxValueAfter(final int i, final double[] data) {
        // Phase 3g: align to v1.42.1 — C++ Discount::maxValueAfter does NOT
        // gate negative-rate handling on a Settings flag; it always returns
        // {@code data[i-1] * exp(maxRate * dt)} (where {@code maxRate = 1}),
        // which permits discount factors above 1 (negative rates) within a
        // sane bound. The previous Java code clamped {@code data[i] <=
        // data[i-1]} unless {@code isNegativeRates()} was explicitly set,
        // which silently clobbered EUR negative-rate bootstrap fixtures
        // (Phase 3g testIsdaCalculatorReconcile* root cause). The Java Traits
        // interface does not expose {@code times[]} so we can't compute the
        // exact C++ bound; use a generous constant {@code 3.0} (matching
        // what the previous {@code isNegativeRates=true} branch returned).
        // The Brent solver inside IterativeBootstrap converges to the same
        // root regardless of bracket width, as long as the root is contained.
        return 3.0;
    }

    @Override
    public void updateGuess(final double[] data, final double value, final int i) {
        // Phase 3e: align to v1.42.1 — C++ Discount::updateGuess sets only
        // data[i]; the Java port had Arrays.fill which clobbered all earlier
        // bootstrapped points each iteration, breaking PiecewiseYieldCurve
        // bootstrap on multi-helper inputs.
        data[i] = value;
    }


    @Override
    public boolean dummyInitialValue() {
        return false;
    }

    @Override
    public Date initialDate(final YieldTermStructure curve) {
        return curve.referenceDate();
    }

    @Override
    public int maxIterations() {
        // Phase Bug-Fix-3: align to v1.42.1 — C++ Discount::maxIterations
        // returns 100 (bootstraptraits.hpp:123). The Java port had 50,
        // which is too tight for slow-converging combinations like
        // LogCubic+Discount where the iteration loop oscillates over
        // ~50 steps (testLogCubicDiscountConsistency).
        return 100;
    }

}