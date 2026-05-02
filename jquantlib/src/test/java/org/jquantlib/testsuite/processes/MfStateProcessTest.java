/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Cross-validated tests for MfStateProcess against QuantLib v1.42.1 via
 migration-harness/references/processes/mf_state_process.json.
 Phase 2j WI-4.0a.
*/
package org.jquantlib.testsuite.processes;

import org.jquantlib.processes.MfStateProcess;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.testsuite.util.Tolerance;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.fail;

/**
 * Probe-driven tests for {@link MfStateProcess}: x0, drift, diffusion,
 * expectation, variance, stdDeviation — all at TIGHT tier.
 *
 * <p>The probe uses three process instances:
 * <ul>
 *   <li>Standard: reversion=0.03, times={1,2,3}, vols={0.010,0.012,0.015,0.018}</li>
 *   <li>Zero-reversion: reversion=0.0, same times/vols</li>
 *   <li>Single-vol: reversion=0.03, times={} (empty), vols={0.020}</li>
 *   <li>Single-vol zero-reversion: reversion=0.0, same empty times, vols={0.020}</li>
 * </ul>
 */
public class MfStateProcessTest {

    private static final String REF_GROUP = "processes/mf_state_process";

    /** Standard process: reversion=0.03, piecewise vols */
    private static MfStateProcess buildStandardProcess() {
        return new MfStateProcess(
                0.03,
                new double[]{1.0, 2.0, 3.0},
                new double[]{0.010, 0.012, 0.015, 0.018});
    }

    /** Zero-reversion variant: a=0, piecewise vols */
    private static MfStateProcess buildRev0Process() {
        return new MfStateProcess(
                0.0,
                new double[]{1.0, 2.0, 3.0},
                new double[]{0.010, 0.012, 0.015, 0.018});
    }

    /** Single-vol process: reversion=0.03, empty breakpoints */
    private static MfStateProcess buildSingleProcess() {
        return new MfStateProcess(0.03, new double[]{}, new double[]{0.020});
    }

    /** Single-vol zero-reversion: a=0, empty breakpoints */
    private static MfStateProcess buildSingleRev0Process() {
        return new MfStateProcess(0.0, new double[]{}, new double[]{0.020});
    }

    @Test
    public void mfStateProcess_matchesCpp() {
        final ReferenceReader ref = ReferenceReader.load(REF_GROUP);

        final MfStateProcess proc       = buildStandardProcess();
        final MfStateProcess procRev0   = buildRev0Process();
        final MfStateProcess procSingle = buildSingleProcess();
        final MfStateProcess procSingleRev0 = buildSingleRev0Process();

        final List<String> mismatches = new ArrayList<>();

        for (final String name : ref.caseNames()) {
            final Case c = ref.getCase(name);
            final JSONObject inp = c.inputs();
            // expected is always a JSONObject {"value": <double>} from our probes
            final double expected = ((JSONObject) c.expectedRaw()).getDouble("value");
            final double actual;

            if (name.equals("x0")) {
                actual = proc.x0();

            } else if (name.startsWith("drift_")) {
                final double t = inp.getDouble("t");
                final double x = inp.getDouble("x");
                actual = proc.drift(t, x);

            } else if (name.startsWith("diff_")) {
                final double t = inp.getDouble("t");
                final double x = inp.getDouble("x");
                actual = proc.diffusion(t, x);

            } else if (name.startsWith("exp_")) {
                final double t  = inp.getDouble("t");
                final double dt = inp.getDouble("dt");
                final double x  = inp.getDouble("x");
                actual = proc.expectation(t, x, dt);

            } else if (name.startsWith("var_single_rev0_")) {
                final double t  = inp.getDouble("t");
                final double dt = inp.getDouble("dt");
                actual = procSingleRev0.variance(t, 0.0, dt);

            } else if (name.startsWith("var_single_")) {
                final double t  = inp.getDouble("t");
                final double dt = inp.getDouble("dt");
                actual = procSingle.variance(t, 0.0, dt);

            } else if (name.startsWith("var_rev0_")) {
                final double t  = inp.getDouble("t");
                final double dt = inp.getDouble("dt");
                actual = procRev0.variance(t, 0.0, dt);

            } else if (name.startsWith("var_")) {
                final double t  = inp.getDouble("t");
                final double dt = inp.getDouble("dt");
                actual = proc.variance(t, 0.0, dt);

            } else if (name.startsWith("std_")) {
                final double t  = inp.getDouble("t");
                final double dt = inp.getDouble("dt");
                actual = proc.stdDeviation(t, 0.0, dt);

            } else {
                mismatches.add(name + ": UNRECOGNIZED case prefix");
                continue;
            }

            if (!Tolerance.tight(actual, expected)) {
                mismatches.add(String.format(
                        "%s: expected=%.17e actual=%.17e diff=%.3e",
                        name, expected, actual, Math.abs(actual - expected)));
            }
        }

        if (!mismatches.isEmpty()) {
            fail(mismatches.size() + " mismatch(es) at TIGHT tier:\n"
                    + String.join("\n", mismatches));
        }
    }
}
