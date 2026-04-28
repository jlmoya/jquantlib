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
 * Second-order mixed cross-derivative operator: {@code ∂²/∂x∂y} with
 * appropriate interior / edge / corner stencils.
 * <p>
 * Java port of v1.42.1
 * ql/methods/finitedifferences/operators/secondordermixedderivativeop.{hpp,cpp}.
 * <p>
 * Used by {@link FdmG2Op} to discretize the
 * {@code rho * sigma * eta * ∂²/∂x∂y} cross-correlation term.
 *
 * @author Phase 2h WI-1 port
 */
public class SecondOrderMixedDerivativeOp extends NinePointLinearOp {

    public SecondOrderMixedDerivativeOp(final int d0, final int d1, final FdmMesher mesher) {
        super(d0, d1, mesher);

        for (final FdmLinearOpIterator iter : mesher.layout()) {
            final int i = iter.index();
            final double hm_d0 = mesher.dminus(iter, this.d0);
            final double hp_d0 = mesher.dplus(iter, this.d0);
            final double hm_d1 = mesher.dminus(iter, this.d1);
            final double hp_d1 = mesher.dplus(iter, this.d1);

            final double zetam1 = hm_d0 * (hm_d0 + hp_d0);
            final double zeta0  = hm_d0 * hp_d0;
            final double zetap1 = hp_d0 * (hm_d0 + hp_d0);
            final double phim1  = hm_d1 * (hm_d1 + hp_d1);
            final double phi0   = hm_d1 * hp_d1;
            final double phip1  = hp_d1 * (hm_d1 + hp_d1);

            final int c0 = iter.coordinates()[this.d0];
            final int c1 = iter.coordinates()[this.d1];
            final int n0 = mesher.layout().dim()[this.d0];
            final int n1 = mesher.layout().dim()[this.d1];

            if (c0 == 0 && c1 == 0) {
                // lower left corner
                a00[i] = a01[i] = a02[i] = a10[i] = a20[i] = 0.0;
                final double v = 1.0 / (hp_d0 * hp_d1);
                a11[i] = v; a22[i] = v;
                a21[i] = -v; a12[i] = -v;
            } else if (c0 == n0 - 1 && c1 == 0) {
                // upper left corner
                a22[i] = a21[i] = a20[i] = a10[i] = a00[i] = 0.0;
                final double v = 1.0 / (hm_d0 * hp_d1);
                a01[i] = v; a12[i] = v;
                a11[i] = -v; a02[i] = -v;
            } else if (c0 == 0 && c1 == n1 - 1) {
                // lower right corner
                a00[i] = a01[i] = a02[i] = a12[i] = a22[i] = 0.0;
                final double v = 1.0 / (hp_d0 * hm_d1);
                a10[i] = v; a21[i] = v;
                a20[i] = -v; a11[i] = -v;
            } else if (c0 == n0 - 1 && c1 == n1 - 1) {
                // upper right corner
                a20[i] = a21[i] = a22[i] = a12[i] = a02[i] = 0.0;
                final double v = 1.0 / (hm_d0 * hm_d1);
                a00[i] = v; a11[i] = v;
                a10[i] = -v; a01[i] = -v;
            } else if (c0 == 0) {
                // lower side
                a00[i] = a01[i] = a02[i] = 0.0;
                a10[i] =  hp_d1 / (hp_d0 * phim1);
                a20[i] = -a10[i];
                a21[i] =  (hp_d1 - hm_d1) / (hp_d0 * phi0);
                a11[i] = -a21[i];
                a22[i] =  hm_d1 / (hp_d0 * phip1);
                a12[i] = -a22[i];
            } else if (c0 == n0 - 1) {
                // upper side
                a20[i] = a21[i] = a22[i] = 0.0;
                a00[i] =  hp_d1 / (hm_d0 * phim1);
                a10[i] = -a00[i];
                a11[i] =  (hp_d1 - hm_d1) / (hm_d0 * phi0);
                a01[i] = -a11[i];
                a12[i] =  hm_d1 / (hm_d0 * phip1);
                a02[i] = -a12[i];
            } else if (c1 == 0) {
                // left side
                a00[i] = a10[i] = a20[i] = 0.0;
                a01[i] =  hp_d0 / (zetam1 * hp_d1);
                a02[i] = -a01[i];
                a12[i] =  (hp_d0 - hm_d0) / (zeta0 * hp_d1);
                a11[i] = -a12[i];
                a22[i] =  hm_d0 / (zetap1 * hp_d1);
                a21[i] = -a22[i];
            } else if (c1 == n1 - 1) {
                // right side
                a22[i] = a12[i] = a02[i] = 0.0;
                a00[i] =  hp_d0 / (zetam1 * hm_d1);
                a01[i] = -a00[i];
                a11[i] =  (hp_d0 - hm_d0) / (zeta0 * hm_d1);
                a10[i] = -a11[i];
                a21[i] =  hm_d0 / (zetap1 * hm_d1);
                a20[i] = -a21[i];
            } else {
                // interior — full 9-point stencil
                a00[i] =  hp_d0 * hp_d1 / (zetam1 * phim1);
                a10[i] = -(hp_d0 - hm_d0) * hp_d1 / (zeta0 * phim1);
                a20[i] = -hm_d0 * hp_d1 / (zetap1 * phim1);
                a01[i] = -hp_d0 * (hp_d1 - hm_d1) / (zetam1 * phi0);
                a11[i] =  (hp_d0 - hm_d0) * (hp_d1 - hm_d1) / (zeta0 * phi0);
                a21[i] =  hm_d0 * (hp_d1 - hm_d1) / (zetap1 * phi0);
                a02[i] = -hp_d0 * hm_d1 / (zetam1 * phip1);
                a12[i] =  hm_d1 * (hp_d0 - hm_d0) / (zeta0 * phip1);
                a22[i] =  hm_d0 * hm_d1 / (zetap1 * phip1);
            }
        }
    }
}
