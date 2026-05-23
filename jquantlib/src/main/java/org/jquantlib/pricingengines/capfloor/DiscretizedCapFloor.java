/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2001, 2002, 2003 Sadruddin Rejeb
 Copyright (C) 2004, 2007 StatPro Italia srl
*/

package org.jquantlib.pricingengines.capfloor;

import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.CapFloor;
import org.jquantlib.instruments.DiscretizedAsset;
import org.jquantlib.instruments.DiscretizedDiscountBond;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.time.Date;

import java.util.ArrayList;
import java.util.List;

/**
 * Discretized cap/floor for lattice (tree) pricing.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/pricingengines/capfloor/discretizedcapfloor.{hpp,cpp}} (Phase 2 L3-D). Used by {@link TreeCapFloorEngine}; the
 * caplet/floorlet payoffs are decomposed into bond-options (Jamshidian-style) so that each caplet on the rate fixing at
 * {@code startTimes[i]} is priced as a put option on a discount bond maturing at {@code endTimes[i]} (and the floorlet
 * as a call), see Hull-White / lattice references.
 */
public class DiscretizedCapFloor extends DiscretizedAsset {

    private final CapFloor.ArgumentsImpl arguments_;
    private final double[] startTimes_;
    private final double[] endTimes_;

    public DiscretizedCapFloor(final CapFloor.ArgumentsImpl args, final Date referenceDate, final DayCounter dc) {
        super();
        this.arguments_ = args;
        this.startTimes_ = new double[args.startDates.length];
        for ( int i = 0; i < startTimes_.length; ++i ) {
            this.startTimes_[i] = dc.yearFraction(referenceDate, args.startDates[i]);
        }
        this.endTimes_ = new double[args.endDates.length];
        for ( int i = 0; i < endTimes_.length; ++i ) {
            this.endTimes_[i] = dc.yearFraction(referenceDate, args.endDates[i]);
        }
    }

    @Override
    public void reset(final int size) {
        values_ = new Array(size).fill(0.0);
        adjustValues();
    }

    @Override
    public List< Double > mandatoryTimes() {
        final List< Double > times = new ArrayList<>(startTimes_.length + endTimes_.length);
        for ( final double s : startTimes_ ) {
            times.add(s);
        }
        for ( final double e : endTimes_ ) {
            times.add(e);
        }
        return times;
    }

    @Override
    protected void preAdjustValuesImpl() {
        for ( int i = 0; i < startTimes_.length; i++ ) {
            if ( isOnTime(startTimes_[i]) ) {
                final double end = endTimes_[i];
                final double tenor = arguments_.accrualTimes[i];
                final DiscretizedDiscountBond bond = new DiscretizedDiscountBond();
                bond.initialize(method(), end);
                bond.rollback(time());

                final CapFloor.Type type = arguments_.type;
                final double gearing = arguments_.gearings[i];
                final double nominal = arguments_.nominals[i];

                if ( type == CapFloor.Type.Cap || type == CapFloor.Type.Collar ) {
                    final double accrual = 1.0 + arguments_.capRates[i] * tenor;
                    final double strike = 1.0 / accrual;
                    for ( int j = 0; j < values_.size(); j++ ) {
                        values_.set(j, values_.get(j)
                                + nominal * accrual * gearing * Math.max(strike - bond.values().get(j), 0.0));
                    }
                }

                if ( type == CapFloor.Type.Floor || type == CapFloor.Type.Collar ) {
                    final double accrual = 1.0 + arguments_.floorRates[i] * tenor;
                    final double strike = 1.0 / accrual;
                    final double mult = (type == CapFloor.Type.Floor) ? 1.0 : -1.0;
                    for ( int j = 0; j < values_.size(); j++ ) {
                        values_.set(j, values_.get(j)
                                + nominal * accrual * mult * gearing * Math.max(bond.values().get(j) - strike, 0.0));
                    }
                }
            }
        }
    }

    @Override
    protected void postAdjustValuesImpl() {
        for ( int i = 0; i < endTimes_.length; i++ ) {
            if ( isOnTime(endTimes_[i]) ) {
                if ( startTimes_[i] < 0.0 ) {
                    final double nominal = arguments_.nominals[i];
                    final double accrual = arguments_.accrualTimes[i];
                    final double fixing = arguments_.forwards[i];
                    final double gearing = arguments_.gearings[i];
                    final CapFloor.Type type = arguments_.type;

                    if ( type == CapFloor.Type.Cap || type == CapFloor.Type.Collar ) {
                        final double cap = arguments_.capRates[i];
                        final double capletRate = Math.max(fixing - cap, 0.0);
                        final double inc = capletRate * accrual * nominal * gearing;
                        for ( int j = 0; j < values_.size(); j++ ) {
                            values_.set(j, values_.get(j) + inc);
                        }
                    }

                    if ( type == CapFloor.Type.Floor || type == CapFloor.Type.Collar ) {
                        final double floor = arguments_.floorRates[i];
                        final double floorletRate = Math.max(floor - fixing, 0.0);
                        final double inc = floorletRate * accrual * nominal * gearing;
                        if ( type == CapFloor.Type.Floor ) {
                            for ( int j = 0; j < values_.size(); j++ ) {
                                values_.set(j, values_.get(j) + inc);
                            }
                        } else {
                            for ( int j = 0; j < values_.size(); j++ ) {
                                values_.set(j, values_.get(j) - inc);
                            }
                        }
                    }
                }
            }
        }
    }
}
