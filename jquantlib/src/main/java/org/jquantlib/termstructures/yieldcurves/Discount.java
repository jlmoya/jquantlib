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

import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;

/*
 * Phase Bug-Fix-Curve: minValueAfter/maxValueAfter ported from C++ v1.42.1
 * ql/termstructures/yield/bootstraptraits.hpp (struct Discount). The previous
 * Java port returned loose constants (Constants.QL_EPSILON / 3.0) because the
 * Java Traits interface did not expose times[]. Now that the interface is
 * extended, we can mirror C++ exactly:
 *   minValueAfter(i): validData? min(data)/2 : data[i-1] * exp(-maxRate*dt)
 *   maxValueAfter(i): data[i-1] * exp(+maxRate*dt)        (regardless of validData)
 * with maxRate = 1.0 and dt = times[i] - times[i-1].
 */

/**
 * Discount-curve traits
 *
 * @author Richard Gomes
 * @author John Martin
 */
public class Discount implements Traits {

    private static final double averageRate = .05;
    private static final double maxRate = 1.0;

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
        return c.discount(d, true);
    }

    @Override
    public double minValueAfter(final int i, final double[] data, final boolean validData, final double[] times) {
        // Phase Bug-Fix-Curve: pillar-aware bound matching C++ v1.42.1
        // Discount::minValueAfter (bootstraptraits.hpp lines 81-93).
        if ( validData ) {
            // min over data[]/2.0
            double minVal = data[0];
            for ( int k = 1; k < data.length; ++k ) {
                if ( data[k] < minVal ) {
                    minVal = data[k];
                }
            }
            return minVal / 2.0;
        }
        final double dt = times[i] - times[i - 1];
        return data[i - 1] * Math.exp(-maxRate * dt);
    }

    @Override
    public double maxValueAfter(final int i, final double[] data, final boolean validData, final double[] times) {
        // Phase Bug-Fix-Curve: pillar-aware bound matching C++ v1.42.1
        // Discount::maxValueAfter (bootstraptraits.hpp lines 94-102). C++
        // does NOT branch on validData here — it always returns
        // data[i-1] * exp(+maxRate * dt). This permits discount factors
        // above 1 (negative rates) within a sane bound, fixing Phase 3g
        // testIsdaCalculatorReconcile* root cause and unblocking spline
        // bootstrap convergence (Brent root-bracketing).
        final double dt = times[i] - times[i - 1];
        return data[i - 1] * Math.exp(maxRate * dt);
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
