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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.marketmodels.MarketModel;
import org.jquantlib.model.marketmodels.SwapForwardMappings;
import org.jquantlib.model.marketmodels.curvestates.LMMCurveState;

/**
 * Derivative of swaption implied volatility with respect to changes in
 * pseudo-root elements. Needed for computing market vegas from a
 * Pathwise-Vegas accounting engine result.
 *
 * <p>Mirrors C++ {@code SwaptionPseudoDerivative}
 * (ql/models/marketmodels/pathwisegreeks/swaptionpseudojacobian.{hpp,cpp}
 * v1.42.1). Used in {@code testPathwiseVegas} as a building block for
 * {@code BumpInstrumentJacobian}.
 *
 * @author Jose Moya
 */
public class SwaptionPseudoDerivative {

    private final List<Matrix> varianceDerivatives_;
    private final List<Matrix> volatilityDerivatives_;

    private final double impliedVolatility_;
    private final double expiry_;
    private final double variance_;

    public SwaptionPseudoDerivative(final MarketModel inputModel,
                                    final int startIndex,
                                    final int endIndex) {
        final double[] subRateTimes = Arrays.copyOfRange(
                inputModel.evolution().rateTimes(), startIndex, endIndex + 1);
        final double[] subForwards = Arrays.copyOfRange(
                inputModel.initialRates(), startIndex, endIndex);

        final LMMCurveState cs = new LMMCurveState(subRateTimes);
        cs.setOnForwardRates(subForwards);

        final Matrix zed = SwapForwardMappings.coterminalSwapZedMatrix(
                cs, inputModel.displacements()[0]);
        final int factors = inputModel.numberOfFactors();

        // first compute variance and implied vol
        double variance = 0.0;
        int index = 0;

        while (index < inputModel.evolution().numberOfSteps()
                && inputModel.evolution().firstAliveRate()[index] <= startIndex) {
            final Matrix thisPseudo = inputModel.pseudoRoot(index);

            double thisVariance = 0.0;
            for (int j = startIndex; j < endIndex; ++j) {
                for (int k = startIndex; k < endIndex; ++k) {
                    for (int f = 0; f < factors; ++f) {
                        thisVariance += zed.get(0, j - startIndex)
                                * thisPseudo.get(j, f)
                                * thisPseudo.get(k, f)
                                * zed.get(0, k - startIndex);
                    }
                }
            }

            variance += thisVariance;
            ++index;
        }

        this.variance_ = variance;

        final int stopIndex = index;

        this.expiry_ = subRateTimes[0];
        this.impliedVolatility_ = Math.sqrt(variance_ / expiry_);

        final double scale = 0.5 * (1.0 / expiry_) / impliedVolatility_;

        final int numberRates = inputModel.evolution().numberOfRates();

        this.varianceDerivatives_ = new ArrayList<>(inputModel.evolution().numberOfSteps());
        this.volatilityDerivatives_ = new ArrayList<>(inputModel.evolution().numberOfSteps());

        index = 0;
        while (index < stopIndex) {
            final Matrix thisPseudo = inputModel.pseudoRoot(index);
            final Matrix thisDerivative = new Matrix(numberRates, factors);
            // already zero-initialised by Matrix(rows, cols)

            for (int rate = startIndex; rate < endIndex; ++rate) {
                final int zIndex = rate - startIndex;
                for (int f = 0; f < factors; ++f) {
                    double sum = 0.0;
                    for (int rate2 = startIndex; rate2 < endIndex; ++rate2) {
                        final int zIndex2 = rate2 - startIndex;
                        sum += zed.get(0, zIndex2) * thisPseudo.get(rate2, f);
                    }
                    sum *= 2.0 * zed.get(0, zIndex);
                    thisDerivative.set(rate, f, sum);
                }
            }

            // Save a copy of varianceDerivative
            varianceDerivatives_.add(new Matrix(thisDerivative));

            // Scale to volatility derivative
            for (int rate = startIndex; rate < endIndex; ++rate) {
                for (int f = 0; f < factors; ++f) {
                    thisDerivative.set(rate, f, thisDerivative.get(rate, f) * scale);
                }
            }

            volatilityDerivatives_.add(new Matrix(thisDerivative));
            ++index;
        }

        // Pad remaining steps with null derivative matrices
        for (; index < inputModel.evolution().numberOfSteps(); ++index) {
            varianceDerivatives_.add(new Matrix(numberRates, factors));
            volatilityDerivatives_.add(new Matrix(numberRates, factors));
        }
    }

    public Matrix varianceDerivative(final int i) {
        return varianceDerivatives_.get(i);
    }

    public Matrix volatilityDerivative(final int i) {
        return volatilityDerivatives_.get(i);
    }

    public double impliedVolatility() {
        return impliedVolatility_;
    }

    public double variance() {
        return variance_;
    }

    public double expiry() {
        return expiry_;
    }
}
