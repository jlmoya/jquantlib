/*
 Copyright (C) 2015 Peter Caspers
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

package org.jquantlib.experimental.math;

import org.jquantlib.math.Closeness;
import org.jquantlib.math.Constants;
import org.jquantlib.math.Ops;
import org.jquantlib.math.integrals.Integrator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Integral of a piecewise well-behaved function using a custom integrator for the pieces.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/experimental/math/piecewiseintegral.{hpp,cpp}}.
 *
 * <p>The function is integrated over intervals strictly not containing the
 * critical points (when {@code avoidCriticalPoints} is true).
 */
public class PiecewiseIntegral extends Integrator {

    private final Integrator integrator_;
    private final List< Double > criticalPoints_;
    private final double eps_;

    public PiecewiseIntegral(final Integrator integrator, final List< Double > criticalPoints,
            final boolean avoidCriticalPoints) {
        super(1.0, 1);
        this.integrator_ = integrator;
        // sort + dedupe via close_enough
        final List< Double > sorted = new ArrayList<>(criticalPoints);
        Collections.sort(sorted);
        final List< Double > deduped = new ArrayList<>();
        for ( final Double v : sorted ) {
            if ( deduped.isEmpty() || !Closeness.isCloseEnough(deduped.get(deduped.size() - 1), v) ) {
                deduped.add(v);
            }
        }
        this.criticalPoints_ = deduped;
        this.eps_ = avoidCriticalPoints ? (1.0 + Constants.QL_EPSILON) : 1.0;
    }

    public PiecewiseIntegral(final Integrator integrator, final List< Double > criticalPoints) {
        this(integrator, criticalPoints, true);
    }

    @Override
    protected double integrate(final Ops.DoubleOp f, final double a, final double b) {
        final int a0 = lowerBoundIndex(a);
        final int b0 = lowerBoundIndex(b);

        if ( a0 == criticalPoints_.size() ) {
            double tmp = 1.0;
            if ( !criticalPoints_.isEmpty() ) {
                if ( Closeness.isCloseEnough(a, criticalPoints_.get(criticalPoints_.size() - 1)) ) {
                    tmp = eps_;
                }
            }
            return integrate_h(f, a * tmp, b);
        }

        double res = 0.0;

        if ( !Closeness.isCloseEnough(a, criticalPoints_.get(a0)) ) {
            res += integrate_h(f, a, Math.min(criticalPoints_.get(a0) / eps_, b));
        }

        int b0Eff = b0;
        if ( b0Eff == criticalPoints_.size() ) {
            --b0Eff;
            if ( !Closeness.isCloseEnough(criticalPoints_.get(b0Eff), b) ) {
                res += integrate_h(f, criticalPoints_.get(b0Eff) * eps_, b);
            }
        }

        for ( int x = a0; x < b0Eff; ++x ) {
            res += integrate_h(f, criticalPoints_.get(x) * eps_, Math.min(criticalPoints_.get(x + 1) / eps_, b));
        }
        return res;
    }

    private int lowerBoundIndex(final double v) {
        // returns the first index whose criticalPoints_[idx] >= v (lower_bound)
        int lo = 0;
        int hi = criticalPoints_.size();
        while ( lo < hi ) {
            final int mid = (lo + hi) >>> 1;
            if ( criticalPoints_.get(mid) < v ) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    private double integrate_h(final Ops.DoubleOp f, final double a, final double b) {
        if ( !Closeness.isCloseEnough(a, b) ) {
            return integrator_.op(f, a, b);
        }
        return 0.0;
    }
}
