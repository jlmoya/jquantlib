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
 Copyright (C) 2023 Marcin Rybacki

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.instruments;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.EquityCashFlow;
import org.jquantlib.cashflow.IborLeg;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.cashflow.OvernightLeg;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.EquityIndex;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.indexes.InterestRateIndex;
import org.jquantlib.indexes.OvernightIndex;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.NullCalendar;

/**
 * Equity total return swap (TRS).
 *
 * <p>Exchanges the total return on an equity index for a floating
 * (Ibor- or Overnight-linked) funding leg. The equity-leg future
 * value is
 * <pre>
 *   FV^{equity} = N * (I(t, T_M) / I(T_0) - 1)
 * </pre>
 * where {@code N} is the swap notional, {@code I(T_0)} is the value of
 * the equity index on the start date, and {@code I(t, T_M)} is the
 * value at maturity.
 *
 * <p>Swap type ({@code Payer} / {@code Receiver}) refers to the equity
 * leg.
 *
 * <p>Mirrors C++ {@code QuantLib::EquityTotalReturnSwap} at v1.42.1
 * ({@code ql/instruments/equitytotalreturnswap.{hpp,cpp}}).
 *
 * <p>Java port note: {@code IborLeg} in this codebase does not yet expose
 * {@code withPaymentCalendar} or {@code withPaymentLag}. For the IBOR
 * variant the constructor records those fields for inspection but the
 * generated coupons pay on the schedule date with no extra delay
 * (consistent with the default Java IborCoupon path). The OvernightIndex
 * variant honours both arguments via {@link OvernightLeg}. This deviation
 * from C++ is documented and exercised by the
 * {@code EquityTotalReturnSwapTest} cases that pass {@code paymentDelay=0}.
 *
 * @author JQuantLib migration team (Phase 5d.5-EQ)
 */
public class EquityTotalReturnSwap extends Swap {

    private final EquityIndex equityIndex_;
    private final InterestRateIndex interestRateIndex_;
    private final VanillaSwap.Type type_;
    private final double nominal_;
    private final Schedule schedule_;
    private final DayCounter dayCounter_;
    private final double margin_;
    private final double gearing_;
    private final Calendar paymentCalendar_;
    private final BusinessDayConvention paymentConvention_;
    private final int paymentDelay_;

    //
    // public constructors
    //

    public EquityTotalReturnSwap(final VanillaSwap.Type type,
                                 final double nominal,
                                 final Schedule schedule,
                                 final EquityIndex equityIndex,
                                 final IborIndex interestRateIndex,
                                 final DayCounter dayCounter,
                                 final double margin,
                                 final double gearing,
                                 final Calendar paymentCalendar,
                                 final BusinessDayConvention paymentConvention,
                                 final int paymentDelay) {
        super(2);
        this.type_ = type;
        this.nominal_ = nominal;
        this.schedule_ = schedule;
        this.equityIndex_ = equityIndex;
        this.interestRateIndex_ = interestRateIndex;
        this.dayCounter_ = dayCounter;
        this.margin_ = margin;
        this.gearing_ = gearing;
        this.paymentCalendar_ = paymentCalendar;
        this.paymentConvention_ = paymentConvention;
        this.paymentDelay_ = paymentDelay;

        QL.require(!(nominal_ < 0.0), "Nominal cannot be negative");

        // Equity leg (single cashflow on the schedule's start/end).
        final Leg equityLeg = new Leg();
        equityLeg.add(createEquityCashFlow());
        legs.add(equityLeg);

        // Floating leg via IborLeg (paymentCalendar / paymentLag dropped —
        // see class doc).
        final Leg floatingLeg = new IborLeg(schedule, interestRateIndex)
                .withNotionals(nominal)
                .withPaymentDayCounter(dayCounter)
                .withSpreads(margin)
                .withGearings(gearing)
                .withPaymentAdjustment(paymentConvention)
                .Leg();
        legs.add(floatingLeg);

        wirePayerSign();
        wireObservers();
    }

    public EquityTotalReturnSwap(final VanillaSwap.Type type,
                                 final double nominal,
                                 final Schedule schedule,
                                 final EquityIndex equityIndex,
                                 final OvernightIndex interestRateIndex,
                                 final DayCounter dayCounter,
                                 final double margin,
                                 final double gearing,
                                 final Calendar paymentCalendar,
                                 final BusinessDayConvention paymentConvention,
                                 final int paymentDelay) {
        super(2);
        this.type_ = type;
        this.nominal_ = nominal;
        this.schedule_ = schedule;
        this.equityIndex_ = equityIndex;
        this.interestRateIndex_ = interestRateIndex;
        this.dayCounter_ = dayCounter;
        this.margin_ = margin;
        this.gearing_ = gearing;
        this.paymentCalendar_ = paymentCalendar;
        this.paymentConvention_ = paymentConvention;
        this.paymentDelay_ = paymentDelay;

        QL.require(!(nominal_ < 0.0), "Nominal cannot be negative");

        final Leg equityLeg = new Leg();
        equityLeg.add(createEquityCashFlow());
        legs.add(equityLeg);

        final Leg floatingLeg = new OvernightLeg(schedule, interestRateIndex)
                .withNotionals(nominal)
                .withPaymentDayCounter(dayCounter)
                .withSpreads(margin)
                .withGearings(gearing)
                .withPaymentCalendar(paymentCalendar)
                .withPaymentAdjustment(paymentConvention)
                .withPaymentLag(paymentDelay)
                .leg();
        legs.add(floatingLeg);

        wirePayerSign();
        wireObservers();
    }

    //
    // private helpers
    //

    /**
     * Build the single {@link EquityCashFlow}. Mirrors the anonymous-namespace
     * helper {@code createEquityCashFlow} at
     * {@code ql/instruments/equitytotalreturnswap.cpp:31-49}.
     */
    private CashFlow createEquityCashFlow() {
        final Date startDate = schedule_.startDate();
        final Date endDate = schedule_.endDate();

        Calendar cal = paymentCalendar_;
        if (cal == null || cal.empty()) {
            QL.require(schedule_.calendar() != null && !schedule_.calendar().empty(),
                    "Calendar in schedule cannot be empty");
            cal = schedule_.calendar();
        }
        final Date paymentDate = cal.advance(endDate, paymentDelay_, TimeUnit.Days,
                paymentConvention_, schedule_.endOfMonth());
        return new EquityCashFlow(nominal_, equityIndex_, startDate, endDate, paymentDate);
    }

    private void wirePayerSign() {
        switch (type_) {
        case Payer:
            payer[0] = -1.0;
            payer[1] = +1.0;
            break;
        case Receiver:
            payer[0] = +1.0;
            payer[1] = -1.0;
            break;
        default:
            QL.require(false, "unknown equity total return swap type");
        }
    }

    private void wireObservers() {
        for (final Leg leg : legs) {
            for (final CashFlow item : leg) {
                item.addObserver(this);
            }
        }
    }

    //
    // public inspectors
    //

    public VanillaSwap.Type type() { return type_; }
    public double nominal() { return nominal_; }
    public EquityIndex equityIndex() { return equityIndex_; }
    public InterestRateIndex interestRateIndex() { return interestRateIndex_; }
    public Schedule schedule() { return schedule_; }
    public DayCounter dayCounter() { return dayCounter_; }
    public double margin() { return margin_; }
    public double gearing() { return gearing_; }
    public Calendar paymentCalendar() { return paymentCalendar_; }
    public BusinessDayConvention paymentConvention() { return paymentConvention_; }
    public int paymentDelay() { return paymentDelay_; }

    public Leg equityLeg() { return legs.get(0); }
    public Leg interestRateLeg() { return legs.get(1); }

    //
    // public results
    //

    public double equityLegNPV() { return legNPV(0); }
    public double interestRateLegNPV() { return legNPV(1); }

    /**
     * Mirrors C++ {@code EquityTotalReturnSwap::fairMargin} at
     * {@code ql/instruments/equitytotalreturnswap.cpp:185-194}.
     */
    public double fairMargin() {
        final double basisPoint = 1.0e-4;
        final double interestLegBps = legBPS(1) / basisPoint;
        final double exMarginInterestLegNpv = interestRateLegNPV() - margin_ * interestLegBps;
        return -(equityLegNPV() + exMarginInterestLegNpv) / interestLegBps;
    }
}
