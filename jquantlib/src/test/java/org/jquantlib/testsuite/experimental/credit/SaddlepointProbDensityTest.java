/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.credit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jquantlib.experimental.credit.SaddlepointLossModel;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Cross-validation of {@link SaddlepointLossModel#probOverLossPortfCond}
 * and {@link SaddlepointLossModel#probDensityCond} against C++ v1.42.1
 * reference values in
 * {@code migration-harness/references/credit-loss-models/saddlepoint_prob_density.json}.
 *
 * <p>Tolerance tier: TIGHT-to-LOOSE: 1e-9 absolute on the high-order
 * saddle-point evaluators (the cumulative-normal × exp × polynomial chain
 * is well-conditioned at moderate s but the probability magnitude at the
 * heavy tails is small so absolute tolerance dominates).
 */
public class SaddlepointProbDensityTest {

    private static final String TEST_GROUP = "credit-loss-models/saddlepoint_prob_density";
    private static final ReferenceReader REF = ReferenceReader.load(TEST_GROUP);
    private static final double ABS_TOL = 1.0e-9;

    @Test
    public void allCasesMatchCppReferences() {
        for (final String name : REF.caseNames()) {
            final ReferenceReader.Case c = REF.getCase(name);
            final JSONObject in = c.inputs();
            final double[] cp = doubles(in.getJSONArray("condProbs"));
            final double[] lid = doubles(in.getJSONArray("lossInDef"));
            final double L = in.getDouble("relLoss");
            final double expected = c.expectedDouble();

            final double actual;
            if (name.startsWith("probOverLoss_")) {
                actual = SaddlepointLossModel.probOverLossPortfCond(cp, lid, L);
            } else if (name.startsWith("probDensity_")) {
                actual = SaddlepointLossModel.probDensityCond(cp, lid, L);
            } else {
                throw new AssertionError("unknown prefix in case: " + name);
            }
            assertEquals(name + " (expected=" + expected + ", actual=" + actual + ")",
                    expected, actual, ABS_TOL);
        }
    }

    @Test
    public void splitLossCondSumsToLoss() {
        // 5-name pool, all equal LGD 20, total notional 100 -> relative LGD 0.2
        final double[] cp = {0.10, 0.05, 0.15, 0.08, 0.12};
        final double[] lid = {20.0, 20.0, 20.0, 20.0, 20.0};
        final double remainingNotional = 100.0;
        final double loss = 15.0;  // 15% of total

        final double[] split = SaddlepointLossModel.splitLossCond(cp, lid, remainingNotional, loss);
        assertEquals(5, split.length);
        double sum = 0.0;
        for (final double x : split) {
            assertTrue("split entry >= 0: " + x, x >= 0.0);
            sum += x;
        }
        // The C++ docstring says sums to "loss"; this is exact in the saddle-point
        // Newton solution since K'(s) = sum(lossInDef * pBuf*exp(.) / denom).
        // Allow LOOSE 0.5 absolute due to Newton solver tolerance (1e-3 on K'(s)
        // converted to ~3% absolute slack on the relative loss target).
        assertEquals("sum of contributions ≈ loss",
                loss, sum, 0.5);
    }

    @Test
    public void conditionalExpectedLossIsLinear() {
        // E[L | mkt] = sum p_i * LGD_i
        final double[] cp = {0.10, 0.20, 0.30};
        final double[] lid = {100.0, 50.0, 25.0};
        final double expected = 0.10 * 100 + 0.20 * 50 + 0.30 * 25;  // = 27.5
        final double actual = SaddlepointLossModel.conditionalExpectedLoss(cp, lid);
        assertEquals(expected, actual, 1.0e-12);
    }

    @Test
    public void conditionalExpectedTrancheLossClips() {
        // Total expected loss 27.5, attach 10, detach 25 -> tranche width 15.
        // Effective ETL = min(max(27.5 - 10, 0), 15) = 15.
        final double[] cp = {0.10, 0.20, 0.30};
        final double[] lid = {100.0, 50.0, 25.0};
        final double etl = SaddlepointLossModel.conditionalExpectedTrancheLoss(cp, lid, 10.0, 25.0);
        assertEquals(15.0, etl, 1.0e-12);

        // Below attach -> zero.
        final double[] cpLow = {0.01, 0.01, 0.01};
        final double etl2 = SaddlepointLossModel.conditionalExpectedTrancheLoss(cpLow, lid, 10.0, 25.0);
        assertEquals(0.0, etl2, 1.0e-12);
    }

    private static double[] doubles(final JSONArray arr) {
        final double[] out = new double[arr.length()];
        for (int i = 0; i < out.length; ++i) out[i] = arr.getDouble(i);
        return out;
    }
}
