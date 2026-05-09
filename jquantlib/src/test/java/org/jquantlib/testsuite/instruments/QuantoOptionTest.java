/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5i skeleton port of {@code test-suite/quantooption.cpp} v1.42.1
 * (1,345 LOC, 10 cases).
 *
 * <p>Exercises quanto-adjusted vanilla, forward, barrier, and double-barrier
 * options under both analytic and FD engines, plus the {@code FdmQuantoHelper}
 * utility and the American quanto path.
 *
 * <p><strong>All 10 cases deferred to Phase 5i.5</strong> — Java has no
 * equivalent for the quanto vanilla / forward families:
 * <ul>
 *   <li>No {@code QuantoVanillaOption} instrument (only
 *       {@code experimental.barrieroption.QuantoDoubleBarrierOption} exists);
 *   <li>No {@code QuantoEngine} / {@code QuantoForwardEngine} /
 *       {@code QuantoBarrierEngine} ports;
 *   <li>No {@code FdmQuantoHelper} port (used for FD quanto adjustments
 *       in the Phase 2m FD vanilla framework);
 *   <li>No FD quanto vanilla engine ({@code FdBlackScholesVanillaEngine}
 *       in Java does not yet expose the quanto-helper hook).
 * </ul>
 *
 * <p>Source: {@code test-suite/quantooption.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class QuantoOptionTest {

    private static final String REASON_VANILLA =
            "Phase 5i.5 — requires QuantoVanillaOption instrument + "
          + "QuantoEngine port (no Java equivalent yet)";

    private static final String REASON_FORWARD =
            "Phase 5i.5 — requires QuantoForwardVanillaOption + "
          + "QuantoForwardEngine port (no Java equivalent yet)";

    private static final String REASON_BARRIER =
            "Phase 5i.5 — requires QuantoBarrierOption + QuantoBarrierEngine "
          + "port (Java has only QuantoDoubleBarrierOption under experimental)";

    private static final String REASON_FDM_HELPER =
            "Phase 5i.5 — requires FdmQuantoHelper port + FD vanilla engine "
          + "quanto-helper hook (Phase 2m FD framework prereq)";

    private static final String REASON_AMERICAN =
            "Phase 5i.5 — requires American FD quanto engine path "
          + "(FdBlackScholesVanillaEngine + quanto helper)";

    private static final String REASON_DOUBLE_BARRIER =
            "Phase 5i.5 — QuantoDoubleBarrierOption exists under experimental; "
          + "in-instruments-package wrapper test deferred until promotion";

    @Ignore(REASON_VANILLA)
    @Test
    public void testValues() { fail("not implemented"); }

    @Ignore(REASON_VANILLA + " + Greeks numerical-derivative cross-check")
    @Test
    public void testGreeks() { fail("not implemented"); }

    @Ignore(REASON_FORWARD)
    @Test
    public void testForwardValues() { fail("not implemented"); }

    @Ignore(REASON_FORWARD + " + Greeks numerical-derivative cross-check")
    @Test
    public void testForwardGreeks() { fail("not implemented"); }

    @Ignore(REASON_FORWARD + " — performance-style discounted-strike variant")
    @Test
    public void testForwardPerformanceValues() { fail("not implemented"); }

    @Ignore(REASON_BARRIER)
    @Test
    public void testBarrierValues() { fail("not implemented"); }

    @Ignore(REASON_FDM_HELPER)
    @Test
    public void testFDMQuantoHelper() { fail("not implemented"); }

    @Ignore(REASON_FDM_HELPER + " — European PDE quanto FD vs analytic")
    @Test
    public void testPDEOptionValues() { fail("not implemented"); }

    @Ignore(REASON_AMERICAN)
    @Test
    public void testAmericanQuantoOption() { fail("not implemented"); }

    @Ignore(REASON_DOUBLE_BARRIER)
    @Test
    public void testDoubleBarrierValues() { fail("not implemented"); }
}
