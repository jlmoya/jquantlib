/*
 Copyright (C) 2015 Andres Hernandez
 Copyright (C) 2026 JQuantLib migration contributors.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

package org.jquantlib.experimental.math;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.EndCriteria;
import org.jquantlib.math.optimization.OptimizationMethod;
import org.jquantlib.math.optimization.Problem;
import org.jquantlib.math.randomnumbers.MersenneTwisterUniformRng;

/**
 * Particle Swarm Optimization (PSO).
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/experimental/math/particleswarmoptimization.{hpp,cpp}}.
 *
 * <p>Reference: Clerc, M., Kennedy, J. (2002) The particle swarm-explosion,
 * stability and convergence in a multidimensional complex space. IEEE Transactions on Evolutionary Computation, 6(2):
 * 58-73.
 *
 * <p>This Java port keeps the canonical PSO-Co algorithm with constriction
 * factor {@code chi}; the Inertia and Topology hierarchies are simplified to inner classes ({@link TrivialInertia},
 * {@link DecreasingInertia}, {@link GlobalTopology}) that are sufficient for the test suite. The QuantLib
 * {@code Constraint::upperBound/lowerBound} API does not yet exist in JQuantLib, so this implementation requires the
 * bounds to be provided explicitly.
 */
public class ParticleSwarmOptimization extends OptimizationMethod {

    //
    // Inertia hierarchy (subset of the C++ classes)
    //

    final int M_;
    final double c0_;
    final double c1_;

    //
    // Topology hierarchy (subset of the C++ classes)
    //
    final double c2_;
    final MersenneTwisterUniformRng rng_;

    //
    // PSO state
    //
    final Topology topology_;
    final Inertia inertia_;
    final double[] lowerBoundsExternal_;
    final double[] upperBoundsExternal_;
    Array[] X_;
    Array[] V_;
    Array[] pBX_;
    Array[] gBX_;
    double[] pBF_;
    double[] gBF_;
    Array lX_;
    Array uX_;
    int N_;
    /**
     * Construct a PSO-Co optimiser. The constriction factor is automatically derived from {@code c1 + c2} so
     * {@code phi*phi - 4*phi} must be non-zero.
     */
    public ParticleSwarmOptimization(final int M, final Topology topology, final Inertia inertia, final double c1,
            final double c2, final long seed, final double[] lowerBounds, final double[] upperBounds) {
        this.M_ = M;
        this.rng_ = new MersenneTwisterUniformRng(seed);
        this.topology_ = topology;
        this.inertia_ = inertia;
        this.lowerBoundsExternal_ = lowerBounds.clone();
        this.upperBoundsExternal_ = upperBounds.clone();
        final double phi = c1 + c2;
        QL.ensure(phi * phi - 4.0 * phi != 0.0, "Invalid phi");
        this.c0_ = 2.0 / Math.abs(2.0 - phi - Math.sqrt(phi * phi - 4.0 * phi));
        this.c1_ = c0_ * c1;
        this.c2_ = c0_ * c2;
    }
    /**
     * Construct a PSO-In optimiser with explicit inertia coefficient {@code omega}. The coefficients {@code c1} and
     * {@code c2} are NOT scaled by the constriction factor.
     */
    public ParticleSwarmOptimization(final int M, final Topology topology, final Inertia inertia, final double omega,
            final double c1, final double c2, final long seed, final double[] lowerBounds, final double[] upperBounds) {
        this.M_ = M;
        this.c0_ = omega;
        this.c1_ = c1;
        this.c2_ = c2;
        this.rng_ = new MersenneTwisterUniformRng(seed);
        this.topology_ = topology;
        this.inertia_ = inertia;
        this.lowerBoundsExternal_ = lowerBounds.clone();
        this.upperBoundsExternal_ = upperBounds.clone();
    }

    private void startState(final Problem P, final EndCriteria endCriteria) {
        QL.require(topology_ != null, "Invalid topology");
        QL.require(inertia_ != null, "Invalid inertia");
        N_ = P.currentValue().size();
        QL.require(lowerBoundsExternal_.length == N_, "lowerBounds length must equal problem dimension");
        QL.require(upperBoundsExternal_.length == N_, "upperBounds length must equal problem dimension");

        topology_.setSize(M_);
        inertia_.setSize(M_, N_, c0_, endCriteria);
        X_ = new Array[M_];
        V_ = new Array[M_];
        pBX_ = new Array[M_];
        pBF_ = new double[M_];
        gBX_ = new Array[M_];
        gBF_ = new double[M_];
        uX_ = new Array(N_);
        lX_ = new Array(N_);
        for ( int j = 0; j < N_; ++j ) {
            lX_.set(j, lowerBoundsExternal_[j]);
            uX_.set(j, upperBoundsExternal_[j]);
        }

        for ( int i = 0; i < M_; ++i ) {
            X_[i] = new Array(N_);
            V_[i] = new Array(N_);
            gBX_[i] = new Array(N_);
            for ( int j = 0; j < N_; ++j ) {
                final double bound = uX_.get(j) - lX_.get(j);
                X_[i].set(j, lX_.get(j) + bound * rng_.next().value());
                V_[i].set(j, bound * (2.0 * rng_.next().value() - 1.0));
            }
            pBX_[i] = X_[i].clone();
            pBF_[i] = P.value(X_[i]);
        }

        topology_.init(this);
        inertia_.init(this);
    }

    @Override
    public EndCriteria.Type minimize(final Problem P, final EndCriteria endCriteria) {
        EndCriteria.Type ecType;
        P.reset();
        int iteration = 0;
        int iterationStat = 0;
        final int maxIteration = endCriteria.getMaxIterations();
        final int maxIStationary = endCriteria.getMaxStationaryStateIterations();
        double bestValue = Double.MAX_VALUE;
        int bestPosition = 0;

        startState(P, endCriteria);
        for ( int i = 0; i < M_; ++i ) {
            if ( pBF_[i] < bestValue ) {
                bestValue = pBF_[i];
                bestPosition = i;
            }
        }

        while ( true ) {
            iteration++;
            iterationStat++;
            if ( iteration > maxIteration || iterationStat > maxIStationary )
                break;

            topology_.findSocialBest();
            inertia_.setValues();

            for ( int i = 0; i < M_; ++i ) {
                final Array x = X_[i];
                final Array pB = pBX_[i];
                final Array gB = gBX_[i];
                final Array v = V_[i];

                for ( int j = 0; j < N_; ++j ) {
                    final double newV =
                            v.get(j) + c1_ * rng_.next().value() * (pB.get(j) - x.get(j)) + c2_ * rng_.next().value()
                                    * (gB.get(j) - x.get(j));
                    v.set(j, newV);
                    double newX = x.get(j) + newV;
                    if ( newX < lX_.get(j) ) {
                        newX = lX_.get(j);
                        v.set(j, 0.0);
                    } else if ( newX > uX_.get(j) ) {
                        newX = uX_.get(j);
                        v.set(j, 0.0);
                    }
                    x.set(j, newX);
                }
                final double f = P.value(x);
                if ( f < pBF_[i] ) {
                    pBF_[i] = f;
                    for ( int j = 0; j < N_; ++j )
                        pB.set(j, x.get(j));
                    if ( f < bestValue ) {
                        bestValue = f;
                        bestPosition = i;
                        iterationStat = 0;
                    }
                }
            }
        }
        if ( iteration > maxIteration ) {
            ecType = EndCriteria.Type.MaxIterations;
        } else {
            ecType = EndCriteria.Type.StationaryPoint;
        }
        P.setCurrentValue(pBX_[bestPosition]);
        P.setFunctionValue(bestValue);
        return ecType;
    }

    /** Base inertia. */
    public abstract static class Inertia {
        protected ParticleSwarmOptimization pso_;

        public abstract void setSize(int M, int N, double c0, EndCriteria endCriteria);

        public abstract void setValues();

        protected void init(final ParticleSwarmOptimization pso) {
            this.pso_ = pso;
        }
    }

    /** Trivial inertia: V *= c0. */
    public static class TrivialInertia extends Inertia {
        private double c0_;
        private int M_;

        @Override
        public void setSize(final int M, final int N, final double c0, final EndCriteria endCriteria) {
            this.c0_ = c0;
            this.M_ = M;
        }

        @Override
        public void setValues() {
            for ( int i = 0; i < M_; ++i ) {
                final Array v = pso_.V_[i];
                for ( int j = 0; j < v.size(); ++j ) {
                    v.set(j, v.get(j) * c0_);
                }
            }
        }
    }

    /**
     * Decreasing inertia: starts from c0 and linearly decreases to threshold * c0 over the maximum number of
     * iterations.
     */
    public static class DecreasingInertia extends Inertia {
        private final double threshold_;
        private double c0_;
        private int M_, N_;
        private int maxIterations_;
        private int iteration_;

        public DecreasingInertia(final double threshold) {
            QL.require(threshold >= 0.0 && threshold < 1.0, "Threshold must be in [0,1)");
            this.threshold_ = threshold;
        }

        @Override
        public void setSize(final int M, final int N, final double c0, final EndCriteria endCriteria) {
            this.c0_ = c0;
            this.M_ = M;
            this.N_ = N;
            this.maxIterations_ = endCriteria.getMaxIterations();
            this.iteration_ = 0;
        }

        @Override
        public void setValues() {
            final double c0 =
                    c0_ * (threshold_ + (1.0 - threshold_) * ((double) (maxIterations_ - iteration_)) / maxIterations_);
            iteration_++;
            for ( int i = 0; i < M_; ++i ) {
                final Array v = pso_.V_[i];
                for ( int j = 0; j < v.size(); ++j ) {
                    v.set(j, v.get(j) * c0);
                }
            }
        }
    }

    /**
     * Simple random inertia: the inertia value is multiplied by a uniform random number in
     * {@code (threshold, 1)} drawn fresh for every particle each iteration.
     *
     * <p>C++ particleswarmoptimization.hpp:178.
     */
    public static class SimpleRandomInertia extends Inertia {
        private final double threshold_;
        private final MersenneTwisterUniformRng rng_;
        private double c0_;
        private int M_;

        public SimpleRandomInertia(final double threshold, final long seed) {
            QL.require(threshold >= 0.0 && threshold < 1.0, "Threshold must be a Real in [0, 1)");
            this.threshold_ = threshold;
            this.rng_ = new MersenneTwisterUniformRng(seed);
        }

        @Override
        public void setSize(final int M, final int N, final double c0, final EndCriteria endCriteria) {
            this.M_ = M;
            this.c0_ = c0;
        }

        @Override
        public void setValues() {
            // C++ particleswarmoptimization.hpp:188-193
            for ( int i = 0; i < M_; ++i ) {
                final double val = c0_ * (threshold_ + (1.0 - threshold_) * rng_.next().value());
                pso_.V_[i].mulAssign(val);
            }
        }
    }

    /**
     * Adaptive inertia (Alen Lukic, "Approximating Kinetic Parameters Using Particle Swarm Optimization").
     *
     * <p>The first iteration leaves the inertia unchanged and records the swarm best. Subsequently, when the
     * swarm best improves the {@code adaptiveCounter} is decremented, otherwise incremented. When the counter
     * exceeds {@code sh} the inertia is halved (clamped to {@code [minInertia, maxInertia]}); when below
     * {@code sl} it is doubled (clamped likewise).
     *
     * <p>The counter and thresholds are unsigned {@code Size} in C++, so a decrement at 0 wraps to the maximum
     * value and selects the "halve" branch. This port preserves that behaviour by comparing the counter to the
     * thresholds with unsigned semantics (see {@code setValues}), matching C++ in every case.
     *
     * <p>C++ particleswarmoptimization.hpp:233 / particleswarmoptimization.cpp AdaptiveInertia::setValues.
     */
    public static class AdaptiveInertia extends Inertia {
        private final double minInertia_;
        private final double maxInertia_;
        private final int sh_;
        private final int sl_;
        private double c0_;
        private double best_;
        private int M_;
        private int adaptiveCounter_;
        private boolean started_;

        public AdaptiveInertia(final double minInertia, final double maxInertia, final int sh, final int sl) {
            this.minInertia_ = minInertia;
            this.maxInertia_ = maxInertia;
            this.sh_ = sh;
            this.sl_ = sl;
        }

        public AdaptiveInertia(final double minInertia, final double maxInertia) {
            this(minInertia, maxInertia, 5, 2);
        }

        @Override
        public void setSize(final int M, final int N, final double c0, final EndCriteria endCriteria) {
            this.M_ = M;
            this.c0_ = c0;
            this.adaptiveCounter_ = 0;
            this.best_ = Double.MAX_VALUE;
            this.started_ = false;
        }

        @Override
        public void setValues() {
            // C++ particleswarmoptimization.cpp AdaptiveInertia::setValues
            double currBest = pso_.pBF_[0];
            for ( int i = 1; i < M_; ++i ) {
                if ( currBest > pso_.pBF_[i] )
                    currBest = pso_.pBF_[i];
            }
            if ( started_ ) { // First iteration leaves inertia unchanged
                if ( currBest < best_ ) {
                    best_ = currBest;
                    adaptiveCounter_--;
                } else {
                    adaptiveCounter_++;
                }
                // C++ declares adaptiveCounter, sh_ and sl_ as unsigned Size. When the swarm best
                // improves while the counter is 0, C++ does `adaptiveCounter--`, which UNDERFLOWS to
                // SIZE_MAX (defined unsigned-wrap behaviour); `adaptiveCounter > sh_` is then TRUE, so the
                // inertia is HALVED. A naive Java int would give -1, taking the opposite (< sl_ -> double)
                // branch. We mirror C++ exactly by comparing the counter to the thresholds with UNSIGNED
                // semantics (Integer.compareUnsigned), so -1 reads as the largest unsigned value and every
                // case — including the wrap — selects the same branch C++ takes.
                if ( Integer.compareUnsigned(adaptiveCounter_, sh_) > 0 ) {
                    c0_ = Math.max(minInertia_, Math.min(maxInertia_, c0_ * 0.5));
                } else if ( Integer.compareUnsigned(adaptiveCounter_, sl_) < 0 ) {
                    c0_ = Math.max(minInertia_, Math.min(maxInertia_, c0_ * 2.0));
                }
            } else {
                best_ = currBest;
                started_ = true;
            }
            for ( int i = 0; i < M_; ++i ) {
                pso_.V_[i].mulAssign(c0_);
            }
        }

        /** Exposes the current inertia coefficient for cross-validation. */
        public double currentInertia() {
            return c0_;
        }
    }

    /**
     * Levy-flight inertia. While a particle keeps receiving frequent updates to its personal best it behaves
     * like a {@link SimpleRandomInertia} with threshold {@code 0.5}; after {@code threshold} iterations without
     * improvement the velocity is overwritten by a sign-symmetric Levy-flight step {@code ~ +/- u^{-1/alpha}}.
     *
     * <p>C++ (hpp:291) fills the Levy velocity through {@code IsotropicRandomWalk::nextReal}, which draws the
     * Levy radius and then places it isotropically on an N-sphere. For {@code N == 1}
     * (isotropicrandomwalk.hpp:72-77) that reduces to applying a random {@code +/-} sign to the radius. This
     * port reproduces the 1-D sign-symmetric step exactly; for {@code N > 1} it applies an independent
     * per-dimension {@code +/-} sign rather than the full isotropic-sphere direction -- a deliberate, noted
     * simplification matching the sibling {@code FireflyAlgorithm.LevyFlightWalk}.
     *
     * <p>C++ particleswarmoptimization.hpp:262.
     */
    public static class LevyFlightInertia extends Inertia {
        private final MersenneTwisterUniformRng rng_;
        private final LevyFlightDistribution flight_;
        private final java.util.Random flightRng_;
        private final int threshold_;
        private double[] personalBestF_;
        private int[] adaptiveCounter_;
        private double c0_;
        private int M_, N_;

        public LevyFlightInertia(final double alpha, final int threshold, final long seed) {
            this.rng_ = new MersenneTwisterUniformRng(seed);
            this.flight_ = new LevyFlightDistribution(1.0, alpha);
            this.flightRng_ = new java.util.Random(seed);
            this.threshold_ = threshold;
        }

        @Override
        public void setSize(final int M, final int N, final double c0, final EndCriteria endCriteria) {
            this.M_ = M;
            this.N_ = N;
            this.c0_ = c0;
            this.adaptiveCounter_ = new int[M_];
        }

        @Override
        protected void init(final ParticleSwarmOptimization pso) {
            super.init(pso);
            // C++ copies the personal best fitness snapshot at init time
            this.personalBestF_ = pso.pBF_.clone();
        }

        @Override
        public void setValues() {
            // C++ particleswarmoptimization.hpp:275-294
            for ( int i = 0; i < M_; ++i ) {
                if ( pso_.pBF_[i] < personalBestF_[i] ) {
                    personalBestF_[i] = pso_.pBF_[i];
                    adaptiveCounter_[i] = 0;
                } else {
                    adaptiveCounter_[i]++;
                }
                if ( adaptiveCounter_[i] <= threshold_ ) {
                    // Simple Random Inertia with threshold 0.5
                    pso_.V_[i].mulAssign(c0_ * (0.5 + 0.5 * rng_.next().value()));
                } else {
                    // Trigger a Levy-flight pattern for the velocity.
                    //
                    // C++ hpp:291 fills the velocity via IsotropicRandomWalk::nextReal. For the 1-D case
                    // (isotropicrandomwalk.hpp:72-77) that draws the Levy radius and then applies a random
                    // +/- SIGN (rng_.nextReal() < 0.5 ? -radius : radius), so the step is sign-symmetric;
                    // the bare flight_.draw(u) is always positive (xm * u^(-1/alpha) >= 1). We restore the
                    // +/- sign here using the same draw-then-coin idiom as the sibling
                    // FireflyAlgorithm.LevyFlightWalk (fireflyalgorithm.hpp / LevyFlightWalk.walkImpl).
                    //
                    // DELIBERATE SIMPLIFICATION: C++'s IsotropicRandomWalk places the radius isotropically on
                    // an N-sphere for N > 1; this port applies an independent per-dimension +/- sign instead,
                    // which is exact for N == 1 (the PSO/Levy use here) and a noted simplification for N > 1.
                    final Array v = pso_.V_[i];
                    for ( int j = 0; j < N_; ++j ) {
                        final double radius = flight_.draw(flightRng_.nextDouble());
                        final double sign = (flightRng_.nextDouble() < 0.5) ? -1.0 : 1.0;
                        v.set(j, sign * radius);
                    }
                }
            }
        }
    }

    /** Base topology. */
    public abstract static class Topology {
        protected ParticleSwarmOptimization pso_;

        public abstract void setSize(int M);

        public abstract void findSocialBest();

        protected void init(final ParticleSwarmOptimization pso) {
            this.pso_ = pso;
        }
    }

    /** Global topology: every particle sees the swarm-wide best. */
    public static class GlobalTopology extends Topology {
        private int M_;

        @Override
        public void setSize(final int M) {
            this.M_ = M;
        }

        @Override
        public void findSocialBest() {
            // C++ used `<` (looking for largest), reproducing here.
            double bestF = pso_.pBF_[0];
            int bestP = 0;
            for ( int i = 1; i < M_; ++i ) {
                if ( bestF < pso_.pBF_[i] ) {
                    bestF = pso_.pBF_[i];
                    bestP = i;
                }
            }
            final Array x = pso_.pBX_[bestP];
            for ( int i = 0; i < M_; ++i ) {
                if ( i != bestP ) {
                    pso_.gBX_[i] = x.clone();
                    pso_.gBF_[i] = bestF;
                }
            }
        }
    }

    /**
     * K-neighbor topology. The global best as seen by particle {@code i} is the best of personal bests in the
     * ring window {@code [i-K, i+K]} (wrapping around the ends of the swarm).
     *
     * <p>C++ particleswarmoptimization.hpp:376 / particleswarmoptimization.cpp KNeighbors::findSocialBest.
     */
    public static class KNeighbors extends Topology {
        private final int K_;
        private int M_;

        public KNeighbors(final int K) {
            QL.require(K > 0, "Neighbors need to be larger than 0");
            this.K_ = K;
        }

        public KNeighbors() {
            this(1);
        }

        @Override
        public void setSize(final int M) {
            this.M_ = M;
            QL.ensure(K_ < M, "Number of neighbors need to be smaller than total particles in swarm");
        }

        @Override
        public void findSocialBest() {
            // C++ particleswarmoptimization.cpp KNeighbors::findSocialBest
            for ( int i = 0; i < M_; ++i ) {
                double bestF = pso_.pBF_[i];
                int bestX = 0;
                // Search K_ neighbors upwards
                final int upper = Math.min(i + K_, M_);
                // Search K_ neighbors downwards
                final int lower = Math.max(i, K_ + 1) - K_ - 1;
                for ( int j = lower; j < upper; ++j ) {
                    if ( pso_.pBF_[j] < bestF ) {
                        bestF = pso_.pBF_[j];
                        bestX = j;
                    }
                }
                if ( i + K_ >= M_ ) { // loop around if i+K >= M_
                    for ( int j = 0; j < i + K_ - M_; ++j ) {
                        if ( pso_.pBF_[j] < bestF ) {
                            bestF = pso_.pBF_[j];
                            bestX = j;
                        }
                    }
                } else if ( i < K_ ) { // loop around from above
                    for ( int j = M_ - (K_ - i) - 1; j < M_; ++j ) {
                        if ( pso_.pBF_[j] < bestF ) {
                            bestF = pso_.pBF_[j];
                            bestX = j;
                        }
                    }
                }
                pso_.gBX_[i] = pso_.pBX_[bestX].clone();
                pso_.gBF_[i] = bestF;
            }
        }
    }

    /**
     * Clubs topology (H.M. Emara, "Adaptive Clubs-based Particle Swarm Optimization").
     *
     * <p>Each particle is assigned to {@code defaultClubs} clubs out of {@code totalClubs}. The best a particle
     * sees is the best among the clubs it belongs to. Particles that are best in all their clubs leave a random
     * club (down to {@code minClubs}); particles that are worst in all their clubs join a random club (up to
     * {@code maxClubs}). Every {@code resetIteration} iterations memberships drift back towards
     * {@code defaultClubs}.
     *
     * <p>C++ particleswarmoptimization.hpp:401 / particleswarmoptimization.cpp ClubsTopology.
     *
     * <p>Note: the random club assignment uses a {@code std::uniform_int_distribution} over
     * {@code std::mt19937} in C++. JQuantLib cannot bit-match that pipeline, so a {@link java.util.Random}
     * draw over the same inclusive range {@code [1, totalClubs]} is used. When {@code defaultClubs == totalClubs}
     * the assignment is RNG-free (every particle belongs to every club), so behaviour is identical to C++.
     */
    public static class ClubsTopology extends Topology {
        private final int totalClubs_;
        private final int maxClubs_;
        private final int minClubs_;
        private final int defaultClubs_;
        private final int resetIteration_;
        private int iteration_;
        private int M_;
        private boolean[][] clubs4particles_;
        private boolean[][] particles4clubs_;
        private int[] bestByClub_;
        private int[] worstByClub_;
        private final java.util.Random generator_;

        public ClubsTopology(final int defaultClubs, final int totalClubs, final int maxClubs, final int minClubs,
                final int resetIteration, final long seed) {
            QL.require(totalClubs >= defaultClubs,
                    "Total number of clubs must be larger or equal than default clubs");
            QL.require(defaultClubs >= minClubs, "Number of default clubs must be larger or equal than minimum clubs");
            QL.require(maxClubs >= defaultClubs, "Number of maximum clubs must be larger or equal than default clubs");
            QL.require(totalClubs >= maxClubs, "Total number of clubs must be larger or equal than maximum clubs");
            this.totalClubs_ = totalClubs;
            this.maxClubs_ = maxClubs;
            this.minClubs_ = minClubs;
            this.defaultClubs_ = defaultClubs;
            this.resetIteration_ = resetIteration;
            this.iteration_ = 0;
            this.bestByClub_ = new int[totalClubs];
            this.worstByClub_ = new int[totalClubs];
            this.generator_ = new java.util.Random(seed);
        }

        // C++ uses std::uniform_int_distribution<Size>; inclusive on both ends.
        // When hi <= lo the range is empty/degenerate; C++ (UB for lo>hi) effectively yields `lo`
        // and the subsequent club-scan loop no-ops because the particle has no eligible clubs. We
        // reproduce that no-op behaviour explicitly rather than throwing on nextInt(<=0).
        private int draw(final int lo, final int hi) {
            if ( hi <= lo )
                return lo;
            return lo + generator_.nextInt(hi - lo + 1);
        }

        @Override
        public void setSize(final int M) {
            this.M_ = M;
            if ( defaultClubs_ < totalClubs_ ) {
                clubs4particles_ = new boolean[M_][totalClubs_];
                particles4clubs_ = new boolean[totalClubs_][M_];
                // Assign particles to clubs randomly.
                //
                // DELIBERATE DIVERGENCE (corrects a v1.42.1 bug): C++
                // particleswarmoptimization.cpp:272-275 draws
                //   Size index = distribution_(generator_);
                // where distribution_ is uniform_int_distribution<Size>(1, totalClubs_),
                // i.e. the INCLUSIVE range [1, totalClubs_], and then uses `index` DIRECTLY
                // as a 0-based subscript into clubSet / particles4clubs_, both sized
                // totalClubs_. That is a genuine off-by-one bug: index 0 is never assigned
                // (club 0 is unreachable) and index == totalClubs_ is an out-of-bounds access
                // (undefined behaviour in C++). Faithfully replicating it in Java would throw
                // ArrayIndexOutOfBoundsException, so we draw the same inclusive [1, totalClubs_]
                // and shift it down by one into the correct 0-based range [0, totalClubs_-1].
                for ( int i = 0; i < M_; ++i ) {
                    final boolean[] clubSet = clubs4particles_[i];
                    for ( int j = 0; j < defaultClubs_; ++j ) {
                        int index = draw(1, totalClubs_) - 1;
                        while ( clubSet[index] ) {
                            index = draw(1, totalClubs_) - 1;
                        }
                        clubSet[index] = true;
                        particles4clubs_[index][i] = true;
                    }
                }
            } else {
                // totalClubs_ == defaultClubs_: every particle in every club
                clubs4particles_ = new boolean[M_][totalClubs_];
                particles4clubs_ = new boolean[totalClubs_][M_];
                for ( int i = 0; i < M_; ++i ) {
                    java.util.Arrays.fill(clubs4particles_[i], true);
                }
                for ( int i = 0; i < totalClubs_; ++i ) {
                    java.util.Arrays.fill(particles4clubs_[i], true);
                }
            }
        }

        @Override
        public void findSocialBest() {
            // C++ particleswarmoptimization.cpp ClubsTopology::findSocialBest
            iteration_++;
            boolean reset = false;
            if ( iteration_ == resetIteration_ ) {
                iteration_ = 0;
                reset = true;
            }

            // Find best by current club
            for ( int i = 0; i < totalClubs_; ++i ) {
                double bestByClub = Double.MAX_VALUE;
                double worstByClub = -Double.MAX_VALUE;
                int bestP = 0;
                int worstP = 0;
                final boolean[] particlesSet = particles4clubs_[i];
                for ( int j = 0; j < M_; ++j ) {
                    if ( particlesSet[j] ) {
                        if ( bestByClub > pso_.pBF_[j] ) {
                            bestByClub = pso_.pBF_[j];
                            bestP = j;
                        } else if ( worstByClub < pso_.pBF_[j] ) {
                            worstByClub = pso_.pBF_[j];
                            worstP = j;
                        }
                    }
                }
                bestByClub_[i] = bestP;
                worstByClub_[i] = worstP;
            }

            // Update clubs && global best
            for ( int i = 0; i < M_; ++i ) {
                final boolean[] clubSet = clubs4particles_[i];
                boolean best = true;
                boolean worst = true;
                int currentClubs = 0;
                for ( int j = 0; j < totalClubs_; ++j ) {
                    if ( clubSet[j] ) {
                        if ( best && i != bestByClub_[j] )
                            best = false;
                        if ( worst && i != worstByClub_[j] )
                            worst = false;
                        currentClubs++;
                    }
                }
                // Update clubs
                if ( best ) {
                    leaveRandomClub(i, currentClubs);
                } else if ( worst ) {
                    joinRandomClub(i, currentClubs);
                } else if ( reset && currentClubs != defaultClubs_ ) {
                    if ( currentClubs < defaultClubs_ ) {
                        joinRandomClub(i, currentClubs);
                    } else {
                        leaveRandomClub(i, currentClubs);
                    }
                }

                // Update global best
                double bestNeighborF = Double.MAX_VALUE;
                int bestNeighborX = 0;
                for ( int j = 0; j < totalClubs_; ++j ) {
                    if ( clubSet[j] && bestNeighborF > pso_.pBF_[bestByClub_[j]] ) {
                        bestNeighborF = pso_.pBF_[bestByClub_[j]];
                        bestNeighborX = j;
                    }
                }
                pso_.gBX_[i] = pso_.pBX_[bestNeighborX].clone();
                pso_.gBF_[i] = bestNeighborF;
            }
        }

        // C++ leaveRandomClub: unconditionally leaves one club (no minClubs_ guard in v1.42.1 .cpp)
        private void leaveRandomClub(final int particle, final int currentClubs) {
            final int randIndex = draw(1, currentClubs);
            int index = 1;
            final boolean[] clubSet = clubs4particles_[particle];
            for ( int j = 0; j < totalClubs_; ++j ) {
                if ( clubSet[j] ) {
                    if ( index == randIndex ) {
                        clubSet[j] = false;
                        particles4clubs_[j][particle] = false;
                        break;
                    }
                    index++;
                }
            }
        }

        // C++ joinRandomClub: unconditionally joins one club (no maxClubs_ guard in v1.42.1 .cpp)
        private void joinRandomClub(final int particle, final int currentClubs) {
            final int randIndex = totalClubs_ == currentClubs ? 1 : draw(1, totalClubs_ - currentClubs);
            int index = 1;
            final boolean[] clubSet = clubs4particles_[particle];
            for ( int j = 0; j < totalClubs_; ++j ) {
                if ( !clubSet[j] ) {
                    if ( index == randIndex ) {
                        clubSet[j] = true;
                        particles4clubs_[j][particle] = true;
                        break;
                    }
                    index++;
                }
            }
        }
    }
}
