/*
 Copyright (C) 2026 JQuantLib migration

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
 Copyright (C) 2015 Jose Aparicio
*/

package org.jquantlib.experimental.credit;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.lang.annotation.Time;
import org.jquantlib.model.shortrate.onefactormodels.OneFactorAffineModel;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.credit.HazardRateStructure;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;

import java.util.Collections;
import java.util.List;

/**
 * Survival probability term structure based on a one-factor stochastic model of the default intensity.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code QuantLib::OneFactorAffineSurvivalStructure} ({@code ql/experimental/credit/onefactoraffinesurvival.hpp}).
 *
 * <p>The hazard-rate-structure inherited path is for the deterministic
 * component of the model; the survival probabilities, default densities, and conditional / forward survivals depend on
 * the affine model and are computed via {@link OneFactorAffineModel#discountBond(double, double, double)}.
 *
 * <p>Phase 4m.5 work-item 11.
 */
public class OneFactorAffineSurvivalStructure extends HazardRateStructure {

    protected final OneFactorAffineModel model;

    public OneFactorAffineSurvivalStructure(final OneFactorAffineModel model, final DayCounter dayCounter,
            final List< Handle< Quote > > jumps, final List< Date > jumpDates) {
        super(dayCounter, jumps, jumpDates);
        this.model = model;
    }

    public OneFactorAffineSurvivalStructure(final OneFactorAffineModel model, final DayCounter dayCounter) {
        this(model, dayCounter, Collections.emptyList(), Collections.emptyList());
    }

    public OneFactorAffineSurvivalStructure(final OneFactorAffineModel model, final Date referenceDate,
            final Calendar cal, final DayCounter dayCounter, final List< Handle< Quote > > jumps,
            final List< Date > jumpDates) {
        super(referenceDate, cal, dayCounter, jumps, jumpDates);
        this.model = model;
    }

    public OneFactorAffineSurvivalStructure(final OneFactorAffineModel model, final int settlementDays,
            final Calendar calendar, final DayCounter dayCounter, final List< Handle< Quote > > jumps,
            final List< Date > jumpDates) {
        super(settlementDays, calendar, dayCounter, jumps, jumpDates);
        this.model = model;
    }

    @Override
    public Date maxDate() {
        return Date.maxDate();
    }

    /**
     * Survival probability at a future time {@code dTgt}, conditional to survival at a prior time {@code dFwd} and to
     * the realisation of a particular hazard-rate value at {@code dFwd}. Mirrors C++
     * {@code conditionalSurvivalProbability}.
     */
    public double conditionalSurvivalProbability(final Date dFwd, final Date dTgt, final double yVal,
            final boolean extrapolate) {
        return conditionalSurvivalProbability(timeFromReference(dFwd), timeFromReference(dTgt), yVal, extrapolate);
    }

    public double conditionalSurvivalProbability(final @Time double tFwd, final @Time double tgt, final double yVal,
            final boolean extrapolate) {
        QL.require(tgt >= tFwd, "Incorrect dates ordering.");
        checkRange(tFwd, extrapolate);
        checkRange(tgt, extrapolate);
        return conditionalSurvivalProbabilityImpl(tFwd, tgt, yVal);
    }

    /** Default-term-structure interface — hazard rate (deterministic component only). */
    public double hazardRate(final @Time double t, final boolean extrapolate) {
        checkRange(t, extrapolate);
        return hazardRateImpl(t);
    }

    @Override
    protected double survivalProbabilityImpl(final @Time double t) {
        final double initValHR = model.dynamics().shortRate(0.0, model.dynamics().process().x0());
        return model.discountBond(0.0, t, initValHR);
    }

    @Override
    protected double defaultDensityImpl(final @Time double t) {
        final double initValHR = model.dynamics().shortRate(0.0, model.dynamics().process().x0());
        return hazardRateImpl(t) * survivalProbabilityImpl(t) / model.discountBond(0.0, t, initValHR);
    }

    /** Conditional survival via {@code model.discountBond(tFwd, tgt, yVal)}. */
    protected double conditionalSurvivalProbabilityImpl(final @Time double tFwd, final @Time double tgt,
            final double yVal) {
        return model.discountBond(tFwd, tgt, yVal);
    }

    /** No deterministic component by default; subclasses may override. */
    @Override
    protected double hazardRateImpl(final @Time double t) {
        return 0.0;
    }
}
