/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/
 */
/*
 Copyright (C) 2008 Ferdinando Ametrano
 Copyright (C) 2007 Giorgio Facchinetti
 Copyright (C) 2015 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */
package org.jquantlib.termstructures.volatilities.optionlet;

import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.volatilities.SmileSection;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;

/**
 * Spreaded caplet/floorlet volatility — adds a constant
 * additive spread to a base optionlet vol surface.
 *
 * <p>Port of C++ QuantLib v1.42.1
 * {@code ql/termstructures/volatility/optionlet/spreadedoptionletvol.{hpp,cpp}}.
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 *  <li>{@code smileSectionImpl} returns {@code null} pending the port of
 *      {@code SpreadedSmileSection} (Phase 5g.5b carry-forward). Downstream
 *      callers using {@code volatility(t, strike)} (e.g.,
 *      {@link OptionletStripper2}) are fully supported.</li>
 *  <li>Java cannot multi-inherit; we extend {@link OptionletVolatilityStructure}
 *      and forward all inspector methods to {@code baseVol_} so that
 *      {@code referenceDate}, {@code maxDate}, {@code calendar}, etc., reflect
 *      the underlying base.</li>
 * </ul>
 */
public class SpreadedOptionletVolatility extends OptionletVolatilityStructure {

    //
    // private fields
    //

    private final Handle<OptionletVolatilityStructure> baseVol_;
    private final Handle<Quote> spread_;

    //
    // public constructor
    //

    public SpreadedOptionletVolatility(final Handle<OptionletVolatilityStructure> baseVol,
                                       final Handle<Quote> spread) {
        // We pass the base's settings via super(), then forward inspectors below.
        super(baseVol.currentLink().settlementDays(),
                baseVol.currentLink().calendar(),
                baseVol.currentLink().businessDayConvention(),
                baseVol.currentLink().dayCounter());
        this.baseVol_ = baseVol;
        this.spread_ = spread;
        // Mirrors C++ enableExtrapolation(baseVol->allowsExtrapolation()).
        if (baseVol.currentLink().allowsExtrapolation()) {
            enableExtrapolation();
        }
        baseVol.addObserver(this);
        spread.addObserver(this);
    }

    //
    // OptionletVolatilityStructure interface — forwarders
    //

    @Override
    public BusinessDayConvention businessDayConvention() {
        return baseVol_.currentLink().businessDayConvention();
    }

    @Override
    public double minStrike() {
        return baseVol_.currentLink().minStrike();
    }

    @Override
    public double maxStrike() {
        return baseVol_.currentLink().maxStrike();
    }

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

    @Override
    public int settlementDays() {
        return baseVol_.currentLink().settlementDays();
    }

    @Override
    public VolatilityType volatilityType() {
        return baseVol_.currentLink().volatilityType();
    }

    @Override
    public double displacement() {
        return baseVol_.currentLink().displacement();
    }

    //
    // protected — implementation
    //

    /**
     * Pending Phase 5g.5b: returns {@code null} until {@code SpreadedSmileSection}
     * is ported. The {@code volatilityImpl} path is fully implemented and
     * sufficient for {@link OptionletStripper2} bootstrap.
     */
    @Override
    protected SmileSection smileSectionImpl(final double optionTime) {
        return null;
    }

    @Override
    protected double volatilityImpl(final double t, final double strike) {
        return baseVol_.currentLink().volatility(t, strike, true)
                + spread_.currentLink().value();
    }
}
