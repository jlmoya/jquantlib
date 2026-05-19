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
 Copyright (C) 2006, 2007 Ferdinando Ametrano
 Copyright (C) 2006 Marco Bianchetti
 Copyright (C) 2006 Cristina Duminuco
 Copyright (C) 2006 Giorgio Facchinetti
 Copyright (C) 2006, 2007 Mark Joshi
*/

package org.jquantlib.model.marketmodels;

import org.jquantlib.QL;

import java.util.Arrays;

/**
 * Market-model evolution description.
 *
 * <p>Java port of {@code ql/models/marketmodels/evolutiondescription.{hpp,cpp}}
 * (QuantLib v1.42.1).
 *
 * <p>This class stores:
 * <ul>
 *   <li>{@code evolutionTimes} = the times at which the rates need to be known,</li>
 *   <li>{@code rateTimes} = the times defining the rates that are to be evolved,</li>
 *   <li>{@code relevanceRates} = which rates need to be known at each time.</li>
 * </ul>
 *
 * <p>{@code std::pair<Size,Size>} from C++ is mapped to {@link Range} (a small
 * value object) per Phase 3h plan §A.2.
 */
public class EvolutionDescription {

    private final int numberOfRates_;
    private final double[] rateTimes_;
    private final double[] evolutionTimes_;
    private final Range[] relevanceRates_;
    private double[] rateTaus_;
    private final int[] firstAliveRate_;
    /** Default constructor — empty description. */
    public EvolutionDescription() {
        this.numberOfRates_ = 0;
        this.rateTimes_ = new double[0];
        this.evolutionTimes_ = new double[0];
        this.relevanceRates_ = new Range[0];
        this.rateTaus_ = new double[0];
        this.firstAliveRate_ = new int[0];
    }

    public EvolutionDescription(final double[] rateTimes) {
        this(rateTimes, null, null);
    }

    public EvolutionDescription(final double[] rateTimes, final double[] evolutionTimes) {
        this(rateTimes, evolutionTimes, null);
    }

    public EvolutionDescription(final double[] rateTimes, final double[] evolutionTimes, final Range[] relevanceRates) {
        this.numberOfRates_ = (rateTimes == null || rateTimes.length == 0) ? 0 : rateTimes.length - 1;
        this.rateTimes_ = (rateTimes == null) ? new double[0] : rateTimes.clone();

        if ( (evolutionTimes == null || evolutionTimes.length == 0) && this.rateTimes_.length > 0 ) {
            // default: take rateTimes[0..n-1] (i.e. drop the last)
            this.evolutionTimes_ = Arrays.copyOf(this.rateTimes_, this.rateTimes_.length - 1);
        } else {
            this.evolutionTimes_ = (evolutionTimes == null) ? new double[0] : evolutionTimes.clone();
        }

        // rateTaus: computed from rateTimes
        this.rateTaus_ = new double[numberOfRates_];
        if ( numberOfRates_ > 0 ) {
            this.rateTaus_ = Utilities.checkIncreasingTimesAndCalculateTaus(this.rateTimes_, this.rateTaus_);
        }

        // evolutionTimes must be strictly increasing
        Utilities.checkIncreasingTimes(this.evolutionTimes_);
        final int numberOfSteps = this.evolutionTimes_.length;

        QL.require(this.evolutionTimes_[numberOfSteps - 1] <= this.rateTimes_[this.rateTimes_.length - 2],
                "The last evolution time (" + this.evolutionTimes_[numberOfSteps - 1]
                        + ") is past the last fixing time (" + this.rateTimes_[this.rateTimes_.length - 2] + ")");

        if ( relevanceRates == null || relevanceRates.length == 0 ) {
            this.relevanceRates_ = new Range[numberOfSteps];
            for ( int i = 0; i < numberOfSteps; i++ ) {
                this.relevanceRates_[i] = new Range(0, numberOfRates_);
            }
        } else {
            QL.require(relevanceRates.length == numberOfSteps, "relevanceRates / evolutionTimes mismatch");
            this.relevanceRates_ = relevanceRates.clone();
        }

        this.firstAliveRate_ = new int[numberOfSteps];
        double currentEvolutionTime = 0.0;
        int firstAliveRate = 0;
        for ( int j = 0; j < numberOfSteps; ++j ) {
            while ( this.rateTimes_[firstAliveRate] <= currentEvolutionTime ) {
                ++firstAliveRate;
            }
            this.firstAliveRate_[j] = firstAliveRate;
            currentEvolutionTime = this.evolutionTimes_[j];
        }
    }

    public static void checkCompatibility(final EvolutionDescription evolution, final int[] numeraires) {
        final double[] evolutionTimes = evolution.evolutionTimes();
        final int n = evolutionTimes.length;
        QL.require(numeraires.length == n,
                "Size mismatch between numeraires (" + numeraires.length + ") and evolution times (" + n + ")");
        final double[] rateTimes = evolution.rateTimes();
        for ( int i = 0; i < n - 1; i++ ) {
            QL.require(rateTimes[numeraires[i]] >= evolutionTimes[i],
                    (i + 1) + "-th step, evolution time " + evolutionTimes[i] + ": the numeraire (" + numeraires[i]
                            + "), corresponding to rate time " + rateTimes[numeraires[i]] + ", is expired");
        }
    }

    public static boolean isInTerminalMeasure(final EvolutionDescription evolution, final int[] numeraires) {
        final double[] rateTimes = evolution.rateTimes();
        int min = Integer.MAX_VALUE;
        for ( final int v : numeraires ) {
            if ( v < min ) {
                min = v;
            }
        }
        return min == rateTimes.length - 1;
    }

    public static boolean isInMoneyMarketPlusMeasure(final EvolutionDescription evolution, final int[] numeraires,
            final int offset) {
        boolean res = true;
        final double[] rateTimes = evolution.rateTimes();
        final int maxNumeraire = rateTimes.length - 1;
        QL.require(offset <= maxNumeraire,
                "offset (" + offset + ") is greater than the max allowed value for numeraire (" + maxNumeraire + ")");
        final double[] evolutionTimes = evolution.evolutionTimes();
        int j = 0;
        for ( int i = 0; i < evolutionTimes.length; ++i ) {
            while ( rateTimes[j] < evolutionTimes[i] ) {
                j++;
            }
            res = (numeraires[i] == Math.min(j + offset, maxNumeraire)) && res;
        }
        return res;
    }

    public static boolean isInMoneyMarketMeasure(final EvolutionDescription evolution, final int[] numeraires) {
        return isInMoneyMarketPlusMeasure(evolution, numeraires, 0);
    }

    /** Terminal measure: the last bond is used as numeraire. */
    public static int[] terminalMeasure(final EvolutionDescription evolution) {
        final int n = evolution.evolutionTimes().length;
        final int[] result = new int[n];
        Arrays.fill(result, evolution.rateTimes().length - 1);
        return result;
    }

    /**
     * Offsetted discretely-compounded money market account measure: for each step the offset-th unexpired bond is used
     * as numeraire. When offset=0 the result is the usual discretely-compounded money market account measure.
     */
    public static int[] moneyMarketPlusMeasure(final EvolutionDescription ev, final int offset) {
        final double[] rateTimes = ev.rateTimes();
        final int maxNumeraire = rateTimes.length - 1;
        QL.require(offset <= maxNumeraire,
                "offset (" + offset + ") is greater than the max allowed value for numeraire (" + maxNumeraire + ")");
        final double[] evolutionTimes = ev.evolutionTimes();
        final int n = evolutionTimes.length;
        final int[] numeraires = new int[n];
        int j = 0;
        for ( int i = 0; i < n; ++i ) {
            while ( rateTimes[j] < evolutionTimes[i] ) {
                j++;
            }
            numeraires[i] = Math.min(j + offset, maxNumeraire);
        }
        return numeraires;
    }

    /**
     * Discretely-compounded money market account measure: for each step the first unexpired bond is used as numeraire.
     */
    public static int[] moneyMarketMeasure(final EvolutionDescription ev) {
        return moneyMarketPlusMeasure(ev, 0);
    }

    public double[] rateTimes() {
        return rateTimes_;
    }

    // ----- Numeraire helper functions (free functions in C++) -----

    public double[] rateTaus() {
        return rateTaus_;
    }

    public double[] evolutionTimes() {
        return evolutionTimes_;
    }

    public int[] firstAliveRate() {
        return firstAliveRate_;
    }

    public Range[] relevanceRates() {
        return relevanceRates_;
    }

    public int numberOfRates() {
        return numberOfRates_;
    }

    public int numberOfSteps() {
        return evolutionTimes_.length;
    }

    /** Inclusive-exclusive range; mirrors C++ {@code std::pair<Size,Size>}. */
    public static final class Range {
        private final int first;
        private final int second;

        public Range(final int first, final int second) {
            this.first = first;
            this.second = second;
        }

        public int first() {
            return first;
        }

        public int second() {
            return second;
        }
    }
}
