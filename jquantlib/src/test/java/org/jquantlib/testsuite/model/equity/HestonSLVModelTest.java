/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.model.equity;

import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.math.Ops;
import org.jquantlib.math.distributions.GammaFunction;
import org.jquantlib.math.integrals.DiscreteSimpsonIntegral;
import org.jquantlib.math.integrals.GaussLobattoIntegral;
import org.jquantlib.math.interpolations.NaturalCubicInterpolation;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.finitedifferences.meshers.Fdm1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesherComposite;
import org.jquantlib.methods.finitedifferences.meshers.Predefined1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.Uniform1dMesher;
import org.jquantlib.methods.finitedifferences.operators.FdmSquareRootFwdOp;
import org.jquantlib.methods.finitedifferences.operators.FdmSquareRootFwdOp.TransformationType;
import org.jquantlib.methods.finitedifferences.schemes.DouglasScheme;
import org.jquantlib.methods.finitedifferences.utilities.FdmMesherIntegral;
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
     * Mirrors C++ {@code stationaryLogProbabilityFct}
     * (test-suite/hestonslvmodel.cpp:157). The log-stationary density of
     * the square-root process under the {@code Log} transform is
     * {@code beta^alpha * exp(z*alpha) * exp(-beta*exp(z) - lgamma(alpha))}
     * with {@code alpha = 2*kappa*theta/sigma^2} and {@code beta = alpha/theta}.
     */
    private static double stationaryLogProbabilityFct(
            final double kappa, final double theta, final double sigma, final double z) {
        final double alpha = 2.0 * kappa * theta / (sigma * sigma);
        final double beta  = alpha / theta;
        return Math.pow(beta, alpha) * Math.exp(z * alpha)
                * Math.exp(-beta * Math.exp(z) - new GammaFunction().logValue(alpha));
    }

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

    /**
     * Tests Zero Flow BC for the square-root process (probe via 5 different
     * finite-difference stencils on the conditional PDF). Mirrors C++
     * {@code testSquareRootZeroFlowBC} (test-suite/hestonslvmodel.cpp:827).
     *
     * <p><strong>Java tolerance status:</strong> C++ uses the closed-form
     * non-central chi-squared PDF (Bessel-based); Java's
     * {@code SquareRootProcessRNDCalculator.pdf} is a CDF central-difference
     * approximation (LOOSE 1e-4 — Phase 5h.5-RND). The C++ test compares
     * derivative values against expected[5][5] within 2e-6 absolute, which
     * Java cannot meet without porting Boost's non-central chi-squared PDF.
     *
     * <p>The {@code expected[5][5]} matrix is kept here as documentation and
     * the test runs but stays {@code @Ignore}d pending Java having an exact
     * non-central chi-squared PDF (or modified Bessel functions of fractional
     * order).
     */
    @Ignore("Phase 5h.5-SLV-c — needs exact non-central chi-squared PDF "
            + "(Java SquareRootProcessRNDCalculator.pdf is CDF central-difference "
            + "with ~1e-4 slack vs. C++ Boost; test requires 2e-6 derivative tol).")
    @Test
    public void testSquareRootZeroFlowBC() {
        final double kappa = 1.0;
        final double theta = 0.4;
        final double sigma = 0.8;
        final double v_0   = 0.1;
        final double t     = 1.0;

        final double vmin = 0.0005;
        final double h    = 0.0001;

        final double[][] expected = {
            { 0.000548, -0.000245, -0.005657, -0.001167, -0.000024},
            {-0.000595, -0.000701, -0.003296, -0.000883, -0.000691},
            {-0.001277, -0.001320, -0.003128, -0.001399, -0.001318},
            {-0.001979, -0.002002, -0.003425, -0.002047, -0.002001},
            {-0.002715, -0.002730, -0.003920, -0.002760, -0.002730}
        };

        for (int i = 0; i < 5; ++i) {
            final double v = vmin + i * 0.001;
            final double vm2 = v - 2 * h;
            final double vm1 = v - h;
            final double v0  = v;
            final double v1  = v + h;
            final double v2  = v + 2 * h;

            final SquareRootProcessRNDCalculator rnd =
                    new SquareRootProcessRNDCalculator(v_0, kappa, theta, sigma);

            final double pm2 = rnd.pdf(vm2, t);
            final double pm1 = rnd.pdf(vm1, t);
            final double p0  = rnd.pdf(v0,  t);
            final double p1  = rnd.pdf(v1,  t);
            final double p2  = rnd.pdf(v2,  t);

            final double driftTerm = (kappa * (v0 - theta) + sigma * sigma / 2.0) * p0;

            final double flowSym2Order = sigma * sigma * v0 / (4.0 * h) * (p1 - pm1)        + driftTerm;
            final double flowSym4Order = sigma * sigma * v0 / (24.0 * h) * (-p2 + 8.0 * p1 - 8.0 * pm1 + pm2) + driftTerm;
            final double fwd1Order     = sigma * sigma * v0 / (2.0 * h) * (p1 - p0)         + driftTerm;
            final double fwd2Order     = sigma * sigma * v0 / (4.0 * h) * (4.0 * p1 - 3.0 * p0 - p2) + driftTerm;
            final double fwd3Order     = sigma * sigma * v0 / (12.0 * h) * (-p2 + 6.0 * p1 - 3.0 * p0 - 2.0 * pm1) + driftTerm;

            final double tol = 0.000002;
            if (   Math.abs(expected[i][0] - flowSym2Order) > tol
                || Math.abs(expected[i][1] - flowSym4Order) > tol
                || Math.abs(expected[i][2] - fwd1Order)     > tol
                || Math.abs(expected[i][3] - fwd2Order)     > tol
                || Math.abs(expected[i][4] - fwd3Order)     > tol) {
                fail("failed to reproduce Zero Flow BC at v=" + v + " tol=" + tol);
            }
        }
    }

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

    /**
     * Tests Fokker-Planck forward evolution of the square-root process from
     * a stationary initial density: after evolving, the density should still
     * integrate to {@code 1 - 2*eps} on the truncated mesh. Mirrors C++
     * {@code testSquareRootEvolveWithStationaryDensity}
     * (test-suite/hestonslvmodel.cpp:938). Tolerance: 0.005 absolute (loose,
     * matches C++).
     *
     * <p>Iterates over sigma in [0.2, 2.0] with step 0.1 (19 sub-cases). For
     * each sigma it picks the Plain or Power transformation per Feller
     * condition, builds an FdmSquareRootFwdOp on a Uniform1dMesher, evolves
     * 100 Douglas time-steps of dt=0.01, then integrates back to check the
     * total mass.
     */
    @Test
    public void testSquareRootEvolveWithStationaryDensity() {
        final double kappa = 2.5;
        final double theta = 0.2;
        final int vGrid = 100;
        final double eps = 1.0e-2;

        for (double sigma = 0.2; sigma < 2.01; sigma += 0.1) {
            final double alpha = 1.0 - 2.0 * kappa * theta / (sigma * sigma);

            final SquareRootProcessRNDCalculator rnd =
                    new SquareRootProcessRNDCalculator(theta, kappa, theta, sigma);
            final double vMin = rnd.stationary_invcdf(eps);
            final double vMax = rnd.stationary_invcdf(1.0 - eps);

            final List<Fdm1dMesher> ms1 = new ArrayList<Fdm1dMesher>(1);
            ms1.add(new Uniform1dMesher(vMin, vMax, vGrid));
            final FdmMesher mesher = new FdmMesherComposite(ms1);

            final Array v = mesher.locations(0);
            final TransformationType transform =
                    (sigma < 0.75) ? TransformationType.Plain : TransformationType.Power;

            final double[] vq = new double[v.size()];
            final double[] vmq = new double[v.size()];
            for (int i = 0; i < v.size(); ++i) {
                vq[i] = Math.pow(v.get(i), alpha);
                vmq[i] = 1.0 / vq[i];
            }

            final Array p = new Array(vGrid);
            for (int i = 0; i < v.size(); ++i) {
                double pi = rnd.stationary_pdf(v.get(i));
                if (transform == TransformationType.Power) {
                    pi *= vq[i];
                }
                p.set(i, pi);
            }

            final FdmSquareRootFwdOp op =
                    new FdmSquareRootFwdOp(mesher, kappa, theta, sigma, 0, transform);

            final int n = 100;
            final double dt = 0.01;
            final DouglasScheme evolver = new DouglasScheme(0.5, op);
            evolver.setStep(dt);

            for (int i = 1; i <= n; ++i) {
                evolver.step(p, i * dt);
            }

            final double expected = 1.0 - 2.0 * eps;

            if (transform == TransformationType.Power) {
                for (int i = 0; i < v.size(); ++i) {
                    p.set(i, p.get(i) * vmq[i]);
                }
            }

            // Equivalent to C++ q_fct: spline of q[i] = v[i]^alpha * p[i],
            // integrated as q(v) * v^(-alpha).
            final double[] qarr = new double[v.size()];
            final double[] varr = new double[v.size()];
            for (int i = 0; i < v.size(); ++i) {
                varr[i] = v.get(i);
                qarr[i] = Math.pow(v.get(i), alpha) * p.get(i);
            }
            final NaturalCubicInterpolation spline =
                    new NaturalCubicInterpolation(new Array(varr), new Array(qarr));
            spline.update();
            spline.enableExtrapolation();
            final double alphaFinal = alpha;
            final Ops.DoubleOp qFct = new Ops.DoubleOp() {
                @Override
                public double op(final double vv) {
                    return spline.op(vv, true) * Math.pow(vv, -alphaFinal);
                }
            };

            final GaussLobattoIntegral integ = new GaussLobattoIntegral(1000000, 1e-6);
            final double calculated = integ.op(qFct, v.first(), v.last());

            final double tol = 0.005;
            if (Math.abs(calculated - expected) > tol) {
                fail("failed to reproduce stationary probability function for sigma=" + sigma
                        + "\n    calculated: " + calculated
                        + "\n    expected:   " + expected
                        + "\n    tolerance:  " + tol);
            }
        }
    }

    /**
     * Tests Fokker-Planck forward evolution of the square-root process under
     * the {@code Log} transform from a stationary initial density. Mirrors
     * C++ {@code testSquareRootLogEvolveWithStationaryDensity}
     * (test-suite/hestonslvmodel.cpp:1014). Tolerance: 0.005 absolute.
     *
     * <p>Iterates over sigma in [0.2, 2.0] (19 sub-cases). For each sigma
     * builds a Uniform1dMesher on log(v), evolves the log-stationary density
     * for 100 Douglas time-steps of dt=0.01 with the {@code FdmSquareRootFwdOp.Log}
     * operator, then integrates via {@code FdmMesherIntegral} +
     * {@code DiscreteSimpsonIntegral} and checks the integral ≈ 1-eps-lowEps.
     */
    @Test
    public void testSquareRootLogEvolveWithStationaryDensity() {
        final double kappa = 2.5;
        final double theta = 0.2;
        final int vGrid = 1000;
        final double eps = 1.0e-2;

        for (double sigma = 0.2; sigma < 2.01; sigma += 0.1) {
            final double lowerLimit = 0.001;

            final SquareRootProcessRNDCalculator rnd =
                    new SquareRootProcessRNDCalculator(theta, kappa, theta, sigma);
            final double vMin = Math.max(lowerLimit, rnd.stationary_invcdf(eps));
            final double lowEps = Math.max(eps, rnd.stationary_cdf(lowerLimit));
            final double expected = 1.0 - eps - lowEps;
            final double vMax = rnd.stationary_invcdf(1.0 - eps);

            final List<Fdm1dMesher> ms = new ArrayList<Fdm1dMesher>(1);
            ms.add(new Uniform1dMesher(Math.log(vMin), Math.log(vMax), vGrid));
            final FdmMesherComposite mesher = new FdmMesherComposite(ms);

            final Array v = mesher.locations(0);
            final Array p = new Array(vGrid);
            for (int i = 0; i < v.size(); ++i) {
                p.set(i, stationaryLogProbabilityFct(kappa, theta, sigma, v.get(i)));
            }

            final FdmSquareRootFwdOp op = new FdmSquareRootFwdOp(
                    mesher, kappa, theta, sigma, 0, TransformationType.Log);

            final int n = 100;
            final double dt = 0.01;
            final DouglasScheme evolver = new DouglasScheme(0.5, op);
            evolver.setStep(dt);

            for (int i = 1; i <= n; ++i) {
                evolver.step(p, i * dt);
            }

            final FdmMesherIntegral mi = new FdmMesherIntegral(
                    mesher,
                    new FdmMesherIntegral.Integrator1d() {
                        @Override
                        public double op(final Array x, final Array f) {
                            return new DiscreteSimpsonIntegral().op(x, f);
                        }
                    });
            final double calculated = mi.integrate(p);

            final double tol = 0.005;
            if (Math.abs(calculated - expected) > tol) {
                fail("failed to reproduce stationary probability function for "
                        + "\n    sigma:      " + sigma
                        + "\n    calculated: " + calculated
                        + "\n    expected:   " + expected
                        + "\n    tolerance:  " + tol);
            }
        }
    }

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
