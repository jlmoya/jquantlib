/*
 Copyright (C) 2012 Peter Caspers

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

import org.jquantlib.methods.finitedifferences.operators.FdmLinearOp;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Typed list of boundary conditions for the Fdm framework.
 * <p>
 * Java port of v1.42.1 ql/methods/finitedifferences/utilities/fdmboundaryconditionset.hpp which on the C++ side is just
 * the typedef
 * <pre>
 * typedef OperatorTraits&lt;FdmLinearOp&gt;::bc_set FdmBoundaryConditionSet;
 * </pre>
 * i.e. {@code std::vector<ext::shared_ptr<BoundaryCondition<FdmLinearOp>>>}.
 * <p>
 * Java does not have type aliases, so we materialize this as a thin subclass of {@link ArrayList} with the right
 * element type. The schemes (sub-layer 1.4) accept this type directly; sub-layer 1.3 will populate it with concrete
 * {@link BoundaryCondition} implementations.
 *
 * @author Phase 2h WI-1 port
 */
public final class FdmBoundaryConditionSet extends ArrayList< BoundaryCondition< FdmLinearOp > > {

    private static final long serialVersionUID = 1L;

    /** Empty boundary condition set. */
    public FdmBoundaryConditionSet() {
        super();
    }

    /** Pre-populated boundary condition set. */
    public FdmBoundaryConditionSet(final Collection< ? extends BoundaryCondition< FdmLinearOp > > initial) {
        super(initial);
    }
}
