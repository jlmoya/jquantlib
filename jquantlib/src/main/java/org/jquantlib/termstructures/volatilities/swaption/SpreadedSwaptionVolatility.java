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
 Copyright (C) 2007 Giorgio Facchinetti
 Copyright (C) 2015 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */
package org.jquantlib.termstructures.volatilities.swaption;

import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.SwaptionVolatilityStructure;
import org.jquantlib.termstructures.volatilities.SmileSection;
import org.jquantlib.termstructures.volatilities.SpreadedSmileSection;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;

/**
 * Spread overlay on a base {@link SwaptionVolatilityStructure}: adds a constant additive volatility spread (via a
 * {@link Quote}) to every vol/smile returned by the base.
 *
 * <p>Port of C++ QuantLib v1.42.1
 * {@code ql/termstructures/volatility/swaption/spreadedswaptionvol.{hpp,cpp}}.
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 *  <li>Every {@code TermStructure}/{@code VolatilityTermStructure} accessor
 *      forwards to {@code baseVol_}, exactly matching the C++ inline
 *      forwarders.</li>
 *  <li>{@code shiftImpl} delegates to {@code baseVol_->shift(t, l, true)}
 *      to allow extrapolation, mirroring the C++ inline (header line 113).
 *      Java's {@code SwaptionVolatilityStructure} does not expose a
 *      {@code shift(time, length, bool)} overload directly; we route via the
 *      no-arg {@code shift()} which returns the base lognormal shift
 *      (zero unless the base is a shifted-lognormal matrix).</li>
 * </ul>
 */
public class SpreadedSwaptionVolatility extends SwaptionVolatilityStructure {

    private final Handle< SwaptionVolatilityStructure > baseVol_;
    private final Handle< Quote > spread_;

    public SpreadedSwaptionVolatility(final Handle< SwaptionVolatilityStructure > baseVol,
            final Handle< Quote > spread) {
        super(baseVol.currentLink().dayCounter(), baseVol.currentLink().businessDayConvention());
        this.baseVol_ = baseVol;
        this.spread_ = spread;
        if ( baseVol_.currentLink().allowsExtrapolation() ) {
            enableExtrapolation();
        }
        baseVol_.addObserver(this);
        spread_.addObserver(this);
    }

    //
    // TermStructure interface
    //

    @Override
    public DayCounter dayCounter() {
        return baseVol_.currentLink().dayCounter();
    }

    @Override
    public Date maxDate() {
        return baseVol_.currentLink().maxDate();
    }

    @Override
    public double maxTime() {
        return baseVol_.currentLink().maxTime();
    }

    @Override
    public Date referenceDate() {
        return baseVol_.currentLink().referenceDate();
    }

    @Override
    public Calendar calendar() {
        return baseVol_.currentLink().calendar();
    }

    //
    // VolatilityTermStructure interface
    //

    @Override
    public double minStrike() {
        return baseVol_.currentLink().minStrike();
    }

    @Override
    public double maxStrike() {
        return baseVol_.currentLink().maxStrike();
    }

    //
    // SwaptionVolatilityStructure interface
    //

    @Override
    public Period maxSwapTenor() {
        return baseVol_.currentLink().maxSwapTenor();
    }

    @Override
    public VolatilityType volatilityType() {
        return baseVol_.currentLink().volatilityType();
    }

    @Override
    public BusinessDayConvention businessDayConvention() {
        return baseVol_.currentLink().businessDayConvention();
    }

    @Override
    public double shift() {
        return baseVol_.currentLink().shift();
    }

    //
    // smile section forwarders
    //

    @Override
    protected SmileSection smileSectionImpl(final Date optionDate, final Period swapTenor) {
        final SmileSection baseSmile = baseVol_.currentLink().smileSection(optionDate, swapTenor, true);
        return new SpreadedSmileSection(baseSmile, spread_);
    }

    @Override
    protected SmileSection smileSectionImpl(final double optionTime, final double swapLength) {
        // The base SwaptionVolatilityStructure exposes smileSection(Date,...)
        // but not directly smileSection(double, double, bool). Fall back via
        // optionDateFromTenor: we cannot easily invert (optionTime, swapLength)
        // to (Date, Period) without the discrete-grid optionInterpolator_.
        // Reuse the optionTime→strike→volatility path: build an ad-hoc smile
        // wrapping baseVol_.volatility(t, l, strike) + spread.
        return new SpreadedSmileSection(
                new BaseSmileWrapper(optionTime, swapLength, baseVol_.currentLink(), dayCounter(), volatilityType(),
                        shift()), spread_);
    }

    @Override
    public double volatilityImpl(final double optionTime, final double swapLength, final double strike) {
        return baseVol_.currentLink().volatility(optionTime, swapLength, strike, true) + spread_.currentLink().value();
    }

    @Override
    protected double volatilityImpl(final Date optionDate, final Period swapTenor, final double strike) {
        return baseVol_.currentLink().volatility(optionDate, swapTenor, strike, true) + spread_.currentLink().value();
    }

    @Override
    public double blackVariance(final double optionTime, final double swapLength, final double strike,
            final boolean extrapolate) {
        final double v =
                baseVol_.currentLink().volatility(optionTime, swapLength, strike, extrapolate) + spread_.currentLink()
                        .value();
        return v * v * optionTime;
    }

    /**
     * Helper smile-section that lazily evaluates the base SwaptionVolatilityStructure's
     * {@code volatility(time, length, strike)} for a fixed (optionTime, swapLength) coordinate. Used by the
     * {@code smileSectionImpl(double, double)} fallback when the base does not expose its own (time, length) smile
     * section.
     */
    private static final class BaseSmileWrapper extends SmileSection {
        private final double optionTime_;
        private final double swapLength_;
        private final SwaptionVolatilityStructure base_;

        BaseSmileWrapper(final double optionTime, final double swapLength, final SwaptionVolatilityStructure base,
                final DayCounter dc, final VolatilityType type, final double shift) {
            super(optionTime, dc, type, shift);
            this.optionTime_ = optionTime;
            this.swapLength_ = swapLength;
            this.base_ = base;
        }

        @Override
        public double minStrike() {
            return base_.minStrike();
        }

        @Override
        public double maxStrike() {
            return base_.maxStrike();
        }

        @Override
        public double atmLevel() {
            return Double.NaN;
        }

        @Override
        protected double volatilityImpl(final double strike) {
            return base_.volatility(optionTime_, swapLength_, strike, true);
        }
    }
}
