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
 Copyright (C) 2007 Mark Joshi
*/

package org.jquantlib.math.randomnumbers;

/**
 * Rank-1 lattice rule sequence generator (low-discrepancy).
 *
 * <p>Direct port of C++ v1.42.1 {@code ql/math/randomnumbers/latticersg.{hpp,cpp}}.
 * Generates the {@code i}-th lattice point as
 * {@code theta_j = (i * z_j) / N mod 1} for {@code j = 0..dimensionality-1}.
 */
public class LatticeRsg {

    /** Weighted lattice sample (value vector + scalar weight). */
    public static final class Sample {
        public final double[] value;
        public double weight;

        public Sample(final int dim) {
            this.value = new double[dim];
            this.weight = 1.0;
        }
    }

    private final int dimensionality_;
    private final int n_;
    private long i_ = 0;
    private final double[] z_;
    private final Sample sequence_;

    public LatticeRsg(final int dimensionality, final double[] z, final int n) {
        this.dimensionality_ = dimensionality;
        this.n_ = n;
        this.z_ = z; // C++ moves the vector in; we share the reference (read-only access).
        this.sequence_ = new Sample(dimensionality);
    }

    /** Skip to the n-th sample in the low-discrepancy sequence. */
    public void skipTo(final long n) {
        i_ += n;
    }

    public Sample nextSequence() {
        for (int j = 0; j < dimensionality_; ++j) {
            final double theta = (double) i_ * z_[j] / (double) n_;
            // C++ uses std::fmod(theta, 1.0); for theta >= 0 (always the case
            // here since i_, z_ and N_ are non-negative) this is equivalent to
            // theta - floor(theta).
            sequence_.value[j] = theta - Math.floor(theta);
        }
        ++i_;
        return sequence_;
    }

    public int dimension() {
        return dimensionality_;
    }

    public Sample lastSequence() {
        return sequence_;
    }
}
