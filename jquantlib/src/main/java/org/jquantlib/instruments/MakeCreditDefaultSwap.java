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
 Copyright (C) 2014 Jose Aparicio
 Copyright (C) 2014 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.instruments;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.time.*;
import org.jquantlib.time.calendars.WeekendsOnly;

/**
 * Helper class to instantiate a standard market credit default swap.
 *
 * <p>Java port of QuantLib v1.42.1 {@code QuantLib::MakeCreditDefaultSwap}
 * ({@code ql/instruments/makecds.{hpp,cpp}}). Provides a fluent builder for CDS construction with sensible defaults:
 * WeekendsOnly calendar, Actual360 day counter, quarterly coupon tenor, Following business-day convention, Buyer side,
 * nominal 1.0, upfront 0, three-day cash settlement, settles-accrual / pays-at-default-time / rebates-accrual all
 * enabled, {@link DateGeneration.Rule#CDS} schedule rule.
 *
 * <p>Three constructor overloads mirror the C++ originals:
 * <ul>
 *   <li>{@code MakeCreditDefaultSwap(Period tenor, Rate runningSpread)}</li>
 *   <li>{@code MakeCreditDefaultSwap(Date termDate, Rate runningSpread)}</li>
 *   <li>{@code MakeCreditDefaultSwap(Schedule schedule, Rate runningSpread)}</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 *   CreditDefaultSwap cds = new MakeCreditDefaultSwap(
 *           new Period(5, TimeUnit.Years), 0.01)
 *           .withNominal(10000.0)
 *           .withDateGenerationRule(DateGeneration.Rule.CDS)
 *           .build();
 *   cds.setPricingEngine(engine);
 * </pre>
 *
 * <p><b>Java vs C++ note:</b> C++ uses implicit conversion operators
 * ({@code operator CreditDefaultSwap()} / {@code operator
 * ext::shared_ptr<CreditDefaultSwap>()}) to materialise the swap. Java
 * exposes {@link #build()} (and convenience {@link #value()}, equivalent
 * to {@code build()}) instead. The C++
 * {@code MakeCreditDefaultSwap::operator ext::shared_ptr<CreditDefaultSwap>()}
 * also calls {@code cds->setPricingEngine(engine_)} unconditionally —
 * Java {@link #build()} replicates that, attaching the engine if one was
 * supplied via {@link #withPricingEngine}.
 *
 * @category instruments
 * @see CreditDefaultSwap
 * @see CreditDefaultSwap#cdsMaturity
 */
public class MakeCreditDefaultSwap {

    // Exactly one of {tenor_, termDate_, schedule_} is non-null; mirrors the
    // C++ ext::optional members.
    private final Period tenor_;
    private final Date termDate_;
    private final Schedule schedule_;
    private final double runningSpread_;

    private Protection.Side side_ = Protection.Side.Buyer;
    private double nominal_ = 1.0;
    private double upfrontRate_ = 0.0;
    private Period couponTenor_ = new Period(3, TimeUnit.Months);
    private DateGeneration.Rule rule_ = DateGeneration.Rule.CDS;
    private BusinessDayConvention convention_ = BusinessDayConvention.Following;
    private DayCounter dayCounter_ = new Actual360();
    private boolean settlesAccrual_ = true;
    private boolean paysAtDefaultTime_ = true;
    private Date protectionStart_;       // null sentinel
    private Date upfrontDate_;           // null sentinel
    private Claim claim_;                // null = use FaceValueClaim
    /**
     * C++ defaults to {@code Actual360(true)} ("Actual/360 (inc)"). Java now matches as of Phase 3d L0 A.2.
     */
    private DayCounter lastPeriodDayCounter_ = new Actual360(true);
    private boolean rebatesAccrual_ = true;
    private Date tradeDate_;             // null = use Settings.evaluationDate()
    private int cashSettlementDays_ = 3;

    private PricingEngine engine_;

    //
    // public constructors
    //

    public MakeCreditDefaultSwap(final Period tenor, final double runningSpread) {
        this.tenor_ = tenor;
        this.termDate_ = null;
        this.schedule_ = null;
        this.runningSpread_ = runningSpread;
    }

    public MakeCreditDefaultSwap(final Date termDate, final double runningSpread) {
        this.tenor_ = null;
        this.termDate_ = termDate;
        this.schedule_ = null;
        this.runningSpread_ = runningSpread;
    }

    public MakeCreditDefaultSwap(final Schedule schedule, final double runningSpread) {
        this.tenor_ = null;
        this.termDate_ = null;
        this.schedule_ = schedule;
        this.runningSpread_ = runningSpread;
    }

    //
    // fluent setters
    //

    public MakeCreditDefaultSwap withSide(final Protection.Side side) {
        this.side_ = side;
        return this;
    }

    public MakeCreditDefaultSwap withNominal(final double nominal) {
        this.nominal_ = nominal;
        return this;
    }

    public MakeCreditDefaultSwap withUpfrontRate(final double upfrontRate) {
        this.upfrontRate_ = upfrontRate;
        return this;
    }

    public MakeCreditDefaultSwap withCouponTenor(final Period couponTenor) {
        this.couponTenor_ = couponTenor;
        return this;
    }

    public MakeCreditDefaultSwap withDateGenerationRule(final DateGeneration.Rule rule) {
        this.rule_ = rule;
        return this;
    }

    public MakeCreditDefaultSwap withConvention(final BusinessDayConvention convention) {
        this.convention_ = convention;
        return this;
    }

    public MakeCreditDefaultSwap withDayCounter(final DayCounter dayCounter) {
        this.dayCounter_ = dayCounter;
        return this;
    }

    public MakeCreditDefaultSwap settleAccrual(final boolean b) {
        this.settlesAccrual_ = b;
        return this;
    }

    public MakeCreditDefaultSwap payAtDefaultTime(final boolean b) {
        this.paysAtDefaultTime_ = b;
        return this;
    }

    public MakeCreditDefaultSwap withProtectionStart(final Date d) {
        this.protectionStart_ = d;
        return this;
    }

    public MakeCreditDefaultSwap withUpfrontDate(final Date d) {
        this.upfrontDate_ = d;
        return this;
    }

    public MakeCreditDefaultSwap withClaim(final Claim claim) {
        this.claim_ = claim;
        return this;
    }

    public MakeCreditDefaultSwap withLastPeriodDayCounter(final DayCounter dayCounter) {
        this.lastPeriodDayCounter_ = dayCounter;
        return this;
    }

    public MakeCreditDefaultSwap rebateAccrual(final boolean b) {
        this.rebatesAccrual_ = b;
        return this;
    }

    public MakeCreditDefaultSwap withTradeDate(final Date tradeDate) {
        this.tradeDate_ = tradeDate;
        return this;
    }

    public MakeCreditDefaultSwap withCashSettlementDays(final int cashSettlementDays) {
        this.cashSettlementDays_ = cashSettlementDays;
        return this;
    }

    public MakeCreditDefaultSwap withPricingEngine(final PricingEngine engine) {
        this.engine_ = engine;
        return this;
    }

    //
    // build — mirrors C++ operator ext::shared_ptr<CreditDefaultSwap>()
    //

    /**
     * Builds the configured {@link CreditDefaultSwap}, attaching the pricing engine if one was supplied via
     * {@link #withPricingEngine}.
     */
    public CreditDefaultSwap build() {
        final Date tradeDate = (tradeDate_ != null && !tradeDate_.isNull())
                ? tradeDate_
                : new Settings().evaluationDate();

        final Date upfrontDate;
        if ( upfrontDate_ != null && !upfrontDate_.isNull() ) {
            upfrontDate = upfrontDate_;
        } else {
            upfrontDate = new WeekendsOnly().advance(tradeDate, cashSettlementDays_, TimeUnit.Days);
        }

        Date protectionStart = protectionStart_;
        if ( protectionStart == null || protectionStart.isNull() ) {
            if ( schedule_ != null ) {
                protectionStart = schedule_.date(0);
            } else {
                if ( rule_ == DateGeneration.Rule.CDS2015 || rule_ == DateGeneration.Rule.CDS ) {
                    protectionStart = tradeDate;
                } else {
                    protectionStart = tradeDate.add(1);
                }
            }
        }

        // Schedule, tenor and termDate come from different constructors;
        // exactly one of them is non-null.
        final Schedule schedule;
        if ( schedule_ != null ) {
            schedule = schedule_;
        } else {
            final Date end;
            if ( tenor_ != null ) {
                if ( rule_ == DateGeneration.Rule.CDS2015 || rule_ == DateGeneration.Rule.CDS
                        || rule_ == DateGeneration.Rule.OldCDS ) {
                    end = CreditDefaultSwap.cdsMaturity(tradeDate, tenor_, rule_);
                } else {
                    end = tradeDate.add(tenor_);
                }
            } else {
                // termDate_ is the only one left.
                end = termDate_;
            }
            schedule = new Schedule(protectionStart, end, couponTenor_, new WeekendsOnly(), convention_,
                    BusinessDayConvention.Unadjusted, rule_, false);
        }

        final CreditDefaultSwap cds = new CreditDefaultSwap(side_, nominal_, upfrontRate_, runningSpread_, schedule,
                convention_, dayCounter_, settlesAccrual_, paysAtDefaultTime_, protectionStart, upfrontDate, claim_,
                lastPeriodDayCounter_, rebatesAccrual_, tradeDate, cashSettlementDays_);

        if ( engine_ != null ) {
            cds.setPricingEngine(engine_);
        }
        return cds;
    }

    /**
     * Convenience alias for {@link #build()}. Mirrors C++ implicit
     * {@code operator ext::shared_ptr<CreditDefaultSwap>()} call sites.
     */
    public CreditDefaultSwap value() {
        return build();
    }
}
