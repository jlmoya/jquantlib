/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 * White-box, same-package cross-validation tests for the six PSO / Firefly
 * optimizer variants ported from QuantLib v1.42.1
 * (ql/experimental/math/particleswarmoptimization.{hpp,cpp} and
 *  ql/experimental/math/fireflyalgorithm.hpp).
 *
 * These deterministic algorithms are validated by transcribing the exact C++
 * formula / control flow and asserting the closed-form result, with a
 * `// C++ <file>:<line>` citation for each expected value. RNG-driven pieces
 * are validated against an INDEPENDENT reference draw from the same RNG and/or
 * by pinning the deterministic part of the formula (the inertia/step scaling
 * and the per-iteration decay), never by bit-matching C++'s std::mt19937
 * pipeline (which JQuantLib cannot reproduce).
 *
 * The class lives in package org.jquantlib.experimental.math so it can populate
 * the package-private PSO / FireflyAlgorithm state arrays directly without
 * adding any test-only API to the production classes.
 */
package org.jquantlib.experimental.math;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jquantlib.experimental.math.FireflyAlgorithm.DecreasingGaussianWalk;
import org.jquantlib.experimental.math.ParticleSwarmOptimization.AdaptiveInertia;
import org.jquantlib.experimental.math.ParticleSwarmOptimization.ClubsTopology;
import org.jquantlib.experimental.math.ParticleSwarmOptimization.KNeighbors;
import org.jquantlib.experimental.math.ParticleSwarmOptimization.LevyFlightInertia;
import org.jquantlib.experimental.math.ParticleSwarmOptimization.SimpleRandomInertia;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.EndCriteria;
import org.jquantlib.math.randomnumbers.MersenneTwisterUniformRng;
import org.junit.Test;

public class PsoFireflyVariantsCrossValTest {

    private static final double TIGHT = 1.0e-12;

    /** Build a bare PSO whose state arrays we overwrite for white-box testing of inertia/topology. */
    private static ParticleSwarmOptimization barePso(final int M, final int N) {
        final double[] lb = new double[N];
        final double[] ub = new double[N];
        for (int j = 0; j < N; ++j) {
            lb[j] = -1.0;
            ub[j] = 1.0;
        }
        // PSO-In ctor (omega) avoids the phi constriction validation; topology/inertia unused here.
        final ParticleSwarmOptimization pso = new ParticleSwarmOptimization(
                M, new ParticleSwarmOptimization.GlobalTopology(),
                new ParticleSwarmOptimization.TrivialInertia(),
                0.7, 1.0, 1.0, 42L, lb, ub);
        pso.N_ = N;
        pso.X_ = new Array[M];
        pso.V_ = new Array[M];
        pso.pBX_ = new Array[M];
        pso.gBX_ = new Array[M];
        pso.pBF_ = new double[M];
        pso.gBF_ = new double[M];
        pso.lX_ = new Array(N);
        pso.uX_ = new Array(N);
        for (int j = 0; j < N; ++j) {
            pso.lX_.set(j, -1.0);
            pso.uX_.set(j, 1.0);
        }
        for (int i = 0; i < M; ++i) {
            pso.X_[i] = new Array(N);
            pso.V_[i] = new Array(N);
            pso.pBX_[i] = new Array(N);
            pso.gBX_[i] = new Array(N);
        }
        return pso;
    }

    // ------------------------------------------------------------------
    // AdaptiveInertia  (deterministic) -- C++ particleswarmoptimization.cpp
    // AdaptiveInertia::setValues, hpp:233
    // ------------------------------------------------------------------
    @Test
    public void testAdaptiveInertiaWeightSequence() {
        final int M = 1;
        final ParticleSwarmOptimization pso = barePso(M, 1);
        // minInertia=0.1, maxInertia=2.0, sh=2, sl=1
        final AdaptiveInertia inertia = new AdaptiveInertia(0.1, 2.0, 2, 1);
        inertia.setSize(M, 1, /*c0=*/1.0, dummyEndCriteria());
        inertia.init(pso);

        // Drive the swarm-best fitness history and assert the inertia coefficient after each setValues().
        // currBest = pBF_[0] (single particle).
        // C++ particleswarmoptimization.cpp AdaptiveInertia::setValues
        final double[] fitness = { 10.0, 10.0, 10.0, 10.0, 1.0, 0.5, 0.1 };
        //  call1: started=false  -> best=10, c0 unchanged                       = 1.0
        //  call2: no improve, counter 0->1; 1>2?no 1<1?no                       = 1.0
        //  call3: no improve, counter 1->2; 2>2?no 2<1?no                       = 1.0
        //  call4: no improve, counter 2->3; 3>2?yes -> c0=clamp(1.0*0.5)=0.5    = 0.5
        //  call5: improve(1<10), counter 3->2; 2>2?no 2<1?no                    = 0.5
        //  call6: improve(0.5<1), counter 2->1; 1>2?no 1<1?no                   = 0.5
        //  call7: improve(0.1<0.5), counter 1->0; 0<1?yes -> c0=clamp(0.5*2)=1.0= 1.0
        final double[] expected = { 1.0, 1.0, 1.0, 0.5, 0.5, 0.5, 1.0 };
        for (int k = 0; k < fitness.length; ++k) {
            pso.pBF_[0] = fitness[k];
            pso.V_[0].set(0, 1.0); // reset velocity so the test focuses on the coefficient
            inertia.setValues();
            assertEquals("inertia after call " + (k + 1), expected[k], inertia.currentInertia(), 0.0);
            // V_ was multiplied by the coefficient (V reset to 1.0 before each call).
            assertEquals("V after call " + (k + 1), expected[k], pso.V_[0].get(0), TIGHT);
        }
    }

    @Test
    public void testAdaptiveInertiaCounterUnderflowHalves() {
        // Locks the C++ unsigned-wrap semantics: when the swarm best improves while the counter is 0,
        // C++ does `adaptiveCounter--` (Size, unsigned) which wraps to SIZE_MAX, so `adaptiveCounter > sh_`
        // is TRUE and the inertia is HALVED. A naive Java int (-1) would take the opposite (< sl_ -> double)
        // branch, so this test fails if the unsigned-comparison fix is reverted.
        // C++ particleswarmoptimization.cpp AdaptiveInertia::setValues (adaptiveCounter declared Size).
        final int M = 1;
        final ParticleSwarmOptimization pso = barePso(M, 1);
        // sh=5, sl=2 (C++ defaults). counter starts at 0; an early improvement underflows it.
        final AdaptiveInertia inertia = new AdaptiveInertia(0.1, 2.0, 5, 2);
        inertia.setSize(M, 1, /*c0=*/1.0, dummyEndCriteria());
        inertia.init(pso);

        // call1: started=false -> best=10.0, counter stays 0, inertia unchanged.
        pso.pBF_[0] = 10.0;
        pso.V_[0].set(0, 1.0);
        inertia.setValues();
        assertEquals("inertia after call 1 (init)", 1.0, inertia.currentInertia(), 0.0);

        // call2: improvement (1.0 < 10.0) -> adaptiveCounter-- underflows 0 -> SIZE_MAX.
        //   C++: SIZE_MAX > sh_(5) is TRUE -> c0 = clamp(1.0*0.5) = 0.5  (HALVED, NOT doubled).
        pso.pBF_[0] = 1.0;
        pso.V_[0].set(0, 1.0);
        inertia.setValues();
        assertEquals("inertia after underflow must be HALVED (C++ unsigned wrap)",
                0.5, inertia.currentInertia(), 0.0);
        assertEquals("V after underflow", 0.5, pso.V_[0].get(0), TIGHT);
    }

    @Test
    public void testAdaptiveInertiaClampsToBounds() {
        // Force repeated halving and confirm it never drops below minInertia.
        final int M = 1;
        final ParticleSwarmOptimization pso = barePso(M, 1);
        final AdaptiveInertia inertia = new AdaptiveInertia(0.25, 2.0, 0, 99); // sh=0 so every miss halves
        inertia.setSize(M, 1, 1.0, dummyEndCriteria());
        inertia.init(pso);
        pso.pBF_[0] = 5.0;
        inertia.setValues(); // started -> best=5, c0=1.0
        for (int k = 0; k < 10; ++k) {
            pso.pBF_[0] = 5.0; // never improves -> counter climbs, c0 halves but clamps at 0.25
            inertia.setValues();
        }
        // C++ clamp: max(minInertia, min(maxInertia, c0*0.5)) -> floor at minInertia_=0.25
        assertEquals(0.25, inertia.currentInertia(), 0.0);
    }

    // ------------------------------------------------------------------
    // KNeighbors  (deterministic) -- C++ particleswarmoptimization.cpp
    // KNeighbors::findSocialBest, hpp:376
    // ------------------------------------------------------------------
    @Test
    public void testKNeighborsRingBest() {
        final int M = 5;
        final ParticleSwarmOptimization pso = barePso(M, 1);
        // pBF_ (lower is better); tag each pBX_ with its index so we can read back the winner.
        final double[] pbf = { 5.0, 1.0, 3.0, 0.0, 4.0 };
        for (int i = 0; i < M; ++i) {
            pso.pBF_[i] = pbf[i];
            pso.pBX_[i].set(0, i); // sentinel = index
        }
        final KNeighbors topo = new KNeighbors(1);
        topo.setSize(M);
        topo.init(pso);
        topo.findSocialBest();

        // Hand-traced from C++ particleswarmoptimization.cpp KNeighbors::findSocialBest, K=1, M=5:
        //   window [lower,upper) = [i-K, i+K) plus the wrap branches, self seeded as bestF.
        //   i=0: self{5} + wrap-below{3,4}={0,4}      -> min 0
        //   i=1: self{1} + main{0,1}={5,1}            -> min 1
        //   i=2: self{3} + main{0,1,2}={5,1,3}        -> min 1
        //   i=3: self{0} + main{1,2,3}={1,3,0}        -> min 0
        //   i=4: self{4} + main{2,3,4}={3,0,4}        -> min 0
        final double[] expectedGbf = { 0.0, 1.0, 1.0, 0.0, 0.0 };
        for (int i = 0; i < M; ++i) {
            assertEquals("gBF_[" + i + "]", expectedGbf[i], pso.gBF_[i], 0.0);
        }
    }

    // ------------------------------------------------------------------
    // ClubsTopology  (deterministic in the all-clubs config)
    // -- C++ particleswarmoptimization.cpp ClubsTopology::findSocialBest, hpp:401
    // ------------------------------------------------------------------
    @Test
    public void testClubsTopologyAllClubsGlobalBest() {
        final int M = 4;
        final ParticleSwarmOptimization pso = barePso(M, 1);
        final double[] pbf = { 3.0, 1.0, 4.0, 2.0 };
        for (int i = 0; i < M; ++i) {
            pso.pBF_[i] = pbf[i];
            pso.pBX_[i].set(0, i);
        }
        // defaultClubs == totalClubs => RNG-free assignment: every particle belongs to every club.
        // C++ particleswarmoptimization.cpp ClubsTopology::setSize (else branch).
        final ClubsTopology topo = new ClubsTopology(2, 2, 2, 1, /*resetIteration=*/1000, 7L);
        topo.setSize(M);
        topo.init(pso);
        topo.findSocialBest();

        // With every particle in every club, bestByClub == global argmin for all clubs, so each
        // particle's social best FITNESS is the global minimum pBF (= 1.0 at index 1), independent of
        // the RNG-driven club leave/join (the global-min particle leaves one of its two identical
        // clubs but remains best in the other). C++ ClubsTopology::findSocialBest sets gBF_ here.
        //
        // NOTE: gBX_ is deliberately NOT asserted. In C++ v1.42.1 the global-best loop sets
        //   bestNeighborX = j   // <-- the CLUB index, not the particle index
        //   (*gBX_)[i] = (*pBX_)[bestNeighborX];
        // i.e. pBX_ is indexed by a club index -- a faithful-port quirk -- and for the leaving
        // particle which club survives is RNG-dependent, so gBX_ is not deterministically pinnable.
        // The fitness gBF_ is the unambiguous deterministic observable. (C++ particleswarmoptimization.cpp)
        for (int i = 0; i < M; ++i) {
            assertEquals("gBF_[" + i + "]", 1.0, pso.gBF_[i], 0.0);
        }
    }

    // ------------------------------------------------------------------
    // SimpleRandomInertia  (RNG-driven; validate the closed-form scaling)
    // -- C++ particleswarmoptimization.hpp:188-193
    // ------------------------------------------------------------------
    @Test
    public void testSimpleRandomInertiaFormula() {
        final int M = 3;
        final long seed = 9876L;
        final double threshold = 0.3;
        final double c0 = 0.8;
        final ParticleSwarmOptimization pso = barePso(M, 1);
        for (int i = 0; i < M; ++i) {
            pso.V_[i].set(0, 1.0);
        }
        final SimpleRandomInertia inertia = new SimpleRandomInertia(threshold, seed);
        inertia.setSize(M, 1, c0, dummyEndCriteria());
        inertia.init(pso);
        inertia.setValues();

        // Reference RNG with the SAME seed reproduces the exact uniform draws; assert the production
        // velocity equals the closed form  V *= c0*(threshold + (1-threshold)*u).
        // C++ particleswarmoptimization.hpp:190  val = c0_*(threshold_ + (1-threshold_)*rng_.nextReal())
        final MersenneTwisterUniformRng ref = new MersenneTwisterUniformRng(seed);
        for (int i = 0; i < M; ++i) {
            final double u = ref.next().value();
            final double expected = c0 * (threshold + (1.0 - threshold) * u);
            assertEquals("V_[" + i + "]", expected, pso.V_[i].get(0), TIGHT);
            // Drawn value lies in (threshold, 1) scaled by c0.
            assertTrue(pso.V_[i].get(0) >= c0 * threshold && pso.V_[i].get(0) <= c0);
        }
    }

    // ------------------------------------------------------------------
    // LevyFlightInertia  (RNG-driven; validate BOTH regimes deterministically)
    // -- C++ particleswarmoptimization.hpp:275-294
    // ------------------------------------------------------------------
    @Test
    public void testLevyFlightInertiaSimpleRegime() {
        // counter <= threshold -> behaves like SimpleRandomInertia with threshold 0.5.
        final int M = 1;
        final long seed = 555L;
        final double c0 = 0.9;
        final ParticleSwarmOptimization pso = barePso(M, 1);
        pso.V_[0].set(0, 1.0);
        pso.pBF_[0] = 10.0;
        final LevyFlightInertia inertia = new LevyFlightInertia(1.5, /*threshold=*/3, seed);
        inertia.setSize(M, 1, c0, dummyEndCriteria());
        inertia.init(pso); // snapshots personalBestF_ = pBF_ = {10}

        // First call: pBF (10) is NOT < snapshot (10) -> counter becomes 1 (<=3) -> simple-random branch.
        // C++ hpp:286  (*V_)[i] *= c0_*(0.5 + 0.5*rng_.nextReal());
        final MersenneTwisterUniformRng ref = new MersenneTwisterUniformRng(seed);
        inertia.setValues();
        final double u0 = ref.next().value();
        assertEquals(c0 * (0.5 + 0.5 * u0), pso.V_[0].get(0), TIGHT);
    }

    @Test
    public void testLevyFlightInertiaLevyRegime() {
        // After > threshold non-improving iterations, the velocity is OVERWRITTEN by a Levy draw.
        final int M = 1;
        final int N = 1;
        final long seed = 1234L;
        final double alpha = 1.7;
        final int threshold = 2;
        final double c0 = 0.9;
        final ParticleSwarmOptimization pso = barePso(M, N);
        pso.V_[0].set(0, 7.0);
        pso.pBF_[0] = 5.0; // constant -> never improves -> counter climbs each call
        final LevyFlightInertia inertia = new LevyFlightInertia(alpha, threshold, seed);
        inertia.setSize(M, N, c0, dummyEndCriteria());
        inertia.init(pso);

        // Mirror the exact RNG usage: simple-random rng_ for counter<=threshold calls, then the
        // flight RNG (java.util.Random(seed)) once the Levy branch triggers.
        final MersenneTwisterUniformRng simpleRef = new MersenneTwisterUniformRng(seed);
        final java.util.Random flightRef = new java.util.Random(seed);
        final LevyFlightDistribution flightRef2 = new LevyFlightDistribution(1.0, alpha);

        // calls 1,2,3 -> counter 1,2,3; 3<=2 is false on the 3rd, so:
        //   call1 counter=1 (<=2) simple ; call2 counter=2 (<=2) simple ; call3 counter=3 (>2) Levy.
        inertia.setValues(); // counter 1 -> simple
        simpleRef.next();
        inertia.setValues(); // counter 2 -> simple
        simpleRef.next();
        inertia.setValues(); // counter 3 -> Levy (sign-symmetric step)
        // C++ hpp:291 fills V via IsotropicRandomWalk::nextReal; for N==1
        // (isotropicrandomwalk.hpp:72-77) that is `radius = flight(); sign = (u<0.5)? -radius : radius`.
        // The port mirrors the sibling LevyFlightWalk idiom: draw the radius, then draw a sign coin from
        // the SAME flight RNG. Pin the full sign-bearing formula (reverting the +/- fix fails this).
        final double u = flightRef.nextDouble();
        final double radius = flightRef2.draw(u); // Levy radius >= xm = 1 (always positive)
        final double sign = (flightRef.nextDouble() < 0.5) ? -1.0 : 1.0;
        final double expected = sign * radius;
        assertEquals(expected, pso.V_[0].get(0), TIGHT);
        // Sanity: the radius itself is >= 1, so a magnitude-only port would have the WRONG sign whenever
        // the coin is negative -- assert the signed value matches, not just |value|.
        assertEquals(Math.abs(radius), Math.abs(pso.V_[0].get(0)), TIGHT);
    }

    // ------------------------------------------------------------------
    // DecreasingGaussianWalk  (RNG-driven step; validate the deterministic decay)
    // -- C++ fireflyalgorithm.hpp:255-282
    // ------------------------------------------------------------------
    @Test
    public void testDecreasingGaussianWalkDeltaDecay() {
        final int Mfa = 3;
        final int N = 1;
        final double delta0 = 0.5;
        // FireflyAlgorithm with Mde=0 so Mfa_ == M == 3.
        final double[] lb = { -1.0 };
        final double[] ub = { 1.0 };
        final FireflyAlgorithm fa = new FireflyAlgorithm(
                Mfa, new FireflyAlgorithm.ExponentialIntensity(1.0, 0.1, 0.1),
                new FireflyAlgorithm.GaussianWalk(0.5, 0.9, 1L),
                0, 1.0, 0.5, 3L, lb, ub);
        // Populate the package-private FA state the walk needs (normally set by private startState()).
        fa.N_ = N;
        fa.lX_ = new Array(N);
        fa.uX_ = new Array(N);
        fa.lX_.set(0, -1.0);
        fa.uX_.set(0, 1.0);
        fa.x_ = new Array[Mfa];
        fa.xRW_ = new Array[Mfa];
        fa.values_ = new FireflyAlgorithm.ValueIndex[Mfa];
        for (int i = 0; i < Mfa; ++i) {
            fa.x_[i] = new Array(N);
            fa.xRW_[i] = new Array(N);
            fa.values_[i] = new FireflyAlgorithm.ValueIndex(0.0, i);
        }

        final DecreasingGaussianWalk walk = new DecreasingGaussianWalk(0.5, delta0, 1L);
        walk.init(fa); // delta_ reset to delta0, iteration_=0, Mfa_=3

        // walk() invokes walkImpl Mfa_ times. delta squares every (Mfa_+1)-th walkImpl call.
        // C++ fireflyalgorithm.hpp:263-271  (iteration_>Mfa_ -> reset & delta_*=delta_)
        assertEquals("delta at init", delta0, walk.currentDelta(), 0.0);
        walk.walk(); // 3 calls (n=1..3): no square
        assertEquals("after walk#1", 0.5, walk.currentDelta(), 0.0);
        walk.walk(); // n=4..6: squared once at n=4 -> 0.5^2
        assertEquals("after walk#2", 0.25, walk.currentDelta(), TIGHT);
        walk.walk(); // n=7..9: squared at n=8 -> 0.5^4
        assertEquals("after walk#3", 0.0625, walk.currentDelta(), TIGHT);
        walk.walk(); // n=10..12: squared at n=12 -> 0.5^8
        assertEquals("after walk#4", 0.00390625, walk.currentDelta(), TIGHT);

        // Re-init resets the decay (C++ DecreasingGaussianWalk::init).
        walk.init(fa);
        assertEquals("delta after re-init", delta0, walk.currentDelta(), 0.0);
    }

    private static EndCriteria dummyEndCriteria() {
        return new EndCriteria(100, 25, 1.0e-8, 1.0e-8, 1.0e-8);
    }
}
