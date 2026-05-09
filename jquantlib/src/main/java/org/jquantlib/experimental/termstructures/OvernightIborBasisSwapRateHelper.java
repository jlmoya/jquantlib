/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2021 StatPro Italia srl

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

/*! \file basisswapratehelpers.hpp/.cpp
    \brief overnight-ibor basis swap rate helper
*/

package org.jquantlib.experimental.termstructures;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.cashflow.IborCoupon;
import org.jquantlib.cashflow.IborLeg;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.instruments.Swap;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.RelativeDateRateHelper;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.MakeSchedule;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;

/**
 * Rate helper for bootstrapping over overnight-ibor basis swaps.
 * <p>
 * Port of C++ QuantLib v1.42.1
 * {@code ql/experimental/termstructures/basisswapratehelpers.hpp/.cpp}
 * {@code OvernightIborBasisSwapRateHelper}.
 * <p>
 * The swap is assumed to pay {@code baseIndex + basis} and receive
 * {@code otherIndex}. The helper bootstraps the forecast curve for
 * {@code otherIndex}; {@code baseIndex} (the overnight index) must already
 * have a forecast curve. An exogenous discount curve may be provided; if not,
 * the overnight-index curve is used for discounting.
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 * <li>C++ uses {@code OvernightIndex} and {@code OvernightLeg} for the base
 *     leg with compounded overnight coupons. JQuantLib does not yet have
 *     {@code OvernightIndex} or {@code OvernightLeg}, so the base leg is
 *     approximated here as a plain {@link IborLeg} treating the overnight
 *     index as a 1-day IborIndex. Bootstrap results will differ from the
 *     C++ version when overnight compounding is material.
 * </ul>
 */
public class OvernightIborBasisSwapRateHelper extends RelativeDateRateHelper {

    private static final double BASIS_POINT = 1.0e-4;

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    protected final Period tenor_;
    protected final int    settlementDays_;
    protected final Calendar calendar_;
    protected final BusinessDayConvention convention_;
    protected final boolean endOfMonth_;

    /**
     * Overnight base index. In C++ this is typed {@code OvernightIndex}; here
     * it is stored as plain {@link IborIndex} for compatibility.
     */
    protected final IborIndex baseIndex_;
    protected IborIndex otherIndex_;

    protected final Handle<YieldTermStructure> discountHandle_;

    protected Swap swap_;
    protected final RelinkableHandle<YieldTermStructure> termStructureHandle_ =
            new RelinkableHandle<>(null);

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Constructor with explicit discount handle.
     *
     * @param basis          quoted basis spread
     * @param tenor          swap tenor
     * @param settlementDays settlement-lag in days
     * @param calendar       calendar
     * @param convention     business-day convention
     * @param endOfMonth     end-of-month flag
     * @param baseIndex      overnight base index
     * @param otherIndex     ibor index to bootstrap
     * @param discountHandle exogenous discount curve (empty = use base curve)
     */
    public OvernightIborBasisSwapRateHelper(
            final Handle<Quote> basis,
            final Period tenor,
            final int settlementDays,
            final Calendar calendar,
            final BusinessDayConvention convention,
            final boolean endOfMonth,
            final IborIndex baseIndex,
            final IborIndex otherIndex,
            final Handle<YieldTermStructure> discountHandle) {

        super(basis);
        tenor_          = tenor;
        settlementDays_ = settlementDays;
        calendar_       = calendar;
        convention_     = convention;
        endOfMonth_     = endOfMonth;
        baseIndex_      = baseIndex;
        discountHandle_ = discountHandle;

        // Clone the other index onto the bootstrap term-structure handle
        otherIndex_ = otherIndex.clone(termStructureHandle_).currentLink();
        otherIndex_.deleteObserver(this);

        baseIndex_.addObserver(this);
        otherIndex_.addObserver(this);
        if (!discountHandle_.empty()) {
            discountHandle_.currentLink().addObserver(this);
        }

        initializeDates();
    }

    /**
     * Convenience constructor without explicit discount curve (uses base curve).
     */
    public OvernightIborBasisSwapRateHelper(
            final Handle<Quote> basis,
            final Period tenor,
            final int settlementDays,
            final Calendar calendar,
            final BusinessDayConvention convention,
            final boolean endOfMonth,
            final IborIndex baseIndex,
            final IborIndex otherIndex) {

        this(basis, tenor, settlementDays, calendar, convention, endOfMonth,
             baseIndex, otherIndex, new Handle<>());
    }

    // -------------------------------------------------------------------------
    // RelativeDateRateHelper interface
    // -------------------------------------------------------------------------

    @Override
    protected void initializeDates() {
        final Date today = new Settings().evaluationDate();
        earliestDate = calendar_.advance(today, settlementDays_, TimeUnit.Days,
                BusinessDayConvention.Following, false);
        latestDate = calendar_.advance(earliestDate, tenor_, convention_, endOfMonth_);

        // Build schedule based on other (ibor) index tenor
        final Schedule schedule = new MakeSchedule(
                earliestDate, latestDate,
                otherIndex_.tenor(), calendar_, convention_)
                .endOfMonth(endOfMonth_)
                .forwards()
                .schedule();

        // Base leg: overnight (approximated as IborLeg for Java portability)
        final Leg baseLeg = new IborLeg(schedule, baseIndex_)
                .withNotionals(100.0)
                .Leg();

        // Other leg: ibor
        final Leg otherLeg = new IborLeg(schedule, otherIndex_)
                .withNotionals(100.0)
                .Leg();

        // Extend latestDate to cover the last ibor fixing end date
        final IborCoupon lastOther = (IborCoupon) otherLeg.last();
        final Date otherFixingEnd  = otherIndex_.maturityDate(
                otherIndex_.valueDate(lastOther.fixingDate()));
        latestDate = Date.max(latestDate, otherFixingEnd);

        // Discount handle: use exogenous if provided, otherwise use the bootstrapped curve
        final Handle<YieldTermStructure> discountForEngine =
                discountHandle_.empty() ? termStructureHandle_ : discountHandle_;

        swap_ = new Swap(baseLeg, otherLeg);
        swap_.setPricingEngine(new DiscountingSwapEngine(discountForEngine));
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
        return -(swap_.NPV() / swap_.legBPS(0)) * BASIS_POINT;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the internal bootstrap swap instrument.
     */
    public Swap swap() {
        return swap_;
    }

    // -------------------------------------------------------------------------
    // PolymorphicVisitable
    // -------------------------------------------------------------------------

    @Override
    public void accept(final PolymorphicVisitor pv) {
        final Visitor<OvernightIborBasisSwapRateHelper> v =
                (pv != null) ? pv.visitor(this.getClass()) : null;
        if (v != null) {
            v.visit(this);
        } else {
            super.accept(pv);
        }
    }
}
