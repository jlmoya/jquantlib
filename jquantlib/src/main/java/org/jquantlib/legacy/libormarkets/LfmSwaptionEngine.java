/*
 Copyright (C) 2006 Klaus Spanderen (C++ original).
 Copyright (C) 2026 JQuantLib migration contributors (Java port).

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
*/

package org.jquantlib.legacy.libormarkets;

import org.jquantlib.QL;
import org.jquantlib.cashflow.FixedRateCoupon;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.*;
import org.jquantlib.pricingengines.BlackFormula;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.swaption.SwaptionVolatilityMatrix;
import org.jquantlib.time.Date;

/**
 * Libor forward model swaption engine — Black formula on a Rebonato-derived swaption-volatility surface.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code legacy/libormarketmodels/lfmswaptionengine.{hpp,cpp}}.
 *
 * <p>In C++ this is {@code GenericModelEngine<LiborForwardModel,
 * Swaption::arguments, Swaption::results>}. Java's {@link org.jquantlib.pricingengines.GenericModelEngine}
 * parameterises on a {@link org.jquantlib.model.CalibratedModel} but expects {@link Instrument.Arguments} /
 * {@link Instrument.Results} types. The Java {@link Swaption.EngineImpl} provides the right base for the
 * {@code GenericEngine} side; we hold the model directly here.
 */
public class LfmSwaptionEngine extends Swaption.EngineImpl {

    private final LiborForwardModel model_;
    private final Handle< YieldTermStructure > discountCurve_;

    public LfmSwaptionEngine(final LiborForwardModel model, final Handle< YieldTermStructure > discountCurve) {
        super();
        this.model_ = model;
        this.discountCurve_ = discountCurve;
        if ( this.model_ != null ) {
            this.model_.addObserver(this);
        }
        if ( this.discountCurve_ != null ) {
            this.discountCurve_.addObserver(this);
        }
    }

    @Override
    public void calculate() {
        final Swaption.ArgumentsImpl arguments = (Swaption.ArgumentsImpl) this.arguments_;
        final Instrument.ResultsImpl results = (Instrument.ResultsImpl) this.results_;

        QL.require(arguments.settlementMethod != Settlement.Method.ParYieldCurve,
                "cash settled (ParYieldCurve) swaptions not priced with Lfm engine");

        final double basisPoint = 1.0e-4;

        // Mirror C++ swap->setPricingEngine + spread/leg-BPS read-out.
        final VanillaSwap swap = arguments.swap;
        swap.setPricingEngine(new DiscountingSwapEngine(discountCurve_));

        final double correction = swap.spread() * Math.abs(swap.floatingLegBPS() / swap.fixedLegBPS());
        final double fixedRate = swap.fixedRate() - correction;
        final double fairRate = swap.fairRate() - correction;

        final SwaptionVolatilityMatrix volatility = model_.getSwaptionVolatilityMatrix();

        final Date referenceDate = volatility.referenceDate();
        final DayCounter dayCounter = volatility.dayCounter();

        // C++ reads arguments_.exercise->date(0) and arguments_.fixedPayDates.
        // The Java Swaption.ArgumentsImpl carries the underlying swap reference
        // (`arguments.swap`) plus the exercise; we read fixed-leg dates off
        // the swap's fixed leg directly to mirror the C++ behaviour.
        final Date exerciseDate = arguments.exercise.date(0);
        final Leg fixedLeg = swap.fixedLeg();
        QL.require(fixedLeg != null && !fixedLeg.isEmpty(), "swap with empty fixed leg passed to LfmSwaptionEngine");

        final Date lastFixedPayDate = fixedLeg.get(fixedLeg.size() - 1).date();
        final Date firstFixedResetDate = ((FixedRateCoupon) fixedLeg.get(0)).accrualStartDate();

        final double exercise = dayCounter.yearFraction(referenceDate, exerciseDate);
        final double swapLength =
                dayCounter.yearFraction(referenceDate, lastFixedPayDate) - dayCounter.yearFraction(referenceDate,
                        firstFixedResetDate);

        final Option.Type w = (swap.type() == VanillaSwap.Type.Payer) ? Option.Type.Call : Option.Type.Put;
        // Swaption-vol surface volatility(time, swapLength, strike, extrap).
        final double vol = volatility.volatility(exercise, swapLength, fairRate, true);

        results.value = (swap.fixedLegBPS() / basisPoint) * BlackFormula.blackFormula(w, fixedRate, fairRate,
                vol * Math.sqrt(exercise));
    }
}
