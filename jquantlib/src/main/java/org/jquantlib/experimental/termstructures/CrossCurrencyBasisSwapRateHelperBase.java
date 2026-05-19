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

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <http://www.quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
*/

/*! \file crosscurrencyratehelpers.hpp/.cpp
    \brief cross-currency basis swap rate helpers — intermediate base class
*/

package org.jquantlib.experimental.termstructures;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.cashflow.IborLeg;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.indexes.OvernightIndex;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.*;

/**
 * Abstract intermediate base for cross-currency basis swap rate helpers.
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/experimental/termstructures/crosscurrencyratehelpers.hpp}
 * {@code CrossCurrencyBasisSwapRateHelperBase}.
 * <p>
 * Holds base- and quote-currency ibor legs and exposes the discount handle selectors used by derived classes.
 */
public abstract class CrossCurrencyBasisSwapRateHelperBase extends CrossCurrencySwapRateHelperBase {

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    protected final boolean isFxBaseCurrencyCollateralCurrency_;
    protected final boolean isBasisOnFxBaseCurrencyLeg_;
    protected final Frequency paymentFrequency_;
    protected IborIndex baseCcyIdx_;
    protected IborIndex quoteCcyIdx_;
    /** Base-currency ibor leg (floating). */
    protected Leg baseCcyIborLeg_;
    /** Quote-currency ibor leg (floating). */
    protected Leg quoteCcyIborLeg_;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    protected CrossCurrencyBasisSwapRateHelperBase(final Handle< Quote > basis, final Period tenor,
            final int fixingDays, final Calendar calendar, final BusinessDayConvention convention,
            final boolean endOfMonth, final IborIndex baseCurrencyIndex, final IborIndex quoteCurrencyIndex,
            final Handle< YieldTermStructure > collateralCurve, final boolean isFxBaseCurrencyCollateralCurrency,
            final boolean isBasisOnFxBaseCurrencyLeg, final Frequency paymentFrequency, final int paymentLag) {

        super(basis, tenor, fixingDays, calendar, convention, endOfMonth, collateralCurve, paymentLag);

        baseCcyIdx_ = baseCurrencyIndex;
        quoteCcyIdx_ = quoteCurrencyIndex;
        isFxBaseCurrencyCollateralCurrency_ = isFxBaseCurrencyCollateralCurrency;
        isBasisOnFxBaseCurrencyLeg_ = isBasisOnFxBaseCurrencyLeg;
        paymentFrequency_ = paymentFrequency;

        baseCcyIdx_.addObserver(this);
        quoteCcyIdx_.addObserver(this);

        initializeDates();
    }

    // -------------------------------------------------------------------------
    // RelativeDateRateHelper interface
    // -------------------------------------------------------------------------

    /**
     * Builds a schedule from an evaluation date.
     */
    protected static Schedule legSchedule(final Date evaluationDate, final Period tenor, final Period frequency,
            final int fixingDays, final Calendar calendar, final BusinessDayConvention convention,
            final boolean endOfMonth) {

        // tenor must be >= frequency
        final Date referenceDate = calendar.adjust(evaluationDate);
        final Date earliestDate = calendar.advance(referenceDate, fixingDays, TimeUnit.Days, convention, false);
        final Date maturity = earliestDate.add(tenor);
        return new MakeSchedule(earliestDate, maturity, frequency, calendar, convention).endOfMonth(endOfMonth)
                .backwards().schedule();
    }

    // -------------------------------------------------------------------------
    // Protected helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a floating (ibor) leg for the given index, frequency, and payment lag.
     * <p>
     * Mirrors C++ {@code buildFloatingLeg} (crosscurrencyratehelpers.cpp:59-86): for overnight indices the caller must
     * supply an explicit payment frequency, otherwise the leg would use the overnight tenor (1D) which is meaningless
     * for a basis swap.
     * <p>
     * Java deviation: paymentLag is not applied to coupon dates (JQuantLib's {@link IborLeg} builder does not expose
     * {@code withPaymentLag} in the legacy interface used here); payment dates are therefore coincident with accrual
     * end dates.
     */
    protected static Leg buildFloatingLeg(final Date evaluationDate, final Period tenor, final int fixingDays,
            final Calendar calendar, final BusinessDayConvention convention, final boolean endOfMonth,
            final IborIndex idx, final Frequency paymentFrequency, final int paymentLag) {

        final boolean isOvernight = idx instanceof OvernightIndex;
        final Period freqPeriod;
        if ( paymentFrequency == Frequency.NoFrequency ) {
            QL.require(!isOvernight, "Require payment frequency for overnight indices.");
            freqPeriod = idx.tenor();
        } else {
            freqPeriod = new Period(paymentFrequency);
        }

        final Schedule sch = legSchedule(evaluationDate, tenor, freqPeriod, fixingDays, calendar, convention,
                endOfMonth);

        return new IborLeg(sch, idx).withNotionals(1.0).Leg();
    }

    /**
     * Computes (NPV, BPS) for a constant-notional leg, including the initial (-1) and final (+1) notional exchange cash
     * flows.
     * <p>
     * Mirrors C++ anonymous {@code npvbpsConstNotionalLeg} (ql/experimental/termstructures/crosscurrencyratehelpers.cpp
     * v1.42.1 lines 108-123). The C++ implementation delegates to {@code CashFlows::npvbps(...)}, which returns
     * {@code bps = basisPoint_ * sum(nominal*accrual*df) / d}, and then undoes the {@code basisPoint_} factor via
     * {@code bps /= basisPoint;}. Because the caller pins {@code npvDate = settlementDate = referenceDate}, the divisor
     * {@code d = discount(referenceDate) = 1.0}, so the net result is {@code bps = sum(nominal*accrual*df)} — i.e. the
     * raw PV01-without-basisPoint-scaling, consistent with {@code npvbpsResettingLeg}. The {@code impliedQuote()}
     * formula {@code -(npvQuote - npvBase) / bps} relies on these matched scales.
     */
    protected static double[] npvbpsConstNotionalLeg(final Leg leg, final Date initialNotionalExchangeDate,
            final Date finalNotionalExchangeDate, final Handle< YieldTermStructure > discountCurveHandle) {

        final YieldTermStructure disc = discountCurveHandle.currentLink();
        final Date refDt = disc.referenceDate();

        double npv = 0.0;
        double bps = 0.0;

        for ( org.jquantlib.cashflow.CashFlow cf : leg ) {
            if ( !cf.date().lt(refDt) ) {
                final double df = disc.discount(cf.date());
                npv += cf.amount() * df;
                if ( cf instanceof org.jquantlib.cashflow.Coupon ) {
                    final org.jquantlib.cashflow.Coupon c = (org.jquantlib.cashflow.Coupon) cf;
                    bps += c.nominal() * c.accrualPeriod() * df;
                }
            }
        }

        // Include notional exchange at start (pay) and maturity (receive)
        npv += (-1.0) * disc.discount(initialNotionalExchangeDate);
        npv += disc.discount(finalNotionalExchangeDate);
        // bps is kept in raw {nominal*accrual*df} units; see contract above.

        return new double[] { npv, bps };
    }

    // -------------------------------------------------------------------------
    // Internal schedule/leg builder (reused by ConstNotionalCrossCurrencySwapRateHelper)
    // -------------------------------------------------------------------------

    /**
     * Computes (NPV, BPS) for a resetting (MtM) leg.
     * <p>
     * Mirrors C++ anonymous {@code npvbpsResettingLeg}.
     * <p>
     * For each coupon the notional is adjusted by the forward FX rate (ratio of foreign and domestic discount
     * factors).
     */
    protected static double[] npvbpsResettingLeg(final Leg iborLeg, final int paymentLag, final Calendar calendar,
            final BusinessDayConvention convention, final Handle< YieldTermStructure > discountCurveHandle,
            final Handle< YieldTermStructure > foreignCurveHandle) {

        final YieldTermStructure discountCurve = discountCurveHandle.currentLink();
        final YieldTermStructure foreignCurve = foreignCurveHandle.currentLink();

        double npv = 0.0;
        double bps = 0.0;

        for ( org.jquantlib.cashflow.CashFlow cashFlow : iborLeg ) {
            if ( !(cashFlow instanceof org.jquantlib.cashflow.Coupon) )
                continue;

            final org.jquantlib.cashflow.Coupon c = (org.jquantlib.cashflow.Coupon) cashFlow;
            final Date start = c.accrualStartDate();
            final Date end = c.accrualEndDate();
            final double accrual = c.accrualPeriod();

            // Forward FX adjustment: ratio of foreign / domestic discount factors
            final double adjustedNotional = c.nominal() * foreignCurve.discount(start) / discountCurve.discount(start);

            final double discountStart, discountEnd;
            if ( paymentLag == 0 ) {
                discountStart = discountCurve.discount(start);
                discountEnd = discountCurve.discount(end);
            } else {
                final Date payStart = calendar.advance(start, paymentLag, TimeUnit.Days, convention, false);
                final Date payEnd = calendar.advance(end, paymentLag, TimeUnit.Days, convention, false);
                discountStart = discountCurve.discount(payStart);
                discountEnd = discountCurve.discount(payEnd);
            }

            // NPV = adjustedNotional * discountEnd * (1 + rate * accrual) - adjustedNotional * discountStart
            final double npvRedeemed = adjustedNotional * discountEnd * (1.0 + c.rate() * accrual);
            final double npvBorrowed = -adjustedNotional * discountStart;
            npv += npvRedeemed + npvBorrowed;
            bps += adjustedNotional * discountEnd * accrual;
        }

        return new double[] { npv, bps };
    }

    @Override
    protected void initializeDates() {
        final Date evaluationDate = new Settings().evaluationDate();

        baseCcyIborLeg_ = buildFloatingLeg(evaluationDate, tenor_, fixingDays_, calendar_, convention_, endOfMonth_,
                baseCcyIdx_, paymentFrequency_, paymentLag_);

        quoteCcyIborLeg_ = buildFloatingLeg(evaluationDate, tenor_, fixingDays_, calendar_, convention_, endOfMonth_,
                quoteCcyIdx_, paymentFrequency_, paymentLag_);

        initializeDatesFromLegs(baseCcyIborLeg_, quoteCcyIborLeg_);
    }

    // -------------------------------------------------------------------------
    // NPV/BPS helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the discount handle for the base-currency leg. Mirrors C++
     * {@code CrossCurrencyBasisSwapRateHelperBase::baseCcyLegDiscountHandle}.
     */
    protected Handle< YieldTermStructure > baseCcyLegDiscountHandle() {
        return isFxBaseCurrencyCollateralCurrency_ ? collateralHandle_ : termStructureHandle_;
    }

    /**
     * Returns the discount handle for the quote-currency leg. Mirrors C++
     * {@code CrossCurrencyBasisSwapRateHelperBase::quoteCcyLegDiscountHandle}.
     */
    protected Handle< YieldTermStructure > quoteCcyLegDiscountHandle() {
        return isFxBaseCurrencyCollateralCurrency_ ? termStructureHandle_ : collateralHandle_;
    }
}
