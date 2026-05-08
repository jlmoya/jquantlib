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
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.InflationTermStructure;
import org.jquantlib.termstructures.YoYInflationTermStructure;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.util.Pair;

/**
 * Base class for year-on-year inflation indices.
 *
 * <p>These may be genuine indices published on, say, Bloomberg, or
 * "fake" indices that are defined as the ratio of an index at different time
 * points.
 *
 * <p>Mirrors C++ v1.42.1 {@code QuantLib::YoYInflationIndex}
 * ({@code ql/indexes/inflationindex.{hpp,cpp}}). C++ exposes the class as
 * concrete; the Java port follows suit (Phase 2q L0 A.1) so that
 * {@link #clone(Handle)} can return a new YoYInflationIndex with the supplied
 * curve handle without requiring a concrete subclass.
 *
 * @author Tim Blackler
 *
 */
// TODO: code review :: license, class comments, comments for access modifiers, comments for @Override
public class YoYInflationIndex extends InflationIndex {

    private Handle<YoYInflationTermStructure> yoyInflation;
    private boolean ratio;

    /**
     * Underlying {@link ZeroInflationIndex} for ratio-style YoY indices built via
     * {@link #YoYInflationIndex(ZeroInflationIndex)} or
     * {@link #YoYInflationIndex(ZeroInflationIndex, Handle)}.
     * {@code null} for quoted (ratio=false) YoY indices.
     */
    private ZeroInflationIndex underlyingIndex;

    public YoYInflationIndex(final String familyName,
			 final Region region,
			 final boolean revised,
			 final boolean interpolated,
			 final boolean ratio, // is this one a genuine index or a ratio?
			 final Frequency frequency,
			 final Period availabilityLag,
			 final Currency currency) {
    	this(familyName, region, revised, interpolated, ratio, frequency, availabilityLag, currency, new Handle<YoYInflationTermStructure	>());

    }

    public YoYInflationIndex(final String familyName,
            				 final Region region,
            				 final boolean revised,
            				 final boolean interpolated,
            				 final boolean ratio, // is this one a genuine index or a ratio?
            				 final Frequency frequency,
            				 final Period availabilityLag,
            				 final Currency currency,
            				 final Handle<YoYInflationTermStructure> yoyInflation) {
    	super(familyName, region, revised, interpolated, frequency, availabilityLag, currency);
    	this.ratio = ratio;
    	this.underlyingIndex = null;
    	this.yoyInflation = yoyInflation;
    	this.yoyInflation.addObserver(this);
    }

    /**
     * Constructor for ratio-style year-on-year indices derived from a
     * {@link ZeroInflationIndex}.
     *
     * <p>Mirrors C++ v1.42.1
     * {@code YoYInflationIndex::YoYInflationIndex(const ext::shared_ptr<ZeroInflationIndex>&,
     * Handle<YoYInflationTermStructure>)}
     * ({@code ql/indexes/inflationindex.cpp:253-261}).
     *
     * <p>The YoY index name is {@code "YYR_" + underlying.familyName()},
     * prefixed by the region, matching C++:
     * {@code "YYR_" + underlyingIndex->familyName()}.
     * All index metadata (region, revised, frequency, availabilityLag, currency)
     * are copied from the underlying index.  {@code ratio_} is set to
     * {@code true} so that {@link #fixing(Date)} reads from the underlying
     * index's stored fixings via the ratio formula.
     *
     * @param underlying  the zero-inflation index whose past CPI levels are
     *                    divided to produce the YoY rate
     * @param ts          optional YoY term structure for forecast fixings
     */
    public YoYInflationIndex(final ZeroInflationIndex underlying,
                             final Handle<YoYInflationTermStructure> ts) {
        super("YYR_" + underlying.familyName(),
              underlying.region(),
              underlying.revised(),
              /*interpolated=*/ false,
              underlying.frequency(),
              underlying.availabilityLag(),
              underlying.currency());
        this.ratio = true;
        this.underlyingIndex = underlying;
        this.yoyInflation = ts;
        this.yoyInflation.addObserver(this);
        underlying.addObserver(this);
    }

    /**
     * Convenience constructor for ratio-style YoY indices (no YoY term structure).
     *
     * <p>Mirrors C++ v1.42.1 default-ts overload
     * ({@code inflationindex.cpp:253-261}).
     *
     * @param underlying  the zero-inflation index
     */
    public YoYInflationIndex(final ZeroInflationIndex underlying) {
        this(underlying, new Handle<YoYInflationTermStructure>());
    }

    /**
     * Return a new {@link YoYInflationIndex} that is a copy of this one with
     * the supplied {@link Handle} replacing the internal year-on-year
     * inflation term-structure handle.
     *
     * <p>Mirrors C++ v1.42.1 {@code YoYInflationIndex::clone(const
     * Handle<YoYInflationTermStructure>&)} ({@code inflationindex.cpp:391-396}).
     *
     * <p>For ratio-style indices built via
     * {@link #YoYInflationIndex(ZeroInflationIndex)}, delegates to the
     * ZII-based constructor (mirrors the C++ {@code ratio_=true} branch).
     * For quoted YoY indices, copies all metadata and the ratio flag.
     *
     * @param h the new year-on-year term-structure handle
     * @return  a new {@code YoYInflationIndex} linked to the supplied handle
     */
    public YoYInflationIndex clone(final Handle<YoYInflationTermStructure> h) {
        if (ratio && underlyingIndex != null) {
            return new YoYInflationIndex(underlyingIndex, h);
        }
        return new YoYInflationIndex(familyName, region, revised, interpolated,
                ratio, frequency, availabilityLag, currency, h);
    }

    /**
     * Return the underlying {@link ZeroInflationIndex} for ratio-style indices,
     * or {@code null} for quoted YoY indices.
     *
     * <p>Mirrors C++ v1.42.1 {@code YoYInflationIndex::underlyingIndex()}
     * ({@code ql/indexes/inflationindex.hpp:329-330}).
     */
    public ZeroInflationIndex underlyingIndex() {
        return underlyingIndex;
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

    		// Mirrors C++ v1.42.1 YoYInflationIndex::pastFixing (inflationindex.cpp).
    		if (ratio) {
    		    if (underlyingIndex != null) {
    		        // ratio==true with ZeroInflationIndex underlying (A.3 path):
    		        // compute YoY as ratio of underlying CPI levels, mirroring C++
    		        // YoYInflationIndex::pastFixing (inflationindex.cpp:331-341).
    		        //
    		        // C++ uses CPI::laggedFixing(underlyingIndex_, fixingDate, 0M, CPI::Flat)
    		        // which evaluates to: underlyingIndex_->fixing(inflationPeriod(fixingDate,freq).first)
    		        final Pair<Date,Date> curPeriod =
    		                InflationTermStructure.inflationPeriod(fixingDate, frequency);
    		        final Date prevFixDate = fixingDate.sub(new Period(1, TimeUnit.Years));
    		        final Pair<Date,Date> prevPeriod =
    		                InflationTermStructure.inflationPeriod(prevFixDate, frequency);

    		        final @Rate double curr = underlyingIndex.fixing(curPeriod.first());
    		        final @Rate double prev = underlyingIndex.fixing(prevPeriod.first());
    		        return curr / prev - 1.0;
    		    } else {
    		        // Legacy path: ratio==true but no underlying stored (old-style
    		        // construction with ratio=true flag directly).  Read from this
    		        // index's own time series (pre-Phase-2u behavior).
    		        @Real double pastFixing = IndexManager.getInstance().getHistory(name()).get(fixingDate);
    		        QL.require(!(Double.isNaN(pastFixing)) , "Missing " + name() + " fixing for " + fixingDate);

    		        Date previousDate = fixingDate.sub(new Period(1,TimeUnit.Years));
    		        @Rate double previousFixing = IndexManager.getInstance().getHistory(name()).get(previousDate);
    		        QL.require(!(Double.isNaN(previousFixing)) , "Missing " + name() + " fixing for " + previousDate);

    		        return pastFixing / previousFixing - 1.0;
    		    }

    		} else {
    		    // ratio==false: genuine YoY index — the stored time series
    		    // holds the YoY rate directly.  C++ looks up ts[periodStart]
    		    // and (for interpolated indices) interpolates to ts[periodEnd+1].
    		    // Phase 2r L0 A.3 — aligns with C++ pastFixing ratio_=false branch.
    		    Pair<Date,Date> period = InflationTermStructure.inflationPeriod(fixingDate, frequency);
    		    Date periodStart = period.first();
    		    Date periodEnd   = period.second();

    		    @Rate double YY0 = IndexManager.getInstance().getHistory(name()).get(periodStart);
    		    QL.require(!(Double.isNaN(YY0)) , "Missing " + name() + " fixing for " + periodStart);

    		    if (!interpolated || fixingDate.eq(periodStart)) {
    		        return YY0;
    		    } else {
    		        // Linearly interpolate between period start and period end + 1.
    		        Date periodEndP1 = periodEnd.inc();
    		        @Rate double YY1 = IndexManager.getInstance().getHistory(name()).get(periodEndP1);
    		        QL.require(!(Double.isNaN(YY1)) , "Missing " + name() + " fixing for " + periodEndP1);

    		        double dp = periodEnd.inc().serialNumber() - periodStart.serialNumber();
    		        double dl = fixingDate.serialNumber()     - periodStart.serialNumber();
    		        return YY0 + (YY1 - YY0) * dl / dp;
    		    }
    		}

    	} else {
    		return forecastFixing(fixingDate);
    	}
    }    
    
    /**
     * Return the date of the last stored fixing, adjusted to the first day of
     * the corresponding inflation period.
     *
     * <p>For ratio-style indices ({@code ratio=true}) built via
     * {@link #YoYInflationIndex(ZeroInflationIndex)}, delegates to the
     * underlying {@link ZeroInflationIndex}.  For quoted YoY indices
     * ({@code ratio=false}), reads the last key from this index's own
     * time series.
     *
     * <p>Mirrors C++ v1.42.1 {@code YoYInflationIndex::lastFixingDate()}
     * ({@code ql/indexes/inflationindex.cpp:287-297}).
     *
     * @throws IllegalArgumentException if no fixings are stored
     */
    public Date lastFixingDate() {
        if (ratio && underlyingIndex != null) {
            return underlyingIndex.lastFixingDate();
        }
        final org.jquantlib.time.TimeSeries<Double> fixings = timeSeries();
        QL.require(!fixings.isEmpty(), "no fixings stored for " + name());
        // attribute fixing to first day of the underlying inflation period
        return InflationTermStructure.inflationPeriod(fixings.lastKey(), frequency).first();
    }

    public Handle<YoYInflationTermStructure> yoyInflationTermStructure() {
    	return yoyInflation;
    }
    
    public boolean ratio() {
        return ratio;
    }
    
    private /* @Rate */ double forecastFixing(final Date fixingDate) {
        // if the value is not interpolated get the value for
        // half way along the period.
        Date d = fixingDate;
        
        if (!interpolated()) {
            Pair<Date,Date> lim = InflationTermStructure.inflationPeriod(fixingDate, frequency);
            int n = (int)(lim.second().sub(lim.first())) / 2;
    		d = lim.first().add(n);
        }

        return yoyInflation.currentLink().yoyRate(d);
    }
       
}
