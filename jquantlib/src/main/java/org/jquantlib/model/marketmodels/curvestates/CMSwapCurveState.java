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
 Copyright (C) 2006, 2007 Ferdinando Ametrano
 Copyright (C) 2007 François du Vignaud
 Copyright (C) 2006, 2007 Mark Joshi
*/

package org.jquantlib.model.marketmodels.curvestates;

import org.jquantlib.QL;
import org.jquantlib.model.marketmodels.CurveState;

import java.util.Arrays;

/**
 * %Curve state for constant-maturity-swap market models.
 *
 * <p>Java port of {@code ql/models/marketmodels/curvestates/cmswapcurvestate.{hpp,cpp}}
 * (QuantLib v1.42.1).
 */
public class CMSwapCurveState extends CurveState {

    private final int spanningFwds_;
    private int first_;
    private final double[] discRatios_;
    private final double[] forwardRates_;
    // fixed number of spanning forwards
    private final double[] cmSwapRates_;
    private final double[] cmSwapAnnuities_;
    // irregular number of spanning forwards (lazy)
    private final double[] irrCMSwapRates_;
    private final double[] irrCMSwapAnnuities_;
    private final double[] cotSwapRates_;
    private final double[] cotAnnuities_;

    public CMSwapCurveState(final double[] rateTimes, final int spanningForwards) {
        super(rateTimes);
        this.spanningFwds_ = spanningForwards;
        this.first_ = numberOfRates_;
        this.discRatios_ = new double[numberOfRates_ + 1];
        Arrays.fill(this.discRatios_, 1.0);
        this.forwardRates_ = new double[numberOfRates_];
        this.cmSwapRates_ = new double[numberOfRates_];
        this.cmSwapAnnuities_ = new double[numberOfRates_];
        Arrays.fill(this.cmSwapAnnuities_, rateTaus_[numberOfRates_ - 1]);
        this.irrCMSwapRates_ = new double[numberOfRates_];
        this.irrCMSwapAnnuities_ = new double[numberOfRates_];
        Arrays.fill(this.irrCMSwapAnnuities_, rateTaus_[numberOfRates_ - 1]);
        this.cotSwapRates_ = new double[numberOfRates_];
        this.cotAnnuities_ = new double[numberOfRates_];
        Arrays.fill(this.cotAnnuities_, rateTaus_[numberOfRates_ - 1]);
    }

    private CMSwapCurveState(final CMSwapCurveState other) {
        super(other.rateTimes_);
        this.spanningFwds_ = other.spanningFwds_;
        this.first_ = other.first_;
        this.discRatios_ = other.discRatios_.clone();
        this.forwardRates_ = other.forwardRates_.clone();
        this.cmSwapRates_ = other.cmSwapRates_.clone();
        this.cmSwapAnnuities_ = other.cmSwapAnnuities_.clone();
        this.irrCMSwapRates_ = other.irrCMSwapRates_.clone();
        this.irrCMSwapAnnuities_ = other.irrCMSwapAnnuities_.clone();
        this.cotSwapRates_ = other.cotSwapRates_.clone();
        this.cotAnnuities_ = other.cotAnnuities_.clone();
    }

    public void setOnCMSwapRates(final double[] rates) {
        setOnCMSwapRates(rates, 0);
    }

    public void setOnCMSwapRates(final double[] rates, final int firstValidIndex) {
        QL.require(rates.length == numberOfRates_,
                "rates mismatch: " + numberOfRates_ + " required, " + rates.length + " provided");
        QL.require(firstValidIndex < numberOfRates_,
                "first valid index must be less than " + numberOfRates_ + ": " + firstValidIndex + " not allowed");

        first_ = firstValidIndex;
        System.arraycopy(rates, first_, cmSwapRates_, first_, numberOfRates_ - first_);

        // Joshi-Liesch eq 6.1
        int oldAnnuityEndIndex = numberOfRates_;
        for ( int i = numberOfRates_ - 1; i > first_; --i ) {
            final int endIndex = Math.min(i + spanningFwds_, numberOfRates_);
            final int annuityEndIndex = Math.min(i + spanningFwds_ - 1, numberOfRates_);

            discRatios_[i] = discRatios_[endIndex] + cmSwapRates_[i] * cmSwapAnnuities_[i];
            cmSwapAnnuities_[i - 1] = cmSwapAnnuities_[i] + discRatios_[i] * rateTaus_[i - 1];

            if ( annuityEndIndex < oldAnnuityEndIndex ) {
                cmSwapAnnuities_[i - 1] -= discRatios_[oldAnnuityEndIndex] * rateTaus_[oldAnnuityEndIndex - 1];
            }
            oldAnnuityEndIndex = annuityEndIndex;
        }
        final int endIndex = Math.min(first_ + spanningFwds_, numberOfRates_);
        discRatios_[first_] = discRatios_[endIndex] + cmSwapRates_[first_] * cmSwapAnnuities_[first_];
    }

    @Override
    public double discountRatio(final int i, final int j) {
        QL.require(first_ < numberOfRates_, "curve state not initialized yet");
        QL.require(Math.min(i, j) >= first_, "invalid index");
        QL.require(Math.max(i, j) <= numberOfRates_, "invalid index");
        return discRatios_[i] / discRatios_[j];
    }

    @Override
    public double forwardRate(final int i) {
        QL.require(first_ < numberOfRates_, "curve state not initialized yet");
        QL.require(i >= first_ && i <= numberOfRates_, "invalid index");
        forwardsFromDiscountRatios(first_, discRatios_, rateTaus_, forwardRates_);
        return forwardRates_[i];
    }

    @Override
    public double coterminalSwapAnnuity(final int numeraire, final int i) {
        QL.require(first_ < numberOfRates_, "curve state not initialized yet");
        QL.require(numeraire >= first_ && numeraire <= numberOfRates_, "invalid numeraire");
        QL.require(i >= first_ && i <= numberOfRates_, "invalid index");
        coterminalFromDiscountRatios(first_, discRatios_, rateTaus_, cotSwapRates_, cotAnnuities_);
        return cotAnnuities_[i] / discRatios_[numeraire];
    }

    @Override
    public double coterminalSwapRate(final int i) {
        QL.require(first_ < numberOfRates_, "curve state not initialized yet");
        QL.require(i >= first_ && i <= numberOfRates_, "invalid index");
        coterminalFromDiscountRatios(first_, discRatios_, rateTaus_, cotSwapRates_, cotAnnuities_);
        return cotSwapRates_[i];
    }

    @Override
    public double cmSwapAnnuity(final int numeraire, final int i, final int spanningForwards) {
        QL.require(first_ < numberOfRates_, "curve state not initialized yet");
        QL.require(numeraire >= first_ && numeraire <= numberOfRates_, "invalid numeraire");
        QL.require(i >= first_ && i <= numberOfRates_, "invalid index");
        if ( spanningForwards == spanningFwds_ ) {
            return cmSwapAnnuities_[i] / discRatios_[numeraire];
        } else {
            constantMaturityFromDiscountRatios(spanningForwards, first_, discRatios_, rateTaus_, irrCMSwapRates_,
                    irrCMSwapAnnuities_);
            return irrCMSwapAnnuities_[i] / discRatios_[numeraire];
        }
    }

    @Override
    public double cmSwapRate(final int i, final int spanningForwards) {
        QL.require(first_ < numberOfRates_, "curve state not initialized yet");
        QL.require(i >= first_ && i <= numberOfRates_, "invalid index");
        if ( spanningForwards == spanningFwds_ ) {
            return cmSwapRates_[i];
        } else {
            constantMaturityFromDiscountRatios(spanningForwards, first_, discRatios_, rateTaus_, irrCMSwapRates_,
                    irrCMSwapAnnuities_);
            return irrCMSwapRates_[i];
        }
    }

    @Override
    public double[] forwardRates() {
        QL.require(first_ < numberOfRates_, "curve state not initialized yet");
        forwardsFromDiscountRatios(first_, discRatios_, rateTaus_, forwardRates_);
        return forwardRates_;
    }

    @Override
    public double[] coterminalSwapRates() {
        QL.require(first_ < numberOfRates_, "curve state not initialized yet");
        coterminalFromDiscountRatios(first_, discRatios_, rateTaus_, cotSwapRates_, cotAnnuities_);
        return cotSwapRates_;
    }

    @Override
    public double[] cmSwapRates(final int spanningForwards) {
        QL.require(first_ < numberOfRates_, "curve state not initialized yet");
        if ( spanningForwards == spanningFwds_ ) {
            return cmSwapRates_;
        } else {
            constantMaturityFromDiscountRatios(spanningForwards, first_, discRatios_, rateTaus_, irrCMSwapRates_,
                    irrCMSwapAnnuities_);
            return irrCMSwapRates_;
        }
    }

    @Override
    public CurveState clone() {
        return new CMSwapCurveState(this);
    }
}
