/*
 Copyright (C) 2026 Jose Moya

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
 Copyright (C) 2006 Mark Joshi
*/

package org.jquantlib.model.marketmodels;

/**
 * Discount-factor calculator for an arbitrary payment time, expressed in units of a numeraire bond using a
 * {@link CurveState}.
 *
 * <p>The constructor identifies the rate-time index immediately before the
 * payment time and computes the linear-interpolation weight {@code beforeWeight_} between {@code rateTimes[before_]}
 * and {@code rateTimes[before_+1]}. The accessor {@link #numeraireBonds(CurveState, int)} returns the value
 * {@code preDF^w * postDF^(1-w)} where {@code preDF}, {@code postDF} are discount-ratios from the curve state evaluated
 * at the bracketing rate indices vs the numeraire.
 *
 * @author Jose Moya
 * @see "ql/models/marketmodels/discounter.{hpp,cpp}" v1.42.1
 */
public class MarketModelDiscounter {

    private final int before_;
    private final double beforeWeight_;

    public MarketModelDiscounter(final double paymentTime, final double[] rateTimes) {
        Utilities.checkIncreasingTimes(rateTimes);

        // C++: lower_bound returns first index whose value is NOT less than paymentTime
        int b = lowerBound(rateTimes, paymentTime);
        // handle the case where the payment is in the last period or after
        if ( b > rateTimes.length - 2 ) {
            b = rateTimes.length - 2;
        }
        this.before_ = b;
        this.beforeWeight_ = 1.0 - (paymentTime - rateTimes[before_]) / (rateTimes[before_ + 1] - rateTimes[before_]);
    }

    /**
     * Mirrors {@code std::lower_bound}: returns the first index {@code i} in {@code arr[0..arr.length]} such that
     * {@code arr[i] >= value}, or {@code arr.length} if no such index exists.
     */
    private static int lowerBound(final double[] arr, final double value) {
        int lo = 0;
        int hi = arr.length;
        while ( lo < hi ) {
            final int mid = (lo + hi) >>> 1;
            if ( arr[mid] < value ) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    public double numeraireBonds(final CurveState curveState, final int numeraire) {
        final double preDF = curveState.discountRatio(before_, numeraire);
        if ( beforeWeight_ == 1.0 ) {
            return preDF;
        }
        final double postDF = curveState.discountRatio(before_ + 1, numeraire);
        if ( beforeWeight_ == 0.0 ) {
            return postDF;
        }
        return Math.pow(preDF, beforeWeight_) * Math.pow(postDF, 1.0 - beforeWeight_);
    }
}
