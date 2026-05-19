/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2008 Allen Kuo

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */
package org.jquantlib.experimental.callablebonds;

import org.jquantlib.QL;
import org.jquantlib.cashflow.Callability;
import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.CashFlows;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.experimental.callablebonds.CallableBond.CallableBondArgumentsImpl;
import org.jquantlib.experimental.callablebonds.CallableBond.CallableBondEngineImpl;
import org.jquantlib.experimental.callablebonds.CallableBond.CallableBondResultsImpl;
import org.jquantlib.instruments.Option;
import org.jquantlib.pricingengines.BlackFormula;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.InterestRate;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.calendars.NullCalendar;

/**
 * Black-formula callable fixed-rate bond engine.
 * <p>
 * Port of C++ v1.42.1 {@code ql/experimental/callablebonds/blackcallablebondengine.{hpp,cpp}}.
 * <p>
 * The embedded (European) option follows the Black "European bond option" treatment in Hull, Fourth Edition, Chapter
 * 20.
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 * <li>Uses {@link CashFlows#irr} (instance method) and {@link CashFlows#duration}
 *     (instance method) since {@link CashFlows} does not expose static
 *     {@code yield()} / {@code duration()} variants taking
 *     {@code includeSettlementDateFlows + npvDate} like the C++ static methods.
 *     Behaviorally equivalent for the Black engine's needs.
 * <li>Mirrors C++'s implicit instantiation of a {@link CallableBondConstantVolatility}
 *     wrapping the supplied {@link Quote} when the simpler ctor is used.
 * </ul>
 */
public class BlackCallableFixedRateBondEngine extends CallableBondEngineImpl {

    protected final Handle< CallableBondVolatilityStructure > volatility_;
    protected final Handle< YieldTermStructure > discountCurve_;

    /**
     * Convenience ctor wrapping a {@link Quote} handle with a {@link CallableBondConstantVolatility} on the fly.
     * Mirrors the C++ {@code BlackCallableFixedRateBondEngine(Handle<Quote>, Handle<YieldTermStructure>)}.
     */
    public BlackCallableFixedRateBondEngine(final Handle< Quote > fwdYieldVol,
            final Handle< YieldTermStructure > discountCurve) {
        super();
        final CallableBondVolatilityStructure vol = new CallableBondConstantVolatility(0, new NullCalendar(),
                fwdYieldVol, new Actual365Fixed());
        this.volatility_ = new Handle< CallableBondVolatilityStructure >(vol);
        this.discountCurve_ = discountCurve;
        this.volatility_.addObserver(this);
        this.discountCurve_.addObserver(this);
    }

    /**
     * Visible to subclasses (e.g. {@link BlackCallableZeroCouponBondEngine}) so they can wire their own static factory
     * through this path.
     */
    protected BlackCallableFixedRateBondEngine(final Handle< CallableBondVolatilityStructure > yieldVolStructure,
            final Handle< YieldTermStructure > discountCurve, final boolean marker) {
        super();
        this.volatility_ = yieldVolStructure;
        this.discountCurve_ = discountCurve;
        this.volatility_.addObserver(this);
        this.discountCurve_.addObserver(this);
    }

    /**
     * Java port: factory matching C++
     * {@code BlackCallableFixedRateBondEngine(Handle<CallableBondVolatilityStructure>, Handle<YieldTermStructure>)}
     * expressed as a static method because Java type erasure forbids two ctors differing only in the {@link Handle}
     * type parameter.
     */
    public static BlackCallableFixedRateBondEngine fromVolStructure(
            final Handle< CallableBondVolatilityStructure > yieldVolStructure,
            final Handle< YieldTermStructure > discountCurve) {
        return new BlackCallableFixedRateBondEngine(yieldVolStructure, discountCurve, true);
    }

    @Override
    public void calculate() {
        final CallableBondArgumentsImpl args = (CallableBondArgumentsImpl) arguments_;
        final CallableBondResultsImpl results = (CallableBondResultsImpl) results_;

        QL.require(args.putCallSchedule.size() == 1, "Must have exactly one call/put date to use Black Engine");

        final Date settle = args.settlementDate;
        final Date exerciseDate = args.callabilityDates.get(0);
        QL.require(exerciseDate.ge(settle), "must have exercise Date >= settlement Date");

        final Leg fixedLeg = args.cashflows;
        final YieldTermStructure ts = discountCurve_.currentLink();

        // Phase 5e.5b-CFC-d-271 fix: C++ engine uses the 4-arg CashFlows::npv
        // overload where `npvDate` defaults to `settlementDate`, producing the
        // PRESENT value AT the settlement (i.e. divided by ts.discount(settle)).
        // The Java static `CashFlows.npv(..., settle, null)` returns the
        // un-rescaled total instead (PV at the curve's reference date), which
        // is wrong whenever settle != referenceDate. Explicitly pass the
        // settlement as the npvDate so the division is performed.
        final double value = CashFlows.npv(fixedLeg, ts, false, settle, settle);
        final double npv = CashFlows.npv(fixedLeg, ts, false, ts.referenceDate(), ts.referenceDate());

        final double fwdCashPrice = (value - spotIncome()) / ts.discount(exerciseDate);
        final double cashStrike = args.callabilityPrices.get(0) * args.faceAmount / 100.0;

        final Option.Type type = (args.putCallSchedule.get(0).type() == Callability.Type.Call)
                ? Option.Type.Call
                : Option.Type.Put;

        final double priceVol = forwardPriceVolatility();

        final double exerciseTime = volatility_.currentLink().dayCounter()
                .yearFraction(volatility_.currentLink().referenceDate(), exerciseDate);

        final double discount = ts.discount(exerciseDate);
        final double discountToSettlement = discount / ts.discount(settle);

        final double embeddedOptionValue = BlackFormula.blackFormula(type, cashStrike, fwdCashPrice,
                priceVol * Math.sqrt(exerciseTime));

        if ( type == Option.Type.Call ) {
            results.value = npv - embeddedOptionValue * discount;
            results.settlementValue = value - embeddedOptionValue * discountToSettlement;
        } else {
            results.value = npv + embeddedOptionValue * discount;
            results.settlementValue = value + embeddedOptionValue * discountToSettlement;
        }
    }

    /** Present value of all coupons paid during the life of the option. */
    private double spotIncome() {
        final CallableBondArgumentsImpl args = (CallableBondArgumentsImpl) arguments_;
        final Date settlement = args.settlementDate;
        final Leg cf = args.cashflows;
        final Date optionMaturity = args.putCallSchedule.get(0).date();
        final YieldTermStructure ts = discountCurve_.currentLink();

        /* Assumes:
           1. cashflows are in ascending order!
           2. income = coupons paid between settlementDate() and put/call date
           Java port: walk every cashflow and break on the first that has not
           yet been called (regardless of whether it's a coupon or redemption);
           the unstable sort caveat described in CallableBond.setupArguments
           does not apply here because this loop discriminates by date, not
           by trailing position.
        */
        double income = 0.0;
        for ( int i = 0; i < cf.size(); i++ ) {
            final CashFlow c = cf.get(i);
            if ( !c.hasOccurred(settlement, false) ) {
                if ( c.hasOccurred(optionMaturity, false) ) {
                    income += c.amount() * ts.discount(c.date());
                } else {
                    break;
                }
            }
        }
        return income / ts.discount(settlement);
    }

    /** Converts the yield volatility into a forward price volatility. */
    private double forwardPriceVolatility() {
        final CallableBondArgumentsImpl args = (CallableBondArgumentsImpl) arguments_;
        final Date bondMaturity = args.redemptionDate;
        final Date exerciseDate = args.callabilityDates.get(0);
        final Leg fixedLeg = args.cashflows;
        final YieldTermStructure ts = discountCurve_.currentLink();

        // value of bond cash flows at option maturity (forward NPV).
        // Phase 5e.5b-CFC-d-271 fix: C++ uses the 4-arg CashFlows::npv where
        // `npvDate` defaults to `settlementDate`; pass exerciseDate explicitly
        // as the npvDate so the result is the forward PV AT exerciseDate
        // (i.e. divided by ts.discount(exerciseDate)), not the raw sum of
        // discounted future cashflows.
        final double fwdNpv = CashFlows.npv(fixedLeg, ts, false, exerciseDate, exerciseDate);

        DayCounter dayCounter = args.paymentDayCounter;
        Frequency frequency = args.frequency;

        // adjust if zero-coupon bond
        if ( frequency == Frequency.NoFrequency || frequency == Frequency.Once ) {
            frequency = Frequency.Annual;
        }

        // C++ uses static CashFlows::yield(leg, npv, dc, comp, freq, false, exerciseDate);
        // Java's CashFlows.irr does the equivalent IRR root-find via Brent.
        final CashFlows cashflows = CashFlows.getInstance();
        final double fwdYtm = cashflows.irr(fixedLeg, fwdNpv, dayCounter, Compounding.Compounded, frequency,
                exerciseDate, 1.0e-10, 100, 0.05);

        final InterestRate fwdRate = new InterestRate(fwdYtm, dayCounter, Compounding.Compounded, frequency);

        // C++: CashFlows::duration(leg, fwdRate, Duration.Modified, false, exerciseDate)
        final double fwdDur = cashflows.duration(fixedLeg, fwdRate, CashFlows.Duration.Modified, exerciseDate);

        final double cashStrike = args.callabilityPrices.get(0) * args.faceAmount / 100.0;
        final DayCounter volDc = volatility_.currentLink().dayCounter();
        final Date referenceDate = volatility_.currentLink().referenceDate();
        final double exerciseTime = volDc.yearFraction(referenceDate, exerciseDate);
        final double maturityTime = volDc.yearFraction(referenceDate, bondMaturity);
        final double yieldVol = volatility_.currentLink()
                .volatility(exerciseTime, maturityTime - exerciseTime, cashStrike);
        return yieldVol * fwdDur * fwdYtm;
    }
}
