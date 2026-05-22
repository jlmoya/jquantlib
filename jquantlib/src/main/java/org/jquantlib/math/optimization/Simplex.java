/*
 Copyright (C) 2010 Selene Makarios

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
 Copyright (C) 2001, 2002, 2003 Sadruddin Rejeb
 Copyright (C) 2006 Ferdinando Ametrano
 Copyright (C) 2007 Marco Bianchetti
 Copyright (C) 2007 Francois du Vignaud

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <http://quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
 */

package org.jquantlib.math.optimization;

import org.jquantlib.math.Constants;
import org.jquantlib.math.matrixutilities.Array;

import java.util.ArrayList;
import java.util.List;

//TODO: code review
// This is a re-implementation of Simplex, from the original C++ source.
// Currently, the junit test for the SABR implementation fails, *unless* Simplex.java is
// replaced with this alternative implementation Simplex2.java, in which case it passes
// the junit test (making sure that -DEXPERIMENTAL=true property is set).  At
// the point in the SABR code where Simplex is called, this implementation and the
// original one return different results. The chances that there are errors in
// SABRInterpolation.java that are exactly cancelled out by errors in Simplex2.java, so
// as to have the tests pass, is probably small, indicating a potential problem with
// the original Simplex.java.  I won't presume to replace it with this one, however. -- SKM

/**
 * The implementation of the algorithm was highly inspired by "Numerical Recipes in C", 2nd edition, Press, Teukolsky,
 * Vetterling, Flannery, chapter 10.
 * <p>
 * Modified may 2007: end criteria set on x instead on fx, inspired by bad behaviour found with test function
 * {@latex$ fx=x*x+x+1, xStart = -100, lambda = 1.0, ftol = 1.e-16} (it reports x=0 as the minimum!) and by GSL
 * implementation, v. 1.9 (http://www.gnu.org/software/gsl/)
 *
 * @author Selene Makarios
 * @see http://www.gnu.org/software/gsl/
 */
public class Simplex extends OptimizationMethod {

    private final double lambda_;
    private List< Array > vertices_;
    private Array values_, sum_;

    /**
     * constructor taking as input the characteristic length
     */
    public Simplex(final double lambda) {
        lambda_ = lambda;
    }

    // Computes the size of the simplex
    public double computeSimplexSize(final List< Array > vertices) {
        final Array center = new Array(vertices.get(0).size());
        for ( int i = 0; i < vertices.size(); ++i ) {
            center.addAssign(vertices.get(i));
        }
        center.mulAssign(1.0 / vertices.size());
        double result = 0;
        for ( int i = 0; i < vertices.size(); ++i ) {
            final Array temp = vertices.get(i).sub(center);
            result += Math.sqrt(temp.dotProduct(temp));
        }
        return result / vertices.size();
    }

    /**
     * Mirrors C++ {@code simplex.cpp::Simplex::extrapolate} (lines 54-77). The {@code factor} argument is an in/out
     * parameter: C++ passes by reference ({@code Real &factor}) and the caller observes the post-call value to drive
     * the reflection/expansion/contraction logic. Java emulates this with a single-element {@code double[]} since
     * primitives are pass-by-value.
     *
     * <p>Aligned with v1.42.1 in Phase 2d WI-3 — prior Java port took
     * {@code double factor} by value, silently losing the {@code *= 0.5} (constraint-shrink loop) and {@code *= 2.0}
     * (post-shrink restore) mutations. The lost mutations broke the caller's {@code factor == -1.0} /
     * {@code |factor| > EPS} checks, allowing the simplex to drift into NaN territory on cost functions with narrow
     * valid domains (e.g., SABR's {@code alpha > 0} validation).
     */
    public double extrapolate(final Problem P, final int iHighest, final double[] factor) {

        Array pTry;
        do {
            final int dimensions = values_.size() - 1;
            final double factor1 = (1.0 - factor[0]) / dimensions;
            final double factor2 = factor1 - factor[0];
            pTry = sum_.mul(factor1).sub(vertices_.get(iHighest).mul(factor2));
            factor[0] *= 0.5;
        } while ( !P.constraint().test(pTry) && Math.abs(factor[0]) > Constants.QL_EPSILON ); // QL_EPSILON);
        if ( Math.abs(factor[0]) <= Constants.QL_EPSILON )
            return values_.get(iHighest);
        factor[0] *= 2.0;
        final double vTry = P.value(pTry);
        if ( vTry < values_.get(iHighest) ) {
            values_.set(iHighest, vTry);
            sum_.addAssign(pTry.sub(vertices_.get(iHighest)));
            vertices_.set(iHighest, pTry);
        }
        return vTry;
    }

    @Override
    public EndCriteria.Type minimize(final Problem P, final EndCriteria endCriteria) {
        // set up of the problem
        // double ftol = endCriteria.functionEpsilon(); // end criteria on f(x)
        // (see Numerical Recipes in C++, p.410)
        final double xtol = endCriteria.getRootEpsilon(); // end criteria on x (see
        // GSL v. 1.9,
        // http://www.gnu.org/software/gsl/)
        // Pass-by-reference holders for EndCriteria's mutating check methods.
        final int[] maxStationaryStateIterations_ = { endCriteria.getMaxStationaryStateIterations() };
        final EndCriteria.Type[] ecType = { EndCriteria.Type.None };
        P.reset();
        Array x_ = P.currentValue();
        Integer iterationNumber_ = 0;

        // Initialize vertices of the simplex
        final int n = x_.size();
        vertices_ = new ArrayList<>(n + 1);
        for ( int i = 0; i <= n; i++ ) {
            vertices_.add(new Array(x_));
        }
        for ( int i = 0; i < n; i++ ) {
            final Array direction = new Array(n);
            direction.set(i, 1.0);
            P.constraint().update(vertices_.get(i + 1), direction, lambda_);
        }
        // Initialize function values at the vertices of the simplex
        values_ = new Array(n + 1);
        for ( int i = 0; i <= n; i++ ) {
            values_.set(i, P.value(vertices_.get(i)));
        }
        // Loop looking for minimum
        do {
            sum_ = new Array(n);
            for ( int i = 0; i <= n; i++ ) {
                sum_.addAssign(vertices_.get(i));
            }
            // Determine the best (iLowest), worst (iHighest)
            // and 2nd worst (iNextHighest) vertices
            int iLowest = 0;
            int iHighest, iNextHighest;
            if ( values_.get(0) < values_.get(1) ) {
                iHighest = 1;
                iNextHighest = 0;
            } else {
                iHighest = 0;
                iNextHighest = 1;
            }
            for ( int i = 1; i <= n; i++ ) {
                if ( values_.get(i) > values_.get(iHighest) ) {
                    iNextHighest = iHighest;
                    iHighest = i;
                } else {
                    if ( (values_.get(i) > values_.get(iNextHighest)) && i != iHighest ) {
                        iNextHighest = i;
                    }
                }
                if ( values_.get(i) < values_.get(iLowest) ) {
                    iLowest = i;
                }
            }
            // Now compute accuracy, update iteration number and check end
            // criteria
            // // Numerical Recipes exit strategy on fx (see NR in C++, p.410)
            // double low = values_[iLowest];
            // double high = values_[iHighest];
            // double rtol = 2.0*Math.fabs(high - low)/
            // (Math.fabs(high) + Math.fabs(low) + QL_EPSILON);
            // ++iterationNumber_;
            // if (rtol < ftol ||
            // endCriteria.checkMaxIterations(iterationNumber_, ecType))
            // GSL exit strategy on x (see GSL v. 1.9,
            // http://www.gnu.org/software/gsl
            final double simplexSize = computeSimplexSize(vertices_);
            ++iterationNumber_;
            if ( simplexSize < xtol || endCriteria.checkMaxIterations(iterationNumber_, ecType) ) {
                endCriteria.checkStationaryPoint(0.0, 0.0, maxStationaryStateIterations_, ecType);
                endCriteria.checkMaxIterations(iterationNumber_, ecType);
                x_ = vertices_.get(iLowest);
                final double low = values_.get(iLowest);
                P.setFunctionValue(low);
                P.setCurrentValue(x_);
                return ecType[0];
            }
            // If end criteria is not met, continue.
            // factor is in/out — see extrapolate() javadoc; callsite reads
            // post-call value to choose reflection vs expansion vs
            // contraction (matches C++ simplex.cpp:160-185).
            final double[] factor = { -1.0 };
            double vTry = extrapolate(P, iHighest, factor);
            if ( (vTry <= values_.get(iLowest)) && (factor[0] == -1.0) ) {
                factor[0] = 2.0;
                extrapolate(P, iHighest, factor);
            } else if ( Math.abs(factor[0]) > Constants.QL_EPSILON ) {
                if ( vTry >= values_.get(iNextHighest) ) {
                    final double vSave = values_.get(iHighest);
                    factor[0] = 0.5;
                    vTry = extrapolate(P, iHighest, factor);
                    if ( vTry >= vSave && Math.abs(factor[0]) > Constants.QL_EPSILON ) {
                        for ( int i = 0; i <= n; i++ ) {
                            if ( i != iLowest ) {
                                vertices_.set(i, (vertices_.get(i).add(vertices_.get(iLowest))).mul(0.5));
                                values_.set(i, P.value(vertices_.get(i)));
                            }
                        }
                    }
                }
            }
            // If can't extrapolate given theraints, exit
            if ( Math.abs(factor[0]) <= Constants.QL_EPSILON ) {
                x_ = vertices_.get(iLowest);
                final double low = values_.get(iLowest);
                P.setFunctionValue(low);
                P.setCurrentValue(x_);
                return EndCriteria.Type.StationaryFunctionValue;
            }
        } while ( true );
        //FIXME: code review
        //-- throw new LibraryException("optimization failed: unexpected behaviour");
    }
}
