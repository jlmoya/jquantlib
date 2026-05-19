/*
 Copyright (C) 2000, 2001, 2002, 2003 RiskMap srl

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
package org.jquantlib.methods.finitedifferences.utilities;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOp;

/**
 * Boundary condition for an Fdm-shape (modern) finite-difference operator.
 * <p>
 * Java port of v1.42.1 ql/methods/finitedifferences/boundarycondition.hpp (the {@code BoundaryCondition<Operator>}
 * template, instantiated for {@link FdmLinearOp}-shape operators used by the Fdm framework).
 * <p>
 * <strong>Note:</strong> the legacy
 * {@code org.jquantlib.methods.finitedifferences.BoundaryCondition} interface targets the pre-2010 {@code Operator}
 * (TridiagonalOperator) hierarchy and is unrelated; both will coexist until the legacy framework is retired. Phase 2h
 * sub-layer 1.4 introduces this interface as a forward declaration so the schemes can compile; sub-layer 1.3 supplies
 * the concrete {@code FdmDirichletBoundary} / {@code FdmDiscountDirichletBoundary} implementations.
 *
 * @param <O> the operator type to which this boundary condition applies
 * @author Phase 2h WI-1 port
 */
public interface BoundaryCondition< O extends FdmLinearOp > {

    /**
     * Modify the operator {@code op} before it is applied to an array {@code u} so that {@code v = op * u} satisfies
     * the boundary condition.
     */
    void applyBeforeApplying(O op);

    /**
     * Modify the array {@code a} so that it satisfies the boundary condition.
     */
    void applyAfterApplying(Array a);

    /**
     * Modify the operator {@code op} before the linear system {@code op * x = rhs} is solved so that {@code x}
     * satisfies the boundary condition.
     */
    void applyBeforeSolving(O op, Array rhs);

    /**
     * Modify the array {@code a} (the just-solved {@code x}) so that it satisfies the boundary condition.
     */
    void applyAfterSolving(Array a);

    /**
     * Set the current time; called by the schemes between rollback steps for time-dependent boundary conditions.
     */
    void setTime(double t);

    /** Boundary side enumeration. */
    enum Side {None, Upper, Lower}
}
