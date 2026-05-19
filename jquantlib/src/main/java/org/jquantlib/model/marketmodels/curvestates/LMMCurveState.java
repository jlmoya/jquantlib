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
 Copyright (C) 2006 Marco Bianchetti
 Copyright (C) 2006 Cristina Duminuco
 Copyright (C) 2006 Giorgio Facchinetti
 Copyright (C) 2006, 2007 Mark Joshi
*/

package org.jquantlib.model.marketmodels.curvestates;

import org.jquantlib.QL;
import org.jquantlib.model.marketmodels.CurveState;

import java.util.Arrays;

/**
 * %Curve state for %Libor market models.
 *
 * <p>Java port of {@code ql/models/marketmodels/curvestates/lmmcurvestate.{hpp,cpp}}
 * (QuantLib v1.42.1).
 *
 * <p>Set the curve state from forward rates via {@link #setOnForwardRates(double[], int)}
 * or from discount ratios via {@link #setOnDiscountRatios(double[], int)}. After setting, the discount ratios, swap
 * rates, and CMS rates can be queried.
 */
public class LMMCurveState extends CurveState {

    private int first_;
    private final double[] discRatios_;
    private final double[] forwardRates_;
    // mutable lazy state
    private final double[] cmSwapRates_;
    private final double[] cmSwapAnnuities_;
    private final double[] cotSwapRates_;
    private final double[] cotAnnuities_;
    private int firstCotAnnuityComped_;

    public LMMCurveState(final double[] rateTimes) {
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
        this.firstCotAnnuityComped_ = numberOfRates_;
    }

    /** Copy constructor — used by {@link #clone()}. */
    private LMMCurveState(final LMMCurveState other) {
        super(other.rateTimes_);
        this.first_ = other.first_;
        this.discRatios_ = other.discRatios_.clone();
        this.forwardRates_ = other.forwardRates_.clone();
        this.cmSwapRates_ = other.cmSwapRates_.clone();
        this.cmSwapAnnuities_ = other.cmSwapAnnuities_.clone();
        this.cotSwapRates_ = other.cotSwapRates_.clone();
        this.cotAnnuities_ = other.cotAnnuities_.clone();
        this.firstCotAnnuityComped_ = other.firstCotAnnuityComped_;
    }

    public void setOnForwardRates(final double[] rates) {
        setOnForwardRates(rates, 0);
    }

    public void setOnForwardRates(final double[] rates, final int firstValidIndex) {
        QL.require(rates.length == numberOfRates_,
                "rates mismatch: " + numberOfRates_ + " required, " + rates.length + " provided");
        QL.require(firstValidIndex < numberOfRates_,
                "first valid index must be less than " + numberOfRates_ + ": " + firstValidIndex + " not allowed");

        first_ = firstValidIndex;
        // copy forwardRates_[first..] = rates[first..]
        System.arraycopy(rates, first_, forwardRates_, first_, numberOfRates_ - first_);

        // compute discount ratios forward
        for ( int i = first_; i < numberOfRates_; ++i ) {
            discRatios_[i + 1] = discRatios_[i] / (1.0 + forwardRates_[i] * rateTaus_[i]);
        }
        firstCotAnnuityComped_ = numberOfRates_;
    }

    public void setOnDiscountRatios(final double[] discRatios) {
        setOnDiscountRatios(discRatios, 0);
    }

    public void setOnDiscountRatios(final double[] discRatios, final int firstValidIndex) {
        QL.require(discRatios.length == numberOfRates_ + 1,
                "too many discount ratios: " + (numberOfRates_ + 1) + " required, " + discRatios.length + " provided");
        QL.require(firstValidIndex < numberOfRates_,
                "first valid index must be less than " + (numberOfRates_ + 1) + ": " + firstValidIndex
                        + " not allowed");

        first_ = firstValidIndex;
        System.arraycopy(discRatios, first_, discRatios_, first_, discRatios.length - first_);

        for ( int i = first_; i < numberOfRates_; ++i ) {
            forwardRates_[i] = (discRatios_[i] / discRatios_[i + 1] - 1.0) / rateTaus_[i];
        }
        firstCotAnnuityComped_ = numberOfRates_;
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
        return forwardRates_[i];
    }

    @Override
    public double coterminalSwapAnnuity(final int numeraire, final int i) {
        QL.require(first_ < numberOfRates_, "curve state not initialized yet");
        QL.require(numeraire >= first_ && numeraire <= numberOfRates_, "invalid numeraire");
        QL.require(i >= first_ && i <= numberOfRates_, "invalid index");

        if ( firstCotAnnuityComped_ <= i ) {
            return cotAnnuities_[i] / discRatios_[numeraire];
        }

        if ( firstCotAnnuityComped_ == numberOfRates_ ) {
            cotAnnuities_[numberOfRates_ - 1] = rateTaus_[numberOfRates_ - 1] * discRatios_[numberOfRates_];
            --firstCotAnnuityComped_;
        }

        for ( int j = firstCotAnnuityComped_ - 1; j >= i; --j ) {
            cotAnnuities_[j] = cotAnnuities_[j + 1] + rateTaus_[j] * discRatios_[j + 1];
        }
        firstCotAnnuityComped_ = i;
        return cotAnnuities_[i] / discRatios_[numeraire];
    }

    @Override
    public double coterminalSwapRate(final int i) {
        QL.require(first_ < numberOfRates_, "curve state not initialized yet");
        QL.require(i >= first_ && i <= numberOfRates_, "invalid index");
        return (discRatios_[i] / discRatios_[numberOfRates_] - 1.0) / coterminalSwapAnnuity(numberOfRates_, i);
    }

    @Override
    public double cmSwapAnnuity(final int numeraire, final int i, final int spanningForwards) {
        QL.require(first_ < numberOfRates_, "curve state not initialized yet");
        QL.require(numeraire >= first_ && numeraire <= numberOfRates_, "invalid numeraire");
        QL.require(i >= first_ && i <= numberOfRates_, "invalid index");

        constantMaturityFromDiscountRatios(spanningForwards, first_, discRatios_, rateTaus_, cmSwapRates_,
                cmSwapAnnuities_);
        return cmSwapAnnuities_[i] / discRatios_[numeraire];
    }

    @Override
    public double cmSwapRate(final int i, final int spanningForwards) {
        QL.require(first_ < numberOfRates_, "curve state not initialized yet");
        QL.require(i >= first_ && i <= numberOfRates_, "invalid index");

        constantMaturityFromDiscountRatios(spanningForwards, first_, discRatios_, rateTaus_, cmSwapRates_,
                cmSwapAnnuities_);
        return cmSwapRates_[i];
    }

    @Override
    public double[] forwardRates() {
        QL.require(first_ < numberOfRates_, "curve state not initialized yet");
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
        constantMaturityFromDiscountRatios(spanningForwards, first_, discRatios_, rateTaus_, cmSwapRates_,
                cmSwapAnnuities_);
        return cmSwapRates_;
    }

    @Override
    public CurveState clone() {
        return new LMMCurveState(this);
    }
}
