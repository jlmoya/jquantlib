/*
 Copyright (C) 2012, 2013 Grzegorz Andruszkiewicz
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
*/

package org.jquantlib.experimental.catbonds;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.time.Date;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.Period;

/**
 * Monte-Carlo simulation of catastrophe losses using a compound Poisson /
 * Beta distribution model.
 *
 * <p>Port of {@code ql/experimental/catbonds/catrisk.hpp/.cpp}
 * {@code BetaRiskSimulation}.
 *
 * <p>The C++ implementation uses {@code std::mt19937} together with
 * {@code std::exponential_distribution} and {@code std::gamma_distribution}.
 * Java's {@link java.util.Random} does not supply these directly; we implement
 * them using standard transformations:
 * <ul>
 *   <li>Exponential(lambda): {@code -ln(U)/lambda}
 *   <li>Gamma(alpha): Marsaglia-Tsang method
 * </ul>
 */
public class BetaRiskSimulation extends CatSimulation {

    private final double maxLoss_;
    private final int    dayCount_;
    private final double yearFraction_;

    // Poisson inter-arrival rate (lambda for exponential)
    private final double lambda_;
    // Beta shape parameters
    private final double alpha_;
    private final double betaParam_;

    private final Random rng_ = new Random();

    public BetaRiskSimulation(
            final Date start,
            final Date end,
            final double maxLoss,
            final double lambda,
            final double alpha,
            final double beta) {

        super(start, end);
        this.maxLoss_   = maxLoss;
        this.lambda_    = lambda;
        this.alpha_     = alpha;
        this.betaParam_ = beta;

        final ActualActual dayCounter = new ActualActual(ActualActual.Convention.ISDA);
        this.dayCount_      = (int) dayCounter.dayCount(start, end);
        this.yearFraction_  = dayCounter.yearFraction(start, end);
    }

    /** Generate a Beta(alpha_, betaParam_)-distributed loss in [0, maxLoss_]. */
    public double generateBeta() {
        final double x = sampleGamma(alpha_);
        final double y = sampleGamma(betaParam_);
        return x * maxLoss_ / (x + y);
    }

    @Override
    public boolean nextPath(final List<DateRealPair> path) {
        path.clear();
        double eventFraction = sampleExponential(lambda_);
        while (eventFraction <= yearFraction_) {
            final int days = (int) Math.round(eventFraction * dayCount_ / yearFraction_);
            final Date eventDate = start_.add(days);
            if (!eventDate.gt(end_)) {
                path.add(new DateRealPair(eventDate, generateBeta()));
            } else {
                break;
            }
            eventFraction = sampleExponential(lambda_);
        }
        return true;
    }

    // --- private helpers ---

    private double sampleExponential(final double rate) {
        return -Math.log(rng_.nextDouble()) / rate;
    }

    /**
     * Sample from Gamma(shape) using the Marsaglia-Tsang (2000) method.
     * Valid for shape >= 1; for shape < 1, use the scaling: X ~ Gamma(shape+1) * U^(1/shape).
     */
    private double sampleGamma(double shape) {
        if (shape < 1.0) {
            return sampleGamma(shape + 1.0) * Math.pow(rng_.nextDouble(), 1.0 / shape);
        }
        final double d = shape - 1.0 / 3.0;
        final double c = 1.0 / Math.sqrt(9.0 * d);
        while (true) {
            double x, v;
            do {
                x = nextGaussian();
                v = 1.0 + c * x;
            } while (v <= 0.0);
            v = v * v * v;
            final double u = rng_.nextDouble();
            if (u < 1.0 - 0.0331 * (x * x) * (x * x)) {
                return d * v;
            }
            if (Math.log(u) < 0.5 * x * x + d * (1.0 - v + Math.log(v))) {
                return d * v;
            }
        }
    }

    private double nextGaussian() {
        // Box-Muller transform using a single pair
        return rng_.nextGaussian();
    }
}
