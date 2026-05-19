/*
 Copyright (C) 2012, 2013 Grzegorz Andruszkiewicz
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
*/

package org.jquantlib.experimental.catbonds;

import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.math.randomnumbers.MersenneTwisterUniformRng;
import org.jquantlib.time.Date;

import java.util.List;
import java.util.Random;

/**
 * Monte-Carlo simulation of catastrophe losses using a compound Poisson / Beta distribution model.
 *
 * <p>Port of {@code ql/experimental/catbonds/catrisk.hpp/.cpp}
 * {@code BetaRiskSimulation}.
 *
 * <p>The C++ implementation uses {@code std::mt19937} together with
 * {@code std::exponential_distribution} and {@code std::gamma_distribution}. Java's {@link java.util.Random} does not
 * supply these directly; we implement them using standard transformations:
 * <ul>
 *   <li>Exponential(lambda): {@code -ln(U)/lambda}
 *   <li>Gamma(alpha): Marsaglia-Tsang method
 * </ul>
 *
 * <p><b>RNG sourcing (Phase 5e.5b-CFC-d-300):</b> the seedable ctor uses
 * {@link MersenneTwisterUniformRng} (same algorithm as C++ {@code std::mt19937})
 * and a Box-Muller pair on top of the MT uniforms for Gaussian draws. This
 * makes the simulation deterministic and aligned with C++ for the *underlying*
 * uniform stream, though the gamma/exponential distributions are not
 * bit-exact with libstdc++/libc++ (different rejection-region constants and
 * Box-Muller vs Ziggurat for gaussian), so the empirical moments still
 * differ a few percent from C++ at any finite sample size — within the C++
 * libc++ tolerance tier (5% mean / 10% variance).
 *
 * <p>The legacy no-seed ctor still uses {@link java.util.Random} (time-seeded)
 * for backward compatibility with existing call sites
 * ({@code BetaRisk.newSimulation} with no seed; tests that rely on legacy
 * behavior).
 */
public class BetaRiskSimulation extends CatSimulation {

    private final double maxLoss_;
    private final int dayCount_;
    private final double yearFraction_;

    // Poisson inter-arrival rate (lambda for exponential)
    private final double lambda_;
    // Beta shape parameters
    private final double alpha_;
    private final double betaParam_;

    // Legacy time-seeded path: java.util.Random.  Non-null iff no MT.
    private final Random rng_;
    // Deterministic-seed path: MT19937.  Non-null iff seedable ctor used.
    private final MersenneTwisterUniformRng mt_;
    // Box-Muller state for the MT path: holds the second of a pair.
    private boolean haveNextGaussian_ = false;
    private double nextGaussian_ = 0.0;

    public BetaRiskSimulation(final Date start, final Date end, final double maxLoss, final double lambda,
            final double alpha, final double beta) {

        super(start, end);
        this.maxLoss_ = maxLoss;
        this.lambda_ = lambda;
        this.alpha_ = alpha;
        this.betaParam_ = beta;
        this.rng_ = new Random();
        this.mt_ = null;

        final ActualActual dayCounter = new ActualActual(ActualActual.Convention.ISDA);
        this.dayCount_ = (int) dayCounter.dayCount(start, end);
        this.yearFraction_ = dayCounter.yearFraction(start, end);
    }

    /**
     * Deterministic, MT19937-backed constructor.
     *
     * <p>Phase 5e.5b-CFC-d-300: added to support {@code testBetaRisk}, which
     * cannot run reliably on the time-seeded {@link java.util.Random} path
     * because the test asserts QL_CHECK_CLOSE on the empirical moments at
     * the C++ libc++ tolerance tier (5%/10%).
     *
     * @param seed MT19937 seed (32 bits used, matching C++ {@code unsigned long})
     */
    public BetaRiskSimulation(final Date start, final Date end, final double maxLoss, final double lambda,
            final double alpha, final double beta, final long seed) {

        super(start, end);
        this.maxLoss_ = maxLoss;
        this.lambda_ = lambda;
        this.alpha_ = alpha;
        this.betaParam_ = beta;
        this.rng_ = null;
        this.mt_ = new MersenneTwisterUniformRng(seed);

        final ActualActual dayCounter = new ActualActual(ActualActual.Convention.ISDA);
        this.dayCount_ = (int) dayCounter.dayCount(start, end);
        this.yearFraction_ = dayCounter.yearFraction(start, end);
    }

    /** Generate a Beta(alpha_, betaParam_)-distributed loss in [0, maxLoss_]. */
    public double generateBeta() {
        final double x = sampleGamma(alpha_);
        final double y = sampleGamma(betaParam_);
        return x * maxLoss_ / (x + y);
    }

    @Override
    public boolean nextPath(final List< DateRealPair > path) {
        path.clear();
        double eventFraction = sampleExponential(lambda_);
        while ( eventFraction <= yearFraction_ ) {
            final int days = (int) Math.round(eventFraction * dayCount_ / yearFraction_);
            final Date eventDate = start_.add(days);
            if ( !eventDate.gt(end_) ) {
                path.add(new DateRealPair(eventDate, generateBeta()));
            } else {
                break;
            }
            eventFraction = sampleExponential(lambda_);
        }
        return true;
    }

    // --- private helpers ---

    private double nextUniform() {
        if ( mt_ != null ) {
            return mt_.next().value();
        }
        return rng_.nextDouble();
    }

    private double sampleExponential(final double rate) {
        return -Math.log(nextUniform()) / rate;
    }

    /**
     * Sample from Gamma(shape) using the Marsaglia-Tsang (2000) method. Valid for shape >= 1; for shape < 1, use the
     * scaling: X ~ Gamma(shape+1) * U^(1/shape).
     */
    private double sampleGamma(double shape) {
        if ( shape < 1.0 ) {
            return sampleGamma(shape + 1.0) * Math.pow(nextUniform(), 1.0 / shape);
        }
        final double d = shape - 1.0 / 3.0;
        final double c = 1.0 / Math.sqrt(9.0 * d);
        while ( true ) {
            double x, v;
            do {
                x = nextGaussian();
                v = 1.0 + c * x;
            } while ( v <= 0.0 );
            v = v * v * v;
            final double u = nextUniform();
            if ( u < 1.0 - 0.0331 * (x * x) * (x * x) ) {
                return d * v;
            }
            if ( Math.log(u) < 0.5 * x * x + d * (1.0 - v + Math.log(v)) ) {
                return d * v;
            }
        }
    }

    private double nextGaussian() {
        if ( mt_ == null ) {
            // legacy path: java.util.Random's polar/Box-Muller
            return rng_.nextGaussian();
        }
        // MT path: polar Box-Muller producing a pair of standard normals.
        // Cache the second of each pair so consumption matches the
        // distributional density (one uniform pair → two normals).
        if ( haveNextGaussian_ ) {
            haveNextGaussian_ = false;
            return nextGaussian_;
        }
        double s, v1, v2;
        do {
            v1 = 2.0 * nextUniform() - 1.0;
            v2 = 2.0 * nextUniform() - 1.0;
            s = v1 * v1 + v2 * v2;
        } while ( s >= 1.0 || s == 0.0 );
        final double mult = Math.sqrt(-2.0 * Math.log(s) / s);
        nextGaussian_ = v2 * mult;
        haveNextGaussian_ = true;
        return v1 * mult;
    }
}
