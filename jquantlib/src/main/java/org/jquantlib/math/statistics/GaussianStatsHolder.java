/*
 Copyright (C) 2026 JQuantLib migration contributors.

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

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/
package org.jquantlib.math.statistics;

import org.jquantlib.QL;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.distributions.NormalDistribution;

/**
 * Java analogue of the C++ {@code GenericGaussianStatistics<StatsHolder>}
 * instantiation used in v1.42.1 {@code test-suite/riskstats.cpp} (lines 343-351).
 *
 * <p>C++ has a single class template {@code GenericGaussianStatistics<Stat>}
 * that only needs {@code mean()} and {@code standardDeviation()} from
 * {@code Stat}; the {@code StatsHolder} variant exposes only those two
 * accessors over precomputed values (no underlying sample stream).
 *
 * <p>Java's {@link GenericGaussianStatistics} is {@code abstract} and extends
 * {@link GeneralStatistics} (carrying a samples list), so it cannot be
 * directly instantiated with a {@link StatsHolder}. This class fills that gap
 * by reimplementing the {@code gaussian*} risk measures on a fixed
 * {@code (mean, sigma)} pair -- a faithful Java port of the
 * {@code GenericGaussianStatistics<StatsHolder>} typedef behaviour.
 *
 * <p>Formulas are copied verbatim from
 * {@code ql/math/statistics/gaussianstatistics.hpp} so the same numerics apply
 * (no probe needed -- {@code mean()/standardDeviation()} are the only inputs).
 *
 * <p>Phase 2 L6-A {@code align(math.statistics)} follow-up.
 */
public class GaussianStatsHolder extends StatsHolder {

    public GaussianStatsHolder(final double mean, final double standardDeviation) {
        super(mean, standardDeviation);
    }

    /**
     * Convenience copy-constructor mirroring the C++ {@code GenericGaussianStatistics(const Stat&)}.
     */
    public GaussianStatsHolder(final StatsHolder s) {
        super(s.mean(), s.standardDeviation());
    }

    public /*@Real*/ double gaussianDownsideVariance() /* @ReadOnly */ {
        return gaussianRegret(0.0);
    }

    public /*@Real*/ double gaussianDownsideDeviation() /* @ReadOnly */ {
        return Math.sqrt(gaussianDownsideVariance());
    }

    public /*@Real*/ double gaussianRegret(/*@Real*/ final double target) /* @ReadOnly */ {
        final double m = this.mean();
        final double std = this.standardDeviation();
        final double variance = std * std;
        final CumulativeNormalDistribution gIntegral = new CumulativeNormalDistribution(m, std);
        final NormalDistribution g = new NormalDistribution(m, std);
        final double firstTerm = variance + m * m - 2.0 * target * m + target * target;
        final double alfa = gIntegral.op(target);
        final double secondTerm = m - target;
        final double beta = variance * g.op(target);
        final double result = alfa * firstTerm - beta * secondTerm;
        return result / alfa;
    }

    public /*@Real*/ double gaussianPercentile(/*@Real*/ final double percentile) /* @ReadOnly */ {
        QL.require(percentile > 0.0, "percentile must be > 0.0");
        QL.require(percentile < 1.0, "percentile must be < 1.0");
        final InverseCumulativeNormal gInverse = new InverseCumulativeNormal(mean(), standardDeviation());
        return gInverse.op(percentile);
    }

    public /*@Real*/ double gaussianTopPercentile(/*@Real*/ final double percentile) /* @ReadOnly */ {
        return gaussianPercentile(1.0 - percentile);
    }

    public /*@Real*/ double gaussianPotentialUpside(/*@Real*/ final double percentile) /* @ReadOnly */ {
        QL.require(percentile < 1.0 && percentile >= 0.9, "percentile is out of range [0.9, 1)");
        final double result = gaussianPercentile(percentile);
        return Math.max(result, 0.0);
    }

    public /*@Real*/ double gaussianValueAtRisk(/*@Real*/ final double percentile) /* @ReadOnly */ {
        QL.require(percentile < 1.0 && percentile >= 0.9, "percentile is out of range [0.9, 1)");
        final double result = gaussianPercentile(1.0 - percentile);
        return -Math.min(result, 0.0);
    }

    public /*@Real*/ double gaussianExpectedShortfall(/*@Real*/ final double percentile) /* @ReadOnly */ {
        QL.require(percentile < 1.0 && percentile >= 0.9, "percentile is out of range [0.9, 1)");
        final double m = this.mean();
        final double std = this.standardDeviation();
        final InverseCumulativeNormal gInverse = new InverseCumulativeNormal(m, std);
        final double var = gInverse.op(1.0 - percentile);
        final NormalDistribution g = new NormalDistribution(m, std);
        final double result = m - std * std * g.op(var) / (1.0 - percentile);
        return -Math.min(result, 0.0);
    }

    public /*@Real*/ double gaussianShortfall(/*@Real*/ final double target) /* @ReadOnly */ {
        final CumulativeNormalDistribution gIntegral = new CumulativeNormalDistribution(mean(), standardDeviation());
        return gIntegral.op(target);
    }

    public /*@Real*/ double gaussianAverageShortfall(/*@Real*/ final double target) /* @ReadOnly */ {
        final double m = mean();
        final double std = standardDeviation();
        final CumulativeNormalDistribution gIntegral = new CumulativeNormalDistribution(m, std);
        final NormalDistribution g = new NormalDistribution(m, std);
        return ((target - m) + std * std * g.op(target) / gIntegral.op(target));
    }
}
