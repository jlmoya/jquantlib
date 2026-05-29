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

package org.jquantlib.cashflow;

import org.jquantlib.math.matrixutilities.Array;

/**
 * Package-private vector-access helper shared by the digital leg builders ({@link DigitalCmsLeg},
 * {@link DigitalIborLeg}).
 * <p>
 * Mirror of C++ QuantLib v1.42.1 {@code QuantLib::detail::get(const std::vector<T>&, Size, U)}
 * (ql/utilities/vectors.hpp:32-43): if the vector is empty, the default value is returned; if {@code i} is in range, the
 * {@code i}-th element is returned; otherwise the last element is returned.
 */
final class DigitalLegUtil {

    private DigitalLegUtil() {
        // utility class — not instantiable
    }

    static double get(final Array v, final int i, final double defaultValue) {
        if ( v == null || v.empty() ) {
            return defaultValue;
        }
        if ( i < v.size() ) {
            return v.get(i);
        }
        return v.get(v.size() - 1);
    }

}
