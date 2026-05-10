/*
 Copyright (C) 2026 Jose Moya

 This source code is release under the BSD License.

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

/*
 Copyright (C) 2008 Master IMAFA - Polytech'Nice Sophia - Université de Nice Sophia Antipolis

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <http://quantlib.org/license.shtml>.
*/

package org.jquantlib.pricingengines.cliquet;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.CliquetOption;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.instruments.PercentageStrikePayoff;
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
 * Pricing engine for performance (cliquet) options using Monte Carlo simulation.
 * <p>
 * Mirrors C++ QuantLib v1.42.1 {@code MCPerformanceEngine} in
 * {@code ql/pricingengines/cliquet/mcperformanceengine.{hpp,cpp}}.
 * Specialised for {@code RNG = PseudoRandom} (Mersenne-Twister +
 * InverseCumulativeNormal) following the same convention as
 * {@link org.jquantlib.pricingengines.vanilla.MCEuropeanEngine}.
 *
 * <p>Cross-validates against {@link AnalyticPerformanceEngine}.
 *
 * @author Jose Moya
 */
public class MCPerformanceEngine extends OneAssetOption.EngineImpl {

    //
    // protected fields
    //

    protected final GeneralizedBlackScholesProcess process_;
    protected final int requiredSamples_;
    protected final int maxSamples_;
    protected final double requiredTolerance_;
    protected final boolean brownianBridge_;
    protected final boolean antitheticVariate_;
    protected final long seed_;

    /** Lazily-built delegate that owns the {@link MonteCarloModel}. */
    protected McSimulation<Path> simulation_;


    //
    // constructors
    //

    public MCPerformanceEngine(final GeneralizedBlackScholesProcess process,
                               final boolean brownianBridge,
                               final boolean antitheticVariate,
                               final int requiredSamples,
                               final double requiredTolerance,
                               final int maxSamples,
                               final long seed) {
        super(new CliquetOption.ArgumentsImpl(), new OneAssetOption.ResultsImpl());
        this.process_ = process;
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
     * Mirrors C++ {@code TimeGrid timeGrid()}: builds a non-uniform grid
     * from the option's reset dates plus the exercise (terminal) date.
     */
    protected TimeGrid timeGrid() {
        final CliquetOption.ArgumentsImpl a = (CliquetOption.ArgumentsImpl) arguments_;
        final List<Double> fixingTimes = new ArrayList<Double>(a.resetDates.size() + 1);
        for (int i = 0; i < a.resetDates.size(); i++) {
            fixingTimes.add(process_.time(a.resetDates.get(i)));
        }
        fixingTimes.add(process_.time(a.exercise.lastDate()));
        return new TimeGrid(fixingTimes);
    }

    protected MonteCarloModel.PathGeneratorAdapter<Path> pathGenerator() {
        final TimeGrid grid = timeGrid();
        final int dimensions = process_.factors() * (grid.size() - 1);
        final RandomSequenceGenerator<MersenneTwisterUniformRng> uniformRsg =
                new RandomSequenceGenerator<MersenneTwisterUniformRng>(
                        MersenneTwisterUniformRng.class, dimensions, seed_);
        final InverseCumulativeRsg<RandomSequenceGenerator<MersenneTwisterUniformRng>,
                InverseCumulativeNormal> gsg =
                new InverseCumulativeRsg<RandomSequenceGenerator<MersenneTwisterUniformRng>,
                        InverseCumulativeNormal>(uniformRsg, new InverseCumulativeNormal());
        final PathGenerator<InverseCumulativeRsg<RandomSequenceGenerator<MersenneTwisterUniformRng>,
                InverseCumulativeNormal>> gen =
                new PathGenerator<InverseCumulativeRsg<RandomSequenceGenerator<MersenneTwisterUniformRng>,
                        InverseCumulativeNormal>>(process_, grid, gsg, brownianBridge_);
        return new MonteCarloModel.PathGeneratorAdapterImpl(gen);
    }

    protected PathPricer<Path> pathPricer() {
        final CliquetOption.ArgumentsImpl a = (CliquetOption.ArgumentsImpl) arguments_;
        QL.require(a.payoff instanceof PercentageStrikePayoff, "non-percentage payoff given");
        final PercentageStrikePayoff payoff = (PercentageStrikePayoff) a.payoff;

        final Exercise exercise = a.exercise;
        QL.require(exercise instanceof EuropeanExercise, "wrong exercise given");

        final List<Date> resetDates = a.resetDates;
        final double[] discounts = new double[resetDates.size() + 1];
        for (int k = 0; k < resetDates.size(); k++) {
            discounts[k] = process_.riskFreeRate().currentLink().discount(resetDates.get(k));
        }
        discounts[resetDates.size()] =
                process_.riskFreeRate().currentLink().discount(exercise.lastDate());

        return new PerformanceOptionPathPricer(payoff.optionType(), payoff.strike(), discounts);
    }


    //
    // PricingEngine
    //

    @Override
    public void calculate() /* @ReadOnly */ {
        final OneAssetOption.ResultsImpl r = (OneAssetOption.ResultsImpl) results_;

        this.simulation_ = new McSimulation<Path>(antitheticVariate_, /* controlVariate */ false) {
            @Override protected PathPricer<Path> pathPricer() {
                return MCPerformanceEngine.this.pathPricer();
            }
            @Override protected MonteCarloModel.PathGeneratorAdapter<Path> pathGenerator() {
                return MCPerformanceEngine.this.pathGenerator();
            }
            @Override protected TimeGrid timeGrid() {
                return MCPerformanceEngine.this.timeGrid();
            }
        };
        this.simulation_.calculate(requiredTolerance_, requiredSamples_, maxSamples_);
        r.value = this.simulation_.sampleAccumulator().mean();
        r.errorEstimate = this.simulation_.errorEstimate();
    }
}
