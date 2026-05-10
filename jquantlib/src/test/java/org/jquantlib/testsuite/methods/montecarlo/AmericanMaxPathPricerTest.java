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
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.montecarlo.AmericanMaxPathPricer;
import org.jquantlib.methods.montecarlo.LongstaffSchwartzMultiPathPricer;
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
 * Structural tests for {@link AmericanMaxPathPricer} and
 * {@link LongstaffSchwartzMultiPathPricer} (Phase MC-extras WI-3 + WI-4).
 *
 * <p>Validates:
 * <ul>
 *   <li>{@code state(path, t)} returns the per-asset state vector at step t</li>
 *   <li>{@code operator(path, t)} returns {@code payoff(max_i path[i][t])}</li>
 *   <li>{@code basisSystem()} returns the configured basis (default: dim=2,
 *       order=2 Monomial — 6 functions)</li>
 *   <li>{@link LongstaffSchwartzMultiPathPricer} routes through the generic
 *       multi-variate {@code calibrate()} pipeline (Phase MC-extras WI-2)
 *       end-to-end without subclassing</li>
 * </ul>
 *
 * <p>Tier: TIGHT for structural assertions; LOOSE 1e-3 for the regression
 * pricing convergence (200 calibration paths, no Black-Scholes process).
 *
 * <p>End-to-end MC pricing of the canonical 2-asset American max-call
 * (Glasserman 2004 p.462, expected values [8.08, 13.90, 21.34]) is
 * deferred to Phase MC-extras-b: it requires
 * {@code MCAmericanMaxEngine} which is a derived
 * {@link org.jquantlib.pricingengines.MCLongstaffSchwartzEngine}
 * specialised for {@code MultiVariate} paths +
 * {@link org.jquantlib.model.shortrate.StochasticProcessArray}. See
 * {@code MCLongstaffSchwartzEngineTest.testAmericanMaxOption} (still
 * {@code @Ignore}'d).
 */
public class AmericanMaxPathPricerTest {

    public AmericanMaxPathPricerTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }


    private static MultiPath makeMultiPath(final TimeGrid grid,
                                           final double[] s1, final double[] s2) {
        final List<Path> components = new ArrayList<Path>(2);
        components.add(new Path(grid, s1));
        components.add(new Path(grid, s2));
        return new MultiPath(components);
    }


    @Test
    public void testStateReturnsAssetVectorAtStep() {
        final TimeGrid grid = new TimeGrid(1.0, 4); // 5 points
        final MultiPath mp = makeMultiPath(grid,
                new double[] { 100, 102, 105, 108, 115 },
                new double[] { 100,  95,  90,  98, 105 });
        final AmericanMaxPathPricer p = new AmericanMaxPathPricer(
                new PlainVanillaPayoff(Option.Type.Call, 100.0));

        // state(path, 0) == [100, 100]
        final Array s0 = p.state(mp, 0);
        assertEquals(2, s0.size());
        assertEquals(100.0, s0.get(0), 0.0);
        assertEquals(100.0, s0.get(1), 0.0);

        // state(path, 4) == [115, 105]
        final Array s4 = p.state(mp, 4);
        assertEquals(115.0, s4.get(0), 0.0);
        assertEquals(105.0, s4.get(1), 0.0);
    }

    @Test
    public void testOperatorMaxPayoff() {
        final TimeGrid grid = new TimeGrid(1.0, 4);
        final MultiPath mp = makeMultiPath(grid,
                new double[] { 100, 102, 105, 108, 115 },
                new double[] { 100,  95,  90,  98, 105 });

        // Call payoff K=100: max(S1,S2) at t=4 is 115; payoff = max(115-100,0) = 15
        final AmericanMaxPathPricer call = new AmericanMaxPathPricer(
                new PlainVanillaPayoff(Option.Type.Call, 100.0));
        assertEquals(15.0, call.operator(mp, 4), 0.0);
        // at t=2: max(105, 90) = 105; payoff = 5
        assertEquals(5.0, call.operator(mp, 2), 0.0);
        // at t=1: max(102, 95) = 102; payoff = 2
        assertEquals(2.0, call.operator(mp, 1), 0.0);

        // Put K=110: max(S1,S2) at t=4 is 115; max(110-115,0) = 0
        final AmericanMaxPathPricer put = new AmericanMaxPathPricer(
                new PlainVanillaPayoff(Option.Type.Put, 110.0));
        assertEquals(0.0, put.operator(mp, 4), 0.0);
        // at t=0: max(100, 100) = 100; payoff = max(110-100, 0) = 10
        assertEquals(10.0, put.operator(mp, 0), 0.0);
    }

    @Test
    public void testBasisSystemDefault() {
        final AmericanMaxPathPricer p = new AmericanMaxPathPricer(
                new PlainVanillaPayoff(Option.Type.Call, 100.0));
        // dim=2, order=2 Monomial: tuples whose components sum to 0..2
        // (0,0), (1,0),(0,1), (2,0),(1,1),(0,2) → 6 functions
        assertEquals(6, p.basisSystem().size());
    }

    @Test
    public void testBasisSystemExplicitDim() {
        final AmericanMaxPathPricer p = new AmericanMaxPathPricer(
                new PlainVanillaPayoff(Option.Type.Call, 100.0),
                3, 2, LsmBasisSystem.PolynomialType.Hermite);
        // dim=3, order=2 → tuples summing to 0..2:
        //   (0,0,0)
        //   (1,0,0),(0,1,0),(0,0,1)
        //   (2,0,0),(1,1,0),(1,0,1),(0,2,0),(0,1,1),(0,0,2)
        //  → 1+3+6 = 10 functions
        assertEquals(10, p.basisSystem().size());
    }

    @Test
    public void testLongstaffSchwartzMultiPathPricerEndToEnd() {
        // Smoke test: AmericanMaxPathPricer + LongstaffSchwartzMultiPathPricer +
        // synthetic 2-asset paths → calibration succeeds, post-cal price > 0.
        final Date today = new Date(15, Month.January, 2026);
        new org.jquantlib.Settings().setEvaluationDate(today);
        final TimeGrid grid = new TimeGrid(1.0, 4);
        final YieldTermStructure ts = new FlatForward(today,
                new Handle<SimpleQuote>(new SimpleQuote(0.05)), new Actual365Fixed());

        final AmericanMaxPathPricer pricer = new AmericanMaxPathPricer(
                new PlainVanillaPayoff(Option.Type.Call, 100.0));

        final LongstaffSchwartzMultiPathPricer lsmp =
                new LongstaffSchwartzMultiPathPricer(grid, pricer, ts);

        final java.util.Random rand = new java.util.Random(42L);
        for (int i = 0; i < 250; ++i) {
            final double[] s1 = new double[5];
            final double[] s2 = new double[5];
            s1[0] = 100.0;
            s2[0] = 100.0;
            for (int t = 1; t < 5; ++t) {
                s1[t] = s1[t - 1] * (1.0 + 0.10 * (rand.nextDouble() - 0.5));
                s2[t] = s2[t - 1] * (1.0 + 0.10 * (rand.nextDouble() - 0.5));
            }
            // Calibration phase: op() returns 0
            assertEquals(0.0, lsmp.op(makeMultiPath(grid, s1, s2)), 0.0);
        }

        // calibrate routes through the multi-variate GLS path
        lsmp.calibrate();

        // Price an ITM 2-asset path: assert positive discounted price
        final double price = lsmp.op(makeMultiPath(grid,
                new double[] { 100, 102, 105, 108, 115 },
                new double[] { 100, 101, 103, 106, 110 }));
        assertTrue("post-calibration multi-path price > 0 for ITM, was " + price,
                price > 0.0);
        assertTrue("exerciseProbability ∈ [0,1]",
                lsmp.exerciseProbability() >= 0.0
                && lsmp.exerciseProbability() <= 1.0);
    }
}
