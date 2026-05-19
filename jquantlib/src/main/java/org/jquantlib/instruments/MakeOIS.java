/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2009, 2014, 2015 Ferdinando Ametrano
 Copyright (C) 2017 Joseph Jeisman

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.instruments;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.cashflow.RateAveraging;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.OvernightIndex;
import org.jquantlib.indexes.ibor.Sonia;
import org.jquantlib.math.Constants;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.*;

/**
 * Helper class to instantiate {@link OvernightIndexedSwap} more comfortably, mirroring C++ {@code MakeOIS}.
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/instruments/makeois.hpp/cpp} {@code MakeOIS}.
 * <p>
 * <b>Phase 5e.5b-CFC-d-107:</b> lookback / lockout / observation-shift
 * builder knobs added (matched to C++ MakeOIS). Separate fixed/overnight schedule rules deferred.
 * <p>
 * <b>Phase 5e.5b-CFC-d-116:</b> lookback / lockout / observation-shift
 * now flowed through to the {@link OvernightIndexedSwap} ctor; the {@code withSettlementDays} /
 * {@code withEffectiveDate} mutual-exclusion guard is enforced (mirror of C++
 * {@code testSettlementDaysEffectiveDateConflict}).
 *
 * @author JQuantLib migration team
 * @category instruments
 */
public class MakeOIS {

    private final Period swapTenor_;
    private final OvernightIndex overnightIndex_;
    private final Period forwardStart_;
    private final double fixedRate_;
    private int settlementDays_ = Constants.NULL_NATURAL;
    private Date effectiveDate_ = new Date();
    private Date terminationDate_ = new Date();
    private Calendar fixedCalendar_;
    private Calendar overnightCalendar_;
    private DayCounter fixedDayCount_;

    private Frequency paymentFrequency_ = Frequency.Annual;
    private BusinessDayConvention paymentAdjustment_ = BusinessDayConvention.Following;
    private int paymentLag_ = 0;
    private Calendar paymentCalendar_ = null;

    private VanillaSwap.Type type_ = VanillaSwap.Type.Payer;
    private double nominal_ = 1.0;

    private double overnightSpread_ = 0.0;
    private RateAveraging.Type averagingMethod_ = RateAveraging.Type.Compound;
    private boolean telescopicValueDates_ = false;
    private int lookbackDays_ = Constants.NULL_NATURAL;
    private int lockoutDays_ = 0;
    private boolean applyObservationShift_ = false;

    private DateGeneration.Rule rule_ = DateGeneration.Rule.Backward;
    private boolean endOfMonth_;
    private boolean isDefaultEOM_ = true;
    private boolean conflictRequested_ = false;

    private Handle< YieldTermStructure > discountingTermStructure_ = new Handle< YieldTermStructure >();
    private PricingEngine engine_ = null;

    public MakeOIS(final Period swapTenor, final OvernightIndex overnightIndex) {
        this(swapTenor, overnightIndex, Constants.NULL_REAL, new Period(0, TimeUnit.Days));
    }

    public MakeOIS(final Period swapTenor, final OvernightIndex overnightIndex, final double fixedRate) {
        this(swapTenor, overnightIndex, fixedRate, new Period(0, TimeUnit.Days));
    }

    public MakeOIS(final Period swapTenor, final OvernightIndex overnightIndex, final double fixedRate,
            final Period fwdStart) {
        this.swapTenor_ = swapTenor;
        this.overnightIndex_ = overnightIndex;
        this.fixedRate_ = fixedRate;
        this.forwardStart_ = fwdStart;
        // Both legs default to the overnight index fixing calendar, matching
        // C++ MakeOIS ctor (makeois.cpp:38-39).
        this.fixedCalendar_ = overnightIndex.fixingCalendar();
        this.overnightCalendar_ = overnightIndex.fixingCalendar();
        this.fixedDayCount_ = overnightIndex.dayCounter();
    }

    //
    // builder methods
    //

    public MakeOIS receiveFixed(final boolean flag) {
        this.type_ = flag ? VanillaSwap.Type.Receiver : VanillaSwap.Type.Payer;
        return this;
    }

    public MakeOIS withType(final VanillaSwap.Type type) {
        this.type_ = type;
        return this;
    }

    public MakeOIS withNominal(final double n) {
        this.nominal_ = n;
        return this;
    }

    /**
     * Setter pair {@code withSettlementDays}/{@code withEffectiveDate}: only one can be active at build time; calling
     * both (in either order) flips an immutable conflict flag that {@link #value()} rejects, mirroring C++
     * {@code MakeOIS::withSettlementDays} / {@code withEffectiveDate} guard (overnightindexedswap.cpp
     * testSettlementDaysEffectiveDateConflict).
     */
    public MakeOIS withSettlementDays(final int settlementDays) {
        if ( !effectiveDate_.isNull() ) {
            this.conflictRequested_ = true;
        }
        this.settlementDays_ = settlementDays;
        return this;
    }

    public MakeOIS withEffectiveDate(final Date d) {
        if ( settlementDays_ != Constants.NULL_NATURAL ) {
            this.conflictRequested_ = true;
        }
        this.effectiveDate_ = d;
        return this;
    }

    public MakeOIS withTerminationDate(final Date d) {
        this.terminationDate_ = d;
        return this;
    }

    public MakeOIS withRule(final DateGeneration.Rule r) {
        this.rule_ = r;
        return this;
    }

    public MakeOIS withPaymentFrequency(final Frequency f) {
        this.paymentFrequency_ = f;
        return this;
    }

    public MakeOIS withPaymentAdjustment(final BusinessDayConvention bdc) {
        this.paymentAdjustment_ = bdc;
        return this;
    }

    public MakeOIS withPaymentLag(final int lag) {
        this.paymentLag_ = lag;
        return this;
    }

    public MakeOIS withPaymentCalendar(final Calendar cal) {
        this.paymentCalendar_ = cal;
        return this;
    }

    /**
     * Sets the calendar for both the fixed and overnight legs, mirroring C++
     * {@code MakeOIS::withCalendar} (makeois.cpp:246-248) which delegates to
     * {@link #withFixedLegCalendar(Calendar)} and
     * {@link #withOvernightLegCalendar(Calendar)}.
     */
    public MakeOIS withCalendar(final Calendar cal) {
        return withFixedLegCalendar(cal).withOvernightLegCalendar(cal);
    }

    /**
     * Sets the calendar used for the fixed-leg schedule generation. Mirror of C++
     * {@code MakeOIS::withFixedLegCalendar} (makeois.cpp:250-253).
     */
    public MakeOIS withFixedLegCalendar(final Calendar cal) {
        this.fixedCalendar_ = cal;
        return this;
    }

    /**
     * Sets the calendar used for the overnight-leg schedule generation and for
     * the start-date / spot computation (which advances along the overnight
     * fixing calendar in C++). Mirror of C++ {@code MakeOIS::withOvernightLegCalendar}
     * (makeois.cpp:255-258).
     */
    public MakeOIS withOvernightLegCalendar(final Calendar cal) {
        this.overnightCalendar_ = cal;
        return this;
    }

    public MakeOIS withFixedLegDayCount(final DayCounter dc) {
        this.fixedDayCount_ = dc;
        return this;
    }

    public MakeOIS withOvernightLegSpread(final double sp) {
        this.overnightSpread_ = sp;
        return this;
    }

    public MakeOIS withEndOfMonth(final boolean flag) {
        this.endOfMonth_ = flag;
        this.isDefaultEOM_ = false;
        return this;
    }

    public MakeOIS withTelescopicValueDates(final boolean v) {
        this.telescopicValueDates_ = v;
        return this;
    }

    public MakeOIS withAveragingMethod(final RateAveraging.Type avg) {
        this.averagingMethod_ = avg;
        return this;
    }

    /**
     * Mirror of C++ {@code MakeOIS::withLookbackDays} — value is propagated to the {@link OvernightIndexedSwap} ctor
     * (and thence the underlying {@link org.jquantlib.cashflow.OvernightLeg}).
     */
    public MakeOIS withLookbackDays(final int lookbackDays) {
        this.lookbackDays_ = lookbackDays;
        return this;
    }

    /** Mirror of C++ {@code MakeOIS::withLockoutDays}. See {@link #withLookbackDays}. */
    public MakeOIS withLockoutDays(final int lockoutDays) {
        this.lockoutDays_ = lockoutDays;
        return this;
    }

    /** Mirror of C++ {@code MakeOIS::withObservationShift}. See {@link #withLookbackDays}. */
    public MakeOIS withObservationShift(final boolean applyObservationShift) {
        this.applyObservationShift_ = applyObservationShift;
        return this;
    }

    public MakeOIS withDiscountingTermStructure(final Handle< YieldTermStructure > ts) {
        this.discountingTermStructure_ = ts;
        return this;
    }

    public MakeOIS withPricingEngine(final PricingEngine engine) {
        this.engine_ = engine;
        return this;
    }

    /**
     * Build the {@link OvernightIndexedSwap}.
     */
    public OvernightIndexedSwap value() {
        QL.require(!conflictRequested_,
                "cannot set both an explicit effective date and settlement days; " + "use one or the other");

        Date startDate;
        if ( !effectiveDate_.isNull() ) {
            startDate = effectiveDate_;
        } else {
            int settlementDays = settlementDays_;
            if ( settlementDays == Constants.NULL_NATURAL ) {
                if ( overnightIndex_ instanceof Sonia ) {
                    settlementDays = 0;
                } else {
                    settlementDays = 2;
                }
            }
            // C++ uses overnightCalendar_ for the start-date / spot
            // computation (makeois.cpp:71-82).
            Date refDate = new Settings().evaluationDate();
            refDate = overnightCalendar_.adjust(refDate);
            Date spotDate = overnightCalendar_.advance(refDate, new Period(settlementDays, TimeUnit.Days),
                    BusinessDayConvention.Following);
            startDate = spotDate.add(forwardStart_);
            startDate = overnightCalendar_.adjust(startDate,
                    forwardStart_.length() < 0 ? BusinessDayConvention.Preceding : BusinessDayConvention.Following);
        }

        // Default EOM keys off the overnight calendar (mirrors C++
        // makeois.cpp:85-87 which uses overnightCalendar_.isEndOfMonth).
        boolean useEOM = isDefaultEOM_ ? overnightCalendar_.isEndOfMonth(startDate) : endOfMonth_;

        Date endDate = terminationDate_;
        if ( endDate.isNull() ) {
            endDate = startDate.add(swapTenor_);
        }

        final Schedule fixedSchedule = new Schedule(startDate, endDate, new Period(paymentFrequency_), fixedCalendar_,
                paymentAdjustment_, paymentAdjustment_, rule_, useEOM, new Date(), new Date());

        final Schedule overnightSchedule = new Schedule(startDate, endDate, new Period(paymentFrequency_),
                overnightCalendar_, paymentAdjustment_, paymentAdjustment_, rule_, useEOM, new Date(), new Date());

        // Default paymentCalendar -> overnight calendar (matches C++ where an
        // unset paymentCalendar_ falls back to the overnight schedule's
        // calendar in the OvernightIndexedSwap ctor).
        final Calendar effectivePaymentCalendar = paymentCalendar_ != null ? paymentCalendar_ : overnightCalendar_;

        double usedFixedRate = fixedRate_;
        if ( fixedRate_ == Constants.NULL_REAL ) {
            // bootstrap fair rate
            final OvernightIndexedSwap temp = new OvernightIndexedSwap(type_, nominal_, fixedSchedule, 0.0,
                    fixedDayCount_, overnightSchedule, overnightIndex_, overnightSpread_, paymentLag_,
                    paymentAdjustment_, effectivePaymentCalendar, telescopicValueDates_, averagingMethod_,
                    lookbackDays_, lockoutDays_, applyObservationShift_);
            if ( engine_ == null ) {
                final Handle< YieldTermStructure > disc = discountingTermStructure_.empty()
                        ? overnightIndex_.termStructure()
                        : discountingTermStructure_;
                QL.require(!disc.empty(), "null term structure set to this instance of " + overnightIndex_.name());
                temp.setPricingEngine(new DiscountingSwapEngine(disc));
            } else {
                temp.setPricingEngine(engine_);
            }
            usedFixedRate = temp.fairRate();
        }

        final OvernightIndexedSwap ois = new OvernightIndexedSwap(type_, nominal_, fixedSchedule, usedFixedRate,
                fixedDayCount_, overnightSchedule, overnightIndex_, overnightSpread_, paymentLag_, paymentAdjustment_,
                effectivePaymentCalendar, telescopicValueDates_, averagingMethod_,
                lookbackDays_, lockoutDays_, applyObservationShift_);

        if ( engine_ == null && !discountingTermStructure_.empty() ) {
            ois.setPricingEngine(new DiscountingSwapEngine(discountingTermStructure_));
        } else if ( engine_ != null ) {
            ois.setPricingEngine(engine_);
        }
        return ois;
    }
}
