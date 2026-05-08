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
import org.jquantlib.indexes.YoYInflationIndex;
import org.jquantlib.instruments.YearOnYearInflationSwap;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.BootstrapHelper;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.InflationTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.YoYInflationTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.util.Pair;

/**
 * Bootstrap helper for {@link PiecewiseYoYInflationCurve}, anchored to a
 * synthetic year-on-year inflation-indexed swap (YYIIS).
 *
 * <p>Java port of QuantLib v1.42.1 {@code YearOnYearInflationSwapHelper}
 * ({@code ql/termstructures/inflation/inflationhelpers.cpp:209-346}).
 *
 * <p>Mirrors the C++ delegation pattern (post-Phase 2q L0 A.1):
 * <ol>
 *   <li>Helper clones the YoY index with a {@link Handle} pointing at the
 *       curve being bootstrapped (see Phase 2q L0 A.1
 *       {@link YoYInflationIndex#clone(Handle)}).</li>
 *   <li>On {@link #setTermStructure(YoYInflationTermStructure)}, the helper
 *       builds an internal {@link YearOnYearInflationSwap} priced through a
 *       {@link DiscountingSwapEngine} on a flat-zero nominal curve.</li>
 *   <li>{@link #impliedQuote()} delegates to
 *       {@link YearOnYearInflationSwap#fairRate()}.</li>
 * </ol>
 *
 * @see PiecewiseYoYInflationCurve
 * @see YearOnYearInflationSwap
 */
public class YearOnYearInflationSwapHelper extends BootstrapHelper<YoYInflationTermStructure> {

    //
    // protected fields
    //

    protected final Period swapObsLag;
    protected final Date startDate;
    protected final Date maturity;
    protected final Calendar calendar;
    protected final BusinessDayConvention paymentConvention;
    protected final DayCounter dayCounter;
    protected final YoYInflationIndex yii;
    protected final CPI.InterpolationType interpolation;

    /** Lazily-allocated YYIIS used to compute {@link #impliedQuote()}. */
    private YearOnYearInflationSwap yyiis;

    /** Index cloned to point at the bootstrapping curve handle. */
    private YoYInflationIndex yiiClone;

    //
    // public constructors
    //

    /**
     * Construct a YYIIS bootstrap helper with the swap effective date
     * defaulted to today's evaluation date. Mirrors the C++ overload that
     * defaults the start date.
     *
     * <p>The {@code interpolation} parameter defaults to
     * {@link CPI.InterpolationType#AsIndex} (no interpolation) — matches
     * non-interpolated YoY index conventions.
     */
    public YearOnYearInflationSwapHelper(
            final Handle<Quote> quote,
            final Period swapObsLag,
            final Date maturity,
            final Calendar calendar,
            final BusinessDayConvention paymentConvention,
            final DayCounter dayCounter,
            final YoYInflationIndex yii) {
        this(quote, swapObsLag, maturity, calendar, paymentConvention,
             dayCounter, yii, CPI.InterpolationType.AsIndex);
    }

    public YearOnYearInflationSwapHelper(
            final Handle<Quote> quote,
            final Period swapObsLag,
            final Date maturity,
            final Calendar calendar,
            final BusinessDayConvention paymentConvention,
            final DayCounter dayCounter,
            final YoYInflationIndex yii,
            final CPI.InterpolationType interpolation) {
        super(quote);
        this.swapObsLag = swapObsLag;
        this.startDate = new Settings().evaluationDate();
        this.maturity = maturity;
        this.calendar = calendar;
        this.paymentConvention = paymentConvention;
        this.dayCounter = dayCounter;
        this.yii = yii;
        this.interpolation = interpolation;

        // Compute earliest/latest from inflation period of (maturity - swapObsLag).
        final Pair<Date, Date> fixingPeriod = InflationTermStructure
                .inflationPeriod(maturity.sub(swapObsLag), yii.frequency());
        // Mirror C++ NoInterpolation branch (line 290-291) — pillar at fixingPeriod.first
        this.earliestDate = fixingPeriod.first();
        this.latestDate = fixingPeriod.first();

        yii.addObserver(this);
    }

    //
    // BootstrapHelper hooks
    //

    /**
     * Bind the curve being bootstrapped. Mirrors C++
     * {@code YearOnYearInflationSwapHelper::setTermStructure} +
     * {@code initializeDates()}.
     *
     * <p>Builds an internal {@link YearOnYearInflationSwap} whose YoY leg
     * uses an index cloned with a Handle pointing at this curve. NPV is
     * computed via a flat-zero nominal-curve {@link DiscountingSwapEngine}
     * — when computing the fair rate the equal discount factors on the two
     * legs cancel, so any nominal curve gives the same fair rate.
     */
    @Override
    public void setTermStructure(final YoYInflationTermStructure ts) {
        super.setTermStructure(ts);

        final Handle<YoYInflationTermStructure> tsHandle =
                new Handle<YoYInflationTermStructure>(ts);
        this.yiiClone = yii.clone(tsHandle);

        // Build annual fixed/yoy schedules — both share the same backwards
        // schedule (mirrors C++ initializeDates).
        final Schedule fixedSchedule = YearOnYearInflationSwap.makeDefaultSchedule(
                startDate, maturity, calendar, paymentConvention);
        final Schedule yoySchedule = fixedSchedule;

        this.yyiis = new YearOnYearInflationSwap(
                YearOnYearInflationSwap.Type.Payer,
                /* nominal */ 1.0,
                fixedSchedule,
                /* fixedRate */ 0.0,
                dayCounter,
                yoySchedule,
                yiiClone,
                swapObsLag,
                interpolation,
                /* spread */ 0.0,
                dayCounter,
                calendar,
                paymentConvention);

        // Flat-zero nominal curve — discount factors cancel between legs.
        final FlatForward nominalCurve = new FlatForward(
                ts.referenceDate(), 0.0, dayCounter,
                Compounding.Continuous, Frequency.Annual);
        final Handle<YieldTermStructure> nominalHandle =
                new Handle<YieldTermStructure>(nominalCurve);
        this.yyiis.setPricingEngine(new DiscountingSwapEngine(nominalHandle));
    }

    /**
     * Implied YYIIS fair-rate quote — delegates to
     * {@link YearOnYearInflationSwap#fairRate()} mirroring C++
     * {@code YearOnYearInflationSwapHelper::impliedQuote()}
     * ({@code inflationhelpers.cpp:309-312}).
     */
    @Override
    public double impliedQuote() {
        QL.ensure(termStructure != null, "term structure not set");
        QL.ensure(yyiis != null, "YYIIS not initialized; call setTermStructure first");
        // Force re-pricing in case the curve mutated between Brent steps
        // without firing observer notifications (mirrors C++ deepUpdate()).
        yyiis.update();
        return yyiis.fairRate();
    }

    //
    // inspectors
    //

    public Period swapObsLag() { return swapObsLag; }
    public Date maturityDate() { return maturity; }
    public YoYInflationIndex inflationIndex() { return yii; }

    /**
     * Pillar date — the curve node this helper anchors. For the
     * NoInterpolation case this is the inflation-period start of
     * {@code maturity - swapObsLag}.
     */
    public Date pillarDate() {
        return latestDate;
    }
}
