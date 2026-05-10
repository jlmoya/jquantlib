/*
 Copyright (C) 2007 Gang Liang
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
package org.jquantlib.math.statistics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.math.Closeness;

/**
 * Histogram class.
 *
 * <p>Java port of QuantLib v1.42.1 {@code class Histogram} (declared in
 * {@code ql/math/statistics/histogram.hpp}). Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>Computes the histogram of a given data set. The caller can specify
 * the number of bins, the breaks, or the algorithm for determining these
 * quantities.
 *
 * <p>Constructor / API mapping:
 * <ul>
 *   <li>{@code Histogram(data, breaks)} — fixed number of bins (=
 *       {@code breaks + 1}).</li>
 *   <li>{@code Histogram(data, Algorithm)} — bin count derived by the
 *       algorithm (Sturges / FD / Scott).</li>
 *   <li>{@code Histogram(data, breaks)} where {@code breaks} is a
 *       {@code double[]} — explicit break points.</li>
 * </ul>
 *
 * <p>Mirrors C++ semantics:
 * <ul>
 *   <li>Bin {@code i} (for {@code i &lt; bins-1}) contains points {@code p}
 *       with {@code p &lt; breaks[i]} and not yet placed.</li>
 *   <li>Bin {@code bins-1} catches the remainder (all points {@code &ge;
 *       breaks[bins-2]}).</li>
 * </ul>
 */
public final class Histogram {

    /** Bin-count selection algorithm. */
    public enum Algorithm {
        /** No algorithm — fail if bin count not provided. */
        None,
        /** Sturges' rule: ceil(log2(N) + 1). */
        Sturges,
        /** Freedman-Diaconis: bin width = 2*IQR/N^(1/3). */
        FD,
        /** Scott's rule: bin width = 3.5*sigma/N^(1/3). */
        Scott
    }

    private final double[] data_;
    private int bins_;
    private final Algorithm algorithm_;
    private double[] breaks_;
    private int[] counts_;
    private double[] frequency_;

    private Histogram(final double[] data, final int bins, final Algorithm algo,
                      final double[] breaks) {
        QL.require(data != null && data.length > 0, "no data given");
        this.data_ = data.clone();
        this.bins_ = bins;
        this.algorithm_ = algo;
        this.breaks_ = breaks == null ? null : breaks.clone();
        calculate();
    }

    /**
     * Fixed-bin-count constructor.
     *
     * <p>Mirrors C++ {@code Histogram(data_begin, data_end, breaks)}; resulting
     * bin count is {@code breaks + 1}.
     *
     * @param data    raw observations
     * @param breaks  number of breaks; bins = breaks + 1
     */
    public Histogram(final double[] data, final int breaks) {
        this(data, breaks + 1, Algorithm.None, null);
    }

    /**
     * Algorithm-driven constructor.
     *
     * <p>Mirrors C++ {@code Histogram(data_begin, data_end, algorithm)}.
     *
     * @param data       raw observations
     * @param algorithm  bin-count selection algorithm
     */
    public Histogram(final double[] data, final Algorithm algorithm) {
        this(data, Integer.MIN_VALUE, algorithm, null);
    }

    /**
     * Explicit-break constructor.
     *
     * <p>Mirrors C++ {@code Histogram(data_begin, data_end, breaks_begin,
     * breaks_end)}; resulting bin count is {@code breaks.length + 1}.
     *
     * @param data    raw observations
     * @param breaks  explicit break points (will be sorted + deduplicated)
     */
    public Histogram(final double[] data, final double[] breaks) {
        this(data, Integer.MIN_VALUE, Algorithm.None, breaks);
    }

    /** Number of bins. */
    public int bins() {
        return bins_;
    }

    /** Bin-break points (length = {@code bins() - 1}); read-only. */
    public double[] breaks() {
        return breaks_.clone();
    }

    /** Active bin-count algorithm (or {@link Algorithm#None}). */
    public Algorithm algorithm() {
        return algorithm_;
    }

    /** True if {@link #bins()} == 0 (cannot occur post-{@code calculate}). */
    public boolean empty() {
        return bins_ == 0;
    }

    /** Count in bin {@code i}. */
    public int counts(final int i) {
        return counts_[i];
    }

    /** Frequency in bin {@code i} (= {@link #counts(int)} / N). */
    public double frequency(final int i) {
        return frequency_[i];
    }

    /**
     * Worker mirroring C++ {@code Histogram::calculate()}: derives the
     * bin count if needed, fills in equispaced breaks if missing, then
     * tallies counts and frequencies.
     */
    private void calculate() {
        // min/max
        double dmin = data_[0], dmax = data_[0];
        for (int i = 1; i < data_.length; ++i) {
            if (data_[i] < dmin) dmin = data_[i];
            if (data_[i] > dmax) dmax = data_[i];
        }

        // derive bin count if needed (Integer.MIN_VALUE sentinel = unset).
        // When explicit breaks were passed, the bin count is set in the
        // breaks-handling branch below.
        if (bins_ == Integer.MIN_VALUE && breaks_ == null) {
            switch (algorithm_) {
                case Sturges:
                    bins_ = (int) Math.ceil(Math.log((double) data_.length) / Math.log(2.0) + 1.0);
                    break;
                case FD: {
                    final double r1 = quantile(data_, 0.25);
                    final double r2 = quantile(data_, 0.75);
                    final double h = 2.0 * (r2 - r1) * Math.pow((double) data_.length, -1.0 / 3.0);
                    bins_ = (int) Math.ceil((dmax - dmin) / h);
                    break;
                }
                case Scott: {
                    final IncrementalStatistics summary = new IncrementalStatistics();
                    for (final double d : data_) summary.add(d, 1.0);
                    final double variance = summary.variance();
                    final double h = 3.5 * Math.sqrt(variance)
                            * Math.pow((double) data_.length, -1.0 / 3.0);
                    bins_ = (int) Math.ceil((dmax - dmin) / h);
                    break;
                }
                case None:
                    throw new IllegalStateException("a bin-partition algorithm is required");
                default:
                    throw new IllegalStateException("unknown bin-partition algorithm");
            }
            if (bins_ < 1) bins_ = 1;
        }

        if (breaks_ == null) {
            breaks_ = new double[bins_ - 1];
            final double h = (dmax - dmin) / bins_;
            for (int i = 0; i < breaks_.length; ++i) {
                breaks_[i] = dmin + (i + 1) * h;
            }
        } else {
            // sort + de-dup using Closeness (C++ uses close_enough).
            Arrays.sort(breaks_);
            final List<Double> dedup = new ArrayList<>(breaks_.length);
            for (int i = 0; i < breaks_.length; ++i) {
                if (i == 0 || !Closeness.isCloseEnough(breaks_[i], dedup.get(dedup.size() - 1))) {
                    dedup.add(breaks_[i]);
                }
            }
            breaks_ = new double[dedup.size()];
            for (int i = 0; i < breaks_.length; ++i) breaks_[i] = dedup.get(i);
            // Note: C++ recomputes bins_ = breaks.size()+1 only in the explicit-
            // breaks ctor (see header). Since that's the only path that takes
            // this branch, we mirror that.
            bins_ = breaks_.length + 1;
        }

        // tally
        counts_ = new int[bins_];
        for (final double p : data_) {
            boolean processed = false;
            for (int i = 0; i < breaks_.length; ++i) {
                if (p < breaks_[i]) {
                    counts_[i]++;
                    processed = true;
                    break;
                }
            }
            if (!processed) counts_[bins_ - 1]++;
        }

        frequency_ = new double[bins_];
        final int totalCounts = data_.length;
        for (int i = 0; i < bins_; ++i) {
            frequency_[i] = (double) counts_[i] / totalCounts;
        }
    }

    /**
     * Discontinuous quantile (Hyndman-Fan type 8). Mirrors the file-static
     * {@code quantile} helper inside C++ {@code histogram.cpp}; required
     * by the FD algorithm.
     */
    private static double quantile(final double[] samples, final double prob) {
        final int n = samples.length;
        QL.require(prob >= 0.0 && prob <= 1.0, "Probability has to be in [0,1].");
        QL.require(n > 0, "The sample size has to be positive.");

        if (n == 1) return samples[0];

        final double a = 1.0 / 3.0;
        final double b = 2.0 * a / (n + a);
        if (prob < b) {
            double m = samples[0];
            for (int i = 1; i < n; ++i) if (samples[i] < m) m = samples[i];
            return m;
        }
        if (prob > 1.0 - b) {
            double m = samples[0];
            for (int i = 1; i < n; ++i) if (samples[i] > m) m = samples[i];
            return m;
        }

        final int index = (int) Math.floor((n + a) * prob + a);
        // partial-sort: get smallest (index+1) elements
        final double[] sorted = samples.clone();
        Arrays.sort(sorted);
        // sort full array; trim conceptually
        final double weight = n * prob + a - index;
        return (1.0 - weight) * sorted[index - 1] + weight * sorted[index];
    }

    /** Convenience {@code List<Double>} variant of {@link #breaks()}. */
    public List<Double> breaksList() {
        final List<Double> out = new ArrayList<>(breaks_.length);
        for (final double b : breaks_) out.add(b);
        return Collections.unmodifiableList(out);
    }
}
