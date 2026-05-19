/*
 Copyright (C) 2026 JQuantLib migration contributors.

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
 Copyright (C) 2006, 2007, 2014 Ferdinando Ametrano

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.
 */
package org.jquantlib.instruments;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.cashflow.*;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.indexes.SwapIndex;
import org.jquantlib.math.Constants;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper class for instantiating standard market constant-maturity swaps (CMS).
 * <p>
 * Java port of C++ QuantLib v1.42.1 {@code MakeCms} in {@code ql/instruments/makecms.{hpp,cpp}}. Two-leg helper: a CMS
 * leg (built via {@link CmsLeg}) referenced against a {@link SwapIndex}, and a floating Ibor leg (built via
 * {@link IborLeg}) referenced against an {@link IborIndex}. The pay-direction defaults to "pay CMS", and the pricing
 * engine defaults to {@link DiscountingSwapEngine} on the swap index's forwarding term structure.
 *
 * <p>Use {@link #value()} (parallel to C++ {@code operator Swap()}/
 * {@code operator ext::shared_ptr<Swap>()}) to build the {@link Swap}.
 *
 * @see MakeVanillaSwap
 */
public class MakeCms {

    private final Period swapTenor_;
    private final SwapIndex swapIndex_;
    private final IborIndex iborIndex_;
    private final double iborSpread_;
    private final Period forwardStart_;
    private boolean useAtmSpread_;
    private final double cmsSpread_;
    private final double cmsGearing_;
    private final double cmsCap_;
    private final double cmsFloor_;

    private Date effectiveDate_;
    private Calendar cmsCalendar_;
    private Calendar floatCalendar_;

    private boolean payCms_;
    private double nominal_;
    private Period cmsTenor_;
    private Period floatTenor_;
    private BusinessDayConvention cmsConvention_;
    private BusinessDayConvention cmsTerminationDateConvention_;
    private BusinessDayConvention floatConvention_;
    private BusinessDayConvention floatTerminationDateConvention_;
    private DateGeneration.Rule cmsRule_;
    private DateGeneration.Rule floatRule_;
    private boolean cmsEndOfMonth_;
    private boolean floatEndOfMonth_;
    private Date cmsFirstDate_;
    private Date cmsNextToLastDate_;
    private Date floatFirstDate_;
    private Date floatNextToLastDate_;
    private DayCounter cmsDayCount_;
    private DayCounter floatDayCount_;

    private PricingEngine engine_;
    private CmsCouponPricer couponPricer_;

    /**
     * Convenience overload — mirrors C++ default {@code iborSpread = 0, forwardStart = 0*Days}.
     */
    public MakeCms(final Period swapTenor, final SwapIndex swapIndex, final IborIndex iborIndex) {
        this(swapTenor, swapIndex, iborIndex, 0.0, new Period(0, TimeUnit.Days));
    }

    public MakeCms(final Period swapTenor, final SwapIndex swapIndex, final IborIndex iborIndex,
            final /*Spread*/ double iborSpread, final Period forwardStart) {
        this.swapTenor_ = swapTenor;
        this.swapIndex_ = swapIndex;
        this.iborIndex_ = iborIndex;
        this.iborSpread_ = iborSpread;
        this.useAtmSpread_ = false;
        this.forwardStart_ = forwardStart;

        this.cmsSpread_ = 0.0;
        this.cmsGearing_ = 1.0;
        // C++ Null<Real>() sentinel — Java FloatingLeg.noOption() checks
        // against Constants.NULL_RATE (= Double.MAX_VALUE) to elide the
        // CappedFlooredCmsCoupon path. NaN would defeat that check.
        this.cmsCap_ = Constants.NULL_RATE;
        this.cmsFloor_ = Constants.NULL_RATE;

        this.cmsCalendar_ = swapIndex.fixingCalendar();
        this.floatCalendar_ = iborIndex.fixingCalendar();
        this.payCms_ = true;
        this.nominal_ = 1.0;
        this.cmsTenor_ = new Period(3, TimeUnit.Months);
        this.floatTenor_ = iborIndex.tenor();
        this.cmsConvention_ = BusinessDayConvention.ModifiedFollowing;
        this.cmsTerminationDateConvention_ = BusinessDayConvention.ModifiedFollowing;
        this.floatConvention_ = iborIndex.businessDayConvention();
        this.floatTerminationDateConvention_ = iborIndex.businessDayConvention();
        this.cmsRule_ = DateGeneration.Rule.Backward;
        this.floatRule_ = DateGeneration.Rule.Backward;
        this.cmsEndOfMonth_ = false;
        this.floatEndOfMonth_ = false;

        this.cmsFirstDate_ = new Date();
        this.cmsNextToLastDate_ = new Date();
        this.floatFirstDate_ = new Date();
        this.floatNextToLastDate_ = new Date();

        this.cmsDayCount_ = new Actual360();
        this.floatDayCount_ = iborIndex.dayCounter();
        // arbitrary choice: discount on the SwapIndex forwarding curve
        // (mirrors C++ makecms.cpp:55).
        this.engine_ = new DiscountingSwapEngine(swapIndex.termStructure());
        this.effectiveDate_ = new Date();
    }

    /**
     * Convenience overload — mirrors C++ second ctor that derives {@code iborIndex} from
     * {@code swapIndex->iborIndex()}.
     */
    public MakeCms(final Period swapTenor, final SwapIndex swapIndex) {
        this(swapTenor, swapIndex, swapIndex.iborIndex(), 0.0, new Period(0, TimeUnit.Days));
    }

    public MakeCms(final Period swapTenor, final SwapIndex swapIndex, final /*Spread*/ double iborSpread,
            final Period forwardStart) {
        this(swapTenor, swapIndex, swapIndex.iborIndex(), iborSpread, forwardStart);
    }

    /**
     * Build the {@link Swap} (mirrors C++ {@code operator ext::shared_ptr<Swap>()}).
     */
    public Swap value() /* @ReadOnly */ {
        final Date startDate;
        if ( !effectiveDate_.isNull() ) {
            startDate = effectiveDate_;
        } else {
            final int fixingDays = iborIndex_.fixingDays();
            Date refDate = new Settings().evaluationDate();
            // if the evaluation date is not a business day, move forward
            refDate = floatCalendar_.adjust(refDate);
            final Date spotDate = floatCalendar_.advance(refDate, fixingDays, TimeUnit.Days);
            startDate = spotDate.add(forwardStart_);
        }

        final Date terminationDate = startDate.add(swapTenor_);

        final Schedule cmsSchedule = new Schedule(startDate, terminationDate, cmsTenor_, cmsCalendar_, cmsConvention_,
                cmsTerminationDateConvention_, cmsRule_, cmsEndOfMonth_, cmsFirstDate_, cmsNextToLastDate_);

        final Schedule floatSchedule = new Schedule(startDate, terminationDate, floatTenor_, floatCalendar_,
                floatConvention_, floatTerminationDateConvention_, floatRule_, floatEndOfMonth_, floatFirstDate_,
                floatNextToLastDate_);

        final CmsLeg cmsLegBuilder = new CmsLeg(cmsSchedule, swapIndex_).withNotionals(nominal_)
                .withPaymentDayCounter(cmsDayCount_).withPaymentAdjustment(cmsConvention_)
                .withFixingDays(swapIndex_.fixingDays()).withGearings(cmsGearing_).withSpreads(cmsSpread_)
                .withCaps(cmsCap_).withFloors(cmsFloor_);
        final Leg cmsLeg = cmsLegBuilder.Leg();
        if ( couponPricer_ != null ) {
            CashFlows.setCouponPricer(cmsLeg, couponPricer_);
        }

        double usedSpread = iborSpread_;
        if ( useAtmSpread_ ) {
            QL.require(!iborIndex_.termStructure().empty(),
                    "null term structure set to this instance of " + iborIndex_.name());
            QL.require(!swapIndex_.termStructure().empty(),
                    "null term structure set to this instance of " + swapIndex_.name());
            QL.require(couponPricer_ != null, "no CmsCouponPricer set (yet)");

            final Leg floatLegAtm = new IborLeg(floatSchedule, iborIndex_).withNotionals(nominal_)
                    .withPaymentDayCounter(floatDayCount_).withPaymentAdjustment(floatConvention_)
                    .withFixingDays(iborIndex_.fixingDays()).Leg();

            final Swap temp = new Swap(cmsLeg, floatLegAtm);
            temp.setPricingEngine(engine_);

            final double npv = temp.legNPV(0) + temp.legNPV(1);
            usedSpread = -npv / temp.legBPS(1) * 1.0e-4;
        } else {
            QL.require(!Double.isNaN(usedSpread), "null spread set");
        }

        final Leg floatLeg = new IborLeg(floatSchedule, iborIndex_).withNotionals(nominal_)
                .withPaymentDayCounter(floatDayCount_).withPaymentAdjustment(floatConvention_)
                .withFixingDays(iborIndex_.fixingDays()).withSpreads(usedSpread).Leg();

        final List< Leg > legs = new ArrayList< Leg >(2);
        final boolean[] payer = new boolean[2];
        if ( payCms_ ) {
            legs.add(cmsLeg);
            legs.add(floatLeg);
            payer[0] = true;
            payer[1] = false;
        } else {
            legs.add(floatLeg);
            legs.add(cmsLeg);
            payer[0] = true;
            payer[1] = false;
        }
        final Swap swap = new Swap(legs, payer);
        swap.setPricingEngine(engine_);
        return swap;
    }

    public MakeCms receiveCms(final boolean flag) {
        this.payCms_ = !flag;
        return this;
    }

    public MakeCms receiveCms() {
        return receiveCms(true);
    }

    public MakeCms withNominal(final /*Real*/ double n) {
        this.nominal_ = n;
        return this;
    }

    public MakeCms withEffectiveDate(final Date effectiveDate) {
        this.effectiveDate_ = effectiveDate;
        return this;
    }

    public MakeCms withDiscountingTermStructure(final Handle< YieldTermStructure > discountingTermStructure) {
        this.engine_ = new DiscountingSwapEngine(discountingTermStructure);
        return this;
    }

    public MakeCms withCmsCouponPricer(final CmsCouponPricer couponPricer) {
        this.couponPricer_ = couponPricer;
        return this;
    }

    public MakeCms withCmsLegTenor(final Period t) {
        this.cmsTenor_ = t;
        return this;
    }

    public MakeCms withCmsLegCalendar(final Calendar cal) {
        this.cmsCalendar_ = cal;
        return this;
    }

    public MakeCms withCmsLegConvention(final BusinessDayConvention bdc) {
        this.cmsConvention_ = bdc;
        return this;
    }

    public MakeCms withCmsLegTerminationDateConvention(final BusinessDayConvention bdc) {
        this.cmsTerminationDateConvention_ = bdc;
        return this;
    }

    public MakeCms withCmsLegRule(final DateGeneration.Rule r) {
        this.cmsRule_ = r;
        return this;
    }

    public MakeCms withCmsLegEndOfMonth(final boolean flag) {
        this.cmsEndOfMonth_ = flag;
        return this;
    }

    public MakeCms withCmsLegEndOfMonth() {
        return withCmsLegEndOfMonth(true);
    }

    public MakeCms withCmsLegFirstDate(final Date d) {
        this.cmsFirstDate_ = d;
        return this;
    }

    public MakeCms withCmsLegNextToLastDate(final Date d) {
        this.cmsNextToLastDate_ = d;
        return this;
    }

    public MakeCms withCmsLegDayCount(final DayCounter dc) {
        this.cmsDayCount_ = dc;
        return this;
    }

    public MakeCms withFloatingLegTenor(final Period t) {
        this.floatTenor_ = t;
        return this;
    }

    public MakeCms withFloatingLegCalendar(final Calendar cal) {
        this.floatCalendar_ = cal;
        return this;
    }

    public MakeCms withFloatingLegConvention(final BusinessDayConvention bdc) {
        this.floatConvention_ = bdc;
        return this;
    }

    public MakeCms withFloatingLegTerminationDateConvention(final BusinessDayConvention bdc) {
        this.floatTerminationDateConvention_ = bdc;
        return this;
    }

    public MakeCms withFloatingLegRule(final DateGeneration.Rule r) {
        this.floatRule_ = r;
        return this;
    }

    public MakeCms withFloatingLegEndOfMonth(final boolean flag) {
        this.floatEndOfMonth_ = flag;
        return this;
    }

    public MakeCms withFloatingLegEndOfMonth() {
        return withFloatingLegEndOfMonth(true);
    }

    public MakeCms withFloatingLegFirstDate(final Date d) {
        this.floatFirstDate_ = d;
        return this;
    }

    public MakeCms withFloatingLegNextToLastDate(final Date d) {
        this.floatNextToLastDate_ = d;
        return this;
    }

    public MakeCms withFloatingLegDayCount(final DayCounter dc) {
        this.floatDayCount_ = dc;
        return this;
    }

    public MakeCms withAtmSpread(final boolean flag) {
        this.useAtmSpread_ = flag;
        return this;
    }

    public MakeCms withAtmSpread() {
        return withAtmSpread(true);
    }
}
