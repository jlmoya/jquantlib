/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/
 */
/*
 Copyright (C) 2009, 2011 Chris Kenyon

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/
package org.jquantlib.termstructures.volatilities.inflation;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.termstructures.InflationTermStructure;
import org.jquantlib.termstructures.volatilities.VolatilityTermStructure;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.util.Pair;

/**
 * Zero-inflation (i.e. CPI/RPI/HICP/etc.) volatility surface (abstract).
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/termstructures/volatility/inflation/cpivolatilitystructure.{hpp,cpp}} — class
 * {@code CPIVolatilitySurface}.
 *
 * <p>Abstract interface. CPI volatility is always with respect to some base
 * date. Also deals with lagged observations of an index with a (usually different) availability lag.
 *
 * <p>Because inflation is highly linked to dates (for interpolation, periods,
 * etc.), time-based overloads of the lookup methods are not provided beyond the bare
 * {@link #volatility(double, double)} which goes straight to {@link #volatilityImpl}.
 */
public abstract class CPIVolatilitySurface extends VolatilityTermStructure {

    /** Sentinel: "use the surface's own observation lag" (matches C++ {@code Period(-1, Days)}). */
    private static final Period DEFAULT_OBS_LAG = new Period(-1, TimeUnit.Days);

    /** Surface base-level volatility (acts as zero-time bootstrap value). NaN until set. */
    protected double baseLevel_ = Double.NaN;
    /** Observation lag — usually different from the availability lag of the index. */
    protected final Period observationLag_;
    /** Sampling frequency of the underlying CPI rate. */
    protected final Frequency frequency_;
    /** Whether the underlying index is point-in-time interpolated (vs. period-snapped). */
    protected final boolean indexIsInterpolated_;

    //
    // public constructors
    //

    /**
     * Calculate the reference date based on the global evaluation date. Mirror of the C++ primary constructor.
     */
    public CPIVolatilitySurface(final /*@Natural*/ int settlementDays, final Calendar cal,
            final BusinessDayConvention bdc, final DayCounter dc, final Period observationLag, final Frequency frequency,
            final boolean indexIsInterpolated) {
        super(settlementDays, cal, bdc, dc);
        this.observationLag_ = observationLag;
        this.frequency_ = frequency;
        this.indexIsInterpolated_ = indexIsInterpolated;
    }

    //
    // Volatility (Date / Period / Time)
    //

    /** {@code volatility(maturityDate, strike, defaultObsLag, false)}. */
    public double volatility(final Date maturityDate, final double strike) {
        return volatility(maturityDate, strike, DEFAULT_OBS_LAG, false);
    }

    public double volatility(final Date maturityDate, final double strike, final Period obsLag) {
        return volatility(maturityDate, strike, obsLag, false);
    }

    /**
     * Volatility for a given maturity date and strike rate.
     *
     * <p>If {@code obsLag} equals the C++ sentinel {@code Period(-1, Days)},
     * the surface's own {@link #observationLag()} is used.
     */
    public double volatility(final Date maturityDate, final double strike, final Period obsLag,
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
    public double volatility(final Period optionTenor, final double strike) {
        return volatility(optionTenor, strike, DEFAULT_OBS_LAG, false);
    }

    public double volatility(final Period optionTenor, final double strike, final Period obsLag,
            final boolean extrapolate) {
        final Date maturityDate = optionDateFromTenor(optionTenor);
        return volatility(maturityDate, strike, obsLag, extrapolate);
    }

    /**
     * Volatility for a given time and strike rate. No adjustments due to lags and interpolation are applied to the
     * input time — mirrors C++ {@code volatility(Time, Rate)}.
     */
    public double volatility(final double time, final double strike) {
        return volatilityImpl(time, strike);
    }

    //
    // Total integrated variance
    //

    public double totalVariance(final Date exerciseDate, final double strike) {
        return totalVariance(exerciseDate, strike, DEFAULT_OBS_LAG, false);
    }

    /**
     * Total integrated variance for a given exercise date and strike rate.
     *
     * <p>{@code totalVariance = vol*vol*timeFromBase(...)}. "Total" because
     * the surface does not know whether it represents Black, Bachelier or Displaced-Diffusion variance. Virtual so
     * alternate connections between const vol and total var are possible.
     */
    public double totalVariance(final Date exerciseDate, final double strike, final Period obsLag,
            final boolean extrapolate) {
        final double vol = volatility(exerciseDate, strike, obsLag, extrapolate);
        final double t = timeFromBase(exerciseDate, obsLag);
        return vol * vol * t;
    }

    public double totalVariance(final Period optionTenor, final double strike) {
        return totalVariance(optionTenor, strike, DEFAULT_OBS_LAG, false);
    }

    public double totalVariance(final Period optionTenor, final double strike, final Period obsLag,
            final boolean extrapolate) {
        final Date maturityDate = optionDateFromTenor(optionTenor);
        return totalVariance(maturityDate, strike, obsLag, extrapolate);
    }

    //
    // Inspectors
    //

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
     * Surface base date — depends on interpolation (or not) of the observed index and observation lag with which it was
     * built. Works even if the index does not have a term structure.
     */
    public Date baseDate() {
        if ( indexIsInterpolated() ) {
            return referenceDate().sub(observationLag());
        }
        final Pair< Date, Date > p = InflationTermStructure.inflationPeriod(referenceDate().sub(observationLag()),
                frequency());
        return p.first();
    }

    /** Base date is in the past because of observation lag. */
    public double timeFromBase(final Date date, final Period obsLag) {
        Period useLag = obsLag;
        if ( obsLag.eq(DEFAULT_OBS_LAG) ) {
            useLag = observationLag();
        }
        Date useDate;
        if ( indexIsInterpolated() ) {
            useDate = date.sub(useLag);
        } else {
            final Pair< Date, Date > p = InflationTermStructure.inflationPeriod(date.sub(useLag), frequency());
            useDate = p.first();
        }
        // This assumes that the inflation term structure starts as late as
        // possible given the inflation index definition, which is the usual case.
        return dayCounter().yearFraction(baseDate(), useDate);
    }

    public double timeFromBase(final Date date) {
        return timeFromBase(date, DEFAULT_OBS_LAG);
    }

    /** Acts as zero-time value for bootstrapping. Throws if not set (mirrors C++ {@code QL_REQUIRE}). */
    public double baseLevel() {
        QL.require(!Double.isNaN(baseLevel_), "Base volatility, for baseDate(), not set.");
        return baseLevel_;
    }

    protected void setBaseLevel(final double v) {
        this.baseLevel_ = v;
    }

    //
    // Limits — pure-virtual in C++
    //

    @Override
    public abstract double minStrike();

    @Override
    public abstract double maxStrike();

    //
    // Range checks (date- and time-keyed) — mirror C++ checkRange()
    //

    protected void checkRange(final Date d, final double strike, final boolean extrapolate) {
        QL.require(d.ge(baseDate()), "date (" + d + ") is before base date");
        QL.require(extrapolate || allowsExtrapolation() || d.le(maxDate()),
                "date (" + d + ") is past max curve date (" + maxDate() + ")");
        QL.require(extrapolate || allowsExtrapolation() || (strike >= minStrike() && strike <= maxStrike()),
                "strike (" + strike + ") is outside the curve domain [" + minStrike() + "," + maxStrike()
                        + "]] at date = " + d);
    }

    protected void checkRange(final double t, final double strike, final boolean extrapolate) {
        QL.require(t >= timeFromReference(baseDate()), "time (" + t + ") is before base date");
        QL.require(extrapolate || allowsExtrapolation() || t <= maxTime(),
                "time (" + t + ") is past max curve time (" + maxTime() + ")");
        QL.require(extrapolate || allowsExtrapolation() || (strike >= minStrike() && strike <= maxStrike()),
                "strike (" + strike + ") is outside the curve domain [" + minStrike() + "," + maxStrike()
                        + "] at time = " + t);
    }

    /**
     * Implements the actual volatility surface calculation in derived classes e.g. bilinear interpolation. N.B. does
     * not derive the surface.
     */
    protected abstract double volatilityImpl(final double length, final double strike);
}
