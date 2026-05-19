/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

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
 Copyright (C) 2011 Klaus Spanderen
 */
package org.jquantlib.experimental.finitedifferences;

import org.jquantlib.experimental.finitedifferences.FdmExpExtOUInnerValueCalculator.ShapePoint;
import org.jquantlib.instruments.Payoff;
import org.jquantlib.math.Constants;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpIterator;
import org.jquantlib.methods.finitedifferences.utilities.FdmInnerValueCalculator;

import java.util.List;

/**
 * Inner value calculator for the OU + exp-jumps (Kluge) model on a 2D mesh.
 * <p>
 * Java port of v1.42.1 {@code ql/experimental/finitedifferences/fdmextoujumpmodelinnervalue.hpp}.
 * <p>
 * The mesh stores {@code (X, Y)} where {@code S = exp(f(t) + X + Y)}; the inner value is
 * {@code payoff(exp(f(t) + X + Y))}, with {@code f} read from a piecewise-constant shape sorted by time. {@code X} is
 * in direction 0 and {@code Y} in direction 1.
 *
 * @author Phase 4n WI port
 */
public class FdmExtOUJumpModelInnerValue implements FdmInnerValueCalculator {

    private final Payoff payoff_;
    private final FdmMesher mesher_;
    private final List< ShapePoint > shape_;

    public FdmExtOUJumpModelInnerValue(final Payoff payoff, final FdmMesher mesher) {
        this(payoff, mesher, null);
    }

    public FdmExtOUJumpModelInnerValue(final Payoff payoff, final FdmMesher mesher, final List< ShapePoint > shape) {
        this.payoff_ = payoff;
        this.mesher_ = mesher;
        this.shape_ = shape;
    }

    private static double lowerBound(final List< ShapePoint > shape, final double key) {
        int lo = 0, hi = shape.size();
        while ( lo < hi ) {
            final int mid = (lo + hi) >>> 1;
            if ( shape.get(mid).time < key ) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        if ( lo == shape.size() ) {
            return shape.get(shape.size() - 1).value;
        }
        return shape.get(lo).value;
    }

    @Override
    public double innerValue(final FdmLinearOpIterator iter, final double t) {
        final double x = mesher_.location(iter, 0);
        final double y = mesher_.location(iter, 1);
        double f = 0;
        if ( shape_ != null ) {
            f = lowerBound(shape_, t - Math.sqrt(Constants.QL_EPSILON));
        }
        return payoff_.get(Math.exp(f + x + y));
    }

    @Override
    public double avgInnerValue(final FdmLinearOpIterator iter, final double t) {
        return innerValue(iter, t);
    }
}
