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
 Copyright (C) 2006 Joseph Wang
 Copyright (C) 2009 Liquidnet Holdings, Inc.

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
*/

package org.jquantlib.math;

import org.jquantlib.QL;

/**
 * Fast Fourier Transform (radix-2, in-place, Cooley–Tukey).
 *
 * <p>Java port of QuantLib v1.42.1 {@code ql/math/fastfouriertransform.hpp}.
 * Based on public-domain code by Christopher Diggins.
 *
 * <p>The transform operates on {@code 2^order} complex samples. The input
 * length must not exceed that size; the output buffer must be at least that
 * size and is populated in bit-reversed natural order by the implementation.
 *
 * <p>Convention follows the C++ class: {@link #transform} computes the
 * forward DFT (negative-frequency twiddle); {@link #inverseTransform}
 * computes the unnormalized inverse DFT (positive-frequency twiddle).
 * Neither variant divides by {@code N}; callers wanting the orthonormal
 * inverse must do that themselves.
 */
public final class FastFourierTransform {

    private final double[] cs_;
    private final double[] sn_;

    /**
     * Construct an FFT plan for {@code 2^order} samples.
     *
     * @param order log<sub>2</sub> of the transform length (must be &ge; 1).
     */
    public FastFourierTransform(final int order) {
        QL.require(order >= 1, "FFT order must be at least 1");
        this.cs_ = new double[order];
        this.sn_ = new double[order];
        final long m = 1L << order;
        cs_[order - 1] = Math.cos(2.0 * Math.PI / m);
        sn_[order - 1] = Math.sin(2.0 * Math.PI / m);
        for (int i = order - 1; i > 0; --i) {
            cs_[i - 1] = cs_[i] * cs_[i] - sn_[i] * sn_[i];
            sn_[i - 1] = 2.0 * sn_[i] * cs_[i];
        }
    }

    /**
     * Minimum order required so that {@code 2^order >= inputSize}.
     */
    public static int minOrder(final int inputSize) {
        QL.require(inputSize > 0, "input size must be positive");
        return (int) Math.ceil(Math.log(inputSize) / Math.log(2.0));
    }

    /** Output buffer size: {@code 2^order}. */
    public int outputSize() {
        return 1 << cs_.length;
    }

    /**
     * Forward FFT. {@code out} must have length at least {@link #outputSize()};
     * {@code in} length must be &le; {@link #outputSize()}. Real-valued inputs
     * are accepted via the convention {@code Im(in[k]) = 0} — pass a separate
     * imaginary array of zeros, or use {@link #transformReal(double[])}.
     *
     * @param inRe real parts of input samples (length n &le; 2^order)
     * @param inIm imaginary parts of input samples (length n)
     * @param outRe real parts of output (length &ge; 2^order)
     * @param outIm imaginary parts of output (length &ge; 2^order)
     */
    public void transform(final double[] inRe, final double[] inIm,
                          final double[] outRe, final double[] outIm) {
        transformImpl(inRe, inIm, outRe, outIm, false);
    }

    /**
     * Inverse FFT (unnormalized). Same buffer requirements as
     * {@link #transform}.
     */
    public void inverseTransform(final double[] inRe, final double[] inIm,
                                 final double[] outRe, final double[] outIm) {
        transformImpl(inRe, inIm, outRe, outIm, true);
    }

    /**
     * Convenience: forward FFT of a real-valued sequence. Allocates the input
     * imaginary buffer internally.
     *
     * @param in real input (length n &le; 2^order)
     * @param outRe real-part output (length &ge; 2^order)
     * @param outIm imaginary-part output (length &ge; 2^order)
     */
    public void transformReal(final double[] in,
                              final double[] outRe, final double[] outIm) {
        final double[] inIm = new double[in.length];
        transformImpl(in, inIm, outRe, outIm, false);
    }

    /**
     * Convenience: inverse FFT of a real-valued sequence.
     */
    public void inverseTransformReal(final double[] in,
                                     final double[] outRe, final double[] outIm) {
        final double[] inIm = new double[in.length];
        transformImpl(in, inIm, outRe, outIm, true);
    }

    private void transformImpl(final double[] inRe, final double[] inIm,
                               final double[] outRe, final double[] outIm,
                               final boolean inverse) {
        final int order = cs_.length;
        final int N = 1 << order;
        QL.require(inRe.length == inIm.length, "in real/imag length mismatch");
        QL.require(outRe.length >= N && outIm.length >= N, "FFT output buffer too small");
        QL.require(inRe.length <= N, "FFT order is too small");

        // Zero output, then scatter input into bit-reversed positions.
        for (int i = 0; i < N; ++i) {
            outRe[i] = 0.0;
            outIm[i] = 0.0;
        }
        final int n = inRe.length;
        for (int i = 0; i < n; ++i) {
            final int br = bitReverse(i, order);
            outRe[br] = inRe[i];
            outIm[br] = inIm[i];
        }

        // Butterflies. wm = cos(s-1) + i * (inverse ? sn[s-1] : -sn[s-1]).
        for (int s = 1; s <= order; ++s) {
            final int m = 1 << s;
            final int half = m >> 1;
            double wRe = 1.0;
            double wIm = 0.0;
            final double wmRe = cs_[s - 1];
            final double wmIm = inverse ? sn_[s - 1] : -sn_[s - 1];
            for (int j = 0; j < half; ++j) {
                for (int k = j; k < N; k += m) {
                    // t = w * out[k + half]
                    final double xRe = outRe[k + half];
                    final double xIm = outIm[k + half];
                    final double tRe = wRe * xRe - wIm * xIm;
                    final double tIm = wRe * xIm + wIm * xRe;
                    final double uRe = outRe[k];
                    final double uIm = outIm[k];
                    outRe[k] = uRe + tRe;
                    outIm[k] = uIm + tIm;
                    outRe[k + half] = uRe - tRe;
                    outIm[k + half] = uIm - tIm;
                }
                // w *= wm
                final double nwRe = wRe * wmRe - wIm * wmIm;
                final double nwIm = wRe * wmIm + wIm * wmRe;
                wRe = nwRe;
                wIm = nwIm;
            }
        }
    }

    private static int bitReverse(int x, final int order) {
        int n = 0;
        for (int i = 0; i < order; ++i) {
            n <<= 1;
            n |= (x & 1);
            x >>>= 1;
        }
        return n;
    }
}
