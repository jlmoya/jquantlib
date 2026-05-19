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
import org.jquantlib.termstructures.RateHelper;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.credit.PiecewiseDefaultCurve;
import org.jquantlib.termstructures.inflation.PiecewiseYoYInflationCurve;
import org.jquantlib.termstructures.inflation.PiecewiseZeroInflationCurve;
import org.jquantlib.time.Date;
import org.jquantlib.util.Pair;

import java.util.List;

/**
 * This interface represent a family of piecewise curves.
 *
 * @author Richard Gomes
 * @see PiecewiseYieldCurve
 * @see PiecewiseDefaultCurve
 * @see PiecewiseYoYInflationCurve
 * @see PiecewiseZeroInflationCurve
 */
public interface PiecewiseCurve< I extends Interpolator > extends YieldTermStructure {

    @Override
    Date maxDate() /* @ReadOnly */;

    RateHelper[] instruments() /* @ReadOnly */;

    Date[] dates() /* @ReadOnly */;

    /* @Time */ double[] times() /* @ReadOnly */;

    double accuracy() /* @ReadOnly */;

    Date[] jumpDates() /* @ReadOnly */;

    /* @Time */ double[] jumpTimes() /* @ReadOnly */;

    List< Pair< Date, /* @Rate */Double > > nodes() /* @ReadOnly */;

    double[] data();

    Traits traits() /* @ReadOnly */;

    Interpolator interpolator() /* @ReadOnly */;

    Interpolation interpolation() /* @ReadOnly */;

    void setInterpolation(final Interpolation interpolation);

    //FIXME:: remove these methods. SEE: http://bugs.jquantlib.org/view.php?id=464
    // Ideally, we should employ Array<T> which could mimick closer std::vector (which is a dynamic array).
    // Doing so, we would not be obliged to overwrite an existing
    // data structure, but we could simply rezise it and add more data.
    // Then these 3 methods below could be removed.
    // The same issue happens in Traits.Curve
    void setDates(final Date[] dates);

    void setTimes(/*@Time*/ double[] times);

    void setData(final double[] data);

}
