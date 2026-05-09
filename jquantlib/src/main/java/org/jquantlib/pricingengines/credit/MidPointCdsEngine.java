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
 Copyright (C) 2008, 2009 StatPro Italia srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.pricingengines.credit;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.FixedRateCoupon;
import org.jquantlib.instruments.CreditDefaultSwap;
import org.jquantlib.instruments.Protection;
import org.jquantlib.math.Constants;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.DefaultProbabilityTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;

/**
 * Mid-point engine for credit default swaps.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code QuantLib::MidPointCdsEngine}
 * ({@code ql/pricingengines/credit/midpointcdsengine.{hpp,cpp}}, 239 LOC).
 *
 * <p>The engine computes coupon-leg and default-leg NPVs by approximating each
 * coupon period's expected default time by its mid-point — an
 * O(num-coupons) closed-form computation. Mirrors the C++
 * {@code MidPointCdsEngine::calculate()} algorithm verbatim:
 *
 * <ol>
 *   <li>Upfront and accrual-rebate NPVs (skipped if cash flows have already
 *       occurred per {@code includeSettlementDateFlows}).</li>
 *   <li>For each coupon, compute survival-to-payment-date and
 *       default-in-period probabilities; add coupon contribution discounted
 *       at the payment date and protection contribution discounted at the
 *       mid-point of the period.</li>
 *   <li>Apply the buyer/seller sign convention.</li>
 *   <li>Derive {@code fairSpread} and {@code fairUpfront} (Null when their
 *       respective denominators are zero).</li>
 * </ol>
 *
 * <p><b>Phase 3b Track B</b> — first concrete CDS pricing engine. The
 * {@code IsdaCdsEngine} and {@code IntegralCdsEngine} variants ship with
 * Phase 3c.
 *
 * @category pricingengines.credit
 */
public class MidPointCdsEngine extends CreditDefaultSwap.Engine {

    private final Handle<DefaultProbabilityTermStructure> probability_;
    private final double recoveryRate_;
    private final Handle<YieldTermStructure> discountCurve_;
    /** Mirrors C++ {@code ext::optional<bool> includeSettlementDateFlows_}.
     *  {@code null} == "use Settings::includeTodaysCashFlows()". */
    private final Boolean includeSettlementDateFlows_;

    //
    // public constructors
    //

    /**
     * Full constructor mirroring C++
     * {@code MidPointCdsEngine(Handle<DefaultProbabilityTermStructure>,
     *                          Real recoveryRate,
     *                          Handle<YieldTermStructure> discountCurve,
     *                          const ext::optional<bool>& includeSettlementDateFlows)}.
     */
    public MidPointCdsEngine(
            final Handle<DefaultProbabilityTermStructure> probability,
            final double recoveryRate,
            final Handle<YieldTermStructure> discountCurve,
            final Boolean includeSettlementDateFlows) {
        this.probability_ = probability;
        this.recoveryRate_ = recoveryRate;
        this.discountCurve_ = discountCurve;
        this.includeSettlementDateFlows_ = includeSettlementDateFlows;
        // C++ registerWith(probability_) / registerWith(discountCurve_).
        if (probability_ != null) {
            probability_.addObserver(this);
        }
        if (discountCurve_ != null) {
            discountCurve_.addObserver(this);
        }
    }

    /** Convenience overload defaulting {@code includeSettlementDateFlows = null}
     *  (C++ {@code ext::nullopt} → consult {@code Settings::includeTodaysCashFlows()}). */
    public MidPointCdsEngine(
            final Handle<DefaultProbabilityTermStructure> probability,
            final double recoveryRate,
            final Handle<YieldTermStructure> discountCurve) {
        this(probability, recoveryRate, discountCurve, null);
    }

    //
    // engine entry point
    //

    @Override
    public void calculate() {
        QL.require(discountCurve_ != null && !discountCurve_.empty(),
                "no discount term structure set");
        QL.require(probability_ != null && !probability_.empty(),
                "no probability term structure set");

        final CreditDefaultSwap.ArgumentsImpl args =
                (CreditDefaultSwap.ArgumentsImpl) arguments_;
        final CreditDefaultSwap.ResultsImpl res =
                (CreditDefaultSwap.ResultsImpl) results_;

        final DefaultProbabilityTermStructure prob = probability_.currentLink();
        final YieldTermStructure disc = discountCurve_.currentLink();

        final Date today = new Settings().evaluationDate();
        final Date settlementDate = disc.referenceDate();

        // Upfront amount.
        double upfPVO1 = 0.0;
        res.upfrontNPV = 0.0;
        if (!hasOccurred(args.upfrontPayment, settlementDate)) {
            upfPVO1 = disc.discount(args.upfrontPayment.date());
            res.upfrontNPV = upfPVO1 * args.upfrontPayment.amount();
        }

        // Accrual rebate.
        res.accrualRebateNPV = 0.0;
        if (args.accrualRebate != null
                && !hasOccurred(args.accrualRebate, settlementDate)) {
            res.accrualRebateNPV =
                    disc.discount(args.accrualRebate.date())
                    * args.accrualRebate.amount();
        }

        res.couponLegNPV = 0.0;
        res.defaultLegNPV = 0.0;
        for (int i = 0; i < args.leg.size(); ++i) {
            final CashFlow cf = args.leg.get(i);
            if (hasOccurred(cf, settlementDate)) {
                continue;
            }

            QL.require(cf instanceof FixedRateCoupon,
                    "expected FixedRateCoupon in CDS premium leg");
            final FixedRateCoupon coupon = (FixedRateCoupon) cf;

            // In order to avoid a few switches, we calculate the NPV of both
            // legs as a positive quantity. We'll give them the right sign at
            // the end.
            final Date paymentDate = coupon.date();
            Date startDate = coupon.accrualStartDate();
            final Date endDate = coupon.accrualEndDate();
            // this is the only point where it might not coincide
            if (i == 0) {
                startDate = args.protectionStart;
            }
            final Date effectiveStartDate =
                    (startDate.le(today) && today.le(endDate)) ? today : startDate;
            // mid-point — Date.sub(Date) returns long; cast safe because all
            // CDS coupon periods are far smaller than Integer.MAX_VALUE days.
            final long midOffset = (endDate.sub(effectiveStartDate)) / 2L;
            final Date defaultDate = effectiveStartDate.add((int) midOffset);

            final double S = prob.survivalProbability(paymentDate);
            final double P = prob.defaultProbability(effectiveStartDate, endDate);

            // on one side, we add the fixed rate payments in case of survival...
            res.couponLegNPV += S * coupon.amount() * disc.discount(paymentDate);
            // ...possibly including accrual in case of default.
            if (args.settlesAccrual) {
                if (args.paysAtDefaultTime) {
                    res.couponLegNPV +=
                            P * coupon.accruedAmount(defaultDate)
                              * disc.discount(defaultDate);
                } else {
                    // pays at the end
                    res.couponLegNPV +=
                            P * coupon.amount() * disc.discount(paymentDate);
                }
            }

            // on the other side, we add the payment in case of default.
            final double claim =
                    args.claim.amount(defaultDate, args.notional, recoveryRate_);
            if (args.paysAtDefaultTime) {
                res.defaultLegNPV += P * claim * disc.discount(defaultDate);
            } else {
                res.defaultLegNPV += P * claim * disc.discount(paymentDate);
            }
        }

        double upfrontSign = 1.0;
        switch (args.side) {
          case Seller:
            res.defaultLegNPV    *= -1.0;
            res.accrualRebateNPV *= -1.0;
            break;
          case Buyer:
            res.couponLegNPV *= -1.0;
            res.upfrontNPV   *= -1.0;
            upfrontSign = -1.0;
            break;
          default:
            QL.error("unknown protection side");
        }

        res.value = res.defaultLegNPV + res.couponLegNPV
                  + res.upfrontNPV + res.accrualRebateNPV;
        res.errorEstimate = Constants.NULL_REAL;

        if (res.couponLegNPV != 0.0) {
            res.fairSpread = -res.defaultLegNPV * args.spread
                    / (res.couponLegNPV + res.accrualRebateNPV);
        } else {
            res.fairSpread = Constants.NULL_RATE;
        }

        if (upfPVO1 > 0.0) {
            res.fairUpfront = -upfrontSign * (res.defaultLegNPV
                    + res.couponLegNPV + res.accrualRebateNPV)
                    / (upfPVO1 * args.notional);
        } else {
            res.fairUpfront = Constants.NULL_RATE;
        }

        final double basisPoint = 1.0e-4;

        if (args.spread != 0.0) {
            res.couponLegBPS = res.couponLegNPV * basisPoint / args.spread;
        } else {
            res.couponLegBPS = Constants.NULL_RATE;
        }

        if (args.upfront != null && args.upfront.doubleValue() != 0.0) {
            res.upfrontBPS = res.upfrontNPV * basisPoint
                    / args.upfront.doubleValue();
        } else {
            res.upfrontBPS = Constants.NULL_RATE;
        }

        // suppress unused-variable warning while keeping Buyer/Seller branch
        // structure 1:1 with C++ (upfrontSign feeds res.fairUpfront above).
        if (Double.isNaN(upfrontSign)) {
            QL.require(false, "upfrontSign is NaN");
        }
    }

    /**
     * Mirrors C++ {@code CashFlow::hasOccurred(d, includeSettlementDateFlows_)}.
     * The C++ default for {@code includeSettlementDateFlows_ == ext::nullopt}
     * is {@code Settings::includeTodaysCashFlows()}; in Java the corresponding
     * setting is {@link Settings#isTodaysPayments()}.
     */
    private boolean hasOccurred(final CashFlow cf, final Date refDate) {
        if (includeSettlementDateFlows_ == null) {
            return cf.hasOccurred(refDate);
        }
        return cf.hasOccurred(refDate, includeSettlementDateFlows_.booleanValue());
    }
}
