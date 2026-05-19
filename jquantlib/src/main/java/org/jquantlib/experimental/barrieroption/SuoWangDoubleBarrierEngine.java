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
 Copyright (C) 2013 Yue Tian
*/

package org.jquantlib.experimental.barrieroption;

import org.jquantlib.QL;
import org.jquantlib.instruments.EuropeanOption;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.pricingengines.AnalyticEuropeanEngine;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.time.Frequency;

/**
 * Pricing engine for double-barrier options using the analytical formulae of Wulin Suo and Yong Wang ("Barrier Option
 * Pricing").
 * <p>
 * Mirrors {@code QuantLib::SuoWangDoubleBarrierEngine} from
 * {@code ql/experimental/barrieroption/suowangdoublebarrierengine.cpp} (v1.42.1).
 *
 * <p>Supports {@link DoubleBarrierType#KnockIn} and {@link DoubleBarrierType#KnockOut}.
 * The series truncation defaults to 5 terms (matching the C++ default).
 *
 * @author JQuantLib migration
 */
public class SuoWangDoubleBarrierEngine extends DoubleBarrierOption.EngineImpl {

    private final GeneralizedBlackScholesProcess process_;
    private final int series_;
    private final CumulativeNormalDistribution f_;
    private final DoubleBarrierOption.ArgumentsImpl a;
    private final OneAssetOption.ResultsImpl r;

    public SuoWangDoubleBarrierEngine(final GeneralizedBlackScholesProcess process) {
        this(process, 5);
    }

    public SuoWangDoubleBarrierEngine(final GeneralizedBlackScholesProcess process, final int series) {
        this.process_ = process;
        this.series_ = series;
        this.f_ = new CumulativeNormalDistribution();
        this.a = (DoubleBarrierOption.ArgumentsImpl) arguments_;
        this.r = (OneAssetOption.ResultsImpl) results_;
        this.process_.addObserver(this);
    }

    @Override
    public void calculate() {
        QL.require(a.payoff instanceof PlainVanillaPayoff, "non-plain payoff given");
        final PlainVanillaPayoff payoff = (PlainVanillaPayoff) a.payoff;
        QL.require(payoff.strike() > 0.0, "strike must be positive");

        final double K = payoff.strike();
        final double S = process_.x0();
        QL.require(S > 0.0, "negative or null underlying given");
        QL.require(!triggered(S), "barrier touched");

        final DoubleBarrierType barrierType = a.barrierType;
        QL.require(barrierType == DoubleBarrierType.KnockOut || barrierType == DoubleBarrierType.KnockIn,
                "only KnockIn and KnockOut options supported");

        final double L = a.barrier_lo;
        final double H = a.barrier_hi;
        final double K_up = Math.min(H, K);
        final double K_down = Math.max(L, K);
        final double T = residualTime();
        final double rd = riskFreeRate();
        final double dd = riskFreeDiscount();
        final double rf = dividendYield();
        final double df = dividendDiscount();
        final double vol = volatility();
        final double mu = rd - rf - vol * vol / 2.0;
        final double sgn = mu > 0 ? 1.0 : (mu < 0 ? -1.0 : 0.0);
        // rebate
        final double R_L = a.rebate;
        final double R_H = a.rebate;

        // european reference value
        final EuropeanOption europeanOption = new EuropeanOption(payoff, a.exercise);
        europeanOption.setPricingEngine(new AnalyticEuropeanEngine(process_));
        final double european = europeanOption.NPV();

        double barrierOut = 0.0;
        double rebateIn = 0.0;
        for ( int n = -series_; n < series_; n++ ) {
            final double d1 = D(S / H * Math.pow(L / H, 2.0 * n), vol * vol + mu, vol, T);
            final double d2 = d1 - vol * Math.sqrt(T);
            final double g1 = D(H / S * Math.pow(L / H, 2.0 * n - 1.0), vol * vol + mu, vol, T);
            final double g2 = g1 - vol * Math.sqrt(T);
            final double h1 = D(S / H * Math.pow(L / H, 2.0 * n - 1.0), vol * vol + mu, vol, T);
            final double h2 = h1 - vol * Math.sqrt(T);
            final double k1 = D(L / S * Math.pow(L / H, 2.0 * n - 1.0), vol * vol + mu, vol, T);
            final double k2 = k1 - vol * Math.sqrt(T);
            final double d1_down = D(S / K_down * Math.pow(L / H, 2.0 * n), vol * vol + mu, vol, T);
            final double d2_down = d1_down - vol * Math.sqrt(T);
            final double d1_up = D(S / K_up * Math.pow(L / H, 2.0 * n), vol * vol + mu, vol, T);
            final double d2_up = d1_up - vol * Math.sqrt(T);
            final double k1_down = D((H * H) / (K_down * S) * Math.pow(L / H, 2.0 * n), vol * vol + mu, vol, T);
            final double k2_down = k1_down - vol * Math.sqrt(T);
            final double k1_up = D((H * H) / (K_up * S) * Math.pow(L / H, 2.0 * n), vol * vol + mu, vol, T);
            final double k2_up = k1_up - vol * Math.sqrt(T);

            if ( payoff.optionType() == Option.Type.Call ) {
                barrierOut += Math.pow(L / H, 2.0 * n * mu / (vol * vol)) * (
                        df * S * Math.pow(L / H, 2.0 * n) * (f_.op(d1_down) - f_.op(d1)) - dd * K * (f_.op(d2_down)
                                - f_.op(d2)) - df * Math.pow(L / H, 2.0 * n) * H * H / S * Math.pow(H / S,
                                2.0 * mu / (vol * vol)) * (f_.op(k1_down) - f_.op(k1)) + dd * K * Math.pow(H / S,
                                2.0 * mu / (vol * vol)) * (f_.op(k2_down) - f_.op(k2)));
            } else if ( payoff.optionType() == Option.Type.Put ) {
                barrierOut += Math.pow(L / H, 2.0 * n * mu / (vol * vol)) * (
                        dd * K * (f_.op(h2) - f_.op(d2_up)) - df * S * Math.pow(L / H, 2.0 * n) * (f_.op(h1) - f_.op(
                                d1_up)) - dd * K * Math.pow(H / S, 2.0 * mu / (vol * vol)) * (f_.op(g2) - f_.op(k2_up))
                                + df * Math.pow(L / H, 2.0 * n) * H * H / S * Math.pow(H / S, 2.0 * mu / (vol * vol))
                                * (f_.op(g1) - f_.op(k1_up)));
            } else {
                throw new LibraryException("option type not recognized");
            }

            final double v1 = D(H / S * Math.pow(H / L, 2.0 * n), -mu, vol, T);
            final double v2 = D(H / S * Math.pow(H / L, 2.0 * n), mu, vol, T);
            final double v3 = D(S / L * Math.pow(H / L, 2.0 * n), -mu, vol, T);
            final double v4 = D(S / L * Math.pow(H / L, 2.0 * n), mu, vol, T);
            rebateIn += dd * R_H * sgn * (Math.pow(L / H, 2.0 * n * mu / (vol * vol)) * f_.op(sgn * v1)
                    - Math.pow(H / S, 2.0 * mu / (vol * vol)) * f_.op(-sgn * v2)) + dd * R_L * sgn * (
                    Math.pow(L / S, 2.0 * mu / (vol * vol)) * f_.op(-sgn * v3)
                            - Math.pow(H / L, 2.0 * n * mu / (vol * vol)) * f_.op(sgn * v4));
        }

        // rebate paid at maturity
        if ( barrierType == DoubleBarrierType.KnockOut ) {
            r.value = barrierOut;
        } else {
            r.value = european - barrierOut;
        }
        // additionalResults map: not modeled in JQuantLib (rebateIn unused — see C++ for parity)
        // Touching the unused locals so static analyzers don't strip them; they document the C++ contract.
        if ( false ) {
            System.out.println(rebateIn);
        }
    }

    //
    // helpers (mirror C++ private members)
    //

    private double residualTime() {
        return process_.time(a.exercise.lastDate());
    }

    private double volatility() {
        final PlainVanillaPayoff payoff = (PlainVanillaPayoff) a.payoff;
        return process_.blackVolatility().currentLink().blackVol(residualTime(), payoff.strike());
    }

    private double riskFreeRate() {
        return process_.riskFreeRate().currentLink()
                .zeroRate(residualTime(), Compounding.Continuous, Frequency.NoFrequency, false).rate();
    }

    private double riskFreeDiscount() {
        return process_.riskFreeRate().currentLink().discount(residualTime());
    }

    private double dividendYield() {
        return process_.dividendYield().currentLink()
                .zeroRate(residualTime(), Compounding.Continuous, Frequency.NoFrequency, false).rate();
    }

    private double dividendDiscount() {
        return process_.dividendYield().currentLink().discount(residualTime());
    }

    private double D(final double X, final double lambda, final double sigma, final double T) {
        return (Math.log(X) + lambda * T) / (sigma * Math.sqrt(T));
    }
}
