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

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2009 Andrea Odetti

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.experimental.mcbasket;

import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.montecarlo.PathPricer;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Longstaff-Schwartz multi-path pricer for early-exercise basket options.
 *
 * <p>Phase 4i scaffold port of C++ QuantLib v1.42.1
 * {@code ql/experimental/mcbasket/longstaffschwartzmultipathpricer.{hpp,cpp}}. Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>References:
 * Longstaff & Schwartz (2001), <i>Valuing American Options by Simulation: A Simple Least-Squares Approach</i>, Review
 * of Financial Studies 14(1), 113-147.
 *
 * <h3>Phase 4i carry-forward (Phase 4i.5)</h3>
 *
 * <p>This is a partial port. The pricer holds the full LSM state machine
 * (calibration / pricing phases, regression coefficients, lower-bound tracking) but the {@link #op(Object)} and
 * {@link #calibrate()} bodies are stubs that throw {@link UnsupportedOperationException}, because the Java codebase
 * does not yet provide:
 *
 * <ul>
 *   <li>{@code MultiPath} (only {@link org.jquantlib.methods.montecarlo.Path}
 *       is ported);</li>
 *   <li>{@code LsmBasisSystem.multiPathBasisSystem(...)} for generating
 *       multi-asset basis functions;</li>
 *   <li>{@code GeneralLinearLeastSquares} for solving the regression at
 *       each time step.</li>
 * </ul>
 *
 * <p>The class type-parameterises on {@code Object} so that it can compile
 * within the existing {@link PathPricer} hierarchy without forcing a
 * stub {@code MultiPath} class. Once the dependencies above land, the
 * type parameter should be re-bound to {@code MultiPath} and the
 * algorithm body filled in (the algorithm is fully specified in the
 * referenced C++ source).
 */
public class LongstaffSchwartzMultiPathPricer extends PathPricer< Object > {

    protected final PathPayoff payoff_;
    protected final int[] timePositions_;
    protected final List< Handle< YieldTermStructure > > forwardTermStructures_;
    protected final Array dF_;
    /** Calibration-time storage of per-path information. */
    protected final List< PathInfo > paths_ = new ArrayList< PathInfo >();
    protected final int polynomialOrder_;
    protected final PolynomialType polynomialType_;
    protected boolean calibrationPhase_ = true;
    protected Array[] coeff_;
    protected double[] lowerBounds_;

    public LongstaffSchwartzMultiPathPricer(final PathPayoff payoff, final int[] timePositions,
            final List< Handle< YieldTermStructure > > forwardTermStructures, final Array discounts,
            final int polynomialOrder, final PolynomialType polynomialType) {
        this.payoff_ = payoff;
        this.timePositions_ = Arrays.copyOf(timePositions, timePositions.length);
        this.forwardTermStructures_ = forwardTermStructures;
        this.dF_ = discounts;
        this.polynomialOrder_ = polynomialOrder;
        this.polynomialType_ = polynomialType;
        this.coeff_ = new Array[timePositions.length - 1];
        this.lowerBounds_ = new double[timePositions.length];

        // Mirrors C++ runtime check.
        switch ( polynomialType ) {
        case Monomial:
        case Laguerre:
        case Hermite:
        case Hyperbolic:
        case Chebyshev2nd:
            break;
        default:
            throw new LibraryException("insufficient polynomial type");
        }
    }

    /**
     * Mirrors C++ {@code Real operator()(const MultiPath&)}. Currently a scaffold — see class javadoc.
     */
    @Override
    public Double op(final Object multiPath) {
        // TODO Phase 4i.5: replicate the C++ pricing/calibration loop once
        //                  MultiPath, LsmBasisSystem and
        //                  GeneralLinearLeastSquares are available.
        throw new UnsupportedOperationException("LongstaffSchwartzMultiPathPricer.op pending Phase 4i.5 "
                + "(MultiPath / LsmBasisSystem / GeneralLinearLeastSquares)");
    }

    /**
     * Two-phase calibration: solves the per-step regression and decides always/never/optimised exercise per the C++
     * logic. Currently a scaffold — see class javadoc.
     */
    public void calibrate() {
        // TODO Phase 4i.5: same dependency set as op().
        throw new UnsupportedOperationException("LongstaffSchwartzMultiPathPricer.calibrate pending Phase 4i.5");
    }

    /**
     * Polynomial-family selector for the LSM basis. Mirrors {@code LsmBasisSystem::PolynomialType} (Phase 4i.5 import
     * target).
     */
    public enum PolynomialType {
        Monomial, Laguerre, Hermite, Hyperbolic, Chebyshev2nd
    }

    /**
     * Cached per-path payoff/exercise/state data. Populated during the calibration phase and replayed during pricing.
     */
    public static class PathInfo {
        public Array payments;
        public Array exercises;
        public List< Array > states;

        public PathInfo(final int numberOfTimes) {
            this.payments = new Array(numberOfTimes);
            this.exercises = new Array(numberOfTimes);
            this.states = new ArrayList< Array >(numberOfTimes);
            for ( int i = 0; i < numberOfTimes; ++i ) {
                states.add(new Array(0));
            }
        }

        public int pathLength() {
            return states.size();
        }
    }
}
