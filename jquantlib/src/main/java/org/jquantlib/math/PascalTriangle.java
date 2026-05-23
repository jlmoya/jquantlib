/*
 Copyright (C) 2026 Jose Moya

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

package org.jquantlib.math;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Pascal triangle coefficients calculator.
 * <p>
 * Faithful port of {@code ql/math/pascaltriangle.{hpp,cpp}} from QuantLib
 * v1.42.1 @ {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>Stores one vector of coefficients after another. The C++ implementation
 * uses {@code BigNatural} (unsigned long); we use {@code long} here, matching
 * the JQuantLib idiom for unsigned counters/coefficients.
 *
 * @author Jose Moya
 */
public final class PascalTriangle {

    private static final List<long[]> coefficients = new ArrayList<>();

    private PascalTriangle() {
        // utility class
    }

    /**
     * Returns an immutable view of the coefficients for the requested order.
     * Rows up to and including {@code order} are computed (and cached) lazily.
     *
     * @param order the order of the desired Pascal-triangle row
     * @return the cached coefficient row {@code [C(n,0), C(n,1), ..., C(n,n)]}
     */
    public static synchronized List<Long> get(final int order) {
        if (coefficients.isEmpty()) {
            // order zero (mandatory bootstrap)
            coefficients.add(new long[]{1L});

            final long[] row1 = new long[2];
            Arrays.fill(row1, 1L);
            coefficients.add(row1);

            final long[] row2 = new long[]{1L, 2L, 1L};
            coefficients.add(row2);

            final long[] row3 = new long[]{1L, 3L, 3L, 1L};
            coefficients.add(row3);
        }
        while (coefficients.size() <= order) {
            nextOrder();
        }
        final long[] row = coefficients.get(order);
        final Long[] boxed = new Long[row.length];
        for (int i = 0; i < row.length; ++i) {
            boxed[i] = row[i];
        }
        return Collections.unmodifiableList(Arrays.asList(boxed));
    }

    private static void nextOrder() {
        final int order = coefficients.size();
        final long[] row = new long[order + 1];
        row[0] = 1L;
        row[order] = 1L;
        final long[] prev = coefficients.get(order - 1);
        for (int i = 1; i < order / 2 + 1; ++i) {
            row[i] = prev[i - 1] + prev[i];
            row[order - i] = row[i];
        }
        coefficients.add(row);
    }
}
