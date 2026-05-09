/*
 Copyright (C) 2026 JQuantLib migration contributors

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

package org.jquantlib.termstructures.yieldcurves;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.Constraint;
import org.jquantlib.math.optimization.NoConstraint;
import org.jquantlib.math.optimization.OptimizationMethod;

/**
 * Simple polynomial fitting method.
 * <p>
 * Fits a discount function in Bernstein form:
 * <pre>
 * d(t) = sum_{i=0..size_-1} x[i] * B(i,i,t)              if !constrainAtZero
 * d(t) = 1 + sum_{i=0..size_-1} x[i] * B(i+1,i+1,t)      if constrainAtZero (d(0)=1)
 * </pre>
 * where B(i,n,t) = C(n,i) * t^i * (1-t)^(n-i) is the i-th Bernstein basis
 * polynomial of degree n. Note B(n,n,t) = t^n simplifies the diagonal
 * basis used by this method to {@code x[i] * t^(i+1)} (constrained) or
 * {@code x[i] * t^i} (unconstrained).
 *
 * <p>Faithful port of QuantLib v1.42.1 {@code SimplePolynomialFitting}
 * (ql/termstructures/yield/nonlinearfittingmethods.{hpp,cpp}). The C++
 * implementation calls {@code BernsteinPolynomial::get(i,n,t)} which for
 * the diagonal case (i==n) reduces to t^i — Java reproduces the reduction
 * inline because BernsteinPolynomial is not yet ported.
 *
 * <p>Phase 5d.5-ZCS+FB.
 */
public class SimplePolynomialFitting extends FittingMethod {

    private final int size_;

    public SimplePolynomialFitting(final int degree, final boolean constrainAtZero) {
        this(degree, constrainAtZero,
             new Array(0), null, new Array(0),
             0.0, Double.MAX_VALUE, new NoConstraint());
    }

    public SimplePolynomialFitting(final int degree,
                                   final boolean constrainAtZero,
                                   final Array weights,
                                   final OptimizationMethod optimizationMethod,
                                   final Array l2,
                                   final double minCutoffTime,
                                   final double maxCutoffTime,
                                   final Constraint constraint) {
        super(constrainAtZero, weights, optimizationMethod, l2,
              minCutoffTime, maxCutoffTime, constraint);
        // C++: size_(constrainAtZero ? degree : degree + 1)
        this.size_ = constrainAtZero ? degree : degree + 1;
    }

    public SimplePolynomialFitting(final int degree,
                                   final boolean constrainAtZero,
                                   final Array weights,
                                   final Array l2,
                                   final double minCutoffTime,
                                   final double maxCutoffTime,
                                   final Constraint constraint) {
        this(degree, constrainAtZero, weights, null, l2,
             minCutoffTime, maxCutoffTime, constraint);
    }

    @Override
    public SimplePolynomialFitting clone() {
        // degree to reconstruct: in unconstrained mode size_ = degree+1, so
        // "stored degree" = constrainAtZero ? size_ : size_ - 1.
        final int storedDegree = constrainAtZero_ ? size_ : size_ - 1;
        return new SimplePolynomialFitting(storedDegree, constrainAtZero_,
                weights(), optimizationMethod(), l2(),
                0.0, Double.MAX_VALUE, constraint());
    }

    @Override
    public int size() {
        return size_;
    }

    @Override
    protected double discountFunction(final Array x, final double t) {
        // Bernstein diagonal: B(i,i,t) = C(i,i) * t^i * (1-t)^0 = t^i.
        double d;
        if (!constrainAtZero_) {
            d = 0.0;
            // d = sum_{i=0..size_-1} x[i] * t^i
            double tpow = 1.0;
            for (int i = 0; i < size_; ++i) {
                d += x.get(i) * tpow;
                tpow *= t;
            }
        } else {
            d = 1.0;
            // d = 1 + sum_{i=0..size_-1} x[i] * t^(i+1)
            double tpow = t;
            for (int i = 0; i < size_; ++i) {
                d += x.get(i) * tpow;
                tpow *= t;
            }
        }
        return d;
    }
}
