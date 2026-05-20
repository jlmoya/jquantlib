/*
 Copyright (C) 2022 Klaus Spanderen
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.pricingengines.vanilla;

import org.jquantlib.QL;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.instruments.VanillaOption;
import org.jquantlib.math.Closeness;
import org.jquantlib.pricingengines.BlackCalculator;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;

/**
 * Common base for the QD-family American engines providing the put-call parity
 * dispatch and the small-value/zero-vol edge cases. Phase 1 closure A1 port of
 * {@code QuantLib::detail::QdPutCallParityEngine}
 * (v1.42.1 ql/pricingengines/vanilla/qdplusamericanengine.{hpp,cpp}; pinned
 * commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}).
 */
abstract class QdPutCallParityEngine extends VanillaOption.EngineImpl {

    protected final GeneralizedBlackScholesProcess process_;
    protected final OneAssetOption.ArgumentsImpl a;
    protected final OneAssetOption.ResultsImpl r;

    protected QdPutCallParityEngine(final GeneralizedBlackScholesProcess process) {
        this.a = (OneAssetOption.ArgumentsImpl) arguments_;
        this.r = (OneAssetOption.ResultsImpl) results_;
        this.process_ = process;
        // C++ default-constructs a null shared_ptr in the boundary-only test paths; we must
        // tolerate a null process when only static/boundary methods are used.
        if (this.process_ != null) {
            this.process_.addObserver(this);
        }
    }

    /** Calculate the put price (American QD-family engines call this). */
    protected abstract double calculatePut(double S, double K, double r, double q,
                                           double vol, double T);

    @Override
    public void calculate() /*@ReadOnly*/ {
        QL.require(a.exercise.type() == Exercise.Type.American, "not an American option");
        QL.require(a.payoff instanceof StrikedTypePayoff, "non-striked payoff given");
        final StrikedTypePayoff payoff = (StrikedTypePayoff) a.payoff;

        final double spot = process_.x0();
        QL.require(spot >= 0.0, "negative underlying given");

        final org.jquantlib.time.Date maturity = a.exercise.lastDate();
        final double T = process_.time(maturity);
        final double S = process_.x0();
        final double K = payoff.strike();
        final double rfDisc = process_.riskFreeRate().currentLink().discount(maturity);
        final double divDisc = process_.dividendYield().currentLink().discount(maturity);
        final double rRate = -Math.log(rfDisc) / T;
        final double qRate = -Math.log(divDisc) / T;
        final double vol = process_.blackVolatility().currentLink().blackVol(T, K);

        QL.require(S >= 0.0, "zero or positive underlying value is required");
        QL.require(K >= 0.0, "zero or positive strike is required");
        QL.require(vol >= 0.0, "zero or positive volatility is required");

        final double value;
        if (payoff.optionType() == Option.Type.Put) {
            value = calculatePutWithEdgeCases(S, K, rRate, qRate, vol, T);
        } else if (payoff.optionType() == Option.Type.Call) {
            value = calculatePutWithEdgeCases(K, S, qRate, rRate, vol, T);
        } else {
            throw new IllegalStateException("unknown option type");
        }
        this.r.value = value;
    }

    private double calculatePutWithEdgeCases(final double S, final double K, final double r,
                                             final double q, final double vol, final double T) {
        if (Closeness.isClose(K, 0.0)) {
            return 0.0;
        }
        if (Closeness.isClose(S, 0.0)) {
            return Math.max(K, K * Math.exp(-r * T));
        }
        if (r <= 0.0 && r <= q) {
            return Math.max(0.0,
                    new BlackCalculator(Option.Type.Put, K, S * Math.exp((r - q) * T),
                            vol * Math.sqrt(T), Math.exp(-r * T)).value());
        }
        if (Closeness.isClose(vol, 0.0)) {
            final double npv0 = Math.max(0.0, K - S);
            final double npvT = Math.max(0.0, K * Math.exp(-r * T) - S * Math.exp(-q * T));
            final double extremT = Closeness.isCloseEnough(r, q)
                    ? Double.MAX_VALUE
                    : Math.log(r * K / (q * S)) / (r - q);
            if (extremT > 0.0 && extremT < T) {
                final double npvExtr = Math.max(0.0,
                        K * Math.exp(-r * extremT) - S * Math.exp(-q * extremT));
                return Math.max(Math.max(npv0, npvT), npvExtr);
            } else {
                return Math.max(npv0, npvT);
            }
        }
        return calculatePut(S, K, r, q, vol, T);
    }
}
