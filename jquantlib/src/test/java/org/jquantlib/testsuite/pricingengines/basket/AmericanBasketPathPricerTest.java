/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.pricingengines.basket;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.instruments.MaxBasketPayoff;
import org.jquantlib.instruments.MinBasketPayoff;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.math.Ops;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.montecarlo.LsmBasisSystem;
import org.jquantlib.methods.montecarlo.MultiPath;
import org.jquantlib.methods.montecarlo.Path;
import org.jquantlib.pricingengines.basket.AmericanBasketPathPricer;
import org.jquantlib.time.TimeGrid;
import org.junit.Test;

/**
 * Structural tests for {@link AmericanBasketPathPricer} (Phase 4i.5b WI-1).
 *
 * <p>Validates:
 * <ul>
 *   <li>{@code state(path,t)} returns {@code [S_i[t]/strike, ...]}
 *       (scaling by reciprocal of strike since base is StrikedTypePayoff)</li>
 *   <li>{@code operator(path,t)} returns the unscaled basket payoff</li>
 *   <li>{@code basisSystem()} returns multipath basis + payoff functional</li>
 *   <li>Polynomial-type guard rejects Legendre / Chebyshev families</li>
 *   <li>Non-basket payoff guard fires</li>
 * </ul>
 */
public class AmericanBasketPathPricerTest {

    public AmericanBasketPathPricerTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static MultiPath makeMultiPath(final TimeGrid grid,
                                           final double[]... paths) {
        final List<Path> components = new ArrayList<Path>(paths.length);
        for (final double[] p : paths) {
            components.add(new Path(grid, p));
        }
        return new MultiPath(components);
    }


    @Test
    public void testStateScalesByReciprocalStrike() {
        final TimeGrid grid = new TimeGrid(1.0, 4); // 5 points
        final MultiPath mp = makeMultiPath(grid,
                new double[] { 100, 102, 105, 108, 115 },
                new double[] { 100,  95,  90,  98, 105 });
        final double K = 100.0;
        final MaxBasketPayoff mp2 = new MaxBasketPayoff(
                new PlainVanillaPayoff(Option.Type.Call, K));
        final AmericanBasketPathPricer p =
                new AmericanBasketPathPricer(2, mp2);

        // state(path,0) == [1.0, 1.0]
        final Array s0 = p.state(mp, 0);
        assertEquals(2, s0.size());
        assertEquals(1.0, s0.get(0), 1e-15);
        assertEquals(1.0, s0.get(1), 1e-15);

        // state(path,4) == [115/100, 105/100]
        final Array s4 = p.state(mp, 4);
        assertEquals(1.15, s4.get(0), 1e-15);
        assertEquals(1.05, s4.get(1), 1e-15);
    }

    @Test
    public void testOperatorMaxCallPayoff() {
        final TimeGrid grid = new TimeGrid(1.0, 4);
        final MultiPath mp = makeMultiPath(grid,
                new double[] { 100, 102, 105, 108, 115 },
                new double[] { 100,  95,  90,  98, 105 });
        // Max basket call K=100: max(S1,S2) at t=4 is 115; payoff = 15
        final AmericanBasketPathPricer call = new AmericanBasketPathPricer(2,
                new MaxBasketPayoff(new PlainVanillaPayoff(Option.Type.Call, 100.0)));
        assertEquals(15.0, call.operator(mp, 4), 1e-12);
        // at t=2: max(105, 90) = 105; payoff = 5
        assertEquals(5.0, call.operator(mp, 2), 1e-12);
        // at t=1: max(102, 95) = 102; payoff = 2
        assertEquals(2.0, call.operator(mp, 1), 1e-12);
    }

    @Test
    public void testOperatorMinPutPayoff() {
        final TimeGrid grid = new TimeGrid(1.0, 4);
        final MultiPath mp = makeMultiPath(grid,
                new double[] { 100, 102, 105, 108, 115 },
                new double[] { 100,  95,  90,  98, 105 });
        // Min basket put K=110: min(S1,S2) at t=4 is 105; payoff = max(110-105,0) = 5
        final AmericanBasketPathPricer put = new AmericanBasketPathPricer(2,
                new MinBasketPayoff(new PlainVanillaPayoff(Option.Type.Put, 110.0)));
        assertEquals(5.0, put.operator(mp, 4), 1e-12);
        // at t=2: min(105, 90) = 90; payoff = max(110-90,0) = 20
        assertEquals(20.0, put.operator(mp, 2), 1e-12);
    }

    @Test
    public void testBasisSystemSizeIncludesPayoffFunctional() {
        // dim=2, order=2 Monomial: 6 functions; +1 payoff functional -> 7 total
        final AmericanBasketPathPricer p = new AmericanBasketPathPricer(2,
                new MaxBasketPayoff(new PlainVanillaPayoff(Option.Type.Call, 100.0)));
        final List<? extends Ops.Op<Array, Double>> b = p.basisSystem();
        assertEquals(7, b.size());

        // The last entry should compute the payoff at the supplied scaled state.
        // With state = [1.15, 1.05], the unscaled max is max(115, 105)=115, payoff = 15.
        final Array state = new Array(new double[] { 1.15, 1.05 });
        final double expectedPayoff = 15.0;
        assertEquals(expectedPayoff, b.get(b.size() - 1).op(state), 1e-12);
    }

    @Test
    public void testRejectsLegendreOrChebyshev1st() {
        // Legendre / Chebyshev: rejected per C++ QL_REQUIRE.
        try {
            new AmericanBasketPathPricer(2,
                    new MaxBasketPayoff(new PlainVanillaPayoff(Option.Type.Call, 100.0)),
                    2, LsmBasisSystem.PolynomialType.Legendre);
            fail("expected RuntimeException for Legendre type");
        } catch (final RuntimeException e) {
            assertTrue(e.getMessage().toLowerCase().contains("polynomial"));
        }

        try {
            new AmericanBasketPathPricer(2,
                    new MaxBasketPayoff(new PlainVanillaPayoff(Option.Type.Call, 100.0)),
                    2, LsmBasisSystem.PolynomialType.Chebyshev);
            fail("expected RuntimeException for Chebyshev type");
        } catch (final RuntimeException e) {
            assertTrue(e.getMessage().toLowerCase().contains("polynomial"));
        }
    }

    @Test
    public void testRejectsNonBasketPayoff() {
        try {
            // PlainVanillaPayoff is not a BasketPayoff -> ctor must throw.
            new AmericanBasketPathPricer(2,
                    new PlainVanillaPayoff(Option.Type.Call, 100.0));
            fail("expected RuntimeException for non-basket payoff");
        } catch (final RuntimeException e) {
            assertTrue(e.getMessage().toLowerCase().contains("basket"));
        }
    }
}
