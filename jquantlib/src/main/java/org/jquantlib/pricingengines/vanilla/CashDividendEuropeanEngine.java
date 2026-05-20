/*
 Copyright (C) 2026 Jose Moya

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
 Copyright (C) 2025 Klaus Spanderen

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.
 */

package org.jquantlib.pricingengines.vanilla;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.cashflow.Dividend;
import org.jquantlib.cashflow.FixedDividend;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.AverageBasketPayoff;
import org.jquantlib.instruments.BasketOption;
import org.jquantlib.instruments.DividendSchedule;
import org.jquantlib.instruments.DividendVanillaOption;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.instruments.VanillaOption;
import org.jquantlib.math.Constants;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.pricingengines.AnalyticEuropeanEngine;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.basket.ChoiBasketEngine;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.BlackConstantVol;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Date;

/**
 * (Semi)-Analytic pricing engine for European options with cash dividends.
 *
 * <p>Reference: Jherek Healy, 2021. <i>The Pricing of Vanilla Options with Cash
 * Dividends as a Classic Vanilla Basket Option Problem</i>,
 * <a href="https://arxiv.org/pdf/2106.12971">arXiv:2106.12971</a>.</p>
 *
 * <p>Faithful port of C++ v1.42.1
 * {@code ql/pricingengines/vanilla/cashdividendeuropeanengine.{hpp,cpp}}.
 * Pinned commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.</p>
 *
 * <p>The {@code Escrowed} branch (and the single-dividend-at-settlement edge case)
 * defers to {@link AnalyticDividendEuropeanEngine}. The {@code Spot} branch (with
 * multiple non-trivial dividends) reduces to a basket option priced via
 * {@link ChoiBasketEngine}.</p>
 *
 * @author Jose Moya
 */
public class CashDividendEuropeanEngine extends VanillaOption.EngineImpl {

    public enum CashDividendModel {
        Spot,
        Escrowed
    }

    private final GeneralizedBlackScholesProcess process_;
    private final DividendSchedule dividends_;
    private final CashDividendModel cashDividendModel_;
    private final OneAssetOption.ArgumentsImpl a;
    private final OneAssetOption.ResultsImpl r;

    public CashDividendEuropeanEngine(final GeneralizedBlackScholesProcess process,
            final DividendSchedule dividends) {
        this(process, dividends, CashDividendModel.Spot);
    }

    public CashDividendEuropeanEngine(final GeneralizedBlackScholesProcess process,
            final DividendSchedule dividends, final CashDividendModel cashDividendModel) {
        this.a = (OneAssetOption.ArgumentsImpl) arguments_;
        this.r = (OneAssetOption.ResultsImpl) results_;
        this.process_ = process;
        this.dividends_ = dividends;
        this.cashDividendModel_ = cashDividendModel;
        this.process_.addObserver(this);
    }

    @Override
    public void calculate() {
        QL.require(a.exercise.type() == Exercise.Type.European, "not an European option");
        final EuropeanExercise exercise = (EuropeanExercise) a.exercise;

        QL.require(a.payoff instanceof PlainVanillaPayoff, "non-striked payoff given");
        final StrikedTypePayoff payoff = (StrikedTypePayoff) a.payoff;
        final double strike = payoff.strike();

        final Handle< YieldTermStructure > rTS = process_.riskFreeRate();
        final Handle< YieldTermStructure > qTS = process_.dividendYield();

        final Date settlementDate = rTS.currentLink().referenceDate();
        final Date maturityDate = exercise.lastDate();
        final double maturity = process_.time(maturityDate);

        // Filter and sort dividends to keep only those strictly in [settlement, maturity] with positive amount.
        final List< Dividend > dividends = new ArrayList<>();
        for ( final Dividend div : dividends_ ) {
            if ( div.date().ge(settlementDate) && div.date().le(maturityDate) && div.amount() > 0.0 ) {
                dividends.add(div);
            }
        }
        Collections.sort(dividends, new Comparator< Dividend >() {
            @Override
            public int compare(final Dividend d1, final Dividend d2) {
                return d1.date().compareTo(d2.date());
            }
        });

        // Branch 1 — Escrowed model OR single dividend on the settlement date itself OR strike==0
        // (degenerate Choi-basket case) defers to AnalyticDividendEuropeanEngine.
        // For strike==0 a Spot call collapses to the closed-form S0*Dq - sum(div_i*Dr_i/Dq_i)*Dq,
        // which AnalyticDividendEuropeanEngine reproduces exactly via its spot-adjustment formula.
        if ( cashDividendModel_ == CashDividendModel.Escrowed
                || (dividends.size() == 1 && dividends.get(dividends.size() - 1).date().eq(settlementDate))
                || strike == 0.0 ) {
            // Build a DividendVanillaOption mirroring the **filtered** dividends list (C++ passes
            // dividends_ unfiltered but its AnalyticDividendEuropeanEngine ignores out-of-range entries;
            // the Java DividendVanillaOption.ArgumentsImpl.validate() rejects them, so we filter here).
            final List< Date > dDates = new ArrayList<>();
            final List< Double > dAmounts = new ArrayList<>();
            for ( final Dividend d : dividends ) {
                dDates.add(d.date());
                dAmounts.add(d.amount());
            }
            final DividendVanillaOption option = new DividendVanillaOption(payoff, exercise, dDates, dAmounts);
            option.setPricingEngine(new AnalyticDividendEuropeanEngine(process_));
            r.value = option.NPV();
            return;
        }

        // Build "underlyings" — clone the filtered dividends list and append the strike as a
        // synthetic dividend at maturity (or fold strike into the last dividend if it lands at maturity).
        final List< Dividend > underlyings = new ArrayList<>(dividends);
        if ( !underlyings.isEmpty() && underlyings.get(underlyings.size() - 1).date().eq(maturityDate) ) {
            final Dividend last = underlyings.get(underlyings.size() - 1);
            underlyings.set(underlyings.size() - 1, new FixedDividend(last.amount() + strike, maturityDate));
        } else {
            underlyings.add(new FixedDividend(strike, maturityDate));
        }

        // Branch 2 — single underlying degenerates to a plain AnalyticEuropeanEngine call on a
        // payoff with strike = (single underlying amount). The original process_ is reused.
        if ( underlyings.size() == 1 ) {
            final double newStrike = underlyings.get(0).amount();
            final VanillaOption option = new VanillaOption(new PlainVanillaPayoff(payoff.optionType(), newStrike),
                    exercise);
            option.setPricingEngine(new AnalyticEuropeanEngine(process_));
            r.value = option.NPV();
            return;
        }

        // Branch 3 — n underlyings: build n virtual GBM processes and price via ChoiBasketEngine.
        final Handle< BlackVolTermStructure > volTS = process_.blackVolatility();
        final Date volRefDate = volTS.currentLink().referenceDate();
        final org.jquantlib.daycounters.DayCounter volDc = volTS.currentLink().dayCounter();

        final Handle< YieldTermStructure > zeroRateTS = new Handle< YieldTermStructure >(
                new FlatForward(settlementDate, 0.0, rTS.currentLink().dayCounter()));

        final List< GeneralizedBlackScholesProcess > processes = new ArrayList<>();
        for ( final Dividend div : underlyings ) {
            final double r_mod = Math.log(rTS.currentLink().discount(div.date())) / maturity;
            final double q_mod = Math.log(qTS.currentLink().discount(div.date())) / maturity;

            final Handle< Quote > divAmt = new Handle< Quote >(new SimpleQuote(div.amount()));
            final Handle< YieldTermStructure > qmrTS = new Handle< YieldTermStructure >(
                    new FlatForward(settlementDate, q_mod - r_mod, rTS.currentLink().dayCounter()));
            final Handle< Quote > constVolQuote = new Handle< Quote >(new SimpleQuote(
                    Math.sqrt(volTS.currentLink().blackVariance(div.date(), strike) / maturity)));
            final Handle< BlackVolTermStructure > volHandle = new Handle< BlackVolTermStructure >(
                    new BlackConstantVol(volRefDate, volTS.currentLink().calendar(), constVolQuote, volDc));

            processes.add(new GeneralizedBlackScholesProcess(divAmt, qmrTS, zeroRateTS, volHandle));
        }

        // v[i] = blackVariance(underlying_i.date, strike)
        final double[] v = new double[underlyings.size()];
        for ( int i = 0; i < underlyings.size(); i++ ) {
            v[i] = volTS.currentLink().blackVariance(underlyings.get(i).date(), strike);
        }

        // rho[i][j] = v[min(i,j)] / sqrt(v[i] * v[j]) (autocorrelation of accumulated Brownian variances)
        final Matrix rho = new Matrix(underlyings.size(), underlyings.size());
        for ( int i = 0; i < underlyings.size(); i++ ) {
            rho.set(i, i, 1.0);
            for ( int j = 0; j < i; j++ ) {
                final double rij;
                if ( v[j] > Constants.QL_EPSILON ) {
                    rij = v[j] / Math.sqrt(v[i] * v[j]);
                } else {
                    rij = Constants.QL_EPSILON;
                }
                rho.set(i, j, rij);
                rho.set(j, i, rij);
            }
        }

        // BasketOption with AverageBasketPayoff(Put, x0) — weights are all 1.
        final double[] weights = new double[underlyings.size()];
        for ( int i = 0; i < underlyings.size(); i++ ) {
            weights[i] = 1.0;
        }
        final BasketOption basketOption = new BasketOption(
                new AverageBasketPayoff(new PlainVanillaPayoff(Option.Type.Put, process_.x0()), weights),
                new EuropeanExercise(maturityDate));

        final PricingEngine choiEngine = new ChoiBasketEngine(processes, rho, 10.0, 2000L, false, true);
        basketOption.setPricingEngine(choiEngine);

        if ( payoff.optionType() == Option.Type.Call ) {
            r.value = basketOption.NPV() * qTS.currentLink().discount(maturityDate);
        } else {
            double divDiscounted = 0.0;
            for ( final Dividend div : dividends ) {
                divDiscounted += div.amount() * rTS.currentLink().discount(div.date())
                        / qTS.currentLink().discount(div.date());
            }
            final double fwd = (process_.x0() - divDiscounted) * qTS.currentLink().discount(maturityDate)
                    / rTS.currentLink().discount(maturityDate);

            r.value = basketOption.NPV() * qTS.currentLink().discount(maturityDate)
                    - (fwd - payoff.strike()) * rTS.currentLink().discount(maturityDate);
        }
    }
}
