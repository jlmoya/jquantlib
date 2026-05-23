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
 Copyright (C) 2015 Ferdinando Ametrano
 Copyright (C) 2015 Paolo Mazzocchi

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.math;

import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;

/**
 * Polynomial functional form: {@code f(t) = sum_{i=0}^{n-1} c_i * t^i}.
 * <p>
 * Mirrors C++ {@code PolynomialFunction} in {@code ql/math/polynomialmathfunction.hpp} (v1.42.1).
 *
 * @author JQuantLib migration contributors
 */
public class PolynomialFunction {

    private final int order_;
    private final double[] c_;
    private final double[] derC_;
    private final double[] prC_;
    private final double K_ = 0.0;
    private final Matrix eqs_;
    private boolean eqsInitialized_ = false;
    private double lastT_ = Double.NaN;
    private double lastT2_ = Double.NaN;

    public PolynomialFunction(final double[] coeff) {
        QL.require(coeff != null && coeff.length > 0, "empty coefficient vector");
        this.order_ = coeff.length;
        this.c_ = coeff.clone();
        this.derC_ = new double[Math.max(order_ - 1, 0)];
        this.prC_ = new double[order_];
        this.eqs_ = new Matrix(order_, order_);

        int i;
        for (i = 0; i < order_ - 1; ++i) {
            prC_[i] = c_[i] / (i + 1);
            derC_[i] = c_[i + 1] * (i + 1);
        }
        // i == order_ - 1 (final iteration, no derC_)
        prC_[i] = c_[i] / (i + 1);
    }

    /** Function value at time {@code t}. */
    public double op(final double t) {
        double result = 0.0;
        double tPower = 1.0;
        for (int i = 0; i < order_; ++i) {
            result += c_[i] * tPower;
            tPower *= t;
        }
        return result;
    }

    /** First derivative at time {@code t}. */
    public double derivative(final double t) {
        double result = 0.0;
        double tPower = 1.0;
        for (int i = 0; i < order_ - 1; ++i) {
            result += derC_[i] * tPower;
            tPower *= t;
        }
        return result;
    }

    /** Indefinite integral at time {@code t} (with K=0). */
    public double primitive(final double t) {
        double result = K_;
        double tPower = t;
        for (int i = 0; i < order_; ++i) {
            result += prC_[i] * tPower;
            tPower *= t;
        }
        return result;
    }

    /** Definite integral between {@code t1} and {@code t2}. */
    public double definiteIntegral(final double t1, final double t2) {
        return primitive(t2) - primitive(t1);
    }

    public int order() { return order_; }

    public double[] coefficients() {
        return c_.clone();
    }

    public double[] derivativeCoefficients() {
        return derC_.clone();
    }

    public double[] primitiveCoefficients() {
        return prC_.clone();
    }

    /**
     * Coefficients of a PolynomialFunction defined as a definite integral on the
     * rolling window {@code [t, t+tau]} with {@code tau = t2-t}.
     */
    public double[] definiteIntegralCoefficients(final double t, final double t2) {
        final Array k = new Array(c_);
        initializeEqs(t, t2);
        final Array coeff = eqs_.mul(k);
        final double[] result = new double[order_];
        for (int i = 0; i < order_; ++i) {
            result[i] = coeff.get(i);
        }
        return result;
    }

    /**
     * Coefficients of a PolynomialFunction defined as a definite derivative on the
     * rolling window {@code [t, t+tau]} with {@code tau = t2-t}.
     */
    public double[] definiteDerivativeCoefficients(final double t, final double t2) {
        final Array k = new Array(c_);
        initializeEqs(t, t2);
        final Matrix inv = eqs_.inverse();
        final Array coeff = inv.mul(k);
        final double[] result = new double[order_];
        for (int i = 0; i < order_; ++i) {
            result[i] = coeff.get(i);
        }
        return result;
    }

    private void initializeEqs(final double t, final double t2) {
        final double dt = t2 - t;
        for (int i = 0; i < order_; ++i) {
            double tau = 1.0;
            for (int j = i; j < order_; ++j) {
                tau *= dt;
                final List<Long> row = PascalTriangle.get(j + 1);
                eqs_.set(i, j, (tau * row.get(i)) / (j + 1));
            }
        }
        this.eqsInitialized_ = true;
        this.lastT_ = t;
        this.lastT2_ = t2;
    }
}
