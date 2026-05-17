/*
 Copyright (C) 2009 Dimitri Reiswich
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

package org.jquantlib.math;

import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.math.distributions.NormalDistribution;

/**
 * Gaussian kernel function.
 *
 * <p>Java port of QuantLib v1.42.1 {@code ql/math/kernelfunctions.hpp}
 * (class {@code GaussianKernel}).
 *
 * <p>The Gaussian kernel evaluates to
 * <pre>
 *   K(x) = sqrt(2*pi) * pdf(x; mean, sigma)
 * </pre>
 * where {@code pdf} is the normal probability density function with the
 * given mean and standard deviation. The {@code sqrt(2*pi)} factor cancels
 * the {@code 1/(sigma*sqrt(2*pi))} normalisation in the pdf so that
 * {@code K(mean) = 1/sigma} — matching the C++ behaviour exactly.
 *
 * @author Phase 5e.5b-CFC-d-59 port
 */
public class GaussianKernel implements KernelFunction {

    private final NormalDistribution nd_;
    private final CumulativeNormalDistribution cnd_;
    private final double normFact_;

    /**
     * Construct a Gaussian kernel with the given mean and standard
     * deviation.
     *
     * @param average mean of the normal distribution
     * @param sigma   standard deviation (must be &gt; 0)
     */
    public GaussianKernel(final double average, final double sigma) {
        this.nd_  = new NormalDistribution(average, sigma);
        this.cnd_ = new CumulativeNormalDistribution(average, sigma);
        // normFact_ = sqrt(2) * sqrt(pi) = sqrt(2*pi)
        this.normFact_ = Constants.M_SQRT2 * Constants.M_SQRTPI;
    }

    /**
     * Evaluate the kernel at point {@code x}.
     */
    @Override
    public double op(final double x) {
        return nd_.op(x) * normFact_;
    }

    /**
     * First derivative of the kernel at point {@code x}.
     */
    public double derivative(final double x) {
        return nd_.derivative(x) * normFact_;
    }

    /**
     * Primitive (antiderivative) of the kernel at point {@code x}.
     */
    public double primitive(final double x) {
        return cnd_.op(x) * normFact_;
    }
}
