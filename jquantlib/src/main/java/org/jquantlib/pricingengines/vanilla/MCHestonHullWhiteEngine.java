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
 Copyright (C) 2007, 2008 Klaus Spanderen
*/
package org.jquantlib.pricingengines.vanilla;

import org.jquantlib.QL;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.EuropeanOption;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.instruments.Payoff;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.randomnumbers.InverseCumulativeRsg;
import org.jquantlib.math.randomnumbers.MersenneTwisterUniformRng;
import org.jquantlib.math.randomnumbers.RandomSequenceGenerator;
import org.jquantlib.methods.montecarlo.MonteCarloModel;
import org.jquantlib.methods.montecarlo.MultiPath;
import org.jquantlib.methods.montecarlo.MultiPathGenerator;
import org.jquantlib.methods.montecarlo.PathPricer;
import org.jquantlib.model.equity.HestonModel;
import org.jquantlib.model.shortrate.onefactormodels.HullWhite;
import org.jquantlib.pricingengines.McSimulation;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.processes.HullWhiteForwardProcess;
import org.jquantlib.processes.HybridHestonHullWhiteProcess;
import org.jquantlib.time.Date;
import org.jquantlib.time.TimeGrid;

/**
 * Monte-Carlo Heston / Hull-White vanilla option engine.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/pricingengines/vanilla/mchestonhullwhiteengine.{hpp,cpp}}
 * (Phase 5e.5b-CFC-d-113). Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>The C++ class is a template
 * {@code MCHestonHullWhiteEngine<RNG=PseudoRandom, S=Statistics>}
 * inheriting from {@code MCVanillaEngine<MultiVariate, RNG, S>}. Java
 * follows the {@link MCEuropeanHestonEngine} pattern: stand the engine
 * up directly on {@link OneAssetOption.EngineImpl} + an embedded
 * {@link McSimulation McSimulation&lt;MultiPath&gt;} delegate, keeping
 * the C++ {@code calculate / pathPricer / controlPathPricer /
 * controlPricingEngine / controlPathGenerator} contract.
 *
 * <p>The control-variate variant uses a zero-cross-correlation HHW
 * process with the analytic Heston / Hull-White semi-closed-form
 * pricer ({@link AnalyticHestonHullWhiteEngine}, integration order 128 —
 * see Phase 4a.5 A.5.2 note in {@link AnalyticHestonEngine}) as the CV
 * value. Negative deep-OTM CV values are clipped to zero just like in
 * the C++ {@code calculate()} hook.
 *
 * <p>Specialised for {@code RNG = PseudoRandom} (Mersenne-Twister +
 * {@code InverseCumulativeNormal}) — quasi-random / low-discrepancy
 * variants are deferred.
 *
 * @see HybridHestonHullWhiteProcess
 * @see AnalyticHestonHullWhiteEngine
 * @see MCEuropeanHestonEngine
 *
 * @author JQuantLib
 */
public class MCHestonHullWhiteEngine extends OneAssetOption.EngineImpl {

    //
    // protected fields (mirror C++ MCVanillaEngine<MultiVariate,...>
    // that this class would inherit from in the C++ template).
    //

    protected final HybridHestonHullWhiteProcess process_;
    protected final int timeSteps_;
    protected final int timeStepsPerYear_;
    protected final int requiredSamples_;
    protected final int maxSamples_;
    protected final double requiredTolerance_;
    protected final boolean antitheticVariate_;
    protected final boolean controlVariate_;
    protected final long seed_;

    /** Lazily-built delegate that owns the {@link MonteCarloModel}. */
    protected McSimulation<MultiPath> simulation_;


    //
    // constructors
    //

    /**
     * Mirrors C++ {@code MCHestonHullWhiteEngine(process, timeSteps,
     * timeStepsPerYear, antitheticVariate, controlVariate,
     * requiredSamples, requiredTolerance, maxSamples, seed)}.
     *
     * <p>Pass {@link McSimulation#NULL_SAMPLES} ({@code Integer.MAX_VALUE})
     * or {@link McSimulation#NULL_TOLERANCE} ({@code NaN}) for "not
     * specified".
     */
    public MCHestonHullWhiteEngine(final HybridHestonHullWhiteProcess process,
                                   final int timeSteps,
                                   final int timeStepsPerYear,
                                   final boolean antitheticVariate,
                                   final boolean controlVariate,
                                   final int requiredSamples,
                                   final double requiredTolerance,
                                   final int maxSamples,
                                   final long seed) {
        super();
        QL.require(process != null, "null hybrid Heston / Hull-White process");
        QL.require(timeSteps != McSimulation.NULL_SAMPLES
                || timeStepsPerYear != McSimulation.NULL_SAMPLES,
                "no time steps provided");
        QL.require(timeSteps == McSimulation.NULL_SAMPLES
                || timeStepsPerYear == McSimulation.NULL_SAMPLES,
                "both time steps and time steps per year were provided");
        QL.require(timeSteps != 0,
                "timeSteps must be positive, " + timeSteps + " not allowed");
        QL.require(timeStepsPerYear != 0,
                "timeStepsPerYear must be positive, " + timeStepsPerYear + " not allowed");

        this.process_ = process;
        this.timeSteps_ = timeSteps;
        this.timeStepsPerYear_ = timeStepsPerYear;
        this.antitheticVariate_ = antitheticVariate;
        this.controlVariate_ = controlVariate;
        this.requiredSamples_ = requiredSamples;
        this.maxSamples_ = maxSamples;
        this.requiredTolerance_ = requiredTolerance;
        this.seed_ = seed;
        this.process_.addObserver(this);
    }


    //
    // McSimulation-shaped helpers
    //

    /** Mirrors C++ {@code MCVanillaEngine::timeGrid()}. */
    protected TimeGrid timeGrid() {
        final OneAssetOption.ArgumentsImpl a = (OneAssetOption.ArgumentsImpl) arguments_;
        final Date lastExerciseDate = a.exercise.lastDate();
        final double t = process_.time(lastExerciseDate);
        if (timeSteps_ != McSimulation.NULL_SAMPLES) {
            return new TimeGrid(t, timeSteps_);
        } else if (timeStepsPerYear_ != McSimulation.NULL_SAMPLES) {
            final int steps = (int) (timeStepsPerYear_ * t);
            return new TimeGrid(t, Math.max(steps, 1));
        } else {
            throw new RuntimeException("time steps not specified");
        }
    }

    /**
     * Builds a Gaussian-driven {@link MultiPathGenerator} for the
     * underlying {@link HybridHestonHullWhiteProcess}. Mirrors C++
     * {@code MCVanillaEngine::pathGenerator()} specialised to
     * {@code MC = MultiVariate, RNG = PseudoRandom}.
     */
    protected MonteCarloModel.PathGeneratorAdapter<MultiPath> pathGenerator() {
        return makeGenerator(process_);
    }

    /**
     * Control-variate path generator — same time grid but a process
     * with zero cross-correlation (C++ {@code controlPathGenerator()}).
     */
    protected MonteCarloModel.PathGeneratorAdapter<MultiPath> controlPathGenerator() {
        final HybridHestonHullWhiteProcess cvProcess =
                new HybridHestonHullWhiteProcess(
                        process_.hestonProcess(),
                        process_.hullWhiteProcess(),
                        0.0,
                        process_.discretization());
        return makeGenerator(cvProcess);
    }

    private MonteCarloModel.PathGeneratorAdapter<MultiPath> makeGenerator(
            final HybridHestonHullWhiteProcess proc) {
        final TimeGrid grid = timeGrid();
        final int dimensions = proc.factors() * (grid.size() - 1);
        final RandomSequenceGenerator<MersenneTwisterUniformRng> uniformRsg =
                new RandomSequenceGenerator<MersenneTwisterUniformRng>(
                        MersenneTwisterUniformRng.class, dimensions, seed_);
        final InverseCumulativeRsg<RandomSequenceGenerator<MersenneTwisterUniformRng>,
                InverseCumulativeNormal> gsg =
                new InverseCumulativeRsg<RandomSequenceGenerator<MersenneTwisterUniformRng>,
                        InverseCumulativeNormal>(uniformRsg, new InverseCumulativeNormal());
        final MultiPathGenerator<InverseCumulativeRsg<RandomSequenceGenerator<MersenneTwisterUniformRng>,
                InverseCumulativeNormal>> gen =
                new MultiPathGenerator<InverseCumulativeRsg<RandomSequenceGenerator<MersenneTwisterUniformRng>,
                        InverseCumulativeNormal>>(proc, grid, gsg, /* brownianBridge */ false);
        return new MonteCarloModel.MultiPathGeneratorAdapterImpl(gen);
    }

    /**
     * Mirrors C++ {@code MCHestonHullWhiteEngine::pathPricer()} —
     * European plain-vanilla payoff applied to the asset trajectory
     * (sub-path 0 of the multi-path), discounted under the
     * forward-measure numeraire of the joint process.
     */
    protected PathPricer<MultiPath> pathPricer() {
        final OneAssetOption.ArgumentsImpl a = (OneAssetOption.ArgumentsImpl) arguments_;
        final Exercise exercise = a.exercise;
        QL.require(exercise.type() == Exercise.Type.European,
                "only european exercise is supported");

        final double exerciseTime = process_.time(exercise.lastDate());
        return new HestonHullWhitePathPricer(exerciseTime, a.payoff, process_);
    }

    /**
     * Mirrors C++ {@code MCHestonHullWhiteEngine::controlPathPricer()}
     * — same as {@link #pathPricer()} but applied against the
     * zero-cross-correlation CV process.
     */
    protected PathPricer<MultiPath> controlPathPricer() {
        final OneAssetOption.ArgumentsImpl a = (OneAssetOption.ArgumentsImpl) arguments_;
        final Exercise exercise = a.exercise;
        QL.require(exercise.type() == Exercise.Type.European,
                "only european exercise is supported");

        final double exerciseTime = process_.time(exercise.lastDate());
        return new HestonHullWhitePathPricer(exerciseTime, a.payoff, process_);
    }

    /**
     * Mirrors C++ {@code MCHestonHullWhiteEngine::controlPricingEngine()}
     * — wraps the analytic Heston / Hull-White semi-closed-form pricer
     * around the joint process's constituents (note Java
     * {@link AnalyticHestonHullWhiteEngine} defaults to integration
     * order 128, vs C++ 144 — see Phase 4a.5 A.5.2 note in
     * {@link AnalyticHestonEngine}).
     */
    protected PricingEngine controlPricingEngine() {
        final HestonProcess hestonProcess = process_.hestonProcess();
        final HullWhiteForwardProcess hullWhiteProcess = process_.hullWhiteProcess();
        final HestonModel hestonModel = new HestonModel(hestonProcess);
        final HullWhite hwModel = new HullWhite(
                hestonProcess.riskFreeRate(),
                hullWhiteProcess.a(),
                hullWhiteProcess.sigma());
        return new AnalyticHestonHullWhiteEngine(hestonModel, hestonProcess, hwModel);
    }


    //
    // PricingEngine
    //

    /**
     * Mirrors C++ {@code MCVanillaEngine::calculate()} + the inline
     * {@code MCHestonHullWhiteEngine::calculate()} clipping: drives the
     * embedded {@link McSimulation} with the engine's stored tolerance
     * / sample budget, then writes the mean (and error estimate) to
     * the results, clipping deep-OTM CV-induced negatives to zero.
     */
    @Override
    public void calculate() /* @ReadOnly */ {
        final OneAssetOption.ResultsImpl r = (OneAssetOption.ResultsImpl) results_;

        // Pre-compute the control-variate value (an option NPV against
        // the analytic engine) once, before driving the MC loop.
        final double cvValue;
        if (controlVariate_) {
            final OneAssetOption.ArgumentsImpl a = (OneAssetOption.ArgumentsImpl) arguments_;
            final EuropeanOption cvOption = new EuropeanOption(
                    (org.jquantlib.instruments.StrikedTypePayoff) a.payoff,
                    (org.jquantlib.exercise.EuropeanExercise) a.exercise);
            cvOption.setPricingEngine(controlPricingEngine());
            cvValue = cvOption.NPV();
        } else {
            cvValue = Double.NaN;
        }

        final double cvValueFinal = cvValue;
        this.simulation_ = new McSimulation<MultiPath>(antitheticVariate_, controlVariate_) {
            @Override protected PathPricer<MultiPath> pathPricer() {
                return MCHestonHullWhiteEngine.this.pathPricer();
            }
            @Override protected MonteCarloModel.PathGeneratorAdapter<MultiPath> pathGenerator() {
                return MCHestonHullWhiteEngine.this.pathGenerator();
            }
            @Override protected TimeGrid timeGrid() {
                return MCHestonHullWhiteEngine.this.timeGrid();
            }
            @Override protected PathPricer<MultiPath> controlPathPricer() {
                return MCHestonHullWhiteEngine.this.controlPathPricer();
            }
            @Override protected MonteCarloModel.PathGeneratorAdapter<MultiPath> controlPathGenerator() {
                return MCHestonHullWhiteEngine.this.controlPathGenerator();
            }
            @Override protected double controlVariateValue() {
                return cvValueFinal;
            }
        };
        this.simulation_.calculate(requiredTolerance_, requiredSamples_, maxSamples_);
        double value = this.simulation_.sampleAccumulator().mean();
        if (controlVariate_) {
            // control variate might lead to small negative option values
            // for deep OTM options
            value = Math.max(0.0, value);
        }
        r.value = value;
        r.errorEstimate = this.simulation_.errorEstimate();
    }


    //
    // PathPricer
    //

    /**
     * Java port of C++ {@code HestonHullWhitePathPricer}: evaluates the
     * payoff at the terminal asset price and discounts by the joint
     * process's numeraire factor at the exercise time.
     */
    public static final class HestonHullWhitePathPricer extends PathPricer<MultiPath> {

        private final double exerciseTime_;
        private final Payoff payoff_;
        private final HybridHestonHullWhiteProcess process_;

        public HestonHullWhitePathPricer(final double exerciseTime,
                                         final Payoff payoff,
                                         final HybridHestonHullWhiteProcess process) {
            this.exerciseTime_ = exerciseTime;
            this.payoff_ = payoff;
            this.process_ = process;
        }

        @Override
        public Double op(final MultiPath path) {
            QL.require(path.pathSize() > 0, "the path cannot be empty");

            final int n = path.assetNumber();
            final double[] s = new double[n];
            for (int j = 0; j < n; j++) {
                s[j] = path.get(j).back();
            }
            final Array states = new Array(s);
            final double df = 1.0 / process_.numeraire(exerciseTime_, states);
            return payoff_.get(states.get(0)) * df;
        }
    }
}
