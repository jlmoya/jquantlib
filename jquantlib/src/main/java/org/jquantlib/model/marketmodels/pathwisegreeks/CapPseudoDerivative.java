/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k Track C C.8.

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
 Copyright (C) 2008 Mark Joshi
*/

package org.jquantlib.model.marketmodels.pathwisegreeks;

import org.jquantlib.QL;
import org.jquantlib.instruments.Option;
import org.jquantlib.math.Ops;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.solvers1D.Brent;
import org.jquantlib.model.marketmodels.MarketModel;
import org.jquantlib.model.marketmodels.curvestates.LMMCurveState;
import org.jquantlib.pricingengines.BlackFormula;

import java.util.ArrayList;
import java.util.List;

/**
 * Derivative of cap implied volatility with respect to changes in pseudo-root elements. The relationship between cap
 * implied vol and caplet implied vols is non-trivial — handled here via a Brent solve on {@link QuickCap}.
 *
 * <p>Mirrors C++ {@code CapPseudoDerivative}
 * (ql/models/marketmodels/pathwisegreeks/swaptionpseudojacobian.{hpp,cpp} v1.42.1).
 *
 * @author Jose Moya
 */
public class CapPseudoDerivative {

    private final List< Matrix > volatilityDerivatives_;
    private final List< Matrix > priceDerivatives_;
    private final double impliedVolatility_;
    private final double vega_;
    private final double firstDF_;

    public CapPseudoDerivative(final MarketModel inputModel, final double strike, final int startIndex,
            final int endIndex, final double firstDF) {
        this.firstDF_ = firstDF;
        QL.require(startIndex < endIndex, "for a cap pseudo derivative the start of the cap must be before the end");
        QL.require(endIndex <= inputModel.numberOfRates(),
                "for a cap pseudo derivative the end of the cap must before the end of the rates");

        final int numberCaplets = endIndex - startIndex;
        final int numberRates = inputModel.numberOfRates();
        final int factors = inputModel.numberOfFactors();
        final LMMCurveState curve = new LMMCurveState(inputModel.evolution().rateTimes());
        curve.setOnForwardRates(inputModel.initialRates());

        final Matrix totalCovariance = inputModel.totalCovariance(inputModel.numberOfSteps() - 1);

        final double[] displacedImpliedVols = new double[numberCaplets];
        final double[] annuities = new double[numberCaplets];
        final double[] initialRates = new double[numberCaplets];
        final double[] expiries = new double[numberCaplets];

        double capPrice = 0.0;
        double guess = 0.0;
        double minVol = 1e10;
        double maxVol = 0.0;

        for ( int j = startIndex; j < endIndex; ++j ) {
            final int capletIndex = j - startIndex;
            final double resetTime = inputModel.evolution().rateTimes()[j];
            expiries[capletIndex] = resetTime;

            final double sd = Math.sqrt(totalCovariance.get(j, j));
            displacedImpliedVols[capletIndex] = Math.sqrt(totalCovariance.get(j, j) / resetTime);

            final double forward = inputModel.initialRates()[j];
            initialRates[capletIndex] = forward;

            final double annuity = curve.discountRatio(j + 1, 0) * inputModel.evolution().rateTaus()[j] * firstDF_;
            annuities[capletIndex] = annuity;

            final double displacement = inputModel.displacements()[j];

            guess += displacedImpliedVols[capletIndex] * (forward + displacement) / forward;
            minVol = Math.min(minVol, displacedImpliedVols[capletIndex]);
            maxVol = Math.max(maxVol, displacedImpliedVols[capletIndex] * (forward + displacement) / forward);

            final double capletPrice = BlackFormula.blackFormula(Option.Type.Call, strike, forward, sd, annuity,
                    displacement);

            capPrice += capletPrice;
        }

        guess /= numberCaplets;

        this.priceDerivatives_ = new ArrayList<>(inputModel.evolution().numberOfSteps());

        for ( int step = 0; step < inputModel.evolution().numberOfSteps(); ++step ) {
            final Matrix thisDerivative = new Matrix(numberRates, factors);

            for ( int rate = Math.max(inputModel.evolution().firstAliveRate()[step], startIndex);
                    rate < endIndex; ++rate ) {
                for ( int f = 0; f < factors; ++f ) {
                    final double expiry = inputModel.evolution().rateTimes()[rate];
                    final double volDerivative =
                            inputModel.pseudoRoot(step).get(rate, f) / (displacedImpliedVols[rate - startIndex]
                                    * expiry);
                    final double capletVega = blackFormulaVolDerivative(strike, inputModel.initialRates()[rate],
                            displacedImpliedVols[rate - startIndex] * Math.sqrt(expiry), expiry,
                            annuities[rate - startIndex], inputModel.displacements()[rate]);

                    thisDerivative.set(rate, f, volDerivative * capletVega);
                }
            }
            priceDerivatives_.add(thisDerivative);
        }

        final QuickCap capPricer = new QuickCap(strike, annuities, initialRates, expiries, capPrice);

        final Brent solver = new Brent();
        solver.setMaxEvaluations(1000);
        this.impliedVolatility_ = solver.solve(capPricer, 1e-6, guess, minVol * 0.99, maxVol * 1.01);
        this.vega_ = capPricer.vega(impliedVolatility_);

        this.volatilityDerivatives_ = new ArrayList<>(inputModel.evolution().numberOfSteps());

        for ( int step = 0; step < inputModel.evolution().numberOfSteps(); ++step ) {
            final Matrix thisDerivative = new Matrix(numberRates, factors);
            for ( int rate = Math.max(inputModel.evolution().firstAliveRate()[step], startIndex);
                    rate < endIndex; ++rate ) {
                for ( int f = 0; f < factors; ++f ) {
                    thisDerivative.set(rate, f, priceDerivatives_.get(step).get(rate, f) / vega_);
                }
            }
            volatilityDerivatives_.add(thisDerivative);
        }
    }

    /**
     * Black-formula vol derivative (vega): {@code blackFormulaStdDevDerivative * sqrt(expiry)}. Mirrors the C++ free
     * function {@code blackFormulaVolDerivative} from {@code blackformula.hpp}.
     */
    private static double blackFormulaVolDerivative(final double strike, final double forward, final double stdDev,
            final double expiry, final double discount, final double displacement) {
        return BlackFormula.blackFormulaStdDevDerivative(strike, forward, stdDev, discount, displacement) * Math.sqrt(
                expiry);
    }

    public Matrix priceDerivative(final int i) {
        return priceDerivatives_.get(i);
    }

    public Matrix volatilityDerivative(final int i) {
        return volatilityDerivatives_.get(i);
    }

    public double impliedVolatility() {
        return impliedVolatility_;
    }

    /**
     * Quick (allocation-free) cap pricer used as the function for the implied-vol Brent solve. Mirrors the
     * anonymous-namespace C++ class.
     */
    private static final class QuickCap implements Ops.DoubleOp {
        private final double strike_;
        private final double[] annuities_;
        private final double[] currentRates_;
        private final double[] expiries_;
        private final double price_;

        QuickCap(final double strike, final double[] annuities, final double[] currentRates, final double[] expiries,
                final double price) {
            this.strike_ = strike;
            this.annuities_ = annuities;
            this.currentRates_ = currentRates;
            this.expiries_ = expiries;
            this.price_ = price;
        }

        @Override
        public double op(final double volatility) {
            double price = 0.0;
            for ( int i = 0; i < annuities_.length; ++i ) {
                price += BlackFormula.blackFormula(Option.Type.Call, strike_, currentRates_[i],
                        volatility * Math.sqrt(expiries_[i]), annuities_[i]);
            }
            return price - price_;
        }

        double vega(final double volatility) {
            double v = 0.0;
            for ( int i = 0; i < annuities_.length; ++i ) {
                v += BlackFormula.blackFormulaStdDevDerivative(strike_, currentRates_[i],
                        volatility * Math.sqrt(expiries_[i]), annuities_[i], 0.0) * Math.sqrt(expiries_[i]);
            }
            return v;
        }
    }
}
