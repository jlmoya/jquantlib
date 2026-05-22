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
 Copyright (C) 2003 RiskMap srl
 Copyright (C) 2004, 2005, 2006, 2007, 2008 StatPro Italia srl
 Copyright (C) 2009 Chris Kenyon

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/
package org.jquantlib.testsuite.cashflows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.cashflow.BachelierYoYInflationCouponPricer;
import org.jquantlib.cashflow.BlackYoYInflationCouponPricer;
import org.jquantlib.cashflow.CappedFlooredYoYInflationCoupon;
import org.jquantlib.cashflow.CashFlows;
import org.jquantlib.cashflow.FixedRateLeg;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.cashflow.UnitDisplacedBlackYoYInflationCouponPricer;
import org.jquantlib.cashflow.YoYInflationCoupon;
import org.jquantlib.cashflow.YoYInflationCouponPricer;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.daycounters.Thirty360;
import org.jquantlib.indexes.CPI;
import org.jquantlib.indexes.YoYInflationIndex;
import org.jquantlib.indexes.inflation.UKRPI;
import org.jquantlib.instruments.InflationCapFloor;
import org.jquantlib.instruments.Swap;
import org.jquantlib.instruments.YearOnYearInflationSwap;
import org.jquantlib.math.Constants;
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.inflation.YoYInflationBachelierCapFloorEngine;
import org.jquantlib.pricingengines.inflation.YoYInflationBlackCapFloorEngine;
import org.jquantlib.pricingengines.inflation.YoYInflationUnitDisplacedBlackCapFloorEngine;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.YoYInflationTermStructure;
import org.jquantlib.termstructures.inflation.InterpolatedYoYInflationCurve;
import org.jquantlib.termstructures.volatility.inflation.ConstantYoYOptionletVolatility;
import org.jquantlib.termstructures.volatility.inflation.YoYOptionletVolatilitySurface;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.MakeSchedule;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.UnitedKingdom;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Java port of the C++ {@code inflationcapflooredcoupon.cpp} BOOST test
 * suite (v1.42.1, 784 LOC) — covers
 * {@link CappedFlooredYoYInflationCoupon} behaviour and integration with
 * the {@link BlackYoYInflationCouponPricer},
 * {@link UnitDisplacedBlackYoYInflationCouponPricer},
 * {@link BachelierYoYInflationCouponPricer} pricers, plus parity with the
 * {@link InflationCapFloor} instrument under all three engines.
 *
 * <p>Phase 2u Track E — replaces the Phase 2q D.1 smoke test with a full
 * faithful port. The test fixture mirrors the C++ {@code CommonVars}
 * struct (UK 2007-08-13 setup, UKRPI fixings, 16-pillar YoY curve, 5%
 * flat nominal curve). The C++ {@code PiecewiseYoYInflationCurve}
 * bootstrap is replaced with an {@link InterpolatedYoYInflationCurve}
 * with synthetic pillar rates approximating the C++ bootstrap output —
 * the bootstrap path triggers an infinite-recursion handle cycle in Java
 * (no shared_ptr {@code reset()} equivalent), and absolute curve levels
 * are irrelevant to the cap/floor decomposition identities tested here.
 *
 * <p>Coverage:
 * <ul>
 *   <li>{@link #testDecomposition()} — capped/floored/collared YoY leg
 *       decomposes into vanilla leg ± cap/floor/collar instrument NPV
 *       (gearing=1, plus positive and negative gearing variants).</li>
 *   <li>{@link #testInstrumentEquality()} — capped coupon NPV equals
 *       {@code swap(0).NPV() - cap.NPV()} (and floored = swap + floor)
 *       across the full grid {@code lengths=[1,2,3,5,7,10,15,20]} ×
 *       {@code strikes=[0.01..0.07]} × {@code vols=[0.001..0.020]} ×
 *       {@code whichPricer=0..2}.</li>
 * </ul>
 *
 * <p>Tolerances: tier LOOSE (1e-6 absolute on NPV — matches C++) and
 * 1e-10 for the decomposition test (matches C++ {@code tolerance}).
 */
public class CappedFlooredYoYInflationCouponTest {

    /**
     * Fixture mirroring the C++ {@code CommonVars} struct in
     * {@code inflationcapflooredcoupon.cpp}. Builds:
     * <ul>
     *   <li>UK 2007-08-13 evaluation date, {@code Thirty360 BondBasis} day
     *       counter, {@code ModifiedFollowing} convention,
     *       Annual frequency, {@code observationLag = 2*Months}.</li>
     *   <li>A {@code UKRPI} index seeded with 31 monthly fixings
     *       (Jan-2005..Jul-2007) — same fix data as C++.</li>
     *   <li>A {@link YoYInflationIndex} (ratio-style) wrapping the UKRPI.</li>
     *   <li>A 16-pillar {@link InterpolatedYoYInflationCurve} (Linear) on
     *       the 5% flat-forward {@code ActualActual.ISDA} nominal curve.</li>
     * </ul>
     */
    private static final class CommonVars {

        // Common data — matches C++ CommonVars verbatim.
        final int length;
        final Date startDate;
        final double volatility;
        final Frequency frequency;
        final double[] nominals;
        final Calendar calendar;
        final BusinessDayConvention convention;
        final int fixingDays;
        final Date evaluationDate;
        final int settlementDays;
        final Date settlement;
        final Period observationLag;
        final DayCounter dc;
        final YoYInflationIndex iir;

        final Handle<YieldTermStructure> nominalTS;
        final Handle<YoYInflationTermStructure> hy;
        final YoYInflationTermStructure yoyTS;

        CommonVars() {
            this.frequency = Frequency.Annual;
            this.volatility = 0.01;
            this.length = 7;
            this.calendar = new UnitedKingdom();
            this.convention = BusinessDayConvention.ModifiedFollowing;
            this.nominals = new double[]{1_000_000.0};

            final Date today = new Date(13, Month.August, 2007);
            this.evaluationDate = calendar.adjust(today);
            new Settings().setEvaluationDate(evaluationDate);
            this.settlementDays = 0;
            this.fixingDays = 0;
            this.settlement = calendar.advance(today, settlementDays, TimeUnit.Days);
            this.startDate = settlement;
            this.dc = new Thirty360(Thirty360.Convention.BondBasis);

            // YoY index — fixings: monthly RPI 2005-01..2007-07 (31 entries).
            final Date from = new Date(1, Month.January, 2005);
            final Date to = new Date(13, Month.August, 2007);
            final Schedule rpiSchedule = new MakeSchedule(from, to,
                    new Period(1, TimeUnit.Months),
                    new UnitedKingdom(),
                    BusinessDayConvention.ModifiedFollowing)
                    .schedule();
            final double[] fixData = new double[]{
                    189.9, 189.9, 189.6, 190.5, 191.6, 192.0,
                    192.2, 192.2, 192.6, 193.1, 193.3, 193.6,
                    194.1, 193.4, 194.2, 195.0, 196.5, 197.7,
                    198.5, 198.5, 199.2, 200.1, 200.4, 201.1,
                    202.7, 201.6, 203.1, 204.4, 205.4, 206.2,
                    207.3,
            };

            final UKRPI rpi = new UKRPI(Frequency.Monthly, false, false);
            // C++ seeds with 33 entries (last two are -999 sentinels and a
            // trailing -999); rpi schedule has 32 dates here. We seed only
            // the 31 real entries (matches C++ behavior — the trailing -999
            // values are flagged as missing).
            for (int i = 0; i < fixData.length && i < rpiSchedule.size(); ++i) {
                rpi.addFixing(rpiSchedule.date(i), fixData[i], true);
            }

            // Link YoY index to YoY term structure via ratio constructor
            // (Phase 2u L0 A.3 align). This mirrors C++
            // ext::make_shared<YoYInflationIndex>(rpi, hy).
            // Build the YoY inflation curve directly using
            // InterpolatedYoYInflationCurve (Linear) with synthetic pillar
            // rates. We deliberately avoid PiecewiseYoYInflationCurve here
            // because the bootstrap path triggers an infinite-recursion cycle
            // when relinking the index handle — the C++ test sidesteps the
            // cycle via shared_ptr reset(), which has no Java equivalent.
            //
            // The pillar rates below approximate the C++ bootstrap output
            // (15-pillar UK YoY curve dated 13-Aug-2007); the absolute level
            // is irrelevant to the decomposition / parity tests, which check
            // identities like "capped = vanilla - cap" that hold for any
            // self-consistent YoY curve.
            this.observationLag = new Period(2, TimeUnit.Months);
            // Curve base date — must precede the first pillar. Use the
            // 31-fixing tail (1-July-2007). Hard-coded to be robust to any
            // leftover global IndexManager state from prior tests in the
            // same JVM (rpi.lastFixingDate() depends on global state).
            final Date yoyBaseDate = new Date(1, Month.July, 2007);
            final Date[] yoyNodeDates = new Date[]{
                    yoyBaseDate,
                    new Date(13, Month.August, 2008),
                    new Date(13, Month.August, 2009),
                    new Date(13, Month.August, 2010),
                    new Date(15, Month.August, 2011),
                    new Date(13, Month.August, 2012),
                    new Date(13, Month.August, 2013),
                    new Date(13, Month.August, 2014),
                    new Date(13, Month.August, 2015),
                    new Date(13, Month.August, 2016),
                    new Date(13, Month.August, 2017),
                    new Date(13, Month.August, 2019),
                    new Date(15, Month.August, 2022),
                    new Date(13, Month.August, 2027),
                    new Date(13, Month.August, 2032),
                    new Date(13, Month.August, 2037)
            };
            final double[] yoyNodeRates = new double[]{
                    0.0295, // base @ rpi.lastFixingDate()
                    0.0295, 0.0295, 0.0293, 0.02955, 0.02945,
                    0.02985, 0.0301, 0.03035, 0.03055, 0.03075,
                    0.03105, 0.03135, 0.03155, 0.03145, 0.03145
            };
            final InterpolatedYoYInflationCurve<Linear> curve =
                    new InterpolatedYoYInflationCurve<Linear>(Linear.class,
                            evaluationDate, yoyNodeDates, yoyNodeRates,
                            Frequency.Monthly, dc);
            curve.enableExtrapolation();
            this.yoyTS = curve;
            this.hy = new Handle<YoYInflationTermStructure>(curve);

            // Create the YoY ratio index pointing at the curve directly. This
            // mirrors C++ ext::make_shared<YoYInflationIndex>(rpi, hy) but
            // without the deferred-binding cycle.
            this.iir = new YoYInflationIndex(rpi, hy);

            final FlatForward nominalFF = new FlatForward(evaluationDate, 0.05,
                    new ActualActual(ActualActual.Convention.ISDA));
            this.nominalTS = new Handle<YieldTermStructure>(nominalFF);
        }

        /**
         * Build the YoY swaplet leg (no caps/floors). Mirrors C++
         * {@code makeYoYLeg(startDate, length, gearing=1, spread=0)}.
         */
        Leg makeYoYLeg(final Date startDate, final int legLength) {
            return makeYoYLeg(startDate, legLength, 1.0, 0.0);
        }

        Leg makeYoYLeg(final Date startDate, final int legLength,
                       final double gearing, final double spread) {
            final Date endDate = calendar.advance(startDate,
                    legLength, TimeUnit.Years, BusinessDayConvention.Unadjusted, false);
            final Schedule schedule = new Schedule(startDate, endDate,
                    new Period(frequency), calendar,
                    BusinessDayConvention.Unadjusted,
                    BusinessDayConvention.Unadjusted,
                    DateGeneration.Rule.Forward, false);

            return buildYoyInflationLeg(schedule, calendar, iir,
                    observationLag, CPI.InterpolationType.Flat,
                    nominals, dc, gearing, spread,
                    /*caps*/ null, /*floors*/ null,
                    convention, /*pricer*/ new YoYInflationCouponPricer());
        }

        /**
         * Build a fixed-rate (zero-coupon) leg. Mirrors C++
         * {@code makeFixedLeg(startDate, length)}.
         */
        Leg makeFixedLeg(final Date startDate, final int legLength) {
            final Date endDate = calendar.advance(startDate,
                    legLength, TimeUnit.Years, convention, false);
            final Schedule schedule = new Schedule(startDate, endDate,
                    new Period(frequency), calendar,
                    convention, convention,
                    DateGeneration.Rule.Forward, false);
            // Zero coupon rates.
            final double[] coupons = new double[legLength];
            Arrays.fill(coupons, 0.0);
            return new FixedRateLeg(schedule, dc)
                    .withNotionals(nominals)
                    .withCouponRates(coupons)
                    .Leg();
        }

        /**
         * Build a capped/floored YoY leg. Mirrors C++
         * {@code makeYoYCapFlooredLeg(which, startDate, length, caps, floors,
         * volatility, gearing, spread)}.
         */
        Leg makeYoYCapFlooredLeg(final int which,
                                 final Date startDate,
                                 final int legLength,
                                 final List<Double> caps,
                                 final List<Double> floors,
                                 final double vol) {
            return makeYoYCapFlooredLeg(which, startDate, legLength,
                    caps, floors, vol, 1.0, 0.0);
        }

        Leg makeYoYCapFlooredLeg(final int which,
                                 final Date startDate,
                                 final int legLength,
                                 final List<Double> caps,
                                 final List<Double> floors,
                                 final double vol,
                                 final double gearing,
                                 final double spread) {
            final Handle<YoYOptionletVolatilitySurface> volHandle =
                    new Handle<YoYOptionletVolatilitySurface>(
                            new ConstantYoYOptionletVolatility(vol,
                                    settlementDays, calendar, convention,
                                    dc, observationLag, frequency,
                                    iir.interpolated()));

            final YoYInflationCouponPricer pricer;
            switch (which) {
                case 0:
                    pricer = new BlackYoYInflationCouponPricer(volHandle, nominalTS);
                    break;
                case 1:
                    pricer = new UnitDisplacedBlackYoYInflationCouponPricer(volHandle, nominalTS);
                    break;
                case 2:
                    pricer = new BachelierYoYInflationCouponPricer(volHandle, nominalTS);
                    break;
                default:
                    throw new IllegalArgumentException(
                            "unknown coupon pricer request: which=" + which);
            }

            final Date endDate = calendar.advance(startDate,
                    legLength, TimeUnit.Years, BusinessDayConvention.Unadjusted, false);
            final Schedule schedule = new Schedule(startDate, endDate,
                    new Period(frequency), calendar,
                    BusinessDayConvention.Unadjusted,
                    BusinessDayConvention.Unadjusted,
                    DateGeneration.Rule.Forward, false);

            return buildYoyInflationLeg(schedule, calendar, iir,
                    observationLag, CPI.InterpolationType.Flat,
                    nominals, dc, gearing, spread,
                    caps, floors, convention, pricer);
        }

        /**
         * Build the {@link InflationCapFloor} pricing engine that
         * matches the supplied pricer choice. Mirrors C++
         * {@code makeEngine}.
         */
        PricingEngine makeEngine(final double vol, final int which) {
            final Handle<YoYOptionletVolatilitySurface> volHandle =
                    new Handle<YoYOptionletVolatilitySurface>(
                            new ConstantYoYOptionletVolatility(vol,
                                    settlementDays, calendar, convention,
                                    dc, observationLag, frequency,
                                    iir.interpolated()));
            switch (which) {
                case 0:
                    return new YoYInflationBlackCapFloorEngine(iir, volHandle, nominalTS);
                case 1:
                    return new YoYInflationUnitDisplacedBlackCapFloorEngine(iir, volHandle, nominalTS);
                case 2:
                    return new YoYInflationBachelierCapFloorEngine(iir, volHandle, nominalTS);
                default:
                    throw new IllegalArgumentException(
                            "unknown engine request: which=" + which);
            }
        }

        /**
         * Build a {@link InflationCapFloor} with the given type and a
         * single strike. Mirrors C++ {@code makeYoYCapFloor}.
         */
        InflationCapFloor makeYoYCapFloor(final InflationCapFloor.Type type,
                                          final Leg leg, final double strike,
                                          final double vol, final int which) {
            final InflationCapFloor result;
            switch (type) {
                case Cap: {
                    final List<Double> strikes = new ArrayList<>();
                    strikes.add(strike);
                    result = new InflationCapFloor.Cap(leg, strikes);
                    break;
                }
                case Floor: {
                    final List<Double> strikes = new ArrayList<>();
                    strikes.add(strike);
                    result = new InflationCapFloor.Floor(leg, strikes);
                    break;
                }
                default:
                    throw new IllegalArgumentException(
                            "unknown YoYInflation cap/floor type");
            }
            result.setPricingEngine(makeEngine(vol, which));
            return result;
        }
    }

    /**
     * Java translation of the C++ {@code yoyInflationLeg} builder. Builds
     * a leg of {@link YoYInflationCoupon} when no cap/floor is active,
     * else {@link CappedFlooredYoYInflationCoupon}, then sets the supplied
     * pricer on each coupon (mirrors {@code setCouponPricer(yoyLeg,
     * pricer)}).
     */
    private static Leg buildYoyInflationLeg(final Schedule schedule,
                                            final Calendar calendar,
                                            final YoYInflationIndex index,
                                            final Period observationLag,
                                            final CPI.InterpolationType interpolation,
                                            final double[] notionals,
                                            final DayCounter paymentDayCounter,
                                            final double gearing,
                                            final double spread,
                                            final List<Double> caps,
                                            final List<Double> floors,
                                            final BusinessDayConvention paymentAdjustment,
                                            final YoYInflationCouponPricer pricer) {
        final int n = schedule.size() - 1;
        final Leg leg = new Leg();

        for (int i = 0; i < n; ++i) {
            Date refStart = schedule.date(i);
            Date start = schedule.date(i);
            Date refEnd = schedule.date(i + 1);
            Date end = schedule.date(i + 1);
            final Date paymentDate = calendar.adjust(end, paymentAdjustment);

            // Mirrors C++ schedule.hasIsRegular() && !schedule.isRegular(i+1)
            // first/last short-period adjustment. For our use the schedule
            // is built with Unadjusted endpoints from start to start+length*Y
            // — every period is regular, so refStart/refEnd remain start/end.

            final double notional = notionals[Math.min(i, notionals.length - 1)];
            final boolean hasCap = caps != null && !caps.isEmpty();
            final boolean hasFloor = floors != null && !floors.isEmpty();

            if (gearing == 0.0) {
                // Fixed coupon — for our tests gearing is never 0.
                throw new IllegalStateException(
                        "fixed-coupon path not exercised by Phase 2u tests");
            }

            if (!hasCap && !hasFloor) {
                final YoYInflationCoupon coupon = new YoYInflationCoupon(
                        notional, paymentDate, start, end,
                        /* fixingDays */ 0,
                        index, observationLag, interpolation, paymentDayCounter,
                        gearing, spread, refStart, refEnd);
                coupon.setPricer(pricer);
                leg.add(coupon);
            } else {
                final double cap = hasCap
                        ? caps.get(Math.min(i, caps.size() - 1))
                        : Constants.NULL_REAL;
                final double floor = hasFloor
                        ? floors.get(Math.min(i, floors.size() - 1))
                        : Constants.NULL_REAL;
                final CappedFlooredYoYInflationCoupon coupon =
                        new CappedFlooredYoYInflationCoupon(
                                notional, paymentDate, start, end,
                                /* fixingDays */ 0,
                                index, observationLag, interpolation, paymentDayCounter,
                                gearing, spread, cap, floor, refStart, refEnd);
                coupon.setPricer(pricer);
                leg.add(coupon);
            }
        }
        return leg;
    }

    //
    // Test cases — mirror BOOST_AUTO_TEST_CASE blocks 1:1.
    //

    /**
     * Java port of C++ {@code testDecomposition} (inflationcapflooredcoupon.cpp:366-679).
     *
     * <p>Tests that capped/floored/collared YoY legs decompose into
     * vanilla floating leg ± {@link InflationCapFloor} NPV under all
     * eight gearing/spread/strike combinations.
     */
    @Test
    public void testDecomposition() {
        final CommonVars vars = new CommonVars();
        final double tolerance = 1.0e-10;

        final double floorstrike = 0.05;
        final double capstrike = 0.10;
        final List<Double> caps = constList(vars.length, capstrike);
        final List<Double> capsEmpty = new ArrayList<>();
        final List<Double> floors = constList(vars.length, floorstrike);
        final List<Double> floorsEmpty = new ArrayList<>();
        final double gearingP = 0.5;
        final double spreadP = 0.002;
        final double gearingN = -1.5;
        final double spreadN = 0.12;

        // Vanilla building blocks.
        final Leg fixedLeg = vars.makeFixedLeg(vars.startDate, vars.length);
        final Leg floatLeg = vars.makeYoYLeg(vars.startDate, vars.length);
        final Leg floatLegP = vars.makeYoYLeg(vars.startDate, vars.length,
                gearingP, spreadP);
        final Leg floatLegN = vars.makeYoYLeg(vars.startDate, vars.length,
                gearingN, spreadN);

        final Swap vanillaLeg = new Swap(fixedLeg, floatLeg);
        final Swap vanillaLegP = new Swap(fixedLeg, floatLegP);
        final Swap vanillaLegN = new Swap(fixedLeg, floatLegN);

        final PricingEngine engine = new DiscountingSwapEngine(vars.nominalTS);
        vanillaLeg.setPricingEngine(engine);
        vanillaLegP.setPricingEngine(engine);
        vanillaLegN.setPricingEngine(engine);

        final int whichPricer = 0; // Black

        // Case 1: gearing=1, spread=0, capped.
        // Payoff = VanillaFloatingLeg - Call
        {
            final Leg cappedLeg = vars.makeYoYCapFlooredLeg(whichPricer,
                    vars.startDate, vars.length, caps, floorsEmpty, vars.volatility);
            final Swap capLeg = new Swap(fixedLeg, cappedLeg);
            capLeg.setPricingEngine(engine);
            final InflationCapFloor cap = new InflationCapFloor.Cap(floatLeg,
                    constList(1, capstrike));
            cap.setPricingEngine(vars.makeEngine(vars.volatility, whichPricer));

            final double npvVanilla = vanillaLeg.NPV();
            final double npvCappedLeg = capLeg.NPV();
            final double npvCap = cap.NPV();
            final double error = Math.abs(npvCappedLeg - (npvVanilla - npvCap));
            assertTrue("YoY Capped Leg gearing=1, spread=0, strike=" + capstrike
                    + ": Capped Leg NPV=" + npvCappedLeg
                    + ", VanillaLeg-Cap=" + (npvVanilla - npvCap)
                    + ", diff=" + error,
                    error <= tolerance);
        }

        // Case 2: gearing=1, spread=0, floored.
        // Payoff = VanillaFloatingLeg + Put
        final double npvFlooredVanilla = vanillaLeg.NPV();
        {
            final Leg flooredLeg = vars.makeYoYCapFlooredLeg(whichPricer,
                    vars.startDate, vars.length, capsEmpty, floors, vars.volatility);
            final Swap floorLegSwap = new Swap(fixedLeg, flooredLeg);
            floorLegSwap.setPricingEngine(engine);
            final InflationCapFloor floor = new InflationCapFloor.Floor(floatLeg,
                    constList(1, floorstrike));
            floor.setPricingEngine(vars.makeEngine(vars.volatility, whichPricer));

            final double npvFlooredLeg = floorLegSwap.NPV();
            final double npvFloor = floor.NPV();
            final double error = Math.abs(npvFlooredLeg - (npvFlooredVanilla + npvFloor));
            assertTrue("YoY Floored Leg gearing=1, spread=0, strike=" + floorstrike
                    + ": Floored Leg NPV=" + npvFlooredLeg
                    + ", VanillaLeg+Floor=" + (npvFlooredVanilla + npvFloor)
                    + ", diff=" + error,
                    error <= tolerance);
        }

        // Case 3: gearing=1, spread=0, collared.
        // Payoff = VanillaFloatingLeg - Collar
        {
            final Leg collaredLeg = vars.makeYoYCapFlooredLeg(whichPricer,
                    vars.startDate, vars.length, caps, floors, vars.volatility);
            final Swap collarLeg = new Swap(fixedLeg, collaredLeg);
            collarLeg.setPricingEngine(engine);
            final InflationCapFloor collar = new InflationCapFloor.Collar(floatLeg,
                    constList(1, capstrike), constList(1, floorstrike));
            collar.setPricingEngine(vars.makeEngine(vars.volatility, whichPricer));

            final double npvVanilla = vanillaLeg.NPV();
            final double npvCollaredLeg = collarLeg.NPV();
            final double npvCollar = collar.NPV();
            final double error = Math.abs(npvCollaredLeg - (npvVanilla - npvCollar));
            assertTrue("YoY Collared Leg gearing=1, spread=0, strikes="
                    + floorstrike + "/" + capstrike
                    + ": Collared Leg NPV=" + npvCollaredLeg
                    + ", VanillaLeg-Collar=" + (npvVanilla - npvCollar)
                    + ", diff=" + error,
                    error <= tolerance);
        }

        // Case 4: positive gearing, capped.
        // Payoff = VanillaFloatingLeg - Call(a*rate+b, strike)
        {
            final Leg cappedLegP = vars.makeYoYCapFlooredLeg(whichPricer,
                    vars.startDate, vars.length, caps, floorsEmpty,
                    vars.volatility, gearingP, spreadP);
            final Swap capLegP = new Swap(fixedLeg, cappedLegP);
            capLegP.setPricingEngine(engine);
            final InflationCapFloor capP = new InflationCapFloor.Cap(floatLegP,
                    constList(1, capstrike));
            capP.setPricingEngine(vars.makeEngine(vars.volatility, whichPricer));

            final double npvVanilla = vanillaLegP.NPV();
            final double npvCappedLeg = capLegP.NPV();
            final double npvCap = capP.NPV();
            final double error = Math.abs(npvCappedLeg - (npvVanilla - npvCap));
            assertTrue("YoY Capped Leg gearing=" + gearingP + ", spread=" + spreadP
                    + ", strike=" + capstrike
                    + ": diff=" + error, error <= tolerance);
        }

        // Case 5: negative gearing, capped.
        // Payoff = VanillaFloatingLeg + Put(|a|*rate+b, strike)
        {
            final Leg cappedLegN = vars.makeYoYCapFlooredLeg(whichPricer,
                    vars.startDate, vars.length, caps, floorsEmpty,
                    vars.volatility, gearingN, spreadN);
            final Swap capLegN = new Swap(fixedLeg, cappedLegN);
            capLegN.setPricingEngine(engine);
            final InflationCapFloor floorN = new InflationCapFloor.Floor(floatLeg,
                    constList(1, (capstrike - spreadN) / gearingN));
            floorN.setPricingEngine(vars.makeEngine(vars.volatility, whichPricer));

            final double npvVanilla = vanillaLegN.NPV();
            final double npvCappedLeg = capLegN.NPV();
            final double npvFloor = floorN.NPV();
            final double error = Math.abs(
                    npvCappedLeg - (npvVanilla + gearingN * npvFloor));
            assertTrue("YoY Capped Leg gearing=" + gearingN + ", spread=" + spreadN
                    + ", strike=" + capstrike
                    + ": diff=" + error, error <= tolerance);
        }

        // Case 6: positive gearing, floored.
        // Payoff = VanillaFloatingLeg + Put(a*rate+b, strike)
        {
            final Leg flooredLegP1 = vars.makeYoYCapFlooredLeg(whichPricer,
                    vars.startDate, vars.length, capsEmpty, floors,
                    vars.volatility, gearingP, spreadP);
            final Swap floorLegP1 = new Swap(fixedLeg, flooredLegP1);
            floorLegP1.setPricingEngine(engine);
            final InflationCapFloor floorP1 = new InflationCapFloor.Floor(floatLegP,
                    constList(1, floorstrike));
            floorP1.setPricingEngine(vars.makeEngine(vars.volatility, whichPricer));

            final double npvVanilla = vanillaLegP.NPV();
            final double npvFlooredLeg = floorLegP1.NPV();
            final double npvFloor = floorP1.NPV();
            final double error = Math.abs(npvFlooredLeg - (npvVanilla + npvFloor));
            assertTrue("YoY Floored Leg gearing=" + gearingP + ", spread=" + spreadP
                    + ", strike=" + floorstrike
                    + ": diff=" + error, error <= tolerance);
        }

        // Case 7: negative gearing, floored.
        // Payoff = VanillaFloatingLeg - Call(|a|*rate+b, strike)
        {
            final Leg flooredLegN = vars.makeYoYCapFlooredLeg(whichPricer,
                    vars.startDate, vars.length, capsEmpty, floors,
                    vars.volatility, gearingN, spreadN);
            final Swap floorLegN = new Swap(fixedLeg, flooredLegN);
            floorLegN.setPricingEngine(engine);
            final InflationCapFloor capN = new InflationCapFloor.Cap(floatLeg,
                    constList(1, (floorstrike - spreadN) / gearingN));
            capN.setPricingEngine(vars.makeEngine(vars.volatility, whichPricer));

            final double npvVanilla = vanillaLegN.NPV();
            final double npvFlooredLeg = floorLegN.NPV();
            final double npvCap = capN.NPV();
            final double error = Math.abs(
                    npvFlooredLeg - (npvVanilla - gearingN * npvCap));
            assertTrue("YoY Floored Leg gearing=" + gearingN + ", spread=" + spreadN
                    + ", strike=" + floorstrike
                    + ": diff=" + error, error <= tolerance);
        }

        // Case 8: positive gearing, collared.
        // Payoff = VanillaFloatingLeg - Collar(a*rate+b, floorrate, caprate)
        {
            final Leg collaredLegP = vars.makeYoYCapFlooredLeg(whichPricer,
                    vars.startDate, vars.length, caps, floors,
                    vars.volatility, gearingP, spreadP);
            final Swap collarLegP1 = new Swap(fixedLeg, collaredLegP);
            collarLegP1.setPricingEngine(engine);
            final InflationCapFloor collarP = new InflationCapFloor.Collar(floatLegP,
                    constList(1, capstrike), constList(1, floorstrike));
            collarP.setPricingEngine(vars.makeEngine(vars.volatility, whichPricer));

            final double npvVanilla = vanillaLegP.NPV();
            final double npvCollaredLeg = collarLegP1.NPV();
            final double npvCollar = collarP.NPV();
            final double error = Math.abs(npvCollaredLeg - (npvVanilla - npvCollar));
            assertTrue("YoY Collared Leg gearing=" + gearingP + ", spread=" + spreadP
                    + ": diff=" + error, error <= tolerance);
        }

        // Case 9: negative gearing, collared.
        // Payoff = VanillaFloatingLeg + Collar(|a|*rate+b, caprate, floorrate)
        {
            final Leg collaredLegN = vars.makeYoYCapFlooredLeg(whichPricer,
                    vars.startDate, vars.length, caps, floors,
                    vars.volatility, gearingN, spreadN);
            final Swap collarLegN1 = new Swap(fixedLeg, collaredLegN);
            collarLegN1.setPricingEngine(engine);
            final InflationCapFloor collarN = new InflationCapFloor.Collar(floatLeg,
                    constList(1, (floorstrike - spreadN) / gearingN),
                    constList(1, (capstrike - spreadN) / gearingN));
            collarN.setPricingEngine(vars.makeEngine(vars.volatility, whichPricer));

            final double npvVanilla = vanillaLegN.NPV();
            final double npvCollaredLeg = collarLegN1.NPV();
            final double npvCollar = collarN.NPV();
            final double error = Math.abs(
                    npvCollaredLeg - (npvVanilla - gearingN * npvCollar));
            assertTrue("YoY Collared Leg gearing=" + gearingN + ", spread=" + spreadN
                    + ": diff=" + error, error <= tolerance);
        }
    }

    /**
     * Java port of C++ {@code testInstrumentEquality} (inflationcapflooredcoupon.cpp:681-780).
     *
     * <p>Verifies parity between a capped/floored YoY coupon leg's
     * {@link CashFlows#npv} and the underlying YoY swap NPV (with strike=0)
     * adjusted by the corresponding {@link InflationCapFloor} NPV, across
     * a grid of lengths/strikes/vols and all three pricers.
     */
    @Test
    public void testInstrumentEquality() {
        final CommonVars vars = new CommonVars();
        final int[] lengths = {1, 2, 3, 5, 7, 10, 15, 20};
        final double[] strikes = {0.01, 0.025, 0.029, 0.03, 0.031, 0.035, 0.07};
        final double[] vols = {0.001, 0.005, 0.010, 0.015, 0.020};
        final double tolerance = 1.0e-6;

        for (int whichPricer = 0; whichPricer < 3; whichPricer++) {
            for (final int legLength : lengths) {
                for (final double strike : strikes) {
                    for (final double vol : vols) {
                        // Build the underlying vanilla YoY leg.
                        final Leg leg = vars.makeYoYLeg(vars.evaluationDate, legLength);

                        // Build cap and floor instruments on the same leg.
                        final InflationCapFloor cap = vars.makeYoYCapFloor(
                                InflationCapFloor.Type.Cap, leg, strike, vol, whichPricer);
                        final InflationCapFloor floor = vars.makeYoYCapFloor(
                                InflationCapFloor.Type.Floor, leg, strike, vol, whichPricer);

                        // Reference YoY swap with strike=0 — computes the
                        // forward (vanilla floating) NPV.
                        final Date from = vars.nominalTS.currentLink().referenceDate();
                        final Date to = from.add(new Period(legLength, TimeUnit.Years));
                        final Schedule yoySchedule = new MakeSchedule(from, to,
                                new Period(1, TimeUnit.Years),
                                new UnitedKingdom(),
                                BusinessDayConvention.Unadjusted)
                                .withTerminationDateConvention(BusinessDayConvention.Unadjusted)
                                .backwards()
                                .schedule();

                        final YearOnYearInflationSwap swap = new YearOnYearInflationSwap(
                                YearOnYearInflationSwap.Type.Payer,
                                1_000_000.0,
                                yoySchedule, // fixed schedule (same as yoy schedule)
                                0.0, // fixedRate
                                vars.dc,
                                yoySchedule,
                                vars.iir,
                                vars.observationLag,
                                CPI.InterpolationType.Flat,
                                0.0, // spread on index
                                vars.dc,
                                new UnitedKingdom());

                        final Handle<YieldTermStructure> hTS = vars.nominalTS;
                        final PricingEngine sppe = new DiscountingSwapEngine(hTS);
                        swap.setPricingEngine(sppe);

                        // Capped leg with this pricer's volatility surface.
                        final Leg leg2 = vars.makeYoYCapFlooredLeg(whichPricer,
                                from, legLength,
                                constList(legLength, strike), // cap
                                new ArrayList<>(),       // floor
                                vol, 1.0, 0.0);

                        // Floored leg with this pricer's volatility surface.
                        final Leg leg3 = vars.makeYoYCapFlooredLeg(whichPricer,
                                from, legLength,
                                new ArrayList<>(),       // cap
                                constList(legLength, strike), // floor
                                vol, 1.0, 0.0);

                        // capped coupon = swap(0) - cap
                        final double capped = CashFlows.getInstance().npv(leg2, vars.nominalTS);
                        final double diffCap = Math.abs(capped - (swap.NPV() - cap.NPV()));
                        assertTrue("capped coupon != swap(0) - cap"
                                + ": pricer=" + whichPricer
                                + ", length=" + legLength
                                + ", vol=" + vol
                                + ", strike=" + strike
                                + ", capped=" + capped
                                + ", swap-cap=" + (swap.NPV() - cap.NPV())
                                + ", diff=" + diffCap,
                                diffCap <= tolerance);

                        // floored coupon = swap(0) + floor
                        final double floored = CashFlows.getInstance().npv(leg3, vars.nominalTS);
                        final double diffFloor = Math.abs(floored - (swap.NPV() + floor.NPV()));
                        assertTrue("floored coupon != swap(0) + floor"
                                + ": pricer=" + whichPricer
                                + ", length=" + legLength
                                + ", vol=" + vol
                                + ", strike=" + strike
                                + ", floored=" + floored
                                + ", swap+floor=" + (swap.NPV() + floor.NPV())
                                + ", diff=" + diffFloor,
                                diffFloor <= tolerance);
                    }
                }
            }
        }
    }

    //
    // Helpers
    //

    private static List<Double> constList(final int n, final double v) {
        final List<Double> out = new ArrayList<>(n);
        for (int i = 0; i < n; ++i) out.add(v);
        return out;
    }
}
