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
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.BarrierOption;
import org.jquantlib.instruments.BarrierType;
import org.jquantlib.instruments.DiscretizedAsset;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.pricingengines.vanilla.DiscretizedVanillaOption;
import org.jquantlib.processes.StochasticProcess;
import org.jquantlib.time.TimeGrid;

import java.util.ArrayList;
import java.util.List;

/**
 * Discretized barrier option for lattice (tree) pricing.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/pricingengines/barrier/discretizedbarrieroption.{hpp,cpp}} (Phase 2 L3-D). Used by {@link BinomialBarrierEngine}.
 * Holds a nested {@link DiscretizedVanillaOption} (the un-knocked counterpart needed for the In-type payoffs) and a
 * vector of stopping times; the {@code postAdjustValuesImpl} step applies the barrier crossing rule depending on the
 * {@code DownIn/DownOut/UpIn/UpOut} type.
 */
public class DiscretizedBarrierOption extends DiscretizedAsset {

    private final BarrierOption.ArgumentsImpl arguments_;
    private final double[] stoppingTimes_;
    private final DiscretizedVanillaOption vanilla_;

    public DiscretizedBarrierOption(final BarrierOption.ArgumentsImpl args, final StochasticProcess process,
            final TimeGrid grid) {
        super();
        this.arguments_ = args;
        this.vanilla_ = new DiscretizedVanillaOption(args, process, grid);
        QL.require(!args.exercise.dates().isEmpty(), "specify at least one stopping date");

        final int n = args.exercise.dates().size();
        this.stoppingTimes_ = new double[n];
        for ( int i = 0; i < n; ++i ) {
            stoppingTimes_[i] = process.time(args.exercise.date(i));
            if ( !grid.empty() ) {
                stoppingTimes_[i] = grid.closestTime(stoppingTimes_[i]);
            }
        }
    }

    public DiscretizedBarrierOption(final BarrierOption.ArgumentsImpl args, final StochasticProcess process) {
        this(args, process, new TimeGrid());
    }

    public Array vanilla() {
        return vanilla_.values();
    }

    public BarrierOption.ArgumentsImpl arguments() {
        return arguments_;
    }

    @Override
    public void reset(final int size) {
        vanilla_.initialize(method(), time());
        values_ = new Array(size).fill(0.0);
        adjustValues();
    }

    @Override
    public List< Double > mandatoryTimes() {
        final List< Double > out = new ArrayList<>(stoppingTimes_.length);
        for ( final double s : stoppingTimes_ ) {
            out.add(s);
        }
        return out;
    }

    @Override
    protected void postAdjustValuesImpl() {
        if ( arguments_.barrierType == BarrierType.DownIn || arguments_.barrierType == BarrierType.UpIn ) {
            vanilla_.rollback(time());
        }
        final Array grid = method().grid(time());
        checkBarrier(values_, grid);
    }

    public void checkBarrier(final Array optvalues, final Array grid) {
        final double now = time();
        final boolean endTime = isOnTime(stoppingTimes_[stoppingTimes_.length - 1]);
        boolean stoppingTime = false;
        switch ( arguments_.exercise.type() ) {
            case American:
                if ( now <= stoppingTimes_[1] && now >= stoppingTimes_[0] ) {
                    stoppingTime = true;
                }
                break;
            case European:
                if ( isOnTime(stoppingTimes_[0]) ) {
                    stoppingTime = true;
                }
                break;
            case Bermudan:
                for ( final double i : stoppingTimes_ ) {
                    if ( isOnTime(i) ) {
                        stoppingTime = true;
                        break;
                    }
                }
                break;
            default:
                throw new RuntimeException("invalid option type");
        }
        final double barrier = arguments_.barrier;
        final double rebate = arguments_.rebate;
        for ( int j = 0; j < optvalues.size(); j++ ) {
            switch ( arguments_.barrierType ) {
                case DownIn:
                    if ( grid.get(j) <= barrier ) {
                        // knocked in
                        if ( stoppingTime ) {
                            optvalues.set(j, Math.max(vanilla_.values().get(j), arguments_.payoff.get(grid.get(j))));
                        } else {
                            optvalues.set(j, vanilla_.values().get(j));
                        }
                    } else if ( endTime ) {
                        optvalues.set(j, rebate);
                    }
                    break;
                case DownOut:
                    if ( grid.get(j) <= barrier ) {
                        optvalues.set(j, rebate); // knocked out
                    } else if ( stoppingTime ) {
                        optvalues.set(j, Math.max(optvalues.get(j), arguments_.payoff.get(grid.get(j))));
                    }
                    break;
                case UpIn:
                    if ( grid.get(j) >= barrier ) {
                        if ( stoppingTime ) {
                            optvalues.set(j, Math.max(vanilla_.values().get(j), arguments_.payoff.get(grid.get(j))));
                        } else {
                            optvalues.set(j, vanilla_.values().get(j));
                        }
                    } else if ( endTime ) {
                        optvalues.set(j, rebate);
                    }
                    break;
                case UpOut:
                    if ( grid.get(j) >= barrier ) {
                        optvalues.set(j, rebate);
                    } else if ( stoppingTime ) {
                        optvalues.set(j, Math.max(optvalues.get(j), arguments_.payoff.get(grid.get(j))));
                    }
                    break;
                default:
                    throw new RuntimeException("invalid barrier type");
            }
        }
    }
}
