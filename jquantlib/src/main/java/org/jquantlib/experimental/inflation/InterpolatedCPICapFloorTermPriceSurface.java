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
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.Closeness;
import org.jquantlib.math.interpolations.Interpolation2D;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;

/**
 * Interpolated CPI cap/floor term-price surface — fills out the surface across all strikes from the partial cap- and
 * floor-price grids using put/call parity, then 2D-interpolates over (time, strike).
 *
 * <p>Mirrors C++ v1.42.1 {@code QuantLib::InterpolatedCPICapFloorTermPriceSurface}
 * ({@code ql/experimental/inflation/cpicapfloortermpricesurface.hpp:149}).
 *
 * <p>The C++ class is templated on a 2D interpolator; the Java port follows
 * the {@code Class<I>}-factory pattern used elsewhere in the codebase.
 *
 * @param <I> {@link Interpolation2D.Interpolator2D} factory class (e.g.
 *            {@link org.jquantlib.math.interpolations.factories.BicubicSpline} or
 *            {@link org.jquantlib.math.interpolations.factories.Bilinear}).
 * @author JQuantLib migration team (Phase 2s C.1)
 */
public class InterpolatedCPICapFloorTermPriceSurface< I extends Interpolation2D.Interpolator2D >
        extends CPICapFloorTermPriceSurface {

    private final Class< I > classI;
    private final Interpolation2D.Interpolator2D interpolator2d_;

    // Filled-out price matrices (rows = cfStrikes, cols = maturities)
    private Matrix cPriceB_;
    private Matrix fPriceB_;

    // 2D interpolations over (time, strike) — keyed on filled matrices
    private Interpolation2D capPrice_;
    private Interpolation2D floorPrice_;

    /**
     * Construct surface using the default-constructed interpolator factory.
     */
    public InterpolatedCPICapFloorTermPriceSurface(final Class< I > classI, final double nominal,
            final double startRate, final Period observationLag, final Calendar cal, final BusinessDayConvention bdc,
            final DayCounter dc, final ZeroInflationIndex zii, final CPI.InterpolationType interpolationType,
            final Handle< YieldTermStructure > yts, final double[] cStrikes, final double[] fStrikes,
            final Period[] cfMaturities, final Matrix cPrice, final Matrix fPrice) {
        super(nominal, startRate, observationLag, cal, bdc, dc, zii, interpolationType, yts, cStrikes, fStrikes,
                cfMaturities, cPrice, fPrice);
        QL.require(classI != null, "Interpolator factory class must not be null");
        this.classI = classI;
        this.interpolator2d_ = constructInterpolator(classI);
        performCalculations();
    }

    private static Interpolation2D.Interpolator2D constructInterpolator(final Class< ? > klass) {
        try {
            return (Interpolation2D.Interpolator2D) klass.getDeclaredConstructor().newInstance();
        } catch ( final Exception e ) {
            throw new LibraryException("cannot create Interpolator2D", e);
        }
    }

    /**
     * Set up the interpolations for capPrice_ and floorPrice_. Since we know ATM and we have single flows, we use
     * put/call parity to extend the surfaces across all strikes.
     *
     * <p>Mirrors C++ template {@code performCalculations()}
     * ({@code cpicapfloortermpricesurface.hpp:233}).
     */
    private void performCalculations() {
        cPriceB_ = new Matrix(cfStrikes_.length, cfMaturities_.length);
        fPriceB_ = new Matrix(cfStrikes_.length, cfMaturities_.length);
        // mark all entries unfilled
        final boolean[][] cFilled = new boolean[cfStrikes_.length][cfMaturities_.length];
        final boolean[][] fFilled = new boolean[cfStrikes_.length][cfMaturities_.length];

        QL.require(nominalTS_ != null && !nominalTS_.empty(), "Yts is empty!!!");

        for ( int j = 0; j < cfMaturities_.length; ++j ) {
            final Period mat = cfMaturities_[j];
            final double df = nominalTS_.currentLink().discount(cpiOptionDateFromTenor(mat));
            final double atm_quote = atmRate(cpiOptionDateFromTenor(mat));
            final double atm = Math.pow(1.0 + atm_quote, mat.length());
            final double S = atm * df;
            for ( int i = 0; i < cfStrikes_.length; ++i ) {
                final double K_quote = cfStrikes_[i];
                final double K = Math.pow(1.0 + K_quote, mat.length());
                int indF = -1;
                for ( int q = 0; q < fStrikes_.length; q++ ) {
                    if ( Closeness.isCloseEnough(fStrikes_[q], cfStrikes_[i]) ) {
                        indF = q;
                        break;
                    }
                }
                int indC = -1;
                for ( int q = 0; q < cStrikes_.length; q++ ) {
                    if ( Closeness.isCloseEnough(cStrikes_[q], cfStrikes_[i]) ) {
                        indC = q;
                        break;
                    }
                }
                final boolean isFloorStrike = indF >= 0;
                final boolean isCapStrike = indC >= 0;
                if ( isFloorStrike ) {
                    fPriceB_.set(i, j, fPrice_.get(indF, j));
                    fFilled[i][j] = true;
                    if ( !isCapStrike ) {
                        cPriceB_.set(i, j, fPrice_.get(indF, j) + S - K * df);
                        cFilled[i][j] = true;
                    }
                }
                if ( isCapStrike ) {
                    cPriceB_.set(i, j, cPrice_.get(indC, j));
                    cFilled[i][j] = true;
                    if ( !isFloorStrike ) {
                        fPriceB_.set(i, j, cPrice_.get(indC, j) + K * df - S);
                        fFilled[i][j] = true;
                    }
                }
            }
        }

        // Check that all cells are filled.
        for ( int i = 0; i < cPriceB_.rows(); ++i ) {
            for ( int j = 0; j < cPriceB_.cols(); ++j ) {
                QL.require(cFilled[i][j],
                        "InterpolatedCPICapFloorTermPriceSurface: did not fill" + " call price matrix at (" + i + ","
                                + j + "), this is unexpected");
                QL.require(fFilled[i][j],
                        "InterpolatedCPICapFloorTermPriceSurface: did not fill" + " floor price matrix at (" + i + ","
                                + j + "), this is unexpected");
            }
        }

        // Build the maturity time grid.
        cfMaturityTimes_ = new double[cfMaturities_.length];
        for ( int i = 0; i < cfMaturities_.length; i++ ) {
            cfMaturityTimes_[i] = timeFromReference(cpiOptionDateFromTenor(cfMaturities_[i]));
        }

        // 2D-interpolate using vx=time, vy=strike, mz[strike_row][time_col].
        // Note matrices are already stored as [strike_row][maturity_col].
        capPrice_ = interpolator2d_.interpolate(new Array(cfMaturityTimes_), new Array(cfStrikes_), cPriceB_);
        capPrice_.enableExtrapolation();

        floorPrice_ = interpolator2d_.interpolate(new Array(cfMaturityTimes_), new Array(cfStrikes_), fPriceB_);
        floorPrice_.enableExtrapolation();
    }

    @Override
    public double price(final Date d, final double k) {
        final double atm = atmRate(d);
        return k > atm ? capPrice(d, k) : floorPrice(d, k);
    }

    @Override
    public double capPrice(final Date d, final double k) {
        final double t = timeFromReference(d);
        return capPrice_.op(t, k, true);
    }

    @Override
    public double floorPrice(final Date d, final double k) {
        final double t = timeFromReference(d);
        return floorPrice_.op(t, k, true);
    }

    /** Returns the interpolator factory class used by this surface. */
    public Class< I > interpolator2dClass() {
        return classI;
    }

}
