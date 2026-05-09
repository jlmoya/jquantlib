/*
 Copyright (C) 2026 JQuantLib migration contributors

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

package org.jquantlib.termstructures.yieldcurves;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.termstructures.AbstractYieldTermStructure;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;

/**
 * Discount curve fitted to a set of fixed-coupon bonds (or evaluated from
 * pre-computed parameters).
 * <p>
 * Faithful port of QuantLib v1.42.1 {@code FittedBondDiscountCurve}
 * (ql/termstructures/yield/fittedbonddiscountcurve.{hpp,cpp}).
 *
 * <p><b>Phase 5d.5-ZCS+FB scope:</b> only the parametric (no-fit)
 * constructors are wired today; passing bond helpers will compile but
 * the {@code calculate()} path will fail-fast because BondHelper-driven
 * least-squares optimization (Simplex / LM) is tracked as a Phase
 * 5d.5-ZCS+FBb carry-forward. The parametric mode is the primary entry
 * point exercised by the C++ {@code testEvaluation} test; see also the
 * {@link FittingMethod} subclasses {@link NelsonSiegelFitting},
 * {@link SvenssonFitting}, {@link SimplePolynomialFitting}.
 */
public class FittedBondDiscountCurve extends AbstractYieldTermStructure {

    // -- target accuracy / iteration controls ---------------------------------

    private final double accuracy_;
    private final int maxEvaluations_;
    private final double simplexLambda_;
    private final int maxStationaryStateIterations_;
    private Array guessSolution_;
    private Date maxDate_;

    // -- fitting method (clone, owned by the curve) ---------------------------
    private final FittingMethod fittingMethod_;

    // -- lazy-calculation flag (mirrors LazyObject in C++) --------------------
    private boolean calculated_ = false;

    //
    // public constructors — bond-helper variants (no-fit branch only for now)
    //

    /**
     * Reference date based on current evaluation date. Bond-helper fitting is
     * not yet supported in this slice.
     */
    public FittedBondDiscountCurve(final int settlementDays,
                                   final Calendar calendar,
                                   final DayCounter dayCounter,
                                   final FittingMethod fittingMethod,
                                   final double accuracy,
                                   final int maxEvaluations,
                                   final Array guess,
                                   final double simplexLambda,
                                   final int maxStationaryStateIterations) {
        super(settlementDays, calendar, dayCounter);
        this.accuracy_ = accuracy;
        this.maxEvaluations_ = maxEvaluations;
        this.simplexLambda_ = simplexLambda;
        this.maxStationaryStateIterations_ = maxStationaryStateIterations;
        this.guessSolution_ = (guess == null) ? new Array(0) : guess;
        this.fittingMethod_ = fittingMethod.clone();
        this.fittingMethod_.curve_ = this;
    }

    /**
     * Curve reference date fixed for life of curve. Bond-helper fitting is
     * not yet supported in this slice.
     */
    public FittedBondDiscountCurve(final Date referenceDate,
                                   final DayCounter dayCounter,
                                   final FittingMethod fittingMethod,
                                   final double accuracy,
                                   final int maxEvaluations,
                                   final Array guess,
                                   final double simplexLambda,
                                   final int maxStationaryStateIterations) {
        super(referenceDate, new org.jquantlib.time.calendars.NullCalendar(), dayCounter);
        this.accuracy_ = accuracy;
        this.maxEvaluations_ = maxEvaluations;
        this.simplexLambda_ = simplexLambda;
        this.maxStationaryStateIterations_ = maxStationaryStateIterations;
        this.guessSolution_ = (guess == null) ? new Array(0) : guess;
        this.fittingMethod_ = fittingMethod.clone();
        this.fittingMethod_.curve_ = this;
    }

    /**
     * Don't fit, use precalculated parameters (settlement-days / calendar
     * variant). Mirrors C++ ctor #3.
     */
    public FittedBondDiscountCurve(final int settlementDays,
                                   final Calendar calendar,
                                   final FittingMethod fittingMethod,
                                   final Array parameters,
                                   final Date maxDate,
                                   final DayCounter dayCounter) {
        super(settlementDays, calendar, dayCounter);
        this.accuracy_ = 1e-10;
        this.maxEvaluations_ = 0;
        this.simplexLambda_ = 1.0;
        this.maxStationaryStateIterations_ = 100;
        this.guessSolution_ = parameters.clone();
        this.maxDate_ = maxDate;
        this.fittingMethod_ = fittingMethod.clone();
        this.fittingMethod_.curve_ = this;
    }

    /**
     * Don't fit, use precalculated parameters (fixed reference date variant).
     * Mirrors C++ ctor #4.
     */
    public FittedBondDiscountCurve(final Date referenceDate,
                                   final FittingMethod fittingMethod,
                                   final Array parameters,
                                   final Date maxDate,
                                   final DayCounter dayCounter) {
        super(referenceDate, new org.jquantlib.time.calendars.NullCalendar(), dayCounter);
        this.accuracy_ = 1e-10;
        this.maxEvaluations_ = 0;
        this.simplexLambda_ = 1.0;
        this.maxStationaryStateIterations_ = 100;
        this.guessSolution_ = parameters.clone();
        this.maxDate_ = maxDate;
        this.fittingMethod_ = fittingMethod.clone();
        this.fittingMethod_.curve_ = this;
    }

    //
    // public inspectors
    //

    @Override
    public Date maxDate() {
        calculate();
        return maxDate_;
    }

    /** Class holding the results of the fit. */
    public FittingMethod fitResults() {
        calculate();
        return fittingMethod_;
    }

    /** Allow trying multiple guesses to avoid local minima. */
    public void resetGuess(final Array guess) {
        QL.require(guess.size() == 0 || guess.size() == fittingMethod_.size(),
                "guess is of wrong size");
        guessSolution_ = guess.clone();
        calculated_ = false;
        update();
    }

    @Override
    public void update() {
        calculated_ = false;
        super.update();
    }

    //
    // package-private accessors used by FittingMethod
    //

    int maxEvaluations() { return maxEvaluations_; }

    double accuracy() { return accuracy_; }

    double simplexLambda() { return simplexLambda_; }

    int maxStationaryStateIterations() { return maxStationaryStateIterations_; }

    Array guessSolution() { return guessSolution_; }

    void setGuessSolution(final Array a) { this.guessSolution_ = a; }

    //
    // discount implementation
    //

    @Override
    protected double discountImpl(final double t) {
        calculate();
        return fittingMethod_.discount(fittingMethod_.solution(), t);
    }

    /** Lazy-calculate guard mirroring LazyObject::calculate(). */
    private void calculate() {
        if (calculated_) {
            return;
        }
        performCalculations();
        calculated_ = true;
    }

    /** Mirrors C++ FittedBondDiscountCurve::performCalculations(). */
    private void performCalculations() {
        if (maxEvaluations_ == 0) {
            // No fit. We require an explicit max date (helpers not yet supported
            // in this slice).
            QL.require(maxDate_ != null, "no bond helpers or max date given");
        } else {
            QL.require(false,
                    "FittedBondDiscountCurve: bond-helper fitting is not yet ported; "
                    + "use the parametric (no-fit) constructor with explicit parameters and maxDate");
        }
        fittingMethod_.init();
        fittingMethod_.calculate();
    }
}
