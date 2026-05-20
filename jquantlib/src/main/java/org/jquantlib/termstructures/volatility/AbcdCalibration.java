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

/*
 Copyright (C) 2006, 2007, 2015 Ferdinando Ametrano
 Copyright (C) 2006 Cristina Duminuco
 Copyright (C) 2007 Giorgio Facchinetti
 Copyright (C) 2015 Paolo Mazzocchi
*/

package org.jquantlib.termstructures.volatility;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.CostFunction;
import org.jquantlib.math.optimization.EndCriteria;
import org.jquantlib.math.optimization.LevenbergMarquardt;
import org.jquantlib.math.optimization.NoConstraint;
import org.jquantlib.math.optimization.OptimizationMethod;
import org.jquantlib.math.optimization.ParametersTransformation;
import org.jquantlib.math.optimization.Problem;
import org.jquantlib.math.optimization.ProjectedCostFunction;

/**
 * Abcd parameterized volatility calibration.
 *
 * <p>Java port of QuantLib v1.42.1 {@code ql/termstructures/volatility/abcdcalibration.{hpp,cpp}}.
 *
 * <p>Calibrates the four parameters {@code (a, b, c, d)} of {@link AbcdFunction} (Rebonato's parameterization of
 * instantaneous volatility) against a vector of Black volatilities {@code blackVols} at corresponding times {@code t}.
 *
 * <p>The optimisation uses {@link LevenbergMarquardt} by default, with a parameter transformation enforcing the
 * positivity constraints {@code c &gt;= 0}, {@code d &gt;= 0}, and {@code a + d &gt;= 0}.
 */
public class AbcdCalibration {

    //
    // Inner cost-function class
    //
    private class AbcdError extends CostFunction {

        @Override
        public double value(final Array x) {
            final Array y = transformation_.direct(x);
            a_ = y.get(0);
            b_ = y.get(1);
            c_ = y.get(2);
            d_ = y.get(3);
            return error();
        }

        @Override
        public Array values(final Array x) {
            final Array y = transformation_.direct(x);
            a_ = y.get(0);
            b_ = y.get(1);
            c_ = y.get(2);
            d_ = y.get(3);
            return errors();
        }
    }

    //
    // Inner parameter-transformation class
    //
    private static class AbcdParametersTransformation implements ParametersTransformation {

        private final Array y_ = new Array(4);

        // to constrained <- from unconstrained
        @Override
        public Array direct(final Array x) {
            y_.set(1, x.get(1));
            y_.set(2, Math.exp(x.get(2)));
            y_.set(3, Math.exp(x.get(3)));
            y_.set(0, Math.exp(x.get(0)) - y_.get(3));
            return y_;
        }

        // to unconstrained <- from constrained
        @Override
        public Array inverse(final Array x) {
            y_.set(1, x.get(1));
            y_.set(2, Math.log(x.get(2)));
            y_.set(3, Math.log(x.get(3)));
            y_.set(0, Math.log(x.get(0) + x.get(3)));
            return y_;
        }
    }

    //
    // Public fields (match C++ public access)
    //
    public boolean aIsFixed_, bIsFixed_, cIsFixed_, dIsFixed_;
    public double a_, b_, c_, d_;
    public ParametersTransformation transformation_;

    //
    // Private fields
    //
    private EndCriteria.Type abcdEndCriteria_;
    private EndCriteria endCriteria_;
    private OptimizationMethod optMethod_;
    private List< Double > weights_;
    private final boolean vegaWeighted_;
    private final List< Double > times_, blackVols_;

    //
    // Default constructor: matches C++ {@code AbcdCalibration() = default;}.
    //
    public AbcdCalibration() {
        this.vegaWeighted_ = false;
        this.times_ = new ArrayList<>();
        this.blackVols_ = new ArrayList<>();
        this.weights_ = new ArrayList<>();
        this.abcdEndCriteria_ = EndCriteria.Type.None;
    }

    //
    // Full constructor.
    //
    public AbcdCalibration(final List< Double > t, final List< Double > blackVols, final double aGuess,
            final double bGuess, final double cGuess, final double dGuess, final boolean aIsFixed,
            final boolean bIsFixed, final boolean cIsFixed, final boolean dIsFixed, final boolean vegaWeighted,
            final EndCriteria endCriteria, final OptimizationMethod method) {

        this.aIsFixed_ = aIsFixed;
        this.bIsFixed_ = bIsFixed;
        this.cIsFixed_ = cIsFixed;
        this.dIsFixed_ = dIsFixed;
        this.a_ = aGuess;
        this.b_ = bGuess;
        this.c_ = cGuess;
        this.d_ = dGuess;
        this.abcdEndCriteria_ = EndCriteria.Type.None;
        this.endCriteria_ = endCriteria;
        this.optMethod_ = method;
        this.vegaWeighted_ = vegaWeighted;
        this.times_ = t;
        this.blackVols_ = blackVols;

        // Initialise weights uniformly: 1/N each.
        this.weights_ = new ArrayList<>(blackVols.size());
        final double w = 1.0 / blackVols.size();
        for ( int i = 0; i < blackVols.size(); ++i ) {
            this.weights_.add(w);
        }

        AbcdFunction.validate(aGuess, bGuess, cGuess, dGuess);

        QL.require(blackVols.size() == t.size(),
                "mismatch between number of times (" + t.size() + ") and blackVols (" + blackVols.size() + ")");

        // If no optimization method provided, default to Levenberg-Marquardt.
        if ( optMethod_ == null ) {
            final double epsfcn = 1.0e-8;
            final double xtol = 1.0e-8;
            final double gtol = 1.0e-8;
            optMethod_ = new LevenbergMarquardt(epsfcn, xtol, gtol);
        }
        if ( endCriteria_ == null ) {
            final int maxIterations = 10000;
            final int maxStationaryStateIterations = 1000;
            final double rootEpsilon = 1.0e-8;
            final double functionEpsilon = 0.3e-4;     // Why 0.3e-4 ?
            final double gradientNormEpsilon = 0.3e-4; // Why 0.3e-4 ?
            endCriteria_ = new EndCriteria(maxIterations, maxStationaryStateIterations, rootEpsilon, functionEpsilon,
                    gradientNormEpsilon);
        }
    }

    //
    // Convenience constructor with default guesses and no fixes / weighting.
    // Matches C++ defaulted parameters in the header declaration.
    //
    public AbcdCalibration(final List< Double > t, final List< Double > blackVols) {
        this(t, blackVols, -0.06, 0.17, 0.54, 0.17, false, false, false, false, false, null, null);
    }

    /**
     * Run the calibration. Updates {@code a_, b_, c_, d_} to the optimal parameters.
     */
    public void compute() {
        if ( vegaWeighted_ ) {
            double weightsSum = 0.0;
            final CumulativeNormalDistribution N = new CumulativeNormalDistribution();
            for ( int i = 0; i < times_.size(); ++i ) {
                final double stdDev = Math.sqrt(blackVols_.get(i) * blackVols_.get(i) * times_.get(i));
                // when strike==forward, the blackFormulaStdDevDerivative becomes
                weights_.set(i, N.derivative(0.5 * stdDev));
                weightsSum += weights_.get(i);
            }
            // weight normalization
            for ( int i = 0; i < times_.size(); ++i ) {
                weights_.set(i, weights_.get(i) / weightsSum);
            }
        }

        // there is nothing to optimize
        if ( aIsFixed_ && bIsFixed_ && cIsFixed_ && dIsFixed_ ) {
            abcdEndCriteria_ = EndCriteria.Type.None;
            return;
        }

        final AbcdError costFunction = new AbcdError();
        transformation_ = new AbcdParametersTransformation();

        final Array guess = new Array(4);
        guess.set(0, a_);
        guess.set(1, b_);
        guess.set(2, c_);
        guess.set(3, d_);

        final boolean[] parameterAreFixed = new boolean[4];
        parameterAreFixed[0] = aIsFixed_;
        parameterAreFixed[1] = bIsFixed_;
        parameterAreFixed[2] = cIsFixed_;
        parameterAreFixed[3] = dIsFixed_;

        // Note: transformation_.inverse returns a reference to its internal Array;
        // clone here so subsequent direct/inverse calls (e.g. inside the cost
        // function evaluation) do not clobber the inversed guess we pass to the
        // ProjectedCostFunction.
        final Array inversedTransformatedGuess = transformation_.inverse(guess).clone();

        final ProjectedCostFunction projectedAbcdCostFunction = new ProjectedCostFunction(costFunction,
                inversedTransformatedGuess, parameterAreFixed);

        final Array projectedGuess = projectedAbcdCostFunction.project(inversedTransformatedGuess);

        final NoConstraint constraint = new NoConstraint();
        final Problem problem = new Problem(projectedAbcdCostFunction, constraint, projectedGuess);
        abcdEndCriteria_ = optMethod_.minimize(problem, endCriteria_);
        final Array projectedResult = problem.currentValue();
        final Array transfResult = projectedAbcdCostFunction.include(projectedResult);

        // Clone: direct returns internal y_ array; subsequent direct() calls would mutate it.
        final Array result = transformation_.direct(transfResult).clone();
        AbcdFunction.validate(a_, b_, c_, d_);
        a_ = result.get(0);
        b_ = result.get(1);
        c_ = result.get(2);
        d_ = result.get(3);
    }

    /**
     * Calibrated Black volatility at time {@code x}: equivalent to {@code abcdBlackVolatility(x, a_, b_, c_, d_)}.
     */
    public double value(final double x) {
        return new AbcdFunction(a_, b_, c_, d_).volatility(0.0, x, x);
    }

    /**
     * Adjustment factors needed to match input Black vols: {@code k[i] = blackVols[i] / value(t[i])}.
     */
    public List< Double > k(final List< Double > t, final List< Double > blackVols) {
        QL.require(blackVols.size() == t.size(),
                "mismatch between number of times (" + t.size() + ") and blackVols (" + blackVols.size() + ")");
        final List< Double > k = new ArrayList<>(t.size());
        for ( int i = 0; i < t.size(); ++i ) {
            k.add(blackVols.get(i) / value(t.get(i)));
        }
        return k;
    }

    /**
     * Weighted root-mean-square error between calibrated and target vols.
     */
    public double error() {
        final int n = times_.size();
        double err;
        double squaredError = 0.0;
        for ( int i = 0; i < times_.size(); ++i ) {
            err = value(times_.get(i)) - blackVols_.get(i);
            squaredError += err * err * weights_.get(i);
        }
        return Math.sqrt(n * squaredError / (n - 1));
    }

    /**
     * Maximum absolute error between calibrated and target vols.
     */
    public double maxError() {
        double err;
        double maxError = -Double.MAX_VALUE;
        for ( int i = 0; i < times_.size(); ++i ) {
            err = Math.abs(value(times_.get(i)) - blackVols_.get(i));
            maxError = Math.max(maxError, err);
        }
        return maxError;
    }

    /**
     * Weighted-difference vector used by the Levenberg-Marquardt least-squares loop.
     */
    public Array errors() {
        final Array results = new Array(times_.size());
        for ( int i = 0; i < times_.size(); ++i ) {
            results.set(i, (value(times_.get(i)) - blackVols_.get(i)) * Math.sqrt(weights_.get(i)));
        }
        return results;
    }

    /**
     * End-criteria reached by the last {@link #compute()} call.
     */
    public EndCriteria.Type endCriteria() {
        return abcdEndCriteria_;
    }

    // ----- inspectors -----

    public double a() {
        return a_;
    }

    public double b() {
        return b_;
    }

    public double c() {
        return c_;
    }

    public double d() {
        return d_;
    }
}
