/*
 Copyright (C) 2026 JQuantLib migration

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
 Copyright (C) 2015 Thema Consulting SA
*/

package org.jquantlib.experimental.barrieroption;

import java.lang.reflect.Constructor;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.DiscretizedAsset;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.lattices.BlackScholesLattice;
import org.jquantlib.methods.lattices.Tree;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.processes.StochasticProcess1D;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.BlackConstantVol;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.TimeGrid;

/**
 * Pricing engine for double-barrier options using binomial trees.
 * <p>
 * Mirrors {@code QuantLib::BinomialDoubleBarrierEngine<T,D>} from
 * {@code ql/experimental/barrieroption/binomialdoublebarrierengine.hpp} (v1.42.1).
 *
 * <p>The C++ engine is a class template parametrised on tree type {@code T}
 * and discretized helper {@code D} (defaulting to
 * {@link DiscretizedDoubleBarrierOption}). Java cannot express that with
 * generics without losing the constructor signatures, so we use the same
 * reflection-based pattern as {@link org.jquantlib.pricingengines.vanilla.BinomialVanillaEngine}:
 * pass the {@code Class} objects for both tree and discretized helper.
 *
 * <p>Greeks (delta/gamma/theta) are estimated from the third-last/second-last
 * tree slices following Hull, "Options, Futures and other Derivatives", 6th ed.
 *
 * @author JQuantLib migration
 */
public class BinomialDoubleBarrierEngine<T extends Tree, D extends DiscretizedAsset>
        extends DoubleBarrierOption.EngineImpl {

    private final Class<? extends Tree> treeClass;
    private final Class<? extends DiscretizedAsset> discretizedClass;
    private final GeneralizedBlackScholesProcess process_;
    private final int timeSteps_;
    private final DoubleBarrierOption.ArgumentsImpl a;
    private final OneAssetOption.ResultsImpl r;


    /**
     * Constructs a binomial double-barrier engine.
     *
     * @param treeClass         binomial tree implementation (e.g. {@code CoxRossRubinstein.class})
     * @param discretizedClass  discretized-helper class
     *                          ({@link DiscretizedDoubleBarrierOption} or
     *                          {@link DiscretizedDermanKaniDoubleBarrierOption})
     * @param process           Black-Scholes process
     * @param timeSteps         number of binomial steps (must be positive)
     */
    public BinomialDoubleBarrierEngine(
            final Class<? extends Tree> treeClass,
            final Class<? extends DiscretizedAsset> discretizedClass,
            final GeneralizedBlackScholesProcess process,
            final int timeSteps) {
        QL.require(timeSteps > 0,
                "timeSteps must be positive, " + timeSteps + " not allowed");
        this.treeClass = treeClass;
        this.discretizedClass = discretizedClass;
        this.process_ = process;
        this.timeSteps_ = timeSteps;
        this.a = (DoubleBarrierOption.ArgumentsImpl) arguments_;
        this.r = (OneAssetOption.ResultsImpl) results_;
        this.process_.addObserver(this);
    }

    /**
     * Convenience constructor defaulting to {@link DiscretizedDoubleBarrierOption}.
     */
    public BinomialDoubleBarrierEngine(
            final Class<? extends Tree> treeClass,
            final GeneralizedBlackScholesProcess process,
            final int timeSteps) {
        this(treeClass, DiscretizedDoubleBarrierOption.class, process, timeSteps);
    }


    private Tree getTreeInstance(
            final StochasticProcess1D bs,
            final double maturity,
            final int timeSteps,
            final double strike) {
        try {
            // Phase 4a A.1: all extended-binomial trees use the (process, T, steps, strike) signature.
            final Constructor<?> c = treeClass.getConstructor(
                    StochasticProcess1D.class, double.class, int.class, double.class);
            return (Tree) c.newInstance(bs, maturity, timeSteps, strike);
        } catch (final Exception e) {
            throw new LibraryException(e);
        }
    }

    private DiscretizedAsset getDiscretizedInstance(
            final DoubleBarrierOption.Arguments arguments,
            final GeneralizedBlackScholesProcess process,
            final TimeGrid grid) {
        try {
            final Constructor<?> c = discretizedClass.getConstructor(
                    DoubleBarrierOption.Arguments.class,
                    org.jquantlib.processes.StochasticProcess.class,
                    TimeGrid.class);
            return (DiscretizedAsset) c.newInstance(arguments, process, grid);
        } catch (final Exception e) {
            throw new LibraryException(e);
        }
    }


    @Override
    public void calculate() {
        final DayCounter rfdc = process_.riskFreeRate().currentLink().dayCounter();
        final DayCounter divdc = process_.dividendYield().currentLink().dayCounter();
        final DayCounter voldc = process_.blackVolatility().currentLink().dayCounter();
        final Calendar volcal = process_.blackVolatility().currentLink().calendar();

        final double s0 = process_.stateVariable().currentLink().value();
        QL.require(s0 > 0.0, "negative or null underlying given");
        final double v = process_.blackVolatility().currentLink().blackVol(a.exercise.lastDate(), s0);
        final Date maturityDate = a.exercise.lastDate();
        final double rRate = process_.riskFreeRate().currentLink().zeroRate(
                maturityDate, rfdc, Compounding.Continuous, Frequency.NoFrequency).rate();
        final double qRate = process_.dividendYield().currentLink().zeroRate(
                maturityDate, divdc, Compounding.Continuous, Frequency.NoFrequency).rate();
        final Date referenceDate = process_.riskFreeRate().currentLink().referenceDate();

        // binomial trees with constant coefficient
        final Handle<YieldTermStructure> flatRiskFree =
                new Handle<YieldTermStructure>(new FlatForward(referenceDate, rRate, rfdc));
        final Handle<YieldTermStructure> flatDividends =
                new Handle<YieldTermStructure>(new FlatForward(referenceDate, qRate, divdc));
        final Handle<BlackVolTermStructure> flatVol =
                new Handle<BlackVolTermStructure>(new BlackConstantVol(referenceDate, volcal, v, voldc));

        QL.require(a.payoff instanceof StrikedTypePayoff, "non-striked payoff given");
        final StrikedTypePayoff payoff = (StrikedTypePayoff) a.payoff;

        final double maturity = rfdc.yearFraction(referenceDate, maturityDate);

        final StochasticProcess1D bs = new GeneralizedBlackScholesProcess(
                process_.stateVariable(), flatDividends, flatRiskFree, flatVol);

        final TimeGrid grid = new TimeGrid(maturity, timeSteps_);

        final Tree tree = getTreeInstance(bs, maturity, timeSteps_, payoff.strike());
        final BlackScholesLattice<Tree> lattice =
                new BlackScholesLattice<Tree>(tree, rRate, maturity, timeSteps_);

        final DiscretizedAsset option = getDiscretizedInstance(a, process_, grid);
        option.initialize(lattice, maturity);

        // Partial derivatives calculated from various points in the binomial tree
        // (Hull, "Options, Futures and other Derivatives", 6th edition, pp 397/398)

        // Rollback to third-last step, get prices (s2) & values (p2)
        option.rollback(grid.at(2));
        final Array va2 = option.values();
        QL.ensure(va2.size() == 3, "Expect 3 nodes in grid at second step");
        final double p2u = va2.get(2);
        final double p2m = va2.get(1);
        final double p2d = va2.get(0);
        final double s2u = lattice.underlying(2, 2);
        final double s2m = lattice.underlying(2, 1);
        final double s2d = lattice.underlying(2, 0);

        final double delta2u = (p2u - p2m) / (s2u - s2m);
        final double delta2d = (p2m - p2d) / (s2m - s2d);
        final double gamma = (delta2u - delta2d) / ((s2u - s2d) / 2.0);

        // Rollback to second-last step, get values (p1)
        option.rollback(grid.at(1));
        final Array va = option.values();
        QL.ensure(va.size() == 2, "Expect 2 nodes in grid at first step");
        final double p1u = va.get(1);
        final double p1d = va.get(0);
        final double s1u = lattice.underlying(1, 1);
        final double s1d = lattice.underlying(1, 0);

        final double delta = (p1u - p1d) / (s1u - s1d);

        // Finally, rollback to t=0
        option.rollback(0.0);
        final double p0 = option.presentValue();

        r.value = p0;
        final org.jquantlib.instruments.Option.GreeksImpl greeks = r.greeks();
        greeks.delta = delta;
        greeks.gamma = gamma;
        // theta approximated from numerical derivative between mid value at
        // third-last step and at t0; underlying price unchanged, only time varies.
        greeks.theta = (p2m - p0) / grid.at(2);
    }
}
