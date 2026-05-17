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
import org.jquantlib.cashflow.FloatingRateCoupon;
import org.jquantlib.cashflow.FloatingRateCouponPricer;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.cashflow.OvernightIndexedCoupon;
import org.jquantlib.cashflow.RateAveraging;
import org.jquantlib.indexes.OvernightIndex;
import org.jquantlib.instruments.MakeOIS;
import org.jquantlib.instruments.OvernightIndexedSwap;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.termstructures.Pillar;
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
 * <p><b>Phase 5e.5b-CFC-d-169 align:</b>
 * <ul>
 *   <li>Added {@link Pillar.Choice} parameter (mirrors C++
 *       {@code pillarChoice_}). Java's bootstrap places curve nodes at
 *       {@link #latestDate()} so {@code Pillar::MaturityDate} sets
 *       {@code latestDate = maturityDate} while {@code Pillar::LastRelevantDate}
 *       (default) keeps the max-of-(maturity,lastPayment,fixingEnd) behavior.</li>
 *   <li>Added optional {@link FloatingRateCouponPricer} ctor parameter
 *       (C++ {@code pricer_}) and a {@code withCouponPricer} setter, applied
 *       to the overnight leg before bootstrap (mirrors C++
 *       {@code setCouponPricer(swap_->overnightLeg(), pricer_)}).</li>
 *   <li>Added date-based ctor (startDate, endDate) mirroring the C++
 *       overload; in this mode {@code settlementDays_} is unused and the
 *       internal {@link MakeOIS} receives explicit
 *       {@code withEffectiveDate}/{@code withTerminationDate}.</li>
 * </ul>
 *
 * <p>Lookback / lockout / observation-shift, forward-start, custom rule,
 * separate fixed/overnight calendars are deferred — the Phase 5e MVP
 * constructor does not yet expose them.
 *
 * @category termstructures
 *
 * @author JQuantLib migration team
 */
public class OISRateHelper extends RelativeDateRateHelper {

    private static final double BASIS_POINT = 1.0e-4;

    protected final int settlementDays_;
    protected final Period tenor_;
    /** Non-null when the date-based ctor was used. */
    protected final Date startDate_;
    /** Non-null when the date-based ctor was used. */
    protected final Date endDate_;
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
    protected final Pillar.Choice pillarChoice_;
    protected Date pillarDate_;
    protected FloatingRateCouponPricer pricer_;

    protected OvernightIndexedSwap swap_;
    protected final RelinkableHandle<YieldTermStructure> termStructureHandle_ =
            new RelinkableHandle<YieldTermStructure>(null);
    protected final RelinkableHandle<YieldTermStructure> discountRelinkableHandle_ =
            new RelinkableHandle<YieldTermStructure>(null);

    /**
     * Most-common-case constructor (defaults: discountHandle empty so the
     * bootstrapped curve is used for discounting; Annual payment frequency;
     * Following BDC; Compound averaging; no telescopic dates; no payment lag;
     * payment calendar defaults to the overnight index fixing calendar;
     * pillar = LastRelevantDate; no coupon pricer).
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
             RateAveraging.Type.Compound,
             Pillar.Choice.LastRelevantDate, new Date(), null /* pricer */);
    }

    /**
     * Pre-Phase-5e.5b-CFC-d-169 constructor (backward-compatible). Defaults
     * pillar = LastRelevantDate and pricer = null.
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
        this(settlementDays, tenor, fixedRate, overnightIndex,
             discountingCurve, telescopicValueDates, paymentLag,
             paymentConvention, paymentFrequency, paymentCalendar,
             averagingMethod,
             Pillar.Choice.LastRelevantDate, new Date(), null /* pricer */);
    }

    /**
     * Full constructor mirroring C++ tenor-based ctor (Phase 5e MVP slice
     * + Phase 5e.5b-CFC-d-169 pillar + pricer hook).
     *
     * @param paymentCalendar may be {@code null} to default to the overnight
     *                        index fixing calendar (C++ {@code Calendar()} default).
     * @param pillarChoice    {@link Pillar.Choice#LastRelevantDate} (default)
     *                        keeps {@code latestDate = max(maturity,
     *                        lastPayment, fixingEnd)}; {@link
     *                        Pillar.Choice#MaturityDate} fixes
     *                        {@code latestDate = maturityDate} so bootstrap
     *                        nodes land exactly on each swap's maturity
     *                        (regression #1.16 / FedFunds).
     * @param customPillarDate required when {@code pillarChoice ==
     *                         Pillar.Choice.CustomDate}; ignored otherwise.
     * @param pricer           optional {@link FloatingRateCouponPricer} (e.g.
     *                         {@link
     *                         org.jquantlib.cashflow.ArithmeticAveragedOvernightIndexedCouponPricer})
     *                         applied to every overnight coupon in the bootstrap
     *                         swap. {@code null} means "use the leg's default
     *                         pricer", which is what {@link
     *                         org.jquantlib.cashflow.OvernightIndexedCoupon#amount()}
     *                         falls back to.
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
            final RateAveraging.Type averagingMethod,
            final Pillar.Choice pillarChoice,
            final Date customPillarDate,
            final FloatingRateCouponPricer pricer) {
        super(fixedRate);
        this.settlementDays_ = settlementDays;
        this.tenor_ = tenor;
        this.startDate_ = null;
        this.endDate_ = null;
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
        this.pillarChoice_ = pillarChoice;
        this.pillarDate_ = customPillarDate;
        this.pricer_ = pricer;

        overnightIndex_.addObserver(this);
        if (!discountHandle_.empty()) {
            discountHandle_.currentLink().addObserver(this);
        }
        initializeDates();
    }

    /**
     * Date-based constructor mirroring C++
     * {@code OISRateHelper(const Date& startDate, const Date& endDate, ...)}.
     * <p>
     * Used by the 1.31 bootstrap regression where helpers anchor explicit
     * effective + termination dates rather than {@code (settlementDays, tenor)}.
     */
    public OISRateHelper(
            final Date startDate,
            final Date endDate,
            final Handle<Quote> fixedRate,
            final OvernightIndex overnightIndex) {
        this(startDate, endDate, fixedRate, overnightIndex,
             new Handle<YieldTermStructure>(),
             false, 0, BusinessDayConvention.Following, Frequency.Annual,
             null /* paymentCalendar -> defaults to overnightIndex.fixingCalendar() */,
             RateAveraging.Type.Compound,
             Pillar.Choice.LastRelevantDate, new Date(), null /* pricer */);
    }

    /**
     * Full date-based constructor (Phase 5e.5b-CFC-d-169). See {@link
     * #OISRateHelper(int, Period, Handle, OvernightIndex, Handle, boolean,
     * int, BusinessDayConvention, Frequency, Calendar, RateAveraging.Type,
     * Pillar.Choice, Date, FloatingRateCouponPricer)} for parameter docs.
     */
    public OISRateHelper(
            final Date startDate,
            final Date endDate,
            final Handle<Quote> fixedRate,
            final OvernightIndex overnightIndex,
            final Handle<YieldTermStructure> discountingCurve,
            final boolean telescopicValueDates,
            final int paymentLag,
            final BusinessDayConvention paymentConvention,
            final Frequency paymentFrequency,
            final Calendar paymentCalendar,
            final RateAveraging.Type averagingMethod,
            final Pillar.Choice pillarChoice,
            final Date customPillarDate,
            final FloatingRateCouponPricer pricer) {
        super(fixedRate);
        this.settlementDays_ = 0; // unused for date-based ctor
        this.tenor_ = null;       // unused for date-based ctor
        this.startDate_ = startDate;
        this.endDate_ = endDate;
        this.overnightIndex_ =
                (OvernightIndex) overnightIndex.clone(termStructureHandle_).currentLink();
        this.discountHandle_ = discountingCurve;
        this.telescopicValueDates_ = telescopicValueDates;
        this.paymentLag_ = paymentLag;
        this.paymentConvention_ = paymentConvention;
        this.paymentFrequency_ = paymentFrequency;
        this.paymentCalendar_ = paymentCalendar;
        this.averagingMethod_ = averagingMethod;
        this.pillarChoice_ = pillarChoice;
        this.pillarDate_ = customPillarDate;
        this.pricer_ = pricer;

        overnightIndex_.addObserver(this);
        if (!discountHandle_.empty()) {
            discountHandle_.currentLink().addObserver(this);
        }
        initializeDates();
    }

    /**
     * Set the coupon pricer applied to the overnight leg of the internal
     * bootstrap swap. Mirrors C++ pattern where {@code pricer_} can be
     * supplied at construction; the Java setter exists to allow late-binding
     * via the builder-style usage in tests and rebuilds the swap so the
     * pricer is wired in before the bootstrap iterates.
     */
    public void withCouponPricer(final FloatingRateCouponPricer pricer) {
        this.pricer_ = pricer;
        initializeDates();
    }

    @Override
    protected void initializeDates() {
        // Delegate swap construction to MakeOIS to mirror C++ exactly
        // (matches the test-side check swap built via the same MakeOIS,
        // so bootstrap-then-recompute is consistent).
        // Port of C++ ql/termstructures/yield/oisratehelper.cpp:132-163.
        final Period tenorForMake = (tenor_ != null)
                ? tenor_
                : new Period(1, TimeUnit.Days); // dummy; overridden by termination date
        final MakeOIS make = new MakeOIS(tenorForMake, overnightIndex_, 0.0,
                                         new Period(0, TimeUnit.Days))
                .withDiscountingTermStructure(discountRelinkableHandle_)
                .withTelescopicValueDates(telescopicValueDates_)
                .withPaymentLag(paymentLag_)
                .withPaymentAdjustment(paymentConvention_)
                .withPaymentFrequency(paymentFrequency_)
                .withAveragingMethod(averagingMethod_);
        if (startDate_ != null) {
            // date-based ctor: pass explicit dates; MakeOIS guards against
            // settlementDays+effectiveDate conflict, so do NOT call
            // withSettlementDays in this path.
            make.withEffectiveDate(startDate_).withTerminationDate(endDate_);
        } else {
            make.withSettlementDays(settlementDays_);
        }
        if (paymentCalendar_ != null) {
            make.withPaymentCalendar(paymentCalendar_);
        }
        swap_ = make.value();

        // Apply the custom coupon pricer (e.g.
        // ArithmeticAveragedOvernightIndexedCouponPricer) to every coupon
        // of the overnight leg. Mirror of C++
        // `setCouponPricer(swap_->overnightLeg(), pricer_)` in
        // oisratehelper.cpp:165-166. We call FloatingRateCoupon.setPricer
        // directly to avoid a hard dependency on CashFlows.setCouponPricer
        // / PricerSetter dispatch logic (which is keyed off coupon subtype).
        if (pricer_ != null) {
            final Leg overnightLeg = swap_.overnightLeg();
            for (int i = 0; i < overnightLeg.size(); ++i) {
                final CashFlow cf = overnightLeg.get(i);
                if (cf instanceof FloatingRateCoupon) {
                    ((FloatingRateCoupon) cf).setPricer(pricer_);
                }
            }
        }

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

        Date latestRelevant = maturityDate;
        if (lastPaymentDate.gt(latestRelevant)) {
            latestRelevant = lastPaymentDate;
        }
        if (fixingEndDate.gt(latestRelevant)) {
            latestRelevant = fixingEndDate;
        }

        // Java places curve nodes at latestDate() (no separate pillarDate_
        // hook on BootstrapHelper), so the pillar choice is realized by
        // choosing which date latestDate_ takes.
        // Port of C++ oisratehelper.cpp:181-201.
        switch (pillarChoice_) {
        case MaturityDate:
            latestDate = maturityDate;
            pillarDate_ = maturityDate;
            break;
        case LastRelevantDate:
            latestDate = latestRelevant;
            pillarDate_ = latestRelevant;
            break;
        case CustomDate:
            QL.require(pillarDate_ != null && !pillarDate_.isNull(),
                    "custom pillar date must be provided");
            QL.require(pillarDate_.ge(earliestDate),
                    "pillar date (" + pillarDate_ + ") must be later than or "
                            + "equal to the instrument's earliest date ("
                            + earliestDate + ")");
            QL.require(pillarDate_.le(latestRelevant),
                    "pillar date (" + pillarDate_ + ") must be before or "
                            + "equal to the instrument's latest relevant date ("
                            + latestRelevant + ")");
            latestDate = latestRelevant;
            break;
        default:
            throw new LibraryException("unknown Pillar::Choice");
        }
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

    /**
     * @return the pillar date used as the curve-node anchor for this helper.
     *         Mirrors C++ {@code BootstrapHelper::pillarDate()}.
     */
    public Date pillarDate() {
        return pillarDate_;
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
