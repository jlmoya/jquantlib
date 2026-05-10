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

package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.cashflow.BlackIborCouponPricer;
import org.jquantlib.cashflow.FixedRateLeg;
import org.jquantlib.cashflow.IborLeg;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.cashflow.PricerSetter;
import org.jquantlib.currencies.Europe.EURCurrency;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.daycounters.SimpleDayCounter;
import org.jquantlib.daycounters.Thirty360;
import org.jquantlib.indexes.Euribor;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.instruments.Swap;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.optionlet.ConstantOptionletVolatility;
import org.jquantlib.termstructures.volatilities.optionlet.OptionletVolatilityStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.testsuite.util.Utilities;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.NullCalendar;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/swap.cpp (Phase 5e).
 *
 * <p>10 BOOST_AUTO_TEST_CASE methods exercising vanilla-swap pricing
 * via {@link org.jquantlib.instruments.VanillaSwap} and
 * {@link org.jquantlib.pricingengines.swap.DiscountingSwapEngine}.
 *
 * <h3>Phase 5e.5 carry-forward rationale</h3>
 *
 * <p>All methods are present as skeleton {@code @Test} stubs to mirror the
 * C++ test-suite topology. They are marked {@code @Ignore} pending the
 * following production-side prereqs:
 *
 * <ul>
 *   <li>{@code testFairRate}, {@code testFairSpread}, {@code testRateDependency},
 *       {@code testSpreadDependency}: existing JQuant {@link
 *       org.jquantlib.instruments.VanillaSwap} supports these, but the
 *       {@code makeSwap(length, fixedRate, spread, rule)} convenience that
 *       the C++ {@code CommonVars} fixture leans on differs from
 *       {@link org.jquantlib.instruments.MakeVanillaSwap}; need a
 *       {@code MakeVanillaSwap.withRule(DateGeneration.Rule)} overload to
 *       fully reproduce the C++ schedule generation. See WI-5e.5-SWAP-1.</li>
 *
 *   <li>{@code testInArrears}: requires
 *       {@code IborLeg.inArrears()} builder flag and
 *       {@code BlackIborCouponPricer} + {@code ConstantOptionletVolatility}
 *       to be wired to {@link org.jquantlib.cashflow.IborCoupon}. JQuant
 *       has the optionlet-vol scaffolding (Phase 2j) but the in-arrears
 *       path through {@code IborCouponPricer} setup is incomplete. See
 *       WI-5e.5-SWAP-2.</li>
 *
 *   <li>{@code testCachedValue}: depends on
 *       {@link org.jquantlib.cashflow.IborCoupon}.{@code Settings#usingAtParCoupons()}
 *       (already present per Phase 2x), but the cached NPV
 *       {@code -5.872342992212} requires regenerating from C++ v1.42.1 via
 *       a probe to confirm cross-version stability. See WI-5e.5-SWAP-3.</li>
 *
 *   <li>{@code testThirdWednesdayAdjustment},
 *       {@code testFixedTenorInferenceWithTerminationDate},
 *       {@code testSettlementDaysEffectiveDateConflict}: depend on
 *       {@link org.jquantlib.instruments.MakeVanillaSwap} convenience
 *       overloads that mirror the C++ {@code MakeVanillaSwap} fluent API
 *       (third-Wednesday end-of-month rule, fixed tenor inference, and
 *       conflicting settlement-days/effective-date error reporting). The
 *       Java {@code MakeVanillaSwap} stub exists but lacks these specific
 *       guard rails. See WI-5e.5-SWAP-4.</li>
 *
 *   <li>{@code testNotifications}: requires the {@link
 *       org.jquantlib.util.WeakReferenceObservable} batched-notification
 *       semantics (Phase 2x A.4) to fire after a curve relink. JQuant has
 *       the infrastructure but the swap-side observer plumbing for
 *       {@code DiscountingSwapEngine} needs to be confirmed against the
 *       C++ behavior (notification count after a single
 *       {@code RelinkableHandle} update). See WI-5e.5-SWAP-5.</li>
 * </ul>
 */
public class SwapTest {

    public SwapTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    /** Mirror of C++ {@code CommonVars} struct (swap.cpp:49-99). */
    private static final class CommonVars {
        final Date today;
        final Date settlement;
        final VanillaSwap.Type type = VanillaSwap.Type.Payer;
        final double nominal = 100.0;
        final Calendar calendar;
        final BusinessDayConvention fixedConvention =
                BusinessDayConvention.Unadjusted;
        final BusinessDayConvention floatingConvention =
                BusinessDayConvention.ModifiedFollowing;
        final Frequency fixedFrequency = Frequency.Annual;
        final Frequency floatingFrequency = Frequency.Semiannual;
        final DayCounter fixedDayCount =
                new Thirty360(Thirty360.Convention.BondBasis);
        final IborIndex index;
        final int settlementDays = 2;
        final RelinkableHandle<YieldTermStructure> termStructure;

        CommonVars() {
            this.termStructure = new RelinkableHandle<YieldTermStructure>();
            this.index = new Euribor(
                    new Period(floatingFrequency), termStructure);
            this.calendar = index.fixingCalendar();
            this.today = calendar.adjust(
                    new Settings().evaluationDate());
            this.settlement = calendar.advance(today,
                    settlementDays, TimeUnit.Days);
            this.termStructure.linkTo(Utilities.flatRate(settlement, 0.05,
                    new Actual365Fixed()));
        }

        VanillaSwap makeSwap(final int length, final double fixedRate,
                             final double floatingSpread,
                             final DateGeneration.Rule rule) {
            final Date maturity = calendar.advance(settlement,
                    new Period(length, TimeUnit.Years), floatingConvention);
            final Schedule fixedSchedule = new Schedule(
                    settlement, maturity, new Period(fixedFrequency),
                    calendar, fixedConvention, fixedConvention,
                    rule, false);
            final Schedule floatSchedule = new Schedule(
                    settlement, maturity, new Period(floatingFrequency),
                    calendar, floatingConvention, floatingConvention,
                    rule, false);
            final VanillaSwap swap = new VanillaSwap(type, nominal,
                    fixedSchedule, fixedRate, fixedDayCount,
                    floatSchedule, index, floatingSpread,
                    index.dayCounter());
            swap.setPricingEngine(new DiscountingSwapEngine(termStructure));
            return swap;
        }

        VanillaSwap makeSwap(final int length, final double fixedRate,
                             final double floatingSpread) {
            return makeSwap(length, fixedRate, floatingSpread,
                    DateGeneration.Rule.Forward);
        }
    }

    @Test
    public void testFairRate() {
        QL.info("Testing vanilla-swap calculation of fair fixed rate...");

        final CommonVars vars = new CommonVars();

        final int[] lengths = { 1, 2, 5, 10, 20 };
        final double[] spreads = { -0.001, -0.01, 0.0, 0.01, 0.001 };

        for (final int length : lengths) {
            for (final double spread : spreads) {
                VanillaSwap swap = vars.makeSwap(length, 0.0, spread);
                swap = vars.makeSwap(length, swap.fairRate(), spread);
                if (Math.abs(swap.NPV()) > 1.0e-10) {
                    fail("recalculating with implied rate:\n"
                            + "    length: " + length + " years\n"
                            + "    floating spread: " + spread + "\n"
                            + "    swap value: " + swap.NPV());
                }
            }
        }
    }

    @Test
    public void testFairSpread() {
        QL.info("Testing vanilla-swap calculation of fair floating spread...");

        final CommonVars vars = new CommonVars();

        final int[] lengths = { 1, 2, 5, 10, 20 };
        final double[] rates = { 0.04, 0.05, 0.06, 0.07 };

        for (final int length : lengths) {
            for (final double rate : rates) {
                VanillaSwap swap = vars.makeSwap(length, rate, 0.0);
                swap = vars.makeSwap(length, rate, swap.fairSpread());
                if (Math.abs(swap.NPV()) > 1.0e-10) {
                    fail("recalculating with implied spread:\n"
                            + "    length: " + length + " years\n"
                            + "    fixed rate: " + rate + "\n"
                            + "    swap value: " + swap.NPV());
                }
            }
        }
    }

    @Test
    public void testRateDependency() {
        QL.info("Testing vanilla-swap dependency on fixed rate...");

        final CommonVars vars = new CommonVars();

        final int[] lengths = { 1, 2, 5, 10, 20 };
        final double[] spreads = { -0.001, -0.01, 0.0, 0.01, 0.001 };
        final double[] rates = { 0.03, 0.04, 0.05, 0.06, 0.07 };

        for (final int length : lengths) {
            for (final double spread : spreads) {
                final double[] swapValues = new double[rates.length];
                for (int i = 0; i < rates.length; ++i) {
                    swapValues[i] = vars.makeSwap(length, rates[i], spread).NPV();
                }
                // Payer swap NPV must be non-increasing in fixed rate.
                for (int i = 0; i + 1 < rates.length; ++i) {
                    if (swapValues[i] < swapValues[i + 1]) {
                        fail("NPV is increasing with the fixed rate in a swap:\n"
                                + "    length: " + length + " years\n"
                                + "    value:  " + swapValues[i]
                                + " paying fixed rate: " + rates[i] + "\n"
                                + "    value:  " + swapValues[i + 1]
                                + " paying fixed rate: " + rates[i + 1]);
                    }
                }
            }
        }
    }

    @Test
    public void testSpreadDependency() {
        QL.info("Testing vanilla-swap dependency on floating spread...");

        final CommonVars vars = new CommonVars();

        final int[] lengths = { 1, 2, 5, 10, 20 };
        final double[] rates = { 0.04, 0.05, 0.06, 0.07 };
        final double[] spreads = {
                -0.01, -0.002, -0.001, 0.0, 0.001, 0.002, 0.01
        };

        for (final int length : lengths) {
            for (final double rate : rates) {
                final double[] swapValues = new double[spreads.length];
                for (int i = 0; i < spreads.length; ++i) {
                    swapValues[i] = vars.makeSwap(length, rate, spreads[i]).NPV();
                }
                // Payer swap NPV must be non-decreasing in floating spread.
                for (int i = 0; i + 1 < spreads.length; ++i) {
                    if (swapValues[i] > swapValues[i + 1]) {
                        fail("NPV is decreasing with the floating spread in a swap:\n"
                                + "    length: " + length + " years\n"
                                + "    value:  " + swapValues[i]
                                + " receiving spread: " + spreads[i] + "\n"
                                + "    value:  " + swapValues[i + 1]
                                + " receiving spread: " + spreads[i + 1]);
                    }
                }
            }
        }
    }

    /**
     * Phase 5d.5-Bonds-b WI-5e.5-SWAP-2 — port of C++ swap.cpp:218-282
     * (testInArrears).
     *
     * <p>Builds a 5-year EURCurrency swap on a NullCalendar with annual
     * tenor and exercises the {@link IborLeg#inArrears()} flag plus
     * {@link BlackIborCouponPricer} wiring via
     * {@link PricerSetter#setCouponPricer}.  The expected NPV
     * {@code -144813.0} is the cached value from C++ Hull "Options,
     * Futures, and Other Derivatives" 4th-ed worked example, p.550 (with
     * the documented sign-corrected adjustment 0.05 + 0.000115·T1).
     *
     * <p>Tolerance is loose ({@code 1.0}) per the C++ test fixture; this
     * matches the Hull text's quoted precision.
     */
    @Test
    public void testInArrears() {
        final Date today = new Settings().evaluationDate();
        final Date maturity = today.add(new Period(5, TimeUnit.Years));
        final NullCalendar calendar = new NullCalendar();
        final Schedule schedule = new Schedule(today, maturity,
                new Period(Frequency.Annual), calendar,
                BusinessDayConvention.Following,
                BusinessDayConvention.Following,
                DateGeneration.Rule.Forward, false);
        final SimpleDayCounter dayCounter = new SimpleDayCounter();
        final double nominal = 100_000_000.0;

        final double oneYear = 0.05;
        final double r = Math.log(1.0 + oneYear);
        final FlatForward flat = new FlatForward(today,
                new Handle<org.jquantlib.quotes.Quote>(new SimpleQuote(r)), dayCounter);
        final Handle<YieldTermStructure> termStructure = new Handle<YieldTermStructure>(flat);

        final IborIndex index = new IborIndex("dummy",
                new Period(1, TimeUnit.Years), 0,
                new EURCurrency(), calendar,
                BusinessDayConvention.Following, false, dayCounter,
                termStructure);

        final Leg fixedLeg = new FixedRateLeg(schedule, dayCounter)
                .withNotionals(nominal)
                .withCouponRates(oneYear)
                .Leg();

        final double capletVolatility = 0.22;
        final Handle<OptionletVolatilityStructure> vol =
                new Handle<OptionletVolatilityStructure>(
                        new ConstantOptionletVolatility(today,
                                new NullCalendar(),
                                BusinessDayConvention.Following,
                                capletVolatility, dayCounter));
        final BlackIborCouponPricer pricer = new BlackIborCouponPricer(vol);

        // Per C++: gearings + spreads vectors empty (use defaults). Java
        // leaves them at default (empty Array) when unset on the builder.
        // fixingDays = 0 mirrors C++ withFixingDays(0).
        final Leg floatingLeg = new IborLeg(schedule, index)
                .withNotionals(new Array(new double[] { nominal }))
                .withPaymentDayCounter(dayCounter)
                .withFixingDays(0)
                .inArrears()
                .Leg();
        PricerSetter.setCouponPricer(floatingLeg, pricer);

        final Swap swap = new Swap(floatingLeg, fixedLeg);
        swap.setPricingEngine(new DiscountingSwapEngine(termStructure));

        final double storedValue = -144813.0;
        final double tolerance = 1.0;
        final double npv = swap.NPV();

        // Smoke: confirm finite, non-zero, sign-correct.  When BlackIbor
        // pricer plumbing diverges in subtle ways from C++ the absolute
        // NPV may shift slightly; first assert directionally then
        // compare to stored value within tolerance.
        assertTrue("swap NPV should be finite", !Double.isNaN(npv) && !Double.isInfinite(npv));

        if (Math.abs(npv - storedValue) > tolerance) {
            fail("Wrong NPV calculation:\n"
                    + "    expected:   " + storedValue + "\n"
                    + "    calculated: " + npv);
        }
    }

    @Ignore("Phase 5e.5 carry-forward WI-5e.5-SWAP-3 — depends on regenerating cached NPV "
            + "(-5.872342992212 with at-par coupons) from C++ v1.42.1 via a probe.")
    @Test
    public void testCachedValue() {
    }

    @Ignore("Phase 5e.5 carry-forward WI-5e.5-SWAP-4 — needs MakeVanillaSwap third-Wednesday "
            + "end-of-month rule overload.")
    @Test
    public void testThirdWednesdayAdjustment() {
    }

    @Ignore("Phase 5e.5 carry-forward WI-5e.5-SWAP-5 — needs swap-side observer plumbing for "
            + "DiscountingSwapEngine batched-notification semantics confirmation against C++.")
    @Test
    public void testNotifications() {
    }

    @Ignore("Phase 5e.5 carry-forward WI-5e.5-SWAP-4 — needs MakeVanillaSwap fixed-tenor inference "
            + "with terminationDate convenience overload.")
    @Test
    public void testFixedTenorInferenceWithTerminationDate() {
    }

    @Ignore("Phase 5e.5 carry-forward WI-5e.5-SWAP-4 — needs MakeVanillaSwap conflicting "
            + "settlementDays/effectiveDate guard-rail/error reporting.")
    @Test
    public void testSettlementDaysEffectiveDateConflict() {
    }
}
