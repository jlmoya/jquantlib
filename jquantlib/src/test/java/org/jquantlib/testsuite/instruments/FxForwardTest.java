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
 * Phase 5k skeleton port of {@code test-suite/fxforward.cpp} v1.42.1
 * (454 LOC, 13 cases).
 *
 * <p>Exercises the FX forward instrument: construction (notional/contracted
 * rate variants), expiry handling, fair-forward-rate solving, position
 * direction (long/short), discounting engine, IR curve and spot FX
 * sensitivity, additional-results map, and settlement-day handling
 * (with and without an explicit calendar).
 *
 * <p><strong>All 13 cases deferred to Phase 5k.5</strong> — Java has no
 * FX forward instrument or engine:
 * <ul>
 *   <li>No {@code FxForward} instrument class;
 *   <li>No {@code DiscountingFxForwardEngine} pricing engine;
 *   <li>No {@code Position::Type} (Long/Short) enum on FX-forward instruments;
 *   <li>The currency dual-discount-curve framework needed for
 *       {@code testFxForwardConstruction} (paying in CCY1, receiving CCY2)
 *       is present but not wired into a forward instrument.
 * </ul>
 *
 * <p>The FX forward instrument is a foundational FX building block; this
 * is a production-code carry-forward to a future FX-instruments phase.
 * Phase 5k.5 is the test-only carry-forward tag.
 *
 * <p>Source: {@code test-suite/fxforward.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class FxForwardTest {

    private static final String REASON_INSTRUMENT =
            "Phase 5k.5 — requires FxForward instrument port "
          + "(no Java equivalent for the FX forward family yet)";

    private static final String REASON_ENGINE =
            "Phase 5k.5 — requires DiscountingFxForwardEngine port "
          + "(dual-discount-curve discounting engine for FX forwards)";

    private static final String REASON_FAIR_RATE =
            "Phase 5k.5 — requires FxForward + DiscountingFxForwardEngine; "
          + "fairForwardRate is solved by the engine at zero NPV";

    private static final String REASON_POSITION =
            "Phase 5k.5 — requires Position::Type wiring on FxForward "
          + "(long/short notional sign convention)";

    private static final String REASON_SENSITIVITY =
            "Phase 5k.5 — requires FxForward engine wiring for IR-curve "
          + "and spot-FX sensitivity bumping";

    private static final String REASON_ADDITIONAL_RESULTS =
            "Phase 5k.5 — requires FxForward engine populating the "
          + "additional-results map (forwardValue/spotIncome/spotForeignAmount)";

    private static final String REASON_SETTLEMENT =
            "Phase 5k.5 — requires FxForward settlementDate / settlementDays "
          + "wiring (with and without an explicit calendar)";

    @Ignore(REASON_INSTRUMENT)         @Test public void testFxForwardConstruction()             { fail("not implemented"); }
    @Ignore(REASON_INSTRUMENT)         @Test public void testFxForwardConstructionWithRate()     { fail("not implemented"); }
    @Ignore(REASON_INSTRUMENT)         @Test public void testContractedForwardRate()             { fail("not implemented"); }
    @Ignore(REASON_INSTRUMENT)         @Test public void testFxForwardExpiry()                   { fail("not implemented"); }
    @Ignore(REASON_ENGINE)             @Test public void testDiscountingFxForwardEngine()        { fail("not implemented"); }
    @Ignore(REASON_FAIR_RATE)          @Test public void testFairForwardRate()                   { fail("not implemented"); }
    @Ignore(REASON_FAIR_RATE)          @Test public void testAtTheMoney()                        { fail("not implemented"); }
    @Ignore(REASON_POSITION)           @Test public void testPositionDirection()                 { fail("not implemented"); }
    @Ignore(REASON_SENSITIVITY)        @Test public void testIRCurveSensitivity()                { fail("not implemented"); }
    @Ignore(REASON_SENSITIVITY)        @Test public void testSpotFxSensitivity()                 { fail("not implemented"); }
    @Ignore(REASON_ADDITIONAL_RESULTS) @Test public void testAdditionalResults()                 { fail("not implemented"); }
    @Ignore(REASON_SETTLEMENT)         @Test public void testSettlementDays()                    { fail("not implemented"); }
    @Ignore(REASON_SETTLEMENT)         @Test public void testSettlementDaysWithCalendar()        { fail("not implemented"); }
}
