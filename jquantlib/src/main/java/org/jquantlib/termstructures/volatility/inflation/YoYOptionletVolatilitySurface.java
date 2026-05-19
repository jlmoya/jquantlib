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
 Copyright (C) 2009 Chris Kenyon

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/
package org.jquantlib.termstructures.volatility.inflation;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.Closeness;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.termstructures.InflationTermStructure;
import org.jquantlib.termstructures.volatilities.VolatilityTermStructure;
import org.jquantlib.time.*;
import org.jquantlib.util.Pair;

/**
 * YoY-inflation optionlet volatility surface (abstract).
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/termstructures/volatility/inflation/yoyinflationoptionletvolatilitystructure.{hpp,cpp}}.
 *
 * <p>This abstract class mirrors C++ {@code YoYOptionletVolatilitySurface}:
 * an "abstract interface ... no data, only results", changing {@code BlackVariance()} to {@code totalVariance()} and
 * dealing with lagged observations of an index (with a usually different availability lag).
 *
 * <h3>Inflation date model</h3>
 * Like other inflation curves, the surface honours an {@code observationLag} (typically the publication lag of the
 * underlying YoY index) and a sampling {@code frequency}. The {@code indexIsInterpolated} flag selects between
 * date-exact lookup and inflation-period snapping.
 *
 * <p>Because inflation is highly linked to dates (for interpolation, periods,
 * etc.), C++ does not provide a {@code Volatility(Time, ...)} convenience with lag/extrapolation knobs at the public
 * API; the only {@code Time}-keyed overload is the bare implementation. Java mirrors that.
 */
public abstract class YoYOptionletVolatilitySurface extends VolatilityTermStructure {

    /** Sentinel: "use the surface's own observation lag" (matches C++ {@code Period(-1, Days)}). */
    private static final Period DEFAULT_OBS_LAG = new Period(-1, TimeUnit.Days);
    /** Observation lag for the YoY rate. */
    protected final Period observationLag_;
    /** Sampling frequency of the underlying YoY rate. */
    protected final Frequency frequency_;
    /** Whether the underlying index is point-in-time interpolated (vs. period-snapped). */
    protected final boolean indexIsInterpolated_;
    /** Volatility type ({@code ShiftedLognormal} or {@code Normal}). */
    protected final VolatilityType volType_;
    /** Displacement for shifted-lognormal vols. C++ enforces 0 or 1. */
    protected final double displacement_;
    /** Surface base-level volatility (acts as zero-time bootstrap value). NaN until set. */
    protected double baseLevel_ = Double.NaN;

    //
    // public constructors
    //

    /**
     * Calculate the reference date from the global evaluation date. Defaults to ShiftedLognormal/displacement=0.
     */
    public YoYOptionletVolatilitySurface(final int settlementDays, final Calendar cal, final BusinessDayConvention bdc,
            final DayCounter dc, final Period observationLag, final Frequency frequency,
            final boolean indexIsInterpolated) {
        this(settlementDays, cal, bdc, dc, observationLag, frequency, indexIsInterpolated,
                VolatilityType.ShiftedLognormal, 0.0);
    }

    /**
     * Calculate the reference date from the global evaluation date. Mirror of C++ primary constructor.
     */
    public YoYOptionletVolatilitySurface(final int settlementDays, final Calendar cal, final BusinessDayConvention bdc,
            final DayCounter dc, final Period observationLag, final Frequency frequency,
            final boolean indexIsInterpolated, final VolatilityType volType, final double displacement) {
        super(settlementDays, cal, bdc, dc);
        QL.require(Closeness.isCloseEnough(displacement, 0.0) || Closeness.isCloseEnough(displacement, 1.0),
                "YoYOptionletVolatilitySurface: displacement (" + displacement + ") must be 0 or 1");
        this.observationLag_ = observationLag;
        this.frequency_ = frequency;
        this.indexIsInterpolated_ = indexIsInterpolated;
        this.volType_ = volType;
        this.displacement_ = displacement;
    }

    //
    // Accessors mirroring C++ {volatilityType, displacement, observationLag,
    // frequency, indexIsInterpolated, baseDate, baseLevel}
    //

    public VolatilityType volatilityType() {
        return volType_;
    }

    public double displacement() {
        return displacement_;
    }

    public Period observationLag() {
        return observationLag_;
    }

    public Frequency frequency() {
        return frequency_;
    }

    public boolean indexIsInterpolated() {
        return indexIsInterpolated_;
    }

    /**
     * Surface base date — depends on the index interpolation style and observation lag. Mirrors C++:
     * <pre>
     *   if (indexIsInterpolated())
     *       return referenceDate() - observationLag();
     *   else
     *       return inflationPeriod(referenceDate() - observationLag(), frequency()).first;
     * </pre>
     */
    public Date baseDate() {
        if ( indexIsInterpolated() ) {
            return referenceDate().sub(observationLag());
        }
        final Pair< Date, Date > p = InflationTermStructure.inflationPeriod(referenceDate().sub(observationLag()),
                frequency());
        return p.first();
    }

    /**
     * Base-level volatility — bootstrapping zero-time value. Throws if not set (mirrors C++ {@code QL_REQUIRE}).
     */
    public double baseLevel() {
        QL.require(!Double.isNaN(baseLevel_), "Base volatility, for baseDate(), not set.");
        return baseLevel_;
    }

    protected void setBaseLevel(final double v) {
        baseLevel_ = v;
    }

    //
    // Volatility (Date / Period / Time)
    //

    /** {@code volatility(maturityDate, strike, useDefaultObsLag, false)}. */
    public final double volatility(final Date maturityDate, final double strike) {
        return volatility(maturityDate, strike, DEFAULT_OBS_LAG, false);
    }

    /** {@code volatility(maturityDate, strike, obsLag, false)}. */
    public final double volatility(final Date maturityDate, final double strike, final Period obsLag) {
        return volatility(maturityDate, strike, obsLag, false);
    }

    /**
     * Volatility for a given maturity date and strike rate.
     *
     * <p>If {@code obsLag} equals the C++ sentinel {@code Period(-1, Days)},
     * the surface's own {@link #observationLag()} is used. The maturity is shifted back by {@code useLag} and (when
     * {@code !indexIsInterpolated}) snapped to the inflation-period start before the surface lookup.
     */
    public final double volatility(final Date maturityDate, final double strike, final Period obsLag,
            final boolean extrapolate) {
        Period useLag = obsLag;
        if ( obsLag.eq(DEFAULT_OBS_LAG) ) {
            useLag = observationLag();
        }
        if ( indexIsInterpolated() ) {
            checkRange(maturityDate.sub(useLag), strike, extrapolate);
            final double t = timeFromReference(maturityDate.sub(useLag));
            return volatilityImpl(t, strike);
        }
        final Pair< Date, Date > dd = InflationTermStructure.inflationPeriod(maturityDate.sub(useLag), frequency());
        checkRange(dd.first(), strike, extrapolate);
        final double t = timeFromReference(dd.first());
        return volatilityImpl(t, strike);
    }

    /** Volatility for a given option tenor and strike rate. */
    public final double volatility(final Period optionTenor, final double strike) {
        return volatility(optionTenor, strike, DEFAULT_OBS_LAG, false);
    }

    public final double volatility(final Period optionTenor, final double strike, final Period obsLag,
            final boolean extrapolate) {
        final Date maturityDate = optionDateFromTenor(optionTenor);
        return volatility(maturityDate, strike, obsLag, extrapolate);
    }

    /**
     * Volatility for a given time and strike rate. No lag/period adjustments — mirrors C++
     * {@code volatility(Time, Rate)} which goes straight to {@link #volatilityImpl}.
     */
    public final double volatility(final double time, final double strike) {
        return volatilityImpl(time, strike);
    }

    //
    // Total integrated variance
    //

    /**
     * Total integrated variance for a given maturity date and strike rate.
     *
     * <p>{@code totalVariance = vol*vol*timeFromBase(...)}. "Total" because
     * this surface does not know if it represents Black, Bachelier or Displaced-Diffusion variance.
     */
    public double totalVariance(final Date maturityDate, final double strike) {
        return totalVariance(maturityDate, strike, DEFAULT_OBS_LAG, false);
    }

    public double totalVariance(final Date maturityDate, final double strike, final Period obsLag,
            final boolean extrapolate) {
        final double vol = volatility(maturityDate, strike, obsLag, extrapolate);
        final double t = timeFromBase(maturityDate, obsLag);
        return vol * vol * t;
    }

    public double totalVariance(final Period tenor, final double strike) {
        return totalVariance(tenor, strike, DEFAULT_OBS_LAG, false);
    }

    public double totalVariance(final Period tenor, final double strike, final Period obsLag,
            final boolean extrapolate) {
        final Date maturityDate = optionDateFromTenor(tenor);
        return totalVariance(maturityDate, strike, obsLag, extrapolate);
    }

    /**
     * Time elapsed from the surface's base date to the (lag-adjusted) maturity. For inflation, the term structure
     * starts as far back as the inflation index definition allows.
     */
    public double timeFromBase(final Date maturityDate, final Period obsLag) {
        Period useLag = obsLag;
        if ( obsLag.eq(DEFAULT_OBS_LAG) ) {
            useLag = observationLag();
        }
        Date useDate;
        if ( indexIsInterpolated() ) {
            useDate = maturityDate.sub(useLag);
        } else {
            final Pair< Date, Date > p = InflationTermStructure.inflationPeriod(maturityDate.sub(useLag), frequency());
            useDate = p.first();
        }
        return dayCounter().yearFraction(baseDate(), useDate);
    }

    public double timeFromBase(final Date maturityDate) {
        return timeFromBase(maturityDate, DEFAULT_OBS_LAG);
    }

    //
    // Range checks (date- and time-keyed) — mirror C++ checkRange()
    //

    protected void checkRange(final Date d, final double strike, final boolean extrapolate) {
        QL.require(d.ge(baseDate()), "date (" + d + ") is before base date " + baseDate());
        QL.require(extrapolate || allowsExtrapolation() || d.le(maxDate()),
                "date (" + d + ") is past max curve date (" + maxDate() + ")");
        QL.require(extrapolate || allowsExtrapolation() || (strike >= minStrike() && strike <= maxStrike()),
                "strike (" + strike + ") is outside the curve domain [" + minStrike() + "," + maxStrike()
                        + "] at date = " + d);
    }

    protected void checkRange(final double t, final double strike, final boolean extrapolate) {
        QL.require(t >= timeFromReference(baseDate()), "time (" + t + ") is before base date");
        QL.require(extrapolate || allowsExtrapolation() || t <= maxTime(),
                "time (" + t + ") is past max curve time (" + maxTime() + ")");
        QL.require(extrapolate || allowsExtrapolation() || (strike >= minStrike() && strike <= maxStrike()),
                "strike (" + strike + ") is outside the curve domain [" + minStrike() + "," + maxStrike()
                        + "] at time = " + t);
    }

    //
    // Hooks for derived classes
    //

    /**
     * Implements the surface volatility lookup in derived classes (e.g. constant, bilinear interpolation). Does not
     * derive the surface.
     */
    protected abstract double volatilityImpl(final double length, final double strike);

    @Override
    public abstract double minStrike();

    @Override
    public abstract double maxStrike();
}
