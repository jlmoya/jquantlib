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
 */
/*
 Copyright (C) 2007, 2009 Chris Kenyon
 Copyright (C) 2007 StatPro Italia srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.termstructures.inflation;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.CPI;
import org.jquantlib.indexes.ZeroInflationIndex;
import org.jquantlib.instruments.ZeroCouponInflationSwap;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.BootstrapHelper;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.InflationTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.ZeroInflationTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Period;
import org.jquantlib.util.Pair;

/**
 * Bootstrap helper for {@link PiecewiseZeroInflationCurve}, anchored to a
 * synthetic zero-coupon inflation-indexed swap (ZCIIS).
 *
 * <p>Java port of QuantLib v1.42.1 {@code ZeroCouponInflationSwapHelper}
 * (termstructures/inflation/inflationhelpers.cpp:34-206).
 *
 * <h3>Phase 2q L0 A.3 — delegation to ZeroCouponInflationSwap.fairRate()</h3>
 *
 * <p>This helper now mirrors C++ exactly: a private {@code ZeroCouponInflationSwap}
 * (ZCIIS) instance is constructed when the term structure is bound (via
 * {@link #setTermStructure(ZeroInflationTermStructure)}), priced through a
 * {@link DiscountingSwapEngine} on a flat-zero nominal curve (matching the
 * C++ default-overload behavior in
 * {@code inflationhelpers.cpp:48} and {@code :69}), and queried for
 * {@code fairRate()} on each {@link #impliedQuote()} call.
 *
 * <p>The helper's index is cloned with a {@link Handle} pointing at the curve
 * being bootstrapped, so that the ZCIIS routes its inflation-leg forecast
 * through the live curve. This mirrors the C++ pattern
 * {@code zii_ = zii->clone(termStructureHandle_)} from
 * {@code inflationhelpers.cpp:106}.
 *
 * @see PiecewiseZeroInflationCurve
 * @see ZeroCouponInflationSwap
 */
public class ZeroCouponInflationSwapHelper extends BootstrapHelper<ZeroInflationTermStructure> {

    //
    // protected fields
    //

    protected final Period swapObsLag;
    protected final Date startDate;
    protected final Date maturity;
    protected final Calendar calendar;
    protected final BusinessDayConvention paymentConvention;
    protected final DayCounter dayCounter;
    protected final ZeroInflationIndex zii;
    protected final CPI.InterpolationType observationInterpolation;

    /**
     * Lazily-allocated ZCIIS used to compute {@link #impliedQuote()}. Built in
     * {@link #setTermStructure(ZeroInflationTermStructure)} once the curve is
     * known. {@code null} until then.
     */
    private ZeroCouponInflationSwap zciis;

    /**
     * Index cloned to point at the bootstrapping curve handle. Built alongside
     * {@link #zciis} in {@link #setTermStructure(ZeroInflationTermStructure)}.
     * {@code null} until the curve is bound.
     */
    private ZeroInflationIndex ziiClone;

    //
    // public constructors
    //

    /**
     * Construct a ZCIIS bootstrap helper with the swap's effective date
     * defaulted to today's evaluation date. Mirrors the C++ overload that
     * defaults the start date and uses a flat-zero nominal curve internally
     * ({@code inflationhelpers.cpp:34-49}).
     *
     * @param quote                    the swap's fair-rate quote
     * @param swapObsLag               observation lag from index publication (e.g. 3M)
     * @param maturity                 swap maturity date (pre-adjustment)
     * @param calendar                 calendar for payment-date adjustment
     * @param paymentConvention        business-day convention for payments
     * @param dayCounter               day counter for time computations
     * @param zii                      zero-inflation index used for fixings
     * @param observationInterpolation CPI interpolation convention
     */
    public ZeroCouponInflationSwapHelper(
            final Handle<Quote> quote,
            final Period swapObsLag,
            final Date maturity,
            final Calendar calendar,
            final BusinessDayConvention paymentConvention,
            final DayCounter dayCounter,
            final ZeroInflationIndex zii,
            final CPI.InterpolationType observationInterpolation) {
        this(quote, swapObsLag, null, maturity, calendar, paymentConvention,
             dayCounter, zii, observationInterpolation);
    }

    /**
     * Backward-compatible overload — defaults {@code observationInterpolation}
     * to {@link CPI.InterpolationType#AsIndex}.
     *
     * @param quote             the swap's fair-rate quote
     * @param swapObsLag        observation lag from index publication (e.g. 3M)
     * @param maturity          swap maturity date (pre-adjustment)
     * @param calendar          calendar for payment-date adjustment
     * @param paymentConvention business-day convention for payments
     * @param dayCounter        day counter for time computations
     * @param zii               zero-inflation index used for fixings
     */
    public ZeroCouponInflationSwapHelper(
            final Handle<Quote> quote,
            final Period swapObsLag,
            final Date maturity,
            final Calendar calendar,
            final BusinessDayConvention paymentConvention,
            final DayCounter dayCounter,
            final ZeroInflationIndex zii) {
        this(quote, swapObsLag, null, maturity, calendar, paymentConvention,
             dayCounter, zii, CPI.InterpolationType.AsIndex);
    }

    /**
     * Dual-date overload — explicit swap start date and maturity (end) date.
     * Mirrors the C++ overload introduced in v1.42.1
     * ({@code inflationhelpers.cpp:55-68}).
     *
     * <p>Used for sub-annual helpers where the swap effective date differs
     * from the evaluation date (e.g. USCPI CPI::Linear bootstrap tests).
     *
     * @param quote                    the swap's fair-rate quote
     * @param swapObsLag               observation lag from index publication
     * @param startDate                swap effective date ({@code null} ⟹ evaluationDate)
     * @param endDate                  swap maturity date
     * @param calendar                 calendar for payment-date adjustment
     * @param paymentConvention        business-day convention for payments
     * @param dayCounter               day counter for time computations
     * @param zii                      zero-inflation index used for fixings
     * @param observationInterpolation CPI interpolation convention
     */
    public ZeroCouponInflationSwapHelper(
            final Handle<Quote> quote,
            final Period swapObsLag,
            final Date startDate,
            final Date endDate,
            final Calendar calendar,
            final BusinessDayConvention paymentConvention,
            final DayCounter dayCounter,
            final ZeroInflationIndex zii,
            final CPI.InterpolationType observationInterpolation) {
        super(quote);
        this.swapObsLag = swapObsLag;
        this.startDate = (startDate != null) ? startDate : new Settings().evaluationDate();
        this.maturity = endDate;
        this.calendar = calendar;
        this.paymentConvention = paymentConvention;
        this.dayCounter = dayCounter;
        this.zii = zii;
        this.observationInterpolation = observationInterpolation;

        // Compute pillar / earliest / latest as the inflation-period start of
        // the fixing date (matches C++ NoInterpolation branch
        // inflationhelpers.cpp:158-165).
        final Pair<Date, Date> fixingPeriod = InflationTermStructure.inflationPeriod(
                endDate.sub(swapObsLag), zii.frequency());
        this.earliestDate = fixingPeriod.first();
        this.latestDate = fixingPeriod.first();

        zii.addObserver(this);
    }

    //
    // BootstrapHelper hooks
    //

    /**
     * Bind the curve being bootstrapped. Mirrors C++
     * {@code ZeroCouponInflationSwapHelper::setTermStructure}
     * ({@code inflationhelpers.cpp:197-206}) followed by
     * {@code initializeDates()} ({@code inflationhelpers.cpp:186-195}).
     *
     * <p>On each call we (a) record the curve, (b) clone the helper's index
     * with a Handle pointing at the curve, and (c) build a fresh
     * {@link ZeroCouponInflationSwap} priced through a
     * {@link DiscountingSwapEngine} backed by a flat-zero nominal curve.
     */
    @Override
    public void setTermStructure(final ZeroInflationTermStructure ts) {
        super.setTermStructure(ts);

        // C++ wraps the curve in a relinkable handle that the cloned index
        // observes. Java's BootstrapHelper holds the curve as a direct
        // reference; the curve mutates in place during bootstrap. Wrapping it
        // in a non-relinkable Handle is sufficient for the cloned index's
        // forecastFixing path because each call dereferences the same live
        // curve object.
        final Handle<ZeroInflationTermStructure> tsHandle =
                new Handle<ZeroInflationTermStructure>(ts);
        this.ziiClone = zii.clone(tsHandle);

        // Build the ZCIIS exactly as C++ does in initializeDates():
        //   zciis_ = ext::make_shared<ZeroCouponInflationSwap>(
        //       Swap::Payer, 1.0, startDate, maturity, calendar,
        //       paymentConvention, dayCounter, 0.0,
        //       zii_, swapObsLag, observationInterpolation);
        this.zciis = new ZeroCouponInflationSwap(
                ZeroCouponInflationSwap.Type.Payer,
                /*nominal*/ 1.0,
                startDate,
                maturity,
                calendar,
                paymentConvention,
                dayCounter,
                /*fixedRate*/ 0.0,
                ziiClone,
                swapObsLag,
                observationInterpolation);

        // Nominal discount curve: flat zero, matches C++
        // FlatForward(0, NullCalendar(), 0.0, dayCounter)
        // (inflationhelpers.cpp:48 and :69). When computing the fair rate the
        // equal discount factors on the two legs cancel, so any nominal curve
        // gives the same fair rate.
        final FlatForward nominalCurve = new FlatForward(
                ts.referenceDate(), 0.0, dayCounter,
                Compounding.Continuous, Frequency.Annual);
        final Handle<YieldTermStructure> nominalHandle =
                new Handle<YieldTermStructure>(nominalCurve);
        this.zciis.setPricingEngine(new DiscountingSwapEngine(nominalHandle));
    }

    /**
     * Implied ZCIIS fair-rate quote — delegates to
     * {@link ZeroCouponInflationSwap#fairRate()} mirroring C++
     * {@code ZeroCouponInflationSwapHelper::impliedQuote()}
     * ({@code inflationhelpers.cpp:181-184}).
     */
    @Override
    public double impliedQuote() {
        QL.ensure(termStructure != null, "term structure not set");
        QL.ensure(zciis != null, "ZCIIS not initialized; call setTermStructure first");
        // C++ calls zciis_->deepUpdate() before fairRate() to force a fresh
        // calculation. Java's Instrument inherits LazyObject.update() which
        // simply marks the instrument as not-calculated; the next call to
        // fairRate() (which itself calls calculate() under the hood) will
        // re-run the engine. The bootstrap mutates the curve in place between
        // Brent solver steps without firing observer notifications, so we
        // invalidate the cache explicitly here.
        zciis.update();
        return zciis.fairRate();
    }

    //
    // inspectors
    //

    public Period swapObsLag() { return swapObsLag; }
    public Date maturityDate() { return maturity; }
    public ZeroInflationIndex inflationIndex() { return zii; }

    /**
     * Pillar date — the curve node that this helper anchors. For the
     * NoInterpolation case this is the inflation-period start of
     * {@code maturity - swapObsLag}.
     */
    public Date pillarDate() {
        return latestDate;
    }
}
