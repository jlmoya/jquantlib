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
import org.jquantlib.instruments.BarrierOption;
import org.jquantlib.instruments.BarrierType;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.math.distributions.NormalDistribution;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.pricingengines.BlackDeltaCalculator;
import org.jquantlib.pricingengines.BlackFormula;
import org.jquantlib.pricingengines.barrier.AnalyticBarrierEngine;
import org.jquantlib.processes.BlackScholesMertonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.BlackConstantVol;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.calendars.NullCalendar;

/**
 * Vanna/Volga single-barrier option engine.
 * <p>
 * Java port of QuantLib v1.42.1
 * {@code ql/experimental/barrieroption/vannavolgabarrierengine.{hpp,cpp}}.
 *
 * <p>The Vanna/Volga approach derives a smile-corrected barrier-option price by
 * blending an analytic flat-vol Black price with vega/vanna/volga adjustments
 * computed from a smile defined by three FX quotes: ATM and 25-delta call/put
 * volatilities. A survival-probability ({@code lambda}) weighting is applied so
 * that the correction vanishes as the barrier is approached.
 *
 * <p>Supports the four single-barrier types
 * ({@link BarrierType#UpIn}, {@link BarrierType#UpOut},
 * {@link BarrierType#DownIn}, {@link BarrierType#DownOut}).
 *
 * @author JQuantLib migration
 */
public class VannaVolgaBarrierEngine extends BarrierOption.EngineImpl {

    private final Handle<DeltaVolQuote> atmVol_;
    private final Handle<DeltaVolQuote> vol25Put_;
    private final Handle<DeltaVolQuote> vol25Call_;
    private final double T_;
    private final Handle<? extends Quote> spotFX_;
    private final Handle<YieldTermStructure> domesticTS_;
    private final Handle<YieldTermStructure> foreignTS_;
    private final boolean adaptVanDelta_;
    private final double bsPriceWithSmile_;


    public VannaVolgaBarrierEngine(final Handle<DeltaVolQuote> atmVol,
                                   final Handle<DeltaVolQuote> vol25Put,
                                   final Handle<DeltaVolQuote> vol25Call,
                                   final Handle<? extends Quote> spotFX,
                                   final Handle<YieldTermStructure> domesticTS,
                                   final Handle<YieldTermStructure> foreignTS) {
        this(atmVol, vol25Put, vol25Call, spotFX, domesticTS, foreignTS, false, 0.0);
    }

    public VannaVolgaBarrierEngine(final Handle<DeltaVolQuote> atmVol,
                                   final Handle<DeltaVolQuote> vol25Put,
                                   final Handle<DeltaVolQuote> vol25Call,
                                   final Handle<? extends Quote> spotFX,
                                   final Handle<YieldTermStructure> domesticTS,
                                   final Handle<YieldTermStructure> foreignTS,
                                   final boolean adaptVanDelta,
                                   final double bsPriceWithSmile) {
        this.atmVol_ = atmVol;
        this.vol25Put_ = vol25Put;
        this.vol25Call_ = vol25Call;
        this.T_ = atmVol.currentLink().maturity();
        this.spotFX_ = spotFX;
        this.domesticTS_ = domesticTS;
        this.foreignTS_ = foreignTS;
        this.adaptVanDelta_ = adaptVanDelta;
        this.bsPriceWithSmile_ = bsPriceWithSmile;

        QL.require(vol25Put.currentLink().delta() == -0.25,
                "25 delta put is required by vanna volga method");
        QL.require(vol25Call.currentLink().delta() == 0.25,
                "25 delta call is required by vanna volga method");

        QL.require(vol25Put.currentLink().maturity() == vol25Call.currentLink().maturity() &&
                   vol25Put.currentLink().maturity() == atmVol.currentLink().maturity(),
                "Maturity of 3 vols are not the same");

        QL.require(!domesticTS.empty(), "domestic yield curve is not defined");
        QL.require(!foreignTS.empty(), "foreign yield curve is not defined");
    }


    @Override
    public void calculate() {
        final BarrierOption.ArgumentsImpl a = (BarrierOption.ArgumentsImpl) arguments_;
        final BarrierOption.ResultsImpl r = (BarrierOption.ResultsImpl) results_;

        QL.require(a.barrierType == BarrierType.UpIn || a.barrierType == BarrierType.UpOut
                || a.barrierType == BarrierType.DownIn || a.barrierType == BarrierType.DownOut,
                "Invalid barrier type");

        final double sigmaShift_vega = 0.0001;
        final double sigmaShift_volga = 0.0001;
        final double spotShift_delta = 0.0001 * spotFX_.currentLink().value();
        final double sigmaShift_vanna = 0.0001;

        final SimpleQuote x0Quote = new SimpleQuote(spotFX_.currentLink().value());
        final SimpleQuote atmVolQuote = new SimpleQuote(atmVol_.currentLink().value());

        final BlackVolTermStructure blackVolTS = new BlackConstantVol(
                new Settings().evaluationDate(),
                new NullCalendar(),
                new Handle<Quote>(atmVolQuote),
                new Actual365Fixed());
        final BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(
                new Handle<Quote>(x0Quote),
                foreignTS_,
                domesticTS_,
                new Handle<BlackVolTermStructure>(blackVolTS));

        final AnalyticBarrierEngine engineBS = new AnalyticBarrierEngine(stochProcess);

        final BlackDeltaCalculator blackDeltaCalculatorAtm = new BlackDeltaCalculator(
                Option.Type.Call, atmVol_.currentLink().deltaType(), x0Quote.value(),
                domesticTS_.currentLink().discount(T_), foreignTS_.currentLink().discount(T_),
                atmVol_.currentLink().value() * Math.sqrt(T_));
        final double atmStrike = blackDeltaCalculatorAtm.atmStrike(atmVol_.currentLink().atmType());

        final double call25Vol = vol25Call_.currentLink().value();
        final double put25Vol = vol25Put_.currentLink().value();

        final BlackDeltaCalculator blackDeltaCalculatorPut25 = new BlackDeltaCalculator(
                Option.Type.Put, vol25Put_.currentLink().deltaType(), x0Quote.value(),
                domesticTS_.currentLink().discount(T_), foreignTS_.currentLink().discount(T_),
                put25Vol * Math.sqrt(T_));
        final double put25Strike = blackDeltaCalculatorPut25.strikeFromDelta(-0.25);
        final BlackDeltaCalculator blackDeltaCalculatorCall25 = new BlackDeltaCalculator(
                Option.Type.Call, vol25Call_.currentLink().deltaType(), x0Quote.value(),
                domesticTS_.currentLink().discount(T_), foreignTS_.currentLink().discount(T_),
                call25Vol * Math.sqrt(T_));
        final double call25Strike = blackDeltaCalculatorCall25.strikeFromDelta(0.25);

        // Use vanna-volga interpolated smile to price the vanilla.
        final double[] strikes = new double[] { put25Strike, atmStrike, call25Strike };
        final double[] vols    = new double[] { put25Vol, atmVol_.currentLink().value(), call25Vol };
        final VannaVolgaInterpolation interpolation = new VannaVolgaInterpolation(
                strikes, vols, x0Quote.value(),
                domesticTS_.currentLink().discount(T_), foreignTS_.currentLink().discount(T_), T_);
        final StrikedTypePayoff payoff = (StrikedTypePayoff) a.payoff;
        final double strikeVol = interpolation.value(payoff.strike());

        // Vanilla option price under interpolated smile vol
        final double forward = x0Quote.value() * foreignTS_.currentLink().discount(T_)
                / domesticTS_.currentLink().discount(T_);
        final double vanillaOption = BlackFormula.blackFormula(payoff.optionType(), payoff.strike(),
                forward, strikeVol * Math.sqrt(T_), domesticTS_.currentLink().discount(T_));

        // Already-touched short-circuits
        if (x0Quote.value() >= a.barrier && a.barrierType == BarrierType.UpOut) {
            r.value = 0.0;
            return;
        }
        if (x0Quote.value() >= a.barrier && a.barrierType == BarrierType.UpIn) {
            r.value = adaptVanDelta_ ? bsPriceWithSmile_ : vanillaOption;
            return;
        }
        if (x0Quote.value() <= a.barrier && a.barrierType == BarrierType.DownOut) {
            r.value = 0.0;
            return;
        }
        if (x0Quote.value() <= a.barrier && a.barrierType == BarrierType.DownIn) {
            r.value = adaptVanDelta_ ? bsPriceWithSmile_ : vanillaOption;
            return;
        }

        // Set up BS barrier option pricing. Only the OUT direction is computed by BS;
        // IN is recovered via vanilla - OUT.
        final BarrierType barrierTypeBS;
        if (a.barrierType == BarrierType.UpOut)        barrierTypeBS = BarrierType.UpOut;
        else if (a.barrierType == BarrierType.UpIn)    barrierTypeBS = BarrierType.UpOut;
        else if (a.barrierType == BarrierType.DownOut) barrierTypeBS = BarrierType.DownOut;
        else                                           barrierTypeBS = BarrierType.DownOut;

        final BarrierOption barrierOption = new BarrierOption(barrierTypeBS, a.barrier, a.rebate,
                (StrikedTypePayoff) a.payoff, a.exercise);
        barrierOption.setPricingEngine(engineBS);

        // BS price with atm vol
        final double priceBS = barrierOption.NPV();
        final double price25CallBS = BlackFormula.blackFormula(Option.Type.Call, call25Strike,
                forward, atmVol_.currentLink().value() * Math.sqrt(T_),
                domesticTS_.currentLink().discount(T_));
        final double price25PutBS = BlackFormula.blackFormula(Option.Type.Put, put25Strike,
                forward, atmVol_.currentLink().value() * Math.sqrt(T_),
                domesticTS_.currentLink().discount(T_));

        // Market prices for the 25-delta strikes
        final double price25CallMkt = BlackFormula.blackFormula(Option.Type.Call, call25Strike,
                forward, call25Vol * Math.sqrt(T_),
                domesticTS_.currentLink().discount(T_));
        final double price25PutMkt = BlackFormula.blackFormula(Option.Type.Put, put25Strike,
                forward, put25Vol * Math.sqrt(T_),
                domesticTS_.currentLink().discount(T_));

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

        // BS vega via finite-difference
        atmVolQuote.setValue(atmVolQuote.value() + sigmaShift_vega);
        barrierOption.recalculate();
        final double vegaBarBS = (barrierOption.NPV() - priceBS) / sigmaShift_vega;
        atmVolQuote.setValue(atmVolQuote.value() - sigmaShift_vega);

        // BS volga via second-derivative of vega in sigma
        atmVolQuote.setValue(atmVolQuote.value() + sigmaShift_volga);
        barrierOption.recalculate();
        final double priceBS2 = barrierOption.NPV();

        atmVolQuote.setValue(atmVolQuote.value() + sigmaShift_vega);
        barrierOption.recalculate();
        final double vegaBarBS2 = (barrierOption.NPV() - priceBS2) / sigmaShift_vega;
        final double volgaBarBS = (vegaBarBS2 - vegaBarBS) / sigmaShift_volga;
        atmVolQuote.setValue(atmVolQuote.value() - sigmaShift_volga - sigmaShift_vega);

        // BS delta — central difference, base
        x0Quote.setValue(x0Quote.value() + spotShift_delta);
        barrierOption.recalculate();
        double priceBS_delta1 = barrierOption.NPV();

        x0Quote.setValue(x0Quote.value() - 2.0 * spotShift_delta);
        barrierOption.recalculate();
        double priceBS_delta2 = barrierOption.NPV();

        x0Quote.setValue(x0Quote.value() + spotShift_delta);
        final double deltaBar1 = (priceBS_delta1 - priceBS_delta2) / (2.0 * spotShift_delta);

        // Shifted delta — vanna via FD of delta in sigma
        atmVolQuote.setValue(atmVolQuote.value() + sigmaShift_vanna);
        x0Quote.setValue(x0Quote.value() + spotShift_delta);
        barrierOption.recalculate();
        priceBS_delta1 = barrierOption.NPV();

        x0Quote.setValue(x0Quote.value() - 2.0 * spotShift_delta);
        barrierOption.recalculate();
        priceBS_delta2 = barrierOption.NPV();

        x0Quote.setValue(x0Quote.value() + spotShift_delta);
        final double deltaBar2 = (priceBS_delta1 - priceBS_delta2) / (2.0 * spotShift_delta);

        final double vannaBarBS = (deltaBar2 - deltaBar1) / sigmaShift_vanna;

        atmVolQuote.setValue(atmVolQuote.value() - sigmaShift_vanna);

        // Solve A q = b for the three weights
        final Matrix A = new Matrix(3, 3);
        A.set(0, 0, vegaAtm);  A.set(0, 1, vega25Call);  A.set(0, 2, vega25Put);
        A.set(1, 0, vannaAtm); A.set(1, 1, vanna25Call); A.set(1, 2, vanna25Put);
        A.set(2, 0, volgaAtm); A.set(2, 1, volga25Call); A.set(2, 2, volga25Put);

        final Array b = new Array(3);
        b.set(0, vegaBarBS);
        b.set(1, vannaBarBS);
        b.set(2, volgaBarBS);

        final Array q = A.inverse().mul(b);

        // Touch probability — analytical no-touch
        final CumulativeNormalDistribution cnd = new CumulativeNormalDistribution();
        final double mu = domesticTS_.currentLink().zeroRate(T_, Compounding.Continuous, Frequency.NoFrequency, false).rate()
                - foreignTS_.currentLink().zeroRate(T_, Compounding.Continuous, Frequency.NoFrequency, false).rate()
                - 0.5 * atmV * atmV;
        final double h2 = (Math.log(a.barrier / x0Quote.value()) + mu * T_) / (atmV * sqrtT);
        final double h2Prime = (Math.log(x0Quote.value() / a.barrier) + mu * T_) / (atmV * sqrtT);
        final double probTouch;
        if (a.barrierType == BarrierType.UpIn || a.barrierType == BarrierType.UpOut) {
            probTouch = cnd.op(h2Prime)
                    + Math.pow(a.barrier / x0Quote.value(), 2.0 * mu / (atmV * atmV)) * cnd.op(-h2);
        } else {
            probTouch = cnd.op(-h2Prime)
                    + Math.pow(a.barrier / x0Quote.value(), 2.0 * mu / (atmV * atmV)) * cnd.op(h2);
        }
        final double p_survival = 1.0 - probTouch;
        final double lambda = p_survival;
        final double adjust = q.get(1) * (price25CallMkt - price25CallBS)
                            + q.get(2) * (price25PutMkt - price25PutBS);
        double outPrice = priceBS + lambda * adjust;
        final double inPrice;

        if (adaptVanDelta_) {
            outPrice += lambda * (bsPriceWithSmile_ - vanillaOption);
            outPrice = Math.max(0.0, Math.min(bsPriceWithSmile_, outPrice));
            inPrice = bsPriceWithSmile_ - outPrice;
        } else {
            outPrice = Math.max(0.0, Math.min(vanillaOption, outPrice));
            inPrice = vanillaOption - outPrice;
        }

        if (a.barrierType == BarrierType.DownOut || a.barrierType == BarrierType.UpOut) {
            r.value = outPrice;
        } else {
            r.value = inPrice;
        }
    }
}
