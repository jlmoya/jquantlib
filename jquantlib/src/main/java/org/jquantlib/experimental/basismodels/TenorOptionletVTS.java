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

/*! \file tenoroptionletvts.hpp/.cpp
    \brief caplet volatility term structure based on volatility transformation
*/

package org.jquantlib.experimental.basismodels;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.math.Rounding;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.volatilities.SmileSection;
import org.jquantlib.termstructures.volatilities.optionlet.OptionletVolatilityStructure;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.MakeSchedule;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;

/**
 * Caplet volatility term structure based on volatility transformation.
 * <p>
 * Port of C++ QuantLib v1.42.1
 * {@code ql/experimental/basismodels/tenoroptionletvts.hpp/.cpp}.
 * <p>
 * This class transforms a caplet volatility term structure quoted for a base
 * (short) tenor index into an equivalent structure for a target (long) tenor
 * index. The transformation is based on expressing the longer-tenor FRA rate
 * as a weighted sum of shorter-tenor FRA rates and then propagating the
 * volatilities through a correlation structure.
 * <p>
 * The methodology is designed for <em>normal</em> (Bachelier) volatilities.
 */
public class TenorOptionletVTS extends OptionletVolatilityStructure {

    // -------------------------------------------------------------------------
    // Inner interface and implementations for correlation structure
    // -------------------------------------------------------------------------

    /**
     * Functor interface: returns the correlation between two FRA rates
     * starting at {@code start1} and {@code start2}.
     * <p>
     * Mirrors C++ {@code TenorOptionletVTS::CorrelationStructure}.
     */
    public interface CorrelationStructure {
        double correlation(double start1, double start2);
    }

    /**
     * Two-parameter exponential-decay correlation:
     * <pre>
     *   rho(t1, t2) = rhoInf(t1) + (1 - rhoInf(t1)) * exp(-beta(t1) * |t2 - t1|)
     * </pre>
     * Mirrors C++ {@code TenorOptionletVTS::TwoParameterCorrelation}.
     */
    public static class TwoParameterCorrelation implements CorrelationStructure {

        /** Callable that returns rhoInf as a function of startTime. */
        public interface DoubleFunc {
            double apply(double t);
        }

        private final DoubleFunc rhoInf_;
        private final DoubleFunc beta_;

        /**
         * @param rhoInf function mapping startTime → asymptotic correlation rhoInf
         * @param beta   function mapping startTime → decay rate beta
         */
        public TwoParameterCorrelation(final DoubleFunc rhoInf, final DoubleFunc beta) {
            rhoInf_ = rhoInf;
            beta_   = beta;
        }

        @Override
        public double correlation(final double start1, final double start2) {
            final double rhoInf = rhoInf_.apply(start1);
            final double beta   = beta_.apply(start1);
            return rhoInf + (1.0 - rhoInf) * Math.exp(-beta * Math.abs(start2 - start1));
        }
    }

    // -------------------------------------------------------------------------
    // Protected inner SmileSection
    // -------------------------------------------------------------------------

    /**
     * Smile section that performs the tenor-transformation at a given optionTime.
     * Mirrors C++ {@code TenorOptionletVTS::TenorOptionletSmileSection}.
     */
    protected class TenorOptionletSmileSection extends SmileSection {

        private final CorrelationStructure correlation_;
        private final List<SmileSection> baseSmileSection_ = new ArrayList<>();
        private final List<Double> startTimeBase_ = new ArrayList<>();
        private final List<Double> fraRateBase_   = new ArrayList<>();
        private final double fraRateTarg_;
        private final List<Double> v_             = new ArrayList<>();

        TenorOptionletSmileSection(final TenorOptionletVTS volTS, final double optionTime) {
            // Use Normal vol type with shift 0 — mirrors C++
            super(optionTime, volTS.baseVTS_.currentLink().dayCounter(),
                  VolatilityType.Normal, 0.0);

            correlation_ = volTS.correlation_;

            // Compute exercise date from optionTime using day-fraction rounding
            final double oneDayAsYear = volTS.dayCounter().yearFraction(
                    volTS.referenceDate(), volTS.referenceDate().add(1));
            final long offsetDays = Math.round(optionTime / oneDayAsYear);
            final Date exerciseDate = volTS.referenceDate().add((int) offsetDays);

            final Date effectiveDate = volTS.baseIndex_.fixingCalendar().advance(
                    exerciseDate, volTS.baseIndex_.fixingDays(), TimeUnit.Days);
            final Date maturityDate  = volTS.baseIndex_.fixingCalendar().advance(
                    effectiveDate, volTS.targIndex_.tenor(),
                    BusinessDayConvention.Unadjusted, false);

            // Build short-tenor schedule from effectiveDate to maturityDate
            final Schedule baseFloatSchedule = new MakeSchedule(
                    effectiveDate, maturityDate,
                    volTS.baseIndex_.tenor(),
                    volTS.baseIndex_.fixingCalendar(),
                    BusinessDayConvention.ModifiedFollowing)
                    .backwards()
                    .schedule();

            fraRateTarg_ = volTS.targIndex_.fixing(exerciseDate);
            final double yfTarg = volTS.targIndex_.dayCounter().yearFraction(
                    effectiveDate, maturityDate);

            final List<Date> dates = baseFloatSchedule.dates();
            for (int k = 0; k < dates.size() - 1; k++) {
                final Date startDate  = dates.get(k);
                final Date fixingDate = volTS.baseIndex_.fixingCalendar().advance(
                        startDate,
                        -volTS.baseIndex_.fixingDays(), TimeUnit.Days);
                final double yearFrac = volTS.baseIndex_.dayCounter().yearFraction(
                        dates.get(k), dates.get(k + 1));

                baseSmileSection_.add(volTS.baseVTS_.currentLink().smileSection(fixingDate, true));
                startTimeBase_.add(volTS.dayCounter().yearFraction(
                        volTS.referenceDate(), startDate));

                final double fraBase = volTS.baseIndex_.fixing(fixingDate);
                fraRateBase_.add(fraBase);
                v_.add(yearFrac / yfTarg * (1.0 + yfTarg * fraRateTarg_) /
                       (1.0 + yearFrac * fraBase));
            }
        }

        @Override
        protected double volatilityImpl(final double strike) {
            double sumV = 0.0;
            for (double vk : v_) sumV += vk;

            final double[] volBase = new double[v_.size()];
            for (int k = 0; k < fraRateBase_.size(); k++) {
                final double strikeK = (strike - (fraRateTarg_ - sumV * fraRateBase_.get(k))) / sumV;
                volBase[k] = baseSmileSection_.get(k).volatility(strikeK);
            }

            double var = 0.0;
            for (int i = 0; i < volBase.length; i++) {
                var += v_.get(i) * v_.get(i) * volBase[i] * volBase[i];
                for (int j = i + 1; j < volBase.length; j++) {
                    final double corr = correlation_.correlation(
                            startTimeBase_.get(i), startTimeBase_.get(j));
                    var += 2.0 * corr * v_.get(i) * v_.get(j) * volBase[i] * volBase[j];
                }
            }
            return Math.sqrt(var);
        }

        @Override public double minStrike() {
            return baseSmileSection_.get(0).minStrike() + fraRateTarg_ - fraRateBase_.get(0);
        }

        @Override public double maxStrike() {
            return baseSmileSection_.get(0).maxStrike() + fraRateTarg_ - fraRateBase_.get(0);
        }

        @Override public double atmLevel() { return fraRateTarg_; }
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    protected final Handle<OptionletVolatilityStructure> baseVTS_;
    protected final IborIndex baseIndex_;
    protected final IborIndex targIndex_;
    protected final CorrelationStructure correlation_;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * @param baseVTS     base caplet vol surface (for the short tenor)
     * @param baseIndex   short-tenor ibor index
     * @param targIndex   long-tenor ibor index
     * @param correlation correlation structure between base-tenor FRA rates
     */
    public TenorOptionletVTS(
            final Handle<OptionletVolatilityStructure> baseVTS,
            final IborIndex baseIndex,
            final IborIndex targIndex,
            final CorrelationStructure correlation) {

        super(baseVTS.currentLink().referenceDate(),
              baseVTS.currentLink().calendar(),
              baseVTS.currentLink().businessDayConvention(),
              baseVTS.currentLink().dayCounter());

        QL.require(
                baseIndex.tenor().frequency().toInteger() % targIndex.tenor().frequency().toInteger() == 0,
                "Base index frequency must be a multiple of target tenor frequency");

        baseVTS_     = baseVTS;
        baseIndex_   = baseIndex;
        targIndex_   = targIndex;
        correlation_ = correlation;
    }

    // -------------------------------------------------------------------------
    // OptionletVolatilityStructure interface
    // -------------------------------------------------------------------------

    @Override
    public Date maxDate() {
        return baseVTS_.currentLink().maxDate();
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
    public VolatilityType volatilityType() {
        return VolatilityType.Normal;
    }

    @Override
    protected SmileSection smileSectionImpl(final double optionTime) {
        return new TenorOptionletSmileSection(this, optionTime);
    }

    @Override
    protected double volatilityImpl(final double optionTime, final double strike) {
        // Mirrors C++ {@code volatilityImpl(t, strike) = smileSection(t)->volatility(strike)}.
        // We call smileSectionImpl directly to bypass an extra range check
        // (extrapolation is already enforced at the public {@code volatility(...)}
        // entry point); without this the inner range check rejects the very
        // boundary date for which the outer call was allowed.
        return smileSectionImpl(optionTime).volatility(strike);
    }
}
