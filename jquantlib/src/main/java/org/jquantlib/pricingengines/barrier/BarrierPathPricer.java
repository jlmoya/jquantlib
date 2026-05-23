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
import org.jquantlib.instruments.BarrierType;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.math.randomnumbers.MersenneTwisterUniformRng;
import org.jquantlib.math.randomnumbers.RandomSequenceGenerator;
import org.jquantlib.methods.montecarlo.Path;
import org.jquantlib.methods.montecarlo.PathPricer;
import org.jquantlib.processes.StochasticProcess1D;
import org.jquantlib.time.TimeGrid;

/**
 * Brownian-bridge corrected MC barrier path pricer.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/pricingengines/barrier/mcbarrierengine.{hpp,cpp}}
 * {@code BarrierPathPricer} (Phase 2 L3-D). Detects whether the underlying crossed the barrier between two grid points
 * via the Beaglehole-Dybvig-Zhou (1997) / El Babsiri-Noel (1998) Brownian-bridge correction (a separate uniform sample
 * per step is drawn from {@code sequenceGen} — same correction used in {@code MCDigitalEngine}).
 *
 * <p>Sibling to {@link BiasedBarrierPathPricer} which checks the barrier
 * only at the discrete grid points (no bridge correction, biased).
 */
public class BarrierPathPricer extends PathPricer< Path > {

    private final BarrierType barrierType_;
    private final double barrier_;
    private final double rebate_;
    private final StochasticProcess1D diffProcess_;
    private final RandomSequenceGenerator< MersenneTwisterUniformRng > sequenceGen_;
    private final PlainVanillaPayoff payoff_;
    private final double[] discounts_;

    public BarrierPathPricer(final BarrierType barrierType, final double barrier, final double rebate,
            final Option.Type type, final double strike, final double[] discounts,
            final StochasticProcess1D diffProcess,
            final RandomSequenceGenerator< MersenneTwisterUniformRng > sequenceGen) {
        QL.require(strike >= 0.0, "strike less than zero not allowed");
        QL.require(barrier > 0.0, "barrier less/equal zero not allowed");
        this.barrierType_ = barrierType;
        this.barrier_ = barrier;
        this.rebate_ = rebate;
        this.diffProcess_ = diffProcess;
        this.sequenceGen_ = sequenceGen;
        this.payoff_ = new PlainVanillaPayoff(type, strike);
        this.discounts_ = discounts.clone();
    }

    @Override
    public Double op(final Path path) {
        final int NULL_NODE = -1;
        final int n = path.length();
        QL.require(n > 1, "the path cannot be empty");

        boolean isOptionActive;
        int knockNode = NULL_NODE;
        double asset_price = path.front();
        final TimeGrid timeGrid = path.timeGrid();
        final double[] u = sequenceGen_.nextSequence().value();

        switch ( barrierType_ ) {
            case DownIn:
                isOptionActive = false;
                for ( int i = 0; i < n - 1; i++ ) {
                    final double new_asset_price = path.get(i + 1);
                    final double vol = diffProcess_.diffusion(timeGrid.get(i), asset_price);
                    final double dt = timeGrid.dt(i);
                    final double x = Math.log(new_asset_price / asset_price);
                    double y = 0.5 * (x - Math.sqrt(x * x - 2.0 * vol * vol * dt * Math.log(u[i])));
                    y = asset_price * Math.exp(y);
                    if ( y <= barrier_ ) {
                        isOptionActive = true;
                        if ( knockNode == NULL_NODE ) {
                            knockNode = i + 1;
                        }
                    }
                    asset_price = new_asset_price;
                }
                break;
            case UpIn:
                isOptionActive = false;
                for ( int i = 0; i < n - 1; i++ ) {
                    final double new_asset_price = path.get(i + 1);
                    final double vol = diffProcess_.diffusion(timeGrid.get(i), asset_price);
                    final double dt = timeGrid.dt(i);
                    final double x = Math.log(new_asset_price / asset_price);
                    double y = 0.5 * (x + Math.sqrt(x * x - 2.0 * vol * vol * dt * Math.log(1.0 - u[i])));
                    y = asset_price * Math.exp(y);
                    if ( y >= barrier_ ) {
                        isOptionActive = true;
                        if ( knockNode == NULL_NODE ) {
                            knockNode = i + 1;
                        }
                    }
                    asset_price = new_asset_price;
                }
                break;
            case DownOut:
                isOptionActive = true;
                for ( int i = 0; i < n - 1; i++ ) {
                    final double new_asset_price = path.get(i + 1);
                    final double vol = diffProcess_.diffusion(timeGrid.get(i), asset_price);
                    final double dt = timeGrid.dt(i);
                    final double x = Math.log(new_asset_price / asset_price);
                    double y = 0.5 * (x - Math.sqrt(x * x - 2.0 * vol * vol * dt * Math.log(u[i])));
                    y = asset_price * Math.exp(y);
                    if ( y <= barrier_ ) {
                        isOptionActive = false;
                        if ( knockNode == NULL_NODE ) {
                            knockNode = i + 1;
                        }
                    }
                    asset_price = new_asset_price;
                }
                break;
            case UpOut:
                isOptionActive = true;
                for ( int i = 0; i < n - 1; i++ ) {
                    final double new_asset_price = path.get(i + 1);
                    final double vol = diffProcess_.diffusion(timeGrid.get(i), asset_price);
                    final double dt = timeGrid.dt(i);
                    final double x = Math.log(new_asset_price / asset_price);
                    double y = 0.5 * (x + Math.sqrt(x * x - 2.0 * vol * vol * dt * Math.log(1.0 - u[i])));
                    y = asset_price * Math.exp(y);
                    if ( y >= barrier_ ) {
                        isOptionActive = false;
                        if ( knockNode == NULL_NODE ) {
                            knockNode = i + 1;
                        }
                    }
                    asset_price = new_asset_price;
                }
                break;
            default:
                throw new RuntimeException("unknown barrier type");
        }

        if ( isOptionActive ) {
            return payoff_.get(asset_price) * discounts_[discounts_.length - 1];
        }
        return switch ( barrierType_ ) {
            case UpIn, DownIn -> rebate_ * discounts_[discounts_.length - 1];
            case UpOut, DownOut -> rebate_ * discounts_[knockNode];
            default -> throw new RuntimeException("unknown barrier type");
        };
    }
}
