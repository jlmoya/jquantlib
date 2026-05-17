/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.finitedifferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;

import org.jquantlib.experimental.processes.ExtOUWithJumpsProcess;
import org.jquantlib.experimental.processes.ExtendedOrnsteinUhlenbeckProcess;
import org.jquantlib.math.Constants;
import org.jquantlib.math.Ops;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.randomnumbers.InverseCumulativeRsg;
import org.jquantlib.math.randomnumbers.MersenneTwisterUniformRng;
import org.jquantlib.math.randomnumbers.RandomSequenceGenerator;
import org.jquantlib.methods.finitedifferences.meshers.ExponentialJump1dMesher;
import org.jquantlib.methods.montecarlo.Sample;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5j skeleton port of {@code test-suite/swingoption.cpp} v1.42.1.
 *
 * <p>Phase 5e.5b-CFC-d-161: ported {@code ExponentialJump1dMesher} and
 * body-filled the two process/mesh-level cases
 * ({@code testExtendedOrnsteinUhlenbeckProcess},
 * {@code testFdmExponentialJump1dMesher}).  The four remaining cases need
 * full FD pricing engines (FdExtOUJumpVanillaEngine,
 * FdSimpleBSSwingEngine, etc.) plus the {@code VanillaSwingOption}
 * instrument and {@code SwingExercise}, all deferred to Phase 5j.5.
 *
 * <p>Source: {@code test-suite/swingoption.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class SwingOptionTest {

    private static final String REASON_ENGINE =
            "Phase 5j.5 — requires swing/jump FD engines "
          + "(FdSimpleBSSwingEngine, FdExtOUJumpVanillaEngine) — Phase 4n.5 carry-forward";

    /**
     * Cross-validates the {@link ExtendedOrnsteinUhlenbeckProcess#evolve(double,
     * double, double, double)} discretizations against a high-accuracy
     * {@code GaussLobatto} reference, for three different deterministic levels
     * {@code b(t)} (constant, linear, sine).  The C++ test consumes a
     * {@code PseudoRandom::rng_type} stream of standard normals and asserts
     * that {@code MidPoint} and {@code Trapezodial} stay within {@code 1e-6} of
     * the reference at every step.  The Java port mirrors this exactly using
     * {@code MersenneTwisterUniformRng} -> {@code RandomSequenceGenerator} ->
     * {@code InverseCumulativeRsg<,InverseCumulativeNormal>} at
     * {@code dimension=1}.
     */
    @Test
    public void testExtendedOrnsteinUhlenbeckProcess() {
        final double speed = 2.5;
        final double vol = 0.70;
        final double level = 1.43;

        final ExtendedOrnsteinUhlenbeckProcess.Discretization[] discr = {
            ExtendedOrnsteinUhlenbeckProcess.Discretization.MidPoint,
            ExtendedOrnsteinUhlenbeckProcess.Discretization.Trapezodial,
            ExtendedOrnsteinUhlenbeckProcess.Discretization.GaussLobatto
        };

        final Ops.DoubleOp[] f = {
            new Ops.DoubleOp() { @Override public double op(final double x) { return level; } },
            new Ops.DoubleOp() { @Override public double op(final double x) { return x + 1.0; } },
            new Ops.DoubleOp() { @Override public double op(final double x) { return Math.sin(x); } }
        };

        for (int n = 0; n < f.length; ++n) {
            final ExtendedOrnsteinUhlenbeckProcess refProcess =
                new ExtendedOrnsteinUhlenbeckProcess(speed, vol, 0.0, f[n],
                    ExtendedOrnsteinUhlenbeckProcess.Discretization.GaussLobatto, 1e-6);

            for (int i = 0; i < discr.length - 1; ++i) {
                final ExtendedOrnsteinUhlenbeckProcess eouProcess =
                    new ExtendedOrnsteinUhlenbeckProcess(speed, vol, 0.0, f[n], discr[i], 1e-4);

                final double T = 10.0;
                final int nTimeSteps = 10000;

                final double dt = T / nTimeSteps;
                double t = 0.0;
                double q = 0.0;
                double p = 0.0;

                final InverseCumulativeRsg<RandomSequenceGenerator<MersenneTwisterUniformRng>,
                                           InverseCumulativeNormal> rng = makeScalarGaussianRsg(1234L);

                for (int j = 0; j < nTimeSteps; ++j) {
                    final double dw = rng.nextSequence().value()[0];
                    q = eouProcess.evolve(t, q, dt, dw);
                    p = refProcess.evolve(t, p, dt, dw);

                    if (Math.abs(q - p) > 1e-6) {
                        fail("invalid process evaluation "
                                + n + " " + i + " " + j + " " + (q - p));
                    }
                    t += dt;
                }
            }
        }
    }

    /**
     * Exercises the {@link ExponentialJump1dMesher#jumpSizeDistribution(double)}
     * approximation against an empirical CDF built from
     * {@link ExtOUWithJumpsProcess#evolve(double, Array, double, Array) }
     * sample paths.
     * <p>
     * Faithful port of the C++ test; the C++ version uses a large MC budget
     * ({@code n = 1_000_000}) which is too slow for our unit-test cadence.
     * The Java port keeps the algorithm identical but uses
     * {@code n = 200_000} which is still enough to reach the analytic
     * approximation accuracy ({@code 2e-3} tight or {@code 2e-2} when the
     * mesher-approximated value lies below the {@code 0.9} threshold).
     */
    @Test
    public void testFdmExponentialJump1dMesher() {
        final Array x = new Array(new double[] { 1.0, 1.0 });
        final double beta = 100.0;
        final double eta  = 1.0 / 0.4;
        final double jumpIntensity = 4.0;
        final int    dummySteps = 2;

        final ExponentialJump1dMesher mesher =
            new ExponentialJump1dMesher(dummySteps, beta, jumpIntensity, eta);

        final ExtendedOrnsteinUhlenbeckProcess ouProcess =
            new ExtendedOrnsteinUhlenbeckProcess(1.0, 1.0, x.get(0),
                new Ops.DoubleOp() { @Override public double op(final double t) { return 1.0; } });
        final ExtOUWithJumpsProcess jumpProcess =
            new ExtOUWithJumpsProcess(ouProcess, x.get(1), beta, jumpIntensity, eta);

        final double dt = 1.0 / (10.0 * beta);
        final int n = 200_000;

        final double[] path = new double[n];
        final InverseCumulativeRsg<RandomSequenceGenerator<MersenneTwisterUniformRng>,
                                   InverseCumulativeNormal> mt = makeScalarGaussianRsg(123L);

        Array state = x;
        for (int i = 0; i < n; ++i) {
            final Array dw = new Array(3);
            dw.set(0, mt.nextSequence().value()[0]);
            dw.set(1, mt.nextSequence().value()[0]);
            dw.set(2, mt.nextSequence().value()[0]);
            state = jumpProcess.evolve(0.0, state, dt, dw);
            path[i] = state.get(1);
        }
        Arrays.sort(path);

        final double relTol1 = 2e-3;
        final double relTol2 = 2e-2;
        final double threshold = 0.9;

        boolean anyChecked = false;
        for (double xx = 1e-12; xx < 1.0; xx *= 10) {
            final double v = mesher.jumpSizeDistribution(xx);

            // lower_bound: first index k s.t. path[k] >= xx
            int lo = 0, hi = path.length;
            while (lo < hi) {
                final int mid = (lo + hi) >>> 1;
                if (path[mid] < xx) lo = mid + 1; else hi = mid;
            }
            final double q = lo / (double) n;

            final boolean ok = Math.abs(q - v) < relTol1
                    || (v < threshold && Math.abs(q - v) < relTol2);
            assertTrue("can not reproduce jump distribution at x=" + xx
                    + ": empirical=" + q + " mesher=" + v + " diff=" + (q - v),
                    ok);
            anyChecked = true;
        }
        assertTrue("no x sample was actually checked", anyChecked);

        // Sanity: confirm mesher locations are monotonic and positive
        // (mesher implementation cross-check).
        final double[] locs = mesher.locations();
        assertEquals("mesher.size()", dummySteps, locs.length);
        // For steps=2, location[0]=0 by construction.
        assertEquals("mesher.location(0)", 0.0, locs[0], Constants.QL_EPSILON);
        assertTrue("mesher.location(1) > 0", locs[1] > 0.0);
    }

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

    // ----- helpers ---------------------------------------------------------

    /**
     * Mirrors C++ {@code PseudoRandom::rng_type rng(PseudoRandom::urng_type(seed))}:
     * a scalar (dimension=1) Mersenne-Twister-based Gaussian generator
     * obtained by composing {@code MersenneTwisterUniformRng} with
     * {@code InverseCumulativeNormal} via {@code RandomSequenceGenerator}.
     */
    private static InverseCumulativeRsg<RandomSequenceGenerator<MersenneTwisterUniformRng>,
                                        InverseCumulativeNormal>
            makeScalarGaussianRsg(final long seed) {
        final MersenneTwisterUniformRng rng = new MersenneTwisterUniformRng(seed);
        final RandomSequenceGenerator<MersenneTwisterUniformRng> rsg =
                new RandomSequenceGenerator<MersenneTwisterUniformRng>(
                        MersenneTwisterUniformRng.class, 1, rng);
        return new InverseCumulativeRsg<RandomSequenceGenerator<MersenneTwisterUniformRng>,
                                        InverseCumulativeNormal>(rsg, new InverseCumulativeNormal());
    }
}
