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
 Copyright (C) 2008 Ferdinando Ametrano
 Copyright (C) 2006, 2007 StatPro Italia srl
 Copyright (C) 2015 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */
package org.jquantlib.termstructures.volatilities.swaption;

import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.SwaptionVolatilityStructure;
import org.jquantlib.termstructures.volatilities.SmileSection;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;

/**
 * Constant swaption volatility, no time-strike dependence.
 * <p>
 * Port of C++ QuantLib v1.42.1
 * {@code ql/termstructures/volatility/swaption/swaptionconstantvol.hpp}.
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 * <li>The C++ {@code volatilityType} / {@code shift} machinery (used for
 *     shifted-lognormal and Bachelier vol surfaces) is not yet ported on the
 *     Java {@link SwaptionVolatilityStructure} base class. This class
 *     therefore models only the (shifted-lognormal, shift = 0) Black76 case
 *     used by {@code BlackSwaptionEngine}. Bachelier and non-zero shift are
 *     deferred to a later phase.
 * <li>{@code smileSectionImpl} returns {@code null} — the only consumer of
 *     this constant-vol surface in this commit is {@code BlackSwaptionEngine},
 *     which uses {@link #blackVariance(double, double, double, boolean)} and
 *     never asks for a SmileSection. A future caller that needs smile slices
 *     will have to extend this stub.
 * </ul>
 *
 * @see org.jquantlib.pricingengines.swaption.BlackSwaptionEngine
 */
public class ConstantSwaptionVolatility extends SwaptionVolatilityStructure {

    private final Handle<? extends Quote> volatility_;
    private final Period maxSwapTenor_;
    private final VolatilityType volatilityType_;
    private final double shift_;

    //
    // public constructors
    //

    /** Floating reference date, floating market data. Defaults to ShiftedLognormal/0. */
    public ConstantSwaptionVolatility(final int settlementDays,
                                      final Calendar cal,
                                      final BusinessDayConvention bdc,
                                      final Handle<? extends Quote> vol,
                                      final DayCounter dc) {
        this(settlementDays, cal, bdc, vol, dc, VolatilityType.ShiftedLognormal, 0.0);
    }

    /** Floating reference date, floating market data, explicit type / shift. */
    public ConstantSwaptionVolatility(final int settlementDays,
                                      final Calendar cal,
                                      final BusinessDayConvention bdc,
                                      final Handle<? extends Quote> vol,
                                      final DayCounter dc,
                                      final VolatilityType type,
                                      final double shift) {
        super(settlementDays, cal, dc, bdc);
        this.volatility_ = vol;
        this.maxSwapTenor_ = new Period(100, TimeUnit.Years);
        this.volatilityType_ = type;
        this.shift_ = shift;
        this.volatility_.addObserver(this);
    }

    /** Fixed reference date, floating market data. Defaults to ShiftedLognormal/0. */
    public ConstantSwaptionVolatility(final Date referenceDate,
                                      final Calendar cal,
                                      final BusinessDayConvention bdc,
                                      final Handle<? extends Quote> vol,
                                      final DayCounter dc) {
        this(referenceDate, cal, bdc, vol, dc, VolatilityType.ShiftedLognormal, 0.0);
    }

    /** Fixed reference date, floating market data, explicit type / shift. */
    public ConstantSwaptionVolatility(final Date referenceDate,
                                      final Calendar cal,
                                      final BusinessDayConvention bdc,
                                      final Handle<? extends Quote> vol,
                                      final DayCounter dc,
                                      final VolatilityType type,
                                      final double shift) {
        super(referenceDate, cal, dc, bdc);
        this.volatility_ = vol;
        this.maxSwapTenor_ = new Period(100, TimeUnit.Years);
        this.volatilityType_ = type;
        this.shift_ = shift;
        this.volatility_.addObserver(this);
    }

    /** Floating reference date, fixed market data. */
    public ConstantSwaptionVolatility(final int settlementDays,
                                      final Calendar cal,
                                      final BusinessDayConvention bdc,
                                      final double vol,
                                      final DayCounter dc) {
        this(settlementDays, cal, bdc, new Handle<Quote>(new SimpleQuote(vol)), dc);
    }

    /** Fixed reference date, fixed market data. */
    public ConstantSwaptionVolatility(final Date referenceDate,
                                      final Calendar cal,
                                      final BusinessDayConvention bdc,
                                      final double vol,
                                      final DayCounter dc) {
        this(referenceDate, cal, bdc, new Handle<Quote>(new SimpleQuote(vol)), dc);
    }

    //
    // implements SwaptionVolatilityStructure
    //

    @Override
    public double volatilityImpl(final double optionTime, final double swapLength, final double strike) {
        return volatility_.currentLink().value();
    }

    @Override
    public double blackVariance(final double optionTime, final double swapLength,
            final double strike, final boolean extrapolate) {
        // Mirrors base class: variance = vol^2 * optionTime, but we override
        // because base class' override calls volatilityImpl after
        // checkRange — this avoids the redundant checkRange when callers
        // (e.g. BlackSwaptionEngine via the Date overload) already validated.
        final double v = volatility_.currentLink().value();
        return v * v * optionTime;
    }

    @Override
    public Period maxSwapTenor() {
        return maxSwapTenor_;
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
    public BusinessDayConvention businessDayConvention() {
        // SwaptionVolatilityStructure stores bdc privately; expose it via the
        // VolatilityTermStructure-style accessor used by the C++ base.
        // Here ConstantSwaptionVolatility's only direct consumer is
        // BlackSwaptionEngine, which never calls this method on the Java side.
        // Returning ModifiedFollowing matches QuantLib's typical default for
        // swaption surfaces. If a caller starts depending on this value,
        // wire it up properly through the parent constructor.
        return BusinessDayConvention.ModifiedFollowing;
    }

    @Override
    protected SmileSection smileSectionImpl(final double optionTime, final double swapLength) {
        // Black76-only port: BlackSwaptionEngine reads variance/vol directly,
        // never asks for a SmileSection. Deferring port of FlatSmileSection
        // until a smile-aware consumer arrives.
        return null;
    }

    @Override
    protected SmileSection smileSectionImpl(final Date optionDate, final Period swapTenor) {
        return null;
    }

    @Override
    public Date maxDate() {
        return Date.maxDate();
    }

    @Override
    public VolatilityType volatilityType() {
        return volatilityType_;
    }

    @Override
    public double shift() {
        return shift_;
    }
}
