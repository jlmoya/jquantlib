/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Cross-validated tests for GsrProcessCore + GsrProcess against
 QuantLib v1.42.1 via migration-harness/references/processes/gsr_process.json.
 Phase 2j WI-1.2.
*/
package org.jquantlib.testsuite.processes.gsr;

import org.jquantlib.processes.gsr.GsrProcess;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.testsuite.util.Tolerance;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Probe-driven tests for {@link GsrProcess}: drift, diffusion, expectation,
 * variance, sigma, y, G — all at TIGHT tier.
 *
 * <p>The probe uses piecewise vols [0,1)=0.01, [1,2)=0.012, [2,∞)=0.015
 * and constant reversion 0.01 with T=10.
 */
public class GsrProcessTest {

    private static final String REF_GROUP = "processes/gsr_process";

    /** Array parameters shared by all cases — matches the probe. */
    private static GsrProcess buildProcess() {
        final double[] times      = {1.0, 2.0};
        final double[] vols       = {0.01, 0.012, 0.015};
        final double[] reversions = {0.01};
        final double T            = 10.0;
        return new GsrProcess(times, vols, reversions, T);
    }

    // ---- drift ----

    @Test public void drift_000() { runCase("drift_000"); }
    @Test public void drift_001() { runCase("drift_001"); }
    @Test public void drift_002() { runCase("drift_002"); }
    @Test public void drift_003() { runCase("drift_003"); }
    @Test public void drift_004() { runCase("drift_004"); }
    @Test public void drift_005() { runCase("drift_005"); }
    @Test public void drift_006() { runCase("drift_006"); }
    @Test public void drift_007() { runCase("drift_007"); }
    @Test public void drift_008() { runCase("drift_008"); }
    @Test public void drift_009() { runCase("drift_009"); }
    @Test public void drift_010() { runCase("drift_010"); }
    @Test public void drift_011() { runCase("drift_011"); }
    @Test public void drift_012() { runCase("drift_012"); }
    @Test public void drift_013() { runCase("drift_013"); }
    @Test public void drift_014() { runCase("drift_014"); }
    @Test public void drift_015() { runCase("drift_015"); }
    @Test public void drift_016() { runCase("drift_016"); }
    @Test public void drift_017() { runCase("drift_017"); }
    @Test public void drift_018() { runCase("drift_018"); }
    @Test public void drift_019() { runCase("drift_019"); }
    @Test public void drift_020() { runCase("drift_020"); }
    @Test public void drift_021() { runCase("drift_021"); }
    @Test public void drift_022() { runCase("drift_022"); }
    @Test public void drift_023() { runCase("drift_023"); }
    @Test public void drift_024() { runCase("drift_024"); }
    @Test public void drift_025() { runCase("drift_025"); }
    @Test public void drift_026() { runCase("drift_026"); }
    @Test public void drift_027() { runCase("drift_027"); }
    @Test public void drift_028() { runCase("drift_028"); }
    @Test public void drift_029() { runCase("drift_029"); }
    @Test public void drift_030() { runCase("drift_030"); }
    @Test public void drift_031() { runCase("drift_031"); }
    @Test public void drift_032() { runCase("drift_032"); }
    @Test public void drift_033() { runCase("drift_033"); }
    @Test public void drift_034() { runCase("drift_034"); }

    // ---- diffusion ----

    @Test public void diff_000() { runCase("diff_000"); }
    @Test public void diff_001() { runCase("diff_001"); }
    @Test public void diff_005() { runCase("diff_005"); }
    @Test public void diff_010() { runCase("diff_010"); }
    @Test public void diff_015() { runCase("diff_015"); }
    @Test public void diff_020() { runCase("diff_020"); }
    @Test public void diff_025() { runCase("diff_025"); }
    @Test public void diff_030() { runCase("diff_030"); }
    @Test public void diff_034() { runCase("diff_034"); }

    // ---- expectation ----

    @Test public void exp_000() { runCase("exp_000"); }
    @Test public void exp_001() { runCase("exp_001"); }
    @Test public void exp_002() { runCase("exp_002"); }
    @Test public void exp_003() { runCase("exp_003"); }
    @Test public void exp_004() { runCase("exp_004"); }
    @Test public void exp_005() { runCase("exp_005"); }
    @Test public void exp_006() { runCase("exp_006"); }
    @Test public void exp_007() { runCase("exp_007"); }
    @Test public void exp_008() { runCase("exp_008"); }
    @Test public void exp_009() { runCase("exp_009"); }
    @Test public void exp_010() { runCase("exp_010"); }
    @Test public void exp_011() { runCase("exp_011"); }
    @Test public void exp_020() { runCase("exp_020"); }
    @Test public void exp_030() { runCase("exp_030"); }

    // ---- variance ----

    @Test public void var_000() { runCase("var_000"); }
    @Test public void var_001() { runCase("var_001"); }
    @Test public void var_002() { runCase("var_002"); }
    @Test public void var_003() { runCase("var_003"); }
    @Test public void var_004() { runCase("var_004"); }
    @Test public void var_005() { runCase("var_005"); }
    @Test public void var_006() { runCase("var_006"); }
    @Test public void var_007() { runCase("var_007"); }
    @Test public void var_008() { runCase("var_008"); }
    @Test public void var_009() { runCase("var_009"); }
    @Test public void var_010() { runCase("var_010"); }
    @Test public void var_020() { runCase("var_020"); }
    @Test public void var_030() { runCase("var_030"); }

    // ---- sigma ----

    @Test public void sigma_000() { runCase("sigma_000"); }
    @Test public void sigma_001() { runCase("sigma_001"); }
    @Test public void sigma_002() { runCase("sigma_002"); }
    @Test public void sigma_003() { runCase("sigma_003"); }
    @Test public void sigma_004() { runCase("sigma_004"); }
    @Test public void sigma_005() { runCase("sigma_005"); }

    // ---- y(t) ----

    @Test public void y_000() { runCase("y_000"); }
    @Test public void y_001() { runCase("y_001"); }
    @Test public void y_002() { runCase("y_002"); }
    @Test public void y_003() { runCase("y_003"); }
    @Test public void y_004() { runCase("y_004"); }
    @Test public void y_005() { runCase("y_005"); }
    @Test public void y_006() { runCase("y_006"); }

    // ---- G(t, T) ----

    @Test public void G_000() { runCase("G_000"); }
    @Test public void G_001() { runCase("G_001"); }
    @Test public void G_002() { runCase("G_002"); }
    @Test public void G_003() { runCase("G_003"); }
    @Test public void G_004() { runCase("G_004"); }
    @Test public void G_005() { runCase("G_005"); }
    @Test public void G_006() { runCase("G_006"); }
    @Test public void G_007() { runCase("G_007"); }
    @Test public void G_008() { runCase("G_008"); }
    @Test public void G_009() { runCase("G_009"); }
    @Test public void G_010() { runCase("G_010"); }

    // -----------------------------------------------------------------------
    // Dispatch
    // -----------------------------------------------------------------------

    private static void runCase(final String name) {
        final ReferenceReader reader = ReferenceReader.load(REF_GROUP);
        final Case c = reader.getCase(name);
        final JSONObject in = c.inputs();
        final JSONObject exp = (JSONObject) c.expectedRaw();
        final double expected = exp.getDouble("value");

        final GsrProcess proc = buildProcess();
        final double actual;

        if (name.startsWith("drift_")) {
            final double t = in.getDouble("t");
            final double x = in.getDouble("x");
            actual = proc.drift(t, x);
        } else if (name.startsWith("diff_")) {
            final double t = in.getDouble("t");
            final double x = in.getDouble("x");
            actual = proc.diffusion(t, x);
        } else if (name.startsWith("exp_")) {
            final double t  = in.getDouble("t");
            final double dt = in.getDouble("dt");
            final double x  = in.getDouble("x");
            actual = proc.expectation(t, x, dt);
        } else if (name.startsWith("var_")) {
            final double t  = in.getDouble("t");
            final double dt = in.getDouble("dt");
            final double x  = in.getDouble("x");
            actual = proc.variance(t, x, dt);
        } else if (name.startsWith("sigma_")) {
            final double t = in.getDouble("t");
            actual = proc.sigma(t);
        } else if (name.startsWith("y_")) {
            final double t = in.getDouble("t");
            actual = proc.y(t);
        } else if (name.startsWith("G_")) {
            final double t = in.getDouble("t");
            final double T = in.getDouble("T");
            actual = proc.G(t, T, 0.0);
        } else {
            fail("Unknown case prefix: " + name);
            return; // unreachable
        }

        assertTrue(
                String.format("Case %s: actual=%.17g expected=%.17g diff=%.3g",
                        name, actual, expected, Math.abs(actual - expected)),
                Tolerance.tight(actual, expected));
    }
}
