/*
 Copyright (C) 2026 JQuantLib migration contributors.

 Cross-validation of BinomialLossModel.lossProbabilityKernel against
 C++ QuantLib v1.42.1 reference values produced by binomial_loss_probe.

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
import java.util.List;

import org.jquantlib.experimental.credit.BinomialLossModel;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Cross-validation of {@link BinomialLossModel#lossProbabilityKernel}
 * against C++ v1.42.1 references in
 * {@code migration-harness/references/credit-loss-models/binomial_loss.json}.
 *
 * <p>Tolerance tier: TIGHT (1e-12 abs / 1e-14 rel near zero) — pure
 * arithmetic on floating-point inputs (no transcendentals, no integrals).
 */
public class BinomialLossModelTest {

    private static final String TEST_GROUP = "credit-loss-models/binomial_loss";
    private static final ReferenceReader REF = ReferenceReader.load(TEST_GROUP);

    private static final double TIGHT = 1.0e-12;

    @Test
    public void lossProbabilityKernel_homogeneous_5_p05() {
        check("binom_5_homog_p05_lgd60");
    }

    @Test
    public void lossProbabilityKernel_homogeneous_5_p20() {
        check("binom_5_homog_p20_lgd40");
    }

    @Test
    public void lossProbabilityKernel_inhomogeneous_10_mixed() {
        check("binom_10_inhomog_mixed");
    }

    @Test
    public void lossProbabilityKernel_atom_at_0() {
        check("binom_5_low_prob_atom_at_0");
    }

    @Test
    public void lossProbabilityKernel_atom_at_N() {
        check("binom_5_high_prob_atom_at_N");
    }

    @Test
    public void lossProbabilityKernel_homogeneous_3_p50() {
        // For p=0.5 homogeneous, the adjusted-binomial reduces to the
        // exact independent binomial: pmf = {0.125, 0.375, 0.375, 0.125}.
        check("binom_3_homog_p50_lgd100");
    }

    private static void check(final String caseName) {
        final Case c = REF.getCase(caseName);
        final JSONObject in = c.inputs();
        final JSONArray probsArr = in.getJSONArray("condProbs");
        final JSONArray lgdsArr = in.getJSONArray("lgds");
        final double[] condProbs = new double[probsArr.length()];
        final double[] lgds = new double[lgdsArr.length()];
        for (int i = 0; i < condProbs.length; ++i) {
            condProbs[i] = probsArr.getDouble(i);
            lgds[i] = lgdsArr.getDouble(i);
        }
        final double[] actual = BinomialLossModel.lossProbabilityKernel(condProbs, lgds);
        final JSONArray expectedArr = c.expectedArray();
        final List<String> failures = new ArrayList<>();
        if (actual.length != expectedArr.length()) {
            failures.add(caseName + " size mismatch: expected=" + expectedArr.length()
                    + " actual=" + actual.length);
        } else {
            for (int i = 0; i < actual.length; ++i) {
                final double e = expectedArr.getDouble(i);
                final double diff = Math.abs(actual[i] - e);
                if (diff > Math.max(TIGHT, TIGHT * Math.abs(e))) {
                    failures.add(caseName + "[" + i + "] expected=" + e
                            + " actual=" + actual[i] + " diff=" + diff);
                }
            }
        }
        assertTrue("Failures:\n" + String.join("\n", failures), failures.isEmpty());
    }
}
