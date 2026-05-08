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
 Copyright (C) 2007, 2009, 2011 Chris Kenyon
 Copyright (C) 2009 StatPro Italia srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.instruments;

import java.util.Arrays;

import org.jquantlib.QL;
import org.jquantlib.cashflow.CPICashFlow;
import org.jquantlib.cashflow.CPICoupon;
import org.jquantlib.cashflow.CPICouponPricer;
import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.IborLeg;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.cashflow.SimpleCashFlow;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.CPI;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.indexes.ZeroInflationIndex;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.Constants;
import org.jquantlib.pricingengines.GenericEngine;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;

/**
 * Zero-inflation-indexed-ratio-with-base swap.
 *
 * <p>Mirrors C++ v1.42.1 {@code QuantLib::CPISwap}
 * ({@code ql/instruments/cpiswap.{hpp,cpp}}).
 *
 * <p>Fixed x zero-inflation, i.e. fixed x {@code CPI(i'th fixing) / CPI(base)}
 * versus floating + spread.
 *
 * <p>This swap can mimic a ZCIIS where {@code [(1+q)^n - 1]} is exchanged
 * against {@code (cpi ratio - 1)}, by using different nominals on each leg
 * and setting {@code subtractInflationNominal} to true. ALSO — there must
 * be just one date in each schedule.
 *
 * <p>The Java port mirrors C++ {@code Swap::Type} via {@link Type} and emits
 * standard {@link Swap} legs (CPI leg + floating-leg). Pricing is via
 * {@code DiscountingSwapEngine}; {@code fairRate} / {@code fairSpread} are
 * computed from the engine's NPV/legBPS results inline (the C++ fallback
 * path when the engine is a generic Swap engine).
 *
 * @author JQuantLib migration team (Phase 2r C.1)
 */
public class CPISwap extends Swap {

    //
    // public inner enums
    //

    /**
     * Payer/Receiver type — refers to the FLOATING leg (matches C++).
     */
    public enum Type {
        Receiver(-1),
        Payer(1);

        private final int value;

        Type(final int value) {
            this.value = value;
        }

        public int toInteger() {
            return value;
        }

        public static Type valueOf(final int v) {
            switch (v) {
                case -1: return Receiver;
                case  1: return Payer;
                default: throw new LibraryException("value must be -1 (Receiver) or 1 (Payer)");
            }
        }
    }

    //
    // private final fields
    //

    private final Type type_;
    private final double nominal_;
    private final boolean subtractInflationNominal_;

    // float + spread leg
    private final double spread_;
    private final DayCounter floatDayCount_;
    private final Schedule floatSchedule_;
    private final BusinessDayConvention floatPaymentRoll_;
    private final int fixingDays_;
    private final IborIndex floatIndex_;

    // fixed x inflation leg
    private final double fixedRate_;
    private final double baseCPI_;
    private final DayCounter fixedDayCount_;
    private final Schedule fixedSchedule_;
    private final BusinessDayConvention fixedPaymentRoll_;
    private final ZeroInflationIndex fixedIndex_;
    private final Period observationLag_;
    private final CPI.InterpolationType observationInterpolation_;
    private final double inflationNominal_;

    //
    // public constructors
    //

    public CPISwap(final Type type,
                   final double nominal,
                   final boolean subtractInflationNominal,
                   // float + spread leg
                   final double spread,
                   final DayCounter floatDayCount,
                   final Schedule floatSchedule,
                   final BusinessDayConvention floatRoll,
                   final int fixingDays,
                   final IborIndex floatIndex,
                   // fixed x inflation leg
                   final double fixedRate,
                   final double baseCPI,
                   final DayCounter fixedDayCount,
                   final Schedule fixedSchedule,
                   final BusinessDayConvention fixedRoll,
                   final Period observationLag,
                   final ZeroInflationIndex fixedIndex) {
        this(type, nominal, subtractInflationNominal,
                spread, floatDayCount, floatSchedule, floatRoll, fixingDays, floatIndex,
                fixedRate, baseCPI, fixedDayCount, fixedSchedule, fixedRoll,
                observationLag, fixedIndex,
                CPI.InterpolationType.AsIndex, Constants.NULL_REAL);
    }

    public CPISwap(final Type type,
                   final double nominal,
                   final boolean subtractInflationNominal,
                   // float + spread leg
                   final double spread,
                   final DayCounter floatDayCount,
                   final Schedule floatSchedule,
                   final BusinessDayConvention floatRoll,
                   final int fixingDays,
                   final IborIndex floatIndex,
                   // fixed x inflation leg
                   final double fixedRate,
                   final double baseCPI,
                   final DayCounter fixedDayCount,
                   final Schedule fixedSchedule,
                   final BusinessDayConvention fixedRoll,
                   final Period observationLag,
                   final ZeroInflationIndex fixedIndex,
                   final CPI.InterpolationType observationInterpolation,
                   final double inflationNominal) {
        super(2);
        this.type_ = type;
        this.nominal_ = nominal;
        this.subtractInflationNominal_ = subtractInflationNominal;
        this.spread_ = spread;
        this.floatDayCount_ = floatDayCount;
        this.floatSchedule_ = floatSchedule;
        this.floatPaymentRoll_ = floatRoll;
        this.fixingDays_ = fixingDays;
        this.floatIndex_ = floatIndex;
        this.fixedRate_ = fixedRate;
        this.baseCPI_ = baseCPI;
        this.fixedDayCount_ = fixedDayCount;
        this.fixedSchedule_ = fixedSchedule;
        this.fixedPaymentRoll_ = fixedRoll;
        this.fixedIndex_ = fixedIndex;
        this.observationLag_ = observationLag;
        this.observationInterpolation_ = observationInterpolation;

        QL.require(!floatSchedule_.empty(), "empty float schedule");
        QL.require(!fixedSchedule_.empty(), "empty fixed schedule");

        if (Double.isNaN(inflationNominal) || inflationNominal == Constants.NULL_REAL) {
            this.inflationNominal_ = nominal_;
        } else {
            this.inflationNominal_ = inflationNominal;
        }

        // Build the floating leg (Ibor) when more than one date exists.
        Leg floatingLeg = new Leg();
        if (floatSchedule_.size() > 1) {
            floatingLeg = new IborLeg(floatSchedule_, floatIndex_)
                    .withNotionals(nominal_)
                    .withSpreads(spread_)
                    .withPaymentDayCounter(floatDayCount_)
                    .withPaymentAdjustment(floatPaymentRoll_)
                    .withFixingDays(fixingDays_)
                    .Leg();
        }

        if (floatSchedule_.size() == 1
                || !subtractInflationNominal_
                || (subtractInflationNominal_
                        && Math.abs(nominal_ - inflationNominal_) > 0.00001)) {
            Date payNotional;
            if (floatSchedule_.size() == 1) { // no coupons
                payNotional = floatSchedule_.date(0);
                payNotional = floatSchedule_.calendar().adjust(payNotional, floatPaymentRoll_);
            } else { // pay date of last coupon
                payNotional = floatingLeg.get(floatingLeg.size() - 1).date();
            }
            final double floatAmount = subtractInflationNominal_
                    ? nominal_ - inflationNominal_ : nominal_;
            floatingLeg.add(new SimpleCashFlow(floatAmount, payNotional));
        }

        // Build CPI leg inline (replicates C++ CPILeg::operator Leg() for the
        // simple swaplet path — no caps/floors, fixedRate != 0).
        final Leg cpiLeg = buildCpiLeg();

        // register coupons as observers
        for (final CashFlow cf : cpiLeg) {
            cf.addObserver(this);
        }
        for (final CashFlow cf : floatingLeg) {
            cf.addObserver(this);
        }

        legs.add(cpiLeg);
        legs.add(floatingLeg);

        if (type_ == Type.Payer) {
            payer[0] = 1.0;
            payer[1] = -1.0;
        } else {
            payer[0] = -1.0;
            payer[1] = 1.0;
        }
    }

    /**
     * Build the CPI leg (mirrors C++ {@code CPILeg::operator Leg()} for the
     * simple path: no caps/floors, fixedRate != 0, plus a terminal
     * CPICashFlow notional flow).
     */
    private Leg buildCpiLeg() {
        final int n = fixedSchedule_.size() - 1;
        QL.require(n >= 0, "fixed schedule must have at least 1 date");

        final Leg leg = new Leg();
        Date baseDate = new Date(); // default-constructed null date
        // Note: CPILeg also has withBaseDate; for simple constructor here we
        // leave baseDate as null and rely on the supplied baseCPI_.

        for (int i = 0; i < n; ++i) {
            final Date start = fixedSchedule_.date(i);
            final Date end = fixedSchedule_.date(i + 1);
            final Date paymentDate = fixedSchedule_.calendar().adjust(end, fixedPaymentRoll_);

            if (fixedRate_ == 0.0) {
                // fixed coupon path (C++ optimization) — produce a FixedRateCoupon.
                // Caps/floors not implemented here.
                throw new LibraryException(
                        "fixedRate=0.0 path (FixedRateCoupon optimization) not yet ported");
            }
            // simple swaplet path — CPICoupon
            final CPICoupon coupon = new CPICoupon(
                    baseCPI_, baseDate,
                    paymentDate,
                    inflationNominal_,
                    start, end,
                    fixedIndex_,
                    observationLag_,
                    observationInterpolation_,
                    fixedDayCount_,
                    fixedRate_,
                    /* refPeriodStart */ start,
                    /* refPeriodEnd   */ end,
                    /* exCouponDate   */ new Date());
            leg.add(coupon);
        }

        // Terminal notional cash flow (always present in CPI legs, per C++).
        final Date payDate = fixedSchedule_.calendar()
                .adjust(fixedSchedule_.date(n), fixedPaymentRoll_);
        final CPICashFlow notionalFlow = new CPICashFlow(
                inflationNominal_, fixedIndex_,
                baseDate, baseCPI_,
                fixedSchedule_.date(n),
                observationLag_,
                observationInterpolation_,
                payDate,
                subtractInflationNominal_);
        leg.add(notionalFlow);

        // attach default CPICouponPricer (no nominal TS — discounting handled by
        // the swap engine)
        final CPICouponPricer pricer = new CPICouponPricer();
        for (final CashFlow cf : leg) {
            if (cf instanceof CPICoupon) {
                ((CPICoupon) cf).setPricer(pricer);
            }
        }
        return leg;
    }

    //
    // results / inspectors
    //

    public double floatLegNPV() {
        calculate();
        QL.require(legNPV[1] != Constants.NULL_REAL, "result not available");
        return legNPV[1];
    }

    public double fixedLegNPV() {
        calculate();
        QL.require(legNPV[0] != Constants.NULL_REAL, "result not available");
        return legNPV[0];
    }

    /**
     * Fair rate (closed-form fallback per C++ fetchResults path):
     * {@code fairRate = fixedRate - NPV / (legBPS[0]/basisPoint)}.
     */
    public double fairRate() {
        calculate();
        final double basisPoint = 1.0e-4;
        QL.require(!Double.isNaN(legBPS[0]) && legBPS[0] != Constants.NULL_REAL,
                "fair rate result not available — legBPS[0] is unavailable");
        return fixedRate_ - NPV / (legBPS[0] / basisPoint);
    }

    /**
     * Fair spread (closed-form fallback):
     * {@code fairSpread = spread - NPV / (legBPS[1]/basisPoint)}.
     */
    public double fairSpread() {
        calculate();
        final double basisPoint = 1.0e-4;
        QL.require(!Double.isNaN(legBPS[1]) && legBPS[1] != Constants.NULL_REAL,
                "fair spread result not available — legBPS[1] is unavailable");
        return spread_ - NPV / (legBPS[1] / basisPoint);
    }

    public Type type() { return type_; }
    public double nominal() { return nominal_; }
    public boolean subtractInflationNominal() { return subtractInflationNominal_; }

    public double spread() { return spread_; }
    public DayCounter floatDayCount() { return floatDayCount_; }
    public Schedule floatSchedule() { return floatSchedule_; }
    public BusinessDayConvention floatPaymentRoll() { return floatPaymentRoll_; }
    public int fixingDays() { return fixingDays_; }
    public IborIndex floatIndex() { return floatIndex_; }

    public double fixedRate() { return fixedRate_; }
    public double baseCPI() { return baseCPI_; }
    public DayCounter fixedDayCount() { return fixedDayCount_; }
    public Schedule fixedSchedule() { return fixedSchedule_; }
    public BusinessDayConvention fixedPaymentRoll() { return fixedPaymentRoll_; }
    public Period observationLag() { return observationLag_; }
    public ZeroInflationIndex fixedIndex() { return fixedIndex_; }
    public CPI.InterpolationType observationInterpolation() { return observationInterpolation_; }
    public double inflationNominal() { return inflationNominal_; }

    public Leg cpiLeg() { return legs.get(0); }
    public Leg floatLeg() { return legs.get(1); }

    //
    // overrides Swap
    //

    @Override
    protected void setupExpired() {
        super.setupExpired();
        Arrays.fill(legBPS, 0.0);
    }

    @Override
    public void setupArguments(final PricingEngine.Arguments args) {
        super.setupArguments(args);
        // CPISwap-specific arguments piggyback on Swap.ArgumentsImpl populated
        // by the parent. Mirrors C++ {@code CPISwap::setupArguments} — base
        // method is enough; concrete CPISwap arguments are only consumed by
        // CPISwap-specific engines (none ported in Phase 2r).
    }

    //
    // public inner classes
    //

    /** Marking interface; mirrors C++ {@code CPISwap::arguments}. */
    public interface Arguments extends Swap.Arguments { /* marker */ }

    /** Marking interface; mirrors C++ {@code CPISwap::results}. */
    public interface Results extends Swap.Results { /* marker */ }

    /** Concrete arguments DTO. */
    public static class ArgumentsImpl extends Swap.ArgumentsImpl
            implements CPISwap.Arguments {
        public Type type = Type.Receiver;
        public double nominal = Constants.NULL_REAL;

        @Override
        public void validate() {
            super.validate();
        }
    }

    /** Concrete results DTO. */
    public static class ResultsImpl extends Swap.ResultsImpl
            implements CPISwap.Results {
        public double fairRate = Constants.NULL_REAL;
        public double fairSpread = Constants.NULL_REAL;

        @Override
        public void reset() {
            super.reset();
            fairRate = Constants.NULL_REAL;
            fairSpread = Constants.NULL_REAL;
        }
    }

    /**
     * CPISwap engine. Mirrors C++ {@code CPISwap::engine =
     * GenericEngine<CPISwap::arguments, CPISwap::results>}.
     */
    public abstract static class Engine
            extends GenericEngine<CPISwap.Arguments, CPISwap.Results> {
        protected Engine() {
            super(new CPISwap.ArgumentsImpl(), new CPISwap.ResultsImpl());
        }
    }
}
