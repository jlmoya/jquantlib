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

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2009 Andrea Odetti

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.experimental.mcbasket;

import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;

/**
 * Path payoff that exposes an adapted-process valuation contract.
 *
 * <p>Phase 4i port of C++ QuantLib v1.42.1
 * {@code ql/experimental/mcbasket/adaptedpathpayoff.{hpp,cpp}}.
 * Pinned commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>Subclasses override {@link #operatorImpl(ValuationData)} (mirrors C++
 * {@code operator()(ValuationData&)}) to fill payment/exercise data via the
 * provided {@link ValuationData} accessor. The base
 * {@link #value(Matrix, List, Array, Array, List)} wraps the inputs into a
 * {@link ValuationData} and dispatches.
 *
 * <p>{@link ValuationData#setPayoffValue(int, double)} and
 * {@link ValuationData#setExerciseData(int, double, Array)} both require
 * {@code time >= maximumTimeRead_}, ensuring the payoff is adapted (does not
 * peek into the future).
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 *   <li>The C++ {@code operator()} method is renamed to
 *       {@link #operatorImpl(ValuationData)} since Java has no operator
 *       overloading.</li>
 *   <li>C++ {@code Size maximumTimeRead_ = 0} initialization preserved; the
 *       comment about {@code -1} not being viable for unsigned types
 *       carries over verbatim.</li>
 * </ul>
 */
public abstract class AdaptedPathPayoff extends PathPayoff {

    /**
     * Adapted-process accessor exposed to the payoff implementation.
     * Mirrors C++ {@code AdaptedPathPayoff::ValuationData}.
     */
    public static class ValuationData {

        private final Matrix path_;
        private final List<Handle<YieldTermStructure>> forwardTermStructures_;
        private final Array payments_;
        private final Array exercises_;
        private final List<Array> states_;

        // C++: Size maximumTimeRead_ = 0; (initialising to -1 would be
        // semantically clearer but Size is unsigned in C++; 0 has identical
        // behaviour). See class javadoc.
        private int maximumTimeRead_ = 0;

        ValuationData(final Matrix path,
                final List<Handle<YieldTermStructure>> forwardTermStructures,
                final Array payments, final Array exercises,
                final List<Array> states) {
            this.path_ = path;
            this.forwardTermStructures_ = forwardTermStructures;
            this.payments_ = payments;
            this.exercises_ = exercises;
            this.states_ = states;
        }

        public int numberOfTimes() {
            return path_.columns();
        }

        public int numberOfAssets() {
            return path_.rows();
        }

        public double getAssetValue(final int time, final int asset) {
            maximumTimeRead_ = Math.max(maximumTimeRead_, time);
            return path_.get(asset, time);
        }

        public Handle<YieldTermStructure> getYieldTermStructure(final int time) {
            maximumTimeRead_ = Math.max(maximumTimeRead_, time);
            return forwardTermStructures_.get(time);
        }

        /**
         * Sets the payoff value at {@code time}. Throws if {@code time}
         * lies before the maximum time read so far (would violate adapted
         * process constraint).
         */
        public void setPayoffValue(final int time, final double value) {
            QL.require(time >= maximumTimeRead_,
                    "not adapted payoff: looking into the future");
            payments_.set(time, value);
        }

        /**
         * Sets the exercise data at {@code time}. The supplied {@code state}
         * is swapped into {@code states_[time]} (mirrors C++ std::swap).
         */
        public void setExerciseData(final int time, final double exercise, final Array state) {
            QL.require(time >= maximumTimeRead_,
                    "not adapted payoff: looking into the future");

            if (exercises_ != null && !exercises_.empty()) {
                exercises_.set(time, exercise);
            }

            if (states_ != null && !states_.isEmpty()) {
                // C++ std::swap(states_[time], state). Java has no in-place
                // swap of caller-owned references; simply overwrite the slot.
                states_.set(time, state);
            }
        }
    }

    @Override
    public void value(final Matrix path,
            final List<Handle<YieldTermStructure>> forwardTermStructures,
            final Array payments, final Array exercises,
            final List<Array> states) {
        final ValuationData data = new ValuationData(path, forwardTermStructures,
                payments, exercises, states);
        operatorImpl(data);
    }

    /**
     * Key payoff entry point: subclasses fill in payments/exercises via the
     * {@link ValuationData} accessor. Mirrors C++
     * {@code virtual void operator()(ValuationData & data) const = 0}.
     */
    protected abstract void operatorImpl(final ValuationData data);
}
