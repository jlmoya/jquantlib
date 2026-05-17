/*
Copyright (C) 2009 John Martin

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
 Copyright (C) 2007 Ferdinando Ametrano
 Copyright (C) 2001, 2002, 2003 Sadruddin Rejeb
 Copyright (C) 2006 StatPro Italia srl

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
 * Black-formula cap/floor engine.
 *
 * <p>Mirrors C++ v1.42.1 ql/pricingengines/capfloor/blackcapfloorengine.{hpp,cpp}.
 * Java's {@link OptionletVolatilityStructure} does not yet expose
 * {@code displacement()} (C++ adds it in v1.42.1 but Java has not been
 * retrofitted), so the {@code (YTS, Handle<OVS>)} ctor still defaults the
 * displacement to 0.0 unless overridden via the
 * {@code (YTS, Handle<OVS>, double displacement)} overload.
 * Phase 5g.5d adds the {@code displacement} parameter to all ctors,
 * matching v1.42.1's API.
 *
 * <p>Phase 2e WI-2; displacement parameter added Phase 5g.5d.
 */
public class BlackCapFloorEngine extends CapFloor.Engine {

    private final Handle<YieldTermStructure> discountCurve_;
    private final Handle<OptionletVolatilityStructure> vol_;
    private final double displacement_;

    public BlackCapFloorEngine(
            final Handle<YieldTermStructure> discountCurve,
            final double v,
            final DayCounter dc) {
        this(discountCurve, v, dc, 0.0);
    }

    public BlackCapFloorEngine(
            final Handle<YieldTermStructure> discountCurve,
            final double v,
            final DayCounter dc,
            final double displacement) {
        this.discountCurve_ = discountCurve;
        // Wrap the fixed double in a SimpleQuote so the engine can hold
        // a Handle<OptionletVolatilityStructure> uniformly.
        this.vol_ = new Handle<OptionletVolatilityStructure>(
                new ConstantOptionletVolatility(0, new NullCalendar(),
                        BusinessDayConvention.Following,
                        new Handle<Quote>(new SimpleQuote(v)), dc));
        this.displacement_ = displacement;
        this.discountCurve_.addObserver(this);
    }

    public BlackCapFloorEngine(
            final Handle<YieldTermStructure> discountCurve,
            final Handle<Quote> v,
            final DayCounter dc) {
        this(discountCurve, v, dc, 0.0);
    }

    public BlackCapFloorEngine(
            final Handle<YieldTermStructure> discountCurve,
            final Handle<Quote> v,
            final DayCounter dc,
            final double displacement) {
        this.discountCurve_ = discountCurve;
        this.vol_ = new Handle<OptionletVolatilityStructure>(
                new ConstantOptionletVolatility(0, new NullCalendar(),
                        BusinessDayConvention.Following, v, dc));
        this.displacement_ = displacement;
        this.discountCurve_.addObserver(this);
        this.vol_.addObserver(this);
    }

    public BlackCapFloorEngine(
            final Handle<YieldTermStructure> discountCurve,
            final Handle<OptionletVolatilityStructure> volatility) {
        this(discountCurve, volatility, 0.0);
    }

    /**
     * Mirrors C++ v1.42.1 ctor with displacement parameter.
     *
     * <p>C++ asserts the OVS uses ShiftedLognormal model and compares the
     * caller-provided displacement to the OVS-stored displacement when the
     * caller passes a non-Null value. Java's OVS has not yet been
     * retrofitted with {@code displacement()}, so we only store the
     * caller-supplied value. Pass {@code 0.0} (or use the no-displacement
     * overload) for the legacy zero-shift behavior.
     */
    public BlackCapFloorEngine(
            final Handle<YieldTermStructure> discountCurve,
            final Handle<OptionletVolatilityStructure> volatility,
            final double displacement) {
        this.discountCurve_ = discountCurve;
        this.vol_ = volatility;
        // Java OVS lacks displacement(); see class javadoc.
        // The C++ engine asserts ShiftedLognormal and matches the curve's
        // stored displacement here. Java only stores the caller value.
        this.displacement_ = displacement;
        this.discountCurve_.addObserver(this);
        this.vol_.addObserver(this);
    }

    public BlackCapFloorEngine(
            final Handle<YieldTermStructure> discountCurve,
            final double v) {
        this(discountCurve, v, new Actual365Fixed());
    }

    // Note: cannot offer a (Handle<YTS>, Handle<Quote>) default-DC overload
    // because Handle<Quote> and Handle<OptionletVolatilityStructure> erase
    // to the same JVM signature. Callers using the Handle<Quote> shape must
    // pass the DayCounter explicitly (use Actual365Fixed to match C++).

    public Handle<YieldTermStructure> termStructure() {
        return discountCurve_;
    }

    public Handle<OptionletVolatilityStructure> volatility() {
        return vol_;
    }

    public double displacement() {
        return displacement_;
    }

    /**
     * Mirrors C++ blackcapfloorengine.cpp lines 77-166.
     *
     * <p>Populates {@code results.value} plus the named additionalResults
     * (vega, optionletsPrice, optionletsVega, optionletsDelta,
     * optionletsDiscountFactor, optionletsAtmForward, optionletsStdDev)
     * exactly as C++ does — Phase 5e.5b-CFC-d-49 finished the port.
     *
     * <p>When the OVS is stripped under the Bachelier (Normal) model the
     * engine falls back to {@code bachelierBlackFormula} for the optionlet
     * value to stay compatible with legacy callers. The analytic vega /
     * delta entries are then left at zero — the proper Java
     * {@code BachelierCapFloorEngine} (Phase 5e.5 WI-5e.5-CF-2) owns that
     * dispatch.
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

        // Phase 2f WI-1: optionlet vol surfaces now expose volatilityType().
        // When the surface is stripped under the normal model, dispatch
        // each optionlet to the Bachelier closed form. C++ keeps Black /
        // Bachelier as separate engines and asserts the surface type at
        // construction; Java picks the formula at calculate() time so the
        // same Java BlackCapFloorEngine instance can be used regardless
        // of how the OVS was stripped.
        final boolean useBachelier =
                vol_.currentLink().volatilityType() == VolatilityType.Normal;

        for (int i = 0; i < optionlets; ++i) {
            final Date paymentDate = arguments.endDates[i];
            // handling of settlementDate, npvDate and includeSettlementFlows
            // should be implemented; for the time being just discard expired
            // caplets.
            if (paymentDate.gt(settlement)) {
                final double d = discountCurve_.currentLink().discount(paymentDate);
                discountFactors[i] = d;
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

                if (type == CapFloor.Type.Cap || type == CapFloor.Type.Collar) {
                    final double strike = arguments.capRates[i];
                    if (sqrtTime > 0.0) {
                        stdDevs[i] = Math.sqrt(
                                vol_.currentLink().blackVariance(fixingDate, strike));
                        if (!useBachelier) {
                            vegas[i] = BlackFormula.blackFormulaStdDevDerivative(
                                    strike, forward, stdDevs[i],
                                    discountedAccrual, displacement_)
                                    * sqrtTime;
                            deltas[i] = BlackFormula.blackFormulaAssetItmProbability(
                                    Option.Type.Call, strike, forward,
                                    stdDevs[i], displacement_);
                        }
                    }
                    // include caplets with past fixing date
                    values[i] = useBachelier
                            ? BlackFormula.bachelierBlackFormula(Option.Type.Call,
                                    strike, forward, stdDevs[i], discountedAccrual)
                            : BlackFormula.blackFormula(Option.Type.Call,
                                    strike, forward, stdDevs[i], discountedAccrual, displacement_);
                }
                if (type == CapFloor.Type.Floor || type == CapFloor.Type.Collar) {
                    final double strike = arguments.floorRates[i];
                    double floorletVega = 0.0;
                    double floorletDelta = 0.0;
                    if (sqrtTime > 0.0) {
                        stdDevs[i] = Math.sqrt(
                                vol_.currentLink().blackVariance(fixingDate, strike));
                        if (!useBachelier) {
                            floorletVega = BlackFormula.blackFormulaStdDevDerivative(
                                    strike, forward, stdDevs[i],
                                    discountedAccrual, displacement_)
                                    * sqrtTime;
                            // C++: Integer(Option::Put) * blackFormulaAssetItmProbability(Put, ...)
                            //      = -1 * Phi(-d1) — a non-positive delta.
                            floorletDelta = Option.Type.Put.toInteger()
                                    * BlackFormula.blackFormulaAssetItmProbability(
                                            Option.Type.Put, strike, forward,
                                            stdDevs[i], displacement_);
                        }
                    }
                    final double floorlet = useBachelier
                            ? BlackFormula.bachelierBlackFormula(Option.Type.Put,
                                    strike, forward, stdDevs[i], discountedAccrual)
                            : BlackFormula.blackFormula(Option.Type.Put,
                                    strike, forward, stdDevs[i], discountedAccrual, displacement_);
                    if (type == CapFloor.Type.Floor) {
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
        QL.require(!Double.isNaN(value), "BlackCapFloorEngine produced NaN value");
        results.value = value;

        // Populate additionalResults exactly as C++ does (lines 157-165).
        // The keys must match the C++ names verbatim so call-sites that
        // read results via Instrument.result(key) port without churn.
        results.additionalResults().put("vega", vega);
        results.additionalResults().put("optionletsPrice", values);
        results.additionalResults().put("optionletsVega", vegas);
        results.additionalResults().put("optionletsDelta", deltas);
        results.additionalResults().put("optionletsDiscountFactor", discountFactors);
        results.additionalResults().put("optionletsAtmForward", arguments.forwards);
        if (type != CapFloor.Type.Collar) {
            results.additionalResults().put("optionletsStdDev", stdDevs);
        }
    }
}
