/*
 Copyright (C) 2026 JQuantLib migration contributors.

 Smoke test for {@link HomogeneousPoolLossModel} and
 {@link InhomogeneousPoolLossModel} construction (Phase 4m.7 WI-4).

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
import java.util.Collections;
import java.util.List;

import org.jquantlib.experimental.credit.ConstantLossLatentModel;
import org.jquantlib.experimental.credit.HomogeneousPoolLossModel;
import org.jquantlib.experimental.credit.InhomogeneousPoolLossModel;
import org.jquantlib.experimental.credit.LatentModel;
import org.jquantlib.experimental.math.GaussianCopulaPolicy;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * Smoke tests confirming {@link HomogeneousPoolLossModel} and
 * {@link InhomogeneousPoolLossModel} construct cleanly with a 1-factor
 * {@link ConstantLossLatentModel} and reject multifactor models.
 *
 * <p>End-to-end Basket-driven loss-distribution tests are deferred to
 * Phase 4m.7b once the existing Basket's {@code defaultKeys()} HashMap
 * ordering bug is fixed (orthogonal to this phase).
 */
public class PoolLossModelSmokeTest {

    private static ConstantLossLatentModel<GaussianCopulaPolicy> oneFactorCLLM(final int n) {
        final List<List<Double>> factorWeights = new ArrayList<>();
        final double w = Math.sqrt(0.20);
        for (int i = 0; i < n; ++i) {
            factorWeights.add(new ArrayList<>(Collections.singletonList(w)));
        }
        final List<Double> recoveries = new ArrayList<>(n);
        for (int i = 0; i < n; ++i) {
            recoveries.add(0.40);
        }
        final GaussianCopulaPolicy copula = new GaussianCopulaPolicy(factorWeights);
        return new ConstantLossLatentModel<>(factorWeights, recoveries, copula,
                LatentModel.IntegrationType.GaussianQuadrature);
    }

    private static ConstantLossLatentModel<GaussianCopulaPolicy> twoFactorCLLM(final int n) {
        final List<List<Double>> factorWeights = new ArrayList<>();
        for (int i = 0; i < n; ++i) {
            final List<Double> row = new ArrayList<>();
            row.add(0.3);
            row.add(0.4);
            factorWeights.add(row);
        }
        final List<Double> recoveries = new ArrayList<>(n);
        for (int i = 0; i < n; ++i) {
            recoveries.add(0.40);
        }
        final GaussianCopulaPolicy copula = new GaussianCopulaPolicy(factorWeights);
        return new ConstantLossLatentModel<>(factorWeights, recoveries, copula,
                LatentModel.IntegrationType.GaussianQuadrature);
    }

    @Test
    public void homogeneousPoolLossModel_construction_oneFactor() {
        final var m = new HomogeneousPoolLossModel<GaussianCopulaPolicy>(oneFactorCLLM(5), 10);
        assertNotNull(m);
    }

    @Test
    public void inhomogeneousPoolLossModel_construction_oneFactor() {
        final var m = new InhomogeneousPoolLossModel<GaussianCopulaPolicy>(oneFactorCLLM(5), 20);
        assertNotNull(m);
    }

    @Test
    public void homogeneousPoolLossModel_rejectsMultifactor() {
        try {
            new HomogeneousPoolLossModel<>(twoFactorCLLM(5), 10);
            fail("expected IllegalStateException for multifactor model");
        } catch (RuntimeException e) {
            // expected — see C++ "Inhomogeneous model not implemented for multifactor"
            // (the message is shared between both pool defs)
        }
    }

    @Test
    public void inhomogeneousPoolLossModel_rejectsMultifactor() {
        try {
            new InhomogeneousPoolLossModel<>(twoFactorCLLM(5), 10);
            fail("expected IllegalStateException for multifactor model");
        } catch (RuntimeException e) {
            // expected
        }
    }
}
