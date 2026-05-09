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

import org.jquantlib.math.distributions.BinomialDistribution;

/**
 * Binomial loss distribution.
 *
 * <p>Java port of QuantLib v1.42.1 {@code QuantLib::LossDistBinomial}
 * ({@code ql/experimental/credit/lossdistribution.{hpp,cpp}}).
 *
 * <p>Phase 4m.6.
 */
public class LossDistBinomial extends LossDist {

    private final int nBuckets_;
    private final double maximum_;
    private double volume_;
    private int n_;
    private List<Double> probability_ = new ArrayList<>();
    private List<Double> excessProbability_ = new ArrayList<>();

    public LossDistBinomial(final int nBuckets, final double maximum) {
        this.nBuckets_ = nBuckets;
        this.maximum_ = maximum;
    }

    public Distribution op(final int n, final double volume, final double probability) {
        n_ = n;
        volume_ = volume;
        probability_ = new ArrayList<>(n + 1);
        for (int i = 0; i <= n; ++i) {
            probability_.add(0.0);
        }
        final Distribution dist = new Distribution(nBuckets_, 0.0, maximum_);
        final BinomialDistribution binomial = new BinomialDistribution(probability, n);
        for (int i = 0; i <= n; ++i) {
            if (volume * i <= maximum_) {
                probability_.set(i, binomial.op(i));
                final int bucket = dist.locate(volume * i);
                dist.addDensity(bucket, probability_.get(i) / dist.dx(bucket));
                dist.addAverage(bucket, volume * i);
            }
        }

        excessProbability_ = new ArrayList<>(n + 1);
        for (int i = 0; i <= n; ++i) {
            excessProbability_.add(0.0);
        }
        excessProbability_.set(n_, probability_.get(n_));
        for (int k = n_ - 1; k >= 0; --k) {
            excessProbability_.set(k, excessProbability_.get(k + 1) + probability_.get(k));
        }

        dist.normalize();
        return dist;
    }

    @Override
    public Distribution op(final List<Double> nominals, final List<Double> probabilities) {
        return op(nominals.size(), nominals.get(0), probabilities.get(0));
    }

    @Override
    public int buckets() {
        return nBuckets_;
    }

    @Override
    public double maximum() {
        return maximum_;
    }

    public double volume() {
        return volume_;
    }

    public int size() {
        return n_;
    }

    public List<Double> probability() {
        return Collections.unmodifiableList(probability_);
    }

    public List<Double> excessProbability() {
        return Collections.unmodifiableList(excessProbability_);
    }
}
