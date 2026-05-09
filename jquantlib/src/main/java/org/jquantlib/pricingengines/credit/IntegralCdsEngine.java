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
 Copyright (C) 2008 Roland Lichters
 Copyright (C) 2008, 2009 StatPro Italia srl
 Copyright (C) 2009 Jose Aparicio

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
import org.jquantlib.time.Period;

/**
 * Integral engine for credit default swaps.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code QuantLib::IntegralCdsEngine}
 * ({@code ql/pricingengines/credit/integralcdsengine.{hpp,cpp}}, ~200 LOC).
 *
 * <p>Computes coupon-leg and default-leg NPVs by numerically integrating the
 * default-leg payoff and accrual over each coupon period in
 * {@code integrationStep} sub-intervals (vs. {@link MidPointCdsEngine}'s
 * single mid-point approximation). For each coupon period {@code
 * [effectiveStart, end]}, the engine:
 *
 * <ol>
 *   <li>Adds the survival-weighted coupon payment at the period's payment
 *       date (no integration needed — payment is contingent on no-default
 *       up to {@code paymentDate}).</li>
 *   <li>Steps from {@code effectiveStart} forward in {@code integrationStep}
 *       chunks; for each sub-interval {@code [d0, d1]}, accumulates the
 *       default-leg claim (and, conditionally, the accrued coupon payable on
 *       default) weighted by the marginal default probability {@code P(d0,d1)}
 *       and discounted at either the sub-interval end or the coupon payment
 *       date depending on {@code paysAtDefaultTime}.</li>
 * </ol>
 *
 * <p>The C++ {@code IntegralCdsEngine} ships alongside MidPoint; the integral
 * variant trades performance for accuracy when fine-grained default timing
 * matters (typical {@code integrationStep} is a day or week).
 *
 * <p><b>Phase 3c Track B.</b>
 *
 * @see MidPointCdsEngine
 * @category pricingengines.credit
 */
public class IntegralCdsEngine extends CreditDefaultSwap.Engine {

    private final Period integrationStep_;
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
     * {@code IntegralCdsEngine(const Period& integrationStep,
     *                          Handle<DefaultProbabilityTermStructure> probability,
     *                          Real recoveryRate,
     *                          Handle<YieldTermStructure> discountCurve,
     *                          const ext::optional<bool>& includeSettlementDateFlows)}.
     */
    public IntegralCdsEngine(
            final Period integrationStep,
            final Handle<DefaultProbabilityTermStructure> probability,
            final double recoveryRate,
            final Handle<YieldTermStructure> discountCurve,
            final Boolean includeSettlementDateFlows) {
        this.integrationStep_ = integrationStep;
        this.probability_ = probability;
        this.recoveryRate_ = recoveryRate;
        this.discountCurve_ = discountCurve;
        this.includeSettlementDateFlows_ = includeSettlementDateFlows;
        if (probability_ != null) {
            probability_.addObserver(this);
        }
        if (discountCurve_ != null) {
            discountCurve_.addObserver(this);
        }
    }

    /** Convenience overload defaulting {@code includeSettlementDateFlows = null}
     *  (C++ {@code ext::nullopt} → consult
     *  {@code Settings::includeTodaysCashFlows()}). */
    public IntegralCdsEngine(
            final Period integrationStep,
            final Handle<DefaultProbabilityTermStructure> probability,
            final double recoveryRate,
            final Handle<YieldTermStructure> discountCurve) {
        this(integrationStep, probability, recoveryRate, discountCurve, null);
    }

    //
    // engine entry point
    //

    @Override
    public void calculate() {
        QL.require(integrationStep_ != null && integrationStep_.length() != 0,
                "null period set");
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
            if (i == 0) {
                startDate = args.protectionStart;
            }
            final Date effectiveStartDate =
                    (startDate.le(today) && today.le(endDate)) ? today : startDate;
            final double couponAmount = coupon.amount();

            final double S = prob.survivalProbability(paymentDate);

            // On one side, we add the fixed rate payments in case of survival.
            res.couponLegNPV += S * couponAmount * disc.discount(paymentDate);

            // On the other side, we add the payment (and possibly the accrual)
            // in case of default. Numerical integration over the period:
            // step from effectiveStartDate to endDate in `integrationStep_`
            // chunks.
            final Period step = integrationStep_;
            Date d0 = effectiveStartDate;
            Date d1 = minDate(d0.add(step), endDate);
            double P0 = prob.defaultProbability(d0);
            final double endDiscount = disc.discount(paymentDate);
            do {
                final double B = args.paysAtDefaultTime
                        ? disc.discount(d1)
                        : endDiscount;

                final double P1 = prob.defaultProbability(d1);
                final double dP = P1 - P0;

                // accrual...
                if (args.settlesAccrual) {
                    if (args.paysAtDefaultTime) {
                        res.couponLegNPV += coupon.accruedAmount(d1) * B * dP;
                    } else {
                        res.couponLegNPV += couponAmount * B * dP;
                    }
                }

                // ...and claim.
                final double claim =
                        args.claim.amount(d1, args.notional, recoveryRate_);
                res.defaultLegNPV += claim * B * dP;

                // setup for next time around the loop
                P0 = P1;
                d0 = d1;
                d1 = minDate(d0.add(step), endDate);
            } while (d0.lt(endDate));
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
    }

    /** Mirrors C++ {@code std::min(d0 + step, endDate)} via two-arg min. */
    private static Date minDate(final Date a, final Date b) {
        return a.le(b) ? a : b;
    }

    /**
     * Mirrors C++ {@code CashFlow::hasOccurred(d, includeSettlementDateFlows_)}.
     */
    private boolean hasOccurred(final CashFlow cf, final Date refDate) {
        if (includeSettlementDateFlows_ == null) {
            return cf.hasOccurred(refDate);
        }
        return cf.hasOccurred(refDate, includeSettlementDateFlows_.booleanValue());
    }
}
