/*
Copyright (C) 2026 JQuantLib migration

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
package org.jquantlib.model.shortrate.calibrationhelpers;

import org.jquantlib.cashflow.FixedRateLeg;
import org.jquantlib.cashflow.IborLeg;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.instruments.CapFloor;
import org.jquantlib.instruments.Swap;
import org.jquantlib.lang.annotation.Time;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.model.BlackCalibrationHelper;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.capfloor.BachelierCapFloorEngine;
import org.jquantlib.pricingengines.capfloor.BlackCapFloorEngine;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

/**
 * Calibration helper for ATM caps. Port of C++ v1.42.1 ql/models/shortrate/calibrationhelpers/caphelper.{hpp,cpp}.
 */
public class CapHelper extends BlackCalibrationHelper {

    private final Period length_;
    private final IborIndex index_;
    private final Handle< YieldTermStructure > termStructure_;
    private final Frequency fixedLegFrequency_;
    private final DayCounter fixedLegDayCounter_;
    private final boolean includeFirstSwaplet_;

    private CapFloor cap_;

    public CapHelper(final Period length, final Handle< Quote > volatility, final IborIndex index,
            // data for ATM swap-rate calculation
            final Frequency fixedLegFrequency, final DayCounter fixedLegDayCounter, final boolean includeFirstSwaplet,
            final Handle< YieldTermStructure > termStructure) {
        this(length, volatility, index, fixedLegFrequency, fixedLegDayCounter, includeFirstSwaplet, termStructure,
                CalibrationErrorType.RelativePriceError, VolatilityType.ShiftedLognormal, 0.0);
    }

    public CapHelper(final Period length, final Handle< Quote > volatility, final IborIndex index,
            final Frequency fixedLegFrequency, final DayCounter fixedLegDayCounter, final boolean includeFirstSwaplet,
            final Handle< YieldTermStructure > termStructure, final CalibrationErrorType errorType,
            final VolatilityType type, final double shift) {
        super(volatility, errorType, type, shift);
        this.length_ = length;
        this.index_ = index;
        this.termStructure_ = termStructure;
        this.fixedLegFrequency_ = fixedLegFrequency;
        this.fixedLegDayCounter_ = fixedLegDayCounter;
        this.includeFirstSwaplet_ = includeFirstSwaplet;
        this.termStructure_.addObserver(this);
        this.index_.addObserver(this);
    }

    @Override
    protected void performCalculations() {
        final Period indexTenor = index_.tenor();
        final double fixedRate = 0.04; // dummy value — re-solved below
        final Date startDate;
        final Date maturity;
        if ( includeFirstSwaplet_ ) {
            startDate = termStructure_.currentLink().referenceDate();
            maturity = termStructure_.currentLink().referenceDate().add(length_);
        } else {
            startDate = termStructure_.currentLink().referenceDate().add(indexTenor);
            maturity = termStructure_.currentLink().referenceDate().add(length_);
        }

        final Array nominals = new Array(new double[] { 1.0 });

        final Schedule floatSchedule = new Schedule(startDate, maturity, indexTenor, index_.fixingCalendar(),
                index_.businessDayConvention(), index_.businessDayConvention(), DateGeneration.Rule.Forward, false);
        final Leg floatingLeg = new IborLeg(floatSchedule, index_).withNotionals(nominals)
                .withPaymentAdjustment(index_.businessDayConvention()).withFixingDays(0).Leg();

        final Schedule fixedSchedule = new Schedule(startDate, maturity, new Period(fixedLegFrequency_),
                index_.fixingCalendar(), BusinessDayConvention.Unadjusted, BusinessDayConvention.Unadjusted,
                DateGeneration.Rule.Forward, false);
        // FixedRateLeg's Java ctor takes the day counter directly (mirrors
        // C++ FixedRateLeg(...).withCouponRates(rate, dayCounter) usage).
        final Leg fixedLeg = new FixedRateLeg(fixedSchedule, fixedLegDayCounter_).withNotionals(new double[] { 1.0 })
                .withCouponRates(fixedRate).withPaymentAdjustment(index_.businessDayConvention()).Leg();

        final Swap swap = new Swap(floatingLeg, fixedLeg);
        swap.setPricingEngine(new DiscountingSwapEngine(termStructure_));
        final double fairRate = fixedRate - swap.NPV() / (swap.legBPS(1) / 1.0e-4);

        // Java CapFloor takes (Type, Leg, List<Double> strikes, Handle<YTS>, engine).
        // Mirrors C++ Cap(floatingLeg, vector<Rate>(1, fairRate)) — the
        // termStructure parameter is Java-specific carryover and is not
        // used by the Cap constructor in C++ v1.42.1.
        cap_ = new CapFloor(CapFloor.Type.Cap, floatingLeg,
                new ArrayList<>(Collections.singletonList(Double.valueOf(fairRate))), termStructure_, null);

        super.performCalculations(); // sets marketValue_ from blackPrice
    }

    @Override
    public void addTimesTo(final ArrayList< Time > times) {
        calculate();
        // CapFloor::arguments + DiscretizedCapFloor.mandatoryTimes() are
        // not yet ported in Java. Mirror C++ caphelper.cpp lines 51-61
        // when those are available; deferred to Phase 2e.
    }

    @Override
    public double modelValue() {
        calculate();
        cap_.setPricingEngine(engine_);
        return cap_.NPV();
    }

    @Override
    public double blackPrice(final double sigma) {
        calculate();
        // Mirror C++ caphelper.cpp lines 69-89: build a transient
        // BlackCapFloorEngine with vol = SimpleQuote(sigma), price the
        // cap, then restore engine_. Phase 2e WI-2 unstub.
        final Handle< Quote > vol = new Handle< Quote >(new SimpleQuote(sigma));
        final PricingEngine engine;
        switch ( volatilityType_ ) {
        case ShiftedLognormal:
            // Java BlackCapFloorEngine doesn't yet take a displacement
            // ctor argument; ConstantOptionletVolatility doesn't yet
            // carry VolatilityType / displacement either. shift_ is
            // therefore unused in Java today (it is captured on the
            // helper for forward compatibility). All current call-sites
            // pass shift_ = 0.0 so this preserves correctness.
            engine = new BlackCapFloorEngine(termStructure_, vol, new Actual365Fixed());
            break;
        case Normal:
            // Phase 2f WI-1: BachelierCapFloorEngine is now real;
            // mirror C++ caphelper.cpp lines 78-86.
            engine = new BachelierCapFloorEngine(termStructure_, vol, new Actual365Fixed());
            break;
        default:
            throw new IllegalStateException("unknown volatility type");
        }
        cap_.setPricingEngine(engine);
        final double value = cap_.NPV();
        if ( engine_ != null ) {
            cap_.setPricingEngine(engine_);
        }
        return value;
    }
}
