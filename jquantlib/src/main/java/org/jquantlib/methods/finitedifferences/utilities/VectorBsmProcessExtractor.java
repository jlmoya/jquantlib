/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2024 Klaus Spanderen

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license. You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.
*/
package org.jquantlib.methods.finitedifferences.utilities;

import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.math.Closeness;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.time.Date;

/**
 * Helper class to extract underlying, volatility etc. from a vector of
 * Generalized Black-Scholes processes.
 *
 * <p>Java port of v1.42.1
 * {@code ql/pricingengines/basket/vectorbsmprocessextractor.{hpp,cpp}}.
 *
 * <p>Used by {@code FdndimBlackScholesVanillaEngine} and other basket
 * engines to project per-process scalar values into {@code Array}s
 * indexed by underlying.
 *
 * <p>The Java port places the class in
 * {@code methods.finitedifferences.utilities} (rather than
 * {@code pricingengines.basket}) to keep it package-loop free —
 * basket engines depend on FD utilities but not vice-versa.
 *
 * @author Phase 5e.5b-CFC-d-280 port
 */
public final class VectorBsmProcessExtractor {

    /** Functional interface used to project a scalar per process. */
    @FunctionalInterface
    public interface ProcessProjection {
        double apply(GeneralizedBlackScholesProcess p);
    }

    private final List<GeneralizedBlackScholesProcess> processes;

    public VectorBsmProcessExtractor(final List<GeneralizedBlackScholesProcess> processes) {
        QL.require(processes != null && !processes.isEmpty(),
                "no Black-Scholes process given");
        this.processes = processes;
    }

    private Array extract(final ProcessProjection f) {
        final Array a = new Array(processes.size());
        for (int i = 0; i < processes.size(); ++i) {
            a.set(i, f.apply(processes.get(i)));
        }
        return a;
    }

    /** Spots: {@code p->x0()} for each process. */
    public Array getSpot() {
        return extract(p -> p.x0());
    }

    /** Dividend yield discount factors at {@code maturityDate}. */
    public Array getDividendYieldDf(final Date maturityDate) {
        return extract(p -> p.dividendYield().currentLink().discount(maturityDate));
    }

    /** Black variances {@code sigma^2 * T} at {@code maturityDate} & spot. */
    public Array getBlackVariance(final Date maturityDate) {
        return extract(p -> p.blackVolatility().currentLink()
                .blackVariance(maturityDate, p.x0()));
    }

    /** Black total std dev {@code sigma * sqrt(T)} at {@code maturityDate} & spot. */
    public Array getBlackStdDev(final Date maturityDate) {
        return extract(p -> {
            final double t = p.blackVolatility().currentLink()
                    .timeFromReference(maturityDate);
            return p.blackVolatility().currentLink().blackVol(maturityDate, p.x0())
                    * Math.sqrt(t);
        });
    }

    /**
     * Common risk-free discount factor at {@code maturityDate}.
     * <p>
     * Requires that every process share the same risk-free curve (the
     * basket payoff is discounted with a single curve). Throws when the
     * curves disagree.
     */
    public double getInterestRateDf(final Date maturityDate) {
        final Array dr = extract(p -> p.riskFreeRate().currentLink().discount(maturityDate));
        for (int i = 1; i < dr.size(); ++i) {
            QL.require(Closeness.isCloseEnough(dr.get(i), dr.get(0)),
                    "interest rates need to be the same for all underlyings");
        }
        return dr.get(0);
    }
}
