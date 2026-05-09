/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.finitedifferences;

import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5j skeleton port of {@code test-suite/swingoption.cpp} v1.42.1.
 *
 * <p><strong>All 6 cases deferred to Phase 5j.5</strong> — the test file
 * exercises swing option pricing, the {@code ExtendedOrnsteinUhlenbeckProcess},
 * the {@code ExtOUWithJumpsProcess}, and the {@code ExponentialJump1dMesher}.
 *
 * <p>Java has the process classes
 * ({@code org.jquantlib.experimental.processes.ExtendedOrnsteinUhlenbeckProcess},
 * {@code .ExtOUWithJumpsProcess}, {@code .KlugeExtOUProcess}) but the
 * pricing engines (FdSimpleBSSwingEngine, FdExtOUJumpVanillaEngine,
 * FdSimpleKlugeExtOUVPPEngine) and the {@code ExponentialJump1dMesher}
 * utility are NOT yet ported (Phase 4n.5 carry-forward).
 *
 * <p>{@code testExtendedOrnsteinUhlenbeckProcess} could in principle be
 * ported (just process discretisation comparison) but requires
 * {@code PseudoRandom::rng_type} wiring which is non-trivial — deferred to
 * Phase 5j.5 alongside the engine ports for cohesive coverage.
 *
 * <p>Source: {@code test-suite/swingoption.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class SwingOptionTest {

    private static final String REASON_ENGINE =
            "Phase 5j.5 — requires swing/jump FD engines "
          + "(FdSimpleBSSwingEngine, FdExtOUJumpVanillaEngine) — Phase 4n.5 carry-forward";

    @Ignore("Phase 5j.5 — needs PseudoRandom::rng_type wiring + process evolve loop")
    @Test
    public void testExtendedOrnsteinUhlenbeckProcess() { fail("not implemented"); }

    @Ignore("Phase 5j.5 — requires ExponentialJump1dMesher utility")
    @Test
    public void testFdmExponentialJump1dMesher() { fail("not implemented"); }

    @Ignore(REASON_ENGINE)
    @Test
    public void testExtOUJumpVanillaEngine() { fail("not implemented"); }

    @Ignore(REASON_ENGINE + " + VanillaSwingOption instrument")
    @Test
    public void testFdBSSwingOption() { fail("not implemented"); }

    @Ignore(REASON_ENGINE + " + VanillaSwingOption instrument")
    @Test
    public void testExtOUJumpSwingOption() { fail("not implemented"); }

    @Ignore("Phase 5j.5 — requires Kluge characteristic-function pricer + COS method")
    @Test
    public void testKlugeChFVanillaPricing() { fail("not implemented"); }
}
