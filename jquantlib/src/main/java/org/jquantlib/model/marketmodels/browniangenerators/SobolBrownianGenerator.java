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
import org.jquantlib.math.randomnumbers.InverseCumulativeRsg;
import org.jquantlib.math.randomnumbers.SobolRsg;
import org.jquantlib.methods.montecarlo.BrownianBridge;
import org.jquantlib.methods.montecarlo.Sample;
import org.jquantlib.model.marketmodels.BrownianGenerator;

/**
 * Sobol Brownian generator for market-model simulations.
 * <p>
 * Incremental Brownian generator using a Sobol low-discrepancy generator, inverse-cumulative Gaussian transformation,
 * and Brownian-bridge ordering.
 * <p>
 * The {@link Ordering} controls how the dimensions of the Sobol sequence are mapped to factors and steps.
 * {@link Ordering#Factors} ranks the best Sobol dimensions on the first factor's full path; {@link Ordering#Steps}
 * ranks the best on the largest steps of all factors; {@link Ordering#Diagonal} uses a diagonal scheme.
 *
 * @author Jose Moya
 * @see "ql/models/marketmodels/browniangenerators/sobolbrowniangenerator.{hpp,cpp}" v1.42.1
 */
public class SobolBrownianGenerator extends BrownianGenerator {

    private final int factors_;
    private final int steps_;
    private final Ordering ordering_;
    private final BrownianBridge bridge_;
    private final int[][] orderedIndices_;
    private final double[][] bridgedVariates_;
    // generator
    private final InverseCumulativeRsg< SobolRsg, InverseCumulativeNormal > generator_;
    // scratch
    private final double[] permuted_;
    // work variables
    private int lastStep_;
    public SobolBrownianGenerator(final int factors, final int steps, final Ordering ordering) {
        this(factors, steps, ordering, 0L, SobolRsg.DirectionIntegers.Jaeckel);
    }

    public SobolBrownianGenerator(final int factors, final int steps, final Ordering ordering, final long seed) {
        this(factors, steps, ordering, seed, SobolRsg.DirectionIntegers.Jaeckel);
    }

    public SobolBrownianGenerator(final int factors, final int steps, final Ordering ordering, final long seed,
            final SobolRsg.DirectionIntegers directionIntegers) {
        this.factors_ = factors;
        this.steps_ = steps;
        this.ordering_ = ordering;
        this.bridge_ = new BrownianBridge(steps);
        this.lastStep_ = 0;
        this.orderedIndices_ = new int[factors][steps];
        this.bridgedVariates_ = new double[factors][steps];
        this.permuted_ = new double[steps];

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

        this.generator_ = new InverseCumulativeRsg< SobolRsg, InverseCumulativeNormal >(
                new SobolRsg(factors * steps, seed, directionIntegers), new InverseCumulativeNormal());
    }

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

    // variate 2 is used for the second factor's full path
    private static void fillByDiagonal(final int[][] m, final int factors, final int steps) {
        // starting position of the current diagonal
        int i0 = 0;
        int j0 = 0;
        // current position
        int i = 0;
        int j = 0;
        int counter = 0;
        while ( counter < factors * steps ) {
            m[i][j] = counter++;
            if ( i == 0 || j == steps - 1 ) {
                // we completed a diagonal; start a new one
                if ( i0 < factors - 1 ) {
                    // start the path of the next factor
                    i0 = i0 + 1;
                    j0 = 0;
                } else {
                    // move along the path of the last factor
                    i0 = factors - 1;
                    j0 = j0 + 1;
                }
                i = i0;
                j = j0;
            } else {
                // move along the diagonal
                i = i - 1;
                j = j + 1;
            }
        }
    }

    @Override
    public double nextPath() {
        final Sample< double[] > sample = generator_.nextSequence();
        final double[] value = sample.value();
        // Brownian-bridge the variates according to the ordered indices
        for ( int i = 0; i < factors_; ++i ) {
            // build a permuted view of `value` according to orderedIndices_[i]
            for ( int s = 0; s < steps_; ++s ) {
                permuted_[s] = value[orderedIndices_[i][s]];
            }
            bridge_.transform(permuted_, bridgedVariates_[i]);
        }
        lastStep_ = 0;
        return sample.weight();
    }

    @Override
    public double nextStep(final double[] output) {
        QL.require(output.length == factors_, "size mismatch");
        QL.require(lastStep_ < steps_, "sequence exhausted");
        for ( int i = 0; i < factors_; ++i ) {
            output[i] = bridgedVariates_[i][lastStep_];
        }
        ++lastStep_;
        return 1.0;
    }

    @Override
    public int numberOfFactors() {
        return factors_;
    }

    @Override
    public int numberOfSteps() {
        return steps_;
    }

    /** Test interface — exposes the dimension-to-(factor,step) ordering. */
    public int[][] orderedIndices() {
        return orderedIndices_;
    }

    /**
     * Test interface — applies the Brownian-bridge reordering to a batch of pre-generated variate paths. Mirrors the
     * C++ {@code transform(...)} test helper.
     *
     * @param variates [factors*steps][nPaths] — input variates per dimension
     * @return [factors][nPaths*steps] — bridged & rearranged variates
     */
    public double[][] transform(final double[][] variates) {
        QL.require(variates.length == factors_ * steps_, "inconsistent variate vector");

        final int dim = factors_ * steps_;
        final int nPaths = variates[0].length;

        final double[][] retVal = new double[factors_][nPaths * steps_];
        final double[] sample = new double[steps_ * factors_];
        final double[] tempPermuted = new double[steps_];
        final double[] tempBridged = new double[steps_];

        for ( int j = 0; j < nPaths; ++j ) {
            for ( int k = 0; k < dim; ++k ) {
                sample[k] = variates[k][j];
            }
            for ( int i = 0; i < factors_; ++i ) {
                for ( int s = 0; s < steps_; ++s ) {
                    tempPermuted[s] = sample[orderedIndices_[i][s]];
                }
                bridge_.transform(tempPermuted, tempBridged);
                System.arraycopy(tempBridged, 0, retVal[i], j * steps_, steps_);
            }
        }

        return retVal;
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
