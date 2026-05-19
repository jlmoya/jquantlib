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
 Copyright (C) 2008, 2009 Jose Aparicio
 Copyright (C) 2008 Roland Lichters
 Copyright (C) 2008 StatPro Italia srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.instruments;

import org.jquantlib.QL;
import org.jquantlib.cashflow.*;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.Constants;
import org.jquantlib.math.Ops;
import org.jquantlib.math.solvers1D.Brent;
import org.jquantlib.pricingengines.GenericEngine;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.credit.MidPointCdsEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.DefaultProbabilityTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.credit.FlatHazardRate;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.NullCalendar;

/**
 * Credit default swap.
 *
 * <p>Java port of QuantLib v1.42.1 {@code QuantLib::CreditDefaultSwap}
 * ({@code ql/instruments/creditdefaultswap.{hpp,cpp}}, 874 LOC). Mirrors the C++ class structure verbatim:
 * <ul>
 *   <li>extends {@link Instrument};</li>
 *   <li>two constructor overloads (running-spread only, upfront + running);</li>
 *   <li>inner DTOs {@link Arguments} / {@link Results} and base
 *       {@link Engine} {@code GenericEngine<Arguments,Results>};</li>
 *   <li>structural inspectors mirroring the C++ getter set.</li>
 * </ul>
 *
 * <p>Phase 3b L0 scope. The {@link MidPointCdsEngine} concrete engine and
 * the {@link #impliedHazardRate} / {@link #conventionalSpread} helpers
 * (which build a transient {@code MidPointCdsEngine} internally in C++) are
 * delivered by Phase 3b Track B; until then those two methods throw
 * {@link UnsupportedOperationException}. The {@code IsdaCdsEngine} branch is
 * Phase 3c. Result accessors ({@link #fairUpfront} etc.) require an external
 * engine to be set via {@link #setPricingEngine} just like the C++ code path.
 *
 * <p><b>Schedule rule note.</b> The C++ {@code postBigBang} branch keys off
 * {@code DateGeneration::CDS}/{@code CDS2015}; those rules are not yet
 * present in the Java {@link org.jquantlib.time.DateGeneration} enum. The
 * Java port treats every schedule as pre-Big-Bang for now (the
 * {@code protectionStart_ <= schedule[0]} invariant is enforced
 * unconditionally and the trade date defaults to
 * {@code protectionStart_ - 1}). Adding the CDS/CDS2015 rules and the
 * post-Big-Bang trade-date inference is left to a future Phase 3b/3c align
 * once a CDS-rule schedule is actually constructed in tests.
 *
 * <p><b>Last-period day counter.</b> The Java {@link FixedRateLeg} builder
 * does not yet expose {@code withLastPeriodDayCounter(...)}; the parameter
 * is accepted on the constructor for signature parity with C++ but is not
 * forwarded to the leg builder. This is consistent with the existing JQ
 * behaviour for other fixed-rate instruments and is sufficient for the
 * Phase 3b smoke tests; a follow-up align in Phase 3b Track B / Track C may
 * extend the leg builder if the test port requires it.
 *
 * @category instruments
 */
public class CreditDefaultSwap extends Instrument {

    private final Protection.Side side_;

    //
    // data members — mirror C++ creditdefaultswap.hpp:282-302
    //
    private final double notional_;
    /**
     * May be {@code null} when the CDS is constructed without an upfront payment. Mirrors C++
     * {@code ext::optional<Rate>}.
     */
    private final Double upfront_;
    private final double runningSpread_;
    private final boolean settlesAccrual_;
    private final boolean paysAtDefaultTime_;
    private final Date protectionStart_;
    private final int cashSettlementDays_;
    private Claim claim_;
    private Leg leg_;
    private SimpleCashFlow upfrontPayment_;
    private SimpleCashFlow accrualRebate_;
    private Date tradeDate_;
    private Date maturity_;
    // results (populated by the engine and copied in fetchResults)
    private double fairUpfront_;
    private double fairSpread_;
    private double couponLegBPS_;
    private double couponLegNPV_;
    private double upfrontBPS_;
    private double upfrontNPV_;
    private double defaultLegNPV_;
    private double accrualRebateNPV_;
    /**
     * CDS quoted as running-spread only. Mirrors C++ overload at {@code creditdefaultswap.hpp:99-112}.
     */
    public CreditDefaultSwap(final Protection.Side side, final double notional, final double spread,
            final Schedule schedule, final BusinessDayConvention paymentConvention, final DayCounter dayCounter,
            final boolean settlesAccrual, final boolean paysAtDefaultTime, final Date protectionStart,
            final Claim claim, final DayCounter lastPeriodDayCounter, final boolean rebatesAccrual,
            final Date tradeDate, final int cashSettlementDays) {

        this.side_ = side;
        this.notional_ = notional;
        this.upfront_ = null;
        this.runningSpread_ = spread;
        this.settlesAccrual_ = settlesAccrual;
        this.paysAtDefaultTime_ = paysAtDefaultTime;
        this.claim_ = claim;
        this.protectionStart_ = (protectionStart == null || protectionStart.isNull())
                ? schedule.date(0)
                : protectionStart;
        this.tradeDate_ = tradeDate;
        this.cashSettlementDays_ = cashSettlementDays;

        init(schedule, paymentConvention, dayCounter, lastPeriodDayCounter, rebatesAccrual, null);
    }

    //
    // public constructors
    //

    /**
     * Convenience overload defaulting to claim=null, lastPeriodDayCounter=null, rebatesAccrual=true, tradeDate=null,
     * cashSettlementDays=3 — matches the most common C++ default-argument call site.
     */
    public CreditDefaultSwap(final Protection.Side side, final double notional, final double spread,
            final Schedule schedule, final BusinessDayConvention paymentConvention, final DayCounter dayCounter,
            final boolean settlesAccrual, final boolean paysAtDefaultTime, final Date protectionStart) {
        this(side, notional, spread, schedule, paymentConvention, dayCounter, settlesAccrual, paysAtDefaultTime,
                protectionStart, null, null, true, null, 3);
    }

    /** Convenience overload — minimal C++ signature. */
    public CreditDefaultSwap(final Protection.Side side, final double notional, final double spread,
            final Schedule schedule, final BusinessDayConvention paymentConvention, final DayCounter dayCounter) {
        this(side, notional, spread, schedule, paymentConvention, dayCounter, true, true, null, null, null, true, null,
                3);
    }

    /**
     * CDS quoted as upfront and running spread. Mirrors C++ overload at {@code creditdefaultswap.hpp:151-166}.
     */
    public CreditDefaultSwap(final Protection.Side side, final double notional, final double upfront,
            final double runningSpread, final Schedule schedule, final BusinessDayConvention paymentConvention,
            final DayCounter dayCounter, final boolean settlesAccrual, final boolean paysAtDefaultTime,
            final Date protectionStart, final Date upfrontDate, final Claim claim,
            final DayCounter lastPeriodDayCounter, final boolean rebatesAccrual, final Date tradeDate,
            final int cashSettlementDays) {

        this.side_ = side;
        this.notional_ = notional;
        this.upfront_ = Double.valueOf(upfront);
        this.runningSpread_ = runningSpread;
        this.settlesAccrual_ = settlesAccrual;
        this.paysAtDefaultTime_ = paysAtDefaultTime;
        this.claim_ = claim;
        this.protectionStart_ = (protectionStart == null || protectionStart.isNull())
                ? schedule.date(0)
                : protectionStart;
        this.tradeDate_ = tradeDate;
        this.cashSettlementDays_ = cashSettlementDays;

        init(schedule, paymentConvention, dayCounter, lastPeriodDayCounter, rebatesAccrual, upfrontDate);
    }

    /** Convenience overload: upfront + spread, default tail params. */
    public CreditDefaultSwap(final Protection.Side side, final double notional, final double upfront,
            final double runningSpread, final Schedule schedule, final BusinessDayConvention paymentConvention,
            final DayCounter dayCounter, final boolean settlesAccrual, final boolean paysAtDefaultTime,
            final Date protectionStart, final Date upfrontDate) {
        this(side, notional, upfront, runningSpread, schedule, paymentConvention, dayCounter, settlesAccrual,
                paysAtDefaultTime, protectionStart, upfrontDate, null, null, true, null, 3);
    }

    /**
     * Returns the standard CDS maturity for a given trade date and tenor under the
     * {@link org.jquantlib.time.DateGeneration.Rule#CDS}, {@link org.jquantlib.time.DateGeneration.Rule#CDS2015}, or
     * {@link org.jquantlib.time.DateGeneration.Rule#OldCDS} convention.
     *
     * <p>Mirrors C++ {@code QuantLib::cdsMaturity(Date, Period, DateGeneration::Rule)}
     * declared in {@code ql/instruments/creditdefaultswap.hpp:361}. The maturity is the previous-twentieth of the trade
     * date plus tenor plus three months, with a special case for CDS2015 anchor dates that fall on June 20 or December
     * 20.
     */
    public static Date cdsMaturity(final Date tradeDate, final org.jquantlib.time.Period tenor,
            final org.jquantlib.time.DateGeneration.Rule rule) {
        QL.require(rule == org.jquantlib.time.DateGeneration.Rule.CDS2015
                        || rule == org.jquantlib.time.DateGeneration.Rule.CDS
                        || rule == org.jquantlib.time.DateGeneration.Rule.OldCDS,
                "cdsMaturity should only be used with date generation rule CDS2015, CDS or OldCDS");

        QL.require(tenor.units() == org.jquantlib.time.TimeUnit.Years || (
                        tenor.units() == org.jquantlib.time.TimeUnit.Months && tenor.length() % 3 == 0),
                "cdsMaturity expects a tenor that is a multiple of 3 months.");

        if ( rule == org.jquantlib.time.DateGeneration.Rule.OldCDS ) {
            QL.require(tenor.length() != 0, "A tenor of 0M is not supported for OldCDS.");
        }

        Date anchorDate = org.jquantlib.time.Schedule.previousTwentieth(tradeDate, rule);
        if ( rule == org.jquantlib.time.DateGeneration.Rule.CDS2015 && (
                anchorDate.eq(new Date(20, org.jquantlib.time.Month.December, anchorDate.year())) || anchorDate.eq(
                        new Date(20, org.jquantlib.time.Month.June, anchorDate.year()))) ) {
            if ( tenor.length() == 0 ) {
                return new Date();
            }
            anchorDate = anchorDate.sub(new org.jquantlib.time.Period(3, org.jquantlib.time.TimeUnit.Months));
        }

        final Date maturity = anchorDate.add(tenor)
                .add(new org.jquantlib.time.Period(3, org.jquantlib.time.TimeUnit.Months));
        QL.require(maturity.gt(tradeDate),
                "error calculating CDS maturity. Tenor is " + tenor + ", trade date is " + tradeDate
                        + " generating a maturity of " + maturity + " <= trade date.");
        return maturity;
    }

    //
    // shared initialisation — mirrors C++ creditdefaultswap.cpp:87-176
    //

    private void init(final Schedule schedule, final BusinessDayConvention paymentConvention,
            final DayCounter dayCounter, final DayCounter lastPeriodDayCounter, final boolean rebatesAccrual,
            final Date upfrontDate) {

        QL.require(!schedule.empty(), "CreditDefaultSwap needs a non-empty schedule.");

        // C++ branches on schedule.hasRule() && rule == DateGeneration::CDS /
        // CDS2015 (creditdefaultswap.cpp:88-93). Java has no hasRule() alias —
        // accessing schedule.rule() throws when the schedule was built with
        // the simplified date-list constructor; guard with try/catch so legacy
        // call sites (no rule) still work.
        boolean postBigBang = false;
        try {
            final org.jquantlib.time.DateGeneration.Rule r = schedule.rule();
            if ( r == org.jquantlib.time.DateGeneration.Rule.CDS
                    || r == org.jquantlib.time.DateGeneration.Rule.CDS2015 ) {
                postBigBang = true;
            }
        } catch ( final RuntimeException ignored ) {
            // schedule has no rule — leave postBigBang = false.
        }

        if ( !postBigBang ) {
            QL.require(protectionStart_.le(schedule.date(0)), "protection can not start after accrual");
        }

        // Build the fixed-rate (premium) leg. Mirrors C++ FixedRateLeg(schedule)
        // .withNotionals(notional_).withCouponRates(spread, dayCounter)
        // .withPaymentAdjustment(paymentConvention)
        // .withLastPeriodDayCounter(lastPeriodDayCounter).
        // Phase 3d L0 A.2 — withLastPeriodDayCounter is now wired through
        // FixedRateLeg (was previously accepted but ignored).
        final FixedRateLeg builder = new FixedRateLeg(schedule, dayCounter).withNotionals(notional_)
                .withCouponRates(runningSpread_).withPaymentAdjustment(paymentConvention);
        if ( lastPeriodDayCounter != null ) {
            builder.withLastPeriodDayCounter(lastPeriodDayCounter);
        }
        leg_ = builder.Leg();

        // Deduce trade date if not given. C++ creditdefaultswap.cpp:110-116.
        if ( tradeDate_ == null || tradeDate_.isNull() ) {
            if ( postBigBang ) {
                tradeDate_ = protectionStart_.clone();
            } else {
                tradeDate_ = protectionStart_.sub(1);
            }
        }

        // Deduce cash settlement date. C++ creditdefaultswap.cpp:118-125.
        Date effectiveUpfrontDate = upfrontDate;
        if ( effectiveUpfrontDate == null || effectiveUpfrontDate.isNull() ) {
            effectiveUpfrontDate = schedule.calendar()
                    .advance(tradeDate_, cashSettlementDays_, TimeUnit.Days, paymentConvention, false);
        }
        QL.require(effectiveUpfrontDate.ge(protectionStart_),
                "The cash settlement date must not be before the protection start date.");

        // Create the upfront payment cash flow. C++ creditdefaultswap.cpp:127-131.
        double upfrontAmount = 0.0;
        if ( upfront_ != null ) {
            upfrontAmount = upfront_.doubleValue() * notional_;
        }
        upfrontPayment_ = new SimpleCashFlow(upfrontAmount, effectiveUpfrontDate);

        // Maturity = last schedule date. C++ creditdefaultswap.cpp:133-134.
        maturity_ = schedule.dates().get(schedule.dates().size() - 1);

        // Accrual rebate. C++ creditdefaultswap.cpp:138-171.
        if ( rebatesAccrual ) {
            double rebateAmount = 0.0;
            final Date refDate = tradeDate_.add(1);

            if ( tradeDate_.ge(schedule.dates().get(0)) ) {
                for ( int i = 0; i < leg_.size(); ++i ) {
                    final CashFlow cf = leg_.get(i);
                    if ( refDate.gt(cf.date()) ) {
                        // Past coupon; check next.
                        continue;
                    } else if ( refDate.eq(cf.date()) ) {
                        // Coupon pays at refDate. If it's the last coupon, rebate
                        // is the full amount; otherwise zero.
                        if ( i < leg_.size() - 1 ) {
                            rebateAmount = 0.0;
                        } else {
                            QL.require(cf instanceof FixedRateCoupon, "expected FixedRateCoupon in CDS premium leg");
                            rebateAmount = cf.amount();
                        }
                        break;
                    } else {
                        // Future coupon; first one to do so. Compute accrual.
                        QL.require(cf instanceof FixedRateCoupon, "expected FixedRateCoupon in CDS premium leg");
                        rebateAmount = ((FixedRateCoupon) cf).accruedAmount(refDate);
                        break;
                    }
                }
            }

            accrualRebate_ = new SimpleCashFlow(rebateAmount, effectiveUpfrontDate);
        }

        // Default to FaceValueClaim if none provided. C++ creditdefaultswap.cpp:173-175.
        if ( claim_ == null ) {
            claim_ = new FaceValueClaim();
        }
        claim_.addObserver(this);
    }

    //
    // Inspectors — mirror C++ creditdefaultswap.hpp:174-191
    //

    public Protection.Side side() {
        return side_;
    }

    public double notional() {
        return notional_;
    }

    public double runningSpread() {
        return runningSpread_;
    }

    /**
     * Mirrors C++ {@code ext::optional<Rate> upfront()}. Returns {@code null} when the CDS was constructed without an
     * upfront.
     */
    public Double upfront() {
        return upfront_;
    }

    public boolean settlesAccrual() {
        return settlesAccrual_;
    }

    public boolean paysAtDefaultTime() {
        return paysAtDefaultTime_;
    }

    public Leg coupons() {
        return leg_;
    }

    public Leg couponSchedule() {
        return leg_;
    } // alias used in some call sites

    public Date protectionStartDate() {
        return protectionStart_;
    }

    public Date protectionEndDate() {
        QL.require(leg_ != null && !leg_.isEmpty(), "premium leg has no coupons");
        final CashFlow last = leg_.get(leg_.size() - 1);
        QL.require(last instanceof Coupon, "expected Coupon for last entry of CDS premium leg");
        return ((Coupon) last).accrualEndDate();
    }

    public boolean rebatesAccrual() {
        return accrualRebate_ != null;
    }

    public SimpleCashFlow upfrontPayment() {
        return upfrontPayment_;
    }

    public SimpleCashFlow accrualRebate() {
        return accrualRebate_;
    }

    public Date tradeDate() {
        return tradeDate_;
    }

    public int cashSettlementDays() {
        return cashSettlementDays_;
    }

    public Claim claim() {
        return claim_;
    }

    //
    // Result accessors — mirror C++ creditdefaultswap.cpp:259-313
    //

    public double fairUpfront() {
        calculate();
        QL.require(fairUpfront_ != Constants.NULL_REAL, "fair upfront not available");
        return fairUpfront_;
    }

    public double fairSpread() {
        calculate();
        QL.require(fairSpread_ != Constants.NULL_REAL, "fair spread not available");
        return fairSpread_;
    }

    public double couponLegBPS() {
        calculate();
        QL.require(couponLegBPS_ != Constants.NULL_REAL, "coupon-leg BPS not available");
        return couponLegBPS_;
    }

    public double couponLegNPV() {
        calculate();
        QL.require(couponLegNPV_ != Constants.NULL_REAL, "coupon-leg NPV not available");
        return couponLegNPV_;
    }

    public double defaultLegNPV() {
        calculate();
        QL.require(defaultLegNPV_ != Constants.NULL_REAL, "default-leg NPV not available");
        return defaultLegNPV_;
    }

    public double upfrontNPV() {
        calculate();
        QL.require(upfrontNPV_ != Constants.NULL_REAL, "upfront NPV not available");
        return upfrontNPV_;
    }

    public double upfrontBPS() {
        calculate();
        QL.require(upfrontBPS_ != Constants.NULL_REAL, "upfront BPS not available");
        return upfrontBPS_;
    }

    public double accrualRebateNPV() {
        calculate();
        QL.require(accrualRebateNPV_ != Constants.NULL_REAL, "accrual rebate NPV not available");
        return accrualRebateNPV_;
    }

    //
    // Helper calculations — Phase 3b Track B will fill these in once
    // MidPointCdsEngine lands; Phase 3c adds the ISDA branch.
    //

    /**
     * Implied hazard rate. Mirrors C++ {@code CreditDefaultSwap::impliedHazardRate}
     * ({@code creditdefaultswap.cpp:340-381}).
     *
     * <p>Builds a transient flat-hazard-rate term structure backed by a
     * {@link SimpleQuote}, attaches a {@link MidPointCdsEngine} to it, and uses {@link Brent} to solve for the hazard
     * rate that makes the engine's NPV equal {@code targetNPV}. The {@link PricingModel#ISDA} branch is Phase 3c.
     */
    public double impliedHazardRate(final double targetNPV, final Handle< YieldTermStructure > discountCurve,
            final DayCounter dayCounter, final double recoveryRate, final double accuracy, final PricingModel model) {

        final SimpleQuote flatRate = new SimpleQuote(0.0);

        final Handle< DefaultProbabilityTermStructure > probability = new Handle< DefaultProbabilityTermStructure >(
                new FlatHazardRate(0, new NullCalendar(), new Handle< Quote >(flatRate), dayCounter));

        final PricingEngine engine;
        switch ( model ) {
        case Midpoint:
            engine = new MidPointCdsEngine(probability, recoveryRate, discountCurve);
            break;
        case ISDA:
            // Phase 3d L1: wire IsdaCdsEngine with C++ defaults (Taylor /
            // HalfDayBias / Piecewise, includeSettlementDateFlows=false).
            engine = new org.jquantlib.pricingengines.credit.IsdaCdsEngine(probability, recoveryRate, discountCurve,
                    Boolean.FALSE, org.jquantlib.pricingengines.credit.IsdaCdsEngine.NumericalFix.Taylor,
                    org.jquantlib.pricingengines.credit.IsdaCdsEngine.AccrualBias.HalfDayBias,
                    org.jquantlib.pricingengines.credit.IsdaCdsEngine.ForwardsInCouponPeriod.Piecewise);
            break;
        default:
            throw new IllegalArgumentException("unknown CDS pricing model: " + model);
        }

        setupArguments(engine.getArguments());
        final CreditDefaultSwap.ResultsImpl res = (CreditDefaultSwap.ResultsImpl) engine.getResults();

        final Ops.DoubleOp f = new Ops.DoubleOp() {
            @Override
            public double op(final double guess) {
                flatRate.setValue(guess);
                engine.calculate();
                return res.value - targetNPV;
            }
        };

        // Very close guess if targetNPV = 0 (mirrors C++ comment).
        final double guess = runningSpread_ / (1.0 - recoveryRate) * 365.0 / 360.0;
        final double step = 0.1 * guess;
        return new Brent().solve(f, accuracy, guess, step);
    }

    /**
     * Convenience overload defaulting to recoveryRate=0.4, accuracy=1e-8, model=Midpoint — matches the C++ default
     * arguments.
     */
    public double impliedHazardRate(final double targetNPV, final Handle< YieldTermStructure > discountCurve,
            final DayCounter dayCounter) {
        return impliedHazardRate(targetNPV, discountCurve, dayCounter, 0.4, 1.0e-8, PricingModel.Midpoint);
    }

    /**
     * Conventional / standard upfront-to-spread conversion. Mirrors C++ {@code CreditDefaultSwap::conventionalSpread}
     * ({@code creditdefaultswap.cpp:383-423}). The {@link PricingModel#ISDA} branch is Phase 3c.
     */
    public double conventionalSpread(final double conventionalRecovery,
            final Handle< YieldTermStructure > discountCurve, final DayCounter dayCounter, final PricingModel model) {

        final SimpleQuote flatRate = new SimpleQuote(0.0);

        final Handle< DefaultProbabilityTermStructure > probability = new Handle< DefaultProbabilityTermStructure >(
                new FlatHazardRate(0, new NullCalendar(), new Handle< Quote >(flatRate), dayCounter));

        final PricingEngine engine;
        switch ( model ) {
        case Midpoint:
            engine = new MidPointCdsEngine(probability, conventionalRecovery, discountCurve);
            break;
        case ISDA:
            // Phase 3d L1: wire IsdaCdsEngine with C++ defaults.
            engine = new org.jquantlib.pricingengines.credit.IsdaCdsEngine(probability, conventionalRecovery,
                    discountCurve, Boolean.FALSE, org.jquantlib.pricingengines.credit.IsdaCdsEngine.NumericalFix.Taylor,
                    org.jquantlib.pricingengines.credit.IsdaCdsEngine.AccrualBias.HalfDayBias,
                    org.jquantlib.pricingengines.credit.IsdaCdsEngine.ForwardsInCouponPeriod.Piecewise);
            break;
        default:
            throw new IllegalArgumentException("unknown CDS pricing model: " + model);
        }

        setupArguments(engine.getArguments());
        final CreditDefaultSwap.ResultsImpl res = (CreditDefaultSwap.ResultsImpl) engine.getResults();

        final Ops.DoubleOp f = new Ops.DoubleOp() {
            @Override
            public double op(final double guess) {
                flatRate.setValue(guess);
                engine.calculate();
                return res.value - 0.0;
            }
        };
        final double guess = runningSpread_ / (1.0 - conventionalRecovery) * 365.0 / 360.0;
        final double step = guess * 0.1;
        new Brent().solve(f, 1.0e-9, guess, step);
        return res.fairSpread;
    }

    /** Convenience overload defaulting to model=Midpoint. */
    public double conventionalSpread(final double conventionalRecovery,
            final Handle< YieldTermStructure > discountCurve, final DayCounter dayCounter) {
        return conventionalSpread(conventionalRecovery, discountCurve, dayCounter, PricingModel.Midpoint);
    }

    //
    // Instrument interface — mirrors C++ creditdefaultswap.cpp:207-257
    //

    @Override
    public boolean isExpired() {
        // Iterate from the back of the leg as in C++: the last coupon is the
        // most likely candidate for "still pending".
        final Date today = new org.jquantlib.Settings().evaluationDate();
        for ( int i = leg_.size() - 1; i >= 0; --i ) {
            if ( !leg_.get(i).hasOccurred(today) ) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void setupExpired() {
        super.setupExpired();
        fairSpread_ = 0.0;
        fairUpfront_ = 0.0;
        couponLegBPS_ = 0.0;
        upfrontBPS_ = 0.0;
        couponLegNPV_ = 0.0;
        defaultLegNPV_ = 0.0;
        upfrontNPV_ = 0.0;
        accrualRebateNPV_ = 0.0;
    }

    @Override
    public void setupArguments(final PricingEngine.Arguments args) {
        // Promoted from protected so CdsOption (in experimental.credit) can
        // delegate its CDS-arguments population through the underlying swap;
        // mirrors the C++ pattern where Instrument::setupArguments is public.
        QL.require(args instanceof CreditDefaultSwap.ArgumentsImpl,
                "wrong argument type — expected CreditDefaultSwap.Arguments");
        final CreditDefaultSwap.ArgumentsImpl a = (CreditDefaultSwap.ArgumentsImpl) args;

        a.side = side_;
        a.notional = notional_;
        a.leg = leg_;
        a.upfrontPayment = upfrontPayment_;
        a.accrualRebate = accrualRebate_;
        a.settlesAccrual = settlesAccrual_;
        a.paysAtDefaultTime = paysAtDefaultTime_;
        a.claim = claim_;
        a.upfront = upfront_;
        a.spread = runningSpread_;
        a.protectionStart = protectionStart_;
        a.maturity = maturity_;
    }

    @Override
    protected void fetchResults(final PricingEngine.Results r) {
        super.fetchResults(r);
        QL.require(r instanceof CreditDefaultSwap.ResultsImpl,
                "wrong result type — expected CreditDefaultSwap.Results");
        final CreditDefaultSwap.ResultsImpl res = (CreditDefaultSwap.ResultsImpl) r;

        fairSpread_ = res.fairSpread;
        fairUpfront_ = res.fairUpfront;
        couponLegBPS_ = res.couponLegBPS;
        couponLegNPV_ = res.couponLegNPV;
        defaultLegNPV_ = res.defaultLegNPV;
        upfrontBPS_ = res.upfrontBPS;
        upfrontNPV_ = res.upfrontNPV;
        accrualRebateNPV_ = res.accrualRebateNPV;
    }

    //
    // public inner interfaces and classes —
    // mirror C++ CreditDefaultSwap::arguments / results / engine
    //

    /**
     * Pricing-model selector for the {@link #impliedHazardRate} and {@link #conventionalSpread} helpers. Mirrors C++
     * {@code CreditDefaultSwap::PricingModel}.
     */
    public enum PricingModel {
        Midpoint, ISDA
    }

    /** Marking interface; mirrors C++ {@code CreditDefaultSwap::arguments} base. */
    public interface Arguments extends Instrument.Arguments { /* marker */
    }

    /** Marking interface; mirrors C++ {@code CreditDefaultSwap::results} base. */
    public interface Results extends Instrument.Results { /* marker */
    }

    /**
     * Concrete arguments DTO populated by {@link #setupArguments} and consumed by {@link Engine#calculate}. Mirrors C++
     * {@code CreditDefaultSwap::arguments} fields verbatim ({@code creditdefaultswap.hpp:311-329}).
     */
    static public class ArgumentsImpl implements CreditDefaultSwap.Arguments {
        public Protection.Side side;
        public double notional;
        /** Mirrors C++ {@code ext::optional<Rate> upfront}. */
        public Double upfront;
        public double spread;
        public Leg leg;
        public SimpleCashFlow upfrontPayment;
        public SimpleCashFlow accrualRebate;
        public boolean settlesAccrual;
        public boolean paysAtDefaultTime;
        public Claim claim;
        public Date protectionStart;
        public Date maturity;

        public ArgumentsImpl() {
            // Mirror C++ default-constructed sentinels.
            this.side = null;
            this.notional = Constants.NULL_REAL;
            this.spread = Constants.NULL_RATE;
        }

        @Override
        public void validate() {
            QL.require(side != null, "side not set");
            QL.require(notional != Constants.NULL_REAL, "notional not set");
            QL.require(notional != 0.0, "null notional set");
            QL.require(spread != Constants.NULL_RATE, "spread not set");
            QL.require(leg != null && !leg.isEmpty(), "coupons not set");
            QL.require(upfrontPayment != null, "upfront payment not set");
            QL.require(claim != null, "claim not set");
            QL.require(protectionStart != null && !protectionStart.isNull(), "protection start date not set");
            QL.require(maturity != null && !maturity.isNull(), "maturity date not set");
        }
    }

    /**
     * Concrete results DTO. Mirrors C++ {@code CreditDefaultSwap::results} fields
     * ({@code creditdefaultswap.hpp:331-342}).
     */
    static public class ResultsImpl extends Instrument.ResultsImpl implements CreditDefaultSwap.Results {

        public double fairSpread;
        public double fairUpfront;
        public double couponLegBPS;
        public double couponLegNPV;
        public double defaultLegNPV;
        public double upfrontBPS;
        public double upfrontNPV;
        public double accrualRebateNPV;

        @Override
        public void reset() {
            super.reset();
            fairSpread = Constants.NULL_RATE;
            fairUpfront = Constants.NULL_RATE;
            couponLegBPS = Constants.NULL_REAL;
            couponLegNPV = Constants.NULL_REAL;
            defaultLegNPV = Constants.NULL_REAL;
            upfrontBPS = Constants.NULL_REAL;
            upfrontNPV = Constants.NULL_REAL;
            accrualRebateNPV = Constants.NULL_REAL;
        }
    }

    //
    // Free helpers — mirrors C++ free functions in
    // ql/instruments/creditdefaultswap.{hpp,cpp}
    //

    /**
     * Base class for CDS pricing engines. Mirrors C++
     * {@code CreditDefaultSwap::engine = GenericEngine<arguments, results>}.
     *
     * <p>Concrete engines (Phase 3b Track B
     * {@code MidPointCdsEngine}; Phase 3c {@code IsdaCdsEngine}, {@code IntegralCdsEngine}) extend this class.
     */
    static public abstract class Engine
            extends GenericEngine< CreditDefaultSwap.Arguments, CreditDefaultSwap.Results > {
        protected Engine() {
            super(new CreditDefaultSwap.ArgumentsImpl(), new CreditDefaultSwap.ResultsImpl());
        }
    }
}
