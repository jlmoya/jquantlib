/*
 Copyright (C) 2008 Andreas Gaida
 Copyright (C) 2008, 2009 Ralph Schreyer
 Copyright (C) 2008, 2009 Klaus Spanderen
 Copyright (C) 2026 JQuantLib migration contributors.

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

package org.jquantlib.methods.finitedifferences.utilities;

import java.util.function.DoubleUnaryOperator;

import org.jquantlib.instruments.Payoff;
import org.jquantlib.math.integrals.SimpsonIntegral;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpIterator;

/**
 * FDM inner-value calculator with cell-averaging via Simpson integration.
 * <p>
 * Java port of v1.42.1
 * {@code ql/methods/finitedifferences/utilities/fdminnervaluecalculator.{hpp,cpp}}
 * — the {@code FdmCellAveragingInnerValue} class.
 *
 * <p>For interior cells the average payoff over {@code [loc - dminus/2,
 * loc + dplus/2]} is computed via {@link SimpsonIntegral}. Boundary cells
 * fall back to the point-evaluation {@link #innerValue}.
 *
 * <p>An optional {@code gridMapping} function transforms the grid coordinate
 * before applying the payoff (e.g., {@code Math::exp} for log-space grids).
 * Defaults to identity.
 *
 * @author Phase 2m Track C port
 */
public class FdmCellAveragingInnerValue implements FdmInnerValueCalculator {

    private final Payoff payoff_;
    private final FdmMesher mesher_;
    private final int direction_;
    private final DoubleUnaryOperator gridMapping_;

    /** Cache of per-1D-index averaged values, populated lazily. */
    private double[] avgInnerValues_;

    public FdmCellAveragingInnerValue(
            final Payoff payoff,
            final FdmMesher mesher,
            final int direction,
            final DoubleUnaryOperator gridMapping) {
        this.payoff_      = payoff;
        this.mesher_      = mesher;
        this.direction_   = direction;
        this.gridMapping_ = gridMapping;
    }

    /** Identity-mapping constructor (most common usage). */
    public FdmCellAveragingInnerValue(
            final Payoff payoff,
            final FdmMesher mesher,
            final int direction) {
        this(payoff, mesher, direction, x -> x);
    }

    @Override
    public double innerValue(final FdmLinearOpIterator iter, final double t) {
        final double loc = mesher_.location(iter, direction_);
        return payoff_.get(gridMapping_.applyAsDouble(loc));
    }

    @Override
    public double avgInnerValue(final FdmLinearOpIterator iter, final double t) {
        if (avgInnerValues_ == null) {
            // Lazily compute cached averages for each 1D index along direction_.
            final int dim = mesher_.layout().dim()[direction_];
            avgInnerValues_ = new double[dim];
            final boolean[] initialized = new boolean[dim];

            for (final FdmLinearOpIterator it : mesher_.layout()) {
                final int xn = it.coordinates()[direction_];
                if (!initialized[xn]) {
                    initialized[xn]    = true;
                    avgInnerValues_[xn] = avgInnerValueCalc(it, t);
                }
            }
        }

        return avgInnerValues_[iter.coordinates()[direction_]];
    }

    /** Compute cell-average for a single iterator position. */
    private double avgInnerValueCalc(
            final FdmLinearOpIterator iter, final double t) {

        final int dim   = mesher_.layout().dim()[direction_];
        final int coord = iter.coordinates()[direction_];

        // Boundary cells: just point value
        if (coord == 0 || coord == dim - 1) {
            return innerValue(iter, t);
        }

        final double loc = mesher_.location(iter, direction_);
        final double a   = loc - mesher_.dminus(iter, direction_) / 2.0;
        final double b   = loc + mesher_.dplus(iter, direction_)  / 2.0;

        final double fa = payoff_.get(gridMapping_.applyAsDouble(a));
        final double fb = payoff_.get(gridMapping_.applyAsDouble(b));

        try {
            final double acc = (fa != 0.0 || fb != 0.0) ? (fa + fb) * 5e-5 : 1e-4;
            final double integral = new SimpsonIntegral(acc, 8)
                    .op(x -> payoff_.get(gridMapping_.applyAsDouble(x)), a, b);
            return integral / (b - a);
        } catch (final Exception e) {
            return innerValue(iter, t);
        }
    }
}
