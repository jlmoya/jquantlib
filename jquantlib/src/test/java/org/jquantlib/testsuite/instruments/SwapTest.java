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
import org.jquantlib.daycounters.SimpleDayCounter;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.instruments.Swap;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.optionlet.ConstantOptionletVolatility;
import org.jquantlib.termstructures.volatilities.optionlet.OptionletVolatilityStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.BusinessDayConvention;
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

    @Ignore("Phase 5e.5 WI-5e.5-SWAP-1: MakeVanillaSwap.withRule(DateGeneration) now exists; empty test body — needs full port from C++ swap.cpp::testFairRate")
    @Test
    public void testFairRate() {
    }

    @Ignore("Phase 5e.5 WI-5e.5-SWAP-1: MakeVanillaSwap.withRule now exists; empty test body — needs full port from C++ swap.cpp::testFairSpread")
    @Test
    public void testFairSpread() {
    }

    @Ignore("Phase 5e.5 WI-5e.5-SWAP-1: MakeVanillaSwap.withRule now exists; empty test body — needs full port from C++ swap.cpp::testRateDependency")
    @Test
    public void testRateDependency() {
    }

    @Ignore("Phase 5e.5 WI-5e.5-SWAP-1: MakeVanillaSwap.withRule now exists; empty test body — needs full port from C++ swap.cpp::testSpreadDependency")
    @Test
    public void testSpreadDependency() {
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
