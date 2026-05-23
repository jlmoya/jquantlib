/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2009 Ralph Schreyer
 */

package org.jquantlib.methods.finitedifferences.stepconditions;

import org.jquantlib.QL;
import org.jquantlib.math.interpolations.MonotonicNaturalCubicInterpolation;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.finitedifferences.StepCondition;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;

import java.util.List;

/**
 * Arithmetic-average step condition for discrete-fixing Asian options on a 2-D
 * {@link FdmMesher} (one axis = log-spot, other axis = log-running-average).
 *
 * <p>Java port of v1.42.1
 * {@code ql/methods/finitedifferences/stepconditions/fdmarithmeticaveragecondition.{hpp,cpp}}.
 *
 * <p>On each fixing date {@code t} that appears in {@code averageTimes_}, the
 * running average {@code A_iT} updates as
 * {@code A_iT = (iT-1)/iT * A_(iT-1) + (1/iT) * S_iT} where
 * {@code iT = pastFixings + fixingIndex(t) + 1}. The step condition replaces the
 * grid value at each {@code (S_i, A_j)} cell with the value interpolated at the
 * post-fixing average level
 * {@code ((iT-nTimes)/iT) * a_j + (nTimes/iT) * S_i}, using a monotonic natural
 * cubic spline along the average axis. The number of coincident fixing times
 * at {@code t} is {@code nTimes} (typically 1).
 *
 * <p>Quantitatively identical to the C++ source: same indices, same
 * interpolation (C++ {@code MonotonicCubicNaturalSpline} ↔ Java
 * {@link MonotonicNaturalCubicInterpolation}), same extrapolation flag (enabled).
 */
public class FdmArithmeticAverageCondition implements StepCondition< Array > {

    private final Array x_;             // physical-units equity grid (exp of mesher locations)
    private final Array a_;             // physical-units average grid (exp of mesher locations)

    private final List< Double > averageTimes_;
    private final int pastFixings_;
    private final FdmMesher mesher_;
    private final int equityDirection_;

    /**
     * @param averageTimes      fixing-date year-fractions (sorted ascending; may
     *                          contain coincident entries).
     * @param unused            placeholder matching C++ unnamed {@code Real} parameter
     *                          (the C++ ctor accepts but ignores it).
     * @param pastFixings       number of fixings already realized before the
     *                          present pricing date.
     * @param mesher            2-D FDM mesh.
     * @param equityDirection   axis index of the equity (log-spot) axis (0 or 1);
     *                          the other axis carries the running average.
     */
    public FdmArithmeticAverageCondition(final List< Double > averageTimes, final double unused, final int pastFixings,
            final FdmMesher mesher, final int equityDirection) {

        QL.require(mesher.layout().dim().length == 2, "2D allowed only");
        QL.require(equityDirection == 0 || equityDirection == 1, "equityDirection has to be 0 or 1");

        this.averageTimes_ = new java.util.ArrayList<>(averageTimes);
        this.pastFixings_ = pastFixings;
        this.mesher_ = mesher;
        this.equityDirection_ = equityDirection;

        final int[] dim = mesher.layout().dim();
        final int averageDirection = (equityDirection == 0) ? 1 : 0;

        this.x_ = new Array(dim[equityDirection]);
        this.a_ = new Array(dim[averageDirection]);

        final int xSpacing = mesher.layout().spacing()[equityDirection];
        final Array xLoc = mesher.locations(equityDirection);
        for ( int i = 0; i < x_.size(); ++i ) {
            x_.set(i, Math.exp(xLoc.get(i * xSpacing)));
        }

        final int aSpacing = mesher.layout().spacing()[averageDirection];
        final Array aLoc = mesher.locations(averageDirection);
        for ( int j = 0; j < a_.size(); ++j ) {
            a_.set(j, Math.exp(aLoc.get(j * aSpacing)));
        }
    }

    @Override
    public void applyTo(final Array a, final double t) {
        QL.require(mesher_.layout().size() == a.size(), "inconsistent array dimensions");

        // Find first index of t in averageTimes_ and count coincident entries.
        int firstIdx = -1;
        int nTimes = 0;
        for ( int k = 0; k < averageTimes_.size(); ++k ) {
            final double tk = averageTimes_.get(k);
            if ( tk == t ) {
                if ( firstIdx < 0 ) {
                    firstIdx = k;
                }
                ++nTimes;
            }
        }

        if ( nTimes > 0 ) {
            final Array aCopy = a.clone();
            // iT = (iter - begin) + 1 + pastFixings_  (C++ uses 1-based fixing count)
            final int iT = firstIdx + 1 + pastFixings_;
            final int averageDirection = (equityDirection_ == 0) ? 1 : 0;
            final int xSpacing = mesher_.layout().spacing()[equityDirection_];
            final int aSpacing = mesher_.layout().spacing()[averageDirection];
            final Array tmp = new Array(a_.size());

            final int xn = x_.size();
            final int an = a_.size();
            for ( int i = 0; i < xn; ++i ) {
                for ( int j = 0; j < an; ++j ) {
                    final int index = i * xSpacing + j * aSpacing;
                    tmp.set(j, aCopy.get(index));
                }
                final MonotonicNaturalCubicInterpolation interp =
                        new MonotonicNaturalCubicInterpolation(a_, tmp.clone());
                interp.enableExtrapolation();
                final double xi = x_.get(i);
                final double w1 = (iT - nTimes) / (double) iT;
                final double w2 = nTimes / (double) iT;
                for ( int j = 0; j < an; ++j ) {
                    final int index = i * xSpacing + j * aSpacing;
                    final double xq = w1 * a_.get(j) + w2 * xi;
                    a.set(index, interp.op(xq, true));
                }
            }
        }
    }
}
