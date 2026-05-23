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

 JQuantLib is based on QuantLib. http://quantlib.org/
*/

/*
 Copyright (C) 2007 Cristina Duminuco
 Copyright (C) 2007 Ferdinando Ametrano

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.experimental.volatility;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.interpolations.AbcdInterpolation;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.EndCriteria;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.util.Observer;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;

/**
 * Abcd-interpolated at-the-money (no-smile) volatility curve.
 *
 * <p>Faithful port of QuantLib v1.42.1
 * {@code ql/experimental/volatility/abcdatmvolcurve.{hpp,cpp}}. Wraps an
 * {@link AbcdInterpolation} fit over the subset of (optionTenor, ATM vol) pairs
 * flagged for inclusion; queries at arbitrary times use the calibrated Abcd
 * function value scaled by a linear-interpolated correction factor
 * {@code k(t) = blackVol[i] / value(t[i])}.
 *
 * <p>Java port simplifications:
 * <ul>
 *   <li>C++ double-inherits from {@code BlackAtmVolCurve} and
 *       {@code LazyObject}; Java single-inheritance keeps
 *       {@link BlackAtmVolCurve} as the parent and re-runs
 *       {@link #performCalculations()} eagerly whenever a market quote
 *       changes (mirrors {@code SabrInterpolatedSmileSection}'s strategy).</li>
 *   <li>Quote-observer wiring uses {@link Observer} directly; the
 *       {@code performCalculations()} hook re-snapshots vols and updates the
 *       Abcd fit.</li>
 * </ul>
 */
public class AbcdAtmVolCurve extends BlackAtmVolCurve {

    private final int nOptionTenors_;
    private final List< Period > optionTenors_;
    private final List< Period > actualOptionTenors_;
    private final List< Date > optionDates_;
    private final List< Double > optionTimes_;
    private final List< Double > actualOptionTimes_;
    private final List< Handle< Quote > > volHandles_;
    private final List< Double > vols_;
    private final List< Double > actualVols_;
    private final List< Boolean > inclusionInInterpolation_;

    private AbcdInterpolation interpolation_;
    private Date evaluationDate_;
    private boolean dirty_;

    public AbcdAtmVolCurve(final int settlementDays, final Calendar cal, final List< Period > optionTenors,
            final List< Handle< Quote > > volsHandles) {
        this(settlementDays, cal, optionTenors, volsHandles, singletonInclusion(true), BusinessDayConvention.Following,
                new Actual365Fixed());
    }

    public AbcdAtmVolCurve(final int settlementDays, final Calendar cal, final List< Period > optionTenors,
            final List< Handle< Quote > > volsHandles, final List< Boolean > inclusionFlag,
            final BusinessDayConvention bdc, final DayCounter dc) {
        super(settlementDays, cal, bdc, dc);

        this.nOptionTenors_ = optionTenors.size();
        this.optionTenors_ = new ArrayList<>(optionTenors);
        this.actualOptionTenors_ = new ArrayList<>(nOptionTenors_);
        this.optionDates_ = new ArrayList<>(nOptionTenors_);
        this.optionTimes_ = new ArrayList<>(nOptionTenors_);
        this.actualOptionTimes_ = new ArrayList<>(nOptionTenors_);
        for ( int i = 0; i < nOptionTenors_; ++i ) {
            optionDates_.add(null);
            optionTimes_.add(0.0);
        }
        this.volHandles_ = new ArrayList<>(volsHandles);
        this.vols_ = new ArrayList<>(volsHandles.size());
        this.actualVols_ = new ArrayList<>(volsHandles.size());
        for ( int i = 0; i < volsHandles.size(); ++i ) {
            vols_.add(0.0);
        }
        this.inclusionInInterpolation_ = new ArrayList<>(inclusionFlag);

        checkInputs();
        initializeOptionDatesAndTimes();
        initializeVolatilities();
        registerWithMarketData();
        for ( int i = 0; i < vols_.size(); ++i ) {
            vols_.set(i, volHandles_.get(i).currentLink().value());
        }
        interpolate();
        this.evaluationDate_ = new Settings().evaluationDate();
        this.dirty_ = false;
    }

    private static List< Boolean > singletonInclusion(final boolean v) {
        final List< Boolean > l = new ArrayList<>(1);
        l.add(v);
        return l;
    }

    private void checkInputs() {
        QL.require(!optionTenors_.isEmpty(), "empty option tenor vector");
        QL.require(nOptionTenors_ == vols_.size(),
                "mismatch between number of option tenors (" + nOptionTenors_ + ") and number of volatilities ("
                        + vols_.size() + ")");
        QL.require(optionTenors_.get(0).gt(new Period(0, TimeUnit.Days)),
                "negative first option tenor: " + optionTenors_.get(0));
        for ( int i = 1; i < nOptionTenors_; ++i ) {
            QL.require(optionTenors_.get(i).gt(optionTenors_.get(i - 1)), "non increasing option tenor at index " + i);
        }
        if ( inclusionInInterpolation_.size() == 1 ) {
            final boolean v = inclusionInInterpolation_.get(0);
            for ( int j = 1; j < nOptionTenors_; ++j ) {
                inclusionInInterpolation_.add(v);
            }
        } else {
            QL.require(nOptionTenors_ == inclusionInInterpolation_.size(),
                    "mismatch between number of option tenors (" + nOptionTenors_ + ") and number of inclusion flags ("
                            + inclusionInInterpolation_.size() + ")");
        }
    }

    private void registerWithMarketData() {
        for ( final Handle< Quote > h : volHandles_ ) {
            if ( h != null && h.currentLink() != null ) {
                h.currentLink().addObserver(new Observer() {
                    @Override
                    public void update() {
                        AbcdAtmVolCurve.this.dirty_ = true;
                        AbcdAtmVolCurve.this.notifyObservers();
                    }
                });
            }
        }
    }

    private void interpolate() {
        this.interpolation_ = new AbcdInterpolation(toArray(actualOptionTimes_), toArray(actualVols_),
                -0.06, 0.17, 0.54, 0.17, false, false, false, false, false, null, null);
    }

    private static Array toArray(final List<Double> list) {
        final double[] a = new double[list.size()];
        for (int i = 0; i < a.length; i++) a[i] = list.get(i);
        return new Array(a);
    }

    private void initializeOptionDatesAndTimes() {
        for ( int i = 0; i < nOptionTenors_; ++i ) {
            optionDates_.set(i, optionDateFromTenor(optionTenors_.get(i)));
            optionTimes_.set(i, timeFromReference(optionDates_.get(i)));
        }
        actualOptionTimes_.clear();
        actualOptionTenors_.clear();
        for ( int i = 0; i < nOptionTenors_; ++i ) {
            if ( inclusionInInterpolation_.get(i) ) {
                actualOptionTimes_.add(optionTimes_.get(i));
                actualOptionTenors_.add(optionTenors_.get(i));
            }
        }
    }

    private void initializeVolatilities() {
        actualVols_.clear();
        for ( int i = 0; i < nOptionTenors_; ++i ) {
            vols_.set(i, volHandles_.get(i).currentLink().value());
            if ( inclusionInInterpolation_.get(i) ) {
                actualVols_.add(vols_.get(i));
            }
        }
    }

    /**
     * Re-snapshot all vols and re-run the Abcd calibration. Mirrors C++
     * {@code performCalculations()} (called lazily by {@code calculate()}).
     */
    public void performCalculations() {
        actualVols_.clear();
        for ( int i = 0; i < vols_.size(); ++i ) {
            vols_.set(i, volHandles_.get(i).currentLink().value());
            if ( inclusionInInterpolation_.get(i) ) {
                actualVols_.add(vols_.get(i));
            }
        }
        interpolation_ = new AbcdInterpolation(toArray(actualOptionTimes_), toArray(actualVols_),
                -0.06, 0.17, 0.54, 0.17, false, false, false, false, false, null, null);
        dirty_ = false;
    }

    private void calculate() {
        if ( dirty_ ) {
            performCalculations();
        }
    }

    @Override
    public void update() {
        // recalculate dates if necessary
        final Date d = new Settings().evaluationDate();
        if ( evaluationDate_ != null && !evaluationDate_.eq(d) ) {
            evaluationDate_ = d;
            initializeOptionDatesAndTimes();
        }
        dirty_ = true;
        super.update();
    }

    @Override
    public Date maxDate() {
        calculate();
        return optionDateFromTenor(optionTenors_.get(optionTenors_.size() - 1));
    }

    @Override
    public double minStrike() {
        return Double.NEGATIVE_INFINITY;
    }

    @Override
    public double maxStrike() {
        return Double.POSITIVE_INFINITY;
    }

    @Override
    protected double atmVarianceImpl(final double t) {
        final double vol = atmVolImpl(t);
        return vol * vol * t;
    }

    @Override
    protected double atmVolImpl(final double t) {
        calculate();
        return k(t) * interpolation_.op(t, true);
    }

    // --- Inspectors mirroring C++ inline accessors ---

    public List< Period > optionTenors() {
        return optionTenors_;
    }

    public List< Period > optionTenorsInInterpolation() {
        return actualOptionTenors_;
    }

    public List< Date > optionDates() {
        return optionDates_;
    }

    public List< Double > optionTimes() {
        return optionTimes_;
    }

    public List< Double > k() {
        return interpolation_.k();
    }

    public double k(final double t) {
        final Array times = toArray(actualOptionTimes_);
        return interpolation_.k(t, times, times);
    }

    public double a() {
        return interpolation_.a();
    }

    public double b() {
        return interpolation_.b();
    }

    public double c() {
        return interpolation_.c();
    }

    public double d() {
        return interpolation_.d();
    }

    public double rmsError() {
        return interpolation_.rmsError();
    }

    public double maxError() {
        return interpolation_.maxError();
    }

    public EndCriteria.Type endCriteria() {
        return interpolation_.endCriteria();
    }

    @Override
    public void accept(final PolymorphicVisitor pv) {
        final Visitor< AbcdAtmVolCurve > v = (pv != null) ? pv.visitor(this.getClass()) : null;
        if ( v != null ) {
            v.visit(this);
        } else {
            super.accept(pv);
        }
    }
}
