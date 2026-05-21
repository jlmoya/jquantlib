/*
 Copyright (C) 2008 Srinivas Hasti
 Copyright (C) 2010 NeelSheyal
 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

package org.jquantlib.termstructures.yieldcurves;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.cashflow.FloatingRateCoupon;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.indexes.SwapIndex;
import org.jquantlib.instruments.MakeVanillaSwap;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.*;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;

/**
 * Rate helper for bootstrapping over swap rates
 *
 * @author Richard Gomes
 * @author Neel Sheyal
 */
//
public class SwapRateHelper extends RelativeDateRateHelper {

    private static final /*@Spread*/ double basisPoint = 1.0e-4;

    protected final Period tenor;
    protected final Calendar calendar;
    protected final BusinessDayConvention fixedConvention;
    protected final Frequency fixedFrequency;
    protected final DayCounter fixedDayCount;
    protected final IborIndex iborIndex;
    protected final Handle< Quote > spread;
    protected final Period fwdStart;
    /** Optional caller-supplied effective date (date-based ctor); null otherwise. */
    protected final Date explicitStartDate;
    /** Optional caller-supplied termination date (date-based ctor); null otherwise. */
    protected final Date explicitEndDate;
    protected VanillaSwap swap;
    protected RelinkableHandle< YieldTermStructure > termStructureHandle = new RelinkableHandle< YieldTermStructure >(
            null);

    //
    // public constructors
    //

    public SwapRateHelper(final Handle< Quote > rate, final SwapIndex swapIndex) {
        this(rate, swapIndex, new Handle< Quote >(), new Period(0, TimeUnit.Days));
    }

    public SwapRateHelper(final Handle< Quote > rate, final SwapIndex swapIndex, final Handle< Quote > spread) {
        this(rate, swapIndex, spread, new Period(0, TimeUnit.Days));
    }

    public SwapRateHelper(final Handle< Quote > rate, final SwapIndex swapIndex, final Handle< Quote > spread,
            final Period fwdStart) {
        super(rate);

        this.tenor = swapIndex.tenor();
        this.calendar = swapIndex.fixingCalendar();
        this.fixedConvention = swapIndex.fixedLegConvention();
        this.fixedFrequency = swapIndex.fixedLegTenor().frequency();
        this.fixedDayCount = swapIndex.dayCounter();
        this.iborIndex = swapIndex.iborIndex();
        this.spread = spread;
        this.fwdStart = fwdStart;
        this.explicitStartDate = null;
        this.explicitEndDate = null;

        this.iborIndex.addObserver(this);
        this.spread.addObserver(this);

        initializeDates();
    }

    public SwapRateHelper(final Handle< Quote > rate, final Period tenor, final Calendar calendar,
            final Frequency fixedFrequency, final BusinessDayConvention fixedConvention, final DayCounter fixedDayCount,
            final IborIndex iborIndex) {
        this(rate, tenor, calendar, fixedFrequency, fixedConvention, fixedDayCount, iborIndex, new Handle< Quote >(),
                new Period(0, TimeUnit.Days));
    }

    public SwapRateHelper(final Handle< Quote > rate, final Period tenor, final Calendar calendar,
            final Frequency fixedFrequency, final BusinessDayConvention fixedConvention, final DayCounter fixedDayCount,
            final IborIndex iborIndex, final Handle< Quote > spread) {
        this(rate, tenor, calendar, fixedFrequency, fixedConvention, fixedDayCount, iborIndex, spread,
                new Period(0, TimeUnit.Days));
    }

    public SwapRateHelper(final Handle< Quote > rate, final Period tenor, final Calendar calendar,
            final Frequency fixedFrequency, final BusinessDayConvention fixedConvention, final DayCounter fixedDayCount,
            final IborIndex iborIndex, final Handle< Quote > spread, final Period fwdStart) {
        super(rate);

        this.tenor = tenor;
        this.calendar = calendar;
        this.fixedConvention = fixedConvention;
        this.fixedFrequency = fixedFrequency;
        this.fixedDayCount = fixedDayCount;
        this.iborIndex = iborIndex;
        this.spread = spread;
        this.fwdStart = fwdStart;
        this.explicitStartDate = null;
        this.explicitEndDate = null;

        this.iborIndex.addObserver(this);
        this.spread.addObserver(this);

        initializeDates();
    }

    public SwapRateHelper(final /*@Rate*/ double rate, final Period tenor, final Calendar calendar,
            final Frequency fixedFrequency, final BusinessDayConvention fixedConvention, final DayCounter fixedDayCount,
            final IborIndex iborIndex) {
        this(rate, tenor, calendar, fixedFrequency, fixedConvention, fixedDayCount, iborIndex, new Handle< Quote >(),
                new Period(0, TimeUnit.Days));
    }

    public SwapRateHelper(final /*@Rate*/ double rate, final Period tenor, final Calendar calendar,
            final Frequency fixedFrequency, final BusinessDayConvention fixedConvention, final DayCounter fixedDayCount,
            final IborIndex iborIndex, final Handle< Quote > spread) {
        this(rate, tenor, calendar, fixedFrequency, fixedConvention, fixedDayCount, iborIndex, spread,
                new Period(0, TimeUnit.Days));
    }

    public SwapRateHelper(final /*@Rate*/ double rate, final Period tenor, final Calendar calendar,
            final Frequency fixedFrequency, final BusinessDayConvention fixedConvention, final DayCounter fixedDayCount,
            final IborIndex iborIndex, final Handle< Quote > spread, final Period fwdStart) {
        super(rate);

        this.tenor = tenor;
        this.calendar = calendar;
        this.fixedConvention = fixedConvention;
        this.fixedFrequency = fixedFrequency;
        this.fixedDayCount = fixedDayCount;
        this.iborIndex = iborIndex;
        this.spread = spread;
        this.fwdStart = fwdStart;
        this.explicitStartDate = null;
        this.explicitEndDate = null;

        this.iborIndex.addObserver(this);
        this.spread.addObserver(this);

        initializeDates();
    }

    public SwapRateHelper(final /*@Rate*/ double rate, final SwapIndex swapIndex) {
        this(rate, swapIndex, new Handle< Quote >(), new Period(0, TimeUnit.Days));
    }

    public SwapRateHelper(final /*@Rate*/ double rate, final SwapIndex swapIndex, final Handle< Quote > spread) {
        this(rate, swapIndex, spread, new Period(0, TimeUnit.Days));
    }

    public SwapRateHelper(final /*@Rate*/ double rate, final SwapIndex swapIndex, final Handle< Quote > spread,
            final Period fwdStart) {
        super(rate);

        this.tenor = swapIndex.tenor();
        this.calendar = swapIndex.fixingCalendar();
        this.fixedConvention = swapIndex.fixedLegConvention();
        this.fixedFrequency = swapIndex.fixedLegTenor().frequency();
        this.fixedDayCount = swapIndex.dayCounter();
        this.iborIndex = swapIndex.iborIndex();
        this.spread = spread;
        this.fwdStart = fwdStart;
        this.explicitStartDate = null;
        this.explicitEndDate = null;

        this.iborIndex.addObserver(this);
        this.spread.addObserver(this);

        initializeDates();
    }

    /**
     * Dated SwapRateHelper ctor — mirrors C++ v1.42.1 ratehelpers.hpp:229-246
     * + ratehelpers.cpp:501-526. Pins both effective and termination dates to
     * caller-supplied absolutes; super-ctor flag {@code updateDates=false} prevents
     * recomputation on evaluation-date change.
     *
     * @param rate market quote (handle)
     * @param startDate effective date (absolute, caller-supplied)
     * @param endDate termination date (absolute, caller-supplied)
     * @param calendar swap calendar
     * @param fixedFrequency fixed-leg frequency
     * @param fixedConvention fixed-leg business-day convention
     * @param fixedDayCount fixed-leg day-counter
     * @param iborIndex floating-leg ibor index
     */
    public SwapRateHelper(final Handle< Quote > rate,
            final Date startDate, final Date endDate,
            final Calendar calendar,
            final Frequency fixedFrequency,
            final BusinessDayConvention fixedConvention,
            final DayCounter fixedDayCount,
            final IborIndex iborIndex) {
        this(rate, startDate, endDate, calendar, fixedFrequency, fixedConvention,
                fixedDayCount, iborIndex, new Handle< Quote >());
    }

    /**
     * Dated SwapRateHelper ctor with spread — see
     * {@link #SwapRateHelper(Handle, Date, Date, Calendar, Frequency, BusinessDayConvention, DayCounter, IborIndex)}.
     */
    public SwapRateHelper(final Handle< Quote > rate,
            final Date startDate, final Date endDate,
            final Calendar calendar,
            final Frequency fixedFrequency,
            final BusinessDayConvention fixedConvention,
            final DayCounter fixedDayCount,
            final IborIndex iborIndex,
            final Handle< Quote > spread) {
        super(rate, false); // updateDates=false: do NOT recompute dates on evaluation-date change
        QL.require(fixedFrequency != Frequency.Once,
                "fixedFrequency == Once is not supported when passing explicit startDate and endDate");

        this.tenor = new Period(0, TimeUnit.Days); // not used when explicit dates supplied
        this.calendar = calendar;
        this.fixedConvention = fixedConvention;
        this.fixedFrequency = fixedFrequency;
        this.fixedDayCount = fixedDayCount;
        this.iborIndex = iborIndex;
        this.spread = spread;
        this.fwdStart = new Period(0, TimeUnit.Days);
        this.explicitStartDate = startDate;
        this.explicitEndDate = endDate;

        this.iborIndex.addObserver(this);
        this.spread.addObserver(this);

        initializeDates();
    }

    //
    // protected methods
    //

    /**
     *
     * @see org.jquantlib.termstructures.yield.RelativeDateRateHelper#initializeDates()
     */
    @Override
    protected void initializeDates() {
        // dummy ibor index with curve/swap arguments
        final IborIndex clonedIborIndex = iborIndex.clone(this.termStructureHandle).currentLink();

        // do not pass the spread here, as it might be a Quote i.e. it can dynamically change
        // Mirrors C++ v1.42.1 ratehelpers.cpp:545-606: when explicit start/end dates are
        // supplied (dated ctor), feed them to MakeVanillaSwap via withEffective/Termination.
        // Mirror C++ v1.42.1 ratehelpers.cpp:556 — when fixedFrequency == Once
        // the fixed leg uses the swap tenor itself as a single coupon, rather
        // than Period(Once) (which would be 0 Years).
        final Period fixedLegTenor = (fixedFrequency == Frequency.Once) ? tenor : new Period(fixedFrequency);
        MakeVanillaSwap mvs = new MakeVanillaSwap(tenor, clonedIborIndex, 0.0, fwdStart)
                .withFixedLegDayCount(fixedDayCount)
                .withFixedLegTenor(fixedLegTenor)
                .withFixedLegConvention(fixedConvention)
                .withFixedLegTerminationDateConvention(fixedConvention)
                .withFixedLegCalendar(calendar)
                .withFloatingLegCalendar(calendar);
        if ( this.explicitStartDate != null ) {
            mvs = mvs.withEffectiveDate(this.explicitStartDate);
        }
        if ( this.explicitEndDate != null ) {
            mvs = mvs.withTerminationDate(this.explicitEndDate);
        }
        this.swap = mvs.value();

        this.earliestDate = swap.startDate();

        // Usually...
        this.latestDate = swap.maturityDate();

        // ...but due to adjustments, the last floating coupon might need a later date for fixing
        if ( new Settings().isUseIndexedCoupon() ) {
            final FloatingRateCoupon lastFloating = (FloatingRateCoupon) this.swap.floatingLeg().last();
            final Date fixingValueDate = this.iborIndex.valueDate(lastFloating.fixingDate());
            final Date endValueDate = this.iborIndex.maturityDate(fixingValueDate);
            this.latestDate = Date.max(latestDate, endValueDate);
        }
    }

    /**
     * Do not set the relinkable handle as an observer. Force recalculation when needed
     *
     * @param t
     * @see org.jquantlib.termstructures.BootstrapHelper#setTermStructure
     */
    @Override
    public void setTermStructure(final YieldTermStructure t) {
        this.termStructureHandle.linkTo(t, false);
        super.setTermStructure(t);
    }

    /**
     * @see org.jquantlib.termstructures.BootstrapHelper#getImpliedQuote()
     */
    @Override
    public /*@Real*/ double impliedQuote() /* @ReadOnly */ {
        QL.require(termStructure != null, "term structure not set");

        // we didn't register as observers - force calculation
        swap.recalculate();

        // weak implementation... to be improved
        /*@Real*/
        final double floatingLegNPV = swap.floatingLegNPV();
        /*@Spread*/
        final double spread = this.spread.empty() ? 0.0 : this.spread.currentLink().value();
        /*@Real*/
        final double spreadNPV = swap.floatingLegBPS() / basisPoint * spread;
        /*@Real*/
        final double totNPV = -(floatingLegNPV + spreadNPV);
        /*@Real*/
        final double result = totNPV / (swap.fixedLegBPS() / basisPoint);
        return result;
    }

    public /*@Spread*/ double spread() /* @ReadOnly */ {
        return this.spread.empty() ? 0.0 : spread.currentLink().value();
    }

    public VanillaSwap swap() /* @ReadOnly */ {
        return this.swap;
    }

    public final Period forwardStart() /* @ReadOnly */ {
        return this.fwdStart;
    }

    //
    // implements PolymorphicVisitable
    //

    @Override
    public void accept(final PolymorphicVisitor pv) {
        final Visitor< SwapRateHelper > v = (pv != null) ? pv.visitor(this.getClass()) : null;
        if ( v != null ) {
            v.visit(this);
        } else {
            super.accept(pv);
        }
    }

}
