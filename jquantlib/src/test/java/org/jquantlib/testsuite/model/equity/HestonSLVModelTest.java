/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.model.equity;

import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5h skeleton port of {@code test-suite/hestonslvmodel.cpp} v1.42.1
 * (2,686 LOC, 15 active test cases — three additional cases are commented
 * out in the C++ source: {@code testFDMCalibration},
 * {@code testBarrierPricingMixedModels}, {@code testForwardSkewSLV},
 * {@code testBarrierPricingMixedModelsMonteCarloVsFdmPricing}).
 *
 * <p>The 15 active tests exercise the Heston Stochastic-Local-Vol (SLV)
 * model and its constituent infrastructure:
 *
 * <ol>
 *   <li><strong>Fokker-Planck forward PDE</strong> —
 *       {@code testBlackScholesFokkerPlanckFwdEquation},
 *       {@code testSquareRootFokkerPlanckFwdEquation},
 *       {@code testHestonFokkerPlanckFwdEquation},
 *       {@code testHestonFokkerPlanckFwdEquationLogLVLeverage},
 *       {@code testBlackScholesFokkerPlanckFwdEquationLocalVol}.
 *       Verify finite-difference forward Fokker-Planck densities for
 *       BSM, square-root, and Heston processes match analytical
 *       benchmarks.</li>
 *
 *   <li><strong>Square-root boundary &amp; stationary density</strong> —
 *       {@code testSquareRootZeroFlowBC},
 *       {@code testTransformedZeroFlowBC},
 *       {@code testSquareRootEvolveWithStationaryDensity},
 *       {@code testSquareRootLogEvolveWithStationaryDensity}.</li>
 *
 *   <li><strong>SLV calibration / propagation</strong> —
 *       {@code testLocalVolsvSLVPropDensity},
 *       {@code testMonteCarloCalibration} (CPU-intensive).</li>
 *
 *   <li><strong>Pricing checks</strong> —
 *       {@code testBarrierPricingViaHestonLocalVol},
 *       {@code testMonteCarloVsFdmPricing},
 *       {@code testMoustacheGraph}.</li>
 *
 *   <li><strong>Process discretization</strong> —
 *       {@code testDiffusionAndDriftSlvProcess}.</li>
 * </ol>
 *
 * <p><strong>Phase 5h.5-SLV-b status:</strong> Java now has most of the
 * SLV-specific infrastructure. Body-fills can land in Phase 5h.5-SLV-c:
 * <ul>
 *   <li>{@code FdmSquareRootFwdOp} — landed (Phase 5h.5-SLV WI-1);</li>
 *   <li>{@code FdmHestonFwdOp} — landed (Phase 5h.5-SLV WI-2);</li>
 *   <li>{@code HestonStochasticLocalVolProcess} — landed (Phase 5h.5-SLV WI-3);</li>
 *   <li>{@code FdmLocalVolFwdOp}, {@code LocalVolRNDCalculator},
 *       {@code FixedLocalVolSurface} — landed (Phase 5h.5-RND-b);</li>
 *   <li>{@code FdmHestonGreensFct}, {@code FdmMesherIntegral} —
 *       landed (Phase 5h.5-SLV-b);</li>
 *   <li>{@code HestonSLVFDMModel}, {@code HestonSLVMCModel} —
 *       body-filled (Phase 5h.5-SLV-b).</li>
 * </ul>
 * Still missing:
 * <ul>
 *   <li>{@code NoExceptLocalVolSurface}, {@code GridModelLocalVolSurface};</li>
 *   <li>{@code SquareRootProcessFwdSolver}, generic
 *       {@code FokkerPlanckFwdEquation} backward-PDE adapters;</li>
 *   <li>Multi-cPoint {@code Concentrating1dMesher} variant;</li>
 *   <li>{@code HestonProcess.pdf()} (Fourier inversion) for
 *       FdmHestonGreensFct.SemiAnalytical algorithm.</li>
 * </ul>
 *
 * <p>Slow-test discipline (Phase 5 META D8): once enabled,
 * {@code testMonteCarloCalibration} and {@code testMonteCarloVsFdmPricing}
 * must be tagged {@code @Tag("slow")}.
 *
 * <p>Source: {@code test-suite/hestonslvmodel.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class HestonSLVModelTest {

    private static final String REASON_FP =
            "Phase 5h.5 — requires FokkerPlanckFwdEquation solver + Fdm forward operators "
            + "(no Java equivalent; Phase 2m only ported the backward Heston FD stack via HHW).";

    private static final String REASON_SLV =
            "Phase 5h.5 — requires HestonSLV-family classes "
            + "(HestonStochasticLocalVolProcess + HestonSLVMCModel + HestonSLVFDMModel) "
            + "(Phase 2m carry-forward; D9).";

    private static final String REASON_SLOW =
            "Phase 5h.5 + slow — requires SLV infra and @Tag(\"slow\") (Phase 5 META D8 + D9).";

    /* ---- 1. Fokker-Planck forward PDE -------------------------------- */

    @Ignore(REASON_FP)
    @Test
    public void testBlackScholesFokkerPlanckFwdEquation() { fail("not implemented"); }

    @Ignore(REASON_FP)
    @Test
    public void testSquareRootFokkerPlanckFwdEquation() { fail("not implemented"); }

    @Ignore(REASON_FP)
    @Test
    public void testHestonFokkerPlanckFwdEquation() { fail("not implemented"); }

    @Ignore(REASON_FP)
    @Test
    public void testHestonFokkerPlanckFwdEquationLogLVLeverage() { fail("not implemented"); }

    @Ignore(REASON_FP)
    @Test
    public void testBlackScholesFokkerPlanckFwdEquationLocalVol() { fail("not implemented"); }

    /* ---- 2. Square-root boundary / stationary -------------------------- */

    @Ignore(REASON_FP)
    @Test
    public void testSquareRootZeroFlowBC() { fail("not implemented"); }

    @Ignore(REASON_FP)
    @Test
    public void testTransformedZeroFlowBC() { fail("not implemented"); }

    @Ignore(REASON_FP)
    @Test
    public void testSquareRootEvolveWithStationaryDensity() { fail("not implemented"); }

    @Ignore(REASON_FP)
    @Test
    public void testSquareRootLogEvolveWithStationaryDensity() { fail("not implemented"); }

    /* ---- 3. SLV calibration / propagation ----------------------------- */

    @Ignore(REASON_SLV)
    @Test
    public void testLocalVolsvSLVPropDensity() { fail("not implemented"); }

    @Ignore(REASON_SLOW)
    @Test
    public void testMonteCarloCalibration() { fail("not implemented"); }

    /* ---- 4. Pricing checks -------------------------------------------- */

    @Ignore(REASON_SLV)
    @Test
    public void testBarrierPricingViaHestonLocalVol() { fail("not implemented"); }

    @Ignore(REASON_SLOW)
    @Test
    public void testMonteCarloVsFdmPricing() { fail("not implemented"); }

    @Ignore(REASON_SLV)
    @Test
    public void testMoustacheGraph() { fail("not implemented"); }

    /* ---- 5. Process discretization ------------------------------------ */

    @Ignore(REASON_SLV)
    @Test
    public void testDiffusionAndDriftSlvProcess() { fail("not implemented"); }
}
