/*
 Copyright (C) 2005, 2006 Klaus Spanderen (C++ original).
 Copyright (C) 2026 JQuantLib migration contributors (Java port).

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.model;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.legacy.libormarkets.LfmCovarianceProxy;
import org.jquantlib.legacy.libormarkets.LmCorrelationModel;
import org.jquantlib.legacy.libormarkets.LmExponentialCorrelationModel;
import org.jquantlib.legacy.libormarkets.LmLinearExponentialVolatilityModel;
import org.jquantlib.legacy.libormarkets.LmVolatilityModel;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Java port of {@code test-suite/libormarketmodel.cpp} v1.42.1 (465 LOC,
 * 4 test cases).
 *
 * <p>Status (Phase 5e.5b-CFC-d-132):
 * <ul>
 *   <li>{@code testSimpleCovarianceModels} — <strong>body-filled</strong>.
 *       The C++ test exercises (i) the exponential correlation model
 *       reconstructs from its pseudo-square-root, (ii) the
 *       linear-exponential volatility model returns the closed-form
 *       Brigo-Mercurio-Morini values, and (iii) the
 *       {@link LfmCovarianceProxy} composes covariance from diffusion
 *       consistently. None of those checks require a fully-functional
 *       {@code LiborForwardModelProcess}, so we omit the dead
 *       process/model construction the C++ test performed but never
 *       used (see C++ lines 144-148).</li>
 *   <li>{@code testCapletPricing} — deferred: needs
 *       {@code LfmHullWhiteParameterization} and an
 *       {@code AnalyticCapFloorEngine} wired to a calibrated
 *       {@code LiborForwardModel}.</li>
 *   <li>{@code testCalibration} — deferred: needs {@code CapHelper},
 *       {@code SwaptionHelper}, {@code LfmSwaptionEngine}, and a
 *       Levenberg-Marquardt calibration loop over
 *       {@code LiborForwardModel}.</li>
 *   <li>{@code testSwaptionPricing} — deferred: needs the
 *       {@code MultiPathGenerator} / {@code PseudoRandom} pipeline plus
 *       {@code LiborForwardModel::S_0} and an LFM swaption engine.</li>
 * </ul>
 *
 * <p>Source: {@code test-suite/libormarketmodel.cpp} v1.42.1 @
 * {@code 099987f0ca}.
 */
public class LiborMarketModelTest {

    @Test
    public void testSimpleCovarianceModels() {
        final int size = 10;
        final double tolerance = 1e-14;

        // ----- (i) Exponential correlation reconstructs from its pseudo-sqrt
        final LmCorrelationModel corrModel = new LmExponentialCorrelationModel(size, 0.1);

        final Matrix corr = corrModel.correlation(0.0);
        final Matrix ps = corrModel.pseudoSqrt(0.0);
        final Matrix recon = corr.sub(ps.mul(ps.transpose()));

        for (int i = 0; i < size; ++i) {
            for (int j = 0; j < size; ++j) {
                if (Math.abs(recon.get(i, j)) > tolerance) {
                    fail("Failed to reproduce correlation matrix"
                            + "\n    calculated: " + recon.get(i, j)
                            + "\n    expected:   0");
                }
            }
        }

        // ----- (ii) volatility surface against closed-form Brigo-Mercurio-Morini
        final List<Double> fixingTimes = new ArrayList<>(size);
        for (int i = 0; i < size; ++i) {
            fixingTimes.add(0.5 * i);
        }

        final double a = 0.2;
        final double b = 0.1;
        final double c = 2.1;
        final double d = 0.3;

        final LmVolatilityModel volaModel =
                new LmLinearExponentialVolatilityModel(fixingTimes, a, b, c, d);

        final LfmCovarianceProxy covarProxy = new LfmCovarianceProxy(volaModel, corrModel);

        // (note) the C++ test additionally instantiates a LiborForwardModelProcess
        // and a LiborForwardModel here but never references them again; we omit
        // those allocations until those classes are ported.

        for (double t = 0; t < 4.6; t += 0.31) {
            // covariance(t) == diffusion(t) * diffusion(t)^T
            final Matrix diff = covarProxy.diffusion(t);
            final Matrix reconCov = covarProxy.covariance(t).sub(diff.mul(diff.transpose()));

            for (int i = 0; i < size; ++i) {
                for (int j = 0; j < size; ++j) {
                    if (Math.abs(reconCov.get(i, j)) > tolerance) {
                        fail("Failed to reproduce covariance/diffusion identity"
                                + "\n    t: " + t
                                + "\n    (i,j): (" + i + "," + j + ")"
                                + "\n    calculated: " + reconCov.get(i, j)
                                + "\n    expected:   0");
                    }
                }
            }

            // Closed-form: sigma_k(t) = (a*(T_k - t) + d) * exp(-b*(T_k - t)) + c
            //              whenever k > 2*t, else 0
            final Array volatility = volaModel.volatility(t);
            for (int k = 0; k < size; ++k) {
                double expected = 0.0;
                if ((double) k > 2.0 * t) {
                    final double T = fixingTimes.get(k);
                    expected = (a * (T - t) + d) * Math.exp(-b * (T - t)) + c;
                }
                if (Math.abs(expected - volatility.get(k)) > tolerance) {
                    fail("Failed to reproduce volatilities"
                            + "\n    t: " + t
                            + "\n    k: " + k
                            + "\n    calculated: " + volatility.get(k)
                            + "\n    expected:   " + expected);
                }
            }
        }
        assertTrue(true);
    }

    @Ignore("Phase 5e.5b-CFC-d-132+ — needs LfmHullWhiteParameterization, "
            + "AnalyticCapFloorEngine wired to a calibrated LiborForwardModel, "
            + "and a working LiborForwardModelProcess constructor")
    @Test
    public void testCapletPricing() { fail("not implemented"); }

    @Ignore("Phase 5e.5b-CFC-d-132+ — needs CapHelper/SwaptionHelper, "
            + "LfmSwaptionEngine, LiborForwardModel + LM calibration loop")
    @Test
    public void testCalibration() { fail("not implemented"); }

    @Ignore("Phase 5e.5b-CFC-d-132+ — needs MultiPathGenerator/PseudoRandom + "
            + "LiborForwardModel::S_0 + LfmSwaptionEngine")
    @Test
    public void testSwaptionPricing() { fail("not implemented"); }
}
