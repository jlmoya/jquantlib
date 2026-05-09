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

import org.jquantlib.math.randomnumbers.SobolRsg;
import org.jquantlib.model.marketmodels.BrownianGenerator;
import org.jquantlib.model.marketmodels.BrownianGeneratorFactory;

/**
 * Factory producing {@link SobolBrownianGenerator} instances with stored
 * ordering, seed, and direction-integers configuration.
 *
 * @see "ql/models/marketmodels/browniangenerators/sobolbrowniangenerator.{hpp,cpp}" v1.42.1
 *
 * @author Jose Moya
 */
public class SobolBrownianGeneratorFactory extends BrownianGeneratorFactory {

    private final SobolBrownianGenerator.Ordering ordering_;
    private final long seed_;
    private final SobolRsg.DirectionIntegers integers_;

    public SobolBrownianGeneratorFactory(final SobolBrownianGenerator.Ordering ordering) {
        this(ordering, 0L, SobolRsg.DirectionIntegers.Jaeckel);
    }

    public SobolBrownianGeneratorFactory(final SobolBrownianGenerator.Ordering ordering,
                                         final long seed) {
        this(ordering, seed, SobolRsg.DirectionIntegers.Jaeckel);
    }

    public SobolBrownianGeneratorFactory(final SobolBrownianGenerator.Ordering ordering,
                                         final long seed,
                                         final SobolRsg.DirectionIntegers integers) {
        this.ordering_ = ordering;
        this.seed_ = seed;
        this.integers_ = integers;
    }

    @Override
    public BrownianGenerator create(final int factors, final int steps) {
        return new SobolBrownianGenerator(factors, steps, ordering_, seed_, integers_);
    }
}
