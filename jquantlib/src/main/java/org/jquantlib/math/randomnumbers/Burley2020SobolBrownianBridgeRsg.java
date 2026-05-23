/*
 Copyright (C) 2026 Jose Moya

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

package org.jquantlib.math.randomnumbers;

import org.jquantlib.QL;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.methods.montecarlo.BrownianBridge;
import org.jquantlib.methods.montecarlo.Sample;
import org.jquantlib.model.marketmodels.browniangenerators.SobolBrownianGenerator;

/**
 * Interface class mapping the functionality of a Burley2020-scrambled Sobol Brownian generator
 * to the conventional sequence-generator (RSG) interface.
 * <p>
 * Faithful Java port of QuantLib v1.42.1 {@code ql/math/randomnumbers/sobolbrownianbridgersg.hpp}
 * ({@code Burley2020SobolBrownianBridgeRsg}, Klaus Spanderen, 2012).
 * <p>
 * Mirrors {@link SobolBrownianBridgeRsg} but feeds an inverse-cumulative Gaussian transform over
 * {@link Burley2020SobolRsg} (owen-scrambled Sobol per Burley 2020), then Brownian-bridges the
 * variates according to the chosen {@link SobolBrownianGenerator.Ordering ordering}.
 * <p>
 * Each call to {@link #nextSequence()} returns a flat array of {@code factors * steps} Gaussian
 * variates, ordered step-major (the first {@code factors} entries are the variates for step 0, etc.).
 *
 * @author Jose Moya
 */
public final class Burley2020SobolBrownianBridgeRsg {

    private final int factors_;
    private final int steps_;
    private final SobolBrownianGenerator.Ordering ordering_;
    private final BrownianBridge bridge_;
    private final int[][] orderedIndices_;
    private final double[][] bridgedVariates_;
    private final Burley2020SobolRsg sobol_;
    private final InverseCumulativeNormal icn_;
    private final double[] permuted_;
    private final Sample< double[] > seq_;

    public Burley2020SobolBrownianBridgeRsg(final int factors, final int steps) {
        this(factors, steps, SobolBrownianGenerator.Ordering.Diagonal, 42L,
                SobolRsg.DirectionIntegers.Jaeckel, 43L);
    }

    public Burley2020SobolBrownianBridgeRsg(final int factors, final int steps,
            final SobolBrownianGenerator.Ordering ordering) {
        this(factors, steps, ordering, 42L, SobolRsg.DirectionIntegers.Jaeckel, 43L);
    }

    public Burley2020SobolBrownianBridgeRsg(final int factors, final int steps,
            final SobolBrownianGenerator.Ordering ordering, final long seed) {
        this(factors, steps, ordering, seed, SobolRsg.DirectionIntegers.Jaeckel, 43L);
    }

    public Burley2020SobolBrownianBridgeRsg(final int factors, final int steps,
            final SobolBrownianGenerator.Ordering ordering, final long seed,
            final SobolRsg.DirectionIntegers directionIntegers) {
        this(factors, steps, ordering, seed, directionIntegers, 43L);
    }

    public Burley2020SobolBrownianBridgeRsg(final int factors, final int steps,
            final SobolBrownianGenerator.Ordering ordering, final long seed,
            final SobolRsg.DirectionIntegers directionIntegers, final long scrambleSeed) {
        this.factors_ = factors;
        this.steps_ = steps;
        this.ordering_ = ordering;
        this.bridge_ = new BrownianBridge(steps);
        this.orderedIndices_ = new int[factors][steps];
        this.bridgedVariates_ = new double[factors][steps];
        this.permuted_ = new double[steps];
        this.seq_ = new Sample< double[] >(new double[factors * steps], 1.0);

        switch ( ordering_ ) {
        case Factors:
            fillByFactor(orderedIndices_, factors_, steps_);
            break;
        case Steps:
            fillByStep(orderedIndices_, factors_, steps_);
            break;
        case Diagonal:
            fillByDiagonal(orderedIndices_, factors_, steps_);
            break;
        default:
            QL.error("unknown ordering");
        }

        this.sobol_ = new Burley2020SobolRsg(factors * steps, seed, directionIntegers, scrambleSeed);
        this.icn_ = new InverseCumulativeNormal();
    }

    // --- ordering helpers (same as SobolBrownianGenerator) ---

    private static void fillByFactor(final int[][] m, final int factors, final int steps) {
        int counter = 0;
        for ( int i = 0; i < factors; ++i ) {
            for ( int j = 0; j < steps; ++j ) {
                m[i][j] = counter++;
            }
        }
    }

    private static void fillByStep(final int[][] m, final int factors, final int steps) {
        int counter = 0;
        for ( int j = 0; j < steps; ++j ) {
            for ( int i = 0; i < factors; ++i ) {
                m[i][j] = counter++;
            }
        }
    }

    private static void fillByDiagonal(final int[][] m, final int factors, final int steps) {
        int i0 = 0;
        int j0 = 0;
        int i = 0;
        int j = 0;
        int counter = 0;
        while ( counter < factors * steps ) {
            m[i][j] = counter++;
            if ( i == 0 || j == steps - 1 ) {
                if ( i0 < factors - 1 ) {
                    i0 = i0 + 1;
                    j0 = 0;
                } else {
                    i0 = factors - 1;
                    j0 = j0 + 1;
                }
                i = i0;
                j = j0;
            } else {
                i = i - 1;
                j = j + 1;
            }
        }
    }

    /**
     * Produces the next Brownian-bridge sample (length = {@code factors * steps}, step-major).
     * The returned buffer is reused across calls.
     */
    public Sample< double[] > nextSequence() {
        // Pull next sobol uniform; apply inverse-cumulative-normal to get Gaussian variates.
        final Sample< double[] > uniform = sobol_.nextSequence();
        final double[] uvals = uniform.value();
        final double[] gaussian = new double[uvals.length];
        for ( int i = 0; i < uvals.length; ++i ) {
            gaussian[i] = icn_.op(uvals[i]);
        }
        // Bridge per factor.
        for ( int i = 0; i < factors_; ++i ) {
            for ( int s = 0; s < steps_; ++s ) {
                permuted_[s] = gaussian[orderedIndices_[i][s]];
            }
            bridge_.transform(permuted_, bridgedVariates_[i]);
        }
        // Flatten step-major into seq_.
        final double[] out = seq_.value();
        for ( int s = 0; s < steps_; ++s ) {
            for ( int i = 0; i < factors_; ++i ) {
                out[s * factors_ + i] = bridgedVariates_[i][s];
            }
        }
        return seq_;
    }

    /** Returns the last produced sequence (the buffer last filled by {@link #nextSequence()}). */
    public Sample< double[] > lastSequence() {
        return seq_;
    }

    /** @return {@code factors * steps} */
    public int dimension() {
        return factors_ * steps_;
    }
}
