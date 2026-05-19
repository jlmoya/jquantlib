/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k A.9.

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
 Copyright (C) 2007 Mark Joshi
*/

package org.jquantlib.model.marketmodels.products.multistep;

import org.jquantlib.QL;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.model.marketmodels.CurveState;
import org.jquantlib.model.marketmodels.MarketModelMultiProduct;
import org.jquantlib.model.marketmodels.Utilities;
import org.jquantlib.model.marketmodels.products.MultiProductMultiStep;

/**
 * Multi-step Period Caplets and Swaptions — at each period boundary emits one caplet and one swaption
 * (period-aggregated structures).
 * <p>
 * Mirrors C++ {@code class MultiStepPeriodCapletSwaptions}
 * (ql/models/marketmodels/products/multistep/multistepperiodcapletswaptions.{hpp,cpp} v1.42.1).
 *
 * @author Jose Moya
 */
public class MultiStepPeriodCapletSwaptions extends MultiProductMultiStep {

    private final double[] paymentTimes_;
    private final double[] forwardOptionPaymentTimes_;
    private final double[] swaptionPaymentTimes_;
    private final StrikedTypePayoff[] forwardPayOffs_;
    private final StrikedTypePayoff[] swapPayOffs_;
    private final int lastIndex_;
    private final int period_;
    private final int offset_;
    private final int numberFRAs_;
    private final int numberBigFRAs_;
    // path-varying state
    private int currentIndex_;
    private int productIndex_;

    public MultiStepPeriodCapletSwaptions(final double[] rateTimes, final double[] forwardOptionPaymentTimes,
            final double[] swaptionPaymentTimes, final StrikedTypePayoff[] forwardPayOffs,
            final StrikedTypePayoff[] swapPayOffs, final int period, final int offset) {
        super(rateTimes);
        QL.require(rateTimes.length >= 2, "we need at least two rate times in MultiStepPeriodCapletSwaptions");
        Utilities.checkIncreasingTimes(forwardOptionPaymentTimes);
        Utilities.checkIncreasingTimes(swaptionPaymentTimes);

        // paymentTimes_ = forwardOptionPaymentTimes ++ swaptionPaymentTimes
        this.paymentTimes_ = new double[forwardOptionPaymentTimes.length + swaptionPaymentTimes.length];
        System.arraycopy(forwardOptionPaymentTimes, 0, paymentTimes_, 0, forwardOptionPaymentTimes.length);
        System.arraycopy(swaptionPaymentTimes, 0, paymentTimes_, forwardOptionPaymentTimes.length,
                swaptionPaymentTimes.length);

        this.forwardOptionPaymentTimes_ = forwardOptionPaymentTimes.clone();
        this.swaptionPaymentTimes_ = swaptionPaymentTimes.clone();
        this.forwardPayOffs_ = forwardPayOffs.clone();
        this.swapPayOffs_ = swapPayOffs.clone();
        this.period_ = period;
        this.offset_ = offset;
        this.lastIndex_ = rateTimes.length - 1;
        this.numberFRAs_ = rateTimes.length - 1;
        this.numberBigFRAs_ = (numberFRAs_ - offset_) / period_;

        QL.require(offset_ < period_, "the offset must be less than the period in MultiStepPeriodCapletSwaptions");
        QL.require(numberBigFRAs_ > 0, "we must have at least one FRA after the periodizing");
        QL.require(forwardOptionPaymentTimes_.length == numberBigFRAs_,
                "we must have precisely one payment time for each forward option");
        QL.require(forwardPayOffs_.length == numberBigFRAs_,
                "we must have precisely one payoff for each forward option");
        QL.require(swaptionPaymentTimes_.length == numberBigFRAs_,
                "we must have precisely one payment time for each swaption");
        QL.require(swapPayOffs_.length == numberBigFRAs_, "we must have precisely one payoff for each swaption");
    }

    @Override
    public double[] possibleCashFlowTimes() {
        return paymentTimes_;
    }

    @Override
    public int numberOfProducts() {
        return numberBigFRAs_ * 2;
    }

    @Override
    public int maxNumberOfCashFlowsPerProductPerStep() {
        return 1;
    }

    @Override
    public void reset() {
        currentIndex_ = 0;
        productIndex_ = 0;
    }

    @Override
    public boolean nextTimeStep(final CurveState currentState, final int[] numberCashFlowsThisStep,
            final MarketModelMultiProduct.CashFlow[][] genCashFlows) {
        for ( int i = 0; i < numberCashFlowsThisStep.length; ++i ) {
            numberCashFlowsThisStep[i] = 0;
        }

        if ( currentIndex_ >= offset_ && (currentIndex_ - offset_) % period_ == 0 ) {
            // caplet first
            final double df = currentState.discountRatio(currentIndex_ + period_, currentIndex_);
            final double tau = rateTimes_[currentIndex_ + period_] - rateTimes_[currentIndex_];
            final double forward = (1.0 / df - 1.0) / tau;
            double value = forwardPayOffs_[productIndex_].get(forward);
            value *= tau * currentState.discountRatio(currentIndex_ + period_, currentIndex_);

            if ( value > 0 ) {
                numberCashFlowsThisStep[productIndex_] = 1;
                genCashFlows[productIndex_][0].amount = value;
                genCashFlows[productIndex_][0].timeIndex = productIndex_;
            }

            // now swaption
            final int numberPeriods = numberBigFRAs_ - productIndex_;
            double B = 0.0;
            final double P0 = 1.0; // i.e. discountRatio(currentIndex, currentIndex)
            final double Pn = currentState.discountRatio(currentIndex_ + numberPeriods * period_, currentIndex_);
            for ( int i = 0; i < numberPeriods; ++i ) {
                final double t =
                        rateTimes_[currentIndex_ + (i + 1) * period_] - rateTimes_[currentIndex_ + i * period_];
                B += t * currentState.discountRatio(currentIndex_ + (i + 1) * period_, currentIndex_);
            }

            final double swapRate = (P0 - Pn) / B;
            double swaptionValue = swapPayOffs_[productIndex_].get(swapRate);
            swaptionValue *= B;

            if ( swaptionValue > 0 ) {
                numberCashFlowsThisStep[productIndex_ + numberBigFRAs_] = 1;
                genCashFlows[productIndex_ + numberBigFRAs_][0].amount = swaptionValue;
                genCashFlows[productIndex_ + numberBigFRAs_][0].timeIndex = productIndex_ + numberBigFRAs_;
            }

            ++productIndex_;
        }

        ++currentIndex_;
        return productIndex_ >= numberBigFRAs_;
    }

    @Override
    public MarketModelMultiProduct clone() {
        return new MultiStepPeriodCapletSwaptions(rateTimes_, forwardOptionPaymentTimes_, swaptionPaymentTimes_,
                forwardPayOffs_, swapPayOffs_, period_, offset_);
    }
}
