/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/
 */
/*
 Copyright (C) 2007 Ferdinando Ametrano
 Copyright (C) 2007 Katiuscia Manzoni

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */
package org.jquantlib.termstructures.volatilities.capfloor;

import org.jquantlib.QL;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.interpolations.BicubicSplineInterpolation;
import org.jquantlib.math.interpolations.Interpolation2D;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;

import java.util.ArrayList;
import java.util.List;

/**
 * Cap/floor smile-volatility surface.
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/termstructures/volatility/capfloor/capfloortermvolsurface.{hpp,cpp}}.
 * Bicubic-spline interpolation over the {@code (strike, optionTime) → vol} grid (the strike axis is x, the option-time
 * axis is y) — mirrors C++ which uses {@code BicubicSpline(strikes_, optionTimes_, vols_)}.
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 *  <li>Lazy semantics composed inline (calculated_ flag) rather than via
 *      LazyObject multi-inheritance — same pattern used by the curve sibling.</li>
 *  <li>The 2-D interpolator has extrapolation enabled at the
 *      {@code volatilityImpl} call site (true argument to op()), matching the
 *      C++ {@code interpolation_(strike, t, true)} call.</li>
 * </ul>
 */
public class CapFloorTermVolSurface extends CapFloorTermVolatilityStructure {

    //
    // private fields
    //

    private final int nOptionTenors_;
    private final List< Period > optionTenors_;
    private final List< Date > optionDates_;
    private final double[] optionTimes_;
    private final int nStrikes_;
    private final double[] strikes_;
    private final List< List< Handle< ? extends Quote > > > volHandles_;
    private final Matrix vols_;
    /** Mirror of C++ {@code LazyObject::calculated_}. */
    protected boolean calculated_;
    private final Date evaluationDate_;
    private Interpolation2D interpolation_;

    //
    // public constructors
    //

    /** Floating reference date, floating market data. */
    public CapFloorTermVolSurface(final int settlementDays, final Calendar cal, final BusinessDayConvention bdc,
            final List< Period > optionTenors, final double[] strikes,
            final List< List< Handle< ? extends Quote > > > vols, final DayCounter dc) {
        super(settlementDays, cal, bdc, dc);
        this.nOptionTenors_ = optionTenors.size();
        this.optionTenors_ = new ArrayList< Period >(optionTenors);
        this.optionDates_ = newNullList(nOptionTenors_);
        this.optionTimes_ = new double[nOptionTenors_];
        this.nStrikes_ = strikes.length;
        this.strikes_ = strikes.clone();
        this.volHandles_ = vols;
        this.vols_ = new Matrix(nOptionTenors_, nStrikes_);
        checkInputs();
        initializeOptionDatesAndTimes();
        registerWithMarketData();
        interpolate();
        this.evaluationDate_ = referenceDate();
        this.calculated_ = false;
    }

    /** Fixed reference date, floating market data. */
    public CapFloorTermVolSurface(final Date settlementDate, final Calendar cal, final BusinessDayConvention bdc,
            final List< Period > optionTenors, final double[] strikes,
            final List< List< Handle< ? extends Quote > > > vols, final DayCounter dc) {
        super(settlementDate, cal, bdc, dc);
        this.nOptionTenors_ = optionTenors.size();
        this.optionTenors_ = new ArrayList< Period >(optionTenors);
        this.optionDates_ = newNullList(nOptionTenors_);
        this.optionTimes_ = new double[nOptionTenors_];
        this.nStrikes_ = strikes.length;
        this.strikes_ = strikes.clone();
        this.volHandles_ = vols;
        this.vols_ = new Matrix(nOptionTenors_, nStrikes_);
        checkInputs();
        initializeOptionDatesAndTimes();
        registerWithMarketData();
        interpolate();
        this.evaluationDate_ = referenceDate();
        this.calculated_ = false;
    }

    /** Fixed reference date, fixed market data (Matrix input). */
    public CapFloorTermVolSurface(final Date settlementDate, final Calendar cal, final BusinessDayConvention bdc,
            final List< Period > optionTenors, final double[] strikes, final Matrix volatilities, final DayCounter dc) {
        super(settlementDate, cal, bdc, dc);
        this.nOptionTenors_ = optionTenors.size();
        this.optionTenors_ = new ArrayList< Period >(optionTenors);
        this.optionDates_ = newNullList(nOptionTenors_);
        this.optionTimes_ = new double[nOptionTenors_];
        this.nStrikes_ = strikes.length;
        this.strikes_ = strikes.clone();
        this.vols_ = new Matrix(volatilities.rows(), volatilities.columns());
        // Mirror C++ ctor: pre-fill with eager copy of the given Matrix.
        for ( int i = 0; i < volatilities.rows(); ++i ) {
            for ( int j = 0; j < volatilities.columns(); ++j ) {
                this.vols_.set(i, j, volatilities.get(i, j));
            }
        }
        // Wrap each cell in a SimpleQuote (parity with C++ which builds dummy
        // Handle<Quote> objects to allow generic handle-based recompute later).
        this.volHandles_ = new ArrayList< List< Handle< ? extends Quote > > >(volatilities.rows());
        for ( int i = 0; i < volatilities.rows(); ++i ) {
            final List< Handle< ? extends Quote > > row = new ArrayList< Handle< ? extends Quote > >(
                    volatilities.columns());
            for ( int j = 0; j < volatilities.columns(); ++j ) {
                row.add(new Handle< Quote >(new SimpleQuote(volatilities.get(i, j))));
            }
            this.volHandles_.add(row);
        }
        checkInputs();
        initializeOptionDatesAndTimes();
        interpolate();
        this.evaluationDate_ = referenceDate();
        this.calculated_ = false;
    }

    /** Floating reference date, fixed market data (Matrix input). */
    public CapFloorTermVolSurface(final int settlementDays, final Calendar cal, final BusinessDayConvention bdc,
            final List< Period > optionTenors, final double[] strikes, final Matrix volatilities, final DayCounter dc) {
        super(settlementDays, cal, bdc, dc);
        this.nOptionTenors_ = optionTenors.size();
        this.optionTenors_ = new ArrayList< Period >(optionTenors);
        this.optionDates_ = newNullList(nOptionTenors_);
        this.optionTimes_ = new double[nOptionTenors_];
        this.nStrikes_ = strikes.length;
        this.strikes_ = strikes.clone();
        this.vols_ = new Matrix(volatilities.rows(), volatilities.columns());
        for ( int i = 0; i < volatilities.rows(); ++i ) {
            for ( int j = 0; j < volatilities.columns(); ++j ) {
                this.vols_.set(i, j, volatilities.get(i, j));
            }
        }
        this.volHandles_ = new ArrayList< List< Handle< ? extends Quote > > >(volatilities.rows());
        for ( int i = 0; i < volatilities.rows(); ++i ) {
            final List< Handle< ? extends Quote > > row = new ArrayList< Handle< ? extends Quote > >(
                    volatilities.columns());
            for ( int j = 0; j < volatilities.columns(); ++j ) {
                row.add(new Handle< Quote >(new SimpleQuote(volatilities.get(i, j))));
            }
            this.volHandles_.add(row);
        }
        checkInputs();
        initializeOptionDatesAndTimes();
        interpolate();
        this.evaluationDate_ = referenceDate();
        this.calculated_ = false;
    }

    /** Convenience: ctor with default Actual365Fixed day-counter. */
    public CapFloorTermVolSurface(final Date settlementDate, final Calendar cal, final BusinessDayConvention bdc,
            final List< Period > optionTenors, final double[] strikes, final Matrix volatilities) {
        this(settlementDate, cal, bdc, optionTenors, strikes, volatilities, new Actual365Fixed());
    }

    //
    // CapFloorTermVolatilityStructure interface
    //

    private static List< Date > newNullList(final int n) {
        final List< Date > out = new ArrayList< Date >(n);
        for ( int i = 0; i < n; ++i ) {
            out.add(null);
        }
        return out;
    }

    @Override
    public Date maxDate() {
        calculate();
        return optionDateFromTenor(optionTenors_.get(nOptionTenors_ - 1));
    }

    @Override
    public double minStrike() {
        return strikes_[0];
    }

    @Override
    public double maxStrike() {
        return strikes_[strikes_.length - 1];
    }

    //
    // inspectors
    //

    @Override
    protected double volatilityImpl(final double t, final double strike) {
        calculate();
        // Mirror C++: interpolation_(strike, t, true) — strike is x-axis.
        return interpolation_.op(strike, t, true);
    }

    public List< Period > optionTenors() {
        return optionTenors_;
    }

    public List< Date > optionDates() {
        calculate();
        return optionDates_;
    }

    public double[] optionTimes() {
        calculate();
        return optionTimes_;
    }

    //
    // observer / lazy plumbing
    //

    public double[] strikes() {
        return strikes_;
    }

    @Override
    public void update() {
        super.update();
        calculated_ = false;
    }

    protected final void calculate() {
        if ( !calculated_ ) {
            calculated_ = true;
            try {
                performCalculations();
            } catch ( final RuntimeException e ) {
                calculated_ = false;
                throw e;
            }
        }
    }

    //
    // helpers
    //

    protected void performCalculations() {
        // Pull market data
        for ( int i = 0; i < nOptionTenors_; ++i ) {
            for ( int j = 0; j < nStrikes_; ++j ) {
                vols_.set(i, j, volHandles_.get(i).get(j).currentLink().value());
            }
        }
        // 2-D interpolation refresh (BilinearInterpolation lazy update)
        if ( interpolation_ != null ) {
            interpolation_.update();
        }
    }

    private void checkInputs() {
        QL.require(!optionTenors_.isEmpty(), "empty option tenor vector");
        QL.require(optionTenors_.get(0).length() > 0, "negative first option tenor: " + optionTenors_.get(0));
        for ( int i = 1; i < nOptionTenors_; ++i ) {
            QL.require(optionTenors_.get(i).gt(optionTenors_.get(i - 1)),
                    "non increasing option tenor: position " + i + " is " + optionTenors_.get(i - 1) + ", position " + (
                            i + 1) + " is " + optionTenors_.get(i));
        }
        QL.require(strikes_.length > 0, "empty strikes vector");
        for ( int j = 1; j < strikes_.length; ++j ) {
            QL.require(strikes_[j] > strikes_[j - 1],
                    "non increasing strikes: position " + j + " is " + strikes_[j - 1] + ", position " + (j + 1)
                            + " is " + strikes_[j]);
        }
        QL.require(vols_.rows() == nOptionTenors_,
                "vols matrix rows mismatch: " + vols_.rows() + " vs nOptionTenors_=" + nOptionTenors_);
        QL.require(vols_.columns() == nStrikes_,
                "vols matrix columns mismatch: " + vols_.columns() + " vs nStrikes_=" + nStrikes_);
    }

    private void registerWithMarketData() {
        for ( int i = 0; i < volHandles_.size(); ++i ) {
            for ( int j = 0; j < volHandles_.get(i).size(); ++j ) {
                volHandles_.get(i).get(j).addObserver(this);
            }
        }
    }

    private void initializeOptionDatesAndTimes() {
        for ( int i = 0; i < nOptionTenors_; ++i ) {
            optionDates_.set(i, optionDateFromTenor(optionTenors_.get(i)));
            optionTimes_[i] = timeFromReference(optionDates_.get(i));
        }
    }

    private void interpolate() {
        final Array strikeAxis = new Array(strikes_);
        final Array timeAxis = new Array(optionTimes_);
        // Mirror C++: BicubicSpline over (strike-x, t-y, vols)
        // (cf. capfloortermvolsurface.cpp interpolate())
        interpolation_ = new BicubicSplineInterpolation(strikeAxis, timeAxis, vols_);
    }
}
