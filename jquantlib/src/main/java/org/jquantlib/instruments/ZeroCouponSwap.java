/*
 Copyright (C) 2026 JQuantLib migration contributors

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

package org.jquantlib.instruments;

import org.jquantlib.QL;
import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.CompoundingMultipleResetsPricer;
import org.jquantlib.cashflow.FixedRateCoupon;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.cashflow.MultipleResetsCoupon;
import org.jquantlib.cashflow.SimpleCashFlow;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.InterestRate;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;

/**
 * Zero-coupon interest rate swap.
 * <p>
 * Quoted in terms of either a known fixed cash flow {@code N^FIX} or a
 * fixed rate {@code R}, where:
 * <pre>
 *   N^FIX = N * [ (1 + R)^alpha(T_0, T_K) - 1 ]
 * </pre>
 * with {@code alpha(T_0, T_K)} the time fraction between the start date
 * and the end date according to a given day count convention. {@code N}
 * is the base notional amount prior to compounding.
 *
 * <p>The floating leg pays a single cash flow {@code N^FLT} obtained by
 * compounding the periodic IBOR fixings:
 * <pre>
 *   N^FLT = N * [ prod_{k=0..K-1} (1 + alpha(T_k, T_{k+1}) * L(T_k, T_{k+1})) - 1 ]
 * </pre>
 *
 * <p>Faithful port of QuantLib v1.42.1 {@code ZeroCouponSwap}
 * (ql/instruments/zerocouponswap.{hpp,cpp}). The floating leg is built as
 * a single {@link MultipleResetsCoupon} with a
 * {@link CompoundingMultipleResetsPricer}.
 *
 * <p>Phase 5d.5-ZCS+FB.
 */
public class ZeroCouponSwap extends Swap {

    private final VanillaSwap.Type type_;
    private final double baseNominal_;
    private final IborIndex iborIndex_;
    private final Date startDate_;
    private final Date maturityDate_;
    private final Date paymentDate_;

    /**
     * Construct a ZCS quoted by a known fixed payment.
     */
    public ZeroCouponSwap(final VanillaSwap.Type type,
                          final double baseNominal,
                          final Date startDate,
                          final Date maturityDate,
                          final double fixedPayment,
                          final IborIndex iborIndex,
                          final Calendar paymentCalendar,
                          final BusinessDayConvention paymentConvention,
                          final int paymentDelay) {
        // Common base init
        super(2);
        QL.require(!(baseNominal < 0.0), "base nominal cannot be negative");
        QL.require(startDate.lt(maturityDate),
                "start date (" + startDate + ") later than or equal to maturity date ("
                + maturityDate + ")");

        this.type_ = type;
        this.baseNominal_ = baseNominal;
        this.iborIndex_ = iborIndex;
        this.startDate_ = startDate;
        this.maturityDate_ = maturityDate;
        this.paymentDate_ = paymentCalendar.advance(maturityDate, paymentDelay,
                TimeUnit.Days, paymentConvention, false);

        // Build legs (legs[0] = fixed, legs[1] = float).
        final Leg fixedLeg = new Leg();
        final Leg floatingLeg = buildFloatingLeg();
        // legs are populated in the order Swap expects; payer signs follow type
        super.legs.add(fixedLeg);
        super.legs.add(floatingLeg);

        // Append the simple-cashflow fixed leg
        fixedLeg.add(new SimpleCashFlow(fixedPayment, paymentDate_));

        // Observer wiring
        for (final CashFlow cf : floatingLeg) {
            cf.addObserver(this);
        }

        applyPayer();
    }

    /**
     * Construct a ZCS quoted by a fixed rate.
     */
    public ZeroCouponSwap(final VanillaSwap.Type type,
                          final double baseNominal,
                          final Date startDate,
                          final Date maturityDate,
                          final double fixedRate,
                          final DayCounter fixedDayCounter,
                          final IborIndex iborIndex,
                          final Calendar paymentCalendar,
                          final BusinessDayConvention paymentConvention,
                          final int paymentDelay) {
        super(2);
        QL.require(!(baseNominal < 0.0), "base nominal cannot be negative");
        QL.require(startDate.lt(maturityDate),
                "start date (" + startDate + ") later than or equal to maturity date ("
                + maturityDate + ")");

        this.type_ = type;
        this.baseNominal_ = baseNominal;
        this.iborIndex_ = iborIndex;
        this.startDate_ = startDate;
        this.maturityDate_ = maturityDate;
        this.paymentDate_ = paymentCalendar.advance(maturityDate, paymentDelay,
                TimeUnit.Days, paymentConvention, false);

        final Leg fixedLeg = new Leg();
        final Leg floatingLeg = buildFloatingLeg();
        super.legs.add(fixedLeg);
        super.legs.add(floatingLeg);

        // Fixed-rate coupon (compounded annually) — same convention as C++
        final InterestRate interest = new InterestRate(fixedRate, fixedDayCounter,
                Compounding.Compounded, Frequency.Annual);
        fixedLeg.add(new FixedRateCoupon(baseNominal_, paymentDate_,
                interest, fixedDayCounter, startDate, maturityDate));

        for (final CashFlow cf : floatingLeg) {
            cf.addObserver(this);
        }

        applyPayer();
    }

    private Leg buildFloatingLeg() {
        // Mirrors C++ compoundedSubPeriodicCoupon():
        //   Schedule = MakeSchedule().from(start).to(end)
        //              .withTenor(index.tenor())
        //              .withCalendar(index.fixingCalendar())
        //              .withConvention(index.businessDayConvention())
        //              .backwards()
        //              .endOfMonth(index.endOfMonth());
        // The Java MakeSchedule helper does not yet expose the fluent setters
        // used in C++, so build the Schedule directly with equivalent inputs.
        final Schedule schedule = new Schedule(
                startDate_,                                 // effective
                maturityDate_,                              // termination
                iborIndex_.tenor(),                         // tenor
                iborIndex_.fixingCalendar(),                // calendar
                iborIndex_.businessDayConvention(),         // convention
                iborIndex_.businessDayConvention(),         // termination convention
                DateGeneration.Rule.Backward,               // backwards()
                iborIndex_.endOfMonth(),                    // endOfMonth
                new Date(),                                 // firstDate (default)
                new Date()                                  // nextToLastDate (default)
        );

        final MultipleResetsCoupon cpn = new MultipleResetsCoupon(
                paymentDate_, baseNominal_, schedule,
                iborIndex_.fixingDays(), iborIndex_);
        cpn.setPricer(new CompoundingMultipleResetsPricer());

        final Leg leg = new Leg();
        leg.add(cpn);
        return leg;
    }

    private void applyPayer() {
        switch (type_) {
            case Payer:
                super.payer[0] = -1.0;
                super.payer[1] = +1.0;
                break;
            case Receiver:
                super.payer[0] = +1.0;
                super.payer[1] = -1.0;
                break;
            default:
                throw new LibraryException("unknown zero coupon swap type");
        }
    }

    //
    // public inspectors
    //

    public VanillaSwap.Type type() { return type_; }

    public double baseNominal() { return baseNominal_; }

    @Override
    public Date startDate() { return startDate_.clone(); }

    @Override
    public Date maturityDate() { return maturityDate_.clone(); }

    public IborIndex iborIndex() { return iborIndex_; }

    /** Single-cashflow fixed leg. */
    public Leg fixedLeg() { return legs.get(0); }

    /** Single-cashflow floating leg. */
    public Leg floatingLeg() { return legs.get(1); }

    /** Notional amount of the fixed cashflow. */
    public double fixedPayment() { return fixedLeg().get(0).amount(); }

    //
    // public results — require the curve via the engine
    //

    public double fixedLegNPV() { return legNPV(0); }

    public double floatingLegNPV() { return legNPV(1); }

    /**
     * Fair fixed payment such that the NPV equals zero, given the current
     * floating leg NPV and discount factor at the fixed cashflow date.
     */
    public double fairFixedPayment() {
        // NPV = (discount at fixed pay date) * (payer[0] * fixed amount)
        //     + (discount at float pay date) * (payer[1] * float amount)
        // For NPV = 0, fair amount = NPV float / (discount * scaling)
        final double scaling = (super.payer[1] < 0.0) ? -1.0 : 1.0;
        return floatingLegNPV() / (endDiscounts(0) * scaling);
    }

    /**
     * Fair fixed compounded-annual rate implied by the fair fixed payment.
     */
    public double fairFixedRate(final DayCounter dayCounter) {
        // Compound factor C = N^FIX / N + 1
        final double compound = fairFixedPayment() / baseNominal_ + 1.0;
        return InterestRate.impliedRate(compound, startDate_, maturityDate_,
                dayCounter, Compounding.Compounded, Frequency.Annual).rate();
    }
}
