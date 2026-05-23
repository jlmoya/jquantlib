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

import org.jquantlib.instruments.BarrierOption;
import org.jquantlib.instruments.DiscretizedAsset;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.processes.StochasticProcess;
import org.jquantlib.time.TimeGrid;

import java.util.List;

/**
 * Discretized barrier option with the Derman-Kani barrier-snap correction for lattice (tree) pricing.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/pricingengines/barrier/discretizedbarrieroption.{hpp,cpp}}
 * {@code DiscretizedDermanKaniBarrierOption} (Phase 2 L3-D). Wraps an unenhanced {@link DiscretizedBarrierOption} and
 * applies the Derman-Kani linear interpolation correction near the barrier to reduce the discretisation bias when the
 * barrier falls between grid points. See Derman, Kani, Ergener, Bardhan — "Enhanced Numerical Methods for Options with
 * Barriers", Goldman Sachs Quantitative Strategies Research Notes, 1995.
 */
public class DiscretizedDermanKaniBarrierOption extends DiscretizedAsset {

    private final DiscretizedBarrierOption unenhanced_;

    public DiscretizedDermanKaniBarrierOption(final BarrierOption.ArgumentsImpl args, final StochasticProcess process,
            final TimeGrid grid) {
        super();
        this.unenhanced_ = new DiscretizedBarrierOption(args, process, grid);
    }

    public DiscretizedDermanKaniBarrierOption(final BarrierOption.ArgumentsImpl args,
            final StochasticProcess process) {
        this(args, process, new TimeGrid());
    }

    @Override
    public void reset(final int size) {
        unenhanced_.initialize(method(), time());
        values_ = new Array(size).fill(0.0);
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
        adjustBarrier(values_, grid);
        unenhanced_.checkBarrier(values_, grid);
    }

    private void adjustBarrier(final Array optvalues, final Array grid) {
        final double barrier = unenhanced_.arguments().barrier;
        final double rebate = unenhanced_.arguments().rebate;
        switch ( unenhanced_.arguments().barrierType ) {
            case DownIn:
                for ( int j = 0; j < optvalues.size() - 1; ++j ) {
                    if ( grid.get(j) <= barrier && grid.get(j + 1) > barrier ) {
                        final double ltob = barrier - grid.get(j);
                        final double htob = grid.get(j + 1) - barrier;
                        final double htol = grid.get(j + 1) - grid.get(j);
                        final double u1 = unenhanced_.values().get(j + 1);
                        final double t1 = unenhanced_.vanilla().get(j + 1);
                        optvalues.set(j + 1, Math.max(0.0, (ltob * t1 + htob * u1) / htol));
                    }
                }
                break;
            case DownOut:
                for ( int j = 0; j < optvalues.size() - 1; ++j ) {
                    if ( grid.get(j) <= barrier && grid.get(j + 1) > barrier ) {
                        final double a = (barrier - grid.get(j)) * rebate;
                        final double b = (grid.get(j + 1) - barrier) * unenhanced_.values().get(j + 1);
                        final double c = grid.get(j + 1) - grid.get(j);
                        optvalues.set(j + 1, Math.max(0.0, (a + b) / c));
                    }
                }
                break;
            case UpIn:
                for ( int j = 0; j < optvalues.size() - 1; ++j ) {
                    if ( grid.get(j) < barrier && grid.get(j + 1) >= barrier ) {
                        final double ltob = barrier - grid.get(j);
                        final double htob = grid.get(j + 1) - barrier;
                        final double htol = grid.get(j + 1) - grid.get(j);
                        final double u = unenhanced_.values().get(j);
                        final double t = unenhanced_.vanilla().get(j);
                        optvalues.set(j, Math.max(0.0, (ltob * u + htob * t) / htol));
                    }
                }
                break;
            case UpOut:
                for ( int j = 0; j < optvalues.size() - 1; ++j ) {
                    if ( grid.get(j) < barrier && grid.get(j + 1) >= barrier ) {
                        final double a = (barrier - grid.get(j)) * unenhanced_.values().get(j);
                        final double b = (grid.get(j + 1) - barrier) * rebate;
                        final double c = grid.get(j + 1) - grid.get(j);
                        optvalues.set(j, Math.max(0.0, (a + b) / c));
                    }
                }
                break;
            default:
                break;
        }
    }
}
