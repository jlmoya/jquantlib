/*
 Copyright (C) 2008 Richard Gomes

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

package org.jquantlib.math.distributions;

import org.jquantlib.QL;
import org.jquantlib.math.Constants;

/**
 * Provides the probability density function (pdf) of the (unit) normal distribution
 *
 * {@latex[ \frac{1}{\sigma \sqrt{2\pi} } \exp \left(-\frac{(x-\mu)^2}{2\sigma ^2} \right) }
 *
 * @author Richard Gomes
 * @see <a href="http://en.wikipedia.org/wiki/Probability_density_function">Normal Distribution</a>
 */
// TODO: code review :: license, class comments, comments for access modifiers, comments for @Override
public class NormalDistribution implements Derivative {

    //
    // protected fields
    //

    private final double normalizationFactor; // FIXME: code review
    private final double denominator; // FIXME: code review

    //
    // private fields
    //
    private final double denormalizationFactor;
    protected double average;
    protected double sigma;

    //
    // public constructors
    //

    /**
     * Default constructor which assumes {@latex$ \mu \leftarrow 0.0} and {@latex \sigma \leftarrow 1.0 }.
     */
    public NormalDistribution() {
        this(0.0, 1.0);
    }

    /**
     * Default constructor which assumes {@latex \sigma \leftarrow 1.0 }.
     *
     * @param average
     */
    public NormalDistribution(final double average) {
        this(average, 1.0);
    }

    /**
     * Constructor which initializes {@latex$ \mu } and {@latex \sigma }.
     *
     * @param average
     * @param sigma
     */
    public NormalDistribution(final double average, final double sigma) {
        QL.require(sigma > 0.0, "sigma must be greater than 0.0");

        this.average = average;
        this.sigma = sigma;

        this.normalizationFactor = Constants.M_SQRT_2 * Constants.M_1_SQRTPI / sigma;
        this.denormalizationFactor = sigma * sigma;
        this.denominator = 2.0 * denormalizationFactor;
    }

    //
    // implements Ops.DoubleOp
    //

    /**
     * {@inheritDoc}
     * <p>
     * Computes the Normal distribution at point {@latex$ x }
     *
     * @param x
     * @return the Normal distribution at point {@latex$ x }
     */
    @Override
    public double op(final double x) /* @ReadOnly */ {
        // Mirrors C++ v1.42.1 normaldistribution.hpp NormalDistribution::operator():
        //   delta = x - mean
        //   exponent = -delta^2 / (2*sigma^2)
        //   pdf = M_SQRT_2 * M_1_SQRTPI / sigma * exp(exponent)
        // Phase 5h.5-RND align: the previous implementation computed only the
        // standard-normal density, ignoring stored mean/sigma. Callers using
        // the (mean, sigma) constructor (e.g. GenericGaussianStatistics,
        // BSMRNDCalculator, LocalVolRNDCalculator) now match C++.
        final double delta = x - average;
        final double exponent = -(delta * delta) / denominator;
        if ( exponent <= -690.0 )
            return 0.0;
        return normalizationFactor * Math.exp(exponent);
    }

    //
    // implements Derivative
    //

    /**
     * {@inheritDoc}
     * <p>
     * Calculates the first derivative of a Normal distribution at point {@latex$ x }
     *
     * @param x
     * @return the first derivative of a Normal distribution at point {@latex$ x }
     */
    @Override
    public double derivative(final double x) /* @ReadOnly */ {
        return (op(x) * (average - x)) / denormalizationFactor;
    }

}
