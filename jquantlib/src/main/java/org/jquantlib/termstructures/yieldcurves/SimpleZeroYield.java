/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is released under the BSD License.

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
 Copyright (C) 2019 SoftSolutions! S.r.l.

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.termstructures.yieldcurves;

import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;

/**
 * Simply-compounded zero-yield traits — Java port of v1.42.1
 * {@code ql/termstructures/yield/bootstraptraits.hpp}:313 {@code struct SimpleZeroYield}.
 *
 * <p>Distinct from {@link ZeroYield} (which is continuously compounded): the curve type produced is
 * {@link InterpolatedSimpleZeroCurve} whose discount is {@code 1/(1 + R*t)} rather than {@code exp(-R*t)}.
 *
 * <h3>Trait semantics</h3>
 * <ul>
 *   <li>{@code initialDate}     → curve.referenceDate()</li>
 *   <li>{@code initialValue}    → {@code avgRate = 0.05}</li>
 *   <li>{@code guess(i)}        → if validData, c.data[i]; if i==1, avgRate; else
 *                                  c.zeroRate(d, dayCounter, Simple, Annual, true)</li>
 *   <li>{@code minValueAfter}   → max(validData? min(data)*2 or /2 : -maxRate, -1/times[i] + 1E-8)</li>
 *   <li>{@code maxValueAfter}   → if validData max(data)*2 or /2 ; else maxRate</li>
 *   <li>{@code updateGuess}     → data[i] = rate; if i==1 also data[0] = rate</li>
 *   <li>{@code maxIterations()} → 100</li>
 *   <li>{@code transformDirect(x,i)}  → {@code exp(x) + (-1/times[i] + 1E-8)} (positivity offset)</li>
 *   <li>{@code transformInverse(x,i)} → {@code log(x - (-1/times[i] + 1E-8))}</li>
 * </ul>
 *
 * @see InterpolatedSimpleZeroCurve
 * @see GlobalBootstrap
 */
public class SimpleZeroYield implements Traits {

    private static final double avgRate = 0.05;
    private static final double maxRate = 1.0;

    public SimpleZeroYield() {
    }

    @Override
    public double initialValue(final YieldTermStructure curve) {
        return avgRate;
    }

    @Override
    public double initialGuess() {
        return avgRate;
    }

    @Override
    public double guess(final YieldTermStructure c, final Date d) {
        // C++ guess() uses Simple compounding, Annual frequency.
        return c.zeroRate(d, c.dayCounter(), Compounding.Simple, Frequency.Annual, true).rate();
    }

    @Override
    public double minValueAfter(final int i, final double[] data, final boolean validData, final double[] times) {
        // Port of C++ SimpleZeroYield::minValueAfter (bootstraptraits.hpp:351-366):
        //   result = validData ? min(data)*2 or /2 : -maxRate
        //   return max(result, -1.0/times[i] + 1E-8)
        double result;
        if ( validData ) {
            double r = data[0];
            for ( int k = 1; k < data.length; ++k ) {
                if ( data[k] < r ) {
                    r = data[k];
                }
            }
            result = r < 0.0 ? r * 2.0 : r / 2.0;
        } else {
            result = -maxRate;
        }
        return Math.max(result, -1.0 / times[i] + 1.0e-8);
    }

    @Override
    public double maxValueAfter(final int i, final double[] data, final boolean validData, final double[] times) {
        // Port of C++ SimpleZeroYield::maxValueAfter (bootstraptraits.hpp:368-381).
        if ( validData ) {
            double r = data[0];
            for ( int k = 1; k < data.length; ++k ) {
                if ( data[k] > r ) {
                    r = data[k];
                }
            }
            return r < 0.0 ? r / 2.0 : r * 2.0;
        }
        return maxRate;
    }

    @Override
    public void updateGuess(final double[] data, final double value, final int i) {
        // Port of C++ SimpleZeroYield::updateGuess (bootstraptraits.hpp:396-402).
        data[i] = value;
        if ( i == 1 ) {
            data[0] = value; // first point is updated as well
        }
    }

    @Override
    public boolean dummyInitialValue() {
        return true;
    }

    @Override
    public Date initialDate(final YieldTermStructure curve) {
        return curve.referenceDate();
    }

    @Override
    public int maxIterations() {
        return 100;
    }

    /**
     * Pillar-time-dependent positivity offset used by {@link GlobalBootstrap} when transforming the unconstrained
     * optimiser variable into the curve value. Mirrors C++ {@code transformDirect}/{@code transformInverse}
     * (bootstraptraits.hpp:385-393): {@code direct(x) = exp(x) + (-1/t + 1E-8)}.
     */
    public static double transformOffset(final double timeAtPillar) {
        return -1.0 / timeAtPillar + 1.0e-8;
    }

}
