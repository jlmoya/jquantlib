/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2020, 2025 Marcin Rybacki

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.
 */

package org.jquantlib.termstructures.yieldcurves;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.Rounding;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.InterestRate;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Period;

/**
 * Ultimate Forward Rate (UFR) term structure.
 * <p>
 * Dutch regulatory term structure for pension funds with a parametrized
 * extrapolation mechanism designed for discounting long dated liabilities.
 * <p>
 * Optionally, computed zero rates may be rounded. The specified number of
 * decimal places will affect the rate in decimal format; for example,
 * rounding a rate of 1.5555% to 5 decimal places results in 0.015555
 * becoming 0.01556, or 1.556%.
 * <p>
 * This term structure remains linked to the original structure, i.e., any
 * changes in the latter are reflected in this structure as well.
 * <p>
 * Mirrors C++ QuantLib v1.42.1
 * {@code ql/termstructures/yield/ultimateforwardtermstructure.hpp}.
 *
 * @category yieldtermstructures
 */
public class UltimateForwardTermStructure extends ZeroYieldStructure {

    //
    // private fields
    //

    private final Handle<YieldTermStructure> originalCurve;
    private final Handle<Quote> llfr;
    private final Handle<Quote> ufr;
    private final Period fsp;
    private final double alpha;
    private final Integer roundingDigits; // null == no rounding (mirrors C++ ext::optional)
    private final Compounding compounding;
    private final Frequency frequency;


    //
    // public constructors
    //

    /**
     * Full-arity constructor mirroring C++ v1.42.1.
     *
     * @param h                    underlying liquid yield curve.
     * @param lastLiquidForwardRate LLFR quote at the cut-off point.
     * @param ultimateForwardRate  UFR quote (continuously-compounded long-end forward).
     * @param firstSmoothingPoint  cut-off point (must be a positive Period).
     * @param alpha                growth-factor of the smoothing weight.
     * @param roundingDigits       optional rounding precision (may be {@code null}).
     * @param compounding          compounding convention used for the rounding step.
     * @param frequency            frequency used for the rounding step.
     */
    public UltimateForwardTermStructure(
            final Handle<YieldTermStructure> h,
            final Handle<Quote> lastLiquidForwardRate,
            final Handle<Quote> ultimateForwardRate,
            final Period firstSmoothingPoint,
            final double alpha,
            final Integer roundingDigits,
            final Compounding compounding,
            final Frequency frequency) {
        super();
        QL.require(firstSmoothingPoint != null && firstSmoothingPoint.length() > 0,
                "first smoothing point must be a period with positive length");

        this.originalCurve = h;
        this.llfr = lastLiquidForwardRate;
        this.ufr = ultimateForwardRate;
        this.fsp = firstSmoothingPoint;
        this.alpha = alpha;
        this.roundingDigits = roundingDigits;
        this.compounding = compounding;
        this.frequency = frequency;

        if (this.originalCurve != null && !this.originalCurve.empty()) {
            if (this.originalCurve.currentLink().allowsExtrapolation()) {
                enableExtrapolation();
            }
        }
        if (this.originalCurve != null) {
            this.originalCurve.addObserver(this);
        }
        if (this.llfr != null) {
            this.llfr.addObserver(this);
        }
        if (this.ufr != null) {
            this.ufr.addObserver(this);
        }
    }

    /** C++ default-argument overload — no rounding, Compounded/Annual rounding-config. */
    public UltimateForwardTermStructure(
            final Handle<YieldTermStructure> h,
            final Handle<Quote> lastLiquidForwardRate,
            final Handle<Quote> ultimateForwardRate,
            final Period firstSmoothingPoint,
            final double alpha) {
        this(h, lastLiquidForwardRate, ultimateForwardRate, firstSmoothingPoint, alpha,
                null, Compounding.Compounded, Frequency.Annual);
    }

    /** C++ default-argument overload — rounding digits + Compounded/Annual. */
    public UltimateForwardTermStructure(
            final Handle<YieldTermStructure> h,
            final Handle<Quote> lastLiquidForwardRate,
            final Handle<Quote> ultimateForwardRate,
            final Period firstSmoothingPoint,
            final double alpha,
            final Integer roundingDigits) {
        this(h, lastLiquidForwardRate, ultimateForwardRate, firstSmoothingPoint, alpha,
                roundingDigits, Compounding.Compounded, Frequency.Annual);
    }


    //
    // YieldTermStructure interface overrides — delegate to base curve
    //

    @Override
    public DayCounter dayCounter() {
        return originalCurve.currentLink().dayCounter();
    }

    @Override
    public Calendar calendar() {
        return originalCurve.currentLink().calendar();
    }

    @Override
    public int settlementDays() {
        return originalCurve.currentLink().settlementDays();
    }

    @Override
    public Date referenceDate() {
        return originalCurve.currentLink().referenceDate();
    }

    @Override
    public Date maxDate() {
        return Date.maxDate();
    }


    //
    // Observer interface
    //

    @Override
    public void update() {
        if (originalCurve != null && !originalCurve.empty()) {
            super.update();
            if (originalCurve.currentLink().allowsExtrapolation()) {
                enableExtrapolation();
            } else {
                disableExtrapolation();
            }
        } else {
            // Original curve not set yet — mirror the C++ TermStructure::update()
            // shortcut: just notify observers without touching referenceDate().
            super.update();
        }
    }


    //
    // overrides ZeroYieldStructure
    //

    @Override
    protected double zeroYieldImpl(final double t) {
        final double cutOffTime = originalCurve.currentLink()
                .timeFromReference(referenceDate().add(fsp));
        final double deltaT = t - cutOffTime;
        if (deltaT > 0.0) {
            final InterestRate baseRate = originalCurve.currentLink()
                    .zeroRate(cutOffTime, Compounding.Continuous, Frequency.NoFrequency, true);
            final double beta = (1.0 - Math.exp(-alpha * deltaT)) / (alpha * deltaT);
            final double extrapolatedForward =
                    ufr.currentLink().value()
                    + (llfr.currentLink().value() - ufr.currentLink().value()) * beta;
            return applyRounding(
                    (cutOffTime * baseRate.rate() + deltaT * extrapolatedForward) / t, t);
        }
        return applyRounding(
                originalCurve.currentLink()
                        .zeroRate(t, Compounding.Continuous, Frequency.NoFrequency, true).rate(),
                t);
    }


    //
    // private helpers
    //

    /**
     * Applies the C++ {@code applyRounding} step on a continuously-compounded
     * zero rate. When {@code roundingDigits} is {@code null} the rate is
     * returned unchanged.
     */
    private double applyRounding(final double r, final double t) {
        if (roundingDigits == null) {
            return r;
        }
        // Input rate is continuously compounded by definition. If the requested
        // rounding compounding matches, round directly; otherwise convert,
        // round, convert back.
        final double equivalentRate;
        if (compounding == Compounding.Continuous) {
            equivalentRate = r;
        } else {
            equivalentRate = new InterestRate(r, dayCounter(),
                    Compounding.Continuous, Frequency.NoFrequency)
                    .equivalentRate(t, compounding, frequency).rate();
        }
        final double rounded = new Rounding.ClosestRounding(
                roundingDigits.intValue()).operator(equivalentRate);
        if (compounding == Compounding.Continuous) {
            return rounded;
        }
        return new InterestRate(rounded, dayCounter(), compounding, frequency)
                .equivalentRate(t, Compounding.Continuous, Frequency.NoFrequency).rate();
    }
}
