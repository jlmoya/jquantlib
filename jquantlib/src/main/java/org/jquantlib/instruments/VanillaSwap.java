/*
Copyright (C) 2009 John Martin

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
 Copyright (C) 2006, 2008 Ferdinando Ametrano
 Copyright (C) 2000, 2001, 2002, 2003 RiskMap srl
 Copyright (C) 2003, 2004, 2005, 2006, 2007 StatPro Italia srl

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.FixedRateCoupon;
import org.jquantlib.cashflow.FixedRateLeg;
import org.jquantlib.cashflow.IborCoupon;
import org.jquantlib.cashflow.IborLeg;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.Constants;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.Schedule;

/**
 * Plain-vanilla swap
 *
 * @note if you define TodaysPayments like this
 * <pre>
 * new Settings().setTodaysPayments(true);
 * </pre>
 * payments occurring at the settlement date of
 * the swap are included in the NPV, and therefore
 * affect the fair-rate and fair-spread
 * calculation. This might not be what you want.
 *
 * @category instruments
 *
 * @author Richard Gomes
 */
// TODO: code review :: license, class comments, comments for access modifiers, comments for @Override
public class VanillaSwap extends Swap {

    static final /*@Spread*/ double  basisPoint = 1.0e-4;

    private final Type type;
    private final /*@Real*/ double nominal;
    private final Schedule fixedSchedule;
    private final /*@Rate*/ double  fixedRate;
    private final DayCounter fixedDayCount;
    private final Schedule floatingSchedule;
    private final IborIndex iborIndex;
    private final /*@Spread*/ double spread;
    private final DayCounter floatingDayCount;
    private final BusinessDayConvention paymentConvention;
    // private final Array fixingDays;

    // results
    private /*@Rate*/ double fairRate;
    private /*@Spread*/ double fairSpread;


    public VanillaSwap(
            final Type type,
            final /*@Real*/ double nominal,
            final Schedule fixedSchedule,
            final /*@Rate*/ double fixedRate,
            final DayCounter fixedDayCount,
            final Schedule floatSchedule,
            final IborIndex iborIndex,
            final /*@Spread*/ double spread,
            final DayCounter floatingDayCount) {
        this(type, nominal, fixedSchedule, fixedRate, fixedDayCount, floatSchedule,
                iborIndex, spread, floatingDayCount, BusinessDayConvention.Following);
    }

    //FIXME: remove parameter "fixingDays"
    public VanillaSwap(
            final Type type,
            final /*@Real*/ double nominal,
            final Schedule fixedSchedule,
            final /*@Rate*/ double fixedRate,
            final DayCounter fixedDayCount,
            final Schedule floatSchedule,
            final IborIndex iborIndex,
            final /*@Spread*/ double spread,
            final DayCounter floatingDayCount,
            final BusinessDayConvention paymentConvention
    /*, final Array fixingDays*/) {
        super(2);
        this.type = type;
        this.nominal = nominal;
        this.fixedSchedule = fixedSchedule;
        this.fixedRate = fixedRate;
        this.fixedDayCount = fixedDayCount;
        this.floatingSchedule = floatSchedule;
        this.iborIndex = iborIndex;
        this.spread = spread;
        this.floatingDayCount = floatingDayCount;
        this.paymentConvention = paymentConvention;
        //FIXME this.fixingDays = fixingDays;

        final Leg fixedLeg = new FixedRateLeg(fixedSchedule, fixedDayCount)
        .withNotionals(nominal)
        .withCouponRates(fixedRate)
        .withPaymentAdjustment(paymentConvention)
        .Leg();

        // JM where are gearings set they cannot be null for the floating leg.
        final Leg floatingLeg = new IborLeg(floatingSchedule, iborIndex)
        .withNotionals(nominal)
        .withPaymentDayCounter(floatingDayCount)
        .withPaymentAdjustment(paymentConvention)

        //FIXME:: .withFixingDays (fixingDays) // FIXME: slight deviation from quantlib, need to expose fixing days up the stack

        .withSpreads(spread)

        // FIXME: JM quantlib does not assign this, it is currently required for construction
        // .withGearings(1.0)

        .Leg();

        for (final CashFlow item : floatingLeg) {
            item.addObserver(this);
        }

        super.legs.add(fixedLeg);
        super.legs.add(floatingLeg);
        if (type==Type.Payer) {
            super.payer[0] = -1.0;
            super.payer[1] = +1.0;
        } else {
            super.payer[0] = +1.0;
            super.payer[1] = -1.0;
        }
    }

    /**
     * @return the swap type (Payer/Receiver).
     * Mirrors C++ {@code VanillaSwap::type()} (FixedVsFloatingSwap::type in v1.42.1).
     * Exposed so engines (e.g. BlackSwaptionEngine) can branch on payer vs
     * receiver without reflection.
     */
    /**
     * @return the swap's notional. Mirrors C++ {@code FixedVsFloatingSwap::nominal()}.
     */
    public /*@Real*/ double nominal() /* @ReadOnly */ {
        return nominal;
    }

    /**
     * @return the fixed-leg day counter. Mirrors C++
     * {@code FixedVsFloatingSwap::fixedDayCount()}.
     */
    public DayCounter fixedDayCount() /* @ReadOnly */ {
        return fixedDayCount;
    }

    /**
     * @return the floating-leg day counter. Mirrors C++
     * {@code FixedVsFloatingSwap::floatingDayCount()}.
     */
    public DayCounter floatingDayCount() /* @ReadOnly */ {
        return floatingDayCount;
    }

    /**
     * @return the floating-leg index. Mirrors C++
     * {@code FixedVsFloatingSwap::iborIndex()}.
     */
    public IborIndex iborIndex() /* @ReadOnly */ {
        return iborIndex;
    }

    /**
     * @return the payment business-day convention. Mirrors C++
     * {@code FixedVsFloatingSwap::paymentConvention()}.
     */
    public BusinessDayConvention paymentConvention() /* @ReadOnly */ {
        return paymentConvention;
    }

    public Type type() /* @ReadOnly */ {
        return type;
    }

    public /*@Rate*/ double  fairRate() /* @ReadOnly */ {
        calculate();
        QL.require(!Double.isNaN(fairRate) , "result not available"); // TODO: message
        return fairRate;
    }

    public /*@Spread*/ double fairSpread() /* @ReadOnly */ {
        calculate();
        QL.require(!Double.isNaN(fairSpread) , "result not available"); // TODO: message
        return fairSpread;
    }


    public final Leg fixedLeg() /* @ReadOnly */ {
        return legs.get(0);
    }

    public final Leg floatingLeg() /* @ReadOnly */ {
        return legs.get(1);
    }

    /**
     * @return the fixed-leg schedule. Mirrors C++ {@code VanillaSwap::fixedSchedule()}.
     */
    public final Schedule fixedSchedule() /* @ReadOnly */ {
        return fixedSchedule;
    }

    /**
     * @return the floating-leg schedule. Mirrors C++ {@code VanillaSwap::floatingSchedule()}.
     */
    public final Schedule floatingSchedule() /* @ReadOnly */ {
        return floatingSchedule;
    }

    /**
     * @return the fixed rate. Mirrors C++ {@code VanillaSwap::fixedRate()}.
     */
    public final /*@Rate*/ double fixedRate() /* @ReadOnly */ {
        return fixedRate;
    }

    /**
     * @return the spread on the floating leg. Mirrors C++ {@code VanillaSwap::spread()}.
     */
    public final /*@Spread*/ double spread() /* @ReadOnly */ {
        return spread;
    }


    public /*@Real*/ double fixedLegBPS() /* @ReadOnly */ {
        calculate();
        QL.require(!Double.isNaN(legBPS[0]) , "result not available"); // TODO: message
        return legBPS[0];
    }

    public /*@Real*/ double floatingLegBPS() /* @ReadOnly */ {
        calculate();
        QL.require(!Double.isNaN(legBPS[1]) , "result not available");
        return legBPS[1];
    }

    public /*@Real*/ double fixedLegNPV() /* @ReadOnly */ {
        calculate();
        QL.require(!Double.isNaN(legNPV[0]) , "result not available"); // TODO: message
        return legNPV[0];
    }

    public /*@Real*/ double floatingLegNPV() /* @ReadOnly */ {
        calculate();
        QL.require(!Double.isNaN(legNPV[1]) , "result not available"); // TODO: message
        return legNPV[1];
    }

    @Override
    public void setupExpired() /* @ReadOnly */ {
        super.setupExpired();
        legBPS[0] = 0.0;
        legBPS[1] = 0.0;
        fairRate   = Constants.NULL_REAL;
        fairSpread = Constants.NULL_REAL;
    }

    @Override
    public void setupArguments(final PricingEngine.Arguments arguments) /* @ReadOnly */ {
        super.setupArguments(arguments);
        // Phase 2g WI-1: align with C++ v1.42.1 fixedvsfloatingswap.cpp
        // setupArguments. C++ does dynamic_cast<FixedVsFloatingSwap::arguments*>
        // and returns early on a null cast — the equivalent Java check is
        // "is `arguments` an instance of VanillaSwap.Arguments", i.e.
        // VanillaSwap.Arguments.class.isAssignableFrom(arguments.getClass())
        // or simply arguments instanceof VanillaSwap.Arguments. The previous
        // arguments.getClass().isAssignableFrom(VanillaSwap.Arguments.class)
        // check was inverted (asks the opposite question) and was always
        // false for the common subclass case (e.g. SwaptionArgumentsImpl
        // extending VanillaSwap.ArgumentsImpl), so swaption engines never
        // saw populated swap args.
        if (arguments instanceof VanillaSwap.Arguments) {
            final VanillaSwap.ArgumentsImpl a = (VanillaSwap.ArgumentsImpl) arguments;

            a.type = type;
            a.nominal = nominal;

            final Leg fixedCoupons = fixedLeg();

            // Phase 2g WI-1: ArrayList(int) only sets *capacity*; .set(i, ...)
            // throws IndexOutOfBoundsException because size() == 0. C++
            // std::vector<Date>(n) creates a vector OF SIZE n with default-
            // constructed elements; the Java mirror is
            // new ArrayList<>(Collections.nCopies(size, null)) which yields
            // size() == n with null entries ready to be overwritten via .set().
            final int nFixed = fixedCoupons.size();
            a.fixedResetDates = new ArrayList<Date>(Collections.nCopies(nFixed, (Date) null));
            a.fixedPayDates = new ArrayList<Date>(Collections.nCopies(nFixed, (Date) null));
            a.fixedCoupons = new ArrayList</*@Real*/ Double>(Collections.nCopies(nFixed, (Double) null));

            for (int i=0; i<nFixed; i++) {
                final FixedRateCoupon coupon = (FixedRateCoupon) fixedCoupons.get(i);
                a.fixedPayDates.set(i, coupon.date());
                a.fixedResetDates.set(i, coupon.accrualStartDate());
                a.fixedCoupons.set(i, coupon.amount());
            }

            final Leg floatingCoupons = floatingLeg();

            final int nFloat = floatingCoupons.size();
            a.floatingResetDates = new ArrayList<Date>(Collections.nCopies(nFloat, (Date) null));
            a.floatingPayDates = new ArrayList<Date>(Collections.nCopies(nFloat, (Date) null));
            a.floatingFixingDates = new ArrayList<Date>(Collections.nCopies(nFloat, (Date) null));

            a.floatingAccrualTimes = new ArrayList</*@Time*/ Double>(Collections.nCopies(nFloat, (Double) null));
            a.floatingSpreads = new ArrayList</*@Spread*/ Double>(Collections.nCopies(nFloat, (Double) null));
            a.floatingCoupons = new ArrayList</*@Real*/ Double>(Collections.nCopies(nFloat, (Double) null));
            for (int i=0; i<nFloat; ++i) {
                final IborCoupon coupon = (IborCoupon) floatingCoupons.get(i);

                a.floatingResetDates.set(i, coupon.accrualStartDate());
                a.floatingPayDates.set(i, coupon.date());

                a.floatingFixingDates.set(i, coupon.fixingDate());
                a.floatingAccrualTimes.set(i, coupon.accrualPeriod());
                a.floatingSpreads.set(i, coupon.spread());
                try {
                    a.floatingCoupons.set(i, coupon.amount());
                } catch (final Exception e) {
                    a.floatingCoupons.set(i, Constants.NULL_REAL);
                }
            }
        }
    }


    @Override
    public void fetchResults(final PricingEngine.Results results) /* @ReadOnly */ {
        super.fetchResults(results);

        // Two pre-existing bugs fixed here while preparing the
        // BlackSwaptionEngine port (Phase 2e WI-3):
        //
        //  1. The original isAssignableFrom check was inverted —
        //     {@code results.getClass().isAssignableFrom(VanillaSwap.Results.class)}
        //     asks "is VanillaSwap.Results a supertype of results' class?",
        //     which is false for the common case (results is Swap.ResultsImpl
        //     produced by DiscountingSwapEngine, not a VanillaSwap.ResultsImpl).
        //     The intended check is "is results an instance of VanillaSwap.Results"
        //     i.e. {@code VanillaSwap.Results.class.isAssignableFrom(results.getClass())}
        //     — equivalently {@code results instanceof VanillaSwap.Results}.
        //
        //  2. The fallback below tested {@code Double.isNaN(fairRate)} but
        //     {@code Constants.NULL_REAL == Double.MAX_VALUE} (matches C++
        //     {@code std::numeric_limits<float>::max()}), which is not NaN.
        //     Without this fix the fallback never fired for non-Vanilla swap
        //     engines and {@code fairRate()} returned MAX_VALUE.
        //
        // C++ v1.42.1 takes a different code path (FixedVsFloatingSwap stores
        // fairRate as a private mutable populated by fetchResults from
        // FixedVsFloatingSwap::results which DiscountingSwapEngine derives
        // from). Java's narrower hierarchy here means the fallback is the
        // common case and must work.
        if (results instanceof VanillaSwap.Results) {
            final VanillaSwap.ResultsImpl r = (VanillaSwap.ResultsImpl)results;
            fairRate = r.fairRate;
            fairSpread = r.fairSpread;
        } else {
            fairRate   = Constants.NULL_REAL;
            fairSpread = Constants.NULL_REAL;
        }

        if (fairRate == Constants.NULL_REAL || Double.isNaN(fairRate)) {
            // calculate it from other results
            if (legBPS[0] != Constants.NULL_REAL && !Double.isNaN(legBPS[0])) {
                fairRate = fixedRate- NPV/(legBPS[0]/basisPoint);
            }
        }
        if (fairSpread == Constants.NULL_REAL || Double.isNaN(fairSpread)) {
            // ditto
            if (legBPS[1] != Constants.NULL_REAL && !Double.isNaN(legBPS[1])) {
                fairSpread = spread - NPV/(legBPS[1]/basisPoint);
            }
        }
    }


    @Override
    public String toString() {
        return type.toString();
    }


    //
    // inner public enums
    //

    public enum Type {
        Receiver (-1),
        Payer (1);

        private final int enumValue;

        private Type(final int frequency) {
            this.enumValue = frequency;
        }

        static public Type valueOf(final int value) {
            switch (value) {
            case -1:
                return Type.Receiver;
            case 1:
                return Type.Payer;
            default:
                throw new LibraryException("value must be one of -1, 1"); // TODO: message
            }
        }

        public int toInteger() {
            return this.enumValue;
        }
    }






    //
    // inner interfaces
    //

    public interface Arguments extends Swap.Arguments { /* marking interface */ }


    public interface Results extends Swap.Results { /* marking interface */ }


    //
    // ???? inner classes
    //


    /**
     * Arguments for simple swap calculation
     *
     * @author Richard Gomes
     */
    // TODO: code review :: object model needs to be validated and eventually refactored
    public class ArgumentsImpl extends Swap.ArgumentsImpl implements VanillaSwap.Arguments {

        public Type type;
        public /*@Real*/ double nominal;

        public List<Date> fixedResetDates;
        public List<Date> fixedPayDates;
        public List</*@Time*/ Double> floatingAccrualTimes;
        public List<Date> floatingResetDates;
        public List<Date> floatingFixingDates;
        public List<Date> floatingPayDates;

        public List</*@Real*/ Double> fixedCoupons;
        public List</*@Spread*/ Double> floatingSpreads;
        public List</*@Real*/ Double> floatingCoupons;


        @Override
        public void validate() /* @ReadOnly */ {
            super.validate();
            QL.require(!Double.isNaN(nominal) , "nominal null or not set"); // TODO: message
            QL.require(fixedResetDates.size() == fixedPayDates.size() , "number of fixed start dates different from number of fixed payment dates");
            QL.require(fixedPayDates.size() == fixedCoupons.size() , "number of fixed payment dates different from number of fixed coupon amounts");
            QL.require(floatingResetDates.size() == floatingPayDates.size() , "number of floating start dates different from number of floating payment dates");
            QL.require(floatingFixingDates.size() == floatingPayDates.size() , "number of floating fixing dates different from number of floating payment dates");
            QL.require(floatingAccrualTimes.size() == floatingPayDates.size() , "number of floating accrual Times different from number of floating payment dates");
            QL.require(floatingSpreads.size() == floatingPayDates.size() , "number of floating spreads different from number of floating payment dates");
            QL.require(floatingPayDates.size() == floatingCoupons.size() , "number of floating payment dates different from number of floating coupon amounts");
        }

    }




    /**
     * Results from simple swap calculation
     *
     * @author Richard Gomes
     */
    // TODO: code review :: object model needs to be validated and eventually refactored
    public class ResultsImpl extends Swap.ResultsImpl implements VanillaSwap.Results {

        public /*@Rate*/ double  fairRate;
        public /*@Spread*/ double  fairSpread;

        @Override
        public void reset() {
            super.reset();
            fairRate   = Constants.NULL_REAL;
            fairSpread = Constants.NULL_REAL;
        }


    }




}
