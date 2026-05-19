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
 Copyright (C) 2009 Dimitri Reiswich

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <http://quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
*/

package org.jquantlib.pricingengines.vanilla;

import org.jquantlib.QL;
import org.jquantlib.instruments.CompoundOption;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.math.Ops;
import org.jquantlib.math.distributions.BivariateNormalDistribution;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.math.distributions.NormalDistribution;
import org.jquantlib.math.solvers1D.Brent;
import org.jquantlib.pricingengines.BlackFormula;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;

/**
 * Pricing engine for compound options using analytical formulae.
 * <p>
 * The formulas are taken from "Foreign Exchange Risk", Uwe Wystup, Risk 2002, where closed-form Greeks are also
 * available (Value: p.84, Greeks: pp.94-95).
 * <p>
 * Mirrors C++ QuantLib v1.42.1 {@code AnalyticCompoundOptionEngine} in
 * {@code ql/pricingengines/exotic/analyticcompoundoptionengine.{hpp,cpp}}.
 *
 * @author Jose Moya
 */
public class AnalyticCompoundOptionEngine extends OneAssetOption.EngineImpl {

    private final GeneralizedBlackScholesProcess process;
    private final CompoundOption.ArgumentsImpl a;
    private final OneAssetOption.ResultsImpl r;
    private final Option.GreeksImpl greeks;

    private final CumulativeNormalDistribution N = new CumulativeNormalDistribution();
    private final NormalDistribution n = new NormalDistribution();

    public AnalyticCompoundOptionEngine(final GeneralizedBlackScholesProcess process) {
        super(new CompoundOption.ArgumentsImpl(), new OneAssetOption.ResultsImpl());
        this.a = (CompoundOption.ArgumentsImpl) arguments_;
        this.r = (OneAssetOption.ResultsImpl) results_;
        this.greeks = r.greeks();
        this.process = process;
        this.process.addObserver(this);
    }

    @Override
    public void calculate() {
        QL.require(strikeDaughter() > 0.0, "Daughter strike must be positive");
        QL.require(strikeMother() > 0.0, "Mother strike must be positive");
        QL.require(spot() > 0.0, "negative or null underlying given");

        // Solver setup ***************************************************
        final Date helpDate = process.riskFreeRate().currentLink().referenceDate();
        final Date helpMaturity = helpDate.add((int) maturityDaughter().sub(maturityMother()));
        double vol = process.blackVolatility().currentLink().blackVol(helpMaturity, strikeDaughter());

        final double helpTimeToMat = process.time(helpMaturity);
        vol = vol * Math.sqrt(helpTimeToMat);

        final double dividendDiscount = process.dividendYield().currentLink().discount(helpMaturity);
        final double riskFreeDiscount = process.riskFreeRate().currentLink().discount(helpMaturity);

        final ImpliedSpotHelper f = new ImpliedSpotHelper(dividendDiscount, riskFreeDiscount, vol, payoffDaughter(),
                strikeMother());

        final Brent solver = new Brent();
        solver.setMaxEvaluations(1000);
        final double accuracy = 1.0e-6;

        final double sSolved = solver.solve(f, accuracy, strikeDaughter(), 1.0e-6, strikeDaughter() * 1000.0);
        final double X = transformX(sSolved);
        // Solver setup finished *****************************************

        final double phi = typeDaughter(); // -1 or 1
        final double w = typeMother();     // -1 or 1

        final double rho = Math.sqrt(residualTimeMother() / residualTimeDaughter());
        final BivariateNormalDistribution N2 = new BivariateNormalDistribution(w * rho);

        final double ddD = dividendDiscountDaughter();
        final double rdD = riskFreeDiscountDaughter();
        final double rdM = riskFreeDiscountMother();

        final double XmSM = X - stdDeviationMother();
        final double S = spot();
        final double dP = dPlus();
        final double dPT12 = dPlusTau12(sSolved);
        final double vD = volatilityDaughter();

        final double dM = dMinus();
        final double strD = strikeDaughter();
        final double strM = strikeMother();
        final double rTM = residualTimeMother();
        final double rTD = residualTimeDaughter();

        final double rD = riskFreeRateDaughter();
        final double dD = dividendRateDaughter();

        final double N2XmSM = N2.op(-phi * w * XmSM, phi * dP);
        final double N2X = N2.op(-phi * w * X, phi * dM);
        final double NeX = N.op(-phi * w * e(X));
        final double NX = N.op(-phi * w * X);
        final double NT12 = N.op(phi * dPT12);
        final double ndP = n.op(dP);
        final double nXm = n.op(XmSM);
        final double invMTime = 1.0 / Math.sqrt(rTM);
        final double invDTime = 1.0 / Math.sqrt(rTD);

        final double tempRes = phi * w * S * ddD * N2XmSM - phi * w * strD * rdD * N2X - w * strM * rdM * NX;
        final double tempDelta = phi * w * ddD * N2XmSM;
        final double tempGamma = (ddD / (vD * S)) * (invMTime * nXm * NT12 + w * invDTime * ndP * NeX);
        final double tempVega = ddD * S * ((1.0 / invMTime) * nXm * NT12 + w * (1.0 / invDTime) * ndP * NeX);
        double tempTheta = phi * w * dD * S * ddD * N2XmSM - phi * w * rD * strD * rdD * N2X - w * rD * strM * rdM * NX;
        tempTheta -= 0.5 * vD * S * ddD * (invMTime * nXm * NT12 + w * invDTime * ndP * NeX);

        r.value = tempRes;
        greeks.delta = tempDelta;
        greeks.gamma = tempGamma;
        greeks.vega = tempVega;
        greeks.theta = tempTheta;
    }

    // ---- helper methods (mirroring C++ private methods) ----

    private double typeDaughter() {
        return payoffDaughter().optionType().toInteger();
    }

    private double typeMother() {
        return payoffMother().optionType().toInteger();
    }

    private Date maturityDaughter() {
        return a.daughterExercise.lastDate();
    }

    private Date maturityMother() {
        return a.exercise.lastDate();
    }

    private double residualTimeDaughter() {
        return process.time(maturityDaughter());
    }

    private double residualTimeMother() {
        return process.time(maturityMother());
    }

    private double residualTimeMotherDaughter() {
        return residualTimeDaughter() - residualTimeMother();
    }

    private double volatilityDaughter() {
        return process.blackVolatility().currentLink().blackVol(maturityDaughter(), strikeDaughter());
    }

    private double volatilityMother() {
        return process.blackVolatility().currentLink().blackVol(maturityMother(), strikeMother());
    }

    private double stdDeviationDaughter() {
        return volatilityDaughter() * Math.sqrt(residualTimeDaughter());
    }

    private double stdDeviationMother() {
        return volatilityMother() * Math.sqrt(residualTimeMother());
    }

    private PlainVanillaPayoff payoffDaughter() {
        QL.require(a.daughterPayoff instanceof PlainVanillaPayoff, "non-plain payoff given");
        return (PlainVanillaPayoff) a.daughterPayoff;
    }

    private PlainVanillaPayoff payoffMother() {
        QL.require(a.payoff instanceof PlainVanillaPayoff, "non-plain payoff given");
        return (PlainVanillaPayoff) a.payoff;
    }

    private double strikeMother() {
        return payoffMother().strike();
    }

    private double strikeDaughter() {
        return payoffDaughter().strike();
    }

    private double riskFreeDiscountDaughter() {
        return process.riskFreeRate().currentLink().discount(residualTimeDaughter());
    }

    private double riskFreeDiscountMother() {
        return process.riskFreeRate().currentLink().discount(residualTimeMother());
    }

    private double riskFreeDiscountMotherDaughter() {
        return process.riskFreeRate().currentLink().discount(residualTimeMotherDaughter());
    }

    private double dividendDiscountDaughter() {
        return process.dividendYield().currentLink().discount(residualTimeDaughter());
    }

    private double dividendDiscountMother() {
        return process.dividendYield().currentLink().discount(residualTimeMother());
    }

    private double dividendDiscountMotherDaughter() {
        return process.dividendYield().currentLink().discount(residualTimeMotherDaughter());
    }

    private double dPlus() {
        final double forward = spot() * dividendDiscountDaughter() / riskFreeDiscountDaughter();
        final double sd = stdDeviationDaughter();
        return Math.log(forward / strikeDaughter()) / sd + 0.5 * sd;
    }

    private double dMinus() {
        return dPlus() - stdDeviationDaughter();
    }

    private double dPlusTau12(final double S) {
        final double forward = S * dividendDiscountMotherDaughter() / riskFreeDiscountMotherDaughter();
        final double sd = volatilityDaughter() * Math.sqrt(residualTimeMotherDaughter());
        return Math.log(forward / strikeDaughter()) / sd + 0.5 * sd;
    }

    private double spot() {
        return process.x0();
    }

    private double riskFreeRateDaughter() {
        return process.riskFreeRate().currentLink()
                .zeroRate(residualTimeDaughter(), Compounding.Continuous, Frequency.NoFrequency, false).rate();
    }

    private double dividendRateDaughter() {
        return process.dividendYield().currentLink()
                .zeroRate(residualTimeDaughter(), Compounding.Continuous, Frequency.NoFrequency, false).rate();
    }

    private double transformX(final double X) {
        final double sd = stdDeviationMother();
        double resX = riskFreeDiscountMother() * X / (spot() * dividendDiscountMother());
        resX = resX * Math.exp(0.5 * sd * sd);
        resX = Math.log(resX);
        return resX / sd;
    }

    private double e(final double X) {
        final double rtM = residualTimeMother();
        final double rtD = residualTimeDaughter();
        return (X * Math.sqrt(rtD) + Math.sqrt(rtM) * dMinus()) / Math.sqrt(rtD - rtM);
    }

    /**
     * Helper class needed to solve an implicit problem of finding a spot to a corresponding option price.
     */
    private static class ImpliedSpotHelper implements Ops.DoubleOp {
        private final double dividendDiscount;
        private final double riskFreeDiscount;
        private final double standardDeviation;
        private final double strike;
        private final PlainVanillaPayoff payoff;

        ImpliedSpotHelper(final double dividendDiscount, final double riskFreeDiscount, final double standardDeviation,
                final PlainVanillaPayoff payoff, final double strike) {
            this.dividendDiscount = dividendDiscount;
            this.riskFreeDiscount = riskFreeDiscount;
            this.standardDeviation = standardDeviation;
            this.strike = strike;
            this.payoff = payoff;
        }

        @Override
        public double op(final double spot) {
            final double forwardPrice = spot * dividendDiscount / riskFreeDiscount;
            // Note: Java BlackFormula.blackFormula(payoff, strike, forward, stddev, discount)
            // ignores the strike parameter and uses payoff.strike() (line 194 of BlackFormula).
            // We pass through the type+strike+forward+stddev+discount overload for clarity.
            final double value = BlackFormula.blackFormula(payoff.optionType(), payoff.strike(), forwardPrice,
                    standardDeviation, riskFreeDiscount);
            return value - strike;
        }
    }
}
