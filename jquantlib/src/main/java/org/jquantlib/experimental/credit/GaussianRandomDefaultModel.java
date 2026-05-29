/*
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

/*
 Copyright (C) 2008 Roland Lichters
 Copyright (C) 2009 Jose Aparicio
*/

package org.jquantlib.experimental.credit;

import org.jquantlib.QL;
import org.jquantlib.math.Ops;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.randomnumbers.InverseCumulativeRsg;
import org.jquantlib.math.randomnumbers.MersenneTwisterUniformRng;
import org.jquantlib.math.randomnumbers.RandomSequenceGenerator;
import org.jquantlib.math.solvers1D.Bisection;
import org.jquantlib.math.solvers1D.Brent;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.DefaultProbabilityTermStructure;

import java.util.List;

/**
 * Random default times using a one-factor Gaussian copula.
 *
 * <p>Java port of QuantLib v1.42.1 {@code QuantLib::GaussianRandomDefaultModel}
 * ({@code ql/experimental/credit/randomdefaultmodel.{hpp,cpp}}). Pinned
 * commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>For each name {@code j}, a systemic factor {@code M = values[0]} and an
 * idiosyncratic factor {@code Z_j = values[j+1]} (both standard normal) are
 * combined as {@code y = a M + sqrt(1 - a^2) Z_j} with {@code a =
 * sqrt(copula.correlation())}. The implied default probability is
 * {@code p = Phi(y)}; the default time is the {@code t} solving
 * {@code dts.defaultProbability(t) = p}, found with {@link Brent} (bracketed
 * on {@code [0, tmax]}) and {@link Bisection} as a guaranteed fallback.
 *
 * <p><b>RNG note (cross-validation):</b> the C++ class uses
 * {@code PseudoRandom::make_sequence_generator}, i.e. a Mersenne-Twister
 * uniform sequence mapped through the inverse cumulative normal. The Java port
 * builds the bit-equivalent pipeline ({@link RandomSequenceGenerator}{@code <}
 * {@link MersenneTwisterUniformRng}{@code >} wrapped in an
 * {@link InverseCumulativeRsg} with {@link InverseCumulativeNormal}). Matching
 * the raw Monte-Carlo path counts of C++ bit-for-bit is brittle, so the
 * cross-validation tests assert the <i>deterministic</i> sub-computations
 * instead: the conditional default probability {@code Phi(y)} for a fixed draw
 * {@code y}, and the default time obtained by inverting a flat-hazard-rate
 * curve at a fixed probability {@code p}. See
 * {@code GaussianRandomDefaultModelTest}.
 */
public class GaussianRandomDefaultModel extends RandomDefaultModel {

    private static final CumulativeNormalDistribution PHI = new CumulativeNormalDistribution();

    private final Handle< OneFactorCopula > copula_;
    private final double accuracy_;
    private final long seed_;
    private InverseCumulativeRsg< RandomSequenceGenerator< MersenneTwisterUniformRng >, InverseCumulativeNormal > rsg_;

    public GaussianRandomDefaultModel(final Pool pool, final List< DefaultProbKey > defaultKeys,
            final Handle< OneFactorCopula > copula, final double accuracy, final long seed) {
        super(pool, defaultKeys);
        this.copula_ = copula;
        this.accuracy_ = accuracy;
        this.seed_ = seed;
        this.rsg_ = makeSequenceGenerator(pool.size() + 1, seed);
        copula.addObserver(this);
    }

    /**
     * Builds the Gaussian random-sequence generator that mirrors C++
     * {@code PseudoRandom::make_sequence_generator(dimension, seed)}:
     * a Mersenne-Twister uniform sequence transformed by the inverse
     * cumulative normal.
     */
    private static InverseCumulativeRsg< RandomSequenceGenerator< MersenneTwisterUniformRng >, InverseCumulativeNormal > makeSequenceGenerator(
            final int dimension, final long seed) {
        final RandomSequenceGenerator< MersenneTwisterUniformRng > ursg = new RandomSequenceGenerator<>(
                MersenneTwisterUniformRng.class, dimension, seed);
        return new InverseCumulativeRsg<>(ursg, new InverseCumulativeNormal());
    }

    @Override
    public void reset() {
        final int dim = pool_.size() + 1;
        this.rsg_ = makeSequenceGenerator(dim, seed_);
    }

    @Override
    public void nextSequence(final double tmax) {
        final double[] values = rsg_.nextSequence().value();
        final double a = Math.sqrt(copula_.currentLink().correlation());
        for ( int j = 0; j < pool_.size(); j++ ) {
            final String name = pool_.names().get(j);
            final DefaultProbabilityTermStructure dts = pool_.get(name).defaultProbability(defaultKeys_.get(j))
                    .currentLink();

            final double y = a * values[0] + Math.sqrt(1.0 - a * a) * values[j + 1];
            final double p = PHI.op(y);

            if ( dts.defaultProbability(tmax) < p ) {
                pool_.setTime(name, tmax + 1);
            } else {
                // we know there is a zero of f(t) = dts.defaultProbability(t) - p in [0, tmax]
                try {
                    // try bracketing the root and find it with Brent
                    final Brent brent = new Brent();
                    brent.setLowerBound(0.0);
                    brent.setUpperBound(tmax);
                    pool_.setTime(name, brent.solve(new Root(dts, p), accuracy_, tmax / 2.0, 1.0));
                } catch ( final RuntimeException e ) {
                    // if Brent fails, use Bisection, this is guaranteed to find the root
                    pool_.setTime(name, new Bisection().solve(new Root(dts, p), accuracy_, tmax / 2.0, 0.0, tmax));
                }
            }
        }
    }

    /**
     * Utility for the numerical solver: {@code f(t) = dts.defaultProbability(t,true) - pd}.
     * Mirrors the anonymous {@code Root} functor in the C++ source.
     */
    private static final class Root implements Ops.DoubleOp {
        private final DefaultProbabilityTermStructure dts_;
        private final double pd_;

        Root(final DefaultProbabilityTermStructure dts, final double pd) {
            this.dts_ = dts;
            this.pd_ = pd;
        }

        @Override
        public double op(final double t) {
            QL.require(t >= 0.0,
                    "GaussianRandomDefaultModel: internal error, t < 0 (" + t + ") during root searching.");
            return dts_.defaultProbability(t, true) - pd_;
        }
    }
}
