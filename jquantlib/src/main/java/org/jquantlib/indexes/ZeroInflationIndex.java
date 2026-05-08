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
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.InflationTermStructure;
import org.jquantlib.termstructures.ZeroInflationTermStructure;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Period;
import org.jquantlib.math.transcendental.JQuantMath;
import org.jquantlib.util.Pair;

/**
 * Base class for zero inflation indices.
 *
 * <p>Mirrors C++ v1.42.1 {@code QuantLib::ZeroInflationIndex}
 * ({@code ql/indexes/inflationindex.{hpp,cpp}}). C++ exposes the class as
 * concrete; the Java port follows suit (Phase 2q L0 A.1) so that
 * {@link #clone(Handle)} can return a new ZCII with the supplied curve handle
 * without requiring a concrete subclass.
 *
 * @author Tim Blackler
 *
 */
// TODO: code review :: license, class comments, comments for access modifiers, comments for @Override
public class ZeroInflationIndex extends InflationIndex {

    private Handle<ZeroInflationTermStructure> zeroInflation;

    public ZeroInflationIndex(final String familyName,
			 final Region region,
			 final boolean revised,
			 final boolean interpolated,
			 final Frequency frequency,
			 final Period availabilityLag,
			 final Currency currency) {
    	this(familyName, region, revised, interpolated, frequency, availabilityLag, currency, new Handle<ZeroInflationTermStructure>());
    }

    public ZeroInflationIndex(final String familyName,
            				 final Region region,
            				 final boolean revised,
            				 final boolean interpolated,
            				 final Frequency frequency,
            				 final Period availabilityLag,
            				 final Currency currency,
            				 final Handle<ZeroInflationTermStructure> zeroInflation) {
    	super(familyName, region, revised, interpolated, frequency, availabilityLag, currency);
    	this.zeroInflation = zeroInflation;
    	this.zeroInflation.addObserver(this);
    }

    /**
     * Return a new {@link ZeroInflationIndex} that is a copy of this one with
     * the supplied {@link Handle} replacing the internal zero-inflation
     * term-structure handle.
     *
     * <p>Mirrors C++ v1.42.1 {@code ZeroInflationIndex::clone(const
     * Handle<ZeroInflationTermStructure>&)} ({@code inflationindex.cpp:240}).
     *
     * @param h the new zero-inflation term-structure handle
     * @return  a new {@code ZeroInflationIndex} sharing this index's
     *          {@code familyName}, {@code region}, {@code revised},
     *          {@code interpolated}, {@code frequency},
     *          {@code availabilityLag} and {@code currency}, but linked to
     *          the supplied curve handle
     */
    public ZeroInflationIndex clone(final Handle<ZeroInflationTermStructure> h) {
        return new ZeroInflationIndex(familyName, region, revised, interpolated,
                frequency, availabilityLag, currency, h);
    }
 
    @Override
    public double fixing(Date fixingDate) {
    	return this.fixing(fixingDate, false);
    }	
    
    @Override
    public double fixing(Date fixingDate, boolean forecastTodaysFixing) {
    	Date today = new Settings().evaluationDate();
    	Date todayMinusLag = today.sub(availabilityLag);
    	
    	Pair<Date,Date> lim = InflationTermStructure.inflationPeriod(todayMinusLag, frequency);
    	todayMinusLag = lim.second().inc();
    	
    	if ((fixingDate.lt(todayMinusLag)) ||
    		(fixingDate.eq(todayMinusLag) && !forecastTodaysFixing)) {
    		
    		@Real double pastFixing = IndexManager.getInstance().getHistory(name()).get(fixingDate);
    		QL.require(!(Double.isNaN(pastFixing)) , "Missing " + name() + " fixing for " + fixingDate);
    		return pastFixing;
    	} else {
    		return forecastFixing(fixingDate);
    	}
    }
    
    public Handle<ZeroInflationTermStructure> zeroInflationTermStructure() {
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

        final Pair<Date, Date> fixingPeriod =
                InflationTermStructure.inflationPeriod(fixingDate, frequency);
        final Date firstDateInPeriod = fixingPeriod.first();

        final @Rate double z1 = zeroInflation.currentLink().zeroRate(firstDateInPeriod);

        // inflationYearFraction(NoInterpolation, dc, baseDate, firstDateInPeriod):
        //   dc.yearFraction(period(baseDate).first, firstDateInPeriod)
        final Pair<Date, Date> baseLim =
                InflationTermStructure.inflationPeriod(baseDate, frequency);
        final @Time double t1 = zeroInflation.currentLink().dayCounter()
                .yearFraction(baseLim.first(), firstDateInPeriod);

        // During bootstrapping, extrapolated rates can temporarily go below -1.
        // Guard against pow of a negative base with non-integer exponent.
        if (z1 <= -1.0) {
            return 0.0;
        }
        return baseFixing * JQuantMath.pow(1.0 + z1, t1);
    }
       
}
