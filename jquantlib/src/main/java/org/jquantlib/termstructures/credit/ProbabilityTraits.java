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
 Copyright (C) 2008, 2016 Jose Aparicio
 Copyright (C) 2008 Chris Kenyon
 Copyright (C) 2008 Roland Lichters
 Copyright (C) 2008 StatPro Italia srl
 Copyright (C) 2009, 2011 Ferdinando Ametrano

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.termstructures.credit;

/**
 * Bootstrap traits for default-probability curves — Java port of QuantLib v1.42.1 {@code probabilitytraits.hpp}.
 *
 * <p>Three traits implementations mirror the three C++ structs
 * {@code SurvivalProbability}, {@code HazardRate}, {@code DefaultDensity}. Each encodes:
 * <ul>
 *   <li>initial-value seeding for the curve's first node,
 *   <li>per-iteration {@code guess(i)},
 *   <li>per-iteration value bounds {@code minValueAfter(i)} / {@code maxValueAfter(i)},
 *   <li>{@code updateGuess(data, x, i)} for the bootstrap Newton step,
 *   <li>{@code maxIterations()} convergence cap.
 * </ul>
 *
 * <p>The Java port deliberately uses a non-generic, non-templated, instance-method
 * shape — same precedent as Phase 2p's
 * {@link org.jquantlib.termstructures.inflation.InflationTraits}.
 */
public final class ProbabilityTraits {

    /** {@code detail::avgHazardRate = 0.01} from C++ probabilitytraits.hpp:39. */
    public static final double AVG_HAZARD_RATE = 0.01;

    /** {@code detail::maxHazardRate = 1.0} from C++ probabilitytraits.hpp:40. */
    public static final double MAX_HAZARD_RATE = 1.0;

    /** Tiny epsilon used as a bound when no other constraint applies. */
    public static final double QL_EPSILON = 2.2204460492503131e-16;

    private ProbabilityTraits() {
        // not instantiable — use the three trait classes below.
    }

    //
    // Survival-Probability traits — mirrors C++ struct SurvivalProbability.
    //

    /**
     * Common interface implemented by the three trait classes above. Mirrors the C++ template static-method shape,
     * recast as Java instance methods.
     */
    public interface Traits {
        double initialValue();

        double guess(int i, double[] data, boolean validData, double[] times);

        double minValueAfter(int i, double[] data, boolean validData, double[] times);

        double maxValueAfter(int i, double[] data, boolean validData, double[] times);

        void updateGuess(double[] data, double x, int i);

        int maxIterations();
    }

    //
    // Hazard-Rate traits — mirrors C++ struct HazardRate.
    //

    public static final class SurvivalProbability implements Traits {

        @Override
        public double initialValue() {
            return 1.0;
        }

        @Override
        public double guess(final int i, final double[] data, final boolean validData, final double[] times) {
            if ( validData )
                return data[i];
            if ( i == 1 )
                return 1.0 / (1.0 + AVG_HAZARD_RATE * 0.25);
            // Best fallback: use previous node's value (curve must monotone-decrease).
            return data[i - 1];
        }

        @Override
        public double minValueAfter(final int i, final double[] data, final boolean validData, final double[] times) {
            if ( validData ) {
                return data[data.length - 1] / 2.0;
            }
            final double dt = times[i] - times[i - 1];
            return data[i - 1] * Math.exp(-MAX_HAZARD_RATE * dt);
        }

        @Override
        public double maxValueAfter(final int i, final double[] data, final boolean validData, final double[] times) {
            return data[i - 1];
        }

        @Override
        public void updateGuess(final double[] data, final double p, final int i) {
            data[i] = p;
        }

        @Override
        public int maxIterations() {
            return 50;
        }
    }

    //
    // Default-Density traits — mirrors C++ struct DefaultDensity.
    //

    public static final class HazardRate implements Traits {

        @Override
        public double initialValue() {
            return AVG_HAZARD_RATE;
        }

        @Override
        public double guess(final int i, final double[] data, final boolean validData, final double[] times) {
            if ( validData )
                return data[i];
            if ( i == 1 )
                return AVG_HAZARD_RATE;
            return data[i - 1];
        }

        @Override
        public double minValueAfter(final int i, final double[] data, final boolean validData, final double[] times) {
            if ( validData ) {
                double r = data[0];
                for ( int k = 1; k < data.length; ++k ) {
                    if ( data[k] < r )
                        r = data[k];
                }
                return r / 2.0;
            }
            return QL_EPSILON;
        }

        @Override
        public double maxValueAfter(final int i, final double[] data, final boolean validData, final double[] times) {
            if ( validData ) {
                double r = data[0];
                for ( int k = 1; k < data.length; ++k ) {
                    if ( data[k] > r )
                        r = data[k];
                }
                return r * 2.0;
            }
            return MAX_HAZARD_RATE;
        }

        @Override
        public void updateGuess(final double[] data, final double rate, final int i) {
            data[i] = rate;
            if ( i == 1 )
                data[0] = rate;
        }

        @Override
        public int maxIterations() {
            return 30;
        }
    }

    public static final class DefaultDensity implements Traits {

        @Override
        public double initialValue() {
            return AVG_HAZARD_RATE;
        }

        @Override
        public double guess(final int i, final double[] data, final boolean validData, final double[] times) {
            if ( validData )
                return data[i];
            if ( i == 1 )
                return AVG_HAZARD_RATE;
            return data[i - 1];
        }

        @Override
        public double minValueAfter(final int i, final double[] data, final boolean validData, final double[] times) {
            if ( validData ) {
                double r = data[0];
                for ( int k = 1; k < data.length; ++k ) {
                    if ( data[k] < r )
                        r = data[k];
                }
                return r / 2.0;
            }
            return QL_EPSILON;
        }

        @Override
        public double maxValueAfter(final int i, final double[] data, final boolean validData, final double[] times) {
            if ( validData ) {
                double r = data[0];
                for ( int k = 1; k < data.length; ++k ) {
                    if ( data[k] > r )
                        r = data[k];
                }
                return r * 2.0;
            }
            return MAX_HAZARD_RATE;
        }

        @Override
        public void updateGuess(final double[] data, final double density, final int i) {
            data[i] = density;
            if ( i == 1 )
                data[0] = density;
        }

        @Override
        public int maxIterations() {
            return 30;
        }
    }
}
