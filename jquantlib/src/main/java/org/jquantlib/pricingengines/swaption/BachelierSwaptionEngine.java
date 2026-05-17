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
 Copyright (C) 2007 Ferdinando Ametrano
 Copyright (C) 2001, 2002, 2003 Sadruddin Rejeb
 Copyright (C) 2006 StatPro Italia srl
 Copyright (C) 2015, 2016, 2017 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */
package org.jquantlib.pricingengines.swaption;

import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.SwaptionVolatilityStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.swaption.ConstantSwaptionVolatility;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.calendars.NullCalendar;

/**
 * Normal Bachelier-formula swaption engine.
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code BachelierSwaptionEngine}, specifically
 * the {@code BlackStyleSwaptionEngine<BachelierSpec>} specialisation in
 * {@code ql/pricingengines/swaption/blackswaptionengine.hpp}.
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 * <li>The C++ source factors the engine as a templated
 *     {@code BlackStyleSwaptionEngine<Spec>} with two specs
 *     ({@code Black76Spec} and {@code BachelierSpec}). Java collapses the
 *     {@code Spec::value} / {@code vega} / {@code delta} dispatch into the
 *     {@code calculate()} body of {@link BlackSwaptionEngine}, which branches
 *     at runtime on {@code vol_.volatilityType()}. {@code BachelierSwaptionEngine}
 *     therefore extends {@link BlackSwaptionEngine} and merely wires the
 *     volatility surface as {@link VolatilityType#Normal} so the existing
 *     branch fires the Bachelier closed-form path. This faithfully reproduces
 *     the C++ behaviour without duplicating the {@code calculate()} code.
 * <li>The C++ {@code BachelierSwaptionEngine} constructors do not expose a
 *     {@code displacement} parameter (a normal-vol surface has no shift); the
 *     Java port mirrors this and always passes {@code displacement = 0.0} to
 *     the parent {@link BlackSwaptionEngine} ctor.
 * <li>Additional results: identical to {@link BlackSwaptionEngine} with one
 *     caveat — the {@code vega} additional result is currently omitted for
 *     Normal volatility because {@code bachelierBlackFormulaStdDevDerivative}
 *     is not yet ported into {@link org.jquantlib.pricingengines.BlackFormula}.
 *     The {@code delta} entry is correct via
 *     {@link org.jquantlib.pricingengines.BlackFormula#bachelierBlackFormulaForwardDerivative}.
 * </ul>
 *
 * @see BlackSwaptionEngine
 * @see ConstantSwaptionVolatility
 */
public class BachelierSwaptionEngine extends BlackSwaptionEngine {

    //
    // public constructors
    //

    /**
     * Build with a constant normal volatility (fixed market data, floating
     * reference date). Wraps {@code vol} in an internal
     * {@link ConstantSwaptionVolatility} built on a {@link NullCalendar} with
     * {@link BusinessDayConvention#Following} and the supplied day counter,
     * matching the C++ defaults.
     */
    public BachelierSwaptionEngine(final Handle<YieldTermStructure> discountCurve,
            final double vol) {
        this(discountCurve, vol, new Actual365Fixed(), CashAnnuityModel.DiscountCurve);
    }

    /**
     * Constant normal volatility, explicit day counter and cash-annuity model.
     */
    public BachelierSwaptionEngine(final Handle<YieldTermStructure> discountCurve,
            final double vol,
            final DayCounter dc,
            final CashAnnuityModel model) {
        super(discountCurve, wrapConstantNormalVol(
                        new Handle<Quote>(new SimpleQuote(vol)), dc),
                model, 0.0);
    }

    /**
     * Build with a Quote-driven normal volatility (floating market data).
     * <p>
     * Java type erasure makes this ambiguous with the
     * {@code (Handle<YieldTermStructure>, Handle<SwaptionVolatilityStructure>)}
     * constructor, so it is exposed as a static factory (mirroring
     * {@link BlackSwaptionEngine#fromVolQuote}).
     */
    public static BachelierSwaptionEngine fromVolQuote(
            final Handle<YieldTermStructure> discountCurve,
            final Handle<Quote> vol) {
        return fromVolQuote(discountCurve, vol, new Actual365Fixed(),
                CashAnnuityModel.DiscountCurve);
    }

    /**
     * Quote-driven normal volatility, explicit day counter and cash-annuity
     * model.
     */
    public static BachelierSwaptionEngine fromVolQuote(
            final Handle<YieldTermStructure> discountCurve,
            final Handle<Quote> vol,
            final DayCounter dc,
            final CashAnnuityModel model) {
        return new BachelierSwaptionEngine(discountCurve,
                wrapConstantNormalVol(vol, dc), model);
    }

    /**
     * Build with a precomputed swaption volatility surface. The supplied
     * surface must itself report {@link VolatilityType#Normal} via
     * {@link SwaptionVolatilityStructure#volatilityType()}; otherwise the
     * parent {@link BlackSwaptionEngine#calculate()} routes to the
     * shifted-lognormal Black76 branch and the result will not be Bachelier.
     */
    public BachelierSwaptionEngine(final Handle<YieldTermStructure> discountCurve,
            final Handle<SwaptionVolatilityStructure> vol) {
        this(discountCurve, vol, CashAnnuityModel.DiscountCurve);
    }

    /**
     * Precomputed normal-vol surface with explicit cash-annuity model.
     */
    public BachelierSwaptionEngine(final Handle<YieldTermStructure> discountCurve,
            final Handle<SwaptionVolatilityStructure> vol,
            final CashAnnuityModel model) {
        super(discountCurve, vol, model, 0.0);
    }

    //
    // private helpers
    //

    private static Handle<SwaptionVolatilityStructure> wrapConstantNormalVol(
            final Handle<Quote> vol,
            final DayCounter dc) {
        return new Handle<SwaptionVolatilityStructure>(
                new ConstantSwaptionVolatility(0, new NullCalendar(),
                        BusinessDayConvention.Following, vol, dc,
                        VolatilityType.Normal, 0.0));
    }
}
