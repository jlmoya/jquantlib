/*
 Copyright (C) 2026 JQuantLib migration contributors.

 Smoke test for {@link RecursiveLossModel} construction and basket-driven
 reset (Phase 2 L5-A experimental/credit triage).

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
import org.jquantlib.experimental.credit.LatentModel;
import org.jquantlib.experimental.credit.RecursiveLossModel;
import org.jquantlib.experimental.math.GaussianCopulaPolicy;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * Smoke tests confirming {@link RecursiveLossModel} constructs cleanly with a
 * 1-factor {@link ConstantLossLatentModel} and rejects invalid arguments.
 *
 * <p>End-to-end Basket-driven loss-distribution cross-validation against
 * {@code RecursiveLossModel<GaussianCopulaPolicy>} is deferred — full Basket plumbing for credit-pool integration tests
 * is shared between {@code HomogeneousPoolLossModel} / {@code InhomogeneousPoolLossModel} and tracked separately.
 */
public class RecursiveLossModelSmokeTest {

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

    @Test
    public void recursiveLossModel_construction_singleBucket() {
        final var m = new RecursiveLossModel<GaussianCopulaPolicy>(oneFactorCLLM(5));
        assertNotNull(m);
    }

    @Test
    public void recursiveLossModel_construction_multiBucket() {
        final var m = new RecursiveLossModel<GaussianCopulaPolicy>(oneFactorCLLM(10), 25);
        assertNotNull(m);
    }

    @Test
    public void recursiveLossModel_rejectsNullModel() {
        try {
            new RecursiveLossModel<GaussianCopulaPolicy>(null, 1);
            fail("expected exception for null model");
        } catch (RuntimeException e) {
            // expected
        }
    }

    @Test
    public void recursiveLossModel_rejectsZeroBuckets() {
        try {
            new RecursiveLossModel<GaussianCopulaPolicy>(oneFactorCLLM(3), 0);
            fail("expected exception for zero buckets");
        } catch (RuntimeException e) {
            // expected
        }
    }
}
