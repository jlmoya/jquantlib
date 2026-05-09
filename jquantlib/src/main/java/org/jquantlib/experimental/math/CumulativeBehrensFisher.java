/*
 Copyright (C) 2014 Jose Aparicio
 Copyright (C) 2026 JQuantLib migration contributors.

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

package org.jquantlib.experimental.math;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.math.Factorial;

/**
 * Cumulative (generalized) Behrens-Fisher distribution.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/experimental/math/convolvedstudentt.{hpp,cpp}}.
 *
 * <p>Exact analytical computation of the cumulative probability distribution
 * of the linear combination of an arbitrary number of T random variables of
 * odd integer order. Adapted from the algorithm in
 *
 * <p>V. Witkovsky, Journal of Statistical Planning and Inference 94 (2001)
 * 1-13.
 *
 * <p>Implementation only supports odd orders.
 */
public class CumulativeBehrensFisher {

    private static final Factorial FACT = new Factorial();

    private final List<Integer> degreesFreedom_;
    private final List<Double> factors_;

    private final List<List<Double>> polynCharFnc_;
    private List<Double> polyConvolved_;

    // cached factor in the exponential of the characteristic function
    private double a_;
    private double a2_;

    public CumulativeBehrensFisher(final List<Integer> degreesFreedom,
                                   final List<Double> factors) {
        QL.require(degreesFreedom.size() == factors.size(),
                "Incompatible sizes in convolution.");
        for (final Integer i : degreesFreedom) {
            QL.require(i % 2 != 0, "Even degree of freedom not allowed");
            QL.require(i >= 0, "Negative degree of freedom not allowed");
        }
        this.degreesFreedom_ = new ArrayList<>(degreesFreedom);
        this.factors_ = new ArrayList<>(factors);
        this.polyConvolved_ = new ArrayList<>();
        this.polyConvolved_.add(1.0);
        this.polynCharFnc_ = new ArrayList<>();

        for (int i = 0; i < degreesFreedom_.size(); ++i) {
            polynCharFnc_.add(polynCharactT((degreesFreedom_.get(i) - 1) / 2));
        }
        // Adjust the polynomial coefficients by the factors
        for (int i = 0; i < degreesFreedom_.size(); ++i) {
            double multiplier = 1.0;
            final List<Double> p = polynCharFnc_.get(i);
            for (int k = 1; k < p.size(); ++k) {
                multiplier *= Math.abs(factors_.get(i));
                p.set(k, p.get(k) * multiplier);
            }
        }
        // Convolve all polynomials
        for (final List<Double> p : polynCharFnc_) {
            polyConvolved_ = convolveVectorPolynomials(polyConvolved_, p);
        }
        // Trim trailing zeros
        while (!polyConvolved_.isEmpty()
                && polyConvolved_.get(polyConvolved_.size() - 1) == 0.0) {
            polyConvolved_.remove(polyConvolved_.size() - 1);
        }
        // Cache 'a' value (the exponent)
        a_ = 0.0;
        for (int i = 0; i < degreesFreedom_.size(); ++i) {
            a_ += Math.sqrt((double) degreesFreedom_.get(i)) * Math.abs(factors_.get(i));
        }
        a2_ = a_ * a_;
    }

    /** Degrees of freedom of the Ts involved in the convolution. */
    public List<Integer> degreeFreedom() {
        return Collections.unmodifiableList(degreesFreedom_);
    }

    /** Factors in the linear combination. */
    public List<Double> factors() {
        return Collections.unmodifiableList(factors_);
    }

    /**
     * Student t characteristic-polynomial generator.
     * For odd order nu = 2n+1 the characteristic function is
     * {@code phi(t) = phi_n(t) * exp(-nu^{1/2} |t|)}.
     */
    private List<Double> polynCharactT(final int n) {
        final int nu = 2 * n + 1;
        final List<Double> low = new ArrayList<>();
        low.add(1.0);
        final List<Double> high = new ArrayList<>();
        high.add(1.0);
        high.add(Math.sqrt((double) nu));
        if (n == 0) return low;
        if (n == 1) return high;

        List<Double> lowL = low;
        List<Double> highL = high;
        for (int k = 1; k < n; ++k) {
            final List<Double> recursionFactor = new ArrayList<>();
            recursionFactor.add(0.0);
            recursionFactor.add(0.0);
            recursionFactor.add(((double) nu) / ((2.0 * k + 1.0) * (2.0 * k - 1.0)));
            final List<Double> lowUp = convolveVectorPolynomials(recursionFactor, lowL);
            for (int i = 0; i < highL.size(); ++i) {
                lowUp.set(i, lowUp.get(i) + highL.get(i));
            }
            lowL = highL;
            highL = lowUp;
        }
        return highL;
    }

    private List<Double> convolveVectorPolynomials(final List<Double> v1,
                                                   final List<Double> v2) {
        final List<Double> shorter = v1.size() < v2.size() ? v1 : v2;
        final List<Double> longer = (v1 == shorter) ? v2 : v1;

        final int newDegree = v1.size() + v2.size() - 2;
        final List<Double> result = new ArrayList<>();
        for (int k = 0; k <= newDegree; ++k) {
            result.add(0.0);
        }
        for (int polyOrdr = 0; polyOrdr < result.size(); ++polyOrdr) {
            final int from = Math.max(0, polyOrdr - longer.size() + 1);
            final int to = Math.min(polyOrdr, shorter.size() - 1);
            double acc = 0.0;
            for (int i = from; i <= to; ++i) {
                acc += shorter.get(i) * longer.get(polyOrdr - i);
            }
            result.set(polyOrdr, acc);
        }
        return result;
    }

    /**
     * Returns the cumulative probability of the resulting distribution.
     * Applies the Gil-Pelaez theorem analytically.
     */
    public double op(final double x) {
        // 1st & 0th terms with the table integration
        double integral = polyConvolved_.get(0) * Math.atan(x / a_);
        final double squared = a2_ + x * x;
        final double rootsqr = Math.sqrt(squared);
        final double atan2xa = Math.atan2(-x, a_);
        if (polyConvolved_.size() > 1) {
            integral += polyConvolved_.get(1) * x / squared;
        }
        for (int exponent = 2; exponent < polyConvolved_.size(); ++exponent) {
            integral -= polyConvolved_.get(exponent)
                    * FACT.get(exponent - 1) * Math.sin(exponent * atan2xa)
                    / Math.pow(rootsqr, (double) exponent);
        }
        return 0.5 + integral / Math.PI;
    }

    /** Returns the probability density of the resulting distribution. */
    public double density(final double x) {
        final double squared = a2_ + x * x;
        double integral = polyConvolved_.get(0) * a_ / squared;
        final double rootsqr = Math.sqrt(squared);
        final double atan2xa = Math.atan2(-x, a_);
        for (int exponent = 1; exponent < polyConvolved_.size(); ++exponent) {
            integral += polyConvolved_.get(exponent)
                    * FACT.get(exponent) * Math.cos((exponent + 1) * atan2xa)
                    / Math.pow(rootsqr, (double) (exponent + 1));
        }
        return integral / Math.PI;
    }
}
