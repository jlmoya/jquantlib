/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2021 Marcin Rybacki
 Copyright (C) 2025 Uzair Beg

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */

/*! \file crosscurrencyratehelpers.hpp/.cpp
    \brief constant-notional fixed-vs-floating cross-currency swap rate helper
*/

package org.jquantlib.experimental.termstructures;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.cashflow.FixedRateLeg;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;

/**
 * Rate helper for bootstrapping over constant-notional fixed-vs-floating
 * cross-currency par swaps.
 * <p>
 * Port of C++ QuantLib v1.42.1
 * {@code ql/experimental/termstructures/crosscurrencyratehelpers.hpp}
 * {@code ConstNotionalCrossCurrencySwapRateHelper}.
 * <p>
 * Represents a par cross-currency swap exchanging a fixed-rate leg against a
 * floating-rate leg in a different currency. Since the swap is quoted at par,
 * the FX spot cancels out and is not required.
 */
public class ConstNotionalCrossCurrencySwapRateHelper
        extends CrossCurrencySwapRateHelperBase {

    /** Sample fixed rate used when building the fixed leg (see C++ source). */
    private static final double SAMPLE_FIXED_RATE = 0.01;

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    protected final Frequency fixedFrequency_;
    protected final DayCounter fixedDayCount_;
    protected final IborIndex floatIndex_;
    protected final boolean collateralOnFixedLeg_;

    protected Leg fixedLeg_;
    protected Leg floatLeg_;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * @param fixedRate            quoted fixed rate
     * @param tenor                swap tenor
     * @param fixingDays           fixing lag in days
     * @param calendar             calendar
     * @param convention           business-day convention
     * @param endOfMonth           end-of-month flag
     * @param fixedFrequency       fixed-leg payment frequency
     * @param fixedDayCount        fixed-leg day counter
     * @param floatIndex           floating-leg ibor index
     * @param collateralCurve      collateral discount curve
     * @param collateralOnFixedLeg if true, collateral curve discounts the fixed leg
     * @param paymentLag           payment lag in days (typically 0)
     */
    public ConstNotionalCrossCurrencySwapRateHelper(
            final Handle<org.jquantlib.quotes.Quote> fixedRate,
            final Period tenor,
            final int fixingDays,
            final Calendar calendar,
            final BusinessDayConvention convention,
            final boolean endOfMonth,
            final Frequency fixedFrequency,
            final DayCounter fixedDayCount,
            final IborIndex floatIndex,
            final Handle<YieldTermStructure> collateralCurve,
            final boolean collateralOnFixedLeg,
            final int paymentLag) {

        super(fixedRate, tenor, fixingDays, calendar, convention, endOfMonth,
              collateralCurve, paymentLag);

        QL.require(floatIndex != null, "floating index required");

        fixedFrequency_    = fixedFrequency;
        fixedDayCount_     = fixedDayCount;
        floatIndex_        = floatIndex;
        collateralOnFixedLeg_ = collateralOnFixedLeg;

        floatIndex_.addObserver(this);

        initializeDates();
    }

    /**
     * Constructor with zero payment lag.
     */
    public ConstNotionalCrossCurrencySwapRateHelper(
            final Handle<org.jquantlib.quotes.Quote> fixedRate,
            final Period tenor,
            final int fixingDays,
            final Calendar calendar,
            final BusinessDayConvention convention,
            final boolean endOfMonth,
            final Frequency fixedFrequency,
            final DayCounter fixedDayCount,
            final IborIndex floatIndex,
            final Handle<YieldTermStructure> collateralCurve,
            final boolean collateralOnFixedLeg) {

        this(fixedRate, tenor, fixingDays, calendar, convention, endOfMonth,
             fixedFrequency, fixedDayCount, floatIndex, collateralCurve,
             collateralOnFixedLeg, 0);
    }

    // -------------------------------------------------------------------------
    // RelativeDateRateHelper interface
    // -------------------------------------------------------------------------

    @Override
    protected void initializeDates() {
        final Date evaluationDate = new Settings().evaluationDate();

        fixedLeg_ = buildFixedLeg(evaluationDate, tenor_, fixingDays_, calendar_,
                convention_, endOfMonth_, fixedFrequency_, fixedDayCount_, paymentLag_);

        floatLeg_ = CrossCurrencyBasisSwapRateHelperBase.buildFloatingLeg(
                evaluationDate, tenor_, fixingDays_,
                floatIndex_.fixingCalendar(),
                floatIndex_.businessDayConvention(),
                endOfMonth_,
                floatIndex_,
                floatIndex_.tenor().frequency(),
                paymentLag_);

        initializeDatesFromLegs(fixedLeg_, floatLeg_);
    }

    // -------------------------------------------------------------------------
    // Protected helpers
    // -------------------------------------------------------------------------

    protected Handle<YieldTermStructure> fixedLegDiscountHandle() {
        return collateralOnFixedLeg_ ? collateralHandle_ : termStructureHandle_;
    }

    protected Handle<YieldTermStructure> floatingLegDiscountHandle() {
        return collateralOnFixedLeg_ ? termStructureHandle_ : collateralHandle_;
    }

    private static Leg buildFixedLeg(
            final Date evaluationDate,
            final Period tenor,
            final int fixingDays,
            final Calendar calendar,
            final BusinessDayConvention convention,
            final boolean endOfMonth,
            final Frequency fixedFrequency,
            final DayCounter dayCount,
            final int paymentLag) {

        final Period freqPeriod = new Period(fixedFrequency);
        final Schedule sch = CrossCurrencyBasisSwapRateHelperBase.legSchedule(
                evaluationDate, tenor, freqPeriod, fixingDays, calendar, convention, endOfMonth);

        return new FixedRateLeg(sch, dayCount)
                .withNotionals(1.0)
                .withCouponRates(SAMPLE_FIXED_RATE)
                .Leg();
    }

    // -------------------------------------------------------------------------
    // BootstrapHelper interface
    // -------------------------------------------------------------------------

    @Override
    public double impliedQuote() {
        QL.require(termStructure != null, "term structure not set");
        QL.require(!collateralHandle_.empty(), "collateral term structure not set");

        final double[] fixedResult = CrossCurrencyBasisSwapRateHelperBase.npvbpsConstNotionalLeg(
                fixedLeg_,
                initialNotionalExchangeDate_,
                finalNotionalExchangeDate_,
                fixedLegDiscountHandle());

        final double[] floatResult = CrossCurrencyBasisSwapRateHelperBase.npvbpsConstNotionalLeg(
                floatLeg_,
                initialNotionalExchangeDate_,
                finalNotionalExchangeDate_,
                floatingLegDiscountHandle());

        final double fixedNpv = fixedResult[0];
        final double fixedBps = fixedResult[1];
        final double floatNpv = floatResult[0];

        QL.require(Math.abs(fixedBps) > 0.0, "null fixed-leg BPS");

        return SAMPLE_FIXED_RATE + (floatNpv - fixedNpv) / fixedBps;
    }

    // -------------------------------------------------------------------------
    // PolymorphicVisitable
    // -------------------------------------------------------------------------

    @Override
    public void accept(final PolymorphicVisitor pv) {
        final Visitor<ConstNotionalCrossCurrencySwapRateHelper> v =
                (pv != null) ? pv.visitor(this.getClass()) : null;
        if (v != null) {
            v.visit(this);
        } else {
            super.accept(pv);
        }
    }
}
