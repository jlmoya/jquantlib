/*
 Copyright (C) 2026 The JQuantLib contributors

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
 Copyright (C) 2006, 2007 Giorgio Facchinetti
 Copyright (C) 2006, 2007 Mario Pucci

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.
*/

package org.jquantlib.cashflow;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.instruments.Option;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.math.distributions.NormalDistribution;
import org.jquantlib.pricingengines.BlackFormula;
import org.jquantlib.termstructures.volatilities.SmileSection;

/**
 * BGM-based pricer for {@link RangeAccrualFloatersCoupon}.
 *
 * <p>Java port of QuantLib v1.42.1 {@code RangeAccrualPricerByBgm}
 * defined in {@code ql/cashflows/rangeaccrual.{hpp,cpp}}.</p>
 *
 * <p>Computes the swaplet price as the discounted average of digital range
 * payoffs across the observation schedule. Each digital is priced with
 * either a flat-volatility or smile-aware (call-spread or correction)
 * approximation under the LIBOR market model.</p>
 *
 * @author JQuantLib Phase 5e.7 port
 */
public class RangeAccrualPricerByBgm extends RangeAccrualPricer {

    private final double correlation_;   // correlation between L(S) and L(T)
    private final boolean withSmile_;
    private final boolean byCallSpread_;

    private final SmileSection smilesOnExpiry_;
    private final SmileSection smilesOnPayment_;
    private final double eps_ = 1.0e-8;

    public RangeAccrualPricerByBgm(
            final double correlation,
            final SmileSection smilesOnExpiry,
            final SmileSection smilesOnPayment,
            final boolean withSmile,
            final boolean byCallSpread) {
        this.correlation_ = correlation;
        this.smilesOnExpiry_ = smilesOnExpiry;
        this.smilesOnPayment_ = smilesOnPayment;
        this.withSmile_ = withSmile;
        this.byCallSpread_ = byCallSpread;
    }

    @Override
    public double swapletPrice() {
        double result = 0.0;
        final double deflator = discount_ * initialValues_.get(0);
        for (int i = 0; i < observationsNo_; ++i) {
            final double digitalFloater = digitalRangePrice(
                lowerTrigger_, upperTrigger_, initialValues_.get(i + 1),
                observationTimes_.get(i), deflator);
            result += digitalFloater;
        }
        return gearing_ * (result * accrualFactor_ / observationsNo_) + spreadLegValue_;
    }

    /**
     * Drifts of the LIBOR L(U) viewed as a process whose drift switches at S
     * (start time) — returns {@code [driftBeforeFixing, driftAfterFixing]}.
     */
    protected List<Double> driftsOverPeriod(
            final double U, final double lambdaS, final double lambdaT,
            final double correlation) {
        final List<Double> result = new ArrayList<Double>(2);

        final double p = (U - startTime_) / accrualFactor_;
        final double q = (endTime_ - U) / accrualFactor_;
        final double L0T = initialValues_.get(initialValues_.size() - 1);

        final double lambdaU = lambda(U, lambdaS, lambdaT);
        final double driftBeforeFixing =
            p * accrualFactor_ * L0T / (1.0 + L0T * accrualFactor_)
                * (p * lambdaT * lambdaT + q * lambdaS * lambdaT * correlation)
            + q * lambdaS * lambdaS + p * lambdaS * lambdaT * correlation
            - 0.5 * lambdaU * lambdaU;
        final double driftAfterFixing =
            (p * accrualFactor_ * L0T / (1.0 + L0T * accrualFactor_) - 0.5)
                * lambdaT * lambdaT;

        result.add(driftBeforeFixing);
        result.add(driftAfterFixing);
        return result;
    }

    /**
     * Vols of the LIBOR L(U) viewed as a process whose vol switches at S —
     * returns {@code [lambdaBeforeFixing, lambdaAfterFixing]}.
     */
    protected List<Double> lambdasOverPeriod(
            final double U, final double lambdaS, final double lambdaT) {
        final List<Double> result = new ArrayList<Double>(2);

        final double p = (U - startTime_) / accrualFactor_;
        final double q = (endTime_ - U) / accrualFactor_;

        final double lambdaBeforeFixing = q * lambdaS + p * lambdaT;
        final double lambdaAfterFixing = lambdaT;

        result.add(lambdaBeforeFixing);
        result.add(lambdaAfterFixing);
        return result;
    }

    protected double drift(final double U, final double lambdaS,
                           final double lambdaT, final double correlation) {
        final double p = (U - startTime_) / accrualFactor_;
        final double q = (endTime_ - U) / accrualFactor_;
        final double L0T = initialValues_.get(initialValues_.size() - 1);

        final double driftBeforeFixing =
            p * accrualFactor_ * L0T / (1.0 + L0T * accrualFactor_)
                * (p * lambdaT * lambdaT + q * lambdaS * lambdaT * correlation)
            + q * lambdaS * lambdaS + p * lambdaS * lambdaT * correlation;
        final double driftAfterFixing =
            (p * accrualFactor_ * L0T / (1.0 + L0T * accrualFactor_) - 0.5)
                * lambdaT * lambdaT;

        return startTime_ > 0 ? driftBeforeFixing : driftAfterFixing;
    }

    protected double lambda(final double U, final double lambdaS, final double lambdaT) {
        final double p = (U - startTime_) / accrualFactor_;
        final double q = (endTime_ - U) / accrualFactor_;
        return startTime_ > 0 ? (q * lambdaS + p * lambdaT) : lambdaT;
    }

    protected double derDriftDerLambdaS(
            final double U, final double lambdaS, final double lambdaT,
            final double correlation) {
        final double p = (U - startTime_) / accrualFactor_;
        final double q = (endTime_ - U) / accrualFactor_;
        final double L0T = initialValues_.get(initialValues_.size() - 1);

        final double driftBeforeFixing =
            p * accrualFactor_ * L0T / (1.0 + L0T * accrualFactor_)
                * (q * lambdaT * correlation)
            + 2 * q * lambdaS + p * lambdaT * correlation;
        final double driftAfterFixing = 0.0;

        return startTime_ > 0 ? driftBeforeFixing : driftAfterFixing;
    }

    protected double derLambdaDerLambdaS(final double U) {
        if (startTime_ > 0) {
            final double q = (endTime_ - U) / accrualFactor_;
            return q;
        } else {
            return 0.0;
        }
    }

    protected double derDriftDerLambdaT(
            final double U, final double lambdaS, final double lambdaT,
            final double correlation) {
        final double p = (U - startTime_) / accrualFactor_;
        final double q = (endTime_ - U) / accrualFactor_;
        final double L0T = initialValues_.get(initialValues_.size() - 1);

        final double driftBeforeFixing =
            p * accrualFactor_ * L0T / (1.0 + L0T * accrualFactor_)
                * (2 * p * lambdaT + q * lambdaS * correlation)
            + p * lambdaS * correlation;
        final double driftAfterFixing =
            (p * accrualFactor_ * L0T / (1.0 + L0T * accrualFactor_) - 0.5)
                * 2 * lambdaT;

        return startTime_ > 0 ? driftBeforeFixing : driftAfterFixing;
    }

    protected double derLambdaDerLambdaT(final double U) {
        if (startTime_ > 0) {
            final double p = (U - startTime_) / accrualFactor_;
            return p;
        } else {
            return 0.0;
        }
    }

    protected double digitalRangePrice(
            final double lowerTrigger, final double upperTrigger,
            final double initialValue, final double expiry, final double deflator) {
        final double lowerPrice = digitalPrice(lowerTrigger, initialValue, expiry, deflator);
        final double upperPrice = digitalPrice(upperTrigger, initialValue, expiry, deflator);
        final double result = lowerPrice - upperPrice;
        QL.require(result >= 0.0,
            "RangeAccrualPricerByBgm::digitalRangePrice: digitalPrice("
            + upperTrigger + "): " + upperPrice + " >  digitalPrice("
            + lowerTrigger + "): " + lowerPrice);
        return result;
    }

    protected double digitalPrice(
            final double strike, final double initialValue,
            final double expiry, final double deflator) {
        double result = deflator;
        if (strike > eps_ / 2) {
            if (withSmile_) {
                result = digitalPriceWithSmile(strike, initialValue, expiry, deflator);
            } else {
                result = digitalPriceWithoutSmile(strike, initialValue, expiry, deflator);
            }
        }
        return result;
    }

    protected double digitalPriceWithoutSmile(
            final double strike, final double initialValue,
            final double expiry, final double deflator) {
        final double lambdaS = smilesOnExpiry_.volatility(strike);
        final double lambdaT = smilesOnPayment_.volatility(strike);

        final List<Double> lambdaU = lambdasOverPeriod(expiry, lambdaS, lambdaT);
        final double variance =
            startTime_ * lambdaU.get(0) * lambdaU.get(0)
            + (expiry - startTime_) * lambdaU.get(1) * lambdaU.get(1);

        final double lambdaSATM = smilesOnExpiry_.volatility(initialValue);
        final double lambdaTATM = smilesOnPayment_.volatility(initialValue);
        // drift of Lognormal process (of Libor) "a_U()" in paper
        final List<Double> muU = driftsOverPeriod(expiry, lambdaSATM, lambdaTATM, correlation_);
        final double adjustment = (startTime_ * muU.get(0) + (expiry - startTime_) * muU.get(1));

        final double d2 = (Math.log(initialValue / strike) + adjustment - 0.5 * variance)
                          / Math.sqrt(variance);

        final CumulativeNormalDistribution phi = new CumulativeNormalDistribution();
        final double result = deflator * phi.op(d2);

        QL.require(result > 0.0,
            "RangeAccrualPricerByBgm::digitalPriceWithoutSmile: result< 0. Result:" + result);
        QL.require(result / deflator <= 1.0,
            "RangeAccrualPricerByBgm::digitalPriceWithoutSmile: result/deflator > 1. Ratio: "
            + (result / deflator) + " result: " + result + " deflator: " + deflator);
        return result;
    }

    protected double digitalPriceWithSmile(
            final double strike, final double initialValue,
            final double expiry, final double deflator) {
        double result;
        if (byCallSpread_) {
            // Previous strike
            final double previousStrike = strike - eps_ / 2;
            double lambdaS = smilesOnExpiry_.volatility(previousStrike);
            double lambdaT = smilesOnPayment_.volatility(previousStrike);

            // drift of Lognormal process (of Libor) "a_U()"
            List<Double> lambdaU = lambdasOverPeriod(expiry, lambdaS, lambdaT);
            final double previousVariance =
                Math.max(startTime_, 0.0) * lambdaU.get(0) * lambdaU.get(0)
                + Math.min(expiry - startTime_, expiry) * lambdaU.get(1) * lambdaU.get(1);

            final double lambdaSATM = smilesOnExpiry_.volatility(initialValue);
            final double lambdaTATM = smilesOnPayment_.volatility(initialValue);
            List<Double> muU = driftsOverPeriod(expiry, lambdaSATM, lambdaTATM, correlation_);
            final double previousAdjustment = Math.exp(
                Math.max(startTime_, 0.0) * muU.get(0)
                + Math.min(expiry - startTime_, expiry) * muU.get(1));
            final double previousForward = initialValue * previousAdjustment;

            // Next strike
            final double nextStrike = strike + eps_ / 2;
            lambdaS = smilesOnExpiry_.volatility(nextStrike);
            lambdaT = smilesOnPayment_.volatility(nextStrike);

            lambdaU = lambdasOverPeriod(expiry, lambdaS, lambdaT);
            final double nextVariance =
                Math.max(startTime_, 0.0) * lambdaU.get(0) * lambdaU.get(0)
                + Math.min(expiry - startTime_, expiry) * lambdaU.get(1) * lambdaU.get(1);
            // drift of Lognormal process (of Libor) "a_U()"
            muU = driftsOverPeriod(expiry, lambdaSATM, lambdaTATM, correlation_);
            final double nextAdjustment = Math.exp(
                Math.max(startTime_, 0.0) * muU.get(0)
                + Math.min(expiry - startTime_, expiry) * muU.get(1));
            final double nextForward = initialValue * nextAdjustment;

            result = callSpreadPrice(previousForward, nextForward, previousStrike, nextStrike,
                                      deflator, previousVariance, nextVariance);
        } else {
            result = digitalPriceWithoutSmile(strike, initialValue, expiry, deflator)
                   + smileCorrection(strike, initialValue, expiry, deflator);
        }

        QL.require(result > -Math.pow(eps_, 0.5),
            "RangeAccrualPricerByBgm::digitalPriceWithSmile: result< 0 Result:" + result);
        QL.require(result / deflator <= 1.0 + Math.pow(eps_, 0.2),
            "RangeAccrualPricerByBgm::digitalPriceWithSmile: result/deflator > 1. Ratio: "
            + (result / deflator) + " result: " + result + " deflator: " + deflator);
        return result;
    }

    protected double smileCorrection(
            final double strike, final double forward,
            final double expiry, final double deflator) {
        final double previousStrike = strike - eps_ / 2;
        final double nextStrike = strike + eps_ / 2;

        final double derSmileS = (smilesOnExpiry_.volatility(nextStrike)
                                   - smilesOnExpiry_.volatility(previousStrike)) / eps_;
        final double derSmileT = (smilesOnPayment_.volatility(nextStrike)
                                   - smilesOnPayment_.volatility(previousStrike)) / eps_;

        final double lambdaS = smilesOnExpiry_.volatility(strike);
        final double lambdaT = smilesOnPayment_.volatility(strike);

        final double derLambdaDerK = derLambdaDerLambdaS(expiry) * derSmileS
                                    + derLambdaDerLambdaT(expiry) * derSmileT;

        final double lambdaSATM = smilesOnExpiry_.volatility(forward);
        final double lambdaTATM = smilesOnPayment_.volatility(forward);
        final List<Double> lambdasOverPeriodU = lambdasOverPeriod(expiry, lambdaS, lambdaT);
        final List<Double> muU = driftsOverPeriod(expiry, lambdaSATM, lambdaTATM, correlation_);

        final double variance =
            Math.max(startTime_, 0.0) * lambdasOverPeriodU.get(0) * lambdasOverPeriodU.get(0)
            + Math.min(expiry - startTime_, expiry) * lambdasOverPeriodU.get(1) * lambdasOverPeriodU.get(1);

        final double forwardAdjustment = Math.exp(
            Math.max(startTime_, 0.0) * muU.get(0)
            + Math.min(expiry - startTime_, expiry) * muU.get(1));
        final double forwardAdjusted = forward * forwardAdjustment;

        final double d1 = (Math.log(forwardAdjusted / strike) + 0.5 * variance) / Math.sqrt(variance);

        final double sqrtOfTimeToExpiry =
            (Math.max(startTime_, 0.0) * lambdasOverPeriodU.get(0)
             + Math.min(expiry - startTime_, expiry) * lambdasOverPeriodU.get(1))
            * (1.0 / Math.sqrt(variance));

        // CumulativeNormalDistribution phi unused (matches C++ commented portion);
        final NormalDistribution psi = new NormalDistribution();
        double result = -forwardAdjusted * psi.op(d1) * sqrtOfTimeToExpiry * derLambdaDerK;

        result *= deflator;

        QL.require(Math.abs(result / deflator) <= 1.0 + Math.pow(eps_, 0.2),
            "RangeAccrualPricerByBgm::smileCorrection: abs(result/deflator) > 1. Ratio: "
            + (result / deflator) + " result: " + result + " deflator: " + deflator);
        return result;
    }

    protected double callSpreadPrice(
            final double previousForward, final double nextForward,
            final double previousStrike, final double nextStrike,
            final double deflator,
            final double previousVariance, final double nextVariance) {
        final double nextCall = BlackFormula.blackFormula(
            Option.Type.Call, nextStrike, nextForward, Math.sqrt(nextVariance), deflator);
        final double previousCall = BlackFormula.blackFormula(
            Option.Type.Call, previousStrike, previousForward, Math.sqrt(previousVariance), deflator);

        QL.ensure(nextCall < previousCall,
            "RangeAccrualPricerByBgm::callSpreadPrice: nextCall > previousCall"
            + "\n nextCall: strike :" + nextStrike + "; variance: " + nextVariance
            + " adjusted initial value " + nextForward
            + "\n previousCall: strike :" + previousStrike + "; variance: " + previousVariance
            + " adjusted initial value " + previousForward);

        return (previousCall - nextCall) / (nextStrike - previousStrike);
    }
}
