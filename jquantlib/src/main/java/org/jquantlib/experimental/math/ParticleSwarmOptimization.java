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
 * stability and convergence in a multidimensional complex space. IEEE
 * Transactions on Evolutionary Computation, 6(2): 58-73.
 *
 * <p>This Java port keeps the canonical PSO-Co algorithm with constriction
 * factor {@code chi}; the Inertia and Topology hierarchies are simplified to
 * inner classes ({@link TrivialInertia}, {@link DecreasingInertia},
 * {@link GlobalTopology}) that are sufficient for the test suite. The
 * QuantLib {@code Constraint::upperBound/lowerBound} API does not yet exist in
 * JQuantLib, so this implementation requires the bounds to be provided
 * explicitly.
 */
public class ParticleSwarmOptimization extends OptimizationMethod {

    //
    // Inertia hierarchy (subset of the C++ classes)
    //

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
            for (int i = 0; i < M_; ++i) {
                final Array v = pso_.V_[i];
                for (int j = 0; j < v.size(); ++j) {
                    v.set(j, v.get(j) * c0_);
                }
            }
        }
    }

    /**
     * Decreasing inertia: starts from c0 and linearly decreases to
     * threshold * c0 over the maximum number of iterations.
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
            final double c0 = c0_ * (threshold_ + (1.0 - threshold_)
                    * ((double) (maxIterations_ - iteration_)) / maxIterations_);
            iteration_++;
            for (int i = 0; i < M_; ++i) {
                final Array v = pso_.V_[i];
                for (int j = 0; j < v.size(); ++j) {
                    v.set(j, v.get(j) * c0);
                }
            }
        }
    }

    //
    // Topology hierarchy (subset of the C++ classes)
    //

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
            for (int i = 1; i < M_; ++i) {
                if (bestF < pso_.pBF_[i]) {
                    bestF = pso_.pBF_[i];
                    bestP = i;
                }
            }
            final Array x = pso_.pBX_[bestP];
            for (int i = 0; i < M_; ++i) {
                if (i != bestP) {
                    pso_.gBX_[i] = x.clone();
                    pso_.gBF_[i] = bestF;
                }
            }
        }
    }

    //
    // PSO state
    //

    Array[] X_;
    Array[] V_;
    Array[] pBX_;
    Array[] gBX_;
    double[] pBF_;
    double[] gBF_;
    Array lX_;
    Array uX_;
    final int M_;
    int N_;
    final double c0_;
    final double c1_;
    final double c2_;
    final MersenneTwisterUniformRng rng_;
    final Topology topology_;
    final Inertia inertia_;
    final double[] lowerBoundsExternal_;
    final double[] upperBoundsExternal_;

    /**
     * Construct a PSO-Co optimiser. The constriction factor is automatically
     * derived from {@code c1 + c2} so {@code phi*phi - 4*phi} must be
     * non-zero.
     */
    public ParticleSwarmOptimization(final int M,
                                     final Topology topology,
                                     final Inertia inertia,
                                     final double c1,
                                     final double c2,
                                     final long seed,
                                     final double[] lowerBounds,
                                     final double[] upperBounds) {
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
     * Construct a PSO-In optimiser with explicit inertia coefficient
     * {@code omega}. The coefficients {@code c1} and {@code c2} are NOT
     * scaled by the constriction factor.
     */
    public ParticleSwarmOptimization(final int M,
                                     final Topology topology,
                                     final Inertia inertia,
                                     final double omega,
                                     final double c1,
                                     final double c2,
                                     final long seed,
                                     final double[] lowerBounds,
                                     final double[] upperBounds) {
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
        QL.require(lowerBoundsExternal_.length == N_,
                "lowerBounds length must equal problem dimension");
        QL.require(upperBoundsExternal_.length == N_,
                "upperBounds length must equal problem dimension");

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
        for (int j = 0; j < N_; ++j) {
            lX_.set(j, lowerBoundsExternal_[j]);
            uX_.set(j, upperBoundsExternal_[j]);
        }

        for (int i = 0; i < M_; ++i) {
            X_[i] = new Array(N_);
            V_[i] = new Array(N_);
            gBX_[i] = new Array(N_);
            for (int j = 0; j < N_; ++j) {
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
        for (int i = 0; i < M_; ++i) {
            if (pBF_[i] < bestValue) {
                bestValue = pBF_[i];
                bestPosition = i;
            }
        }

        while (true) {
            iteration++;
            iterationStat++;
            if (iteration > maxIteration || iterationStat > maxIStationary) break;

            topology_.findSocialBest();
            inertia_.setValues();

            for (int i = 0; i < M_; ++i) {
                final Array x = X_[i];
                final Array pB = pBX_[i];
                final Array gB = gBX_[i];
                final Array v = V_[i];

                for (int j = 0; j < N_; ++j) {
                    final double newV = v.get(j)
                            + c1_ * rng_.next().value() * (pB.get(j) - x.get(j))
                            + c2_ * rng_.next().value() * (gB.get(j) - x.get(j));
                    v.set(j, newV);
                    double newX = x.get(j) + newV;
                    if (newX < lX_.get(j)) {
                        newX = lX_.get(j);
                        v.set(j, 0.0);
                    } else if (newX > uX_.get(j)) {
                        newX = uX_.get(j);
                        v.set(j, 0.0);
                    }
                    x.set(j, newX);
                }
                final double f = P.value(x);
                if (f < pBF_[i]) {
                    pBF_[i] = f;
                    for (int j = 0; j < N_; ++j) pB.set(j, x.get(j));
                    if (f < bestValue) {
                        bestValue = f;
                        bestPosition = i;
                        iterationStat = 0;
                    }
                }
            }
        }
        if (iteration > maxIteration) {
            ecType = EndCriteria.Type.MaxIterations;
        } else {
            ecType = EndCriteria.Type.StationaryPoint;
        }
        P.setCurrentValue(pBX_[bestPosition]);
        P.setFunctionValue(bestValue);
        return ecType;
    }
}
