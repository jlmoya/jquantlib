/*
 Copyright (C) 2012, 2013 Grzegorz Andruszkiewicz
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
*/

package org.jquantlib.experimental.catbonds;

import org.jquantlib.QL;
import org.jquantlib.time.Date;

/**
 * Catastrophe risk based on a compound Poisson / Beta-distribution model.
 *
 * <p>Port of {@code ql/experimental/catbonds/catrisk.hpp/.cpp} {@code BetaRisk}.
 *
 * @param maxLoss maximum loss per event
 * @param years   mean years between events (1/lambda)
 * @param mean    mean loss per event
 * @param stdDev  standard deviation of loss per event
 */
public class BetaRisk extends CatRisk {

    /** Sentinel: no deterministic seed; use the time-seeded legacy RNG path. */
    private static final long NO_SEED = Long.MIN_VALUE;

    private final double maxLoss_;
    private final double lambda_;
    private final double alpha_;
    private final double beta_;
    private final long seed_;

    public BetaRisk(final double maxLoss, final double years, final double mean, final double stdDev) {
        this(maxLoss, years, mean, stdDev, NO_SEED);
    }

    /**
     * Deterministic-seed constructor.
     *
     * <p>Phase 5e.5b-CFC-d-300: forwards {@code seed} to each
     * {@link BetaRiskSimulation} returned by {@link #newSimulation}, so
     * Monte-Carlo callers (notably {@code testBetaRisk}) can exercise a
     * reproducible sample stream.
     *
     * @param seed MT19937 seed for the underlying simulation
     */
    public BetaRisk(final double maxLoss, final double years, final double mean, final double stdDev,
            final long seed) {

        QL.require(mean < maxLoss,
                "Mean " + mean + " of the loss distribution must be less than the maximum loss " + maxLoss);

        this.maxLoss_ = maxLoss;
        this.lambda_ = 1.0 / years;
        this.seed_ = seed;

        final double normalizedMean = mean / maxLoss;
        final double normalizedVar = stdDev * stdDev / (maxLoss * maxLoss);

        QL.require(normalizedVar < normalizedMean * (1.0 - normalizedMean),
                "Standard deviation of " + stdDev + " is impossible to achieve in gamma distribution with mean "
                        + mean);

        final double nu = normalizedMean * (1.0 - normalizedMean) / normalizedVar - 1.0;
        this.alpha_ = normalizedMean * nu;
        this.beta_ = (1.0 - normalizedMean) * nu;
    }

    @Override
    public CatSimulation newSimulation(final Date start, final Date end) {
        if ( seed_ == NO_SEED ) {
            return new BetaRiskSimulation(start, end, maxLoss_, lambda_, alpha_, beta_);
        }
        return new BetaRiskSimulation(start, end, maxLoss_, lambda_, alpha_, beta_, seed_);
    }
}
