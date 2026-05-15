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
 Copyright (C) 2009 Dirk Eddelbuettel
 Copyright (C) 2006, 2009, 2010 Klaus Spanderen
 Copyright (C) 2010 Kakhkhor Abdijalilov
 Copyright (C) 2010 Slava Mazur
*/

package org.jquantlib.math;

import java.util.List;

/**
 * General linear least squares regression — backward-compatibility wrapper.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/math/linearleastsquaresregression.hpp::LinearLeastSquaresRegression}
 * (Phase 5e.5b-CFC-d-16b). Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>This interface is supported for backward compatibility only — please
 * use {@link GeneralLinearLeastSquares} directly.
 *
 * @author JQuantLib
 */
public class LinearLeastSquaresRegression extends GeneralLinearLeastSquares {

    public LinearLeastSquaresRegression(final double[] x,
                                        final double[] y,
                                        final List<? extends Ops.DoubleOp> v) {
        super(x, y, v);
    }
}
