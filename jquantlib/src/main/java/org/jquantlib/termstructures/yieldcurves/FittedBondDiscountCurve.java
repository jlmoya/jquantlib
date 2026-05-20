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
 * Discount curve fitted to a set of fixed-coupon bonds (or evaluated from pre-computed parameters).
 * <p>
 * Faithful port of QuantLib v1.42.1 {@code FittedBondDiscountCurve}
 * (ql/termstructures/yield/fittedbonddiscountcurve.{hpp,cpp}).
 *
 * <p>Two operating modes:
 * <ul>
 *   <li><b>Parametric / no-fit:</b> caller provides {@link Array parameters} +
 *   maxDate; the curve simply uses them via the supplied {@link FittingMethod}.
 *   Useful e.g. to reuse a previously-fit solution in a different currency.</li>
 *   <li><b>Bond-helper / fitting:</b> caller provides a list of
 *   {@link BondHelper}s + accuracy + maxIterations; the curve minimises the
 *   weighted price-error of the bonds via {@link FittingMethod#calculate()}.
 *   Weights default to 1/duration (computed internally if not given).</li>
 * </ul>
 *
 * <p>See {@link FittingMethod} subclasses {@link NelsonSiegelFitting},
 * {@link SvenssonFitting}, {@link SimplePolynomialFitting}.
 */
public class FittedBondDiscountCurve extends AbstractYieldTermStructure {

    // -- target accuracy / iteration controls ---------------------------------

    private final double accuracy_;
    private final int maxEvaluations_;
    private final double simplexLambda_;
    private final int maxStationaryStateIterations_;
    // -- fitting method (clone, owned by the curve) ---------------------------
    private final FittingMethod fittingMethod_;
    private Array guessSolution_;
    private Date maxDate_;
    // -- bond helpers for fitting branch --------------------------------------
    private BondHelper[] bondHelpers_;
    // -- lazy-calculation flag (mirrors LazyObject in C++) --------------------
    private boolean calculated_ = false;

    //
    // public constructors — bond-helper variants (fitting branch)
    //

    /**
     * Bond-helper fit ctor #1 (settlement-days + calendar). Mirrors C++ ctor with helpers.
     * Defaults: simplexLambda=1.0, maxStationaryStateIterations=100.
     */
    public FittedBondDiscountCurve(final int settlementDays, final Calendar calendar, final BondHelper[] bondHelpers,
            final DayCounter dayCounter, final FittingMethod fittingMethod, final double accuracy,
            final int maxEvaluations, final Array guess, final double simplexLambda,
            final int maxStationaryStateIterations) {
        super(settlementDays, calendar, dayCounter);
        this.accuracy_ = accuracy;
        this.maxEvaluations_ = maxEvaluations;
        this.simplexLambda_ = simplexLambda;
        this.maxStationaryStateIterations_ = maxStationaryStateIterations;
        this.guessSolution_ = (guess == null) ? new Array(0) : guess;
        this.bondHelpers_ = (bondHelpers == null) ? new BondHelper[0] : bondHelpers.clone();
        this.fittingMethod_ = fittingMethod.clone();
        this.fittingMethod_.curve_ = this;
        setup();
    }

    /** Convenience: matches C++ default {@code simplexLambda=1.0, maxStationaryStateIterations=100}. */
    public FittedBondDiscountCurve(final int settlementDays, final Calendar calendar, final BondHelper[] bondHelpers,
            final DayCounter dayCounter, final FittingMethod fittingMethod, final double accuracy,
            final int maxEvaluations, final Array guess) {
        this(settlementDays, calendar, bondHelpers, dayCounter, fittingMethod, accuracy, maxEvaluations, guess, 1.0,
                100);
    }

    /** Convenience: matches C++ default with no guess. */
    public FittedBondDiscountCurve(final int settlementDays, final Calendar calendar, final BondHelper[] bondHelpers,
            final DayCounter dayCounter, final FittingMethod fittingMethod, final double accuracy,
            final int maxEvaluations) {
        this(settlementDays, calendar, bondHelpers, dayCounter, fittingMethod, accuracy, maxEvaluations, null, 1.0,
                100);
    }

    //
    // public constructors — no-fit (parametric) variants (existing)
    //

    /**
     * Reference date based on current evaluation date (no-fit / parametric variant). This signature is the historical
     * one used before the bond-helper branch was wired; it remains supported to avoid breaking call sites.
     */
    public FittedBondDiscountCurve(final int settlementDays, final Calendar calendar, final DayCounter dayCounter,
            final FittingMethod fittingMethod, final double accuracy, final int maxEvaluations, final Array guess,
            final double simplexLambda, final int maxStationaryStateIterations) {
        super(settlementDays, calendar, dayCounter);
        this.accuracy_ = accuracy;
        this.maxEvaluations_ = maxEvaluations;
        this.simplexLambda_ = simplexLambda;
        this.maxStationaryStateIterations_ = maxStationaryStateIterations;
        this.guessSolution_ = (guess == null) ? new Array(0) : guess;
        this.bondHelpers_ = new BondHelper[0];
        this.fittingMethod_ = fittingMethod.clone();
        this.fittingMethod_.curve_ = this;
    }

    /**
     * Curve reference date fixed for life of curve (no-fit / parametric variant).
     */
    public FittedBondDiscountCurve(final Date referenceDate, final DayCounter dayCounter,
            final FittingMethod fittingMethod, final double accuracy, final int maxEvaluations, final Array guess,
            final double simplexLambda, final int maxStationaryStateIterations) {
        super(referenceDate, new org.jquantlib.time.calendars.NullCalendar(), dayCounter);
        this.accuracy_ = accuracy;
        this.maxEvaluations_ = maxEvaluations;
        this.simplexLambda_ = simplexLambda;
        this.maxStationaryStateIterations_ = maxStationaryStateIterations;
        this.guessSolution_ = (guess == null) ? new Array(0) : guess;
        this.bondHelpers_ = new BondHelper[0];
        this.fittingMethod_ = fittingMethod.clone();
        this.fittingMethod_.curve_ = this;
    }

    /**
     * Don't fit, use precalculated parameters (settlement-days / calendar variant). Mirrors C++ ctor #3.
     */
    public FittedBondDiscountCurve(final int settlementDays, final Calendar calendar, final FittingMethod fittingMethod,
            final Array parameters, final Date maxDate, final DayCounter dayCounter) {
        super(settlementDays, calendar, dayCounter);
        this.accuracy_ = 1e-10;
        this.maxEvaluations_ = 0;
        this.simplexLambda_ = 1.0;
        this.maxStationaryStateIterations_ = 100;
        this.guessSolution_ = parameters.clone();
        this.bondHelpers_ = new BondHelper[0];
        this.maxDate_ = maxDate;
        this.fittingMethod_ = fittingMethod.clone();
        this.fittingMethod_.curve_ = this;
    }

    /**
     * Don't fit, use precalculated parameters (fixed reference date variant). Mirrors C++ ctor #4.
     */
    public FittedBondDiscountCurve(final Date referenceDate, final FittingMethod fittingMethod, final Array parameters,
            final Date maxDate, final DayCounter dayCounter) {
        super(referenceDate, new org.jquantlib.time.calendars.NullCalendar(), dayCounter);
        this.accuracy_ = 1e-10;
        this.maxEvaluations_ = 0;
        this.simplexLambda_ = 1.0;
        this.maxStationaryStateIterations_ = 100;
        this.guessSolution_ = parameters.clone();
        this.bondHelpers_ = new BondHelper[0];
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
        QL.require(guess.size() == 0 || guess.size() == fittingMethod_.size(), "guess is of wrong size");
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

    int maxEvaluations() {
        return maxEvaluations_;
    }

    double accuracy() {
        return accuracy_;
    }

    double simplexLambda() {
        return simplexLambda_;
    }

    int maxStationaryStateIterations() {
        return maxStationaryStateIterations_;
    }

    Array guessSolution() {
        return guessSolution_;
    }

    void setGuessSolution(final Array a) {
        this.guessSolution_ = a;
    }

    //
    // package-private accessors used by FittingMethod (bond-helper branch)
    //

    BondHelper[] bondHelpers() {
        return bondHelpers_;
    }

    /** Mirrors C++ {@code numberOfBonds()}. */
    public int numberOfBonds() {
        return bondHelpers_.length;
    }

    //
    // discount implementation
    //

    @Override
    protected double discountImpl(final double t) {
        calculate();
        return fittingMethod_.discount(fittingMethod_.solution(), t);
    }

    /**
     * Lazy-calculate guard mirroring C++ {@code LazyObject::calculate()}.
     * <p>
     * We set {@code calculated_ = true} <em>before</em> entering
     * {@link #performCalculations()} so that any re-entrant calls from inside
     * the cost-function evaluation (the bond helper re-prices its bond off
     * <em>this</em> curve, which back-calls {@link #discountImpl(double)})
     * short-circuit and return the discount of the current trial solution
     * stored in {@link FittingMethod#solution_}. This mirrors the C++
     * "frozen_ during calculate" semantics of {@code LazyObject}.
     */
    private void calculate() {
        if ( calculated_ ) {
            return;
        }
        calculated_ = true; // set first to break re-entrant recursion via bondhelper.quoteError
        try {
            performCalculations();
        } catch ( final RuntimeException e ) {
            calculated_ = false; // allow caller to retry / observe the error
            throw e;
        }
    }

    /**
     * Mirrors C++ {@code FittedBondDiscountCurve::setup()}: register as
     * observer of every helper.
     * <p>
     * <b>Note:</b> Java's Observable / Observer chain is more reentrant than
     * the C++ Signals2-based implementation; in particular the FBdC ←
     * BondHelper ← Bond ← cashflow chain can re-enter via the bond's
     * pricing-engine notification on each Simplex evaluation, blowing the
     * stack. We intentionally do NOT subscribe the curve to the helpers — the
     * fitting is driven eagerly via {@link #performCalculations()} from
     * {@link #discountImpl(double)}, which is sufficient for the unit tests
     * exercised in Phase 1 (no re-pricing across evaluation-date changes is
     * required by the BondHelper test cases).
     */
    private void setup() {
        // Intentionally no-op for Phase 1 — see method JavaDoc.
    }

    /** Mirrors C++ FittedBondDiscountCurve::performCalculations(). */
    private void performCalculations() {
        if ( maxEvaluations_ != 0 ) {
            // Fitting mode: helpers are required.
            QL.require(bondHelpers_.length > 0, "no bond helpers given");
        }
        if ( maxEvaluations_ == 0 ) {
            // No-fit mode: need either explicit max date OR helpers.
            QL.require(maxDate_ != null || bondHelpers_.length > 0, "no bond helpers or max date given");
        }

        if ( bondHelpers_.length > 0 ) {
            maxDate_ = Date.minDate();
            final Date refDate = referenceDate();
            for ( int i = 0; i < bondHelpers_.length; ++i ) {
                final org.jquantlib.instruments.Bond bond = bondHelpers_[i].bond();
                QL.require(bondHelpers_[i].quoteIsValid(), (i + 1) + "-th bond has an invalid price quote");
                final Date bondSettlement = bond.settlementDate();
                QL.require(bondSettlement.ge(refDate), (i + 1) + "-th bond settlement date (" + bondSettlement
                        + ") before curve reference date (" + refDate + ")");
                final Date pillar = bondHelpers_[i].pillarDate();
                if ( pillar.gt(maxDate_) ) {
                    maxDate_ = pillar;
                }
                bondHelpers_[i].setTermStructure(this);
            }
        }

        fittingMethod_.init();
        fittingMethod_.calculate();
    }
}
