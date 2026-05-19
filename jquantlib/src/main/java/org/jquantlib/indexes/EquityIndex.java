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
 Copyright (C) 2023 Marcin Rybacki

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.indexes;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.currencies.Currency;
import org.jquantlib.math.Constants;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.util.Observer;

/**
 * Base class for equity indexes.
 *
 * <p>The equity index object allows to retrieve past fixings, as well as
 * project future fixings using either both the risk-free interest-rate term structure and the dividend term structure,
 * or just the interest-rate term structure (in which case one can provide a term structure of equity forwards implied
 * from, e.g., option prices).
 *
 * <p>In case of the first method, the forward is calculated as
 * <pre>
 *     I(t, T) = I(t, t) * P_D(t, T) / P_R(t, T)
 * </pre>
 * where {@code I(t, t)} is today's value of the index, {@code P_D(t, T)} is a discount factor of the dividend curve at
 * future time {@code T}, and {@code P_R(t, T)} is a discount factor of the risk-free curve.
 *
 * <p>In case of the latter method, the forward is calculated as
 * <pre>
 *     I(t, T) = I(t, t) * 1 / P_F(t, T)
 * </pre>
 * where {@code P_F(t, T)} is a discount factor of the equity-forward term structure (passed in via the {@code interest}
 * handle).
 *
 * <p>Mirrors C++ {@code QuantLib::EquityIndex} at v1.42.1
 * ({@code ql/indexes/equityindex.{hpp,cpp}}).
 *
 * @author JQuantLib migration team (Phase 5d.5-EQ)
 */
public class EquityIndex extends Index implements Observer {

    private final String name_;
    private final Calendar fixingCalendar_;
    private final Currency currency_;
    private final Handle< YieldTermStructure > interest_;
    private final Handle< YieldTermStructure > dividend_;
    private final Handle< ? extends Quote > spot_;

    //
    // public constructors
    //

    public EquityIndex(final String name, final Calendar fixingCalendar, final Currency currency) {
        this(name, fixingCalendar, currency, new Handle< YieldTermStructure >(), new Handle< YieldTermStructure >(),
                new Handle< Quote >());
    }

    public EquityIndex(final String name, final Calendar fixingCalendar, final Currency currency,
            final Handle< YieldTermStructure > interest) {
        this(name, fixingCalendar, currency, interest, new Handle< YieldTermStructure >(), new Handle< Quote >());
    }

    public EquityIndex(final String name, final Calendar fixingCalendar, final Currency currency,
            final Handle< YieldTermStructure > interest, final Handle< YieldTermStructure > dividend) {
        this(name, fixingCalendar, currency, interest, dividend, new Handle< Quote >());
    }

    public EquityIndex(final String name, final Calendar fixingCalendar, final Currency currency,
            final Handle< YieldTermStructure > interest, final Handle< YieldTermStructure > dividend,
            final Handle< ? extends Quote > spot) {
        QL.require(name != null, "name cannot be null");
        QL.require(fixingCalendar != null, "fixingCalendar cannot be null");
        QL.require(currency != null, "currency cannot be null");
        this.name_ = name;
        this.fixingCalendar_ = fixingCalendar;
        this.currency_ = currency;
        this.interest_ = (interest != null) ? interest : new Handle< YieldTermStructure >();
        this.dividend_ = (dividend != null) ? dividend : new Handle< YieldTermStructure >();
        this.spot_ = (spot != null) ? spot : new Handle< Quote >();

        // Mirror C++ registerWith: forward notifications from curves, spot,
        // and the global evaluation date through to this index's observers.
        if ( !this.interest_.empty() ) {
            this.interest_.addObserver(this);
        }
        if ( !this.dividend_.empty() ) {
            this.dividend_.addObserver(this);
        }
        if ( !this.spot_.empty() ) {
            this.spot_.addObserver(this);
        }
        new Settings().evaluationDate().addObserver(this);
        // Per-name notifier (mirrors C++ ql/indexes/indexmanager.cpp wiring;
        // see Phase 5c align in Index.addFixing for details).
        IndexManager.getInstance().notifier(name_).addObserver(this);
    }

    //
    // implements Index
    //

    private static double resolveSpot(final Handle< ? extends Quote > spot, final double lastFixing) {
        QL.require(!spot.empty() || lastFixing != Constants.NULL_REAL,
                "Cannot forecast equity index, missing both spot and historical index");
        return spot.empty() ? lastFixing : spot.currentLink().value();
    }

    @Override
    public String name() {
        return name_;
    }

    @Override
    public Calendar fixingCalendar() {
        return fixingCalendar_;
    }

    @Override
    public boolean isValidFixingDate(final Date fixingDate) {
        return fixingCalendar_.isBusinessDay(fixingDate);
    }

    //
    // public inspectors
    //

    /**
     * Mirrors C++ {@code EquityIndex::fixing(const Date&, bool)} at {@code ql/indexes/equityindex.cpp:51-72}.
     */
    @Override
    public double fixing(final Date fixingDate, final boolean forecastTodaysFixing) {
        QL.require(isValidFixingDate(fixingDate), "Fixing date " + fixingDate + " is not valid");

        final Date today = new Settings().evaluationDate();

        if ( fixingDate.gt(today) || (fixingDate.equals(today) && forecastTodaysFixing) ) {
            return forecastFixing(fixingDate);
        }

        final double result = pastFixing(fixingDate);
        if ( result != Constants.NULL_REAL ) {
            // historical fixing present
            return result;
        }

        if ( fixingDate.equals(today) && !spot_.empty() ) {
            // today's fixing is missing, but spot is provided — use it as proxy
            return spot_.currentLink().value();
        }

        QL.require(false, "Missing " + name() + " fixing for " + fixingDate);
        return Constants.NULL_REAL; // unreachable
    }

    public Currency currency() {
        return currency_;
    }

    /** The rate curve used to forecast fixings. */
    public Handle< YieldTermStructure > equityInterestRateCurve() {
        return interest_;
    }

    /** The dividend curve used to forecast fixings. */
    public Handle< YieldTermStructure > equityDividendCurve() {
        return dividend_;
    }

    //
    // public fixing calculations
    //

    /** Index spot value. */
    public Handle< ? extends Quote > spot() {
        return spot_;
    }

    /**
     * Mirrors C++ {@code EquityIndex::forecastFixing} at {@code ql/indexes/equityindex.cpp:74-90}.
     *
     * <p>Forward = spot * (P_D(today, T) / P_R(today, T)) when both curves
     * present, else spot / P_R(today, T).
     */
    public double forecastFixing(final Date fixingDate) {
        QL.require(!interest_.empty(), "null interest rate term structure set to this instance of " + name());

        final Date today = new Settings().evaluationDate();
        final Date lastFixingDate = fixingCalendar_.adjust(today, BusinessDayConvention.Preceding);

        final double spot = resolveSpot(spot_, pastFixing(lastFixingDate));

        final double iDiscount = interest_.currentLink().discount(fixingDate);
        if ( !dividend_.empty() ) {
            final double dDiscount = dividend_.currentLink().discount(fixingDate);
            return spot * dDiscount / iDiscount;
        }
        return spot / iDiscount;
    }

    //
    // public helpers
    //

    /**
     * Returns a copy of itself linked to different interest, dividend curves or spot quote. Mirrors C++
     * {@code EquityIndex::clone} at {@code ql/indexes/equityindex.cpp:92-97}.
     */
    public EquityIndex clone(final Handle< YieldTermStructure > interest, final Handle< YieldTermStructure > dividend,
            final Handle< ? extends Quote > spot) {
        return new EquityIndex(name_, fixingCalendar_, currency_, interest, dividend, spot);
    }

    /**
     * Look up a historical fixing for {@code fixingDate}. Returns {@link Constants#NULL_REAL} when missing, mirroring
     * the C++ {@code Index::pastFixing} behaviour (returns {@code Null<Real>()}).
     *
     * <p>Public to match the C++ {@code Index::pastFixing(const Date&)}
     * surface area exercised by {@code test-suite/equityindex.cpp}.
     */
    public double pastFixing(final Date fixingDate) {
        QL.require(isValidFixingDate(fixingDate), "Fixing date " + fixingDate + " is not valid");
        final Double v = IndexManager.getInstance().getHistory(name_).get(fixingDate);
        if ( v == null ) {
            return Constants.NULL_REAL;
        }
        return v.doubleValue();
    }

    //
    // implements Observer
    //

    @Override
    public void update() {
        notifyObservers();
    }
}
