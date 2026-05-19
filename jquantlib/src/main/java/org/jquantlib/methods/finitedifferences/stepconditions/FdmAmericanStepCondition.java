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
package org.jquantlib.methods.finitedifferences.stepconditions;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.finitedifferences.StepCondition;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpIterator;
import org.jquantlib.methods.finitedifferences.utilities.FdmInnerValueCalculator;

/**
 * American exercise step condition for multi-dimensional FDM problems.
 * <p>
 * Java port of v1.42.1 {@code ql/methods/finitedifferences/stepconditions/fdmamericanstepcondition.{hpp,cpp}}.
 * <p>
 * At each time step for {@code t >= exerciseStart_}, iterates over all mesh cells and replaces the value array entry
 * with the intrinsic (inner) value whenever the intrinsic value exceeds the current grid value. If
 * {@code t < exerciseStart_} the array is left unchanged (used for options that only become American after some
 * forward-start date).
 *
 * @author Phase 2l Track B port
 */
public class FdmAmericanStepCondition implements StepCondition< Array > {

    private final FdmMesher mesher_;
    private final FdmInnerValueCalculator calculator_;
    private final double exerciseStart_;

    /**
     * @param mesher        the FDM mesh
     * @param calculator    inner value (intrinsic payoff) calculator
     * @param exerciseStart time from which American exercise is active (default 0.0)
     */
    public FdmAmericanStepCondition(final FdmMesher mesher, final FdmInnerValueCalculator calculator,
            final double exerciseStart) {
        this.mesher_ = mesher;
        this.calculator_ = calculator;
        this.exerciseStart_ = exerciseStart;
    }

    /**
     * Convenience constructor with {@code exerciseStart = 0.0}.
     */
    public FdmAmericanStepCondition(final FdmMesher mesher, final FdmInnerValueCalculator calculator) {
        this(mesher, calculator, 0.0);
    }

    /**
     * Apply the American exercise condition.
     * <p>
     * If {@code t < exerciseStart_}, does nothing. Otherwise, for each cell, replaces {@code a[i]} with
     * {@code max(a[i], innerValue(i, t))}.
     */
    @Override
    public void applyTo(final Array a, final double t) {
        if ( t < exerciseStart_ ) {
            return;
        }

        QL.require(mesher_.layout().size() == a.size(), "inconsistent array dimensions");

        for ( final FdmLinearOpIterator iter : mesher_.layout() ) {
            final double innerValue = calculator_.innerValue(iter, t);
            if ( innerValue > a.get(iter.index()) ) {
                a.set(iter.index(), innerValue);
            }
        }
    }
}
