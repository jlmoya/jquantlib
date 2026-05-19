/*
Copyright (C) 2008 John Martin

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

/*
 Copyright (C) 2006, 2007 Ferdinando Ametrano
 Copyright (C) 2006 Katiuscia Manzoni
 Copyright (C) 2006 StatPro Italia srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <http://quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
 */
package org.jquantlib.instruments;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.cashflow.OvernightLeg;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.daycounters.Thirty360;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.indexes.OvernightIndex;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.*;

/**
 * This class provides a more comfortable way to instantiate standard market swap.
 *
 * @author John Martin
 */
public class MakeVanillaSwap {

    /** Sentinel for "settlementDays not set". Mirrors C++ {@code Null<Natural>()}. */
    private static final int NULL_SETTLEMENT_DAYS = Integer.MIN_VALUE;
    private final IborIndex iborIndex;
    private final /*@Rate*/ double fixedRate;
    private final Period forwardStart;
    private Period swapTenor;
    private int settlementDays = NULL_SETTLEMENT_DAYS;
    /** null = unset; defer to {@code floatEndOfMonth}. Mirrors C++ {@code ext::optional<bool>}. */
    private Boolean maturityEndOfMonth;
    /** null = unset; defer to {@code Following} per C++. */
    private BusinessDayConvention paymentConvention;
    private Date effectiveDate;
    private Calendar fixedCalendar;
    private Calendar floatCalendar;

    private VanillaSwap.Type type;
    private /*@Real*/ double nominal;
    private Period fixedTenor;
    private Period floatTenor;
    private BusinessDayConvention fixedConvention;
    private BusinessDayConvention fixedTerminationDateConvention;
    private BusinessDayConvention floatConvention;
    private BusinessDayConvention floatTerminationDateConvention;
    private DateGeneration.Rule fixedRule;
    private DateGeneration.Rule floatRule;
    private boolean fixedEndOfMonth;
    private boolean floatEndOfMonth;
    private Date fixedFirstDate;
    private Date fixedNextToLastDate;
    private Date floatFirstDate;
    private Date floatNextToLastDate;
    private /*@Spread*/ double floatSpread;
    private DayCounter fixedDayCount;
    private DayCounter floatDayCount;
    private Date terminationDate;
    private PricingEngine engine;

    public MakeVanillaSwap(final Period swapTenor, final IborIndex index) {
        this(swapTenor, index, Double.NaN, new Period(0, TimeUnit.Days));
    }

    public MakeVanillaSwap(final Period swapTenor, final IborIndex index, final /*Rate*/ double fixedRate) {
        this(swapTenor, index, fixedRate, new Period(0, TimeUnit.Days));
    }

    public MakeVanillaSwap(final Period swapTenor, final IborIndex index, final /*@Rate*/ double fixedRate,
            final Period forwardStart) {
        this.swapTenor = swapTenor;
        this.iborIndex = index;
        this.fixedRate = fixedRate;
        this.forwardStart = forwardStart;
        this.effectiveDate = new Date();
        this.fixedCalendar = index.fixingCalendar();
        this.floatCalendar = index.fixingCalendar();
        this.type = VanillaSwap.Type.Payer;
        this.nominal = 1.0;
        // null = unset → currency-based inference in value(); mirrors C++ Period() default.
        this.fixedTenor = null;
        this.floatTenor = index.tenor();
        this.fixedConvention = BusinessDayConvention.ModifiedFollowing;
        this.fixedTerminationDateConvention = BusinessDayConvention.ModifiedFollowing;
        this.floatConvention = index.businessDayConvention();
        this.floatTerminationDateConvention = index.businessDayConvention();
        this.fixedRule = DateGeneration.Rule.Backward;
        this.floatRule = DateGeneration.Rule.Backward;
        this.fixedEndOfMonth = false;
        this.floatEndOfMonth = false;
        this.fixedFirstDate = new Date();
        this.fixedNextToLastDate = new Date();
        this.floatFirstDate = new Date();
        this.floatNextToLastDate = new Date();
        this.floatSpread = 0.0;
        // null = unset → currency-based inference in value(); mirrors C++ DayCounter() default.
        this.fixedDayCount = null;
        this.floatDayCount = index.dayCounter();
        this.maturityEndOfMonth = null;
        this.paymentConvention = null;
        this.engine = new DiscountingSwapEngine(index.termStructure());
    }

    /**
     * Mirrors C++ {@code allowsEndOfMonth(Period)} (schedule.cpp:656-658) — the EOM convention is meaningful only for
     * tenors expressed in months or years, of length at least one month.
     */
    private static boolean allowsEndOfMonth(final Period tenor) {
        if ( tenor == null ) {
            return false;
        }
        final TimeUnit u = tenor.units();
        if ( u != TimeUnit.Months && u != TimeUnit.Years ) {
            return false;
        }
        return periodGe(tenor, new Period(1, TimeUnit.Months));
    }

    private static boolean isEmptyPeriod(final Period p) {
        return p == null || p.length() == 0;
    }

    private static boolean periodLe(final Period a, final Period b) {
        try {
            return a.le(b);
        } catch ( final RuntimeException e ) {
            return approxDays(a) <= approxDays(b);
        }
    }

    private static boolean periodLt(final Period a, final Period b) {
        try {
            return a.lt(b);
        } catch ( final RuntimeException e ) {
            return approxDays(a) < approxDays(b);
        }
    }

    private static boolean periodGt(final Period a, final Period b) {
        try {
            return a.gt(b);
        } catch ( final RuntimeException e ) {
            return approxDays(a) > approxDays(b);
        }
    }

    private static boolean periodGe(final Period a, final Period b) {
        try {
            return a.ge(b);
        } catch ( final RuntimeException e ) {
            return approxDays(a) >= approxDays(b);
        }
    }

    private static long approxDays(final Period p) {
        switch ( p.units() ) {
        case Days:
            return p.length();
        case Weeks:
            return 7L * p.length();
        case Months:
            return 30L * p.length();
        case Years:
            return 365L * p.length();
        default:
            return p.length();
        }
    }

    public VanillaSwap value() /* @ReadOnly */ {

        // C++ MakeVanillaSwap (makevanillaswap.cpp:59-61): cannot set both
        // explicit effective date AND settlement days.
        QL.require(effectiveDate.isNull() || settlementDays == NULL_SETTLEMENT_DAYS,
                "cannot set both an explicit effective date and settlement days; " + "use one or the other");

        Date startDate;
        if ( !effectiveDate.isNull() ) {
            startDate = effectiveDate;
        } else {
            Date refDate = new Settings().evaluationDate();
            refDate = floatCalendar.adjust(refDate);
            final Date spotDate;
            if ( settlementDays == NULL_SETTLEMENT_DAYS ) {
                spotDate = iborIndex.valueDate(refDate);
            } else {
                spotDate = floatCalendar.advance(refDate, settlementDays, TimeUnit.Days);
            }
            startDate = spotDate.add(forwardStart);
            if ( forwardStart.length() < 0 ) {
                startDate = floatCalendar.adjust(startDate, BusinessDayConvention.Preceding);
            } else if ( forwardStart.length() > 0 ) {
                startDate = floatCalendar.adjust(startDate, BusinessDayConvention.Following);
            }
        }

        Date endDate;
        if ( terminationDate != null && !terminationDate.isNull() ) {
            endDate = terminationDate;
        } else {
            endDate = startDate.add(swapTenor);
            final boolean useMaturityEoM = (maturityEndOfMonth != null)
                    ? maturityEndOfMonth.booleanValue()
                    : floatEndOfMonth;
            if ( useMaturityEoM && allowsEndOfMonth(swapTenor) && floatCalendar.isEndOfMonth(startDate) ) {
                endDate = floatCalendar.endOfMonth(endDate);
            }
        }

        // Currency-based fixed-tenor inference (C++ makevanillaswap.cpp:99-126).
        final org.jquantlib.currencies.Currency curr = iborIndex.currency();
        final Period usedFixedTenor;
        if ( fixedTenor != null ) {
            usedFixedTenor = fixedTenor;
        } else {
            Period tenor = (swapTenor != null) ? swapTenor : new Period();
            if ( isEmptyPeriod(tenor) && endDate.gt(startDate) ) {
                final int months = (int) ((12L * endDate.sub(startDate) + 182L) / 365L);
                // Express as years when cleanly divisible to keep Period
                // unit-equality with the per-currency thresholds (4Y, 1Y);
                // Java Period.eq compares units strictly so a 48M-vs-4Y
                // boundary check would otherwise miss.
                if ( months > 0 && months % 12 == 0 ) {
                    tenor = new Period(months / 12, TimeUnit.Years);
                } else {
                    tenor = new Period(months, TimeUnit.Months);
                }
            }
            if ( curr.eq(new org.jquantlib.currencies.Europe.EURCurrency()) || curr.eq(
                    new org.jquantlib.currencies.America.USDCurrency()) || curr.eq(
                    new org.jquantlib.currencies.Europe.CHFCurrency()) || curr.eq(
                    new org.jquantlib.currencies.Europe.SEKCurrency()) || (
                    curr.eq(new org.jquantlib.currencies.Europe.GBPCurrency()) && periodLe(tenor,
                            new Period(1, TimeUnit.Years))) ) {
                usedFixedTenor = new Period(1, TimeUnit.Years);
            } else if ( (curr.eq(new org.jquantlib.currencies.Europe.GBPCurrency()) && periodGt(tenor,
                    new Period(1, TimeUnit.Years))) || curr.eq(new org.jquantlib.currencies.Asia.JPYCurrency()) || (
                    curr.eq(new org.jquantlib.currencies.Oceania.AUDCurrency()) && periodGe(tenor,
                            new Period(4, TimeUnit.Years))) ) {
                usedFixedTenor = new Period(6, TimeUnit.Months);
            } else if ( curr.eq(new org.jquantlib.currencies.Asia.HKDCurrency()) || (
                    curr.eq(new org.jquantlib.currencies.Oceania.AUDCurrency()) && periodLt(tenor,
                            new Period(4, TimeUnit.Years))) ) {
                usedFixedTenor = new Period(3, TimeUnit.Months);
            } else {
                throw new IllegalStateException("unknown fixed leg default tenor for " + curr);
            }
        }

        final Schedule fixedSchedule = new Schedule(startDate, endDate, usedFixedTenor, fixedCalendar, fixedConvention,
                fixedTerminationDateConvention, fixedRule, fixedEndOfMonth, fixedFirstDate, fixedNextToLastDate);

        final Schedule floatSchedule = new Schedule(startDate, endDate, floatTenor, floatCalendar, floatConvention,
                floatTerminationDateConvention, floatRule, floatEndOfMonth, floatFirstDate, floatNextToLastDate);

        // Currency-based fixed-day-count inference (C++ makevanillaswap.cpp:142-157).
        final DayCounter usedFixedDayCount;
        if ( fixedDayCount != null ) {
            usedFixedDayCount = fixedDayCount;
        } else {
            if ( curr.eq(new org.jquantlib.currencies.America.USDCurrency()) ) {
                usedFixedDayCount = new org.jquantlib.daycounters.Actual360();
            } else if ( curr.eq(new org.jquantlib.currencies.Europe.EURCurrency()) || curr.eq(
                    new org.jquantlib.currencies.Europe.CHFCurrency()) || curr.eq(
                    new org.jquantlib.currencies.Europe.SEKCurrency()) ) {
                usedFixedDayCount = new Thirty360(Thirty360.Convention.BondBasis);
            } else if ( curr.eq(new org.jquantlib.currencies.Europe.GBPCurrency()) || curr.eq(
                    new org.jquantlib.currencies.Asia.JPYCurrency()) || curr.eq(
                    new org.jquantlib.currencies.Oceania.AUDCurrency()) || curr.eq(
                    new org.jquantlib.currencies.Asia.HKDCurrency()) || curr.eq(
                    new org.jquantlib.currencies.Asia.THBCurrency()) ) {
                usedFixedDayCount = new org.jquantlib.daycounters.Actual365Fixed();
            } else {
                throw new IllegalStateException("unknown fixed leg day counter for " + curr);
            }
        }

        double usedFixedRate = fixedRate;
        final BusinessDayConvention usedPaymentConv = (paymentConvention != null)
                ? paymentConvention
                : BusinessDayConvention.Following;

        if ( Double.isNaN(fixedRate) ) {
            QL.require(!iborIndex.termStructure().empty(),
                    "no forecasting term structure set to " + iborIndex.name()); // TODO: message

            final VanillaSwap temp = new VanillaSwap(type, nominal, fixedSchedule, 0.0, usedFixedDayCount,
                    floatSchedule, iborIndex, floatSpread, floatDayCount, usedPaymentConv);

            // ATM on the forecasting curve
            temp.setPricingEngine(new DiscountingSwapEngine(iborIndex.termStructure()));
            usedFixedRate = temp.fairRate();
        }

        final VanillaSwap swap;
        if ( iborIndex instanceof OvernightIndex ) {
            // Phase 5g.5f: when iborIndex is an OvernightIndex, build the
            // floating leg via OvernightLeg (overnight-indexed coupons) rather
            // than the IborLeg path. C++ MakeVanillaSwap unconditionally calls
            // VanillaSwap with an IborIndex; in C++ the OvernightIndex IS-A
            // IborIndex hierarchy plus IborLeg's coupon-pricer dispatch
            // transparently builds OvernightIndexedCoupons. Java's IborLeg
            // hard-codes the IborCoupon path, so we substitute the floating
            // leg with an OvernightLeg via a VanillaSwap subclass that
            // overrides legs[1] in-place. Used by MakeCapFloor →
            // OptionletStripper1 to bootstrap caps on overnight indexes
            // (e.g., SOFR).
            swap = buildOvernightVanillaSwap(usedFixedRate, fixedSchedule, floatSchedule, usedFixedDayCount,
                    usedPaymentConv);
        } else {
            swap = new VanillaSwap(type, nominal, fixedSchedule, usedFixedRate, usedFixedDayCount, floatSchedule,
                    iborIndex, floatSpread, floatDayCount, usedPaymentConv);
        }
        swap.setPricingEngine(engine);
        return swap;
    }

    /**
     * Build a VanillaSwap whose floating leg slot is populated by an {@link OvernightLeg} (overnight-indexed coupons).
     * Used by the OvernightIndex branch in {@link #value()}. The IborLeg path inside the superclass ctor still runs (we
     * feed it the OvernightIndex, which IS-A IborIndex), then we replace {@code legs.get(1)} with the OvernightLeg. The
     * resulting swap's {@link VanillaSwap#floatingLeg()} therefore returns the overnight-indexed leg, which is what
     * {@link MakeCapFloor} (and downstream
     * {@link org.jquantlib.termstructures.volatilities.optionlet.OptionletStripper1}) consume.
     */
    private VanillaSwap buildOvernightVanillaSwap(final double usedFixedRate, final Schedule fixedSchedule,
            final Schedule floatSchedule, final DayCounter usedFixedDayCount,
            final BusinessDayConvention usedPaymentConv) {
        final OvernightIndex on = (OvernightIndex) iborIndex;
        final Leg overnightLeg = new OvernightLeg(floatSchedule, on).withNotionals(nominal)
                .withPaymentDayCounter(floatDayCount).withPaymentAdjustment(usedPaymentConv).withSpreads(floatSpread)
                .leg();
        final VanillaSwap swap = new VanillaSwap(type, nominal, fixedSchedule, usedFixedRate, usedFixedDayCount,
                floatSchedule, iborIndex, floatSpread, floatDayCount, usedPaymentConv);
        // Replace the IborLeg in slot [1] with the OvernightLeg.
        // legs is protected; access via reflection-free in-place set.
        swap.legs.set(1, overnightLeg);
        for ( final org.jquantlib.cashflow.CashFlow item : overnightLeg ) {
            item.addObserver(swap);
        }
        return swap;
    }

    public MakeVanillaSwap receiveFixed(final boolean flag) {
        this.type = flag ? VanillaSwap.Type.Receiver : VanillaSwap.Type.Payer;
        return this;
    }

    public MakeVanillaSwap withType(final VanillaSwap.Type type) {
        this.type = type;
        return this;
    }

    public MakeVanillaSwap withNominal(/* Real */final double n) {
        this.nominal = n;
        return this;
    }

    public MakeVanillaSwap withSettlementDays(final int settlementDays) {
        QL.require(effectiveDate.isNull(),
                "cannot set both an explicit effective date and settlement days; " + "use one or the other");
        this.settlementDays = settlementDays;
        return this;
    }

    public MakeVanillaSwap withEffectiveDate(final Date effectiveDate) {
        QL.require(settlementDays == NULL_SETTLEMENT_DAYS,
                "cannot set both an explicit effective date and settlement days; " + "use one or the other");
        this.effectiveDate = effectiveDate;
        return this;
    }

    public MakeVanillaSwap withTerminationDate(final Date terminationDate) {
        this.terminationDate = terminationDate;
        // Mirrors C++ makevanillaswap.cpp:225-229 — withTerminationDate
        // clears the constructor swapTenor so the date-span drives the
        // currency-based fixed-tenor inference.
        if ( terminationDate != null && !terminationDate.isNull() ) {
            this.swapTenor = new Period();
        }
        return this;
    }

    public MakeVanillaSwap withPaymentConvention(final BusinessDayConvention bdc) {
        this.paymentConvention = bdc;
        return this;
    }

    public MakeVanillaSwap withPricingEngine(final PricingEngine engine) {
        this.engine = engine;
        return this;
    }

    public MakeVanillaSwap withMaturityEndOfMonth(final boolean flag) {
        this.maturityEndOfMonth = Boolean.valueOf(flag);
        return this;
    }

    public MakeVanillaSwap withRule(final DateGeneration.Rule r) {
        this.fixedRule = r;
        this.floatRule = r;
        return this;
    }

    public MakeVanillaSwap withDiscountingTermStructure(final Handle< YieldTermStructure > discountingTermStructure) {
        this.engine = (new DiscountingSwapEngine(discountingTermStructure));
        return this;
    }

    public MakeVanillaSwap withFixedLegTenor(final Period t) {
        this.fixedTenor = t;
        return this;
    }

    public MakeVanillaSwap withFixedLegCalendar(final Calendar cal) {
        this.fixedCalendar = cal;
        return this;
    }

    public MakeVanillaSwap withFixedLegConvention(final BusinessDayConvention bdc) {
        this.fixedConvention = bdc;
        return this;
    }

    public MakeVanillaSwap withFixedLegTerminationDateConvention(final BusinessDayConvention bdc) {
        this.fixedTerminationDateConvention = bdc;
        return this;
    }

    public MakeVanillaSwap withFixedLegRule(final DateGeneration.Rule r) {
        this.fixedRule = r;
        return this;
    }

    public MakeVanillaSwap withFixedLegEndOfMonth(final boolean flag) {
        this.fixedEndOfMonth = flag;
        return this;
    }

    public MakeVanillaSwap withFixedLegFirstDate(final Date d) {
        this.fixedFirstDate = d;
        return this;
    }

    public MakeVanillaSwap withFixedLegNextToLastDate(final Date d) {
        this.fixedNextToLastDate = d;
        return this;
    }

    public MakeVanillaSwap withFixedLegDayCount(final DayCounter dc) {
        this.fixedDayCount = dc;
        return this;
    }

    public MakeVanillaSwap withFloatingLegTenor(final Period t) {
        this.floatTenor = t;
        return this;
    }

    public MakeVanillaSwap withFloatingLegCalendar(final Calendar cal) {
        this.floatCalendar = cal;
        return this;
    }

    public MakeVanillaSwap withFloatingLegConvention(final BusinessDayConvention bdc) {
        this.floatConvention = bdc;
        return this;
    }

    public MakeVanillaSwap withFloatingLegTerminationDateConvention(final BusinessDayConvention bdc) {
        this.floatTerminationDateConvention = bdc;
        return this;
    }

    public MakeVanillaSwap withFloatingLegRule(final DateGeneration.Rule r) {
        this.floatRule = r;
        return this;
    }

    public MakeVanillaSwap withFloatingLegEndOfMonth(final boolean flag) {
        this.floatEndOfMonth = flag;
        return this;
    }

    public MakeVanillaSwap withFloatingLegFirstDate(final Date d) {
        this.floatFirstDate = d;
        return this;
    }

    public MakeVanillaSwap withFloatingLegNextToLastDate(final Date d) {
        this.floatNextToLastDate = d;
        return this;
    }

    public MakeVanillaSwap withFloatingLegDayCount(final DayCounter dc) {
        this.floatDayCount = dc;
        return this;
    }

    public MakeVanillaSwap withFloatingLegSpread(/* Spread */final double sp) {
        this.floatSpread = sp;
        return this;
    }

}
