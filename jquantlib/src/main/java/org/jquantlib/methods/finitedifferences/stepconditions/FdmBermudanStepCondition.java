/*
 Copyright (C) 2010 Klaus Spanderen

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
package org.jquantlib.methods.finitedifferences.stepconditions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.finitedifferences.StepCondition;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpIterator;
import org.jquantlib.methods.finitedifferences.utilities.FdmInnerValueCalculator;
import org.jquantlib.time.Date;

/**
 * Bermudan exercise step condition for multi-dimensional FDM problems.
 * <p>
 * Java port of v1.42.1
 * {@code ql/methods/finitedifferences/stepconditions/fdmbermudanstepcondition.{hpp,cpp}}.
 * <p>
 * Converts a list of {@link Date} exercise dates into continuous time fractions
 * (using the supplied day counter and reference date). At each time step the
 * condition checks whether the current time {@code t} matches one of those
 * exercise times; if so, it replaces each cell's value with the maximum of
 * the current value and the inner (intrinsic) value.
 *
 * @author Phase 2l Track B port
 */
public class FdmBermudanStepCondition implements StepCondition<Array> {

    private final List<Double> exerciseTimes_;
    private final FdmMesher mesher_;
    private final FdmInnerValueCalculator calculator_;

    /**
     * @param exerciseDates list of Bermudan exercise dates
     * @param referenceDate reference date for year-fraction computation
     * @param dayCounter    day counter used for year fractions
     * @param mesher        the FDM mesh
     * @param calculator    inner value (intrinsic payoff) calculator
     */
    public FdmBermudanStepCondition(
            final List<Date> exerciseDates,
            final Date referenceDate,
            final DayCounter dayCounter,
            final FdmMesher mesher,
            final FdmInnerValueCalculator calculator) {
        this.mesher_ = mesher;
        this.calculator_ = calculator;

        exerciseTimes_ = new ArrayList<>(exerciseDates.size());
        for (final Date d : exerciseDates) {
            exerciseTimes_.add(dayCounter.yearFraction(referenceDate, d));
        }
    }

    /**
     * Returns the exercise times derived from the supplied exercise dates.
     */
    public List<Double> exerciseTimes() {
        return Collections.unmodifiableList(exerciseTimes_);
    }

    /**
     * Apply the Bermudan exercise condition.
     * <p>
     * If {@code t} is one of the exercise times, iterates over all mesh cells
     * and replaces {@code a[i]} with {@code max(a[i], innerValue(i, t))}.
     * Otherwise the array is left unchanged.
     */
    @Override
    public void applyTo(final Array a, final double t) {
        if (!exerciseTimes_.contains(t)) {
            return;
        }

        QL.require(mesher_.layout().size() == a.size(),
                "inconsistent array dimensions");

        for (final FdmLinearOpIterator iter : mesher_.layout()) {
            final double innerValue = calculator_.innerValue(iter, t);
            if (innerValue > a.get(iter.index())) {
                a.set(iter.index(), innerValue);
            }
        }
    }
}
