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

import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.randomnumbers.Burley2020SobolRsg;
import org.jquantlib.math.randomnumbers.SobolRsg;
import org.jquantlib.methods.montecarlo.Sample;

/**
 * Brownian generator using a Burley-2020 Owen-scrambled Sobol sequence as the variate source.
 *
 * <p>Drop-in alternative to {@link SobolBrownianGenerator} that replaces the plain Sobol generator with the
 * Burley-2020 scrambled variant ({@link Burley2020SobolRsg}). Otherwise identical: inverse-cumulative Gaussian
 * mapping followed by Brownian-bridge ordering per the chosen
 * {@link SobolBrownianGenerator.Ordering ordering}.
 *
 * <p>Faithful port of QuantLib v1.42.1 {@code Burley2020SobolBrownianGenerator}
 * ({@code ql/models/marketmodels/browniangenerators/sobolbrowniangenerator.{hpp,cpp}}, Klaus Spanderen et al.).
 *
 * @author Jose Moya
 */
public class Burley2020SobolBrownianGenerator extends SobolBrownianGeneratorBase {

    private final Burley2020SobolRsg generator_;
    private final InverseCumulativeNormal icn_;
    /** Persistent buffer for Gaussian values returned by {@link #nextSequence()} (length = factors*steps). */
    private final double[] storage_;
    private final Sample< double[] > sample_;

    public Burley2020SobolBrownianGenerator(final int factors, final int steps,
            final SobolBrownianGenerator.Ordering ordering) {
        this(factors, steps, ordering, 42L, SobolRsg.DirectionIntegers.Jaeckel, 43L);
    }

    public Burley2020SobolBrownianGenerator(final int factors, final int steps,
            final SobolBrownianGenerator.Ordering ordering, final long seed) {
        this(factors, steps, ordering, seed, SobolRsg.DirectionIntegers.Jaeckel, 43L);
    }

    public Burley2020SobolBrownianGenerator(final int factors, final int steps,
            final SobolBrownianGenerator.Ordering ordering, final long seed,
            final SobolRsg.DirectionIntegers directionIntegers) {
        this(factors, steps, ordering, seed, directionIntegers, 43L);
    }

    public Burley2020SobolBrownianGenerator(final int factors, final int steps,
            final SobolBrownianGenerator.Ordering ordering, final long seed,
            final SobolRsg.DirectionIntegers directionIntegers, final long scrambleSeed) {
        super(factors, steps, ordering);
        this.generator_ = new Burley2020SobolRsg(factors * steps, seed, directionIntegers, scrambleSeed);
        this.icn_ = new InverseCumulativeNormal();
        this.storage_ = new double[factors * steps];
        this.sample_ = new Sample< double[] >(storage_, 1.0);
    }

    @Override
    protected Sample< double[] > nextSequence() {
        final Sample< double[] > uniform = generator_.nextSequence();
        final double[] u = uniform.value();
        for ( int i = 0; i < storage_.length; ++i ) {
            storage_[i] = icn_.op(u[i]);
        }
        return sample_;
    }
}
