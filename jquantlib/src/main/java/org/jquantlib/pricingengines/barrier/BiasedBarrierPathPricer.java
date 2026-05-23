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
import org.jquantlib.methods.montecarlo.Path;
import org.jquantlib.methods.montecarlo.PathPricer;

/**
 * Biased (uncorrected) MC barrier path pricer.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/pricingengines/barrier/mcbarrierengine.{hpp,cpp}}
 * {@code BiasedBarrierPathPricer} (Phase 2 L3-D). Checks the barrier crossing only at the discrete sampling instants of
 * the path — biased low for knock-in / biased high for knock-out compared to the corrected continuous-monitoring
 * {@link BarrierPathPricer} which applies the Brownian-bridge crossing correction (Beaglehole-Dybvig-Zhou 1997).
 *
 * <p>Used when sampling steps are fine enough that the bias is acceptable
 * or when the corrected path pricer's per-step diffusion lookup is undesirable.
 */
public class BiasedBarrierPathPricer extends PathPricer< Path > {

    private final BarrierType barrierType_;
    private final double barrier_;
    private final double rebate_;
    private final PlainVanillaPayoff payoff_;
    private final double[] discounts_;

    public BiasedBarrierPathPricer(final BarrierType barrierType, final double barrier, final double rebate,
            final Option.Type type, final double strike, final double[] discounts) {
        QL.require(strike >= 0.0, "strike less than zero not allowed");
        QL.require(barrier > 0.0, "barrier less/equal zero not allowed");
        this.barrierType_ = barrierType;
        this.barrier_ = barrier;
        this.rebate_ = rebate;
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

        switch ( barrierType_ ) {
            case DownIn:
                isOptionActive = false;
                for ( int i = 1; i < n; i++ ) {
                    asset_price = path.get(i);
                    if ( asset_price <= barrier_ ) {
                        isOptionActive = true;
                        if ( knockNode == NULL_NODE ) {
                            knockNode = i;
                        }
                    }
                }
                break;
            case UpIn:
                isOptionActive = false;
                for ( int i = 1; i < n; i++ ) {
                    asset_price = path.get(i);
                    if ( asset_price >= barrier_ ) {
                        isOptionActive = true;
                        if ( knockNode == NULL_NODE ) {
                            knockNode = i;
                        }
                    }
                }
                break;
            case DownOut:
                isOptionActive = true;
                for ( int i = 1; i < n; i++ ) {
                    asset_price = path.get(i);
                    if ( asset_price <= barrier_ ) {
                        isOptionActive = false;
                        if ( knockNode == NULL_NODE ) {
                            knockNode = i;
                        }
                    }
                }
                break;
            case UpOut:
                isOptionActive = true;
                for ( int i = 1; i < n; i++ ) {
                    asset_price = path.get(i);
                    if ( asset_price >= barrier_ ) {
                        isOptionActive = false;
                        if ( knockNode == NULL_NODE ) {
                            knockNode = i;
                        }
                    }
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
