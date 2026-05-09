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
 Copyright (C) 2006 Ferdinando Ametrano
 Copyright (C) 2006 François du Vignaud

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */
package org.jquantlib.termstructures.volatilities.swaption;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.interpolations.Interpolation;
import org.jquantlib.math.interpolations.LinearInterpolation;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.termstructures.SwaptionVolatilityStructure;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;

/**
 * Discretized swaption volatility — abstract base for {@link SwaptionVolatilityMatrix}.
 * <p>
 * Port of C++ QuantLib v1.42.1
 * {@code ql/termstructures/volatility/swaption/swaptionvoldiscrete.{hpp,cpp}}.
 *
 * <p>The C++ class multiply-inherits from {@code LazyObject} and
 * {@code SwaptionVolatilityStructure}. Java's single-inheritance forces us
 * to compose the lazy-evaluation semantics inline (a {@code calculated_} flag
 * + {@link #performCalculations()} hook + {@link #calculate()}). This mirrors
 * the pattern used elsewhere in the JQuantLib port (see
 * {@code PiecewiseYoYOptionletVolatility} / {@code PiecewiseYieldCurve}).
 */
public abstract class SwaptionVolatilityDiscrete extends SwaptionVolatilityStructure {

    //
    // protected fields
    //

    protected final int nOptionTenors_;
    protected final List<Period> optionTenors_;
    protected final List<Date> optionDates_;
    protected final double[] optionTimes_;
    protected final double[] optionDatesAsReal_;
    protected final double[] optionInterpolatorTimes_;
    protected final double[] optionInterpolatorDatesAsReal_;

    protected final int nSwapTenors_;
    protected final List<Period> swapTenors_;
    protected final double[] swapLengths_;

    protected Date cachedReferenceDate_;
    protected Interpolation optionInterpolator_;

    /** {@code true} once {@link #performCalculations()} has been run since the
     *  last invalidation. Mirrors C++ {@code LazyObject::calculated_}. */
    protected boolean calculated_;

    private final BusinessDayConvention bdc_;
    private final boolean moving_;

    //
    // public constructors
    //

    /**
     * Floating reference date constructor.
     */
    public SwaptionVolatilityDiscrete(final List<Period> optionTenors,
                                      final List<Period> swapTenors,
                                      final int settlementDays,
                                      final Calendar cal,
                                      final BusinessDayConvention bdc,
                                      final DayCounter dc) {
        super(settlementDays, cal, dc, bdc);
        this.bdc_ = bdc;
        this.moving_ = true;
        this.nOptionTenors_ = optionTenors.size();
        this.optionTenors_ = new ArrayList<Period>(optionTenors);
        this.optionDates_ = new ArrayList<Date>(nOptionTenors_);
        for (int i = 0; i < nOptionTenors_; ++i) {
            this.optionDates_.add(null);
        }
        this.optionTimes_ = new double[nOptionTenors_];
        this.optionDatesAsReal_ = new double[nOptionTenors_];
        this.optionInterpolatorTimes_ = new double[nOptionTenors_ + 1];
        this.optionInterpolatorDatesAsReal_ = new double[nOptionTenors_ + 1];

        this.nSwapTenors_ = swapTenors.size();
        this.swapTenors_ = new ArrayList<Period>(swapTenors);
        this.swapLengths_ = new double[nSwapTenors_];

        checkOptionTenors();
        initializeOptionDatesAndTimes();
        checkSwapTenors();
        initializeSwapLengths();
        rebuildOptionInterpolator();
        this.cachedReferenceDate_ = referenceDate();
        this.calculated_ = false;
    }

    /**
     * Fixed reference date constructor (option tenors).
     */
    public SwaptionVolatilityDiscrete(final List<Period> optionTenors,
                                      final List<Period> swapTenors,
                                      final Date referenceDate,
                                      final Calendar cal,
                                      final BusinessDayConvention bdc,
                                      final DayCounter dc) {
        super(referenceDate, cal, dc, bdc);
        this.bdc_ = bdc;
        this.moving_ = false;
        this.nOptionTenors_ = optionTenors.size();
        this.optionTenors_ = new ArrayList<Period>(optionTenors);
        this.optionDates_ = new ArrayList<Date>(nOptionTenors_);
        for (int i = 0; i < nOptionTenors_; ++i) {
            this.optionDates_.add(null);
        }
        this.optionTimes_ = new double[nOptionTenors_];
        this.optionDatesAsReal_ = new double[nOptionTenors_];
        this.optionInterpolatorTimes_ = new double[nOptionTenors_ + 1];
        this.optionInterpolatorDatesAsReal_ = new double[nOptionTenors_ + 1];

        this.nSwapTenors_ = swapTenors.size();
        this.swapTenors_ = new ArrayList<Period>(swapTenors);
        this.swapLengths_ = new double[nSwapTenors_];

        checkOptionTenors();
        initializeOptionDatesAndTimes();
        checkSwapTenors();
        initializeSwapLengths();
        rebuildOptionInterpolator();
        this.cachedReferenceDate_ = referenceDate();
        this.calculated_ = false;
    }

    /**
     * Fixed reference date constructor (option dates).
     * <p>
     * Marker enum disambiguates this from the {@code List<Period>} overload
     * (Java type erasure makes the two signatures identical).
     */
    public enum FromDates { Marker }

    public SwaptionVolatilityDiscrete(final List<Date> optionDates,
                                      final FromDates marker,
                                      final List<Period> swapTenors,
                                      final Date referenceDate,
                                      final Calendar cal,
                                      final BusinessDayConvention bdc,
                                      final DayCounter dc) {
        super(referenceDate, cal, dc, bdc);
        this.bdc_ = bdc;
        this.moving_ = false;
        this.nOptionTenors_ = optionDates.size();
        // optionTenors are not used in this branch (initialised to placeholders
        // so size() agrees with C++ semantics where the vector exists but is
        // never populated).
        this.optionTenors_ = new ArrayList<Period>(nOptionTenors_);
        for (int i = 0; i < nOptionTenors_; ++i) {
            this.optionTenors_.add(null);
        }
        this.optionDates_ = new ArrayList<Date>(optionDates);
        this.optionTimes_ = new double[nOptionTenors_];
        this.optionDatesAsReal_ = new double[nOptionTenors_];
        this.optionInterpolatorTimes_ = new double[nOptionTenors_ + 1];
        this.optionInterpolatorDatesAsReal_ = new double[nOptionTenors_ + 1];

        this.nSwapTenors_ = swapTenors.size();
        this.swapTenors_ = new ArrayList<Period>(swapTenors);
        this.swapLengths_ = new double[nSwapTenors_];

        checkOptionDates(referenceDate);
        // Mirror C++ ctor: option dates already known, only times need init.
        // optionDatesAsReal_ + optionInterpolatorDatesAsReal_ are populated
        // here so the LinearInterpolation has consistent input.
        optionInterpolatorDatesAsReal_[0] = (double) referenceDate().serialNumber();
        for (int i = 0; i < nOptionTenors_; ++i) {
            optionDatesAsReal_[i] = optionInterpolatorDatesAsReal_[i + 1] =
                    (double) optionDates_.get(i).serialNumber();
        }
        initializeOptionTimes();
        checkSwapTenors();
        initializeSwapLengths();
        rebuildOptionInterpolator();
        this.cachedReferenceDate_ = referenceDate();
        this.calculated_ = false;
    }

    //
    // public inspectors
    //

    public List<Period> optionTenors() {
        return optionTenors_;
    }

    public List<Date> optionDates() {
        return optionDates_;
    }

    public double[] optionTimes() {
        return optionTimes_;
    }

    public List<Period> swapTenors() {
        return swapTenors_;
    }

    public double[] swapLengths() {
        return swapLengths_;
    }

    /**
     * Date corresponding to a given option time, via inverse linear interpolation.
     * Mirrors C++ {@code SwaptionVolatilityDiscrete::optionDateFromTime}.
     */
    public Date optionDateFromTime(final double optionTime) {
        return new Date((long) optionInterpolator_.op(optionTime));
    }

    @Override
    public BusinessDayConvention businessDayConvention() {
        return bdc_;
    }

    //
    // observer / lazy interface
    //

    /**
     * Force recomputation on next read. Called by base when the reference date
     * moves (only meaningful for the floating-reference-date constructor).
     */
    @Override
    public void update() {
        super.update();
        // Invalidate the lazy cache; downstream will call calculate() again.
        calculated_ = false;
    }

    /**
     * Re-runs {@link #performCalculations()} if invalidated.
     * Mirrors C++ {@code LazyObject::calculate()}.
     */
    protected final void calculate() {
        if (!calculated_) {
            calculated_ = true;
            try {
                performCalculations();
            } catch (final RuntimeException e) {
                calculated_ = false;
                throw e;
            }
        }
    }

    /**
     * Hook: derived classes pull market data here and re-build interpolations.
     * Default implementation handles the moving-reference-date date-roll
     * (mirror of C++ {@code SwaptionVolatilityDiscrete::performCalculations()}).
     */
    protected void performCalculations() {
        if (moving_) {
            if (!cachedReferenceDate_.eq(referenceDate())) {
                cachedReferenceDate_ = referenceDate();
                initializeOptionDatesAndTimes();
                initializeSwapLengths();
                rebuildOptionInterpolator();
            }
        }
    }

    //
    // helpers
    //

    private void checkOptionTenors() {
        QL.require(optionTenors_.get(0).length() > 0,
                "first option tenor is negative (" + optionTenors_.get(0) + ")");
        for (int i = 1; i < nOptionTenors_; ++i) {
            QL.require(optionTenors_.get(i).gt(optionTenors_.get(i - 1)),
                    "non increasing option tenor: position " + i + " is "
                            + optionTenors_.get(i - 1) + ", position " + (i + 1)
                            + " is " + optionTenors_.get(i));
        }
    }

    private void checkOptionDates(final Date reference) {
        QL.require(optionDates_.get(0).gt(reference),
                "first option date (" + optionDates_.get(0)
                        + ") must be greater than reference date (" + reference + ")");
        for (int i = 1; i < nOptionTenors_; ++i) {
            QL.require(optionDates_.get(i).gt(optionDates_.get(i - 1)),
                    "non increasing option dates: position " + i + " is "
                            + optionDates_.get(i - 1) + ", position " + (i + 1)
                            + " is " + optionDates_.get(i));
        }
    }

    private void checkSwapTenors() {
        QL.require(swapTenors_.get(0).length() > 0,
                "first swap tenor is negative (" + swapTenors_.get(0) + ")");
        for (int i = 1; i < nSwapTenors_; ++i) {
            QL.require(swapTenors_.get(i).gt(swapTenors_.get(i - 1)),
                    "non increasing swap tenor: position " + i + " is "
                            + swapTenors_.get(i - 1) + ", position " + (i + 1)
                            + " is " + swapTenors_.get(i));
        }
    }

    /**
     * Computes optionDates_ from optionTenors_ using {@link #optionDateFromTenor(Period)},
     * then derives optionTimes_ and the interpolator companion arrays.
     */
    private void initializeOptionDatesAndTimes() {
        optionInterpolatorDatesAsReal_[0] = (double) referenceDate().serialNumber();
        for (int i = 0; i < nOptionTenors_; ++i) {
            optionDates_.set(i, optionDateFromTenor(optionTenors_.get(i)));
            optionDatesAsReal_[i] = optionInterpolatorDatesAsReal_[i + 1] =
                    (double) optionDates_.get(i).serialNumber();
        }
        initializeOptionTimes();
    }

    /**
     * Recompute optionTimes_ from optionDates_ using the term-structure dayCounter.
     */
    private void initializeOptionTimes() {
        optionInterpolatorTimes_[0] = 0.0;
        for (int i = 0; i < nOptionTenors_; ++i) {
            optionTimes_[i] = optionInterpolatorTimes_[i + 1] =
                    timeFromReference(optionDates_.get(i));
        }
    }

    /**
     * Recompute swapLengths_ via {@link SwaptionVolatilityStructure#swapLength(Period)}.
     */
    private void initializeSwapLengths() {
        for (int i = 0; i < nSwapTenors_; ++i) {
            swapLengths_[i] = swapLength(swapTenors_.get(i));
        }
    }

    /**
     * Re-create the option-time → option-date-as-real linear interpolator
     * (used by {@link #optionDateFromTime(double)}).
     */
    private void rebuildOptionInterpolator() {
        final Array x = new Array(optionInterpolatorTimes_);
        final Array y = new Array(optionInterpolatorDatesAsReal_);
        optionInterpolator_ = new LinearInterpolation(x, y);
        optionInterpolator_.update();
        optionInterpolator_.enableExtrapolation();
    }
}
