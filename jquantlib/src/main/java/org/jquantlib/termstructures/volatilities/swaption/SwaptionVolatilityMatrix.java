/*
 Copyright (C) 2026 JQuantLib migration contributors.

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
 Copyright (C) 2006, 2008 Ferdinando Ametrano
 Copyright (C) 2006 François du Vignaud
 Copyright (C) 2006 Katiuscia Manzoni
 Copyright (C) 2000, 2001, 2002, 2003 RiskMap srl
 Copyright (C) 2015 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */
package org.jquantlib.termstructures.volatilities.swaption;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.Constants;
import org.jquantlib.math.interpolations.BilinearInterpolation;
import org.jquantlib.math.interpolations.FlatExtrapolator2D;
import org.jquantlib.math.interpolations.Interpolation2D;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.volatilities.FlatSmileSection;
import org.jquantlib.termstructures.volatilities.SmileSection;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.util.Pair;

import java.util.ArrayList;
import java.util.List;

/**
 * At-the-money swaption-volatility matrix.
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/termstructures/volatility/swaption/swaptionvolmatrix.{hpp,cpp}}.
 *
 * <p>The volatility matrix {@code M} is laid out so that:
 * <ul>
 *  <li>row count = number of option dates (axis: optionTimes_)</li>
 *  <li>column count = number of swap tenors (axis: swapLengths_)</li>
 *  <li>{@code M[i][j]} = vol for option {@code i} and swap tenor {@code j}</li>
 * </ul>
 *
 * <p>The underlying 2-D interpolation is bilinear (default) or
 * flat-extrapolated bilinear when {@code flatExtrapolation == true}. The
 * shifts grid (used for shifted-lognormal vol type) is interpolated via the
 * same 2-D scheme; default-zero when no shifts are supplied.
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 *  <li>The C++ {@code performCalculations()} pulls fresh values from the
 *      {@code Handle<Quote>} grid; we do the same. Tests that hand-roll a
 *      {@code Matrix} input go through the SimpleQuote-wrapping ctor (4-arg
 *      Matrix variants) which behaves identically.</li>
 *  <li>{@code locate(Time, Time)} returns a {@link Pair} of
 *      ({@code i = locateY}, {@code j = locateX}) — i.e. (row, column) in matrix
 *      coordinates — to match the C++ {@code std::pair<Size,Size>}; note the
 *      argument order ({@code optionTime, swapLength}) matches C++.</li>
 *  <li>{@link #smileSectionImpl(double, double)} returns a {@link FlatSmileSection}
 *      at the ATM vol with no atm-level (caller handles) — same as the
 *      uncommented C++ version (see {@code swaptionvolmatrix.cpp} lines 319-327).</li>
 * </ul>
 */
public class SwaptionVolatilityMatrix extends SwaptionVolatilityDiscrete {

    //
    // private fields
    //

    private final List< List< Handle< ? extends Quote > > > volHandles_;
    private final List< List< Double > > shiftValues_;
    private final Matrix volatilities_;
    private final VolatilityType volatilityType_;
    private final boolean flatExtrapolation_;
    private final Matrix shifts_;
    private Interpolation2D interpolation_;
    private Interpolation2D interpolationShifts_;

    //
    // public constructors
    //

    /** Floating reference date, floating market data. */
    public SwaptionVolatilityMatrix(final Calendar cal, final BusinessDayConvention bdc, final List< Period > optionT,
            final List< Period > swapT, final List< List< Handle< ? extends Quote > > > vols, final DayCounter dc,
            final boolean flatExtrapolation, final VolatilityType type, final List< List< Double > > shifts) {
        super(optionT, swapT, 0, cal, bdc, dc);
        this.volHandles_ = vols;
        this.shiftValues_ = shifts;
        this.volatilities_ = new Matrix(vols.size(), vols.get(0).size());
        this.shifts_ = new Matrix(vols.size(), vols.get(0).size());
        this.volatilityType_ = type;
        this.flatExtrapolation_ = flatExtrapolation;
        checkInputs(volatilities_.rows(), volatilities_.columns(), shifts == null ? 0 : shifts.size(),
                shifts == null || shifts.isEmpty() ? 0 : shifts.get(0).size());
        registerWithMarketData();
        buildInterpolations();
    }

    /** Fixed reference date, floating market data. */
    public SwaptionVolatilityMatrix(final Date refDate, final Calendar cal, final BusinessDayConvention bdc,
            final List< Period > optionT, final List< Period > swapT,
            final List< List< Handle< ? extends Quote > > > vols, final DayCounter dc, final boolean flatExtrapolation,
            final VolatilityType type, final List< List< Double > > shifts) {
        super(optionT, swapT, refDate, cal, bdc, dc);
        this.volHandles_ = vols;
        this.shiftValues_ = shifts;
        this.volatilities_ = new Matrix(vols.size(), vols.get(0).size());
        this.shifts_ = new Matrix(vols.size(), vols.get(0).size());
        this.volatilityType_ = type;
        this.flatExtrapolation_ = flatExtrapolation;
        checkInputs(volatilities_.rows(), volatilities_.columns(), shifts == null ? 0 : shifts.size(),
                shifts == null || shifts.isEmpty() ? 0 : shifts.get(0).size());
        registerWithMarketData();
        buildInterpolations();
    }

    /** Floating reference date, fixed market data (Matrix input). */
    public SwaptionVolatilityMatrix(final Calendar cal, final BusinessDayConvention bdc, final List< Period > optionT,
            final List< Period > swapT, final Matrix vols, final DayCounter dc, final boolean flatExtrapolation,
            final VolatilityType type, final Matrix shifts) {
        super(optionT, swapT, 0, cal, bdc, dc);
        this.volatilities_ = new Matrix(vols.rows(), vols.columns());
        this.shifts_ = new Matrix(vols.rows(), vols.columns());
        this.volatilityType_ = type;
        this.flatExtrapolation_ = flatExtrapolation;
        checkInputs(vols.rows(), vols.columns(), shifts == null ? 0 : shifts.rows(),
                shifts == null ? 0 : shifts.columns());

        // Wrap each cell in a SimpleQuote (parity with C++ which builds
        // dummy Handle<Quote> objects for generic handle-based recompute).
        this.volHandles_ = new ArrayList< List< Handle< ? extends Quote > > >(vols.rows());
        this.shiftValues_ = new ArrayList<>(vols.rows());
        for ( int i = 0; i < vols.rows(); ++i ) {
            final List< Handle< ? extends Quote > > rowH = new ArrayList<>(vols.columns());
            final List< Double > rowS = new ArrayList<>(vols.columns());
            for ( int j = 0; j < vols.columns(); ++j ) {
                rowH.add(new Handle< Quote >(new SimpleQuote(vols.get(i, j))));
                rowS.add(shifts != null && shifts.rows() > 0 ? shifts.get(i, j) : 0.0);
            }
            this.volHandles_.add(rowH);
            this.shiftValues_.add(rowS);
        }
        buildInterpolations();
    }

    /** Fixed reference date, fixed market data (Matrix input). */
    public SwaptionVolatilityMatrix(final Date refDate, final Calendar cal, final BusinessDayConvention bdc,
            final List< Period > optionT, final List< Period > swapT, final Matrix vols, final DayCounter dc,
            final boolean flatExtrapolation, final VolatilityType type, final Matrix shifts) {
        super(optionT, swapT, refDate, cal, bdc, dc);
        this.volatilities_ = new Matrix(vols.rows(), vols.columns());
        this.shifts_ = new Matrix(vols.rows(), vols.columns());
        this.volatilityType_ = type;
        this.flatExtrapolation_ = flatExtrapolation;
        checkInputs(vols.rows(), vols.columns(), shifts == null ? 0 : shifts.rows(),
                shifts == null ? 0 : shifts.columns());

        this.volHandles_ = new ArrayList< List< Handle< ? extends Quote > > >(vols.rows());
        this.shiftValues_ = new ArrayList<>(vols.rows());
        for ( int i = 0; i < vols.rows(); ++i ) {
            final List< Handle< ? extends Quote > > rowH = new ArrayList<>(vols.columns());
            final List< Double > rowS = new ArrayList<>(vols.columns());
            for ( int j = 0; j < vols.columns(); ++j ) {
                rowH.add(new Handle< Quote >(new SimpleQuote(vols.get(i, j))));
                rowS.add(shifts != null && shifts.rows() > 0 ? shifts.get(i, j) : 0.0);
            }
            this.volHandles_.add(rowH);
            this.shiftValues_.add(rowS);
        }
        buildInterpolations();
    }

    /**
     * Fixed reference date and fixed market data, option dates.
     * <p>
     * The {@link SwaptionVolatilityDiscrete.FromDates} marker disambiguates this constructor from the
     * {@code List<Period>} overload (Java erasure collapses the two list types to the same JVM signature).
     */
    public SwaptionVolatilityMatrix(final Date today, final Calendar cal, final BusinessDayConvention bdc,
            final List< Date > optionDates, final SwaptionVolatilityDiscrete.FromDates marker,
            final List< Period > swapT, final Matrix vols, final DayCounter dc, final boolean flatExtrapolation,
            final VolatilityType type, final Matrix shifts) {
        super(optionDates, marker, swapT, today, cal, bdc, dc);
        this.volatilities_ = new Matrix(vols.rows(), vols.columns());
        this.shifts_ = new Matrix(vols.rows(), vols.columns());
        this.volatilityType_ = type;
        this.flatExtrapolation_ = flatExtrapolation;
        checkInputs(vols.rows(), vols.columns(), shifts == null ? 0 : shifts.rows(),
                shifts == null ? 0 : shifts.columns());

        this.volHandles_ = new ArrayList< List< Handle< ? extends Quote > > >(vols.rows());
        this.shiftValues_ = new ArrayList<>(vols.rows());
        for ( int i = 0; i < vols.rows(); ++i ) {
            final List< Handle< ? extends Quote > > rowH = new ArrayList<>(vols.columns());
            final List< Double > rowS = new ArrayList<>(vols.columns());
            for ( int j = 0; j < vols.columns(); ++j ) {
                rowH.add(new Handle< Quote >(new SimpleQuote(vols.get(i, j))));
                rowS.add(shifts != null && shifts.rows() > 0 ? shifts.get(i, j) : 0.0);
            }
            this.volHandles_.add(rowH);
            this.shiftValues_.add(rowS);
        }
        buildInterpolations();
    }

    //
    // SwaptionVolatilityStructure interface
    //

    @Override
    public Date maxDate() {
        return optionDates_.get(optionDates_.size() - 1);
    }

    @Override
    public double minStrike() {
        return -Constants.QL_MAX_REAL;
    }

    @Override
    public double maxStrike() {
        return Constants.QL_MAX_REAL;
    }

    @Override
    public Period maxSwapTenor() {
        return swapTenors_.get(swapTenors_.size() - 1);
    }

    @Override
    public VolatilityType volatilityType() {
        return volatilityType_;
    }

    @Override
    public double volatilityImpl(final double optionTime, final double swapLength, final double strike) {
        calculate();
        return interpolation_.op(swapLength, optionTime, true);
    }

    @Override
    public double blackVariance(final double optionTime, final double swapLength, final double strike,
            final boolean extrapolate) {
        // Mirrors C++ base class blackVariance(time, swapLength, strike, extrap):
        // = volatility * volatility * optionTime
        final double v = volatility(optionTime, swapLength, strike, extrapolate);
        return v * v * optionTime;
    }

    /**
     * Shift at a (optionTime, swapLength) point, with extrapolation always allowed (mirrors C++ inline
     * {@code shiftImpl}).
     */
    public double shiftImpl(final double optionTime, final double swapLength) {
        calculate();
        return interpolationShifts_.op(swapLength, optionTime, true);
    }

    /**
     * Shift at (optionTime, swapLength) — convenience wrapper used by the Java tests; matches C++
     * {@code shift(Time, Time, bool)}.
     */
    public double shift(final double optionTime, final double swapLength) {
        return shiftImpl(optionTime, swapLength);
    }

    /**
     * Shift at (optionTime, swapLength) with explicit extrapolate flag. The matrix is always defined within its grid
     * range, so the boolean is accepted for parity with C++ but not consulted.
     */
    public double shift(final double optionTime, final double swapLength, final boolean extrapolate) {
        return shiftImpl(optionTime, swapLength);
    }

    @Override
    protected SmileSection smileSectionImpl(final double optionTime, final double swapLength) {
        // Dummy strike (C++ uses 0.05) — matrix is ATM so vol does not depend on strike.
        final double atmVol = volatilityImpl(optionTime, swapLength, 0.05);
        return new FlatSmileSection(optionTime, atmVol, dayCounter(), Constants.NULL_REAL, volatilityType(),
                shift(optionTime, swapLength, true));
    }

    @Override
    protected SmileSection smileSectionImpl(final Date optionDate, final Period swapTenor) {
        final double t = timeFromReference(optionDate);
        final double l = swapLength(swapTenor);
        return smileSectionImpl(t, l);
    }

    //
    // inspectors specific to the matrix
    //

    /**
     * Returns the lower (i, j) indexes of the surrounding matrix corners for a given (optionTime, swapLength) — i.e.
     * the bilinear cell is {@code [(i, j), (i, j+1), (i+1, j), (i+1, j+1)]}.
     * <p>
     * Mirrors C++ {@code SwaptionVolatilityMatrix::locate(Time, Time)} which returns
     * {@code make_pair(interpolation_.locateY(optionTime), interpolation_.locateX(swapLength))}.
     */
    public Pair< Integer, Integer > locate(final double optionTime, final double swapLength) {
        return new Pair< Integer, Integer >(interpolation_.locateY(optionTime), interpolation_.locateX(swapLength));
    }

    /**
     * Locate by (optionDate, swapTenor) — convenience overload mirroring C++
     * {@code locate(const Date&, const Period&)}.
     */
    public Pair< Integer, Integer > locate(final Date optionDate, final Period swapTenor) {
        return locate(timeFromReference(optionDate), swapLength(swapTenor));
    }

    //
    // lazy hook
    //

    @Override
    protected void performCalculations() {
        super.performCalculations();
        // Pull market data into the dense matrix and refresh the interpolation.
        for ( int i = 0; i < volatilities_.rows(); ++i ) {
            for ( int j = 0; j < volatilities_.columns(); ++j ) {
                volatilities_.set(i, j, volHandles_.get(i).get(j).currentLink().value());
                if ( shiftValues_ != null && !shiftValues_.isEmpty() ) {
                    shifts_.set(i, j, shiftValues_.get(i).get(j));
                }
            }
        }
    }

    //
    // helpers
    //

    private void checkInputs(final int volRows, final int volsColumns, final int shiftRows, final int shiftsColumns) {
        QL.require(nOptionTenors_ == volRows,
                "mismatch between number of option dates (" + nOptionTenors_ + ") and number of rows (" + volRows
                        + ") in the vol matrix");
        QL.require(nSwapTenors_ == volsColumns,
                "mismatch between number of swap tenors (" + nSwapTenors_ + ") and number of columns (" + volsColumns
                        + ") in the vol matrix");

        if ( shiftRows == 0 && shiftsColumns == 0 ) {
            // shifts_ already initialised to (volRows, volsColumns) of zeros
            // by the matrix constructor — nothing more to do.
            return;
        }
        QL.require(nOptionTenors_ == shiftRows,
                "mismatch between number of option dates (" + nOptionTenors_ + ") and number of rows (" + shiftRows
                        + ") in the shift matrix");
        QL.require(nSwapTenors_ == shiftsColumns,
                "mismatch between number of swap tenors (" + nSwapTenors_ + ") and number of columns (" + shiftsColumns
                        + ") in the shift matrix");
    }

    private void registerWithMarketData() {
        for ( int i = 0; i < volHandles_.size(); ++i ) {
            for ( int j = 0; j < volHandles_.get(i).size(); ++j ) {
                volHandles_.get(i).get(j).addObserver(this);
            }
        }
    }

    /**
     * Build the bilinear (or flat-extrapolated bilinear) interpolation over the (swapLengths_, optionTimes_,
     * volatilities_) grid. The shift interpolation uses the same scheme over the shifts_ matrix.
     * <p>
     * Note: parity with C++ requires populating volatilities_ once eagerly here (in addition to lazy refresh in
     * {@link #performCalculations()}) so the very first {@code volatility(...)} call returns the right thing before any
     * explicit observer-driven update.
     */
    private void buildInterpolations() {
        // Eager seed (mirrors C++ Matrix-input ctor which writes vols straight in)
        for ( int i = 0; i < volatilities_.rows(); ++i ) {
            for ( int j = 0; j < volatilities_.columns(); ++j ) {
                volatilities_.set(i, j, volHandles_.get(i).get(j).currentLink().value());
                if ( shiftValues_ != null && !shiftValues_.isEmpty() ) {
                    shifts_.set(i, j, shiftValues_.get(i).get(j));
                }
            }
        }

        final Array swapAxis = new Array(swapLengths_);
        final Array optAxis = new Array(optionTimes_);
        if ( flatExtrapolation_ ) {
            interpolation_ = new FlatExtrapolator2D(new BilinearInterpolation(swapAxis, optAxis, volatilities_));
            interpolationShifts_ = new FlatExtrapolator2D(new BilinearInterpolation(swapAxis, optAxis, shifts_));
        } else {
            interpolation_ = new BilinearInterpolation(swapAxis, optAxis, volatilities_);
            interpolationShifts_ = new BilinearInterpolation(swapAxis, optAxis, shifts_);
        }
    }
}
