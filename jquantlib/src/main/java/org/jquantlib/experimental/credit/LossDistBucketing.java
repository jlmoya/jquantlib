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

import org.jquantlib.QL;

import java.util.ArrayList;
import java.util.List;

/**
 * Loss distribution with Hull-White bucketing for varying volumes and probabilities of default; independence assumed.
 *
 * <p>Java port of QuantLib v1.42.1 {@code QuantLib::LossDistBucketing}
 * ({@code ql/experimental/credit/lossdistribution.{hpp,cpp}}).
 *
 * <p>Reference: Hull-White, "Valuation of a CDO and nth to default CDS
 * without Monte Carlo simulation", Journal of Derivatives 12, 2, 2004.
 *
 * <p>Phase 4m.6.
 */
public class LossDistBucketing extends LossDist {

    private final int nBuckets_;
    private final double maximum_;
    private final double epsilon_;

    public LossDistBucketing(final int nBuckets, final double maximum) {
        this(nBuckets, maximum, 1.0e-6);
    }

    public LossDistBucketing(final int nBuckets, final double maximum, final double epsilon) {
        this.nBuckets_ = nBuckets;
        this.maximum_ = maximum;
        this.epsilon_ = epsilon;
    }

    @Override
    public Distribution op(final List< Double > nominals, final List< Double > probabilities) {
        QL.require(nominals.size() == probabilities.size(),
                "sizes differ: " + nominals.size() + " vs " + probabilities.size());

        final List< Double > p = new ArrayList<>(nBuckets_);
        final List< Double > a = new ArrayList<>(nBuckets_);
        for ( int i = 0; i < nBuckets_; ++i ) {
            p.add(0.0);
            a.add(0.0);
        }

        p.set(0, 1.0);
        a.set(0, 0.0);
        final double dx = maximum_ / nBuckets_;
        for ( int k = 1; k < nBuckets_; ++k ) {
            a.set(k, dx * k + dx / 2);
        }

        for ( int i = 0; i < nominals.size(); ++i ) {
            final double L = nominals.get(i);
            final double P = probabilities.get(i);
            for ( int k = a.size() - 1; k >= 0; --k ) {
                if ( p.get(k) > 0 ) {
                    final int u = locateTargetBucket(a.get(k) + L, k);
                    QL.require(u >= 0, "u=" + u + " at i=" + i + " k=" + k);
                    QL.require(u >= k, "u=" + u + "<k=" + k + " at i=" + i);

                    final double dp = p.get(k) * P;
                    if ( u == k ) {
                        a.set(k, a.get(k) + P * L);
                    } else {
                        // no update of a[u] and p[u] if u is beyond grid end
                        if ( u < nBuckets_ ) {
                            // a[u] remains unchanged if dp = 0
                            if ( dp > 0.0 ) {
                                final double f = 1.0 / (1.0 + (p.get(u) / p.get(k)) / P);
                                a.set(u, (1.0 - f) * a.get(u) + f * (a.get(k) + L));
                            }
                            p.set(u, p.get(u) + dp);
                        }
                        p.set(k, p.get(k) - dp);
                    }
                }
                QL.require(a.get(k) + epsilon_ >= dx * k && a.get(k) < dx * (k + 1),
                        "a out of range at k=" + k + ", contract " + i);
            }
        }

        final Distribution dist = new Distribution(nBuckets_, 0.0, maximum_);
        for ( int i = 0; i < nBuckets_; ++i ) {
            dist.addDensity(i, p.get(i) / dx);
            dist.addAverage(i, a.get(i));
        }
        return dist;
    }

    @Override
    public int buckets() {
        return nBuckets_;
    }

    @Override
    public double maximum() {
        return maximum_;
    }

    private int locateTargetBucket(final double loss, final int i0) {
        QL.require(loss >= 0, "loss " + loss + " must be >= 0");
        final double dx = maximum_ / nBuckets_;
        for ( int i = i0; i < nBuckets_; ++i ) {
            if ( dx * i > loss + epsilon_ ) {
                return i - 1;
            }
        }
        return nBuckets_;
    }
}
