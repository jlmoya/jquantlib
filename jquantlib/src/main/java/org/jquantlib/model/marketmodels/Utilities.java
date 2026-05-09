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
 Copyright (C) 2006 Marco Bianchetti
 Copyright (C) 2006 Mark Joshi
*/

package org.jquantlib.model.marketmodels;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jquantlib.QL;

/**
 * Utility functions used by Market-model classes.
 *
 * <p>Java port of {@code ql/models/marketmodels/utilities.{hpp,cpp}}
 * (QuantLib v1.42.1).
 *
 * <p>Design notes (Phase 3h P3H-4): C++ {@code std::valarray<bool>} maps to
 * Java {@code boolean[]}. The {@link #mergeTimes(List)} method returns its
 * results as a {@link MergeResult} record rather than C++-style out-parameters.
 */
public final class Utilities {

    private Utilities() {
        // utility class — no instances
    }

    /**
     * Result of {@link #mergeTimes(List)} — pure Java alternative to the C++
     * out-parameter pattern.
     */
    public static final class MergeResult {
        private final double[] mergedTimes;
        private final boolean[][] isPresent;

        public MergeResult(final double[] mergedTimes, final boolean[][] isPresent) {
            this.mergedTimes = mergedTimes;
            this.isPresent = isPresent;
        }

        /** Sorted, deduplicated union of all input time vectors. */
        public double[] mergedTimes() { return mergedTimes; }

        /**
         * For each input vector i and merged time j, {@code isPresent[i][j]}
         * indicates whether {@code mergedTimes[j]} appears in {@code times.get(i)}.
         */
        public boolean[][] isPresent() { return isPresent; }
    }

    /**
     * Merges multiple time vectors into a single sorted, deduplicated vector,
     * and reports for each input vector which merged times are present.
     * <p>
     * Java port of C++ {@code mergeTimes(const std::vector<std::vector<Time> >&,
     * std::vector<Time>&, std::vector<std::valarray<bool> >&)}.
     */
    public static MergeResult mergeTimes(final List<double[]> times) {
        // Compute upper bound on total elements
        int total = 0;
        for (final double[] t : times) {
            total += t.length;
        }
        final double[] all = new double[total];
        int pos = 0;
        for (final double[] t : times) {
            System.arraycopy(t, 0, all, pos, t.length);
            pos += t.length;
        }
        Arrays.sort(all);
        // Deduplicate in place
        int unique = 0;
        for (int i = 0; i < all.length; i++) {
            if (i == 0 || all[i] != all[i - 1]) {
                all[unique++] = all[i];
            }
        }
        final double[] mergedTimes = Arrays.copyOf(all, unique);

        final boolean[][] isPresent = new boolean[times.size()][unique];
        for (int i = 0; i < times.size(); i++) {
            final double[] ti = times.get(i);
            for (int j = 0; j < unique; j++) {
                isPresent[i][j] = Arrays.binarySearch(ti, mergedTimes[j]) >= 0;
            }
        }
        return new MergeResult(mergedTimes, isPresent);
    }

    /**
     * Look for elements of a set in a subset.
     * Returns an array of booleans such that:
     * element {@code set[i]} present/not present in subset.
     *
     * <p>Pre-condition: both arrays must be strictly increasing.
     */
    public static boolean[] isInSubset(final double[] set, final double[] subset) {
        final boolean[] result = new boolean[set.length];
        final int dimSubSet = subset.length;
        if (dimSubSet == 0) {
            return result;
        }
        final int dimSet = set.length;
        QL.require(dimSet >= dimSubSet,
                "set is required to be larger or equal than subset");

        for (int i = 0; i < dimSet; ++i) {
            int j = 0;
            final double setElement = set[i];
            for (;;) {
                final double subsetElement = subset[j];
                result[i] = false;
                // if smaller no hope, leave false and go to next i
                if (setElement < subsetElement) {
                    break;
                }
                // if match, set result[i] to true and go to next i
                if (setElement == subsetElement) {
                    result[i] = true;
                    break;
                }
                // if larger, leave false if at the end or go to next j
                if (j == dimSubSet - 1) {
                    break;
                }
                ++j;
            }
        }
        return result;
    }

    /** Check that times are strictly increasing and that times[0] &gt; 0. */
    public static void checkIncreasingTimes(final double[] times) {
        final int nTimes = times.length;
        QL.require(nTimes > 0, "at least one time is required");
        QL.require(times[0] > 0.0,
                "first time (" + times[0] + ") must be greater than zero");
        for (int i = 0; i < nTimes - 1; ++i) {
            QL.require(times[i + 1] - times[i] > 0,
                    "non increasing rate times: times[" + i + "]=" + times[i]
                            + ", times[" + (i + 1) + "]=" + times[i + 1]);
        }
    }

    /**
     * Check increasing times (at least 2 entries) and return their successive
     * differences (taus) into the supplied array — resized in-place if its
     * length differs from {@code times.length - 1}.
     *
     * <p>Java semantics: returns the (possibly-new) tau array. Callers should
     * use the returned reference because the input may have been replaced.
     */
    public static double[] checkIncreasingTimesAndCalculateTaus(
            final double[] times, final double[] taus) {
        final int nTimes = times.length;
        QL.require(nTimes > 1,
                "at least two times are required, " + nTimes + " provided");
        QL.require(times[0] > 0.0,
                "first time (" + times[0] + ") must be greater than zero");
        double[] out = taus;
        if (out == null || out.length != nTimes - 1) {
            out = new double[nTimes - 1];
        }
        for (int i = 0; i < nTimes - 1; ++i) {
            out[i] = times[i + 1] - times[i];
            QL.require(out[i] > 0,
                    "non increasing rate times: times[" + i + "]=" + times[i]
                            + ", times[" + (i + 1) + "]=" + times[i + 1]);
        }
        return out;
    }

    // Convenience overload — convert from the C++ vector<vector<Time>> via a List<List<Double>>
    public static MergeResult mergeTimesBoxed(final List<List<Double>> times) {
        final List<double[]> primitive = new ArrayList<>(times.size());
        for (final List<Double> t : times) {
            final double[] arr = new double[t.size()];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = t.get(i);
            }
            primitive.add(arr);
        }
        return mergeTimes(primitive);
    }
}
