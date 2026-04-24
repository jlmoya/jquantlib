/*
 Copyright (C) 2008 Joon Tiang Heng

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

package org.jquantlib.math.optimization;

import org.jquantlib.QL;

/**
 * Criteria to end optimization process.
 * <p>
 * Faithful port of QuantLib C++ v1.42.1 {@code ql/math/optimization/endcriteria.hpp|cpp}.
 * C++ uses pass-by-reference ({@code Type&}, {@code Size&}) to let the caller see which
 * criterion fired and to update the stationary-state iteration counter. Java has no
 * pass-by-reference for primitives/enums, so the Java API uses one-element arrays
 * ({@code Type[]}, {@code int[]}) as mutable holders. Callers supply a
 * {@code Type[] ecType = { Type.None };} and read {@code ecType[0]} after the call.
 *
 * <ul>
 *   <li>maximum number of iterations AND minimum number of iterations around
 *       stationary point</li>
 *   <li>x (independent variable) stationary point</li>
 *   <li>y=f(x) (dependent variable) stationary point</li>
 *   <li>stationary gradient</li>
 * </ul>
 */
public class EndCriteria {

    public enum Type {
        None,
        MaxIterations,
        StationaryPoint,
        StationaryFunctionValue,
        StationaryFunctionAccuracy,
        ZeroGradientNorm,
        FunctionEpsilonTooSmall,
        Unknown
    }

    //! Maximum number of iterations
    protected final int maxIterations_;

    //! Maximum number of iterations in stationary state (mutable in C++; final here because
    //! we resolve the sentinel in the constructor.)
    protected final int maxStationaryStateIterations_;

    //! root, function and gradient epsilons
    protected final double rootEpsilon_;
    protected final double functionEpsilon_;
    protected final double gradientNormEpsilon_;

    //-- EndCriteria(Size maxIterations, Size maxStationaryStateIterations,
    //--             Real rootEpsilon, Real functionEpsilon, Real gradientNormEpsilon);
    //-- in ql/math/optimization/endcriteria.cpp:29
    /**
     * @param maxIterations              hard iteration cap.
     * @param maxStationaryStateIterations  cap on consecutive stationary iterations; pass
     *                                      {@code 0} as a sentinel meaning "not provided"
     *                                      (C++ uses {@code Null<Size>()}); the constructor
     *                                      then derives {@code min(maxIterations/2, 100)}.
     * @param rootEpsilon                stationary-x tolerance.
     * @param functionEpsilon            stationary-f tolerance.
     * @param gradientNormEpsilon        zero-gradient tolerance; pass {@code Double.NaN}
     *                                   as a sentinel meaning "not provided" (C++ uses
     *                                   {@code Null<Real>()}); falls back to
     *                                   {@code functionEpsilon}.
     */
    public EndCriteria(final int maxIterations,
                       final int maxStationaryStateIterations,
                       final double rootEpsilon,
                       final double functionEpsilon,
                       final double gradientNormEpsilon) {
        this.maxIterations_ = maxIterations;
        this.rootEpsilon_ = rootEpsilon;
        this.functionEpsilon_ = functionEpsilon;
        this.maxStationaryStateIterations_ = (maxStationaryStateIterations != 0)
                ? maxStationaryStateIterations
                : Math.min(maxIterations / 2, 100);
        this.gradientNormEpsilon_ = Double.isNaN(gradientNormEpsilon)
                ? functionEpsilon_
                : gradientNormEpsilon;

        QL.require(this.maxStationaryStateIterations_ > 1,
                "maxStationaryStateIterations_ (" + this.maxStationaryStateIterations_
                        + ") must be greater than one");
        QL.require(this.maxStationaryStateIterations_ < this.maxIterations_,
                "maxStationaryStateIterations_ (" + this.maxStationaryStateIterations_
                        + ") must be less than maxIterations_ (" + this.maxIterations_ + ")");
    }

    //-- bool checkMaxIterations(Size iteration, EndCriteria::Type& ecType) const;
    //-- in ql/math/optimization/endcriteria.cpp:56
    public boolean checkMaxIterations(final int iteration, final Type[] ecType) {
        if (iteration < maxIterations_) {
            return false;
        }
        ecType[0] = Type.MaxIterations;
        return true;
    }

    //-- bool checkStationaryPoint(Real xOld, Real xNew, Size& statStateIterations,
    //--                           EndCriteria::Type& ecType) const;
    //-- in ql/math/optimization/endcriteria.cpp:64
    public boolean checkStationaryPoint(final double xOld, final double xNew,
                                        final int[] statStateIterations, final Type[] ecType) {
        if (Math.abs(xNew - xOld) >= rootEpsilon_) {
            statStateIterations[0] = 0;
            return false;
        }
        ++statStateIterations[0];
        if (statStateIterations[0] <= maxStationaryStateIterations_) {
            return false;
        }
        ecType[0] = Type.StationaryPoint;
        return true;
    }

    //-- bool checkStationaryFunctionValue(Real fxOld, Real fxNew, Size& statStateIterations,
    //--                                   EndCriteria::Type& ecType) const;
    //-- in ql/math/optimization/endcriteria.cpp:79
    public boolean checkStationaryFunctionValue(final double fxOld, final double fxNew,
                                                final int[] statStateIterations,
                                                final Type[] ecType) {
        if (Math.abs(fxNew - fxOld) >= functionEpsilon_) {
            statStateIterations[0] = 0;
            return false;
        }
        ++statStateIterations[0];
        if (statStateIterations[0] <= maxStationaryStateIterations_) {
            return false;
        }
        ecType[0] = Type.StationaryFunctionValue;
        return true;
    }

    //-- bool checkStationaryFunctionAccuracy(Real f, bool positiveOptimization,
    //--                                      EndCriteria::Type& ecType) const;
    //-- in ql/math/optimization/endcriteria.cpp:95
    public boolean checkStationaryFunctionAccuracy(final double f,
                                                   final boolean positiveOptimization,
                                                   final Type[] ecType) {
        if (!positiveOptimization) {
            return false;
        }
        if (f >= functionEpsilon_) {
            return false;
        }
        ecType[0] = Type.StationaryFunctionAccuracy;
        return true;
    }

    //-- bool checkZeroGradientNorm(Real gradientNorm, EndCriteria::Type& ecType) const;
    //-- in ql/math/optimization/endcriteria.cpp:117
    public boolean checkZeroGradientNorm(final double gradientNorm, final Type[] ecType) {
        if (gradientNorm >= gradientNormEpsilon_) {
            return false;
        }
        ecType[0] = Type.ZeroGradientNorm;
        return true;
    }

    //-- bool operator()(Size iteration, Size& statStateIterations,
    //--                 bool positiveOptimization, Real fold, Real /*normgold*/,
    //--                 Real fnew, Real normgnew, EndCriteria::Type& ecType) const;
    //-- in ql/math/optimization/endcriteria.cpp:125
    //
    // Java lacks operator() so the method is named {@code get}, matching the existing
    // JQuantLib convention. The normgold parameter is accepted but ignored, matching C++.
    public boolean get(final int iteration,
                       final int[] statStateIterations,
                       final boolean positiveOptimization,
                       final double fold,
                       @SuppressWarnings("unused") final double normgold,
                       final double fnew,
                       final double normgnew,
                       final Type[] ecType) {
        return checkMaxIterations(iteration, ecType)
                || checkStationaryFunctionValue(fold, fnew, statStateIterations, ecType)
                || checkStationaryFunctionAccuracy(fnew, positiveOptimization, ecType)
                || checkZeroGradientNorm(normgnew, ecType);
    }

    // Inspectors

    public final int getMaxIterations() {
        return maxIterations_;
    }

    public final int getMaxStationaryStateIterations() {
        return maxStationaryStateIterations_;
    }

    public final double getRootEpsilon() {
        return rootEpsilon_;
    }

    public final double getFunctionEpsilon() {
        return functionEpsilon_;
    }

    public final double getGradientNormEpsilon() {
        return gradientNormEpsilon_;
    }

    //-- static bool succeeded(EndCriteria::Type ecType);
    //-- in ql/math/optimization/endcriteria.cpp:161
    /**
     * Whether an end-criterion represents convergence success (a found minimum) as
     * opposed to exhaustion (MaxIterations) or an anomaly (Unknown).
     */
    public static boolean succeeded(final Type ecType) {
        return ecType == Type.StationaryPoint
                || ecType == Type.StationaryFunctionValue
                || ecType == Type.StationaryFunctionAccuracy;
    }
}
