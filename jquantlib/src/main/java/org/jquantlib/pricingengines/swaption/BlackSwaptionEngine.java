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
import org.jquantlib.model.VolatilityType;
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
 *     {@code Black76Spec} (this engine, {@link VolatilityType#ShiftedLognormal})
 *     and {@code BachelierSpec} ({@link VolatilityType#Normal}). Java collapses
 *     both into a single class that branches at runtime on
 *     {@code vol_.volatilityType()} (Phase 2f WI-2): shifted-lognormal uses
 *     {@link BlackFormula#blackFormula(Option.Type, double, double, double, double, double)};
 *     normal uses {@link BlackFormula#bachelierBlackFormula(Option.Type, double, double, double, double)}
 *     (which discards the displacement, mirroring C++ {@code BachelierSpec::value}).
 * <li>The {@link SwaptionVolatilityStructure#shift()} accessor was added in
 *     this WI (see {@code align(termstructures): SwaptionVolatilityStructure
 *     ...}); the engine reads it for shifted-lognormal volatilities and falls
 *     back to the constructor-supplied {@code displacement_} otherwise.
 * <li>The C++ engine populates several additional results
 *     ({@code spreadCorrection}, {@code strike}, {@code atmForward},
 *     {@code annuity}, {@code swapLength}, {@code stdDev}, {@code vega},
 *     {@code delta}, {@code timeToExpiry}, {@code impliedVolatility},
 *     {@code forwardPrice}) on {@code results_.additionalResults}. Java
 *     mirrors C++ (Phase 5e.5b-CFC-d-73): every result above is published.
 *     The C++-only {@code valuationDate} is currently omitted because the
 *     Java {@link org.jquantlib.instruments.Swap} hierarchy has no
 *     {@code valuationDate()} accessor yet. {@link VolatilityType#Normal}
 *     uses {@code BachelierSpec}-style vega via
 *     {@link BlackFormula#bachelierBlackFormulaStdDevDerivative} when that
 *     function is available; until then Normal-vol callers will see a
 *     {@code null} {@code vega} entry — the {@code delta} entry is correct
 *     in both cases via the dedicated forward-derivative formulae.
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
            results.additionalResults().put("spreadCorrection", correction);
        } else {
            results.additionalResults().put("spreadCorrection", 0.0);
        }
        results.additionalResults().put("strike", strike);
        results.additionalResults().put("atmForward", atmForward);

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

        results.additionalResults().put("annuity", annuity);

        // Variance / std dev. The C++ swapLength rounds (end-start)/365.25*12
        // to the nearest whole month then divides by 12 — for our 5Y x 5Y
        // ATM probe the swap is exactly 5 years long so the round-trip is a
        // no-op, but mirror the formula here so other fixtures stay aligned.
        final java.util.List<Date> floatDates = swap.floatingSchedule().dates();
        final double swapLengthRaw = computeSwapLength(floatDates.get(0),
                floatDates.get(floatDates.size() - 1));
        // Match C++: floor swapLength at 1/12 so the vol surface can read a
        // variance/shift for sub-month tenors.
        final double swapLength = Math.max(swapLengthRaw, 1.0 / 12.0);
        results.additionalResults().put("swapLength", swapLength);
        final double variance = vol_.currentLink().blackVariance(
                vol_.currentLink().timeFromReference(exerciseDate),
                swapLength, strike, true);

        final double stdDev = Math.sqrt(variance);
        results.additionalResults().put("stdDev", stdDev);

        // Black76: payer -> Call, receiver -> Put.
        final Option.Type w = (swap.type() == VanillaSwap.Type.Payer)
                ? Option.Type.Call : Option.Type.Put;

        // Volatility-type dispatch (mirrors C++ Spec::value):
        //  - ShiftedLognormal -> blackFormula with displacement (engine ctor
        //    or vol_->shift(); Java port prefers the volatility surface's
        //    shift when the surface overrides the default 0.0).
        //  - Normal           -> bachelierBlackFormula (no displacement).
        final VolatilityType volType = vol_.currentLink().volatilityType();
        final double effectiveDisplacement;
        if (volType == VolatilityType.Normal) {
            effectiveDisplacement = 0.0;
            results.value = BlackFormula.bachelierBlackFormula(
                    w, strike, atmForward, stdDev, annuity);
        } else {
            // ShiftedLognormal. C++ pulls displacement from vol_->shift(...);
            // Java mirrors via the same accessor with a graceful fallback to
            // the constructor-supplied displacement_ when vol_ exposes only
            // the legacy default.
            final double volShift = vol_.currentLink().shift();
            effectiveDisplacement =
                    (volShift != 0.0) ? volShift : displacement_;
            results.value = BlackFormula.blackFormula(
                    w, strike, atmForward, stdDev, annuity, effectiveDisplacement);
        }

        // Additional results: vega, delta, timeToExpiry, impliedVolatility,
        // forwardPrice. Mirrors C++ blackswaptionengine.hpp:320-326.
        final double exerciseTime =
                vol_.currentLink().timeFromReference(exerciseDate);
        final double sqrtT = Math.sqrt(exerciseTime);
        if (volType == VolatilityType.Normal) {
            // Bachelier vega = sqrt(T) * bachelierBlackFormulaStdDevDerivative.
            // Not yet ported in BlackFormula; intentionally omit so Normal-vol
            // callers see a clear "missing key" rather than a wrong number.
            // delta is via bachelierBlackFormulaForwardDerivative — correct.
            results.additionalResults().put("delta",
                    BlackFormula.bachelierBlackFormulaForwardDerivative(
                            w, strike, atmForward, stdDev, annuity));
        } else {
            // Shifted lognormal vega = sqrt(T) * blackFormulaStdDevDerivative.
            final double vega = sqrtT
                    * BlackFormula.blackFormulaStdDevDerivative(
                            strike, atmForward, stdDev, annuity,
                            effectiveDisplacement);
            results.additionalResults().put("vega", vega);
            results.additionalResults().put("delta",
                    BlackFormula.blackFormulaForwardDerivative(
                            w, strike, atmForward, stdDev, annuity,
                            effectiveDisplacement));
        }
        results.additionalResults().put("timeToExpiry", exerciseTime);
        if (exerciseTime > 0.0) {
            results.additionalResults().put("impliedVolatility",
                    stdDev / sqrtT);
        }
        final double discount =
                discountCurve_.currentLink().discount(exerciseDate);
        if (discount > 0.0) {
            results.additionalResults().put("forwardPrice",
                    results.value / discount);
        }
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
