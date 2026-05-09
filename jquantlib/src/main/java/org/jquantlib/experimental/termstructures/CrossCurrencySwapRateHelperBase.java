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
    \brief FX and cross-currency basis swap rate helpers — base class
*/

package org.jquantlib.experimental.termstructures;

import org.jquantlib.cashflow.CashFlows;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.RelativeDateRateHelper;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;

/**
 * Abstract base for cross-currency swap rate helpers.
 * <p>
 * Port of C++ QuantLib v1.42.1
 * {@code ql/experimental/termstructures/crosscurrencyratehelpers.hpp}
 * {@code CrossCurrencySwapRateHelperBase}.
 * <p>
 * Provides the common fields (tenor, fixingDays, calendar, convention,
 * endOfMonth, paymentLag, collateral handle, relinkable term-structure handle)
 * and the {@link #setTermStructure} and {@link #initializeDatesFromLegs} helpers.
 */
public abstract class CrossCurrencySwapRateHelperBase extends RelativeDateRateHelper {

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    protected final Period tenor_;
    protected final int    fixingDays_;
    protected final Calendar calendar_;
    protected final BusinessDayConvention convention_;
    protected final boolean endOfMonth_;
    protected final int     paymentLag_;

    protected final Handle<YieldTermStructure> collateralHandle_;
    protected final RelinkableHandle<YieldTermStructure> termStructureHandle_ =
            new RelinkableHandle<>(null);

    /** Date of the initial notional exchange (may be offset by paymentLag). */
    protected Date initialNotionalExchangeDate_;
    /** Date of the final notional exchange (may be offset by paymentLag). */
    protected Date finalNotionalExchangeDate_;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    protected CrossCurrencySwapRateHelperBase(
            final Handle<Quote> quote,
            final Period tenor,
            final int fixingDays,
            final Calendar calendar,
            final BusinessDayConvention convention,
            final boolean endOfMonth,
            final Handle<YieldTermStructure> collateralCurve,
            final int paymentLag) {

        super(quote);
        tenor_           = tenor;
        fixingDays_      = fixingDays;
        calendar_        = calendar;
        convention_      = convention;
        endOfMonth_      = endOfMonth;
        paymentLag_      = paymentLag;
        collateralHandle_ = collateralCurve;

        collateralHandle_.currentLink().addObserver(this);
    }

    // -------------------------------------------------------------------------
    // Protected helpers
    // -------------------------------------------------------------------------

    /**
     * Sets {@code earliestDate}, {@code latestDate}, and the notional exchange
     * dates from the two legs of the swap.
     * Mirrors C++ {@code CrossCurrencySwapRateHelperBase::initializeDatesFromLegs}.
     */
    protected void initializeDatesFromLegs(final Leg firstLeg, final Leg secondLeg) {
        final CashFlows cf = CashFlows.getInstance();

        earliestDate = Date.min(cf.startDate(firstLeg), cf.startDate(secondLeg));
        latestDate   = Date.max(cf.maturityDate(firstLeg), cf.maturityDate(secondLeg));

        if (paymentLag_ == 0) {
            initialNotionalExchangeDate_ = earliestDate;
            finalNotionalExchangeDate_   = latestDate;
        } else {
            initialNotionalExchangeDate_ = calendar_.advance(
                    earliestDate, paymentLag_, org.jquantlib.time.TimeUnit.Days, convention_, false);
            finalNotionalExchangeDate_   = calendar_.advance(
                    latestDate,   paymentLag_, org.jquantlib.time.TimeUnit.Days, convention_, false);
        }

        final Date lastPaymentDate = Date.max(
                firstLeg.last().date(),
                secondLeg.last().date());

        latestDate = Date.max(latestDate, lastPaymentDate);
    }

    // -------------------------------------------------------------------------
    // Override setTermStructure
    // -------------------------------------------------------------------------

    @Override
    public void setTermStructure(final YieldTermStructure t) {
        termStructureHandle_.linkTo(t, false /* do not register as observer */);
        super.setTermStructure(t);
    }
}
