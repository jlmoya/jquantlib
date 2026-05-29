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

import java.util.Arrays;
import java.util.Comparator;

/**
 * Firefly Algorithm with Differential Evolution hybrid.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/experimental/math/fireflyalgorithm.{hpp,cpp}}.
 *
 * <p>Reference: Yang, Xin-She (2009) Firefly Algorithm, Levy Flights and
 * Global Optimization. Research and Development in Intelligent Systems XXVI, pp 209-218.
 *
 * <p>This Java port keeps the algorithm core; the {@link Intensity} and
 * {@link RandomWalk} hierarchies are simplified into inner classes ({@link ExponentialIntensity},
 * {@link InverseLawSquareIntensity}, {@link GaussianWalk}, {@link LevyFlightWalk}). Bounds must be supplied explicitly
 * to the constructor since {@code Constraint::upperBound} is not available in JQuantLib.
 */
public class FireflyAlgorithm extends OptimizationMethod {

    //
    // Intensity hierarchy
    //

    final double mutation_;
    final double crossover_;
    final int M_;

    //
    // Random walk hierarchy
    //
    final int Mde_;
    final int Mfa_;
    final Intensity intensity_;

    //
    // Helper to mirror the C++ std::pair<Real, Size>.
    //
    final RandomWalk randomWalk_;

    //
    // FireflyAlgorithm state
    //
    final java.util.Random generator_;
    final MersenneTwisterUniformRng rng_;
    final double[] lowerBoundsExternal_;
    final double[] upperBoundsExternal_;
    final int distMin_;
    final int distMax_;
    Array[] x_;
    Array[] xI_;
    Array[] xRW_;
    ValueIndex[] values_;
    Array lX_;
    Array uX_;
    int N_;
    public FireflyAlgorithm(final int M, final Intensity intensity, final RandomWalk randomWalk, final int Mde,
            final double mutation, final double crossover, final long seed, final double[] lowerBounds,
            final double[] upperBounds) {
        this.mutation_ = mutation;
        this.crossover_ = crossover;
        this.M_ = M;
        this.Mde_ = Mde;
        this.Mfa_ = M_ - Mde_;
        this.intensity_ = intensity;
        this.randomWalk_ = randomWalk;
        this.generator_ = new java.util.Random(seed);
        this.distMin_ = Mfa_;
        this.distMax_ = (Mde > 0 ? M_ - 1 : M_);
        this.rng_ = new MersenneTwisterUniformRng(seed);
        this.lowerBoundsExternal_ = lowerBounds.clone();
        this.upperBoundsExternal_ = upperBounds.clone();
        QL.require(M_ >= Mde_, "Differential Evolution subpopulation cannot be larger than total population");
    }

    private int randInRange(final int lo, final int hi) {
        // C++ uniform_int_distribution<>(min, max) is inclusive on both ends
        if ( hi <= lo )
            return lo;
        return lo + generator_.nextInt(hi - lo + 1);
    }

    private void startState(final Problem P, final EndCriteria endCriteria) {
        N_ = P.currentValue().size();
        QL.require(lowerBoundsExternal_.length == N_, "lowerBounds length must equal problem dimension");
        QL.require(upperBoundsExternal_.length == N_, "upperBounds length must equal problem dimension");

        x_ = new Array[M_];
        xI_ = new Array[M_];
        xRW_ = new Array[M_];
        values_ = new ValueIndex[M_];
        uX_ = new Array(N_);
        lX_ = new Array(N_);
        for ( int j = 0; j < N_; ++j ) {
            lX_.set(j, lowerBoundsExternal_[j]);
            uX_.set(j, upperBoundsExternal_[j]);
        }

        for ( int i = 0; i < M_; ++i ) {
            x_[i] = new Array(N_);
            xI_[i] = new Array(N_);
            xRW_[i] = new Array(N_);
            for ( int j = 0; j < N_; ++j ) {
                final double bound = uX_.get(j) - lX_.get(j);
                x_[i].set(j, lX_.get(j) + bound * rng_.next().value());
            }
            values_[i] = new ValueIndex(P.value(x_[i]), i);
        }

        intensity_.init(this);
        randomWalk_.init(this);
    }

    @Override
    public EndCriteria.Type minimize(final Problem P, final EndCriteria endCriteria) {
        EndCriteria.Type ecType;
        P.reset();
        int iteration = 0;
        int iterationStat = 0;
        final int maxIteration = endCriteria.getMaxIterations();
        final int maxIStationary = endCriteria.getMaxStationaryStateIterations();

        startState(P, endCriteria);

        final boolean isFA = Mfa_ > 0;
        final Array z = new Array(N_);

        // Set best value & position
        double bestValue = values_[0].value;
        int bestPosition = 0;
        for ( int i = 1; i < M_; ++i ) {
            if ( values_[i].value < bestValue ) {
                bestPosition = i;
                bestValue = values_[i].value;
            }
        }
        Array bestX = x_[bestPosition].clone();

        while ( true ) {
            iteration++;
            iterationStat++;
            if ( iteration > maxIteration || iterationStat > maxIStationary )
                break;

            // Sort values ascending
            Arrays.sort(values_, new Comparator< ValueIndex >() {
                @Override
                public int compare(final ValueIndex o1, final ValueIndex o2) {
                    return Double.compare(o1.value, o2.value);
                }
            });

            // Differential evolution sub-population
            if ( Mfa_ < M_ ) {
                int indexBest = values_[0].index;
                Array xBest = x_[indexBest];
                for ( int i = Mfa_; i < M_; ++i ) {
                    if ( !isFA ) {
                        indexBest = randInRange(distMin_, distMax_);
                        xBest = x_[indexBest];
                    }
                    int indexR1;
                    do {
                        indexR1 = randInRange(distMin_, distMax_);
                    } while ( indexR1 == indexBest );
                    int indexR2;
                    do {
                        indexR2 = randInRange(distMin_, distMax_);
                    } while ( indexR2 == indexBest || indexR2 == indexR1 );

                    final int index = values_[i].index;
                    final Array x = x_[index];
                    final Array xR1 = x_[indexR1];
                    final Array xR2 = x_[indexR2];
                    final int rIndex = randInRange(0, N_ - 1);
                    for ( int j = 0; j < N_; ++j ) {
                        double zj;
                        if ( j == rIndex || rng_.next().value() <= crossover_ ) {
                            zj = xBest.get(j) + mutation_ * (xR1.get(j) - xR2.get(j));
                        } else {
                            zj = x.get(j);
                        }
                        if ( zj < lX_.get(j) )
                            zj = lX_.get(j);
                        else if ( zj > uX_.get(j) )
                            zj = uX_.get(j);
                        z.set(j, zj);
                    }
                    final double val = P.value(z);
                    if ( val < values_[i].value ) {
                        for ( int j = 0; j < N_; ++j )
                            x.set(j, z.get(j));
                        values_[i].value = val;
                        if ( val < bestValue ) {
                            bestValue = val;
                            bestX = x.clone();
                            iterationStat = 0;
                        }
                    }
                }
            }

            // Firefly algorithm
            if ( isFA ) {
                intensity_.findBrightest();
                randomWalk_.walk();

                for ( int i = 0; i < Mfa_; ++i ) {
                    final int index = values_[i].index;
                    final Array x = x_[index];
                    final Array xI = xI_[index];
                    final Array xRW = xRW_[index];
                    for ( int j = 0; j < N_; ++j ) {
                        double zj = x.get(j) + xI.get(j) + xRW.get(j);
                        if ( zj < lX_.get(j) )
                            zj = lX_.get(j);
                        else if ( zj > uX_.get(j) )
                            zj = uX_.get(j);
                        z.set(j, zj);
                    }
                    final double val = P.value(z);
                    if ( !Double.isNaN(val) ) {
                        for ( int j = 0; j < N_; ++j )
                            x.set(j, z.get(j));
                        values_[i].value = val;
                        if ( val < bestValue ) {
                            bestValue = val;
                            bestX = x.clone();
                            iterationStat = 0;
                        }
                    }
                }
            }
        }
        if ( iteration > maxIteration ) {
            ecType = EndCriteria.Type.MaxIterations;
        } else {
            ecType = EndCriteria.Type.StationaryPoint;
        }
        P.setCurrentValue(bestX);
        P.setFunctionValue(bestValue);
        return ecType;
    }

    /** Base intensity. */
    public abstract static class Intensity {
        protected int Mfa_;
        protected int N_;
        protected Array[] x_;
        protected Array[] xI_;
        protected ValueIndex[] values_;

        protected void init(final FireflyAlgorithm fa) {
            this.x_ = fa.x_;
            this.xI_ = fa.xI_;
            this.values_ = fa.values_;
            this.Mfa_ = fa.Mfa_;
            this.N_ = fa.N_;
        }

        /** Compute the intensity contribution between two firefly values. */
        protected abstract double intensityImpl(double valueX, double valueY, double distance);

        protected double distance(final Array x, final Array y) {
            double d = 0.0;
            for ( int i = 0; i < N_; ++i ) {
                final double diff = x.get(i) - y.get(i);
                d += diff * diff;
            }
            return d;
        }

        /** Find brightest firefly for each firefly. */
        public void findBrightest() {
            // brightest ignores all others
            Array xI = xI_[values_[0].index];
            for ( int j = 0; j < N_; ++j )
                xI.set(j, 0.0);

            for ( int i = 1; i < Mfa_; ++i ) {
                final int idx = values_[i].index;
                final Array x = x_[idx];
                xI = xI_[idx];
                for ( int j = 0; j < N_; ++j )
                    xI.set(j, 0.0);
                final double valueX = values_[i].value;
                for ( int k = 0; k + 1 < i; ++k ) {
                    final Array y = x_[values_[k].index];
                    final double valueY = values_[k].value;
                    final double intensity = intensityImpl(valueX, valueY, distance(x, y));
                    for ( int j = 0; j < N_; ++j ) {
                        xI.set(j, xI.get(j) + intensity * (y.get(j) - x.get(j)));
                    }
                }
            }
        }
    }

    /** Exponentially decreasing intensity. */
    public static class ExponentialIntensity extends Intensity {
        private final double beta0_;
        private final double betaMin_;
        private final double gamma_;

        public ExponentialIntensity(final double beta0, final double betaMin, final double gamma) {
            this.beta0_ = beta0;
            this.betaMin_ = betaMin;
            this.gamma_ = gamma;
        }

        @Override
        protected double intensityImpl(final double valueX, final double valueY, final double d) {
            return (beta0_ - betaMin_) * Math.exp(-gamma_ * d) + betaMin_;
        }
    }

    /** Inverse-square law intensity. */
    public static class InverseLawSquareIntensity extends Intensity {
        private final double beta0_;
        private final double betaMin_;

        public InverseLawSquareIntensity(final double beta0, final double betaMin) {
            this.beta0_ = beta0;
            this.betaMin_ = betaMin;
        }

        @Override
        protected double intensityImpl(final double valueX, final double valueY, final double d) {
            return (beta0_ - betaMin_) / (d + Math.ulp(1.0)) + betaMin_;
        }
    }

    /** Base random walk. */
    public abstract static class RandomWalk {
        protected int Mfa_;
        protected int N_;
        protected Array[] x_;
        protected Array[] xRW_;
        protected ValueIndex[] values_;
        protected Array lX_;
        protected Array uX_;

        protected void init(final FireflyAlgorithm fa) {
            this.x_ = fa.x_;
            this.xRW_ = fa.xRW_;
            this.values_ = fa.values_;
            this.Mfa_ = fa.Mfa_;
            this.N_ = fa.N_;
            this.lX_ = fa.lX_;
            this.uX_ = fa.uX_;
        }

        protected abstract void walkImpl(Array xRW);

        /** Perform one random-walk step on every firefly. */
        public void walk() {
            for ( int i = 0; i < Mfa_; ++i ) {
                walkImpl(xRW_[values_[i].index]);
            }
        }
    }

    /** Gaussian random walk: each component drawn from a centred Gaussian. */
    public static class GaussianWalk extends RandomWalk {
        protected final double sigma_;
        protected final java.util.Random rng_;
        /** Step multiplier; mutable so {@link DecreasingGaussianWalk} can decay it per iteration (C++ delta_). */
        protected double delta_;

        public GaussianWalk(final double sigma, final double delta, final long seed) {
            this.sigma_ = sigma;
            this.delta_ = delta;
            this.rng_ = new java.util.Random(seed);
        }

        @Override
        protected void walkImpl(final Array xRW) {
            for ( int j = 0; j < N_; ++j ) {
                xRW.set(j, delta_ * sigma_ * rng_.nextGaussian());
            }
        }
    }

    /** Levy-flight random walk; each step drawn from a 1-D Levy flight distribution. */
    public static class LevyFlightWalk extends RandomWalk {
        private final LevyFlightDistribution lfd_;
        private final double delta_;
        private final java.util.Random rng_;

        public LevyFlightWalk(final double alpha, final double xm, final double delta, final long seed) {
            this.lfd_ = new LevyFlightDistribution(xm, alpha);
            this.delta_ = delta;
            this.rng_ = new java.util.Random(seed);
        }

        @Override
        protected void walkImpl(final Array xRW) {
            for ( int j = 0; j < N_; ++j ) {
                final double u = rng_.nextDouble();
                final double sign = (rng_.nextDouble() < 0.5) ? -1.0 : 1.0;
                xRW.set(j, delta_ * sign * lfd_.draw(u));
            }
        }
    }

    /**
     * Decreasing Gaussian random walk: like {@link GaussianWalk}, but the step multiplier {@code delta} is
     * squared (i.e. shrinks, since {@code delta < 1}) every time all fireflies have been processed in an
     * iteration.
     *
     * <p>C++ fireflyalgorithm.hpp:255.
     */
    public static class DecreasingGaussianWalk extends GaussianWalk {
        private final double delta0_;
        private int iteration_;

        public DecreasingGaussianWalk(final double sigma, final double delta, final long seed) {
            super(sigma, delta, seed);
            this.delta0_ = delta;
        }

        @Override
        protected void walkImpl(final Array xRW) {
            // C++ fireflyalgorithm.hpp:263-272
            iteration_++;
            if ( iteration_ > Mfa_ ) {
                // Every time all the fireflies have been processed, multiply delta by itself
                iteration_ = 0;
                delta_ *= delta_;
            }
            super.walkImpl(xRW);
        }

        @Override
        protected void init(final FireflyAlgorithm fa) {
            // C++ fireflyalgorithm.hpp:273-277
            super.init(fa);
            iteration_ = 0;
            delta_ = delta0_;
        }

        /** Exposes the current step multiplier for cross-validation. */
        public double currentDelta() {
            return delta_;
        }
    }

    static final class ValueIndex {
        final int index;
        double value;

        ValueIndex(final double v, final int i) {
            this.value = v;
            this.index = i;
        }
    }
}
