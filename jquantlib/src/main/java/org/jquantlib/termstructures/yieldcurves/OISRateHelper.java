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
import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.cashflow.OvernightIndexedCoupon;
import org.jquantlib.cashflow.RateAveraging;
import org.jquantlib.indexes.OvernightIndex;
import org.jquantlib.instruments.MakeOIS;
import org.jquantlib.instruments.OvernightIndexedSwap;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;

/**
 * Rate helper for bootstrapping over Overnight Indexed Swap rates.
 * <p>
 * Port of C++ QuantLib v1.42.1
 * {@code ql/termstructures/yield/oisratehelper.hpp/cpp} {@code OISRateHelper}.
 * <p>
 * <b>Phase 5e.5b-CFC-d-36 align:</b> internal {@link OvernightIndexedSwap}
 * construction delegated to {@link MakeOIS} to mirror C++ exactly (same
 * {@code DateGeneration.Rule}, {@code endOfMonth}, calendar handling). The
 * pillar/latest date is computed as
 * {@code max(maturityDate, lastPaymentDate, fixingEndDate)} per C++
 * {@code oisratehelper.cpp:179}, ensuring partial bootstrap succeeds even
 * when {@code paymentLag > 0} pushes the last payment beyond the maturity.
 *
 * <p>The Phase 5d.5 MVP path (manual {@code Schedule}+swap construction with
 * {@code latestDate = earliestDate + tenor}) failed bootstrap with
 * {@code paymentLag = 2}: the last cashflow's payment date fell past the
 * pillar, triggering "date is past max curve" inside
 * {@code IterativeBootstrap}.
 *
 * <p>Lookback / lockout / observation-shift, forward-start, custom pillar
 * choice, custom rule, separate fixed/overnight calendars are deferred —
 * the Phase 5e MVP constructor does not yet expose them.
 *
 * @category termstructures
 *
 * @author JQuantLib migration team
 */
public class OISRateHelper extends RelativeDateRateHelper {

    private static final double BASIS_POINT = 1.0e-4;

    protected final int settlementDays_;
    protected final Period tenor_;
    /**
     * Clone of the user-supplied overnight index, re-linked to
     * {@link #termStructureHandle_} so the bootstrap drives forecasting.
     * Mirrors C++ {@code oisratehelper.cpp:112}.
     */
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
     * Following BDC; Compound averaging; no telescopic dates; no payment lag;
     * payment calendar defaults to the overnight index fixing calendar).
     */
    public OISRateHelper(
            final int settlementDays,
            final Period tenor,
            final Handle<Quote> fixedRate,
            final OvernightIndex overnightIndex) {
        this(settlementDays, tenor, fixedRate, overnightIndex,
             new Handle<YieldTermStructure>(),
             false, 0, BusinessDayConvention.Following, Frequency.Annual,
             null /* paymentCalendar -> defaults to overnightIndex.fixingCalendar() */,
             RateAveraging.Type.Compound);
    }

    /**
     * Full constructor mirroring C++ tenor-based ctor (Phase 5e MVP slice).
     *
     * @param paymentCalendar may be {@code null} to default to the overnight
     *                        index fixing calendar (C++ {@code Calendar()} default).
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
        // Clone the index so its forwarding curve is the bootstrap curve
        // (termStructureHandle_), not whatever the caller supplied.
        // Mirrors C++ oisratehelper.cpp:111-112 which does
        //   overnightIndex_ = dynamic_pointer_cast<OvernightIndex>(
        //                         overnightIndex->clone(termStructureHandle_))
        // The Java OvernightIndex.clone returns Handle<IborIndex>; we
        // unwrap to OvernightIndex.
        this.overnightIndex_ =
                (OvernightIndex) overnightIndex.clone(termStructureHandle_).currentLink();
        this.discountHandle_ = discountingCurve;
        this.telescopicValueDates_ = telescopicValueDates;
        this.paymentLag_ = paymentLag;
        this.paymentConvention_ = paymentConvention;
        this.paymentFrequency_ = paymentFrequency;
        this.paymentCalendar_ = paymentCalendar;
        this.averagingMethod_ = averagingMethod;

        overnightIndex_.addObserver(this);
        if (!discountHandle_.empty()) {
            discountHandle_.currentLink().addObserver(this);
        }
        initializeDates();
    }

    @Override
    protected void initializeDates() {
        // Delegate swap construction to MakeOIS to mirror C++ exactly
        // (matches the test-side check swap built via the same MakeOIS,
        // so bootstrap-then-recompute is consistent).
        // Port of C++ ql/termstructures/yield/oisratehelper.cpp:132-163.
        final MakeOIS make = new MakeOIS(tenor_, overnightIndex_, 0.0,
                                         new Period(0, TimeUnit.Days))
                .withDiscountingTermStructure(discountRelinkableHandle_)
                .withTelescopicValueDates(telescopicValueDates_)
                .withPaymentLag(paymentLag_)
                .withPaymentAdjustment(paymentConvention_)
                .withPaymentFrequency(paymentFrequency_)
                .withAveragingMethod(averagingMethod_)
                .withSettlementDays(settlementDays_);
        if (paymentCalendar_ != null) {
            make.withPaymentCalendar(paymentCalendar_);
        }
        swap_ = make.value();

        // Discounting: exogenous if provided, otherwise use the bootstrap
        // curve via termStructureHandle_ (relinked in setTermStructure).
        // We always set the engine ourselves rather than relying on
        // MakeOIS.value() — Java's Handle.empty() semantics mean a
        // RelinkableHandle with null link returns true from empty(),
        // so MakeOIS's "if (engine == null && !disc.empty())" branch is
        // skipped when discountRelinkableHandle_ is not yet linked.
        if (discountHandle_.empty()) {
            swap_.setPricingEngine(new DiscountingSwapEngine(termStructureHandle_));
        } else {
            swap_.setPricingEngine(new DiscountingSwapEngine(discountHandle_));
        }

        earliestDate = swap_.startDate();
        final Date maturityDate = swap_.maturityDate();

        // C++ pillar: max(maturityDate, lastPaymentDate, fixingEndDate).
        // - lastPaymentDate: max of last cashflow date on both legs (the
        //   overnight leg can extend past maturity when paymentLag > 0).
        // - fixingEndDate: overnightIndex.maturityDate(valueDate(lastFixingDate))
        //   — the end of the last underlying overnight period, which
        //   determines what curve dates the bootstrap actually needs.
        final Leg overnightLeg = swap_.overnightLeg();
        final Leg fixedLeg = swap_.fixedLeg();
        Date lastPaymentDate = overnightLeg.get(overnightLeg.size() - 1).date();
        final Date lastFixedDate = fixedLeg.get(fixedLeg.size() - 1).date();
        if (lastFixedDate.gt(lastPaymentDate)) {
            lastPaymentDate = lastFixedDate;
        }

        final OvernightIndexedCoupon lastOnCoupon =
                (OvernightIndexedCoupon) overnightLeg.get(overnightLeg.size() - 1);
        final Date lastFixingDate = lastOnCoupon.fixingDate();
        final Date fixingEndDate = overnightIndex_.maturityDate(
                overnightIndex_.valueDate(lastFixingDate));

        Date latest = maturityDate;
        if (lastPaymentDate.gt(latest)) {
            latest = lastPaymentDate;
        }
        if (fixingEndDate.gt(latest)) {
            latest = fixingEndDate;
        }
        latestDate = latest;
    }

    @Override
    public void setTermStructure(final YieldTermStructure t) {
        // Do not register as observer — bootstrap drives recalculation.
        termStructureHandle_.linkTo(t, false);
        // When there is no exogenous discount curve, the bootstrap curve
        // is also the discount curve.
        if (discountHandle_.empty()) {
            discountRelinkableHandle_.linkTo(t, false);
        } else {
            discountRelinkableHandle_.linkTo(discountHandle_.currentLink(), false);
        }
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
