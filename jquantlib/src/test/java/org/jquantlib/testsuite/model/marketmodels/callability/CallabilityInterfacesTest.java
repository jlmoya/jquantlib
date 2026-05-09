/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k B.1-B.4.

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
 */

package org.jquantlib.testsuite.model.marketmodels.callability;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jquantlib.model.marketmodels.CurveState;
import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.jquantlib.model.marketmodels.MarketModelMultiProduct;
import org.jquantlib.model.marketmodels.callability.MarketModelBasisSystem;
import org.jquantlib.model.marketmodels.callability.MarketModelExerciseValue;
import org.jquantlib.model.marketmodels.callability.MarketModelNodeDataProvider;
import org.jquantlib.model.marketmodels.callability.MarketModelParametricExercise;
import org.junit.Test;

/**
 * Compile-level smoke test for the four callability interfaces (Phase 3k
 * B.1-B.4). Verifies that anonymous implementations satisfy each contract
 * without compiler errors and that {@code numberOfData()} default routing
 * works as designed.
 */
public class CallabilityInterfacesTest {

    @Test
    public void exerciseValueAnonymousImplCompiles() {
        final MarketModelExerciseValue ev = new MarketModelExerciseValue() {
            @Override public int numberOfExercises() { return 0; }
            @Override public EvolutionDescription evolution() { return new EvolutionDescription(); }
            @Override public double[] possibleCashFlowTimes() { return new double[0]; }
            @Override public void nextStep(final CurveState s) {}
            @Override public void reset() {}
            @Override public boolean[] isExerciseTime() { return new boolean[0]; }
            @Override public MarketModelMultiProduct.CashFlow value(final CurveState s) {
                return new MarketModelMultiProduct.CashFlow();
            }
            @Override public MarketModelExerciseValue clone() { return this; }
        };
        assertEquals(0, ev.numberOfExercises());
        assertEquals(0.0, ev.value(null).amount, 0.0);
    }

    @Test
    public void basisSystemDefaultNumberOfDataMatchesNumberOfFunctions() {
        final int[] expected = {2, 3, 1};
        final MarketModelBasisSystem bs = new MarketModelBasisSystem() {
            @Override public int numberOfExercises() { return expected.length; }
            @Override public int[] numberOfFunctions() { return expected; }
            @Override public EvolutionDescription evolution() { return new EvolutionDescription(); }
            @Override public void nextStep(final CurveState s) {}
            @Override public void reset() {}
            @Override public boolean[] isExerciseTime() { return new boolean[0]; }
            @Override public void values(final CurveState s, final double[] r) {}
            @Override public MarketModelBasisSystem clone() { return this; }
        };
        assertEquals(expected.length, bs.numberOfData().length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], bs.numberOfData()[i]);
            assertEquals(expected[i], bs.numberOfFunctions()[i]);
        }
    }

    @Test
    public void parametricExerciseDefaultNumberOfDataMatchesNumberOfVariables() {
        final int[] vars = {1, 1, 1};
        final int[] params = {1, 1, 1};
        final MarketModelParametricExercise pe = new MarketModelParametricExercise() {
            @Override public int numberOfExercises() { return vars.length; }
            @Override public int[] numberOfVariables() { return vars; }
            @Override public int[] numberOfParameters() { return params; }
            @Override public boolean exercise(final int n, final double[] p, final double[] v) { return false; }
            @Override public void guess(final int n, final double[] p) {}
            @Override public EvolutionDescription evolution() { return new EvolutionDescription(); }
            @Override public void nextStep(final CurveState s) {}
            @Override public void reset() {}
            @Override public boolean[] isExerciseTime() { return new boolean[0]; }
            @Override public void values(final CurveState s, final double[] r) {}
            @Override public MarketModelParametricExercise clone() { return this; }
        };
        assertEquals(vars.length, pe.numberOfData().length);
    }

    @Test
    public void nodeDataProviderAnonymousImplCompiles() {
        final MarketModelNodeDataProvider ndp = new MarketModelNodeDataProvider() {
            @Override public int numberOfExercises() { return 1; }
            @Override public int[] numberOfData() { return new int[]{1}; }
            @Override public EvolutionDescription evolution() { return new EvolutionDescription(); }
            @Override public void nextStep(final CurveState s) {}
            @Override public void reset() {}
            @Override public boolean[] isExerciseTime() { return new boolean[]{true}; }
            @Override public void values(final CurveState s, final double[] r) {}
        };
        assertEquals(1, ndp.numberOfExercises());
        assertTrue(ndp.isExerciseTime()[0]);
    }
}
