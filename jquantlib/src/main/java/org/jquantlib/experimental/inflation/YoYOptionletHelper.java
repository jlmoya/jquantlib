/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is released under the BSD License.

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
 Copyright (C) 2009 Chris Kenyon

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/
package org.jquantlib.experimental.inflation;

import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.YoYInflationCoupon;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.CPI;
import org.jquantlib.indexes.YoYInflationIndex;
import org.jquantlib.instruments.InflationCapFloor;
import org.jquantlib.instruments.MakeYoYInflationCapFloor;
import org.jquantlib.pricingengines.inflation.InflationCapFloorEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.BootstrapHelper;
import org.jquantlib.termstructures.volatility.inflation.YoYOptionletVolatilitySurface;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Period;

/**
 * Year-on-year inflation-volatility bootstrap helper.
 *
 * <p>Mirrors C++ v1.42.1 {@code QuantLib::YoYOptionletHelper}
 * ({@code ql/experimental/inflation/yoyoptionlethelpers.{hpp,cpp}}).
 *
 * <p>Bootstraps the volatility of a YoY-inflation optionlet by reproducing
 * the price of a quoted YoY cap or floor against a target market price.
 *
 * @author JQuantLib migration team (Phase 2s Track B)
 */
public class YoYOptionletHelper extends BootstrapHelper< YoYOptionletVolatilitySurface > {

    //
    // protected fields (mirror C++ verbatim)
    //

    protected double notional_;
    protected InflationCapFloor.Type capFloorType_;
    protected Period lag_;
    protected int fixingDays_;
    protected YoYInflationIndex index_;
    protected double strike_;
    protected int n_;  // how many payments
    protected DayCounter yoyDayCounter_;
    protected Calendar calendar_;
    protected InflationCapFloorEngine pricer_;
    /** Underlying instrument we re-price during bootstrap. */
    protected InflationCapFloor yoyCapFloor_;

    //
    // constructor
    //

    public YoYOptionletHelper(final Handle< Quote > price, final double notional,
            final InflationCapFloor.Type capFloorType, final Period lag, final DayCounter yoyDayCounter,
            final Calendar paymentCalendar, final int fixingDays, final YoYInflationIndex index,
            final CPI.InterpolationType interpolation, final double strike, final int n,
            final InflationCapFloorEngine pricer) {
        super(price);
        this.notional_ = notional;
        this.capFloorType_ = capFloorType;
        this.lag_ = lag;
        this.fixingDays_ = fixingDays;
        this.index_ = index;
        this.strike_ = strike;
        this.n_ = n;
        this.yoyDayCounter_ = yoyDayCounter;
        this.calendar_ = paymentCalendar;
        this.pricer_ = pricer;

        // Build the instrument to reprice (only need do this once)
        this.yoyCapFloor_ = new MakeYoYInflationCapFloor(capFloorType_, index_, n_, calendar_, lag_,
                interpolation).withNominal(notional_).withFixingDays(fixingDays_).withPaymentDayCounter(yoyDayCounter_)
                .withStrike(strike_).build();

        // dates already built in lag of index/instrument
        // these are the dates of the values of the index
        // that fix the capfloor
        final CashFlow firstCF = yoyCapFloor_.yoyLeg().get(0);
        final CashFlow lastCF = yoyCapFloor_.yoyLeg().get(yoyCapFloor_.yoyLeg().size() - 1);
        if ( firstCF instanceof YoYInflationCoupon ) {
            this.earliestDate = ((YoYInflationCoupon) firstCF).fixingDate();
        }
        if ( lastCF instanceof YoYInflationCoupon ) {
            this.latestDate = ((YoYInflationCoupon) lastCF).fixingDate();
        }

        // Each reprice resets the inflation surface in the pricer ...
        // so set the pricer.
        yoyCapFloor_.setPricingEngine(pricer_);
        // Vol (term structure = surface) is set later via setTermStructure().
    }

    //
    // BootstrapHelper interface
    //

    /**
     * Mirrors C++ {@code Real impliedQuote() const}: prices the underlying YoY cap/floor under the currently-installed
     * vol surface.
     */
    @Override
    public double impliedQuote() {
        // C++ calls deepUpdate(); Java's Instrument doesn't have deepUpdate
        // analog — we simply ensure NPV is recomputed by calling NPV()
        // (which invokes calculate()).
        return yoyCapFloor_.NPV();
    }

    /**
     * Mirrors C++ {@code void setTermStructure(YoYOptionletVolatilitySurface*)}. The new vol surface is a different one
     * each time, so we forward it to the pricer (the cap/floor itself only knows about the engine).
     */
    @Override
    public void setTermStructure(final YoYOptionletVolatilitySurface v) {
        super.setTermStructure(v);
        // Wrap the surface in a Handle and reset the vol on the pricer.
        // The C++ uses null_deleter (own=false) because the helper owns
        // the lifecycle; Java GC handles this naturally.
        final Handle< YoYOptionletVolatilitySurface > volSurf = new Handle<>(v);
        pricer_.setVolatility(volSurf);
    }
}
