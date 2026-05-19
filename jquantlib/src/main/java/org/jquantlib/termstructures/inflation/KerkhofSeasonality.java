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

/*
 Copyright (C) 2015 Bernd Lewerenz

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.termstructures.inflation;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.lang.annotation.Rate;
import org.jquantlib.lang.annotation.Time;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.transcendental.JQuantMath;
import org.jquantlib.termstructures.InflationTermStructure;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.util.Pair;

/**
 * Kerkhof multiplicative seasonality on monthly factors. The {@code i}-th factor multiplies into the cumulative product
 * as the date crosses month boundaries; rates are corrected by raising this product to the inverse of the
 * time-from-curve-base.
 *
 * <p>Mirrors C++ {@code QuantLib::KerkhofSeasonality} at v1.42.1
 * (termstructures/inflation/seasonality.{hpp,cpp}).
 *
 * @author JQuantLib migration team (Phase 2q C.2)
 */
public class KerkhofSeasonality extends MultiplicativePriceSeasonality {

    public KerkhofSeasonality(final Date seasonalityBaseDate, final double[] seasonalityFactors) {
        super(seasonalityBaseDate, Frequency.Monthly, seasonalityFactors);
    }

    @Override
    public double seasonalityFactor(final Date to) {
        int dir = 1;
        final Date from = seasonalityBaseDate();
        int fromMonth = from.month().value();
        int toMonth = to.month().value();

        final Period factorPeriod = new Period(frequency());

        if ( toMonth < fromMonth ) {
            final int dummy = fromMonth;
            fromMonth = toMonth;
            toMonth = dummy;
            dir = 0; // we calculate inverse Factor in loop
        }

        QL.require(seasonalityFactors().length == 12 && factorPeriod.units() == TimeUnit.Months,
                "12 monthly seasonal factors needed for Kerkhof Seasonality: got " + seasonalityFactors().length);

        double seasonalCorrection = 1.0;
        final double[] factors = seasonalityFactors();
        for ( int i = fromMonth; i < toMonth; ++i ) {
            seasonalCorrection *= factors[i];
        }

        if ( dir == 0 ) {
            seasonalCorrection = 1.0 / seasonalCorrection;
        }
        return seasonalCorrection;
    }

    @Override
    protected /*@Rate*/ double seasonalityCorrection(final @Rate double rate, final Date atDate, final DayCounter dc,
            final Date curveBaseDate, final boolean isZeroRate) {
        final double indexFactor = seasonalityFactor(atDate);

        final double f;
        if ( isZeroRate ) {
            final Pair< Date, Date > lim = InflationTermStructure.inflationPeriod(curveBaseDate, Frequency.Monthly);
            final @Time double timeFromCurveBase = dc.yearFraction(lim.first(), atDate);
            f = JQuantMath.pow(indexFactor, 1.0 / timeFromCurveBase);
        } else {
            throw new LibraryException("Seasonal Kerkhof model is not defined on YoY rates");
        }
        return (rate + 1.0) * f - 1.0;
    }
}
