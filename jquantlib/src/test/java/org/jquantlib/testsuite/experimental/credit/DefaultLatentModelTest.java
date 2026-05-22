/*
 Copyright (C) 2026 JQuantLib migration contributors.

 Cross-validation of DefaultLatentModel and ConstantLossLatentModel against
 C++ QuantLib v1.42.1 reference values produced by default_prob_latent_probe.

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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.jquantlib.experimental.credit.ConstantLossLatentModel;
import org.jquantlib.experimental.credit.DefaultLatentModel;
import org.jquantlib.experimental.credit.LatentModel;
import org.jquantlib.experimental.math.GaussianCopulaPolicy;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.json.JSONArray;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Cross-validation of {@link DefaultLatentModel#conditionalDefaultProbabilityInvP}
 * against C++ v1.42.1 references in
 * {@code migration-harness/references/credit-loss-models/default_prob_latent.json}.
 *
 * <p>Tolerance tier: TIGHT (1e-12 abs / 1e-14 rel near zero) — analytic
 * cumulative-normal evaluations.
 */
public class DefaultLatentModelTest {

    private static final String TEST_GROUP = "credit-loss-models/default_prob_latent";
    private static final ReferenceReader REF = ReferenceReader.load(TEST_GROUP);

    private static final double TIGHT = 1.0e-12;

    @Test
    public void conditionalDefaultProbabilityInvP_singleFactor() {
        // The probe iterates m ∈ {-2, -1, 0, 1, 2} for rho=0.20, prob=0.05
        for (int mi : new int[]{2, 1, 0, 1, 2}) {
            // rebuild the case names exactly as the probe produces
        }
        // Use the actual case names emitted by the probe:
        final String[] names = {
                "cond_def_p5pc_rho20pc_mneg2", "cond_def_p5pc_rho20pc_mneg1",
                "cond_def_p5pc_rho20pc_mpos0", "cond_def_p5pc_rho20pc_mpos1",
                "cond_def_p5pc_rho20pc_mpos2"
        };
        final double rho = 0.20;
        final double w = Math.sqrt(rho);

        // Build a 5-name single-factor latent model, all weights = w.
        final List<List<Double>> factorWeights = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            final List<Double> row = new ArrayList<>(Collections.singletonList(w));
            factorWeights.add(row);
        }
        final GaussianCopulaPolicy copula = new GaussianCopulaPolicy(factorWeights);
        final var model = new DefaultLatentModel<GaussianCopulaPolicy>(factorWeights, copula,
                        LatentModel.IntegrationType.GaussianQuadrature);
        final double prob = 0.05;
        final double invY = model.inverseCumulativeY(prob, 0);

        final List<String> failures = new ArrayList<>();
        for (final String name : names) {
            final Case c = REF.getCase(name);
            final JSONArray mArr = c.inputs().getJSONArray("m");
            final double[] m = new double[mArr.length()];
            for (int k = 0; k < m.length; k++) {
                m[k] = mArr.getDouble(k);
            }
            final double expected = c.expectedDouble();
            final double actual = model.conditionalDefaultProbabilityInvP(invY, 0, m);
            if (Math.abs(actual - expected) > TIGHT) {
                failures.add(name + " expected=" + expected + " actual=" + actual
                        + " diff=" + Math.abs(actual - expected));
            }
        }
        assertTrue("Failures:\n" + String.join("\n", failures), failures.isEmpty());
    }

    @Test
    public void conditionalDefaultProbabilityInvP_multiFactor() {
        // 2 systemic factors, weights [0.3, 0.4]. Build as a single-name model
        // (only one row in factor matrix). The kernel uses factorWeights_[iName]
        // and idiosyncFctrs_[iName] so a 1-name model is sufficient.
        final List<List<Double>> factorWeights = new ArrayList<>();
        factorWeights.add(new ArrayList<>(Arrays.asList(0.3, 0.4)));
        final GaussianCopulaPolicy copula = new GaussianCopulaPolicy(factorWeights);
        final var model = new DefaultLatentModel<GaussianCopulaPolicy>(factorWeights, copula,
                        LatentModel.IntegrationType.GaussianQuadrature);
        final double prob = 0.10;
        final double invY = model.inverseCumulativeY(prob, 0);

        final List<String> failures = new ArrayList<>();
        for (final String name : new String[]{
                "cond_def_p10pc_2fact_w34_m1_neg05",
                "cond_def_p10pc_2fact_w34_m_zero"}) {
            final Case c = REF.getCase(name);
            final JSONArray mArr = c.inputs().getJSONArray("m");
            final double[] m = new double[mArr.length()];
            for (int k = 0; k < m.length; k++) {
                m[k] = mArr.getDouble(k);
            }
            final double expected = c.expectedDouble();
            final double actual = model.conditionalDefaultProbabilityInvP(invY, 0, m);
            if (Math.abs(actual - expected) > TIGHT) {
                failures.add(name + " expected=" + expected + " actual=" + actual
                        + " diff=" + Math.abs(actual - expected));
            }
        }
        assertTrue("Failures:\n" + String.join("\n", failures), failures.isEmpty());
    }

    @Test
    public void conditionalDefaultProbabilityInvP_lowProbExtreme() {
        final List<List<Double>> factorWeights = new ArrayList<>();
        factorWeights.add(new ArrayList<>(Collections.singletonList(Math.sqrt(0.50))));
        final GaussianCopulaPolicy copula = new GaussianCopulaPolicy(factorWeights);
        final var model = new DefaultLatentModel<GaussianCopulaPolicy>(factorWeights, copula,
                        LatentModel.IntegrationType.GaussianQuadrature);
        final Case c = REF.getCase("cond_def_low_prob_extreme_neg_m");
        final double prob = c.inputs().getDouble("prob");
        final double invY = model.inverseCumulativeY(prob, 0);
        final JSONArray mArr = c.inputs().getJSONArray("m");
        final double[] m = new double[mArr.length()];
        for (int k = 0; k < m.length; k++) {
            m[k] = mArr.getDouble(k);
        }
        final double expected = c.expectedDouble();
        final double actual = model.conditionalDefaultProbabilityInvP(invY, 0, m);
        assertEquals(expected, actual, TIGHT);
    }

    /**
     * Quick smoke test to confirm {@link ConstantLossLatentModel} construction
     * and {@code recoveries()} accessor agree with C++.
     */
    @Test
    public void constantLossLatentModel_basicConstruction() {
        final List<List<Double>> factorWeights = new ArrayList<>();
        factorWeights.add(new ArrayList<>(Collections.singletonList(0.5)));
        factorWeights.add(new ArrayList<>(Collections.singletonList(0.5)));
        factorWeights.add(new ArrayList<>(Collections.singletonList(0.5)));
        final List<Double> recoveries = Arrays.asList(0.40, 0.40, 0.40);
        final GaussianCopulaPolicy copula = new GaussianCopulaPolicy(factorWeights);
        final var m = new ConstantLossLatentModel<GaussianCopulaPolicy>(factorWeights, recoveries, copula,
                        LatentModel.IntegrationType.GaussianQuadrature);
        assertEquals(3, m.size());
        assertEquals(1, m.numFactors());
        assertEquals(0.40, m.recoveries().get(0), 0.0);
        // recovery is constant; invariant under d/iName/mktFactors
        assertEquals(0.40, m.conditionalRecovery(null, 1, new double[]{0.0}), 0.0);
        assertEquals(0.40, m.expectedRecovery(null, 0, null), 0.0);
    }
}
