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
 * {@code ql/pricingengines/capfloor/bacheliercapfloorengine.{hpp,cpp}}. Identical structurally to
 * {@link BlackCapFloorEngine} except that each optionlet is priced with {@link BlackFormula#bachelierBlackFormula}
 * (additive-normal Bachelier) rather than the lognormal Black-76 form.
 *
 * <p>Phase 2f WI-1.
 */
public class BachelierCapFloorEngine extends CapFloor.Engine {

    private final Handle< YieldTermStructure > discountCurve_;
    private final Handle< OptionletVolatilityStructure > vol_;

    public BachelierCapFloorEngine(final Handle< YieldTermStructure > discountCurve, final double v,
            final DayCounter dc) {
        this.discountCurve_ = discountCurve;
        this.vol_ = new Handle< OptionletVolatilityStructure >(
                new ConstantOptionletVolatility(0, new NullCalendar(), BusinessDayConvention.Following,
                        new Handle< Quote >(new SimpleQuote(v)), dc));
        this.discountCurve_.addObserver(this);
    }

    public BachelierCapFloorEngine(final Handle< YieldTermStructure > discountCurve, final double v) {
        this(discountCurve, v, new Actual365Fixed());
    }

    public BachelierCapFloorEngine(final Handle< YieldTermStructure > discountCurve, final Handle< Quote > v,
            final DayCounter dc) {
        this.discountCurve_ = discountCurve;
        this.vol_ = new Handle< OptionletVolatilityStructure >(
                new ConstantOptionletVolatility(0, new NullCalendar(), BusinessDayConvention.Following, v, dc));
        this.discountCurve_.addObserver(this);
        this.vol_.addObserver(this);
    }

    public BachelierCapFloorEngine(final Handle< YieldTermStructure > discountCurve,
            final Handle< OptionletVolatilityStructure > volatility) {
        this.discountCurve_ = discountCurve;
        this.vol_ = volatility;
        QL.require(this.vol_.currentLink().volatilityType() == VolatilityType.Normal,
                "BachelierCapFloorEngine should only be used for vol " + "surfaces stripped with normal model");
        this.discountCurve_.addObserver(this);
        this.vol_.addObserver(this);
    }

    public Handle< YieldTermStructure > termStructure() {
        return discountCurve_;
    }

    public Handle< OptionletVolatilityStructure > volatility() {
        return vol_;
    }

    /**
     * Mirrors C++ bacheliercapfloorengine.cpp::calculate() (v1.42.1 lines 62-149).
     *
     * <p>Populates {@code results.value} plus the named additionalResults
     * (vega, optionletsPrice, optionletsVega, optionletsDelta, optionletsDiscountFactor, optionletsAtmForward,
     * optionletsStdDev) exactly as C++ does — Phase 5e.5b-CFC-d-299 finished the port and unblocked
     * {@code CapFloorTest.testBachelierOptionLetsDelta}.
     */
    @Override
    public void calculate() {
        final CapFloor.ArgumentsImpl arguments = (CapFloor.ArgumentsImpl) arguments_;
        final CapFloor.ResultsImpl results = (CapFloor.ResultsImpl) results_;

        double value = 0.0;
        double vega = 0.0;
        final int optionlets = arguments.startDates.length;
        final double[] values = new double[optionlets];
        final double[] deltas = new double[optionlets];
        final double[] vegas = new double[optionlets];
        final double[] stdDevs = new double[optionlets];
        final double[] discountFactors = new double[optionlets];
        final CapFloor.Type type = arguments.type;
        final Date today = vol_.currentLink().referenceDate();
        final Date settlement = discountCurve_.currentLink().referenceDate();

        for ( int i = 0; i < optionlets; ++i ) {
            final Date paymentDate = arguments.endDates[i];
            // handling of settlementDate, npvDate and includeSettlementFlows
            // should be implemented; for the time being just discard expired
            // caplets.
            if ( paymentDate.gt(settlement) ) {
                final double d = discountCurve_.currentLink().discount(paymentDate);
                discountFactors[i] = d;
                final double accrualFactor = arguments.nominals[i] * arguments.gearings[i] * arguments.accrualTimes[i];
                final double discountedAccrual = d * accrualFactor;
                final double forward = arguments.forwards[i];

                final Date fixingDate = arguments.fixingDates[i];
                double sqrtTime = 0.0;
                if ( fixingDate.gt(today) ) {
                    sqrtTime = Math.sqrt(vol_.currentLink().timeFromReference(fixingDate));
                }

                if ( type == CapFloor.Type.Cap || type == CapFloor.Type.Collar ) {
                    final double strike = arguments.capRates[i];
                    if ( sqrtTime > 0.0 ) {
                        stdDevs[i] = Math.sqrt(vol_.currentLink().blackVariance(fixingDate, strike));
                        vegas[i] = BlackFormula.bachelierBlackFormulaStdDevDerivative(strike, forward, stdDevs[i],
                                discountedAccrual) * sqrtTime;
                        deltas[i] = BlackFormula.bachelierBlackFormulaAssetItmProbability(Option.Type.Call, strike,
                                forward, stdDevs[i]);
                    }
                    // include caplets with past fixing date
                    values[i] = BlackFormula.bachelierBlackFormula(Option.Type.Call, strike, forward, stdDevs[i],
                            discountedAccrual);
                }
                if ( type == CapFloor.Type.Floor || type == CapFloor.Type.Collar ) {
                    final double strike = arguments.floorRates[i];
                    double floorletVega = 0.0;
                    double floorletDelta = 0.0;
                    if ( sqrtTime > 0.0 ) {
                        stdDevs[i] = Math.sqrt(vol_.currentLink().blackVariance(fixingDate, strike));
                        floorletVega = BlackFormula.bachelierBlackFormulaStdDevDerivative(strike, forward, stdDevs[i],
                                discountedAccrual) * sqrtTime;
                        // C++: Integer(Option::Put) * bachelierBlackFormulaAssetItmProbability(Put, ...)
                        //      = -1 * Phi(-h) — a non-positive delta.
                        floorletDelta = Option.Type.Put.toInteger() * BlackFormula
                                .bachelierBlackFormulaAssetItmProbability(Option.Type.Put, strike, forward, stdDevs[i]);
                    }
                    final double floorlet = BlackFormula.bachelierBlackFormula(Option.Type.Put, strike, forward,
                            stdDevs[i], discountedAccrual);
                    if ( type == CapFloor.Type.Floor ) {
                        values[i] = floorlet;
                        vegas[i] = floorletVega;
                        deltas[i] = floorletDelta;
                    } else {
                        // a collar is long a cap and short a floor
                        values[i] -= floorlet;
                        vegas[i] -= floorletVega;
                        deltas[i] -= floorletDelta;
                    }
                }
                value += values[i];
                vega += vegas[i];
            }
        }
        QL.require(!Double.isNaN(value), "BachelierCapFloorEngine produced NaN value");
        results.value = value;

        // Populate additionalResults exactly as C++ does (lines 140-148).
        // Keys match the C++ names verbatim so callers reading via
        // Instrument.result(key) port without churn.
        results.additionalResults().put("vega", vega);
        results.additionalResults().put("optionletsPrice", values);
        results.additionalResults().put("optionletsVega", vegas);
        results.additionalResults().put("optionletsDelta", deltas);
        results.additionalResults().put("optionletsDiscountFactor", discountFactors);
        results.additionalResults().put("optionletsAtmForward", arguments.forwards);
        if ( type != CapFloor.Type.Collar ) {
            results.additionalResults().put("optionletsStdDev", stdDevs);
        }
    }
}
