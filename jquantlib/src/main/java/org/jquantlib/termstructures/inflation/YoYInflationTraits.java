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
 Copyright (C) 2007 Chris Kenyon
 Copyright (C) 2007, 2008 StatPro Italia srl
 Copyright (C) 2011 Ferdinando Ametrano

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.termstructures.inflation;

import org.jquantlib.termstructures.YoYInflationTermStructure;
import org.jquantlib.time.Date;

/**
 * Bootstrap traits for {@code PiecewiseYoYInflationCurve} — Java port of
 * QuantLib v1.42.1 {@code YoYInflationTraits}
 * ({@code ql/termstructures/inflation/inflationtraits.hpp:118-191}).
 *
 * <p>Differences from the zero-side traits:
 * <ul>
 *   <li>{@code initialValue} returns {@code t.baseRate()} (the user-supplied
 *       base YoY rate at construction), not the {@link InflationTraits#AVG_INFLATION}
 *       constant — see C++ {@code YoYInflationTraits::initialValue}.</li>
 *   <li>{@code updateGuess} writes <strong>only</strong> {@code data[i]} —
 *       does NOT propagate {@code level} to {@code data[0]} as the zero-side
 *       traits do during the first-helper iteration.</li>
 * </ul>
 *
 * <p>Constants {@code AVG_INFLATION} and {@code MAX_INFLATION} are reused
 * from {@link InflationTraits} (same C++ {@code detail::} namespace constants).
 *
 * @see PiecewiseYoYInflationCurve
 */
public final class YoYInflationTraits {

    public static final double AVG_INFLATION = InflationTraits.AVG_INFLATION;
    public static final double MAX_INFLATION = InflationTraits.MAX_INFLATION;
    public static final int MAX_ITERATIONS = InflationTraits.MAX_ITERATIONS;

    /** Start of curve data — the curve's base date. */
    public Date initialDate(final YoYInflationTermStructure t) {
        return t.baseDate();
    }

    /** Value at reference date — the user-supplied base YoY rate. */
    public double initialValue(final YoYInflationTermStructure t) {
        return t.baseRate();
    }

    /**
     * Iterative guess for node {@code i}. If {@code validData}, reuse
     * {@code data[i]}; otherwise fall back to the average-inflation constant.
     */
    public double guess(final int i, final double[] data, final boolean validData) {
        if (validData) {
            return data[i];
        }
        return AVG_INFLATION;
    }

    /** Lower bound for node {@code i}'s value. Mirrors C++ minValueAfter. */
    public double minValueAfter(final int i, final double[] data, final boolean validData) {
        if (validData) {
            double r = data[0];
            for (int k = 1; k < data.length; ++k) {
                if (data[k] < r) r = data[k];
            }
            return r < 0.0 ? r * 2.0 : r / 2.0;
        }
        return -MAX_INFLATION;
    }

    /** Upper bound for node {@code i}'s value. Mirrors C++ maxValueAfter. */
    public double maxValueAfter(final int i, final double[] data, final boolean validData) {
        if (validData) {
            double r = data[0];
            for (int k = 1; k < data.length; ++k) {
                if (data[k] > r) r = data[k];
            }
            return r < 0.0 ? r / 2.0 : r * 2.0;
        }
        return MAX_INFLATION;
    }

    /**
     * Update data on a Newton step. Mirrors C++ {@code updateGuess}: assigns
     * {@code data[i] = level} only — does NOT propagate to {@code data[0]}
     * (this is the key difference from zero-inflation traits).
     */
    public void updateGuess(final double[] data, final double level, final int i) {
        data[i] = level;
    }

    /** Upper bound for the convergence loop. */
    public int maxIterations() {
        return MAX_ITERATIONS;
    }
}
