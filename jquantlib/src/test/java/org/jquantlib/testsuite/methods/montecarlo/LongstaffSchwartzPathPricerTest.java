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

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.math.Ops;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.montecarlo.EarlyExercisePathPricer;
import org.jquantlib.methods.montecarlo.LongstaffSchwartzPathPricer;
import org.jquantlib.methods.montecarlo.LsmBasisSystem;
import org.jquantlib.methods.montecarlo.MultiPath;
import org.jquantlib.methods.montecarlo.Path;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.TimeGrid;
import org.junit.Test;

/**
 * Structural tests for {@link LongstaffSchwartzPathPricer} (Phase 5h.5-MC-AME WI-3).
 *
 * <p>Validates the calibration / pricing two-phase state machine using a
 * simple synthetic {@link EarlyExercisePathPricer} (American-call-style
 * payoff against a fixed strike on a 5-step uniform path).
 *
 * <p>Tier: TIGHT for structural state-machine assertions.
 */
public class LongstaffSchwartzPathPricerTest {

    public LongstaffSchwartzPathPricerTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    /** Synthetic single-state pricer: payoff = max(state - strike, 0). */
    private static final class SyntheticPricer
            implements EarlyExercisePathPricer<Path, Double> {
        private final double strike;
        SyntheticPricer(final double strike) { this.strike = strike; }
        @Override public double operator(final Path path, final int t) {
            return Math.max(path.get(t) - strike, 0.0);
        }
        @Override public Double state(final Path path, final int t) {
            return path.get(t);
        }
        @Override public List<? extends Ops.Op<Double, Double>> basisSystem() {
            final List<Ops.Op<Double, Double>> b = new ArrayList<>();
            b.add(new Ops.Op<Double, Double>() { public Double op(final Double x) { return 1.0; } });
            b.add(new Ops.Op<Double, Double>() { public Double op(final Double x) { return x; } });
            b.add(new Ops.Op<Double, Double>() { public Double op(final Double x) { return x * x; } });
            return b;
        }
    }


    @Test
    public void testCalibrationPhaseStoresAndReturnsZero() {
        final Date today = new Date(15, Month.January, 2026);
        new org.jquantlib.Settings().setEvaluationDate(today);
        final TimeGrid grid = new TimeGrid(1.0, 4); // 5 points: 0, 0.25, 0.5, 0.75, 1.0
        final YieldTermStructure ts = new FlatForward(today,
                new Handle<SimpleQuote>(new SimpleQuote(0.05)), new Actual365Fixed());

        final LongstaffSchwartzPathPricer<Path, Double> p =
                new LongstaffSchwartzPathPricer<Path, Double>(
                        grid, new SyntheticPricer(100.0), ts);

        // Build a few distinct paths
        final Path path1 = new Path(grid, new double[] { 100, 105, 110, 108, 115 });
        final Path path2 = new Path(grid, new double[] { 100,  95,  90,  98, 105 });
        final Path path3 = new Path(grid, new double[] { 100, 102, 101, 105, 108 });

        // Calibration phase: op() returns 0
        assertEquals(0.0, p.op(path1), 0.0);
        assertEquals(0.0, p.op(path2), 0.0);
        assertEquals(0.0, p.op(path3), 0.0);
    }

    /**
     * Synthetic multi-asset pricer: 2-asset American max-call payoff
     * = max(max(S1,S2) - strike, 0).
     *
     * <p>State at step t = Array{S1[t], S2[t]}. Basis: dim=2, order=2
     * Monomial multi-path basis (6 functions: 1, S1, S2, S1², S1·S2, S2²).
     * Mirrors the C++ test-suite/mclongstaffschwartzengine.cpp
     * AmericanMaxPathPricer (Phase MC-extras carry-forward).
     */
    private static final class SyntheticMultiPricer
            implements EarlyExercisePathPricer<MultiPath, Array> {
        private final double strike;
        private final List<Ops.ObjectToDouble<Array>> basis;
        SyntheticMultiPricer(final double strike) {
            this.strike = strike;
            this.basis = LsmBasisSystem.multiPathBasisSystem(2, 2,
                    LsmBasisSystem.PolynomialType.Monomial);
        }
        @Override public double operator(final MultiPath path, final int t) {
            double m = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < path.assetNumber(); ++i) {
                m = Math.max(m, path.get(i).get(t));
            }
            return Math.max(m - strike, 0.0);
        }
        @Override public Array state(final MultiPath path, final int t) {
            final double[] tmp = new double[path.assetNumber()];
            for (int i = 0; i < path.assetNumber(); ++i) {
                tmp[i] = path.get(i).get(t);
            }
            return new Array(tmp);
        }
        @Override public List<? extends Ops.Op<Array, Double>> basisSystem() {
            // ObjectToDouble<Array> is functionally identical to Op<Array, Double>;
            // adapt one to the other so the LSPP generic stays clean.
            final List<Ops.Op<Array, Double>> adapted =
                    new ArrayList<>(basis.size());
            for (final Ops.ObjectToDouble<Array> b : basis) {
                adapted.add(new Ops.Op<Array, Double>() {
                    @Override public Double op(final Array a) { return b.op(a); }
                });
            }
            return adapted;
        }
    }

    @Test
    public void testMultiPathCalibrateAndPrice() {
        // Validates that LongstaffSchwartzPathPricer<MultiPath, Array>
        // (Phase MC-extras WI-2) correctly routes through the new
        // multi-variate GeneralLinearLeastSquares constructor at every
        // backward induction step, and produces a positive price for an
        // ITM 2-asset American max call.
        final Date today = new Date(15, Month.January, 2026);
        new org.jquantlib.Settings().setEvaluationDate(today);
        final TimeGrid grid = new TimeGrid(1.0, 4); // 5 points: 0, 0.25, ..., 1.0
        final YieldTermStructure ts = new FlatForward(today,
                new Handle<SimpleQuote>(new SimpleQuote(0.05)), new Actual365Fixed());

        final LongstaffSchwartzPathPricer<MultiPath, Array> p =
                new LongstaffSchwartzPathPricer<MultiPath, Array>(
                        grid, new SyntheticMultiPricer(100.0), ts);

        final java.util.Random rand = new java.util.Random(42L);
        for (int i = 0; i < 200; ++i) {
            final double[] s1 = new double[5];
            final double[] s2 = new double[5];
            s1[0] = 100.0;
            s2[0] = 100.0;
            for (int t = 1; t < 5; ++t) {
                s1[t] = s1[t - 1] * (1.0 + 0.10 * (rand.nextDouble() - 0.5));
                s2[t] = s2[t - 1] * (1.0 + 0.10 * (rand.nextDouble() - 0.5));
            }
            final List<Path> components = new ArrayList<>(2);
            components.add(new Path(grid, s1));
            components.add(new Path(grid, s2));
            final MultiPath mp = new MultiPath(components);
            // Calibration phase: op() returns 0
            assertEquals(0.0, p.op(mp), 0.0);
        }

        // This exercise the new multi-variate calibrate path
        p.calibrate();

        // Price an ITM 2-asset path: both assets terminal-ITM
        final List<Path> itmComps = new ArrayList<>(2);
        itmComps.add(new Path(grid, new double[] { 100, 102, 105, 108, 115 }));
        itmComps.add(new Path(grid, new double[] { 100, 101, 103, 106, 110 }));
        final MultiPath itm = new MultiPath(itmComps);
        final double price = p.op(itm);
        assertTrue("post-calibration price must be > 0 for ITM multi-path, was " + price,
                price > 0.0);
        assertTrue("exerciseProbability in [0,1]",
                p.exerciseProbability() >= 0.0 && p.exerciseProbability() <= 1.0);
    }

    @Test
    public void testCalibrateThenPriceProducesNonZero() {
        final Date today = new Date(15, Month.January, 2026);
        new org.jquantlib.Settings().setEvaluationDate(today);
        final TimeGrid grid = new TimeGrid(1.0, 4);
        final YieldTermStructure ts = new FlatForward(today,
                new Handle<SimpleQuote>(new SimpleQuote(0.05)), new Actual365Fixed());

        final LongstaffSchwartzPathPricer<Path, Double> p =
                new LongstaffSchwartzPathPricer<Path, Double>(
                        grid, new SyntheticPricer(100.0), ts);

        // Build a small calibration set with diverse trajectories so the
        // regression has full rank.
        final java.util.Random rand = new java.util.Random(123L);
        for (int i = 0; i < 200; ++i) {
            final double[] vals = new double[5];
            vals[0] = 100.0;
            for (int t = 1; t < 5; ++t) {
                // simple correlated random walk
                vals[t] = vals[t - 1] * (1.0 + 0.01 * (rand.nextDouble() - 0.5) * 2 * 5);
            }
            p.op(new Path(grid, vals));
        }

        p.calibrate();

        // Pricing phase: feed an ITM-at-terminal path; expect a positive
        // discounted price.
        final Path priceItm = new Path(grid, new double[] { 100, 102, 105, 108, 115 });
        final double price = p.op(priceItm);
        assertTrue("post-calibration price must be positive for an ITM path, was " + price,
                price > 0.0);

        // exerciseProbability must be in [0,1]
        assertTrue("exerciseProbability in [0,1]",
                p.exerciseProbability() >= 0.0 && p.exerciseProbability() <= 1.0);
    }
}
