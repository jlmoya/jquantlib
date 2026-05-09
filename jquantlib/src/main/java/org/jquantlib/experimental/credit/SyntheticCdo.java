/*
 Copyright (C) 2026 JQuantLib migration

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
*/

package org.jquantlib.experimental.credit;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.cashflow.FixedRateCoupon;
import org.jquantlib.cashflow.FixedRateLeg;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.Instrument;
import org.jquantlib.instruments.Protection;
import org.jquantlib.lang.reflect.ReflectConstants;
import org.jquantlib.math.Constants;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.Schedule;

/**
 * Synthetic Collateralized Debt Obligation.
 *
 * <p>Java port of QuantLib v1.42.1 {@code QuantLib::SyntheticCDO}
 * ({@code ql/experimental/credit/syntheticcdo.{hpp,cpp}}).
 *
 * <p>Prices a mezzanine CDO tranche between attachment {@code D_1} and
 * detachment {@code D_2}. Construction uses the probability-bucketing
 * algorithm from Hull-White (2004), implemented in pricing engines
 * (MidPoint / Integral) that pull the loss distribution from the
 * basket's attached {@link DefaultLossModel}.
 *
 * <p><b>Phase 4m.5 scope</b>: instrument plus arguments/results DTOs.
 * The {@code implicitCorrelation} helper is deferred — it depends on
 * {@code GaussianLHPLossModel} and {@code MidPointCDOEngine}, which are
 * scheduled for a follow-up commit (the model is in 4m.5 priority list
 * item 8 / engine in item 6).
 */
public class SyntheticCdo extends Instrument {

    private final Basket basket;
    private final Protection.Side side;
    private final Leg normalizedLeg;
    private final double upfrontRate;
    private final double runningRate;
    private final double leverageFactor;
    private final DayCounter dayCounter;
    private final BusinessDayConvention paymentConvention;

    private double premiumValue = Constants.NULL_REAL;
    private double protectionValue = Constants.NULL_REAL;
    private double upfrontPremiumValue = Constants.NULL_REAL;
    private double remainingNotional = Constants.NULL_REAL;
    private int error;
    private List<Double> expectedTrancheLoss = new ArrayList<>();

    /**
     * Creates a synthetic CDO. If {@code notional} is {@code null} the
     * leverage factor is 1 (mirrors C++ {@code ext::optional<Real>}).
     */
    public SyntheticCdo(final Basket basket,
                        final Protection.Side side,
                        final Schedule schedule,
                        final double upfrontRate,
                        final double runningRate,
                        final DayCounter dayCounter,
                        final BusinessDayConvention paymentConvention,
                        final Double notional) {
        this.basket = basket;
        this.side = side;
        this.upfrontRate = upfrontRate;
        this.runningRate = runningRate;
        this.leverageFactor = (notional != null)
                ? notional / basket.trancheNotional() : 1.0;
        this.dayCounter = dayCounter;
        this.paymentConvention = paymentConvention;

        QL.require(!basket.names().isEmpty(), "basket is empty");
        QL.require(basket.refDate().compareTo(schedule.startDate()) <= 0,
                "Basket did not exist before contract start.");

        this.normalizedLeg = new FixedRateLeg(schedule, dayCounter)
                .withNotionals(basket.trancheNotional() * leverageFactor)
                .withCouponRates(runningRate)
                .withPaymentAdjustment(paymentConvention)
                .Leg();

        // register with each issuer's default-prob curve
        for (int i = 0; i < basket.names().size(); i++) {
            basket.pool().get(basket.names().get(i))
                    .defaultProbability(basket.pool().defaultKeys().get(i))
                    .addObserver(this);
        }
        basket.addObserver(this);
    }

    public SyntheticCdo(final Basket basket,
                        final Protection.Side side,
                        final Schedule schedule,
                        final double upfrontRate,
                        final double runningRate,
                        final DayCounter dayCounter,
                        final BusinessDayConvention paymentConvention) {
        this(basket, side, schedule, upfrontRate, runningRate, dayCounter,
                paymentConvention, null);
    }

    public Basket basket() {
        return basket;
    }

    @Override
    public boolean isExpired() {
        return normalizedLeg.last().date()
                .compareTo(new Settings().evaluationDate()) <= 0;
    }

    public double premiumValue() {
        calculate();
        return premiumValue;
    }

    public double protectionValue() {
        calculate();
        return protectionValue;
    }

    public double premiumLegNPV() {
        calculate();
        return (side == Protection.Side.Buyer) ? premiumValue : -premiumValue;
    }

    public double protectionLegNPV() {
        calculate();
        return (side == Protection.Side.Buyer) ? -protectionValue : protectionValue;
    }

    public double fairPremium() {
        calculate();
        QL.require(premiumValue != 0,
                "Attempted divide by zero while calculating syntheticCDO premium.");
        return runningRate * (protectionValue - upfrontPremiumValue) / premiumValue;
    }

    public double fairUpfrontPremium() {
        calculate();
        return (protectionValue - premiumValue) / remainingNotional;
    }

    public List<Double> expectedTrancheLoss() {
        calculate();
        return expectedTrancheLoss;
    }

    public int error() {
        calculate();
        return error;
    }

    public double remainingNotional() {
        calculate();
        return remainingNotional;
    }

    public double leverageFactor() {
        return leverageFactor;
    }

    /** Last protection date — accrual end of the last fixed-rate coupon. */
    public Date maturity() {
        return ((FixedRateCoupon) normalizedLeg.last()).accrualEndDate();
    }

    @Override
    protected void setupExpired() {
        super.setupExpired();
        premiumValue = 0.0;
        protectionValue = 0.0;
        upfrontPremiumValue = 0.0;
        remainingNotional = 1.0;
        expectedTrancheLoss.clear();
    }

    @Override
    protected void setupArguments(final PricingEngine.Arguments args) {
        QL.require(args instanceof SyntheticCdo.ArgumentsImpl,
                ReflectConstants.WRONG_ARGUMENT_TYPE);
        final SyntheticCdo.ArgumentsImpl a = (SyntheticCdo.ArgumentsImpl) args;
        a.basket = basket;
        a.side = side;
        a.normalizedLeg = normalizedLeg;
        a.upfrontRate = upfrontRate;
        a.runningRate = runningRate;
        a.dayCounter = dayCounter;
        a.paymentConvention = paymentConvention;
        a.leverageFactor = leverageFactor;
    }

    @Override
    protected void fetchResults(final PricingEngine.Results r) {
        super.fetchResults(r);
        QL.require(r instanceof SyntheticCdo.ResultsImpl,
                ReflectConstants.WRONG_ARGUMENT_TYPE);
        final SyntheticCdo.ResultsImpl res = (SyntheticCdo.ResultsImpl) r;
        premiumValue = res.premiumValue;
        protectionValue = res.protectionValue;
        upfrontPremiumValue = res.upfrontPremiumValue;
        remainingNotional = res.remainingNotional;
        error = res.error;
        expectedTrancheLoss = res.expectedTrancheLoss;
    }

    public static class ArgumentsImpl implements Instrument.Arguments {
        public Basket basket;
        public Protection.Side side;
        public Leg normalizedLeg;
        public double upfrontRate = Constants.NULL_REAL;
        public double runningRate = Constants.NULL_REAL;
        public double leverageFactor;
        public DayCounter dayCounter;
        public BusinessDayConvention paymentConvention;

        @Override
        public void validate() {
            QL.require(side != null, "side not set");
            QL.require(basket != null && !basket.names().isEmpty(), "no basket given");
            QL.require(runningRate != Constants.NULL_REAL, "no premium rate given");
            QL.require(upfrontRate != Constants.NULL_REAL, "no upfront rate given");
            QL.require(dayCounter != null, "no day counter given");
        }
    }

    public static class ResultsImpl extends Instrument.ResultsImpl {
        public double premiumValue = Constants.NULL_REAL;
        public double protectionValue = Constants.NULL_REAL;
        public double upfrontPremiumValue = Constants.NULL_REAL;
        public double remainingNotional = Constants.NULL_REAL;
        public double xMin;
        public double xMax;
        public int error;
        public List<Double> expectedTrancheLoss = new ArrayList<>();

        @Override
        public void reset() {
            super.reset();
            premiumValue = Constants.NULL_REAL;
            protectionValue = Constants.NULL_REAL;
            upfrontPremiumValue = Constants.NULL_REAL;
            remainingNotional = Constants.NULL_REAL;
            error = 0;
            expectedTrancheLoss.clear();
        }
    }
}
