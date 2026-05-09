/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Phase 4c — GeneralizedOrnsteinUhlenbeckProcess cross-validation tests.

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
package org.jquantlib.testsuite.experimental.shortrate;

import org.jquantlib.QL;
import org.jquantlib.experimental.shortrate.GeneralizedOrnsteinUhlenbeckProcess;
import org.jquantlib.math.Ops;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Cross-validated tests for {@link GeneralizedOrnsteinUhlenbeckProcess}
 * against v1.42.1 reference values in
 * {@code migration-harness/references/experimental/shortrate/generalized_ou_process.json}.
 *
 * <p>Tight tolerance — these are pure floating-point operations
 * (Math.exp/sqrt) within JVM 1-ULP slack of the C++ libm.
 */
public class GeneralizedOrnsteinUhlenbeckProcessTest {

    private static final String GROUP =
            "experimental/shortrate/generalized_ou_process";

    private static final ReferenceReader REF = ReferenceReader.load(GROUP);

    private static final double TIGHT = 1e-12;

    public GeneralizedOrnsteinUhlenbeckProcessTest() {
        QL.info("::::: " + getClass().getSimpleName() + " :::::");
    }

    @Test
    public void constantCoefficients() {
        final Case c = REF.getCase("constant_a_0.5_sigma_0.1");
        final JSONObject in = c.inputs();
        final JSONObject e = (JSONObject) c.expectedRaw();

        final double a = in.getDouble("a");
        final double sigma = in.getDouble("sigma");
        final double x0 = in.getDouble("x0");
        final double level = in.getDouble("level");
        final double t = in.getDouble("t");
        final double x = in.getDouble("x");
        final double dt = in.getDouble("dt");

        final Ops.DoubleOp speed = u -> a;
        final Ops.DoubleOp vol = u -> sigma;

        final GeneralizedOrnsteinUhlenbeckProcess p =
                new GeneralizedOrnsteinUhlenbeckProcess(speed, vol, x0, level);

        check(e, p, t, x, dt);
        assertEquals(level, p.level(), 0.0);
        assertEquals(x0, p.x0(), 0.0);
    }

    @Test
    public void linearCoefficients_t_0_50() {
        runLinear("linear_t_0.50");
    }

    @Test
    public void linearCoefficients_t_1_00() {
        runLinear("linear_t_1.00");
    }

    @Test
    public void linearCoefficients_t_2_00() {
        runLinear("linear_t_2.00");
    }

    @Test
    public void linearCoefficients_t_3_00() {
        runLinear("linear_t_3.00");
    }

    @Test
    public void smallSpeedAlgebraicLimit() {
        // The variance branch: speed < sqrt(QL_EPSILON) → vol*vol*dt.
        final Case c = REF.getCase("small_speed_limit");
        final JSONObject in = c.inputs();
        final JSONObject e = (JSONObject) c.expectedRaw();

        final double a = in.getDouble("a");
        final double sigma = in.getDouble("sigma");
        final double t = in.getDouble("t");
        final double x = in.getDouble("x");
        final double dt = in.getDouble("dt");

        final GeneralizedOrnsteinUhlenbeckProcess p =
                new GeneralizedOrnsteinUhlenbeckProcess(u -> a, u -> sigma, 0.0, 0.0);
        assertEquals(e.getDouble("variance"), p.variance(t, x, dt), TIGHT);
        assertEquals(e.getDouble("std_dev"), p.stdDeviation(t, x, dt), TIGHT);
    }

    private void runLinear(final String name) {
        final Case c = REF.getCase(name);
        final JSONObject in = c.inputs();
        final JSONObject e = (JSONObject) c.expectedRaw();

        final double sc0 = in.getDouble("speed_c0");
        final double sc1 = in.getDouble("speed_c1");
        final double vc0 = in.getDouble("vol_c0");
        final double vc1 = in.getDouble("vol_c1");
        final double x0 = in.getDouble("x0");
        final double level = in.getDouble("level");
        final double t = in.getDouble("t");
        final double x = in.getDouble("x");
        final double dt = in.getDouble("dt");

        final Ops.DoubleOp speed = u -> sc0 + sc1 * u;
        final Ops.DoubleOp vol = u -> vc0 + vc1 * u;
        final GeneralizedOrnsteinUhlenbeckProcess p =
                new GeneralizedOrnsteinUhlenbeckProcess(speed, vol, x0, level);
        check(e, p, t, x, dt);
    }

    private void check(final JSONObject e,
                       final GeneralizedOrnsteinUhlenbeckProcess p,
                       final double t, final double x, final double dt) {
        assertEquals(e.getDouble("speed_t"), p.speed(t), TIGHT);
        assertEquals(e.getDouble("vol_t"), p.volatility(t), TIGHT);
        assertEquals(e.getDouble("drift"), p.drift(t, x), TIGHT);
        assertEquals(e.getDouble("diffusion"), p.diffusion(t, x), TIGHT);
        assertEquals(e.getDouble("expectation"), p.expectation(t, x, dt), TIGHT);
        assertEquals(e.getDouble("std_dev"), p.stdDeviation(t, x, dt), TIGHT);
        assertEquals(e.getDouble("variance"), p.variance(t, x, dt), TIGHT);
    }
}
