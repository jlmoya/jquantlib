/*
 Copyright (C) 2016 Klaus Spanderen
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

package org.jquantlib.experimental.models;

import org.jquantlib.QL;
import org.jquantlib.math.Constants;
import static org.jquantlib.math.Constants.NULL_REAL;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.integrals.GaussHermiteIntegration;
import org.jquantlib.math.interpolations.LagrangeInterpolation;
import org.jquantlib.math.interpolations.LinearInterpolation;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.finitedifferences.utilities.GBSMRNDCalculator;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.processes.OrnsteinUhlenbeckProcess;
import org.jquantlib.time.Date;
import org.jquantlib.util.LazyObject;

import java.util.function.BiFunction;

/**
 * CLV (Collocation Local Volatility) model with a normally distributed
 * kernel process (Ornstein-Uhlenbeck).
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/experimental/models/normalclvmodel.{hpp,cpp}}.
 *
 * <p>References: A. Grzelak, 2016, "The CLV Framework – A Fresh Look at
 * Efficient Pricing with Smile," SSRN 2747541.
 *
 * <p>The model maps from the OU process to the BS process via a collocation
 * mapping {@code g(t, x)} built using barycentric Lagrange interpolation at
 * Gauss-Hermite quadrature nodes.
 *
 * @author Phase 4j port
 */
public class NormalCLVModel extends LazyObject {

    private final double[] x_;           // scaled GH abscissae
    private final double sigma_;         // scaling of OU variable
    private final GeneralizedBlackScholesProcess bsProcess_;
    private final OrnsteinUhlenbeckProcess ouProcess_;
    private final Date[] maturityDates_;
    private final GBSMRNDCalculator rndCalculator_;
    private final double[] maturityTimes_;

    // populated in performCalculations
    private volatile BiFunction<Double, Double, Double> g_;

    /**
     * @param bsProcess     Generalized Black-Scholes process
     * @param ouProcess     Ornstein-Uhlenbeck kernel process
     * @param maturityDates collocation dates (sorted ascending)
     * @param lagrangeOrder number of Gauss-Hermite quadrature points
     * @param pMax          upper quantile (pass {@link Double#NaN} for none)
     * @param pMin          lower quantile (pass {@link Double#NaN} for none)
     */
    public NormalCLVModel(final GeneralizedBlackScholesProcess bsProcess,
                          final OrnsteinUhlenbeckProcess ouProcess,
                          final Date[] maturityDates,
                          final int lagrangeOrder,
                          final double pMax,
                          final double pMin) {
        // Compute scaled Hermite nodes: x = sqrt(2) * GH_nodes
        final GaussHermiteIntegration ghInt = new GaussHermiteIntegration(lagrangeOrder);
        final int n = lagrangeOrder;
        x_ = new double[n];
        final double sqrt2 = Math.sqrt(2.0);
        for (int i = 0; i < n; ++i) {
            x_[i] = sqrt2 * ghInt.x(i);
        }
        // Sort x_ ascending (GH nodes may not be sorted)
        java.util.Arrays.sort(x_);

        // Determine sigma scaling
        final InverseCumulativeNormal icn = new InverseCumulativeNormal();
        if (!Double.isNaN(pMax) && pMax != NULL_REAL) {
            sigma_ = x_[n - 1] / icn.op(pMax);
        } else if (!Double.isNaN(pMin) && pMin != NULL_REAL) {
            sigma_ = x_[0] / icn.op(pMin);
        } else {
            sigma_ = 1.0;
        }

        bsProcess_     = bsProcess;
        ouProcess_     = ouProcess;
        maturityDates_ = maturityDates.clone();
        rndCalculator_ = new GBSMRNDCalculator(bsProcess);
        maturityTimes_ = new double[maturityDates.length];

        for (int i = 0; i < maturityTimes_.length; ++i) {
            maturityTimes_[i] = bsProcess_.time(maturityDates[i]);
            QL.require(i == 0 || maturityTimes_[i - 1] < maturityTimes_[i],
                    "NormalCLVModel: maturity dates must be strictly ascending");
        }

        bsProcess_.addObserver(this);
        ouProcess_.addObserver(this);
    }

    /** Construct with no quantile clipping (pMax = pMin = NaN). */
    public NormalCLVModel(final GeneralizedBlackScholesProcess bsProcess,
                          final OrnsteinUhlenbeckProcess ouProcess,
                          final Date[] maturityDates,
                          final int lagrangeOrder) {
        this(bsProcess, ouProcess, maturityDates, lagrangeOrder,
                Double.NaN, Double.NaN);
    }

    /**
     * Cumulative distribution function of the BS process at date {@code d}
     * and spot {@code k}.
     */
    public double cdf(final Date d, final double k) {
        return rndCalculator_.cdf(k, bsProcess_.time(d));
    }

    /**
     * Inverse CDF of the BS process at date {@code d} and quantile {@code q}.
     */
    public double invCDF(final Date d, final double q) {
        return rndCalculator_.invcdf(q, bsProcess_.time(d));
    }

    /**
     * Collocation points of the OU process at date {@code d}
     * (in OU-space, i.e. expectation + stdDev * x_[i]).
     */
    public double[] collocationPointsX(final Date d) {
        final double t  = bsProcess_.time(d);
        final double mu = ouProcess_.expectation(0.0, ouProcess_.x0(), t);
        final double sd = ouProcess_.stdDeviation(0.0, ouProcess_.x0(), t);
        final double[] pts = new double[x_.length];
        for (int i = 0; i < x_.length; ++i) {
            pts[i] = mu + sd * x_[i];
        }
        return pts;
    }

    /**
     * Collocation points for the underlying S_T at date {@code d}
     * (in BS-space, mapped via the invCDF).
     */
    public double[] collocationPointsY(final Date d) {
        final CumulativeNormalDistribution N  = new CumulativeNormalDistribution();
        final double[] s = new double[x_.length];
        for (int i = 0; i < x_.length; ++i) {
            s[i] = invCDF(d, N.op(x_[i] / sigma_));
        }
        return s;
    }

    /**
     * Returns the CLV mapping function {@code g(t, x)}.
     * Triggers {@link #performCalculations()} on the first call or after
     * an update.
     */
    public BiFunction<Double, Double, Double> g() {
        calculate();
        return g_;
    }

    @Override
    protected void performCalculations() {
        g_ = new MappingFunction(this);
    }

    // --- inner class ---

    /**
     * The CLV mapping function built from linear time-interpolation of
     * Lagrange-interpolated collocation data.
     */
    private static class MappingFunction implements BiFunction<Double, Double, Double> {

        private final double[] y_;         // scratch buffer for OU abscissae
        private final double sigma_;
        private final OrnsteinUhlenbeckProcess ouProcess_;

        // s_[i][j] = collocationPointsY(maturityDates[j])[i]
        private final double[][] s_;
        private final double[]   x_;
        private final double[]   t_;
        private final LinearInterpolation[] interpl_;
        private final LagrangeInterpolation lagrangeInterpl_;

        MappingFunction(final NormalCLVModel model) {
            final int xLen = model.x_.length;
            final int tLen = model.maturityDates_.length;

            y_          = new double[xLen];
            sigma_      = model.sigma_;
            ouProcess_  = model.ouProcess_;
            x_          = model.x_.clone();
            t_          = model.maturityTimes_.clone();

            // Build collocation matrix s_[row=x_index][col=time_index]
            s_          = new double[xLen][tLen];
            for (int j = 0; j < tLen; ++j) {
                final double[] ys = model.collocationPointsY(model.maturityDates_[j]);
                for (int i = 0; i < xLen; ++i) {
                    s_[i][j] = ys[i];
                }
            }

            // Build one LinearInterpolation per x-node over time
            interpl_ = new LinearInterpolation[xLen];
            for (int i = 0; i < xLen; ++i) {
                interpl_[i] = new LinearInterpolation(new Array(t_), new Array(s_[i]));
                interpl_[i].enableExtrapolation();
            }

            // Lagrange interpolation placeholder (x-nodes only, y updated per call)
            lagrangeInterpl_ = new LagrangeInterpolation(x_);
        }

        @Override
        public Double apply(final Double t, final Double x) {
            // Interpolate each collocation value at time t
            for (int i = 0; i < y_.length; ++i) {
                y_[i] = interpl_[i].op(t, true);
            }

            // Map x (in OU space) to the normalised Hermite variable r
            final double mu = ouProcess_.expectation(0.0, ouProcess_.x0(), t);
            final double sd = ouProcess_.stdDeviation(0.0, ouProcess_.x0(), t);
            final double r  = sigma_ * (x - mu) / sd;

            return lagrangeInterpl_.value(y_, r);
        }
    }
}
