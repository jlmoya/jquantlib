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
 Copyright (C) 2006 StatPro Italia srl
*/

package org.jquantlib.model.marketmodels.browniangenerators;

import org.jquantlib.QL;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.randomnumbers.MersenneTwisterUniformRng;
import org.jquantlib.math.randomnumbers.RandomSequenceGenerator;
import org.jquantlib.methods.montecarlo.Sample;
import org.jquantlib.model.marketmodels.BrownianGenerator;

/**
 * Mersenne-twister Brownian generator for market-model simulations.
 * <p>
 * Incremental Brownian generator using a Mersenne-twister uniform generator and inverse-cumulative Gaussian method.
 * <p>
 * Note: At this time, generation of the underlying uniform sequence is eager, while its transformation into Gaussian
 * variates is lazy. Further optimization might be possible by using the Mersenne twister directly instead of a
 * RandomSequenceGenerator; however, it is not clear how much of a difference this would make when compared to the
 * inverse-cumulative Gaussian calculation.
 *
 * @author Jose Moya
 * @see "ql/models/marketmodels/browniangenerators/mtbrowniangenerator.{hpp,cpp}" v1.42.1
 */
public class MTBrownianGenerator extends BrownianGenerator {

    private final int factors_;
    private final int steps_;
    private final RandomSequenceGenerator< MersenneTwisterUniformRng > generator_;
    private final InverseCumulativeNormal inverseCumulative_;
    private int lastStep_ = 0;

    public MTBrownianGenerator(final int factors, final int steps) {
        this(factors, steps, 0L);
    }

    public MTBrownianGenerator(final int factors, final int steps, final long seed) {
        this.factors_ = factors;
        this.steps_ = steps;
        // C++: generator_(factors * steps, MersenneTwisterUniformRng(seed))
        this.generator_ = new RandomSequenceGenerator< MersenneTwisterUniformRng >(MersenneTwisterUniformRng.class,
                factors * steps, new MersenneTwisterUniformRng(seed));
        this.inverseCumulative_ = new InverseCumulativeNormal();
    }

    @Override
    public double nextStep(final double[] output) {
        QL.require(output.length == factors_, "size mismatch");
        QL.require(lastStep_ < steps_, "uniform sequence exhausted");
        // no copying, just fetching a reference
        final double[] currentSequence = generator_.lastSequence().value();
        final int start = lastStep_ * factors_;
        for ( int i = 0; i < factors_; ++i ) {
            output[i] = inverseCumulative_.op(currentSequence[start + i]);
        }
        ++lastStep_;
        return 1.0;
    }

    @Override
    public double nextPath() {
        final Sample< double[] > sample = generator_.nextSequence();
        lastStep_ = 0;
        return sample.weight();
    }

    @Override
    public int numberOfFactors() {
        return factors_;
    }

    @Override
    public int numberOfSteps() {
        return steps_;
    }
}
