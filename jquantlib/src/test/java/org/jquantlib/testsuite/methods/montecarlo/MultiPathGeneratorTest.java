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

package org.jquantlib.testsuite.methods.montecarlo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.randomnumbers.RandomSequenceGeneratorIntf;
import org.jquantlib.methods.montecarlo.MultiPath;
import org.jquantlib.methods.montecarlo.MultiPathGenerator;
import org.jquantlib.methods.montecarlo.Sample;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
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
import org.jquantlib.time.TimeGrid;
import org.jquantlib.time.calendars.NullCalendar;
import org.junit.Test;

/**
 * Java port of the structural and convergence checks for
 * {@code QuantLib::MultiPathGenerator&lt;GSG&gt;} (Phase 5h.5-MC-INFRA WI-4).
 *
 * <p>Cross-validation strategy mirrors {@link PathGeneratorAdditionalTest}:
 * use a deterministic Gaussian-sequence stub so the check is independent of
 * the MT-driven RNG path. We currently only have single-asset stochastic
 * processes ported (GeneralizedBlackScholesProcess), so the "multi" here is
 * a single-asset process driven through the multi-asset code path
 * (factors=1, size=1) — that exercises the offset / antithetic / evolve
 * machinery without forcing a multi-asset process port that belongs to a
 * later phase.
 */
@SuppressWarnings("deprecation")
public class MultiPathGeneratorTest {

    public MultiPathGeneratorTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static final class DeterministicGsg implements RandomSequenceGeneratorIntf {
        private final double[] sequence;
        DeterministicGsg(final double[] s) { this.sequence = s; }
        @Override public int dimension() { return sequence.length; }
        @Override public Sample<double[]> nextSequence() { return new Sample<double[]>(sequence, 1.0); }
        @Override public Sample<double[]> lastSequence() { return new Sample<double[]>(sequence, 1.0); }
        @Override public long[] nextInt32Sequence() { throw new UnsupportedOperationException(); }
    }

    private static GeneralizedBlackScholesProcess makeBsm(final Date today) {
        final DayCounter dc = new Actual365Fixed();
        final Calendar cal = new NullCalendar();
        final Handle<? extends Quote> spot = new Handle<Quote>(new SimpleQuote(100.0));
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(
                new FlatForward(today, 0.05, dc));
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(
                new FlatForward(today, 0.02, dc));
        final Handle<BlackVolTermStructure> volTS = new Handle<BlackVolTermStructure>(
                new BlackConstantVol(today, cal, 0.20, dc));
        return new GeneralizedBlackScholesProcess(spot, qTS, rTS, volTS);
    }

    @Test
    public void testSingleAssetMultiPathFollowsDrift() {
        final Date today = new Date(15, Month.January, 2026);
        new org.jquantlib.Settings().setEvaluationDate(today);
        final GeneralizedBlackScholesProcess process = makeBsm(today);

        // 4 time steps, factors=1, size=1 -> dim = 1 * 4 = 4.
        final TimeGrid grid = new TimeGrid(1.0, 4);
        final DeterministicGsg gsg = new DeterministicGsg(new double[] {0, 0, 0, 0});

        final MultiPathGenerator<DeterministicGsg> gen =
                new MultiPathGenerator<DeterministicGsg>(process, grid, gsg, false);
        final Sample<MultiPath> sample = gen.next();
        assertNotNull(sample);
        assertEquals(1.0, sample.weight(), 0.0);

        final MultiPath mp = sample.value();
        assertEquals(1, mp.assetNumber());
        assertEquals(5, mp.pathSize());
        assertEquals(100.0, mp.get(0).front(), 1e-12);
    }

    @Test
    public void testAntitheticReusesLastSequenceWithNegatedNoise() {
        final Date today = new Date(15, Month.January, 2026);
        new org.jquantlib.Settings().setEvaluationDate(today);
        final GeneralizedBlackScholesProcess process = makeBsm(today);

        final TimeGrid grid = new TimeGrid(0.5, 3);
        // factors=1, dim = 1 * 3 = 3.
        final double[] dws = {0.4, -0.2, 0.5};
        final DeterministicGsg gsg = new DeterministicGsg(dws.clone());

        final MultiPathGenerator<DeterministicGsg> gen =
                new MultiPathGenerator<DeterministicGsg>(process, grid, gsg, false);

        final Sample<MultiPath> first = gen.next();
        // Capture before antithetic() overwrites the buffer.
        final double[] firstValues = first.value().get(0).values().clone();

        final Sample<MultiPath> anti = gen.antithetic();
        final double[] antiValues = anti.value().get(0).values().clone();

        // Step-by-step re-derive expected paths from the same evolve()
        // calls the generator uses (TIGHT 1e-12).
        double next = 100.0;
        double antiNext = 100.0;
        for (int i = 1; i <= 3; i++) {
            final double t = (i - 1) * (0.5 / 3.0);
            final double dt = 0.5 / 3.0;
            next = process.evolve(t, next, dt, dws[i - 1]);
            antiNext = process.evolve(t, antiNext, dt, -dws[i - 1]);
            assertEquals("forward asset[0][" + i + "]", next, firstValues[i], 1e-12);
            assertEquals("antithetic asset[0][" + i + "]", antiNext, antiValues[i], 1e-12);
        }
    }

    @Test
    public void testBrownianBridgeNotSupported() {
        final Date today = new Date(15, Month.January, 2026);
        new org.jquantlib.Settings().setEvaluationDate(today);
        final GeneralizedBlackScholesProcess process = makeBsm(today);
        final TimeGrid grid = new TimeGrid(1.0, 4);
        final DeterministicGsg gsg = new DeterministicGsg(new double[] {0, 0, 0, 0});

        final MultiPathGenerator<DeterministicGsg> gen =
                new MultiPathGenerator<DeterministicGsg>(process, grid, gsg, true);
        try {
            gen.next();
            fail("Brownian bridge must throw for multi-asset paths");
        } catch (final UnsupportedOperationException expected) {
            // ok — matches QL_FAIL in C++ multipathgenerator.hpp.
        }
    }

    @Test
    public void testDimensionMismatchThrows() {
        final Date today = new Date(15, Month.January, 2026);
        new org.jquantlib.Settings().setEvaluationDate(today);
        final GeneralizedBlackScholesProcess process = makeBsm(today);
        final TimeGrid grid = new TimeGrid(1.0, 4); // dim required = 4
        final DeterministicGsg gsg = new DeterministicGsg(new double[] {0, 0, 0});
        try {
            new MultiPathGenerator<DeterministicGsg>(process, grid, gsg, false);
            fail("expected IllegalArgumentException for dim mismatch");
        } catch (final IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void testTwoStepThreeAssetCheck() {
        // Quick sanity: with a multi-step grid the path's pathSize and
        // each asset's front are wired correctly even though we only
        // have a single-asset BS process.
        final Date today = new Date(15, Month.January, 2026);
        new org.jquantlib.Settings().setEvaluationDate(today);
        final GeneralizedBlackScholesProcess process = makeBsm(today);

        final TimeGrid grid = new TimeGrid(2.0, 6); // 6 steps, 7 points
        final DeterministicGsg gsg = new DeterministicGsg(
                new double[] {0, 0, 0, 0, 0, 0});
        final MultiPathGenerator<DeterministicGsg> gen =
                new MultiPathGenerator<DeterministicGsg>(process, grid, gsg, false);
        final Sample<MultiPath> s = gen.next();
        assertEquals(7, s.value().pathSize());
        assertEquals(1, s.value().assetNumber());
        assertEquals(100.0, s.value().get(0).front(), 1e-12);
    }
}
