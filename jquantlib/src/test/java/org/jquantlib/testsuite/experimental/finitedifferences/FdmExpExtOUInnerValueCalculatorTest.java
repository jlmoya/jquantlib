/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Phase 4n — FdmExpExtOUInnerValueCalculator smoke tests.

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
package org.jquantlib.testsuite.experimental.finitedifferences;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.experimental.finitedifferences.FdmExpExtOUInnerValueCalculator;
import org.jquantlib.experimental.finitedifferences.FdmExpExtOUInnerValueCalculator.ShapePoint;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.methods.finitedifferences.meshers.Fdm1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesherComposite;
import org.jquantlib.methods.finitedifferences.meshers.Uniform1dMesher;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpIterator;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Smoke tests for {@link FdmExpExtOUInnerValueCalculator}.
 */
public class FdmExpExtOUInnerValueCalculatorTest {

    private static final double TIGHT = 1e-12;

    public FdmExpExtOUInnerValueCalculatorTest() {
        QL.info("::::: " + getClass().getSimpleName() + " :::::");
    }

    @Test
    public void noShapeFunctionEvaluatesPayoffAtExpLocation() {
        // 1D log-mesh from log(50) to log(150), 5 points.
        // Strike 100 call. Inner value at u = log(120) is max(exp(u) - 100, 0) = 20.
        final Uniform1dMesher mesh = new Uniform1dMesher(Math.log(50), Math.log(150), 5);
        final FdmMesherComposite mc = new FdmMesherComposite(
                Collections.<Fdm1dMesher>singletonList(mesh));
        final PlainVanillaPayoff call = new PlainVanillaPayoff(Option.Type.Call, 100.0);
        final FdmExpExtOUInnerValueCalculator calc = new FdmExpExtOUInnerValueCalculator(
                call, mc);

        // Direct evaluation: grid location 0 = log(50), so payoff = max(50 - 100, 0) = 0
        final FdmLinearOpIterator iter = mc.layout().begin();
        assertEquals(0.0, calc.innerValue(iter, 0.0), TIGHT);
    }

    @Test
    public void shapeFunctionAddsTimeDependentShift() {
        // shape = [(0.5, log(2)), (1.0, log(3))]; at t=1.0, lower-bound on
        // (1.0 - sqrt(eps)) returns the (0.5, log(2)) entry; payoff sees
        // exp(log(2) + u). Verify on u = 0 ⇒ S = 2.
        final Uniform1dMesher mesh = new Uniform1dMesher(0.0, 1.0, 3);
        final FdmMesherComposite mc = new FdmMesherComposite(
                Collections.<Fdm1dMesher>singletonList(mesh));
        final PlainVanillaPayoff call = new PlainVanillaPayoff(Option.Type.Call, 1.0);
        final List<ShapePoint> shape = Arrays.asList(
                new ShapePoint(0.5, Math.log(2.0)),
                new ShapePoint(1.0, Math.log(3.0)));
        final FdmExpExtOUInnerValueCalculator calc = new FdmExpExtOUInnerValueCalculator(
                call, mc, shape, 0);
        // First grid point u = 0; at t=1.0 with the lower-bound trick, f = log(3),
        // payoff = max(exp(log(3)+0) - 1, 0) = 2.
        final FdmLinearOpIterator iter = mc.layout().begin();
        assertEquals(2.0, calc.innerValue(iter, 1.0), TIGHT);
    }

    @Test
    public void avgInnerValueDelegatesToInnerValue() {
        final Uniform1dMesher mesh = new Uniform1dMesher(Math.log(50), Math.log(150), 3);
        final FdmMesherComposite mc = new FdmMesherComposite(
                Collections.<Fdm1dMesher>singletonList(mesh));
        final PlainVanillaPayoff call = new PlainVanillaPayoff(Option.Type.Call, 100.0);
        final FdmExpExtOUInnerValueCalculator calc = new FdmExpExtOUInnerValueCalculator(
                call, mc);
        final FdmLinearOpIterator iter = mc.layout().begin();
        assertEquals(calc.innerValue(iter, 0.0), calc.avgInnerValue(iter, 0.0), TIGHT);
    }
}
