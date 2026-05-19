/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/
 */
/*
 Copyright (C) 2007 Ferdinando Ametrano
 Copyright (C) 2007 Katiuscia Manzoni
 Copyright (C) 2000, 2001, 2002, 2003 RiskMap srl
 Copyright (C) 2003, 2004, 2005 StatPro Italia srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */
package org.jquantlib.termstructures.volatilities.capfloor;

import org.jquantlib.QL;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.Constants;
import org.jquantlib.math.interpolations.CubicInterpolation;
import org.jquantlib.math.interpolations.Interpolation;
import org.jquantlib.math.matrixutilities.Array;
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
 * Cap/floor at-the-money term-volatility vector.
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/termstructures/volatility/capfloor/capfloortermvolcurve.{hpp,cpp}}. Provides
 * ATM cap/floor vol via cubic-spline interpolation (natural BC: zero second derivative at both ends) over the
 * {@code (optionTime[i], vol[i])} grid.
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 *  <li>The C++ class multiply-inherits from {@code LazyObject} +
 *      {@code CapFloorTermVolatilityStructure}; Java composes lazy semantics
 *      inline (a {@code calculated_} flag), mirroring the pattern used in
 *      {@code SwaptionVolatilityDiscrete}.</li>
 * </ul>
 */
public class CapFloorTermVolCurve extends CapFloorTermVolatilityStructure {

    //
    // private fields
    //

    private final int nOptionTenors_;
    private final List< Period > optionTenors_;
    private final List< Date > optionDates_;
    private final double[] optionTimes_;
    private final List< Handle< ? extends Quote > > volHandles_;
    private final double[] vols_;
    /**
     * {@code true} once {@link #performCalculations()} has been run since the last invalidation. Mirrors C++
     * {@code LazyObject::calculated_}.
     */
    protected boolean calculated_;
    private final Date evaluationDate_;
    private Interpolation interpolation_;

    //
    // public constructors
    //

    /** Floating reference date, floating market data. */
    public CapFloorTermVolCurve(final int settlementDays, final Calendar cal, final BusinessDayConvention bdc,
            final List< Period > optionTenors, final List< Handle< ? extends Quote > > vols, final DayCounter dc) {
        super(settlementDays, cal, bdc, dc);
        this.nOptionTenors_ = optionTenors.size();
        this.optionTenors_ = new ArrayList< Period >(optionTenors);
        this.optionDates_ = new ArrayList< Date >(nOptionTenors_);
        for ( int i = 0; i < nOptionTenors_; ++i ) {
            this.optionDates_.add(null);
        }
        this.optionTimes_ = new double[nOptionTenors_];
        this.volHandles_ = new ArrayList< Handle< ? extends Quote > >(vols);
        this.vols_ = new double[vols.size()];
        checkInputs();
        initializeOptionDatesAndTimes();
        registerWithMarketData();
        interpolate();
        this.evaluationDate_ = referenceDate();
        this.calculated_ = false;
    }

    /** Fixed reference date, floating market data. */
    public CapFloorTermVolCurve(final Date settlementDate, final Calendar cal, final BusinessDayConvention bdc,
            final List< Period > optionTenors, final List< Handle< ? extends Quote > > vols, final DayCounter dc) {
        super(settlementDate, cal, bdc, dc);
        this.nOptionTenors_ = optionTenors.size();
        this.optionTenors_ = new ArrayList< Period >(optionTenors);
        this.optionDates_ = new ArrayList< Date >(nOptionTenors_);
        for ( int i = 0; i < nOptionTenors_; ++i ) {
            this.optionDates_.add(null);
        }
        this.optionTimes_ = new double[nOptionTenors_];
        this.volHandles_ = new ArrayList< Handle< ? extends Quote > >(vols);
        this.vols_ = new double[vols.size()];
        checkInputs();
        initializeOptionDatesAndTimes();
        registerWithMarketData();
        interpolate();
        this.evaluationDate_ = referenceDate();
        this.calculated_ = false;
    }

    /** Fixed reference date, fixed market data (double[]). */
    public CapFloorTermVolCurve(final Date settlementDate, final Calendar cal, final BusinessDayConvention bdc,
            final List< Period > optionTenors, final double[] vols, final DayCounter dc) {
        super(settlementDate, cal, bdc, dc);
        this.nOptionTenors_ = optionTenors.size();
        this.optionTenors_ = new ArrayList< Period >(optionTenors);
        this.optionDates_ = new ArrayList< Date >(nOptionTenors_);
        for ( int i = 0; i < nOptionTenors_; ++i ) {
            this.optionDates_.add(null);
        }
        this.optionTimes_ = new double[nOptionTenors_];
        this.vols_ = vols.clone();
        this.volHandles_ = new ArrayList< Handle< ? extends Quote > >(vols.length);
        for ( int i = 0; i < vols.length; ++i ) {
            this.volHandles_.add(new Handle< Quote >(new SimpleQuote(vols[i])));
        }
        checkInputs();
        initializeOptionDatesAndTimes();
        interpolate();
        this.evaluationDate_ = referenceDate();
        this.calculated_ = false;
    }

    /** Floating reference date, fixed market data (double[]). */
    public CapFloorTermVolCurve(final int settlementDays, final Calendar cal, final BusinessDayConvention bdc,
            final List< Period > optionTenors, final double[] vols, final DayCounter dc) {
        super(settlementDays, cal, bdc, dc);
        this.nOptionTenors_ = optionTenors.size();
        this.optionTenors_ = new ArrayList< Period >(optionTenors);
        this.optionDates_ = new ArrayList< Date >(nOptionTenors_);
        for ( int i = 0; i < nOptionTenors_; ++i ) {
            this.optionDates_.add(null);
        }
        this.optionTimes_ = new double[nOptionTenors_];
        this.vols_ = vols.clone();
        this.volHandles_ = new ArrayList< Handle< ? extends Quote > >(vols.length);
        for ( int i = 0; i < vols.length; ++i ) {
            this.volHandles_.add(new Handle< Quote >(new SimpleQuote(vols[i])));
        }
        checkInputs();
        initializeOptionDatesAndTimes();
        interpolate();
        this.evaluationDate_ = referenceDate();
        this.calculated_ = false;
    }

    /** Convenience: ctor with default Actual365Fixed day-counter (mirrors C++ default). */
    public CapFloorTermVolCurve(final Date settlementDate, final Calendar cal, final BusinessDayConvention bdc,
            final List< Period > optionTenors, final double[] vols) {
        this(settlementDate, cal, bdc, optionTenors, vols, new Actual365Fixed());
    }

    //
    // CapFloorTermVolatilityStructure interface
    //

    @Override
    public Date maxDate() {
        calculate();
        return optionDateFromTenor(optionTenors_.get(nOptionTenors_ - 1));
    }

    @Override
    public double minStrike() {
        return Constants.QL_MIN_REAL;
    }

    @Override
    public double maxStrike() {
        return Constants.QL_MAX_REAL;
    }

    @Override
    protected double volatilityImpl(final double t, final double strike) {
        calculate();
        return interpolation_.op(t, true);
    }

    //
    // inspectors
    //

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

    @Override
    public void update() {
        super.update();
        calculated_ = false;
    }

    /**
     * Force re-evaluation if invalidated. Mirror of C++ {@code LazyObject::calculate()}.
     */
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

    /**
     * Pull market data into vols_ and refresh the interpolation. Mirror of C++
     * {@code CapFloorTermVolCurve::performCalculations()}.
     */
    protected void performCalculations() {
        for ( int i = 0; i < vols_.length; ++i ) {
            vols_[i] = volHandles_.get(i).currentLink().value();
        }
        if ( interpolation_ != null ) {
            interpolation_.update();
        }
    }

    //
    // helpers
    //

    private void checkInputs() {
        QL.require(!optionTenors_.isEmpty(), "empty option tenor vector");
        QL.require(nOptionTenors_ == vols_.length,
                "mismatch between number of option tenors (" + nOptionTenors_ + ") and number of volatilities ("
                        + vols_.length + ")");
        QL.require(optionTenors_.get(0).length() > 0, "negative first option tenor: " + optionTenors_.get(0));
        for ( int i = 1; i < nOptionTenors_; ++i ) {
            QL.require(optionTenors_.get(i).gt(optionTenors_.get(i - 1)),
                    "non increasing option tenor: position " + i + " is " + optionTenors_.get(i - 1) + ", position " + (
                            i + 1) + " is " + optionTenors_.get(i));
        }
    }

    private void registerWithMarketData() {
        for ( int i = 0; i < volHandles_.size(); ++i ) {
            volHandles_.get(i).addObserver(this);
        }
    }

    private void initializeOptionDatesAndTimes() {
        for ( int i = 0; i < nOptionTenors_; ++i ) {
            optionDates_.set(i, optionDateFromTenor(optionTenors_.get(i)));
            optionTimes_[i] = timeFromReference(optionDates_.get(i));
        }
    }

    /**
     * Build a natural-BC cubic spline over (optionTimes_, vols_). Mirror of C++
     * {@code CapFloorTermVolCurve::interpolate()}:
     * {@code CubicInterpolation::Spline / monotonic=false / SecondDerivative=0 at both ends}.
     */
    private void interpolate() {
        final Array x = new Array(optionTimes_);
        final Array y = new Array(vols_);
        interpolation_ = new CubicInterpolation(x, y, CubicInterpolation.DerivativeApprox.Spline, false,
                CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0,
                CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0);
    }
}
