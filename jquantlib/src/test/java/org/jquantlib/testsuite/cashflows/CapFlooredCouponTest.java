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

package org.jquantlib.testsuite.cashflows;

import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.cashflow.BlackIborCouponPricer;
import org.jquantlib.cashflow.FixedRateLeg;
import org.jquantlib.cashflow.IborCouponPricer;
import org.jquantlib.cashflow.IborLeg;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.cashflow.PricerSetter;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.Thirty360;
import org.jquantlib.indexes.Euribor1Y;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.instruments.CapFloor;
import org.jquantlib.instruments.Swap;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.pricingengines.capfloor.BlackCapFloorEngine;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.optionlet.ConstantOptionletVolatility;
import org.jquantlib.termstructures.volatilities.optionlet.OptionletVolatilityStructure;
import org.jquantlib.testsuite.util.Utilities;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/capflooredcoupon.cpp (Phase 5e).
 *
 * <p>Mirrors {@code BOOST_AUTO_TEST_CASE(testLargeRates)} and
 * {@code BOOST_AUTO_TEST_CASE(testDecomposition)} from
 * {@code test-suite/capflooredcoupon.cpp}.  Both exercise the
 * cap/floored IBOR leg pricing pipeline via
 * {@link PricerSetter#setCouponPricer(Leg, org.jquantlib.cashflow.FloatingRateCouponPricer)}
 * wiring through the {@link BlackIborCouponPricer}.  Body-filled in
 * Phase 5e.5b-CFC-d-31 after the {@code CappedFlooredIborCouponVisitor}
 * was wired into {@link PricerSetter}.
 */
public class CapFlooredCouponTest {

    public CapFlooredCouponTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    /** Mirror of C++ {@code CommonVars} (capflooredcoupon.cpp:47-185). */
    private static final class CommonVars {
        final Date today;
        final Date settlement;
        final Date startDate;
        final Calendar calendar;
        final double nominal;
        final double[] nominals;
        final BusinessDayConvention convention;
        final Frequency frequency;
        final IborIndex index;
        final int fixingDays;
        final RelinkableHandle<YieldTermStructure> termStructure;
        final int length;
        final double volatility;

        CommonVars() {
            this.length = 20;
            this.volatility = 0.20;
            this.nominal = 100.0;
            this.nominals = new double[length];
            Arrays.fill(this.nominals, nominal);
            this.frequency = Frequency.Annual;
            this.termStructure = new RelinkableHandle<YieldTermStructure>();
            this.index = new Euribor1Y(termStructure);
            this.calendar = index.fixingCalendar();
            this.convention = BusinessDayConvention.ModifiedFollowing;
            this.today = calendar.adjust(Date.todaysDate());
            new Settings().setEvaluationDate(today);
            final int settlementDays = 2;
            this.fixingDays = 2;
            this.settlement = calendar.advance(today, settlementDays, TimeUnit.Days);
            this.startDate = settlement;
            this.termStructure.linkTo(Utilities.flatRate(settlement, 0.05,
                    new ActualActual(ActualActual.Convention.ISDA)));
        }

        Leg makeFixedLeg(final Date startDate, final int length) {
            final Date endDate = calendar.advance(startDate,
                    new Period(length, TimeUnit.Years), convention);
            final Schedule schedule = new Schedule(startDate, endDate,
                    new Period(frequency), calendar,
                    convention, convention,
                    DateGeneration.Rule.Forward, false);
            final double[] coupons = new double[length];
            // Arrays.fill(0.0) is the implicit default; redundant but explicit.
            Arrays.fill(coupons, 0.0);
            return new FixedRateLeg(schedule, new Thirty360(Thirty360.Convention.BondBasis))
                    .withNotionals(nominals)
                    .withCouponRates(coupons)
                    .Leg();
        }

        Leg makeFloatingLeg(final Date startDate, final int length) {
            return makeFloatingLeg(startDate, length, 1.0, 0.0);
        }

        Leg makeFloatingLeg(final Date startDate, final int length,
                            final double gearing, final double spread) {
            final Date endDate = calendar.advance(startDate,
                    new Period(length, TimeUnit.Years), convention);
            final Schedule schedule = new Schedule(startDate, endDate,
                    new Period(frequency), calendar,
                    convention, convention,
                    DateGeneration.Rule.Forward, false);
            final double[] gearingVector = new double[length];
            Arrays.fill(gearingVector, gearing);
            final double[] spreadVector = new double[length];
            Arrays.fill(spreadVector, spread);
            return new IborLeg(schedule, index)
                    .withNotionals(new Array(nominals))
                    .withPaymentDayCounter(index.dayCounter())
                    .withPaymentAdjustment(convention)
                    .withFixingDays(fixingDays)
                    .withGearings(new Array(gearingVector))
                    .withSpreads(new Array(spreadVector))
                    .Leg();
        }

        Leg makeCapFlooredLeg(final Date startDate, final int length,
                              final double[] caps, final double[] floors,
                              final double volatility) {
            return makeCapFlooredLeg(startDate, length, caps, floors,
                                     volatility, 1.0, 0.0);
        }

        Leg makeCapFlooredLeg(final Date startDate, final int length,
                              final double[] caps, final double[] floors,
                              final double volatility,
                              final double gearing, final double spread) {
            final Date endDate = calendar.advance(startDate,
                    new Period(length, TimeUnit.Years), convention);
            final Schedule schedule = new Schedule(startDate, endDate,
                    new Period(frequency), calendar,
                    convention, convention,
                    DateGeneration.Rule.Forward, false);
            final Handle<OptionletVolatilityStructure> vol =
                    new Handle<OptionletVolatilityStructure>(
                            new ConstantOptionletVolatility(0, calendar,
                                    BusinessDayConvention.Following,
                                    volatility, new Actual365Fixed()));
            final IborCouponPricer pricer = new BlackIborCouponPricer(vol);
            final double[] gearingVector = new double[length];
            Arrays.fill(gearingVector, gearing);
            final double[] spreadVector = new double[length];
            Arrays.fill(spreadVector, spread);

            final IborLeg builder = new IborLeg(schedule, index)
                    .withNotionals(new Array(nominals))
                    .withPaymentDayCounter(index.dayCounter())
                    .withPaymentAdjustment(convention)
                    .withFixingDays(fixingDays)
                    .withGearings(new Array(gearingVector))
                    .withSpreads(new Array(spreadVector));
            // C++ unconditionally passes the cap/floor vectors (may be empty);
            // mirror via Array(0) when an input array is zero-length so the
            // FloatingLeg builder takes the noOption() branch.
            if (caps.length > 0) {
                builder.withCaps(new Array(caps));
            }
            if (floors.length > 0) {
                builder.withFloors(new Array(floors));
            }
            final Leg iborLeg = builder.Leg();
            PricerSetter.setCouponPricer(iborLeg, pricer);
            return iborLeg;
        }

        BlackCapFloorEngine makeEngine(final double volatility) {
            final Handle<Quote> vol = new Handle<Quote>(new SimpleQuote(volatility));
            return new BlackCapFloorEngine(termStructure, vol,
                    new Actual365Fixed());
        }

        CapFloor makeCapFloor(final CapFloor.Type type, final Leg leg,
                              final double capStrike, final double floorStrike,
                              final double volatility) {
            final CapFloor result;
            switch (type) {
              case Cap:
                result = new CapFloor(CapFloor.Type.Cap, leg,
                        toList(capStrike), termStructure, null);
                break;
              case Floor:
                result = new CapFloor(CapFloor.Type.Floor, leg,
                        toList(floorStrike), termStructure, null);
                break;
              case Collar:
                result = new CapFloor(CapFloor.Type.Collar, leg,
                        toList(capStrike), toList(floorStrike),
                        termStructure, null);
                break;
              default:
                throw new IllegalArgumentException("unknown cap/floor type");
            }
            result.setPricingEngine(makeEngine(volatility));
            return result;
        }

        private static List<Double> toList(final double v) {
            return new ArrayList<>(Arrays.asList(Double.valueOf(v)));
        }
    }

    /**
     * Mirror of {@code testLargeRates} (capflooredcoupon.cpp:188-230).
     *
     * <p>A vanilla floating leg and a capped floating leg with strike
     * equal to 100 and floor equal to 0 must have (about) the same NPV
     * (depending on variance: option expiry and volatility).
     */
    @Test
    public void testLargeRates() {
        QL.info("Testing degenerate collared coupon...");

        final CommonVars vars = new CommonVars();

        final double[] caps = new double[vars.length];
        Arrays.fill(caps, 100.0);
        final double[] floors = new double[vars.length];
        Arrays.fill(floors, 0.0);
        // C++ tolerance 1e-10 (capflooredcoupon.cpp:201).
        final double tolerance = 1e-10;

        final Leg fixedLeg = vars.makeFixedLeg(vars.startDate, vars.length);
        final Leg floatLeg = vars.makeFloatingLeg(vars.startDate, vars.length);
        final Leg collaredLeg = vars.makeCapFlooredLeg(vars.startDate,
                vars.length, caps, floors, vars.volatility);

        final DiscountingSwapEngine engine =
                new DiscountingSwapEngine(vars.termStructure);
        final Swap vanillaLeg = new Swap(fixedLeg, floatLeg);
        final Swap collarLeg = new Swap(fixedLeg, collaredLeg);
        vanillaLeg.setPricingEngine(engine);
        collarLeg.setPricingEngine(engine);

        final double diff = Math.abs(vanillaLeg.NPV() - collarLeg.NPV());
        if (diff > tolerance) {
            fail("Length: " + vars.length + " y\n"
                    + "Volatility: " + (vars.volatility * 100) + "%\n"
                    + "Notional: " + vars.nominal + "\n"
                    + "Vanilla floating leg NPV: " + vanillaLeg.NPV() + "\n"
                    + "Collared floating leg NPV (strikes 0 and 100): "
                    + collarLeg.NPV() + "\n"
                    + "Diff: " + diff);
        }
    }

    /**
     * Mirror of {@code testDecomposition} (capflooredcoupon.cpp:232-540).
     *
     * <p>Verifies the cap/floor/collar decomposition identities for
     * gearing=1/spread=0, positive gearing+spread, and negative
     * gearing+spread.
     */
    @Test
    public void testDecomposition() {
        QL.info("Testing collared coupon against its decomposition...");

        final CommonVars vars = new CommonVars();

        // C++ tolerance 1e-12 (capflooredcoupon.cpp:238).
        final double tolerance = 1e-12;
        final double floorstrike = 0.05;
        final double capstrike = 0.10;
        final double[] caps = new double[vars.length];
        Arrays.fill(caps, capstrike);
        final double[] caps0 = new double[0];
        final double[] floors = new double[vars.length];
        Arrays.fill(floors, floorstrike);
        final double[] floors0 = new double[0];
        final double gearing_p = 0.5;
        final double spread_p = 0.002;
        final double gearing_n = -1.5;
        final double spread_n = 0.12;

        final Leg fixedLeg = vars.makeFixedLeg(vars.startDate, vars.length);
        final Leg floatLeg = vars.makeFloatingLeg(vars.startDate, vars.length);
        final Leg floatLeg_p = vars.makeFloatingLeg(vars.startDate,
                vars.length, gearing_p, spread_p);
        final Leg floatLeg_n = vars.makeFloatingLeg(vars.startDate,
                vars.length, gearing_n, spread_n);
        final Swap vanillaLeg = new Swap(fixedLeg, floatLeg);
        final Swap vanillaLeg_p = new Swap(fixedLeg, floatLeg_p);
        final Swap vanillaLeg_n = new Swap(fixedLeg, floatLeg_n);

        final DiscountingSwapEngine engine =
                new DiscountingSwapEngine(vars.termStructure);
        vanillaLeg.setPricingEngine(engine);
        vanillaLeg_p.setPricingEngine(engine);
        vanillaLeg_n.setPricingEngine(engine);

        double npvVanilla;
        double npvCappedLeg;
        double npvFlooredLeg;
        double npvCollaredLeg;
        double npvCap;
        double npvFloor;
        double npvCollar;
        double error;

        // ============================================================
        // Case gearing = 1 and spread = 0 — Capped leg = Vanilla - Cap.
        // ============================================================
        final Leg cappedLeg = vars.makeCapFlooredLeg(vars.startDate,
                vars.length, caps, floors0, vars.volatility);
        final Swap capLeg = new Swap(fixedLeg, cappedLeg);
        capLeg.setPricingEngine(engine);
        final CapFloor cap = vars.makeCapFloor(CapFloor.Type.Cap, floatLeg,
                capstrike, 0.0, vars.volatility);
        npvVanilla = vanillaLeg.NPV();
        npvCappedLeg = capLeg.NPV();
        npvCap = cap.NPV();
        error = Math.abs(npvCappedLeg - (npvVanilla - npvCap));
        if (error > tolerance) {
            fail("\nCapped Leg: gearing=1, spread=0%, strike="
                    + (capstrike * 100) + "%\n"
                    + "  Capped Floating Leg NPV: " + npvCappedLeg + "\n"
                    + "  Floating Leg NPV - Cap NPV: " + (npvVanilla - npvCap)
                    + "\n  Diff: " + error);
        }

        // ============================================================
        // gearing = 1, spread = 0 — Floored leg = Vanilla + Floor.
        // ============================================================
        final Leg flooredLeg = vars.makeCapFlooredLeg(vars.startDate,
                vars.length, caps0, floors, vars.volatility);
        final Swap floorLeg = new Swap(fixedLeg, flooredLeg);
        floorLeg.setPricingEngine(engine);
        final CapFloor floor = vars.makeCapFloor(CapFloor.Type.Floor, floatLeg,
                0.0, floorstrike, vars.volatility);
        npvFlooredLeg = floorLeg.NPV();
        npvFloor = floor.NPV();
        error = Math.abs(npvFlooredLeg - (npvVanilla + npvFloor));
        if (error > tolerance) {
            fail("Floored Leg: gearing=1, spread=0%, strike="
                    + (floorstrike * 100) + "%\n"
                    + "  Floored Floating Leg NPV: " + npvFlooredLeg + "\n"
                    + "  Floating Leg NPV + Floor NPV: " + (npvVanilla + npvFloor)
                    + "\n  Diff: " + error);
        }

        // ============================================================
        // gearing = 1, spread = 0 — Collared leg = Vanilla - Collar.
        // ============================================================
        final Leg collaredLeg = vars.makeCapFlooredLeg(vars.startDate,
                vars.length, caps, floors, vars.volatility);
        final Swap collarLeg = new Swap(fixedLeg, collaredLeg);
        collarLeg.setPricingEngine(engine);
        final CapFloor collar = vars.makeCapFloor(CapFloor.Type.Collar, floatLeg,
                capstrike, floorstrike, vars.volatility);
        npvCollaredLeg = collarLeg.NPV();
        npvCollar = collar.NPV();
        error = Math.abs(npvCollaredLeg - (npvVanilla - npvCollar));
        if (error > tolerance) {
            fail("\nCollared Leg: gearing=1, spread=0%, strike="
                    + (floorstrike * 100) + "% and " + (capstrike * 100) + "%\n"
                    + "  Collared Floating Leg NPV: " + npvCollaredLeg + "\n"
                    + "  Floating Leg NPV - Collar NPV: " + (npvVanilla - npvCollar)
                    + "\n  Diff: " + error);
        }

        // ============================================================
        // Positive gearing — Capped leg = Vanilla - Cap.
        // ============================================================
        final Leg cappedLeg_p = vars.makeCapFlooredLeg(vars.startDate,
                vars.length, caps, floors0, vars.volatility, gearing_p, spread_p);
        final Swap capLeg_p = new Swap(fixedLeg, cappedLeg_p);
        capLeg_p.setPricingEngine(engine);
        final CapFloor cap_p = vars.makeCapFloor(CapFloor.Type.Cap, floatLeg_p,
                capstrike, 0.0, vars.volatility);
        npvVanilla = vanillaLeg_p.NPV();
        npvCappedLeg = capLeg_p.NPV();
        npvCap = cap_p.NPV();
        error = Math.abs(npvCappedLeg - (npvVanilla - npvCap));
        if (error > tolerance) {
            fail("\nCapped Leg: gearing=" + gearing_p + ", spread= "
                    + (spread_p * 100) + "%, strike=" + (capstrike * 100) + "%, "
                    + "effective strike= " + ((capstrike - spread_p) / gearing_p * 100)
                    + "%\n  Capped Floating Leg NPV: " + npvCappedLeg + "\n"
                    + "  Vanilla Leg NPV: " + npvVanilla + "\n"
                    + "  Cap NPV: " + npvCap + "\n"
                    + "  Floating Leg NPV - Cap NPV: " + (npvVanilla - npvCap)
                    + "\n  Diff: " + error);
        }

        // ============================================================
        // Negative gearing — Capped leg = Vanilla + gearing_n * Floor.
        // ============================================================
        final Leg cappedLeg_n = vars.makeCapFlooredLeg(vars.startDate,
                vars.length, caps, floors0, vars.volatility, gearing_n, spread_n);
        final Swap capLeg_n = new Swap(fixedLeg, cappedLeg_n);
        capLeg_n.setPricingEngine(engine);
        final CapFloor floor_n = vars.makeCapFloor(CapFloor.Type.Floor, floatLeg,
                0.0, (capstrike - spread_n) / gearing_n, vars.volatility);
        npvVanilla = vanillaLeg_n.NPV();
        npvCappedLeg = capLeg_n.NPV();
        npvFloor = floor_n.NPV();
        error = Math.abs(npvCappedLeg - (npvVanilla + gearing_n * npvFloor));
        if (error > tolerance) {
            fail("\nCapped Leg: gearing=" + gearing_n + ", spread= "
                    + (spread_n * 100) + "%, strike=" + (capstrike * 100) + "%, "
                    + "effective strike= " + ((capstrike - spread_n) / gearing_n * 100)
                    + "%\n  Capped Floating Leg NPV: " + npvCappedLeg + "\n"
                    + "  npv Vanilla: " + npvVanilla + "\n"
                    + "  npvFloor: " + npvFloor + "\n"
                    + "  Floating Leg NPV - Cap NPV: " + (npvVanilla + gearing_n * npvFloor)
                    + "\n  Diff: " + error);
        }

        // ============================================================
        // Positive gearing — Floored leg = Vanilla + Floor.
        // ============================================================
        final Leg flooredLeg_p1 = vars.makeCapFlooredLeg(vars.startDate,
                vars.length, caps0, floors, vars.volatility, gearing_p, spread_p);
        final Swap floorLeg_p1 = new Swap(fixedLeg, flooredLeg_p1);
        floorLeg_p1.setPricingEngine(engine);
        final CapFloor floor_p1 = vars.makeCapFloor(CapFloor.Type.Floor, floatLeg_p,
                0.0, floorstrike, vars.volatility);
        npvVanilla = vanillaLeg_p.NPV();
        npvFlooredLeg = floorLeg_p1.NPV();
        npvFloor = floor_p1.NPV();
        error = Math.abs(npvFlooredLeg - (npvVanilla + npvFloor));
        if (error > tolerance) {
            fail("\nFloored Leg: gearing=" + gearing_p + ", spread= "
                    + (spread_p * 100) + "%, strike=" + (floorstrike * 100) + "%, "
                    + "effective strike= " + ((floorstrike - spread_p) / gearing_p * 100)
                    + "%\n  Floored Floating Leg NPV: " + npvFlooredLeg + "\n"
                    + "  Floating Leg NPV + Floor NPV: " + (npvVanilla + npvFloor)
                    + "\n  Diff: " + error);
        }

        // ============================================================
        // Negative gearing — Floored leg = Vanilla - gearing_n * Cap.
        // ============================================================
        final Leg flooredLeg_n = vars.makeCapFlooredLeg(vars.startDate,
                vars.length, caps0, floors, vars.volatility, gearing_n, spread_n);
        final Swap floorLeg_n = new Swap(fixedLeg, flooredLeg_n);
        floorLeg_n.setPricingEngine(engine);
        final CapFloor cap_n = vars.makeCapFloor(CapFloor.Type.Cap, floatLeg,
                (floorstrike - spread_n) / gearing_n, 0.0, vars.volatility);
        npvVanilla = vanillaLeg_n.NPV();
        npvFlooredLeg = floorLeg_n.NPV();
        npvCap = cap_n.NPV();
        error = Math.abs(npvFlooredLeg - (npvVanilla - gearing_n * npvCap));
        if (error > tolerance) {
            fail("\nCapped Leg: gearing=" + gearing_n + ", spread= "
                    + (spread_n * 100) + "%, strike=" + (floorstrike * 100) + "%, "
                    + "effective strike= " + ((floorstrike - spread_n) / gearing_n * 100)
                    + "%\n  Capped Floating Leg NPV: " + npvFlooredLeg + "\n"
                    + "  Floating Leg NPV - Cap NPV: " + (npvVanilla - gearing_n * npvCap)
                    + "\n  Diff: " + error);
        }

        // ============================================================
        // Positive gearing — Collared leg = Vanilla - Collar.
        // ============================================================
        final Leg collaredLeg_p = vars.makeCapFlooredLeg(vars.startDate,
                vars.length, caps, floors, vars.volatility, gearing_p, spread_p);
        final Swap collarLeg_p1 = new Swap(fixedLeg, collaredLeg_p);
        collarLeg_p1.setPricingEngine(engine);
        final CapFloor collar_p = vars.makeCapFloor(CapFloor.Type.Collar, floatLeg_p,
                capstrike, floorstrike, vars.volatility);
        npvVanilla = vanillaLeg_p.NPV();
        npvCollaredLeg = collarLeg_p1.NPV();
        npvCollar = collar_p.NPV();
        error = Math.abs(npvCollaredLeg - (npvVanilla - npvCollar));
        if (error > tolerance) {
            fail("\nCollared Leg: gearing=" + gearing_p + ", spread= "
                    + (spread_p * 100) + "%, strike=" + (floorstrike * 100) + "% and "
                    + (capstrike * 100) + "%, effective strike="
                    + ((floorstrike - spread_p) / gearing_p * 100) + "% and "
                    + ((capstrike - spread_p) / gearing_p * 100) + "%\n"
                    + "  Collared Floating Leg NPV: " + npvCollaredLeg + "\n"
                    + "  Floating Leg NPV - Collar NPV: " + (npvVanilla - npvCollar)
                    + "\n  Diff: " + error);
        }

        // ============================================================
        // Negative gearing — Collared leg = Vanilla - gearing_n * Collar.
        // ============================================================
        final Leg collaredLeg_n = vars.makeCapFlooredLeg(vars.startDate,
                vars.length, caps, floors, vars.volatility, gearing_n, spread_n);
        final Swap collarLeg_n1 = new Swap(fixedLeg, collaredLeg_n);
        collarLeg_n1.setPricingEngine(engine);
        // Negative-gearing collar swaps cap/floor strike inputs (C++:
        // capflooredcoupon.cpp:518-520). The reference collar's capStrike
        // is (floorstrike-spread_n)/gearing_n and floorStrike is
        // (capstrike-spread_n)/gearing_n.
        final CapFloor collar_n = vars.makeCapFloor(CapFloor.Type.Collar, floatLeg,
                (floorstrike - spread_n) / gearing_n,
                (capstrike - spread_n) / gearing_n, vars.volatility);
        npvVanilla = vanillaLeg_n.NPV();
        npvCollaredLeg = collarLeg_n1.NPV();
        npvCollar = collar_n.NPV();
        error = Math.abs(npvCollaredLeg - (npvVanilla - gearing_n * npvCollar));
        if (error > tolerance) {
            fail("\nCollared Leg: gearing=" + gearing_n + ", spread= "
                    + (spread_n * 100) + "%, strike=" + (floorstrike * 100) + "% and "
                    + (capstrike * 100) + "%, effective strike="
                    + ((floorstrike - spread_n) / gearing_n * 100) + "% and "
                    + ((capstrike - spread_n) / gearing_n * 100) + "%\n"
                    + "  Collared Floating Leg NPV: " + npvCollaredLeg + "\n"
                    + "  Floating Leg NPV - Collar NPV: "
                    + (npvVanilla - gearing_n * npvCollar) + "\n  Diff: " + error);
        }
    }
}
