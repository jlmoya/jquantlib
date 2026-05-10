/*
 Copyright (C) 2026 JQuantLib migration contributors.

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

package org.jquantlib.experimental.credit;

import java.util.List;

import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.lang.annotation.Natural;
import org.jquantlib.math.interpolations.BicubicSplineInterpolation;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Period;

/**
 * BaseCorrelationTermStructure with bicubic-spline 2D interpolation.
 *
 * <p>Java port of QuantLib v1.42.1 template specialisation
 * {@code BaseCorrelationTermStructure<BicubicSpline>}
 * ({@code ql/experimental/credit/basecorrelationstructure.cpp}).
 *
 * <p>Note (mirrors C++ comment): "some interpolators might take you out of
 * the [-1,1] correlation domain" — bicubic splines may overshoot at the
 * grid boundaries. Caller must verify the result is admissible.
 *
 * <p>Phase 4m.7c-c.
 */
public class BicubicBaseCorrelationTermStructure extends BaseCorrelationTermStructure {

    public BicubicBaseCorrelationTermStructure(
            final @Natural int settlementDays,
            final Calendar cal,
            final BusinessDayConvention bdc,
            final List<Period> tenors,
            final List<Double> lossLevel,
            final List<List<Handle<Quote>>> correls,
            final DayCounter dc) {
        super(settlementDays, cal, bdc, tenors, lossLevel, correls, dc);
    }

    @Override
    protected void setupInterpolation() {
        // Mirrors C++:
        //   interpolation_ = BicubicSpline(trancheTimes_.begin(),
        //       trancheTimes_.end(), lossLevel_.begin(), lossLevel_.end(),
        //       correlations_);
        this.interpolation = new BicubicSplineInterpolation(
                trancheTimesArray(), lossLevelArray(), this.correlations);
    }
}
