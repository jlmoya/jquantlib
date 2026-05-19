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
 Copyright (C) 2008 Piero Del Boca
 Copyright (C) 2009 Chris Kenyon
 Copyright (C) 2015 Bernd Lewerenz

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.termstructures.inflation;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.lang.annotation.Rate;
import org.jquantlib.lang.annotation.Time;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.transcendental.JQuantMath;
import org.jquantlib.termstructures.InflationTermStructure;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.util.Pair;

/**
 * Multiplicative seasonality in the price index (CPI/RPI/HICP/etc).
 *
 * <p>Stationary multiplicative seasonality in CPI/RPI/HICP (i.e., in price)
 * implies that zero inflation swap rates are affected, but year-on-year inflation swap rates show no effect. Of course,
 * if seasonality in CPI/RPI/HICP is non-stationary then both swap rates will be affected.
 *
 * <p>Factors must be in multiples of the minimum required for one year (e.g.,
 * 12 for monthly), and these factors are reused for as long as is required; i.e., they wrap around. So, for example, if
 * 24 factors are given this repeats every two years. True stationary seasonality can be obtained by giving the same
 * number of factors as the frequency dictates (e.g., 12 for monthly seasonality).
 *
 * <p>Multi-year seasonality (i.e., non-stationary) is fragile: the user
 * <b>must</b> ensure that corrections at whole years before and after the
 * inflation term structure base date are the same. Otherwise there can be an inconsistency with quoted rates. This is
 * enforced if the frequency is lower than daily.
 *
 * <p>Mirrors C++ {@code QuantLib::MultiplicativePriceSeasonality} at v1.42.1
 * (termstructures/inflation/seasonality.{hpp,cpp}).
 *
 * @author JQuantLib migration team (Phase 2q C.2)
 */
public class MultiplicativePriceSeasonality extends Seasonality {

    //
    // private fields
    //

    private Date seasonalityBaseDate_;
    private Frequency frequency_;
    private double[] seasonalityFactors_;

    //
    // public constructors
    //

    /** No-arg constructor; subclasses (or builders) call {@link #set} later. */
    public MultiplicativePriceSeasonality() {
        // intentionally empty — matches C++ default ctor
    }

    public MultiplicativePriceSeasonality(final Date seasonalityBaseDate, final Frequency frequency,
            final double[] seasonalityFactors) {
        set(seasonalityBaseDate, frequency, seasonalityFactors);
    }

    //
    // public methods
    //

    public void set(final Date seasonalityBaseDate, final Frequency frequency, final double[] seasonalityFactors) {
        this.frequency_ = frequency;
        this.seasonalityFactors_ = seasonalityFactors == null ? new double[0] : seasonalityFactors.clone();
        this.seasonalityBaseDate_ = seasonalityBaseDate.clone();
        validate();
    }

    public Date seasonalityBaseDate() {
        return seasonalityBaseDate_.clone();
    }

    public Frequency frequency() {
        return frequency_;
    }

    public double[] seasonalityFactors() {
        return seasonalityFactors_.clone();
    }

    /**
     * The factor returned is NOT normalized relative to anything.
     *
     * <p>Mirrors C++ {@code MultiplicativePriceSeasonality::seasonalityFactor}.
     */
    public double seasonalityFactor(final Date to) {
        final Date from = seasonalityBaseDate_;
        final Frequency factorFrequency = frequency_;
        final int nFactors = seasonalityFactors_.length;
        final Period factorPeriod = new Period(factorFrequency);
        int which = 0;
        if ( from.eq(to) ) {
            which = 0;
        } else {
            // days, weeks, months, years are the only time-unit possibilities
            final long diffDays = Math.abs(to.sub(from));
            final int dir = from.gt(to) ? -1 : 1;
            int diff;
            switch ( factorPeriod.units() ) {
            case Days:
                diff = (int) (dir * diffDays);
                break;
            case Weeks:
                diff = (int) (dir * (diffDays / 7L));
                break;
            case Months: {
                final Pair< Date, Date > lim = InflationTermStructure.inflationPeriod(to, factorFrequency);
                diff = (int) (diffDays / (31L * factorPeriod.length()));
                Date go = from.add(factorPeriod.mul(dir * diff));
                while ( !(lim.first().le(go) && go.le(lim.second())) ) {
                    go = go.add(factorPeriod.mul(dir));
                    diff++;
                }
                diff = dir * diff;
                break;
            }
            case Years:
                throw new LibraryException(
                        "seasonality period time unit is not allowed to be: " + factorPeriod.units());
            default:
                throw new LibraryException("Unknown time unit: " + factorPeriod.units());
            }
            // adjust to the available number of factors, direction-dependent
            if ( dir == 1 ) {
                which = diff % nFactors;
            } else {
                which = (nFactors - ((-diff) % nFactors)) % nFactors;
            }
        }
        return seasonalityFactors_[which];
    }

    //
    // Seasonality interface
    //

    @Override
    public /*@Rate*/ double correctZeroRate(final Date d, final @Rate double r, final InflationTermStructure iTS) {
        // Mirror C++ logic that mimics ZeroInflationIndex::forecastFixing for
        // choosing curveBaseDate and effective fixing date. This means that we
        // retrieve the input seasonality adjustments when looking at
        // I_{SA}(t) / I_{NSA}(t).
        final Date curveBaseDate = iTS.baseDate();
        final Date effectiveFixingDate = InflationTermStructure.inflationPeriod(d, iTS.frequency()).first();
        return seasonalityCorrection(r, effectiveFixingDate, iTS.dayCounter(), curveBaseDate, true);
    }

    @Override
    public /*@Rate*/ double correctYoYRate(final Date d, final @Rate double r, final InflationTermStructure iTS) {
        final Pair< Date, Date > lim = InflationTermStructure.inflationPeriod(iTS.baseDate(), iTS.frequency());
        final Date curveBaseDate = lim.second();
        return seasonalityCorrection(r, d, iTS.dayCounter(), curveBaseDate, false);
    }

    @Override
    public boolean isConsistent(final InflationTermStructure iTS) {
        // If multi-year is the specification, is it consistent with the term
        // structure start date?
        // We do NOT test daily seasonality because this will, in general, never
        // be consistent given weekends, holidays, leap years, etc.
        if ( frequency_ == Frequency.Daily ) {
            return true;
        }
        if ( frequency_.toInteger() == seasonalityFactors_.length ) {
            return true;
        }

        // how many years do you need to test?
        final int nTest = seasonalityFactors_.length / frequency_.toInteger();
        // ... relative to the start of the inflation curve
        final Pair< Date, Date > lim = InflationTermStructure.inflationPeriod(iTS.baseDate(), iTS.frequency());
        final Date curveBaseDate = lim.second();
        final double factorBase = seasonalityFactor(curveBaseDate);

        final double eps = 0.00001;
        for ( int i = 1; i < nTest; ++i ) {
            final double factorAt = seasonalityFactor(curveBaseDate.add(new Period(i, TimeUnit.Years)));
            QL.require(Math.abs(factorAt - factorBase) < eps,
                    "seasonality is inconsistent with inflation term structure, factors " + factorBase
                            + " and later factor " + factorAt + ", " + i
                            + " years later from inflation curve with base date at " + curveBaseDate);
        }
        return true;
    }

    //
    // protected helpers
    //

    /**
     * Validate that the requested frequency is one we support and that the factor count is a multiple of the
     * frequency.
     *
     * <p>Mirrors C++ {@code MultiplicativePriceSeasonality::validate}.
     */
    protected void validate() {
        switch ( frequency_ ) {
        case Semiannual:        // 2
        case EveryFourthMonth:  // 3
        case Quarterly:         // 4
        case Bimonthly:         // 6
        case Monthly:           // 12
        case Biweekly:
        case Weekly:
        case Daily:
            QL.require(seasonalityFactors_ != null && seasonalityFactors_.length > 0, "no seasonality factors given");
            QL.require((seasonalityFactors_.length % frequency_.toInteger()) == 0,
                    "For frequency " + frequency_ + " require multiple of " + frequency_.toInteger() + " factors; "
                            + seasonalityFactors_.length + " were given.");
            break;
        default:
            throw new LibraryException(
                    "bad frequency specified: " + frequency_ + ", only semi-annual through daily permitted.");
        }
    }

    /**
     * Compute the seasonality-adjusted rate.
     *
     * <p>Mirrors C++ {@code MultiplicativePriceSeasonality::seasonalityCorrection}.
     */
    protected /*@Rate*/ double seasonalityCorrection(final @Rate double rate, final Date atDate, final DayCounter dc,
            final Date curveBaseDate, final boolean isZeroRate) {
        // Need _two_ corrections to get:
        //   seasonality = factor[atDate-seasonalityBase] / factor[reference-seasonalityBase]
        // i.e. for ZERO inflation rates you have the true fixing at the curve
        //   base, so this factor must be normalized to one
        //      for YoY inflation rates your reference point is the year before.
        final double factorAt = seasonalityFactor(atDate);

        double f;
        if ( isZeroRate ) {
            final double factorBase = seasonalityFactor(curveBaseDate);
            final double seasonalityAt = factorAt / factorBase;
            final Pair< Date, Date > p = InflationTermStructure.inflationPeriod(atDate, frequency_);
            final @Time double timeFromCurveBase = dc.yearFraction(curveBaseDate, p.first());
            f = JQuantMath.pow(seasonalityAt, 1.0 / timeFromCurveBase);
        } else {
            final double factor1Ybefore = seasonalityFactor(atDate.sub(new Period(1, TimeUnit.Years)));
            f = factorAt / factor1Ybefore;
        }
        return (rate + 1.0) * f - 1.0;
    }
}
