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
 Copyright (C) 2010, 2011 Chris Kenyon

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.experimental.inflation;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.CPI;
import org.jquantlib.indexes.ZeroInflationIndex;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.AbstractTermStructure;
import org.jquantlib.termstructures.InflationTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.*;

/**
 * CPI cap/floor price surface — provides cpi cap/floor prices by interpolation and put/call parity (not cap/floor/swap*
 * parity).
 *
 * <p>The inflation index MUST contain a {@link
 * org.jquantlib.termstructures.ZeroInflationTermStructure} as this is used to create ATM. Unlike YoY price surfaces we
 * assume that
 * <ol>
 *   <li>an ATM ZeroInflationTermStructure is available, and</li>
 *   <li>that it is safe to use it.</li>
 * </ol>
 * This is supported by the fact that no stripping is required for CPI
 * cap/floors as they only give one flow.
 *
 * <p>CPI cap/floors have a single (one) flow (unlike nominal caps) because
 * they observe cumulative inflation up to their maturity. Options are on
 * CPI(T)/CPI(0) but strikes are quoted for yearly average inflation, so
 * require transformation via {@code (1+quote)^T} to obtain actual strikes.
 * These are consistent with ZCIIS quoting conventions.
 *
 * <p>The {@code observationLag} is that for the referenced instrument prices.
 * Strikes are as-quoted, not as-used.
 *
 * <p>Mirrors C++ v1.42.1 {@code QuantLib::CPICapFloorTermPriceSurface}
 * ({@code ql/experimental/inflation/cpicapfloortermpricesurface.{hpp,cpp}}).
 *
 * @author JQuantLib migration team (Phase 2s C.1)
 */
public abstract class CPICapFloorTermPriceSurface extends AbstractTermStructure {

    //
    // protected fields — mirror C++ protected members
    //

    private final double nominal_;
    private final BusinessDayConvention bdc_;
    private final Period observationLag_;
    private final double baseRate_;
    protected ZeroInflationIndex zii_;
    protected CPI.InterpolationType interpolationType_;
    protected Handle< YieldTermStructure > nominalTS_;
    // data
    protected double[] cStrikes_;
    protected double[] fStrikes_;
    protected Period[] cfMaturities_;

    //
    // private fields
    //
    protected double[] cfMaturityTimes_;
    protected Matrix cPrice_;
    protected Matrix fPrice_;
    // constructed
    protected double[] cfStrikes_;

    //
    // public constructors
    //

    /**
     * Constructs a CPI cap/floor term-price surface from quoted prices.
     *
     * @param nominal           notional (typically 1.0)
     * @param baseRate          base inflation rate (avoids crash if index has no TS)
     * @param observationLag    observation lag for the surface instruments
     * @param cal               calendar for date adjustment
     * @param bdc               business day convention
     * @param dc                day counter
     * @param zii               zero inflation index
     * @param interpolationType CPI interpolation type
     * @param yts               nominal yield term structure
     * @param cStrikes          cap strikes
     * @param fStrikes          floor strikes
     * @param cfMaturities      common maturities for both caps and floors
     * @param cPrice            cap price matrix [strike rows x maturity cols]
     * @param fPrice            floor price matrix [strike rows x maturity cols]
     */
    protected CPICapFloorTermPriceSurface(final double nominal, final double baseRate, final Period observationLag,
            final Calendar cal, final BusinessDayConvention bdc, final DayCounter dc, final ZeroInflationIndex zii,
            final CPI.InterpolationType interpolationType, final Handle< YieldTermStructure > yts,
            final double[] cStrikes, final double[] fStrikes, final Period[] cfMaturities, final Matrix cPrice,
            final Matrix fPrice) {
        super(0, cal, dc);
        this.zii_ = zii;
        this.interpolationType_ = interpolationType;
        this.nominalTS_ = yts;
        this.cStrikes_ = cStrikes.clone();
        this.fStrikes_ = fStrikes.clone();
        this.cfMaturities_ = cfMaturities.clone();
        this.cPrice_ = cPrice;
        this.fPrice_ = fPrice;
        this.nominal_ = nominal;
        this.bdc_ = bdc;
        this.observationLag_ = observationLag;
        this.baseRate_ = baseRate;

        // Index has a TS?
        QL.require(zii_ != null, "ZeroInflationIndex must not be null");
        QL.require(zii_.zeroInflationTermStructure() != null && !zii_.zeroInflationTermStructure().empty(),
                "ZITS missing from index");
        QL.require(nominalTS_ != null && !nominalTS_.empty(), "nominal TS missing");

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
                    QL.require(cPrice_.get(i, j) <= cPrice_.get(i - 1, j),
                            "non-decreasing cap prices: " + cPrice_.get(i, j) + " then " + cPrice_.get(i - 1, j));
                }
            }
        }

        // Build the union strikes set: floors first (assumed lower), then
        // caps higher than maxFstrike + eps.
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

        // Final consistency checking
        QL.require(cfStrikes_.length > 2, "overall not enough strikes");
        for ( int i = 1; i < cfStrikes_.length; i++ ) {
            QL.require(cfStrikes_[i] > cfStrikes_[i - 1], "cfStrikes not increasing");
        }
    }

    //
    // InflationTermStructure interface (matches C++)
    //

    /**
     * Mirrors C++ {@code detail::CPI::isInterpolated}.
     */
    private static boolean isInterpolated(final CPI.InterpolationType t) {
        return t == CPI.InterpolationType.Linear;
    }

    /**
     * Mirrors C++ free function {@code inflationYearFraction}
     * ({@code ql/termstructures/inflationtermstructure.cpp:290}).
     */
    private static double inflationYearFraction(final Frequency f, final boolean indexIsInterpolated,
            final DayCounter dc, final Date d1, final Date d2) {
        if ( indexIsInterpolated ) {
            return dc.yearFraction(d1, d2);
        }
        final org.jquantlib.util.Pair< Date, Date > limD1 = InflationTermStructure.inflationPeriod(d1, f);
        final org.jquantlib.util.Pair< Date, Date > limD2 = InflationTermStructure.inflationPeriod(d2, f);
        return dc.yearFraction(limD1.first(), limD2.first());
    }

    public Period observationLag() {
        return observationLag_;
    }

    public Frequency frequency() {
        return zii_.frequency();
    }

    //
    // inspectors
    //

    public Date baseDate() {
        return zii_.zeroInflationTermStructure().currentLink().baseDate();
    }

    public double baseRate() {
        return baseRate_;
    }

    public double nominal() {
        return nominal_;
    }

    public BusinessDayConvention businessDayConvention() {
        return bdc_;
    }

    public ZeroInflationIndex zeroInflationIndex() {
        return zii_;
    }

    /**
     * Computes the ATM rate at a given maturity date by exact ZCIIS-style geometric averaging of CPI fixings.
     *
     * <p>Mirrors C++ {@code CPICapFloorTermPriceSurface::atmRate}.
     */
    public double atmRate(final Date maturity) {
        final double F0 = CPI.laggedFixing(zii_, referenceDate(), observationLag_, interpolationType_);
        final double F1 = CPI.laggedFixing(zii_, maturity, observationLag_, interpolationType_);

        final double T = inflationYearFraction(zii_.frequency(), isInterpolated(interpolationType_), dayCounter(),
                referenceDate().sub(observationLag_), maturity.sub(observationLag_));

        return T > 0.0 ? Math.pow(F1 / F0, 1.0 / T) - 1.0 : baseRate();
    }

    //
    // Tenor convenience overrides — period-flavored overloads delegate to date.
    //

    /** Mirrors C++ {@code price(const Period&, Rate)} — picks cap or floor by ATM. */
    public double price(final Period d, final double k) {
        return price(cpiOptionDateFromTenor(d), k);
    }

    public double capPrice(final Period d, final double k) {
        return capPrice(cpiOptionDateFromTenor(d), k);
    }

    public double floorPrice(final Period d, final double k) {
        return floorPrice(cpiOptionDateFromTenor(d), k);
    }

    /** Subclass implements interpolated price; strike uses quoting convention. */
    public abstract double price(final Date d, final double k);

    public abstract double capPrice(final Date d, final double k);

    public abstract double floorPrice(final Date d, final double k);

    //
    // strike / maturity inspectors
    //

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

    public Matrix capPrices() {
        return cPrice_;
    }

    public Matrix floorPrices() {
        return fPrice_;
    }

    public double minStrike() {
        return cfStrikes_[0];
    }

    public double maxStrike() {
        return cfStrikes_[cfStrikes_.length - 1];
    }

    public Date minDate() {
        return referenceDate().add(cfMaturities_[0]);
    }

    @Override
    public Date maxDate() {
        return referenceDate().add(cfMaturities_[cfMaturities_.length - 1]);
    }

    public Date cpiOptionDateFromTenor(final Period p) {
        return calendar().adjust(referenceDate().add(p), businessDayConvention());
    }

    //
    // internal accessors used by subclass
    //

    protected Handle< YieldTermStructure > nominalTermStructure() {
        return nominalTS_;
    }

    //
    // hidden checkers (mirror C++ for completeness, currently unused)
    //

    @SuppressWarnings( "unused" )
    protected boolean checkStrike(final double K) {
        return minStrike() <= K && K <= maxStrike();
    }

    @SuppressWarnings( "unused" )
    protected boolean checkMaturity(final Date d) {
        return minDate().le(d) && d.le(maxDate());
    }

}
