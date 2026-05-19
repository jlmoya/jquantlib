/*
 Copyright (C) 2008 Andreas Gaida
 Copyright (C) 2008, 2009 Ralph Schreyer
 Copyright (C) 2008, 2009 Klaus Spanderen

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
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

import org.jquantlib.instruments.Payoff;
import org.jquantlib.math.Ops;
import org.jquantlib.math.integrals.SimpsonIntegral;
import org.jquantlib.math.transcendental.JQuantMath;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpIterator;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpLayout;

/**
 * Cell-averaging inner-value calculator for log-space (ln S) grids.
 * <p>
 * Java port of v1.42.1 {@code ql/methods/finitedifferences/utilities/fdminnervaluecalculator.{hpp,cpp}} — specifically
 * {@code FdmLogInnerValue}, which is a specialisation of {@code FdmCellAveragingInnerValue} with
 * {@code gridMapping = exp}.
 * <p>
 * {@link #innerValue} evaluates the payoff at {@code exp(location)}. {@link #avgInnerValue} performs a Simpson-rule
 * integration of the payoff over the half-cell intervals {@code [loc - dminus/2, loc + dplus/2]}, using
 * {@code JQuantMath.exp} for the exp mapping. Boundary cells return the unaveraged value (matches C++).
 * <p>
 * Cached: the averaged values for each x-direction coordinate are computed on the first call and reused. This matches
 * C++ which lazily fills {@code avgInnerValues_} on first {@code avgInnerValue()} call.
 *
 * @author Phase 2m Track A port
 */
public class FdmLogInnerValue implements FdmInnerValueCalculator {

    private final Payoff payoff;
    private final FdmMesher mesher;
    private final int direction;
    private double[] avgInnerValues;   // lazily computed; null until first call

    /**
     * @param payoff    option payoff (e.g. PlainVanillaPayoff)
     * @param mesher    FDM mesh
     * @param direction dimension index (0 for 1-D Black-Scholes)
     */
    public FdmLogInnerValue(final Payoff payoff, final FdmMesher mesher, final int direction) {
        this.payoff = payoff;
        this.mesher = mesher;
        this.direction = direction;
    }

    @Override
    public double innerValue(final FdmLinearOpIterator iter, final double t) {
        final double loc = mesher.location(iter, direction);
        return payoff.get(JQuantMath.exp(loc));
    }

    @Override
    public double avgInnerValue(final FdmLinearOpIterator iter, final double t) {
        if ( avgInnerValues == null ) {
            computeAvgInnerValues(t);
        }
        return avgInnerValues[iter.coordinates()[direction]];
    }

    // -----------------------------------------------------------------------
    // private helpers
    // -----------------------------------------------------------------------

    private void computeAvgInnerValues(final double t) {
        final FdmLinearOpLayout layout = mesher.layout();
        final int dim = layout.dim()[direction];
        avgInnerValues = new double[dim];
        final boolean[] computed = new boolean[dim];

        for ( final FdmLinearOpIterator i : layout ) {
            final int xn = i.coordinates()[direction];
            if ( !computed[xn] ) {
                computed[xn] = true;
                avgInnerValues[xn] = avgInnerValueCalc(i, t);
            }
        }
    }

    /** Mirrors C++ {@code FdmCellAveragingInnerValue::avgInnerValueCalc}. */
    private double avgInnerValueCalc(final FdmLinearOpIterator iter, final double t) {
        final FdmLinearOpLayout layout = mesher.layout();
        final int dim = layout.dim()[direction];
        final int coord = iter.coordinates()[direction];

        // boundary cells: no half-interval to integrate over
        if ( coord == 0 || coord == dim - 1 ) {
            return innerValue(iter, t);
        }

        final double loc = mesher.location(iter, direction);
        final double dm = mesher.dminus(iter, direction);
        final double dp = mesher.dplus(iter, direction);
        final double a = loc - dm / 2.0;
        final double b = loc + dp / 2.0;

        // f(x) = payoff(exp(x))
        final double fa = payoff.get(JQuantMath.exp(a));
        final double fb = payoff.get(JQuantMath.exp(b));

        try {
            final double acc = ((fa != 0.0 || fb != 0.0) ? Math.abs((fa + fb) * 5e-5) : 1e-4);
            final SimpsonIntegral simpson = new SimpsonIntegral(acc, 8);
            final Ops.DoubleOp f = x -> payoff.get(JQuantMath.exp(x));
            return simpson.op(f, a, b) / (b - a);
        } catch ( final Exception ex ) {
            return innerValue(iter, t);
        }
    }
}
