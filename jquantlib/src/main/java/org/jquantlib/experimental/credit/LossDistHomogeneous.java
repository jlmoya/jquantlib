/*
 Copyright (C) 2008 Roland Lichters
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

package org.jquantlib.experimental.credit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Loss distribution for a homogeneous pool of underlyings (equal volumes, varying default probabilities).
 *
 * <p>Java port of QuantLib v1.42.1 {@code QuantLib::LossDistHomogeneous}
 * ({@code ql/experimental/credit/lossdistribution.{hpp,cpp}}).
 *
 * <p>References:
 * <ul>
 *   <li>Xiaofong Ma, "Numerical Methods for the Valuation of Synthetic
 *       Collateralized Debt Obligations", PhD Thesis, U. Toronto 2007
 *       (formula 2.1).</li>
 *   <li>Hull-White, "Valuation of a CDO and nth to default CDS without
 *       Monte Carlo simulation", Journal of Derivatives 12, 2, 2004.</li>
 * </ul>
 *
 * <p>Phase 4m.6.
 */
public class LossDistHomogeneous extends LossDist {

    private final int nBuckets_;
    private final double maximum_;
    private int n_ = 0;
    private double volume_ = 0.0;
    private List< Double > probability_ = new ArrayList<>();
    private List< Double > excessProbability_ = new ArrayList<>();

    public LossDistHomogeneous(final int nBuckets, final double maximum) {
        this.nBuckets_ = nBuckets;
        this.maximum_ = maximum;
    }

    public Distribution op(final double volume, final List< Double > p) {
        volume_ = volume;
        n_ = p.size();
        probability_ = new ArrayList<>(n_ + 1);
        for ( int i = 0; i <= n_; ++i ) {
            probability_.add(0.0);
        }
        probability_.set(0, 1.0);
        for ( int k = 0; k < n_; ++k ) {
            final List< Double > prev = new ArrayList<>(probability_);
            probability_.set(0, prev.get(0) * (1.0 - p.get(k)));
            for ( int i = 1; i <= k; ++i ) {
                probability_.set(i, prev.get(i - 1) * p.get(k) + prev.get(i) * (1.0 - p.get(k)));
            }
            probability_.set(k + 1, prev.get(k) * p.get(k));
        }

        excessProbability_ = new ArrayList<>(n_ + 1);
        for ( int i = 0; i <= n_; ++i ) {
            excessProbability_.add(0.0);
        }
        excessProbability_.set(n_, probability_.get(n_));
        for ( int k = n_ - 1; k >= 0; --k ) {
            excessProbability_.set(k, excessProbability_.get(k + 1) + probability_.get(k));
        }

        final Distribution dist = new Distribution(nBuckets_, 0.0, maximum_);
        for ( int i = 0; i <= n_; ++i ) {
            if ( volume * i <= maximum_ ) {
                final int bucket = dist.locate(volume * i);
                dist.addDensity(bucket, probability_.get(i) / dist.dx(bucket));
                dist.addAverage(bucket, volume * i);
            }
        }

        dist.normalize();
        return dist;
    }

    @Override
    public Distribution op(final List< Double > nominals, final List< Double > probabilities) {
        return op(nominals.get(0), probabilities);
    }

    @Override
    public int buckets() {
        return nBuckets_;
    }

    @Override
    public double maximum() {
        return maximum_;
    }

    public int size() {
        return n_;
    }

    public double volume() {
        return volume_;
    }

    public List< Double > probability() {
        return Collections.unmodifiableList(probability_);
    }

    public List< Double > excessProbability() {
        return Collections.unmodifiableList(excessProbability_);
    }
}
