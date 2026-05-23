/*
 Copyright (C) 2015 Andres Hernandez
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
package org.jquantlib.experimental.math;

import org.jquantlib.QL;
import org.jquantlib.experimental.math.HybridSimulatedAnnealing.Reannealing;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.Problem;

/**
 * Finite-difference reannealing. In multidimensional problems, different dimensions might have different sensitivities;
 * this strategy rescales the per-dimension step counter so the search concentrates more on the more sensitive
 * dimensions.
 *
 * <p>Java port of QuantLib v1.42.1 {@code ReannealingFiniteDifferences}.
 */
public final class ReannealingFiniteDifferences implements Reannealing {

    private final double stepSize_;
    private final double minSize_;
    private final double functionTol_;
    private final int N_;
    private final Array initialTemp_;
    private final Array bounded_;
    private final boolean bound_;
    private Problem problem_;

    public ReannealingFiniteDifferences(final double initialTemp, final int dimension, final Array lower,
            final Array upper, final double stepSize, final double minSize, final double functionTol) {
        this.stepSize_ = stepSize;
        this.minSize_ = minSize;
        this.functionTol_ = functionTol;
        this.N_ = dimension;
        this.initialTemp_ = new Array(dimension, initialTemp, 0.0);
        this.bounded_ = new Array(dimension, 1.0, 0.0);
        if ( lower != null && lower.size() > 0 && upper != null && upper.size() > 0 ) {
            QL.require(lower.size() == N_, "Incompatible input");
            QL.require(upper.size() == N_, "Incompatible input");
            this.bound_ = true;
            for ( int i = 0; i < N_; ++i ) {
                this.bounded_.set(i, upper.get(i) - lower.get(i));
            }
        } else {
            this.bound_ = false;
        }
    }

    public ReannealingFiniteDifferences(final double initialTemp, final int dimension) {
        this(initialTemp, dimension, null, null, 1e-7, 1e-10, 1e-10);
    }

    @Override
    public void setProblem(final Problem p) {
        this.problem_ = p;
    }

    @Override
    public void reanneal(final Array steps, final Array currentPoint, final double currentValue, final Array currTemp) {
        QL.require(currTemp.size() == N_, "Incompatible input");
        QL.require(steps.size() == N_, "Incompatible input");

        final Array finiteDiffs = new Array(N_, 0.0, 0.0);
        double finiteDiffMax = 0.0;
        final Array offsetPoint = currentPoint.clone();
        for ( int i = 0; i < N_; ++i ) {
            offsetPoint.set(i, offsetPoint.get(i) + stepSize_);
            finiteDiffs.set(i, bounded_.get(i) * Math.abs((problem_.value(offsetPoint) - currentValue) / stepSize_));
            offsetPoint.set(i, offsetPoint.get(i) - stepSize_);
            if ( finiteDiffs.get(i) < minSize_ ) {
                finiteDiffs.set(i, minSize_);
            }
            if ( finiteDiffs.get(i) > finiteDiffMax ) {
                finiteDiffMax = finiteDiffs.get(i);
            }
        }
        for ( int i = 0; i < N_; ++i ) {
            final double tRatio = initialTemp_.get(i) / currTemp.get(i);
            final double sRatio = finiteDiffMax / finiteDiffs.get(i);
            if ( sRatio * tRatio < functionTol_ ) {
                steps.set(i, Math.pow(Math.abs(Math.log(functionTol_)), (double) N_));
            } else {
                steps.set(i, Math.pow(Math.abs(Math.log(sRatio * tRatio)), (double) N_));
            }
        }
    }

    /** Whether the reannealing was constructed with explicit lower/upper bounds. */
    public boolean isBounded() {
        return bound_;
    }
}
