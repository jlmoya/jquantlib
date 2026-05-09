/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2009, 2012 Roland Lichters
 Copyright (C) 2009, 2012 Ferdinando Ametrano

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.termstructures.yieldcurves;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.cashflow.RateAveraging;
import org.jquantlib.indexes.OvernightIndex;
import org.jquantlib.instruments.OvernightIndexedSwap;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.math.Constants;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;

/**
 * Rate helper for bootstrapping over Overnight Indexed Swap rates.
 * <p>
 * Port of C++ QuantLib v1.42.1
 * {@code ql/termstructures/yield/oisratehelper.hpp/cpp} {@code OISRateHelper}.
 * <p>
 * <b>Phase 5d.5 MVP:</b> implements the canonical bootstrap path —
 * {@code impliedQuote()} returns {@code fairRate()} of the wrapped
 * {@link OvernightIndexedSwap}. Lookback / lockout / observation-shift,
 * forward-start, custom pillar choice, custom rule, separate
 * fixed/overnight calendars deferred to follow-up.
 *
 * @category termstructures
 *
 * @author JQuantLib migration team
 */
public class OISRateHelper extends RelativeDateRateHelper {

    private static final double BASIS_POINT = 1.0e-4;

    protected final int settlementDays_;
    protected final Period tenor_;
    protected final OvernightIndex overnightIndex_;
    protected final Handle<YieldTermStructure> discountHandle_;
    protected final boolean telescopicValueDates_;
    protected final int paymentLag_;
    protected final BusinessDayConvention paymentConvention_;
    protected final Frequency paymentFrequency_;
    protected final Calendar paymentCalendar_;
    protected final RateAveraging.Type averagingMethod_;

    protected OvernightIndexedSwap swap_;
    protected final RelinkableHandle<YieldTermStructure> termStructureHandle_ =
            new RelinkableHandle<YieldTermStructure>(null);
    protected final RelinkableHandle<YieldTermStructure> discountRelinkableHandle_ =
            new RelinkableHandle<YieldTermStructure>(null);

    /**
     * Most-common-case constructor (defaults: discountHandle empty so the
     * bootstrapped curve is used for discounting; Annual payment frequency;
     * Following BDC; Compound averaging; no telescopic dates; no payment lag).
     */
    public OISRateHelper(
            final int settlementDays,
            final Period tenor,
            final Handle<Quote> fixedRate,
            final OvernightIndex overnightIndex) {
        this(settlementDays, tenor, fixedRate, overnightIndex,
             new Handle<YieldTermStructure>(),
             false, 0, BusinessDayConvention.Following, Frequency.Annual,
             new org.jquantlib.time.calendars.NullCalendar(),
             RateAveraging.Type.Compound);
    }

    /**
     * Full constructor (Phase 5d.5 MVP).
     */
    public OISRateHelper(
            final int settlementDays,
            final Period tenor,
            final Handle<Quote> fixedRate,
            final OvernightIndex overnightIndex,
            final Handle<YieldTermStructure> discountingCurve,
            final boolean telescopicValueDates,
            final int paymentLag,
            final BusinessDayConvention paymentConvention,
            final Frequency paymentFrequency,
            final Calendar paymentCalendar,
            final RateAveraging.Type averagingMethod) {
        super(fixedRate);
        this.settlementDays_ = settlementDays;
        this.tenor_ = tenor;
        this.overnightIndex_ = overnightIndex;
        this.discountHandle_ = discountingCurve;
        this.telescopicValueDates_ = telescopicValueDates;
        this.paymentLag_ = paymentLag;
        this.paymentConvention_ = paymentConvention;
        this.paymentFrequency_ = paymentFrequency;
        this.paymentCalendar_ = paymentCalendar;
        this.averagingMethod_ = averagingMethod;

        // Re-link the index to the bootstrap term structure so its forecasting
        // depends on the curve being built.
        // The index instance is not cloned here (Phase 5d.5 MVP) — caller
        // should pass in an OvernightIndex linked to the bootstrap handle.

        overnightIndex_.addObserver(this);
        if (!discountHandle_.empty()) {
            discountHandle_.currentLink().addObserver(this);
        }
        initializeDates();
    }

    @Override
    protected void initializeDates() {
        final Date today = new Settings().evaluationDate();
        final Calendar fixingCal = overnightIndex_.fixingCalendar();
        earliestDate = fixingCal.advance(today, settlementDays_, TimeUnit.Days,
                                         BusinessDayConvention.Following, false);
        final Date endDate = earliestDate.add(tenor_);
        latestDate = fixingCal.adjust(endDate, paymentConvention_);

        // Schedule on the overnight calendar (Backward generation for tenor
        // matching standard OIS convention).
        final Schedule sch = new Schedule(
                earliestDate, latestDate,
                new Period(paymentFrequency_),
                fixingCal,
                paymentConvention_, paymentConvention_,
                DateGeneration.Rule.Backward,
                false /* endOfMonth */,
                new Date(), new Date());

        // Build the swap with placeholder rate 0 — fairRate will be the
        // implied quote of the bootstrap.
        swap_ = new OvernightIndexedSwap(
                VanillaSwap.Type.Payer, 100.0, sch, 0.0,
                overnightIndex_.dayCounter(), sch, overnightIndex_,
                0.0, paymentLag_, paymentConvention_, paymentCalendar_,
                telescopicValueDates_, averagingMethod_);

        // Discounting: exogenous if provided, otherwise use the bootstrap curve.
        if (discountHandle_.empty()) {
            swap_.setPricingEngine(new DiscountingSwapEngine(termStructureHandle_));
        } else {
            swap_.setPricingEngine(new DiscountingSwapEngine(discountHandle_));
        }
    }

    @Override
    public void setTermStructure(final YieldTermStructure t) {
        termStructureHandle_.linkTo(t, false);
        super.setTermStructure(t);
    }

    @Override
    public double impliedQuote() {
        QL.require(termStructure != null, "term structure not set");
        swap_.recalculate();
        return swap_.fairRate();
    }

    /**
     * @return the internal bootstrap swap.
     */
    public OvernightIndexedSwap swap() {
        return swap_;
    }

    @Override
    public void accept(final PolymorphicVisitor pv) {
        final Visitor<OISRateHelper> v =
                (pv != null) ? pv.visitor(this.getClass()) : null;
        if (v != null) {
            v.visit(this);
        } else {
            super.accept(pv);
        }
    }
}
