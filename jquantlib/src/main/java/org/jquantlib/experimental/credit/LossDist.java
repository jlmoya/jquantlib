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

import org.jquantlib.math.distributions.BinomialDistribution;
import org.jquantlib.math.distributions.CumulativeBinomialDistribution;

import java.util.ArrayList;
import java.util.List;

/**
 * Probability formulas and algorithms for portfolio loss distributions.
 *
 * <p>Java port of QuantLib v1.42.1 {@code QuantLib::LossDist}
 * ({@code ql/experimental/credit/lossdistribution.{hpp,cpp}}).
 *
 * <p>Phase 4m.6.
 */
public abstract class LossDist {

    /** Binomial probability of n defaults using {@code p[0]}. */
    public static double binomialProbabilityOfNEvents(final int n, final List< Double > p) {
        final BinomialDistribution binomial = new BinomialDistribution(p.get(0), p.size());
        return binomial.op(n);
    }

    /** Binomial probability of at least n defaults using {@code p[0]}. */
    public static double binomialProbabilityOfAtLeastNEvents(final int n, final List< Double > p) {
        final CumulativeBinomialDistribution binomial = new CumulativeBinomialDistribution(p.get(0), p.size());
        return 1.0 - binomial.op(n - 1);
    }

    /**
     * Probability of exactly n default events.
     *
     * <p>Reference: Xiaofong Ma, "Numerical Methods for the Valuation of Synthetic
     * Collateralized Debt Obligations", PhD Thesis, University of Toronto, 2007.
     */
    public static List< Double > probabilityOfNEvents(final List< Double > p) {
        final int n = p.size();
        final List< Double > probability = new ArrayList<>(n + 1);
        for ( int k = 0; k <= n; ++k ) {
            probability.add(0.0);
        }
        probability.set(0, 1.0);
        for ( int j = 0; j < n; ++j ) {
            final List< Double > prev = new ArrayList<>(probability);
            probability.set(0, prev.get(0) * (1.0 - p.get(j)));
            for ( int i = 1; i <= j; ++i ) {
                probability.set(i, prev.get(i - 1) * p.get(j) + prev.get(i) * (1.0 - p.get(j)));
            }
            probability.set(j + 1, prev.get(j) * p.get(j));
        }
        return probability;
    }

    /** Probability of exactly k default events. */
    public static double probabilityOfNEvents(final int k, final List< Double > p) {
        return probabilityOfNEvents(p).get(k);
    }

    /** Probability of at least k defaults. */
    public static double probabilityOfAtLeastNEvents(final int k, final List< Double > p) {
        final List< Double > probability = probabilityOfNEvents(p);
        double sum = 1.0;
        for ( int j = 0; j < k; ++j ) {
            sum -= probability.get(j);
        }
        return sum;
    }

    public abstract Distribution op(List< Double > volumes, List< Double > probabilities);

    public abstract int buckets();

    public abstract double maximum();
}
