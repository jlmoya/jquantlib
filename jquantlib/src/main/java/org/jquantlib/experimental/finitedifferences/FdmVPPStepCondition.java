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
 Copyright (C) 2011, 2012 Klaus Spanderen
 */
package org.jquantlib.experimental.finitedifferences;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.finitedifferences.StepCondition;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpIterator;
import org.jquantlib.methods.finitedifferences.utilities.FdmInnerValueCalculator;

/**
 * Virtual power plant (VPP) step condition for FD models.
 *
 * <p>Ported from C++ QuantLib v1.42.1
 * {@code ql/experimental/finitedifferences/fdmvppstepcondition.{hpp,cpp}}.</p>
 *
 * <p>At every time step the condition adds the current-state running-spread
 * payoff to the state array (along the state axis), then performs an
 * in-place dynamic-programming sweep that picks the best transition out of
 * each state. The concrete state-transition policy is supplied by the
 * subclass via {@link #changeState(double, Array, double)}.</p>
 *
 * <p>The VPP state set is parametrised by {@code tMinUp} (minimum up-time)
 * and {@code tMinDown} (minimum down-time): the first {@code tMinUp}
 * states are "running at {@code pMin}", the next {@code tMinUp} states are
 * "running at {@code pMax}", and the last {@code tMinDown} states are
 * "down". Each subclass may multiplex this block by an extra cardinality
 * (e.g. number of remaining starts) to track an additional resource.</p>
 *
 * @author Phase 5e.5b-CFC-d-287 port
 */
public abstract class FdmVPPStepCondition implements StepCondition<Array> {

    /** Plain-data carrier for the VPP step-condition parameters. */
    public static final class Params {
        public final double heatRate;
        public final double pMin;
        public final double pMax;
        public final int tMinUp;
        public final int tMinDown;
        public final double startUpFuel;
        public final double startUpFixCost;
        public final double fuelCostAddon;

        public Params(final double heatRate,
                      final double pMin, final double pMax,
                      final int tMinUp, final int tMinDown,
                      final double startUpFuel,
                      final double startUpFixCost,
                      final double fuelCostAddon) {
            this.heatRate = heatRate;
            this.pMin = pMin;
            this.pMax = pMax;
            this.tMinUp = tMinUp;
            this.tMinDown = tMinDown;
            this.startUpFuel = startUpFuel;
            this.startUpFixCost = startUpFixCost;
            this.fuelCostAddon = fuelCostAddon;
        }
    }

    /** Plain-data carrier for the mesh / state-axis descriptor. */
    public static final class Mesher {
        public final int stateDirection;
        public final FdmMesher mesher;

        public Mesher(final int stateDirection, final FdmMesher mesher) {
            this.stateDirection = stateDirection;
            this.mesher = mesher;
        }
    }

    /** Functional interface mirroring C++ {@code std::function<Real(Real)>}. */
    private interface UnaryReal {
        double op(double x);
    }

    protected final double heatRate_;
    protected final double pMin_;
    protected final double pMax_;
    protected final int tMinUp_;
    protected final int tMinDown_;
    protected final double startUpFuel_;
    protected final double startUpFixCost_;
    protected final double fuelCostAddon_;
    protected final int stateDirection_;
    protected final int nStates_;

    protected final FdmMesher mesher_;
    protected final FdmInnerValueCalculator gasPrice_;
    protected final FdmInnerValueCalculator sparkSpreadPrice_;

    private final UnaryReal[] stateEvolveFcts_;

    protected FdmVPPStepCondition(final Params params,
                                  final int nStates,
                                  final Mesher mesh,
                                  final FdmInnerValueCalculator gasPrice,
                                  final FdmInnerValueCalculator sparkSpreadPrice) {
        this.heatRate_ = params.heatRate;
        this.pMin_ = params.pMin;
        this.pMax_ = params.pMax;
        this.tMinUp_ = params.tMinUp;
        this.tMinDown_ = params.tMinDown;
        this.startUpFuel_ = params.startUpFuel;
        this.startUpFixCost_ = params.startUpFixCost;
        this.fuelCostAddon_ = params.fuelCostAddon;
        this.stateDirection_ = mesh.stateDirection;
        this.nStates_ = nStates;
        this.mesher_ = mesh.mesher;
        this.gasPrice_ = gasPrice;
        this.sparkSpreadPrice_ = sparkSpreadPrice;

        QL.require(nStates_ == mesher_.layout().dim()[stateDirection_],
                "mesher does not fit to vpp arguments");

        this.stateEvolveFcts_ = new UnaryReal[nStates_];

        for (int i = 0; i < nStates_; ++i) {
            final int j = i % (2 * tMinUp_ + tMinDown_);

            if (j < tMinUp_) {
                stateEvolveFcts_[i] = new UnaryReal() {
                    @Override public double op(final double x) {
                        return evolveAtPMin(x);
                    }
                };
            } else if (j < 2 * tMinUp_) {
                stateEvolveFcts_[i] = new UnaryReal() {
                    @Override public double op(final double x) {
                        return evolveAtPMax(x);
                    }
                };
            }
            // else stateEvolveFcts_[i] remains null -> evolve returns 0.
        }
    }

    public int nStates() {
        return nStates_;
    }

    @Override
    public void applyTo(final Array a, final double t) {
        final int nStates = mesher_.layout().dim()[stateDirection_];

        // 1) Pointwise: a[i] += evolve(iter, t).
        for (final FdmLinearOpIterator iter : mesher_.layout()) {
            a.set(iter.index(), a.get(iter.index()) + evolve(iter, t));
        }

        // 2) DP sweep: for every cell with state==0, gather the full
        //    state column, ask the subclass for the best next-state value
        //    given the current gas price, then write it back.
        for (final FdmLinearOpIterator iter : mesher_.layout()) {
            if (iter.coordinates()[stateDirection_] == 0) {

                final Array x = new Array(nStates);
                for (int i = 0; i < nStates; ++i) {
                    final int idx = mesher_.layout().neighbourhood(
                            iter, stateDirection_, i);
                    x.set(i, a.get(idx));
                }

                final double gasPrice = gasPrice_.innerValue(iter, t);
                final Array updated = changeState(gasPrice, x, t);
                for (int i = 0; i < nStates; ++i) {
                    final int idx = mesher_.layout().neighbourhood(
                            iter, stateDirection_, i);
                    a.set(idx, updated.get(i));
                }
            }
        }
    }

    /**
     * Compute the per-step value contribution given the current state.
     *
     * <p>For "running at {@code pMin}" states the spark spread is
     * accumulated at {@code pMin}; for "running at {@code pMax}" the
     * spread is accumulated at {@code pMax}; for "down" states the
     * contribution is zero.</p>
     */
    public double evolve(final FdmLinearOpIterator iter, final double t) {
        final int state = iter.coordinates()[stateDirection_];
        if (stateEvolveFcts_[state] == null) {
            return 0.0;
        }
        final double sparkSpread = sparkSpreadPrice_.innerValue(iter, t);
        return stateEvolveFcts_[state].op(sparkSpread);
    }

    protected double evolveAtPMin(final double sparkSpread) {
        return pMin_ * (sparkSpread - heatRate_ * fuelCostAddon_);
    }

    protected double evolveAtPMax(final double sparkSpread) {
        return pMax_ * (sparkSpread - heatRate_ * fuelCostAddon_);
    }

    /**
     * Subclass hook: given the current gas price and the column of
     * state-values {@code state}, return the column of optimal-action
     * state-values.
     */
    protected abstract Array changeState(double gasPrice, Array state, double t);

    /** Subclass hook: report the maximum value across the final-state array. */
    public abstract double maxValue(Array states);
}
