/*
 Copyright (C) 2011 Tim Blackler

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

package org.jquantlib.indexes;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.currencies.Currency;
import org.jquantlib.lang.annotation.Rate;
import org.jquantlib.lang.annotation.Real;
import org.jquantlib.lang.annotation.Time;
import org.jquantlib.math.Closeness;
import org.jquantlib.math.Constants;
import org.jquantlib.math.transcendental.JQuantMath;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.InflationTermStructure;
import org.jquantlib.termstructures.ZeroInflationTermStructure;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Period;
import org.jquantlib.util.Pair;

/**
 * Base class for zero inflation indices.
 *
 * <p>Mirrors C++ v1.42.1 {@code QuantLib::ZeroInflationIndex}
 * ({@code ql/indexes/inflationindex.{hpp,cpp}}). C++ exposes the class as concrete; the Java port follows suit (Phase
 * 2q L0 A.1) so that {@link #clone(Handle)} can return a new ZCII with the supplied curve handle without requiring a
 * concrete subclass.
 *
 * @author Tim Blackler
 *
 */
public class ZeroInflationIndex extends InflationIndex {

    private final Handle< ZeroInflationTermStructure > zeroInflation;

    public ZeroInflationIndex(final String familyName, final Region region, final boolean revised,
            final boolean interpolated, final Frequency frequency, final Period availabilityLag,
            final Currency currency) {
        this(familyName, region, revised, interpolated, frequency, availabilityLag, currency,
                new Handle< ZeroInflationTermStructure >());
    }

    public ZeroInflationIndex(final String familyName, final Region region, final boolean revised,
            final boolean interpolated, final Frequency frequency, final Period availabilityLag,
            final Currency currency, final Handle< ZeroInflationTermStructure > zeroInflation) {
        super(familyName, region, revised, interpolated, frequency, availabilityLag, currency);
        this.zeroInflation = zeroInflation;
        this.zeroInflation.addObserver(this);
    }

    /**
     * Return a new {@link ZeroInflationIndex} that is a copy of this one with the supplied {@link Handle} replacing the
     * internal zero-inflation term-structure handle.
     *
     * <p>Mirrors C++ v1.42.1 {@code ZeroInflationIndex::clone(const
     * Handle<ZeroInflationTermStructure>&)} ({@code inflationindex.cpp:240}).
     *
     * @param h the new zero-inflation term-structure handle
     * @return a new {@code ZeroInflationIndex} sharing this index's {@code familyName}, {@code region},
     * {@code revised}, {@code interpolated}, {@code frequency}, {@code availabilityLag} and {@code currency}, but
     * linked to the supplied curve handle
     */
    public ZeroInflationIndex clone(final Handle< ZeroInflationTermStructure > h) {
        return new ZeroInflationIndex(familyName, region, revised, interpolated, frequency, availabilityLag, currency,
                h);
    }

    @Override
    public double fixing(Date fixingDate) {
        return this.fixing(fixingDate, false);
    }

    @Override
    public double fixing(Date fixingDate, boolean forecastTodaysFixing) {
        // Mirrors C++ v1.42.1 ZeroInflationIndex::fixing / needsForecast
        // (ql/indexes/inflationindex.cpp:170-219).
        //
        // C++ needsForecast algorithm:
        //   latestPossibleHistoricalFixingPeriod = inflationPeriod(today - lag, freq)
        //   latestNeededDate = inflationPeriod(fixingDate, freq).first    (non-interpolated)
        //   if latestNeededDate < latestPossibleHistoricalFixingPeriod.first → past (not forecast)
        //   if latestNeededDate > latestPossibleHistoricalFixingPeriod.second → future (forecast)
        //   else → check timeSeries: present → past, absent → forecast
        //
        // The earlier Java port collapsed the boundary branch into "always
        // past, throw if missing", which is wrong for sub-annual CPI::Linear
        // bootstraps where a freshly-published month may not yet be in the
        // user-supplied fixings — C++ falls back to the forecast curve in
        // that case (fixing the QuantLib #2454 family of failures).
        final Date today = new Settings().evaluationDate();
        final Date todayMinusLag = today.sub(availabilityLag);

        final Pair< Date, Date > latestPossible = InflationTermStructure.inflationPeriod(todayMinusLag, frequency);
        // Zero-index fixings are always non-interpolated; latestNeededDate is
        // the start of the fixing-date's inflation period.
        final Date latestNeededDate = InflationTermStructure.inflationPeriod(fixingDate, frequency).first();

        if ( latestNeededDate.lt(latestPossible.first()) ) {
            // strictly historical: must be in stored fixings. Mirrors C++
            // ZeroInflationIndex::pastFixing (inflationindex.cpp:189-194):
            // the lookup key is the inflation-period start, not the raw
            // fixingDate, and a missing entry is reported as a QL_REQUIRE
            // failure rather than an NPE. TimeSeries.get returns null for
            // missing keys (C++ returns Null<Real>() sentinel).
            final Double pastFixing = IndexManager.getInstance().getHistory(name()).get(latestNeededDate);
            QL.require(pastFixing != null
                    && !Double.isNaN(pastFixing)
                    && !Closeness.isClose(pastFixing, Constants.NULL_REAL),
                    "Missing " + name() + " fixing for " + latestNeededDate);
            return pastFixing;
        }
        if ( latestNeededDate.gt(latestPossible.second()) ) {
            // strictly future: forecast through the curve
            return forecastFixing(fixingDate);
        }
        // Boundary range — try stored fixings first; fall back to forecast
        // when missing (mirrors C++ inflationindex.cpp:197-220). C++ keys the
        // lookup by latestNeededDate (the inflation-period start), not the raw
        // fixingDate.
        final Double f = IndexManager.getInstance().getHistory(name()).get(latestNeededDate);
        if ( f != null && !Double.isNaN(f) && !Closeness.isClose(f, Constants.NULL_REAL) ) {
            return f;
        }
        return forecastFixing(fixingDate);
    }

    /**
     * Whether a forecast term-structure is needed to produce the fixing for {@code fixingDate}.
     *
     * <p>Mirrors C++ v1.42.1 {@code ZeroInflationIndex::needsForecast(const Date&)}
     * ({@code ql/indexes/inflationindex.cpp:197-220}).
     *
     * <p>For zero-inflation indices, fixings are always non-interpolated:
     * {@code latestNeededDate = inflationPeriod(fixingDate, frequency).first}. If that date lies strictly before the
     * latest possible historical period's start, the fixing is historical (return {@code false}).  If it lies strictly
     * after the historical period's end, it is a future fixing (return {@code true}).  Otherwise, the stored
     * time-series is consulted: present → not forecast; absent → forecast.
     *
     * @param fixingDate the date for which the fixing is needed
     * @return {@code true} if a forecast term-structure is required
     */
    public boolean needsForecast(final Date fixingDate) {
        final Date today = new Settings().evaluationDate();
        final Date todayMinusLag = today.sub(availabilityLag);
        final Pair< Date, Date > latestPossible = InflationTermStructure.inflationPeriod(todayMinusLag, frequency);
        // Zero-index fixings are always non-interpolated.
        final Date latestNeededDate = InflationTermStructure.inflationPeriod(fixingDate, frequency).first();
        if ( latestNeededDate.lt(latestPossible.first()) ) {
            return false;
        } else if ( latestNeededDate.gt(latestPossible.second()) ) {
            return true;
        } else {
            // In the boundary range: check whether the fixing is stored.
            // TimeSeries.get() returns null when the key is not present.
            final Double f = IndexManager.getInstance().getHistory(name()).get(latestNeededDate);
            return f == null || Double.isNaN(f);
        }
    }

    /**
     * Return the date of the last stored fixing, adjusted to the first day of the corresponding inflation period.
     *
     * <p>Mirrors C++ v1.42.1 {@code ZeroInflationIndex::lastFixingDate()}
     * ({@code ql/indexes/inflationindex.cpp:190-194}).
     *
     * @throws IllegalArgumentException if no fixings are stored for this index
     */
    public Date lastFixingDate() {
        final org.jquantlib.time.TimeSeries< Double > fixings = timeSeries();
        QL.require(!fixings.isEmpty(), "no fixings stored for " + name());
        // attribute fixing to first day of the underlying inflation period
        return InflationTermStructure.inflationPeriod(fixings.lastKey(), frequency).first();
    }

    public Handle< ZeroInflationTermStructure > zeroInflationTermStructure() {
        return zeroInflation;
    }

    private /* @Rate */ double forecastFixing(final Date fixingDate) {
        // Phase 2p A.2 align: match C++ v1.42.1 ZeroInflationIndex::forecastFixing
        // (indexes/inflationindex.cpp lines 219-237).
        //
        // The pre-2p Java port used (a) period(baseDate).second as the time
        // anchor and (b) the mid-period date when uninterpolated. v1.42.1
        // uses (a) period(baseDate).first as anchor and (b) the start of the
        // fixing's inflation period in both cases — the inflationYearFraction
        // (NoInterpolation) convention.
        //
        // The term structure is relative to the fixing at the base date.
        final Date baseDate = zeroInflation.currentLink().baseDate();
        final @Real double baseFixing = fixing(baseDate);

        final Pair< Date, Date > fixingPeriod = InflationTermStructure.inflationPeriod(fixingDate, frequency);
        final Date firstDateInPeriod = fixingPeriod.first();

        final @Rate double z1 = zeroInflation.currentLink().zeroRate(firstDateInPeriod);

        // inflationYearFraction(NoInterpolation, dc, baseDate, firstDateInPeriod):
        //   dc.yearFraction(period(baseDate).first, firstDateInPeriod)
        final Pair< Date, Date > baseLim = InflationTermStructure.inflationPeriod(baseDate, frequency);
        final @Time double t1 = zeroInflation.currentLink().dayCounter()
                .yearFraction(baseLim.first(), firstDateInPeriod);

        // During bootstrapping, extrapolated rates can temporarily go below -1.
        // Guard against pow of a negative base with non-integer exponent.
        if ( z1 <= -1.0 ) {
            return 0.0;
        }
        return baseFixing * JQuantMath.pow(1.0 + z1, t1);
    }

}
