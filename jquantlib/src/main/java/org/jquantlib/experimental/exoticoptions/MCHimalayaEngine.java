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
 Copyright (C) 2008 Master IMAFA - Polytech'Nice Sophia - Universite de Nice Sophia Antipolis
*/

package org.jquantlib.experimental.exoticoptions;

import org.jquantlib.QL;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.randomnumbers.InverseCumulativeRsg;
import org.jquantlib.math.randomnumbers.MersenneTwisterUniformRng;
import org.jquantlib.math.randomnumbers.RandomSequenceGenerator;
import org.jquantlib.methods.montecarlo.MonteCarloModel;
import org.jquantlib.methods.montecarlo.MultiPath;
import org.jquantlib.methods.montecarlo.MultiPathGenerator;
import org.jquantlib.methods.montecarlo.PathPricer;
import org.jquantlib.model.shortrate.StochasticProcessArray;
import org.jquantlib.pricingengines.McSimulation;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.processes.StochasticProcess1D;
import org.jquantlib.time.Date;
import org.jquantlib.time.TimeGrid;

import java.util.ArrayList;
import java.util.List;

/**
 * Monte Carlo engine for Himalaya options.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/experimental/exoticoptions/mchimalayaengine.{hpp,cpp}} (Phase 4i.5 WI-3).
 *
 * <p>Specialised for {@code RNG = PseudoRandom}; quasi-random variants are
 * deferred to Phase 4i.5b. The engine extends {@link HimalayaOption.EngineImpl} (single-inheritance) and embeds an
 * {@link McSimulation McSimulation&lt;MultiPath&gt;} delegate via composition, mirroring
 * {@code MCEuropeanBasketEngine}.
 *
 * @author JQuantLib
 */
public class MCHimalayaEngine extends HimalayaOption.EngineImpl {

    //
    // protected fields
    //

    protected final StochasticProcessArray processes_;
    protected final int requiredSamples_;
    protected final int maxSamples_;
    protected final double requiredTolerance_;
    protected final boolean brownianBridge_;
    protected final boolean antitheticVariate_;
    protected final long seed_;

    /** Lazily-built delegate that owns the {@link MonteCarloModel}. */
    protected McSimulation< MultiPath > simulation_;

    //
    // constructors
    //

    public MCHimalayaEngine(final StochasticProcessArray processes, final boolean brownianBridge,
            final boolean antitheticVariate, final int requiredSamples, final double requiredTolerance,
            final int maxSamples, final long seed) {
        super();
        this.processes_ = processes;
        this.brownianBridge_ = brownianBridge;
        this.antitheticVariate_ = antitheticVariate;
        this.requiredSamples_ = requiredSamples;
        this.maxSamples_ = maxSamples;
        this.requiredTolerance_ = requiredTolerance;
        this.seed_ = seed;
        this.processes_.addObserver(this);
    }

    //
    // McSimulation-shaped helpers
    //

    /**
     * Mirrors C++ {@code TimeGrid timeGrid()}: builds a non-uniform time grid from the option's fixing dates.
     */
    protected TimeGrid timeGrid() {
        final HimalayaOption.ArgumentsImpl a = arguments_;
        final List< Double > fixingTimes = new ArrayList< Double >(a.fixingDates.size());
        double prev = -1.0;
        for ( int i = 0; i < a.fixingDates.size(); i++ ) {
            final Date d = a.fixingDates.get(i);
            final double t = processes_.time(d);
            QL.require(t >= 0.0, "seasoned options are not handled");
            if ( i > 0 ) {
                QL.require(t > prev, "fixing dates not sorted");
            }
            fixingTimes.add(t);
            prev = t;
        }
        return new TimeGrid(fixingTimes);
    }

    protected MonteCarloModel.PathGeneratorAdapter< MultiPath > pathGenerator() {
        final int numAssets = processes_.size();
        final TimeGrid grid = timeGrid();
        final int dimensions = numAssets * (grid.size() - 1);
        final RandomSequenceGenerator< MersenneTwisterUniformRng > uniformRsg = new RandomSequenceGenerator< MersenneTwisterUniformRng >(
                MersenneTwisterUniformRng.class, dimensions, seed_);
        final InverseCumulativeRsg< RandomSequenceGenerator< MersenneTwisterUniformRng >, InverseCumulativeNormal > gsg = new InverseCumulativeRsg< RandomSequenceGenerator< MersenneTwisterUniformRng >, InverseCumulativeNormal >(
                uniformRsg, new InverseCumulativeNormal());
        final MultiPathGenerator< InverseCumulativeRsg< RandomSequenceGenerator< MersenneTwisterUniformRng >, InverseCumulativeNormal > > gen = new MultiPathGenerator< InverseCumulativeRsg< RandomSequenceGenerator< MersenneTwisterUniformRng >, InverseCumulativeNormal > >(
                processes_, grid, gsg, brownianBridge_);
        return new MonteCarloModel.MultiPathGeneratorAdapterImpl(gen);
    }

    protected PathPricer< MultiPath > pathPricer() {
        final HimalayaOption.ArgumentsImpl a = arguments_;
        final StochasticProcess1D first = processes_.process(0);
        if ( !(first instanceof GeneralizedBlackScholesProcess) ) {
            throw new RuntimeException("Black-Scholes process required");
        }
        final GeneralizedBlackScholesProcess process = (GeneralizedBlackScholesProcess) first;
        final double discount = process.riskFreeRate().currentLink().discount(a.exercise.lastDate());
        return new HimalayaMultiPathPricer(a.payoff, discount);
    }

    //
    // PricingEngine
    //

    @Override
    public void calculate() /* @ReadOnly */ {
        final HimalayaOption.ResultsImpl r = results_;

        this.simulation_ = new McSimulation< MultiPath >(antitheticVariate_, /* controlVariate */ false) {
            @Override
            protected PathPricer< MultiPath > pathPricer() {
                return MCHimalayaEngine.this.pathPricer();
            }

            @Override
            protected MonteCarloModel.PathGeneratorAdapter< MultiPath > pathGenerator() {
                return MCHimalayaEngine.this.pathGenerator();
            }

            @Override
            protected TimeGrid timeGrid() {
                return MCHimalayaEngine.this.timeGrid();
            }
        };
        this.simulation_.calculate(requiredTolerance_, requiredSamples_, maxSamples_);
        r.value = this.simulation_.sampleAccumulator().mean();
        r.errorEstimate = this.simulation_.errorEstimate();
    }
}
