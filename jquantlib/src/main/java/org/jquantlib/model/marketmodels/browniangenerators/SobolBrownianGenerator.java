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
import org.jquantlib.math.randomnumbers.InverseCumulativeRsg;
import org.jquantlib.math.randomnumbers.SobolRsg;
import org.jquantlib.methods.montecarlo.Sample;

/**
 * Sobol Brownian generator for market-model simulations.
 * <p>
 * Incremental Brownian generator using a Sobol low-discrepancy generator, inverse-cumulative Gaussian transformation,
 * and Brownian-bridge ordering. Concrete subclass of {@link SobolBrownianGeneratorBase} that supplies the Sobol →
 * Gaussian sample source.
 * <p>
 * The {@link Ordering} controls how the dimensions of the Sobol sequence are mapped to factors and steps.
 * {@link Ordering#Factors} ranks the best Sobol dimensions on the first factor's full path; {@link Ordering#Steps}
 * ranks the best on the largest steps of all factors; {@link Ordering#Diagonal} uses a diagonal scheme.
 *
 * @author Jose Moya
 * @see "ql/models/marketmodels/browniangenerators/sobolbrowniangenerator.{hpp,cpp}" v1.42.1
 */
public class SobolBrownianGenerator extends SobolBrownianGeneratorBase {

    private final InverseCumulativeRsg< SobolRsg, InverseCumulativeNormal > generator_;

    public SobolBrownianGenerator(final int factors, final int steps, final Ordering ordering) {
        this(factors, steps, ordering, 0L, SobolRsg.DirectionIntegers.Jaeckel);
    }

    public SobolBrownianGenerator(final int factors, final int steps, final Ordering ordering, final long seed) {
        this(factors, steps, ordering, seed, SobolRsg.DirectionIntegers.Jaeckel);
    }

    public SobolBrownianGenerator(final int factors, final int steps, final Ordering ordering, final long seed,
            final SobolRsg.DirectionIntegers directionIntegers) {
        super(factors, steps, ordering);
        this.generator_ = new InverseCumulativeRsg< SobolRsg, InverseCumulativeNormal >(
                new SobolRsg(factors * steps, seed, directionIntegers), new InverseCumulativeNormal());
    }

    @Override
    protected Sample< double[] > nextSequence() {
        return generator_.nextSequence();
    }

    /** Mapping of Sobol dimensions to (factor, step) cells. */
    public enum Ordering {
        /** The variates with the best quality will be used for the evolution of the first factor. */
        Factors,
        /** The variates with the best quality will be used for the largest steps of all factors. */
        Steps,
        /** A diagonal schema assigns the best variates to the most important factors and largest steps. */
        Diagonal
    }
}
