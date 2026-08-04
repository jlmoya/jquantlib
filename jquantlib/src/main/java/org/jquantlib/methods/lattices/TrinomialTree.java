/*
 Copyright (C) 2008 Srinivas Hasti
 Copyright (C) 2008 Tim Swetonic

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
 Copyright (C) 2001, 2002, 2003 Sadruddin Rejeb
 Copyright (C) 2005 StatPro Italia srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <http://quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
*/

package org.jquantlib.methods.lattices;

import org.jquantlib.QL;
import org.jquantlib.processes.StochasticProcess1D;
import org.jquantlib.time.TimeGrid;

import java.util.Vector;

/**
 * Recombining trinomial tree class
 * <p>
 * This class defines a recombining trinomial tree approximating a 1-D stochastic process.
 *
 * @author Srinivas Hasti
 * @author Tim Swetonic
 * @warning The diffusion term of the SDE must be independent of the underlying process.
 * @category lattices
 */
@SuppressWarnings("deprecation")
public class TrinomialTree extends Tree {

    public static final Branches branches = Branches.TRINOMIAL;

    /**
     * Floor activation threshold: the {@code dx} floor (Clewlow-Strickland 1998) is applied only on grid steps
     * shorter than {@code FLOOR_THRESHOLD * dtMax}. Two orders of magnitude leaves uniform and typical
     * non-uniform grids (weekend rolls, 1-day mismatches) untouched; the floor intervenes only on the
     * pathological small-mandatory-gap case.
     * <p>
     * Mirrors the anonymous-namespace {@code kFloorThreshold} constant of C++ v1.43
     * {@code ql/methods/lattices/trinomialtree.cpp}.
     */
    private static final double FLOOR_THRESHOLD = 0.01;

    protected Vector< Branching > branchings_ = new Vector<>();
    protected double x0_;
    protected Vector< Double > dx_ = new Vector<>();
    protected TimeGrid timeGrid_;

    public TrinomialTree(final StochasticProcess1D process, final TimeGrid timeGrid) {
        this(process, timeGrid, false);
    }

    public TrinomialTree(final StochasticProcess1D process, final TimeGrid timeGrid, final boolean isPositive) {
        super(timeGrid.size());
        dx_.add(new Double(0.0));
        timeGrid_ = timeGrid;
        x0_ = process.x0();

        final int nTimeSteps = timeGrid.size() - 1;
        QL.require(nTimeSteps > 0, "null time steps for trinomial tree");

        // Preflight: capture per-step variances and dtMax in one pass.
        // dxFloor is the largest natural dx in the grid: when applied on a tiny step it produces a dx no
        // larger than what some other step is already using, preventing node explosion. Iterating over
        // actual step durations (rather than a hypothetical dtMax window anchored at each step start) keeps
        // the variance integration strictly within the declared grid horizon and avoids floating-point
        // fragility from any `terminal - t_i` subtraction. The per-step v2 values are cached so the main
        // loop below does not re-invoke process.variance(); for processes with non-trivial variance
        // evaluation cost this halves the call count.
        double dtMax = 0.0;
        final double[] v2Cache = new double[nTimeSteps];
        double dxFloorVar = 0.0;
        for ( int i = 0; i < nTimeSteps; i++ ) {
            final double dt_i = timeGrid.dt(i);
            dtMax = Math.max(dtMax, dt_i);
            final double v2_i = process.variance(timeGrid.at(i), 0.0, dt_i);
            v2Cache[i] = v2_i;
            dxFloorVar = Math.max(dxFloorVar, v2_i);
        }
        final double dxFloor = Math.sqrt(3.0 * dxFloorVar);

        Integer jMin = 0;
        Integer jMax = 0;

        for ( int i = 0; i < nTimeSteps; i++ ) {
            final double t = timeGrid.at(i);
            final double dt = timeGrid.dt(i);

            // Variance must be independent of x
            final double v2 = v2Cache[i];
            /* Volatility */
            final double v = Math.sqrt(v2);
            final double dxNatural = v * Math.sqrt(3.0);
            final double dxNext = (dt < FLOOR_THRESHOLD * dtMax)
                    ? Math.max(dxNatural, dxFloor)
                    : dxNatural;
            dx_.add(dxNext);

            // dxIsFloored captures whether the floor was *effective* at this step (dx widened beyond its
            // natural value), not just whether the gate condition fired. Time-dependent diffusions can have
            // a small dt whose natural dx already equals dxFloor (which happens exactly when that step is
            // the grid's variance argmax); in that case the gate fires but dx is unchanged, and the
            // classical-formula branch below still applies.
            final boolean dxIsFloored = dxNext > dxNatural;
            final double dx2 = dxNext * dxNext;

            final Branching branching = new Branching();
            for ( int j = jMin; j <= jMax; j++ ) {
                final double x = x0_ + j * dx_.get(i);
                final double m = process.expectation(t, x, dt);
                int temp = (int) Math.floor((m - x0_) / dx_.get(i + 1) + 0.5);

                boolean tempBumped = false;
                if ( isPositive ) {
                    while ( x0_ + (temp - 1) * dx_.get(i + 1) <= 0 ) {
                        temp++;
                        tempBumped = true;
                    }
                }

                final double e = m - (x0_ + temp * dx_.get(i + 1));
                final double e2 = e * e;

                final double p1;
                final double p2;
                final double p3;
                if ( dxIsFloored ) {
                    // General moment-matching probabilities valid for any grid spacing dx, used only when
                    // the floor widened dx beyond v*sqrt(3). They redistribute probability toward the middle
                    // node to reflect the smaller variance of the short step.
                    //
                    // Non-negativity requires v^2 >= |e|*(dx - |e|), which can fail in the floored regime
                    // when v << v_max. We accept slightly negative weights as the cost of the pathology fix;
                    // first two moments are still matched exactly so signed weights remain arithmetically
                    // consistent. Hull-White-style alternative branching does not help (it solves boundary
                    // drift, not the small-variance regime).
                    p1 = (v2 + e2 - e * dxNext) / (2.0 * dx2);
                    p2 = 1.0 - (v2 + e2) / dx2;
                    p3 = (v2 + e2 + e * dxNext) / (2.0 * dx2);
                } else {
                    // Classical Hull-White / Clewlow trinomial probabilities for dx = v*sqrt(3). Kept in
                    // this exact form (not the algebraically-equivalent dx-based form) so cached pricing
                    // values in the Bermudan-swaption / callable-bond trees remain bit-for-bit identical to
                    // v1.42.1 by construction rather than by FP coincidence.
                    final double e3 = e * Math.sqrt(3.0);
                    p1 = (1.0 + e2 / v2 - e3 / v) / 6.0;
                    p2 = (2.0 - e2 / v2) / 3.0;
                    p3 = (1.0 + e2 / v2 + e3 / v) / 6.0;
                }

                // In the unfloored regime with naturally-rounded temp the formulas above are non-negative by
                // construction (|e| <= dx/2 implies v^2 >= |e|*(dx - |e|)); guard against future drift in
                // this safe path. When isPositive bumps temp upward to keep the underlying positive, |e| can
                // exceed dx/2 -- the resulting signed weights were accepted by upstream before this change
                // (CIR family models rely on this), so we skip the assertion in that case. In the floored
                // regime the limitation is documented and accepted uniformly.
                if ( !dxIsFloored && !tempBumped ) {
                    QL.ensure(p1 >= 0.0 && p2 >= 0.0 && p3 >= 0.0,
                            "negative probability in trinomial tree (unfloored regime) at step %d, node %d: "
                                    + "p1=%s, p2=%s, p3=%s (v=%s, dx=%s, e=%s)",
                            i, j, p1, p2, p3, v, dxNext, e);
                }

                branching.add(temp, p1, p2, p3);
            }
            branchings_.add(branching);

            jMin = branching.jMin();
            jMax = branching.jMax();
        }

    }

    public double dx(final int i) {
        return dx_.get(i).doubleValue();
    }

    public TimeGrid timeGrid() {
        return timeGrid_;
    }

    @Override
    public int size(final int i) {
        return i == 0 ? 1 : branchings_.get(i - 1).size();
    }

    @Override
    public double underlying(final int i, final int index) {
        if ( i == 0 )
            return x0_;
        else
            return x0_ + (branchings_.get(i - 1).jMin() + (double) (index)) * dx(i);
    }

    @Override
    public int descendant(final int i, final int index, final int branch) {
        return branchings_.get(i).descendant(index, branch);
    }

    @Override
    public double probability(final int i, final int index, final int branch) {
        return branchings_.get(i).probability(index, branch);
    }

    private static class Branching {

        private final Vector< Integer > k_ = new Vector<>();
        private final Vector< Vector< Double > > probs_ = new Vector<>(3);
        private int kMin_, jMin_, kMax_, jMax_;

        public Branching() {
            // Phase 2c WI-5 align: C++ initializes probs_ with three empty
            // per-branch vectors (probs_(3)); Branching.add() then pushes
            // one element to each. The previous Java code created brand-new
            // 1-element vectors per add() call and appended them, which
            // made probs_ grow as 3*N entries and probability(i, b) only
            // ever return the b-th column of the FIRST node (and throw on
            // any other index). See trinomialtree.hpp ctor at line 105-107.
            probs_.add(new Vector<>());
            probs_.add(new Vector<>());
            probs_.add(new Vector<>());
            kMin_ = Integer.MAX_VALUE;
            jMin_ = Integer.MAX_VALUE;
            kMax_ = Integer.MIN_VALUE;
            jMax_ = Integer.MIN_VALUE;
        }

        public int descendant(final int index, final int branch) {
            return k_.elementAt(index) - jMin_ - 1 + branch;
        }

        public double probability(final int index, final int branch) {
            return probs_.elementAt(branch).elementAt(index);
        }

        public int size() {
            return jMax_ - jMin_ + 1;
        }

        public int jMin() {
            return jMin_;
        }

        public int jMax() {
            return jMax_;
        }

        public void add(final int k, final double p1, final double p2, final double p3) {
            // store: append one prob per branch to the corresponding column
            // (matching C++ Branching::add's probs_[0/1/2].push_back).
            k_.add(k);
            probs_.elementAt(0).add(new Double(p1));
            probs_.elementAt(1).add(new Double(p2));
            probs_.elementAt(2).add(new Double(p3));

            // maintain invariants
            kMin_ = Math.min(kMin_, k);
            jMin_ = kMin_ - 1;
            kMax_ = Math.max(kMax_, k);
            jMax_ = kMax_ + 1;
        }

    }

}
