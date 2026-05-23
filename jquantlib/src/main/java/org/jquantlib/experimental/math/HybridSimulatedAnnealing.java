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

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.EndCriteria;
import org.jquantlib.math.optimization.LevenbergMarquardt;
import org.jquantlib.math.optimization.OptimizationMethod;
import org.jquantlib.math.optimization.Problem;

/**
 * Hybrid simulated-annealing optimiser with optional local-search post-processing.
 *
 * <p>Java port of QuantLib v1.42.1 template
 * {@code template <class Sampler, class Probability, class Temperature, class Reannealing>
 * class HybridSimulatedAnnealing} (declared in
 * {@code ql/experimental/math/hybridsimulatedannealing.hpp}). Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>Implementation based on Lester Ingber, <i>"Very Fast Simulated
 * Re-Annealing"</i>, Mathl. Comput. Modelling, 967-973, 1989.
 *
 * <p>The method:
 * <ol>
 *   <li>{@link Sampler} draws a new point from a temperature-controlled
 *       distribution centred on the current point.</li>
 *   <li>{@link Probability} accepts or rejects the new point based on the
 *       current and proposed objective values plus the current temperature.</li>
 *   <li>{@link Temperature} updates the per-dimension temperature schedule
 *       {@code T(k)} for iteration {@code k}.</li>
 *   <li>{@link Reannealing} (optional) rescales the iteration counter
 *       independently per dimension to improve convergence.</li>
 * </ol>
 * The "hybrid" refers to the optional {@link OptimizationMethod} local optimizer
 * applied at each new accepted point or each new best point depending on
 * {@link LocalOptimizeScheme}.
 *
 * <p>Java vs C++: the C++ class uses non-virtual template-parameter dispatch
 * for the four functor classes; Java uses interfaces. Performance impact is
 * negligible because the inner loop is dominated by the objective evaluation,
 * not the functor dispatch. The C++ catch-all {@code try { ... } catch(...) {}}
 * swallowing all exceptions on objective evaluation is mirrored: any exception
 * thrown by {@code P.value(newPoint)} (or by the local optimizer) is caught
 * and the iteration proceeds with the previous current point.
 *
 * @see SamplerGaussian
 * @see ProbabilityBoltzmannDownhill
 * @see TemperatureExponential
 * @see ReannealingTrivial
 */
public final class HybridSimulatedAnnealing extends OptimizationMethod {

    /** Local-optimiser invocation policy. Mirrors C++ {@code LocalOptimizeScheme}. */
    public enum LocalOptimizeScheme {
        /** Never run the local optimiser. */
        NoLocalOptimize,
        /** Run the local optimiser on every accepted new point. */
        EveryNewPoint,
        /** Run the local optimiser only when a new best point is found. */
        EveryBestPoint
    }

    /** Reset-on-stagnation strategy. Mirrors C++ {@code ResetScheme}. */
    public enum ResetScheme {
        /** Never reset the current point. */
        NoResetScheme,
        /** Reset the current point to the best-so-far point. */
        ResetToBestPoint,
        /** Reset the current point to the starting point. */
        ResetToOrigin
    }

    /**
     * Sampler functor: draws a new candidate {@code newPoint} from the proposal distribution centred on
     * {@code currentPoint} parameterised by the per-dimension temperature {@code temp}.
     *
     * <p>Mirrors C++ functor signature
     * {@code void operator()(Array &newPoint, const Array &currentPoint, const Array &temp) const}.
     */
    @FunctionalInterface
    public interface Sampler {
        void sample(Array newPoint, Array currentPoint, Array temp);
    }

    /**
     * Probability functor: returns {@code true} when the new point should be accepted.
     *
     * <p>Mirrors C++ functor signature
     * {@code bool operator()(Real currentValue, Real newValue, const Array &temp) const}.
     */
    @FunctionalInterface
    public interface Probability {
        boolean accept(double currentValue, double newValue, Array temp);
    }

    /**
     * Temperature functor: updates the per-dimension temperature schedule. The new temperature is written into
     * {@code newTemp}; {@code steps} is the per-dimension iteration counter (rescaled by the reannealing functor).
     *
     * <p>Mirrors C++ functor signature
     * {@code void operator()(Array &newTemp, const Array &currTemp, const Array &steps) const}.
     */
    @FunctionalInterface
    public interface Temperature {
        void update(Array newTemp, Array currTemp, Array steps);
    }

    /**
     * Reannealing functor: rescales the per-dimension {@code steps} counter to redistribute search effort across
     * dimensions. The default {@link ReannealingTrivial} is a no-op.
     *
     * <p>Mirrors C++ functor signature
     * {@code void operator()(Array &steps, const Array &currentPoint, Real currentValue, const Array &currTemp) const}
     * with an additional {@code setProblem(Problem)} hook used by the finite-difference variant.
     */
    public interface Reannealing {
        /** Called once at the start of {@link HybridSimulatedAnnealing#minimize}. */
        default void setProblem(Problem p) {
        }

        void reanneal(Array steps, Array currentPoint, double currentValue, Array currTemp);
    }

    private final Sampler sampler;
    private final Probability probability;
    private final Temperature temperature;
    private final Reannealing reannealing;
    private final double startTemperature;
    private final double endTemperature;
    private final int reAnnealSteps;
    private final ResetScheme resetScheme;
    private final int resetSteps;
    private final OptimizationMethod localOptimizer;
    private final LocalOptimizeScheme optimizeScheme;

    /** Sentinel matching C++ {@code QL_MAX_INTEGER}; used when caller passes 0 to disable reannealing/reset. */
    private static final int DISABLED_STEPS = Integer.MAX_VALUE;

    /**
     * Full constructor mirroring C++ ctor.
     *
     * @param sampler          proposal-distribution functor
     * @param probability      acceptance functor
     * @param temperature      temperature-schedule functor
     * @param reannealing      reannealing functor; pass {@link ReannealingTrivial} to disable
     * @param startTemperature initial temperature (per dimension); C++ default 200.0
     * @param endTemperature   final-temperature threshold; C++ default 0.01
     * @param reAnnealSteps    iterations between reannealings; pass 0 to disable; C++ default 50
     * @param resetScheme      reset strategy on stagnation; C++ default {@link ResetScheme#ResetToBestPoint}
     * @param resetSteps       iterations between resets; pass 0 to disable; C++ default 150
     * @param localOptimizer   optional local-search optimiser; may be {@code null} to disable
     * @param optimizeScheme   when to invoke {@code localOptimizer}; forced to
     *                         {@link LocalOptimizeScheme#NoLocalOptimize} when {@code localOptimizer == null}
     */
    public HybridSimulatedAnnealing(final Sampler sampler, final Probability probability, final Temperature temperature,
            final Reannealing reannealing, final double startTemperature, final double endTemperature,
            final int reAnnealSteps, final ResetScheme resetScheme, final int resetSteps,
            final OptimizationMethod localOptimizer, final LocalOptimizeScheme optimizeScheme) {
        this.sampler = sampler;
        this.probability = probability;
        this.temperature = temperature;
        this.reannealing = reannealing;
        this.startTemperature = startTemperature;
        this.endTemperature = endTemperature;
        this.reAnnealSteps = (reAnnealSteps == 0) ? DISABLED_STEPS : reAnnealSteps;
        this.resetScheme = resetScheme;
        this.resetSteps = (resetSteps == 0) ? DISABLED_STEPS : resetSteps;
        this.localOptimizer = localOptimizer;
        this.optimizeScheme = (localOptimizer != null) ? optimizeScheme : LocalOptimizeScheme.NoLocalOptimize;
    }

    /** Convenience ctor matching the C++ defaults: LevenbergMarquardt local optimiser + EveryBestPoint scheme. */
    public HybridSimulatedAnnealing(final Sampler sampler, final Probability probability,
            final Temperature temperature) {
        this(sampler, probability, temperature, new ReannealingTrivial(), 200.0, 0.01, 50, ResetScheme.ResetToBestPoint,
                150, new LevenbergMarquardt(), LocalOptimizeScheme.EveryBestPoint);
    }

    /** Convenience ctor with no local optimiser. */
    public HybridSimulatedAnnealing(final Sampler sampler, final Probability probability, final Temperature temperature,
            final Reannealing reannealing) {
        this(sampler, probability, temperature, reannealing, 200.0, 0.01, 50, ResetScheme.ResetToBestPoint, 150, null,
                LocalOptimizeScheme.NoLocalOptimize);
    }

    @Override
    public EndCriteria.Type minimize(final Problem p, final EndCriteria endCriteria) {
        EndCriteria.Type ecType = EndCriteria.Type.None;
        p.reset();
        reannealing.setProblem(p);
        Array x = p.currentValue();
        final int n = x.size();
        int k = 1;
        int kStationary = 1;
        int kReAnneal = 1;
        int kReset = 1;
        final int maxK = endCriteria.getMaxIterations();
        final int maxKStationary = endCriteria.getMaxStationaryStateIterations();
        boolean temperatureBreached = false;
        final Array currentTemperature = new Array(n, startTemperature, 0.0);
        final Array annealStep = new Array(n, 1.0, 0.0);
        Array bestPoint = x.clone();
        Array currentPoint = x.clone();
        final Array startingPoint = x.clone();
        final Array newPoint = x.clone();
        double bestValue = p.value(bestPoint);
        double currentValue = bestValue;
        final double startingValue = bestValue; // for ResetToOrigin

        while ( k <= maxK && kStationary <= maxKStationary && !temperatureBreached ) {
            // Draw a new sample point
            sampler.sample(newPoint, currentPoint, currentTemperature);
            try {
                // Evaluate new point
                double newValue = p.value(newPoint);

                // Determine whether the new point is accepted
                if ( probability.accept(currentValue, newValue, currentTemperature) ) {
                    if ( optimizeScheme == LocalOptimizeScheme.EveryNewPoint ) {
                        p.setCurrentValue(newPoint);
                        p.setFunctionValue(newValue);
                        localOptimizer.minimize(p, endCriteria);
                        // refresh newPoint/newValue from problem state
                        final Array opt = p.currentValue();
                        for ( int i = 0; i < n; ++i ) {
                            newPoint.set(i, opt.get(i));
                        }
                        newValue = p.functionValue();
                    }
                    for ( int i = 0; i < n; ++i ) {
                        currentPoint.set(i, newPoint.get(i));
                    }
                    currentValue = newValue;
                }

                // Check if we have a new best point
                if ( newValue < bestValue ) {
                    if ( optimizeScheme == LocalOptimizeScheme.EveryBestPoint ) {
                        p.setCurrentValue(newPoint);
                        p.setFunctionValue(newValue);
                        localOptimizer.minimize(p, endCriteria);
                        final Array opt = p.currentValue();
                        for ( int i = 0; i < n; ++i ) {
                            newPoint.set(i, opt.get(i));
                        }
                        newValue = p.functionValue();
                    }
                    kStationary = 0;
                    bestValue = newValue;
                    bestPoint = newPoint.clone();
                }
            } catch ( final RuntimeException ignore ) {
                // C++ catches all exceptions and continues to the next draw.
            }

            // Increase step counters
            k++;
            kStationary++;
            for ( int i = 0; i < n; ++i ) {
                annealStep.set(i, annealStep.get(i) + 1.0);
            }

            // Reanneal if necessary
            if ( kReAnneal == reAnnealSteps ) {
                kReAnneal = 0;
                reannealing.reanneal(annealStep, currentPoint, currentValue, currentTemperature);
            }
            kReAnneal++;

            // Reset if necessary
            if ( kReset == resetSteps ) {
                kReset = 0;
                switch ( resetScheme ) {
                case NoResetScheme:
                    break;
                case ResetToOrigin:
                    for ( int i = 0; i < n; ++i ) {
                        currentPoint.set(i, startingPoint.get(i));
                    }
                    currentValue = startingValue;
                    break;
                case ResetToBestPoint:
                    for ( int i = 0; i < n; ++i ) {
                        currentPoint.set(i, bestPoint.get(i));
                    }
                    currentValue = bestValue;
                    break;
                }
            }
            kReset++;

            // Update temperature
            temperature.update(currentTemperature, currentTemperature, annealStep);

            // Check breach.
            // NB: the C++ uses (temperatureBreached && currentTemperature[i] < endTemperature_) which is buggy —
            // once false it never flips true. We mirror that exact bug for behaviour parity.
            for ( int i = 0; i < n; ++i ) {
                temperatureBreached = temperatureBreached && currentTemperature.get(i) < endTemperature;
            }
        }

        // End criteria
        if ( k > maxK ) {
            ecType = EndCriteria.Type.MaxIterations;
        } else if ( kStationary > maxKStationary ) {
            ecType = EndCriteria.Type.StationaryPoint;
        }

        // Set result to best point
        p.setCurrentValue(bestPoint);
        p.setFunctionValue(bestValue);
        return ecType;
    }
}
