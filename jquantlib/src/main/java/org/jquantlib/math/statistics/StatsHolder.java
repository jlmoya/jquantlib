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

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.math.statistics;

/**
 * Lightweight (mean, standardDeviation) record satisfying the small
 * Statistics-like interface used by {@code GenericGaussianStatistics<Stat>}
 * when only mean/sigma are known up front (no underlying sample stream).
 *
 * <p>Faithful Java port of {@code QuantLib::StatsHolder}
 * (v1.42.1 {@code ql/math/statistics/gaussianstatistics.hpp}, pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}).
 *
 * <p>The class is intentionally minimal — it only exposes {@code mean()} and
 * {@code standardDeviation()}, matching the C++ struct exactly. Used to wrap
 * precomputed statistics for the GaussianStatistics template machinery.
 *
 * <p>Phase 2 L1-D port.
 */
public class StatsHolder {

    private final double mean_;
    private final double standardDeviation_;

    public StatsHolder(final double mean, final double standardDeviation) {
        this.mean_ = mean;
        this.standardDeviation_ = standardDeviation;
    }

    public double mean() {
        return mean_;
    }

    public double standardDeviation() {
        return standardDeviation_;
    }
}
