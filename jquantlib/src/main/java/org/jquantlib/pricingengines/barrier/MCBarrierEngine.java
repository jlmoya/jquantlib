/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2003 Neil Firth
 Copyright (C) 2003 Ferdinando Ametrano
 Copyright (C) 2003, 2004, 2005 StatPro Italia srl
*/

package org.jquantlib.pricingengines.barrier;

import org.jquantlib.QL;
import org.jquantlib.instruments.BarrierOption;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.randomnumbers.InverseCumulativeRsg;
import org.jquantlib.math.randomnumbers.MersenneTwisterUniformRng;
import org.jquantlib.math.randomnumbers.RandomSequenceGenerator;
import org.jquantlib.methods.montecarlo.MonteCarloModel;
import org.jquantlib.methods.montecarlo.Path;
import org.jquantlib.methods.montecarlo.PathGenerator;
import org.jquantlib.methods.montecarlo.PathPricer;
import org.jquantlib.pricingengines.McSimulation;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.time.Date;
import org.jquantlib.time.TimeGrid;

/**
 * Pricing engine for barrier options using Monte Carlo simulation.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/pricingengines/barrier/mcbarrierengine.{hpp,cpp}} (Phase 2 L3-D). Specialised to {@code RNG = PseudoRandom}
 * (Mersenne-Twister + InverseCumulativeNormal); the {@code Statistics} accumulator and quasi-random variants are
 * deferred (matches the existing MC engine specialisation across the Java port).
 *
 * <p>Uses {@link BarrierPathPricer} when {@code isBiased == false} (default — Brownian-bridge corrected per
 * Beaglehole-Dybvig-Zhou 1997 / El Babsiri-Noel 1998) or {@link BiasedBarrierPathPricer} when biased monitoring is
 * requested. The corrected pricer uses a separate uniform sequence with fixed seed {@code 5} (matches C++).
 */
public class MCBarrierEngine extends BarrierOption.EngineImpl {

    private final GeneralizedBlackScholesProcess process_;
    private final int timeSteps_;
    private final int timeStepsPerYear_;
    private final boolean brownianBridge_;
    private final boolean antitheticVariate_;
    private final int requiredSamples_;
    private final int maxSamples_;
    private final double requiredTolerance_;
    private final boolean isBiased_;
    private final long seed_;

    private McSimulation< Path > simulation_;

    public MCBarrierEngine(final GeneralizedBlackScholesProcess process, final int timeSteps,
            final int timeStepsPerYear, final boolean brownianBridge, final boolean antitheticVariate,
            final int requiredSamples, final double requiredTolerance, final int maxSamples, final boolean isBiased,
            final long seed) {
        super();
        QL.require(process != null, "null GBS process");
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
        this.isBiased_ = isBiased;
        this.seed_ = seed;
        this.process_.addObserver(this);
    }

    protected TimeGrid timeGrid() {
        final BarrierOption.ArgumentsImpl args = (BarrierOption.ArgumentsImpl) arguments_;
        final Date lastExerciseDate = args.exercise.lastDate();
        final double t = process_.time(lastExerciseDate);
        if ( timeSteps_ != McSimulation.NULL_SAMPLES ) {
            return new TimeGrid(t, timeSteps_);
        } else if ( timeStepsPerYear_ != McSimulation.NULL_SAMPLES ) {
            final int steps = (int) (timeStepsPerYear_ * t);
            return new TimeGrid(t, Math.max(steps, 1));
        } else {
            throw new RuntimeException("time steps not specified");
        }
    }

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

    protected PathPricer< Path > pathPricer() {
        final BarrierOption.ArgumentsImpl args = (BarrierOption.ArgumentsImpl) arguments_;
        final PlainVanillaPayoff payoff;
        try {
            payoff = (PlainVanillaPayoff) args.payoff;
        } catch ( final ClassCastException e ) {
            throw new RuntimeException("non-plain payoff given");
        }
        QL.require(payoff != null, "non-plain payoff given");

        final TimeGrid grid = timeGrid();
        final double[] discounts = new double[grid.size()];
        for ( int i = 0; i < grid.size(); i++ ) {
            discounts[i] = process_.riskFreeRate().currentLink().discount(grid.at(i));
        }

        if ( isBiased_ ) {
            return new BiasedBarrierPathPricer(args.barrierType, args.barrier, args.rebate, payoff.optionType(),
                    payoff.strike(), discounts);
        }
        // Corrected pricer: separate uniform sequence (fixed seed 5 per C++).
        final RandomSequenceGenerator< MersenneTwisterUniformRng > sequenceGen = new RandomSequenceGenerator< MersenneTwisterUniformRng >(
                MersenneTwisterUniformRng.class, grid.size() - 1, 5L);
        return new BarrierPathPricer(args.barrierType, args.barrier, args.rebate, payoff.optionType(), payoff.strike(),
                discounts, process_, sequenceGen);
    }

    @Override
    public void calculate() /* @ReadOnly */ {
        final BarrierOption.ResultsImpl r = (BarrierOption.ResultsImpl) results_;
        final double spot = process_.x0();
        QL.require(spot > 0.0, "negative or null underlying given");
        QL.require(!triggered(spot), "barrier touched");

        this.simulation_ = new McSimulation< Path >(antitheticVariate_, /* controlVariate */ false) {
            @Override
            protected PathPricer< Path > pathPricer() {
                return MCBarrierEngine.this.pathPricer();
            }

            @Override
            protected MonteCarloModel.PathGeneratorAdapter< Path > pathGenerator() {
                return MCBarrierEngine.this.pathGenerator();
            }

            @Override
            protected TimeGrid timeGrid() {
                return MCBarrierEngine.this.timeGrid();
            }
        };
        this.simulation_.calculate(requiredTolerance_, requiredSamples_, maxSamples_);
        r.value = this.simulation_.sampleAccumulator().mean();
        r.errorEstimate = this.simulation_.errorEstimate();
    }
}
