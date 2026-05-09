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
 * Phase 5k skeleton port of {@code test-suite/extensibleoptions.cpp}
 * v1.42.1 (156 LOC, 2 cases).
 *
 * <p>Exercises the extensible option family (Longstaff 1990): the
 * holder-extensible option (the holder may extend on a fee) and
 * the writer-extensible option (the writer may extend on a fee).
 * Cross-validated against Haug 2007 reference values.
 *
 * <p><strong>All 2 cases deferred to Phase 5k.5</strong> — Java has no
 * extensible-option subsystem:
 * <ul>
 *   <li>No {@code HolderExtensibleOption} / {@code WriterExtensibleOption}
 *       instrument classes;
 *   <li>No {@code AnalyticHolderExtensibleOptionEngine} /
 *       {@code AnalyticWriterExtensibleOptionEngine} (Longstaff 1990
 *       closed-form engines);
 *   <li>The bivariate normal CDF used by the Longstaff formulas is
 *       present in {@code org.jquantlib.math.distributions} but not
 *       wired through an extensible-option engine.
 * </ul>
 *
 * <p>Production-code carry-forward to a future option-extensions phase;
 * Phase 5k.5 is the test-only carry-forward tag.
 *
 * <p>Source: {@code test-suite/extensibleoptions.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class ExtensibleOptionsTest {

    private static final String REASON_HOLDER =
            "Phase 5k.5 — requires HolderExtensibleOption + "
          + "AnalyticHolderExtensibleOptionEngine (Longstaff 1990 holder branch)";

    private static final String REASON_WRITER =
            "Phase 5k.5 — requires WriterExtensibleOption + "
          + "AnalyticWriterExtensibleOptionEngine (Longstaff 1990 writer branch)";

    @Ignore(REASON_HOLDER) @Test public void testAnalyticHolderExtensibleOptionEngine() { fail("not implemented"); }
    @Ignore(REASON_WRITER) @Test public void testAnalyticWriterExtensibleOptionEngine() { fail("not implemented"); }
}
