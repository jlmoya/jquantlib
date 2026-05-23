/*
 Copyright (C) 2026 JQuantLib migration contributors

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

package org.jquantlib.termstructures.yieldcurves;

import org.jquantlib.QL;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.JointCalendar;
import org.jquantlib.time.calendars.JointCalendar.JointCalendarRule;

/**
 * Rate helper for bootstrapping over Fx Swap rates.
 * <p>
 * {@code fwdFx = spotFx + fwdPoint}. {@code isFxBaseCurrencyCollateralCurrency} indicates if the base
 * currency of the FX currency pair is the one used as collateral.
 *
 * <p>{@code calendar} is usually the joint calendar of the two currencies in the FX currency pair.
 * If a {@code tradingCalendar} is provided, it is used to compute the spot date and the trade settlement
 * adjustment (e.g. USD bank holidays for non-USD trades).
 *
 * <p>{@code fixingDays} are the number of business days before the trade date to compute the spot date.
 *
 * <p>The other currency in the pair (other than the collateral one) must be discounted using
 * {@code fwdPoints}.
 *
 * <p>Faithful port of QuantLib v1.42.1 {@code FxSwapRateHelper}
 * (ql/termstructures/yield/ratehelpers.{hpp,cpp}).
 *
 * <p>Phase 2 forward closure L2-B.
 */
public class FxSwapRateHelper extends RelativeDateRateHelper {

    private final Handle< Quote > spot_;
    private final Period tenor_;
    private final int fixingDays_;
    private final Calendar cal_;
    private final BusinessDayConvention conv_;
    private final boolean eom_;
    private final boolean isFxBaseCurrencyCollateralCurrency_;

    private final RelinkableHandle< YieldTermStructure > termStructureHandle_ =
            new RelinkableHandle< YieldTermStructure >(null);

    private final Handle< YieldTermStructure > collHandle_;
    private final RelinkableHandle< YieldTermStructure > collRelinkableHandle_ =
            new RelinkableHandle< YieldTermStructure >(null);

    private final Calendar tradingCalendar_;
    private final Calendar jointCalendar_;

    /**
     * Tenor-based ctor (relative-date pillar). Mirrors C++
     * {@code FxSwapRateHelper(fwdPoint, spotFx, tenor, fixingDays, calendar, convention, endOfMonth,
     *                         isFxBaseCurrencyCollateralCurrency, collateralCurve, tradingCalendar = Calendar())}.
     */
    public FxSwapRateHelper(final Handle< Quote > fwdPoint, final Handle< Quote > spotFx, final Period tenor,
            final int fixingDays, final Calendar calendar, final BusinessDayConvention convention,
            final boolean endOfMonth, final boolean isFxBaseCurrencyCollateralCurrency,
            final Handle< YieldTermStructure > collateralCurve, final Calendar tradingCalendar) {
        super(fwdPoint);
        this.spot_ = spotFx;
        this.tenor_ = tenor;
        this.fixingDays_ = fixingDays;
        this.cal_ = calendar;
        this.conv_ = convention;
        this.eom_ = endOfMonth;
        this.isFxBaseCurrencyCollateralCurrency_ = isFxBaseCurrencyCollateralCurrency;
        this.collHandle_ = collateralCurve;
        this.tradingCalendar_ = (tradingCalendar == null) ? new Calendar() : tradingCalendar;

        // C++: registerWith(spot_); registerWith(collHandle_);
        if ( this.spot_ != null ) {
            this.spot_.addObserver(this);
        }
        if ( this.collHandle_ != null ) {
            this.collHandle_.addObserver(this);
        }

        if ( this.tradingCalendar_.empty() ) {
            this.jointCalendar_ = this.cal_;
        } else {
            this.jointCalendar_ = new JointCalendar(this.tradingCalendar_, this.cal_, JointCalendarRule.JoinHolidays);
        }

        initializeDates();
    }

    /** Tenor-based ctor with empty tradingCalendar (C++ default). */
    public FxSwapRateHelper(final Handle< Quote > fwdPoint, final Handle< Quote > spotFx, final Period tenor,
            final int fixingDays, final Calendar calendar, final BusinessDayConvention convention,
            final boolean endOfMonth, final boolean isFxBaseCurrencyCollateralCurrency,
            final Handle< YieldTermStructure > collateralCurve) {
        this(fwdPoint, spotFx, tenor, fixingDays, calendar, convention, endOfMonth,
                isFxBaseCurrencyCollateralCurrency, collateralCurve, new Calendar());
    }

    /**
     * Date-based ctor. Mirrors C++
     * {@code FxSwapRateHelper(fwdPoint, spotFx, startDate, endDate, isFxBaseCurrencyCollateralCurrency, coll)}.
     * {@code updateDates=false} so the caller-supplied absolute dates are not overwritten.
     */
    public FxSwapRateHelper(final Handle< Quote > fwdPoint, final Handle< Quote > spotFx, final Date startDate,
            final Date endDate, final boolean isFxBaseCurrencyCollateralCurrency,
            final Handle< YieldTermStructure > coll) {
        super(fwdPoint, false);
        this.spot_ = spotFx;
        this.tenor_ = null;
        this.fixingDays_ = 0;
        this.cal_ = new Calendar();
        this.conv_ = BusinessDayConvention.Following;
        this.eom_ = false;
        this.isFxBaseCurrencyCollateralCurrency_ = isFxBaseCurrencyCollateralCurrency;
        this.collHandle_ = coll;
        this.tradingCalendar_ = new Calendar();
        this.jointCalendar_ = this.cal_;

        if ( this.spot_ != null ) {
            this.spot_.addObserver(this);
        }
        if ( this.collHandle_ != null ) {
            this.collHandle_.addObserver(this);
        }
        this.earliestDate = startDate;
        this.latestDate = endDate;
    }

    //
    // public inspectors
    //

    public double spot() {
        return spot_.currentLink().value();
    }

    public Period tenor() {
        return tenor_;
    }

    public int fixingDays() {
        return fixingDays_;
    }

    public Calendar calendar() {
        return cal_;
    }

    public BusinessDayConvention businessDayConvention() {
        return conv_;
    }

    public boolean endOfMonth() {
        return eom_;
    }

    public boolean isFxBaseCurrencyCollateralCurrency() {
        return isFxBaseCurrencyCollateralCurrency_;
    }

    public Calendar tradingCalendar() {
        return tradingCalendar_;
    }

    public Calendar adjustmentCalendar() {
        return jointCalendar_;
    }

    //
    // RelativeDateRateHelper overrides
    //

    @Override
    protected void initializeDates() {
        if ( !updateDates ) {
            return;
        }
        // if the evaluation date is not a business day then move to the next business day
        final Date refDate = cal_.adjust(evaluationDate);
        this.earliestDate = cal_.advance(refDate, fixingDays_, TimeUnit.Days);

        if ( !tradingCalendar_.empty() ) {
            // check if fx trade can be settled in US, if not, adjust it
            this.earliestDate = jointCalendar_.adjust(earliestDate);
            this.latestDate = jointCalendar_.advance(earliestDate, tenor_, conv_, eom_);
        } else {
            this.latestDate = cal_.advance(earliestDate, tenor_, conv_, eom_);
        }
    }

    @Override
    public double impliedQuote() {
        QL.require(termStructure != null, "term structure not set");
        QL.require(collHandle_ != null && !collHandle_.empty(), "collateral term structure not set");

        double d1 = collHandle_.currentLink().discount(earliestDate);
        double d2 = collHandle_.currentLink().discount(latestDate);
        final double collRatio = d1 / d2;

        d1 = termStructureHandle_.currentLink().discount(earliestDate);
        d2 = termStructureHandle_.currentLink().discount(latestDate);
        final double ratio = d1 / d2;

        final double spotVal = spot_.currentLink().value();
        if ( isFxBaseCurrencyCollateralCurrency_ ) {
            return (ratio / collRatio - 1.0) * spotVal;
        } else {
            return (collRatio / ratio - 1.0) * spotVal;
        }
    }

    @Override
    public void setTermStructure(final YieldTermStructure t) {
        // do not set the relinkable handle as an observer — force recalculation when needed
        termStructureHandle_.linkTo(t, false);
        // mirror C++: collRelinkableHandle_.linkTo(*collHandle_, observer=false)
        collRelinkableHandle_.linkTo(collHandle_ == null ? null : collHandle_.currentLink(), false);
        super.setTermStructure(t);
    }
}
