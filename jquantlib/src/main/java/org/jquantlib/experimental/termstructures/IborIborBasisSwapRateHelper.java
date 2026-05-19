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
    \brief ibor-ibor basis swap rate helper
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
import org.jquantlib.time.*;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;

/**
 * Rate helper for bootstrapping over ibor-ibor basis swaps.
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/experimental/termstructures/basisswapratehelpers.hpp/.cpp}.
 * <p>
 * The swap is assumed to pay {@code baseIndex + basis} and receive {@code otherIndex}. Set
 * {@code bootstrapBaseCurve = true} to bootstrap the forecast curve for {@code baseIndex} (the other index must already
 * have a forecast curve); set it to {@code false} to bootstrap the forecast curve for {@code otherIndex}. An exogenous
 * discount curve is required in both cases.
 */
public class IborIborBasisSwapRateHelper extends RelativeDateRateHelper {

    private static final double BASIS_POINT = 1.0e-4;

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    protected final Period tenor_;
    protected final int settlementDays_;
    protected final Calendar calendar_;
    protected final BusinessDayConvention convention_;
    protected final boolean endOfMonth_;
    protected final Handle< YieldTermStructure > discountHandle_;
    protected final boolean bootstrapBaseCurve_;
    protected final RelinkableHandle< YieldTermStructure > termStructureHandle_ = new RelinkableHandle<>(null);
    protected IborIndex baseIndex_;
    protected IborIndex otherIndex_;
    protected Swap swap_;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * @param basis              quoted basis spread
     * @param tenor              swap tenor
     * @param settlementDays     settlement-lag in days
     * @param calendar           calendar
     * @param convention         business-day convention for payment dates
     * @param endOfMonth         end-of-month adjustment flag
     * @param baseIndex          base ibor index (pays base + basis)
     * @param otherIndex         other ibor index (receives other)
     * @param discountHandle     exogenous discount curve
     * @param bootstrapBaseCurve if true bootstrap base index forecast curve; if false bootstrap other index forecast
     *                           curve
     */
    public IborIborBasisSwapRateHelper(final Handle< Quote > basis, final Period tenor, final int settlementDays,
            final Calendar calendar, final BusinessDayConvention convention, final boolean endOfMonth,
            final IborIndex baseIndex, final IborIndex otherIndex, final Handle< YieldTermStructure > discountHandle,
            final boolean bootstrapBaseCurve) {

        super(basis);
        tenor_ = tenor;
        settlementDays_ = settlementDays;
        calendar_ = calendar;
        convention_ = convention;
        endOfMonth_ = endOfMonth;
        discountHandle_ = discountHandle;
        bootstrapBaseCurve_ = bootstrapBaseCurve;

        // Clone the index whose forecast curve we want to bootstrap
        if ( bootstrapBaseCurve_ ) {
            baseIndex_ = baseIndex.clone(termStructureHandle_).currentLink();
            baseIndex_.deleteObserver(this);  // unregister from termStructureHandle_
            otherIndex_ = otherIndex;
        } else {
            baseIndex_ = baseIndex;
            otherIndex_ = otherIndex.clone(termStructureHandle_).currentLink();
            otherIndex_.deleteObserver(this);
        }

        baseIndex_.addObserver(this);
        otherIndex_.addObserver(this);
        discountHandle_.currentLink().addObserver(this);

        initializeDates();
    }

    // -------------------------------------------------------------------------
    // RelativeDateRateHelper interface
    // -------------------------------------------------------------------------

    @Override
    protected void initializeDates() {
        final Date today = new Settings().evaluationDate();
        earliestDate = calendar_.advance(today, settlementDays_, TimeUnit.Days, BusinessDayConvention.Following, false);
        latestDate = calendar_.advance(earliestDate, tenor_, convention_, endOfMonth_);

        final Schedule baseSchedule = new MakeSchedule(earliestDate, latestDate, baseIndex_.tenor(), calendar_,
                convention_).endOfMonth(endOfMonth_).forwards().schedule();

        final Leg baseLeg = new IborLeg(baseSchedule, baseIndex_).withNotionals(100.0).Leg();

        final Schedule otherSchedule = new MakeSchedule(earliestDate, latestDate, otherIndex_.tenor(), calendar_,
                convention_).endOfMonth(endOfMonth_).forwards().schedule();

        final Leg otherLeg = new IborLeg(otherSchedule, otherIndex_).withNotionals(100.0).Leg();

        // latestDate = max(maturity, lastFixingEndDate)
        // C++ IborCoupon::fixingEndDate() = index->maturityDate(index->valueDate(fixingDate()))
        final IborCoupon lastBase = (IborCoupon) baseLeg.last();
        final IborCoupon lastOther = (IborCoupon) otherLeg.last();
        final Date baseFixingEnd = baseIndex_.maturityDate(baseIndex_.valueDate(lastBase.fixingDate()));
        final Date otherFixingEnd = otherIndex_.maturityDate(otherIndex_.valueDate(lastOther.fixingDate()));
        latestDate = Date.max(latestDate, Date.max(baseFixingEnd, otherFixingEnd));

        swap_ = new Swap(baseLeg, otherLeg);
        swap_.setPricingEngine(new DiscountingSwapEngine(discountHandle_));
    }

    @Override
    public void setTermStructure(final YieldTermStructure t) {
        termStructureHandle_.linkTo(t, false /* do not register as observer */);
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
        final Visitor< IborIborBasisSwapRateHelper > v = (pv != null) ? pv.visitor(this.getClass()) : null;
        if ( v != null ) {
            v.visit(this);
        } else {
            super.accept(pv);
        }
    }
}
