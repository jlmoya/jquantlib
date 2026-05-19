/*
 Copyright (C) 2026 JQuantLib

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2008 J. Erik Radmall
*/

package org.jquantlib.experimental.commodities;

import org.jquantlib.currencies.Currency;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.interpolations.Interpolation;
import org.jquantlib.math.interpolations.factories.ForwardFlat;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.termstructures.AbstractTermStructure;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.SortedMap;

/**
 * Commodity term structure.
 * <p>
 * Java port of QuantLib v1.42.1 {@code commoditycurve.{hpp,cpp}}.
 * <p>
 * Stores a set of dates and prices interpolated with a {@link ForwardFlat} scheme. Optionally references a basis curve
 * whose prices are added (after a UoM conversion) when {@link #price(Date, ExchangeContracts, int)} is called.
 */
public class CommodityCurve extends AbstractTermStructure {

    private final String name_;
    private final CommodityType commodityType_;
    private final UnitOfMeasure unitOfMeasure_;
    private final Currency currency_;
    private final List< Date > dates_;
    private List< Double > times_;
    private final List< Double > data_;
    private Interpolation interpolation_;
    private CommodityCurve basisOfCurve_;
    private double basisOfCurveUomConversionFactor_;

    /** Single-curve constructor, prices known at construction. */
    public CommodityCurve(final String name, final CommodityType commodityType, final Currency currency,
            final UnitOfMeasure unitOfMeasure, final Calendar calendar, final List< Date > dates,
            final List< Double > prices, final DayCounter dayCounter) {
        super(dates.get(0), calendar, dayCounter);
        if ( dates.size() <= 1 ) {
            throw new LibraryException("too few dates");
        }
        if ( prices.size() != dates.size() ) {
            throw new LibraryException("dates/prices count mismatch");
        }
        this.name_ = name;
        this.commodityType_ = commodityType;
        this.unitOfMeasure_ = unitOfMeasure;
        this.currency_ = currency;
        this.dates_ = new ArrayList<>(dates);
        this.data_ = new ArrayList<>(prices);
        this.basisOfCurveUomConversionFactor_ = 1.0;
        this.times_ = new ArrayList<>(dates_.size());
        recomputeTimesAndInterpolation(dayCounter);
    }

    public CommodityCurve(final String name, final CommodityType commodityType, final Currency currency,
            final UnitOfMeasure unitOfMeasure, final Calendar calendar, final List< Date > dates,
            final List< Double > prices) {
        this(name, commodityType, currency, unitOfMeasure, calendar, dates, prices, new Actual365Fixed());
    }

    /** Empty-curve constructor; prices set later via {@link #setPrices(SortedMap)}. */
    public CommodityCurve(final String name, final CommodityType commodityType, final Currency currency,
            final UnitOfMeasure unitOfMeasure, final Calendar calendar, final DayCounter dayCounter) {
        super(0, calendar, dayCounter);
        this.name_ = name;
        this.commodityType_ = commodityType;
        this.unitOfMeasure_ = unitOfMeasure;
        this.currency_ = currency;
        this.dates_ = new ArrayList<>();
        this.times_ = new ArrayList<>();
        this.data_ = new ArrayList<>();
        this.basisOfCurveUomConversionFactor_ = 1.0;
    }

    public CommodityCurve(final String name, final CommodityType commodityType, final Currency currency,
            final UnitOfMeasure unitOfMeasure, final Calendar calendar) {
        this(name, commodityType, currency, unitOfMeasure, calendar, new Actual365Fixed());
    }

    public final String name() {
        return name_;
    }

    public final CommodityType commodityType() {
        return commodityType_;
    }

    public final UnitOfMeasure unitOfMeasure() {
        return unitOfMeasure_;
    }

    public final Currency currency() {
        return currency_;
    }

    @Override
    public Date maxDate() {
        return dates_.get(dates_.size() - 1);
    }

    public final List< Double > times() {
        return times_;
    }

    public final List< Date > dates() {
        return dates_;
    }

    public final List< Double > prices() {
        return data_;
    }

    public final boolean empty() {
        return dates_.isEmpty();
    }

    public final CommodityCurve basisOfCurve() {
        return basisOfCurve_;
    }

    /** Replace the price grid; used when the curve was built empty. */
    public void setPrices(final SortedMap< Date, Double > prices) {
        if ( prices.size() <= 1 ) {
            throw new LibraryException("too few prices");
        }
        dates_.clear();
        data_.clear();
        for ( final java.util.Map.Entry< Date, Double > e : prices.entrySet() ) {
            dates_.add(e.getKey());
            data_.add(e.getValue());
        }
        times_ = new ArrayList<>(dates_.size());
        recomputeTimesAndInterpolation(dayCounter());
    }

    /** Set the basis-of curve and pre-compute the UoM conversion factor. */
    public void setBasisOfCurve(final CommodityCurve basisOfCurve) {
        this.basisOfCurve_ = basisOfCurve;
        this.basisOfCurveUomConversionFactor_ = CommodityPricingHelper.calculateUomConversionFactor(commodityType_,
                basisOfCurve_.unitOfMeasure_, unitOfMeasure_);
    }

    /** Get a price (basis curves are added in if present). */
    public double price(final Date d, final List< ExchangeContract > exchangeContracts, final int nearbyOffset) {
        final Date date = nearbyOffset > 0 ? underlyingPriceDate(d, exchangeContracts, nearbyOffset) : d;
        final double t = timeFromReference(date);
        double priceValue;
        try {
            priceValue = priceImpl(t);
        } catch ( final RuntimeException e ) {
            throw new LibraryException("error retrieving price for curve [" + name_ + "]: " + e.getMessage(), e);
        }
        return priceValue + basisOfPriceImpl(t);
    }

    public double basisOfPrice(final Date d) {
        return basisOfPriceImpl(timeFromReference(d));
    }

    /**
     * Find the date associated with a nearby contract.
     * <p>
     * Mirrors C++: walks the lower-bound iterator forward {@code nearbyOffset - 1} steps and returns
     * {@code underlyingStartDate} of that contract.
     */
    public Date underlyingPriceDate(final Date date, final List< ExchangeContract > exchangeContracts,
            final int nearbyOffset) {
        if ( nearbyOffset <= 0 ) {
            throw new LibraryException("nearby offset must be > 0");
        }
        // Java equivalent of C++ lower_bound on a map keyed by date: since
        // we accept a List<ExchangeContract> here (the test fixture is a
        // simple list), walk forward to the first contract whose
        // expirationDate >= date, then advance (nearbyOffset - 1) more.
        final Iterator< ExchangeContract > it = exchangeContracts.iterator();
        ExchangeContract current = null;
        while ( it.hasNext() ) {
            current = it.next();
            if ( current.expirationDate().ge(date) ) {
                break;
            }
            current = null;
        }
        if ( current == null ) {
            return date;
        }
        for ( int i = 0; i < nearbyOffset - 1; ++i ) {
            if ( !it.hasNext() ) {
                throw new LibraryException(
                        "not enough nearby contracts available for curve [" + name_ + "] for date [" + date + "].");
            }
            current = it.next();
        }
        return current.underlyingStartDate();
    }

    private double basisOfPriceImpl(final double t) {
        if ( basisOfCurve_ != null ) {
            double basisCurvePriceValue;
            try {
                basisCurvePriceValue = basisOfCurve_.priceImpl(t) * basisOfCurveUomConversionFactor_;
            } catch ( final RuntimeException e ) {
                throw new LibraryException("error retrieving price for curve [" + name_ + "]: " + e.getMessage(), e);
            }
            return basisCurvePriceValue + basisOfCurve_.basisOfPriceImpl(t);
        }
        return 0.0;
    }

    private double priceImpl(final double t) {
        return interpolation_.op(t, true);
    }

    private void recomputeTimesAndInterpolation(final DayCounter dayCounter) {
        times_.clear();
        times_.add(0.0);
        for ( int i = 1; i < dates_.size(); ++i ) {
            if ( !dates_.get(i).gt(dates_.get(i - 1)) ) {
                throw new LibraryException("invalid date (" + dates_.get(i) + ", vs " + dates_.get(i - 1) + ")");
            }
            times_.add(dayCounter.yearFraction(dates_.get(0), dates_.get(i)));
        }
        final Array vx = new Array(times_.size());
        final Array vy = new Array(data_.size());
        for ( int i = 0; i < times_.size(); ++i ) {
            vx.set(i, times_.get(i));
            vy.set(i, data_.get(i));
        }
        interpolation_ = new ForwardFlat().interpolate(vx, vy);
        interpolation_.update();
    }
}
