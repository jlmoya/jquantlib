/*
 Copyright (C) 2026 Jose Moya (Java port)

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

/* -*- mode: c++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*- */

/*
 Copyright (C) 2009 Frédéric Degraeve

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
*/

/*! \file bfgs.hpp
    \brief Broyden-Fletcher-Goldfarb-Shanno optimization method
*/
package org.jquantlib.math.optimization;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.optimization.EndCriteria.Type;

/**
 * Broyden-Fletcher-Goldfarb-Shanno (BFGS) line-search optimization method.
 *
 * <p>Faithful Java port of QuantLib C++ v1.42.1
 * {@code ql/math/optimization/bfgs.hpp|cpp} (commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}).
 *
 * <p>Adapted from Numerical Recipes in C, 2nd edition. The inverse-Hessian
 * is updated each iteration via the BFGS rank-2 formula; the search direction
 * is then {@code d = - H^{-1} g}. User has to provide a line-search method
 * and optimization end criteria.
 *
 * <p>The C++ implementation inherits {@code minimize} from
 * {@code LineSearchBasedMethod} and only overrides {@code getUpdatedDirection}.
 * Because the Java {@code LineSearchBasedMethod} does not expose that
 * template-method hook (it is a thin holder for {@code lineSearch_}), this
 * Java port implements {@code minimize} directly, mirroring the body of
 * {@code LineSearchBasedMethod::minimize} from v1.42.1 with the BFGS
 * direction-update inlined where {@code getUpdatedDirection} would be called.
 *
 * @see <a href="http://en.wikipedia.org/wiki/BFGS_method">BFGS on Wikipedia</a>
 */
public class Bfgs extends LineSearchBasedMethod {

    /** Inverse of the Hessian, lazily allocated on the first iteration. */
    private Matrix inverseHessian_;

    //-- BFGS(const ext::shared_ptr<LineSearch>& lineSearch = ext::shared_ptr<LineSearch>())
    //-- : LineSearchBasedMethod(lineSearch) {}
    //-- in ql/math/optimization/bfgs.hpp:41
    public Bfgs() {
        this(null);
    }

    public Bfgs(final LineSearch lineSearch) {
        super(lineSearch);
    }

    //-- Array BFGS::getUpdatedDirection(const Problem& P, Real,
    //--                                 const Array& oldGradient)
    //-- in ql/math/optimization/bfgs.cpp:26
    private Array getUpdatedDirection(final Problem P, final Array oldGradient) {
        final int n = P.currentValue().size();

        if (inverseHessian_ == null || inverseHessian_.rows() == 0) {
            // first time in this update: identity
            inverseHessian_ = new Matrix(n, n);
            for (int i = 0; i < n; ++i)
                inverseHessian_.set(i, i, 1.0);
        }

        // diffGradient = lineSearch_->lastGradient() - oldGradient
        Array diffGradient = lineSearch_.lastGradient().sub(oldGradient);
        Array diffGradientWithHessianApplied = new Array(n);

        for (int i = 0; i < n; ++i) {
            double s = 0.0;
            for (int j = 0; j < n; ++j)
                s += inverseHessian_.get(i, j) * diffGradient.get(j);
            diffGradientWithHessianApplied.set(i, s);
        }

        double fac = 0.0, fae = 0.0, sumdg = 0.0, sumxi = 0.0;
        final Array searchDir = lineSearch_.searchDirection();
        for (int i = 0; i < n; ++i) {
            fac += diffGradient.get(i) * searchDir.get(i);
            fae += diffGradient.get(i) * diffGradientWithHessianApplied.get(i);
            final double dg = diffGradient.get(i);
            sumdg += dg * dg;
            final double xi = searchDir.get(i);
            sumxi += xi * xi;
        }

        // skip update if fac not sufficiently positive
        if (fac > Math.sqrt(1e-8 * sumdg * sumxi)) {
            fac = 1.0 / fac;
            final double fad = 1.0 / fae;

            // diffGradient = fac*searchDir - fad*diffGradientWithHessianApplied
            for (int i = 0; i < n; ++i) {
                diffGradient.set(i,
                        fac * searchDir.get(i) - fad * diffGradientWithHessianApplied.get(i));
            }

            // inverseHessian += fac*xi*xi^T - fad*(H*dg)(H*dg)^T + fae*dg*dg^T
            for (int i = 0; i < n; ++i) {
                for (int j = 0; j < n; ++j) {
                    double v = inverseHessian_.get(i, j);
                    v += fac * searchDir.get(i) * searchDir.get(j);
                    v -= fad * diffGradientWithHessianApplied.get(i)
                            * diffGradientWithHessianApplied.get(j);
                    v += fae * diffGradient.get(i) * diffGradient.get(j);
                    inverseHessian_.set(i, j, v);
                }
            }
        }
        // else: skip — C++ also silently skips ("FAC not sufficiently positive")

        // direction = - H^{-1} g
        final Array direction = new Array(n);
        final Array g = lineSearch_.lastGradient();
        for (int i = 0; i < n; ++i) {
            double s = 0.0;
            for (int j = 0; j < n; ++j)
                s -= inverseHessian_.get(i, j) * g.get(j);
            direction.set(i, s);
        }

        return direction;
    }

    //-- EndCriteria::Type LineSearchBasedMethod::minimize(Problem& P,
    //--                                  const EndCriteria& endCriteria)
    //-- in ql/math/optimization/linesearchbasedmethod.cpp:36
    //
    // Inlined here because the Java LineSearchBasedMethod base does not
    // implement minimize() and does not expose the getUpdatedDirection
    // template-method hook.
    @Override
    public Type minimize(final Problem P, final EndCriteria endCriteria) {
        // Initialisations
        final double ftol = endCriteria.functionEpsilon_;
        // C++ uses maxStationaryStateIterations as the mutating ref handed to
        // checkStationaryFunctionValue (an int-as-Size& quirk in the C++ code,
        // ql/math/optimization/linesearchbasedmethod.cpp:101).
        final int[] maxStationaryStateIterations = { endCriteria.maxStationaryStateIterations_ };
        final EndCriteria.Type[] ecType = { EndCriteria.Type.None };  // reset end criteria
        P.reset();                                                      // reset problem
        Array x_ = P.currentValue();                                    // store the starting point
        int iterationNumber = 0;
        // dimension line search
        lineSearch_.setSearchDirection(new Array(x_.size()));
        boolean done = false;

        // function and squared norm of gradient values
        double fnew, fold, gold2;
        double fdiff;
        // classical initial value for line-search step
        double t = 1.0;
        final int sz = lineSearch_.searchDirection().size();
        Array prevGradient = new Array(sz);
        // Initialize cost function, gradient prevGradient and search direction
        P.setFunctionValue(P.valueAndGradient(prevGradient, x_));
        P.setGradientNormValue(prevGradient.dotProduct(prevGradient));
        lineSearch_.setSearchDirection(prevGradient.mul(-1.0));

        // Reset inverseHessian for each minimize call so successive runs
        // start from identity (matches C++: BFGS member is reset by the
        // C++ user via fresh BFGS instance; in Java we get the same effect
        // by allocating per-minimize).
        inverseHessian_ = null;

        boolean firstTime = true;
        // Loop over iterations
        do {
            // Linesearch
            if (!firstTime)
                prevGradient = lineSearch_.lastGradient();
            t = lineSearch_.evaluate(P, ecType, endCriteria, t);
            // don't throw: it can fail just because maxIterations exceeded
            if (lineSearch_.succeed()) {
                // Updates

                // New point
                x_ = lineSearch_.lastX();
                // New function value
                fold = P.functionValue();
                P.setFunctionValue(lineSearch_.lastFunctionValue());

                // orthogonalisation coef
                gold2 = P.gradientNormValue();
                P.setGradientNormValue(lineSearch_.lastGradientNorm2());

                // BFGS search direction (replaces C++ getUpdatedDirection call;
                // gold2 is unused by BFGS — it is only kept in the C++ ABI for
                // ConjugateGradient).
                final Array direction = getUpdatedDirection(P, prevGradient);
                lineSearch_.setSearchDirection(direction);

                // Now compute accuracy and check end criteria
                // Numerical Recipes exit strategy on fx (see NR in C++, p.423)
                fnew = P.functionValue();
                fdiff = 2.0 * Math.abs(fnew - fold)
                        / (Math.abs(fnew) + Math.abs(fold) + 1e-16);
                if (fdiff < ftol
                        || endCriteria.checkMaxIterations(iterationNumber, ecType)) {
                    endCriteria.checkStationaryFunctionValue(0.0, 0.0,
                            maxStationaryStateIterations, ecType);
                    endCriteria.checkMaxIterations(iterationNumber, ecType);
                    return ecType[0];
                }
                P.setCurrentValue(x_);      // update problem current value
                ++iterationNumber;          // Increase iteration number
                firstTime = false;
            } else {
                done = true;
            }
        } while (!done);
        P.setCurrentValue(x_);
        return ecType[0];
    }
}
