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
import org.jquantlib.math.distributions.InverseNonCentralCumulativeChiSquaredDistribution;
import org.jquantlib.math.distributions.NonCentralCumulativeChiSquaredDistribution;
import org.jquantlib.math.integrals.GaussianQuadrature;
import org.jquantlib.math.interpolations.LagrangeInterpolation;
import org.jquantlib.methods.finitedifferences.utilities.GBSMRNDCalculator;
import org.jquantlib.experimental.math.GaussNonCentralChiSquaredPolynomial;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.processes.SquareRootProcess;
import org.jquantlib.time.Date;
import org.jquantlib.util.LazyObject;

import java.util.Arrays;
import java.util.TreeMap;
import java.util.function.BiFunction;

/**
 * CLV (Collocation Local Volatility) model with a square-root (CIR-type)
 * kernel process.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/experimental/models/squarerootclvmodel.{hpp,cpp}}.
 *
 * <p>References: A. Grzelak, 2016, "The CLV Framework – A Fresh Look at
 * Efficient Pricing with Smile," SSRN 2747541.
 *
 * @author Phase 4j port
 */
public class SquareRootCLVModel extends LazyObject {

    private final double pMax_;
    private final double pMin_;
    private final GeneralizedBlackScholesProcess bsProcess_;
    private final SquareRootProcess sqrtProcess_;
    private final Date[] maturityDates_;
    private final int lagrangeOrder_;
    private final GBSMRNDCalculator rndCalculator_;

    // populated in performCalculations
    private volatile BiFunction<Double, Double, Double> g_;

    /**
     * @param bsProcess     Generalized Black-Scholes process
     * @param sqrtProcess   CIR-type square root kernel process
     * @param maturityDates collocation dates (need not be pre-sorted)
     * @param lagrangeOrder number of non-central chi-squared quadrature points
     * @param pMax          upper quantile for collocation node clipping
     *                      (pass {@link Double#NaN} or {@link Constants#QL_NULL_REAL} for none)
     * @param pMin          lower quantile (pass {@link Double#NaN} for none)
     */
    public SquareRootCLVModel(final GeneralizedBlackScholesProcess bsProcess,
                              final SquareRootProcess sqrtProcess,
                              final Date[] maturityDates,
                              final int lagrangeOrder,
                              final double pMax,
                              final double pMin) {
        this.pMax_         = pMax;
        this.pMin_         = pMin;
        this.bsProcess_    = bsProcess;
        this.sqrtProcess_  = sqrtProcess;
        this.maturityDates_ = maturityDates.clone();
        this.lagrangeOrder_ = lagrangeOrder;
        this.rndCalculator_ = new GBSMRNDCalculator(bsProcess);

        bsProcess_.addObserver(this);
        sqrtProcess_.addObserver(this);
    }

    /** Construct with no quantile clipping. */
    public SquareRootCLVModel(final GeneralizedBlackScholesProcess bsProcess,
                              final SquareRootProcess sqrtProcess,
                              final Date[] maturityDates,
                              final int lagrangeOrder) {
        this(bsProcess, sqrtProcess, maturityDates, lagrangeOrder,
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
     * Collocation points of the square-root process at date {@code d}
     * (in chi-squared space).
     */
    public double[] collocationPointsX(final Date d) {
        final double[] p = nonCentralChiSquaredParams(d);
        final double df  = p[0];
        final double ncp = p[1];

        // Gauss quadrature over nc-chi2 distribution
        final GaussianQuadrature gq = new GaussianQuadrature(
                lagrangeOrder_, new GaussNonCentralChiSquaredPolynomial(df, ncp));

        final double[] x = new double[lagrangeOrder_];
        for (int i = 0; i < lagrangeOrder_; ++i) {
            x[i] = gq.x(i);
        }
        Arrays.sort(x);

        // Compute quantile bounds
        final NonCentralCumulativeChiSquaredDistribution dist =
                new NonCentralCumulativeChiSquaredDistribution(df, ncp);
        final InverseNonCentralCumulativeChiSquaredDistribution invDist =
                new InverseNonCentralCumulativeChiSquaredDistribution(df, ncp, 100, 1e-8);

        final double xMin = Math.max(x[0],
                isNull(pMin_) ? 0.0 : invDist.op(pMin_));
        final double xMax = Math.min(x[lagrangeOrder_ - 1],
                isNull(pMax_) ? Double.MAX_VALUE : invDist.op(pMax_));

        // Affine map x_i -> [xMin, xMax]
        final double b = xMin - x[0];
        final double a = (xMax - xMin) / (x[lagrangeOrder_ - 1] - x[0]);

        for (int i = 0; i < lagrangeOrder_; ++i) {
            x[i] = a * x[i] + b;
        }
        return x;
    }

    /**
     * Collocation points for the underlying S_T at date {@code d}
     * (in BS-space, mapped via the invCDF of the chi-squared distribution).
     */
    public double[] collocationPointsY(final Date d) {
        final double[] xPts = collocationPointsX(d);
        final double[] p    = nonCentralChiSquaredParams(d);
        final NonCentralCumulativeChiSquaredDistribution dist =
                new NonCentralCumulativeChiSquaredDistribution(p[0], p[1]);

        final double[] s = new double[xPts.length];
        for (int i = 0; i < s.length; ++i) {
            final double q = dist.op(xPts[i]);
            s[i] = invCDF(d, q);
        }
        return s;
    }

    /**
     * Returns the CLV mapping function {@code g(t, x)}.
     */
    public BiFunction<Double, Double, Double> g() {
        calculate();
        return g_;
    }

    @Override
    protected void performCalculations() {
        g_ = new MappingFunction(this);
    }

    // --- helpers ---

    /**
     * Returns [df, ncp] for the non-central chi-squared distribution of
     * the square-root process at date {@code d}.
     */
    private double[] nonCentralChiSquaredParams(final Date d) {
        final double t     = bsProcess_.time(d);
        final double kappa = sqrtProcess_.a();
        final double theta = sqrtProcess_.b();
        final double sigma = sqrtProcess_.sigma();

        final double df  = 4.0 * theta * kappa / (sigma * sigma);
        final double ncp = 4.0 * kappa * Math.exp(-kappa * t)
                / (sigma * sigma * (1.0 - Math.exp(-kappa * t))) * sqrtProcess_.x0();

        return new double[]{df, ncp};
    }

    private static boolean isNull(final double v) {
        return Double.isNaN(v) || v == Constants.NULL_REAL;
    }

    // --- inner class ---

    /**
     * The CLV mapping function built from Lagrange interpolation over sorted
     * maturity dates with linear time interpolation between adjacent dates.
     */
    private static class MappingFunction implements BiFunction<Double, Double, Double> {

        // Per-maturity Lagrange interpolations: maturityTime -> interpolation
        private final TreeMap<Double, LagrangeInterpolation> interpl_ = new TreeMap<>();
        // store x/s arrays to keep alive for LagrangeInterpolation (which holds references)
        private final double[][] xData_;
        private final double[][] sData_;

        MappingFunction(final SquareRootCLVModel model) {
            final Date[] maturities = model.maturityDates_.clone();
            Arrays.sort(maturities);

            final int nMat = maturities.length;
            final int nPts = model.lagrangeOrder_;
            xData_ = new double[nMat][nPts];
            sData_ = new double[nMat][nPts];

            for (int i = 0; i < nMat; ++i) {
                final double[] xPts = model.collocationPointsX(maturities[i]);
                final double[] yPts = model.collocationPointsY(maturities[i]);
                System.arraycopy(xPts, 0, xData_[i], 0, nPts);
                System.arraycopy(yPts, 0, sData_[i], 0, nPts);

                final double maturity = model.bsProcess_.time(maturities[i]);
                final LagrangeInterpolation li = new LagrangeInterpolation(xData_[i], sData_[i]);
                interpl_.put(maturity, li);
            }
        }

        @Override
        public Double apply(final Double t, final Double x) {
            // Find ge = first entry with key >= t
            final java.util.Map.Entry<Double, LagrangeInterpolation> ge =
                    interpl_.ceilingEntry(t);

            QL.require(ge != null,
                    "SquareRootCLVModel: extrapolation for t=" + t + " beyond last maturity");

            // Close to a node
            if (Math.abs(ge.getKey() - t) < 1e-14) {
                return ge.getValue().op(x, true);
            }

            final java.util.Map.Entry<Double, LagrangeInterpolation> lt =
                    interpl_.lowerEntry(t);

            QL.require(lt != null,
                    "SquareRootCLVModel: extrapolation before first maturity t=" + t);

            final double t1 = ge.getKey();
            final double y1 = ge.getValue().op(x, true);
            final double t0 = lt.getKey();
            final double y0 = lt.getValue().op(x, true);

            return y0 + (y1 - y0) / (t1 - t0) * (t - t0);
        }
    }
}
