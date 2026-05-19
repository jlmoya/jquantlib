/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2006 Warren Chou
*/
package org.jquantlib.pricingengines.forward;

import org.jquantlib.QL;
import org.jquantlib.instruments.VarianceSwap;
import org.jquantlib.math.Ops;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.integrals.SegmentIntegral;
import org.jquantlib.math.randomnumbers.InverseCumulativeRsg;
import org.jquantlib.math.randomnumbers.MersenneTwisterUniformRng;
import org.jquantlib.math.randomnumbers.RandomSequenceGenerator;
import org.jquantlib.methods.montecarlo.MonteCarloModel;
import org.jquantlib.methods.montecarlo.Path;
import org.jquantlib.methods.montecarlo.PathGenerator;
import org.jquantlib.methods.montecarlo.PathPricer;
import org.jquantlib.pricingengines.McSimulation;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.time.TimeGrid;

/**
 * Variance-swap pricing engine using Monte Carlo simulation, as described in Demeterfi, Derman, Kamal &amp; Zou,
 * <em>A Guide to Volatility and Variance Swaps</em> (1999).
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/pricingengines/forward/mcvarianceswapengine.hpp} (Phase 5e.5b-CFC-d-180).
 *
 * <p>The C++ template uses multiple inheritance ({@code VarianceSwap::engine}
 * + {@code McSimulation<SingleVariate,RNG,S>}). Java single-inheritance forces composition: this engine extends
 * {@link VarianceSwap.EngineImpl} and embeds a delegate {@link McSimulation} built lazily in {@link #calculate()},
 * mirroring {@code MCVanillaEngine}.
 *
 * <p>Specialised for {@code RNG = PseudoRandom} (Mersenne-Twister
 * +InverseCumulativeNormal) — quasi-random / low-discrepancy variants are deferred.
 */
public class MCVarianceSwapEngine extends VarianceSwap.EngineImpl {

    //
    // protected fields
    //

    protected final GeneralizedBlackScholesProcess process_;
    protected final int timeSteps_;
    protected final int timeStepsPerYear_;
    protected final int requiredSamples_;
    protected final int maxSamples_;
    protected final double requiredTolerance_;
    protected final boolean brownianBridge_;
    protected final boolean antitheticVariate_;
    protected final long seed_;

    /** Lazily-built delegate that owns the {@link MonteCarloModel}. */
    protected McSimulation< Path > simulation_;

    //
    // constructors
    //

    /**
     * Mirrors C++
     * {@code MCVarianceSwapEngine(process, timeSteps, timeStepsPerYear, brownianBridge, antitheticVariate,
     * requiredSamples, requiredTolerance, maxSamples, seed)}.
     *
     * <p>Pass {@link McSimulation#NULL_SAMPLES} (Integer.MAX_VALUE) /
     * {@link McSimulation#NULL_TOLERANCE} (NaN) for "not specified".
     */
    public MCVarianceSwapEngine(final GeneralizedBlackScholesProcess process, final int timeSteps,
            final int timeStepsPerYear, final boolean brownianBridge, final boolean antitheticVariate,
            final int requiredSamples, final double requiredTolerance, final int maxSamples, final long seed) {
        super();
        QL.require(timeSteps != McSimulation.NULL_SAMPLES || timeStepsPerYear != McSimulation.NULL_SAMPLES,
                "no time steps provided");
        QL.require(timeSteps == McSimulation.NULL_SAMPLES || timeStepsPerYear == McSimulation.NULL_SAMPLES,
                "both time steps and time steps per year were provided");
        QL.require(timeSteps != 0, "timeSteps must be positive, " + timeSteps + " not allowed");
        QL.require(timeStepsPerYear != 0, "timeStepsPerYear must be positive, " + timeStepsPerYear + " not allowed");

        this.process_ = process;
        this.timeSteps_ = timeSteps;
        this.timeStepsPerYear_ = timeStepsPerYear;
        this.brownianBridge_ = brownianBridge;
        this.antitheticVariate_ = antitheticVariate;
        this.requiredSamples_ = requiredSamples;
        this.maxSamples_ = maxSamples;
        this.requiredTolerance_ = requiredTolerance;
        this.seed_ = seed;
        this.process_.addObserver(this);
    }

    //
    // McSimulation-shaped helpers
    //

    /**
     * Mirrors C++ {@code TimeGrid timeGrid()}: returns a uniform time grid whose terminal date matches the swap's
     * maturity.
     */
    protected TimeGrid timeGrid() {
        final VarianceSwap.ArgumentsImpl a = (VarianceSwap.ArgumentsImpl) arguments_;
        final double t = process_.time(a.maturityDate);
        if ( timeSteps_ != McSimulation.NULL_SAMPLES ) {
            return new TimeGrid(t, timeSteps_);
        } else if ( timeStepsPerYear_ != McSimulation.NULL_SAMPLES ) {
            final int steps = (int) (timeStepsPerYear_ * t);
            return new TimeGrid(t, Math.max(steps, 1));
        } else {
            throw new RuntimeException("time steps not specified");
        }
    }

    /**
     * Builds a Gaussian-driven {@link PathGenerator} for the underlying GBS process. Mirrors C++
     * {@code MCVarianceSwapEngine::pathGenerator()} specialised to {@code RNG = PseudoRandom}.
     */
    protected MonteCarloModel.PathGeneratorAdapter< Path > pathGenerator() {
        final TimeGrid grid = timeGrid();
        final int dimensions = process_.factors() * (grid.size() - 1);
        final RandomSequenceGenerator< MersenneTwisterUniformRng > uniformRsg = new RandomSequenceGenerator< MersenneTwisterUniformRng >(
                MersenneTwisterUniformRng.class, dimensions, seed_);
        final InverseCumulativeRsg< RandomSequenceGenerator< MersenneTwisterUniformRng >, InverseCumulativeNormal > gsg = new InverseCumulativeRsg< RandomSequenceGenerator< MersenneTwisterUniformRng >, InverseCumulativeNormal >(
                uniformRsg, new InverseCumulativeNormal());
        final PathGenerator< InverseCumulativeRsg< RandomSequenceGenerator< MersenneTwisterUniformRng >, InverseCumulativeNormal > > gen = new PathGenerator< InverseCumulativeRsg< RandomSequenceGenerator< MersenneTwisterUniformRng >, InverseCumulativeNormal > >(
                process_, grid, gsg, brownianBridge_);
        return new MonteCarloModel.PathGeneratorAdapterImpl(gen);
    }

    /** Build the per-path pricer (realised average variance via segment integral). */
    protected PathPricer< Path > pathPricer() {
        return new VariancePathPricer(process_);
    }

    //
    // PricingEngine
    //

    @Override
    public void calculate() {
        final VarianceSwap.ArgumentsImpl a = (VarianceSwap.ArgumentsImpl) arguments_;
        final VarianceSwap.ResultsImpl r = (VarianceSwap.ResultsImpl) results_;

        this.simulation_ = new McSimulation< Path >(antitheticVariate_, false) {
            @Override
            protected PathPricer< Path > pathPricer() {
                return MCVarianceSwapEngine.this.pathPricer();
            }

            @Override
            protected MonteCarloModel.PathGeneratorAdapter< Path > pathGenerator() {
                return MCVarianceSwapEngine.this.pathGenerator();
            }

            @Override
            protected TimeGrid timeGrid() {
                return MCVarianceSwapEngine.this.timeGrid();
            }
        };
        this.simulation_.calculate(requiredTolerance_, requiredSamples_, maxSamples_);

        r.variance = this.simulation_.sampleAccumulator().mean();

        final double riskFreeDiscount = process_.riskFreeRate().currentLink().discount(a.maturityDate);
        final double multiplier;
        switch ( a.position ) {
        case Long:
            multiplier = +1.0;
            break;
        case Short:
            multiplier = -1.0;
            break;
        default:
            throw new RuntimeException("Unknown position");
        }
        final double m = multiplier * riskFreeDiscount * a.notional;

        r.value = m * (r.variance - a.strike);

        // PseudoRandom allows error estimate.
        final double varianceError = this.simulation_.errorEstimate();
        r.errorEstimate = m * varianceError;
    }

    //
    // path-pricer (mirrors C++ VariancePathPricer)
    //

    /**
     * Computes the realised average variance along the path via a trapezoidal {@link SegmentIntegral} of
     * {@code sigma(t, S(t))^2}. Mirrors C++ {@code VariancePathPricer::operator()(const Path&)}.
     */
    public static final class VariancePathPricer extends PathPricer< Path > {

        private final GeneralizedBlackScholesProcess process_;

        public VariancePathPricer(final GeneralizedBlackScholesProcess process) {
            this.process_ = process;
        }

        @Override
        public Double op(final Path path) {
            QL.require(!path.empty(), "the path cannot be empty");
            final double t0 = path.timeGrid().front();
            final double t = path.timeGrid().back();
            final double dt = path.timeGrid().dt(0);
            final SegmentIntegral integrator = new SegmentIntegral((int) (t / dt));
            final Integrand f = new Integrand(path, process_, dt);
            return integrator.op(f, t0, t) / t;
        }
    }

    /** sigma^2(t, S(t)) integrand for the realised-variance computation. */
    private static final class Integrand implements Ops.DoubleOp {
        private final Path path_;
        private final GeneralizedBlackScholesProcess process_;
        private final double dt_;

        Integrand(final Path path, final GeneralizedBlackScholesProcess process, final double dt) {
            this.path_ = path;
            this.process_ = process;
            this.dt_ = dt;
        }

        @Override
        public double op(final double t) {
            int i = (int) (t / dt_);
            if ( i >= path_.length() ) {
                i = path_.length() - 1;
            }
            final double sigma = process_.diffusion(t, path_.get(i));
            return sigma * sigma;
        }
    }

    //
    // builder (mirrors C++ MakeMCVarianceSwapEngine)
    //

    /**
     * Fluent builder for {@link MCVarianceSwapEngine}.
     *
     * <p>Java port of {@code QuantLib v1.42.1 MakeMCVarianceSwapEngine}.
     * The C++ template is parameterised by an RNG traits type; the Java port is specialised to PseudoRandom (MT +
     * InverseCumulativeNormal).
     */
    public static class MakeMCVarianceSwapEngine {

        private final GeneralizedBlackScholesProcess process_;
        private boolean antithetic_ = false;
        private int steps_ = McSimulation.NULL_SAMPLES;
        private int stepsPerYear_ = McSimulation.NULL_SAMPLES;
        private int samples_ = McSimulation.NULL_SAMPLES;
        private int maxSamples_ = McSimulation.NULL_SAMPLES;
        private double tolerance_ = McSimulation.NULL_TOLERANCE;
        private boolean brownianBridge_ = false;
        private long seed_ = 0L;

        public MakeMCVarianceSwapEngine(final GeneralizedBlackScholesProcess process) {
            QL.require(process != null, "null GBS process");
            this.process_ = process;
        }

        public MakeMCVarianceSwapEngine withSteps(final int steps) {
            this.steps_ = steps;
            return this;
        }

        public MakeMCVarianceSwapEngine withStepsPerYear(final int steps) {
            this.stepsPerYear_ = steps;
            return this;
        }

        public MakeMCVarianceSwapEngine withSamples(final int samples) {
            QL.require(Double.isNaN(tolerance_), "tolerance already set");
            this.samples_ = samples;
            return this;
        }

        public MakeMCVarianceSwapEngine withAbsoluteTolerance(final double tolerance) {
            QL.require(samples_ == McSimulation.NULL_SAMPLES, "number of samples already set");
            this.tolerance_ = tolerance;
            return this;
        }

        public MakeMCVarianceSwapEngine withMaxSamples(final int samples) {
            this.maxSamples_ = samples;
            return this;
        }

        public MakeMCVarianceSwapEngine withSeed(final long seed) {
            this.seed_ = seed;
            return this;
        }

        public MakeMCVarianceSwapEngine withBrownianBridge(final boolean brownianBridge) {
            this.brownianBridge_ = brownianBridge;
            return this;
        }

        public MakeMCVarianceSwapEngine withBrownianBridge() {
            return withBrownianBridge(true);
        }

        public MakeMCVarianceSwapEngine withAntitheticVariate(final boolean b) {
            this.antithetic_ = b;
            return this;
        }

        public MakeMCVarianceSwapEngine withAntitheticVariate() {
            return withAntitheticVariate(true);
        }

        public MCVarianceSwapEngine value() {
            QL.require(steps_ != McSimulation.NULL_SAMPLES || stepsPerYear_ != McSimulation.NULL_SAMPLES,
                    "number of steps not given");
            QL.require(steps_ == McSimulation.NULL_SAMPLES || stepsPerYear_ == McSimulation.NULL_SAMPLES,
                    "number of steps overspecified");
            return new MCVarianceSwapEngine(process_, steps_, stepsPerYear_, brownianBridge_, antithetic_, samples_,
                    tolerance_, maxSamples_, seed_);
        }
    }
}
