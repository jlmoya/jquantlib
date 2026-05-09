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
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;

/**
 * Helper class to instantiate {@link OvernightIndexedSwap} more comfortably,
 * mirroring C++ {@code MakeOIS}.
 * <p>
 * Port of C++ QuantLib v1.42.1
 * {@code ql/instruments/makeois.hpp/cpp} {@code MakeOIS}.
 * <p>
 * <b>Phase 5d.5 MVP:</b> exposes the most-used builder methods. Lookback,
 * lockout, observation-shift, separate fixed/overnight schedule rules and
 * end-of-month flags deferred to follow-up.
 *
 * @category instruments
 *
 * @author JQuantLib migration team
 */
public class MakeOIS {

    private final Period swapTenor_;
    private final OvernightIndex overnightIndex_;
    private double fixedRate_;
    private final Period forwardStart_;

    private int settlementDays_ = Constants.NULL_NATURAL;
    private Date effectiveDate_ = new Date();
    private Date terminationDate_ = new Date();
    private Calendar calendar_;
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

    private DateGeneration.Rule rule_ = DateGeneration.Rule.Backward;
    private boolean endOfMonth_;
    private boolean isDefaultEOM_ = true;

    private Handle<YieldTermStructure> discountingTermStructure_ = new Handle<YieldTermStructure>();
    private PricingEngine engine_ = null;

    public MakeOIS(final Period swapTenor, final OvernightIndex overnightIndex) {
        this(swapTenor, overnightIndex, Constants.NULL_REAL, new Period(0, TimeUnit.Days));
    }

    public MakeOIS(final Period swapTenor, final OvernightIndex overnightIndex,
                   final double fixedRate) {
        this(swapTenor, overnightIndex, fixedRate, new Period(0, TimeUnit.Days));
    }

    public MakeOIS(final Period swapTenor, final OvernightIndex overnightIndex,
                   final double fixedRate, final Period fwdStart) {
        this.swapTenor_ = swapTenor;
        this.overnightIndex_ = overnightIndex;
        this.fixedRate_ = fixedRate;
        this.forwardStart_ = fwdStart;
        this.calendar_ = overnightIndex.fixingCalendar();
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

    public MakeOIS withSettlementDays(final int settlementDays) {
        this.settlementDays_ = settlementDays;
        this.effectiveDate_ = new Date();
        return this;
    }

    public MakeOIS withEffectiveDate(final Date d) {
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

    public MakeOIS withCalendar(final Calendar cal) {
        this.calendar_ = cal;
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

    public MakeOIS withDiscountingTermStructure(final Handle<YieldTermStructure> ts) {
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
        QL.require(effectiveDate_.isNull() || settlementDays_ == Constants.NULL_NATURAL,
            "cannot set both an explicit effective date and settlement days; "
            + "use one or the other");

        Date startDate;
        if (!effectiveDate_.isNull()) {
            startDate = effectiveDate_;
        } else {
            int settlementDays = settlementDays_;
            if (settlementDays == Constants.NULL_NATURAL) {
                if (overnightIndex_ instanceof Sonia) {
                    settlementDays = 0;
                } else {
                    settlementDays = 2;
                }
            }
            Date refDate = new Settings().evaluationDate();
            refDate = calendar_.adjust(refDate);
            Date spotDate = calendar_.advance(refDate,
                                              new Period(settlementDays, TimeUnit.Days),
                                              BusinessDayConvention.Following);
            startDate = spotDate.add(forwardStart_);
            startDate = calendar_.adjust(startDate,
                forwardStart_.length() < 0
                    ? BusinessDayConvention.Preceding
                    : BusinessDayConvention.Following);
        }

        boolean useEOM = isDefaultEOM_
                ? calendar_.isEndOfMonth(startDate)
                : endOfMonth_;

        Date endDate = terminationDate_;
        if (endDate.isNull()) {
            endDate = startDate.add(swapTenor_);
        }

        final Schedule schedule = new Schedule(
                startDate, endDate, new Period(paymentFrequency_),
                calendar_, paymentAdjustment_, paymentAdjustment_,
                rule_, useEOM, new Date(), new Date());

        double usedFixedRate = fixedRate_;
        if (fixedRate_ == Constants.NULL_REAL) {
            // bootstrap fair rate
            final OvernightIndexedSwap temp = new OvernightIndexedSwap(
                    type_, nominal_, schedule, 0.0, fixedDayCount_,
                    schedule, overnightIndex_, overnightSpread_,
                    paymentLag_, paymentAdjustment_,
                    paymentCalendar_ != null ? paymentCalendar_ : calendar_,
                    telescopicValueDates_, averagingMethod_);
            if (engine_ == null) {
                final Handle<YieldTermStructure> disc =
                    discountingTermStructure_.empty()
                        ? overnightIndex_.termStructure()
                        : discountingTermStructure_;
                QL.require(!disc.empty(),
                    "null term structure set to this instance of " + overnightIndex_.name());
                temp.setPricingEngine(new DiscountingSwapEngine(disc));
            } else {
                temp.setPricingEngine(engine_);
            }
            usedFixedRate = temp.fairRate();
        }

        final OvernightIndexedSwap ois = new OvernightIndexedSwap(
                type_, nominal_, schedule, usedFixedRate, fixedDayCount_,
                schedule, overnightIndex_, overnightSpread_,
                paymentLag_, paymentAdjustment_,
                paymentCalendar_ != null ? paymentCalendar_ : calendar_,
                telescopicValueDates_, averagingMethod_);

        if (engine_ == null && !discountingTermStructure_.empty()) {
            ois.setPricingEngine(new DiscountingSwapEngine(discountingTermStructure_));
        } else if (engine_ != null) {
            ois.setPricingEngine(engine_);
        }
        return ois;
    }
}
