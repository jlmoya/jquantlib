/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2009 Roland Lichters
 Copyright (C) 2009 Ferdinando Ametrano
 Copyright (C) 2017 Joseph Jeisman
 Copyright (C) 2017 Fabrice Lecuyer

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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.FixedRateLeg;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.cashflow.OvernightLeg;
import org.jquantlib.cashflow.RateAveraging;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.OvernightIndex;
import org.jquantlib.math.Constants;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Schedule;

/**
 * Overnight-indexed swap: fixed leg vs compounded (or simple-averaged)
 * overnight-rate leg.
 * <p>
 * Port of C++ QuantLib v1.42.1
 * {@code ql/instruments/overnightindexedswap.hpp/cpp}
 * {@code OvernightIndexedSwap}.
 *
 * @category instruments
 *
 * @author JQuantLib migration team
 */
public class OvernightIndexedSwap extends Swap {

    static final /*@Spread*/ double basisPoint = 1.0e-4;

    private final VanillaSwap.Type type_;
    private final double[] fixedNominals_;
    private final double[] overnightNominals_;
    private final Schedule fixedSchedule_;
    private final double fixedRate_;
    private final DayCounter fixedDC_;
    private final Schedule overnightSchedule_;
    private final OvernightIndex overnightIndex_;
    private final double spread_;
    private final int paymentLag_;
    private final BusinessDayConvention paymentAdjustment_;
    private final Calendar paymentCalendar_;
    private final boolean telescopicValueDates_;
    private final RateAveraging.Type averagingMethod_;
    private final int lookbackDays_;
    private final int lockoutDays_;
    private final boolean applyObservationShift_;

    private double fairRate_ = Double.NaN;
    private double fairSpread_ = Double.NaN;

    /** Single-schedule constructor: fixed and floating use the same schedule. */
    public OvernightIndexedSwap(
            final VanillaSwap.Type type,
            final double nominal,
            final Schedule schedule,
            final double fixedRate,
            final DayCounter fixedDC,
            final OvernightIndex overnightIndex) {
        this(type, nominal, schedule, fixedRate, fixedDC, schedule, overnightIndex,
             0.0, 0, BusinessDayConvention.Following, null,
             false, RateAveraging.Type.Compound);
    }

    /** Single-schedule constructor with spread. */
    public OvernightIndexedSwap(
            final VanillaSwap.Type type,
            final double nominal,
            final Schedule schedule,
            final double fixedRate,
            final DayCounter fixedDC,
            final OvernightIndex overnightIndex,
            final double spread) {
        this(type, nominal, schedule, fixedRate, fixedDC, schedule, overnightIndex,
             spread, 0, BusinessDayConvention.Following, null,
             false, RateAveraging.Type.Compound);
    }

    /**
     * Single-schedule, multi-nominal (amortizing) constructor.
     * <p>Mirror of C++ overload taking {@code std::vector<Real> nominals}
     * (overnightindexedswap.hpp:60-74).
     */
    public OvernightIndexedSwap(
            final VanillaSwap.Type type,
            final double[] nominals,
            final Schedule schedule,
            final double fixedRate,
            final DayCounter fixedDC,
            final OvernightIndex overnightIndex) {
        this(type, nominals, schedule, fixedRate, fixedDC, nominals, schedule,
             overnightIndex, 0.0, 0, BusinessDayConvention.Following, null,
             false, RateAveraging.Type.Compound,
             Constants.NULL_NATURAL, 0, false);
    }

    /** Full single-nominal constructor (separate fixed and overnight schedules). */
    public OvernightIndexedSwap(
            final VanillaSwap.Type type,
            final double nominal,
            final Schedule fixedSchedule,
            final double fixedRate,
            final DayCounter fixedDC,
            final Schedule overnightSchedule,
            final OvernightIndex overnightIndex,
            final double spread,
            final int paymentLag,
            final BusinessDayConvention paymentAdjustment,
            final Calendar paymentCalendar,
            final boolean telescopicValueDates,
            final RateAveraging.Type averagingMethod) {
        this(type, new double[]{nominal}, fixedSchedule, fixedRate, fixedDC,
             new double[]{nominal}, overnightSchedule, overnightIndex,
             spread, paymentLag, paymentAdjustment, paymentCalendar,
             telescopicValueDates, averagingMethod,
             Constants.NULL_NATURAL, 0, false);
    }

    /**
     * Full single-nominal constructor with lookback / lockout / observation-shift.
     * <p>Mirror of C++ overload taking {@code lookbackDays / lockoutDays /
     * applyObservationShift} (overnightindexedswap.hpp:42-58 and 76-91).
     */
    public OvernightIndexedSwap(
            final VanillaSwap.Type type,
            final double nominal,
            final Schedule fixedSchedule,
            final double fixedRate,
            final DayCounter fixedDC,
            final Schedule overnightSchedule,
            final OvernightIndex overnightIndex,
            final double spread,
            final int paymentLag,
            final BusinessDayConvention paymentAdjustment,
            final Calendar paymentCalendar,
            final boolean telescopicValueDates,
            final RateAveraging.Type averagingMethod,
            final int lookbackDays,
            final int lockoutDays,
            final boolean applyObservationShift) {
        this(type, new double[]{nominal}, fixedSchedule, fixedRate, fixedDC,
             new double[]{nominal}, overnightSchedule, overnightIndex,
             spread, paymentLag, paymentAdjustment, paymentCalendar,
             telescopicValueDates, averagingMethod,
             lookbackDays, lockoutDays, applyObservationShift);
    }

    /**
     * Multi-nominal, single-schedule constructor with separate fixed and overnight
     * schedules. Mirrors C++ overload taking {@code std::vector<Real>
     * fixedNominals + std::vector<Real> overnightNominals}
     * (overnightindexedswap.hpp:93-109).
     */
    public OvernightIndexedSwap(
            final VanillaSwap.Type type,
            final double[] fixedNominals,
            final Schedule fixedSchedule,
            final double fixedRate,
            final DayCounter fixedDC,
            final double[] overnightNominals,
            final Schedule overnightSchedule,
            final OvernightIndex overnightIndex) {
        this(type, fixedNominals, fixedSchedule, fixedRate, fixedDC,
             overnightNominals, overnightSchedule, overnightIndex,
             0.0, 0, BusinessDayConvention.Following, null,
             false, RateAveraging.Type.Compound,
             Constants.NULL_NATURAL, 0, false);
    }

    /**
     * Full constructor (separate fixed and overnight schedules + multi-nominal).
     * Mirror of C++ private-delegated ctor (overnightindexedswap.cpp:128-170).
     */
    public OvernightIndexedSwap(
            final VanillaSwap.Type type,
            final double[] fixedNominals,
            final Schedule fixedSchedule,
            final double fixedRate,
            final DayCounter fixedDC,
            final double[] overnightNominals,
            final Schedule overnightSchedule,
            final OvernightIndex overnightIndex,
            final double spread,
            final int paymentLag,
            final BusinessDayConvention paymentAdjustment,
            final Calendar paymentCalendar,
            final boolean telescopicValueDates,
            final RateAveraging.Type averagingMethod,
            final int lookbackDays,
            final int lockoutDays,
            final boolean applyObservationShift) {
        super(2);
        QL.require(fixedNominals != null && fixedNominals.length > 0,
                "no fixed nominal given");
        QL.require(overnightNominals != null && overnightNominals.length > 0,
                "no overnight nominal given");
        this.type_ = type;
        this.fixedNominals_ = fixedNominals.clone();
        this.overnightNominals_ = overnightNominals.clone();
        this.fixedSchedule_ = fixedSchedule;
        this.fixedRate_ = fixedRate;
        this.fixedDC_ = fixedDC;
        this.overnightSchedule_ = overnightSchedule;
        this.overnightIndex_ = overnightIndex;
        this.spread_ = spread;
        this.paymentLag_ = paymentLag;
        this.paymentAdjustment_ = paymentAdjustment;
        this.paymentCalendar_ = (paymentCalendar == null)
                ? overnightSchedule.calendar() : paymentCalendar;
        this.telescopicValueDates_ = telescopicValueDates;
        this.averagingMethod_ = averagingMethod;
        this.lookbackDays_ = lookbackDays;
        this.lockoutDays_ = lockoutDays;
        this.applyObservationShift_ = applyObservationShift;

        // Fixed leg
        final Leg fixedLeg = new FixedRateLeg(fixedSchedule_, fixedDC_)
                .withNotionals(fixedNominals_)
                .withCouponRates(fixedRate)
                .withPaymentAdjustment(paymentAdjustment_)
                .Leg();

        // Overnight leg
        final List<Double> overnightNominalList = new ArrayList<Double>(overnightNominals_.length);
        for (final double n : overnightNominals_) {
            overnightNominalList.add(n);
        }
        final Leg floatingLeg = new OvernightLeg(overnightSchedule_, overnightIndex_)
                .withNotionals(overnightNominalList)
                .withPaymentDayCounter(overnightIndex_.dayCounter())
                .withPaymentAdjustment(paymentAdjustment_)
                .withPaymentCalendar(paymentCalendar_)
                .withPaymentLag(paymentLag_)
                .withSpreads(spread)
                .withTelescopicValueDates(telescopicValueDates_)
                .withAveragingMethod(averagingMethod_)
                .withLookbackDays(lookbackDays_)
                .withLockoutDays(lockoutDays_)
                .withObservationShift(applyObservationShift_)
                .leg();

        for (final CashFlow cf : floatingLeg) {
            cf.addObserver(this);
        }

        super.legs.add(fixedLeg);
        super.legs.add(floatingLeg);
        if (type_ == VanillaSwap.Type.Payer) {
            super.payer[0] = -1.0;
            super.payer[1] = +1.0;
        } else {
            super.payer[0] = +1.0;
            super.payer[1] = -1.0;
        }
    }

    //
    // public inspectors
    //

    public VanillaSwap.Type type() { return type_; }

    /**
     * Returns the (single) nominal, throwing if the swap is amortizing.
     * Mirror of C++ {@code FixedVsFloatingSwap::nominal()} guard
     * (fixedvsfloatingswap.cpp).
     */
    public double nominal() {
        QL.require(fixedNominals_.length == 1
                && (overnightNominals_.length == 1)
                && fixedNominals_[0] == overnightNominals_[0],
                "nominal is not constant");
        return fixedNominals_[0];
    }

    /**
     * Returns the unified nominals vector (requires fixedNominals
     * == overnightNominals exactly).
     * Mirror of C++ {@code FixedVsFloatingSwap::nominals()} guard.
     */
    public double[] nominals() {
        QL.require(Arrays.equals(fixedNominals_, overnightNominals_),
                "different nominals on fixed and floating leg");
        return fixedNominals_.clone();
    }

    public double[] fixedNominals() { return fixedNominals_.clone(); }
    public double[] overnightNominals() { return overnightNominals_.clone(); }

    /**
     * Joint payment frequency: max of the fixed-schedule frequency and
     * the overnight-schedule frequency. Mirror of C++
     * {@code OvernightIndexedSwap::paymentFrequency()}
     * (overnightindexedswap.hpp:113-116).
     */
    public Frequency paymentFrequency() {
        final Frequency f1 = fixedSchedule_.tenor().frequency();
        final Frequency f2 = overnightSchedule_.tenor().frequency();
        // C++ std::max compares the underlying int values.
        return f1.toInteger() >= f2.toInteger() ? f1 : f2;
    }

    public Schedule fixedSchedule() { return fixedSchedule_; }
    public double fixedRate() { return fixedRate_; }
    public DayCounter fixedDayCount() { return fixedDC_; }
    public Schedule overnightSchedule() { return overnightSchedule_; }
    public OvernightIndex overnightIndex() { return overnightIndex_; }
    public double spread() { return spread_; }
    public int paymentLag() { return paymentLag_; }
    public BusinessDayConvention paymentAdjustment() { return paymentAdjustment_; }
    public Calendar paymentCalendar() { return paymentCalendar_; }
    public boolean telescopicValueDates() { return telescopicValueDates_; }
    public RateAveraging.Type averagingMethod() { return averagingMethod_; }
    public int lookbackDays() { return lookbackDays_; }
    public int lockoutDays() { return lockoutDays_; }
    public boolean applyObservationShift() { return applyObservationShift_; }

    public Leg fixedLeg() { return legs.get(0); }
    public Leg overnightLeg() { return legs.get(1); }

    /**
     * Fair fixed rate (the rate that zeroes the swap NPV).
     */
    public double fairRate() {
        calculate();
        QL.require(!Double.isNaN(fairRate_), "result not available");
        return fairRate_;
    }

    public double fairSpread() {
        calculate();
        QL.require(!Double.isNaN(fairSpread_), "result not available");
        return fairSpread_;
    }

    public double fixedLegBPS() {
        calculate();
        QL.require(!Double.isNaN(legBPS[0]), "result not available");
        return legBPS[0];
    }

    public double overnightLegBPS() {
        calculate();
        QL.require(!Double.isNaN(legBPS[1]), "result not available");
        return legBPS[1];
    }

    public double fixedLegNPV() {
        calculate();
        QL.require(!Double.isNaN(legNPV[0]), "result not available");
        return legNPV[0];
    }

    public double overnightLegNPV() {
        calculate();
        QL.require(!Double.isNaN(legNPV[1]), "result not available");
        return legNPV[1];
    }

    @Override
    protected void setupExpired() {
        super.setupExpired();
        legBPS[0] = legBPS[1] = 0.0;
        fairRate_ = Constants.NULL_REAL;
        fairSpread_ = Constants.NULL_REAL;
    }

    @Override
    public void fetchResults(final org.jquantlib.pricingengines.PricingEngine.Results r) {
        super.fetchResults(r);
        // Compute fair rate and spread analytically given current legBPS/legNPV.
        // C++ FixedVsFloatingSwap::fetchResults uses additionalResults; for the
        // MVP we recompute from the leg PVs.
        if (legBPS != null && legBPS.length >= 2) {
            // fairRate: rate that makes overnightLegNPV + fixedLegNPV(adjusted) == 0
            // overnightLegNPV is fixed independent of fixed rate; adjusting fixed
            // rate scales legBPS[0] proportionally.
            // legNPV[0] corresponds to fixedRate -> fairRate scales as
            // fairRate = fixedRate - legNPV[0] / legBPS[0] * 1e-4 / payer[0]
            if (legBPS[0] != 0.0 && !Double.isNaN(legBPS[0])) {
                fairRate_ = fixedRate_ - super.NPV / (legBPS[0] / basisPoint);
            }
            if (legBPS[1] != 0.0 && !Double.isNaN(legBPS[1])) {
                fairSpread_ = spread_ - super.NPV / (legBPS[1] / basisPoint);
            }
        }
    }
}
