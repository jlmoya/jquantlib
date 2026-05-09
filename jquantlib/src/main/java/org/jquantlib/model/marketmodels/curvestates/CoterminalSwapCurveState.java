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
 Copyright (C) 2007 Ferdinando Ametrano
 Copyright (C) 2007 Marco Bianchetti
 Copyright (C) 2007 Cristina Duminuco
*/

package org.jquantlib.model.marketmodels.curvestates;

import java.util.Arrays;

import org.jquantlib.QL;
import org.jquantlib.model.marketmodels.CurveState;

/**
 * %Curve state for coterminal-swap market models.
 *
 * <p>Java port of {@code ql/models/marketmodels/curvestates/coterminalswapcurvestate.{hpp,cpp}}
 * (QuantLib v1.42.1).
 *
 * <p>Initialized via {@link #setOnCoterminalSwapRates(double[], int)}.
 */
public class CoterminalSwapCurveState extends CurveState {

    private int first_;
    private double[] discRatios_;
    private double[] forwardRates_;
    private double[] cmSwapRates_;
    private double[] cmSwapAnnuities_;
    private double[] cotSwapRates_;
    private double[] cotAnnuities_;

    public CoterminalSwapCurveState(final double[] rateTimes) {
        super(rateTimes);
        this.first_ = numberOfRates_;
        this.discRatios_ = new double[numberOfRates_ + 1];
        Arrays.fill(this.discRatios_, 1.0);
        this.forwardRates_ = new double[numberOfRates_];
        this.cmSwapRates_ = new double[numberOfRates_];
        this.cmSwapAnnuities_ = new double[numberOfRates_];
        Arrays.fill(this.cmSwapAnnuities_, rateTaus_[numberOfRates_ - 1]);
        this.cotSwapRates_ = new double[numberOfRates_];
        this.cotAnnuities_ = new double[numberOfRates_];
        Arrays.fill(this.cotAnnuities_, rateTaus_[numberOfRates_ - 1]);
    }

    private CoterminalSwapCurveState(final CoterminalSwapCurveState other) {
        super(other.rateTimes_);
        this.first_ = other.first_;
        this.discRatios_ = other.discRatios_.clone();
        this.forwardRates_ = other.forwardRates_.clone();
        this.cmSwapRates_ = other.cmSwapRates_.clone();
        this.cmSwapAnnuities_ = other.cmSwapAnnuities_.clone();
        this.cotSwapRates_ = other.cotSwapRates_.clone();
        this.cotAnnuities_ = other.cotAnnuities_.clone();
    }

    public void setOnCoterminalSwapRates(final double[] rates) {
        setOnCoterminalSwapRates(rates, 0);
    }

    public void setOnCoterminalSwapRates(final double[] rates, final int firstValidIndex) {
        QL.require(rates.length == numberOfRates_,
                "rates mismatch: " + numberOfRates_ + " required, " + rates.length + " provided");
        QL.require(firstValidIndex < numberOfRates_,
                "first valid index must be less than " + numberOfRates_ + ": "
                        + firstValidIndex + " not allowed");

        first_ = firstValidIndex;
        System.arraycopy(rates, first_, cotSwapRates_, first_, numberOfRates_ - first_);

        // Reference: discRatios_[numberOfRates_] = P(n)/P(n) = 1.0 (set in ctor)
        cotAnnuities_[numberOfRates_ - 1] = rateTaus_[numberOfRates_ - 1];
        for (int i = numberOfRates_ - 1; i > first_; --i) {
            discRatios_[i] = 1.0 + cotSwapRates_[i] * cotAnnuities_[i];
            cotAnnuities_[i - 1] = cotAnnuities_[i] + rateTaus_[i - 1] * discRatios_[i];
        }
        discRatios_[first_] = 1.0 + cotSwapRates_[first_] * cotAnnuities_[first_];
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
        return cotAnnuities_[i] / discRatios_[numeraire];
    }

    @Override
    public double coterminalSwapRate(final int i) {
        QL.require(first_ < numberOfRates_, "curve state not initialized yet");
        QL.require(i >= first_ && i <= numberOfRates_, "invalid index");
        return cotSwapRates_[i];
    }

    @Override
    public double cmSwapAnnuity(final int numeraire, final int i, final int spanningForwards) {
        QL.require(first_ < numberOfRates_, "curve state not initialized yet");
        QL.require(numeraire >= first_ && numeraire <= numberOfRates_, "invalid numeraire");
        QL.require(i >= first_ && i <= numberOfRates_, "invalid index");
        constantMaturityFromDiscountRatios(spanningForwards, first_,
                discRatios_, rateTaus_, cmSwapRates_, cmSwapAnnuities_);
        return cmSwapAnnuities_[i] / discRatios_[numeraire];
    }

    @Override
    public double cmSwapRate(final int i, final int spanningForwards) {
        QL.require(first_ < numberOfRates_, "curve state not initialized yet");
        QL.require(i >= first_ && i <= numberOfRates_, "invalid index");
        constantMaturityFromDiscountRatios(spanningForwards, first_,
                discRatios_, rateTaus_, cmSwapRates_, cmSwapAnnuities_);
        return cmSwapRates_[i];
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
        return cotSwapRates_;
    }

    @Override
    public double[] cmSwapRates(final int spanningForwards) {
        QL.require(first_ < numberOfRates_, "curve state not initialized yet");
        constantMaturityFromDiscountRatios(spanningForwards, first_,
                discRatios_, rateTaus_, cmSwapRates_, cmSwapAnnuities_);
        return cmSwapRates_;
    }

    @Override
    public CurveState clone() {
        return new CoterminalSwapCurveState(this);
    }
}
