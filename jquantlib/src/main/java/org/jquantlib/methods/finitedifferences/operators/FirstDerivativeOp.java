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
 * Central-difference first derivative on a non-uniform 1D grid.
 * <p>
 * Java port of v1.42.1
 * ql/methods/finitedifferences/operators/firstderivativeop.{hpp,cpp}.
 * <p>
 * At interior cells:
 * {@code u'(x) ~= (-h_+/(h_-(h_-+h_+))) u_{i-1} + ((h_+ - h_-)/(h_-h_+)) u_i + (h_-/(h_+(h_-+h_+))) u_{i+1}}.
 * Boundaries fall back to upwind / downwind one-sided differences.
 *
 * @author Phase 2h WI-1 port
 */
public class FirstDerivativeOp extends TripleBandLinearOp {

    public FirstDerivativeOp(final int direction, final FdmMesher mesher) {
        super(direction, mesher);

        for (final FdmLinearOpIterator iter : mesher.layout()) {
            final int i = iter.index();
            final double hm = mesher.dminus(iter, this.direction);
            final double hp = mesher.dplus(iter, this.direction);

            final double zetam1 = hm * (hm + hp);
            final double zeta0  = hm * hp;
            final double zetap1 = hp * (hm + hp);

            final int co = iter.coordinates()[this.direction];
            if (co == 0) {
                // upwinding scheme
                lower[i] = 0.0;
                upper[i] = 1.0 / hp;
                diag[i]  = -upper[i];
            } else if (co == mesher.layout().dim()[this.direction] - 1) {
                // downwinding scheme
                diag[i]  = 1.0 / hm;
                lower[i] = -diag[i];
                upper[i] = 0.0;
            } else {
                lower[i] = -hp / zetam1;
                diag[i]  = (hp - hm) / zeta0;
                upper[i] = hm / zetap1;
            }
        }
    }
}
