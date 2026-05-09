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

import java.util.List;

import org.jquantlib.instruments.Payoff;
import org.jquantlib.math.Constants;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpIterator;
import org.jquantlib.methods.finitedifferences.utilities.FdmInnerValueCalculator;

/**
 * Inner value calculator for an exponential extended Ornstein-Uhlenbeck grid.
 * <p>
 * Java port of v1.42.1
 * {@code ql/experimental/finitedifferences/fdmexpextouinnervaluecalculator.hpp}.
 * <p>
 * The mesh stores {@code u = log(S) - f(t)}; the inner value is
 * {@code payoff(exp(f(t) + u))}, where {@code f} is read from a piecewise-
 * constant {@link Shape} (a list of (time, value) pairs sorted by time).
 *
 * @author Phase 4n WI port
 */
public class FdmExpExtOUInnerValueCalculator implements FdmInnerValueCalculator {

    /**
     * Time-dependent shift applied to the log-spot. Pairs of
     * {@code (time, value)} sorted ascending by time. The lookup uses a
     * lower-bound search on the time axis.
     */
    public static final class ShapePoint {
        public final double time;
        public final double value;
        public ShapePoint(final double time, final double value) {
            this.time = time;
            this.value = value;
        }
    }

    private final int direction_;
    private final Payoff payoff_;
    private final FdmMesher mesher_;
    private final List<ShapePoint> shape_;

    public FdmExpExtOUInnerValueCalculator(
            final Payoff payoff,
            final FdmMesher mesher) {
        this(payoff, mesher, null, 0);
    }

    public FdmExpExtOUInnerValueCalculator(
            final Payoff payoff,
            final FdmMesher mesher,
            final List<ShapePoint> shape,
            final int direction) {
        this.payoff_ = payoff;
        this.mesher_ = mesher;
        this.shape_ = shape;
        this.direction_ = direction;
    }

    @Override
    public double innerValue(final FdmLinearOpIterator iter, final double t) {
        final double u = mesher_.location(iter, direction_);
        double f = 0;
        if (shape_ != null) {
            f = lowerBound(shape_, t - Math.sqrt(Constants.QL_EPSILON));
        }
        return payoff_.get(Math.exp(f + u));
    }

    @Override
    public double avgInnerValue(final FdmLinearOpIterator iter, final double t) {
        return innerValue(iter, t);
    }

    /**
     * Returns the value at the first {@link ShapePoint} whose {@code time} is
     * not less than the search key — equivalent to {@code std::lower_bound}.
     * If all points are less than the key, returns the last value (matches
     * C++ undefined behavior of dereferencing end()).
     */
    private static double lowerBound(final List<ShapePoint> shape, final double key) {
        int lo = 0, hi = shape.size();
        while (lo < hi) {
            final int mid = (lo + hi) >>> 1;
            if (shape.get(mid).time < key) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        if (lo == shape.size()) {
            // Defensive — C++ dereferences end() here; we return the last
            // available value to avoid an out-of-bounds error.
            return shape.get(shape.size() - 1).value;
        }
        return shape.get(lo).value;
    }
}
