/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2018 Roy Zywina
 Copyright (C) 2019, 2020 Eisuke Tani

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

package org.jquantlib.termstructures.yieldcurves;

import org.jquantlib.QL;
import org.jquantlib.cashflow.RateAveraging;
import org.jquantlib.indexes.OvernightIndex;
import org.jquantlib.indexes.ibor.Sofr;
import org.jquantlib.instruments.OvernightIndexFuture;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.Pillar;
import org.jquantlib.termstructures.RateHelper;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.*;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;

/**
 * Rate helper for bootstrapping over overnight-compounded (or averaged) futures.
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/termstructures/yield/overnightindexfutureratehelper.hpp/cpp}.
 *
 * @author JQuantLib migration team
 * @category termstructures
 */
public class OvernightIndexFutureRateHelper extends RateHelper {

    private final RelinkableHandle< YieldTermStructure > termStructureHandle_ = new RelinkableHandle< YieldTermStructure >(
            null);
    /**
     * Pillar date for bootstrap-node placement. Mirrors C++ {@code BootstrapHelper::pillarDate_}, which defaults to
     * {@code latestDate} but can be overridden via the {@link Pillar.Choice} ctor parameter.
     */
    protected Date pillarDate_;
    private final OvernightIndexFuture future_;

    public OvernightIndexFutureRateHelper(final Handle< Quote > price, final Date valueDate, final Date maturityDate,
            final OvernightIndex overnightIndex) {
        this(price, valueDate, maturityDate, overnightIndex, new Handle< Quote >(), RateAveraging.Type.Compound,
                Pillar.Choice.LastRelevantDate, new Date());
    }

    public OvernightIndexFutureRateHelper(final Handle< Quote > price, final Date valueDate, final Date maturityDate,
            final OvernightIndex overnightIndex, final Handle< Quote > convexityAdjustment) {
        this(price, valueDate, maturityDate, overnightIndex, convexityAdjustment, RateAveraging.Type.Compound,
                Pillar.Choice.LastRelevantDate, new Date());
    }

    public OvernightIndexFutureRateHelper(final Handle< Quote > price, final Date valueDate, final Date maturityDate,
            final OvernightIndex overnightIndex, final Handle< Quote > convexityAdjustment,
            final RateAveraging.Type averagingMethod) {
        this(price, valueDate, maturityDate, overnightIndex, convexityAdjustment, averagingMethod,
                Pillar.Choice.LastRelevantDate, new Date());
    }

    public OvernightIndexFutureRateHelper(final Handle< Quote > price, final Date valueDate, final Date maturityDate,
            final OvernightIndex overnightIndex, final Handle< Quote > convexityAdjustment,
            final RateAveraging.Type averagingMethod, final Pillar.Choice pillar, final Date customPillarDate) {
        super(price);

        // Clone the index so its forwarding curve is the bootstrap curve
        // (termStructureHandle_), not whatever the caller supplied. Mirrors
        // C++ overnightindexfutureratehelper.cpp:57-58.
        final OvernightIndex clonedIndex = (OvernightIndex) overnightIndex.clone(termStructureHandle_).currentLink();

        final Handle< Quote > conv = (convexityAdjustment == null) ? new Handle< Quote >() : convexityAdjustment;
        future_ = new OvernightIndexFuture(clonedIndex, valueDate, maturityDate, conv, averagingMethod);

        // Mirrors C++ overnightindexfutureratehelper.cpp:60
        // `registerWithObservables(future_)` — registers `this` with the
        // observables that `future_` already observes, NOT with the future
        // itself. This avoids a notification loop during bootstrap: were the
        // helper to observe the future directly, future.recalculate() inside
        // impliedQuote() would re-notify the helper → curve → resetting
        // curve.calculated=false mid-iteration → infinite recursion.
        clonedIndex.addObserver(this);
        if ( !conv.empty() ) {
            conv.addObserver(this);
        }
        new org.jquantlib.Settings().evaluationDate().addObserver(this);

        earliestDate = valueDate;
        latestDate = maturityDate;
        switch ( pillar ) {
        case MaturityDate:
            pillarDate_ = maturityDate;
            break;
        case LastRelevantDate:
            pillarDate_ = latestDate;
            break;
        case CustomDate:
            QL.require(customPillarDate != null && !customPillarDate.isNull(), "custom pillar date must be provided");
            QL.require(customPillarDate.ge(earliestDate), "custom pillar date before start of reference period");
            QL.require(customPillarDate.le(latestDate), "custom pillar date after end of reference period");
            pillarDate_ = customPillarDate;
            break;
        default:
            throw new LibraryException("unknown Pillar::Choice");
        }
    }

    private static Date getSofrStart(final Month month, final int year, final Frequency freq) {
        return freq == Frequency.Monthly
                ? new Date(1, month, year)
                : Date.nthWeekday(3, Weekday.Wednesday, month, year);
    }

    private static Date getSofrEnd(final Month month, final int year, final Frequency freq) {
        if ( freq == Frequency.Monthly ) {
            return Date.endOfMonth(new Date(1, month, year)).add(1);
        } else {
            final Date d = getSofrStart(month, year, freq).add(new Period(freq));
            return Date.nthWeekday(3, Weekday.Wednesday, d.month(), d.year());
        }
    }

    /**
     * Returns the pillar date used as the curve-node anchor for this helper.
     * <p>
     * Mirrors C++ {@code BootstrapHelper::pillarDate()}.
     */
    public Date pillarDate() {
        return pillarDate_;
    }

    @Override
    public double impliedQuote() {
        future_.recalculate();
        return future_.NPV();
    }

    @Override
    public void setTermStructure(final YieldTermStructure t) {
        // do not set the relinkable handle as an observer — force
        // recalculation when needed (C++
        // overnightindexfutureratehelper.cpp:93-101).
        termStructureHandle_.linkTo(t, false);
        super.setTermStructure(t);
    }

    // -------------------------------------------------------------------------
    //  Static helpers — period bounds for CME SOFR futures
    //  (anonymous namespace in C++ overnightindexfutureratehelper.cpp:28-41)
    // -------------------------------------------------------------------------

    public double convexityAdjustment() {
        return future_.convexityAdjustment();
    }

    @Override
    public void accept(final PolymorphicVisitor pv) {
        final Visitor< OvernightIndexFutureRateHelper > v = (pv != null) ? pv.visitor(this.getClass()) : null;
        if ( v != null ) {
            v.visit(this);
        } else {
            super.accept(pv);
        }
    }

    /**
     * Rate helper for bootstrapping over CME SOFR futures.
     * <p>
     * It compounds (Quarterly) or simple-averages (Monthly) overnight SOFR rates from the third Wednesday of the
     * reference month/year (inclusive) to the third Wednesday of the month one Month/Quarter later (exclusive).
     *
     * <p>It requires the index history to be populated when the reference
     * period starts in the past.
     *
     * <p>Port of C++ QuantLib v1.42.1 {@code SofrFutureRateHelper}.
     */
    public static class SofrFutureRateHelper extends OvernightIndexFutureRateHelper {

        public SofrFutureRateHelper(final Handle< Quote > price, final Month referenceMonth, final int referenceYear,
                final Frequency referenceFreq) {
            this(price, referenceMonth, referenceYear, referenceFreq, new Handle< Quote >(new SimpleQuote(0.0)),
                    Pillar.Choice.LastRelevantDate, new Date());
        }

        public SofrFutureRateHelper(final double price, final Month referenceMonth, final int referenceYear,
                final Frequency referenceFreq) {
            this(new Handle< Quote >(new SimpleQuote(price)), referenceMonth, referenceYear, referenceFreq,
                    new Handle< Quote >(new SimpleQuote(0.0)), Pillar.Choice.LastRelevantDate, new Date());
        }

        public SofrFutureRateHelper(final Handle< Quote > price, final Month referenceMonth, final int referenceYear,
                final Frequency referenceFreq, final Handle< Quote > convexityAdjustment, final Pillar.Choice pillar,
                final Date customPillarDate) {
            super(price, getSofrStart(referenceMonth, referenceYear, referenceFreq),
                    getSofrEnd(referenceMonth, referenceYear, referenceFreq), new Sofr(), convexityAdjustment,
                    referenceFreq == Frequency.Quarterly ? RateAveraging.Type.Compound : RateAveraging.Type.Simple,
                    pillar, customPillarDate);
            QL.require(referenceFreq == Frequency.Quarterly || referenceFreq == Frequency.Monthly,
                    "only monthly and quarterly SOFR futures accepted");
        }

        public SofrFutureRateHelper(final double price, final Month referenceMonth, final int referenceYear,
                final Frequency referenceFreq, final double convexityAdjustment, final Pillar.Choice pillar,
                final Date customPillarDate) {
            this(new Handle< Quote >(new SimpleQuote(price)), referenceMonth, referenceYear, referenceFreq,
                    new Handle< Quote >(new SimpleQuote(convexityAdjustment)), pillar, customPillarDate);
        }
    }
}
