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

import java.util.List;

import org.jquantlib.currencies.Currency;
import org.jquantlib.indexes.Index;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;

/**
 * Base commodity-index class.
 * <p>
 * Java port of QuantLib v1.42.1 {@code commodityindex.{hpp,cpp}}.
 * <p>
 * The {@code registerWith(Settings::evaluationDate())} call from C++ is
 * intentionally omitted here; the Java {@link Index} base already exposes
 * the {@link org.jquantlib.util.Observable} interface, and observer
 * registration is done by the consumer where needed.
 */
public class CommodityIndex extends Index {

    private final String name_;
    private final CommodityType commodityType_;
    private final UnitOfMeasure unitOfMeasure_;
    private final Currency currency_;
    private final Calendar calendar_;
    private final double lotQuantity_;
    private final CommodityCurve forwardCurve_;
    private final List<ExchangeContract> exchangeContracts_;
    private final int nearbyOffset_;
    private double forwardCurveUomConversionFactor_;

    public CommodityIndex(final String name,
                          final CommodityType commodityType,
                          final Currency currency,
                          final UnitOfMeasure unitOfMeasure,
                          final Calendar calendar,
                          final double lotQuantity,
                          final CommodityCurve forwardCurve,
                          final List<ExchangeContract> exchangeContracts,
                          final int nearbyOffset) {
        this.name_ = name;
        this.commodityType_ = commodityType;
        this.unitOfMeasure_ = unitOfMeasure;
        this.currency_ = currency;
        this.calendar_ = calendar;
        this.lotQuantity_ = lotQuantity;
        this.forwardCurve_ = forwardCurve;
        this.exchangeContracts_ = exchangeContracts;
        this.nearbyOffset_ = nearbyOffset;
        this.forwardCurveUomConversionFactor_ = 1.0;
        if (forwardCurve_ != null) {
            this.forwardCurveUomConversionFactor_ =
                    CommodityPricingHelper.calculateUomConversionFactor(
                            commodityType_,
                            forwardCurve_.unitOfMeasure(),
                            unitOfMeasure_);
        }
    }

    @Override
    public String name() {
        return name_;
    }

    @Override
    public Calendar fixingCalendar() {
        return calendar_;
    }

    @Override
    public boolean isValidFixingDate(final Date fixingDate) {
        return fixingCalendar().isBusinessDay(fixingDate);
    }

    /**
     * The C++ implementation returns {@code pastFixing(date)} unconditionally
     * (no forecasting). We mirror that by reading the registered time
     * series; if no fixing exists, return NaN.
     */
    @Override
    public double fixing(final Date fixingDate, final boolean forecastTodaysFixing) {
        final org.jquantlib.time.TimeSeries<Double> ts = timeSeries();
        if (ts == null) return Double.NaN;
        final Double f = ts.get(fixingDate);
        return f == null ? Double.NaN : f.doubleValue();
    }

    /** C++ {@code void update() override}; not on Java {@link Index} (which is
     *  Observable, not Observer), so we expose it as a plain helper. */
    public void update() {
        notifyObservers();
    }

    public CommodityType commodityType() {
        return commodityType_;
    }

    public UnitOfMeasure unitOfMeasure() {
        return unitOfMeasure_;
    }

    public Currency currency() {
        return currency_;
    }

    public double lotQuantity() {
        return lotQuantity_;
    }

    public CommodityCurve forwardCurve() {
        return forwardCurve_;
    }

    public double forwardPrice(final Date date) {
        try {
            final double fp = forwardCurve_.price(date, exchangeContracts_, nearbyOffset_);
            return fp * forwardCurveUomConversionFactor_;
        } catch (final RuntimeException e) {
            throw new LibraryException("error fetching forward price for index " + name_
                    + ": " + e.getMessage(), e);
        }
    }

    public Date lastQuoteDate() {
        final org.jquantlib.time.TimeSeries<Double> ts = timeSeries();
        if (ts == null || ts.size() == 0) {
            return new Date();
        }
        return ts.lastKey();
    }

    public boolean empty() {
        final org.jquantlib.time.TimeSeries<Double> ts = timeSeries();
        return ts == null || ts.size() == 0;
    }

    public boolean forwardCurveEmpty() {
        if (forwardCurve_ != null) {
            return forwardCurve_.empty();
        }
        return false;
    }
}
