/*
 Copyright (C) 2012 Klaus Spanderen

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
package org.jquantlib.methods.finitedifferences.schemes;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOp;
import org.jquantlib.methods.finitedifferences.utilities.BoundaryCondition;
import org.jquantlib.methods.finitedifferences.utilities.FdmBoundaryConditionSet;

/**
 * Helper that fans out boundary-condition calls to every
 * {@link BoundaryCondition} in an {@link FdmBoundaryConditionSet}.
 * <p>
 * Java port of v1.42.1
 * ql/methods/finitedifferences/schemes/boundaryconditionschemehelper.hpp.
 *
 * @author Phase 2h WI-1 port
 */
final class BoundaryConditionSchemeHelper {

    private final FdmBoundaryConditionSet bcSet;

    BoundaryConditionSchemeHelper(final FdmBoundaryConditionSet bcSet) {
        this.bcSet = bcSet;
    }

    void applyBeforeApplying(final FdmLinearOp op) {
        for (final BoundaryCondition<FdmLinearOp> bc : bcSet) {
            bc.applyBeforeApplying(op);
        }
    }

    void applyBeforeSolving(final FdmLinearOp op, final Array a) {
        for (final BoundaryCondition<FdmLinearOp> bc : bcSet) {
            bc.applyBeforeSolving(op, a);
        }
    }

    void applyAfterApplying(final Array a) {
        for (final BoundaryCondition<FdmLinearOp> bc : bcSet) {
            bc.applyAfterApplying(a);
        }
    }

    void applyAfterSolving(final Array a) {
        for (final BoundaryCondition<FdmLinearOp> bc : bcSet) {
            bc.applyAfterSolving(a);
        }
    }

    void setTime(final double t) {
        for (final BoundaryCondition<FdmLinearOp> bc : bcSet) {
            bc.setTime(t);
        }
    }
}
