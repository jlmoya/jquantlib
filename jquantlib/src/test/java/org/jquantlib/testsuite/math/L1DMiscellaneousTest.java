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
 */
package org.jquantlib.testsuite.math;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.BiCGStab;
import org.jquantlib.math.matrixutilities.BiCGStabResult;
import org.jquantlib.math.matrixutilities.GMRES;
import org.jquantlib.math.matrixutilities.GMRESResult;
import org.jquantlib.math.matrixutilities.SalvagingAlgorithm;
import org.jquantlib.math.matrixutilities.PseudoSqrt;
import org.jquantlib.math.ode.AdaptiveRungeKutta;
import org.jquantlib.math.ode.OdeFctWrapper;
import org.jquantlib.math.statistics.DoublingConvergenceSteps;
import org.jquantlib.math.statistics.StatsHolder;
import org.junit.Test;

/**
 * Tests for L1-D miscellaneous batch: DoublingConvergenceSteps, StatsHolder,
 * BiCGStabResult, GMRESResult, SalvagingAlgorithm, OdeFctWrapper.
 *
 * <p>Phase 2 L1-D port.
 */
public class L1DMiscellaneousTest {

    public L1DMiscellaneousTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Test
    public void testDoublingConvergenceStepsSequence() {
        final DoublingConvergenceSteps rule = new DoublingConvergenceSteps();
        assertEquals(1, rule.initialSamples());
        assertEquals(3, rule.nextSamples(1));     // 2*1 + 1
        assertEquals(7, rule.nextSamples(3));     // 2*3 + 1
        assertEquals(15, rule.nextSamples(7));    // 2*7 + 1
        assertEquals(2001, rule.nextSamples(1000));
    }

    @Test
    public void testStatsHolderRoundTrip() {
        final StatsHolder holder = new StatsHolder(1.5, 0.25);
        assertEquals(1.5, holder.mean(), 0.0);
        assertEquals(0.25, holder.standardDeviation(), 0.0);
    }

    @Test
    public void testBiCGStabResultRecord() {
        final Array x = new Array(new double[] { 1.0, 2.0, 3.0 });
        final BiCGStabResult result = new BiCGStabResult(7, 1.0e-9, x);
        assertEquals(7, result.iterations());
        assertEquals(1.0e-9, result.error(), 0.0);
        assertEquals(x, result.x());

        // Conversion helper
        final BiCGStab.Result innerResult = new BiCGStab.Result(7, 1.0e-9, x);
        final BiCGStabResult wrapped = BiCGStabResult.from(innerResult);
        assertEquals(7, wrapped.iterations());
    }

    @Test
    public void testGMRESResultRecord() {
        final List<Double> errors = new ArrayList<>();
        errors.add(1.0);
        errors.add(0.5);
        errors.add(0.1);
        final Array x = new Array(new double[] { 1.0, 0.0 });
        final GMRESResult result = new GMRESResult(errors, x);
        assertEquals(3, result.errors().size());
        assertEquals(x, result.x());

        final GMRES.Result innerResult = new GMRES.Result(errors, x);
        final GMRESResult wrapped = GMRESResult.from(innerResult);
        assertEquals(3, wrapped.errors().size());
    }

    @Test
    public void testSalvagingAlgorithmNestedRoundTrip() {
        // Each top-level enum value must round-trip through the nested form.
        for ( final SalvagingAlgorithm v : SalvagingAlgorithm.values() ) {
            final PseudoSqrt.SalvagingAlgorithm nested = v.toNested();
            final SalvagingAlgorithm back = SalvagingAlgorithm.fromNested(nested);
            assertEquals(v, back);
        }
    }

    @Test
    public void testOdeFctWrapperReal() {
        // y' = -y, y(0)=1 => y(1) = 1/e
        final AdaptiveRungeKutta integrator = new AdaptiveRungeKutta(1.0e-9, 1.0e-4);
        final AdaptiveRungeKutta.OdeFct1d scalarOde = (t, y) -> -y;
        // First solve via the inline OdeFct1d-aware solve
        final double y1 = integrator.solve(scalarOde, 1.0, 0.0, 1.0);

        // Then solve via the explicit OdeFctWrapper adapter
        final AdaptiveRungeKutta.OdeFct vectorOde = OdeFctWrapper.wrap(scalarOde);
        final double[] y2 = integrator.solve(vectorOde, new double[] { 1.0 }, 0.0, 1.0);

        assertEquals("OdeFctWrapper round-trip", y1, y2[0], 1.0e-10);
        assertEquals("y(1) = 1/e", 1.0 / Math.E, y2[0], 1.0e-7);
        assertNotNull(vectorOde);
    }
}
