/*
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
*/

/*
 Copyright (C) 2014 Peter Caspers
 Copyright (C) 2023 Ignacio Anguita
 Copyright (C) 2026 Aaditya Panikath

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.experimental.volatility;

import java.util.List;

import org.jquantlib.indexes.SwapIndex;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.EndCriteria;
import org.jquantlib.math.optimization.OptimizationMethod;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.SwaptionVolatilityStructure;
import org.jquantlib.termstructures.volatilities.SmileSection;
import org.jquantlib.termstructures.volatilities.swaption.SabrSwaptionVolatilityCube;
import org.jquantlib.time.Period;

/**
 * No-arbitrage SABR (Doust 2012) volatility cube for swaptions.
 *
 * <p>Faithful port of QuantLib v1.42.1
 * {@code ql/experimental/volatility/noarbsabrswaptionvolatilitycube.hpp},
 * which itself is a typedef
 * {@code XabrSwaptionVolatilityCube<SwaptionVolCubeNoArbSabrModel>}.
 *
 * <p>The Java port (necessarily) cannot use C++ template metaprogramming; we
 * therefore subclass the concrete {@link SabrSwaptionVolatilityCube} and
 * override the two model-specific hooks introduced in that base class:
 * {@link #calibrateCell(Array, Array, double, double, double[], double)}
 * (per-cell calibration via {@link NoArbSabrInterpolation}) and
 * {@link #buildSmileSection(double, double, double[], double)} (smile-section
 * construction via {@link NoArbSabrSmileSection}). The shifted-lognormal
 * branch is rejected (mirrors C++ {@code NoArbSabrInterpolation} ctor's
 * {@code QL_REQUIRE(shift == 0.0, ...)}).
 *
 * <p>Phase 1.1 D5 SKIP-D3.
 */
public class NoArbSabrSwaptionVolatilityCube extends SabrSwaptionVolatilityCube {

    public NoArbSabrSwaptionVolatilityCube(final Handle< SwaptionVolatilityStructure > atmVolStructure,
            final List< Period > optionTenors, final List< Period > swapTenors, final List< Double > strikeSpreads,
            final List< List< Handle< Quote > > > volSpreads, final SwapIndex swapIndexBase,
            final SwapIndex shortSwapIndexBase, final boolean vegaWeightedSmileFit,
            final List< List< Handle< Quote > > > parametersGuess, final boolean[] isParameterFixed,
            final boolean isAtmCalibrated, final EndCriteria endCriteria, final double maxErrorTolerance,
            final OptimizationMethod optMethod, final double errorAccept, final boolean useMaxError,
            final int maxGuesses, final boolean backwardFlat, final double cutoffStrike) {
        super(atmVolStructure, optionTenors, swapTenors, strikeSpreads, volSpreads, swapIndexBase, shortSwapIndexBase,
                vegaWeightedSmileFit, parametersGuess, isParameterFixed, isAtmCalibrated, endCriteria,
                maxErrorTolerance, optMethod, errorAccept, useMaxError, maxGuesses, backwardFlat, cutoffStrike);
    }

    public NoArbSabrSwaptionVolatilityCube(final Handle< SwaptionVolatilityStructure > atmVolStructure,
            final List< Period > optionTenors, final List< Period > swapTenors, final List< Double > strikeSpreads,
            final List< List< Handle< Quote > > > volSpreads, final SwapIndex swapIndexBase,
            final SwapIndex shortSwapIndexBase, final boolean vegaWeightedSmileFit,
            final List< List< Handle< Quote > > > parametersGuess, final boolean[] isParameterFixed,
            final boolean isAtmCalibrated) {
        super(atmVolStructure, optionTenors, swapTenors, strikeSpreads, volSpreads, swapIndexBase, shortSwapIndexBase,
                vegaWeightedSmileFit, parametersGuess, isParameterFixed, isAtmCalibrated);
    }

    @Override
    protected SmileCalibrationResult calibrateCell(final Array strikes, final Array vols, final double t,
            final double forward, final double[] guess, final double shift) {
        // Mirror C++ XabrModelTraits<SwaptionVolCubeNoArbSabrModel>::createInterpolation:
        // NoArbSabrInterpolation does not take a volatilityType parameter; shift must be 0.
        final boolean[] isParamFixed = isParameterFixed();
        final NoArbSabrInterpolation interp = new NoArbSabrInterpolation(strikes, vols, t, forward, guess[0], guess[1],
                guess[2], guess[3], isParamFixed[0], isParamFixed[1], isParamFixed[2], isParamFixed[3],
                vegaWeightedSmileFit_, endCriteria(), optMethod(), errorAccept(), useMaxError(), maxGuesses(), shift);
        interp.xabrImpl().calculate();
        return new SmileCalibrationResult(interp.alpha(), interp.beta(), interp.nu(), interp.rho(), interp.rmsError(),
                interp.maxError(), interp.endCriteria().ordinal());
    }

    @Override
    protected SmileSection buildSmileSection(final double optionTime, final double forward, final double[] sabrParams,
            final double shift) {
        return new NoArbSabrSmileSection(optionTime, forward, sabrParams, shift, volatilityType());
    }
}
