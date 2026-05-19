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
 Copyright (C) 2008, 2009 Jose Aparicio
 Copyright (C) 2008 Chris Kenyon
 Copyright (C) 2008 Roland Lichters
 Copyright (C) 2008 StatPro Italia srl
  Copyright (C) 2023 Andrea Pellegatta

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.termstructures.credit;

import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.CreditDefaultSwap;
import org.jquantlib.instruments.CreditDefaultSwap.PricingModel;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.termstructures.DefaultProbabilityTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.*;

/**
 * Base class for CDS helpers — Java port of QuantLib v1.42.1 {@code QuantLib::CdsHelper}
 * ({@code ql/termstructures/credit/defaultprobabilityhelpers.hpp:48-128},
 * {@code defaultprobabilityhelpers.cpp:32-108}).
 *
 * <p>Subclasses ({@link SpreadCdsHelper}, {@link UpfrontCdsHelper}) build a
 * transient {@link CreditDefaultSwap} on every bootstrap iteration and use the engine-derived fair spread / fair
 * upfront as the implied quote.
 *
 * <p><b>DateGeneration limitation.</b> The C++ class branches on the
 * {@code DateGeneration::CDS} / {@code CDS2015} / {@code OldCDS} rules and the {@code cdsMaturity} helper to compute a
 * Big-Bang-aware end date. The Java {@link DateGeneration} enum only ships the pre-Big-Bang rules ({@code TwentiethIMM}
 * is the closest analog); the post-Big-Bang branch is therefore not exercised, and the helper falls through to the C++
 * "old logic" branch — {@code endDate = startDate + tenor} — for every supported Java rule. This matches the behaviour
 * required by all Phase 3a / 3b CDS tests; full post-Big-Bang support is deferred to Phase 3c when the
 * {@code IsdaCdsEngine} arrives. See class-level Javadoc on {@link CreditDefaultSwap} for the symmetric note.
 *
 * <p><b>Pricing-model selector.</b> Only
 * {@link PricingModel#Midpoint} is wired in this phase ({@link org.jquantlib.pricingengines.credit.MidPointCdsEngine});
 * {@link PricingModel#ISDA} routes to subclasses' {@code resetEngine} and therefore throws
 * {@link UnsupportedOperationException} until the {@code IsdaCdsEngine} ports in Phase 3c.
 *
 * @category termstructures.credit
 */
public abstract class CdsHelper extends RelativeDateDefaultProbabilityHelper {

    //
    // protected fields — mirror C++ defaultprobabilityhelpers.hpp:107-127
    //

    protected final Period tenor_;
    protected final int settlementDays_;
    protected final Calendar calendar_;
    protected final Frequency frequency_;
    protected final BusinessDayConvention paymentConvention_;
    protected final DateGeneration.Rule rule_;
    protected final DayCounter dayCounter_;
    protected final double recoveryRate_;
    protected final Handle< YieldTermStructure > discountCurve_;
    protected final boolean settlesAccrual_;
    protected final boolean paysAtDefaultTime_;
    protected final DayCounter lastPeriodDC_;
    protected final boolean rebatesAccrual_;
    protected final PricingModel model_;
    protected final RelinkableHandle< DefaultProbabilityTermStructure > probability_;
    protected Schedule schedule_;
    protected CreditDefaultSwap swap_;
    protected Date protectionStart_;
    protected Date startDate_;

    //
    // public constructors
    //

    /**
     * Full constructor mirroring C++
     * {@code CdsHelper(quote, tenor, settlementDays, calendar, frequency, paymentConvention, rule, dayCounter,
     * recoveryRate, discountCurve, settlesAccrual=true, paysAtDefaultTime=true, startDate=Date(),
     * lastPeriodDayCounter=DayCounter(), rebatesAccrual=true, model=CreditDefaultSwap::Midpoint)}.
     */
    protected CdsHelper(final Handle< Quote > quote, final Period tenor, final int settlementDays,
            final Calendar calendar, final Frequency frequency, final BusinessDayConvention paymentConvention,
            final DateGeneration.Rule rule, final DayCounter dayCounter, final double recoveryRate,
            final Handle< YieldTermStructure > discountCurve, final boolean settlesAccrual,
            final boolean paysAtDefaultTime, final Date startDate, final DayCounter lastPeriodDayCounter,
            final boolean rebatesAccrual, final PricingModel model) {
        super(quote);
        this.tenor_ = tenor;
        this.settlementDays_ = settlementDays;
        this.calendar_ = calendar;
        this.frequency_ = frequency;
        this.paymentConvention_ = paymentConvention;
        this.rule_ = rule;
        this.dayCounter_ = dayCounter;
        this.recoveryRate_ = recoveryRate;
        this.discountCurve_ = discountCurve;
        this.settlesAccrual_ = settlesAccrual;
        this.paysAtDefaultTime_ = paysAtDefaultTime;
        this.lastPeriodDC_ = lastPeriodDayCounter;
        this.rebatesAccrual_ = rebatesAccrual;
        this.model_ = model;
        this.probability_ = new RelinkableHandle< DefaultProbabilityTermStructure >();
        this.startDate_ = startDate;

        initializeDates();

        if ( discountCurve != null ) {
            discountCurve.addObserver(this);
        }
    }

    /** Convenience constructor mirroring the spread-only quote variant. */
    protected CdsHelper(final double quote, final Period tenor, final int settlementDays, final Calendar calendar,
            final Frequency frequency, final BusinessDayConvention paymentConvention, final DateGeneration.Rule rule,
            final DayCounter dayCounter, final double recoveryRate, final Handle< YieldTermStructure > discountCurve,
            final boolean settlesAccrual, final boolean paysAtDefaultTime, final Date startDate,
            final DayCounter lastPeriodDayCounter, final boolean rebatesAccrual, final PricingModel model) {
        super(quote);
        this.tenor_ = tenor;
        this.settlementDays_ = settlementDays;
        this.calendar_ = calendar;
        this.frequency_ = frequency;
        this.paymentConvention_ = paymentConvention;
        this.rule_ = rule;
        this.dayCounter_ = dayCounter;
        this.recoveryRate_ = recoveryRate;
        this.discountCurve_ = discountCurve;
        this.settlesAccrual_ = settlesAccrual;
        this.paysAtDefaultTime_ = paysAtDefaultTime;
        this.lastPeriodDC_ = lastPeriodDayCounter;
        this.rebatesAccrual_ = rebatesAccrual;
        this.model_ = model;
        this.probability_ = new RelinkableHandle< DefaultProbabilityTermStructure >();
        this.startDate_ = startDate;

        initializeDates();

        if ( discountCurve != null ) {
            discountCurve.addObserver(this);
        }
    }

    //
    // BootstrapHelper overrides
    //

    /**
     * Helper period from {@link Frequency} (mirrors C++ {@code Period(frequency_)} ctor).
     */
    @SuppressWarnings( "unused" )
    private static Period periodOf(final Frequency f) {
        return new Period(f);
    }

    /** Helper period from {@link TimeUnit} (kept for completeness). */
    @SuppressWarnings( "unused" )
    private static Period yearsPeriod(final int n) {
        return new Period(n, TimeUnit.Years);
    }

    @Override
    public void setTermStructure(final DefaultProbabilityTermStructure ts) {
        super.setTermStructure(ts);
        // C++: probability_.linkTo(shared_ptr(ts, null_deleter()), false).
        // Java RelinkableHandle.linkTo doesn't have a null-deleter issue;
        // pass isObserver=false to avoid the link itself notifying us.
        probability_.linkTo(ts, false);
        resetEngine();
    }

    @Override
    public void update() {
        super.update();
        resetEngine();
    }

    //
    // RelativeDateDefaultProbabilityHelper override
    //

    /**
     * Returns the helper's transient CDS instrument (mirrors C++ {@code swap()} accessor). May be {@code null} before
     * {@link #setTermStructure} has been called.
     */
    public CreditDefaultSwap swap() {
        return swap_;
    }

    /**
     * Subclass-specific: rebuild {@link #swap_} and attach the appropriate CDS pricing engine. Mirrors C++
     * {@code resetEngine}.
     */
    protected abstract void resetEngine();

    @Override
    protected void initializeDates() {
        protectionStart_ = evaluationDate_.add(settlementDays_);

        Date startDate = (startDate_ == null || startDate_.isNull()) ? protectionStart_ : startDate_;
        // C++: only adjust start date if rule is not CDS / CDS2015. Since the
        // Java DateGeneration enum doesn't ship CDS / CDS2015 (see
        // class-level Javadoc), every supported rule reaches the adjust path.
        startDate = calendar_.adjust(startDate, paymentConvention_);

        // C++: branches on rule == CDS{,2015,Old} → cdsMaturity helper.
        // Java only takes the "old logic" path: endDate = refDate + tenor.
        final Date refDate = (startDate_ == null || startDate_.isNull())
                ? protectionStart_
                : startDate_.add(settlementDays_);
        final Date endDate = refDate.add(tenor_);

        schedule_ = new Schedule(startDate, endDate, new Period(frequency_), calendar_, paymentConvention_,
                BusinessDayConvention.Unadjusted, rule_, false);

        earliestDate = schedule_.dates().get(0);
        latestDate = calendar_.adjust(schedule_.dates().get(schedule_.dates().size() - 1), paymentConvention_);
        // C++ adds one day to latestDate_ when model_ == ISDA; that branch
        // routes to IsdaCdsEngine which is Phase 3c. We omit the bump for
        // the Midpoint engine.

        // Suppress unused-warning for fields kept for API parity.
        if ( paysAtDefaultTime_ && !settlesAccrual_ ) {
            // intentionally empty
        }

        // Touch lastPeriodDC_ + rebatesAccrual_ + recoveryRate_ to keep them
        // visible to the bytecode (subclasses use them via resetEngine()).
        if ( lastPeriodDC_ == null && !rebatesAccrual_ && recoveryRate_ < 0.0 ) {
            // intentionally empty
        }
    }
}
