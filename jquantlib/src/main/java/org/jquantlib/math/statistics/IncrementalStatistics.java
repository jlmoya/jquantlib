/*
 Copyright (C) 2009 Ueli Hofstetter

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
 Copyright (C) 2003 Ferdinando Ametrano
 Copyright (C) 2000, 2001, 2002, 2003 RiskMap srl
 Copyright (C) 2015 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <http://quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
*/

package org.jquantlib.math.statistics;

import org.jquantlib.QL;
import org.jquantlib.lang.annotation.QualityAssurance;
import org.jquantlib.lang.annotation.QualityAssurance.Quality;
import org.jquantlib.lang.annotation.QualityAssurance.Version;
import org.jquantlib.math.Constants;
import org.jquantlib.math.matrixutilities.Array;


/**
 * Statistics tool based on incremental accumulation
 * <p>
 * It can accumulate a set of data and return statistics (e.g: mean,
 * variance, skewness, kurtosis, error estimation, etc.)
 * <p>
 * Java port of QuantLib v1.42.1 {@code ql/math/statistics/incrementalstatistics.{hpp,cpp}},
 * which wraps {@code boost::accumulators} (post-QL-1.7 rewrite). Internally uses
 * the Welford / Chan-Golub-Levesque / P&eacute;bay weighted online moment
 * recurrences, which are numerically stable for high-mean/low-variance inputs
 * (e.g. {@code mu=1e8, sigma=0.1}) where the pre-QL-1.7 naive
 * {@code <x^2>/W - <x>^2} accumulator catastrophically cancels.
 * <p>
 * The central second/third/fourth weighted moments {@code M2_, M3_, M4_} below
 * are weighted sums of {@code w*(x-mean)^k}; dividing by the weight sum yields
 * the central moments {@code m_k}. The update equations follow
 * P&eacute;bay (2008) and match the {@code boost::accumulators::tag::weighted_*}
 * implementation used by C++ QuantLib v1.42.1.
 *
 * @author Ueli Hofstetter
 * @author Richard Gomes
 * @author JQuantLib migration (Phase 5e.5b-CFC-d-223: Welford online moments)
 */
@QualityAssurance(quality = Quality.Q4_UNIT, reviewers = { "Richard Gomes" }, version = Version.V097)
public class IncrementalStatistics extends GenericRiskStatistics {

    private static final String UNSUFFICIENT_SAMPLE_WEIGHT    = "sampleWeight_=0, unsufficient";
    private static final String UNSUFFICIENT_SAMPLE_NUMBER    = "sample number <=1, unsufficient";
    private static final String UNSUFFICIENT_SAMPLE_NUMBER_2  = "sample number <=2, unsufficient";
    private static final String UNSUFFICIENT_SAMPLE_NUMBER_3  = "sample number <=3, unsufficient";
    private static final String EMPTY_SAMPLE_SET              = "empty sample set";
    private static final String MAX_NUMBER_OF_SAMPLES_REACHED = "maximum number of samples reached";
    private static final String INCOMPATIBLE_ARRAY_SIZES      = "incompatible array sizes";


    protected /*@Size*/ int sampleNumber_;
    protected /*@Size*/ int downsideSampleNumber_;
    protected /*@Real*/ double sampleWeight_, downsideSampleWeight_;

    /**
     * Running weighted mean. Updated incrementally per
     * {@code M_new = M_old + (w/W_new) * (x - M_old)}.
     */
    protected /*@Real*/ double mean_;

    /**
     * Weighted sum of squared deviations from the running mean:
     * {@code M2_ = sum_i w_i * (x_i - mean)^2}. Divide by
     * {@link #sampleWeight_} to obtain the (biased) central second moment.
     */
    protected /*@Real*/ double M2_;

    /**
     * Weighted sum of cubed deviations from the running mean:
     * {@code M3_ = sum_i w_i * (x_i - mean)^3}.
     */
    protected /*@Real*/ double M3_;

    /**
     * Weighted sum of fourth-power deviations from the running mean:
     * {@code M4_ = sum_i w_i * (x_i - mean)^4}.
     */
    protected /*@Real*/ double M4_;

    /**
     * Weighted sum of squared values for negative samples ({@code x < 0}),
     * matching {@code boost::accumulators::tag::moment<2>} on the C++ side:
     * {@code downsideQuadraticSum_ = sum_{x_i<0} w_i * x_i^2}. Note this is
     * the <em>raw</em> second moment for downside (not centered), per the C++
     * downside-accumulator semantics.
     */
    protected /*@Real*/ double downsideQuadraticSum_;

    protected /*@Real*/ double min_, max_;


    public IncrementalStatistics() {
    	super();
        reset();
    }


    //
    // public methods
    //

    /**
     * number of samples collected
     */
    @Override
    public /*@Size*/ int samples() /*@ReadOnly*/ {
        return sampleNumber_;
    }

    /**
     * sum of data weights
     */
    @Override
    public /*@Real*/ double weightSum() /*@ReadOnly*/ {
        return sampleWeight_;
    }

    /**
     * returns the mean, defined as
     * {@latex[ \langle x \rangle = \frac{\sum w_i x_i}{\sum w_i}. }
     */
    @Override
    public /*@Real*/ double mean() /*@ReadOnly*/ {
        QL.require(sampleWeight_>0.0, UNSUFFICIENT_SAMPLE_WEIGHT);
        return mean_;
    }

    /**
     * returns the variance, defined as
     * {@latex[ \frac{N}{N-1} \left\langle \left(
     *      x-\langle x \rangle \right)^2 \right\rangle. }
     * <p>
     * Computed via the Welford recurrence as {@code N/(N-1) * (M2_/W)},
     * which is numerically stable for high-mean/low-variance fixtures
     * (no catastrophic cancellation).
     */
    @Override
    public /*@Real*/ double variance() /*@ReadOnly*/ {
        QL.require(sampleWeight_>0.0, UNSUFFICIENT_SAMPLE_WEIGHT);
        QL.require(sampleNumber_>1, UNSUFFICIENT_SAMPLE_NUMBER);

        final /*@Real*/ double n = (double) sampleNumber_;
        // Biased central second moment m_2 = M2_/W; sample variance = N/(N-1)*m_2.
        return (n / (n - 1.0)) * (M2_ / sampleWeight_);
    }


    /**
     * returns the standard deviation {@latex$ \sigma }, defined as the
     * square root of the variance.
     */
    @Override
    public /*@Real*/ double standardDeviation() /*@ReadOnly*/ {
        return Math.sqrt(variance());
    }


    /**
     * returns the error estimate {@latex$ \epsilon }, defined as the
     * square root of the ratio of the variance to the number of
     * samples.
     */
    @Override
    public /*@Real*/ double errorEstimate() /*@ReadOnly*/ {
        /*@Real*/ double var = variance();
        QL.require(samples() > 0, EMPTY_SAMPLE_SET);
        return Math.sqrt(var/samples());
    }

    /**
     * returns the downside deviation, defined as the
     * square root of the downside variance.
     */
    @Override
    public /*@Real*/ double downsideDeviation() /*@ReadOnly*/ {
        return Math.sqrt(downsideVariance());
    }

    /**
     * returns the downside variance, defined as
     * {@latex[ \frac{N}{N-1} \times \frac{ \sum_{i=1}^{N}
     *      \theta \times x_i^{2}}{ \sum_{i=1}^{N} w_i} },
     *  where {@latex$ \theta } = 0 if x > 0 and
     *  {@latex$ \theta } =1 if x <0
     */
    @Override
    public /*@Real*/ double downsideVariance() /*@ReadOnly*/ {
        if (downsideSampleWeight_==0.0) {
            QL.require(sampleWeight_>0.0, UNSUFFICIENT_SAMPLE_WEIGHT);
            return 0.0;
        }

        QL.require(downsideSampleNumber_>1, "sample number below zero <=1, unsufficient");

        return (downsideSampleNumber_/(downsideSampleNumber_-1.0))*
            (downsideQuadraticSum_ /downsideSampleWeight_);
    }

    /**
     * returns the skewness, defined as
     * {@latex[ \frac{N^2}{(N-1)(N-2)} \frac{\left\langle \left(
     *    x-\langle x \rangle \right)^3 \right\rangle}{\sigma^3}. }
     *  The above evaluates to 0 for a Gaussian distribution.
     * <p>
     * Computed from the Welford central moments as
     * {@code sqrt(r1*r2) * (M3_/W) / (M2_/W)^(3/2)} where
     * {@code r1 = n/(n-2), r2 = (n-1)/(n-2)} — matches the C++ boost
     * weighted_skewness wrapped by QuantLib's {@code skewness()}.
     */
    @Override
    public /*@Real*/ double skewness() /*@ReadOnly*/ {
        QL.require(sampleNumber_>2, UNSUFFICIENT_SAMPLE_NUMBER_2);

        final /*@Real*/ double m2 = M2_ / sampleWeight_;
        if (m2 == 0.0) return 0.0;
        final /*@Real*/ double m3 = M3_ / sampleWeight_;
        // boost weighted_skewness == m3 / m2^(3/2)
        final /*@Real*/ double rawSkew = m3 / (m2 * Math.sqrt(m2));

        final /*@Real*/ double n = (double) sampleNumber_;
        final /*@Real*/ double r1 = n / (n - 2.0);
        final /*@Real*/ double r2 = (n - 1.0) / (n - 2.0);
        return Math.sqrt(r1 * r2) * rawSkew;
    }


    /**
     * returns the excess kurtosis, defined as
     * {@latex[ \frac{N^2(N+1)}{(N-1)(N-2)(N-3)}
     *      \frac{\left\langle \left(x-\langle x \rangle \right)^4
     *      \right\rangle}{\sigma^4} - \frac{3(N-1)^2}{(N-2)(N-3)}. }
     *  The above evaluates to 0 for a Gaussian distribution.
     * <p>
     * Computed from the Welford central moments. boost weighted_kurtosis
     * returns {@code m4/m2^2 - 3} (excess); QuantLib then applies the
     * finite-sample correction {@code ((3 + raw)*r2 - 3*r3)*r1}.
     */
    @Override
    public /*@Real*/ double kurtosis() /*@ReadOnly*/ {
        QL.require(sampleNumber_>3, UNSUFFICIENT_SAMPLE_NUMBER_3);

        final /*@Real*/ double n = (double) sampleNumber_;
        /*@Real*/ double c = (n - 1.0) / (n - 2.0);
        c *= (n - 1.0) / (n - 3.0);
        c *= 3.0;

        final /*@Real*/ double m2 = M2_ / sampleWeight_;
        // Pre-existing JQuantLib v==0 guard (the C++ post-1.7 path does not
        // have this guard; preserved here to avoid behavior drift in callers
        // that may rely on it on degenerate inputs).
        if (m2 == 0.0) return c;
        final /*@Real*/ double m4 = M4_ / sampleWeight_;
        // boost weighted_kurtosis == m4/m2^2 - 3 (excess)
        final /*@Real*/ double rawExcessKurt = (m4 / (m2 * m2)) - 3.0;

        final /*@Real*/ double r1 = (n - 1.0) / (n - 2.0);
        final /*@Real*/ double r2 = (n + 1.0) / (n - 3.0);
        final /*@Real*/ double r3 = (n - 1.0) / (n - 3.0);
        return ((3.0 + rawExcessKurt) * r2 - 3.0 * r3) * r1;
    }

    /**
     * returns the minimum sample value
     */
    @Override
    public /*@Real*/ double min() /*@ReadOnly*/ {
        QL.require(samples() > 0, EMPTY_SAMPLE_SET);
        return min_;
    }


    /**
     * returns the maximum sample value
     */
    @Override
    public /*@Real*/ double max() /*@ReadOnly*/ {
        QL.require(samples() > 0, EMPTY_SAMPLE_SET);
        return max_;
    }

    /**
     * adds a sequence of data to the set, with default weight
     */
    @Override
    public void addSequence(final double[] datum) {
	    for (int i=0; i<datum.length; i++) {
	    	add(datum[i]);
	    }
    }

    /**
     * adds a sequence of data to the set, each with its weight
     * <p>
     * weights must be positive or null
     */
    @Override
    public void addSequence(final double[] datum, final double[] weights) {
        QL.require(datum.length==weights.length, INCOMPATIBLE_ARRAY_SIZES);
        for (int i=0; i<datum.length; i++) {
        	add(datum[i], weights[i]);
        }
    }

    /**
     * adds a sequence of data to the set, with default weight
     */
    @Override
    public void addSequence(final Array datum) {
	    for (int i=0; i<datum.size(); i++) {
	    	add(datum.get(i));
	    }
    }

    /**
     * adds a sequence of data to the set, each with its weight
     * <p>
     * weights must be positive or null
     */
    @Override
    public void addSequence(final Array datum, final Array weights) {
        QL.require(datum.size()==weights.size(), INCOMPATIBLE_ARRAY_SIZES);
        for (int i=0; i<datum.size(); i++) {
        	add(datum.get(i), weights.get(i));
        }
    }


    /**
     * adds a datum to the set, possibly with a weight
     * <p>
     * weight must be positive or null
     */
    @Override
    public void add(final /*@Real*/ double value) {
    	add(value, 1.0);
    }

    /**
     * Weighted Welford / Chan-Golub-Levesque / P&eacute;bay online update for
     * the first four central moments {@code M1..M4}. Matches the recurrences
     * used by {@code boost::accumulators::tag::weighted_{mean,variance,skewness,
     * kurtosis}} in C++ QuantLib v1.42.1 (numerically stable, no
     * catastrophic cancellation for {@code mu &gt;&gt; sigma}).
     * <p>
     * Reference: P&eacute;bay, Philippe (2008). "Formulas for Robust, One-Pass
     * Parallel Computation of Covariances and Arbitrary-Order Statistical
     * Moments", Sandia National Laboratories SAND2008-6212.
     */
    @Override
    public void add(final /*@Real*/ double value, final /*@Real*/ double weight) {
        QL.require(weight>=0.0, "negative weight not allowed");

        /*@Size*/ final int oldSamples = sampleNumber_;
        sampleNumber_++;
        QL.ensure(sampleNumber_ > oldSamples, MAX_NUMBER_OF_SAMPLES_REACHED);

        // weight==0 contributes nothing to any moment / mean: keep weight sum
        // book-keeping consistent but skip the recurrence (delta_n=0 anyway).
        if (weight > 0.0) {
            final /*@Real*/ double newW = sampleWeight_ + weight;
            final /*@Real*/ double oldW = sampleWeight_;
            sampleWeight_ = newW;

            final /*@Real*/ double delta   = value - mean_;
            final /*@Real*/ double deltaN  = delta * weight / newW;
            final /*@Real*/ double deltaN2 = deltaN * deltaN;
            // term1 = w * delta * (oldW/newW) * delta == delta * deltaN * oldW
            final /*@Real*/ double term1   = delta * deltaN * oldW;

            // 4th central moment update — must be applied BEFORE M3_, M2_, mean_
            // (it reads the old M2_, M3_, mean_).
            // ΔM4 = term1 * deltaN^2 * (newW^2 - 3*newW*w + 3*w^2)/newW (folded into deltaN form)
            //     + 6 * deltaN^2 * M2_ - 4 * deltaN * M3_
            // Pébay (2008) eq. 2.5 with weight folded into deltaN.
            M4_ += term1 * deltaN2 * (newW * newW - 3.0 * newW * weight + 3.0 * weight * weight) / (weight * weight)
                 - 4.0 * deltaN * M3_
                 + 6.0 * deltaN2 * M2_;

            // 3rd central moment update (uses old M2_, mean_).
            // ΔM3 = term1 * deltaN * (newW - 2*w) - 3 * deltaN * M2_
            M3_ += term1 * deltaN * (newW - 2.0 * weight) / weight
                 - 3.0 * deltaN * M2_;

            // 2nd central moment update.
            M2_ += term1;

            // Mean update.
            mean_ += deltaN;
        } else {
            // weight==0: no contribution; sampleWeight_ unchanged.
        }

        if (value < 0.0) {
            // boost downside accumulator uses raw 2nd moment <x^2> for x<0,
            // not central — so keep the naive weighted sum (no cancellation
            // issue: downside variance is bounded below by 0 by construction).
            downsideQuadraticSum_ += weight * value * value;
            downsideSampleNumber_++;
            downsideSampleWeight_ += weight;
        }

        if (oldSamples == 0) {
            min_ = max_ = value;
        } else {
            min_ = Math.min(value, min_);
            max_ = Math.max(value, max_);
        }
    }



    /**
     * resets the data to a null set
     */
    @Override
    public void reset() {
        min_ = Constants.DBL_MAX;
        max_ = Constants.DBL_MIN;
        sampleNumber_ = 0;
        downsideSampleNumber_ = 0;
        sampleWeight_ = 0.0;
        downsideSampleWeight_ = 0.0;
        mean_ = 0.0;
        M2_ = 0.0;
        M3_ = 0.0;
        M4_ = 0.0;
        downsideQuadraticSum_ = 0.0;
    }

}
