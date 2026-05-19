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
 Copyright (C) 2014 Peter Caspers
 */
package org.jquantlib.experimental.finitedifferences;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpComposite;
import org.jquantlib.methods.finitedifferences.operators.SecondDerivativeOp;
import org.jquantlib.methods.finitedifferences.operators.TripleBandLinearOp;

import java.util.Collections;
import java.util.List;

/**
 * Dupire local-volatility pricing operator.
 * <p>
 * Java port of v1.42.1 {@code ql/experimental/finitedifferences/fdmdupire1dop.{hpp,cpp}}.
 * <p>
 * Implements the operator {@code 0.5 * sigma_loc(S)^2 * d^2/dS^2} on a 1-D mesh. Time is reversed (the operator is
 * time-independent in the local-vol formulation; {@link #setTime} is a no-op) so that backward solvers can be used
 * directly.
 *
 * @author Phase 4n WI port
 */
public class FdmDupire1dOp implements FdmLinearOpComposite {

    private final FdmMesher mesher_;
    private final Array localVolatility_;
    private final TripleBandLinearOp mapT_;

    public FdmDupire1dOp(final FdmMesher mesher, final Array localVolatility) {
        this.mesher_ = mesher;
        this.localVolatility_ = localVolatility;
        // 0.5 * localVol^2 * d^2/dS^2
        final Array half = new Array(localVolatility.size());
        for ( int i = 0; i < localVolatility.size(); ++i ) {
            final double v = localVolatility.get(i);
            half.set(i, 0.5 * v * v);
        }
        this.mapT_ = new SecondDerivativeOp(0, mesher).mult(half);
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public void setTime(final double t1, final double t2) {
        // time-independent operator
    }

    @Override
    public Array apply(final Array u) {
        return mapT_.apply(u);
    }

    @Override
    public Array applyMixed(final Array r) {
        return r;
    }

    @Override
    public Array applyDirection(final int direction, final Array r) {
        if ( direction == 0 ) {
            return mapT_.apply(r);
        }
        QL.error("direction too large");
        return null;
    }

    @Override
    public Array solveSplitting(final int direction, final Array r, final double a) {
        if ( direction == 0 ) {
            return mapT_.solveSplitting(r, a, 1.0);
        }
        QL.error("direction too large");
        return null;
    }

    @Override
    public Array preconditioner(final Array r, final double dt) {
        return solveSplitting(0, r, dt);
    }

    @Override
    public List< Matrix > toMatrixDecomp() {
        return Collections.singletonList(mapT_.toMatrix());
    }

    @Override
    public Matrix toMatrix() {
        return mapT_.toMatrix();
    }
}
