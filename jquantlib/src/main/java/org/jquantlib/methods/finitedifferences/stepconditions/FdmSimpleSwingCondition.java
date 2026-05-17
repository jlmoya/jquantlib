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
 Copyright (C) 2010, 2014 Klaus Spanderen
 */
package org.jquantlib.methods.finitedifferences.stepconditions;

import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.finitedifferences.StepCondition;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpIterator;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpLayout;
import org.jquantlib.methods.finitedifferences.utilities.FdmInnerValueCalculator;

/**
 * Simple swing-style exercise step condition.
 *
 * <p>Java port of v1.42.1
 * {@code ql/methods/finitedifferences/stepconditions/fdmsimpleswingcondition.{hpp,cpp}}.
 *
 * <p>Encodes a (max-rights+1)-tall axis where the {@code k}-th slice represents
 * "number of exercise rights already used". On each exercise time, the
 * condition decides whether to consume one more right at each spot grid node:
 * the value at slice {@code k} is replaced by the value at slice {@code k+1}
 * plus the immediate cash-flow ({@code calculator.innerValue}) iff doing so
 * is better than holding, or iff the remaining-time-window forces a minimum
 * exercise count (the {@code exercisesUsed + d <= minExercises} clause, where
 * {@code d} is the number of exercise opportunities still to come — including
 * the current one).
 *
 * <p>{@code maxExerciseValue} (== dim[swingDirection] − 1) is the saturation
 * slice: no further rights can be used there.
 *
 * @author Phase 5e.5b-CFC-d-170 port
 */
public class FdmSimpleSwingCondition implements StepCondition<Array> {

    private final List<Double> exerciseTimes_;
    private final FdmMesher mesher_;
    private final FdmInnerValueCalculator calculator_;
    private final int minExercises_;
    private final int swingDirection_;

    public FdmSimpleSwingCondition(final List<Double> exerciseTimes,
                                   final FdmMesher mesher,
                                   final FdmInnerValueCalculator calculator,
                                   final int swingDirection) {
        this(exerciseTimes, mesher, calculator, swingDirection, 0);
    }

    public FdmSimpleSwingCondition(final List<Double> exerciseTimes,
                                   final FdmMesher mesher,
                                   final FdmInnerValueCalculator calculator,
                                   final int swingDirection,
                                   final int minExercises) {
        this.exerciseTimes_  = exerciseTimes;
        this.mesher_         = mesher;
        this.calculator_     = calculator;
        this.minExercises_   = minExercises;
        this.swingDirection_ = swingDirection;
    }

    @Override
    public void applyTo(final Array a, final double t) {
        // C++ uses std::find with exact equality on Time; mirror that.
        int hit = -1;
        for (int i = 0; i < exerciseTimes_.size(); ++i) {
            if (exerciseTimes_.get(i) == t) {
                hit = i;
                break;
            }
        }
        if (hit < 0) {
            return;
        }

        final FdmLinearOpLayout layout = mesher_.layout();
        final int maxExerciseValue = layout.dim()[swingDirection_] - 1;

        QL.require(layout.size() == a.size(),
                "inconsistent array dimensions");

        // distance(iter, end) in C++ — number of remaining exercise dates
        // INCLUDING the current one (i.e., size - hit).
        final int d = exerciseTimes_.size() - hit;

        // Snapshot a (C++: Array retVal = a; then writes through retVal).
        // Critical for in-place updates because we read neighbour slices
        // of the same array — using the snapshot keeps the read-side stable.
        final Array retVal = a.clone();

        for (final FdmLinearOpIterator iter : layout) {
            final int[] coor = iter.coordinates();
            final int exercisesUsed = coor[swingDirection_];

            if (exercisesUsed < maxExerciseValue) {
                final double cashflow = calculator_.innerValue(iter, t);
                final double currentValue = a.get(iter.index());
                final double valuePlusOneExercise =
                        a.get(layout.neighbourhood(iter, swingDirection_, 1));

                if (currentValue < valuePlusOneExercise + cashflow
                        || exercisesUsed + d <= minExercises_) {
                    retVal.set(iter.index(), valuePlusOneExercise + cashflow);
                }
            }
        }

        // a = retVal: copy values back into the in-place array.
        for (int i = 0; i < a.size(); ++i) {
            a.set(i, retVal.get(i));
        }
    }
}
