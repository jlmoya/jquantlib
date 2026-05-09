/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k A.8.

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
 Copyright (C) 2010 Mark Joshi
*/

package org.jquantlib.model.marketmodels.products.multistep;

import org.jquantlib.QL;
import org.jquantlib.model.marketmodels.CurveState;
import org.jquantlib.model.marketmodels.MarketModelMultiProduct;
import org.jquantlib.model.marketmodels.products.MultiProductMultiStep;

/**
 * Multi-step Target Accrual Redemption Note (TARN).
 * <p>
 * Mirrors C++ {@code class MultiStepTarn}
 * (ql/models/marketmodels/products/multistep/multisteptarn.{hpp,cpp} v1.42.1).
 * <p>
 * Pays a floating coupon and an inverse-floating coupon each step until
 * accumulated inverse-floating coupons reach the target {@code totalCoupon};
 * the final payment is truncated to ensure exactly {@code totalCoupon} has
 * been paid.
 *
 * @author Jose Moya
 */
public class MultiStepTarn extends MultiProductMultiStep {

    private final double[] accruals_;
    private final double[] accrualsFloating_;
    private final double[] paymentTimes_;
    private final double[] paymentTimesFloating_;
    private final double[] allPaymentTimes_;
    private final double totalCoupon_;
    private final double[] strikes_;
    private final double[] multipliers_;
    private final double[] floatingSpreads_;
    private final int lastIndex_;
    // path-varying state
    private double couponPaid_;
    private int currentIndex_;

    public MultiStepTarn(final double[] rateTimes,
                         final double[] accruals,
                         final double[] accrualsFloating,
                         final double[] paymentTimes,
                         final double[] paymentTimesFloating,
                         final double totalCoupon,
                         final double[] strikes,
                         final double[] multipliers,
                         final double[] floatingSpreads) {
        super(rateTimes);
        QL.require(accruals.length + 1 == rateTimes.length, "missized accruals in MultiStepTARN");
        QL.require(accrualsFloating.length + 1 == rateTimes.length, "missized accrualsFloating in MultiStepTARN");
        QL.require(paymentTimes.length + 1 == rateTimes.length, "missized paymentTimes in MultiStepTARN");
        QL.require(paymentTimesFloating.length + 1 == rateTimes.length, "missized paymentTimesFloating in MultiStepTARN");
        QL.require(strikes.length + 1 == rateTimes.length, "missized strikes in MultiStepTARN");
        QL.require(floatingSpreads.length + 1 == rateTimes.length, "missized floatingSpreads in MultiStepTARN");

        this.accruals_ = accruals.clone();
        this.accrualsFloating_ = accrualsFloating.clone();
        this.paymentTimes_ = paymentTimes.clone();
        this.paymentTimesFloating_ = paymentTimesFloating.clone();
        this.totalCoupon_ = totalCoupon;
        this.strikes_ = strikes.clone();
        this.multipliers_ = multipliers.clone();
        this.floatingSpreads_ = floatingSpreads.clone();
        this.lastIndex_ = accruals.length;

        // C++: allPaymentTimes_ = paymentTimes; then push_back paymentTimes again
        // for each i in [0, paymentTimesFloating.size()).
        // (Note: original C++ has `paymentTimes[i]` not `paymentTimesFloating[i]`!
        // This is preserved exactly.)
        final int extra = paymentTimesFloating.length;
        this.allPaymentTimes_ = new double[paymentTimes.length + extra];
        System.arraycopy(paymentTimes, 0, allPaymentTimes_, 0, paymentTimes.length);
        for (int i = 0; i < extra; ++i) {
            allPaymentTimes_[paymentTimes.length + i] = paymentTimes[i];
        }
    }

    @Override
    public double[] possibleCashFlowTimes() { return allPaymentTimes_; }

    @Override
    public int numberOfProducts() { return 1; }

    @Override
    public int maxNumberOfCashFlowsPerProductPerStep() { return 2; }

    @Override
    public void reset() {
        currentIndex_ = 0;
        couponPaid_ = 0.0;
    }

    @Override
    public boolean nextTimeStep(final CurveState currentState,
                                final int[] numberCashFlowsThisStep,
                                final MarketModelMultiProduct.CashFlow[][] genCashFlows) {
        final double liborRate = currentState.forwardRate(currentIndex_);

        numberCashFlowsThisStep[0] = 2;

        genCashFlows[0][0].amount =
                (liborRate + floatingSpreads_[currentIndex_]) * accrualsFloating_[currentIndex_];
        genCashFlows[0][0].timeIndex = lastIndex_ + currentIndex_;

        genCashFlows[0][1].timeIndex = currentIndex_;

        final double obviousCoupon =
                Math.max(strikes_[currentIndex_] - multipliers_[currentIndex_] * liborRate, 0.0)
                        * accruals_[currentIndex_];

        couponPaid_ += obviousCoupon;
        ++currentIndex_;

        if (couponPaid_ < totalCoupon_ && currentIndex_ < lastIndex_) {
            genCashFlows[0][1].amount = -obviousCoupon;
            return false;
        }

        // truncate the last coupon so that exactly totalCoupon is paid
        final double coupon = obviousCoupon + (totalCoupon_ - couponPaid_);
        genCashFlows[0][1].amount = -coupon;
        return true;
    }

    @Override
    public MarketModelMultiProduct clone() {
        return new MultiStepTarn(rateTimes_, accruals_, accrualsFloating_, paymentTimes_,
                paymentTimesFloating_, totalCoupon_, strikes_, multipliers_, floatingSpreads_);
    }
}
