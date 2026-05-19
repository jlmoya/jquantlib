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

import org.jquantlib.QL;
import org.jquantlib.instruments.DiscretizedAsset;
import org.jquantlib.instruments.VanillaOption;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.pricingengines.vanilla.DiscretizedVanillaOption;
import org.jquantlib.processes.StochasticProcess;
import org.jquantlib.time.TimeGrid;

import java.util.ArrayList;
import java.util.List;

/**
 * Standard discretized double-barrier option helper class.
 * <p>
 * Used with {@link BinomialDoubleBarrierEngine} to implement a standard binomial algorithm for double barrier options.
 * <p>
 * Mirrors {@code QuantLib::DiscretizedDoubleBarrierOption} from
 * {@code ql/experimental/barrieroption/discretizeddoublebarrieroption.hpp} (v1.42.1).
 *
 * @author JQuantLib migration
 */
public class DiscretizedDoubleBarrierOption extends DiscretizedAsset {

    //
    // private final fields
    //

    private final DoubleBarrierOption.ArgumentsImpl arguments_;
    private final List< Double > stoppingTimes_;
    private final DiscretizedVanillaOption vanilla_;

    //
    // public constructors
    //

    public DiscretizedDoubleBarrierOption(final DoubleBarrierOption.Arguments arguments,
            final StochasticProcess process, final TimeGrid grid) {
        this.arguments_ = (DoubleBarrierOption.ArgumentsImpl) arguments;
        // Reuse a vanilla option discretization so KnockIn-family contracts can
        // fall back to the unrestricted European value when needed.
        // The vanilla option only sees the (payoff, exercise) part of the
        // double-barrier arguments; that subset is shape-compatible with
        // VanillaOption.Arguments since DoubleBarrierOption extends OneAssetOption.
        final VanillaOption.ArgumentsImpl vanillaArgs = new VanillaOption.ArgumentsImpl();
        vanillaArgs.payoff = arguments_.payoff;
        vanillaArgs.exercise = arguments_.exercise;
        this.vanilla_ = new DiscretizedVanillaOption(vanillaArgs, process, grid);

        QL.require(!arguments_.exercise.dates().isEmpty(), "specify at least one stopping date");

        final int n = arguments_.exercise.dates().size();
        this.stoppingTimes_ = new ArrayList< Double >(n);
        for ( int i = 0; i < n; ++i ) {
            double t = process.time(arguments_.exercise.date(i));
            if ( !grid.empty() ) {
                t = grid.closestTime(t);
            }
            stoppingTimes_.add(t);
        }
    }

    //
    // public accessors
    //

    public Array vanilla() {
        return vanilla_.values();
    }

    public DoubleBarrierOption.ArgumentsImpl arguments() {
        return arguments_;
    }

    //
    // overrides DiscretizedAsset
    //

    @Override
    public void reset(final int size) {
        vanilla_.initialize(method(), time());
        values_ = new Array(size); // Array(int) zero-initialises (matches C++ Array(size, 0.0))
        adjustValues();
    }

    @Override
    public List< Double > mandatoryTimes() {
        return stoppingTimes_;
    }

    @Override
    protected void postAdjustValuesImpl() {
        if ( arguments_.barrierType != DoubleBarrierType.KnockOut ) {
            vanilla_.rollback(time());
        }
        final Array grid = method().grid(time());
        checkBarrier(values_, grid);
    }

    //
    // package-visible helpers (used by Derman-Kani extension)
    //

    void checkBarrier(final Array optvalues, final Array grid) {
        final double now = time();
        final boolean endTime = isOnTime(stoppingTimes_.get(stoppingTimes_.size() - 1));
        boolean stoppingTime = false;

        switch ( arguments_.exercise.type() ) {
        case American:
            if ( now <= stoppingTimes_.get(1) && now >= stoppingTimes_.get(0) ) {
                stoppingTime = true;
            }
            break;
        case European:
            if ( isOnTime(stoppingTimes_.get(0)) ) {
                stoppingTime = true;
            }
            break;
        case Bermudan:
            for ( final double s : stoppingTimes_ ) {
                if ( isOnTime(s) ) {
                    stoppingTime = true;
                    break;
                }
            }
            break;
        default:
            throw new LibraryException("invalid option type");
        }

        for ( int j = 0; j < optvalues.size(); j++ ) {
            switch ( arguments_.barrierType ) {
            case KnockIn:
                if ( grid.get(j) <= arguments_.barrier_lo ) {
                    // knocked in dn
                    if ( stoppingTime ) {
                        optvalues.set(j, Math.max(vanilla().get(j), arguments_.payoff.get(grid.get(j))));
                    } else {
                        optvalues.set(j, vanilla().get(j));
                    }
                } else if ( grid.get(j) >= arguments_.barrier_hi ) {
                    // knocked in up
                    if ( stoppingTime ) {
                        optvalues.set(j, Math.max(vanilla().get(j), arguments_.payoff.get(grid.get(j))));
                    } else {
                        optvalues.set(j, vanilla().get(j));
                    }
                } else if ( endTime ) {
                    optvalues.set(j, arguments_.rebate);
                }
                break;
            case KnockOut:
                if ( grid.get(j) <= arguments_.barrier_lo ) {
                    optvalues.set(j, arguments_.rebate); // knocked out lo
                } else if ( grid.get(j) >= arguments_.barrier_hi ) {
                    optvalues.set(j, arguments_.rebate); // knocked out hi
                } else if ( stoppingTime ) {
                    optvalues.set(j, Math.max(optvalues.get(j), arguments_.payoff.get(grid.get(j))));
                }
                break;
            case KIKO:
                // low barrier is KI, high is KO
                if ( grid.get(j) <= arguments_.barrier_lo ) {
                    // knocked in dn
                    if ( stoppingTime ) {
                        optvalues.set(j, Math.max(vanilla().get(j), arguments_.payoff.get(grid.get(j))));
                    } else {
                        optvalues.set(j, vanilla().get(j));
                    }
                } else if ( grid.get(j) >= arguments_.barrier_hi ) {
                    optvalues.set(j, arguments_.rebate); // knocked out hi
                } else if ( endTime ) {
                    optvalues.set(j, arguments_.rebate);
                }
                break;
            case KOKI:
                // low barrier is KO, high is KI
                if ( grid.get(j) <= arguments_.barrier_lo ) {
                    optvalues.set(j, arguments_.rebate); // knocked out lo
                } else if ( grid.get(j) >= arguments_.barrier_hi ) {
                    // knocked in up
                    if ( stoppingTime ) {
                        optvalues.set(j, Math.max(vanilla().get(j), arguments_.payoff.get(grid.get(j))));
                    } else {
                        optvalues.set(j, vanilla().get(j));
                    }
                } else if ( endTime ) {
                    optvalues.set(j, arguments_.rebate);
                }
                break;
            default:
                throw new LibraryException("invalid barrier type");
            }
        }
    }
}
