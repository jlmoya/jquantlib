/*
 Copyright (C) 2008 Srinivas Hasti
 Copyright (C) 2010 Neel Sheyal  

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
import org.jquantlib.currencies.Currency;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.termstructures.Pillar;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.*;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;

/**
 * Rate helper for bootstrapping over IborIndex futures prices
 *
 * @author Srinivas Hasti
 * @author Neel Sheyal
 */

public class FraRateHelper extends RelativeDateRateHelper {

    /** Period-to-start (legacy ctors); {@code null} for dated ctor. */
    private Period periodToStart;
    private final IborIndex iborIndex;
    private final RelinkableHandle< YieldTermStructure > termStructureHandle = new RelinkableHandle< YieldTermStructure >(
            null);
    //
    // private fields
    //
    private Date fixingDate;

    /**
     * Caller-supplied effective date (only for dated ctor); null otherwise.
     * Mirrors C++ v1.42.1 ratehelpers.cpp:340-358 dated FraRateHelper overload.
     */
    private Date explicitStartDate = null;
    /** Caller-supplied termination date (only for dated ctor); null otherwise. */
    private Date explicitEndDate = null;
    /** Pillar choice (date-based ctor); legacy ctors keep historical "LastRelevantDate" default. */
    private Pillar.Choice pillarChoice = Pillar.Choice.LastRelevantDate;
    /** When false, uses the forecast-rate path; mirrors C++ {@code useIndexedCoupon_}. */
    private boolean useIndexedCoupon = true;
    /** Year fraction used by the forecast-rate path. */
    private double spanningTime;

    //
    // public constructors
    //

    public FraRateHelper(final Handle< Quote > rate, final/* @Natural */int monthsToStart,
            final/* @Natural */int monthsToEnd, final/* @Natural */int fixingDays, final Calendar calendar,
            final BusinessDayConvention convention, final boolean endOfMonth, final DayCounter dayCounter) {

        super(rate);
        this.periodToStart = new Period(monthsToStart, TimeUnit.Months);

        QL.require(monthsToEnd > monthsToStart, "monthsToEnd must be greater than monthsToStart");

        this.iborIndex = new IborIndex("no-fix", // never take fixing into account
                new Period(monthsToEnd - monthsToStart, TimeUnit.Months), fixingDays, new Currency(), calendar,
                convention, endOfMonth, dayCounter, this.termStructureHandle);

        initializeDates();
    }

    public FraRateHelper(final/* @Rate */double rate, final/* @Natural */int monthsToStart,
            final/* @Natural */int monthsToEnd, final/* @Natural */int fixingDays, final Calendar calendar,
            final BusinessDayConvention convention, final boolean endOfMonth, final DayCounter dayCounter) {

        super(rate);
        this.periodToStart = new Period(monthsToStart, TimeUnit.Months);

        QL.require(monthsToEnd > monthsToStart, "monthsToEnd must be greater than monthsToStart");

        iborIndex = new IborIndex("no-fix", // never take fixing into account
                new Period(monthsToEnd - monthsToStart, TimeUnit.Months), fixingDays, new Currency(), calendar,
                convention, endOfMonth, dayCounter, this.termStructureHandle);

        initializeDates();
    }

    public FraRateHelper(final Handle< Quote > rate, final/* @Natural */int monthsToStart, final IborIndex i) {

        super(rate);
        this.periodToStart = new Period(monthsToStart, TimeUnit.Months);

        iborIndex = new IborIndex("no-fix", // never take fixing into account
                i.tenor(), i.fixingDays(), new Currency(), i.fixingCalendar(), i.businessDayConvention(),
                i.endOfMonth(), i.dayCounter(), this.termStructureHandle);

        initializeDates();
    }

    public FraRateHelper(final/* @Rate */double rate, final/* @Natural */int monthsToStart, final IborIndex i) {

        super(rate);
        this.periodToStart = new Period(monthsToStart, TimeUnit.Months);

        iborIndex = new IborIndex("no-fix", // never take fixing into account
                i.tenor(), i.fixingDays(), new Currency(), i.fixingCalendar(), i.businessDayConvention(),
                i.endOfMonth(), i.dayCounter(), this.termStructureHandle);

        initializeDates();
    }

    public FraRateHelper(final Handle< Quote > rate, final Period periodToStart, final/* @Natural */int lengthInMonths,
            final/* @Natural */int fixingDays, final Calendar calendar, final BusinessDayConvention convention,
            final boolean endOfMonth, final DayCounter dayCounter) {

        super(rate);
        this.periodToStart = periodToStart;

        iborIndex = new IborIndex("no-fix", // never take fixing into account
                new Period(lengthInMonths, TimeUnit.Months), fixingDays, new Currency(), calendar, convention,
                endOfMonth, dayCounter, this.termStructureHandle);

        initializeDates();

    }

    public FraRateHelper(final/* @Rate */double rate, final Period periodToStart, final/* @Natural */int lengthInMonths,
            final/* @Natural */int fixingDays, final Calendar calendar, final BusinessDayConvention convention,
            final boolean endOfMonth, final DayCounter dayCounter) {

        super(rate);
        this.periodToStart = periodToStart;

        iborIndex = new IborIndex("no-fix", // never take fixing into account
                new Period(lengthInMonths, TimeUnit.Months), fixingDays, new Currency(), calendar, convention,
                endOfMonth, dayCounter, this.termStructureHandle);
        initializeDates();

    }

    public FraRateHelper(final Handle< Quote > rate, final Period periodToStart, final IborIndex i) {

        super(rate);
        this.periodToStart = periodToStart;

        iborIndex = new IborIndex("no-fix",// never take fixing into account
                i.tenor(), i.fixingDays(), new Currency(), i.fixingCalendar(), i.businessDayConvention(),
                i.endOfMonth(), i.dayCounter(), this.termStructureHandle);

        initializeDates();

    }

    public FraRateHelper(final/* @Rate */double rate, final Period periodToStart, final IborIndex i) {

        super(rate);
        this.periodToStart = periodToStart;

        iborIndex = new IborIndex("no-fix",// never take fixing into account
                i.tenor(), i.fixingDays(), new Currency(), i.fixingCalendar(), i.businessDayConvention(),
                i.endOfMonth(), i.dayCounter(), this.termStructureHandle);

        initializeDates();

    }

    /**
     * Dated FraRateHelper ctor — mirrors C++ v1.42.1 ratehelpers.hpp:167-173
     * + ratehelpers.cpp:340-358. Pins both effective and termination dates to
     * caller-supplied absolutes; super-ctor flag {@code updateDates=false} prevents
     * recomputation on evaluation-date change.
     *
     * <p>The {@code useIndexedCoupon} flag mirrors C++ {@code useIndexedCoupon_}:
     * when true (default), {@code impliedQuote} returns {@code iborIndex.fixing}.
     * When false, it returns the forecast rate
     * {@code (discount(start)/discount(end) - 1) / spanningTime}.
     *
     * @param rate market quote (handle)
     * @param startDate effective date (absolute, caller-supplied)
     * @param endDate termination date (absolute, caller-supplied)
     * @param i template IborIndex (cloned with helper's relinkable term-structure handle)
     * @param pillarChoice pillar-date choice (MaturityDate / LastRelevantDate / CustomDate)
     * @param customPillarDate custom pillar (only used when pillarChoice == CustomDate; pass null otherwise)
     * @param useIndexedCoupon when false, use the forecast-rate path
     */
    public FraRateHelper(final Handle< Quote > rate,
            final Date startDate, final Date endDate,
            final IborIndex i,
            final Pillar.Choice pillarChoice,
            final Date customPillarDate,
            final boolean useIndexedCoupon) {
        super(rate, false); // updateDates=false: do NOT recompute dates on evaluation-date change
        this.periodToStart = null;
        this.explicitStartDate = startDate;
        this.explicitEndDate = endDate;
        this.pillarChoice = pillarChoice;
        this.useIndexedCoupon = useIndexedCoupon;
        this.iborIndex = new IborIndex("no-fix",
                i.tenor(), i.fixingDays(), new Currency(), i.fixingCalendar(), i.businessDayConvention(),
                i.endOfMonth(), i.dayCounter(), this.termStructureHandle);
        initializeDates();
        if ( pillarChoice == Pillar.Choice.CustomDate && customPillarDate != null ) {
            QL.require(!customPillarDate.lt(this.earliestDate),
                    "pillar date must be later than or equal to the instrument's earliest date");
            QL.require(!customPillarDate.gt(this.latestDate),
                    "pillar date must be before or equal to the instrument's latest relevant date");
            this.latestDate = customPillarDate;
        }
    }

    //
    // implements BootstrapHelper
    //

    @Override
    public double impliedQuote() {
        QL.require(termStructure != null, "term structure not set");
        if ( this.useIndexedCoupon ) {
            return iborIndex.fixing(this.fixingDate, true);
        } else {
            // Mirrors C++ v1.42.1 ratehelpers.cpp:360-369: forecast rate from discount factors.
            return (termStructure.discount(this.earliestDate) / termStructure.discount(this.latestDate) - 1.0)
                    / this.spanningTime;
        }
    }

    @Override
    public void setTermStructure(final YieldTermStructure t) {
        // no need to register---the index is not lazy
        termStructureHandle.linkTo(t, false);
        super.setTermStructure(t);
    }

    //
    // implements RelativeDateRateHelper
    //

    @Override
    protected void initializeDates() {
        // Mirrors C++ v1.42.1 ratehelpers.cpp:392-450.
        if ( this.updateDates ) {
            // Period-relative path (existing behavior).
            final Date settlement = iborIndex.fixingCalendar()
                    .advance(this.evaluationDate, new Period(iborIndex.fixingDays(), TimeUnit.Days));
            this.earliestDate = iborIndex.fixingCalendar()
                    .advance(settlement, this.periodToStart, iborIndex.businessDayConvention(), iborIndex.endOfMonth());
            this.latestDate = iborIndex.maturityDate(this.earliestDate);
        } else {
            // Dated path: caller-supplied earliest + latest absolute dates.
            this.earliestDate = this.explicitStartDate;
            // For useIndexedCoupon==true, latestRelevantDate is iborIndex.maturityDate(earliest);
            // for useIndexedCoupon==false, latestRelevantDate is the caller-supplied endDate.
            if ( this.useIndexedCoupon ) {
                this.latestDate = iborIndex.maturityDate(this.earliestDate);
            } else {
                this.latestDate = this.explicitEndDate;
                this.spanningTime = iborIndex.dayCounter()
                        .yearFraction(this.earliestDate, this.explicitEndDate);
                QL.require(this.spanningTime > 0.0,
                        "FraRateHelper: spanning time must be positive (start=" + this.earliestDate
                                + ", end=" + this.explicitEndDate + ")");
            }
        }
        this.fixingDate = iborIndex.fixingDate(this.earliestDate);
    }

    //
    // implements PolymorphicVisitable
    //

    @Override
    public void accept(final PolymorphicVisitor pv) {
        final Visitor< FraRateHelper > v = (pv != null) ? pv.visitor(this.getClass()) : null;
        if ( v != null ) {
            v.visit(this);
        } else {
            super.accept(pv);
        }
    }

}
