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
*/

/*
 Copyright (C) 2014, 2015 Michael von den Driesch
 Copyright (C) 2019 Wojciech Slusarski

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.pricingengines.capfloor;

import org.jquantlib.QL;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.CapFloor;
import org.jquantlib.instruments.Option;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.pricingengines.BlackFormula;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.optionlet.ConstantOptionletVolatility;
import org.jquantlib.termstructures.volatilities.optionlet.OptionletVolatilityStructure;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.calendars.NullCalendar;

/**
 * Bachelier-Black-formula cap/floor engine.
 *
 * <p>Mirrors C++ QuantLib v1.42.1
 * {@code ql/pricingengines/capfloor/bacheliercapfloorengine.{hpp,cpp}}.
 * Identical structurally to {@link BlackCapFloorEngine} except that each
 * optionlet is priced with {@link BlackFormula#bachelierBlackFormula}
 * (additive-normal Bachelier) rather than the lognormal Black-76 form.
 *
 * <p>Phase 2f WI-1.
 */
public class BachelierCapFloorEngine extends CapFloor.Engine {

    private final Handle<YieldTermStructure> discountCurve_;
    private final Handle<OptionletVolatilityStructure> vol_;

    public BachelierCapFloorEngine(
            final Handle<YieldTermStructure> discountCurve,
            final double v,
            final DayCounter dc) {
        this.discountCurve_ = discountCurve;
        this.vol_ = new Handle<OptionletVolatilityStructure>(
                new ConstantOptionletVolatility(0, new NullCalendar(),
                        BusinessDayConvention.Following,
                        new Handle<Quote>(new SimpleQuote(v)), dc));
        this.discountCurve_.addObserver(this);
    }

    public BachelierCapFloorEngine(
            final Handle<YieldTermStructure> discountCurve,
            final double v) {
        this(discountCurve, v, new Actual365Fixed());
    }

    public BachelierCapFloorEngine(
            final Handle<YieldTermStructure> discountCurve,
            final Handle<Quote> v,
            final DayCounter dc) {
        this.discountCurve_ = discountCurve;
        this.vol_ = new Handle<OptionletVolatilityStructure>(
                new ConstantOptionletVolatility(0, new NullCalendar(),
                        BusinessDayConvention.Following, v, dc));
        this.discountCurve_.addObserver(this);
        this.vol_.addObserver(this);
    }

    public BachelierCapFloorEngine(
            final Handle<YieldTermStructure> discountCurve,
            final Handle<OptionletVolatilityStructure> volatility) {
        this.discountCurve_ = discountCurve;
        this.vol_ = volatility;
        QL.require(this.vol_.currentLink().volatilityType() == VolatilityType.Normal,
                "BachelierCapFloorEngine should only be used for vol "
                        + "surfaces stripped with normal model");
        this.discountCurve_.addObserver(this);
        this.vol_.addObserver(this);
    }

    public Handle<YieldTermStructure> termStructure() {
        return discountCurve_;
    }

    public Handle<OptionletVolatilityStructure> volatility() {
        return vol_;
    }

    /**
     * Mirrors C++ bacheliercapfloorengine.cpp::calculate(). Phase 2f WI-1
     * ports the {@code value} aggregation only; the additionalResults map
     * (vega, optionletsPrice, optionletsVega, etc.) is a tactical
     * follow-up that does not change the {@code NPV()} surface and
     * matches the deferred scope of {@link BlackCapFloorEngine}.
     */
    @Override
    public void calculate() {
        final CapFloor.ArgumentsImpl arguments = (CapFloor.ArgumentsImpl) arguments_;
        final CapFloor.ResultsImpl results = (CapFloor.ResultsImpl) results_;

        double value = 0.0;
        final int optionlets = arguments.startDates.length;
        final CapFloor.Type type = arguments.type;
        final Date today = vol_.currentLink().referenceDate();
        final Date settlement = discountCurve_.currentLink().referenceDate();

        for (int i = 0; i < optionlets; ++i) {
            final Date paymentDate = arguments.endDates[i];
            if (paymentDate.gt(settlement)) {
                final double d = discountCurve_.currentLink().discount(paymentDate);
                final double accrualFactor = arguments.nominals[i]
                        * arguments.gearings[i]
                        * arguments.accrualTimes[i];
                final double discountedAccrual = d * accrualFactor;
                final double forward = arguments.forwards[i];

                final Date fixingDate = arguments.fixingDates[i];
                double sqrtTime = 0.0;
                if (fixingDate.gt(today)) {
                    sqrtTime = Math.sqrt(vol_.currentLink().timeFromReference(fixingDate));
                }

                double letValue = 0.0;
                if (type == CapFloor.Type.Cap || type == CapFloor.Type.Collar) {
                    final double strike = arguments.capRates[i];
                    double stdDev = 0.0;
                    if (sqrtTime > 0.0) {
                        stdDev = Math.sqrt(vol_.currentLink().blackVariance(fixingDate, strike));
                    }
                    letValue = BlackFormula.bachelierBlackFormula(Option.Type.Call,
                            strike, forward, stdDev, discountedAccrual);
                }
                if (type == CapFloor.Type.Floor || type == CapFloor.Type.Collar) {
                    final double strike = arguments.floorRates[i];
                    double stdDev = 0.0;
                    if (sqrtTime > 0.0) {
                        stdDev = Math.sqrt(vol_.currentLink().blackVariance(fixingDate, strike));
                    }
                    final double floorlet = BlackFormula.bachelierBlackFormula(Option.Type.Put,
                            strike, forward, stdDev, discountedAccrual);
                    if (type == CapFloor.Type.Floor) {
                        letValue = floorlet;
                    } else {
                        // a collar is long a cap and short a floor
                        letValue -= floorlet;
                    }
                }
                value += letValue;
            }
        }
        QL.require(!Double.isNaN(value), "BachelierCapFloorEngine produced NaN value");
        results.value = value;
    }
}
