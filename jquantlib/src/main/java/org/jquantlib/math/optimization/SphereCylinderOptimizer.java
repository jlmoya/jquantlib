/*
 Copyright (C) 2007 Mark Joshi
 Copyright (C) 2009 Ueli Hofstetter
 Copyright (C) 2026 Jose Moya (Java port)

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.math.optimization;

import org.jquantlib.QL;

//-- class SphereCylinderOptimizer; in ql/math/optimization/spherecylinder.hpp:40
/**
 * Finds the point on the intersection of a sphere (centred at origin, radius
 * {@code r}) and a vertical cylinder (centred at {@code (alpha, 0)}, radius
 * {@code s}) closest to a reference point {@code z = (z1, z2, z3)} in R^3.
 *
 * <p>Faithful port of QuantLib C++ v1.42.1
 * {@code ql/math/optimization/spherecylinder.hpp|cpp}.
 *
 * <p>The C++ API mutates caller-supplied {@code Real&} parameters for the
 * result coordinates. Java uses {@code double[]} length-3 holders so the
 * caller reads {@code y[0]}, {@code y[1]}, {@code y[2]} after the call.
 */
public class SphereCylinderOptimizer {

    private final double r_;
    private final double s_;
    private final double alpha_;
    private final double z1_;
    private final double z2_;
    private final double z3_;
    private final double zweight_;
    private final double topValue_;
    private final double bottomValue_;
    private final boolean nonEmpty_;

    //-- SphereCylinderOptimizer(Real r, Real s, Real alpha, Real z1, Real z2, Real z3,
    //--                          Real zweight = 1.0);
    //-- in ql/math/optimization/spherecylinder.cpp:75
    public SphereCylinderOptimizer(final double r, final double s, final double alpha,
                                   final double z1, final double z2, final double z3,
                                   final double zweight) {
        QL.require(r > 0, "sphere must have positive radius");
        final double sClamped = Math.max(s, 0.0);
        QL.require(alpha > 0, "cylinder centre must have positive coordinate");

        this.r_ = r;
        this.s_ = sClamped;
        this.alpha_ = alpha;
        this.z1_ = z1;
        this.z2_ = z2;
        this.z3_ = z3;
        this.zweight_ = zweight;
        this.nonEmpty_ = Math.abs(alpha - sClamped) <= r;

        final double cylinderInside = r * r - (sClamped + alpha) * (sClamped + alpha);
        if (cylinderInside > 0.0) {
            this.topValue_ = alpha + sClamped;
            this.bottomValue_ = alpha - sClamped;
        } else {
            this.bottomValue_ = alpha - sClamped;
            final double tmp = r * r - (sClamped * sClamped + alpha * alpha);
            if (tmp <= 0) {
                // max to left of maximum
                final double topValue2 =
                        Math.sqrt(sClamped * sClamped - tmp * tmp / (4 * alpha * alpha));
                this.topValue_ =
                        alpha - Math.sqrt(sClamped * sClamped - topValue2 * topValue2);
            } else {
                this.topValue_ = alpha + tmp / (2.0 * alpha);
            }
        }
    }

    /** Convenience constructor using {@code zweight=1.0}. */
    public SphereCylinderOptimizer(final double r, final double s, final double alpha,
                                   final double z1, final double z2, final double z3) {
        this(r, s, alpha, z1, z2, z3, 1.0);
    }

    //-- bool isIntersectionNonEmpty() const; in spherecylinder.cpp:110
    public boolean isIntersectionNonEmpty() {
        return nonEmpty_;
    }

    //-- void findClosest(Size maxIterations, Real tolerance,
    //--                  Real& y1, Real& y2, Real& y3) const;
    //-- in ql/math/optimization/spherecylinder.cpp:114
    /**
     * Find the closest point by Brent-style 1D minimization starting from
     * the projection guess.
     *
     * @param y must be a length-3 array; mutated to contain (y1, y2, y3).
     */
    public void findClosest(final int maxIterations, final double tolerance, final double[] y) {
        QL.require(y != null && y.length == 3, "y must be double[3]");
        findByProjection(y);
        final double x1 = y[0];
        y[0] = brentMinimize(bottomValue_, x1, topValue_, tolerance, maxIterations);
        y[1] = Math.sqrt(s_ * s_ - (y[0] - alpha_) * (y[0] - alpha_));
        y[2] = Math.sqrt(r_ * r_ - y[0] * y[0] - y[1] * y[1]);
    }

    //-- Real SphereCylinderOptimizer::objectiveFunction(Real x1) const;
    //-- in ql/math/optimization/spherecylinder.cpp:130
    private double objectiveFunction(final double x1) {
        final double x2sq = s_ * s_ - (x1 - alpha_) * (x1 - alpha_);
        final double x2 = x2sq >= 0.0 ? Math.sqrt(x2sq) : 0.0;
        final double x3 = Math.sqrt(r_ * r_ - x1 * x1 - x2 * x2);

        double err = 0.0;
        err += (x1 - z1_) * (x1 - z1_);
        err += (x2 - z2_) * (x2 - z2_);
        err += (x3 - z3_) * (x3 - z3_) * zweight_;
        return err;
    }

    //-- bool findByProjection(Real& y1, Real& y2, Real& y3) const;
    //-- in ql/math/optimization/spherecylinder.cpp:147
    /**
     * Analytic projection of the reference point onto the cylinder,
     * then onto the sphere.
     *
     * @param y must be a length-3 array; mutated with the computed coordinates.
     * @return true if an intersection point was found; false if the projection
     *         point lies outside the sphere AND the intersection is empty.
     */
    public boolean findByProjection(final double[] y) {
        QL.require(y != null && y.length == 3, "y must be double[3]");
        final double z1moved = z1_ - alpha_;
        final double distance = Math.sqrt(z1moved * z1moved + z2_ * z2_);
        final double scale = s_ / distance;
        final double y1moved = z1moved * scale;
        y[0] = alpha_ + y1moved;
        y[1] = scale * z2_;
        final double residual = r_ * r_ - y[0] * y[0] - y[1] * y[1];
        if (residual >= 0.0) {
            y[2] = Math.sqrt(residual);
            return true;
        }
        // projection point outside sphere
        if (!isIntersectionNonEmpty()) {
            y[2] = 0.0;
            return false;
        }
        // intersection non-empty but projection outside — take rightmost point
        y[2] = 0.0;
        y[0] = topValue_;
        y[1] = Math.sqrt(r_ * r_ - y[0] * y[0]);
        return true;
    }

    //-- Golden-section / Brent 1D minimizer (anonymous namespace in C++); in
    //-- ql/math/optimization/spherecylinder.cpp:29. Inlined here because it is
    //-- used only by findClosest.
    private double brentMinimize(final double low0, final double mid, final double high0,
                                 final double tolerance, final int maxIt) {
        final double W = 0.5 * (3.0 - Math.sqrt(5.0));
        double low = low0;
        double high = high0;
        double x = W * low + (1 - W) * high;
        if (mid > low && mid < high) {
            x = mid;
        }
        double midValue = objectiveFunction(x);

        int iterations = 0;
        while (high - low > tolerance && iterations < maxIt) {
            if (x - low > high - x) {
                // left interval is bigger
                final double tentativeNewMid = W * low + (1 - W) * x;
                final double tentativeNewMidValue = objectiveFunction(tentativeNewMid);
                if (tentativeNewMidValue < midValue) {  // go left
                    high = x;
                    x = tentativeNewMid;
                    midValue = tentativeNewMidValue;
                } else {  // go right
                    low = tentativeNewMid;
                }
            } else {
                // right interval is bigger
                final double tentativeNewMid = W * x + (1 - W) * high;
                final double tentativeNewMidValue = objectiveFunction(tentativeNewMid);
                if (tentativeNewMidValue < midValue) {  // go right
                    low = x;
                    x = tentativeNewMid;
                    midValue = tentativeNewMidValue;
                } else {  // go left
                    high = tentativeNewMid;
                }
            }
            ++iterations;
        }
        return x;
    }

    //-- std::vector<Real> sphereCylinderOptimizerClosest(Real r, Real s, Real alpha,
    //--                    Real z1, Real z2, Real z3, Natural maxIterations,
    //--                    Real tolerance, Real finalWeight = 1.0);
    //-- in ql/math/optimization/spherecylinder.cpp:176
    /**
     * Convenience helper matching the C++ free function
     * {@code sphereCylinderOptimizerClosest}. If {@code maxIterations == 0}
     * it returns the projection-based estimate; otherwise it Brent-minimizes.
     *
     * @return a length-3 array {y1, y2, y3}.
     * @throws org.jquantlib.lang.exceptions.LibraryException if the
     *         intersection is empty.
     */
    public static double[] sphereCylinderOptimizerClosest(
            final double r, final double s, final double alpha,
            final double z1, final double z2, final double z3,
            final int maxIterations, final double tolerance, final double zweight) {
        final SphereCylinderOptimizer opt =
                new SphereCylinderOptimizer(r, s, alpha, z1, z2, z3, zweight);
        QL.require(opt.isIntersectionNonEmpty(), "intersection empty so no solution");
        final double[] y = new double[3];
        if (maxIterations == 0) {
            opt.findByProjection(y);
        } else {
            opt.findClosest(maxIterations, tolerance, y);
        }
        return y;
    }

    /** Convenience overload using {@code zweight=1.0}. */
    public static double[] sphereCylinderOptimizerClosest(
            final double r, final double s, final double alpha,
            final double z1, final double z2, final double z3,
            final int maxIterations, final double tolerance) {
        return sphereCylinderOptimizerClosest(r, s, alpha, z1, z2, z3,
                maxIterations, tolerance, 1.0);
    }
}
