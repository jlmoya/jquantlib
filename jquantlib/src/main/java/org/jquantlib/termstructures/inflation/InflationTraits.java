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

import org.jquantlib.termstructures.ZeroInflationTermStructure;
import org.jquantlib.time.Date;

/**
 * Bootstrap traits for {@code PiecewiseZeroInflationCurve} — Java port of QuantLib v1.42.1
 * {@code ZeroInflationTraits}.
 *
 * <p>Traits encode the curve-specific decisions a generic bootstrap loop needs:
 * the initial node date and value, per-iteration guesses, value-domain constraints (min/max-after), how to update the
 * data array on each Newton step, and an iteration cap.
 *
 * <p>The Java port mirrors the C++ class structurally. We deliberately use a
 * non-generic, non-templated, instance-method shape rather than the C++ static-template form because:
 * <ul>
 *   <li>Java's existing yield-curve {@code Traits} interface
 *       ({@code org.jquantlib.termstructures.yieldcurves.Traits}) is wired
 *       tightly to {@code YieldTermStructure} and cannot be reused for
 *       inflation without mutation, which is forbidden in this phase.</li>
 *   <li>The inflation bootstrap path here is small enough that a dedicated,
 *       inflation-only traits class is simpler than a new generic shim.</li>
 * </ul>
 *
 * <p>Constants {@code AVG_INFLATION = 0.02} and {@code MAX_INFLATION = 0.5}
 * mirror the C++ {@code detail::avgInflation} / {@code detail::maxInflation}.
 *
 * @see PiecewiseZeroInflationCurve
 */
public final class InflationTraits {

    public static final double AVG_INFLATION = 0.02;
    public static final double MAX_INFLATION = 0.5;
    public static final int MAX_ITERATIONS = 40;

    /** Start of curve data — the curve's base date. */
    public Date initialDate(final ZeroInflationTermStructure t) {
        return t.baseDate();
    }

    /** Value at reference date — overwritten during bootstrap. */
    public double initialValue(final ZeroInflationTermStructure t) {
        // C++: detail::avgInflation. Will be overwritten.
        return AVG_INFLATION;
    }

    /**
     * Iterative guess for node {@code i}. If the curve already holds valid data (a previous bootstrap iteration), reuse
     * {@code data[i]}; otherwise fall back to the average inflation constant.
     */
    public double guess(final int i, final double[] data, final boolean validData) {
        if ( validData ) {
            return data[i];
        }
        return AVG_INFLATION;
    }

    /** Lower bound for node {@code i}'s value. Mirrors C++ minValueAfter. */
    public double minValueAfter(final int i, final double[] data, final boolean validData) {
        if ( validData ) {
            double r = data[0];
            for ( int k = 1; k < data.length; ++k ) {
                if ( data[k] < r )
                    r = data[k];
            }
            return r < 0.0 ? r * 2.0 : r / 2.0;
        }
        return -MAX_INFLATION;
    }

    /** Upper bound for node {@code i}'s value. Mirrors C++ maxValueAfter. */
    public double maxValueAfter(final int i, final double[] data, final boolean validData) {
        if ( validData ) {
            double r = data[0];
            for ( int k = 1; k < data.length; ++k ) {
                if ( data[k] > r )
                    r = data[k];
            }
            return r < 0.0 ? r / 2.0 : r * 2.0;
        }
        return MAX_INFLATION;
    }

    /**
     * Update data on a Newton step. Mirrors C++ {@code updateGuess}: assigns {@code data[i] = level}, and additionally
     * propagates {@code level} to {@code data[0]} when {@code i == 1} (which sets the curve's base-rate once the first
     * helper has been solved).
     */
    public void updateGuess(final double[] data, final double level, final int i) {
        data[i] = level;
        if ( i == 1 ) {
            data[0] = level;
        }
    }

    /** Upper bound for the convergence loop. Mirrors C++ {@code maxIterations}. */
    public int maxIterations() {
        return MAX_ITERATIONS;
    }
}
