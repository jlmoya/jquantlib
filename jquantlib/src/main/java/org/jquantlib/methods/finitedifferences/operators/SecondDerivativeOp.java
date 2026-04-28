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
package org.jquantlib.methods.finitedifferences.operators;

import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;

/**
 * Central-difference second derivative on a non-uniform 1D grid.
 * <p>
 * Java port of v1.42.1
 * ql/methods/finitedifferences/operators/secondderivativeop.{hpp,cpp}.
 * <p>
 * At interior cells:
 * {@code u''(x) ~= 2/(h_-(h_-+h_+)) u_{i-1} - 2/(h_-h_+) u_i + 2/(h_+(h_-+h_+)) u_{i+1}}.
 * Boundary cells are zeroed out (Dirichlet contribution comes from the
 * boundary-condition pass, not the operator).
 *
 * @author Phase 2h WI-1 port
 */
public class SecondDerivativeOp extends TripleBandLinearOp {

    public SecondDerivativeOp(final int direction, final FdmMesher mesher) {
        super(direction, mesher);

        for (final FdmLinearOpIterator iter : mesher.layout()) {
            final int i = iter.index();
            final double hm = mesher.dminus(iter, this.direction);
            final double hp = mesher.dplus(iter, this.direction);

            final double zetam1 = hm * (hm + hp);
            final double zeta0  = hm * hp;
            final double zetap1 = hp * (hm + hp);

            final int co = iter.coordinates()[this.direction];
            if (co == 0 || co == mesher.layout().dim()[this.direction] - 1) {
                lower[i] = 0.0;
                diag[i]  = 0.0;
                upper[i] = 0.0;
            } else {
                lower[i] =  2.0 / zetam1;
                diag[i]  = -2.0 / zeta0;
                upper[i] =  2.0 / zetap1;
            }
        }
    }
}
