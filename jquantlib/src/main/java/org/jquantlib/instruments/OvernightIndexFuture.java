/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2018 Roy Zywina
 Copyright (C) 2019 Eisuke Tani

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
*/

package org.jquantlib.instruments;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.cashflow.RateAveraging;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.OvernightIndex;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.Constants;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.TimeSeries;
import org.jquantlib.time.TimeUnit;

/**
 * Future on a compounded (or simple-averaged) overnight index investment.
 * <p>
 * Port of C++ QuantLib v1.42.1
 * {@code ql/instruments/overnightindexfuture.hpp/cpp}.
 *
 * <p>Compatible with SOFR futures and Sonia futures available on CME and ICE
 * exchanges. The price is quoted as {@code 100 * (1 - R)} where {@code R} is
 * either the compounded or simple-averaged overnight rate over the reference
 * period, plus an optional convexity adjustment.
 *
 * @category instruments
 *
 * @author JQuantLib migration team
 */
public class OvernightIndexFuture extends Instrument {

    private final OvernightIndex overnightIndex_;
    private final Date valueDate_;
    private final Date maturityDate_;
    private final Handle<Quote> convexityAdjustment_;
    private final RateAveraging.Type averagingMethod_;

    public OvernightIndexFuture(
            final OvernightIndex overnightIndex,
            final Date valueDate,
            final Date maturityDate) {
        this(overnightIndex, valueDate, maturityDate,
             new Handle<Quote>(), RateAveraging.Type.Compound);
    }

    public OvernightIndexFuture(
            final OvernightIndex overnightIndex,
            final Date valueDate,
            final Date maturityDate,
            final Handle<Quote> convexityAdjustment) {
        this(overnightIndex, valueDate, maturityDate,
             convexityAdjustment, RateAveraging.Type.Compound);
    }

    public OvernightIndexFuture(
            final OvernightIndex overnightIndex,
            final Date valueDate,
            final Date maturityDate,
            final Handle<Quote> convexityAdjustment,
            final RateAveraging.Type averagingMethod) {
        QL.require(overnightIndex != null, "null overnight index");
        this.overnightIndex_ = overnightIndex;
        this.valueDate_ = valueDate;
        this.maturityDate_ = maturityDate;
        this.convexityAdjustment_ =
                (convexityAdjustment == null) ? new Handle<Quote>() : convexityAdjustment;
        this.averagingMethod_ = averagingMethod;

        // Mirrors C++ overnightindexfuture.cpp:36-39 registerWith calls.
        overnightIndex_.addObserver(this);
        if (!convexityAdjustment_.empty()) {
            convexityAdjustment_.addObserver(this);
        }
        new Settings().evaluationDate().addObserver(this);
    }

    /**
     * Average rate over the reference period (simple averaging method).
     * <p>
     * Port of C++ {@code OvernightIndexFuture::averagedRate}.
     */
    private double averagedRate() {
        final Date today = new Settings().evaluationDate();
        final Calendar calendar = overnightIndex_.fixingCalendar();
        final DayCounter dayCounter = overnightIndex_.dayCounter();
        final Handle<YieldTermStructure> forwardCurve = overnightIndex_.termStructure();
        double avg = 0.0;
        Date d1 = valueDate_;
        // d1 could be a holiday
        Date fixingDate = calendar.adjust(d1, BusinessDayConvention.Preceding);
        final TimeSeries<Double> history = overnightIndex_.timeSeries();
        double fwd;
        while (d1.lt(maturityDate_)) {
            final Date d2 = calendar.advance(d1, 1, TimeUnit.Days);
            if (fixingDate.lt(today)) {
                final Double v = history.get(fixingDate);
                QL.require(v != null && v != Constants.NULL_REAL,
                        "missing rate on " + fixingDate
                        + " for index " + overnightIndex_.name());
                fwd = v;
            } else if (fixingDate.equals(today)) {
                final Double v = history.get(fixingDate);
                if (v == null || v == Constants.NULL_REAL) {
                    fwd = forwardCurve.currentLink().forwardRate(
                            fixingDate, d2, dayCounter, Compounding.Simple).rate();
                } else {
                    fwd = v;
                }
            } else {
                fwd = forwardCurve.currentLink().forwardRate(
                        fixingDate, d2, dayCounter, Compounding.Simple).rate();
            }
            // The rate is accrued starting from d1 even when the fixing date
            // is earlier. d2 might be beyond the maturity date if the latter
            // is a holiday.
            final Date upper = d2.lt(maturityDate_) ? d2 : maturityDate_;
            avg += fwd * dayCounter.yearFraction(d1, upper);
            fixingDate = d2;
            d1 = d2;
        }

        return avg / dayCounter.yearFraction(valueDate_, maturityDate_);
    }

    /**
     * Compounded rate over the reference period.
     * <p>
     * Port of C++ {@code OvernightIndexFuture::compoundedRate}.
     */
    private double compoundedRate() {
        Date today = new Settings().evaluationDate();
        final Calendar calendar = overnightIndex_.fixingCalendar();
        final DayCounter dayCounter = overnightIndex_.dayCounter();
        final Handle<YieldTermStructure> forwardCurve = overnightIndex_.termStructure();
        double prod = 1.0;
        Date forwardDiscountStart = valueDate_;
        if (today.gt(valueDate_)) {
            // can't value on a weekend inside reference period because we
            // won't know the reset rate until start of next business day.
            today = calendar.adjust(today);
            forwardDiscountStart = today;
            // for valuations inside the reference period, index quotes
            // must have been populated in the history
            final TimeSeries<Double> history = overnightIndex_.timeSeries();
            Date d1 = valueDate_;
            // d1 could be a holiday
            Date fixingDate = calendar.adjust(d1, BusinessDayConvention.Preceding);
            while (d1.lt(today)) {
                final Double rBoxed = history.get(fixingDate);
                QL.require(rBoxed != null && rBoxed != Constants.NULL_REAL,
                        "missing rate on " + fixingDate
                        + " for index " + overnightIndex_.name());
                final double r = rBoxed;
                final Date d2 = calendar.advance(d1, 1, TimeUnit.Days);
                // The rate is accrued starting from d1 even when the fixing
                // date is earlier. We can't get to the maturity date inside
                // this loop, so we don't need to cap d2 like we do in
                // averagedRate above.
                prod *= 1.0 + r * dayCounter.yearFraction(d1, d2);
                fixingDate = d2;
                d1 = d2;
            }
            // here d1 == today, and we might have today's fixing already
            if (today.lt(maturityDate_)) {
                final Double rBoxed = history.get(today);
                if (rBoxed != null && rBoxed != Constants.NULL_REAL) {
                    final double r = rBoxed;
                    final Date tomorrow = calendar.advance(today, 1, TimeUnit.Days);
                    prod *= 1.0 + r * dayCounter.yearFraction(today, tomorrow);
                    forwardDiscountStart = tomorrow;
                }
            }
        }
        // the telescopic part goes from the end of the last known fixing to
        // the maturity
        final double forwardDiscount =
                forwardCurve.currentLink().discount(maturityDate_)
                / forwardCurve.currentLink().discount(forwardDiscountStart);
        prod /= forwardDiscount;

        return (prod - 1.0) / dayCounter.yearFraction(valueDate_, maturityDate_);
    }

    private double rate() {
        switch (averagingMethod_) {
        case Simple:
            return averagedRate();
        case Compound:
            return compoundedRate();
        default:
            throw new LibraryException(
                    "unknown compounding convention (" + averagingMethod_ + ")");
        }
    }

    public double convexityAdjustment() {
        return convexityAdjustment_.empty() ? 0.0
                : convexityAdjustment_.currentLink().value();
    }

    public OvernightIndex overnightIndex() {
        return overnightIndex_;
    }

    public Date valueDate() {
        return valueDate_;
    }

    public Date maturityDate() {
        return maturityDate_;
    }

    @Override
    public boolean isExpired() {
        // C++ uses detail::simple_event(maturityDate_).hasOccurred().
        // The Java port does not yet have an Event abstraction here; replicate
        // the contract: an event has occurred when its date is strictly before
        // the evaluation date (with the customary "include reference date"
        // option treated as the default true → strict <).
        final Date today = new Settings().evaluationDate();
        return maturityDate_.le(today);
    }

    @Override
    protected void performCalculations() {
        // Bypass the PricingEngine machinery: this instrument computes its
        // own NPV (mirrors C++ overnightindexfuture.cpp:142-145).
        final double R = convexityAdjustment() + rate();
        this.NPV = 100.0 * (1.0 - R);
        this.errorEstimate = 0.0;
    }

    @Override
    protected void setupArguments(final PricingEngine.Arguments a) {
        // No-op: not used (we override performCalculations directly).
    }

    @Override
    protected void fetchResults(final PricingEngine.Results r) {
        // No-op: not used (we override performCalculations directly).
    }
}
