/*
Copyright (C) 2011 Richard Gomes
Copyright (C) 2026 JQuantLib Migration

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

package org.jquantlib.termstructures;

import java.util.Arrays;

import org.jquantlib.QL;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.lang.reflect.ReflectConstants;
import org.jquantlib.math.interpolations.Interpolation;
import org.jquantlib.math.interpolations.Interpolation.Interpolator;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.Constraint;
import org.jquantlib.math.optimization.CostFunction;
import org.jquantlib.math.optimization.EndCriteria;
import org.jquantlib.math.optimization.LevenbergMarquardt;
import org.jquantlib.math.optimization.NoConstraint;
import org.jquantlib.math.optimization.PositiveConstraint;
import org.jquantlib.math.optimization.Problem;
import org.jquantlib.termstructures.yieldcurves.PiecewiseCurve;
import org.jquantlib.termstructures.yieldcurves.PiecewiseYieldCurve;
import org.jquantlib.termstructures.yieldcurves.Traits;
import org.jquantlib.time.Date;

/**
 * Localised-term-structure bootstrapper for most curve types.
 *
 * <p>Faithful port of QuantLib v1.42.1 C++ {@code LocalBootstrap<Curve>}
 * template ({@code ql/termstructures/localbootstrap.hpp}). Specialised
 * here to {@link PiecewiseYieldCurve} for parity with the other Java
 * bootstrappers ({@link IterativeBootstrap},
 * {@link org.jquantlib.termstructures.yieldcurves.GlobalBootstrap}).
 *
 * <p>Algorithm: extend the interpolation one pillar at a time, but
 * solve each step over a sliding window of {@code localisation} pillars
 * via Levenberg-Marquardt. The interpolator must implement
 * {@link Interpolator#localInterpolate} (currently only
 * {@link org.jquantlib.math.interpolations.factories.ConvexMonotone}).
 *
 * <p>Particularly suited to the convex-monotone spline method (Hagan-West),
 * for which a global iterative solve produces non-local risk profiles.
 */
public class LocalBootstrap< Curve extends PiecewiseYieldCurve > implements Bootstrap< Curve > {

    //
    // private final fields
    //

    private final Class< ? > typeCurve;
    private final /*Size*/ int localisation_;
    private final boolean forcePositive_;
    private final double accuracy_;
    private Curve ts_;

    //
    // private fields
    //
    private boolean validCurve_;

    //
    // public constructors
    //

    /**
     * Mirror of the simplest constructor required by
     * {@link PiecewiseYieldCurve#constructBootstrap}. Defaults match
     * C++ ({@code localisation=2}, {@code forcePositive=true},
     * {@code accuracy=Null<Real>()}).
     */
    public LocalBootstrap(final Class< ? > typeCurve) {
        this(typeCurve, 2, true, Double.NaN);
    }

    public LocalBootstrap(final Class< ? > typeCurve, final int localisation) {
        this(typeCurve, localisation, true, Double.NaN);
    }

    public LocalBootstrap(final Class< ? > typeCurve, final int localisation, final boolean forcePositive) {
        this(typeCurve, localisation, forcePositive, Double.NaN);
    }

    public LocalBootstrap(final Class< ? > typeCurve, final int localisation, final boolean forcePositive,
            final double accuracy) {
        if ( typeCurve == null ) {
            throw new LibraryException("null PiecewiseCurve");
        }
        if ( !PiecewiseCurve.class.isAssignableFrom(typeCurve) ) {
            throw new LibraryException(ReflectConstants.WRONG_ARGUMENT_TYPE);
        }
        this.typeCurve = typeCurve;
        this.validCurve_ = false;
        this.ts_ = null;
        this.localisation_ = localisation;
        this.forcePositive_ = forcePositive;
        this.accuracy_ = accuracy;
    }

    /** Legacy convenience constructor. */
    public LocalBootstrap() {
        this(PiecewiseCurve.class, 2, true, Double.NaN);
    }

    @Override
    public void setup(final Curve ts) {
        this.ts_ = ts;
        final int n = ts_.instruments().length;

        QL.require(n >= ts.interpolator().requiredPoints(), "not enough instruments: %d provided, %d required", n,
                ts.interpolator().requiredPoints());
        QL.require(n > localisation_, "not enough instruments: %d provided, %d required.", n, localisation_);

        for (int i = 0; i < n; ++i ) {
            ts_.instruments()[i].addObserver(ts_);
        }
    }

    @Override
    public void calculate() {
        validCurve_ = false;
        final int nInsts = ts_.instruments().length;

        // ensure rate helpers are sorted
        Arrays.sort(ts_.instruments(), new BootstrapHelperSorter());

        // check that there is no instruments with the same maturity
        for (int i = 1; i < nInsts; ++i ) {
            final Date m1 = ts_.instruments()[i - 1].latestDate();
            final Date m2 = ts_.instruments()[i].latestDate();
            QL.require(!m1.eq(m2), "two instruments have the same maturity");
        }

        // check that there is no instruments with invalid quote
        for (int i = 0; i < nInsts; ++i ) {
            QL.require(ts_.instruments()[i].quoteIsValid(),
                    " instrument #%d (maturity: %s) has invalid quote", i + 1,
                    ts_.instruments()[i].latestDate());
        }

        // setup instruments
        for (int i = 0; i < nInsts; ++i ) {
            // don't try this at home!
            // This call creates instruments, and removes "const".
            // There is a significant interaction with observability.
            ts_.instruments()[i].setTermStructure(ts_); // const_cast<Curve*>(ts_)
        }
        // set initial guess only if the current curve cannot be used as guess
        final Traits traits = ts_.traits();
        if ( validCurve_ ) {
            QL.ensure(ts_.data().length == nInsts + 1, "dimension mismatch: expected %d, actual %d", nInsts + 1,
                    ts_.data().length);
        } else {
            final double[] data = new double[nInsts + 1];
            data[0] = traits.initialValue(ts_);
            ts_.setData(data);
        }

        // calculate dates
        final Date[] dates = new Date[nInsts + 1];
        dates[0] = traits.initialDate(ts_);
        ts_.setDates(dates);

        // calculate times
        final double[] times = new double[nInsts + 1];
        times[0] = ts_.timeFromReference(ts_.dates()[0]);
        ts_.setTimes(times);

        for (int i = 0; i < nInsts; ++i ) {
            ts_.dates()[i + 1] = ts_.instruments()[i].latestDate();
            ts_.times()[i + 1] = ts_.timeFromReference(ts_.dates()[i + 1]);
            if ( !validCurve_ ) {
                ts_.data()[i + 1] = ts_.data()[i];
            }
        }

        final double effectiveAccuracy = !Double.isNaN(accuracy_) ? accuracy_ : ts_.accuracy();
        final LevenbergMarquardt solver = new LevenbergMarquardt(effectiveAccuracy, effectiveAccuracy,
                effectiveAccuracy);
        // Mirrors C++ localbootstrap.hpp:193: EndCriteria(100, 10, 0.00, accuracy, 0.00).
        final EndCriteria endCriteria = new EndCriteria(100, 10, 0.00, effectiveAccuracy, 0.00);
        final Constraint solverConstraint = forcePositive_ ? new PositiveConstraint() : new NoConstraint();

        final Interpolator interpolator = ts_.interpolator();
        final int dataAdjust = interpolator.dataSizeAdjustment();

        // now start the bootstrapping.
        int iInst = localisation_ - 1;

        do {
            final int initialDataPt = iInst + 1 - localisation_ + dataAdjust;
            final int startSize = localisation_ + 1 - dataAdjust;
            final double[] startArrayBuf = new double[startSize];
            for (int j = 0; j < startSize - 1; ++j ) {
                startArrayBuf[j] = ts_.data()[initialDataPt + j];
            }

            // here we are extending the interpolation a point at a
            // time... but the local interpolator can make an
            // approximation for the final localisation period.
            // e.g. if the localisation is 2, then the first section
            // of the curve will be solved using the first 2
            // instruments... with the local interpolator making
            // suitable boundary conditions.
            final int sliceLen = iInst + 2;
            final Array sliceTimes = new Array(ts_.times(), sliceLen);
            final Array sliceData = new Array(ts_.data(), sliceLen);
            ts_.setInterpolation(interpolator.localInterpolate(sliceTimes, sliceData,
                    localisation_, ts_.interpolation(), nInsts + 1));

            if ( iInst >= localisation_ ) {
                startArrayBuf[localisation_ - dataAdjust] = traits.guess(ts_, ts_.dates()[iInst]);
            } else {
                startArrayBuf[localisation_ - dataAdjust] = ts_.data()[0];
            }

            final Array startArray = new Array(startArrayBuf);
            final int helpersEnd = iInst + 1;
            final int helpersStart = helpersEnd - localisation_;
            final CostFunction currentCost = new PenaltyFunction(initialDataPt,
                    helpersStart, helpersEnd);

            final Problem toSolve = new Problem(currentCost, solverConstraint, startArray);
            final EndCriteria.Type endType = solver.minimize(toSolve, endCriteria);

            // check the end criteria
            QL.require(EndCriteria.succeeded(endType),
                    "Unable to strip yieldcurve to required accuracy: " + endType);
            ++iInst;
        } while ( iInst < nInsts );
        validCurve_ = true;
    }

    //
    // inner classes
    //

    /**
     * Cost function over a local window of {@code localisation_} rate
     * helpers. The argument vector is the slice of {@code ts_.data()}
     * starting at {@code initialIndex}, and the residual is
     * {@code |helper.quoteError()|} for the localisation window.
     * <p>
     * Mirrors the C++ {@code SimpleCostFunction} lambda body at
     * {@code ql/termstructures/localbootstrap.hpp:234-246}.
     */
    private class PenaltyFunction extends CostFunction {
        private final int initialIndex;
        private final int rateHelpersStart;
        private final int rateHelpersEnd;
        private final int penaltyLocalisation;

        private PenaltyFunction(final int initialIndex, final int rateHelpersStart, final int rateHelpersEnd) {
            this.initialIndex = initialIndex;
            this.rateHelpersStart = rateHelpersStart;
            this.rateHelpersEnd = rateHelpersEnd;
            this.penaltyLocalisation = rateHelpersEnd - rateHelpersStart;
        }

        @Override
        public Array values(final Array x) {
            for (int i = 0; i < x.size(); ++i ) {
                ts_.traits().updateGuess(ts_.data(), x.get(i), initialIndex + i);
            }
            ts_.interpolation().update();

            final Array penalties = new Array(penaltyLocalisation);
            int penIt = 0;
            for (int instIt = rateHelpersStart; instIt < rateHelpersEnd; ++instIt, ++penIt ) {
                penalties.set(penIt, ts_.instruments()[instIt].quoteError());
            }
            return penalties;
        }
    }

}
