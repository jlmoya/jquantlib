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

package org.jquantlib.pricingengines.credit;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.FixedRateCoupon;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.CreditDefaultSwap;
import org.jquantlib.instruments.FaceValueClaim;
import org.jquantlib.math.Constants;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.DefaultProbabilityTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.credit.FlatHazardRate;
import org.jquantlib.termstructures.credit.InterpolatedHazardRateCurve;
import org.jquantlib.termstructures.credit.InterpolatedSurvivalProbabilityCurve;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.termstructures.yieldcurves.InterpolatedDiscountCurve;
import org.jquantlib.termstructures.yieldcurves.InterpolatedForwardCurve;
import org.jquantlib.time.Date;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

/**
 * ISDA-standard pricing engine for credit default swaps.
 *
 * <p>Java port of QuantLib v1.42.1 {@code QuantLib::IsdaCdsEngine}
 * ({@code ql/pricingengines/credit/isdacdsengine.{hpp,cpp}}, 488 LOC C++). Implements the ISDA CDS Standard Model
 * pricing methodology described in
 *
 * <ol>
 *   <li>"The Pricing and Risk Management of Credit Default Swaps, with a
 *       Focus on the ISDA Model", OpenGamma Quantitative Research,
 *       2013-10-15.</li>
 *   <li>"ISDA CDS Standard Model Proposed Numerical Fix", Markit, 2012-11-15.</li>
 *   <li>"Markit Interest Rate Curve XML Specifications", v1.16, 2013-10-15.</li>
 * </ol>
 *
 * <p>Computes:
 *
 * <ul>
 *   <li><b>Protection (default) leg NPV.</b> Sweeps over the union of yield-
 *       and credit-curve node dates (between the effective protection start
 *       and maturity); for each sub-interval, accumulates an analytic ISDA
 *       integral involving forward rates {@code fhat = log(P0/P1)} and forward
 *       hazards {@code hhat = log(Q0/Q1)} with the
 *       {@link NumericalFix#Taylor} or {@link NumericalFix#None} branch to
 *       avoid zero-denominator blow-up when {@code fhat + hhat} is near
 *       zero.</li>
 *   <li><b>Premium leg NPV.</b> Sums survival-weighted coupon amounts (with
 *       the {@code coupon->date() - 1} ISDA discount-date convention).</li>
 *   <li><b>Default-accrual NPV.</b> Per coupon period, sweeps yield/credit
 *       nodes (or the period endpoints depending on
 *       {@link ForwardsInCouponPeriod}) and accumulates an analytic accrual
 *       integral; multiplied by {@code notional × rate × 365/360}. The
 *       {@link AccrualBias#HalfDayBias} branch subtracts a {@code 1/730} time
 *       offset on the period start, mirroring the &lt; 1.8.2 standard-model C
 *       behaviour.</li>
 *   <li><b>Upfront / accrual rebate NPVs</b>, then sign correction by buyer/seller
 *       side, and finally {@code fairSpread} / {@code fairUpfront} /
 *       {@code couponLegBPS} / {@code upfrontBPS}.</li>
 * </ul>
 *
 * <p><b>ISDA-compliant curve preconditions.</b> {@code calculate} enforces
 * the same constraints the C++ engine does:
 *
 * <ul>
 *   <li>both curves' day counters must be Actual/365 (Fixed);</li>
 *   <li>both curves' reference dates must equal {@code Settings::evaluationDate};</li>
 *   <li>the swap must {@code settlesAccrual}, {@code paysAtDefaultTime}, and
 *       use a {@link FaceValueClaim};</li>
 *   <li>per-coupon day counters must be {@code Actual/365 (Fixed)},
 *       {@code Actual/360}, or {@code Actual/360 (inc)} (Phase 3d L0 A.2 added
 *       the latter variant).</li>
 * </ul>
 *
 * <p><b>Phase 3d L1.</b> Closes credit-subsystem coverage. The
 * {@code IsdaCdsEngine} ports together with un-ignoring the four ISDA-specific
 * tests in {@code CreditDefaultSwapTest}.
 *
 * @category pricingengines.credit
 * @see MidPointCdsEngine
 * @see IntegralCdsEngine
 */
public class IsdaCdsEngine extends CreditDefaultSwap.Engine {

    private final Handle< DefaultProbabilityTermStructure > probability_;
    private final double recoveryRate_;
    private final Handle< YieldTermStructure > discountCurve_;
    /**
     * Mirror of C++ {@code ext::optional<bool> includeSettlementDateFlows_}. {@code null} == "use
     * Settings::includeTodaysCashFlows()".
     */
    private final Boolean includeSettlementDateFlows_;
    private final NumericalFix numericalFix_;
    private final AccrualBias accrualBias_;
    private final ForwardsInCouponPeriod forwardsInCouponPeriod_;
    /**
     * Full constructor mirroring C++
     * {@code IsdaCdsEngine(Handle<DefaultProbabilityTermStructure>, Real recoveryRate, Handle<YieldTermStructure>
     * discountCurve, const ext::optional<bool>& includeSettlementDateFlows, NumericalFix, AccrualBias,
     * ForwardsInCouponPeriod)}.
     */
    public IsdaCdsEngine(final Handle< DefaultProbabilityTermStructure > probability, final double recoveryRate,
            final Handle< YieldTermStructure > discountCurve, final Boolean includeSettlementDateFlows,
            final NumericalFix numericalFix, final AccrualBias accrualBias,
            final ForwardsInCouponPeriod forwardsInCouponPeriod) {
        this.probability_ = probability;
        this.recoveryRate_ = recoveryRate;
        this.discountCurve_ = discountCurve;
        this.includeSettlementDateFlows_ = includeSettlementDateFlows;
        this.numericalFix_ = numericalFix;
        this.accrualBias_ = accrualBias;
        this.forwardsInCouponPeriod_ = forwardsInCouponPeriod;
        if ( probability_ != null )
            probability_.addObserver(this);
        if ( discountCurve_ != null )
            discountCurve_.addObserver(this);
    }
    /**
     * Convenience overload with C++ default-argument values ({@link NumericalFix#Taylor},
     * {@link AccrualBias#HalfDayBias}, {@link ForwardsInCouponPeriod#Piecewise},
     * {@code includeSettlementDateFlows = ext::nullopt}).
     */
    public IsdaCdsEngine(final Handle< DefaultProbabilityTermStructure > probability, final double recoveryRate,
            final Handle< YieldTermStructure > discountCurve) {
        this(probability, recoveryRate, discountCurve, null, NumericalFix.Taylor, AccrualBias.HalfDayBias,
                ForwardsInCouponPeriod.Piecewise);
    }

    /**
     * Mirror of C++ {@code detail::simple_event(date).hasOccurred(refDate, includeToday)}. C++ creates a transient
     * Event whose {@code date()} is the supplied date and asks whether it has occurred by {@code refDate}; the test is
     * just a date comparison.
     */
    private static boolean simpleEventHasOccurred(final Date eventDate, final Date refDate,
            final boolean includeToday) {
        if ( includeToday ) {
            return eventDate.compareTo(refDate) < 0;
        }
        return eventDate.compareTo(refDate) <= 0;
    }

    //
    // public constructors
    //

    /**
     * Extract node dates from the discount curve. C++ tries {@code InterpolatedDiscountCurve<LogLinear>},
     * {@code InterpolatedForwardCurve<BackwardFlat>}, {@code InterpolatedForwardCurve<ForwardFlat>}, and
     * {@code FlatForward} in turn (with FlatForward returning empty since it has no nodes). Anything else is rejected
     * as ISDA-incompatible.
     *
     * <p>Java port also accepts a {@link
     * org.jquantlib.termstructures.yieldcurves.PiecewiseYieldCurve} which delegates to an internal
     * {@code InterpolatedDiscountCurve} (or {@code InterpolatedForwardCurve}) baseCurve.
     */
    @SuppressWarnings( "rawtypes" )
    private static List< Date > extractYieldDates(final YieldTermStructure ts) {
        if ( ts instanceof InterpolatedDiscountCurve ) {
            final Date[] arr = ((InterpolatedDiscountCurve) ts).dates();
            return arr == null ? Collections.emptyList() : new ArrayList<>(java.util.Arrays.asList(arr));
        }
        if ( ts instanceof InterpolatedForwardCurve ) {
            final Date[] arr = ((InterpolatedForwardCurve) ts).dates();
            return arr == null ? Collections.emptyList() : new ArrayList<>(java.util.Arrays.asList(arr));
        }
        if ( ts instanceof org.jquantlib.termstructures.yieldcurves.PiecewiseYieldCurve ) {
            final Date[] arr = ((org.jquantlib.termstructures.yieldcurves.PiecewiseYieldCurve) ts).dates();
            return arr == null ? Collections.emptyList() : new ArrayList<>(java.util.Arrays.asList(arr));
        }
        if ( ts instanceof FlatForward ) {
            return Collections.emptyList();
        }
        QL.require(false, "Yield curve must be flat forward interpolated");
        return Collections.emptyList();
    }

    /**
     * Extract node dates from the credit curve. C++ accepts {@code InterpolatedSurvivalProbabilityCurve<LogLinear>},
     * {@code InterpolatedHazardRateCurve<BackwardFlat>}, {@code FlatHazardRate}. Java port also accepts
     * {@link org.jquantlib.termstructures.credit.PiecewiseDefaultCurve} which delegates to an internal
     * interpolated-curve baseCurve.
     */
    @SuppressWarnings( "rawtypes" )
    private static List< Date > extractCreditDates(final DefaultProbabilityTermStructure ts) {
        if ( ts instanceof InterpolatedSurvivalProbabilityCurve ) {
            final Date[] arr = ((InterpolatedSurvivalProbabilityCurve) ts).dates();
            return arr == null ? Collections.emptyList() : new ArrayList<>(java.util.Arrays.asList(arr));
        }
        if ( ts instanceof InterpolatedHazardRateCurve ) {
            final Date[] arr = ((InterpolatedHazardRateCurve) ts).dates();
            return arr == null ? Collections.emptyList() : new ArrayList<>(java.util.Arrays.asList(arr));
        }
        if ( ts instanceof org.jquantlib.termstructures.credit.PiecewiseDefaultCurve ) {
            final Date[] arr = ((org.jquantlib.termstructures.credit.PiecewiseDefaultCurve) ts).dates();
            return arr == null ? Collections.emptyList() : new ArrayList<>(java.util.Arrays.asList(arr));
        }
        if ( ts instanceof FlatHazardRate ) {
            return Collections.emptyList();
        }
        QL.require(false, "Credit curve must be flat forward interpolated");
        return Collections.emptyList();
    }

    public Handle< YieldTermStructure > isdaRateCurve() {
        return discountCurve_;
    }

    public Handle< DefaultProbabilityTermStructure > isdaCreditCurve() {
        return probability_;
    }

    //
    // engine entry point — mirror of C++ IsdaCdsEngine::calculate()
    //

    @Override
    public void calculate() {
        QL.require(numericalFix_ == NumericalFix.None || numericalFix_ == NumericalFix.Taylor,
                "numerical fix must be None or Taylor");
        QL.require(accrualBias_ == AccrualBias.HalfDayBias || accrualBias_ == AccrualBias.NoBias,
                "accrual bias must be HalfDayBias or NoBias");
        QL.require(forwardsInCouponPeriod_ == ForwardsInCouponPeriod.Flat
                        || forwardsInCouponPeriod_ == ForwardsInCouponPeriod.Piecewise,
                "forwards in coupon period must be Flat or Piecewise");

        final Actual365Fixed dc365 = new Actual365Fixed();
        final Actual360 dc360 = new Actual360();
        final Actual360 dc360inc = new Actual360(true);

        final Date evalDate = new Settings().evaluationDate();

        // Curve preconditions (ISDA-compatible).
        QL.require(discountCurve_ != null && !discountCurve_.empty(), "no discount term structure set");
        QL.require(probability_ != null && !probability_.empty(), "no probability term structure set");
        final YieldTermStructure disc = discountCurve_.currentLink();
        final DefaultProbabilityTermStructure prob = probability_.currentLink();

        QL.require(disc.dayCounter().equals(dc365),
                "yield term structure day counter (" + disc.dayCounter().name() + ") should be Act/365(Fixed)");
        QL.require(prob.dayCounter().equals(dc365),
                "probability term structure day counter (" + prob.dayCounter().name() + ") should be Act/365(Fixed)");
        QL.require(disc.referenceDate().eq(evalDate),
                "yield term structure reference date (" + disc.referenceDate() + ") should be evaluation date ("
                        + evalDate + ")");
        QL.require(prob.referenceDate().eq(evalDate),
                "probability term structure reference date (" + prob.referenceDate() + ") should be evaluation date ("
                        + evalDate + ")");

        final CreditDefaultSwap.ArgumentsImpl args = (CreditDefaultSwap.ArgumentsImpl) arguments_;
        final CreditDefaultSwap.ResultsImpl res = (CreditDefaultSwap.ResultsImpl) results_;

        QL.require(args.settlesAccrual, "ISDA engine not compatible with non accrual paying CDS");
        QL.require(args.paysAtDefaultTime, "ISDA engine not compatible with end period payment");
        QL.require(args.claim instanceof FaceValueClaim, "ISDA engine not compatible with non face value claim");

        final Date maturity = args.maturity;
        // effectiveProtectionStart = max(protectionStart, evalDate + 1)
        final Date evalPlusOne = evalDate.add(1);
        final Date effectiveProtectionStart = args.protectionStart.gt(evalPlusOne) ? args.protectionStart : evalPlusOne;

        // Force any underlying piecewise bootstrap to run before we read
        // dates() off the InterpolatedCurve sub-object (mirror of C++
        // discountCurve_->discount(0.0); probability_->defaultProbability(0.0)).
        disc.discount(0.0);
        prob.defaultProbability(0.0);

        // Collect node dates from both curves, union, sort.
        final List< Date > yDates = extractYieldDates(disc);
        final List< Date > cDates = extractCreditDates(prob);

        final TreeSet< Date > nodeSet = new TreeSet<>();
        nodeSet.addAll(yDates);
        nodeSet.addAll(cDates);
        final List< Date > nodes = new ArrayList<>(nodeSet);
        if ( nodes.isEmpty() ) {
            nodes.add(maturity);
        }
        final double nFix = (numericalFix_ == NumericalFix.None ? 1e-50 : 0.0);

        //
        // Protection-leg pricing (npv is always negative at this stage).
        //
        double protectionNpv = 0.0;
        Date d0 = effectiveProtectionStart.sub(1);
        double P0 = disc.discount(d0);
        double Q0 = prob.survivalProbability(d0);

        // start iterating from the first node strictly greater than
        // effectiveProtectionStart — std::upper_bound semantics.
        int startIdx = 0;
        for ( ; startIdx < nodes.size(); ++startIdx ) {
            if ( nodes.get(startIdx).gt(effectiveProtectionStart) )
                break;
        }

        for ( int it = startIdx; it < nodes.size(); ++it ) {
            Date d1 = nodes.get(it);
            boolean lastIter = false;
            if ( d1.gt(maturity) ) {
                d1 = maturity;
                lastIter = true;
            }
            final double P1 = disc.discount(d1);
            final double Q1 = prob.survivalProbability(d1);
            final double fhat = Math.log(P0) - Math.log(P1);
            final double hhat = Math.log(Q0) - Math.log(Q1);
            final double fhphh = fhat + hhat;

            if ( fhphh < 1e-4 && numericalFix_ == NumericalFix.Taylor ) {
                final double fhphhq = fhphh * fhphh;
                protectionNpv +=
                        P0 * Q0 * hhat * (1.0 - 0.5 * fhphh + (1.0 / 6.0) * fhphhq - (1.0 / 24.0) * fhphhq * fhphh
                                + (1.0 / 120.0) * fhphhq * fhphhq);
            } else {
                protectionNpv += hhat / (fhphh + nFix) * (P0 * Q0 - P1 * Q1);
            }
            d0 = d1;
            P0 = P1;
            Q0 = Q1;
            if ( lastIter )
                break;
        }
        // FaceValueClaim amount = notional * (1 - recovery)
        protectionNpv *= args.claim.amount(new Date(), args.notional, recoveryRate_);
        res.defaultLegNPV = protectionNpv;

        //
        // Premium-leg pricing (npv is always positive at this stage).
        //
        double premiumNpv = 0.0;
        double defaultAccrualNpv = 0.0;
        for ( int idx = 0; idx < args.leg.size(); ++idx ) {
            final CashFlow cf = args.leg.get(idx);
            QL.require(cf instanceof FixedRateCoupon, "expected FixedRateCoupon in CDS premium leg");
            final FixedRateCoupon coupon = (FixedRateCoupon) cf;

            final DayCounter cdc = coupon.dayCounter();
            QL.require(cdc.equals(dc365) || cdc.equals(dc360) || cdc.equals(dc360inc),
                    "ISDA engine requires a coupon day counter Act/365Fixed " + "or Act/360 (" + cdc.name() + ")");

            // premium coupons
            if ( !hasOccurred(coupon, effectiveProtectionStart) ) {
                premiumNpv +=
                        coupon.amount() * disc.discount(coupon.date()) * prob.survivalProbability(coupon.date().sub(1));
            }

            // default accruals — only if the coupon's accrualEndDate has not
            // already occurred relative to effectiveProtectionStart with the
            // includeToday=false default (mirror of C++
            // simple_event(coupon->accrualEndDate()).hasOccurred(
            //   effectiveProtectionStart, false)).
            if ( !simpleEventHasOccurred(coupon.accrualEndDate(), effectiveProtectionStart, false) ) {
                final Date couponStart = coupon.accrualStartDate().gt(effectiveProtectionStart)
                        ? coupon.accrualStartDate()
                        : effectiveProtectionStart;
                final Date start = couponStart.sub(1);
                final Date end = coupon.date().sub(1);
                final double tstart = disc.timeFromReference(coupon.accrualStartDate().sub(1)) - (
                        accrualBias_ == AccrualBias.HalfDayBias ? 1.0 / 730.0 : 0.0);

                // Build local nodes: [start, intermediary..., end].
                final List< Date > localNodes = new ArrayList<>();
                localNodes.add(start);
                if ( forwardsInCouponPeriod_ == ForwardsInCouponPeriod.Piecewise ) {
                    // upper_bound(start) and lower_bound(end) into nodes.
                    int lo = 0;
                    while ( lo < nodes.size() && !nodes.get(lo).gt(start) )
                        lo++;
                    int hi = lo;
                    while ( hi < nodes.size() && nodes.get(hi).lt(end) )
                        hi++;
                    for ( int k = lo; k < hi; ++k ) {
                        localNodes.add(nodes.get(k));
                    }
                }
                localNodes.add(end);

                double defaultAccrThisNode = 0.0;
                Date prev = localNodes.get(0);
                double tPrev = disc.timeFromReference(prev);
                double Pprev = disc.discount(prev);
                double Qprev = prob.survivalProbability(prev);

                for ( int k = 1; k < localNodes.size(); ++k ) {
                    final Date cur = localNodes.get(k);
                    final double tCur = disc.timeFromReference(cur);
                    final double Pcur = disc.discount(cur);
                    final double Qcur = prob.survivalProbability(cur);
                    final double fhat = Math.log(Pprev) - Math.log(Pcur);
                    final double hhat = Math.log(Qprev) - Math.log(Qcur);
                    final double fhphh = fhat + hhat;

                    if ( fhphh < 1e-4 && numericalFix_ == NumericalFix.Taylor ) {
                        final double fhphhq = fhphh * fhphh;
                        defaultAccrThisNode += hhat * Pprev * Qprev * (
                                (tPrev - tstart) * (1.0 - 0.5 * fhphh + (1.0 / 6.0) * fhphhq
                                        - (1.0 / 24.0) * fhphhq * fhphh) + (tCur - tPrev) * (
                                        0.5 - (1.0 / 3.0) * fhphh + (1.0 / 8.0) * fhphhq
                                                - (1.0 / 30.0) * fhphhq * fhphh));
                    } else {
                        defaultAccrThisNode += (hhat / (fhphh + nFix)) * (
                                (tCur - tPrev) * ((Pprev * Qprev - Pcur * Qcur) / (fhphh + nFix) - Pcur * Qcur)
                                        + (tPrev - tstart) * (Pprev * Qprev - Pcur * Qcur));
                    }
                    tPrev = tCur;
                    Pprev = Pcur;
                    Qprev = Qcur;
                }
                defaultAccrualNpv += defaultAccrThisNode * args.notional * coupon.rate() * 365.0 / 360.0;
            }
        }
        res.couponLegNPV = premiumNpv + defaultAccrualNpv;

        //
        // Upfront flow NPV.
        //
        double upfPVO1 = 0.0;
        res.upfrontNPV = 0.0;
        if ( !hasOccurred(args.upfrontPayment, evalDate) ) {
            upfPVO1 = disc.discount(args.upfrontPayment.date());
            if ( args.upfrontPayment.amount() != 0.0 ) {
                res.upfrontNPV = upfPVO1 * args.upfrontPayment.amount();
            }
        }

        res.accrualRebateNPV = 0.0;
        if ( args.accrualRebate != null && args.accrualRebate.amount() != 0.0 && !hasOccurred(args.accrualRebate,
                evalDate) ) {
            res.accrualRebateNPV = disc.discount(args.accrualRebate.date()) * args.accrualRebate.amount();
        }

        double upfrontSign = 1.0;
        switch ( args.side ) {
        case Seller:
            res.defaultLegNPV *= -1.0;
            res.accrualRebateNPV *= -1.0;
            break;
        case Buyer:
            res.couponLegNPV *= -1.0;
            res.upfrontNPV *= -1.0;
            upfrontSign = -1.0;
            break;
        default:
            QL.error("unknown protection side");
        }

        res.value = res.defaultLegNPV + res.couponLegNPV + res.upfrontNPV + res.accrualRebateNPV;
        res.errorEstimate = Constants.NULL_REAL;

        if ( res.couponLegNPV != 0.0 ) {
            res.fairSpread = -res.defaultLegNPV * args.spread / (res.couponLegNPV + res.accrualRebateNPV);
        } else {
            res.fairSpread = Constants.NULL_RATE;
        }

        final double upfrontSensitivity = upfPVO1 * args.notional;
        if ( upfrontSensitivity != 0.0 ) {
            res.fairUpfront =
                    -upfrontSign * (res.defaultLegNPV + res.couponLegNPV + res.accrualRebateNPV) / upfrontSensitivity;
        } else {
            res.fairUpfront = Constants.NULL_RATE;
        }

        final double basisPoint = 1.0e-4;
        if ( args.spread != 0.0 ) {
            res.couponLegBPS = res.couponLegNPV * basisPoint / args.spread;
        } else {
            res.couponLegBPS = Constants.NULL_RATE;
        }
        if ( args.upfront != null && args.upfront.doubleValue() != 0.0 ) {
            res.upfrontBPS = res.upfrontNPV * basisPoint / args.upfront.doubleValue();
        } else {
            res.upfrontBPS = Constants.NULL_RATE;
        }
    }

    //
    // helpers
    //

    /**
     * Mirror of C++ {@code CashFlow::hasOccurred(d, includeSettlementDateFlows_)}. When
     * {@code includeSettlementDateFlows_ == null} the override is unset and we defer to the no-arg
     * {@code Event::hasOccurred(date)} which consults {@code Settings::isTodaysPayments()}.
     */
    private boolean hasOccurred(final org.jquantlib.cashflow.Event event, final Date refDate) {
        if ( includeSettlementDateFlows_ == null ) {
            return event.hasOccurred(refDate);
        }
        return event.hasOccurred(refDate, includeSettlementDateFlows_.booleanValue());
    }

    /**
     * Numerical-fix mode for the {@code 1 / (fhat + hhat)} blow-up. Mirror of C++ {@code IsdaCdsEngine::NumericalFix}.
     */
    public enum NumericalFix {
        /**
         * As in [1] footnote 26 — adds {@code 1e-50} to denominators {@code fhat + hhat}.
         */
        None,
        /**
         * As in [2] — for {@code fhat + hhat < 1e-4} a Taylor expansion is used to avoid zero denominators.
         */
        Taylor
    }

    /** Default accrual bias. Mirror of C++ {@code IsdaCdsEngine::AccrualBias}. */
    public enum AccrualBias {
        /** As in [1] formula (50), second (error) term included. */
        HalfDayBias,
        /** As in [1], but second term in formula (50) omitted. */
        NoBias
    }

    /**
     * Forward-rate handling within a coupon period. Mirror of C++ {@code IsdaCdsEngine::ForwardsInCouponPeriod}.
     */
    public enum ForwardsInCouponPeriod {
        /** As in [1] formula (52), second (error) term included. */
        Flat,
        /** As in [1], but second term in formula (52) omitted. */
        Piecewise
    }
}
