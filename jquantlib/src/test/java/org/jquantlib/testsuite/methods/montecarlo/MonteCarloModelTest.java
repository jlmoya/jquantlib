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
import static org.junit.Assert.assertTrue;

import org.jquantlib.QL;
import org.jquantlib.math.statistics.Statistics;
import org.jquantlib.methods.montecarlo.MonteCarloModel;
import org.jquantlib.methods.montecarlo.PathPricer;
import org.jquantlib.methods.montecarlo.Sample;
import org.junit.Test;

/**
 * Java port of the structural integration tests for
 * {@code QuantLib::MonteCarloModel} (Phase 5h.5-MC-INFRA WI-5).
 *
 * <p>These tests use a synthetic non-{@code Path} payload (a boxed
 * {@code Double}) so they are independent of the path-generator port —
 * they exercise the addSamples / accumulator / antithetic / control-variate
 * branches directly.
 */
public class MonteCarloModelTest {

    public MonteCarloModelTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    /** Generator that returns successive integers as the "path" value. */
    private static final class CountingGenerator
            implements MonteCarloModel.PathGeneratorAdapter<Double> {
        private double counter = 0.0;
        private double last = 0.0;
        @Override public Sample<Double> next() {
            this.last = this.counter;
            this.counter += 1.0;
            return new Sample<Double>(last, 1.0);
        }
        @Override public Sample<Double> antithetic() {
            return new Sample<Double>(-last, 1.0);
        }
    }

    /** Identity path pricer. */
    private static final class IdentityPricer extends PathPricer<Double> {
        @Override public Double op(final Double path) { return path; }
    }

    /** Pricer that always returns a fixed offset. */
    private static final class ConstPricer extends PathPricer<Double> {
        private final double v;
        ConstPricer(final double v) { this.v = v; }
        @Override public Double op(final Double path) { return v; }
    }

    @Test
    public void testAddSamplesAccumulatesValues() {
        final MonteCarloModel<Double> mc = new MonteCarloModel<Double>(
                new CountingGenerator(), new IdentityPricer(),
                new Statistics(), false);
        mc.addSamples(5);

        // sequence 0,1,2,3,4 -> mean = 2.0, samples = 5
        assertEquals(5, mc.sampleAccumulator().samples());
        assertEquals(2.0, mc.sampleAccumulator().mean(), 1e-12);
    }

    @Test
    public void testAntitheticAveragesPair() {
        // With antithetic=true, addSamples(N) records N entries (one per
        // antithetic *pair*, value = (price + price2)/2). Our generator
        // returns +k for next() and -k for antithetic(), so pair-mean=0
        // for every sample.
        final MonteCarloModel<Double> mc = new MonteCarloModel<Double>(
                new CountingGenerator(), new IdentityPricer(),
                new Statistics(), true);
        mc.addSamples(7);
        assertEquals(7, mc.sampleAccumulator().samples());
        assertEquals(0.0, mc.sampleAccumulator().mean(), 1e-12);
    }

    @Test
    public void testControlVariateAdjustment() {
        // Without antithetic, with CV: adjusted_price = price + (cvValue
        // - cvPricer(path)). Choose cvPricer = const(10), cvValue = 100
        // — adjustment = 100 - 10 = 90 added to every draw.
        final MonteCarloModel<Double> mc = new MonteCarloModel<Double>(
                new CountingGenerator(), new IdentityPricer(),
                new Statistics(), false,
                new ConstPricer(10.0), 100.0, null);
        mc.addSamples(4);

        // values: (0+90, 1+90, 2+90, 3+90) = (90,91,92,93) -> mean = 91.5
        assertEquals(4, mc.sampleAccumulator().samples());
        assertEquals(91.5, mc.sampleAccumulator().mean(), 1e-12);
    }

    @Test
    public void testIncrementalAddSamples() {
        final MonteCarloModel<Double> mc = new MonteCarloModel<Double>(
                new CountingGenerator(), new IdentityPricer(),
                new Statistics(), false);
        mc.addSamples(3);
        assertEquals(3, mc.sampleAccumulator().samples());
        // values 0,1,2 -> mean 1.0
        assertEquals(1.0, mc.sampleAccumulator().mean(), 1e-12);

        mc.addSamples(2);
        assertEquals(5, mc.sampleAccumulator().samples());
        // values 0,1,2,3,4 -> mean 2.0
        assertEquals(2.0, mc.sampleAccumulator().mean(), 1e-12);
    }

    @Test
    public void testErrorEstimateIsPositive() {
        final MonteCarloModel<Double> mc = new MonteCarloModel<Double>(
                new CountingGenerator(), new IdentityPricer(),
                new Statistics(), false);
        mc.addSamples(20);
        assertTrue("errorEstimate must be positive after >1 samples",
                mc.sampleAccumulator().errorEstimate() > 0.0);
    }
}
