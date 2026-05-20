/*
 Copyright (C) 2026 JQuantLib migration

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
 Copyright (C) 2008 Roland Lichters
*/

package org.jquantlib.experimental.credit;

import org.jquantlib.QL;
import org.jquantlib.math.Closeness;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Discretized probability density and cumulative probability.
 *
 * <p>Java port of QuantLib v1.42.1 {@code QuantLib::Distribution}
 * ({@code ql/experimental/credit/distribution.{hpp,cpp}}).
 *
 * <p>Stores per-bucket density / cumulative / excess / cumulative-excess
 * values along with bucket coordinates {@code x_} and widths {@code dx_}. Mostly used by basket-loss-distribution
 * machinery (recursive / binomial / saddle-point loss models).
 *
 * <p>Phase 4m foundation. Templated {@code expectedValue<F>(F&)} is not
 * yet ported (none of the ported callers use it); add when needed.
 */
public class Distribution {

    private final List< Integer > count;
    /** Coordinate of left-hand cell boundary. */
    private final List< Double > x;
    /** Cell width. */
    private final List< Double > dx;
    /** Probability density (density*dx = probability of loss in cell i). */
    private final List< Double > density;
    /** Cumulated (integrated) density from x = 0. */
    private final List< Double > cumulativeDensity;
    /** Excess probability — cumulated from {@code x[i]} to infinity. */
    private final List< Double > excessProbability;
    /** Integrated {@code excessProbability} from x = 0. */
    private final List< Double > cumulativeExcessProbability;
    /** Average loss in cell i. */
    private final List< Double > average;
    private int size;
    private double xmin;
    private double xmax;
    private int overFlow;
    private int underFlow;
    private boolean isNormalized;

    public Distribution() {
        this.size = 0;
        this.xmin = 0;
        this.xmax = 0;
        this.count = new ArrayList<>();
        this.x = new ArrayList<>();
        this.dx = new ArrayList<>();
        this.density = new ArrayList<>();
        this.cumulativeDensity = new ArrayList<>();
        this.excessProbability = new ArrayList<>();
        this.cumulativeExcessProbability = new ArrayList<>();
        this.average = new ArrayList<>();
    }

    public Distribution(final int nBuckets, final double xmin, final double xmax) {
        this();
        this.size = nBuckets;
        this.xmin = xmin;
        this.xmax = xmax;
        for ( int i = 0; i < nBuckets; i++ ) {
            count.add(0);
            x.add(0.0);
            dx.add(0.0);
            density.add(0.0);
            cumulativeDensity.add(0.0);
            excessProbability.add(0.0);
            cumulativeExcessProbability.add(0.0);
            average.add(0.0);
        }
        // Use single-multiplication x[i] = xmin + i*dx0 (not accumulated +=)
        // so dx[i] stays bit-equal across buckets — required by the
        // constant-bucket-size invariant in convolve()/transform()/etc.
        // Accumulated addition drifts by O(eps*n) and breaks `dx.get(i).equals(dx.get(i-1))`.
        final double dx0 = (xmax - xmin) / nBuckets;
        for ( int i = 0; i < nBuckets; i++ ) {
            dx.set(i, dx0);
            x.set(i, xmin + i * dx0);
        }
    }

    /** Convolve d1 and d2 (mirrors C++ {@code ManipulateDistribution::convolve}). */
    public static Distribution convolve(final Distribution d1, final Distribution d2) {
        QL.require(d1.dx.get(0).equals(d2.dx.get(0)), "bucket sizes differ in d1 and d2");
        for ( int i = 1; i < d1.size(); i++ ) {
            QL.require(d1.dx.get(i).equals(d1.dx.get(i - 1)), "bucket size varies in d1");
        }
        for ( int i = 1; i < d2.size(); i++ ) {
            QL.require(d2.dx.get(i).equals(d2.dx.get(i - 1)), "bucket size varies in d2");
        }
        QL.require(d1.xmin == 0.0 && d2.xmin == 0.0, "distributions offset larger than 0");

        final Distribution dist = new Distribution(d1.size() + d2.size() - 1, 0.0, d1.xmax + d2.xmax);

        for ( int i1 = 0; i1 < d1.size(); i1++ ) {
            final double dxi = d1.dx.get(i1);
            for ( int i2 = 0; i2 < d2.size(); i2++ ) {
                dist.density.set(i1 + i2, d1.density.get(i1) * d2.density.get(i2) * dxi);
            }
        }

        // update cumulated and excess
        dist.excessProbability.set(0, 1.0);
        for ( int i = 0; i < dist.size(); i++ ) {
            dist.cumulativeDensity.set(i, dist.density.get(i) * dist.dx.get(i));
            if ( i > 0 ) {
                dist.cumulativeDensity.set(i, dist.cumulativeDensity.get(i) + dist.cumulativeDensity.get(i - 1));
                dist.excessProbability.set(i,
                        dist.excessProbability.get(i - 1) - dist.density.get(i - 1) * dist.dx.get(i - 1));
            }
        }
        return dist;
    }

    /** Test-only: reset count for a given bucket. Mirrors no public API in C++; keep package-private if needed. */
    @SuppressWarnings( "unused" )
    private static List< Double > tail(final List< Double > in) {
        return new ArrayList<>(in.subList(1, in.size()));
    }

    @SuppressWarnings( "unused" )
    private static double[] toArray(final List< Double > in) {
        final double[] out = new double[in.size()];
        for ( int i = 0; i < in.size(); i++ ) {
            out[i] = in.get(i);
        }
        return out;
    }

    @SuppressWarnings( "unused" )
    private static List< Double > fromArray(final double[] in) {
        return new ArrayList<>(Arrays.asList(boxArray(in)));
    }

    @SuppressWarnings( "unused" )
    private static Double[] boxArray(final double[] in) {
        final Double[] out = new Double[in.length];
        for ( int i = 0; i < in.length; i++ ) {
            out[i] = in[i];
        }
        return out;
    }

    /** Lookup index of grid point to the left of {@code v}. */
    public int locate(final double v) {
        QL.require(
                (v >= x.get(0) || Closeness.isClose(v, x.get(0))) && (v <= x.get(x.size() - 1) + dx.get(dx.size() - 1)
                        || Closeness.isClose(v, x.get(x.size() - 1) + dx.get(dx.size() - 1))),
                "coordinate out of range");
        for ( int i = 0; i < x.size(); i++ ) {
            if ( x.get(i) > v ) {
                return i - 1;
            }
        }
        return x.size() - 1;
    }

    public double dx(final double v) {
        return dx.get(locate(v));
    }

    public void add(final double value) {
        isNormalized = false;
        if ( value < x.get(0) ) {
            underFlow++;
        } else {
            for ( int i = 0; i < count.size(); i++ ) {
                if ( x.get(i) + dx.get(i) > value ) {
                    count.set(i, count.get(i) + 1);
                    average.set(i, average.get(i) + value);
                    return;
                }
            }
            overFlow++;
        }
    }

    public void addDensity(final int bucket, final double value) {
        QL.require(bucket >= 0 && bucket < size, "bucket out of range");
        isNormalized = false;
        density.set(bucket, density.get(bucket) + value);
    }

    public void addAverage(final int bucket, final double value) {
        QL.require(bucket >= 0 && bucket < size, "bucket out of range");
        isNormalized = false;
        average.set(bucket, average.get(bucket) + value);
    }

    public void normalize() {
        if ( isNormalized ) {
            return;
        }
        int total = underFlow + overFlow;
        for ( int i = 0; i < size; i++ ) {
            total += count.get(i);
        }
        excessProbability.set(0, 1.0);
        cumulativeExcessProbability.set(0, 0.0);
        for ( int i = 0; i < size; i++ ) {
            if ( total > 0 ) {
                density.set(i, 1.0 / dx.get(i) * count.get(i) / total);
                if ( count.get(i) > 0 ) {
                    average.set(i, average.get(i) / count.get(i));
                }
            }
            if ( density.get(i) == 0.0 ) {
                average.set(i, x.get(i) + dx.get(i) / 2);
            }
            cumulativeDensity.set(i, density.get(i) * dx.get(i));
            if ( i > 0 ) {
                cumulativeDensity.set(i, cumulativeDensity.get(i) + cumulativeDensity.get(i - 1));
                excessProbability.set(i, 1.0 - cumulativeDensity.get(i - 1));
                cumulativeExcessProbability.set(i,
                        excessProbability.get(i - 1) * dx.get(i - 1) + cumulativeExcessProbability.get(i - 1));
            }
        }
        isNormalized = true;
    }

    public double confidenceLevel(final double quantil) {
        normalize();
        for ( int i = 0; i < size; i++ ) {
            if ( cumulativeDensity.get(i) > quantil ) {
                return x.get(i) + dx.get(i);
            }
        }
        return x.get(size - 1) + dx.get(size - 1);
    }

    public double cumulativeDensity(final double v) {
        final double tiny = dx.get(size - 1) * 1.0e-3;
        QL.require(v > 0, "x must be positive");
        normalize();
        for ( int i = 0; i < size; i++ ) {
            if ( x.get(i) + dx.get(i) + tiny >= v ) {
                return ((v - x.get(i)) * cumulativeDensity.get(i) + (x.get(i) + dx.get(i) - v) * cumulativeDensity.get(
                        i - 1)) / dx.get(i);
            }
        }
        throw new org.jquantlib.lang.exceptions.LibraryException(
                "x = " + v + " beyond distribution cutoff " + (x.get(size - 1) + dx.get(size - 1)));
    }

    // -------- Inspectors --------

    public double cumulativeExcessProbability(final double a, final double b) {
        normalize();
        QL.require(b <= xmax, "end of interval " + b + " out of range [" + xmin + ", " + xmax + "]");
        QL.require(a >= xmin, "start of interval " + a + " out of range [" + xmin + ", " + xmax + "]");
        final int i = locate(a);
        final int j = locate(b);
        return cumulativeExcessProbability.get(j) - cumulativeExcessProbability.get(i);
    }

    public double expectedValue() {
        normalize();
        double expected = 0;
        for ( int i = 0; i < size; i++ ) {
            final double xi = x.get(i) + dx.get(i) / 2;
            expected += xi * dx.get(i) * density.get(i);
        }
        return expected;
    }

    public double trancheExpectedValue(final double a, final double d) {
        normalize();
        double expected = 0;
        for ( int i = 0; i < size; i++ ) {
            final double xi = x.get(i) + dx.get(i) / 2;
            if ( xi < a ) {
                continue;
            }
            if ( xi > d ) {
                break;
            }
            expected += (xi - a) * dx.get(i) * density.get(i);
        }
        expected += (d - a) * (1.0 - cumulativeDensity(d));
        return expected;
    }

    public double expectedShortfall(final double percValue) {
        QL.require(percValue >= 0.0 && percValue <= 1.0, "Incorrect percentile");
        normalize();
        double expected = 0;
        final int iVal = locate(confidenceLevel(percValue));
        if ( iVal == size - 1 ) {
            return x.get(size - 1);
        }
        for ( int i = iVal; i < size; i++ ) {
            expected += x.get(i) * (cumulativeDensity.get(i) - cumulativeDensity.get(i - 1));
        }
        return expected / (1.0 - cumulativeDensity.get(iVal));
    }

    /**
     * Transform the loss distribution into the tranche loss distribution for losses {@code L_T = min(L,d) - min(L,a)}.
     *
     * <p><strong>Note:</strong> dangerous to perform calls to members
     * after this; the C++ source recommends transform-and-clone, but none of the existing callers do that.
     */
    public void tranche(final double attachmentPoint, final double detachmentPoint) {
        QL.require(attachmentPoint < detachmentPoint, "attachment >= detachment point");
        QL.require(
                x.get(x.size() - 1) > attachmentPoint && x.get(x.size() - 1) + dx.get(dx.size() - 1) >= detachmentPoint,
                "attachment or detachment too large");
        normalize();

        // shift: erase leading buckets below attachment
        while ( !x.isEmpty() && x.get(0) < attachmentPoint ) {
            x.remove(0);
            dx.remove(0);
            count.remove(0);
            density.remove(0);
            cumulativeDensity.remove(0);
            excessProbability.remove(0);
        }

        // remove losses past detachment
        int detachIdx = -1;
        for ( int i = 0; i < x.size(); i++ ) {
            if ( x.get(i) > detachmentPoint ) {
                detachIdx = i;
                break;
            }
        }
        if ( detachIdx != -1 && detachIdx + 1 < x.size() ) {
            // erase from detachIdx+1 to end
            while ( x.size() > detachIdx + 1 ) {
                x.remove(x.size() - 1);
            }
        }

        size = x.size();
        // truncate cumulativeDensity / count / dx to size
        while ( cumulativeDensity.size() > size ) {
            cumulativeDensity.remove(cumulativeDensity.size() - 1);
        }
        cumulativeDensity.set(size - 1, 1.0);
        while ( count.size() > size ) {
            count.remove(count.size() - 1);
        }
        while ( dx.size() > size ) {
            dx.remove(dx.size() - 1);
        }

        // truncate x values into [0, d-a]
        for ( int i = 0; i < x.size(); i++ ) {
            x.set(i, Math.min(Math.max(x.get(i) - attachmentPoint, 0.0), detachmentPoint - attachmentPoint));
        }

        density.clear();
        excessProbability.clear();
        cumulativeExcessProbability.clear();
        density.add((cumulativeDensity.get(0) - 0.0) / dx.get(0));
        excessProbability.add(1.0);
        for ( int i = 1; i < size - 1; i++ ) {
            excessProbability.add(1.0 - cumulativeDensity.get(i - 1));
            density.add((cumulativeDensity.get(i) - cumulativeDensity.get(i - 1)) / dx.get(i));
        }
        excessProbability.add(1.0 - cumulativeDensity.get(cumulativeDensity.size() - 1));
        density.add((1.0 - cumulativeDensity.get(cumulativeDensity.size() - 1)) / dx.get(dx.size() - 1));
    }

    public int size() {
        return size;
    }

    public double x(final int k) {
        return x.get(k);
    }

    public List< Double > xList() {
        return x;
    }

    public double dx(final int k) {
        return dx.get(k);
    }

    public List< Double > dxList() {
        return dx;
    }

    public double density(final int k) {
        normalize();
        return density.get(k);
    }

    public double cumulative(final int k) {
        normalize();
        return cumulativeDensity.get(k);
    }

    public double excess(final int k) {
        normalize();
        return excessProbability.get(k);
    }

    public double cumulativeExcess(final int k) {
        normalize();
        return cumulativeExcessProbability.get(k);
    }

    public double average(final int k) {
        return average.get(k);
    }

    /** Convenience static helper {@code ManipulateDistribution.convolve}. */
    public static class ManipulateDistribution {
        private ManipulateDistribution() {
        }

        public static Distribution convolve(final Distribution d1, final Distribution d2) {
            return Distribution.convolve(d1, d2);
        }
    }
}
