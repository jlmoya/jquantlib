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
 Copyright (C) 2007 Chris Kenyon
 Copyright (C) 2021 Ralf Konrad Eckel

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.indexes;

import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.termstructures.InflationTermStructure;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.util.Pair;

/**
 * CPI namespace utilities — observation-interpolation enum and lagged-fixing helper. Mirrors the C++
 * {@code QuantLib::CPI} struct (a namespace bundle of static members) at v1.42.1.
 *
 * <p>Java has no namespaces, so we model this as a final class with private
 * constructor (utility class) and a public nested enum.
 *
 * @author JQuantLib migration team (Phase 2p A.2)
 */
public final class CPI {

    private CPI() {
        // utility class — never instantiated
    }

    /**
     * Interpolated inflation fixing.
     *
     * @param index             the zero-inflation index whose fixing is retrieved
     * @param date              the date without lag; usually, the payment date for some inflation-based coupon
     * @param observationLag    the observation lag to be subtracted from the passed date; for instance, if the passed
     *                          date is in May and the lag is three months, the inflation fixing from February (and
     *                          March, in case of interpolation) will be observed
     * @param interpolationType the interpolation type (flat or linear)
     * @return the interpolated fixing
     */
    public static double laggedFixing(final ZeroInflationIndex index, final Date date, final Period observationLag,
            final InterpolationType interpolationType) {
        switch ( interpolationType ) {
        case AsIndex:
        case Flat: {
            final Pair< Date, Date > fixingPeriod = InflationTermStructure.inflationPeriod(date.sub(observationLag),
                    index.frequency());
            return index.fixing(fixingPeriod.first());
        }
        case Linear: {
            final Pair< Date, Date > fixingPeriod = InflationTermStructure.inflationPeriod(date.sub(observationLag),
                    index.frequency());
            final Pair< Date, Date > interpolationPeriod = InflationTermStructure.inflationPeriod(date,
                    index.frequency());

            final double i0 = index.fixing(fixingPeriod.first());

            if ( date.eq(interpolationPeriod.first()) ) {
                // Special case; no interpolation. Avoids requesting the fixing
                // at the end of the period, which might need a forecast curve
                // to be set.
                return i0;
            }

            final Period oneDay = new Period(1, TimeUnit.Days);
            final double i1 = index.fixing(fixingPeriod.second().add(oneDay));

            final long numerator = date.sub(interpolationPeriod.first());
            final long denominator = (interpolationPeriod.second().add(oneDay)).sub(interpolationPeriod.first());
            return i0 + (i1 - i0) * ((double) numerator) / ((double) denominator);
        }
        default:
            throw new LibraryException("unknown CPI interpolation type: " + interpolationType);
        }
    }

    /**
     * Year-on-year inflation rate, applying observation lag and the requested interpolation between fixings.
     *
     * <p>Mirrors C++ v1.42.1 {@code CPI::laggedYoYRate}
     * ({@code ql/indexes/inflationindex.cpp:65-116}). Used by YoY-coupon pricers and by
     * {@link org.jquantlib.cashflow.YoYInflationCoupon}.
     *
     * <p>The {@code Linear} branch implements the C++ ratio-index special
     * case: for ratio-style {@link YoYInflationIndex} indices whose needed fixings are historical (not forecast), the
     * underlying {@link ZeroInflationIndex} fixings are linearly interpolated first and then divided — mirroring
     * {@code inflationindex.cpp:83-113}.  For quoted (non-ratio) YoY indices the {@code Linear} branch interpolates the
     * YoY rate directly.  Phase 2y A.3 align.
     *
     * @param index             the YoY inflation index whose fixing is observed
     * @param date              the unlagged date (e.g. payment / accrual end)
     * @param observationLag    the observation lag to subtract
     * @param interpolationType {@link InterpolationType#AsIndex}, {@link InterpolationType#Flat}, or
     *                          {@link InterpolationType#Linear}
     */
    public static double laggedYoYRate(final YoYInflationIndex index, final Date date, final Period observationLag,
            final InterpolationType interpolationType) {
        switch ( interpolationType ) {
        case AsIndex:
            return index.fixing(date.sub(observationLag));
        case Flat: {
            final Pair< Date, Date > fixingPeriod = InflationTermStructure.inflationPeriod(date.sub(observationLag),
                    index.frequency());
            return index.fixing(fixingPeriod.first());
        }
        case Linear: {
            // C++ v1.42.1 CPI::laggedYoYRate Linear branch:
            // For ratio indices where the needed fixings are historical
            // (not forecast), interpolate the underlying CPI fixings first
            // and then take the ratio — NOT the same as interpolating the
            // ratio itself.  Mirrors inflationindex.cpp:83-113.
            if ( index.ratio() && index.underlyingIndex() != null && !index.needsForecast(date) ) {
                final ZeroInflationIndex underlying = index.underlyingIndex();
                final double z1 = laggedFixing(underlying, date, observationLag, interpolationType);
                final double z0 = laggedFixing(underlying, date.sub(new Period(1, TimeUnit.Years)), observationLag,
                        interpolationType);
                return z1 / z0 - 1.0;
            }
            // Non-ratio (quoted YoY) or forecast path: interpolate the YoY
            // fixing directly.  Mirrors C++ else-branch in laggedYoYRate.
            {
                final Pair< Date, Date > fixingPeriod = InflationTermStructure.inflationPeriod(date.sub(observationLag),
                        index.frequency());
                final Pair< Date, Date > interpolationPeriod = InflationTermStructure.inflationPeriod(date,
                        index.frequency());

                final double Y0 = index.fixing(fixingPeriod.first());

                if ( date.eq(interpolationPeriod.first()) ) {
                    return Y0;
                }

                final Period oneDay = new Period(1, TimeUnit.Days);
                final double Y1 = index.fixing(fixingPeriod.second().add(oneDay));

                final long numerator = date.sub(interpolationPeriod.first());
                final long denominator = (interpolationPeriod.second().add(oneDay)).sub(interpolationPeriod.first());
                return Y0 + (Y1 - Y0) * ((double) numerator) / ((double) denominator);
            }
        }
        default:
            throw new LibraryException("unknown CPI interpolation type: " + interpolationType);
        }
    }

    /**
     * When you observe a (zero) inflation index, how do you interpolate between fixings?
     */
    public enum InterpolationType {
        /** Same interpolation as the underlying index. */
        AsIndex,
        /** Flat (i.e. piecewise-constant) from previous fixing. */
        Flat,
        /** Linearly between bracketing fixings. */
        Linear
    }
}
