/*
 Copyright (C) 2026 Jose Moya

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
 Copyright (C) 2016 Quaternion Risk Management Ltd
 Copyright (C) 2025 Paolo D'Elia

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
 */

package org.jquantlib.instruments;

import org.jquantlib.QL;
import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.CashFlows;
import org.jquantlib.cashflow.FixedRateLeg;
import org.jquantlib.cashflow.IborLeg;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.cashflow.OvernightLeg;
import org.jquantlib.cashflow.RateAveraging;
import org.jquantlib.currencies.Currency;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.indexes.OvernightIndex;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.Constants;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Schedule;

/**
 * Cross-currency swap paying a fixed rate in one currency against a floating rate in another, with notional exchanged
 * at both ends of each leg.
 * <p>
 * Ported from C++ QuantLib v1.43 {@code ql/instruments/constnotionalcrosscurrencyfixedvsfloatingswap.{hpp,cpp}} — new
 * in that release.
 *
 * @author Jose Moya
 * @category instruments
 */
public class ConstNotionalCrossCurrencyFixedVsFloatingSwap extends ConstNotionalCrossCurrencySwap {

    private static final double BASIS_POINT = 1.0e-4;

    private final VanillaSwap.Type type;

    private final double fixedNominal;
    private final Currency fixedCurrency;
    private final Schedule fixedSchedule;
    private final /*@Rate*/ double fixedRate;
    private final DayCounter fixedDayCount;
    private final BusinessDayConvention fixedPaymentBdc;
    private final int fixedPaymentLag;
    private final Calendar fixedPaymentCalendar;

    private final double floatNominal;
    private final Currency floatCurrency;
    private final Schedule floatSchedule;
    private final IborIndex floatIndex;
    private final /*@Spread*/ double floatSpread;
    private final BusinessDayConvention floatPaymentBdc;
    private final int floatPaymentLag;
    private final Calendar floatPaymentCalendar;

    private final boolean telescopicValueDates;
    private final boolean floatCompoundSpread;
    private final int floatLookbackDays;
    private final boolean floatObservationShift;
    private final int floatLockoutDays;
    private final RateAveraging.Type floatAveragingMethod;

    private /*@Rate*/ double fairFixedRate;
    private /*@Spread*/ double fairSpread;

    //
    // public constructors
    //

    /**
     * Convenience constructor using the C++ defaults for every overnight-only argument.
     */
    public ConstNotionalCrossCurrencyFixedVsFloatingSwap(final VanillaSwap.Type type, final double fixedNominal,
            final Currency fixedCurrency, final Schedule fixedSchedule, final double fixedRate,
            final DayCounter fixedDayCount, final BusinessDayConvention fixedPaymentBdc, final int fixedPaymentLag,
            final Calendar fixedPaymentCalendar, final double floatNominal, final Currency floatCurrency,
            final Schedule floatSchedule, final IborIndex floatIndex, final double floatSpread,
            final BusinessDayConvention floatPaymentBdc, final int floatPaymentLag,
            final Calendar floatPaymentCalendar) {
        this(type, fixedNominal, fixedCurrency, fixedSchedule, fixedRate, fixedDayCount, fixedPaymentBdc,
                fixedPaymentLag, fixedPaymentCalendar, floatNominal, floatCurrency, floatSchedule, floatIndex,
                floatSpread, floatPaymentBdc, floatPaymentLag, floatPaymentCalendar, false, false,
                Constants.NULL_NATURAL, false, 0, RateAveraging.Type.Compound);
    }

    /**
     * Full constructor. Mirrors the C++ argument order exactly.
     *
     * @param type                 {@code Payer} puts the fixed leg first, {@code Receiver} puts the floating leg first
     * @param fixedNominal         notional of the fixed leg
     * @param fixedCurrency        currency of the fixed leg
     * @param fixedSchedule        payment schedule of the fixed leg
     * @param fixedRate            fixed rate
     * @param fixedDayCount        day-count convention of the fixed leg
     * @param fixedPaymentBdc      business-day convention for fixed-leg payments
     * @param fixedPaymentLag      payment lag, in days, on the fixed leg
     * @param fixedPaymentCalendar calendar for fixed-leg payments
     * @param floatNominal         notional of the floating leg
     * @param floatCurrency        currency of the floating leg
     * @param floatSchedule        payment schedule of the floating leg
     * @param floatIndex           floating-rate index
     * @param floatSpread          spread over the floating rate
     * @param floatPaymentBdc      business-day convention for floating-leg payments
     * @param floatPaymentLag      payment lag, in days, on the floating leg
     * @param floatPaymentCalendar calendar for floating-leg payments
     * @param telescopicValueDates use telescopic value dates on an overnight floating leg
     * @param floatCompoundSpread  compound the spread daily on an overnight floating leg
     * @param floatLookbackDays    lookback days on an overnight floating leg
     * @param floatObservationShift apply the observation shift on an overnight floating leg
     * @param floatLockoutDays     lockout period, in business days, on an overnight floating leg
     * @param floatAveragingMethod averaging method on an overnight floating leg
     */
    public ConstNotionalCrossCurrencyFixedVsFloatingSwap(final VanillaSwap.Type type, final double fixedNominal,
            final Currency fixedCurrency, final Schedule fixedSchedule, final double fixedRate,
            final DayCounter fixedDayCount, final BusinessDayConvention fixedPaymentBdc, final int fixedPaymentLag,
            final Calendar fixedPaymentCalendar, final double floatNominal, final Currency floatCurrency,
            final Schedule floatSchedule, final IborIndex floatIndex, final double floatSpread,
            final BusinessDayConvention floatPaymentBdc, final int floatPaymentLag,
            final Calendar floatPaymentCalendar, final boolean telescopicValueDates, final boolean floatCompoundSpread,
            final int floatLookbackDays, final boolean floatObservationShift, final int floatLockoutDays,
            final RateAveraging.Type floatAveragingMethod) {
        super(2);
        this.type = type;
        this.fixedNominal = fixedNominal;
        this.fixedCurrency = fixedCurrency;
        this.fixedSchedule = fixedSchedule;
        this.fixedRate = fixedRate;
        this.fixedDayCount = fixedDayCount;
        this.fixedPaymentBdc = fixedPaymentBdc;
        this.fixedPaymentLag = fixedPaymentLag;
        this.fixedPaymentCalendar = fixedPaymentCalendar;
        this.floatNominal = floatNominal;
        this.floatCurrency = floatCurrency;
        this.floatSchedule = floatSchedule;
        this.floatIndex = floatIndex;
        this.floatSpread = floatSpread;
        this.floatPaymentBdc = floatPaymentBdc;
        this.floatPaymentLag = floatPaymentLag;
        this.floatPaymentCalendar = floatPaymentCalendar;
        this.telescopicValueDates = telescopicValueDates;
        this.floatCompoundSpread = floatCompoundSpread;
        this.floatLookbackDays = floatLookbackDays;
        this.floatObservationShift = floatObservationShift;
        this.floatLockoutDays = floatLockoutDays;
        this.floatAveragingMethod = floatAveragingMethod;

        final Leg floatLeg = buildFloatLeg();
        for ( final CashFlow item : floatLeg ) {
            item.addObserver(this);
        }

        final Leg fixedLeg = new FixedRateLeg(fixedSchedule, fixedDayCount)
                .withNotionals(fixedNominal)
                .withCouponRates(fixedRate)
                .withPaymentAdjustment(fixedPaymentBdc)
                .withPaymentLag(fixedPaymentLag)
                .withPaymentCalendar(fixedPaymentCalendar)
                .Leg();

        final CashFlows cf = CashFlows.getInstance();
        final Date earliestDate = Date.min(cf.startDate(floatLeg), cf.startDate(fixedLeg));
        final Date maturityDate = Date.max(cf.maturityDate(floatLeg), cf.maturityDate(fixedLeg));

        addNotionalExchangesToLeg(floatLeg, floatPaymentCalendar, earliestDate, maturityDate, floatPaymentLag,
                floatPaymentBdc, floatNominal);
        addNotionalExchangesToLeg(fixedLeg, fixedPaymentCalendar, earliestDate, maturityDate, fixedPaymentLag,
                fixedPaymentBdc, fixedNominal);

        payer[0] = -1.0;
        payer[1] = +1.0;

        switch ( type ) {
        case Payer:
            setLeg(0, fixedLeg);
            currencies.set(0, fixedCurrency);
            setLeg(1, floatLeg);
            currencies.set(1, floatCurrency);
            break;
        case Receiver:
            setLeg(1, fixedLeg);
            currencies.set(1, fixedCurrency);
            setLeg(0, floatLeg);
            currencies.set(0, floatCurrency);
            break;
        default:
            throw new LibraryException("unknown cross currency fix float swap type");
        }
    }

    //
    // private methods
    //

    private Leg buildFloatLeg() {
        if ( floatIndex instanceof OvernightIndex ) {
            return new OvernightLeg(floatSchedule, (OvernightIndex) floatIndex)
                    .withNotionals(floatNominal)
                    .withSpreads(floatSpread)
                    .withPaymentAdjustment(floatPaymentBdc)
                    .withPaymentLag(floatPaymentLag)
                    .withLookbackDays(floatLookbackDays)
                    .compoundingSpreadDaily(floatCompoundSpread)
                    .withObservationShift(floatObservationShift)
                    .withPaymentCalendar(floatPaymentCalendar)
                    .withLockoutDays(floatLockoutDays)
                    .withAveragingMethod(floatAveragingMethod)
                    .withTelescopicValueDates(telescopicValueDates)
                    .leg();
        }
        return new IborLeg(floatSchedule, floatIndex)
                .withNotionals(floatNominal)
                .withSpreads(floatSpread)
                .withPaymentAdjustment(floatPaymentBdc)
                .withPaymentLag(floatPaymentLag)
                .withPaymentCalendar(floatPaymentCalendar)
                .Leg();
    }

    //
    // public methods
    //

    public VanillaSwap.Type type() {
        return type;
    }

    public double fixedNominal() {
        return fixedNominal;
    }

    public Currency fixedCurrency() {
        return fixedCurrency;
    }

    public Schedule fixedSchedule() {
        return fixedSchedule;
    }

    public /*@Rate*/ double fixedRate() {
        return fixedRate;
    }

    public DayCounter fixedDayCount() {
        return fixedDayCount;
    }

    public BusinessDayConvention fixedPaymentBdc() {
        return fixedPaymentBdc;
    }

    public int fixedPaymentLag() {
        return fixedPaymentLag;
    }

    public Calendar fixedPaymentCalendar() {
        return fixedPaymentCalendar;
    }

    public double floatNominal() {
        return floatNominal;
    }

    public Currency floatCurrency() {
        return floatCurrency;
    }

    public Schedule floatSchedule() {
        return floatSchedule;
    }

    public IborIndex floatIndex() {
        return floatIndex;
    }

    public /*@Spread*/ double floatSpread() {
        return floatSpread;
    }

    public BusinessDayConvention floatPaymentBdc() {
        return floatPaymentBdc;
    }

    public int floatPaymentLag() {
        return floatPaymentLag;
    }

    public Calendar floatPaymentCalendar() {
        return floatPaymentCalendar;
    }

    public boolean floatCompoundSpread() {
        return floatCompoundSpread;
    }

    public int floatLookbackDays() {
        return floatLookbackDays;
    }

    public int floatLockoutDays() {
        return floatLockoutDays;
    }

    public RateAveraging.Type floatAveragingMethod() {
        return floatAveragingMethod;
    }

    /**
     * Fixed rate that would make the swap worth zero.
     */
    public /*@Rate*/ double fairRate() {
        calculate();
        QL.require(fairFixedRate != Constants.NULL_REAL, "fair fixed rate is not available");
        return fairFixedRate;
    }

    /**
     * Floating-leg spread that would make the swap worth zero.
     */
    public /*@Spread*/ double fairSpread() {
        calculate();
        QL.require(fairSpread != Constants.NULL_REAL, "fair spread is not available");
        return fairSpread;
    }

    //
    // overrides ConstNotionalCrossCurrencySwap
    //

    @Override
    public void setupArguments(final PricingEngine.Arguments a) /* @ReadOnly */ {
        super.setupArguments(a);
        if ( a instanceof ConstNotionalCrossCurrencyFixedVsFloatingSwap.ArgumentsImpl ) {
            final ConstNotionalCrossCurrencyFixedVsFloatingSwap.ArgumentsImpl args = (ConstNotionalCrossCurrencyFixedVsFloatingSwap.ArgumentsImpl) a;
            args.fixedRate = fixedRate;
            args.spread = floatSpread;
        }
    }

    @Override
    public void fetchResults(final PricingEngine.Results r) /* @ReadOnly */ {
        super.fetchResults(r);

        if ( r instanceof ConstNotionalCrossCurrencyFixedVsFloatingSwap.ResultsImpl ) {
            final ConstNotionalCrossCurrencyFixedVsFloatingSwap.ResultsImpl res = (ConstNotionalCrossCurrencyFixedVsFloatingSwap.ResultsImpl) r;
            fairFixedRate = res.fairFixedRate;
            fairSpread = res.fairSpread;
        } else {
            fairFixedRate = Constants.NULL_REAL;
            fairSpread = Constants.NULL_REAL;
        }

        final int idxFixed = type == VanillaSwap.Type.Payer ? 0 : 1;
        if ( fairFixedRate == Constants.NULL_REAL && legBPS[idxFixed] != Constants.NULL_REAL ) {
            fairFixedRate = fixedRate - NPV / (legBPS[idxFixed] / BASIS_POINT);
        }

        final int idxFloat = type == VanillaSwap.Type.Payer ? 1 : 0;
        if ( fairSpread == Constants.NULL_REAL && legBPS[idxFloat] != Constants.NULL_REAL ) {
            fairSpread = floatSpread - NPV / (legBPS[idxFloat] / BASIS_POINT);
        }
    }

    @Override
    protected void setupExpired() /* @ReadOnly */ {
        super.setupExpired();
        fairFixedRate = Constants.NULL_REAL;
        fairSpread = Constants.NULL_REAL;
    }

    //
    // inner interfaces
    //

    public interface Arguments extends ConstNotionalCrossCurrencySwap.Arguments { /* marking interface */
    }

    public interface Results extends ConstNotionalCrossCurrencySwap.Results { /* marking interface */
    }

    //
    // inner classes
    //

    static public class ArgumentsImpl extends ConstNotionalCrossCurrencySwap.ArgumentsImpl
            implements ConstNotionalCrossCurrencyFixedVsFloatingSwap.Arguments {

        public /*@Rate*/ double fixedRate = Constants.NULL_REAL;
        public /*@Spread*/ double spread = Constants.NULL_REAL;

        @Override
        public void validate() /* @ReadOnly */ {
            super.validate();
            QL.require(fixedRate != Constants.NULL_REAL, "fixed rate cannot be null");
            QL.require(spread != Constants.NULL_REAL, "spread cannot be null");
        }
    }

    static public class ResultsImpl extends ConstNotionalCrossCurrencySwap.ResultsImpl
            implements ConstNotionalCrossCurrencyFixedVsFloatingSwap.Results {

        public /*@Rate*/ double fairFixedRate;
        public /*@Spread*/ double fairSpread;

        @Override
        public void reset() {
            super.reset();
            fairFixedRate = Constants.NULL_REAL;
            fairSpread = Constants.NULL_REAL;
        }
    }
}
