/*
 Copyright (C) 2008 Richard Gomes

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

import org.jquantlib.math.interpolations.Interpolation;
import org.jquantlib.math.interpolations.Interpolation.Interpolator;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;
import org.jquantlib.util.Pair;

import java.util.List;

/**
 *
 * @author Richard Gomes
 */

public interface Traits {

    /**
     * value at reference
     */
    double initialValue(YieldTermStructure curve);

    /**
     * initial guess
     */
    double initialGuess();

    /**
     * further guesses
     */
    double guess(final YieldTermStructure curve, final Date d);

    /**
     * possible constraints based on previous values.
     * <p>
     * Phase Bug-Fix-Curve: signature extended to take {@code times[]} and {@code validData} so that {@link Discount},
     * {@link ZeroYield} and {@link ForwardRate} can implement the C++ v1.42.1 pillar-aware bounds (see
     * {@code ql/termstructures/yield/bootstraptraits.hpp}). Callers (currently only
     * {@link org.jquantlib.termstructures.IterativeBootstrap}) pass {@code validData = validCurve || iteration > 0}
     * matching C++.
     */
    double minValueAfter(int i, final double[] data, boolean validData, final double[] times);

    /**
     * possible constraints based on maximum values. See {@link #minValueAfter(int, double[], boolean, double[])}.
     */
    double maxValueAfter(int i, final double[] data, boolean validData, final double[] times);

    /**
     * update with new guess
     */
    void updateGuess(final double[] data, double value, int i);

    boolean dummyInitialValue() /* @ReadOnly */;

    Date initialDate(final YieldTermStructure curve) /* @ReadOnly */;

    int maxIterations() /* @ReadOnly */;

    interface Curve extends YieldTermStructure {

        @Override
        Date maxDate() /* @ReadOnly */;

        Date[] dates() /* @ReadOnly */;

        /*@Time*/ double[] times() /* @ReadOnly */;

        List< Pair< Date, /* @Rate */Double > > nodes() /* @ReadOnly */;

        double[] data();

        @Override
        Date referenceDate() /* @ReadOnly */;

        @Override
        double timeFromReference(final Date date) /* @ReadOnly */;

        @Override
        void update();

        Interpolator interpolator() /* @ReadOnly */;

        Interpolation interpolation() /* @ReadOnly */;

        void setInterpolation(final Interpolation interpolation);

        void setDates(final Date[] dates);

        void setTimes(/*@Time*/ double[] times);

        void setData(final double[] data);

        /*@DiscountFactor*/ double discount(final /*@Time*/ double t) /* @ReadOnly */;

        /*@DiscountFactor*/ double forward(final /*@Time*/ double t) /* @ReadOnly */;

        /*@DiscountFactor*/ double zeroYield(final /*@Time*/ double t) /* @ReadOnly */;

    }

}
