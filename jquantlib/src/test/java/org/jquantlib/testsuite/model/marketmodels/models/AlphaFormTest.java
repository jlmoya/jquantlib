/*
 Copyright (C) 2026 Jose Moya

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.
 */

package org.jquantlib.testsuite.model.marketmodels.models;

import static org.junit.Assert.assertEquals;

import org.jquantlib.QL;
import org.jquantlib.model.marketmodels.models.AlphaForm;
import org.jquantlib.model.marketmodels.models.AlphaFormInverseLinear;
import org.jquantlib.model.marketmodels.models.AlphaFormLinearHyperbolic;
import org.junit.Test;

/**
 * Tests for {@link AlphaFormInverseLinear} and {@link AlphaFormLinearHyperbolic}.
 *
 * <p>Phase 3j B.1 (Track B). Cross-validated against C++ formulas in
 * {@code ql/models/marketmodels/models/alphaformconcrete.cpp}.
 */
public class AlphaFormTest {

    public AlphaFormTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static final double TOL = 1e-12;

    @Test
    public void testInverseLinear() {
        final double[] times = {0.5, 1.0, 1.5, 2.0};
        final AlphaForm a = new AlphaFormInverseLinear(times, 0.5);
        // value(i) = 1 / (1 + 0.5 * times[i])
        assertEquals(1.0 / (1.0 + 0.5 * 0.5), a.apply(0), TOL);
        assertEquals(1.0 / (1.0 + 0.5 * 1.0), a.apply(1), TOL);
        assertEquals(1.0 / (1.0 + 0.5 * 1.5), a.apply(2), TOL);
        assertEquals(1.0 / (1.0 + 0.5 * 2.0), a.apply(3), TOL);

        // alpha = 0 → all values 1.0
        a.setAlpha(0.0);
        assertEquals(1.0, a.apply(0), TOL);
        assertEquals(1.0, a.apply(3), TOL);
    }

    @Test
    public void testLinearHyperbolic() {
        final double[] times = {0.5, 1.0, 1.5, 2.0};
        final AlphaForm a = new AlphaFormLinearHyperbolic(times, 0.3);
        for (int i = 0; i < times.length; ++i) {
            final double at = 0.3 * times[i];
            final double expected = Math.sqrt(1.0 + at * (Math.atan(at) - 0.5 * Math.PI));
            assertEquals(expected, a.apply(i), TOL);
        }

        // alpha = 0 → all values 1.0 (sqrt(1 + 0))
        a.setAlpha(0.0);
        for (int i = 0; i < times.length; ++i) {
            assertEquals(1.0, a.apply(i), TOL);
        }
    }
}
