/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 2 L4-A.

 This source code is release under the BSD License.
 */
package org.jquantlib.testsuite.model.equity;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.model.equity.HestonModel;
import org.junit.Test;

/**
 * Phase 2 L4-A — tests for {@link HestonModel.FellerConstraint}.
 *
 * <p>Validates the Feller condition {@code 2*kappa*theta > sigma^2} (with {@code sigma >= 0}),
 * mirroring C++ v1.42.1 {@code HestonModel::FellerConstraint::Impl::test}:
 * <pre>
 *   return (sigma >= 0.0 && sigma*sigma &lt; 2.0*kappa*theta);
 * </pre>
 *
 * <p>The constraint parameter layout is the same as the CalibratedModel arguments_ array used
 * by {@link HestonModel}: {@code [theta, kappa, sigma, rho, v0]}.
 */
public class FellerConstraintTest {

    @Test
    public void satisfied_strictlyAboveFellerBoundary() {
        // sigma^2 = 0.04; 2*kappa*theta = 2 * 1.5 * 0.04 = 0.12; 0.04 < 0.12 → pass.
        final HestonModel.FellerConstraint c = new HestonModel.FellerConstraint();
        final Array p = new Array(new double[]{0.04 /*theta*/, 1.5 /*kappa*/, 0.20 /*sigma*/,
                                                -0.5 /*rho — unused*/, 0.02 /*v0 — unused*/});
        assertTrue("Feller condition satisfied", c.test(p));
    }

    @Test
    public void violated_atBoundary_strictInequality() {
        // sigma^2 = 2*kappa*theta exactly → C++ uses strict < so this must fail.
        // theta=0.04, kappa=0.5; 2*kappa*theta = 0.04; sigma = 0.2 → sigma^2 = 0.04.
        final HestonModel.FellerConstraint c = new HestonModel.FellerConstraint();
        final Array p = new Array(new double[]{0.04, 0.5, 0.20, -0.5, 0.02});
        assertFalse("Feller condition equality must fail strict <", c.test(p));
    }

    @Test
    public void violated_aboveFellerThreshold() {
        // sigma too large: sigma^2 > 2*kappa*theta.
        final HestonModel.FellerConstraint c = new HestonModel.FellerConstraint();
        final Array p = new Array(new double[]{0.04, 1.0, 0.50, -0.5, 0.02});
        // sigma^2 = 0.25; 2*kappa*theta = 0.08; 0.25 > 0.08 → fail.
        assertFalse("Feller condition violated", c.test(p));
    }

    @Test
    public void violated_negativeSigma() {
        // sigma < 0 must fail regardless of kappa*theta.
        final HestonModel.FellerConstraint c = new HestonModel.FellerConstraint();
        final Array p = new Array(new double[]{0.04, 5.0, -0.10, -0.5, 0.02});
        assertFalse("negative sigma must fail Feller", c.test(p));
    }

    @Test
    public void satisfied_zeroSigma() {
        // sigma == 0 is allowed (sigma^2 = 0 < 2*kappa*theta > 0).
        final HestonModel.FellerConstraint c = new HestonModel.FellerConstraint();
        final Array p = new Array(new double[]{0.04, 1.0, 0.0, -0.5, 0.02});
        assertTrue("zero sigma satisfies Feller when kappa*theta > 0", c.test(p));
    }

    @Test
    public void violated_zeroKappa() {
        // kappa == 0 → 2*kappa*theta == 0; sigma^2 must be < 0 to pass, impossible if sigma > 0.
        final HestonModel.FellerConstraint c = new HestonModel.FellerConstraint();
        final Array p = new Array(new double[]{0.04, 0.0, 0.10, -0.5, 0.02});
        assertFalse("zero kappa with positive sigma fails Feller", c.test(p));
    }

    @Test
    public void notEmpty() {
        final HestonModel.FellerConstraint c = new HestonModel.FellerConstraint();
        assertFalse("FellerConstraint has an Impl set", c.empty());
    }
}
