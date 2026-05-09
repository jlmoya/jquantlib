/*
 Copyright (C) 2026 Jose Moya

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.
 */

/*
 Copyright (C) 2007 Mark Joshi
*/

package org.jquantlib.math;

/**
 * Quadratic-formula utility.
 *
 * <p>Java port of {@code ql/math/quadratic.{hpp,cpp}} (QuantLib v1.42.1; class
 * named {@code quadratic} lower-case in C++; renamed {@code Quadratic} per
 * Java naming conventions).
 *
 * <p>Phase 3j L0.3.
 */
public final class Quadratic {

    private final double a_;
    private final double b_;
    private final double c_;

    public Quadratic(final double a, final double b, final double c) {
        this.a_ = a;
        this.b_ = b;
        this.c_ = c;
    }

    /** {@code -b/(2a)}. */
    public double turningPoint() {
        return -b_ / (2.0 * a_);
    }

    /** Evaluation at the turning point. */
    public double valueAtTurningPoint() {
        return apply(turningPoint());
    }

    /** {@code f(x) = a*x^2 + b*x + c} (computed as {@code x*(x*a + b) + c} per C++). */
    public double apply(final double x) {
        return x * (x * a_ + b_) + c_;
    }

    /** Discriminant {@code b^2 - 4ac}. */
    public double discriminant() {
        return b_ * b_ - 4.0 * a_ * c_;
    }

    /**
     * If real roots exist puts them in {@code out[0]} (smaller), {@code out[1]}
     * (larger) and returns true. Otherwise puts the turning point in both
     * positions and returns false.
     *
     * @param out length-2 array; mutated.
     * @return true iff roots are real.
     */
    public boolean roots(final double[] out) {
        double d = discriminant();
        if (d < 0.0) {
            final double tp = turningPoint();
            out[0] = tp;
            out[1] = tp;
            return false;
        }
        d = Math.sqrt(d);
        out[0] = (-b_ - d) / (2.0 * a_);
        out[1] = (-b_ + d) / (2.0 * a_);
        return true;
    }
}
