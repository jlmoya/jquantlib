/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.credit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jquantlib.experimental.credit.LatentModel;
import org.jquantlib.experimental.credit.SpotRecoveryLatentModel;
import org.jquantlib.experimental.math.GaussianCopulaPolicy;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Cross-validation of {@link SpotRecoveryLatentModel} against C++ v1.42.1
 * reference values from
 * {@code migration-harness/references/credit-loss-models/spot_recovery_latent.json}.
 *
 * <p>Tolerance tier: TIGHT (1e-12 abs).
 */
public class SpotRecoveryLatentModelTest {

    private static final String TEST_GROUP = "credit-loss-models/spot_recovery_latent";
    private static final ReferenceReader REF = ReferenceReader.load(TEST_GROUP);
    private static final double TIGHT = 1.0e-12;

    @Test
    public void expCondRecoveryInvPinvRR_matchesCpp() {
        for (final String caseName : REF.caseNames()) {
            final ReferenceReader.Case c = REF.getCase(caseName);
            final JSONObject inputs = c.inputs();
            final double invP = inputs.getDouble("invP");
            final double invRR = inputs.getDouble("invRR");
            final double modelA = inputs.getDouble("modelA");
            final JSONArray wdArr = inputs.getJSONArray("factorWeightsDef");
            final JSONArray wrArr = inputs.getJSONArray("factorWeightsRR");
            final JSONArray mArr = inputs.getJSONArray("m");

            final List<Double> wd = new ArrayList<>();
            final List<Double> wr = new ArrayList<>();
            for (int i = 0; i < wdArr.length(); ++i) wd.add(wdArr.getDouble(i));
            for (int i = 0; i < wrArr.length(); ++i) wr.add(wrArr.getDouble(i));
            final double[] m = new double[mArr.length()];
            for (int i = 0; i < m.length; ++i) m[i] = mArr.getDouble(i);

            // Build a 2-name model: row 0 = default for name 0 (wd),
            // row 1 = recovery for name 0 (wr). To exercise iName=0,
            // we need numNames=1 -> total rows = 2.
            final List<List<Double>> factorWeights = new ArrayList<>();
            factorWeights.add(wd);
            factorWeights.add(wr);
            final List<Double> recoveries = Arrays.asList(0.40);  // unused for kernel
            final GaussianCopulaPolicy copula = new GaussianCopulaPolicy(factorWeights);
            final SpotRecoveryLatentModel<GaussianCopulaPolicy> model =
                    new SpotRecoveryLatentModel<>(factorWeights, recoveries, modelA,
                            copula, LatentModel.IntegrationType.GaussianQuadrature);

            final double actual = model.expCondRecoveryInvPinvRR(invP, invRR, 0, m);
            assertEquals(caseName, c.expectedDouble(), actual, TIGHT);
        }
    }

    @Test
    public void modelAccessorsAreConsistent() {
        // 4-name model -> 8 rows (4 default + 4 RR).
        final List<List<Double>> w = new ArrayList<>();
        for (int i = 0; i < 8; ++i) {
            w.add(Arrays.asList(0.3));
        }
        final List<Double> rr = Arrays.asList(0.4, 0.4, 0.4, 0.4);
        final GaussianCopulaPolicy copula = new GaussianCopulaPolicy(w);
        final SpotRecoveryLatentModel<GaussianCopulaPolicy> m =
                new SpotRecoveryLatentModel<>(w, rr, 1.0, copula,
                        LatentModel.IntegrationType.GaussianQuadrature);
        assertEquals(4, m.numNames());
        assertEquals(8, m.size());
        assertEquals(1.0, m.modelA(), TIGHT);
        assertEquals(0.4, m.recoveries()[0], TIGHT);
        // Cross-idiosyncratic factor for single-factor 0.3/0.3:
        // = 0.3^2 * 0.3^2 = 0.09 * 0.09 = 0.0081
        assertEquals(0.0081, m.crossIdiosyncFactor(0), TIGHT);
    }

    @Test
    public void rejectsOddRowCount() {
        // 3 rows (must be even).
        final List<List<Double>> w = new ArrayList<>();
        for (int i = 0; i < 3; ++i) w.add(Arrays.asList(0.3));
        final List<Double> rr = Arrays.asList(0.4);
        try {
            new SpotRecoveryLatentModel<>(w, rr, 1.0,
                    new GaussianCopulaPolicy(w),
                    LatentModel.IntegrationType.GaussianQuadrature);
            assertTrue("expected exception", false);
        } catch (final RuntimeException expected) {
            // ok
        }
    }
}
