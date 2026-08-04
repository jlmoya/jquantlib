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
import org.jquantlib.cashflow.IborLeg;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.cashflow.OvernightLeg;
import org.jquantlib.cashflow.RateAveraging;
import org.jquantlib.currencies.Currency;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.indexes.OvernightIndex;
import org.jquantlib.math.Constants;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.time.Date;
import org.jquantlib.time.Schedule;

/**
 * Cross-currency basis swap: both legs float, each in its own currency, and each exchanges notional at start and
 * maturity. The first leg holds the pay-currency cashflows, the second the receive-currency cashflows.
 * <p>
 * Ported from C++ QuantLib v1.43 {@code ql/instruments/constnotionalcrosscurrencybasisswap.{hpp,cpp}} — new in that
 * release.
 *
 * @author Jose Moya
 * @category instruments
 */
public class ConstNotionalCrossCurrencyBasisSwap extends ConstNotionalCrossCurrencySwap {

    private static final double BASIS_POINT = 1.0e-4;

    private final double payNominal;
    private final Currency payCurrency;
    private final Schedule paySchedule;
    private final IborIndex payIndex;
    private final /*@Spread*/ double paySpread;
    private final double payGearing;

    private final double recNominal;
    private final Currency recCurrency;
    private final Schedule recSchedule;
    private final IborIndex recIndex;
    private final /*@Spread*/ double recSpread;
    private final double recGearing;

    private final int payPaymentLag;
    private final int recPaymentLag;

    private final boolean payCompoundSpread;
    private final int payLookbackDays;
    private final boolean payObservationShift;
    private final int payLockoutDays;
    private final RateAveraging.Type payAveragingMethod;

    private final boolean recCompoundSpread;
    private final int recLookbackDays;
    private final boolean recObservationShift;
    private final int recLockoutDays;
    private final RateAveraging.Type recAveragingMethod;

    private final boolean telescopicValueDates;

    private /*@Spread*/ double fairPaySpread;
    private /*@Spread*/ double fairRecSpread;

    //
    // public constructors
    //

    /**
     * Convenience constructor using the C++ defaults for every optional argument.
     */
    public ConstNotionalCrossCurrencyBasisSwap(final double payNominal, final Currency payCurrency,
            final Schedule paySchedule, final IborIndex payIndex, final double paySpread, final double payGearing,
            final double recNominal, final Currency recCurrency, final Schedule recSchedule, final IborIndex recIndex,
            final double recSpread, final double recGearing) {
        this(payNominal, payCurrency, paySchedule, payIndex, paySpread, payGearing, recNominal, recCurrency,
                recSchedule, recIndex, recSpread, recGearing, 0, 0, false, Constants.NULL_NATURAL, false, 0,
                RateAveraging.Type.Compound, false, Constants.NULL_NATURAL, false, 0, RateAveraging.Type.Compound,
                false);
    }

    /**
     * Full constructor. Mirrors the C++ argument order exactly.
     *
     * @param payNominal          notional of the pay leg
     * @param payCurrency         currency of the pay leg
     * @param paySchedule         payment schedule of the pay leg
     * @param payIndex            floating-rate index of the pay leg
     * @param paySpread           spread over the pay-leg floating rate
     * @param payGearing          gearing applied to the pay-leg floating rate
     * @param recNominal          notional of the receive leg
     * @param recCurrency         currency of the receive leg
     * @param recSchedule         payment schedule of the receive leg
     * @param recIndex            floating-rate index of the receive leg
     * @param recSpread           spread over the receive-leg floating rate
     * @param recGearing          gearing applied to the receive-leg floating rate
     * @param payPaymentLag       payment lag in days for an overnight pay leg
     * @param recPaymentLag       payment lag in days for an overnight receive leg
     * @param payCompoundSpread   compound the pay-leg spread daily (overnight legs only)
     * @param payLookbackDays     lookback days for an overnight pay leg
     * @param payObservationShift apply the observation shift on an overnight pay leg
     * @param payLockoutDays      lockout period, in business days, for an overnight pay leg
     * @param payAveragingMethod  averaging method for an overnight pay leg
     * @param recCompoundSpread   compound the receive-leg spread daily (overnight legs only)
     * @param recLookbackDays     lookback days for an overnight receive leg
     * @param recObservationShift apply the observation shift on an overnight receive leg
     * @param recLockoutDays      lockout period, in business days, for an overnight receive leg
     * @param recAveragingMethod  averaging method for an overnight receive leg
     * @param telescopicValueDates use telescopic value dates on overnight legs
     */
    public ConstNotionalCrossCurrencyBasisSwap(final double payNominal, final Currency payCurrency,
            final Schedule paySchedule, final IborIndex payIndex, final double paySpread, final double payGearing,
            final double recNominal, final Currency recCurrency, final Schedule recSchedule, final IborIndex recIndex,
            final double recSpread, final double recGearing, final int payPaymentLag, final int recPaymentLag,
            final boolean payCompoundSpread, final int payLookbackDays, final boolean payObservationShift,
            final int payLockoutDays, final RateAveraging.Type payAveragingMethod, final boolean recCompoundSpread,
            final int recLookbackDays, final boolean recObservationShift, final int recLockoutDays,
            final RateAveraging.Type recAveragingMethod, final boolean telescopicValueDates) {
        super(2);
        this.payNominal = payNominal;
        this.payCurrency = payCurrency;
        this.paySchedule = paySchedule;
        this.payIndex = payIndex;
        this.paySpread = paySpread;
        this.payGearing = payGearing;
        this.recNominal = recNominal;
        this.recCurrency = recCurrency;
        this.recSchedule = recSchedule;
        this.recIndex = recIndex;
        this.recSpread = recSpread;
        this.recGearing = recGearing;
        this.payPaymentLag = payPaymentLag;
        this.recPaymentLag = recPaymentLag;
        this.payCompoundSpread = payCompoundSpread;
        this.payLookbackDays = payLookbackDays;
        this.payObservationShift = payObservationShift;
        this.payLockoutDays = payLockoutDays;
        this.payAveragingMethod = payAveragingMethod;
        this.recCompoundSpread = recCompoundSpread;
        this.recLookbackDays = recLookbackDays;
        this.recObservationShift = recObservationShift;
        this.recLockoutDays = recLockoutDays;
        this.recAveragingMethod = recAveragingMethod;
        this.telescopicValueDates = telescopicValueDates;

        this.payIndex.addObserver(this);
        this.recIndex.addObserver(this);
        initialize();
    }

    //
    // private methods
    //

    private Leg buildLeg(final Schedule schedule, final IborIndex index, final double nominal, final double spread,
            final double gearing, final int paymentLag, final boolean compoundSpread, final int lookbackDays,
            final boolean observationShift, final int lockoutDays, final RateAveraging.Type averagingMethod) {
        if ( index instanceof OvernightIndex ) {
            return new OvernightLeg(schedule, (OvernightIndex) index)
                    .withNotionals(nominal)
                    .withSpreads(spread)
                    .withGearings(gearing)
                    .withPaymentLag(paymentLag)
                    .compoundingSpreadDaily(compoundSpread)
                    .withLookbackDays(lookbackDays)
                    .withObservationShift(observationShift)
                    .withLockoutDays(lockoutDays)
                    .withAveragingMethod(averagingMethod)
                    .withTelescopicValueDates(telescopicValueDates)
                    .leg();
        }
        return new IborLeg(schedule, index)
                .withNotionals(nominal)
                .withSpreads(spread)
                .withGearings(gearing)
                .withPaymentLag(paymentLag)
                .Leg();
    }

    private void initialize() {
        final Leg payLeg = buildLeg(paySchedule, payIndex, payNominal, paySpread, payGearing, payPaymentLag,
                payCompoundSpread, payLookbackDays, payObservationShift, payLockoutDays, payAveragingMethod);
        setLeg(0, payLeg);
        payer[0] = -1.0;
        currencies.set(0, payCurrency);

        final Leg recLeg = buildLeg(recSchedule, recIndex, recNominal, recSpread, recGearing, recPaymentLag,
                recCompoundSpread, recLookbackDays, recObservationShift, recLockoutDays, recAveragingMethod);
        setLeg(1, recLeg);
        payer[1] = +1.0;
        currencies.set(1, recCurrency);

        final CashFlows cf = CashFlows.getInstance();
        final Date earliestDate = Date.min(cf.startDate(payLeg), cf.startDate(recLeg));
        final Date maturityDate = Date.max(cf.maturityDate(payLeg), cf.maturityDate(recLeg));

        addNotionalExchangesToLeg(payLeg, paySchedule.calendar(), earliestDate, maturityDate, payPaymentLag,
                paySchedule.businessDayConvention(), payNominal);
        addNotionalExchangesToLeg(recLeg, recSchedule.calendar(), earliestDate, maturityDate, recPaymentLag,
                recSchedule.businessDayConvention(), recNominal);

        for ( int legNo = 0; legNo < 2; ++legNo ) {
            for ( final CashFlow item : legs.get(legNo) ) {
                item.addObserver(this);
            }
        }
    }

    //
    // public methods
    //

    public double payNominal() {
        return payNominal;
    }

    public Currency payCurrency() {
        return payCurrency;
    }

    public Schedule paySchedule() {
        return paySchedule;
    }

    public IborIndex payIndex() {
        return payIndex;
    }

    public /*@Spread*/ double paySpread() {
        return paySpread;
    }

    public double payGearing() {
        return payGearing;
    }

    public double recNominal() {
        return recNominal;
    }

    public Currency recCurrency() {
        return recCurrency;
    }

    public Schedule recSchedule() {
        return recSchedule;
    }

    public IborIndex recIndex() {
        return recIndex;
    }

    public /*@Spread*/ double recSpread() {
        return recSpread;
    }

    public double recGearing() {
        return recGearing;
    }

    /**
     * Pay-leg spread that would make the swap worth zero.
     */
    public /*@Spread*/ double fairPaySpread() {
        calculate();
        QL.require(fairPaySpread != Constants.NULL_REAL, "fair pay spread is not available");
        return fairPaySpread;
    }

    /**
     * Receive-leg spread that would make the swap worth zero.
     */
    public /*@Spread*/ double fairRecSpread() {
        calculate();
        QL.require(fairRecSpread != Constants.NULL_REAL, "fair rec spread is not available");
        return fairRecSpread;
    }

    //
    // overrides ConstNotionalCrossCurrencySwap
    //

    @Override
    public void setupArguments(final PricingEngine.Arguments args) /* @ReadOnly */ {
        super.setupArguments(args);
        /*
         * Returns here if e.g. args is ConstNotionalCrossCurrencySwap.ArgumentsImpl, which is the case when the pricing
         * engine is a plain ConstNotionalCrossCurrencySwap.EngineImpl.
         */
        if ( !(args instanceof ConstNotionalCrossCurrencyBasisSwap.ArgumentsImpl) ) {
            return;
        }
        final ConstNotionalCrossCurrencyBasisSwap.ArgumentsImpl a = (ConstNotionalCrossCurrencyBasisSwap.ArgumentsImpl) args;
        a.paySpread = paySpread;
        a.recSpread = recSpread;
    }

    @Override
    public void fetchResults(final PricingEngine.Results r) /* @ReadOnly */ {
        super.fetchResults(r);

        if ( r instanceof ConstNotionalCrossCurrencyBasisSwap.ResultsImpl ) {
            final ConstNotionalCrossCurrencyBasisSwap.ResultsImpl results = (ConstNotionalCrossCurrencyBasisSwap.ResultsImpl) r;
            fairPaySpread = results.fairPaySpread;
            fairRecSpread = results.fairRecSpread;
        } else {
            fairPaySpread = Constants.NULL_REAL;
            fairRecSpread = Constants.NULL_REAL;
        }

        // Derive the fair spreads from the leg BPSs whenever the engine did not supply them.
        if ( fairPaySpread == Constants.NULL_REAL && legBPS[0] != Constants.NULL_REAL ) {
            fairPaySpread = paySpread - NPV / (legBPS[0] / BASIS_POINT);
        }
        if ( fairRecSpread == Constants.NULL_REAL && legBPS[1] != Constants.NULL_REAL ) {
            fairRecSpread = recSpread - NPV / (legBPS[1] / BASIS_POINT);
        }
    }

    @Override
    protected void setupExpired() /* @ReadOnly */ {
        super.setupExpired();
        fairPaySpread = Constants.NULL_REAL;
        fairRecSpread = Constants.NULL_REAL;
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
            implements ConstNotionalCrossCurrencyBasisSwap.Arguments {

        public /*@Spread*/ double paySpread = Constants.NULL_REAL;
        public /*@Spread*/ double recSpread = Constants.NULL_REAL;

        @Override
        public void validate() /* @ReadOnly */ {
            super.validate();
            QL.require(paySpread != Constants.NULL_REAL, "pay spread cannot be null");
            QL.require(recSpread != Constants.NULL_REAL, "rec spread cannot be null");
        }
    }

    static public class ResultsImpl extends ConstNotionalCrossCurrencySwap.ResultsImpl
            implements ConstNotionalCrossCurrencyBasisSwap.Results {

        public /*@Spread*/ double fairPaySpread;
        public /*@Spread*/ double fairRecSpread;

        @Override
        public void reset() {
            super.reset();
            fairPaySpread = Constants.NULL_REAL;
            fairRecSpread = Constants.NULL_REAL;
        }
    }
}
