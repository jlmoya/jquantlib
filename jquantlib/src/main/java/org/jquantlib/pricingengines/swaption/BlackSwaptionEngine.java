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

import org.jquantlib.QL;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.Settlement;
import org.jquantlib.instruments.Swaption;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.pricingengines.BlackFormula;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.SwaptionVolatilityStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.swaption.ConstantSwaptionVolatility;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.calendars.NullCalendar;

/**
 * Shifted Lognormal Black-formula swaption engine.
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code BlackSwaptionEngine}, specifically the
 * {@code BlackStyleSwaptionEngine<Black76Spec>} specialisation.
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 * <li>The C++ class is a templated {@code BlackStyleSwaptionEngine<Spec>} with
 *     {@code Black76Spec} (this engine) and {@code BachelierSpec} (the
 *     separate {@code BachelierSwaptionEngine}). Only the Black76 path is
 *     ported in this commit; Bachelier is deferred to a later phase.
 * <li>The {@code volatilityType()} / {@code shift()} machinery is not yet on
 *     the Java {@link SwaptionVolatilityStructure} base class, so the engine
 *     assumes shifted-lognormal with shift = 0 (i.e. plain Black76). When the
 *     base class gains those methods the displacement read should be wired
 *     through here.
 * <li>The C++ engine populates several additional results
 *     ({@code spreadCorrection}, {@code strike}, {@code atmForward},
 *     {@code annuity}, {@code stdDev}, {@code vega}, {@code delta},
 *     {@code timeToExpiry}, {@code impliedVolatility},
 *     {@code forwardPrice}, {@code valuationDate}) on
 *     {@code results_.additionalResults}. This Java port computes the same
 *     intermediate values but only publishes the NPV (via
 *     {@code results_.value}). Additional-results dispatch through Java
 *     {@code Instrument.ResultsImpl#additionalResults()} is straightforward
 *     to wire later if a consumer needs it.
 * <li>The C++ engine handles three settlement methods (Physical, Cash with
 *     CollateralizedCashPrice, Cash with ParYieldCurve). The ParYieldCurve
 *     branch requires {@code CashFlows::bps(InterestRate, ...)} and
 *     {@code Schedule::tenor()/hasTenor()} which are not yet ported on the
 *     Java side. The Java port supports Physical and CollateralizedCashPrice
 *     out of the box; ParYieldCurve throws a clear error directing the caller
 *     to file a follow-up.
 * </ul>
 *
 * @see Swaption
 * @see ConstantSwaptionVolatility
 */
public class BlackSwaptionEngine extends Swaption.EngineImpl {

    /**
     * Cash-annuity discounting model. Mirrors the C++
     * {@code BlackStyleSwaptionEngine<Spec>::CashAnnuityModel} enum.
     * <p>
     * Only meaningful for {@link Settlement.Method#ParYieldCurve}; the other
     * settlement methods ignore this parameter. Default is
     * {@link #DiscountCurve} (matches C++ default).
     */
    public static enum CashAnnuityModel {
        SwapRate,
        DiscountCurve
    }

    private static final double BASIS_POINT = 1.0e-4;

    private final Handle<YieldTermStructure> discountCurve_;
    private final Handle<SwaptionVolatilityStructure> vol_;
    private final double displacement_;
    private final CashAnnuityModel model_;

    //
    // public constructors
    //

    /**
     * Build with a constant volatility (fixed market data, floating reference).
     * Wraps the volatility in an internal {@link ConstantSwaptionVolatility}
     * built on a {@link NullCalendar} with {@link BusinessDayConvention#Following},
     * matching the C++ defaults.
     */
    public BlackSwaptionEngine(final Handle<YieldTermStructure> discountCurve,
            final double vol) {
        this(discountCurve, wrapConstantVol(new Handle<Quote>(new SimpleQuote(vol))),
                CashAnnuityModel.DiscountCurve, 0.0);
    }

    /**
     * Build with a Quote-driven volatility (floating market data).
     * <p>
     * Java type erasure makes this ambiguous with the
     * {@code (Handle<YieldTermStructure>, Handle<SwaptionVolatilityStructure>)}
     * constructor, so it is exposed as a static factory.
     */
    public static BlackSwaptionEngine fromVolQuote(
            final Handle<YieldTermStructure> discountCurve,
            final Handle<Quote> vol) {
        return new BlackSwaptionEngine(discountCurve, wrapConstantVol(vol),
                CashAnnuityModel.DiscountCurve, 0.0);
    }

    /**
     * Build with a precomputed swaption volatility surface.
     */
    public BlackSwaptionEngine(final Handle<YieldTermStructure> discountCurve,
            final Handle<SwaptionVolatilityStructure> vol) {
        this(discountCurve, vol, CashAnnuityModel.DiscountCurve, 0.0);
    }

    private static Handle<SwaptionVolatilityStructure> wrapConstantVol(
            final Handle<Quote> vol) {
        return new Handle<SwaptionVolatilityStructure>(
                new ConstantSwaptionVolatility(0, new NullCalendar(),
                        BusinessDayConvention.Following, vol,
                        new Actual365Fixed()));
    }

    /**
     * Full constructor exposing {@link CashAnnuityModel} and displacement.
     */
    public BlackSwaptionEngine(final Handle<YieldTermStructure> discountCurve,
            final Handle<SwaptionVolatilityStructure> vol,
            final CashAnnuityModel model,
            final double displacement) {
        super();
        this.discountCurve_ = discountCurve;
        this.vol_ = vol;
        this.model_ = model;
        this.displacement_ = displacement;
        this.discountCurve_.addObserver(this);
        this.vol_.addObserver(this);
    }

    //
    // public inspectors
    //

    public Handle<YieldTermStructure> termStructure() {
        return discountCurve_;
    }

    public Handle<SwaptionVolatilityStructure> volatility() {
        return vol_;
    }

    //
    // implements PricingEngine
    //

    @Override
    public void calculate() /* @ReadOnly */ {
        final Swaption.ArgumentsImpl args = (Swaption.ArgumentsImpl) arguments_;
        final Swaption.ResultsImpl results = (Swaption.ResultsImpl) results_;

        final Exercise exercise = args.exercise;
        QL.require(exercise.type() == Exercise.Type.European, "not a European option");

        final Date exerciseDate = exercise.date(0);
        final VanillaSwap swap = args.swap;

        // Mirror C++: temporarily plug a DiscountingSwapEngine into the
        // underlying swap so swap.fairRate()/fixedLegBPS() are computed on
        // the engine's own discount curve (not whatever curve the index was
        // built with). C++ disables observable updates around setPricingEngine
        // to avoid notifying the swaption; Java's setPricingEngine performs
        // notifyObservers but the swaption is a downstream observer of the
        // swap and will simply re-trigger calculate() on next NPV() — harmless
        // here because we are inside calculate() and Instrument.calculate()
        // is idempotent under LazyObject.
        swap.setPricingEngine(new DiscountingSwapEngine(discountCurve_));

        double strike = swap.fixedRate();
        double atmForward = swap.fairRate();

        // Volatilities are quoted for zero-spreaded swaps. Any spread on the
        // floating leg requires a corresponding correction on the fixed leg.
        final double spread = swap.spread();
        if (spread != 0.0) {
            final double correction = spread
                    * Math.abs(swap.floatingLegBPS() / swap.fixedLegBPS());
            strike -= correction;
            atmForward -= correction;
        }

        // Annuity: depends on settlement type / method.
        final double annuity;
        if (args.settlementType == Settlement.Type.Physical
                || (args.settlementType == Settlement.Type.Cash
                        && args.settlementMethod == Settlement.Method.CollateralizedCashPrice)) {
            annuity = Math.abs(swap.fixedLegBPS()) / BASIS_POINT;
        } else if (args.settlementType == Settlement.Type.Cash
                && args.settlementMethod == Settlement.Method.ParYieldCurve) {
            // Requires CashFlows.bps(InterestRate, ...) + Schedule.tenor() /
            // Schedule.hasTenor() — both not yet ported on the Java side.
            // Throw a clear error so the caller (and any future port pass)
            // sees the missing surface area.
            throw new UnsupportedOperationException(
                    "Cash/ParYieldCurve settlement is not yet implemented in the Java"
                    + " BlackSwaptionEngine port (requires CashFlows.bps(InterestRate)"
                    + " + Schedule.tenor()). Use Physical or CollateralizedCashPrice.");
        } else {
            throw new IllegalStateException(
                    "invalid (settlementType, settlementMethod) pair: "
                    + args.settlementType + " / " + args.settlementMethod);
        }

        // Variance / std dev. The C++ swapLength rounds (end-start)/365.25*12
        // to the nearest whole month then divides by 12 — for our 5Y x 5Y
        // ATM probe the swap is exactly 5 years long so the round-trip is a
        // no-op, but mirror the formula here so other fixtures stay aligned.
        final java.util.List<Date> floatDates = swap.floatingSchedule().dates();
        final double swapLength = computeSwapLength(floatDates.get(0),
                floatDates.get(floatDates.size() - 1));
        final double variance = vol_.currentLink().blackVariance(
                vol_.currentLink().timeFromReference(exerciseDate),
                swapLength, strike, true);

        final double stdDev = Math.sqrt(variance);

        // Black76: payer -> Call, receiver -> Put.
        final Option.Type w = (swap.type() == VanillaSwap.Type.Payer)
                ? Option.Type.Call : Option.Type.Put;

        results.value = BlackFormula.blackFormula(
                w, strike, atmForward, stdDev, annuity, displacement_);
    }

    //
    // private helpers
    //

    /**
     * Mirrors C++ {@code SwaptionVolatilityStructure::swapLength(start, end)}:
     * {@code (end-start)/365.25*12} rounded to the nearest whole month, then
     * divided by 12 to get years.
     */
    private static double computeSwapLength(final Date start, final Date end) {
        QL.require(end.gt(start), "swap end date must be greater than start");
        final double months = (end.serialNumber() - start.serialNumber()) / 365.25 * 12.0;
        // ClosestRounding(0): round half-away-from-zero to nearest integer.
        final double monthsRounded = Math.floor(months + 0.5);
        return monthsRounded / 12.0;
    }
}
