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
 * Phase 5i skeleton port of {@code test-suite/asianoptions.cpp} v1.42.1
 * test cases NOT already covered by {@link AsianOptionTest}.
 *
 * <p>{@link AsianOptionTest} (Phase 1 / 2) exercises the 4 base analytic
 * cases plus their Greeks:
 * <ul>
 *   <li>{@code testAnalyticContinuousGeometricAveragePrice}</li>
 *   <li>{@code testAnalyticContinuousGeometricAveragePriceGreeks}</li>
 *   <li>{@code testAnalyticDiscreteGeometricAveragePrice}</li>
 *   <li>{@code testAnalyticDiscreteGeometricAveragePriceGreeks}</li>
 * </ul>
 *
 * <p>The remaining ~19 cases below exercise:
 * <ul>
 *   <li><strong>MC discrete arithmetic / geometric engines</strong> —
 *       require {@code MCDiscreteGeometricAPEngine}, {@code
 *       MCDiscreteArithmeticAPEngine}, {@code MCDiscreteArithmeticASEngine}
 *       (Java has the {@code DiscreteAveragingAsianOption} instrument and
 *       the {@code MakeMCDiscreteGeometricAPEngine} factory family is
 *       partially scaffolded under {@code pricingengines.asian}, but the
 *       MC engines themselves are not yet ported);</li>
 *   <li><strong>Heston-driven Asian engines</strong> —
 *       {@code MCDiscreteGeometricAPHestonEngine},
 *       {@code MCDiscreteArithmeticAPHestonEngine},
 *       {@code AnalyticContinuousGeometricAveragePriceAsianHestonEngine},
 *       {@code AnalyticDiscreteGeometricAveragePriceAsianHestonEngine}
 *       (the analytic-Heston engines exist under
 *       {@code experimental.asian}; their tests live there too —
 *       these wrap the in-instrument-package C++ cases);</li>
 *   <li><strong>Turnbull-Wakeman / Levy / Vecer / Choi analytic engines</strong>
 *       — require {@code TurnbullWakemanAsianEngine},
 *       {@code AnalyticContinuousArithmeticAsianLevyEngine},
 *       {@code ContinuousArithmeticAsianVecerEngine},
 *       {@code ChoiAsianEngine}.  The Vecer engine has Java coverage
 *       under {@code experimental.exoticoptions.ContinuousArithmeticAsianVecerEngine};
 *       Turnbull-Wakeman / Levy / Choi are not yet ported.</li>
 *   <li><strong>Past fixings semantics</strong> — require completed past-fixing
 *       wiring on {@link org.jquantlib.instruments.DiscreteAveragingAsianOption}
 *       (Java instrument exists; past-fixing accumulator path is not
 *       fully ported).</li>
 * </ul>
 *
 * <p>Source: {@code test-suite/asianoptions.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class AsianOptionsAdditionalTest {

    private static final String REASON_MC =
            "Phase 5i.5 — requires MC discrete-geometric / discrete-arithmetic "
          + "Asian engines (MakeMCDiscreteGeometricAPEngine family)";

    private static final String REASON_MC_HESTON =
            "Phase 5i.5 — requires MC Heston-driven Asian engines "
          + "(MCDiscreteGeometricAPHestonEngine, MCDiscreteArithmeticAPHestonEngine)";

    private static final String REASON_ANALYTIC_HESTON =
            "Phase 5i.5 — analytic Heston Asian engines exist under "
          + "experimental.asian; the in-instruments-package (non-experimental) "
          + "wrapper test is deferred until the experimental classes are "
          + "promoted out of experimental";

    private static final String REASON_TW =
            "Phase 5i.5 — requires TurnbullWakemanAsianEngine port "
          + "(no Java equivalent yet)";

    private static final String REASON_LEVY =
            "Phase 5i.5 — requires AnalyticContinuousArithmeticAsianLevyEngine "
          + "port (no Java equivalent yet)";

    private static final String REASON_VECER =
            "Phase 5i.5 — Vecer engine ported under experimental.exoticoptions; "
          + "in-instruments-package wrapper test deferred until the experimental "
          + "engine is promoted";

    private static final String REASON_CHOI =
            "Phase 5i.5 — requires ChoiAsianEngine port (newer v1.41+ engine, "
          + "no Java equivalent yet)";

    private static final String REASON_PAST_FIXINGS =
            "Phase 5i.5 — past-fixing accumulator path on "
          + "DiscreteAveragingAsianOption requires completing the running-sum "
          + "/ running-product wiring against C++ semantics";

    private static final String REASON_SEASONED =
            "Phase 5i.5 — Choi engine prereq + seasoned-option time-step "
          + "schedule generation against C++ v1.42.1 semantics";

    @Ignore(REASON_ANALYTIC_HESTON)
    @Test
    public void testAnalyticContinuousGeometricAveragePriceHeston() { fail("not implemented"); }

    @Ignore(REASON_ANALYTIC_HESTON)
    @Test
    public void testAnalyticDiscreteGeometricAveragePriceHeston() { fail("not implemented"); }

    @Ignore(REASON_ANALYTIC_HESTON + " + past-fixings semantics")
    @Test
    public void testDiscreteGeometricAveragePriceHestonPastFixings() { fail("not implemented"); }

    @Ignore("AsianOptionTest covers Strike-flavour discrete geometric analytic")
    @Test
    public void testAnalyticDiscreteGeometricAverageStrike() { fail("not implemented"); }

    @Ignore(REASON_MC)
    @Test
    public void testMCDiscreteGeometricAveragePrice() { fail("not implemented"); }

    @Ignore(REASON_MC_HESTON)
    @Test
    public void testMCDiscreteGeometricAveragePriceHeston() { fail("not implemented"); }

    @Ignore(REASON_MC)
    @Test
    public void testMCDiscreteArithmeticAveragePrice() { fail("not implemented"); }

    @Ignore(REASON_MC_HESTON)
    @Test
    public void testMCDiscreteArithmeticAveragePriceHeston() { fail("not implemented"); }

    @Ignore(REASON_MC)
    @Test
    public void testMCDiscreteArithmeticAverageStrike() { fail("not implemented"); }

    @Ignore(REASON_MC + " + EuropeanExercise-date scheduling variant")
    @Test
    public void testMCDiscreteArithmeticAverageStrikeExerciseDate() { fail("not implemented"); }

    @Ignore(REASON_PAST_FIXINGS)
    @Test
    public void testPastFixings() { fail("not implemented"); }

    @Ignore(REASON_PAST_FIXINGS + " + model-dependence verification (MC / FD)")
    @Test
    public void testPastFixingsModelDependency() { fail("not implemented"); }

    @Ignore(REASON_PAST_FIXINGS + " + degenerate all-past schedule")
    @Test
    public void testAllFixingsInThePast() { fail("not implemented"); }

    @Ignore(REASON_TW)
    @Test
    public void testTurnbullWakemanAsianEngine() { fail("not implemented"); }

    @Ignore(REASON_LEVY)
    @Test
    public void testLevyEngine() { fail("not implemented"); }

    @Ignore(REASON_VECER)
    @Test
    public void testVecerEngine() { fail("not implemented"); }

    @Ignore(REASON_CHOI + " — vs MC reference")
    @Test
    public void testChoiAsianEngineVsMC() { fail("not implemented"); }

    @Ignore(REASON_CHOI + " — special cases (deep ITM/OTM, very short maturity)")
    @Test
    public void testChoiAsianEngineSpecialCases() { fail("not implemented"); }

    @Ignore(REASON_SEASONED)
    @Test
    public void testContinuousSeasonedAsianOptions() { fail("not implemented"); }
}
