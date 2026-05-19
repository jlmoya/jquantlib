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
 Copyright (C) 2010 Liquidnet Holdings, Inc.
*/
package org.jquantlib.math;

import org.jquantlib.QL;

/**
 * Autocovariance, autocorrelation, and convolution utilities.
 * <p>
 * Java port of QuantLib v1.42.1 {@code ql/math/autocovariance.hpp}.
 * <p>
 * The C++ original computes these statistics via the FFT (using the Wiener-Khinchin theorem: autocorrelation is the
 * inverse FFT of the power-spectrum, i.e. the magnitude-squared FFT of the signal). Because JQuantLib has no
 * {@code FastFourierTransform} class, this Java port computes the convolutions directly. The numerical results are
 * mathematically identical (both compute the cross-correlation sums x[0]*x[n] + x[1]*x[n+1] + ...); only the per-call
 * complexity differs (direct: {@code O(N * (maxLag+1))} vs FFT: {@code O(N log N)}).
 *
 * @author Phase 5e.5b-CFC-d-77 carry-forward
 */
public final class Autocovariance {

    private Autocovariance() {
        // utility class
    }

    /**
     * Convolutions of the input sequence.
     * <p>
     * Returns a length-{@code maxLag+1} array where {@code conv[n] = sum_{i=0}^{N-1-n} x[i] * x[i+n]} for
     * {@code n = 0, 1, ..., maxLag}.
     *
     * @param x      input sequence
     * @param maxLag largest lag (must be {@code < x.length})
     * @return convolutions of {@code x} for lags 0..maxLag
     */
    public static double[] convolutions(final double[] x, final int maxLag) {
        final int nData = x.length;
        QL.require(maxLag < nData, "maxLag must be less than data size");
        final double[] out = new double[maxLag + 1];
        for ( int n = 0; n <= maxLag; ++n ) {
            double s = 0.0;
            for ( int i = 0; i + n < nData; ++i ) {
                s += x[i] * x[i + n];
            }
            out[n] = s;
        }
        return out;
    }

    /**
     * Unbiased auto-covariances of a centered (zero-mean) sequence.
     * <p>
     * Returns a length-{@code maxLag+1} array where the {@code n}-th entry equals
     * {@code convolutions[n] / (nData - n)}.
     *
     * @param x      input sequence (assumed already centered)
     * @param maxLag largest lag (must be {@code < x.length})
     * @return unbiased auto-covariances of {@code x} for lags 0..maxLag
     */
    public static double[] autocovariances(final double[] x, final int maxLag) {
        final int nData = x.length;
        QL.require(maxLag < nData, "number of covariances must be less than data size");
        final double[] conv = convolutions(x, maxLag);
        final double[] out = new double[maxLag + 1];
        for ( int n = 0; n <= maxLag; ++n ) {
            out[n] = conv[n] / (nData - n);
        }
        return out;
    }

    /**
     * Unbiased auto-covariances of a non-centered sequence.
     * <p>
     * Removes the mean from the input data, then computes the unbiased auto-covariances of the centered sequence. The
     * mean is returned via the length-one {@code meanOut} array (index 0). If {@code reuse} is true, the centered
     * sequence is written back into the input array ({@code x[i] -= mean}); otherwise {@code x} is left untouched.
     *
     * @param x       input sequence (modified in place if {@code reuse})
     * @param maxLag  largest lag
     * @param reuse   whether to overwrite {@code x} with the centered values
     * @param meanOut length-one array to receive the sample mean
     * @return unbiased auto-covariances for lags 0..maxLag
     */
    public static double[] autocovariances(final double[] x, final int maxLag, final boolean reuse,
            final double[] meanOut) {
        final double[] centered = reuse ? x : new double[x.length];
        final double mean = removeMean(x, centered);
        meanOut[0] = mean;
        return autocovariances(centered, maxLag);
    }

    /**
     * Unbiased auto-correlations of a centered (zero-mean) sequence.
     * <p>
     * The first element of the output is the unbiased sample variance, {@code conv[0] / (nData - 1)}. Subsequent
     * elements are {@code conv[n] / (variance * (nData - n))} for {@code n = 1..maxLag}, where
     * {@code variance = conv[0] / nData}. This matches the C++ convention exactly.
     *
     * @param x      input sequence (assumed already centered)
     * @param maxLag largest lag
     * @return unbiased auto-correlations (with variance at index 0)
     */
    public static double[] autocorrelations(final double[] x, final int maxLag) {
        final int nData = x.length;
        QL.require(maxLag < nData, "number of correlations must be less than data size");
        final double[] conv = convolutions(x, maxLag);
        final double[] out = new double[maxLag + 1];
        // C++:
        //   Real variance = ft[0].real() * w1 / w2;   // w1 = 1/ftSize, w2 = nData
        //                                              // ft[0].real() * w1 == conv[0]
        //   *out++ = variance * w2 / (w2-1.0);        // unbiased sample variance
        //   w2 -= 1.0;
        //   for (k = 1..maxLag, w2 -= 1.0)
        //     *out++ = ft[k].real() * w1 / (variance * w2);
        // Note: after the first emission, w2 is decremented from nData to nData-1,
        // so for k=1 the divisor uses w2 = nData-1 (i.e. nData - k).
        final double variance = conv[0] / nData;
        out[0] = variance * nData / (nData - 1.0);
        for ( int n = 1; n <= maxLag; ++n ) {
            out[n] = conv[n] / (variance * (nData - n));
        }
        return out;
    }

    /**
     * Unbiased auto-correlations of a non-centered sequence.
     * <p>
     * Removes the mean from the input data, then computes the unbiased auto-correlations of the centered sequence. See
     * {@link #autocovariances(double[], int, boolean, double[])} for the meaning of {@code reuse} and {@code meanOut}.
     */
    public static double[] autocorrelations(final double[] x, final int maxLag, final boolean reuse,
            final double[] meanOut) {
        final double[] centered = reuse ? x : new double[x.length];
        final double mean = removeMean(x, centered);
        meanOut[0] = mean;
        return autocorrelations(centered, maxLag);
    }

    /**
     * Calculates and subtracts the mean from the input data into {@code out}. Returns the mean.
     * <p>
     * Matches the C++ {@code detail::remove_mean} running-average formula so as to preserve any rounding differences
     * vs. the naive {@code sum / n} approach.
     */
    private static double removeMean(final double[] x, final double[] out) {
        double mean = 0.0;
        int n = 1;
        for ( int i = 0; i < x.length; ++i, ++n ) {
            mean = (mean * (n - 1) + x[i]) / n;
        }
        for ( int i = 0; i < x.length; ++i ) {
            out[i] = x[i] - mean;
        }
        return mean;
    }
}
