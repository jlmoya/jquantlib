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
 */
package org.jquantlib.testsuite.experimental.mcbasket;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.experimental.mcbasket.MCPathBasketEngine;
import org.jquantlib.experimental.mcbasket.MakeMCPathBasketEngine;
import org.jquantlib.experimental.mcbasket.PathMultiAssetOption;
import org.jquantlib.experimental.mcbasket.PathPayoff;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.shortrate.StochasticProcessArray;
import org.jquantlib.pricingengines.McSimulation;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.processes.StochasticProcess1D;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.BlackConstantVol;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.calendars.NullCalendar;
import org.jquantlib.util.PolymorphicVisitor;
import org.junit.Test;

/**
 * Structural cross-validation test for {@link MakeMCPathBasketEngine}.
 *
 * <p>Monte Carlo prices are RNG-dependent and not bit-matchable across
 * C++/Java, so correctness of this fluent builder is validated
 * <strong>structurally and deterministically</strong>: every named parameter
 * set on the builder must flow through, unchanged, to the constructed
 * {@link MCPathBasketEngine}, in the exact order and with the exact defaults
 * specified by C++ QuantLib v1.42.1
 * {@code ql/experimental/mcbasket/mcpathbasketengine.hpp} (pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}).
 *
 * <p>The structural asserts mirror the C++ conversion operator
 * (mcpathbasketengine.hpp:323-338), which constructs
 * {@code MCPathBasketEngine<RNG,S>(process_, steps_, stepsPerYear_,
 * brownianBridge_, antithetic_, controlVariate_, samples_, tolerance_,
 * maxSamples_, seed_)}. Tier: EXACT / structural. A single deterministic
 * smoke (fixed seed → finite NPV) is included as an end-to-end wiring check.
 */
public class MakeMCPathBasketEngineTest {

    public MakeMCPathBasketEngineTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static class VanillaAtExpiryPathPayoff extends PathPayoff {
        private final double strike_;

        VanillaAtExpiryPathPayoff(final double strike) {
            this.strike_ = strike;
        }

        @Override
        public String name() {
            return "VanillaAtExpiry";
        }

        @Override
        public String description() {
            return "Vanilla European call on first asset at last fixing date";
        }

        @Override
        public int basisSystemDimension() {
            return 1;
        }

        @Override
        public void value(final Matrix path, final List<Handle<YieldTermStructure>> forwardTermStructures,
                final Array payments, final Array exercises, final List<Array> states) {
            final int numberOfTimes = path.columns();
            for (int i = 0; i < numberOfTimes - 1; i++) {
                payments.set(i, 0.0);
            }
            payments.set(numberOfTimes - 1, Math.max(path.get(0, numberOfTimes - 1) - strike_, 0.0));
        }

        @Override
        public void accept(final PolymorphicVisitor pv) {
            // unused
        }
    }

    private static class TestPathOption extends PathMultiAssetOption {
        private final PathPayoff payoff_;
        private final List<Date> fixingDates_;

        TestPathOption(final PathPayoff payoff, final List<Date> fixingDates) {
            super();
            this.payoff_ = payoff;
            this.fixingDates_ = fixingDates;
        }

        @Override
        public PathPayoff pathPayoff() {
            return payoff_;
        }

        @Override
        public List<Date> fixingDates() {
            return fixingDates_;
        }
    }

    private static GeneralizedBlackScholesProcess makeBsm(final Date today, final double S, final double r,
            final double q, final double vol, final DayCounter dc, final Calendar cal) {
        final Handle<? extends Quote> spot = new Handle<Quote>(new SimpleQuote(S));
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(new FlatForward(today, r, dc));
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(new FlatForward(today, q, dc));
        final Handle<BlackVolTermStructure> volTS = new Handle<BlackVolTermStructure>(
                new BlackConstantVol(today, cal, vol, dc));
        return new GeneralizedBlackScholesProcess(spot, qTS, rTS, volTS);
    }

    private static StochasticProcessArray singleAssetArray(final Date today, final DayCounter dc, final Calendar cal) {
        final GeneralizedBlackScholesProcess process = makeBsm(today, 100.0, 0.05, 0.02, 0.20, dc, cal);
        final List<StochasticProcess1D> processes = new ArrayList<>();
        processes.add(process);
        final Matrix corr = new Matrix(new double[][] { { 1.0 } });
        return new StochasticProcessArray(processes, corr);
    }

    /**
     * The builder must forward a full chain of with*() settings to the engine
     * verbatim. Mirrors the C++ conversion operator argument list
     * (mcpathbasketengine.hpp:327-337).
     */
    @Test
    public void testFullChainFlowsThroughToEngine() {
        final Date today = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(today);
        final DayCounter dc = new Actual365Fixed();
        final Calendar cal = new NullCalendar();
        final StochasticProcessArray array = singleAssetArray(today, dc, cal);

        final PricingEngine pe = new MakeMCPathBasketEngine(array)
                .withSteps(5)
                .withBrownianBridge(true)
                .withAntitheticVariate(true)
                .withControlVariate(true)
                .withSamples(8000)
                .withMaxSamples(40000)
                .withSeed(987L)
                .value();

        assertTrue("builder must produce an MCPathBasketEngine", pe instanceof MCPathBasketEngine);
        final MCPathBasketEngine engine = (MCPathBasketEngine) pe;

        // C++ mcpathbasketengine.hpp:328-337 — exact argument forwarding.
        assertEquals("process", array, engine.processArray());
        assertEquals("steps", 5, engine.timeSteps());
        assertEquals("stepsPerYear (unset → Null<Size>)", McSimulation.NULL_SAMPLES, engine.timeStepsPerYear());
        assertTrue("brownianBridge", engine.brownianBridge());
        assertTrue("antithetic", engine.antitheticVariate());
        assertTrue("controlVariate", engine.controlVariate());
        assertEquals("requiredSamples", 8000, engine.requiredSamples());
        assertEquals("maxSamples", 40000, engine.maxSamples());
        assertEquals("seed", 987L, engine.seed());
        assertTrue("tolerance unset → NaN", Double.isNaN(engine.requiredTolerance()));
    }

    /**
     * Builder defaults must match C++ member initialisers
     * (mcpathbasketengine.hpp:240-244, ctor 247-251): antithetic=false,
     * controlVariate=false, stepsPerYear=Null, samples=Null, maxSamples=Null,
     * tolerance=Null, brownianBridge=false, seed=0.
     */
    @Test
    public void testDefaultsMatchCpp() {
        final Date today = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(today);
        final DayCounter dc = new Actual365Fixed();
        final Calendar cal = new NullCalendar();
        final StochasticProcessArray array = singleAssetArray(today, dc, cal);

        // withSteps is mandatory (the engine ctor enforces the steps xor);
        // everything else exercises the C++ defaults.
        final MCPathBasketEngine engine = (MCPathBasketEngine) new MakeMCPathBasketEngine(array)
                .withSteps(3).value();

        assertEquals("default steps", 3, engine.timeSteps());
        assertEquals("default stepsPerYear", McSimulation.NULL_SAMPLES, engine.timeStepsPerYear());
        assertEquals("default brownianBridge=false", false, engine.brownianBridge());
        assertEquals("default antithetic=false", false, engine.antitheticVariate());
        assertEquals("default controlVariate=false", false, engine.controlVariate());
        assertEquals("default samples=Null", McSimulation.NULL_SAMPLES, engine.requiredSamples());
        assertEquals("default maxSamples=Null", McSimulation.NULL_SAMPLES, engine.maxSamples());
        assertEquals("default seed=0", 0L, engine.seed());
        assertTrue("default tolerance=NaN", Double.isNaN(engine.requiredTolerance()));
    }

    /** withStepsPerYear must flow through (alternative to withSteps). */
    @Test
    public void testStepsPerYearFlowsThrough() {
        final Date today = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(today);
        final DayCounter dc = new Actual365Fixed();
        final Calendar cal = new NullCalendar();
        final StochasticProcessArray array = singleAssetArray(today, dc, cal);

        final MCPathBasketEngine engine = (MCPathBasketEngine) new MakeMCPathBasketEngine(array)
                .withStepsPerYear(24).value();
        assertEquals("stepsPerYear", 24, engine.timeStepsPerYear());
        assertEquals("steps unset → Null", McSimulation.NULL_SAMPLES, engine.timeSteps());
    }

    /**
     * C++ mcpathbasketengine.hpp:125-130 — the engine ctor (invoked by
     * value()) throws when neither steps nor stepsPerYear was provided.
     */
    @Test
    public void testNoStepsThrows() {
        final Date today = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(today);
        final DayCounter dc = new Actual365Fixed();
        final Calendar cal = new NullCalendar();
        final StochasticProcessArray array = singleAssetArray(today, dc, cal);
        try {
            new MakeMCPathBasketEngine(array).value();
            fail("expected exception: no time steps provided");
        } catch (final RuntimeException e) {
            // expected
        }
    }

    /**
     * C++ mcpathbasketengine.hpp:128-130 — the engine ctor (invoked by
     * value()) throws when both steps and stepsPerYear were provided.
     */
    @Test
    public void testOverspecifiedStepsThrows() {
        final Date today = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(today);
        final DayCounter dc = new Actual365Fixed();
        final Calendar cal = new NullCalendar();
        final StochasticProcessArray array = singleAssetArray(today, dc, cal);
        try {
            new MakeMCPathBasketEngine(array).withSteps(3).withStepsPerYear(24).value();
            fail("expected exception: both time steps and time steps per year were provided");
        } catch (final RuntimeException e) {
            // expected
        }
    }

    /**
     * C++ mcpathbasketengine.hpp:270-271 — withSamples must throw once a
     * tolerance has been set (mutually exclusive).
     */
    @Test
    public void testSamplesAfterToleranceThrows() {
        final Date today = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(today);
        final DayCounter dc = new Actual365Fixed();
        final Calendar cal = new NullCalendar();
        final StochasticProcessArray array = singleAssetArray(today, dc, cal);
        try {
            new MakeMCPathBasketEngine(array).withAbsoluteTolerance(0.01).withSamples(1000);
            fail("expected exception: tolerance already set");
        } catch (final RuntimeException e) {
            // expected
        }
    }

    /**
     * C++ mcpathbasketengine.hpp:279-280 — withAbsoluteTolerance must throw
     * once samples has been set (mutually exclusive).
     */
    @Test
    public void testToleranceAfterSamplesThrows() {
        final Date today = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(today);
        final DayCounter dc = new Actual365Fixed();
        final Calendar cal = new NullCalendar();
        final StochasticProcessArray array = singleAssetArray(today, dc, cal);
        try {
            new MakeMCPathBasketEngine(array).withSamples(1000).withAbsoluteTolerance(0.01);
            fail("expected exception: number of samples already set");
        } catch (final RuntimeException e) {
            // expected
        }
    }

    /** withAbsoluteTolerance must flow through (and leave samples unset). */
    @Test
    public void testToleranceFlowsThrough() {
        final Date today = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(today);
        final DayCounter dc = new Actual365Fixed();
        final Calendar cal = new NullCalendar();
        final StochasticProcessArray array = singleAssetArray(today, dc, cal);

        final MCPathBasketEngine engine = (MCPathBasketEngine) new MakeMCPathBasketEngine(array)
                .withSteps(3).withAbsoluteTolerance(0.05).value();
        assertEquals("tolerance", 0.05, engine.requiredTolerance(), 0.0);
        assertEquals("samples unset → Null", McSimulation.NULL_SAMPLES, engine.requiredSamples());
    }

    /**
     * Deterministic end-to-end wiring smoke: a builder-constructed engine must
     * price a single-asset vanilla-at-expiry basket to a finite, positive NPV
     * with a fixed seed. (Not a price cross-validation — see class javadoc.)
     */
    @Test
    public void testBuilderEngineRunsEndToEnd() {
        final Date today = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(today);
        final DayCounter dc = new Actual365Fixed();
        final Calendar cal = new NullCalendar();
        final StochasticProcessArray array = singleAssetArray(today, dc, cal);

        final Date exDate = today.add(365);
        final List<Date> fixingDates = new ArrayList<>();
        fixingDates.add(exDate);

        final PathPayoff payoff = new VanillaAtExpiryPathPayoff(100.0);
        final TestPathOption option = new TestPathOption(payoff, fixingDates);

        final PricingEngine engine = new MakeMCPathBasketEngine(array)
                .withSteps(1)
                .withAntitheticVariate(true)
                .withSamples(20000)
                .withSeed(42L)
                .value();
        option.setPricingEngine(engine);

        final double npv = option.NPV();
        QL.info("MakeMCPathBasketEngine builder smoke NPV = " + npv);
        assertTrue("NPV must be finite", !Double.isNaN(npv) && !Double.isInfinite(npv));
        assertTrue("NPV must be positive: " + npv, npv > 0.0);
        // ATM vanilla call ~9.227; a generous band confirms the builder wired
        // the engine sensibly (this is a wiring check, not a price match).
        assertTrue("NPV must be in a sane range near European 9.227: " + npv, npv > 6.0 && npv < 13.0);
    }
}
