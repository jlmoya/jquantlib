/*
 Copyright (C) 2008 Roland Lichters
 Copyright (C) 2026 JQuantLib migration contributors.

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

package org.jquantlib.experimental.credit;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.DefaultProbabilityTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.credit.DefaultProbabilityHelper;
import org.jquantlib.time.*;

/**
 * Risky-asset-swap helper for default-probability curve bootstrap.
 *
 * <p>Java port of QuantLib v1.42.1 {@code QuantLib::AssetSwapHelper}
 * ({@code ql/experimental/credit/riskyassetswap.{hpp,cpp}}).
 *
 * <p>Phase 4m.6.
 */
public class AssetSwapHelper extends DefaultProbabilityHelper {

    private final Period tenor_;
    private final int settlementDays_;
    private final Calendar calendar_;
    private final BusinessDayConvention fixedConvention_;
    private final Period fixedPeriod_;
    private final DayCounter fixedDayCount_;
    private final BusinessDayConvention floatConvention_;
    private final Period floatPeriod_;
    private final DayCounter floatDayCount_;
    private final double recoveryRate_;
    private final Handle< YieldTermStructure > yieldTS_;
    private final Period integrationStepSize_;

    private Date evaluationDate_;
    private RiskyAssetSwap asw_;
    private Handle< DefaultProbabilityTermStructure > probability_;

    public AssetSwapHelper(final Handle< Quote > spread, final Period tenor, final int settlementDays,
            final Calendar calendar, final Period fixedPeriod, final BusinessDayConvention fixedConvention,
            final DayCounter fixedDayCount, final Period floatPeriod, final BusinessDayConvention floatConvention,
            final DayCounter floatDayCount, final double recoveryRate, final Handle< YieldTermStructure > yieldTS) {
        this(spread, tenor, settlementDays, calendar, fixedPeriod, fixedConvention, fixedDayCount, floatPeriod,
                floatConvention, floatDayCount, recoveryRate, yieldTS, new Period(0, TimeUnit.Days));
    }

    public AssetSwapHelper(final Handle< Quote > spread, final Period tenor, final int settlementDays,
            final Calendar calendar, final Period fixedPeriod, final BusinessDayConvention fixedConvention,
            final DayCounter fixedDayCount, final Period floatPeriod, final BusinessDayConvention floatConvention,
            final DayCounter floatDayCount, final double recoveryRate, final Handle< YieldTermStructure > yieldTS,
            final Period integrationStepSize) {
        super(spread);
        this.tenor_ = tenor;
        this.settlementDays_ = settlementDays;
        this.calendar_ = calendar;
        this.fixedConvention_ = fixedConvention;
        this.fixedPeriod_ = fixedPeriod;
        this.fixedDayCount_ = fixedDayCount;
        this.floatConvention_ = floatConvention;
        this.floatPeriod_ = floatPeriod;
        this.floatDayCount_ = floatDayCount;
        this.recoveryRate_ = recoveryRate;
        this.yieldTS_ = yieldTS;
        this.integrationStepSize_ = integrationStepSize;

        initializeDates();

        // C++ registers Settings::evaluationDate (handled implicitly by
        // BootstrapHelper.update) and the yield term structure.
        yieldTS.addObserver(this);
    }

    @Override
    public double impliedQuote() {
        QL.require(probability_ != null && !probability_.empty(), "default term structure not set");
        // C++: asw_->recalculate(); return asw_->fairSpread();
        // Java RiskyAssetSwap follows the LazyObject pattern.
        asw_.recalculate();
        return asw_.fairSpread();
    }

    @Override
    public void setTermStructure(final DefaultProbabilityTermStructure ts) {
        super.setTermStructure(ts);
        this.probability_ = new Handle< DefaultProbabilityTermStructure >(ts);
        initializeDates();
    }

    @Override
    public void update() {
        if ( evaluationDate_ == null || !evaluationDate_.eq(new Settings().evaluationDate()) ) {
            initializeDates();
        }
        super.update();
    }

    private void initializeDates() {
        evaluationDate_ = new Settings().evaluationDate();

        earliestDate = calendar_.advance(evaluationDate_, settlementDays_, TimeUnit.Days);

        final Date maturity = earliestDate.add(tenor_);

        latestDate = calendar_.adjust(maturity, fixedConvention_);

        final Schedule fixedSchedule = new Schedule(earliestDate, maturity, fixedPeriod_, calendar_, fixedConvention_,
                fixedConvention_, DateGeneration.Rule.Forward, false);
        final Schedule floatSchedule = new Schedule(earliestDate, maturity, floatPeriod_, calendar_, floatConvention_,
                floatConvention_, DateGeneration.Rule.Forward, false);

        // Probability handle may not have been linked yet (constructor pass);
        // RiskyAssetSwap constructor still requires a non-null Handle. Use an
        // empty Handle until setTermStructure binds the real curve.
        final Handle< DefaultProbabilityTermStructure > prob = (probability_ != null)
                ? probability_
                : new Handle< DefaultProbabilityTermStructure >();

        asw_ = new RiskyAssetSwap(true, 100.0, fixedSchedule, floatSchedule, fixedDayCount_, floatDayCount_, 0.01,
                recoveryRate_, yieldTS_, prob);
    }

    /** Inspector for the integration-step-size (declared in C++ but unused). */
    public Period integrationStepSize() {
        return integrationStepSize_;
    }
}
