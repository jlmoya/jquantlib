/*
 Copyright (C) 2015 Andres Hernandez
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

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.randomnumbers.MersenneTwisterUniformRng;

/**
 * Isotropic random walk.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/experimental/math/isotropicrandomwalk.hpp}.
 *
 * <p>A radius-distribution variate is drawn first; the position on the surface
 * of the {@code dim}-dimensional sphere is then sampled uniformly. This Java
 * port replaces the C++ template parameters with a functional interface
 * {@link RadiusDistribution}.
 */
public class IsotropicRandomWalk {

    /**
     * Functional interface representing a draw from a radius distribution. The
     * implementation receives a uniform variate {@code u in (0,1)} and returns
     * a non-negative radius.
     */
    public interface RadiusDistribution {
        double draw(double u);
    }

    private final RadiusDistribution distribution_;
    private final MersenneTwisterUniformRng radiusRng_;
    private final MersenneTwisterUniformRng angleRng_;
    private double[] weights_;
    private int dim_;

    public IsotropicRandomWalk(final RadiusDistribution distribution,
                               final int dim,
                               final long seed) {
        this(distribution, dim, null, seed);
    }

    public IsotropicRandomWalk(final RadiusDistribution distribution,
                               final int dim,
                               final double[] weights,
                               final long seed) {
        this.distribution_ = distribution;
        this.radiusRng_ = new MersenneTwisterUniformRng(seed);
        this.angleRng_ = new MersenneTwisterUniformRng(seed);
        this.dim_ = dim;
        if (weights == null || weights.length == 0) {
            this.weights_ = new double[dim];
            for (int i = 0; i < dim; ++i) this.weights_[i] = 1.0;
        } else {
            QL.require(dim == weights.length, "Invalid weights");
            this.weights_ = weights.clone();
        }
    }

    /** Update dimension only; weights default to 1. */
    public void setDimension(final int dim) {
        this.dim_ = dim;
        this.weights_ = new double[dim];
        for (int i = 0; i < dim; ++i) this.weights_[i] = 1.0;
    }

    /** Update dimension and explicit weights. */
    public void setDimension(final int dim, final double[] weights) {
        QL.require(dim == weights.length, "Invalid weights");
        this.dim_ = dim;
        this.weights_ = weights.clone();
    }

    /** Update dimension using lower/upper bounds to compute ellipsoidal weights. */
    public void setDimension(final int dim,
                             final Array lowerBound,
                             final Array upperBound) {
        QL.require(dim == lowerBound.size(), "Incompatible dimension and lower bound");
        QL.require(dim == upperBound.size(), "Incompatible dimension and upper bound");
        final double[] bounds = new double[dim];
        double maxBound = upperBound.get(0) - lowerBound.get(0);
        bounds[0] = maxBound;
        for (int j = 1; j < dim; ++j) {
            bounds[j] = upperBound.get(j) - lowerBound.get(j);
            if (bounds[j] > maxBound) maxBound = bounds[j];
        }
        for (int j = 0; j < dim; ++j) {
            bounds[j] /= maxBound;
        }
        setDimension(dim, bounds);
    }

    /**
     * Write the next draw into {@code out} (of length {@code dim}).
     */
    public void nextReal(final double[] out) {
        double radius = distribution_.draw(radiusRng_.next().value());
        if (dim_ > 1) {
            double phi = Math.PI * angleRng_.next().value();
            int idx = 0;
            for (int i = 0; i < dim_ - 2; ++i) {
                out[idx] = radius * Math.cos(phi) * weights_[idx];
                radius *= Math.sin(phi);
                phi = Math.PI * angleRng_.next().value();
                ++idx;
            }
            out[idx] = radius * Math.cos(2.0 * phi) * weights_[idx];
            out[idx + 1] = radius * Math.sin(2.0 * phi) * weights_[idx + 1];
        } else {
            if (angleRng_.next().value() < 0.5) {
                out[0] = -radius * weights_[0];
            } else {
                out[0] = radius * weights_[0];
            }
        }
    }
}
