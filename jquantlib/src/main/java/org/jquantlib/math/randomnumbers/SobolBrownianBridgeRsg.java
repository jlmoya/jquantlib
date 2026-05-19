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
 Copyright (C) 2012 Klaus Spanderen
*/

package org.jquantlib.math.randomnumbers;

import org.jquantlib.methods.montecarlo.Sample;
import org.jquantlib.model.marketmodels.browniangenerators.SobolBrownianGenerator;

/**
 * Interface class mapping the functionality of {@link SobolBrownianGenerator} to the conventional sequence-generator
 * (RSG) interface.
 * <p>
 * Each call to {@link #nextSequence()} produces a flat array of {@code factors * steps} Gaussian variates, ordered
 * step-major (i.e., the first {@code factors} entries are the variates for time-step 0, the next {@code factors}
 * entries are the variates for time-step 1, etc.). Internally the variates are produced by Sobol low-discrepancy
 * generation followed by inverse-cumulative Gaussian transform and Brownian-bridge reordering as configured by the
 * {@link SobolBrownianGenerator.Ordering ordering} parameter.
 * <p>
 * Note: C++ defaults the {@code directionIntegers} parameter to {@link SobolRsg.DirectionIntegers#Jaeckel JoeKuoD7};
 * the Java {@link SobolRsg.DirectionIntegers DirectionIntegers} enum does not yet include the JoeKuo or Kuo
 * direction-integer sets (these require additional initialization tables not yet ported). Until those land we default
 * to {@link SobolRsg.DirectionIntegers#Jaeckel}; pass an explicit value if a different direction-integer set is
 * desired.
 *
 * @author Jose Moya
 * @see "ql/math/randomnumbers/sobolbrownianbridgersg.{hpp,cpp}" v1.42.1
 */
public class SobolBrownianBridgeRsg {

    private final SobolBrownianGenerator gen_;
    private final Sample< double[] > seq_;

    public SobolBrownianBridgeRsg(final int factors, final int steps) {
        this(factors, steps, SobolBrownianGenerator.Ordering.Diagonal, 0L, SobolRsg.DirectionIntegers.Jaeckel);
    }

    public SobolBrownianBridgeRsg(final int factors, final int steps, final SobolBrownianGenerator.Ordering ordering) {
        this(factors, steps, ordering, 0L, SobolRsg.DirectionIntegers.Jaeckel);
    }

    public SobolBrownianBridgeRsg(final int factors, final int steps, final SobolBrownianGenerator.Ordering ordering,
            final long seed) {
        this(factors, steps, ordering, seed, SobolRsg.DirectionIntegers.Jaeckel);
    }

    public SobolBrownianBridgeRsg(final int factors, final int steps, final SobolBrownianGenerator.Ordering ordering,
            final long seed, final SobolRsg.DirectionIntegers directionIntegers) {
        this.gen_ = new SobolBrownianGenerator(factors, steps, ordering, seed, directionIntegers);
        this.seq_ = new Sample< double[] >(new double[factors * steps], 1.0);
    }

    /** Mirrors anonymous-namespace {@code setNextSequence} in the C++ source. */
    private static void setNextSequence(final SobolBrownianGenerator gen, final double[] seq) {
        gen.nextPath();
        final int factors = gen.numberOfFactors();
        final double[] output = new double[factors];
        for ( int i = 0; i < gen.numberOfSteps(); ++i ) {
            gen.nextStep(output);
            System.arraycopy(output, 0, seq, i * factors, factors);
        }
    }

    /**
     * Produces the next Brownian-bridge sample (length = {@code factors * steps}, step-major ordering) and returns it.
     * The underlying buffer is reused across calls — copy if the caller needs to retain a snapshot.
     */
    public Sample< double[] > nextSequence() {
        setNextSequence(gen_, seq_.value());
        return seq_;
    }

    /**
     * Returns the last produced sequence (the buffer last filled by {@link #nextSequence()}; weight is always 1.0).
     */
    public Sample< double[] > lastSequence() {
        return seq_;
    }

    /** @return {@code factors * steps} */
    public int dimension() {
        return gen_.numberOfFactors() * gen_.numberOfSteps();
    }
}
