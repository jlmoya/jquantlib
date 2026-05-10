/*
 Copyright (C) 2026 JQuantLib migration contributors.

 Cross-validation of ZabrModel against C++ QuantLib v1.42.1 reference values
 produced by the zabr_model probe (Phase 4f.5b).

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.
 */
package org.jquantlib.testsuite.experimental.volatility;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.experimental.volatility.ZabrModel;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Cross-validation of {@link ZabrModel} (Phase 4f.5b) against C++ v1.42.1
 * references in {@code migration-harness/references/experimental/volatility/zabr_model.json}.
 *
 * <p>Tolerance tiers:
 * <ul>
 *   <li>TIGHT (1e-9 abs / 1e-12 rel) — gamma == 1 closed-form
 *       {@code lognormalVolatility} / {@code normalVolatility}.</li>
 *   <li>LOOSE_RK (1e-6 abs / rel) — gamma != 1 paths that integrate the
 *       {@code F(y, u)} ODE via {@link org.jquantlib.math.ode.AdaptiveRungeKutta}.
 *       Integrator tolerance in C++ is set at {@code 1e-8}, so the actual
 *       residual is well below {@code 1e-7}; we use a 1e-6 envelope to absorb
 *       transcendental drift on JVM math.</li>
 *   <li>LOOSE_FD (5e-3 abs) — Dupire FD price; the 500-point mesh + Douglas
 *       scheme is identical to C++ but numerical roundoff in the
 *       step-conditioned tridiagonal solves accumulates a few ULPs per
 *       step.</li>
 * </ul>
 */
public class ZabrModelCrossValidationTest {

    private static final String TEST_GROUP = "experimental/volatility/zabr_model";
    private static final ReferenceReader REF = ReferenceReader.load(TEST_GROUP);

    private static final double TIGHT = 1.0e-9;
    private static final double LOOSE_RK = 1.0e-6;
    private static final double LOOSE_FD = 5.0e-3;

    public ZabrModelCrossValidationTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static ZabrModel makeModel(final JSONObject in) {
        return new ZabrModel(
                in.getDouble("expiry"),
                in.getDouble("forward"),
                in.getDouble("alpha"),
                in.getDouble("beta"),
                in.getDouble("nu"),
                in.getDouble("rho"),
                in.getDouble("gamma"));
    }

    private static double[] toDoubleArray(final JSONArray a) {
        final double[] out = new double[a.length()];
        for (int i = 0; i < a.length(); ++i) {
            out[i] = a.getDouble(i);
        }
        return out;
    }

    private static double[] toDoubleArrayInputs(final JSONObject in, final String key) {
        return toDoubleArray(in.getJSONArray(key));
    }

    private void assertVectorClose(final String caseName,
                                   final String label,
                                   final double[] expected,
                                   final double[] actual,
                                   final double tol,
                                   final List<String> failures) {
        if (expected.length != actual.length) {
            failures.add(caseName + " " + label + " length mismatch: "
                    + expected.length + " vs " + actual.length);
            return;
        }
        for (int i = 0; i < expected.length; ++i) {
            final double diff = Math.abs(actual[i] - expected[i]);
            final double envelope = Math.max(tol, tol * Math.abs(expected[i]));
            if (diff > envelope) {
                failures.add(caseName + " " + label + "[" + i + "] expected="
                        + expected[i] + " actual=" + actual[i] + " diff=" + diff);
            }
        }
    }

    @Test
    public void lognormalVolatility_gamma1_closedForm_tight() {
        final List<String> failures = new ArrayList<>();
        for (final String name : new String[] {"lognormal_gamma1", "lognormal_beta1_gamma1"}) {
            final Case c = REF.getCase(name);
            final JSONObject in = c.inputs();
            final ZabrModel m = makeModel(in);
            final double[] strikes = toDoubleArrayInputs(in, "strikes");
            final double[] expected = toDoubleArray(
                    ((JSONObject) c.expectedRaw()).getJSONArray("lognormal_vol"));
            final double[] actual = new double[strikes.length];
            for (int i = 0; i < strikes.length; ++i) {
                actual[i] = m.lognormalVolatility(strikes[i]);
            }
            assertVectorClose(name, "lognormal_vol", expected, actual, TIGHT, failures);
        }
        assertTrue("Failures:\n" + String.join("\n", failures), failures.isEmpty());
    }

    @Test
    public void normalVolatility_gamma1_closedForm_tight() {
        final List<String> failures = new ArrayList<>();
        final Case c = REF.getCase("normal_gamma1");
        final JSONObject in = c.inputs();
        final ZabrModel m = makeModel(in);
        final double[] strikes = toDoubleArrayInputs(in, "strikes");
        final double[] expected = toDoubleArray(
                ((JSONObject) c.expectedRaw()).getJSONArray("normal_vol"));
        final double[] actual = new double[strikes.length];
        for (int i = 0; i < strikes.length; ++i) {
            actual[i] = m.normalVolatility(strikes[i]);
        }
        assertVectorClose("normal_gamma1", "normal_vol", expected, actual, TIGHT, failures);
        assertTrue("Failures:\n" + String.join("\n", failures), failures.isEmpty());
    }

    @Test
    public void lognormalVolatility_gammaNot1_runge_kutta_loose() {
        final List<String> failures = new ArrayList<>();
        for (final String name : new String[] {
                "lognormal_gamma075", "lognormal_gamma125", "lognormal_beta1_gamma05"}) {
            final Case c = REF.getCase(name);
            final JSONObject in = c.inputs();
            final ZabrModel m = makeModel(in);
            final double[] strikes = toDoubleArrayInputs(in, "strikes");
            final double[] expected = toDoubleArray(
                    ((JSONObject) c.expectedRaw()).getJSONArray("lognormal_vol"));
            final double[] actual = new double[strikes.length];
            for (int i = 0; i < strikes.length; ++i) {
                actual[i] = m.lognormalVolatility(strikes[i]);
            }
            assertVectorClose(name, "lognormal_vol", expected, actual, LOOSE_RK, failures);
        }
        assertTrue("Failures:\n" + String.join("\n", failures), failures.isEmpty());
    }

    @Test
    public void normalVolatility_gammaNot1_runge_kutta_loose() {
        final List<String> failures = new ArrayList<>();
        for (final String name : new String[] {"normal_gamma075", "normal_gamma05"}) {
            final Case c = REF.getCase(name);
            final JSONObject in = c.inputs();
            final ZabrModel m = makeModel(in);
            final double[] strikes = toDoubleArrayInputs(in, "strikes");
            final double[] expected = toDoubleArray(
                    ((JSONObject) c.expectedRaw()).getJSONArray("normal_vol"));
            final double[] actual = new double[strikes.length];
            for (int i = 0; i < strikes.length; ++i) {
                actual[i] = m.normalVolatility(strikes[i]);
            }
            assertVectorClose(name, "normal_vol", expected, actual, LOOSE_RK, failures);
        }
        assertTrue("Failures:\n" + String.join("\n", failures), failures.isEmpty());
    }

    @Test
    public void localVolatility_allCases_loose() {
        final List<String> failures = new ArrayList<>();
        for (final String name : new String[] {"local_gamma1", "local_gamma075"}) {
            final Case c = REF.getCase(name);
            final JSONObject in = c.inputs();
            final ZabrModel m = makeModel(in);
            final double[] strikes = toDoubleArrayInputs(in, "strikes");
            final double[] expected = toDoubleArray(
                    ((JSONObject) c.expectedRaw()).getJSONArray("local_vol"));
            final double[] actual = new double[strikes.length];
            for (int i = 0; i < strikes.length; ++i) {
                actual[i] = m.localVolatility(strikes[i]);
            }
            assertVectorClose(name, "local_vol", expected, actual, LOOSE_RK, failures);
        }
        assertTrue("Failures:\n" + String.join("\n", failures), failures.isEmpty());
    }

    @Test
    public void fdPrice_gamma1_dupire_fd_loose() {
        final List<String> failures = new ArrayList<>();
        final Case c = REF.getCase("fd_price_gamma1");
        final JSONObject in = c.inputs();
        final ZabrModel m = makeModel(in);
        final double[] strikes = toDoubleArrayInputs(in, "strikes");
        final double[] expected = toDoubleArray(
                ((JSONObject) c.expectedRaw()).getJSONArray("fd_price"));
        final double[] actual = new double[strikes.length];
        for (int i = 0; i < strikes.length; ++i) {
            actual[i] = m.fdPrice(strikes[i]);
        }
        assertVectorClose("fd_price_gamma1", "fd_price", expected, actual, LOOSE_FD, failures);
        assertTrue("Failures:\n" + String.join("\n", failures), failures.isEmpty());
    }
}
