/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2014 Thema Consulting SA
*/

package org.jquantlib.pricingengines.barrier;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.instruments.BarrierOption;
import org.jquantlib.instruments.DiscretizedAsset;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.math.Closeness;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.lattices.BlackScholesLattice;
import org.jquantlib.methods.lattices.CoxRossRubinstein;
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

import java.lang.reflect.Constructor;

/**
 * Pricing engine for barrier options using binomial trees.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/pricingengines/barrier/binomialbarrierengine.hpp}
 * {@code BinomialBarrierEngine<T,D>} (Phase 2 L3-D). C++ is templated on a tree class {@code T} and a discretized-asset
 * class {@code D} (either {@link DiscretizedBarrierOption} or {@link DiscretizedDermanKaniBarrierOption}); Java carries
 * both as {@code Class<?>} reflection hooks since it has no template specialisation.
 *
 * <p>Timesteps for {@link CoxRossRubinstein}-derived trees are adjusted using
 * the Boyle-Lau algorithm (Journal of Derivatives, 1/1994, "Bumping up against the barrier with the binomial method")
 * to ensure the barrier falls (locally) on a node — mitigates the well-known binomial-barrier discretisation bias.
 *
 * @see DiscretizedBarrierOption
 * @see DiscretizedDermanKaniBarrierOption
 */
public class BinomialBarrierEngine< T extends Tree > extends BarrierOption.EngineImpl {

    private final Class< ? extends Tree > classT_;
    private final Class< ? extends DiscretizedAsset > classD_;
    private final GeneralizedBlackScholesProcess process_;
    private final int timeSteps_;
    private final int maxTimeSteps_;

    /**
     * @param classT        tree class (e.g. {@link CoxRossRubinstein}.class)
     * @param classD        discretized-asset class — {@link DiscretizedBarrierOption}.class or
     *                      {@link DiscretizedDermanKaniBarrierOption}.class
     * @param process       Black-Scholes process
     * @param timeSteps     base number of time steps
     * @param maxTimeSteps  maximum allowed (Boyle-Lau enhanced) time steps; {@code 0} → heuristic
     *                      {@code max(1000, 5*timeSteps)}; if {@code == timeSteps} or {@code T} is not CRR-derived,
     *                      Boyle-Lau is disabled
     */
    public BinomialBarrierEngine(final Class< ? extends Tree > classT,
            final Class< ? extends DiscretizedAsset > classD, final GeneralizedBlackScholesProcess process,
            final int timeSteps, final int maxTimeSteps) {
        super();
        QL.require(timeSteps > 0, "timeSteps must be positive, " + timeSteps + " not allowed");
        QL.require(maxTimeSteps == 0 || maxTimeSteps >= timeSteps,
                "maxTimeSteps must be zero or greater than or equal to timeSteps, " + maxTimeSteps + " not allowed");
        this.classT_ = classT;
        this.classD_ = classD;
        this.process_ = process;
        this.timeSteps_ = timeSteps;
        this.maxTimeSteps_ = (maxTimeSteps == 0) ? Math.max(1000, timeSteps * 5) : maxTimeSteps;
        process_.addObserver(this);
    }

    /** Default-arity ctor — Boyle-Lau heuristic max. */
    public BinomialBarrierEngine(final Class< ? extends Tree > classT,
            final Class< ? extends DiscretizedAsset > classD, final GeneralizedBlackScholesProcess process,
            final int timeSteps) {
        this(classT, classD, process, timeSteps, 0);
    }

    @Override
    public void calculate() /* @ReadOnly */ {
        final BarrierOption.ArgumentsImpl args = (BarrierOption.ArgumentsImpl) arguments_;
        final BarrierOption.ResultsImpl results = (BarrierOption.ResultsImpl) results_;

        final StrikedTypePayoff payoff;
        try {
            payoff = (StrikedTypePayoff) args.payoff;
        } catch ( final ClassCastException e ) {
            throw new RuntimeException("non-striked payoff given");
        }
        QL.require(payoff != null, "non-striked payoff given");
        QL.require(payoff.strike() > 0.0, "strike must be positive");

        final double s0 = process_.stateVariable().currentLink().value();
        QL.require(s0 > 0.0, "negative or null underlying given");
        QL.require(!triggered(s0), "barrier touched");

        final DayCounter rfdc = process_.riskFreeRate().currentLink().dayCounter();
        final DayCounter divdc = process_.dividendYield().currentLink().dayCounter();
        final DayCounter voldc = process_.blackVolatility().currentLink().dayCounter();
        final Calendar volcal = process_.blackVolatility().currentLink().calendar();

        final double v = process_.blackVolatility().currentLink().blackVol(args.exercise.lastDate(), s0);
        final Date maturityDate = args.exercise.lastDate();
        final double r = process_.riskFreeRate().currentLink()
                .zeroRate(maturityDate, rfdc, Compounding.Continuous, Frequency.NoFrequency).rate();
        final double q = process_.dividendYield().currentLink()
                .zeroRate(maturityDate, divdc, Compounding.Continuous, Frequency.NoFrequency).rate();
        final Date referenceDate = process_.riskFreeRate().currentLink().referenceDate();

        // flat-coefficient binomial trees
        final Handle< YieldTermStructure > flatRiskFree = new Handle< YieldTermStructure >(
                new FlatForward(referenceDate, r, rfdc));
        final Handle< YieldTermStructure > flatDividends = new Handle< YieldTermStructure >(
                new FlatForward(referenceDate, q, divdc));
        final Handle< BlackVolTermStructure > flatVol = new Handle< BlackVolTermStructure >(
                new BlackConstantVol(referenceDate, volcal, v, voldc));

        final double maturity = rfdc.yearFraction(referenceDate, maturityDate);

        final StochasticProcess1D bs = new GeneralizedBlackScholesProcess(process_.stateVariable(), flatDividends,
                flatRiskFree, flatVol);

        // Boyle-Lau correction (CRR-only)
        int optimum_steps = timeSteps_;
        if ( CoxRossRubinstein.class.isAssignableFrom(classT_) && maxTimeSteps_ > timeSteps_ && s0 > 0
                && args.barrier > 0 ) {
            final double divisor = (s0 > args.barrier) ? Math.pow(Math.log(s0 / args.barrier), 2.0)
                    : Math.pow(Math.log(args.barrier / s0), 2.0);
            if ( !Closeness.isCloseEnough(divisor, 0.0) ) {
                for ( int i = 1; i < timeSteps_; ++i ) {
                    final int optimum = (int) ((i * i * v * v * maturity) / divisor);
                    if ( timeSteps_ < optimum ) {
                        optimum_steps = optimum;
                        break;
                    }
                }
            }
            if ( optimum_steps > maxTimeSteps_ ) {
                optimum_steps = maxTimeSteps_;
            }
        }

        final TimeGrid grid = new TimeGrid(maturity, optimum_steps);
        final T tree = newTree(bs, maturity, optimum_steps, payoff.strike());
        final BlackScholesLattice< T > lattice = new BlackScholesLattice< T >(tree, r, maturity, optimum_steps);

        final DiscretizedAsset option = newDiscretizedAsset(args, bs, grid);
        option.initialize(lattice, maturity);

        // Partial derivatives — see Hull, Options/Futures/.../, 6th ed., pp. 397/398.
        option.rollback(grid.at(2));
        final Array va2 = option.values();
        QL.ensure(va2.size() == 3, "expect 3 nodes in grid at second step");
        final double p2u = va2.get(2);
        final double p2m = va2.get(1);
        final double p2d = va2.get(0);
        final double s2u = lattice.underlying(2, 2);
        final double s2m = lattice.underlying(2, 1);
        final double s2d = lattice.underlying(2, 0);

        final double delta2u = (p2u - p2m) / (s2u - s2m);
        final double delta2d = (p2m - p2d) / (s2m - s2d);
        final double gamma = (delta2u - delta2d) / ((s2u - s2d) / 2.0);

        option.rollback(grid.at(1));
        final Array va = option.values();
        QL.ensure(va.size() == 2, "expect 2 nodes in grid at first step");
        final double p1u = va.get(1);
        final double p1d = va.get(0);
        final double s1u = lattice.underlying(1, 1);
        final double s1d = lattice.underlying(1, 0);

        final double delta = (p1u - p1d) / (s1u - s1d);

        option.rollback(0.0);
        final double p0 = option.presentValue();

        results.value = p0;
        results.greeks().delta = delta;
        results.greeks().gamma = gamma;
        // theta approximated as numerical derivative between mid value @ step 2 and t=0.
        results.greeks().theta = (p2m - p0) / grid.at(2);
    }

    @SuppressWarnings( "unchecked" )
    private T newTree(final StochasticProcess1D bs, final double maturity, final int steps, final double strike) {
        try {
            final Constructor< ? extends Tree > c = classT_.getConstructor(StochasticProcess1D.class, double.class,
                    int.class, double.class);
            return (T) c.newInstance(bs, maturity, steps, strike);
        } catch ( final Exception e ) {
            throw new LibraryException(e);
        }
    }

    private DiscretizedAsset newDiscretizedAsset(final BarrierOption.ArgumentsImpl args, final StochasticProcess1D bs,
            final TimeGrid grid) {
        try {
            final Constructor< ? extends DiscretizedAsset > c = classD_.getConstructor(BarrierOption.ArgumentsImpl.class,
                    org.jquantlib.processes.StochasticProcess.class, TimeGrid.class);
            return c.newInstance(args, bs, grid);
        } catch ( final Exception e ) {
            throw new LibraryException(e);
        }
    }
}
