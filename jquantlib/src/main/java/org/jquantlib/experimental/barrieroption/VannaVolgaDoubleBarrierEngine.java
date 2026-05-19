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
import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.experimental.fx.DeltaVolQuote;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.math.distributions.NormalDistribution;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.pricingengines.BlackDeltaCalculator;
import org.jquantlib.pricingengines.BlackFormula;
import org.jquantlib.processes.BlackScholesMertonProcess;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.BlackConstantVol;
import org.jquantlib.time.calendars.NullCalendar;

/**
 * Vanna/Volga double-barrier option engine.
 * <p>
 * Java port of QuantLib v1.42.1 {@code ql/experimental/barrieroption/vannavolgadoublebarrierengine.hpp} (header-only
 * template).
 *
 * <p>The C++ class is templated on the inner Black-Scholes double-barrier
 * engine; in this Java port the inner engine is supplied via the {@link DoubleBarrierEngineFactory} functional
 * interface, allowing the same Vanna/Volga wrapper to be combined with either {@link SuoWangDoubleBarrierEngine} or
 * {@link org.jquantlib.pricingengines.barrier.AnalyticDoubleBarrierEngine}.
 *
 * <p>Supports {@link DoubleBarrierType#KnockIn} and
 * {@link DoubleBarrierType#KnockOut} only (matching the C++ requirement).
 *
 * @author JQuantLib migration
 */
public class VannaVolgaDoubleBarrierEngine extends DoubleBarrierOption.EngineImpl {

    private final Handle< DeltaVolQuote > atmVol_;
    private final Handle< DeltaVolQuote > vol25Put_;
    private final Handle< DeltaVolQuote > vol25Call_;
    private final double T_;
    private final Handle< ? extends Quote > spotFX_;
    private final Handle< YieldTermStructure > domesticTS_;
    private final Handle< YieldTermStructure > foreignTS_;
    private final boolean adaptVanDelta_;
    private final double bsPriceWithSmile_;
    private final int series_;
    private final DoubleBarrierEngineFactory engineFactory_;
    public VannaVolgaDoubleBarrierEngine(final Handle< DeltaVolQuote > atmVol, final Handle< DeltaVolQuote > vol25Put,
            final Handle< DeltaVolQuote > vol25Call, final Handle< ? extends Quote > spotFX,
            final Handle< YieldTermStructure > domesticTS, final Handle< YieldTermStructure > foreignTS,
            final DoubleBarrierEngineFactory engineFactory) {
        this(atmVol, vol25Put, vol25Call, spotFX, domesticTS, foreignTS, false, 0.0, 5, engineFactory);
    }

    public VannaVolgaDoubleBarrierEngine(final Handle< DeltaVolQuote > atmVol, final Handle< DeltaVolQuote > vol25Put,
            final Handle< DeltaVolQuote > vol25Call, final Handle< ? extends Quote > spotFX,
            final Handle< YieldTermStructure > domesticTS, final Handle< YieldTermStructure > foreignTS,
            final boolean adaptVanDelta, final double bsPriceWithSmile,
            final DoubleBarrierEngineFactory engineFactory) {
        this(atmVol, vol25Put, vol25Call, spotFX, domesticTS, foreignTS, adaptVanDelta, bsPriceWithSmile, 5,
                engineFactory);
    }

    public VannaVolgaDoubleBarrierEngine(final Handle< DeltaVolQuote > atmVol, final Handle< DeltaVolQuote > vol25Put,
            final Handle< DeltaVolQuote > vol25Call, final Handle< ? extends Quote > spotFX,
            final Handle< YieldTermStructure > domesticTS, final Handle< YieldTermStructure > foreignTS,
            final boolean adaptVanDelta, final double bsPriceWithSmile, final int series,
            final DoubleBarrierEngineFactory engineFactory) {
        this.atmVol_ = atmVol;
        this.vol25Put_ = vol25Put;
        this.vol25Call_ = vol25Call;
        this.T_ = atmVol.currentLink().maturity();
        this.spotFX_ = spotFX;
        this.domesticTS_ = domesticTS;
        this.foreignTS_ = foreignTS;
        this.adaptVanDelta_ = adaptVanDelta;
        this.bsPriceWithSmile_ = bsPriceWithSmile;
        this.series_ = series;
        this.engineFactory_ = engineFactory;

        QL.require(vol25Put.currentLink().delta() == -0.25, "25 delta put is required by vanna volga method");
        QL.require(vol25Call.currentLink().delta() == 0.25, "25 delta call is required by vanna volga method");

        QL.require(vol25Put.currentLink().maturity() == vol25Call.currentLink().maturity()
                        && vol25Put.currentLink().maturity() == atmVol.currentLink().maturity(),
                "Maturity of 3 vols are not the same");

        QL.require(!domesticTS.empty(), "domestic yield curve is not defined");
        QL.require(!foreignTS.empty(), "foreign yield curve is not defined");
    }

    @Override
    public void calculate() {
        final DoubleBarrierOption.ArgumentsImpl a = args();
        final OneAssetOption.ResultsImpl r = (OneAssetOption.ResultsImpl) results_;

        final double sigmaShift_vega = 0.001;
        final double sigmaShift_volga = 0.0001;
        final double spotShift_delta = 0.0001 * spotFX_.currentLink().value();
        final double sigmaShift_vanna = 0.0001;

        QL.require(a.barrierType == DoubleBarrierType.KnockIn || a.barrierType == DoubleBarrierType.KnockOut,
                "Only same type barrier supported");

        final SimpleQuote x0Quote = new SimpleQuote(spotFX_.currentLink().value());
        final SimpleQuote atmVolQuote = new SimpleQuote(atmVol_.currentLink().value());

        final BlackVolTermStructure blackVolTS = new BlackConstantVol(new Settings().evaluationDate(),
                new NullCalendar(), new Handle< Quote >(atmVolQuote), new Actual365Fixed());
        final BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(new Handle< Quote >(x0Quote),
                foreignTS_, domesticTS_, new Handle< BlackVolTermStructure >(blackVolTS));

        final DoubleBarrierOption.EngineImpl engineBS = engineFactory_.create(stochProcess, series_);

        final BlackDeltaCalculator blackDeltaCalculatorAtm = new BlackDeltaCalculator(Option.Type.Call,
                atmVol_.currentLink().deltaType(), x0Quote.value(), domesticTS_.currentLink().discount(T_),
                foreignTS_.currentLink().discount(T_), atmVol_.currentLink().value() * Math.sqrt(T_));
        final double atmStrike = blackDeltaCalculatorAtm.atmStrike(atmVol_.currentLink().atmType());

        final double call25Vol = vol25Call_.currentLink().value();
        final double put25Vol = vol25Put_.currentLink().value();

        final BlackDeltaCalculator blackDeltaCalculatorPut25 = new BlackDeltaCalculator(Option.Type.Put,
                vol25Put_.currentLink().deltaType(), x0Quote.value(), domesticTS_.currentLink().discount(T_),
                foreignTS_.currentLink().discount(T_), put25Vol * Math.sqrt(T_));
        final double put25Strike = blackDeltaCalculatorPut25.strikeFromDelta(-0.25);
        final BlackDeltaCalculator blackDeltaCalculatorCall25 = new BlackDeltaCalculator(Option.Type.Call,
                vol25Call_.currentLink().deltaType(), x0Quote.value(), domesticTS_.currentLink().discount(T_),
                foreignTS_.currentLink().discount(T_), call25Vol * Math.sqrt(T_));
        final double call25Strike = blackDeltaCalculatorCall25.strikeFromDelta(0.25);

        // NOTE: C++ uses foreignTS_->discount(T_) twice here (looks like a bug, but we
        // mirror v1.42.1 faithfully so the reference values match).
        final double[] strikes = new double[] { put25Strike, atmStrike, call25Strike };
        final double[] vols = new double[] { put25Vol, atmVol_.currentLink().value(), call25Vol };
        final VannaVolgaInterpolation interpolation = new VannaVolgaInterpolation(strikes, vols, x0Quote.value(),
                foreignTS_.currentLink().discount(T_), foreignTS_.currentLink().discount(T_), T_);
        final StrikedTypePayoff payoff = (StrikedTypePayoff) a.payoff;
        final double strikeVol = interpolation.value(payoff.strike());

        final double forward =
                x0Quote.value() * foreignTS_.currentLink().discount(T_) / domesticTS_.currentLink().discount(T_);
        final double vanillaOption = BlackFormula.blackFormula(payoff.optionType(), payoff.strike(), forward,
                strikeVol * Math.sqrt(T_), domesticTS_.currentLink().discount(T_));

        // Already-touched short-circuits
        final boolean outside = x0Quote.value() > a.barrier_hi || x0Quote.value() < a.barrier_lo;
        if ( outside && a.barrierType == DoubleBarrierType.KnockOut ) {
            r.value = 0.0;
            return;
        }
        if ( outside && a.barrierType == DoubleBarrierType.KnockIn ) {
            r.value = adaptVanDelta_ ? bsPriceWithSmile_ : vanillaOption;
            return;
        }

        // Compute the BS knock-out price; KI = vanilla - KO.
        final DoubleBarrierOption doubleBarrierOption = new DoubleBarrierOption(DoubleBarrierType.KnockOut,
                a.barrier_lo, a.barrier_hi, a.rebate, (StrikedTypePayoff) a.payoff, a.exercise);
        doubleBarrierOption.setPricingEngine(engineBS);

        final double priceBS = doubleBarrierOption.NPV();

        final double priceAtmCallBS = BlackFormula.blackFormula(Option.Type.Call, atmStrike, forward,
                atmVol_.currentLink().value() * Math.sqrt(T_), domesticTS_.currentLink().discount(T_));
        final double price25CallBS = BlackFormula.blackFormula(Option.Type.Call, call25Strike, forward,
                atmVol_.currentLink().value() * Math.sqrt(T_), domesticTS_.currentLink().discount(T_));
        final double price25PutBS = BlackFormula.blackFormula(Option.Type.Put, put25Strike, forward,
                atmVol_.currentLink().value() * Math.sqrt(T_), domesticTS_.currentLink().discount(T_));

        // ATM call market price re-uses atmVol_, so priceAtmCallMkt == priceAtmCallBS
        // (mirrors v1.42.1).
        final double priceAtmCallMkt = BlackFormula.blackFormula(Option.Type.Call, atmStrike, forward,
                atmVol_.currentLink().value() * Math.sqrt(T_), domesticTS_.currentLink().discount(T_));
        final double price25CallMkt = BlackFormula.blackFormula(Option.Type.Call, call25Strike, forward,
                call25Vol * Math.sqrt(T_), domesticTS_.currentLink().discount(T_));
        final double price25PutMkt = BlackFormula.blackFormula(Option.Type.Put, put25Strike, forward,
                put25Vol * Math.sqrt(T_), domesticTS_.currentLink().discount(T_));

        // Analytical vega / vanna / volga of vanillas at atm vol
        final NormalDistribution norm = new NormalDistribution();
        final double sqrtT = Math.sqrt(T_);
        final double atmV = atmVolQuote.value();

        final double d1atm = (Math.log(forward / atmStrike) + 0.5 * atmV * atmV * T_) / (atmV * sqrtT);
        final double vegaAtm = x0Quote.value() * norm.op(d1atm) * sqrtT * foreignTS_.currentLink().discount(T_);
        final double vannaAtm = vegaAtm / x0Quote.value() * (1.0 - d1atm / (atmV * sqrtT));
        final double volgaAtm = vegaAtm * d1atm * (d1atm - atmV * sqrtT) / atmV;

        final double d125call = (Math.log(forward / call25Strike) + 0.5 * atmV * atmV * T_) / (atmV * sqrtT);
        final double vega25Call = x0Quote.value() * norm.op(d125call) * sqrtT * foreignTS_.currentLink().discount(T_);
        final double vanna25Call = vega25Call / x0Quote.value() * (1.0 - d125call / (atmV * sqrtT));
        final double volga25Call = vega25Call * d125call * (d125call - atmV * sqrtT) / atmV;

        final double d125Put = (Math.log(forward / put25Strike) + 0.5 * atmV * atmV * T_) / (atmV * sqrtT);
        final double vega25Put = x0Quote.value() * norm.op(d125Put) * sqrtT * foreignTS_.currentLink().discount(T_);
        final double vanna25Put = vega25Put / x0Quote.value() * (1.0 - d125Put / (atmV * sqrtT));
        final double volga25Put = vega25Put * d125Put * (d125Put - atmV * sqrtT) / atmV;

        // BS vega
        atmVolQuote.setValue(atmVolQuote.value() + sigmaShift_vega);
        doubleBarrierOption.recalculate();
        final double vegaBarBS = (doubleBarrierOption.NPV() - priceBS) / sigmaShift_vega;
        atmVolQuote.setValue(atmVolQuote.value() - sigmaShift_vega);

        // BS volga
        atmVolQuote.setValue(atmVolQuote.value() + sigmaShift_volga);
        doubleBarrierOption.recalculate();
        final double priceBS2 = doubleBarrierOption.NPV();

        atmVolQuote.setValue(atmVolQuote.value() + sigmaShift_vega);
        doubleBarrierOption.recalculate();
        final double vegaBarBS2 = (doubleBarrierOption.NPV() - priceBS2) / sigmaShift_vega;
        final double volgaBarBS = (vegaBarBS2 - vegaBarBS) / sigmaShift_volga;
        atmVolQuote.setValue(atmVolQuote.value() - sigmaShift_volga - sigmaShift_vega);

        // BS delta — base
        x0Quote.setValue(x0Quote.value() + spotShift_delta);
        doubleBarrierOption.recalculate();
        double priceBS_delta1 = doubleBarrierOption.NPV();

        x0Quote.setValue(x0Quote.value() - 2.0 * spotShift_delta);
        doubleBarrierOption.recalculate();
        double priceBS_delta2 = doubleBarrierOption.NPV();

        x0Quote.setValue(x0Quote.value() + spotShift_delta);
        final double deltaBar1 = (priceBS_delta1 - priceBS_delta2) / (2.0 * spotShift_delta);

        // Vanna — shifted delta
        atmVolQuote.setValue(atmVolQuote.value() + sigmaShift_vanna);
        x0Quote.setValue(x0Quote.value() + spotShift_delta);
        doubleBarrierOption.recalculate();
        priceBS_delta1 = doubleBarrierOption.NPV();

        x0Quote.setValue(x0Quote.value() - 2.0 * spotShift_delta);
        doubleBarrierOption.recalculate();
        priceBS_delta2 = doubleBarrierOption.NPV();

        x0Quote.setValue(x0Quote.value() + spotShift_delta);
        final double deltaBar2 = (priceBS_delta1 - priceBS_delta2) / (2.0 * spotShift_delta);

        final double vannaBarBS = (deltaBar2 - deltaBar1) / sigmaShift_vanna;

        atmVolQuote.setValue(atmVolQuote.value() - sigmaShift_vanna);

        // Solve A q = b
        final Matrix A = new Matrix(3, 3);
        A.set(0, 0, vegaAtm);
        A.set(0, 1, vega25Call);
        A.set(0, 2, vega25Put);
        A.set(1, 0, vannaAtm);
        A.set(1, 1, vanna25Call);
        A.set(1, 2, vanna25Put);
        A.set(2, 0, volgaAtm);
        A.set(2, 1, volga25Call);
        A.set(2, 2, volga25Put);

        final Array b = new Array(3);
        b.set(0, vegaBarBS);
        b.set(1, vannaBarBS);
        b.set(2, volgaBarBS);

        final Array q = A.inverse().mul(b);

        // Double-no-touch survival probability via Ikeda/Kunitomo series.
        final double H = a.barrier_hi;
        final double L = a.barrier_lo;
        final double thetaTiltMinus = ((domesticTS_.currentLink()
                .zeroRate(T_, org.jquantlib.termstructures.Compounding.Continuous,
                        org.jquantlib.time.Frequency.NoFrequency, false).rate() - foreignTS_.currentLink()
                .zeroRate(T_, org.jquantlib.termstructures.Compounding.Continuous,
                        org.jquantlib.time.Frequency.NoFrequency, false).rate()) / atmVol_.currentLink().value()
                - atmVol_.currentLink().value() / 2.0) * sqrtT;
        final double h = (1.0 / atmVol_.currentLink().value()) * Math.log(H / x0Quote.value()) / sqrtT;
        final double l = (1.0 / atmVol_.currentLink().value()) * Math.log(L / x0Quote.value()) / sqrtT;
        final CumulativeNormalDistribution cnd = new CumulativeNormalDistribution();

        double doubleNoTouch = 0.0;
        for ( int j = -series_; j < series_; j++ ) {
            final double e_minus = 2.0 * j * (h - l) - thetaTiltMinus;
            doubleNoTouch += Math.exp(-2.0 * j * thetaTiltMinus * (h - l)) * (cnd.op(h + e_minus) - cnd.op(l + e_minus))
                    - Math.exp(-2.0 * j * thetaTiltMinus * (h - l) + 2.0 * thetaTiltMinus * h) * (
                    cnd.op(h - 2.0 * h + e_minus) - cnd.op(l - 2.0 * h + e_minus));
        }

        final double lambda = doubleNoTouch;
        final double adjust =
                q.get(0) * (priceAtmCallMkt - priceAtmCallBS) + q.get(1) * (price25CallMkt - price25CallBS)
                        + q.get(2) * (price25PutMkt - price25PutBS);
        double outPrice = priceBS + lambda * adjust;
        final double inPrice;

        if ( adaptVanDelta_ ) {
            outPrice += lambda * (bsPriceWithSmile_ - vanillaOption);
            outPrice = Math.max(0.0, Math.min(bsPriceWithSmile_, outPrice));
            inPrice = bsPriceWithSmile_ - outPrice;
        } else {
            outPrice = Math.max(0.0, Math.min(vanillaOption, outPrice));
            inPrice = vanillaOption - outPrice;
        }

        if ( a.barrierType == DoubleBarrierType.KnockOut ) {
            r.value = outPrice;
        } else {
            r.value = inPrice;
        }
    }

    /**
     * Factory for the inner Black-Scholes double-barrier engine, mirroring the C++ template parameter. The factory
     * receives the temporary Black-Scholes-Merton process (built with the shifted ATM-vol quote) and the
     * series-truncation count.
     */
    public interface DoubleBarrierEngineFactory {
        DoubleBarrierOption.EngineImpl create(GeneralizedBlackScholesProcess process, int series);
    }
}
