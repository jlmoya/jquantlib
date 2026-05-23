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
 Copyright (C) 2020 Marcin Rybacki

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */
package org.jquantlib.pricingengines.swaption;

import org.jquantlib.QL;
import org.jquantlib.cashflow.CashFlows;
import org.jquantlib.cashflow.FixedRateCoupon;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.OvernightIndexedSwap;
import org.jquantlib.instruments.Settlement;
import org.jquantlib.instruments.Swaption;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.InterestRate;
import org.jquantlib.termstructures.SwaptionVolatilityStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.swaption.ConstantSwaptionVolatility;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.calendars.NullCalendar;

/**
 * Generic Black-style swaption engine, parameterised on a
 * {@link BlackStyleSwaptionSpec}.
 * <p>
 * Port of C++ v1.42.1 {@code detail::BlackStyleSwaptionEngine<Spec>}
 * (blackswaptionengine.hpp:53-77). C++ chooses a {@code Spec} at compile
 * time via a template parameter; Java holds it as a {@link BlackStyleSwaptionSpec}
 * sealed field, which yields identical behaviour with one extra (negligible)
 * virtual dispatch per pricing.
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 * <li>The existing {@link BlackSwaptionEngine} and {@link BachelierSwaptionEngine}
 *     classes (ported earlier with a runtime branch on
 *     {@link SwaptionVolatilityStructure#volatilityType()}) are preserved
 *     unchanged. This engine is the new structural port: the C++ class
 *     hierarchy {@code BlackStyleSwaptionEngine<Black76Spec>} &lt;-
 *     {@code BlackSwaptionEngine} maps onto this Java surface via
 *     {@link BlackStyleSwaptionSpec#BLACK76} / {@link BlackStyleSwaptionSpec#BACHELIER}
 *     as the {@code spec} constructor argument. Callers that want the C++
 *     {@code BlackStyleSwaptionEngine<Spec>} polymorphism — for instance from
 *     a test parameterised on {@code Spec} — can use this class directly.</li>
 * <li>C++ exposes both a {@code Handle<Quote>} and a flat-{@code Volatility}
 *     ctor. Java provides the equivalent factory methods
 *     ({@link #withConstantVol}, {@link #withQuoteVol}) since Java's type
 *     erasure makes the two-arg constructors {@code (discountCurve, vol)}
 *     ambiguous between {@code Handle<Quote>} and
 *     {@code Handle<SwaptionVolatilityStructure>}.</li>
 * <li>Additional results published on {@code results.additionalResults}
 *     mirror C++ (spreadCorrection, strike, atmForward, annuity, swapLength,
 *     stdDev, vega, delta, timeToExpiry, impliedVolatility, forwardPrice).
 *     C++ also stores {@code valuationDate}; Java omits this because the
 *     Swap hierarchy has no {@code valuationDate()} accessor yet.</li>
 * <li>OIS underlyings (C++ {@code FixedVsFloatingSwap}) are routed through a
 *     thin internal {@code FixedFloatView} adapter, matching the layout used
 *     by {@link BlackSwaptionEngine}.</li>
 * </ul>
 *
 * @see BlackStyleSwaptionSpec
 * @see BlackSwaptionEngine
 * @see BachelierSwaptionEngine
 */
public class BlackStyleSwaptionEngine extends Swaption.EngineImpl {

    private static final double BASIS_POINT = 1.0e-4;

    private final BlackStyleSwaptionSpec spec_;
    private final Handle< YieldTermStructure > discountCurve_;
    private final Handle< SwaptionVolatilityStructure > vol_;
    private final double displacement_;
    private final BlackSwaptionEngine.CashAnnuityModel model_;

    //
    // public constructors
    //

    /**
     * Full constructor.
     *
     * @param spec          which spec to use for pricing (Black76 vs Bachelier)
     * @param discountCurve discount curve
     * @param vol           swaption volatility surface (must report a
     *                      {@link VolatilityType} consistent with {@code spec})
     * @param model         cash-annuity model (only meaningful for
     *                      {@link Settlement.Method#ParYieldCurve})
     * @param displacement  shifted-lognormal displacement (ignored for
     *                      {@link BlackStyleSwaptionSpec.BachelierSpec})
     */
    public BlackStyleSwaptionEngine(final BlackStyleSwaptionSpec spec,
            final Handle< YieldTermStructure > discountCurve, final Handle< SwaptionVolatilityStructure > vol,
            final BlackSwaptionEngine.CashAnnuityModel model, final double displacement) {
        super();
        QL.require(spec != null, "spec is required");
        this.spec_ = spec;
        this.discountCurve_ = discountCurve;
        this.vol_ = vol;
        this.model_ = model;
        this.displacement_ = displacement;
        this.discountCurve_.addObserver(this);
        this.vol_.addObserver(this);
    }

    /**
     * Build with a flat constant volatility. Wraps {@code vol} in an internal
     * {@link ConstantSwaptionVolatility} on a {@link NullCalendar} with
     * {@link BusinessDayConvention#Following}, matching the C++ defaults.
     */
    public static BlackStyleSwaptionEngine withConstantVol(final BlackStyleSwaptionSpec spec,
            final Handle< YieldTermStructure > discountCurve, final double vol, final DayCounter dc,
            final double displacement, final BlackSwaptionEngine.CashAnnuityModel model) {
        return withQuoteVol(spec, discountCurve, new Handle< Quote >(new SimpleQuote(vol)), dc, displacement, model);
    }

    /**
     * Build with a {@link Quote}-driven volatility (floating market data).
     * Static factory because Java type erasure makes the Quote and surface
     * constructors ambiguous as overloads.
     */
    public static BlackStyleSwaptionEngine withQuoteVol(final BlackStyleSwaptionSpec spec,
            final Handle< YieldTermStructure > discountCurve, final Handle< Quote > vol, final DayCounter dc,
            final double displacement, final BlackSwaptionEngine.CashAnnuityModel model) {
        final Handle< SwaptionVolatilityStructure > wrapped = new Handle< SwaptionVolatilityStructure >(
                new ConstantSwaptionVolatility(0, new NullCalendar(), BusinessDayConvention.Following, vol,
                        (dc != null) ? dc : new Actual365Fixed(), spec.type(), displacement));
        return new BlackStyleSwaptionEngine(spec, discountCurve, wrapped, model, displacement);
    }

    //
    // public inspectors
    //

    public Handle< YieldTermStructure > termStructure() {
        return discountCurve_;
    }

    public Handle< SwaptionVolatilityStructure > volatility() {
        return vol_;
    }

    public BlackStyleSwaptionSpec spec() {
        return spec_;
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

        final FixedFloatView swap = buildView(args);
        swap.setPricingEngine(new DiscountingSwapEngine(discountCurve_));

        double strike = swap.fixedRate();
        double atmForward = swap.fairRate();

        // Spread correction: zero-spread quotes.
        final double spread = swap.spread();
        if ( spread != 0.0 ) {
            final double correction = spread * Math.abs(swap.floatingLegBPS() / swap.fixedLegBPS());
            strike -= correction;
            atmForward -= correction;
            results.additionalResults().put("spreadCorrection", correction);
        } else {
            results.additionalResults().put("spreadCorrection", 0.0);
        }
        results.additionalResults().put("strike", strike);
        results.additionalResults().put("atmForward", atmForward);

        // Annuity per settlement type / method.
        final double annuity;
        if ( args.settlementType == Settlement.Type.Physical || (args.settlementType == Settlement.Type.Cash
                && args.settlementMethod == Settlement.Method.CollateralizedCashPrice) ) {
            annuity = Math.abs(swap.fixedLegBPS()) / BASIS_POINT;
        } else if ( args.settlementType == Settlement.Type.Cash
                && args.settlementMethod == Settlement.Method.ParYieldCurve ) {
            final Leg fixedLeg = swap.fixedLeg();
            final FixedRateCoupon firstCoupon = (FixedRateCoupon) fixedLeg.get(0);
            final DayCounter dayCount = firstCoupon.dayCounter();
            final Date discountDate = (model_ == BlackSwaptionEngine.CashAnnuityModel.DiscountCurve)
                    ? firstCoupon.accrualStartDate()
                    : discountCurve_.currentLink().referenceDate();
            Frequency freq = Frequency.Annual;
            final Schedule fixedSchedule = swap.fixedSchedule();
            if ( fixedSchedule.hasTenor() ) {
                freq = fixedSchedule.tenor().frequency();
            }
            final InterestRate ir = new InterestRate(atmForward, dayCount, Compounding.Compounded, freq);
            final double fixedLegCashBPS = CashFlows.getInstance().bps(fixedLeg, ir, discountDate);
            annuity = Math.abs(fixedLegCashBPS / BASIS_POINT) * discountCurve_.currentLink().discount(discountDate);
        } else {
            throw new IllegalStateException(
                    "invalid (settlementType, settlementMethod) pair: " + args.settlementType + " / "
                            + args.settlementMethod);
        }
        results.additionalResults().put("annuity", annuity);

        // swapLength: (end-start)/365.25*12 rounded to whole months, /12.
        final java.util.List< Date > floatDates = swap.floatingSchedule().dates();
        final double swapLengthRaw = computeSwapLength(floatDates.get(0), floatDates.get(floatDates.size() - 1));
        final double swapLength = Math.max(swapLengthRaw, 1.0 / 12.0);
        results.additionalResults().put("swapLength", swapLength);

        final double variance = vol_.currentLink()
                .blackVariance(vol_.currentLink().timeFromReference(exerciseDate), swapLength, strike, true);
        final double stdDev = Math.sqrt(variance);
        results.additionalResults().put("stdDev", stdDev);

        // Effective displacement: shifted-lognormal pulls from vol surface
        // when available; Bachelier ignores it entirely (spec.value drops the
        // arg).
        final double effectiveDisplacement;
        if ( spec_.type() == VolatilityType.ShiftedLognormal ) {
            final double volShift = vol_.currentLink().shift();
            effectiveDisplacement = (volShift != 0.0) ? volShift : displacement_;
        } else {
            effectiveDisplacement = 0.0;
        }

        final Option.Type w = (swap.type() == VanillaSwap.Type.Payer) ? Option.Type.Call : Option.Type.Put;
        results.value = spec_.value(w, strike, atmForward, stdDev, annuity, effectiveDisplacement);

        final double exerciseTime = vol_.currentLink().timeFromReference(exerciseDate);
        results.additionalResults().put("vega",
                spec_.vega(strike, atmForward, stdDev, exerciseTime, annuity, effectiveDisplacement));
        results.additionalResults().put("delta",
                spec_.delta(w, strike, atmForward, stdDev, annuity, effectiveDisplacement));
        results.additionalResults().put("timeToExpiry", exerciseTime);
        if ( exerciseTime > 0.0 ) {
            results.additionalResults().put("impliedVolatility", stdDev / Math.sqrt(exerciseTime));
        }
        final double discount = discountCurve_.currentLink().discount(exerciseDate);
        if ( discount > 0.0 ) {
            results.additionalResults().put("forwardPrice", results.value / discount);
        }
    }

    //
    // private helpers
    //

    private static double computeSwapLength(final Date start, final Date end) {
        QL.require(end.gt(start), "swap end date must be greater than start");
        final double months = (end.serialNumber() - start.serialNumber()) / 365.25 * 12.0;
        final double monthsRounded = Math.floor(months + 0.5);
        return monthsRounded / 12.0;
    }

    private static FixedFloatView buildView(final Swaption.ArgumentsImpl args) {
        if ( args.swap != null ) {
            return new VanillaView(args.swap);
        }
        QL.require(args.ois != null, "swap not set");
        return new OisView(args.ois);
    }

    //
    // FixedFloatView adapter — mirrors C++ FixedVsFloatingSwap.
    //

    private interface FixedFloatView {
        double fixedRate();

        double fairRate();

        double spread();

        double fixedLegBPS();

        double floatingLegBPS();

        Leg fixedLeg();

        Schedule fixedSchedule();

        Schedule floatingSchedule();

        VanillaSwap.Type type();

        void setPricingEngine(DiscountingSwapEngine engine);
    }

    private static final class VanillaView implements FixedFloatView {
        private final VanillaSwap s;

        VanillaView(final VanillaSwap s) {
            this.s = s;
        }

        @Override
        public double fixedRate() {
            return s.fixedRate();
        }

        @Override
        public double fairRate() {
            return s.fairRate();
        }

        @Override
        public double spread() {
            return s.spread();
        }

        @Override
        public double fixedLegBPS() {
            return s.fixedLegBPS();
        }

        @Override
        public double floatingLegBPS() {
            return s.floatingLegBPS();
        }

        @Override
        public Leg fixedLeg() {
            return s.fixedLeg();
        }

        @Override
        public Schedule fixedSchedule() {
            return s.fixedSchedule();
        }

        @Override
        public Schedule floatingSchedule() {
            return s.floatingSchedule();
        }

        @Override
        public VanillaSwap.Type type() {
            return s.type();
        }

        @Override
        public void setPricingEngine(final DiscountingSwapEngine engine) {
            s.setPricingEngine(engine);
        }
    }

    private static final class OisView implements FixedFloatView {
        private final OvernightIndexedSwap s;

        OisView(final OvernightIndexedSwap s) {
            this.s = s;
        }

        @Override
        public double fixedRate() {
            return s.fixedRate();
        }

        @Override
        public double fairRate() {
            return s.fairRate();
        }

        @Override
        public double spread() {
            return s.spread();
        }

        @Override
        public double fixedLegBPS() {
            return s.fixedLegBPS();
        }

        @Override
        public double floatingLegBPS() {
            return s.overnightLegBPS();
        }

        @Override
        public Leg fixedLeg() {
            return s.fixedLeg();
        }

        @Override
        public Schedule fixedSchedule() {
            return s.fixedSchedule();
        }

        @Override
        public Schedule floatingSchedule() {
            return s.overnightSchedule();
        }

        @Override
        public VanillaSwap.Type type() {
            return s.type();
        }

        @Override
        public void setPricingEngine(final DiscountingSwapEngine engine) {
            s.setPricingEngine(engine);
        }
    }
}
