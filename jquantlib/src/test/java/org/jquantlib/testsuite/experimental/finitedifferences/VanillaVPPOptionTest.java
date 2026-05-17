/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.finitedifferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jquantlib.Settings;
import org.jquantlib.experimental.finitedifferences.VanillaVPPOption;
import org.jquantlib.instruments.AverageBasketPayoff;
import org.jquantlib.instruments.BasketPayoff;
import org.jquantlib.instruments.SwingExercise;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.junit.Test;

/**
 * Smoke tests for {@link VanillaVPPOption}.
 *
 * <p>The Phase 5e.5b-CFC-d-164 port lands the instrument class and its
 * argument bundle. These tests verify the basic instrument-side API
 * (construction, payoff wiring, expiry detection, argument propagation).
 * Full pricing-engine tests live in {@code VppTest} and remain ignored
 * until the matching FD engines (FdSimpleKlugeExtOUVPPEngine,
 * DynProgVPPIntrinsicValueEngine) are ported.
 *
 * <p>Source reference: {@code test-suite/vpp.cpp} v1.42.1 @
 * {@code 099987f0ca} — parameter values mirror
 * {@code testVPPIntrinsicValue}.
 */
public class VanillaVPPOptionTest {

    @Test
    public void testConstructionAndPayoff() {
        final Date today = new Date(18, Month.December, 2011);
        new Settings().setEvaluationDate(today);

        // Parameter values from C++ testVPPIntrinsicValue.
        final double pMin           = 8.0;
        final double pMax           = 40.0;
        final int    tMinUp         = 2;
        final int    tMinDown       = 2;
        final double startUpFuel    = 20.0;
        final double startUpFixCost = 100.0;
        final double efficiency     = 0.5;
        final double heatRate       = 1.0 / efficiency;

        final SwingExercise exercise = new SwingExercise(today, today.add(6), 3600);

        final VanillaVPPOption option = new VanillaVPPOption(
                heatRate, pMin, pMax, tMinUp, tMinDown,
                startUpFuel, startUpFixCost, exercise);

        assertNotNull("payoff", option.payoff());
        assertTrue("payoff is BasketPayoff",
                BasketPayoff.class.isAssignableFrom(option.payoff().getClass()));
        assertTrue("payoff is AverageBasketPayoff",
                AverageBasketPayoff.class.isAssignableFrom(option.payoff().getClass()));

        final AverageBasketPayoff abp = (AverageBasketPayoff) option.payoff();
        final double[] weights = abp.weights();
        assertEquals("weights.length", 2, weights.length);
        assertEquals("weights[0] (power)", 1.0, weights[0], 0.0);
        assertEquals("weights[1] (gas)", -heatRate, weights[1], 0.0);

        // Spark spread reproduction via the basket accumulator.
        final double powerPx = 50.0;
        final double gasPx   = 20.0;
        final double sparkSpread = powerPx - heatRate * gasPx;
        assertEquals("basket accumulator = spark spread",
                sparkSpread, abp.accumulate(new double[]{ powerPx, gasPx }), 1.0e-15);

        // Identity payoff: P(x) = x — so basket payoff returns the linear
        // combination directly, with no max/min reshaping.
        assertEquals("payoff(prices) = spark spread",
                sparkSpread, abp.get(new double[]{ powerPx, gasPx }), 1.0e-15);
    }

    @Test
    public void testIsExpiredBeforeMaturity() {
        final Date today = new Date(18, Month.December, 2011);
        new Settings().setEvaluationDate(today);

        final SwingExercise exercise = new SwingExercise(today, today.add(6), 3600);
        final VanillaVPPOption option = new VanillaVPPOption(
                /* heatRate */ 2.0, 8.0, 40.0, 2, 2, 20.0, 100.0, exercise);

        assertEquals("not expired the day before maturity",
                false, option.isExpired());
    }

    @Test
    public void testIsExpiredAfterMaturity() {
        final Date today = new Date(18, Month.December, 2011);
        new Settings().setEvaluationDate(today);

        final SwingExercise exercise = new SwingExercise(today, today.add(6), 3600);
        final VanillaVPPOption option = new VanillaVPPOption(
                /* heatRate */ 2.0, 8.0, 40.0, 2, 2, 20.0, 100.0, exercise);

        new Settings().setEvaluationDate(exercise.lastDate().add(1));
        assertEquals("expired one day after maturity",
                true, option.isExpired());
    }

    @Test
    public void testArgumentsPropagation() {
        final Date today = new Date(18, Month.December, 2011);
        new Settings().setEvaluationDate(today);

        final SwingExercise exercise = new SwingExercise(today, today.add(6), 3600);
        final VanillaVPPOption option = new VanillaVPPOption(
                /* heatRate */ 2.5, 8.0, 40.0, 3, 4, 20.0, 100.0, exercise,
                /* nStarts */ VanillaVPPOption.NULL_INT,
                /* nRunningHours */ VanillaVPPOption.NULL_INT);

        final VanillaVPPOption.ArgumentsImpl args = new VanillaVPPOption.ArgumentsImpl();

        // setupArguments is protected — invoke via reflection.
        try {
            final java.lang.reflect.Method m =
                    VanillaVPPOption.class.getDeclaredMethod(
                            "setupArguments",
                            org.jquantlib.pricingengines.PricingEngine.Arguments.class);
            m.setAccessible(true);
            m.invoke(option, args);
        } catch (final Exception e) {
            fail("setupArguments invocation failed: " + e.getMessage());
        }

        assertEquals("heatRate", 2.5, args.heatRate, 0.0);
        assertEquals("pMin", 8.0, args.pMin, 0.0);
        assertEquals("pMax", 40.0, args.pMax, 0.0);
        assertEquals("tMinUp", 3, args.tMinUp);
        assertEquals("tMinDown", 4, args.tMinDown);
        assertEquals("startUpFuel", 20.0, args.startUpFuel, 0.0);
        assertEquals("startUpFixCost", 100.0, args.startUpFixCost, 0.0);
        assertEquals("nStarts is null sentinel",
                VanillaVPPOption.NULL_INT, args.nStarts);
        assertEquals("nRunningHours is null sentinel",
                VanillaVPPOption.NULL_INT, args.nRunningHours);

        // validate() should accept the null/null combination
        // (matches C++ "either a start limit or fuel limit is supported").
        args.validate();
    }

    @Test
    public void testArgumentsValidateRejectsBothLimits() {
        final Date today = new Date(18, Month.December, 2011);
        new Settings().setEvaluationDate(today);

        final SwingExercise exercise = new SwingExercise(today, today.add(6), 3600);
        final VanillaVPPOption option = new VanillaVPPOption(
                /* heatRate */ 2.0, 8.0, 40.0, 2, 2, 20.0, 100.0, exercise,
                /* nStarts */ 3, /* nRunningHours */ 24);

        final VanillaVPPOption.ArgumentsImpl args = new VanillaVPPOption.ArgumentsImpl();
        try {
            final java.lang.reflect.Method m =
                    VanillaVPPOption.class.getDeclaredMethod(
                            "setupArguments",
                            org.jquantlib.pricingengines.PricingEngine.Arguments.class);
            m.setAccessible(true);
            m.invoke(option, args);
        } catch (final Exception e) {
            fail("setupArguments invocation failed: " + e.getMessage());
        }

        try {
            args.validate();
            fail("expected validate() to reject combined start + running-hour limit");
        } catch (final RuntimeException expected) {
            assertTrue("error mentions limit clash",
                    expected.getMessage() == null
                 || expected.getMessage().contains("start limit")
                 || expected.getMessage().contains("fuel limit"));
        }
    }
}
