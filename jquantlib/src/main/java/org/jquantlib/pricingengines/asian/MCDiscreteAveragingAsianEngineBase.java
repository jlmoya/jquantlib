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
 Copyright (C) 2000, 2001, 2002, 2003 RiskMap srl
 Copyright (C) 2003, 2004 Ferdinando Ametrano
 Copyright (C) 2007, 2008 StatPro Italia srl
*/

package org.jquantlib.pricingengines.asian;

import org.jquantlib.QL;
import org.jquantlib.instruments.DiscreteAveragingAsianOption;
import org.jquantlib.pricingengines.McSimulation;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.processes.StochasticProcess;
import org.jquantlib.time.Date;
import org.jquantlib.time.TimeGrid;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for Monte Carlo discrete-averaging Asian engines.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/pricingengines/asian/mcdiscreteasianenginebase.hpp} {@code MCDiscreteAveragingAsianEngineBase} (Phase
 * 5e.5b-CFC-d-114).
 *
 * @param <PathType> {@code Path} for single-variate MC, {@code MultiPath} for multi-variate (Heston) MC.
 * @author JQuantLib
 */
public abstract class MCDiscreteAveragingAsianEngineBase< PathType > extends DiscreteAveragingAsianOption.EngineImpl {

    protected final StochasticProcess process_;
    protected final int requiredSamples_;
    protected final int maxSamples_;
    protected final int timeSteps_;
    protected final int timeStepsPerYear_;
    protected final double requiredTolerance_;
    protected final boolean brownianBridge_;
    protected final boolean antitheticVariate_;
    protected final boolean controlVariate_;
    protected final long seed_;
    protected final boolean includeExerciseDate_;

    protected McSimulation< PathType > simulation_;

    protected MCDiscreteAveragingAsianEngineBase(final StochasticProcess process, final boolean brownianBridge,
            final boolean antitheticVariate, final boolean controlVariate, final int requiredSamples,
            final double requiredTolerance, final int maxSamples, final long seed, final int timeSteps,
            final int timeStepsPerYear, final boolean includeExerciseDate) {
        super();
        QL.require(process != null, "null stochastic process");
        this.process_ = process;
        this.brownianBridge_ = brownianBridge;
        this.antitheticVariate_ = antitheticVariate;
        this.controlVariate_ = controlVariate;
        this.requiredSamples_ = requiredSamples;
        this.requiredTolerance_ = requiredTolerance;
        this.maxSamples_ = maxSamples;
        this.timeSteps_ = timeSteps;
        this.timeStepsPerYear_ = timeStepsPerYear;
        this.seed_ = seed;
        this.includeExerciseDate_ = includeExerciseDate;
        this.process_.addObserver(this);
    }

    /** Mirrors C++ {@code MCDiscreteAveragingAsianEngineBase::timeGrid()}. */
    protected TimeGrid timeGrid() {
        final DiscreteAveragingAsianOption.ArgumentsImpl a = (DiscreteAveragingAsianOption.ArgumentsImpl) arguments_;
        final List< Double > fixingTimes = new ArrayList< Double >();
        for ( int i = 0; i < a.fixingDates.size(); i++ ) {
            final double t = process_.time(a.fixingDates.get(i));
            if ( t >= 0 ) {
                fixingTimes.add(Double.valueOf(t));
            }
        }
        if ( fixingTimes.isEmpty() || (fixingTimes.size() == 1 && fixingTimes.get(0) == 0.0) ) {
            throw new PastFixingsOnlyException();
        }

        final Date lastExerciseDate = a.exercise.lastDate();
        final double t = process_.time(lastExerciseDate);

        if ( includeExerciseDate_ && t > fixingTimes.get(fixingTimes.size() - 1) ) {
            fixingTimes.add(Double.valueOf(t));
        }

        if ( timeSteps_ != McSimulation.NULL_SAMPLES ) {
            return new TimeGrid(fixingTimes, timeSteps_);
        } else if ( timeStepsPerYear_ != McSimulation.NULL_SAMPLES ) {
            return new TimeGrid(fixingTimes, (int) (timeStepsPerYear_ * t));
        }
        // Equivalent to C++ `TimeGrid(begin, end)` — mandatory points only.
        return new TimeGrid(fixingTimes, 0);
    }

    protected abstract org.jquantlib.methods.montecarlo.PathPricer< PathType > pathPricer();

    protected abstract org.jquantlib.methods.montecarlo.MonteCarloModel.PathGeneratorAdapter< PathType > pathGenerator();

    protected org.jquantlib.methods.montecarlo.PathPricer< PathType > controlPathPricer() {
        return null;
    }

    protected PricingEngine controlPricingEngine() {
        return null;
    }

    @Override
    public void calculate() /* @ReadOnly */ {
        final DiscreteAveragingAsianOption.ResultsImpl r = (DiscreteAveragingAsianOption.ResultsImpl) results_;

        this.simulation_ = new McSimulation< PathType >(antitheticVariate_, controlVariate_) {
            @Override
            protected org.jquantlib.methods.montecarlo.PathPricer< PathType > pathPricer() {
                return MCDiscreteAveragingAsianEngineBase.this.pathPricer();
            }

            @Override
            protected org.jquantlib.methods.montecarlo.MonteCarloModel.PathGeneratorAdapter< PathType > pathGenerator() {
                return MCDiscreteAveragingAsianEngineBase.this.pathGenerator();
            }

            @Override
            protected TimeGrid timeGrid() {
                return MCDiscreteAveragingAsianEngineBase.this.timeGrid();
            }

            @Override
            protected org.jquantlib.methods.montecarlo.PathPricer< PathType > controlPathPricer() {
                return MCDiscreteAveragingAsianEngineBase.this.controlPathPricer();
            }

            @Override
            protected double controlVariateValue() {
                final PricingEngine controlPE = MCDiscreteAveragingAsianEngineBase.this.controlPricingEngine();
                if ( controlPE == null ) {
                    return Double.NaN;
                }
                final DiscreteAveragingAsianOption.ArgumentsImpl controlArgs = (DiscreteAveragingAsianOption.ArgumentsImpl) controlPE.getArguments();
                final DiscreteAveragingAsianOption.ArgumentsImpl srcArgs = (DiscreteAveragingAsianOption.ArgumentsImpl) MCDiscreteAveragingAsianEngineBase.this.arguments_;
                controlArgs.payoff = srcArgs.payoff;
                controlArgs.exercise = srcArgs.exercise;
                controlArgs.averageType = srcArgs.averageType;
                controlArgs.runningAccumulator = srcArgs.runningAccumulator;
                controlArgs.pastFixings = srcArgs.pastFixings;
                controlArgs.fixingDates = srcArgs.fixingDates;
                controlPE.calculate();
                final DiscreteAveragingAsianOption.ResultsImpl controlRes = (DiscreteAveragingAsianOption.ResultsImpl) controlPE.getResults();
                return controlRes.value;
            }
        };
        this.simulation_.calculate(requiredTolerance_, requiredSamples_, maxSamples_);
        r.value = this.simulation_.sampleAccumulator().mean();
        if ( controlVariate_ ) {
            r.value = Math.max(0.0, r.value);
        }
        r.errorEstimate = this.simulation_.errorEstimate();
    }

    /** Mirrors C++ {@code detail::PastFixingsOnly}. */
    public static class PastFixingsOnlyException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public PastFixingsOnlyException() {
            super("all fixings are in the past");
        }
    }
}
