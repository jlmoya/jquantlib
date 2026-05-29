/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/
 */
/*
 Copyright (C) 2015 Klaus Spanderen

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/
package org.jquantlib.termstructures.volatilities.equityfx;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.optimization.Constraint;
import org.jquantlib.math.optimization.PositiveConstraint;
import org.jquantlib.model.CalibratedModel;
import org.jquantlib.model.ConstantParameter;
import org.jquantlib.termstructures.LocalVolTermStructure;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;

/**
 * Parameterized (calibratable) grid local-volatility surface.
 *
 * <p>Java port of C++ QuantLib v1.42.1
 * {@code ql/termstructures/volatility/equityfx/gridmodellocalvolsurface.{hpp,cpp}} — class
 * {@code GridModelLocalVolSurface}.
 *
 * <h3>Multiple-inheritance adaptation</h3>
 * C++ derives from <em>both</em> {@code LocalVolTermStructure} and {@code CalibratedModel}.
 * Java has single inheritance, so this class <strong>extends {@link LocalVolTermStructure}</strong>
 * (the dominant "is-a": it is consumed as a local-vol surface) and realizes the
 * {@link CalibratedModel} aspect by <strong>composition</strong>: a private inner
 * {@link GridModel} subclass of {@code CalibratedModel} owns the calibration parameters
 * ({@code arguments_}), and the surface delegates the {@code CalibratedModel} public API
 * ({@link #params()}, {@link #setParams(Array)}, {@link #calibrate}, {@link #constraint()},
 * {@link #endCriteria()}) to it. The inner model's {@code generateArguments()} delegates back
 * to {@link #generateArguments()} on this surface, preserving the exact C++ semantics where
 * (re)generating arguments rebuilds the embedded {@link FixedLocalVolSurface}.
 *
 * <p>The grid holds {@code dates.size() * strikes.front().size()} parameters, each a
 * {@link ConstantParameter} under a {@link PositiveConstraint} initialised to {@code 1.0}.
 * Calibration mutates these and {@link #generateArguments()} re-projects them into the
 * underlying {@link FixedLocalVolSurface}'s vol matrix
 * ({@code rows = nStrikes}, {@code cols = nTimes}).
 */
public class GridModelLocalVolSurface extends LocalVolTermStructure {

    /** Alias mirroring C++ {@code typedef FixedLocalVolSurface::Extrapolation Extrapolation}. */
    public static final FixedLocalVolSurface.Extrapolation CONSTANT_EXTRAPOLATION =
            FixedLocalVolSurface.Extrapolation.ConstantExtrapolation;

    private final Date referenceDate_;
    private final double[] times_;
    private final List< double[] > strikes_;
    private final DayCounter dayCounter_;
    private final FixedLocalVolSurface.Extrapolation lowerExtrapolation_;
    private final FixedLocalVolSurface.Extrapolation upperExtrapolation_;

    /** Composition: the CalibratedModel aspect (C++ multiple-inheritance second base). */
    private final GridModel model_;

    private FixedLocalVolSurface localVol_;

    //
    // public constructors
    //

    /** Constructor with default constant lower/upper extrapolation (mirrors C++ defaults). */
    public GridModelLocalVolSurface(final Date referenceDate, final List< Date > dates,
            final List< double[] > strikes, final DayCounter dayCounter) {
        this(referenceDate, dates, strikes, dayCounter,
                FixedLocalVolSurface.Extrapolation.ConstantExtrapolation,
                FixedLocalVolSurface.Extrapolation.ConstantExtrapolation);
    }

    public GridModelLocalVolSurface(final Date referenceDate, final List< Date > dates,
            final List< double[] > strikes, final DayCounter dayCounter,
            final FixedLocalVolSurface.Extrapolation lowerExtrapolation,
            final FixedLocalVolSurface.Extrapolation upperExtrapolation) {
        super(referenceDate, new org.jquantlib.time.calendars.NullCalendar(), BusinessDayConvention.Following,
                dayCounter);

        this.referenceDate_ = referenceDate;
        this.strikes_ = new ArrayList<>(strikes.size());
        for ( final double[] s : strikes ) {
            this.strikes_.add(s.clone());
        }
        this.dayCounter_ = dayCounter;
        this.lowerExtrapolation_ = lowerExtrapolation;
        this.upperExtrapolation_ = upperExtrapolation;

        for ( int i = 1; i < strikes_.size(); ++i ) {
            QL.require(strikes_.get(i).length == strikes_.get(0).length,
                    "strike vectors must have the same dimension");
        }

        // C++: CalibratedModel(dates.size()*strikes.front()->size())
        this.model_ = new GridModel(dates.size() * strikes_.get(0).length);

        this.times_ = new double[dates.size()];
        for ( int i = 0; i < dates.size(); ++i ) {
            this.times_[i] = dayCounter.yearFraction(referenceDate_, dates.get(i));
        }

        generateArguments();
    }

    //
    // LocalVolTermStructure / TermStructure interface
    //

    @Override
    public void update() {
        // C++: LocalVolTermStructure::update(); CalibratedModel::update();
        super.update();
        model_.update();
    }

    @Override
    public Date maxDate() {
        return localVol_.maxDate();
    }

    @Override
    public double maxTime() {
        return localVol_.maxTime();
    }

    @Override
    public double minStrike() {
        return localVol_.minStrike();
    }

    @Override
    public double maxStrike() {
        return localVol_.maxStrike();
    }

    @Override
    protected double localVolImpl(final double t, final double strike) {
        return localVol_.localVol(t, strike, true);
    }

    //
    // CalibratedModel aspect — delegated to the composed inner model
    //

    /** @return calibration parameter vector (mirrors C++ {@code CalibratedModel::params()}). */
    public Array params() {
        return model_.params();
    }

    /** Set the calibration parameter vector (mirrors C++ {@code CalibratedModel::setParams}). */
    public void setParams(final Array params) {
        model_.setParams(params);
    }

    /** @return the active calibration constraint. */
    public Constraint constraint() {
        return model_.constraint();
    }

    /** @return end-criteria of the last calibration. */
    public org.jquantlib.math.optimization.EndCriteria.Type endCriteria() {
        return model_.endCriteria();
    }

    /** Calibrate the grid against a set of helpers (mirrors C++ {@code CalibratedModel::calibrate}). */
    public void calibrate(final List< org.jquantlib.model.CalibrationHelper > instruments,
            final org.jquantlib.math.optimization.OptimizationMethod method,
            final org.jquantlib.math.optimization.EndCriteria endCriteria,
            final Constraint additionalConstraint, final double[] weights) {
        model_.calibrate(instruments, method, endCriteria, additionalConstraint, weights);
    }

    //
    // protected — generateArguments rebuilds the embedded FixedLocalVolSurface
    //

    /**
     * Mirrors C++ {@code GridModelLocalVolSurface::generateArguments()}: project the calibration parameters into a
     * {@code (nStrikes x nTimes)} vol matrix and rebuild the embedded {@link FixedLocalVolSurface}.
     */
    protected void generateArguments() {
        final List< org.jquantlib.model.Parameter > args = model_.arguments();
        final int nStrikes = strikes_.get(0).length;
        final int nTimes = times_.length;
        final Matrix localVolMatrix = new Matrix(nStrikes, nTimes);
        // C++ std::transform writes arguments_ row-major into a (nStrikes rows x nTimes cols)
        // Matrix using Matrix::begin() (row-major iteration).
        int k = 0;
        for ( int r = 0; r < nStrikes; ++r ) {
            for ( int c = 0; c < nTimes; ++c, ++k ) {
                localVolMatrix.set(r, c, args.get(k).get(0.0));
            }
        }
        localVol_ = new FixedLocalVolSurface(referenceDate_, times_.clone(), strikes_, localVolMatrix, dayCounter_,
                lowerExtrapolation_, upperExtrapolation_);
    }

    //
    // inner class: CalibratedModel aspect
    //

    private final class GridModel extends CalibratedModel {

        GridModel(final int nArguments) {
            super(nArguments);
            // C++: std::fill(arguments_.begin(), arguments_.end(),
            //               ConstantParameter(1.0, PositiveConstraint()));
            for ( int i = 0; i < arguments_.size(); ++i ) {
                arguments_.set(i, new ConstantParameter(1.0, new PositiveConstraint()));
            }
        }

        List< org.jquantlib.model.Parameter > arguments() {
            return arguments_;
        }

        @Override
        protected void generateArguments() {
            // Delegate to the outer surface; this rebuilds localVol_.
            GridModelLocalVolSurface.this.generateArguments();
        }
    }
}
