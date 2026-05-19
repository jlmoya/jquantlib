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
 Copyright (C) 2006, 2007 Mark Joshi
*/

package org.jquantlib.model.marketmodels;

import org.jquantlib.QL;

/**
 * %Curve state for market-model simulations.
 *
 * <p>Java port of {@code ql/models/marketmodels/curvestate.{hpp,cpp}}
 * (QuantLib v1.42.1).
 *
 * <p>This class stores the state of the yield curve associated to the
 * fixed calendar times within the simulation. It is the workhorse discounting object associated to the rate times of
 * the simulation. It is important to pass the rates via an object like this to the product rather than directly to make
 * it easier to switch to other engines such as a coterminal swap rate engine.
 *
 * <pre>
 *           |-----|-----|-----|-----|-----|      (size = 6)
 *           t0    t1    t2    t3    t4    t5     rateTimes
 *           f0    f1    f2    f3    f4           forwardRates
 *           d0    d1    d2    d3    d4    d5     discountBonds
 *           d0/d0 d1/d0 d2/d0 d3/d0 d4/d0 d5/d0  discountRatios
 *           sr0   sr1   sr2   sr3   sr4          coterminalSwaps
 * </pre>
 *
 * <p>P3H-5: C++ {@code std::unique_ptr<CurveState> clone()} maps to Java
 * {@code CurveState clone()} (no generics needed).
 */
public abstract class CurveState {

    protected int numberOfRates_;
    protected double[] rateTimes_;
    protected double[] rateTaus_;

    protected CurveState(final double[] rateTimes) {
        this.numberOfRates_ = (rateTimes == null || rateTimes.length == 0) ? 0 : rateTimes.length - 1;
        this.rateTimes_ = (rateTimes == null) ? new double[0] : rateTimes.clone();
        this.rateTaus_ = new double[numberOfRates_];
        if ( numberOfRates_ > 0 ) {
            this.rateTaus_ = Utilities.checkIncreasingTimesAndCalculateTaus(this.rateTimes_, this.rateTaus_);
        }
    }

    /** From discount-ratio array, fill in forward rates [firstValidIndex .. n). */
    public static void forwardsFromDiscountRatios(final int firstValidIndex, final double[] ds, final double[] taus,
            final double[] fwds) {
        QL.require(taus.length == fwds.length, "taus.size()!=fwds.size()");
        QL.require(ds.length == fwds.length + 1, "ds.size()!=fwds.size()+1");
        for ( int i = firstValidIndex; i < fwds.length; ++i ) {
            fwds[i] = (ds[i] - ds[i + 1]) / (ds[i + 1] * taus[i]);
        }
    }

    /**
     * From discount-ratio array, fill in coterminal swap rates and annuities [firstValidIndex .. n).
     */
    public static void coterminalFromDiscountRatios(final int firstValidIndex, final double[] discountFactors,
            final double[] taus, final double[] cotSwapRates, final double[] cotSwapAnnuities) {
        final int nCotSwapRates = cotSwapRates.length;
        QL.require(taus.length == nCotSwapRates, "taus.size()!=cotSwapRates.size()");
        QL.require(cotSwapAnnuities.length == nCotSwapRates, "cotSwapAnnuities.size()!=cotSwapRates.size()");
        QL.require(discountFactors.length == nCotSwapRates + 1, "discountFactors.size()!=cotSwapRates.size()+1");

        cotSwapAnnuities[nCotSwapRates - 1] = taus[nCotSwapRates - 1] * discountFactors[nCotSwapRates];
        cotSwapRates[nCotSwapRates - 1] =
                (discountFactors[nCotSwapRates - 1] - discountFactors[nCotSwapRates]) / cotSwapAnnuities[nCotSwapRates
                        - 1];

        for ( int i = nCotSwapRates - 1; i > firstValidIndex; --i ) {
            cotSwapAnnuities[i - 1] = cotSwapAnnuities[i] + taus[i - 1] * discountFactors[i];
            cotSwapRates[i - 1] = (discountFactors[i - 1] - discountFactors[nCotSwapRates]) / cotSwapAnnuities[i - 1];
        }
    }

    /**
     * From discount-ratio array, fill in constant-maturity swap rates and annuities [firstValidIndex .. n).
     */
    public static void constantMaturityFromDiscountRatios(final int spanningForwards, final int firstValidIndex,
            final double[] ds, final double[] taus, final double[] cmsRates, final double[] cmsAnnuities) {
        final int nConstMatSwapRates = cmsRates.length;
        QL.require(taus.length == nConstMatSwapRates, "taus.size()!=nConstMatSwapRates");
        QL.require(cmsAnnuities.length == nConstMatSwapRates, "constMatSwapAnnuities.size()!=nConstMatSwapRates");
        QL.require(ds.length == nConstMatSwapRates + 1, "ds.size()!=nConstMatSwapRates+1");

        // first cmsrate and cmsannuity
        cmsAnnuities[firstValidIndex] = 0.0;
        int lastIndex = Math.min(firstValidIndex + spanningForwards, nConstMatSwapRates);
        for ( int i = firstValidIndex; i < lastIndex; ++i ) {
            cmsAnnuities[firstValidIndex] += taus[i] * ds[i + 1];
        }
        cmsRates[firstValidIndex] = (ds[firstValidIndex] - ds[lastIndex]) / cmsAnnuities[firstValidIndex];
        int oldLastIndex = lastIndex;

        // remaining cms rates and annuities
        for ( int i = firstValidIndex + 1; i < nConstMatSwapRates; ++i ) {
            lastIndex = Math.min(i + spanningForwards, nConstMatSwapRates);
            cmsAnnuities[i] = cmsAnnuities[i - 1] - taus[i - 1] * ds[i];
            if ( lastIndex != oldLastIndex ) {
                cmsAnnuities[i] += taus[lastIndex - 1] * ds[lastIndex];
            }
            cmsRates[i] = (ds[i] - ds[lastIndex]) / cmsAnnuities[i];
            oldLastIndex = lastIndex;
        }
    }

    // Pure abstract API (mirrors C++ pure virtuals)

    public final int numberOfRates() {
        return numberOfRates_;
    }

    public final double[] rateTimes() {
        return rateTimes_;
    }

    public final double[] rateTaus() {
        return rateTaus_;
    }

    public abstract double discountRatio(int i, int j);

    public abstract double forwardRate(int i);

    public abstract double coterminalSwapAnnuity(int numeraire, int i);

    public abstract double coterminalSwapRate(int i);

    public abstract double cmSwapAnnuity(int numeraire, int i, int spanningForwards);

    public abstract double cmSwapRate(int i, int spanningForwards);

    public abstract double[] forwardRates();

    public abstract double[] coterminalSwapRates();

    // ----- Free functions (in C++ namespace QuantLib) -----

    public abstract double[] cmSwapRates(int spanningForwards);

    /** Concrete clone — returns a deep copy of this CurveState. */
    public abstract CurveState clone();

    /** Computes the swap rate over the range {@code [begin, end)}. */
    public final double swapRate(final int begin, final int end) {
        QL.require(end > begin, "empty range specified");
        QL.require(end <= numberOfRates_, "taus/end mismatch");

        double sum = 0.0;
        for ( int i = begin; i < end; ++i ) {
            sum += rateTaus_[i] * discountRatio(i + 1, numberOfRates_);
        }
        return (discountRatio(begin, numberOfRates_) - discountRatio(end, numberOfRates_)) / sum;
    }
}
