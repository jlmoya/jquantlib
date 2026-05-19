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

import org.jquantlib.instruments.DiscretizedAsset;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.processes.StochasticProcess;
import org.jquantlib.time.TimeGrid;

import java.util.List;

/**
 * Derman-Kani-Ergener-Bardhan discretized option helper class.
 * <p>
 * Used with {@link BinomialDoubleBarrierEngine} to implement the enhanced binomial algorithm of E. Derman, I. Kani, D.
 * Ergener, I. Bardhan ("Enhanced Numerical Methods for Options with Barriers", 1995).
 * <p>
 * Note: this algorithm is only suitable if the payoff can be approximated linearly, e.g. it is not usable for
 * cash-or-nothing payoffs.
 * <p>
 * Mirrors {@code QuantLib::DiscretizedDermanKaniDoubleBarrierOption} from
 * {@code ql/experimental/barrieroption/discretizeddoublebarrieroption.hpp} (v1.42.1).
 *
 * @author JQuantLib migration
 */
public class DiscretizedDermanKaniDoubleBarrierOption extends DiscretizedAsset {

    private final DiscretizedDoubleBarrierOption unenhanced_;

    public DiscretizedDermanKaniDoubleBarrierOption(final DoubleBarrierOption.Arguments arguments,
            final StochasticProcess process, final TimeGrid grid) {
        this.unenhanced_ = new DiscretizedDoubleBarrierOption(arguments, process, grid);
    }

    @Override
    public void reset(final int size) {
        unenhanced_.initialize(method(), time());
        values_ = new Array(size); // Array(int) zero-initialises
        adjustValues();
    }

    @Override
    public List< Double > mandatoryTimes() {
        return unenhanced_.mandatoryTimes();
    }

    @Override
    protected void postAdjustValuesImpl() {
        unenhanced_.rollback(time());

        final Array grid = method().grid(time());
        unenhanced_.checkBarrier(values_, grid); // compute payoffs
        adjustBarrier(values_, grid);
    }

    private void adjustBarrier(final Array optvalues, final Array grid) {
        final DoubleBarrierOption.ArgumentsImpl args = unenhanced_.arguments();
        final double barrier_lo = args.barrier_lo;
        final double barrier_hi = args.barrier_hi;
        final double rebate = args.rebate;

        switch ( args.barrierType ) {
        case KnockIn:
            for ( int j = 0; j < optvalues.size() - 1; ++j ) {
                if ( grid.get(j) <= barrier_lo && grid.get(j + 1) > barrier_lo ) {
                    // grid[j+1] above barrier_lo, grid[j] under (in),
                    // interpolate optvalues[j+1]
                    final double ltob = barrier_lo - grid.get(j);
                    final double htob = grid.get(j + 1) - barrier_lo;
                    final double htol = grid.get(j + 1) - grid.get(j);
                    final double u1 = unenhanced_.values().get(j + 1);
                    final double t1 = unenhanced_.vanilla().get(j + 1);
                    optvalues.set(j + 1, Math.max(0.0, (ltob * t1 + htob * u1) / htol)); // derman std
                } else if ( grid.get(j) < barrier_hi && grid.get(j + 1) >= barrier_hi ) {
                    // grid[j+1] above barrier_hi (in), grid[j] under,
                    // interpolate optvalues[j]
                    final double ltob = barrier_hi - grid.get(j);
                    final double htob = grid.get(j + 1) - barrier_hi;
                    final double htol = grid.get(j + 1) - grid.get(j);
                    final double u = unenhanced_.values().get(j);
                    final double t = unenhanced_.vanilla().get(j);
                    optvalues.set(j, Math.max(0.0, (ltob * u + htob * t) / htol)); // derman std
                }
            }
            break;
        case KnockOut:
            for ( int j = 0; j < optvalues.size() - 1; ++j ) {
                if ( grid.get(j) <= barrier_lo && grid.get(j + 1) > barrier_lo ) {
                    // grid[j+1] above barrier_lo, grid[j] under (out),
                    // interpolate optvalues[j+1]
                    final double a = (barrier_lo - grid.get(j)) * rebate;
                    final double b = (grid.get(j + 1) - barrier_lo) * unenhanced_.values().get(j + 1);
                    final double c = grid.get(j + 1) - grid.get(j);
                    optvalues.set(j + 1, Math.max(0.0, (a + b) / c));
                } else if ( grid.get(j) < barrier_hi && grid.get(j + 1) >= barrier_hi ) {
                    // grid[j+1] above barrier_hi (out), grid[j] under,
                    // interpolate optvalues[j]
                    final double a = (barrier_hi - grid.get(j)) * unenhanced_.values().get(j);
                    final double b = (grid.get(j + 1) - barrier_hi) * rebate;
                    final double c = grid.get(j + 1) - grid.get(j);
                    optvalues.set(j, Math.max(0.0, (a + b) / c));
                }
            }
            break;
        default:
            throw new LibraryException("unsupported barrier type");
        }
    }
}
