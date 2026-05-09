/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2018 Sebastian Schlenkrich

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <http://www.quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
*/

/*! \file swaptioncfs.hpp/.cpp
    \brief translate swaption into deterministic fixed and float cash flows
*/

package org.jquantlib.experimental.basismodels;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.Coupon;
import org.jquantlib.cashflow.FixedRateCoupon;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.cashflow.SimpleCashFlow;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.instruments.Swaption;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.MakeSchedule;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;

/**
 * Cash-flow representation of an ibor-leg used by basismodel tenor transformations.
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/experimental/basismodels/swaptioncfs.hpp/.cpp}.
 * <p>
 * {@code IborLegCashFlows} maps an existing ibor leg into a sequence of deterministic
 * cash flows (a notional payment at the start of each coupon period, a series of spread
 * coupons, and a final notional redemption) that replicate the leg's present value.
 * {@code SwapCashFlows} adds the fixed leg, and {@code SwaptionCashFlows} further adds
 * the exercise-time array.
 */
public class SwaptionCashFlows {

    // -------------------------------------------------------------------------
    // IborLegCashFlows data (innermost base in C++)
    // -------------------------------------------------------------------------

    /** Reference date (today). */
    protected Date refDate_;

    /** Equivalent deterministic float leg. */
    protected Leg floatLeg_;

    /** Time fractions (Act/365 Fixed from refDate_) for each float cash-flow. */
    protected List<Double> floatTimes_;

    /** Cash-flow amounts for each float cash-flow. */
    protected List<Double> floatWeights_;

    // -------------------------------------------------------------------------
    // SwapCashFlows data
    // -------------------------------------------------------------------------

    protected Leg fixedLeg_;
    protected List<Double> fixedTimes_;
    protected List<Double> fixedWeights_;
    protected List<Double> annuityWeights_;

    // -------------------------------------------------------------------------
    // SwaptionCashFlows data
    // -------------------------------------------------------------------------

    protected Swaption swaption_;
    protected List<Double> exerciseTimes_;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Builds ibor-leg cash flows (IborLegCashFlows in C++).
     *
     * @param iborLeg        existing ibor leg
     * @param discountCurve  discount curve (acts as OIS proxy for spread calc)
     * @param contTenorSpread if true, use continuous tenor-spread convention
     */
    protected void initIborLeg(
            final Leg iborLeg,
            final Handle<YieldTermStructure> discountCurve,
            final boolean contTenorSpread) {

        refDate_ = discountCurve.currentLink().referenceDate();
        floatLeg_ = new Leg();
        floatTimes_ = new ArrayList<>();
        floatWeights_ = new ArrayList<>();

        // find the first live coupon
        int floatIdx = 0;
        while (floatIdx + 1 < iborLeg.size()) {
            Coupon c = (Coupon) iborLeg.get(floatIdx);
            if (refDate_.le(c.accrualStartDate())) break;
            floatIdx++;
        }

        Coupon firstFloatCoupon = (Coupon) iborLeg.get(floatIdx);
        if (refDate_.le(firstFloatCoupon.accrualStartDate())) {
            // initial notional payment at start date
            floatLeg_.add(new SimpleCashFlow(
                    firstFloatCoupon.nominal(),
                    firstFloatCoupon.accrualStartDate()));

            for (int k = floatIdx; k < iborLeg.size(); k++) {
                Coupon coupon = (Coupon) iborLeg.get(k);
                Date startDate = coupon.accrualStartDate();
                Date endDate   = coupon.accrualEndDate();
                double liborForwardRate = coupon.rate();
                double discForwardRate =
                        (discountCurve.currentLink().discount(startDate) /
                         discountCurve.currentLink().discount(endDate) - 1.0) /
                        coupon.accrualPeriod();

                double spread;
                Date payDate;
                if (contTenorSpread) {
                    // Db = (1 + Delta * L_libor) / (1 + Delta * L_ois)
                    // spread (Db - 1) paid at startDate
                    spread = ((1.0 + coupon.accrualPeriod() * liborForwardRate) /
                              (1.0 + coupon.accrualPeriod() * discForwardRate) - 1.0) /
                             coupon.accrualPeriod();
                    payDate = startDate;
                } else {
                    spread = liborForwardRate - discForwardRate;
                    payDate = coupon.date();
                }

                floatLeg_.add(new FixedRateCoupon(
                        coupon.nominal(), payDate, spread,
                        coupon.dayCounter(), startDate, endDate));
            }

            // final notional redemption (negative — received)
            Coupon lastFloatCoupon = (Coupon) iborLeg.get(iborLeg.size() - 1);
            floatLeg_.add(new SimpleCashFlow(
                    -1.0 * lastFloatCoupon.nominal(),
                    lastFloatCoupon.accrualEndDate()));
        }

        // assemble times and weights
        DayCounter dc = new Actual365Fixed();
        for (CashFlow cf : floatLeg_) {
            floatTimes_.add(dc.yearFraction(refDate_, cf.date()));
            floatWeights_.add(cf.amount());
        }
    }

    /**
     * Builds swap cash flows on top of ibor-leg cash flows.
     *
     * @param swap          underlying fixed-vs-floating swap (VanillaSwap)
     * @param discountCurve discount curve
     * @param contTenorSpread continuous tenor spread convention
     */
    protected void initSwap(
            final VanillaSwap swap,
            final Handle<YieldTermStructure> discountCurve,
            final boolean contTenorSpread) {

        initIborLeg(swap.floatingLeg(), discountCurve, contTenorSpread);

        fixedLeg_ = new Leg();
        fixedTimes_ = new ArrayList<>();
        fixedWeights_ = new ArrayList<>();
        annuityWeights_ = new ArrayList<>();

        for (CashFlow cf : swap.fixedLeg()) {
            Coupon coupon = (Coupon) cf;
            if (coupon.accrualStartDate().ge(refDate_)) {
                fixedLeg_.add(cf);
            }
        }

        DayCounter dc = new Actual365Fixed();
        for (CashFlow cf : fixedLeg_) {
            fixedTimes_.add(dc.yearFraction(refDate_, cf.date()));
            fixedWeights_.add(cf.amount());
        }
        for (CashFlow cf : fixedLeg_) {
            Coupon coupon = (Coupon) cf;
            annuityWeights_.add(coupon.nominal() * coupon.accrualPeriod());
        }
    }

    /**
     * Full swaption cash-flow constructor.
     *
     * @param swaption       swaption instrument (European or Bermudan)
     * @param discountCurve  discount curve
     * @param contTenorSpread continuous tenor spread convention
     */
    public SwaptionCashFlows(
            final Swaption swaption,
            final Handle<YieldTermStructure> discountCurve,
            final boolean contTenorSpread) {

        swaption_ = swaption;
        exerciseTimes_ = new ArrayList<>();

        initSwap(swaption.underlying(), discountCurve, contTenorSpread);

        DayCounter dc = new Actual365Fixed();
        List<Date> exerciseDates = swaption.exercise().dates();
        for (Date d : exerciseDates) {
            if (d.gt(refDate_)) {
                exerciseTimes_.add(dc.yearFraction(refDate_, d));
            }
        }
    }

    /** Default no-op constructor (mirrors C++ default). */
    public SwaptionCashFlows() {
        floatLeg_ = new Leg();
        floatTimes_ = new ArrayList<>();
        floatWeights_ = new ArrayList<>();
        fixedLeg_ = new Leg();
        fixedTimes_ = new ArrayList<>();
        fixedWeights_ = new ArrayList<>();
        annuityWeights_ = new ArrayList<>();
        exerciseTimes_ = new ArrayList<>();
    }

    // -------------------------------------------------------------------------
    // Inspectors
    // -------------------------------------------------------------------------

    public Leg floatLeg()             { return floatLeg_; }
    public List<Double> floatTimes()  { return floatTimes_; }
    public List<Double> floatWeights(){ return floatWeights_; }

    public Leg fixedLeg()             { return fixedLeg_; }
    public List<Double> fixedTimes()  { return fixedTimes_; }
    public List<Double> fixedWeights(){ return fixedWeights_; }
    public List<Double> annuityWeights() { return annuityWeights_; }

    public Swaption swaption()        { return swaption_; }
    public List<Double> exerciseTimes(){ return exerciseTimes_; }
}
