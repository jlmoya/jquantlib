/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2006 Banca Profilo S.p.A.
 Copyright (C) 2006 StatPro Italia srl
*/

package org.jquantlib.pricingengines.capfloor;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.CapFloor;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.randomnumbers.InverseCumulativeRsg;
import org.jquantlib.math.randomnumbers.MersenneTwisterUniformRng;
import org.jquantlib.math.randomnumbers.RandomSequenceGenerator;
import org.jquantlib.methods.montecarlo.MonteCarloModel;
import org.jquantlib.methods.montecarlo.Path;
import org.jquantlib.methods.montecarlo.PathGenerator;
import org.jquantlib.methods.montecarlo.PathPricer;
import org.jquantlib.model.shortrate.onefactormodels.HullWhite;
import org.jquantlib.pricingengines.McSimulation;
import org.jquantlib.processes.HullWhiteForwardProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;
import org.jquantlib.time.TimeGrid;

import java.util.ArrayList;
import java.util.List;

/**
 * Monte Carlo Hull-White pricing engine for cap/floors.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/pricingengines/capfloor/mchullwhiteengine.hpp} (Phase 2 L3-D). Specialised to {@code RNG = PseudoRandom}. Uses
 * the {@code Tb}-forward measure for which the short-rate diffuses under {@link HullWhiteForwardProcess}; the time
 * grid is the union of the future fixing times plus the terminal end date.
 */
public class MCHullWhiteCapFloorEngine extends CapFloor.Engine {

    private final HullWhite model_;
    private final boolean brownianBridge_;
    private final boolean antitheticVariate_;
    private final int requiredSamples_;
    private final int maxSamples_;
    private final double requiredTolerance_;
    private final long seed_;

    private McSimulation< Path > simulation_;

    public MCHullWhiteCapFloorEngine(final HullWhite model, final boolean brownianBridge,
            final boolean antitheticVariate, final int requiredSamples, final double requiredTolerance,
            final int maxSamples, final long seed) {
        super();
        QL.require(model != null, "null Hull-White model");
        this.model_ = model;
        this.brownianBridge_ = brownianBridge;
        this.antitheticVariate_ = antitheticVariate;
        this.requiredSamples_ = requiredSamples;
        this.maxSamples_ = maxSamples;
        this.requiredTolerance_ = requiredTolerance;
        this.seed_ = seed;
        this.model_.addObserver(this);
    }

    protected TimeGrid timeGrid() {
        final CapFloor.ArgumentsImpl args = (CapFloor.ArgumentsImpl) arguments_;
        final Date referenceDate = model_.termStructure().currentLink().referenceDate();
        final DayCounter dayCounter = model_.termStructure().currentLink().dayCounter();

        final List< Double > times = new ArrayList<>();
        for ( int i = 0; i < args.fixingDates.length; i++ ) {
            if ( args.fixingDates[i].gt(referenceDate) ) {
                times.add(dayCounter.yearFraction(referenceDate, args.fixingDates[i]));
            }
        }
        // ...and maturity
        times.add(dayCounter.yearFraction(referenceDate, args.endDates[args.endDates.length - 1]));
        return new TimeGrid(times, /* steps */ 0);
    }

    protected MonteCarloModel.PathGeneratorAdapter< Path > pathGenerator() {
        final CapFloor.ArgumentsImpl args = (CapFloor.ArgumentsImpl) arguments_;
        final Handle< YieldTermStructure > curve = model_.termStructure();
        final Date referenceDate = curve.currentLink().referenceDate();
        final DayCounter dayCounter = curve.currentLink().dayCounter();
        final double forwardMeasureTime = dayCounter.yearFraction(referenceDate,
                args.endDates[args.endDates.length - 1]);

        final Array parameters = model_.params();
        final double a = parameters.get(0);
        final double sigma = parameters.get(1);
        final HullWhiteForwardProcess process = new HullWhiteForwardProcess(curve, a, sigma);
        process.setForwardMeasureTime(forwardMeasureTime);

        final TimeGrid grid = timeGrid();
        final int dimensions = process.factors() * (grid.size() - 1);
        final RandomSequenceGenerator< MersenneTwisterUniformRng > uniformRsg = new RandomSequenceGenerator< MersenneTwisterUniformRng >(
                MersenneTwisterUniformRng.class, dimensions, seed_);
        final InverseCumulativeRsg< RandomSequenceGenerator< MersenneTwisterUniformRng >, InverseCumulativeNormal > gsg = new InverseCumulativeRsg< RandomSequenceGenerator< MersenneTwisterUniformRng >, InverseCumulativeNormal >(
                uniformRsg, new InverseCumulativeNormal());
        final PathGenerator< InverseCumulativeRsg< RandomSequenceGenerator< MersenneTwisterUniformRng >, InverseCumulativeNormal > > gen = new PathGenerator< InverseCumulativeRsg< RandomSequenceGenerator< MersenneTwisterUniformRng >, InverseCumulativeNormal > >(
                process, grid, gsg, brownianBridge_);
        return new MonteCarloModel.PathGeneratorAdapterImpl(gen);
    }

    protected PathPricer< Path > pathPricer() {
        final CapFloor.ArgumentsImpl args = (CapFloor.ArgumentsImpl) arguments_;
        final Date referenceDate = model_.termStructure().currentLink().referenceDate();
        final DayCounter dayCounter = model_.termStructure().currentLink().dayCounter();
        final double forwardMeasureTime = dayCounter.yearFraction(referenceDate,
                args.endDates[args.endDates.length - 1]);
        return new HullWhiteCapFloorPricer(args, model_, forwardMeasureTime);
    }

    @Override
    public void calculate() /* @ReadOnly */ {
        final CapFloor.ResultsImpl r = (CapFloor.ResultsImpl) results_;

        this.simulation_ = new McSimulation< Path >(antitheticVariate_, /* controlVariate */ false) {
            @Override
            protected PathPricer< Path > pathPricer() {
                return MCHullWhiteCapFloorEngine.this.pathPricer();
            }

            @Override
            protected MonteCarloModel.PathGeneratorAdapter< Path > pathGenerator() {
                return MCHullWhiteCapFloorEngine.this.pathGenerator();
            }

            @Override
            protected TimeGrid timeGrid() {
                return MCHullWhiteCapFloorEngine.this.timeGrid();
            }
        };
        this.simulation_.calculate(requiredTolerance_, requiredSamples_, maxSamples_);
        r.value = this.simulation_.sampleAccumulator().mean();
        r.errorEstimate = this.simulation_.errorEstimate();
    }
}
