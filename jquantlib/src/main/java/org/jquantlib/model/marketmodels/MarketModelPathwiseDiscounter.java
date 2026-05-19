/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k Track C C.6.

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
 Copyright (C) 2008 Mark Joshi
*/

package org.jquantlib.model.marketmodels;

import org.jquantlib.math.matrixutilities.Matrix;

/**
 * Pathwise discount-factor calculator with rate-derivative output.
 *
 * <p>Returns the number of units of the discretely-compounding money-market
 * account that one unit of cash at the payment time can buy, using the LIBOR rates from the current step. Crucially,
 * also returns the partial derivative of that number with respect to each LIBOR rate — which is what the
 * Giles-Glasserman pathwise-Greeks adjoint engine needs.
 *
 * <p>Discounting is purely based on the simulation LIBOR rates; to get a
 * discounting back to t_0 multiply the {@code factors[0]} entry by the discount factor of t_0.
 *
 * <p>Mirrors C++ {@code MarketModelPathwiseDiscounter}
 * (ql/models/marketmodels/pathwisediscounter.{hpp,cpp} v1.42.1).
 *
 * @author Jose Moya
 * @see MarketModelDiscounter
 */
public class MarketModelPathwiseDiscounter {

    private final int before_;
    private final int numberRates_;
    private final double beforeWeight_;
    private final double postWeight_;
    private final double[] taus_;

    public MarketModelPathwiseDiscounter(final double paymentTime, final double[] rateTimes) {
        Utilities.checkIncreasingTimes(rateTimes);

        this.numberRates_ = rateTimes.length - 1;

        // C++ lower_bound: first index whose value is >= paymentTime
        int b = lowerBound(rateTimes, paymentTime);
        if ( b > rateTimes.length - 2 ) {
            b = rateTimes.length - 2;
        }
        this.before_ = b;

        this.beforeWeight_ = 1.0 - (paymentTime - rateTimes[before_]) / (rateTimes[before_ + 1] - rateTimes[before_]);
        this.postWeight_ = 1.0 - beforeWeight_;

        this.taus_ = new double[numberRates_];
        for ( int i = 0; i < numberRates_; ++i ) {
            taus_[i] = rateTimes[i + 1] - rateTimes[i];
        }
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

    /**
     * Computes the discount factor and its derivative with respect to each forward rate, at the given simulation step.
     *
     * <p>Mirrors C++
     * {@code void getFactors(const Matrix& LIBORRates, const Matrix& Discounts, Size currentStep, std::vector<Real>&
     * factors)}.
     *
     * @param LIBORRates  unused in this implementation (preserved for API parity with the C++ signature; the discount
     *                    factor is read out of {@code Discounts} directly)
     * @param Discounts   matrix of {@code P(t_0, t_j)} for each step, dimensions [numberSteps][numberRates+1]
     * @param currentStep current evolution step index
     * @param factors     output vector of length {@code numberRates+1}; on return {@code factors[0]} = discount factor;
     *                    {@code factors[i]} for {@code i ≥ 1} = partial derivative w.r.t. forward rate {@code i-1}
     */
    public void getFactors(final Matrix LIBORRates, final Matrix Discounts, final int currentStep,
            final double[] factors) {
        final double preDF = Discounts.get(currentStep, before_);
        final double postDF = Discounts.get(currentStep, before_ + 1);

        for ( int i = before_ + 1; i < numberRates_; ++i ) {
            factors[i + 1] = 0.0;
        }

        if ( postWeight_ == 0.0 ) {
            factors[0] = preDF;

            for ( int i = 0; i < before_; ++i ) {
                factors[i + 1] = -preDF * taus_[i] * Discounts.get(currentStep, i + 1) / Discounts.get(currentStep, i);
            }

            factors[before_ + 1] = 0.0;
            return;
        }

        final double df = preDF * Math.pow(postDF / preDF, postWeight_);

        factors[0] = df;

        for ( int i = 0; i <= before_; ++i ) {
            factors[i + 1] = -df * taus_[i] * Discounts.get(currentStep, i + 1) / Discounts.get(currentStep, i);
        }

        factors[before_ + 1] *= postWeight_;
    }
}
