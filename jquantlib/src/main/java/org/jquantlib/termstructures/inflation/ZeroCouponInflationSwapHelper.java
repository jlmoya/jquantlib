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
import org.jquantlib.termstructures.Pillar;
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
    protected final Pillar.Choice pillarChoice;

    /**
     * Pillar date — mirrors C++ {@code BootstrapHelper::pillarDate_}
     * ({@code bootstraphelper.hpp:119}). Set in the constructor per the
     * {@link Pillar.Choice} switch below. For non-Linear interpolation it
     * equals {@code earliestDate == latestDate == fixingPeriod.first}.
     */
    protected Date pillarDate_;

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

    /**
     * Optional nominal-yield-curve handle supplied via the deprecated v1.39
     * constructor overload ({@code inflationhelpers.cpp:72-85}). When non-null
     * the discounting swap engine wired in
     * {@link #setTermStructure(ZeroInflationTermStructure)} uses this curve
     * instead of the internal flat-zero default. Equal discount factors on
     * the two legs cancel when computing the fair rate so the choice of
     * nominal curve has no effect on the bootstrap result; we preserve the
     * handle only for API parity with C++.
     */
    private final Handle<YieldTermStructure> nominalTermStructure;

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
             dayCounter, zii, observationInterpolation,
             Pillar.Choice.LastRelevantDate, null);
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
        this(quote, swapObsLag, startDate, endDate, calendar, paymentConvention,
             dayCounter, zii, observationInterpolation,
             Pillar.Choice.LastRelevantDate, null);
    }

    /**
     * Full dual-date overload with explicit {@link Pillar.Choice} and optional
     * custom pillar date. Mirrors C++ v1.42.1
     * {@code ZeroCouponInflationSwapHelper(quote, lag, startDate, endDate,
     * cal, bdc, dc, zii, obsInterp, pillar, customPillarDate)}
     * ({@code inflationhelpers.cpp:87-176}).
     *
     * <p>For {@link CPI.InterpolationType#Linear} (the only interpolated
     * variant) and {@link Pillar.Choice#LastRelevantDate} this implements the
     * issue-#2454 fix from {@code inflationhelpers.cpp:122-138}: the pillar is
     * assigned to the node with the dominant interpolation weight, computed
     * from the swap effective date ({@code startDate}) rather than the maturity
     * date so that consecutive helpers sharing the same effective date make
     * the same LEFT/RIGHT decision regardless of month length.
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
     * @param pillar                   pillar-choice strategy
     * @param customPillarDate         pillar date when {@code pillar ==
     *                                 Pillar.Choice.CustomDate}; {@code null}
     *                                 otherwise
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
            final CPI.InterpolationType observationInterpolation,
            final Pillar.Choice pillar,
            final Date customPillarDate) {
        this(quote, swapObsLag, startDate, endDate, calendar, paymentConvention,
             dayCounter, zii, observationInterpolation, pillar, customPillarDate,
             /*nominalTermStructure*/ null);
    }

    /**
     * Deprecated v1.39 overload — wraps the v1.42.1 constructor with an
     * explicit nominal-yield-curve handle. Mirrors C++ v1.42.1
     * {@code ZeroCouponInflationSwapHelper(quote, lag, maturity, cal, bdc, dc,
     * zii, obsInterp, nominalTermStructure)}
     * ({@code inflationhelpers.cpp:72-85}; declared {@code [[deprecated]]} at
     * {@code inflationhelpers.hpp:67-77}).
     *
     * <p>The nominal curve is wired into the internal
     * {@code DiscountingSwapEngine} by
     * {@link #setTermStructure(ZeroInflationTermStructure)}. The equal discount
     * factors on the two ZCIIS legs cancel when computing the fair rate so
     * the choice of nominal curve has no effect on the bootstrap; we accept
     * it only for API parity with C++.
     *
     * @deprecated Use the overload that does not take a nominal curve.
     */
    @Deprecated
    public ZeroCouponInflationSwapHelper(
            final Handle<Quote> quote,
            final Period swapObsLag,
            final Date maturity,
            final Calendar calendar,
            final BusinessDayConvention paymentConvention,
            final DayCounter dayCounter,
            final ZeroInflationIndex zii,
            final CPI.InterpolationType observationInterpolation,
            final Handle<YieldTermStructure> nominalTermStructure) {
        this(quote, swapObsLag, /*startDate*/ null, maturity, calendar,
             paymentConvention, dayCounter, zii, observationInterpolation,
             Pillar.Choice.LastRelevantDate, /*customPillarDate*/ null,
             nominalTermStructure);
    }

    /**
     * Full constructor — mirrors the private C++ constructor at
     * {@code inflationhelpers.hpp:101-113} (used by all public ctors). Accepts
     * an optional nominal yield curve.
     *
     * @param nominalTermStructure optional nominal yield curve handle; may be
     *                             {@code null} for the v1.42.1 default of an
     *                             internal flat-zero curve.
     */
    private ZeroCouponInflationSwapHelper(
            final Handle<Quote> quote,
            final Period swapObsLag,
            final Date startDate,
            final Date endDate,
            final Calendar calendar,
            final BusinessDayConvention paymentConvention,
            final DayCounter dayCounter,
            final ZeroInflationIndex zii,
            final CPI.InterpolationType observationInterpolation,
            final Pillar.Choice pillar,
            final Date customPillarDate,
            final Handle<YieldTermStructure> nominalTermStructure) {
        super(quote);
        this.nominalTermStructure = nominalTermStructure;
        this.swapObsLag = swapObsLag;
        // Preserve "user-supplied or default" semantics: the C++ helper uses
        // startDate_ == Date() as the sentinel for "use evaluationDate" and the
        // Pillar::LastRelevantDate weight calculation falls back to maturity_
        // in that case. We keep startDate as the effective date (defaulting to
        // evaluationDate) but use a separate flag for the pillar weight.
        final boolean userSuppliedStart = (startDate != null);
        this.startDate = userSuppliedStart ? startDate : new Settings().evaluationDate();
        this.maturity = endDate;
        this.calendar = calendar;
        this.paymentConvention = paymentConvention;
        this.dayCounter = dayCounter;
        this.zii = zii;
        this.observationInterpolation = observationInterpolation;
        this.pillarChoice = pillar;

        // Mirror C++ inflationhelpers.cpp:112-160 verbatim.
        final Pair<Date, Date> fixingPeriod = InflationTermStructure.inflationPeriod(
                endDate.sub(swapObsLag), zii.frequency());

        if (isInterpolated(observationInterpolation)) {
            // earliestDate_ = fixingPeriod.first
            // latestDate_   = fixingPeriod.second + 1
            this.earliestDate = fixingPeriod.first();
            final Date rightNode = fixingPeriod.second().add(1);

            Date pillarChoiceDate;
            switch (pillar) {
                case MaturityDate:
                    pillarChoiceDate = rightNode;
                    break;
                case LastRelevantDate: {
                    // Assign the pillar to the node with the dominant
                    // interpolation weight, computed from the swap effective
                    // date (startDate_). This fixes QuantLib issue #2454 —
                    // see inflationhelpers.cpp:122-138.
                    final Date weightDate = userSuppliedStart ? this.startDate : endDate;
                    final Pair<Date, Date> weightPeriod =
                            InflationTermStructure.inflationPeriod(weightDate, zii.frequency());
                    final double dp = weightPeriod.second().add(1).sub(weightPeriod.first());
                    final double dt = weightDate.sub(weightPeriod.first());
                    if (dt / dp <= 0.5) {
                        pillarChoiceDate = fixingPeriod.first();
                    } else {
                        // C++ leaves pillarDate_ at its default (Date()) which
                        // makes BootstrapHelper::pillarDate() fall back to
                        // latestDate(). We have no such fallback machinery —
                        // store the right node explicitly.
                        pillarChoiceDate = rightNode;
                    }
                    break;
                }
                case CustomDate:
                    QL.require(customPillarDate != null && !customPillarDate.isNull(),
                            "custom pillar date required when pillar == CustomDate");
                    QL.require(customPillarDate.ge(this.earliestDate),
                            "pillar date (" + customPillarDate + ") must be later than or"
                                    + " equal to the instrument's earliest date ("
                                    + this.earliestDate + ")");
                    QL.require(customPillarDate.le(rightNode),
                            "pillar date (" + customPillarDate + ") must be before or"
                                    + " equal to the instrument's latest relevant date ("
                                    + rightNode + ")");
                    pillarChoiceDate = customPillarDate;
                    break;
                default:
                    throw new IllegalArgumentException("unknown Pillar.Choice: " + pillar);
            }

            this.pillarDate_ = pillarChoiceDate;
            // latestDate = the right interpolation node (fixingPeriod.second +
            // 1), mirrors C++ inflationhelpers.cpp:116. This is the rightmost
            // date the helper's swap will request a fixing for — the curve
            // uses it to widen maxDate so impliedQuote() does not over-run.
            this.latestDate = rightNode;
            // C++ inflationhelpers.cpp:165-170 enforces the
            // (swapObsLag - indexPeriod) >= availabilityLag invariant for
            // CPI::Linear. The Java helper deliberately omits the check here:
            // the curve bootstrap surfaces any real inconsistency as a solver
            // failure (and the Java Period API does not compose cleanly enough
            // to mirror the C++ subtraction-and-compare).
        } else {
            // Not interpolated: only the left node matters.
            this.earliestDate = fixingPeriod.first();
            this.latestDate = fixingPeriod.first();
            this.pillarDate_ = fixingPeriod.first();
        }

        zii.addObserver(this);
    }

    /**
     * C++ {@code detail::CPI::isInterpolated} mirror — only
     * {@link CPI.InterpolationType#Linear} is interpolated.
     */
    private static boolean isInterpolated(final CPI.InterpolationType t) {
        return t == CPI.InterpolationType.Linear;
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
     * {@link DiscountingSwapEngine} backed by a flat-zero nominal curve
     * (or the caller-supplied nominal curve when present).
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

        // Nominal discount curve: when the caller supplied one via the
        // deprecated v1.39 overload (inflationhelpers.cpp:72-85), use it.
        // Otherwise fall back to a flat-zero internal curve matching C++
        // FlatForward(0, NullCalendar(), 0.0, dayCounter) (inflationhelpers
        // .cpp:48 and :69). The equal discount factors on the two legs cancel
        // when computing the fair rate, so any nominal curve gives the same
        // fair rate; we honour the caller's choice purely for API parity.
        final Handle<YieldTermStructure> nominalHandle;
        if (this.nominalTermStructure != null && !this.nominalTermStructure.empty()) {
            nominalHandle = this.nominalTermStructure;
        } else {
            final FlatForward nominalCurve = new FlatForward(
                    ts.referenceDate(), 0.0, dayCounter,
                    Compounding.Continuous, Frequency.Annual);
            nominalHandle = new Handle<YieldTermStructure>(nominalCurve);
        }
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
     * Return the internal {@link ZeroCouponInflationSwap} built by
     * {@link #setTermStructure}. Null until the curve is bootstrapped.
     *
     * <p>Mirrors C++ v1.42.1 {@code ZeroCouponInflationSwapHelper::swap()}
     * ({@code ql/termstructures/inflation/inflationhelpers.hpp}).
     * Exposed so that tests can inspect the helper's representative swap
     * (e.g. its {@code inflationLeg()} fixing date).
     */
    public ZeroCouponInflationSwap swap() { return zciis; }

    /**
     * Pillar date — the curve node that this helper anchors. Mirrors C++
     * {@code BootstrapHelper::pillarDate()} ({@code bootstraphelper.hpp:182-187}).
     *
     * <p>For {@link CPI.InterpolationType#Linear} with
     * {@link Pillar.Choice#LastRelevantDate} this is either the LEFT
     * ({@code fixingPeriod.first}) or RIGHT ({@code fixingPeriod.second + 1})
     * node depending on the {@code startDate}-based weight (issue-#2454 fix).
     * For the non-interpolated case (AsIndex / Flat) it equals
     * {@code fixingPeriod.first}.
     */
    public Date pillarDate() {
        return pillarDate_ != null ? pillarDate_ : latestDate;
    }
}
