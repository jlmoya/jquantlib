/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2018 Sebastian Schlenkrich

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <http://www.quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
*/

/*! \file tenorswaptionvts.hpp/.cpp
    \brief swaption volatility term structure based on volatility transformation
*/

package org.jquantlib.experimental.basismodels;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.instruments.Swaption;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.SwaptionVolatilityStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.SmileSection;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.MakeSchedule;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;

/**
 * Swaption volatility term structure based on volatility transformation.
 * <p>
 * Port of C++ QuantLib v1.42.1
 * {@code ql/experimental/basismodels/tenorswaptionvts.hpp/.cpp}.
 * <p>
 * This class transforms a swaption vol surface quoted for a base (short) tenor
 * index into an equivalent surface for a target (long) tenor index using an
 * affine Terminal Swap Rate (TSR) mapping.
 * <p>
 * The methodology is designed for <em>normal</em> (Bachelier) volatilities.
 */
public class TenorSwaptionVTS extends SwaptionVolatilityStructure {

    // -------------------------------------------------------------------------
    // Inner smile section
    // -------------------------------------------------------------------------

    /**
     * Smile section implementing the tenor-transformation at given
     * {@code optionTime} and {@code swapLength}.
     * Mirrors C++ {@code TenorSwaptionVTS::TenorSwaptionSmileSection}.
     */
    protected class TenorSwaptionSmileSection extends SmileSection {

        private final SmileSection baseSmileSection_;
        private final double swapRateBase_;
        private final double swapRateTarg_;
        private final double swapRateFinl_;
        private final double lambda_;
        private final double annuityScaling_;

        TenorSwaptionSmileSection(
                final TenorSwaptionVTS volTS,
                final double optionTime,
                final double swapLength) {

            super(optionTime, volTS.baseVTS_.currentLink().dayCounter(),
                  VolatilityType.Normal, 0.0);

            // Compute exercise date from optionTime
            final double oneDayAsYear = volTS.dayCounter().yearFraction(
                    volTS.referenceDate(), volTS.referenceDate().add(1));
            final long offsetDays = Math.round(optionTime / oneDayAsYear);
            final Date exerciseDate = volTS.referenceDate().add((int) offsetDays);

            // Obtain the base smile section using the Date/Period public API.
            // swapLength (in years) → approximate swap-tenor Period in months
            final int swapMonths = (int) Math.round(swapLength * 12.0);
            final Period swapTenorPeriod = new Period(swapMonths, TimeUnit.Months);
            baseSmileSection_ = volTS.baseVTS_.currentLink()
                    .smileSection(exerciseDate, swapTenorPeriod, true);

            final Date effectiveDate = volTS.baseIndex_.fixingCalendar().advance(
                    exerciseDate, volTS.baseIndex_.fixingDays(), TimeUnit.Days);

            // Maturity date: swapLength in years → months
            final int months = (int) Math.round(swapLength * 12.0);
            final Date maturityDate = volTS.baseIndex_.fixingCalendar().advance(
                    effectiveDate,
                    new Period(months, TimeUnit.Months),
                    BusinessDayConvention.Unadjusted, false);

            // Build schedules
            final Schedule baseFixedSchedule = new MakeSchedule(
                    effectiveDate, maturityDate,
                    volTS.baseFixedFreq_,
                    volTS.baseIndex_.fixingCalendar(),
                    BusinessDayConvention.ModifiedFollowing)
                    .backwards()
                    .schedule();

            final Schedule finlFixedSchedule = new MakeSchedule(
                    effectiveDate, maturityDate,
                    volTS.targFixedFreq_,
                    volTS.targIndex_.fixingCalendar(),
                    BusinessDayConvention.ModifiedFollowing)
                    .backwards()
                    .schedule();

            final Schedule baseFloatSchedule = new MakeSchedule(
                    effectiveDate, maturityDate,
                    volTS.baseIndex_.tenor(),
                    volTS.baseIndex_.fixingCalendar(),
                    BusinessDayConvention.ModifiedFollowing)
                    .backwards()
                    .schedule();

            final Schedule targFloatSchedule = new MakeSchedule(
                    effectiveDate, maturityDate,
                    volTS.targIndex_.tenor(),
                    volTS.baseIndex_.fixingCalendar(),
                    BusinessDayConvention.ModifiedFollowing)
                    .backwards()
                    .schedule();

            // Build swaps
            final VanillaSwap baseSwap = new VanillaSwap(
                    VanillaSwap.Type.Payer, 1.0,
                    baseFixedSchedule, 1.0, volTS.baseFixedDC_,
                    baseFloatSchedule, volTS.baseIndex_, 0.0,
                    volTS.baseIndex_.dayCounter());

            final VanillaSwap targSwap = new VanillaSwap(
                    VanillaSwap.Type.Payer, 1.0,
                    baseFixedSchedule, 1.0, volTS.baseFixedDC_,
                    targFloatSchedule, volTS.targIndex_, 0.0,
                    volTS.targIndex_.dayCounter());

            final VanillaSwap finlSwap = new VanillaSwap(
                    VanillaSwap.Type.Payer, 1.0,
                    finlFixedSchedule, 1.0, volTS.targFixedDC_,
                    targFloatSchedule, volTS.targIndex_, 0.0,
                    volTS.targIndex_.dayCounter());

            // Set pricing engines
            final PricingEngine engine = new DiscountingSwapEngine(volTS.discountCurve_);
            baseSwap.setPricingEngine(engine);
            targSwap.setPricingEngine(engine);
            finlSwap.setPricingEngine(engine);

            // Compute swap rates
            swapRateBase_ = baseSwap.fairRate();
            swapRateTarg_ = targSwap.fairRate();
            swapRateFinl_ = finlSwap.fairRate();

            // Build swaption cash flows for affine TSR model
            final SwaptionCashFlows cfs = new SwaptionCashFlows();
            cfs.initSwap(baseSwap, volTS.discountCurve_, true);

            final SwaptionCashFlows cf2 = new SwaptionCashFlows();
            cf2.initSwap(targSwap, volTS.discountCurve_, true);

            // Sum tau_j (fixed leg annuity weights)
            double sumTauj = 0.0;
            for (double w : cfs.annuityWeights()) sumTauj += w;

            // Sum tau_j * (T_N - T_j)
            double sumTaujDeltaT = 0.0;
            final java.util.List<Double> fw = cfs.fixedTimes();
            final java.util.List<Double> aw = cfs.annuityWeights();
            for (int k = 0; k < aw.size(); k++) {
                sumTaujDeltaT += aw.get(k) * (fw.get(fw.size() - 1) - fw.get(k));
            }

            // Sum w_i (float leg weights)
            double sumWi = 0.0;
            for (double w : cfs.floatWeights()) sumWi += w;

            // Sum w_i * (T_N - T_i)
            double sumWiDeltaT = 0.0;
            final java.util.List<Double> flt = cfs.floatTimes();
            final java.util.List<Double> flw = cfs.floatWeights();
            for (int k = 0; k < flw.size(); k++) {
                sumWiDeltaT += flw.get(k) * (flt.get(flt.size() - 1) - flt.get(k));
            }

            // Affine TSR parameters
            final double den = sumTaujDeltaT * sumWi - sumWiDeltaT * sumTauj;
            final double u = -sumTauj / den;
            final double v = sumTaujDeltaT / den;

            final double T_N = fw.get(fw.size() - 1);

            // Compute lambda_ as difference of sums (skip first and last float weights)
            double sumBase = 0.0;
            for (int k = 1; k < flw.size() - 1; k++) {
                sumBase += flw.get(k) * (u * (T_N - flt.get(k)) + v);
            }

            final java.util.List<Double> flt2 = cf2.floatTimes();
            final java.util.List<Double> flw2 = cf2.floatWeights();
            double sumTarg = 0.0;
            for (int k = 1; k < flw2.size() - 1; k++) {
                sumTarg += flw2.get(k) * (u * (T_N - flt2.get(k)) + v);
            }

            lambda_ = sumTarg - sumBase;

            // Annuity scaling
            annuityScaling_ = targSwap.fixedLegBPS() / finlSwap.fixedLegBPS();
        }

        @Override
        protected double volatilityImpl(final double strike) {
            final double strikeBase = (strike - (swapRateTarg_ - (1.0 + lambda_) * swapRateBase_)) /
                                     (1.0 + lambda_) / annuityScaling_;
            final double volBase = baseSmileSection_.volatility(strikeBase);
            return annuityScaling_ * (1.0 + lambda_) * volBase;
        }

        @Override public double minStrike() {
            return baseSmileSection_.minStrike() + swapRateTarg_ - swapRateBase_;
        }

        @Override public double maxStrike() {
            return baseSmileSection_.maxStrike() + swapRateTarg_ - swapRateBase_;
        }

        @Override public double atmLevel() { return swapRateFinl_; }
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    protected final Handle<SwaptionVolatilityStructure> baseVTS_;
    protected final Handle<YieldTermStructure> discountCurve_;

    protected final IborIndex baseIndex_;
    protected final IborIndex targIndex_;
    protected final Period baseFixedFreq_;
    protected final Period targFixedFreq_;
    protected final DayCounter baseFixedDC_;
    protected final DayCounter targFixedDC_;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * @param baseVTS        base swaption vol surface for the short tenor
     * @param discountCurve  discount curve used to compute swap rates
     * @param baseIndex      short-tenor ibor index
     * @param targIndex      long-tenor ibor index
     * @param baseFixedFreq  fixed-leg payment frequency for the base (short) tenor
     * @param targFixedFreq  fixed-leg payment frequency for the target (long) tenor
     * @param baseFixedDC    fixed-leg day counter for the base (short) tenor
     * @param targFixedDC    fixed-leg day counter for the target (long) tenor
     */
    public TenorSwaptionVTS(
            final Handle<SwaptionVolatilityStructure> baseVTS,
            final Handle<YieldTermStructure> discountCurve,
            final IborIndex baseIndex,
            final IborIndex targIndex,
            final Period baseFixedFreq,
            final Period targFixedFreq,
            final DayCounter baseFixedDC,
            final DayCounter targFixedDC) {

        super(baseVTS.currentLink().referenceDate(),
              baseVTS.currentLink().calendar(),
              baseVTS.currentLink().dayCounter(),
              baseVTS.currentLink().businessDayConvention());

        baseVTS_      = baseVTS;
        discountCurve_ = discountCurve;
        baseIndex_    = baseIndex;
        targIndex_    = targIndex;
        baseFixedFreq_ = baseFixedFreq;
        targFixedFreq_ = targFixedFreq;
        baseFixedDC_  = baseFixedDC;
        targFixedDC_  = targFixedDC;
    }

    // -------------------------------------------------------------------------
    // SwaptionVolatilityStructure interface
    // -------------------------------------------------------------------------

    @Override
    public Date maxDate() {
        return baseVTS_.currentLink().maxDate();
    }

    @Override
    public Period maxSwapTenor() {
        return baseVTS_.currentLink().maxSwapTenor();
    }

    @Override
    public double minStrike() {
        return baseVTS_.currentLink().minStrike();
    }

    @Override
    public double maxStrike() {
        return baseVTS_.currentLink().maxStrike();
    }

    @Override
    public BusinessDayConvention businessDayConvention() {
        return baseVTS_.currentLink().businessDayConvention();
    }

    @Override
    public VolatilityType volatilityType() {
        return VolatilityType.Normal;
    }

    @Override
    protected SmileSection smileSectionImpl(final double optionTime, final double swapLength) {
        return new TenorSwaptionSmileSection(this, optionTime, swapLength);
    }

    @Override
    protected SmileSection smileSectionImpl(final Date optionDate, final Period swapTenor) {
        final double optionTime = timeFromReference(optionDate);
        final double swapLength = dayCounter().yearFraction(
                optionDate, optionDate.add(swapTenor));
        return smileSectionImpl(optionTime, swapLength);
    }

    @Override
    public double volatilityImpl(final double optionTime, final double swapLength, final double strike) {
        return smileSectionImpl(optionTime, swapLength).volatility(strike);
    }

    @Override
    public double blackVariance(final double optionTime, final double swapLength,
                                final double strike, final boolean extrapolate) {
        final double v = volatilityImpl(optionTime, swapLength, strike);
        return v * v * optionTime;
    }
}
