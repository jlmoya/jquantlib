/*
 Copyright (C) 2013 Peter Caspers

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
 Copyright (C) 2013 Peter Caspers

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

/*!
 * \file floatfloatswap.hpp (Java port)
 * \brief Swap exchanging two floating legs (IborIndex or SwapIndex based).
 *        Generalisation of VanillaSwap with per-coupon gearings, spreads,
 *        caps, and floors on both legs.  The engine-facing types are included
 *        as inner interfaces / classes mirroring the C++ nested-class pattern.
 *
 * \note SwapSpreadIndex (CmsSpreadLeg) is not present in this Java codebase;
 *       only IborIndex and SwapIndex legs are supported.
 *
 * Phase 2j.5 Track B.1.
 */

package org.jquantlib.instruments;

import org.jquantlib.QL;
import org.jquantlib.cashflow.*;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.indexes.InterestRateIndex;
import org.jquantlib.indexes.SwapIndex;
import org.jquantlib.math.Constants;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.pricingengines.GenericEngine;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.Schedule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Swap exchanging two floating legs (each either Ibor or CMS-based) with per-coupon gearings, spreads, caps, and
 * floors.
 *
 * <p>Mirrors C++ v1.42.1 {@code ql/instruments/floatfloatswap.hpp} /
 * {@code .cpp} (author Peter Caspers, 2013).
 *
 * <p>The C++ class supports three index types: IborIndex, SwapIndex (CMS),
 * and SwapSpreadIndex (CMS spread). This Java port supports IborIndex and SwapIndex only — SwapSpreadIndex
 * infrastructure does not exist in this codebase.
 *
 * <p>Phase 2j.5 Track B.1.
 */
public class FloatFloatSwap extends Swap {

    /** Sentinel for "not set" rate (cap/floor absent). */
    public static final double NULL_REAL = Constants.NULL_REAL;

    // ── NULL_REAL sentinel (mirrors C++ Null<Real>()) ────────────────────────
    // ── machine epsilon (same value as C++ QL_EPSILON) ───────────────────────
    private static final double QL_EPSILON = 2.220446049250313e-16;

    // ── private fields ────────────────────────────────────────────────────────
    private final VanillaSwap.Type type_;       // Payer or Receiver
    private final Schedule schedule1_;
    private final Schedule schedule2_;
    private final InterestRateIndex index1_;
    private final InterestRateIndex index2_;
    private final DayCounter dayCount1_;
    private final DayCounter dayCount2_;
    private final boolean intermediateCapitalExchange_;
    private final boolean finalCapitalExchange_;
    private final BusinessDayConvention paymentConvention1_;
    private final BusinessDayConvention paymentConvention2_;
    // These may grow in size when intermediateCapitalExchange_ inserts flows:
    private double[] nominal1_;
    private double[] nominal2_;
    private final double[] gearing1_;
    private final double[] spread1_;
    private final double[] cappedRate1_;
    private final double[] flooredRate1_;
    private final double[] gearing2_;
    private final double[] spread2_;
    private final double[] cappedRate2_;
    private final double[] flooredRate2_;
    // ── result memoisation ────────────────────────────────────────────────────
    private double fairSpread1_ = NULL_REAL;
    private double fairSpread2_ = NULL_REAL;

    // ── Constructor: scalar nominal (convenience overload) ────────────────────

    /**
     * Mirrors C++ {@code FloatFloatSwap(Swap::Type, Real nominal1, Real nominal2, ...)} with scalar nominals expanded
     * to per-coupon vectors.
     *
     * @param type                        Payer or Receiver
     * @param nominal1                    scalar notional for leg 1
     * @param nominal2                    scalar notional for leg 2
     * @param schedule1                   schedule for leg 1
     * @param index1                      floating index for leg 1 (IborIndex or SwapIndex)
     * @param dayCount1                   day counter for leg 1
     * @param schedule2                   schedule for leg 2
     * @param index2                      floating index for leg 2 (IborIndex or SwapIndex)
     * @param dayCount2                   day counter for leg 2
     * @param intermediateCapitalExchange insert intermediate redemption flows
     * @param finalCapitalExchange        append final redemption flows
     * @param gearing1                    uniform gearing for all leg-1 coupons (default 1.0)
     * @param spread1                     uniform spread for all leg-1 coupons (default 0.0)
     * @param cappedRate1                 uniform cap for leg 1 ({@link #NULL_REAL} = none)
     * @param flooredRate1                uniform floor for leg 1 ({@link #NULL_REAL} = none)
     * @param gearing2                    uniform gearing for all leg-2 coupons (default 1.0)
     * @param spread2                     uniform spread for all leg-2 coupons (default 0.0)
     * @param cappedRate2                 uniform cap for leg 2 ({@link #NULL_REAL} = none)
     * @param flooredRate2                uniform floor for leg 2 ({@link #NULL_REAL} = none)
     * @param paymentConvention1          payment convention for leg 1 (null = schedule convention)
     * @param paymentConvention2          payment convention for leg 2 (null = schedule convention)
     */
    public FloatFloatSwap(final VanillaSwap.Type type, final double nominal1, final double nominal2,
            final Schedule schedule1, final InterestRateIndex index1, final DayCounter dayCount1,
            final Schedule schedule2, final InterestRateIndex index2, final DayCounter dayCount2,
            final boolean intermediateCapitalExchange, final boolean finalCapitalExchange, final double gearing1,
            final double spread1, final double cappedRate1, final double flooredRate1, final double gearing2,
            final double spread2, final double cappedRate2, final double flooredRate2,
            final BusinessDayConvention paymentConvention1, final BusinessDayConvention paymentConvention2) {

        super(2);
        this.type_ = type;
        this.schedule1_ = schedule1;
        this.schedule2_ = schedule2;
        this.index1_ = index1;
        this.index2_ = index2;
        this.dayCount1_ = dayCount1;
        this.dayCount2_ = dayCount2;
        this.intermediateCapitalExchange_ = intermediateCapitalExchange;
        this.finalCapitalExchange_ = finalCapitalExchange;

        // Expand scalars to per-coupon arrays (size = schedule.size()-1)
        final int n1 = schedule1.size() - 1;
        final int n2 = schedule2.size() - 1;
        this.nominal1_ = fill(n1, nominal1);
        this.nominal2_ = fill(n2, nominal2);
        this.gearing1_ = fill(n1, gearing1);
        this.spread1_ = fill(n1, spread1);
        this.cappedRate1_ = fill(n1, cappedRate1);
        this.flooredRate1_ = fill(n1, flooredRate1);
        this.gearing2_ = fill(n2, gearing2);
        this.spread2_ = fill(n2, spread2);
        this.cappedRate2_ = fill(n2, cappedRate2);
        this.flooredRate2_ = fill(n2, flooredRate2);

        this.paymentConvention1_ = (paymentConvention1 != null)
                ? paymentConvention1
                : schedule1.businessDayConvention();
        this.paymentConvention2_ = (paymentConvention2 != null)
                ? paymentConvention2
                : schedule2.businessDayConvention();
        init();
    }

    /**
     * Convenience overload — no payment conventions (use schedule defaults) and no caps/floors.
     */
    public FloatFloatSwap(final VanillaSwap.Type type, final double nominal1, final double nominal2,
            final Schedule schedule1, final InterestRateIndex index1, final DayCounter dayCount1,
            final Schedule schedule2, final InterestRateIndex index2, final DayCounter dayCount2,
            final boolean intermediateCapitalExchange, final boolean finalCapitalExchange, final double gearing1,
            final double spread1, final double cappedRate1, final double flooredRate1, final double gearing2,
            final double spread2, final double cappedRate2, final double flooredRate2) {
        this(type, nominal1, nominal2, schedule1, index1, dayCount1, schedule2, index2, dayCount2,
                intermediateCapitalExchange, finalCapitalExchange, gearing1, spread1, cappedRate1, flooredRate1,
                gearing2, spread2, cappedRate2, flooredRate2, null, null);
    }

    // ── Constructor: vector nominals (general overload) ───────────────────────

    /**
     * Mirrors C++ {@code FloatFloatSwap(Swap::Type, vector<Real> nominal1, ...)}.
     *
     * <p>All vector parameters may be empty ({@code null} or length-0) to
     * indicate defaults (gearings=1, spreads=0, caps/floors=absent).
     */
    public FloatFloatSwap(final VanillaSwap.Type type, final double[] nominal1, final double[] nominal2,
            final Schedule schedule1, final InterestRateIndex index1, final DayCounter dayCount1,
            final Schedule schedule2, final InterestRateIndex index2, final DayCounter dayCount2,
            final boolean intermediateCapitalExchange, final boolean finalCapitalExchange, final double[] gearing1,
            final double[] spread1, final double[] cappedRate1, final double[] flooredRate1, final double[] gearing2,
            final double[] spread2, final double[] cappedRate2, final double[] flooredRate2,
            final BusinessDayConvention paymentConvention1, final BusinessDayConvention paymentConvention2) {

        super(2);
        this.type_ = type;
        this.schedule1_ = schedule1;
        this.schedule2_ = schedule2;
        this.index1_ = index1;
        this.index2_ = index2;
        this.dayCount1_ = dayCount1;
        this.dayCount2_ = dayCount2;
        this.intermediateCapitalExchange_ = intermediateCapitalExchange;
        this.finalCapitalExchange_ = finalCapitalExchange;

        final int n1 = nominal1.length;
        final int n2 = nominal2.length;
        this.nominal1_ = nominal1.clone();
        this.nominal2_ = nominal2.clone();
        // Apply defaults for empty arrays
        this.gearing1_ = defaultOrClone(gearing1, n1, 1.0);
        this.spread1_ = defaultOrClone(spread1, n1, 0.0);
        this.cappedRate1_ = defaultOrClone(cappedRate1, n1, NULL_REAL);
        this.flooredRate1_ = defaultOrClone(flooredRate1, n1, NULL_REAL);
        this.gearing2_ = defaultOrClone(gearing2, n2, 1.0);
        this.spread2_ = defaultOrClone(spread2, n2, 0.0);
        this.cappedRate2_ = defaultOrClone(cappedRate2, n2, NULL_REAL);
        this.flooredRate2_ = defaultOrClone(flooredRate2, n2, NULL_REAL);

        this.paymentConvention1_ = (paymentConvention1 != null)
                ? paymentConvention1
                : schedule1.businessDayConvention();
        this.paymentConvention2_ = (paymentConvention2 != null)
                ? paymentConvention2
                : schedule2.businessDayConvention();
        init();
    }

    /**
     * Vector overload without explicit payment conventions.
     */
    public FloatFloatSwap(final VanillaSwap.Type type, final double[] nominal1, final double[] nominal2,
            final Schedule schedule1, final InterestRateIndex index1, final DayCounter dayCount1,
            final Schedule schedule2, final InterestRateIndex index2, final DayCounter dayCount2,
            final boolean intermediateCapitalExchange, final boolean finalCapitalExchange, final double[] gearing1,
            final double[] spread1, final double[] cappedRate1, final double[] flooredRate1, final double[] gearing2,
            final double[] spread2, final double[] cappedRate2, final double[] flooredRate2) {
        this(type, nominal1, nominal2, schedule1, index1, dayCount1, schedule2, index2, dayCount2,
                intermediateCapitalExchange, finalCapitalExchange, gearing1, spread1, cappedRate1, flooredRate1,
                gearing2, spread2, cappedRate2, flooredRate2, null, null);
    }

    // ── Inspectors ────────────────────────────────────────────────────────────

    private static Leg buildIborLeg(final Schedule schedule, final IborIndex index, final double[] nominals,
            final DayCounter dc, final BusinessDayConvention conv, final double[] gearings, final double[] spreads,
            final double[] caps, final double[] floors) {

        IborLeg leg = new IborLeg(schedule, index).withNotionals(new Array(nominals)).withPaymentDayCounter(dc)
                .withPaymentAdjustment(conv).withGearings(new Array(gearings)).withSpreads(new Array(spreads));

        if ( !isNullVector(caps) ) {
            leg = leg.withCaps(new Array(caps));
        }
        if ( !isNullVector(floors) ) {
            leg = leg.withFloors(new Array(floors));
        }
        return leg.Leg();
    }

    private static Leg buildCmsLeg(final Schedule schedule, final SwapIndex index, final double[] nominals,
            final DayCounter dc, final BusinessDayConvention conv, final double[] gearings, final double[] spreads,
            final double[] caps, final double[] floors) {

        CmsLeg leg = new CmsLeg(schedule, index).withNotionals(new Array(nominals)).withPaymentDayCounter(dc)
                .withPaymentAdjustment(conv).withGearings(new Array(gearings)).withSpreads(new Array(spreads));

        if ( !isNullVector(caps) ) {
            leg = leg.withCaps(new Array(caps));
        }
        if ( !isNullVector(floors) ) {
            leg = leg.withFloors(new Array(floors));
        }
        return leg.Leg();
    }

    /** Fill a new double[] of length {@code n} with value {@code v}. */
    private static double[] fill(final int n, final double v) {
        final double[] a = new double[n];
        Arrays.fill(a, v);
        return a;
    }

    /**
     * Return a per-coupon array: if {@code src} is null or empty, fill with {@code defaultVal}; otherwise clone
     * {@code src}.
     */
    private static double[] defaultOrClone(final double[] src, final int n, final double defaultVal) {
        if ( src == null || src.length == 0 ) {
            return fill(n, defaultVal);
        }
        return src.clone();
    }

    /** Insert {@code value} at {@code index} in {@code arr}. */
    private static double[] insertDouble(final double[] arr, final int index, final double value) {
        final double[] result = new double[arr.length + 1];
        System.arraycopy(arr, 0, result, 0, index);
        result[index] = value;
        System.arraycopy(arr, index, result, index + 1, arr.length - index);
        return result;
    }

    /** Append {@code value} to {@code arr}. */
    private static double[] appendDouble(final double[] arr, final double value) {
        final double[] result = new double[arr.length + 1];
        System.arraycopy(arr, 0, result, 0, arr.length);
        result[arr.length] = value;
        return result;
    }

    /**
     * Test whether all entries of {@code vec} are {@link #NULL_REAL}. Used to decide whether to apply caps/floors.
     */
    private static boolean isNullVector(final double[] vec) {
        for ( final double v : vec ) {
            if ( v != NULL_REAL )
                return false;
        }
        return true;
    }

    /**
     * Validate that a cap/floor rate vector is either all-null or all-non-null. Mirrors C++ {@code QL_REQUIRE} checks
     * in {@code FloatFloatSwap::init()}.
     */
    private static void validateAllOrNone(final double[] rates, final String name) {
        if ( rates.length == 0 )
            return;
        final boolean firstIsNull = (rates[0] == NULL_REAL);
        for ( int i = 1; i < rates.length; i++ ) {
            final boolean isNull = (rates[i] == NULL_REAL);
            QL.require(isNull == firstIsNull,
                    name + " must be null for all or none entry (" + (i + 1) + "th is " + rates[i] + ")");
        }
    }

    /**
     * Mirrors C++ {@code QuantLib::close(Real, Real)} (42-epsilon multiples).
     */
    private static boolean close(final double x, final double y) {
        final double diff = Math.abs(x - y);
        if ( diff == 0.0 )
            return true;
        return diff <= 42.0 * QL_EPSILON * Math.abs(x) || diff <= 42.0 * QL_EPSILON * Math.abs(y);
    }

    /**
     * Search {@code dates} for {@code target} in indices 0..{@code before}-1. Returns the index or -1.
     */
    private static int findPayDateIndex(final List< Date > dates, final Date target, final int before) {
        for ( int j = 0; j < before; j++ ) {
            final Date d = dates.get(j);
            if ( d != null && d.equals(target) )
                return j;
        }
        return -1;
    }

    /** Swap type (Payer or Receiver). Mirrors C++ {@code FloatFloatSwap::type()}. */
    public VanillaSwap.Type type() {
        return type_;
    }

    /** Per-coupon notionals for leg 1. Mirrors C++ {@code FloatFloatSwap::nominal1()}. */
    public double[] nominal1() {
        return nominal1_;
    }

    /** Per-coupon notionals for leg 2. Mirrors C++ {@code FloatFloatSwap::nominal2()}. */
    public double[] nominal2() {
        return nominal2_;
    }

    /** Schedule for leg 1. Mirrors C++ {@code FloatFloatSwap::schedule1()}. */
    public Schedule schedule1() {
        return schedule1_;
    }

    /** Schedule for leg 2. Mirrors C++ {@code FloatFloatSwap::schedule2()}. */
    public Schedule schedule2() {
        return schedule2_;
    }

    /** Index for leg 1. Mirrors C++ {@code FloatFloatSwap::index1()}. */
    public InterestRateIndex index1() {
        return index1_;
    }

    /** Index for leg 2. Mirrors C++ {@code FloatFloatSwap::index2()}. */
    public InterestRateIndex index2() {
        return index2_;
    }

    /** Per-coupon spreads for leg 1. Mirrors C++ {@code FloatFloatSwap::spread1()}. */
    public double[] spread1() {
        return spread1_;
    }

    /** Per-coupon spreads for leg 2. Mirrors C++ {@code FloatFloatSwap::spread2()}. */
    public double[] spread2() {
        return spread2_;
    }

    /** Per-coupon gearings for leg 1. Mirrors C++ {@code FloatFloatSwap::gearing1()}. */
    public double[] gearing1() {
        return gearing1_;
    }

    /** Per-coupon gearings for leg 2. Mirrors C++ {@code FloatFloatSwap::gearing2()}. */
    public double[] gearing2() {
        return gearing2_;
    }

    // ── Results ───────────────────────────────────────────────────────────────

    /** Per-coupon cap rates for leg 1. Mirrors C++ {@code FloatFloatSwap::cappedRate1()}. */
    public double[] cappedRate1() {
        return cappedRate1_;
    }

    /** Per-coupon cap rates for leg 2. Mirrors C++ {@code FloatFloatSwap::cappedRate2()}. */
    public double[] cappedRate2() {
        return cappedRate2_;
    }

    // ── Instrument overrides ──────────────────────────────────────────────────

    /** Per-coupon floor rates for leg 1. Mirrors C++ {@code FloatFloatSwap::flooredRate1()}. */
    public double[] flooredRate1() {
        return flooredRate1_;
    }

    /** Per-coupon floor rates for leg 2. Mirrors C++ {@code FloatFloatSwap::flooredRate2()}. */
    public double[] flooredRate2() {
        return flooredRate2_;
    }

    /** Day counter for leg 1. Mirrors C++ {@code FloatFloatSwap::dayCount1()}. */
    public DayCounter dayCount1() {
        return dayCount1_;
    }

    // ── Private init ──────────────────────────────────────────────────────────

    /** Day counter for leg 2. Mirrors C++ {@code FloatFloatSwap::dayCount2()}. */
    public DayCounter dayCount2() {
        return dayCount2_;
    }

    // ── Leg builders ──────────────────────────────────────────────────────────

    /** Payment convention for leg 1. Mirrors C++ {@code FloatFloatSwap::paymentConvention1()}. */
    public BusinessDayConvention paymentConvention1() {
        return paymentConvention1_;
    }

    /** Payment convention for leg 2. Mirrors C++ {@code FloatFloatSwap::paymentConvention2()}. */
    public BusinessDayConvention paymentConvention2() {
        return paymentConvention2_;
    }

    // ── Capital-exchange helpers ───────────────────────────────────────────────

    /** Leg 1 cashflows. Mirrors C++ {@code FloatFloatSwap::leg1()}. */
    public Leg leg1() {
        return legs.get(0);
    }

    // ── Array utilities ───────────────────────────────────────────────────────

    /** Leg 2 cashflows. Mirrors C++ {@code FloatFloatSwap::leg2()}. */
    public Leg leg2() {
        return legs.get(1);
    }

    /**
     * Fair spread on leg 1 that makes the NPV zero. Mirrors C++ {@code FloatFloatSwap::fairSpread1()}.
     */
    public double fairSpread1() {
        calculate();
        QL.require(fairSpread1_ != NULL_REAL, "fair spread 1 not available");
        return fairSpread1_;
    }

    /**
     * Fair spread on leg 2 that makes the NPV zero. Mirrors C++ {@code FloatFloatSwap::fairSpread2()}.
     */
    public double fairSpread2() {
        calculate();
        QL.require(fairSpread2_ != NULL_REAL, "fair spread 2 not available");
        return fairSpread2_;
    }

    @Override
    protected void setupExpired() {
        super.setupExpired();
        fairSpread1_ = NULL_REAL;
        fairSpread2_ = NULL_REAL;
    }

    /**
     * Populate {@link FloatFloatSwap.ArgumentsImpl} from instrument state. Mirrors C++
     * {@code FloatFloatSwap::setupArguments()}.
     */
    @Override
    public void setupArguments(final PricingEngine.Arguments args) {
        super.setupArguments(args);

        if ( !(args instanceof FloatFloatSwap.Arguments) ) {
            return; // plain Swap engine — legs/payer arrays sufficient
        }
        final ArgumentsImpl a = (ArgumentsImpl) args;

        a.type = type_;
        a.nominal1 = nominal1_.clone();
        a.nominal2 = nominal2_.clone();
        a.index1 = index1_;
        a.index2 = index2_;

        // ── Leg 1 ──────────────────────────────────────────────────────────────
        final Leg leg1 = leg1();
        final int n1 = leg1.size();
        a.leg1ResetDates = new ArrayList<>(Collections.nCopies(n1, null));
        a.leg1PayDates = new ArrayList<>(Collections.nCopies(n1, null));
        a.leg1FixingDates = new ArrayList<>(Collections.nCopies(n1, null));
        a.leg1AccrualTimes = new ArrayList<>(Collections.nCopies(n1, 0.0));
        a.leg1Spreads = new ArrayList<>(Collections.nCopies(n1, 0.0));
        a.leg1Gearings = new ArrayList<>(Collections.nCopies(n1, 1.0));
        a.leg1Coupons = new ArrayList<>(Collections.nCopies(n1, NULL_REAL));
        a.leg1CappedRates = new ArrayList<>(Collections.nCopies(n1, NULL_REAL));
        a.leg1FlooredRates = new ArrayList<>(Collections.nCopies(n1, NULL_REAL));
        a.leg1IsRedemptionFlow = new boolean[n1];

        for ( int i = 0; i < n1; i++ ) {
            final CashFlow cf = leg1.get(i);
            if (cf instanceof FloatingRateCoupon c) {
                a.leg1AccrualTimes.set(i, c.accrualPeriod());
                a.leg1PayDates.set(i, c.date());
                a.leg1ResetDates.set(i, c.accrualStartDate());
                a.leg1FixingDates.set(i, c.fixingDate());
                a.leg1Spreads.set(i, c.spread());
                a.leg1Gearings.set(i, c.gearing());
                try {
                    a.leg1Coupons.set(i, c.amount());
                } catch ( final Exception e ) {
                    a.leg1Coupons.set(i, NULL_REAL);
                }
                if (cf instanceof CappedFlooredCoupon cfc) {
                    a.leg1CappedRates.set(i, cfc.cap());
                    a.leg1FlooredRates.set(i, cfc.floor());
                }
            } else {
                // Redemption flow — find the matching coupon pay-date
                final Date cfDate = cf.date();
                final int jIdx = findPayDateIndex(a.leg1PayDates, cfDate, i);
                QL.require(jIdx >= 0, "nominal redemption on " + cfDate + " has no corresponding coupon");
                a.leg1IsRedemptionFlow[i] = true;
                a.leg1Coupons.set(i, cf.amount());
                a.leg1ResetDates.set(i, a.leg1ResetDates.get(jIdx));
                a.leg1FixingDates.set(i, a.leg1FixingDates.get(jIdx));
                a.leg1AccrualTimes.set(i, 0.0);
                a.leg1Spreads.set(i, 0.0);
                a.leg1Gearings.set(i, 1.0);
                a.leg1PayDates.set(i, cfDate);
            }
        }

        // ── Leg 2 ──────────────────────────────────────────────────────────────
        final Leg leg2 = leg2();
        final int n2 = leg2.size();
        a.leg2ResetDates = new ArrayList<>(Collections.nCopies(n2, null));
        a.leg2PayDates = new ArrayList<>(Collections.nCopies(n2, null));
        a.leg2FixingDates = new ArrayList<>(Collections.nCopies(n2, null));
        a.leg2AccrualTimes = new ArrayList<>(Collections.nCopies(n2, 0.0));
        a.leg2Spreads = new ArrayList<>(Collections.nCopies(n2, 0.0));
        a.leg2Gearings = new ArrayList<>(Collections.nCopies(n2, 1.0));
        a.leg2Coupons = new ArrayList<>(Collections.nCopies(n2, NULL_REAL));
        a.leg2CappedRates = new ArrayList<>(Collections.nCopies(n2, NULL_REAL));
        a.leg2FlooredRates = new ArrayList<>(Collections.nCopies(n2, NULL_REAL));
        a.leg2IsRedemptionFlow = new boolean[n2];

        for ( int i = 0; i < n2; i++ ) {
            final CashFlow cf = leg2.get(i);
            if (cf instanceof FloatingRateCoupon c) {
                a.leg2AccrualTimes.set(i, c.accrualPeriod());
                a.leg2PayDates.set(i, c.date());
                a.leg2ResetDates.set(i, c.accrualStartDate());
                a.leg2FixingDates.set(i, c.fixingDate());
                a.leg2Spreads.set(i, c.spread());
                a.leg2Gearings.set(i, c.gearing());
                try {
                    a.leg2Coupons.set(i, c.amount());
                } catch ( final Exception e ) {
                    a.leg2Coupons.set(i, NULL_REAL);
                }
                if (cf instanceof CappedFlooredCoupon cfc) {
                    a.leg2CappedRates.set(i, cfc.cap());
                    a.leg2FlooredRates.set(i, cfc.floor());
                }
            } else {
                final Date cfDate = cf.date();
                final int jIdx = findPayDateIndex(a.leg2PayDates, cfDate, i);
                QL.require(jIdx >= 0, "nominal redemption on " + cfDate + " has no corresponding coupon");
                a.leg2IsRedemptionFlow[i] = true;
                a.leg2Coupons.set(i, cf.amount());
                a.leg2ResetDates.set(i, a.leg2ResetDates.get(jIdx));
                a.leg2FixingDates.set(i, a.leg2FixingDates.get(jIdx));
                a.leg2AccrualTimes.set(i, 0.0);
                a.leg2Spreads.set(i, 0.0);
                a.leg2Gearings.set(i, 1.0);
                a.leg2PayDates.set(i, cfDate);
            }
        }
    }

    /**
     * Fetch results from the engine. Mirrors C++ {@code FloatFloatSwap::fetchResults()}.
     */
    @Override
    public void fetchResults(final PricingEngine.Results r) {
        final double basisPoint = 1.0e-4;

        super.fetchResults(r);

        if ( r instanceof FloatFloatSwap.Results ) {
            final ResultsImpl res = (ResultsImpl) r;
            fairSpread1_ = res.fairSpread1;
            fairSpread2_ = res.fairSpread2;
        } else {
            fairSpread1_ = NULL_REAL;
            fairSpread2_ = NULL_REAL;
        }

        // Fallback: compute from BPS if not provided by engine
        if ( fairSpread1_ == NULL_REAL ) {
            if ( legBPS != null && legBPS.length > 0 && legBPS[0] != NULL_REAL ) {
                final double currentSpread = (spread1_.length > 0) ? spread1_[0] : 0.0;
                fairSpread1_ = currentSpread - NPV / (legBPS[0] / basisPoint);
            }
        }
        if ( fairSpread2_ == NULL_REAL ) {
            if ( legBPS != null && legBPS.length > 1 && legBPS[1] != NULL_REAL ) {
                final double currentSpread = (spread2_.length > 0) ? spread2_[0] : 0.0;
                fairSpread2_ = currentSpread - NPV / (legBPS[1] / basisPoint);
            }
        }
    }

    /**
     * Validates sizes, applies the zero-gearing dirty trick, builds both legs, inserts intermediate/final
     * capital-exchange flows, registers cashflows as observers, and sets payer signs. Mirrors C++
     * {@code FloatFloatSwap::init()}.
     */
    private void init() {
        // ── Validation ────────────────────────────────────────────────────────
        QL.require(nominal1_.length == schedule1_.size() - 1,
                "nominal1 size (" + nominal1_.length + ") does not match schedule1 size (" + schedule1_.size() + ")");
        QL.require(nominal2_.length == schedule2_.size() - 1,
                "nominal2 size (" + nominal2_.length + ") does not match schedule2 size (" + schedule2_.size() + ")");
        QL.require(gearing1_.length == nominal1_.length,
                "gearing1 size (" + gearing1_.length + ") does not match nominal1 size (" + nominal1_.length + ")");
        QL.require(gearing2_.length == nominal2_.length,
                "gearing2 size (" + gearing2_.length + ") does not match nominal2 size (" + nominal2_.length + ")");
        QL.require(cappedRate1_.length == nominal1_.length, "cappedRate1 size does not match nominal1 size");
        QL.require(cappedRate2_.length == nominal2_.length, "cappedRate2 size does not match nominal2 size");
        QL.require(flooredRate1_.length == nominal1_.length, "flooredRate1 size does not match nominal1 size");
        QL.require(flooredRate2_.length == nominal2_.length, "flooredRate2 size does not match nominal2 size");

        // Validate all-or-none for each cap/floor vector
        validateAllOrNone(cappedRate1_, "cappedRate1");
        validateAllOrNone(cappedRate2_, "cappedRate2");
        validateAllOrNone(flooredRate1_, "flooredRate1");
        validateAllOrNone(flooredRate2_, "flooredRate2");

        // ── Zero-gearing dirty trick ──────────────────────────────────────────
        // If gearing is zero the leg builder creates fixed coupons, which
        // confuse Gaussian1d engines. Replace near-zero gearings with QL_EPSILON.
        for ( int i = 0; i < gearing1_.length; i++ ) {
            if ( close(gearing1_[i], 0.0) )
                gearing1_[i] = QL_EPSILON;
        }
        for ( int i = 0; i < gearing2_.length; i++ ) {
            if ( close(gearing2_[i], 0.0) )
                gearing2_[i] = QL_EPSILON;
        }

        // ── Determine index types and build legs ──────────────────────────────
        final boolean ibor1 = (index1_ instanceof IborIndex);
        final boolean ibor2 = (index2_ instanceof IborIndex);
        final boolean cms1 = (!ibor1 && index1_ instanceof SwapIndex);
        final boolean cms2 = (!ibor2 && index2_ instanceof SwapIndex);

        QL.require(ibor1 || cms1, "index1 must be IborIndex or SwapIndex");
        QL.require(ibor2 || cms2, "index2 must be IborIndex or SwapIndex");

        // Leg 1
        final Leg leg1;
        if ( ibor1 ) {
            leg1 = buildIborLeg(schedule1_, (IborIndex) index1_, nominal1_, dayCount1_, paymentConvention1_, gearing1_,
                    spread1_, cappedRate1_, flooredRate1_);
        } else {
            leg1 = buildCmsLeg(schedule1_, (SwapIndex) index1_, nominal1_, dayCount1_, paymentConvention1_, gearing1_,
                    spread1_, cappedRate1_, flooredRate1_);
        }

        // Leg 2
        final Leg leg2;
        if ( ibor2 ) {
            leg2 = buildIborLeg(schedule2_, (IborIndex) index2_, nominal2_, dayCount2_, paymentConvention2_, gearing2_,
                    spread2_, cappedRate2_, flooredRate2_);
        } else {
            leg2 = buildCmsLeg(schedule2_, (SwapIndex) index2_, nominal2_, dayCount2_, paymentConvention2_, gearing2_,
                    spread2_, cappedRate2_, flooredRate2_);
        }

        // ── intermediateCapitalExchange ────────────────────────────────────────
        if ( intermediateCapitalExchange_ ) {
            insertIntermediateRedemptions(leg1, true);
            insertIntermediateRedemptions(leg2, false);
        }

        // ── finalCapitalExchange ───────────────────────────────────────────────
        if ( finalCapitalExchange_ ) {
            final double nom1Last = nominal1_[nominal1_.length - 1];
            leg1.add(new SimpleCashFlow(nom1Last, leg1.get(leg1.size() - 1).date()));
            nominal1_ = appendDouble(nominal1_, nom1Last);

            final double nom2Last = nominal2_[nominal2_.length - 1];
            leg2.add(new SimpleCashFlow(nom2Last, leg2.get(leg2.size() - 1).date()));
            nominal2_ = appendDouble(nominal2_, nom2Last);
        }

        // ── Register observers ────────────────────────────────────────────────
        for ( final CashFlow cf : leg1 )
            cf.addObserver(this);
        for ( final CashFlow cf : leg2 )
            cf.addObserver(this);

        // ── Add legs to parent Swap ────────────────────────────────────────────
        super.legs.add(leg1);
        super.legs.add(leg2);

        // ── Set payer signs ────────────────────────────────────────────────────
        // Payer: pays leg1 (−1), receives leg2 (+1)
        // Receiver: receives leg1 (+1), pays leg2 (−1)
        if ( type_ == VanillaSwap.Type.Payer ) {
            super.payer[0] = -1.0;
            super.payer[1] = +1.0;
        } else {
            super.payer[0] = +1.0;
            super.payer[1] = -1.0;
        }
    }

    /**
     * Insert intermediate redemption flows for amortising schedules.
     *
     * @param leg    the leg to insert flows into (modified in place)
     * @param isLeg1 true to update nominal1_, false to update nominal2_
     */
    private void insertIntermediateRedemptions(final Leg leg, final boolean isLeg1) {
        // Mirrors C++ loop inserting Redemption(nominal[i]-nominal[i+1]) at leg[i].date()
        // whenever consecutive notionals differ. The nominal_ array is also extended.
        double[] nomRef = isLeg1 ? nominal1_ : nominal2_;

        int i = 0;
        while ( i < leg.size() - 1 ) {
            // Get the current notional from the nominal array (accounting for
            // redemption insertions that have already extended it).
            // We track the coupon index separately from the leg index.
            // After an insertion at position i+1, we advance by 2.
            // The nomRef is updated after each insertion, so indices align.
            if ( i + 1 >= nomRef.length )
                break;
            final double diff = nomRef[i] - nomRef[i + 1];
            if ( !close(diff, 0.0) ) {
                final Date payDate = leg.get(i).date();
                leg.add(i + 1, new SimpleCashFlow(diff, payDate));
                // Insert nomRef[i] at position i+1 (duplicate, matching C++)
                nomRef = insertDouble(nomRef, i + 1, nomRef[i]);
                if ( isLeg1 )
                    nominal1_ = nomRef;
                else
                    nominal2_ = nomRef;
                i += 2; // skip past the inserted redemption
            } else {
                i++;
            }
        }
        // Ensure final assignment
        if ( isLeg1 )
            nominal1_ = nomRef;
        else
            nominal2_ = nomRef;
    }

    // ── Inner interfaces and classes ──────────────────────────────────────────

    /**
     * Marker interface for FloatFloatSwap engine arguments. Mirrors C++ {@code FloatFloatSwap::arguments}.
     */
    public interface Arguments extends Swap.Arguments { /* marker */
    }

    /**
     * Marker interface for FloatFloatSwap engine results. Mirrors C++ {@code FloatFloatSwap::results}.
     */
    public interface Results extends Swap.Results { /* marker */
    }

    /**
     * Concrete arguments implementation. Mirrors C++ {@code FloatFloatSwap::arguments}.
     */
    public static class ArgumentsImpl extends Swap.ArgumentsImpl implements FloatFloatSwap.Arguments {

        public VanillaSwap.Type type = VanillaSwap.Type.Receiver;
        public double[] nominal1;
        public double[] nominal2;

        public List< Date > leg1ResetDates;
        public List< Date > leg1FixingDates;
        public List< Date > leg1PayDates;
        public List< Date > leg2ResetDates;
        public List< Date > leg2FixingDates;
        public List< Date > leg2PayDates;

        public List< Double > leg1Spreads;
        public List< Double > leg2Spreads;
        public List< Double > leg1Gearings;
        public List< Double > leg2Gearings;
        public List< Double > leg1CappedRates;
        public List< Double > leg1FlooredRates;
        public List< Double > leg2CappedRates;
        public List< Double > leg2FlooredRates;

        public List< Double > leg1Coupons;
        public List< Double > leg2Coupons;
        public List< Double > leg1AccrualTimes;
        public List< Double > leg2AccrualTimes;

        public InterestRateIndex index1;
        public InterestRateIndex index2;

        public boolean[] leg1IsRedemptionFlow;
        public boolean[] leg2IsRedemptionFlow;

        @Override
        public void validate() {
            super.validate();
            QL.require(nominal1.length == leg1ResetDates.size(), "nominal1 size is different from resetDates1 size");
            QL.require(nominal1.length == leg1FixingDates.size(), "nominal1 size is different from fixingDates1 size");
            QL.require(nominal1.length == leg1PayDates.size(), "nominal1 size is different from payDates1 size");
            QL.require(nominal1.length == leg1Spreads.size(), "nominal1 size is different from spreads1 size");
            QL.require(nominal1.length == leg1Gearings.size(), "nominal1 size is different from gearings1 size");
            QL.require(nominal1.length == leg1CappedRates.size(), "nominal1 size is different from cappedRates1 size");
            QL.require(nominal1.length == leg1FlooredRates.size(),
                    "nominal1 size is different from flooredRates1 size");
            QL.require(nominal1.length == leg1Coupons.size(), "nominal1 size is different from coupons1 size");
            QL.require(nominal1.length == leg1AccrualTimes.size(),
                    "nominal1 size is different from accrualTimes1 size");
            QL.require(nominal1.length == leg1IsRedemptionFlow.length,
                    "nominal1 size is different from redemption1 size");

            QL.require(nominal2.length == leg2ResetDates.size(), "nominal2 size is different from resetDates2 size");
            QL.require(nominal2.length == leg2FixingDates.size(), "nominal2 size is different from fixingDates2 size");
            QL.require(nominal2.length == leg2PayDates.size(), "nominal2 size is different from payDates2 size");
            QL.require(nominal2.length == leg2Spreads.size(), "nominal2 size is different from spreads2 size");
            QL.require(nominal2.length == leg2Gearings.size(), "nominal2 size is different from gearings2 size");
            QL.require(nominal2.length == leg2CappedRates.size(), "nominal2 size is different from cappedRates2 size");
            QL.require(nominal2.length == leg2FlooredRates.size(),
                    "nominal2 size is different from flooredRates2 size");
            QL.require(nominal2.length == leg2Coupons.size(), "nominal2 size is different from coupons2 size");
            QL.require(nominal2.length == leg2AccrualTimes.size(),
                    "nominal2 size is different from accrualTimes2 size");
            QL.require(nominal2.length == leg2IsRedemptionFlow.length,
                    "nominal2 size is different from redemption2 size");

            QL.require(index1 != null, "index1 is null");
            QL.require(index2 != null, "index2 is null");
        }
    }

    /**
     * Concrete results implementation. Mirrors C++ {@code FloatFloatSwap::results}.
     */
    public static class ResultsImpl extends Swap.ResultsImpl implements FloatFloatSwap.Results {

        public double fairSpread1 = NULL_REAL;
        public double fairSpread2 = NULL_REAL;

        @Override
        public void reset() {
            super.reset();
            fairSpread1 = NULL_REAL;
            fairSpread2 = NULL_REAL;
        }
    }

    /**
     * Abstract engine for FloatFloatSwap. Mirrors C++ {@code FloatFloatSwap::engine}.
     */
    public abstract static class EngineImpl extends GenericEngine< FloatFloatSwap.Arguments, FloatFloatSwap.Results > {

        protected EngineImpl() {
            super(new ArgumentsImpl(), new ResultsImpl());
        }
    }
}
