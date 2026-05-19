/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is released under the BSD License.

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
 Copyright (C) 2009 Chris Kenyon
 Copyright (C) 2009 Bernd Engelmann

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.experimental.inflation;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.CPI;
import org.jquantlib.indexes.YoYInflationIndex;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.AbstractTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.YoYInflationTermStructure;
import org.jquantlib.time.*;
import org.jquantlib.util.Pair;

/**
 * Year-on-year cap/floor term-price surface — abstract base.
 *
 * <p>Mirrors C++ v1.42.1 {@code QuantLib::YoYCapFloorTermPriceSurface}
 * ({@code ql/experimental/inflation/yoycapfloortermpricesurface.{hpp,cpp}}).
 *
 * <p>Since this can create a YoY term structure it does take a YoY index.
 *
 * @author JQuantLib migration team (Phase 2s C.1)
 */
public abstract class YoYCapFloorTermPriceSurface extends AbstractTermStructure {

    //
    // protected fields — mirror C++ protected members
    //

    protected int fixingDays_;
    protected BusinessDayConvention bdc_;
    protected YoYInflationIndex yoyIndex_;
    protected Period observationLag_;
    protected Handle< YieldTermStructure > nominalTS_;
    protected double[] cStrikes_;
    protected double[] fStrikes_;
    protected Period[] cfMaturities_;
    protected double[] cfMaturityTimes_;
    protected Matrix cPrice_;
    protected Matrix fPrice_;
    protected boolean indexIsInterpolated_;
    // constructed
    protected double[] cfStrikes_;
    protected YoYInflationTermStructure yoy_;
    protected Pair< double[], double[] > atmYoYSwapTimeRates_;
    protected Pair< Date[], double[] > atmYoYSwapDateRates_;

    //
    // public constructors
    //

    /**
     * Constructs a YoY cap/floor term-price surface from quoted prices.
     *
     * @param fixingDays    fixing days
     * @param yyLag         observation lag
     * @param yii           YoY inflation index
     * @param interpolation CPI interpolation type
     * @param nominal       nominal yield term structure
     * @param dc            day counter
     * @param cal           calendar
     * @param bdc           business day convention
     * @param cStrikes      cap strikes
     * @param fStrikes      floor strikes
     * @param cfMaturities  cap/floor maturities
     * @param cPrice        cap price matrix
     * @param fPrice        floor price matrix
     */
    protected YoYCapFloorTermPriceSurface(final int fixingDays, final Period yyLag, final YoYInflationIndex yii,
            final CPI.InterpolationType interpolation, final Handle< YieldTermStructure > nominal, final DayCounter dc,
            final Calendar cal, final BusinessDayConvention bdc, final double[] cStrikes, final double[] fStrikes,
            final Period[] cfMaturities, final Matrix cPrice, final Matrix fPrice) {
        super(0, cal, dc);
        this.fixingDays_ = fixingDays;
        this.bdc_ = bdc;
        this.yoyIndex_ = yii;
        this.observationLag_ = yyLag;
        this.nominalTS_ = nominal;
        this.cStrikes_ = cStrikes.clone();
        this.fStrikes_ = fStrikes.clone();
        this.cfMaturities_ = cfMaturities.clone();
        this.cPrice_ = cPrice;
        this.fPrice_ = fPrice;
        this.indexIsInterpolated_ = isInterpolated(interpolation, yii);

        // Data consistency checking, enough data?
        QL.require(fStrikes_.length > 1, "not enough floor strikes");
        QL.require(cStrikes_.length > 1, "not enough cap strikes");
        QL.require(cfMaturities_.length > 1, "not enough maturities");
        QL.require(fStrikes_.length == fPrice.rows(), "floor strikes vs floor price rows not equal");
        QL.require(cStrikes_.length == cPrice.rows(), "cap strikes vs cap price rows not equal");
        QL.require(cfMaturities_.length == fPrice.cols(), "maturities vs floor price columns not equal");
        QL.require(cfMaturities_.length == cPrice.cols(), "maturities vs cap price columns not equal");

        // Data has correct properties (positive, monotonic)?
        for ( int j = 0; j < cfMaturities_.length; j++ ) {
            QL.require(cfMaturities_[j].length() > 0, "non-positive maturities");
            if ( j > 0 ) {
                QL.require(cfMaturities_[j].gt(cfMaturities_[j - 1]), "non-increasing maturities");
            }
            for ( int i = 0; i < fPrice_.rows(); i++ ) {
                QL.require(fPrice_.get(i, j) > 0.0, "non-positive floor price: " + fPrice_.get(i, j));
                if ( i > 0 ) {
                    QL.require(fPrice_.get(i, j) >= fPrice_.get(i - 1, j), "non-increasing floor prices");
                }
            }
            for ( int i = 0; i < cPrice_.rows(); i++ ) {
                QL.require(cPrice_.get(i, j) > 0.0, "non-positive cap price: " + cPrice_.get(i, j));
                if ( i > 0 ) {
                    QL.require(cPrice_.get(i, j) <= cPrice_.get(i - 1, j), "non-decreasing cap prices");
                }
            }
        }

        // Build the union strikes set (floors first, then caps strictly greater).
        final double eps = 0.0000001;
        final double maxFstrike = fStrikes_[fStrikes_.length - 1];
        int extraCount = 0;
        for ( int i = 0; i < cStrikes_.length; i++ ) {
            if ( cStrikes_[i] > maxFstrike + eps ) {
                ++extraCount;
            }
        }
        this.cfStrikes_ = new double[fStrikes_.length + extraCount];
        System.arraycopy(fStrikes_, 0, this.cfStrikes_, 0, fStrikes_.length);
        int p = fStrikes_.length;
        for ( int i = 0; i < cStrikes_.length; i++ ) {
            if ( cStrikes_[i] > maxFstrike + eps ) {
                this.cfStrikes_[p++] = cStrikes_[i];
            }
        }

        QL.require(cfStrikes_.length > 2, "overall not enough strikes");
        for ( int i = 1; i < cfStrikes_.length; i++ ) {
            QL.require(cfStrikes_[i] > cfStrikes_[i - 1], "cfStrikes not increasing");
        }
    }

    /**
     * Mirrors C++ {@code detail::CPI::isInterpolated(CPI::InterpolationType, YoYInflationIndex)}: AsIndex follows the
     * index, otherwise Linear is interpolated and Flat is not.
     */
    private static boolean isInterpolated(final CPI.InterpolationType t, final YoYInflationIndex idx) {
        if ( t == CPI.InterpolationType.AsIndex ) {
            return idx != null && idx.interpolated();
        }
        return t == CPI.InterpolationType.Linear;
    }

    /**
     * Mirrors C++ {@code YoYInflationIndex::interpolated()} accessor. Java's {@code YoYInflationIndex} exposes
     * interpolated state via {@code interpolated()}; this is provided here to keep the isInterpolated logic
     * centralised.
     */
    @SuppressWarnings( "unused" )
    private static boolean indexInterpolated(final YoYInflationIndex idx) {
        return idx != null && idx.interpolated();
    }

    public boolean indexIsInterpolated() {
        return indexIsInterpolated_;
    }

    public Period observationLag() {
        return observationLag_;
    }

    public Frequency frequency() {
        return yoyIndex_.frequency();
    }

    /** ATM YoY swap times/rates from put-call parity. */
    public abstract Pair< double[], double[] > atmYoYSwapTimeRates();

    /** ATM YoY swap dates/rates from put-call parity. */
    public abstract Pair< Date[], double[] > atmYoYSwapDateRates();

    /** Derived YoY term structure (bootstrapped from the surface). */
    public abstract YoYInflationTermStructure yoyTS();

    public YoYInflationIndex yoyIndex() {
        return yoyIndex_;
    }

    public BusinessDayConvention businessDayConvention() {
        return bdc_;
    }

    public int fixingDays() {
        return fixingDays_;
    }

    public abstract Date baseDate();

    /** Date-indexed price (cap or floor by ATM) — abstract. */
    public abstract double price(final Date d, final double k);

    public abstract double capPrice(final Date d, final double k);

    public abstract double floorPrice(final Date d, final double k);

    public abstract double atmYoYSwapRate(final Date d, final boolean extrapolate);

    public abstract double atmYoYRate(final Date d, final Period obsLag, final boolean extrapolate);

    /** Default obsLag = -1 day → uses observationLag(). */
    public double atmYoYRate(final Date d) {
        return atmYoYRate(d, new Period(-1, TimeUnit.Days), true);
    }

    //
    // Period overloads
    //

    /** Default extrapolate=true. */
    public double atmYoYSwapRate(final Date d) {
        return atmYoYSwapRate(d, true);
    }

    public double price(final Period d, final double k) {
        return price(yoyOptionDateFromTenor(d), k);
    }

    public double capPrice(final Period d, final double k) {
        return capPrice(yoyOptionDateFromTenor(d), k);
    }

    public double floorPrice(final Period d, final double k) {
        return floorPrice(yoyOptionDateFromTenor(d), k);
    }

    public double atmYoYSwapRate(final Period d, final boolean extrapolate) {
        return atmYoYSwapRate(yoyOptionDateFromTenor(d), extrapolate);
    }

    public double atmYoYSwapRate(final Period d) {
        return atmYoYSwapRate(yoyOptionDateFromTenor(d), true);
    }

    public double atmYoYRate(final Period d, final Period obsLag, final boolean extrapolate) {
        return atmYoYRate(yoyOptionDateFromTenor(d), obsLag, extrapolate);
    }

    public double atmYoYRate(final Period d) {
        return atmYoYRate(yoyOptionDateFromTenor(d), new Period(-1, TimeUnit.Days), true);
    }

    public double[] strikes() {
        return cfStrikes_;
    }

    public double[] capStrikes() {
        return cStrikes_;
    }

    public double[] floorStrikes() {
        return fStrikes_;
    }

    public Period[] maturities() {
        return cfMaturities_;
    }

    public double minStrike() {
        return cfStrikes_[0];
    }

    public double maxStrike() {
        return cfStrikes_[cfStrikes_.length - 1];
    }

    public Date minMaturity() {
        return referenceDate().add(cfMaturities_[0]);
    }

    public Date maxMaturity() {
        return referenceDate().add(cfMaturities_[cfMaturities_.length - 1]);
    }

    public Date yoyOptionDateFromTenor(final Period p) {
        return referenceDate().add(p);
    }

    @SuppressWarnings( "unused" )
    protected boolean checkStrike(final double K) {
        return minStrike() <= K && K <= maxStrike();
    }

    @SuppressWarnings( "unused" )
    protected boolean checkMaturity(final Date d) {
        return minMaturity().le(d) && d.le(maxMaturity());
    }

}
