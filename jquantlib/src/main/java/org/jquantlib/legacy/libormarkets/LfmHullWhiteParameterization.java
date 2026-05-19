/*
 Copyright (C) 2005, 2006 Klaus Spanderen (C++ original).
 Copyright (C) 2026 JQuantLib migration contributors (Java port).

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
*/

package org.jquantlib.legacy.libormarkets;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.matrixutilities.PseudoSqrt;
import org.jquantlib.processes.LfmCovarianceParameterization;
import org.jquantlib.processes.LiborForwardModelProcess;
import org.jquantlib.termstructures.volatilities.optionlet.OptionletVolatilityStructure;
import org.jquantlib.time.Date;

import java.util.ArrayList;
import java.util.List;

/**
 * Libor market model parameterization based on Hull/White.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code legacy/libormarketmodels/lfmhullwhiteparam.{hpp,cpp}}.
 *
 * <p>References: Hull, John, White, Alan, 1999, <em>Forward Rate Volatilities,
 * Swap Rate Volatilities and the Implementation of the Libor Market Model</em>.
 */
public class LfmHullWhiteParameterization extends LfmCovarianceParameterization {

    /** rank-(size-1, factors) diffusion matrix produced by the lambda bootstrap. */
    private final Matrix diffusion_;
    /** size-1 x size-1 covariance = diffusion * diffusion^T (Hull-White lambda product). */
    private final Matrix covariance_;
    /** fixing-time vector mirrored from the underlying process. */
    private final List< Double > fixingTimes_;

    /**
     * Mirrors C++ {@code LfmHullWhiteParameterization(process, capletVol, correlation = Matrix(), factors = 1)}.
     */
    public LfmHullWhiteParameterization(final LiborForwardModelProcess process,
            final OptionletVolatilityStructure capletVol, final Matrix correlation, final int factors) {
        super(process.size(), factors);
        this.diffusion_ = new Matrix(size_ - 1, factors());
        this.fixingTimes_ = new ArrayList< Double >(process.fixingTimes());

        // sqrtCorr defaults to ones-matrix of shape (size-1, factors) when
        // correlation is empty (single-factor case).
        final Matrix sqrtCorr = new Matrix(size_ - 1, factors());
        for ( int i = 0; i < size_ - 1; ++i ) {
            for ( int j = 0; j < factors(); ++j ) {
                sqrtCorr.set(i, j, 1.0);
            }
        }
        if ( correlation == null || correlation.empty() ) {
            QL.require(factors() == 1, "correlation matrix must be given for multi factor models");
        } else {
            QL.require(correlation.rows() == size_ - 1 && correlation.rows() == correlation.columns(),
                    "wrong dimesion of the correlation matrix");
            QL.require(factors() <= size_ - 1, "too many factors for given LFM process");

            final Matrix tmpSqrtCorr = PseudoSqrt.pseudoSqrt(correlation, PseudoSqrt.SalvagingAlgorithm.Spectral);

            // reduce to n-factor model: normalise each row to unit norm,
            // mirror C++ "reconstructing a valid correlation matrix" trick
            // (lfmhullwhiteparam.cpp:52-59).
            for ( int i = 0; i < size_ - 1; ++i ) {
                double p = 0.0;
                for ( int q = 0; q < factors(); ++q ) {
                    p += tmpSqrtCorr.get(i, q) * tmpSqrtCorr.get(i, q);
                }
                p = Math.sqrt(p);
                for ( int q = 0; q < factors(); ++q ) {
                    sqrtCorr.set(i, q, tmpSqrtCorr.get(i, q) / p);
                }
            }
        }

        // Bootstrap the per-period instantaneous lambdas from the cap variance
        // strip (lfmhullwhiteparam.cpp:62-87). lambda[i-1] is the constant
        // sigma_i for the active step on the i-th rate.
        final List< Double > lambda = new ArrayList< Double >();
        final List< Double > fixingTimes = process.fixingTimes();
        final List< Date > fixingDates = process.fixingDates();

        for ( int i = 1; i < size_; ++i ) {
            double cumVar = 0.0;
            for ( int j = 1; j < i; ++j ) {
                final double lam = lambda.get(i - j - 1);
                cumVar += lam * lam * (fixingTimes.get(j + 1) - fixingTimes.get(j));
            }

            final double vol = capletVol.volatility(fixingDates.get(i), 0.0);
            final double var = vol * vol * capletVol.dayCounter().yearFraction(fixingDates.get(0), fixingDates.get(i));

            final double newLambda = Math.sqrt((var - cumVar) / (fixingTimes.get(1) - fixingTimes.get(0)));
            lambda.add(newLambda);

            for ( int q = 0; q < factors(); ++q ) {
                diffusion_.set(i - 1, q, sqrtCorr.get(i - 1, q) * newLambda);
            }
        }

        this.covariance_ = diffusion_.mul(diffusion_.transpose());
    }

    public LfmHullWhiteParameterization(final LiborForwardModelProcess process,
            final OptionletVolatilityStructure capletVol) {
        this(process, capletVol, null, 1);
    }

    /**
     * Mirror of C++ {@code Size nextIndexReset(Time)} — std::upper_bound on the fixing-time vector.
     */
    protected int nextIndexReset(final double t) {
        int lo = 0;
        int hi = fixingTimes_.size();
        while ( lo < hi ) {
            final int mid = (lo + hi) >>> 1;
            if ( t < fixingTimes_.get(mid) ) {
                hi = mid;
            } else {
                lo = mid + 1;
            }
        }
        return lo;
    }

    @Override
    public Matrix diffusion(final double t, final Array x) {
        // C++ lfmhullwhiteparam.cpp:96-106: copy the rolled bootstrap rows
        // starting at index m up to size_-1.
        final Matrix tmp = new Matrix(size_, factors());
        final int m = nextIndexReset(t);
        for ( int k = m; k < size_; ++k ) {
            for ( int q = 0; q < factors(); ++q ) {
                tmp.set(k, q, diffusion_.get(k - m, q));
            }
        }
        return tmp;
    }

    @Override
    public Matrix covariance(final double t, final Array x) {
        // C++ lfmhullwhiteparam.cpp:108-119.
        final Matrix tmp = new Matrix(size_, size_);
        final int m = nextIndexReset(t);
        for ( int k = m; k < size_; ++k ) {
            for ( int i = m; i < size_; ++i ) {
                tmp.set(k, i, covariance_.get(k - m, i - m));
            }
        }
        return tmp;
    }

    @Override
    public Matrix integratedCovariance(final double t, final Array x) {
        // C++ lfmhullwhiteparam.cpp:121-141: piecewise-constant block
        // accumulator over [fixingTimes_[i], fixingTimes_[i+1]) up to t.
        final Matrix tmp = new Matrix(size_, size_);

        // std::lower_bound equivalent.
        int last = 0;
        {
            int lo = 0;
            int hi = fixingTimes_.size();
            while ( lo < hi ) {
                final int mid = (lo + hi) >>> 1;
                if ( fixingTimes_.get(mid) < t ) {
                    lo = mid + 1;
                } else {
                    hi = mid;
                }
            }
            last = lo;
        }

        for ( int i = 0; i < last; ++i ) {
            final double dt = ((i + 1 < last) ? fixingTimes_.get(i + 1) : t) - fixingTimes_.get(i);

            for ( int k = i; k < size_ - 1; ++k ) {
                for ( int l = i; l < size_ - 1; ++l ) {
                    tmp.set(k + 1, l + 1, tmp.get(k + 1, l + 1) + covariance_.get(k - i, l - i) * dt);
                }
            }
        }

        return tmp;
    }
}
