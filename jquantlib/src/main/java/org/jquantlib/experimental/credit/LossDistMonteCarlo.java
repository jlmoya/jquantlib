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

import org.jquantlib.math.randomnumbers.MersenneTwisterUniformRng;

import java.util.List;

/**
 * Loss distribution via Monte Carlo simulation of independent default events.
 *
 * <p>Java port of QuantLib v1.42.1 {@code QuantLib::LossDistMonteCarlo}
 * ({@code ql/experimental/credit/lossdistribution.{hpp,cpp}}).
 *
 * <p>Phase 4m.6.
 */
public class LossDistMonteCarlo extends LossDist {

    private final int nBuckets_;
    private final double maximum_;
    private final int simulations_;
    private final long seed_;
    private final double epsilon_;

    public LossDistMonteCarlo(final int nBuckets, final double maximum, final int simulations) {
        this(nBuckets, maximum, simulations, 42L, 1.0e-6);
    }

    public LossDistMonteCarlo(final int nBuckets, final double maximum, final int simulations, final long seed,
            final double epsilon) {
        this.nBuckets_ = nBuckets;
        this.maximum_ = maximum;
        this.simulations_ = simulations;
        this.seed_ = seed;
        this.epsilon_ = epsilon;
    }

    @Override
    public Distribution op(final List< Double > nominals, final List< Double > probabilities) {
        final Distribution dist = new Distribution(nBuckets_, 0.0, maximum_);
        final MersenneTwisterUniformRng rng = new MersenneTwisterUniformRng(seed_);
        for ( int i = 0; i < simulations_; ++i ) {
            double e = 0;
            for ( int j = 0; j < nominals.size(); ++j ) {
                final double r = rng.next().value();
                if ( r <= probabilities.get(j) ) {
                    e += nominals.get(j);
                }
            }
            dist.add(e + epsilon_);
        }
        dist.normalize();
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
}
