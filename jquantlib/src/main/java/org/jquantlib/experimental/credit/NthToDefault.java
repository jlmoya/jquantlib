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

import org.jquantlib.QL;
import org.jquantlib.Settings;
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
 * N-th to default swap.
 *
 * <p>Java port of QuantLib v1.42.1 {@code QuantLib::NthToDefault}
 * ({@code ql/experimental/credit/nthtodefault.{hpp,cpp}}).
 *
 * <p>An NTD instrument exchanges protection against the n-th default in
 * a basket of underlying credits for premium payments based on the protected notional amount. Pricing follows the
 * Hull-White (2004) algorithm; default correlation is modelled via a one-factor Gaussian copula.
 *
 * <p>Phase 4m.5 work-item 4.
 */
public class NthToDefault extends Instrument {

    private final Basket basket;
    private final int n;
    private final Protection.Side side;
    private final double nominal;
    private final Schedule premiumSchedule;
    private final double premiumRate;
    private final double upfrontRate;
    private final DayCounter dayCounter;
    private final boolean settlePremiumAccrual;

    private final Leg premiumLeg;

    // results
    private double premiumValue = Constants.NULL_REAL;
    private double protectionValue = Constants.NULL_REAL;
    private double upfrontPremiumValue = Constants.NULL_REAL;
    private double fairPremium = Constants.NULL_REAL;
    /** Pricing engine's per-instrument error estimate (mirrors C++ {@code errorEstimate_}). */
    private double ntdErrorEstimate = Constants.NULL_REAL;

    public NthToDefault(final Basket basket, final int n, final Protection.Side side, final Schedule premiumSchedule,
            final double upfrontRate, final double premiumRate, final DayCounter dayCounter, final double nominal,
            final boolean settlePremiumAccrual) {
        this.basket = basket;
        this.n = n;
        this.side = side;
        this.nominal = nominal;
        this.premiumSchedule = premiumSchedule;
        this.premiumRate = premiumRate;
        this.upfrontRate = upfrontRate;
        this.dayCounter = dayCounter;
        this.settlePremiumAccrual = settlePremiumAccrual;

        QL.require(n <= basket.size(), "NTD order provided is larger than the basket size.");
        QL.require(basket.refDate().compareTo(premiumSchedule.startDate()) <= 0,
                "Basket did not exist before contract start.");

        this.premiumLeg = new FixedRateLeg(premiumSchedule, dayCounter).withNotionals(nominal)
                .withCouponRates(premiumRate).withPaymentAdjustment(BusinessDayConvention.Unadjusted).Leg();

        basket.addObserver(this);
    }

    public int basketSize() {
        return basket.size();
    }

    public double premium() {
        return premiumRate;
    }

    public double nominal() {
        return nominal;
    }

    public DayCounter dayCounter() {
        return dayCounter;
    }

    public Protection.Side side() {
        return side;
    }

    public int rank() {
        return n;
    }

    public Date maturity() {
        return premiumSchedule.endDate();
    }

    public Basket basket() {
        return basket;
    }

    @Override
    public boolean isExpired() {
        return premiumLeg.last().date().compareTo(new Settings().evaluationDate()) <= 0;
    }

    public double fairPremium() {
        calculate();
        QL.require(fairPremium != Constants.NULL_REAL, "fair premium not available");
        return fairPremium;
    }

    public double premiumLegNPV() {
        calculate();
        QL.require(premiumValue != Constants.NULL_REAL, "premium leg not available");
        QL.require(upfrontPremiumValue != Constants.NULL_REAL, "upfront value not available");
        return premiumValue + upfrontPremiumValue;
    }

    public double protectionLegNPV() {
        calculate();
        QL.require(protectionValue != Constants.NULL_REAL, "protection leg not available");
        return protectionValue;
    }

    /**
     * Per-instrument error estimate (e.g. Monte-Carlo standard error) mirrors C++
     * {@code NthToDefault::errorEstimate()}.
     *
     * <p>Note: {@link Instrument#errorEstimate()} is already declared
     * {@code final} in the JQuantLib base, so this getter is named {@link #ntdErrorEstimate()}. Engines that propagate
     * per-instrument uncertainty should set the corresponding field on the results.
     */
    public double ntdErrorEstimate() {
        calculate();
        QL.require(ntdErrorEstimate != Constants.NULL_REAL, "error estimate not available");
        return ntdErrorEstimate;
    }

    @Override
    protected void setupExpired() {
        super.setupExpired();
        premiumValue = 0.0;
        protectionValue = 0.0;
        upfrontPremiumValue = 0.0;
        fairPremium = 0.0;
        ntdErrorEstimate = 0.0;
    }

    @Override
    protected void setupArguments(final PricingEngine.Arguments args) {
        QL.require(args instanceof NthToDefault.ArgumentsImpl, ReflectConstants.WRONG_ARGUMENT_TYPE);
        final NthToDefault.ArgumentsImpl a = (NthToDefault.ArgumentsImpl) args;
        a.basket = basket;
        a.side = side;
        a.premiumLeg = premiumLeg;
        a.ntdOrder = n;
        a.settlePremiumAccrual = settlePremiumAccrual;
        a.notional = nominal;
        a.premiumRate = premiumRate;
        a.upfrontRate = upfrontRate;
    }

    @Override
    protected void fetchResults(final PricingEngine.Results r) {
        super.fetchResults(r);
        QL.require(r instanceof NthToDefault.ResultsImpl, ReflectConstants.WRONG_ARGUMENT_TYPE);
        final NthToDefault.ResultsImpl res = (NthToDefault.ResultsImpl) r;
        premiumValue = res.premiumValue;
        protectionValue = res.protectionValue;
        upfrontPremiumValue = res.upfrontPremiumValue;
        fairPremium = res.fairPremium;
        ntdErrorEstimate = res.ntdErrorEstimate;
    }

    public static class ArgumentsImpl implements Instrument.Arguments {
        public Basket basket;
        public Protection.Side side;
        public Leg premiumLeg;

        public int ntdOrder = -1;
        public boolean settlePremiumAccrual;
        /** All names with the same weight; notional is not mapped to the basket here. */
        public double notional = Constants.NULL_REAL;
        public double premiumRate = Constants.NULL_REAL;
        public double upfrontRate = Constants.NULL_REAL;

        @Override
        public void validate() {
            QL.require(basket != null && !basket.names().isEmpty(), "no basket given");
            QL.require(side != null, "side not set");
            QL.require(premiumRate != Constants.NULL_REAL, "no premium rate given");
            QL.require(upfrontRate != Constants.NULL_REAL, "no upfront rate given");
            QL.require(notional != Constants.NULL_REAL, "no notional given");
            QL.require(ntdOrder >= 0, "no NTD order given");
        }
    }

    public static class ResultsImpl extends Instrument.ResultsImpl {
        public double premiumValue = Constants.NULL_REAL;
        public double protectionValue = Constants.NULL_REAL;
        public double upfrontPremiumValue = Constants.NULL_REAL;
        public double fairPremium = Constants.NULL_REAL;
        /** Per-instrument error estimate (e.g. MC standard error). */
        public double ntdErrorEstimate = Constants.NULL_REAL;

        @Override
        public void reset() {
            super.reset();
            premiumValue = Constants.NULL_REAL;
            protectionValue = Constants.NULL_REAL;
            upfrontPremiumValue = Constants.NULL_REAL;
            fairPremium = Constants.NULL_REAL;
            ntdErrorEstimate = Constants.NULL_REAL;
        }
    }
}
