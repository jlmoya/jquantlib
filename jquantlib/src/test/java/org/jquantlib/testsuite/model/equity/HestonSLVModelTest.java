/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.model.equity;

import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.finitedifferences.meshers.Fdm1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesherComposite;
import org.jquantlib.methods.finitedifferences.meshers.Predefined1dMesher;
import org.jquantlib.methods.finitedifferences.utilities.SquareRootProcessRNDCalculator;
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

    /**
     * Mirrors C++ {@code createStationaryDistributionMesher}
     * (test-suite/hestonslvmodel.cpp:166). Mesh points are inverse-CDF
     * quantiles of the stationary chi-square distribution, in a fixed
     * {@code (qMin, qMax)} probability band.
     */
    private static FdmMesher createStationaryDistributionMesher(
            final double kappa, final double theta, final double sigma, final int vGrid) {
        final double qMin = 0.01;
        final double qMax = 0.99;
        final double dq = (qMax - qMin) / (vGrid - 1);

        final SquareRootProcessRNDCalculator rnd =
                new SquareRootProcessRNDCalculator(theta, kappa, theta, sigma);
        final double[] v = new double[vGrid];
        for (int i = 0; i < vGrid; ++i) {
            v[i] = rnd.stationary_invcdf(qMin + i * dq);
        }

        final List<Fdm1dMesher> ms = new ArrayList<Fdm1dMesher>(1);
        ms.add(new Predefined1dMesher(v));
        return new FdmMesherComposite(ms);
    }

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

    /**
     * Verifies the zero-flow boundary condition for the transformed
     * Fokker-Planck forward equation of the square-root process. Mirrors
     * C++ {@code testTransformedZeroFlowBC} (test-suite/hestonslvmodel.cpp:894).
     *
     * <p>The test builds a stationary-distribution mesher (mesh points are the
     * inverse-CDF quantiles of the stationary gamma distribution), evaluates
     * the stationary PDF on the mesh, transforms via {@code q = v^alpha * p}
     * (the natural variable for the {@code Power} transformation in
     * {@code FdmSquareRootFwdOp}), and checks that a 2nd-order one-sided
     * finite-difference of {@code q} satisfies the zero-flow BC at each of
     * the lower-half mesh points. Tolerance: 1e-6 absolute (matches C++).
     *
     * <p>Java uses {@code SquareRootProcessRNDCalculator.stationary_pdf}
     * (closed-form gamma — TIGHT) and {@code stationary_invcdf} (Brent —
     * LOOSE 1e-5). The 1e-5 invcdf slack moves mesh points but does not break
     * the analytic zero-flow identity at machine precision (the BC is exact).
     */
    @Test
    public void testTransformedZeroFlowBC() {
        final double kappa = 1.0;
        final double theta = 0.4;
        final double sigma = 2.0;
        final int vGrid = 100;

        final FdmMesher mesher = createStationaryDistributionMesher(kappa, theta, sigma, vGrid);
        final Array v = mesher.locations(0);

        final double[] p = new double[vGrid];
        final SquareRootProcessRNDCalculator rnd =
                new SquareRootProcessRNDCalculator(theta, kappa, theta, sigma);
        for (int i = 0; i < v.size(); ++i) {
            p[i] = rnd.stationary_pdf(v.get(i));
        }

        final double alpha = 1.0 - 2.0 * kappa * theta / (sigma * sigma);
        final double[] q = new double[vGrid];
        for (int i = 0; i < vGrid; ++i) {
            q[i] = Math.pow(v.get(i), alpha) * p[i];
        }

        for (int i = 0; i < vGrid / 2; ++i) {
            final double hm = v.get(i + 1) - v.get(i);
            final double hp = v.get(i + 2) - v.get(i + 1);

            final double eta = 1.0 / (hm * (hm + hp) * hp);
            final double a = -eta * ((hm + hp) * (hm + hp) - hm * hm);
            final double b =  eta * ((hm + hp) * (hm + hp));
            final double c = -eta * hm * hm;

            final double df = a * q[i] + b * q[i + 1] + c * q[i + 2];
            final double flow = 0.5 * sigma * sigma * v.get(i) * df + kappa * v.get(i) * q[i];

            final double tol = 1.0e-6;
            if (Math.abs(flow) > tol) {
                fail("failed to reproduce Zero Flow BC at i=" + i
                        + "\n   v[i]:       " + v.get(i)
                        + "\n   flow:       " + flow
                        + "\n   tolerance:  " + tol);
            }
        }
    }

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
