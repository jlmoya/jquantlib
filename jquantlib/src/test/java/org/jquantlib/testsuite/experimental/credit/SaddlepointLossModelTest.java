/*
 Copyright (C) 2026 JQuantLib migration contributors.

 Cross-validation of SaddlepointLossModel CGF kernels against C++ QuantLib
 v1.42.1 reference values produced by saddlepoint_cgf_probe.

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
package org.jquantlib.testsuite.experimental.credit;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.jquantlib.experimental.credit.SaddlepointLossModel;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Cross-validation of {@link SaddlepointLossModel} static analytic kernels
 * against C++ v1.42.1 references in
 * {@code migration-harness/references/credit-loss-models/saddlepoint_cgf.json}.
 *
 * <p>Tolerance tier: TIGHT (1e-12 abs / 1e-10 rel near zero) — exp() and
 * log() composition; the most expensive kernel multiplication chain is
 * cgf4Th which has up to 5-fold multiplications of fractional values.
 */
public class SaddlepointLossModelTest {

    private static final String TEST_GROUP = "credit-loss-models/saddlepoint_cgf";
    private static final ReferenceReader REF = ReferenceReader.load(TEST_GROUP);

    private static final double TIGHT = 1.0e-12;
    private static final double REL = 1.0e-10;

    @Test
    public void cgfKernels_5_homog_p05() {
        check("cgf_5_homog_p05_lid12");
    }

    @Test
    public void cgfKernels_5_homog_p20() {
        check("cgf_5_homog_p20_lid12");
    }

    @Test
    public void cgfKernels_8_inhomog() {
        check("cgf_8_inhomog");
    }

    @Test
    public void findSaddleNewton_homogeneous_5_p05_solvesAtKnownLoss() {
        // For p=0.05 5-name homogeneous with lossInDef=0.12 each,
        // expected loss is K'(0) = 0.05 * 0.12 * 5 / (1-0.05+0.05) = 0.03.
        // So findSaddleNewton(targets=0.03) should return s* ≈ 0.0.
        final double[] p = new double[]{0.05, 0.05, 0.05, 0.05, 0.05};
        final double[] l = new double[]{0.12, 0.12, 0.12, 0.12, 0.12};
        final double s = SaddlepointLossModel.findSaddleNewton(p, l, 0.03);
        assertTrue("saddle s for K'(s)=expected loss expected ≈ 0, got " + s,
                Math.abs(s) < 1.0e-6);
    }

    @Test
    public void findSaddleNewton_homogeneous_5_p05_solvesAboveExpected() {
        // K'(0) = 0.03 (expected loss). Target a higher loss; solver must
        // return positive s* and K'(s*) ≈ target.
        final double[] p = new double[]{0.05, 0.05, 0.05, 0.05, 0.05};
        final double[] l = new double[]{0.12, 0.12, 0.12, 0.12, 0.12};
        final double target = 0.06;
        final double s = SaddlepointLossModel.findSaddleNewton(p, l, target, 1.0e-9, 50);
        assertTrue("saddle s should be positive for target above expected: " + s, s > 0.0);
        final double check = SaddlepointLossModel.cumGen1stDerivativeCond(p, l, s);
        assertTrue("K'(s*) - target = " + Math.abs(check - target),
                Math.abs(check - target) < 1.0e-9);
    }

    private static void check(final String caseName) {
        final Case c = REF.getCase(caseName);
        final JSONObject in = c.inputs();
        final JSONArray probsArr = in.getJSONArray("condProbs");
        final JSONArray lidArr = in.getJSONArray("lossInDef");
        final double[] probs = new double[probsArr.length()];
        final double[] lid = new double[lidArr.length()];
        for (int i = 0; i < probs.length; ++i) {
            probs[i] = probsArr.getDouble(i);
            lid[i] = lidArr.getDouble(i);
        }
        final JSONObject expected = (JSONObject) c.expectedRaw();
        final List<String> failures = new ArrayList<>();
        // Cases keyed by "s_<value>" e.g. "s_1.000000"
        final Iterator<String> keys = expected.keys();
        while (keys.hasNext()) {
            final String k = keys.next();
            final double s = parseSaddleKey(k);
            final JSONObject expSub = expected.getJSONObject(k);
            final double e0 = expSub.getDouble("cgf0");
            final double e1 = expSub.getDouble("cgf1");
            final double e2 = expSub.getDouble("cgf2");
            final double e3 = expSub.getDouble("cgf3");
            final double e4 = expSub.getDouble("cgf4");
            final double a0 = SaddlepointLossModel.cumulantGeneratingCond(probs, lid, s);
            final double a1 = SaddlepointLossModel.cumGen1stDerivativeCond(probs, lid, s);
            final double a2 = SaddlepointLossModel.cumGen2ndDerivativeCond(probs, lid, s);
            final double a3 = SaddlepointLossModel.cumGen3rdDerivativeCond(probs, lid, s);
            final double a4 = SaddlepointLossModel.cumGen4thDerivativeCond(probs, lid, s);
            checkClose(failures, caseName + "/" + k + "/cgf0", e0, a0);
            checkClose(failures, caseName + "/" + k + "/cgf1", e1, a1);
            checkClose(failures, caseName + "/" + k + "/cgf2", e2, a2);
            checkClose(failures, caseName + "/" + k + "/cgf3", e3, a3);
            checkClose(failures, caseName + "/" + k + "/cgf4", e4, a4);
            // Also validate the combined cgf0234DerivCond
            final double[] combined = SaddlepointLossModel.cgf0234DerivCond(probs, lid, s);
            checkClose(failures, caseName + "/" + k + "/cgf0234[0]", e0, combined[0]);
            checkClose(failures, caseName + "/" + k + "/cgf0234[2]", e2, combined[1]);
            checkClose(failures, caseName + "/" + k + "/cgf0234[3]", e3, combined[2]);
            checkClose(failures, caseName + "/" + k + "/cgf0234[4]", e4, combined[3]);
        }
        assertTrue("Failures:\n" + String.join("\n", failures), failures.isEmpty());
    }

    private static void checkClose(final List<String> failures, final String label,
                                   final double expected, final double actual) {
        final double diff = Math.abs(actual - expected);
        if (diff > Math.max(TIGHT, REL * Math.abs(expected))) {
            failures.add(label + " expected=" + expected + " actual=" + actual + " diff=" + diff);
        }
    }

    private static double parseSaddleKey(final String key) {
        // "s_<value>" or "s_-<value>"
        final String body = key.substring(2);
        return Double.parseDouble(body);
    }
}
