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
 Copyright (C) 2006 Giorgio Facchinetti
*/

package org.jquantlib.model.marketmodels.products.multistep;

import org.jquantlib.model.marketmodels.CurveState;
import org.jquantlib.model.marketmodels.MarketModelMultiProduct;
import org.jquantlib.model.marketmodels.Utilities;
import org.jquantlib.model.marketmodels.products.MultiProductMultiStep;

/**
 * Multi-step Ratchet (full-ratchet variant).
 * <p>
 * Mirrors C++ {@code class MultiStepRatchet} (ql/models/marketmodels/products/multistep/multistepratchet.{hpp,cpp}
 * v1.42.1).
 * <p>
 * Per step the coupon is {@code max(gearingOfFloor*floor + spreadOfFloor, gearingOfFixing*libor + spreadOfFixing)}; the
 * floor is then updated to this coupon (full-ratchet semantics).
 *
 * @author Jose Moya
 */
public class MultiStepRatchet extends MultiProductMultiStep {

    private final double[] accruals_;
    private final double[] paymentTimes_;
    private final double gearingOfFloor_;
    private final double gearingOfFixing_;
    private final double spreadOfFloor_;
    private final double spreadOfFixing_;
    private final double multiplier_;
    private final boolean payer_;
    private final int lastIndex_;
    private final double initialFloor_;
    // path-varying state
    private double floor_;
    private int currentIndex_;

    public MultiStepRatchet(final double[] rateTimes, final double[] accruals, final double[] paymentTimes,
            final double gearingOfFloor, final double gearingOfFixing, final double spreadOfFloor,
            final double spreadOfFixing, final double initialFloor, final boolean payer) {
        super(rateTimes);
        this.accruals_ = accruals.clone();
        this.paymentTimes_ = paymentTimes.clone();
        this.gearingOfFloor_ = gearingOfFloor;
        this.gearingOfFixing_ = gearingOfFixing;
        this.spreadOfFloor_ = spreadOfFloor;
        this.spreadOfFixing_ = spreadOfFixing;
        this.payer_ = payer;
        this.multiplier_ = payer ? 1.0 : -1.0;
        this.lastIndex_ = rateTimes.length - 1;
        this.initialFloor_ = initialFloor;
        Utilities.checkIncreasingTimes(this.paymentTimes_);
    }

    public MultiStepRatchet(final double[] rateTimes, final double[] accruals, final double[] paymentTimes,
            final double gearingOfFloor, final double gearingOfFixing, final double spreadOfFloor,
            final double spreadOfFixing, final double initialFloor) {
        this(rateTimes, accruals, paymentTimes, gearingOfFloor, gearingOfFixing, spreadOfFloor, spreadOfFixing,
                initialFloor, true);
    }

    @Override
    public double[] possibleCashFlowTimes() {
        return paymentTimes_;
    }

    @Override
    public int numberOfProducts() {
        return 1;
    }

    @Override
    public int maxNumberOfCashFlowsPerProductPerStep() {
        return 1;
    }

    @Override
    public void reset() {
        currentIndex_ = 0;
        floor_ = initialFloor_;
    }

    @Override
    public boolean nextTimeStep(final CurveState currentState, final int[] numberCashFlowsThisStep,
            final MarketModelMultiProduct.CashFlow[][] genCashFlows) {
        final double liborRate = currentState.forwardRate(currentIndex_);
        final double currentCoupon = Math.max(gearingOfFloor_ * floor_ + spreadOfFloor_,
                gearingOfFixing_ * liborRate + spreadOfFixing_);

        genCashFlows[0][0].timeIndex = currentIndex_;
        genCashFlows[0][0].amount = multiplier_ * accruals_[currentIndex_] * currentCoupon;

        // full-ratchet: floor advances to current coupon
        floor_ = currentCoupon;
        numberCashFlowsThisStep[0] = 1;
        ++currentIndex_;
        return currentIndex_ == lastIndex_;
    }

    @Override
    public MarketModelMultiProduct clone() {
        return new MultiStepRatchet(rateTimes_, accruals_, paymentTimes_, gearingOfFloor_, gearingOfFixing_,
                spreadOfFloor_, spreadOfFixing_, initialFloor_, payer_);
    }
}
