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
import org.jquantlib.cashflow.CashFlow;
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
import org.jquantlib.instruments.Swap;
import org.jquantlib.instruments.YearOnYearInflationSwap;
import org.jquantlib.instruments.YoYInflationCap;
import org.jquantlib.instruments.YoYInflationCapFloor;
import org.jquantlib.instruments.YoYInflationCollar;
import org.jquantlib.instruments.YoYInflationFloor;
import org.jquantlib.math.Constants;
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.inflation.YoYInflationBachelierCapFloorEngine;
import org.jquantlib.pricingengines.inflation.YoYInflationBlackCapFloorEngine;
import org.jquantlib.pricingengines.inflation.YoYInflationUnitDisplacedBlackCapFloorEngine;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.InflationTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.YoYInflationTermStructure;
import org.jquantlib.termstructures.inflation.PiecewiseYoYInflationCurve;
import org.jquantlib.termstructures.inflation.YearOnYearInflationSwapHelper;
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

import static org.junit.Assert.fail;

/**
 * Port of the C++ test suite {@code inflationcapflooredcoupon.cpp}
 * (Phase 2t D.5-B, 784 LOC).
 *
 * <p>Mirrors {@code InflationCapFlooredCouponTests} from
 * {@code migration-harness/cpp/quantlib/test-suite/inflationcapflooredcoupon.cpp}:
 * <ul>
 *   <li>{@code testDecomposition}: collared coupon = capped + floored
 *       decomposition checked for gearing=1 and arbitrary (gearing, spread)
 *       combinations using YoY inflation cap/floor instruments with three
 *       coupon pricers (Black, UnitDisplaced Black, Bachelier).</li>
 *   <li>{@code testInstrumentEquality}: capped/floored coupon == swap(0) -/+
 *       cap/floor across an 8 x 7 x 5 (= 280) grid for 3 pricers (= 840
 *       sub-cases).</li>
 * </ul>
 *
 * <p>Tier: TIGHT — payoff identities at 1e-10 (decomposition) and 1e-6
 * (instrument equality), matching the C++ test's hard-coded tolerances.
 */
public class InflationCapFlooredCouponTest {

    // ===== Common test fixture (mirrors C++ CommonVars) =====
    private static final class CommonVars {
        final int length;
        final Date startDate;
        final double volatility;
        final Frequency frequency;
        final double[] nominals = { 1_000_000.0 };
        final Calendar calendar;
        final BusinessDayConvention convention;
        final int fixingDays;
        final Date evaluationDate;
        final int settlementDays;
        final Period observationLag;
        final DayCounter dc;
        final YoYInflationIndex iir;
        final RelinkableHandle<YieldTermStructure> nominalTS;
        final RelinkableHandle<YoYInflationTermStructure> hy;

        CommonVars() {
            this.frequency = Frequency.Annual;
            this.volatility = 0.01;
            this.length = 7;
            this.calendar = new UnitedKingdom();
            this.convention = BusinessDayConvention.ModifiedFollowing;
            final Date today = new Date(13, Month.August, 2007);
            this.evaluationDate = calendar.adjust(today);
            new Settings().setEvaluationDate(evaluationDate);
            this.settlementDays = 0;
            this.fixingDays = 0;
            final Date settlement = calendar.advance(today, settlementDays,
                    TimeUnit.Days, BusinessDayConvention.Following, false);
            this.startDate = settlement;
            this.dc = new Thirty360(Thirty360.Convention.BondBasis);

            // RPI fixing schedule and data
            final Schedule rpiSchedule = new MakeSchedule(
                    new Date(1, Month.January, 2005),
                    new Date(13, Month.August, 2007),
                    new Period(1, TimeUnit.Months),
                    new UnitedKingdom(), BusinessDayConvention.ModifiedFollowing)
                    .schedule();
            final double[] fixData = {
                    189.9, 189.9, 189.6, 190.5, 191.6, 192.0,
                    192.2, 192.2, 192.6, 193.1, 193.3, 193.6,
                    194.1, 193.4, 194.2, 195.0, 196.5, 197.7,
                    198.5, 198.5, 199.2, 200.1, 200.4, 201.1,
                    202.7, 201.6, 203.1, 204.4, 205.4, 206.2,
                    207.3, -999.0, -999.0
            };
            // Seed UKRPI fixings on a standalone (no-handle) index per C++
            final UKRPI rpi = new UKRPI(Frequency.Monthly, false, false);
            for (int i = 0; i < rpiSchedule.size() && i < fixData.length; i++) {
                if (fixData[i] > 0) {
                    rpi.addFixing(rpiSchedule.date(i), fixData[i], true);
                }
            }

            // Wrap RPI as YoYInflationIndex bound to relinkable handle
            this.hy = new RelinkableHandle<>();
            this.iir = new YoYInflationIndex(rpi, hy);

            // Nominal: flat 5% Act/Act ISDA
            final YieldTermStructure nominalFF = new FlatForward(evaluationDate, 0.05,
                    new ActualActual(ActualActual.Convention.ISDA));
            this.nominalTS = new RelinkableHandle<>(nominalFF);

            // YoY inflation curve setup
            this.observationLag = new Period(2, TimeUnit.Months);

            final Object[][] yyData = {
                    { new Date(13, Month.August, 2008), 2.95 },
                    { new Date(13, Month.August, 2009), 2.95 },
                    { new Date(13, Month.August, 2010), 2.93 },
                    { new Date(15, Month.August, 2011), 2.955 },
                    { new Date(13, Month.August, 2012), 2.945 },
                    { new Date(13, Month.August, 2013), 2.985 },
                    { new Date(13, Month.August, 2014), 3.01 },
                    { new Date(13, Month.August, 2015), 3.035 },
                    { new Date(13, Month.August, 2016), 3.055 },
                    { new Date(13, Month.August, 2017), 3.075 },
                    { new Date(13, Month.August, 2019), 3.105 },
                    { new Date(15, Month.August, 2022), 3.135 },
                    { new Date(13, Month.August, 2027), 3.155 },
                    { new Date(13, Month.August, 2032), 3.145 },
                    { new Date(13, Month.August, 2037), 3.145 }
            };

            final List<YearOnYearInflationSwapHelper> helpers = new ArrayList<>();
            for (final Object[] row : yyData) {
                final Date maturity = (Date) row[0];
                final double rate = (Double) row[1];
                final Quote q = new SimpleQuote(rate / 100.0);
                final Handle<Quote> qh = new Handle<>(q);
                helpers.add(new YearOnYearInflationSwapHelper(qh, observationLag,
                        maturity, calendar, convention, dc, iir,
                        CPI.InterpolationType.Flat,
                        new Handle<>(nominalFF)));
            }

            final Date baseDate = rpi.lastFixingDate();
            final double baseYYRate = ((Double) yyData[0][1]) / 100.0;
            // Note: PiecewiseYoYInflationCurve expects a valid baseDate (start of
            // inflation period containing baseDate). Mirrors C++ behaviour where
            // baseDate is the index's lastFixingDate.
            final Date baseDateForCurve = InflationTermStructure
                    .inflationPeriod(baseDate, iir.frequency()).first();
            final var pYYTS = new PiecewiseYoYInflationCurve<Linear>(
                    Linear.class, evaluationDate, baseDateForCurve, baseYYRate,
                    iir.frequency(), dc, helpers);
            hy.linkTo(pYYTS);
        }

        Leg makeYoYLeg(final Date startDate, final int length) {
            return makeYoYLeg(startDate, length, 1.0, 0.0);
        }

        Leg makeYoYLeg(final Date startDate, final int length,
                       final double gearing, final double spread) {
            final Date endDate = calendar.advance(startDate, length, TimeUnit.Years,
                    BusinessDayConvention.Unadjusted, false);
            final Schedule schedule = new Schedule(startDate, endDate,
                    new Period(frequency), calendar,
                    BusinessDayConvention.Unadjusted,
                    BusinessDayConvention.Unadjusted,
                    DateGeneration.Rule.Forward, false);

            return buildYoYLeg(schedule, gearing, spread,
                    new double[0], new double[0]);
        }

        Leg makeFixedLeg(final Date startDate, final int length) {
            final Date endDate = calendar.advance(startDate, length, TimeUnit.Years,
                    convention, false);
            final Schedule schedule = new Schedule(startDate, endDate,
                    new Period(frequency), calendar, convention, convention,
                    DateGeneration.Rule.Forward, false);
            final double[] zeroRates = new double[length];
            return new FixedRateLeg(schedule, dc).withNotionals(nominals[0])
                    .withCouponRates(zeroRates).Leg();
        }

        Leg makeYoYCapFlooredLeg(final int whichPricer, final Date startDate,
                                  final int length, final double[] caps,
                                  final double[] floors, final double vol) {
            return makeYoYCapFlooredLeg(whichPricer, startDate, length, caps, floors,
                    vol, 1.0, 0.0);
        }

        Leg makeYoYCapFlooredLeg(final int whichPricer, final Date startDate,
                                  final int length, final double[] caps,
                                  final double[] floors, final double vol,
                                  final double gearing, final double spread) {
            final var volSurface = new ConstantYoYOptionletVolatility(vol,
                    settlementDays, calendar, convention, dc, observationLag,
                    frequency, iir.interpolated());
            final Handle<YoYOptionletVolatilitySurface> volH = new Handle<>(volSurface);

            final YoYInflationCouponPricer pricer;
            switch (whichPricer) {
                case 0 -> pricer = new BlackYoYInflationCouponPricer(volH, nominalTS);
                case 1 -> pricer = new UnitDisplacedBlackYoYInflationCouponPricer(volH, nominalTS);
                case 2 -> pricer = new BachelierYoYInflationCouponPricer(volH, nominalTS);
                default -> throw new IllegalArgumentException("unknown pricer index: " + whichPricer);
            }

            final Date endDate = calendar.advance(startDate, length, TimeUnit.Years,
                    BusinessDayConvention.Unadjusted, false);
            final Schedule schedule = new Schedule(startDate, endDate,
                    new Period(frequency), calendar,
                    BusinessDayConvention.Unadjusted,
                    BusinessDayConvention.Unadjusted,
                    DateGeneration.Rule.Forward, false);

            final Leg leg = buildYoYLeg(schedule, gearing, spread, caps, floors);

            // Set the chosen pricer on every YoY coupon. Capped/floored wrappers
            // get the narrower setPricer overload so the pricer also propagates
            // to the underlying YoY coupon (mirrors C++ setCouponPricer).
            for (final CashFlow cf : leg) {
                if (cf instanceof CappedFlooredYoYInflationCoupon cfyc) {
                    cfyc.setPricer(pricer);
                } else if (cf instanceof YoYInflationCoupon yc) {
                    yc.setPricer(pricer);
                }
            }
            return leg;
        }

        /**
         * Builds a YoY leg given an existing schedule, gearing, spread, caps
         * and floors. Mirrors the C++ {@code yoyInflationLeg(...)} fluent
         * builder for the small surface used by these two tests (no helper
         * class exists yet in JQuantLib for yoyInflationLeg).
         */
        private Leg buildYoYLeg(final Schedule schedule, final double gearing,
                                 final double spread, final double[] caps,
                                 final double[] floors) {
            final Leg leg = new Leg();
            final boolean hasCaps = caps != null && caps.length > 0;
            final boolean hasFloors = floors != null && floors.length > 0;
            for (int i = 0; i < schedule.size() - 1; i++) {
                final Date start = schedule.date(i);
                final Date end = schedule.date(i + 1);
                final Date paymentDate = calendar.adjust(end, convention);

                final YoYInflationCoupon underlying = new YoYInflationCoupon(
                        nominals[0], paymentDate, start, end, fixingDays, iir,
                        observationLag, CPI.InterpolationType.Flat, dc,
                        gearing, spread, start, end);

                if (hasCaps || hasFloors) {
                    final double cap = hasCaps && i < caps.length ? caps[i]
                            : (hasCaps ? caps[caps.length - 1] : Constants.NULL_REAL);
                    final double floor = hasFloors && i < floors.length ? floors[i]
                            : (hasFloors ? floors[floors.length - 1] : Constants.NULL_REAL);
                    final var capped = new CappedFlooredYoYInflationCoupon(
                            underlying, cap, floor);
                    leg.add(capped);
                } else {
                    leg.add(underlying);
                }
            }
            return leg;
        }

        PricingEngine makeEngine(final double vol, final int whichPricer) {
            final var volSurface = new ConstantYoYOptionletVolatility(vol,
                    settlementDays, calendar, convention, dc, observationLag,
                    frequency, iir.interpolated());
            final Handle<YoYOptionletVolatilitySurface> volH = new Handle<>(volSurface);

            return switch (whichPricer) {
                case 0 -> new YoYInflationBlackCapFloorEngine(iir, volH, nominalTS);
                case 1 -> new YoYInflationUnitDisplacedBlackCapFloorEngine(iir, volH, nominalTS);
                case 2 -> new YoYInflationBachelierCapFloorEngine(iir, volH, nominalTS);
                default -> throw new IllegalArgumentException("unknown engine: " + whichPricer);
            };
        }

        YoYInflationCapFloor makeYoYCapFloor(final YoYInflationCapFloor.Type type,
                                              final Leg leg, final double strike,
                                              final double vol, final int whichPricer) {
            final YoYInflationCapFloor result = switch (type) {
                case Cap -> new YoYInflationCap(leg, List.of(strike));
                case Floor -> new YoYInflationFloor(leg, List.of(strike));
                default -> throw new IllegalArgumentException("unknown YoYInflation cap/floor type");
            };
            result.setPricingEngine(makeEngine(vol, whichPricer));
            return result;
        }
    }

    // ===== testDecomposition =====
    @Test
    public void testDecomposition() {
        final CommonVars vars = new CommonVars();

        final double tolerance = 1e-10;
        final double floorstrike = 0.05;
        final double capstrike = 0.10;
        final double[] caps = filled(vars.length, capstrike);
        final double[] caps0 = new double[0];
        final double[] floors = filled(vars.length, floorstrike);
        final double[] floors0 = new double[0];
        final double gearing_p = 0.5;
        final double spread_p = 0.002;
        final double gearing_n = -1.5;
        final double spread_n = 0.12;

        // Build vanilla legs
        final Leg fixedLeg = vars.makeFixedLeg(vars.startDate, vars.length);
        final Leg floatLeg = vars.makeYoYLeg(vars.startDate, vars.length);
        final Leg floatLeg_p = vars.makeYoYLeg(vars.startDate, vars.length, gearing_p, spread_p);
        final Leg floatLeg_n = vars.makeYoYLeg(vars.startDate, vars.length, gearing_n, spread_n);

        // Attach default YoY pricer to "vanilla" leg coupons so swap pricing
        // works (no caps/floors but coupons still need a pricer).
        final YoYInflationCouponPricer defaultYoYPricer = new YoYInflationCouponPricer();
        attachDefaultPricer(floatLeg, defaultYoYPricer);
        attachDefaultPricer(floatLeg_p, defaultYoYPricer);
        attachDefaultPricer(floatLeg_n, defaultYoYPricer);

        final Swap vanillaLeg = new Swap(fixedLeg, floatLeg);
        final Swap vanillaLeg_p = new Swap(fixedLeg, floatLeg_p);
        final Swap vanillaLeg_n = new Swap(fixedLeg, floatLeg_n);

        final PricingEngine engine = new DiscountingSwapEngine(vars.nominalTS);
        vanillaLeg.setPricingEngine(engine);
        vanillaLeg_p.setPricingEngine(engine);
        vanillaLeg_n.setPricingEngine(engine);

        final int whichPricer = 0;

        // Case gearing = 1 and spread = 0
        Leg cappedLeg = vars.makeYoYCapFlooredLeg(whichPricer, vars.startDate, vars.length,
                caps, floors0, vars.volatility);
        Swap capLeg = new Swap(fixedLeg, cappedLeg);
        capLeg.setPricingEngine(engine);
        YoYInflationCap cap = new YoYInflationCap(floatLeg, List.of(capstrike));
        cap.setPricingEngine(vars.makeEngine(vars.volatility, whichPricer));
        double npvVanilla = vanillaLeg.NPV();
        double npvCappedLeg = capLeg.NPV();
        double npvCap = cap.NPV();
        double error = Math.abs(npvCappedLeg - (npvVanilla - npvCap));
        if (error > tolerance) {
            fail("YoY Capped Leg (g=1, s=0%): diff=" + error
                    + ", cappedLeg=" + npvCappedLeg
                    + ", vanilla-cap=" + (npvVanilla - npvCap));
        }

        // FLOORED gearing=1, spread=0
        Leg flooredLeg = vars.makeYoYCapFlooredLeg(whichPricer, vars.startDate, vars.length,
                caps0, floors, vars.volatility);
        Swap floorLeg = new Swap(fixedLeg, flooredLeg);
        floorLeg.setPricingEngine(engine);
        YoYInflationFloor floor = new YoYInflationFloor(floatLeg, List.of(floorstrike));
        floor.setPricingEngine(vars.makeEngine(vars.volatility, whichPricer));
        double npvFlooredLeg = floorLeg.NPV();
        double npvFloor = floor.NPV();
        error = Math.abs(npvFlooredLeg - (npvVanilla + npvFloor));
        if (error > tolerance) {
            fail("YoY Floored Leg (g=1, s=0%): diff=" + error);
        }

        // COLLARED gearing=1, spread=0
        Leg collaredLeg = vars.makeYoYCapFlooredLeg(whichPricer, vars.startDate, vars.length,
                caps, floors, vars.volatility);
        Swap collarLeg = new Swap(fixedLeg, collaredLeg);
        collarLeg.setPricingEngine(engine);
        YoYInflationCollar collar = new YoYInflationCollar(floatLeg,
                List.of(capstrike), List.of(floorstrike));
        collar.setPricingEngine(vars.makeEngine(vars.volatility, whichPricer));
        double npvCollaredLeg = collarLeg.NPV();
        double npvCollar = collar.NPV();
        error = Math.abs(npvCollaredLeg - (npvVanilla - npvCollar));
        if (error > tolerance) {
            fail("YoY Collared Leg (g=1, s=0%): diff=" + error);
        }

        // Positive gearing capped
        Leg cappedLeg_p = vars.makeYoYCapFlooredLeg(whichPricer, vars.startDate, vars.length,
                caps, floors0, vars.volatility, gearing_p, spread_p);
        Swap capLeg_p = new Swap(fixedLeg, cappedLeg_p);
        capLeg_p.setPricingEngine(engine);
        YoYInflationCap cap_p = new YoYInflationCap(floatLeg_p, List.of(capstrike));
        cap_p.setPricingEngine(vars.makeEngine(vars.volatility, whichPricer));
        npvVanilla = vanillaLeg_p.NPV();
        npvCappedLeg = capLeg_p.NPV();
        npvCap = cap_p.NPV();
        error = Math.abs(npvCappedLeg - (npvVanilla - npvCap));
        if (error > tolerance) {
            fail("YoY Capped Leg (g=" + gearing_p + ", s=" + spread_p + "): diff=" + error);
        }

        // Negative gearing capped (becomes floor)
        Leg cappedLeg_n = vars.makeYoYCapFlooredLeg(whichPricer, vars.startDate, vars.length,
                caps, floors0, vars.volatility, gearing_n, spread_n);
        Swap capLeg_n = new Swap(fixedLeg, cappedLeg_n);
        capLeg_n.setPricingEngine(engine);
        YoYInflationFloor floor_n = new YoYInflationFloor(floatLeg,
                List.of((capstrike - spread_n) / gearing_n));
        floor_n.setPricingEngine(vars.makeEngine(vars.volatility, whichPricer));
        npvVanilla = vanillaLeg_n.NPV();
        npvCappedLeg = capLeg_n.NPV();
        npvFloor = floor_n.NPV();
        error = Math.abs(npvCappedLeg - (npvVanilla + gearing_n * npvFloor));
        if (error > tolerance) {
            fail("YoY Capped Leg (g=" + gearing_n + ", s=" + spread_n + "): diff=" + error);
        }

        // Positive gearing floored
        Leg flooredLeg_p1 = vars.makeYoYCapFlooredLeg(whichPricer, vars.startDate, vars.length,
                caps0, floors, vars.volatility, gearing_p, spread_p);
        Swap floorLeg_p1 = new Swap(fixedLeg, flooredLeg_p1);
        floorLeg_p1.setPricingEngine(engine);
        YoYInflationFloor floor_p1 = new YoYInflationFloor(floatLeg_p, List.of(floorstrike));
        floor_p1.setPricingEngine(vars.makeEngine(vars.volatility, whichPricer));
        npvVanilla = vanillaLeg_p.NPV();
        npvFlooredLeg = floorLeg_p1.NPV();
        npvFloor = floor_p1.NPV();
        error = Math.abs(npvFlooredLeg - (npvVanilla + npvFloor));
        if (error > tolerance) {
            fail("YoY Floored Leg (g=" + gearing_p + ", s=" + spread_p + "): diff=" + error);
        }

        // Negative gearing floored (becomes cap)
        Leg flooredLeg_n = vars.makeYoYCapFlooredLeg(whichPricer, vars.startDate, vars.length,
                caps0, floors, vars.volatility, gearing_n, spread_n);
        Swap floorLeg_n = new Swap(fixedLeg, flooredLeg_n);
        floorLeg_n.setPricingEngine(engine);
        YoYInflationCap cap_n = new YoYInflationCap(floatLeg,
                List.of((floorstrike - spread_n) / gearing_n));
        cap_n.setPricingEngine(vars.makeEngine(vars.volatility, whichPricer));
        npvVanilla = vanillaLeg_n.NPV();
        npvFlooredLeg = floorLeg_n.NPV();
        npvCap = cap_n.NPV();
        error = Math.abs(npvFlooredLeg - (npvVanilla - gearing_n * npvCap));
        if (error > tolerance) {
            fail("YoY Floored Leg (g=" + gearing_n + ", s=" + spread_n + "): diff=" + error);
        }

        // Positive gearing collared
        Leg collaredLeg_p = vars.makeYoYCapFlooredLeg(whichPricer, vars.startDate, vars.length,
                caps, floors, vars.volatility, gearing_p, spread_p);
        Swap collarLeg_p1 = new Swap(fixedLeg, collaredLeg_p);
        collarLeg_p1.setPricingEngine(engine);
        YoYInflationCollar collar_p = new YoYInflationCollar(floatLeg_p,
                List.of(capstrike), List.of(floorstrike));
        collar_p.setPricingEngine(vars.makeEngine(vars.volatility, whichPricer));
        npvVanilla = vanillaLeg_p.NPV();
        npvCollaredLeg = collarLeg_p1.NPV();
        npvCollar = collar_p.NPV();
        error = Math.abs(npvCollaredLeg - (npvVanilla - npvCollar));
        if (error > tolerance) {
            fail("YoY Collared Leg (g=" + gearing_p + "): diff=" + error);
        }

        // Negative gearing collared
        Leg collaredLeg_n = vars.makeYoYCapFlooredLeg(whichPricer, vars.startDate, vars.length,
                caps, floors, vars.volatility, gearing_n, spread_n);
        Swap collarLeg_n1 = new Swap(fixedLeg, collaredLeg_n);
        collarLeg_n1.setPricingEngine(engine);
        YoYInflationCollar collar_n = new YoYInflationCollar(floatLeg,
                List.of((floorstrike - spread_n) / gearing_n),
                List.of((capstrike - spread_n) / gearing_n));
        collar_n.setPricingEngine(vars.makeEngine(vars.volatility, whichPricer));
        npvVanilla = vanillaLeg_n.NPV();
        npvCollaredLeg = collarLeg_n1.NPV();
        npvCollar = collar_n.NPV();
        error = Math.abs(npvCollaredLeg - (npvVanilla - gearing_n * npvCollar));
        if (error > tolerance) {
            fail("YoY Collared Leg (g=" + gearing_n + "): diff=" + error);
        }
    }

    // ===== testInstrumentEquality =====
    @Test
    public void testInstrumentEquality() {
        final CommonVars vars = new CommonVars();

        final int[] lengths = { 1, 2, 3, 5, 7, 10, 15, 20 };
        final double[] strikes = { 0.01, 0.025, 0.029, 0.03, 0.031, 0.035, 0.07 };
        final double[] vols = { 0.001, 0.005, 0.010, 0.015, 0.020 };

        for (int whichPricer = 0; whichPricer < 3; whichPricer++) {
            for (final int length : lengths) {
                for (final double strike : strikes) {
                    for (final double vol : vols) {
                        final Leg leg = vars.makeYoYLeg(vars.evaluationDate, length);
                        // Attach default pricer so vanilla coupons price OK.
                        attachDefaultPricer(leg, new YoYInflationCouponPricer());

                        final YoYInflationCapFloor cap = vars.makeYoYCapFloor(
                                YoYInflationCapFloor.Type.Cap, leg, strike, vol, whichPricer);
                        final YoYInflationCapFloor floor = vars.makeYoYCapFloor(
                                YoYInflationCapFloor.Type.Floor, leg, strike, vol, whichPricer);

                        final Date from = vars.nominalTS.currentLink().referenceDate();
                        final Date to = from.add(new Period(length, TimeUnit.Years));
                        final Schedule yoySchedule = new MakeSchedule(from, to,
                                new Period(1, TimeUnit.Years), new UnitedKingdom(),
                                BusinessDayConvention.Unadjusted)
                                .withTerminationDateConvention(BusinessDayConvention.Unadjusted)
                                .backwards()
                                .schedule();

                        final YearOnYearInflationSwap swap = new YearOnYearInflationSwap(
                                YearOnYearInflationSwap.Type.Payer,
                                1_000_000.0,
                                yoySchedule, 0.0, vars.dc,
                                yoySchedule, vars.iir, vars.observationLag,
                                CPI.InterpolationType.Flat,
                                0.0, vars.dc, new UnitedKingdom());

                        final PricingEngine sppe = new DiscountingSwapEngine(vars.nominalTS);
                        swap.setPricingEngine(sppe);

                        final double[] capStrikes = filled(length, strike);
                        final double[] noStrikes = new double[0];
                        final Leg leg2 = vars.makeYoYCapFlooredLeg(whichPricer, from, length,
                                capStrikes, noStrikes, vol, 1.0, 0.0);
                        final Leg leg3 = vars.makeYoYCapFlooredLeg(whichPricer, from, length,
                                noStrikes, capStrikes, vol, 1.0, 0.0);

                        final double capped = CashFlows.npv(leg2,
                                vars.nominalTS.currentLink(), false, null, null);
                        if (Math.abs(capped - (swap.NPV() - cap.NPV())) > 1.0e-6) {
                            fail(String.format("capped coupon != swap(0) - cap:%n"
                                            + "  length=%d  vol=%.6f  strike=%.6f%n"
                                            + "  cap=%.6f  swap=%.6f  capped=%.6f",
                                    length, vol, strike, cap.NPV(), swap.NPV(), capped));
                        }
                        final double floored = CashFlows.npv(leg3,
                                vars.nominalTS.currentLink(), false, null, null);
                        if (Math.abs(floored - (swap.NPV() + floor.NPV())) > 1.0e-6) {
                            fail(String.format("floored coupon != swap(0) + floor:%n"
                                            + "  length=%d  vol=%.6f  strike=%.6f%n"
                                            + "  floor=%.6f  swap=%.6f  floored=%.6f",
                                    length, vol, strike, floor.NPV(), swap.NPV(), floored));
                        }
                    }
                }
            }
        }
    }

    // ===== utilities =====
    private static double[] filled(final int n, final double v) {
        final double[] out = new double[n];
        Arrays.fill(out, v);
        return out;
    }

    private static void attachDefaultPricer(final Leg leg, final YoYInflationCouponPricer pricer) {
        for (final CashFlow cf : leg) {
            if (cf instanceof CappedFlooredYoYInflationCoupon cfyc) {
                cfyc.setPricer(pricer);
            } else if (cf instanceof YoYInflationCoupon yc) {
                yc.setPricer(pricer);
            }
        }
    }
}
