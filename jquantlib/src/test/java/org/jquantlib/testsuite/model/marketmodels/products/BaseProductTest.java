/*
Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k A.1-A.2.

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

package org.jquantlib.testsuite.model.marketmodels.products;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.jquantlib.QL;
import org.jquantlib.model.marketmodels.CurveState;
import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.jquantlib.model.marketmodels.MarketModelMultiProduct;
import org.jquantlib.model.marketmodels.products.MultiProductMultiStep;
import org.jquantlib.model.marketmodels.products.MultiProductOneStep;
import org.junit.Test;

/**
 * Tests for the abstract product base classes
 * {@link MultiProductMultiStep} and {@link MultiProductOneStep}.
 *
 * <p>Verifies that the {@code evolution()} structure (rateTimes,
 * evolutionTimes) and {@code suggestedNumeraires()} match the C++
 * reference (v1.42.1).
 */
public class BaseProductTest {

    public BaseProductTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static final double TOL = 1e-12;

    /** Concrete stub for testing — emits no cash flows. */
    private static final class StubMultiStep extends MultiProductMultiStep {
        StubMultiStep(final double[] rateTimes) { super(rateTimes); }
        @Override public double[] possibleCashFlowTimes() { return new double[0]; }
        @Override public int numberOfProducts() { return 0; }
        @Override public int maxNumberOfCashFlowsPerProductPerStep() { return 0; }
        @Override public void reset() { /* no state */ }
        @Override public boolean nextTimeStep(final CurveState s, final int[] n,
                                              final MarketModelMultiProduct.CashFlow[][] g) {
            return true;
        }
        @Override public MarketModelMultiProduct clone() { return this; }
    }

    private static final class StubOneStep extends MultiProductOneStep {
        StubOneStep(final double[] rateTimes) { super(rateTimes); }
        @Override public double[] possibleCashFlowTimes() { return new double[0]; }
        @Override public int numberOfProducts() { return 0; }
        @Override public int maxNumberOfCashFlowsPerProductPerStep() { return 0; }
        @Override public void reset() { /* no state */ }
        @Override public boolean nextTimeStep(final CurveState s, final int[] n,
                                              final MarketModelMultiProduct.CashFlow[][] g) {
            return true;
        }
        @Override public MarketModelMultiProduct clone() { return this; }
    }

    @Test
    public void testMultiStepEvolutionFiveRates() {
        // C++: rateTimes = {1,2,3,4,5,6} → evolutionTimes = {1,2,3,4,5}
        // suggestedNumeraires = MoneyMarketPlus(1) → {1,2,3,4,5}
        final double[] rateTimes = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0};
        final StubMultiStep p = new StubMultiStep(rateTimes);

        final EvolutionDescription ev = p.evolution();
        assertEquals(5, ev.numberOfRates());
        assertEquals(5, ev.numberOfSteps());
        assertArrayEquals(rateTimes, ev.rateTimes(), TOL);
        assertArrayEquals(new double[]{1.0, 2.0, 3.0, 4.0, 5.0}, ev.evolutionTimes(), TOL);

        final int[] num = p.suggestedNumeraires();
        assertArrayEquals(new int[]{1, 2, 3, 4, 5}, num);
    }

    @Test
    public void testOneStepEvolutionFiveRates() {
        // C++: rateTimes = {1,2,3,4,5,6} → evolutionTimes = {5} (rateTimes[N-2])
        // suggestedNumeraires = terminal → {5} (rateTimes.size()-1)
        final double[] rateTimes = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0};
        final StubOneStep p = new StubOneStep(rateTimes);

        final EvolutionDescription ev = p.evolution();
        assertEquals(5, ev.numberOfRates());
        assertEquals(1, ev.numberOfSteps());
        assertArrayEquals(rateTimes, ev.rateTimes(), TOL);
        assertArrayEquals(new double[]{5.0}, ev.evolutionTimes(), TOL);

        final int[] num = p.suggestedNumeraires();
        assertArrayEquals(new int[]{5}, num);
    }

    @Test(expected = RuntimeException.class)
    public void testMultiStepRejectsTooFewRateTimes() {
        new StubMultiStep(new double[]{1.0});
    }

    @Test(expected = RuntimeException.class)
    public void testOneStepRejectsTooFewRateTimes() {
        new StubOneStep(new double[]{1.0});
    }
}
