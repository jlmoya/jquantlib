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

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2002, 2003 Ferdinando Ametrano
 Copyright (C) 2002, 2003 Sadruddin Rejeb
 Copyright (C) 2003 Neil Firth
 Copyright (C) 2007 StatPro Italia srl
*/

package org.jquantlib.pricingengines.vanilla;

import org.jquantlib.QL;
import org.jquantlib.exercise.AmericanExercise;
import org.jquantlib.instruments.CashOrNothingPayoff;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.math.randomnumbers.MersenneTwisterUniformRng;
import org.jquantlib.math.randomnumbers.RandomSequenceGenerator;
import org.jquantlib.methods.montecarlo.Path;
import org.jquantlib.methods.montecarlo.PathPricer;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.TimeGrid;

/**
 * Pricing engine for digital options using Monte Carlo simulation.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/pricingengines/vanilla/mcdigitalengine.hpp} (Phase 5e.5b-CFC-d-181).
 *
 * <p>Cash-at-hit path-dependent engine: detects whether the underlying
 * crosses the strike between two grid points using the Brownian-bridge correction of Beaglehole-Dybvig-Zhou (1997) and
 * El Babsiri-Noel (1998). If a crossing is detected, the cash payoff is discounted either to the exercise time (between
 * grid points) or — when {@code payoffAtExpiry} is set on the {@link AmericanExercise} — to the terminal date.
 *
 * <p>Like {@link MCEuropeanEngine}, this is specialised to
 * {@code RNG = PseudoRandom} (Mersenne-Twister with the path generator's seed). The C++ template's
 * {@code LowDiscrepancy} specialisation is a Phase 5h.5-MC-INFRA-b carry-forward.
 *
 * <p>The Brownian-bridge correction draws a separate uniform sequence
 * with a fixed seed (76) per path, matching the C++ implementation verbatim.
 *
 * @author JQuantLib
 */
public final class MCDigitalEngine extends MCVanillaEngine {

    public MCDigitalEngine(final GeneralizedBlackScholesProcess process, final int timeSteps,
            final int timeStepsPerYear, final boolean brownianBridge, final boolean antitheticVariate,
            final int requiredSamples, final double requiredTolerance, final int maxSamples, final long seed) {
        super(process, timeSteps, timeStepsPerYear, brownianBridge, antitheticVariate,
                /* controlVariate=*/ false, requiredSamples, requiredTolerance, maxSamples, seed);
    }

    @Override
    protected PathPricer< Path > pathPricer() {
        final OneAssetOption.ArgumentsImpl a = (OneAssetOption.ArgumentsImpl) arguments_;

        final CashOrNothingPayoff payoff;
        try {
            payoff = (CashOrNothingPayoff) a.payoff;
        } catch ( final ClassCastException e ) {
            throw new RuntimeException("wrong payoff given");
        }
        QL.require(payoff != null, "wrong payoff given");

        final AmericanExercise exercise;
        try {
            exercise = (AmericanExercise) a.exercise;
        } catch ( final ClassCastException e ) {
            throw new RuntimeException("wrong exercise given");
        }
        QL.require(exercise != null, "wrong exercise given");

        QL.require(process_ != null, "Black-Scholes process required");

        final TimeGrid grid = timeGrid();
        // C++: PseudoRandom::ursg_type sequenceGen(grid.size()-1,
        //                                          PseudoRandom::urng_type(76));
        // Fixed seed 76 matches the C++ implementation verbatim.
        final RandomSequenceGenerator< MersenneTwisterUniformRng > sequenceGen = new RandomSequenceGenerator< MersenneTwisterUniformRng >(
                MersenneTwisterUniformRng.class, grid.size() - 1, 76L);

        return new DigitalPathPricer(payoff, exercise, process_.riskFreeRate().currentLink(), process_, sequenceGen);
    }

    /**
     * Path pricer implementing the Brownian-bridge corrected cash-at-hit digital payoff. Java port of the inline
     * {@code DigitalPathPricer} class declared in {@code mcdigitalengine.hpp} and defined in
     * {@code mcdigitalengine.cpp}.
     */
    static final class DigitalPathPricer extends PathPricer< Path > {

        private final CashOrNothingPayoff payoff_;
        private final AmericanExercise exercise_;
        private final GeneralizedBlackScholesProcess diffProcess_;
        private final RandomSequenceGenerator< MersenneTwisterUniformRng > sequenceGen_;
        private final YieldTermStructure discountTS_;

        DigitalPathPricer(final CashOrNothingPayoff payoff, final AmericanExercise exercise,
                final YieldTermStructure discountTS, final GeneralizedBlackScholesProcess diffProcess,
                final RandomSequenceGenerator< MersenneTwisterUniformRng > sequenceGen) {
            this.payoff_ = payoff;
            this.exercise_ = exercise;
            this.discountTS_ = discountTS;
            this.diffProcess_ = diffProcess;
            this.sequenceGen_ = sequenceGen;
        }

        @Override
        public Double op(final Path path) {
            final int n = path.length();
            QL.require(n > 1, "the path cannot be empty");

            double log_asset_price = Math.log(path.front());
            final TimeGrid timeGrid = path.timeGrid();
            final double[] u = sequenceGen_.nextSequence().value();
            final double log_strike = Math.log(payoff_.strike());

            final Option.Type type = payoff_.optionType();
            if ( type == Option.Type.Call ) {
                for ( int i = 0; i < n - 1; i++ ) {
                    final double x = Math.log(path.get(i + 1) / path.get(i));
                    // terminal or initial vol? — C++ uses initial (timeGrid[i+1])
                    final double vol = diffProcess_.diffusion(timeGrid.get(i + 1), Math.exp(log_asset_price));
                    final double dt = timeGrid.dt(i);
                    final double y =
                            log_asset_price + 0.5 * (x + Math.sqrt(x * x - 2 * vol * vol * dt * Math.log(1 - u[i])));
                    // cross the strike
                    if ( y >= log_strike ) {
                        if ( exercise_.payoffAtExpiry() ) {
                            return payoff_.getCashPayoff() * discountTS_.discount(path.timeGrid().back());
                        } else {
                            // discount at the exercise time between
                            // path.timeGrid()[i+1] and path.timeGrid()[i+2]
                            return payoff_.getCashPayoff() * discountTS_.discount(path.timeGrid().get(i + 1));
                        }
                    }
                    log_asset_price += x;
                }
            } else if ( type == Option.Type.Put ) {
                for ( int i = 0; i < n - 1; i++ ) {
                    final double x = Math.log(path.get(i + 1) / path.get(i));
                    final double vol = diffProcess_.diffusion(timeGrid.get(i + 1), Math.exp(log_asset_price));
                    final double dt = timeGrid.dt(i);
                    final double y =
                            log_asset_price + 0.5 * (x - Math.sqrt(x * x - 2 * vol * vol * dt * Math.log(u[i])));
                    if ( y <= log_strike ) {
                        if ( exercise_.payoffAtExpiry() ) {
                            return payoff_.getCashPayoff() * discountTS_.discount(path.timeGrid().back());
                        } else {
                            return payoff_.getCashPayoff() * discountTS_.discount(path.timeGrid().get(i + 1));
                        }
                    }
                    log_asset_price += x;
                }
            } else {
                throw new RuntimeException("unknown option type");
            }

            return 0.0;
        }
    }
}
