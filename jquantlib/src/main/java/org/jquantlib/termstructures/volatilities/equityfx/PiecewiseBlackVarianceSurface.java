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
 Copyright (C) 2026 Rich Amaya

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/
package org.jquantlib.termstructures.volatilities.equityfx;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.Closeness;
import org.jquantlib.math.Constants;
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.termstructures.BlackVarianceTermStructure;
import org.jquantlib.termstructures.volatilities.InterpolatedSmileSection;
import org.jquantlib.termstructures.volatilities.SmileSection;
import org.jquantlib.time.Date;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;

/**
 * Black volatility surface built from smile sections.
 *
 * <p>Faithful port of QuantLib v1.42.1
 * {@code ql/termstructures/volatility/equityfx/piecewiseblackvariancesurface.{hpp,cpp}}.
 *
 * <p>This class builds a Black volatility surface from a set of smile
 * sections, one per tenor. It interpolates linearly in total variance between tenors for a given strike. Beyond the
 * last tenor a flat-vol extrapolation in time is applied (i.e. variance is proportional to time). Below the first tenor
 * the variance is interpolated linearly from the origin {@code (t=0, w=0)} to the first tenor's variance.
 *
 * <h3>Java port notes</h3>
 * <ul>
 *  <li>Strike validity is delegated to the contained {@link SmileSection}s
 *      so {@code minStrike()/maxStrike()} return {@code -Double.MAX_VALUE}
 *      / {@code Double.MAX_VALUE} just like C++ {@code QL_MIN_REAL} /
 *      {@code QL_MAX_REAL}.</li>
 *  <li>Smile sections are observed for change notifications and forward
 *      their updates through this surface's {@code Observable} interface.</li>
 * </ul>
 *
 * @author JQuantLib migration contributors
 */
public class PiecewiseBlackVarianceSurface extends BlackVarianceTermStructure {

    //
    // private fields
    //

    private final DayCounter dayCounter_;
    private final Date maxDate_;
    private final /* @Time */ double[] times_;
    private final SmileSection[] smileSections_;

    //
    // public constructors
    //

    /**
     * Multi-section constructor — mirrors C++ lines 30-68 of {@code piecewiseblackvariancesurface.cpp}.
     */
    public PiecewiseBlackVarianceSurface(final Date referenceDate, final Date[] dates,
            final SmileSection[] smileSections, final DayCounter dayCounter) {
        super(referenceDate);
        this.dayCounter_ = dayCounter;

        QL.require(dates != null && dates.length > 0, "at least one date is required");
        QL.require(smileSections != null && dates.length == smileSections.length,
                "mismatch between " + dates.length + " dates and " + (smileSections == null ? 0 : smileSections.length)
                        + " smile sections");

        this.maxDate_ = dates[dates.length - 1];
        this.times_ = new double[dates.length];
        this.smileSections_ = smileSections.clone();

        this.times_[0] = timeFromReference(dates[0]);
        QL.require(this.times_[0] > 0.0,
                "first date (" + dates[0] + ") must be after reference date (" + referenceDate + ")");

        for ( int i = 1; i < dates.length; ++i ) {
            this.times_[i] = timeFromReference(dates[i]);
            QL.require(this.times_[i] > this.times_[i - 1],
                    "dates must be sorted and unique, but date " + dates[i] + " (t=" + this.times_[i]
                            + ") is not after date " + dates[i - 1] + " (t=" + this.times_[i - 1] + ")");
        }

        for ( int i = 0; i < this.smileSections_.length; ++i ) {
            QL.require(this.smileSections_[i] != null, "null smile section at index " + i);
            this.smileSections_[i].addObserver(this);
        }
    }

    /**
     * Convenience overload — default day counter.
     */
    public PiecewiseBlackVarianceSurface(final Date referenceDate, final Date[] dates,
            final SmileSection[] smileSections) {
        this(referenceDate, dates, smileSections, new DayCounter());
    }

    /**
     * Single-section constructor — mirrors C++ lines 70-79.
     */
    public PiecewiseBlackVarianceSurface(final Date referenceDate, final Date date, final SmileSection smileSection,
            final DayCounter dayCounter) {
        this(referenceDate, new Date[] { date }, new SmileSection[] { smileSection }, dayCounter);
    }

    /**
     * Convenience overload — default day counter.
     */
    public PiecewiseBlackVarianceSurface(final Date referenceDate, final Date date, final SmileSection smileSection) {
        this(referenceDate, date, smileSection, new DayCounter());
    }

    //
    // factory
    //

    /**
     * Build from a rectangular grid of Black vols — mirrors C++ lines 123-158.
     *
     * <p>Each column of the matrix becomes an
     * {@link InterpolatedSmileSection} with linear interpolation.
     *
     * @param blackVols a matrix with rows indexed by strike and columns indexed by date
     */
    public static PiecewiseBlackVarianceSurface makeFromGrid(final Date referenceDate, final Date[] dates,
            final double[] strikes, final Matrix blackVols, final DayCounter dc) {

        QL.require(blackVols.rows() == strikes.length,
                "mismatch between " + strikes.length + " strikes and " + blackVols.rows() + " matrix rows");
        QL.require(blackVols.columns() == dates.length,
                "mismatch between " + dates.length + " dates and " + blackVols.columns() + " matrix columns");

        final SmileSection[] sections = new SmileSection[dates.length];

        for ( int j = 0; j < dates.length; ++j ) {
            final double[] stdDevs = new double[strikes.length];
            final double t = dc.yearFraction(referenceDate, dates[j]);
            QL.require(t > 0.0, "date " + dates[j] + " must be after reference date " + referenceDate);
            final double sqrtT = Math.sqrt(t);
            for ( int i = 0; i < strikes.length; ++i ) {
                stdDevs[i] = blackVols.get(i, j) * sqrtT;
            }

            sections[j] = new InterpolatedSmileSection(dates[j], strikes.clone(), stdDevs, Constants.NULL_REAL, dc,
                    new Linear(), referenceDate, VolatilityType.ShiftedLognormal, 0.0, false);
        }

        return new PiecewiseBlackVarianceSurface(referenceDate, dates, sections, dc);
    }

    //
    // Overrides TermStructure
    //

    @Override
    public final DayCounter dayCounter() {
        return dayCounter_;
    }

    @Override
    public final Date maxDate() {
        return maxDate_;
    }

    //
    // Overrides BlackVolTermStructure
    //

    @Override
    public final /* @Real */ double minStrike() {
        // Mirrors C++ QL_MIN_REAL inline (line 102-105).
        return Constants.QL_MIN_REAL;
    }

    @Override
    public final /* @Real */ double maxStrike() {
        // Mirrors C++ QL_MAX_REAL inline (line 107-110).
        return Constants.QL_MAX_REAL;
    }

    @Override
    protected final /* @Variance */ double blackVarianceImpl(final /* @Time */ double t,
            final /* @Real */ double strike) {

        if ( t == 0.0 ) {
            return 0.0;
        }

        if ( t <= times_[0] ) {
            // linear interpolation from (0, 0) to first tenor
            final double var1 = sectionVariance(0, strike);
            return var1 * t / times_[0];
        }

        if ( t >= times_[times_.length - 1] ) {
            // flat vol extrapolation beyond last tenor
            final double varN = sectionVariance(smileSections_.length - 1, strike);
            return varN * t / times_[times_.length - 1];
        }

        // find enclosing interval: hi = first index with times_[hi] > t
        int hi = 1;
        while ( hi < times_.length && times_[hi] <= t ) {
            ++hi;
        }
        final int lo = hi - 1;

        final double varLo = sectionVariance(lo, strike);
        final double varHi = sectionVariance(hi, strike);
        final double alpha = (t - times_[lo]) / (times_[hi] - times_[lo]);

        return varLo + (varHi - varLo) * alpha;
    }

    //
    // private helpers
    //

    /**
     * Query the variance of the i-th smile section, enforcing strike-range checks unless extrapolation is allowed on
     * this surface. Mirrors C++ {@code sectionVariance} (lines 81-91).
     */
    private double sectionVariance(final int i, final double strike) {
        final SmileSection s = smileSections_[i];
        QL.require(allowsExtrapolation() || (strike >= s.minStrike() && strike <= s.maxStrike()),
                "strike (" + strike + ") is outside the range of smile section " + i + " [" + s.minStrike() + ", "
                        + s.maxStrike() + "]");
        return s.variance(strike);
    }

    //
    // implements PolymorphicVisitable
    //

    @Override
    public void accept(final PolymorphicVisitor pv) {
        final Visitor< PiecewiseBlackVarianceSurface > v = (pv != null) ? pv.visitor(this.getClass()) : null;
        if ( v != null ) {
            v.visit(this);
        } else {
            super.accept(pv);
        }
    }
    /**
     * Returns the stored smile section when {@code t} lands on one of the surface's own tenors, and otherwise falls
     * back to the base class's on-the-fly adapter.
     * <p>
     * New in C++ QuantLib v1.43
     * ({@code ql/termstructures/volatility/equityfx/piecewiseblackvariancesurface.cpp:163}). The point of the
     * override is that a surface built from smile sections can hand the original object back rather than a
     * reconstruction of it, so nothing is lost to interpolation at a tenor the surface actually knows.
     */
    @Override
    protected SmileSection smileSectionImpl(final /*@Time*/ double t) {
        for ( int i = 0; i < times_.length; ++i ) {
            if ( Closeness.isCloseEnough(t, times_[i]) ) {
                return smileSections_[i];
            }
            if ( times_[i] > t ) {
                break; // times_ is increasing, so no later entry can match
            }
        }
        return super.smileSectionImpl(t);
    }
}
