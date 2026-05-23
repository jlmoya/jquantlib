/*
 Copyright (C) 2026 JQuantLib migration contributors.

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
 Copyright (C) 2000, 2001, 2002, 2003 RiskMap srl
 Copyright (C) 2003 Ferdinando Ametrano
 Copyright (C) 2003, 2004, 2005 StatPro Italia srl
 Copyright (C) 2005 Klaus Spanderen
*/

package org.jquantlib.methods.montecarlo;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.randomnumbers.RandomSequenceGeneratorIntf;
import org.jquantlib.processes.StochasticProcess;
import org.jquantlib.time.TimeGrid;

/**
 * Generates a multi-path from a random number generator.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/methods/montecarlo/multipathgenerator.hpp} (Phase 5h.5-MC-INFRA WI-4).
 *
 * <p>{@code RSG} is a sample generator which returns a random
 * sequence — its dimension must be {@code factors() * (timeGrid.size()-1)}.
 *
 * <p>Brownian-bridge transformation is currently unsupported in the C++
 * source (it raises {@code QL_FAIL}); we mirror that here.
 *
 * @author JQuantLib
 */
@SuppressWarnings("deprecation")
public class MultiPathGenerator< GSG extends RandomSequenceGeneratorIntf > {

    private final boolean brownianBridge_;
    private final StochasticProcess process_;
    private final GSG generator_;
    private final Sample< MultiPath > next_;

    public MultiPathGenerator(final StochasticProcess process, final TimeGrid times, final GSG generator,
            final boolean brownianBridge) {
        this.brownianBridge_ = brownianBridge;
        this.process_ = process;
        this.generator_ = generator;
        this.next_ = new Sample< MultiPath >(new MultiPath(process.size(), times), 1.0);

        if ( generator_.dimension() != process.factors() * (times.size() - 1) ) {
            throw new IllegalArgumentException(
                    "dimension (" + generator_.dimension() + ") is not equal to (" + process.factors() + " * " + (
                            times.size() - 1) + ") the number of factors times the number of time steps");
        }
        if ( times.size() <= 1 ) {
            throw new IllegalArgumentException("no times given");
        }
    }

    public MultiPathGenerator(final StochasticProcess process, final TimeGrid times, final GSG generator) {
        this(process, times, generator, false);
    }

    public final Sample< MultiPath > next() /* @ReadOnly */ {
        return next(false);
    }

    public final Sample< MultiPath > antithetic() /* @ReadOnly */ {
        return next(true);
    }

    private Sample< MultiPath > next(final boolean antithetic) {
        if ( brownianBridge_ ) {
            throw new UnsupportedOperationException("Brownian bridge not supported");
        }

        final Sample< double[] > sequence = antithetic ? generator_.lastSequence() : generator_.nextSequence();

        final int m = process_.size();
        final int n = process_.factors();

        final MultiPath path = next_.value();

        // Initialise each sub-path's front with the corresponding initial value.
        Array asset = process_.initialValues();
        for ( int j = 0; j < m; j++ ) {
            path.get(j).setFront(asset.get(j));
        }

        next_.setWeight(sequence.weight());

        final TimeGrid timeGrid = path.get(0).timeGrid();
        final double[] sv = sequence.value();
        for ( int i = 1; i < path.pathSize(); i++ ) {
            final int offset = (i - 1) * n;
            final /*@Time*/ double t = timeGrid.get(i - 1);
            final /*@Time*/ double dt = timeGrid.dt(i - 1);

            final double[] tempArr = new double[n];
            if ( antithetic ) {
                for ( int k = 0; k < n; k++ ) {
                    tempArr[k] = -sv[offset + k];
                }
            } else {
                System.arraycopy(sv, offset, tempArr, 0, n);
            }
            final Array temp = new Array(tempArr);

            asset = process_.evolve(t, asset, dt, temp);
            for ( int j = 0; j < m; j++ ) {
                path.get(j).set(i, asset.get(j));
            }
        }
        return next_;
    }
}
