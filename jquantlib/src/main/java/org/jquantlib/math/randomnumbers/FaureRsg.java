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
 Copyright (C) 2004 Ferdinando Ametrano
 Copyright (C) 2004 Gianni Piolanti
*/

package org.jquantlib.math.randomnumbers;

import org.jquantlib.QL;
import org.jquantlib.math.PrimeNumbers;

/**
 * Faure low-discrepancy sequence generator.
 *
 * <p>Direct port of C++ v1.42.1 {@code ql/math/randomnumbers/faurersg.{hpp,cpp}}.
 * Based on existing Fortran and C algorithms to calculate the Pascal matrix
 * and gray transforms (E. Thiemard; Algorithms 659, 647).
 *
 * <p>The sample type is a {@code double[]} value vector plus a scalar weight,
 * mirroring the C++ {@code Sample<std::vector<Real>>} alias used in the
 * randomnumbers package. The integer sequence and normalization factor follow
 * the C++ formulation directly.
 */
public class FaureRsg {

    /** Weighted Faure sample (value vector + scalar weight). */
    public static final class Sample {
        public final double[] value;
        public double weight;

        public Sample(final int dim) {
            this.value = new double[dim];
            this.weight = 1.0;
        }
    }

    private final int dimensionality_;
    private final Sample sequence_;
    private final long[] integerSequence_;
    private final long[] bary_;
    private final long[][] gray_;
    private final int base_;
    private final int mbit_;
    private final long[][] powBase_;
    private final int[] addOne_;
    private final long[][][] pascal3D;
    private final double normalizationFactor_;

    public FaureRsg(final int dimensionality) {
        QL.require(dimensionality > 0, "dimensionality must be greater than 0");
        this.dimensionality_ = dimensionality;
        this.sequence_ = new Sample(dimensionality);
        this.integerSequence_ = new long[dimensionality];

        // base is the lowest prime number >= dimensionality_
        final PrimeNumbers primes = new PrimeNumbers();
        int b = 2;
        int k = 1;
        while (b < dimensionality) {
            b = (int) primes.get(k);
            k++;
        }
        this.base_ = b;

        // mbit_ = floor(log(LONG_MAX) / log(base_)). Matches the C++ formulation
        // which uses long int — on the 64-bit Linux/Mac builds QuantLib ships
        // with, std::numeric_limits<long int>::max() is 2^63-1 (same as Java's
        // Long.MAX_VALUE), so this expression matches.
        this.mbit_ = (int) (Math.log((double) Long.MAX_VALUE) / Math.log((double) base_));

        this.gray_ = new long[dimensionality][mbit_ + 1];
        this.bary_ = new long[mbit_ + 1];

        // setMatrixValues()
        this.powBase_ = new long[mbit_][2 * base_ - 1];
        powBase_[mbit_ - 1][base_] = 1;
        for (int i2 = mbit_ - 2; i2 >= 0; --i2) {
            powBase_[i2][base_] = powBase_[i2 + 1][base_] * base_;
        }
        for (int ii = 0; ii < mbit_; ii++) {
            for (int j1 = base_ + 1; j1 < 2 * base_ - 1; j1++) {
                powBase_[ii][j1] = powBase_[ii][j1 - 1] + powBase_[ii][base_];
            }
            for (int j2 = base_ - 1; j2 >= 0; --j2) {
                powBase_[ii][j2] = powBase_[ii][j2 + 1] - powBase_[ii][base_];
            }
        }

        this.addOne_ = new int[base_];
        for (int j = 0; j < base_; j++) {
            addOne_[j] = (j + 1) % base_;
        }

        // setPascalMatrix(): pascal3D is a jagged 3-D array.
        // First dim is mbit_, second dim is dimensionality_+1, third dim is
        // (i+1) for the i-th outer index. Mirrors C++ push_back loop.
        this.pascal3D = new long[mbit_][dimensionality + 1][];
        for (int kk = 0; kk < mbit_; kk++) {
            for (int row = 0; row < dimensionality + 1; row++) {
                pascal3D[kk][row] = new long[kk + 1];
            }
            pascal3D[kk][0][kk] = 1;
            pascal3D[kk][1][0] = 1;
            pascal3D[kk][1][kk] = 1;
        }

        long p1, p2;
        for (int kk = 2; kk < mbit_; kk++) {
            for (int i = 1; i < kk; i++) {
                p1 = pascal3D[kk - 1][1][i - 1];
                p2 = pascal3D[kk - 1][1][i];
                pascal3D[kk][1][i] = (p1 + p2) % base_;
            }
        }

        long fact = 1;
        long diag;
        for (int j = 2; j < dimensionality; j++) {
            for (long kk2 = mbit_ - 1; kk2 >= 0; --kk2) {
                diag = mbit_ - kk2 - 1;
                if (diag == 0) {
                    fact = 1;
                } else {
                    fact = (fact * j) % base_;
                }
                for (long ii = 0; ii <= kk2; ii++) {
                    pascal3D[(int) (diag + ii)][j][(int) ii] = (fact *
                            pascal3D[(int) (diag + ii)][1][(int) ii]) % base_;
                }
            }
        }

        this.normalizationFactor_ = (double) base_ * (double) powBase_[0][base_];
    }

    private void generateNextIntSequence() {
        int bit = 0;
        bary_[bit] = addOne_[(int) bary_[bit]];
        while (bary_[bit] == 0) {
            bit++;
            bary_[bit] = addOne_[(int) bary_[bit]];
        }
        QL.require(bit != mbit_, "Error processing Faure sequence.");

        long tmp, g1, g2;
        for (int i = 0; i < dimensionality_; i++) {
            for (int j = 0; j <= bit; j++) {
                tmp = gray_[i][j];
                gray_[i][j] = (pascal3D[bit][i][j] + tmp) % base_;
                g1 = gray_[i][j];
                g2 = base_ - 1 + g1 - tmp;
                integerSequence_[i] += powBase_[j][(int) g2];
            }
        }
    }

    public long[] nextIntSequence() {
        generateNextIntSequence();
        return integerSequence_;
    }

    public long[] lastIntSequence() {
        return integerSequence_;
    }

    public Sample nextSequence() {
        generateNextIntSequence();
        for (int i = 0; i < dimensionality_; i++) {
            sequence_.value[i] = integerSequence_[i] / normalizationFactor_;
        }
        return sequence_;
    }

    public Sample lastSequence() {
        return sequence_;
    }

    public int dimension() {
        return dimensionality_;
    }
}
