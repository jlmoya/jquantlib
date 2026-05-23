/*
 Copyright (C) 2026 Jose Moya

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
 Copyright (C) 2007 Ferdinando Ametrano
 Copyright (C) 2007 Marco Bianchetti
 Copyright (C) 2007 Giorgio Facchinetti
 Copyright (C) 2007 François du Vignaud
*/

package org.jquantlib.model.marketmodels.correlations;

import org.jquantlib.QL;
import org.jquantlib.math.Closeness;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.marketmodels.PiecewiseConstantCorrelation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Exponential forward correlation:
 * <pre>
 *   rho(i,j) = L + (1 - L) * exp(-beta * |(t_i - t)^gamma - (t_j - t)^gamma|)
 * </pre>
 * where {@code L} is the long-term correlation, {@code beta} is the exponential decay between distant forward rates,
 * and {@code gamma} is the exponent on time-to-go.
 *
 * @author Jose Moya
 * @see "ql/models/marketmodels/correlations/expcorrelations.{hpp,cpp}" v1.42.1
 */
public class ExponentialForwardCorrelation extends PiecewiseConstantCorrelation {

    private final int numberOfRates_;
    private final double longTermCorr_;
    private final double beta_;
    private final double gamma_;
    private final List< Double > rateTimes_;
    private final List< Double > times_;
    private final List< Matrix > correlations_;

    public ExponentialForwardCorrelation(final List< Double > rateTimes) {
        this(rateTimes, 0.5, 0.2, 1.0, new ArrayList<>());
    }

    public ExponentialForwardCorrelation(final List< Double > rateTimes, final double longTermCorr, final double beta) {
        this(rateTimes, longTermCorr, beta, 1.0, new ArrayList<>());
    }

    public ExponentialForwardCorrelation(final List< Double > rateTimes, final double longTermCorr, final double beta,
            final double gamma) {
        this(rateTimes, longTermCorr, beta, gamma, new ArrayList<>());
    }

    public ExponentialForwardCorrelation(final List< Double > rateTimes, final double longTermCorr, final double beta,
            final double gamma, final List< Double > times) {
        this.numberOfRates_ = rateTimes == null || rateTimes.isEmpty() ? 0 : rateTimes.size() - 1;
        this.longTermCorr_ = longTermCorr;
        this.beta_ = beta;
        this.gamma_ = gamma;
        this.rateTimes_ = new ArrayList<>(rateTimes);

        QL.require(numberOfRates_ > 1, "Rate times must contain at least two values");

        checkIncreasingTimes(rateTimes_);

        // corrTimes must include all rateTimes but the last
        if ( times == null || times.isEmpty() ) {
            this.times_ = new ArrayList<>(rateTimes_.subList(0, rateTimes_.size() - 1));
        } else {
            this.times_ = new ArrayList<>(times);
            checkIncreasingTimes(this.times_);
        }

        if ( Closeness.isClose(gamma, 1.0) ) {
            final List< Double > temp = new ArrayList<>(rateTimes_.subList(0, rateTimes_.size() - 1));
            QL.require(this.times_.equals(temp), "corr times must be equal to (all) rate times (but the last)");
            final Matrix c = exponentialCorrelations(rateTimes_, longTermCorr_, beta_, 1.0, 0.0);
            this.correlations_ = TimeHomogeneousForwardCorrelation.evolvedMatrices(c);
        } else {
            // Faithful port of C++ v1.42.1 expcorrelations.cpp:105-106,
            // which still carries the upstream FIXME: "should check here
            // that all rateTimes but the last are included in rateTimes".
            // We only check the back-most time, mirroring C++ behavior.
            QL.require(this.times_.get(this.times_.size() - 1) <= rateTimes_.get(numberOfRates_),
                    "last corr time " + this.times_.get(this.times_.size() - 1) + " is after next-to-last rate time "
                            + rateTimes_.get(numberOfRates_));
            this.correlations_ = new ArrayList<>(this.times_.size());
            double time = this.times_.get(0) / 2.0;
            this.correlations_.add(exponentialCorrelations(rateTimes_, longTermCorr_, beta_, gamma_, time));
            for ( int k = 1; k < this.times_.size(); ++k ) {
                time = (this.times_.get(k) + this.times_.get(k - 1)) / 2.0;
                this.correlations_.add(exponentialCorrelations(rateTimes_, longTermCorr_, beta_, gamma_, time));
            }
        }
    }

    /**
     * Stand-alone exponential correlation matrix evaluated at a fixed time. Mirrors C++ free function
     * {@code exponentialCorrelations}.
     *
     * @param rateTimes    rate-time grid (length n+1 for n rates)
     * @param longTermCorr long-term correlation in [0, 1]
     * @param beta         exponential decay; must be non-negative
     * @param gamma        exponent on time-to-go; in [0, 1]
     * @param time         the evaluation time
     */
    public static Matrix exponentialCorrelations(final List< Double > rateTimes, final double longTermCorr,
            final double beta, final double gamma, final double time) {
        // preliminary checks
        checkIncreasingTimes(rateTimes);
        QL.require(longTermCorr <= 1.0 && longTermCorr >= 0.0,
                "Long term correlation (" + longTermCorr + ") outside [0;1] interval");
        QL.require(beta >= 0.0, "beta (" + beta + ") must be greater than zero");
        QL.require(gamma <= 1.0 && gamma >= 0.0, "gamma (" + gamma + ") outside [0;1] interval");

        final int nbRows = rateTimes.size() - 1;
        final Matrix correlations = new Matrix(nbRows, nbRows);
        for ( int i = 0; i < nbRows; ++i ) {
            // correlation is defined only between (alive) stochastic rates...
            if ( time <= rateTimes.get(i) ) {
                correlations.set(i, i, 1.0);
                for ( int j = 0; j < i; ++j ) {
                    if ( time <= rateTimes.get(j) ) {
                        final double v = longTermCorr + (1.0 - longTermCorr) * Math.exp(-beta * Math.abs(
                                Math.pow(rateTimes.get(i) - time, gamma) - Math.pow(rateTimes.get(j) - time, gamma)));
                        correlations.set(i, j, v);
                        correlations.set(j, i, v);
                    }
                }
            }
        }
        return correlations;
    }

    /**
     * Lightweight in-package replacement for {@code utilities::checkIncreasingTimes} (Phase 3h Track A.1). Will be
     * replaced by a call to the proper utilities class once Track A lands; behavior matches QL's check exactly.
     */
    static void checkIncreasingTimes(final List< Double > times) {
        QL.require(!times.isEmpty(), "at least one time is required");
        QL.require(times.get(0) >= 0.0, "first time (" + times.get(0) + ") must be non-negative");
        for ( int i = 1; i < times.size(); ++i ) {
            QL.require(times.get(i) > times.get(i - 1),
                    "non-increasing times: time(" + (i - 1) + ")=" + times.get(i - 1) + " >= time(" + i + ")="
                            + times.get(i));
        }
    }

    /**
     * Convenience overload accepting a primitive array.
     */
    public static Matrix exponentialCorrelations(final double[] rateTimes, final double longTermCorr, final double beta,
            final double gamma, final double time) {
        final List< Double > list = new ArrayList<>(rateTimes.length);
        for ( final double t : rateTimes ) {
            list.add(t);
        }
        return exponentialCorrelations(list, longTermCorr, beta, gamma, time);
    }

    @SuppressWarnings( "unused" )
    private static List< Double > arrayToList(final double[] arr) {
        return Arrays.stream(arr).boxed().collect(java.util.stream.Collectors.toList());
    }

    @Override
    public List< Double > times() {
        return times_;
    }

    @Override
    public List< Double > rateTimes() {
        return rateTimes_;
    }

    @Override
    public List< Matrix > correlations() {
        return correlations_;
    }

    @Override
    public int numberOfRates() {
        return numberOfRates_;
    }
}
