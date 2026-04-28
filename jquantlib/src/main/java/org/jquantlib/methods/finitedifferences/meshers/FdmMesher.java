/*
 Copyright (C) 2008 Andreas Gaida
 Copyright (C) 2008 Ralph Schreyer
 Copyright (C) 2008 Klaus Spanderen

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
package org.jquantlib.methods.finitedifferences.meshers;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpIterator;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpLayout;

/**
 * Multi-dimensional Fdm grid mesher.
 * <p>
 * Java port of v1.42.1
 * ql/methods/finitedifferences/meshers/fdmmesher.hpp.
 * <p>
 * Phase 2h WI-1 sub-layer 1.1 introduces this interface as a forward
 * declaration so the operator classes (FdmHullWhiteOp, FdmG2Op,
 * FirstDerivativeOp, SecondDerivativeOp, NinePointLinearOp,
 * SecondOrderMixedDerivativeOp, TripleBandLinearOp) can compile.
 * Sub-layer 1.2 supplies the concrete {@code Fdm1dMesher},
 * {@code FdmSimpleProcess1dMesher}, and {@code FdmMesherComposite}
 * implementations.
 *
 * @author Phase 2h WI-1 port
 */
public interface FdmMesher {

    /**
     * Forward-mesh-spacing at the cell pointed to by {@code iter} along
     * direction {@code direction}: {@code locations[direction][i+1] - locations[direction][i]}.
     */
    double dplus(final FdmLinearOpIterator iter, final int direction);

    /**
     * Backward-mesh-spacing at the cell pointed to by {@code iter} along
     * direction {@code direction}: {@code locations[direction][i] - locations[direction][i-1]}.
     */
    double dminus(final FdmLinearOpIterator iter, final int direction);

    /** Mesh location of {@code iter} along direction {@code direction}. */
    double location(final FdmLinearOpIterator iter, final int direction);

    /** Per-direction mesh locations as an Array (same size as the layout). */
    Array locations(final int direction);

    /** Layout of the mesh (size + per-direction extents). */
    FdmLinearOpLayout layout();
}
