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
 Copyright (C) 2025 AcadiaSoft Inc.
 Copyright (C) 2026 Paolo D'Elia

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/
package org.jquantlib.termstructures.volatilities.equityfx;

import org.jquantlib.QL;

/**
 * Time-extrapolation strategies for Black volatility term structures.
 * <p>
 * Java port of QuantLib v1.42.1
 * {@code ql/termstructures/volatility/equityfx/blackvoltimeextrapolation.{hpp,cpp}}.
 *
 * <p>Provides static helper methods to extrapolate variance beyond the
 * maximum time of a {@code BlackVolTermStructure}, supporting both surface
 * (strike-dependent) and curve (ATM) cases.
 */
public final class BlackVolTimeExtrapolation {

    /** Time-extrapolation strategy for Black volatility. */
    public enum Type {
        /** Flat extrapolation of the latest available volatility. */
        FlatVolatility,
        /** Delegate extrapolation to the underlying curve or surface,
         *  whatever the method it uses. */
        UseInterpolator,
        /** Linear extrapolation of variance from the last two available nodes. */
        LinearVariance
    }

    /** Functional handle for variance surface evaluation: {@code (t, k) -> var}. */
    public interface VarianceSurface {
        double op(double t, double k);
    }

    /** Functional handle for variance curve evaluation: {@code t -> var}. */
    public interface VarianceCurve {
        double op(double t);
    }

    private BlackVolTimeExtrapolation() { }

    //
    // public methods
    //

    /**
     * Extrapolate a variance surface to a given time and strike.
     * Mirrors C++ overload over surfaces.
     */
    public static double extrapolatedVariance(final Type type, final double t, final double strike,
                                              final double[] times, final VarianceSurface varianceSurface) {
        switch (type) {
            case FlatVolatility:
                return timeExtrapolationFlat(t, strike, times, varianceSurface);
            case UseInterpolator:
                return Math.max(varianceSurface.op(t, strike), 0.0);
            case LinearVariance:
                return timeExtrapolationLinear(t, strike, times, varianceSurface);
            default:
                QL.error("unknown extrapolation type");
                return Double.NaN; // unreachable
        }
    }

    /**
     * Extrapolate an ATM variance curve to a given time.
     * Mirrors C++ overload over curves.
     */
    public static double extrapolatedVariance(final Type type, final double t,
                                              final double[] times, final VarianceCurve varianceCurve) {
        switch (type) {
            case FlatVolatility:
                return timeExtrapolationFlat(t, times, varianceCurve);
            case UseInterpolator:
                return Math.max(varianceCurve.op(t), 0.0);
            case LinearVariance:
                QL.require(times.length >= 2,
                        "at least two times required for volatility extrapolation");
                return timeExtrapolationLinear(t, times, varianceCurve);
            default:
                QL.error("unknown extrapolation type");
                return Double.NaN; // unreachable
        }
    }


    //
    // private helpers
    //

    private static double linearExtrapolation(final double t, final double t1, final double t2,
                                              final double v1, final double v2) {
        QL.require(t > 0.0, "t must be greater than 0.0");
        QL.require(t > t2, "t must be greater than times[1]");
        QL.require(t2 > t1, "times must be sorted");
        QL.require(v2 >= v1, "variances must be non-decreasing");
        return v1 + (t - t1) * (v2 - v1) / (t2 - t1);
    }

    private static double timeExtrapolationFlat(final double t, final double strike,
                                                final double[] times, final VarianceSurface vs) {
        final double tBack = times[times.length - 1];
        return Math.max(vs.op(tBack, strike), 0.0) / tBack * t;
    }

    private static double timeExtrapolationFlat(final double t, final double[] times,
                                                final VarianceCurve vc) {
        final double tBack = times[times.length - 1];
        return Math.max(vc.op(tBack), 0.0) / tBack * t;
    }

    private static double timeExtrapolationLinear(final double t, final double strike,
                                                  final double[] times, final VarianceSurface vs) {
        final int n = times.length;
        final double t1 = times[n - 2];
        final double t2 = times[n - 1];
        final double v1 = vs.op(t1, strike);
        final double v2 = vs.op(t2, strike);
        return linearExtrapolation(t, t1, t2, v1, v2);
    }

    private static double timeExtrapolationLinear(final double t, final double[] times,
                                                  final VarianceCurve vc) {
        final int n = times.length;
        final double t1 = times[n - 2];
        final double t2 = times[n - 1];
        final double v1 = vc.op(t1);
        final double v2 = vc.op(t2);
        return linearExtrapolation(t, t1, t2, v1, v2);
    }
}
