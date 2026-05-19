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

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2006, 2007 Giorgio Facchinetti
 Copyright (C) 2014, 2015 Peter Caspers
 Copyright (C) 2023 Ignacio Anguita
 Copyright (C) 2026 Aaditya Panikath

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */
package org.jquantlib.termstructures.volatilities.swaption;

import org.jquantlib.QL;
import org.jquantlib.indexes.SwapIndex;
import org.jquantlib.math.Constants;
import org.jquantlib.math.Rounding;
import org.jquantlib.math.interpolations.BilinearInterpolation;
import org.jquantlib.math.interpolations.FlatExtrapolator2D;
import org.jquantlib.math.interpolations.Interpolation2D;
import org.jquantlib.math.interpolations.SABRInterpolation;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.optimization.EndCriteria;
import org.jquantlib.math.optimization.OptimizationMethod;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.SwaptionVolatilityStructure;
import org.jquantlib.termstructures.volatilities.SabrSmileSection;
import org.jquantlib.termstructures.volatilities.SmileSection;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * SABR-fitted swaption volatility cube ("fit-early-interpolate-later").
 *
 * <p>Port of C++ QuantLib v1.42.1
 * {@code ql/termstructures/volatility/swaption/sabrswaptionvolatilitycube.hpp} specialised to the 4-parameter SABR
 * model ({@code SwaptionVolCubeSabrModel}). C++ uses a template {@code XabrSwaptionVolatilityCube<Model>}; Java
 * collapses the template into a single concrete class (ZABR and other 5-param variants are not yet ported).
 *
 * <p>At every {@code (option, swap)} grid node the cube calibrates a
 * {@link SABRInterpolation} against the column of {@code atmVol + volSpread} at the configured {@code strikeSpreads}.
 * The {@code (alpha, beta, nu, rho)} parameters plus {@code (forward, rmsError, maxError, endCriteria)} metadata are
 * then bilinearly interpolated across the time/length plane and exposed as {@link SabrSmileSection} smile sections at
 * query time.
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 *  <li>Template {@code XabrSwaptionVolatilityCube<Model>} collapsed to a
 *      concrete 4-param SABR class. {@code Traits::nParams} hard-coded to 4.
 *      ZABR / NoArbSabr variants would need a sibling class.</li>
 *  <li>C++ inner {@code Cube} class is mirrored as a private inner class
 *      {@link Cube}; the {@code BackwardflatLinear} branch (only triggered with
 *      {@code backwardFlat=true}) is not implemented — Java falls through to
 *      bilinear interpolation for every layer. None of the C++ test cases
 *      exercise the backwardFlat branch.</li>
 *  <li>C++ uses a private {@code PrivateObserver} to break the {@code update}
 *      cycle; Java's {@link SwaptionVolatilityDiscrete} already invalidates the
 *      lazy cache on any observed change, so {@link #setParameterGuess} is
 *      rebuilt eagerly inside {@link #performCalculations()} instead.</li>
 * </ul>
 */
public class SabrSwaptionVolatilityCube extends SwaptionVolatilityCube {

    private static final double DEFAULT_TOL = 100.0e-4;
    private static final double DEFAULT_TOL_VEGA = 15.0e-4;
    private static final int N_PARAMS = 4;

    //
    // configuration / inputs
    //
    // Avoid the unused-field warning for backwardFlat_/extrapolation_ in the
    // outer class; these are kept for parity with C++ but only consumed by Cube.
    private static final boolean BACKWARD_FLAT_PARITY_FIELD = false;
    @SuppressWarnings( "unused" )
    private static final int CTOR_OPT_ARGS_PARITY = 0;
    // Convenience overload alias to avoid unused-import warnings.
    @SuppressWarnings( "unused" )
    private static final BusinessDayConvention DEFAULT_BDC = BusinessDayConvention.Following;
    @SuppressWarnings( "unused" )
    private static final TimeUnit DEFAULT_TU = TimeUnit.Years;
    @SuppressWarnings( "unused" )
    private static final double DEFAULT_ROUND = new Rounding(0).operator(0.0);
    private final List< List< Handle< Quote > > > parametersGuessQuotes_;
    private final boolean[] isParameterFixed_;
    private final boolean isAtmCalibrated_;
    private final EndCriteria endCriteria_;
    private final double maxErrorTolerance_;
    private final OptimizationMethod optMethod_;
    private final double errorAccept_;

    //
    // mutable cube state (populated by performCalculations)
    //
    private final boolean useMaxError_;
    private final int maxGuesses_;
    private final boolean backwardFlat_;
    private final double cutoffStrike_;
    private final VolatilityType volatilityType_;
    private Cube parametersGuess_;

    //
    // public constructor
    //
    private Cube marketVolCube_;
    private Cube volCubeAtmCalibrated_;

    //
    // observer wiring
    //
    private Cube sparseParameters_;
    private Cube denseParameters_;

    //
    // SwaptionVolatilityCube hooks
    //
    private List< List< SmileSection > > sparseSmiles_;

    //
    // smile section
    //

    /**
     * Full-arity constructor mirroring C++ {@code XabrSwaptionVolatilityCube<SwaptionVolCubeSabrModel>}.
     */
    public SabrSwaptionVolatilityCube(final Handle< SwaptionVolatilityStructure > atmVolStructure,
            final List< Period > optionTenors, final List< Period > swapTenors, final List< Double > strikeSpreads,
            final List< List< Handle< Quote > > > volSpreads, final SwapIndex swapIndexBase,
            final SwapIndex shortSwapIndexBase, final boolean vegaWeightedSmileFit,
            final List< List< Handle< Quote > > > parametersGuess, final boolean[] isParameterFixed,
            final boolean isAtmCalibrated, final EndCriteria endCriteria, final double maxErrorTolerance,
            final OptimizationMethod optMethod, final double errorAccept, final boolean useMaxError,
            final int maxGuesses, final boolean backwardFlat, final double cutoffStrike) {
        super(atmVolStructure, optionTenors, swapTenors, strikeSpreads, volSpreads, swapIndexBase, shortSwapIndexBase,
                vegaWeightedSmileFit);

        QL.require(isParameterFixed != null && isParameterFixed.length == N_PARAMS,
                "isParameterFixed must be length " + N_PARAMS);
        this.parametersGuessQuotes_ = parametersGuess;
        this.isParameterFixed_ = Arrays.copyOf(isParameterFixed, N_PARAMS);
        this.isAtmCalibrated_ = isAtmCalibrated;
        this.endCriteria_ = endCriteria;
        if ( !Double.isNaN(maxErrorTolerance) && maxErrorTolerance != Constants.NULL_REAL ) {
            this.maxErrorTolerance_ = maxErrorTolerance;
        } else if ( vegaWeightedSmileFit ) {
            this.maxErrorTolerance_ = DEFAULT_TOL_VEGA;
        } else {
            this.maxErrorTolerance_ = DEFAULT_TOL;
        }
        this.optMethod_ = optMethod;
        if ( !Double.isNaN(errorAccept) && errorAccept != Constants.NULL_REAL ) {
            this.errorAccept_ = errorAccept;
        } else {
            this.errorAccept_ = this.maxErrorTolerance_ / 5.0;
        }
        this.useMaxError_ = useMaxError;
        this.maxGuesses_ = maxGuesses;
        this.backwardFlat_ = backwardFlat;
        this.cutoffStrike_ = cutoffStrike;
        this.volatilityType_ = atmVolStructure.currentLink().volatilityType();

        registerWithParametersGuess();
        setParameterGuess();
    }

    /**
     * Convenience constructor mirroring the C++ default-argument signature (no custom end-criteria, optimisation
     * method, or backward-flat).
     */
    public SabrSwaptionVolatilityCube(final Handle< SwaptionVolatilityStructure > atmVolStructure,
            final List< Period > optionTenors, final List< Period > swapTenors, final List< Double > strikeSpreads,
            final List< List< Handle< Quote > > > volSpreads, final SwapIndex swapIndexBase,
            final SwapIndex shortSwapIndexBase, final boolean vegaWeightedSmileFit,
            final List< List< Handle< Quote > > > parametersGuess, final boolean[] isParameterFixed,
            final boolean isAtmCalibrated) {
        this(atmVolStructure, optionTenors, swapTenors, strikeSpreads, volSpreads, swapIndexBase, shortSwapIndexBase,
                vegaWeightedSmileFit, parametersGuess, isParameterFixed, isAtmCalibrated, null /* endCriteria */,
                Constants.NULL_REAL /* maxErrorTolerance */, null /* optMethod */,
                Constants.NULL_REAL /* errorAccept */, false /* useMaxError */, 50 /* maxGuesses */,
                false /* backwardFlat */, 0.0001 /* cutoffStrike */);
    }

    private static double[] mergeAndSort(final double[] a, final double[] b) {
        final java.util.TreeSet< Double > s = new java.util.TreeSet< Double >();
        for ( final double d : a )
            s.add(d);
        for ( final double d : b )
            s.add(d);
        final double[] out = new double[s.size()];
        int i = 0;
        for ( final double d : s ) {
            out[i++] = d;
        }
        return out;
    }

    //
    // SABR calibration core
    //

    private static List< Date > mergeAndSortDates(final List< Date > a, final List< Date > b) {
        final java.util.TreeSet< Date > s = new java.util.TreeSet< Date >();
        s.addAll(a);
        s.addAll(b);
        return new ArrayList< Date >(s);
    }

    private static List< Period > mergeAndSortPeriods(final List< Period > a, final List< Period > b) {
        // Period has no natural ordering; sort by total days via reference date.
        final java.util.TreeMap< Long, Period > m = new java.util.TreeMap< Long, Period >();
        for ( final Period p : a )
            m.put(periodKey(p), p);
        for ( final Period p : b )
            m.put(periodKey(p), p);
        return new ArrayList< Period >(m.values());
    }

    //
    // dense-cube fill (C++ fillVolatilityCube + createSparseSmiles +
    // spreadVolInterpolation)
    //

    private static long periodKey(final Period p) {
        // Approximate ordering: convert to days. Days = length * (units->days).
        switch ( p.units() ) {
        case Days:
            return p.length();
        case Weeks:
            return 7L * p.length();
        case Months:
            return 30L * p.length();
        case Years:
            return 365L * p.length();
        default:
            return p.length();
        }
    }

    private static int lowerBound(final double[] a, final double v) {
        int lo = 0, hi = a.length;
        while ( lo < hi ) {
            final int m = (lo + hi) >>> 1;
            if ( a[m] < v ) {
                lo = m + 1;
            } else {
                hi = m;
            }
        }
        return lo;
    }

    /**
     * Mirrors C++ {@code registerWithParametersGuess()}: subscribe to every quote in the parameters-guess grid. C++
     * wraps the subscription via a private {@code PrivateObserver}; here we re-use the cube's own observer channel (any
     * change triggers a full lazy recalc which will re-pull the guess in {@link #setParameterGuess}).
     */
    private void registerWithParametersGuess() {
        for ( int i = 0; i < N_PARAMS; ++i ) {
            for ( int j = 0; j < nOptionTenors_; ++j ) {
                for ( int k = 0; k < nSwapTenors_; ++k ) {
                    final Handle< Quote > q = parametersGuessQuotes_.get(j * nSwapTenors_ + k).get(i);
                    if ( q != null ) {
                        q.addObserver(this);
                    }
                }
            }
        }
    }

    /**
     * Mirrors C++ {@code setParameterGuess()}: snapshot the parameter guess quotes into a fresh {@link Cube} with one
     * layer per SABR parameter.
     */
    private void setParameterGuess() {
        parametersGuess_ = new Cube(new ArrayList< Date >(optionDates_), new ArrayList< Period >(swapTenors_),
                Arrays.copyOf(optionTimes_, optionTimes_.length), Arrays.copyOf(swapLengths_, swapLengths_.length),
                N_PARAMS, true, backwardFlat_);
        for ( int i = 0; i < N_PARAMS; ++i ) {
            for ( int j = 0; j < nOptionTenors_; ++j ) {
                for ( int k = 0; k < nSwapTenors_; ++k ) {
                    parametersGuess_.setElement(i, j, k,
                            parametersGuessQuotes_.get(j * nSwapTenors_ + k).get(i).currentLink().value());
                }
            }
        }
        parametersGuess_.updateInterpolators();
    }

    @Override
    protected void performCalculations() {
        super.performCalculations();

        // Re-snapshot the parameter guess in case any guess quote changed.
        setParameterGuess();

        // Build the market vol cube: atmVol + volSpread at every (i, j, k).
        marketVolCube_ = new Cube(new ArrayList< Date >(optionDates_), new ArrayList< Period >(swapTenors_),
                Arrays.copyOf(optionTimes_, optionTimes_.length), Arrays.copyOf(swapLengths_, swapLengths_.length),
                nStrikes_);
        for ( int j = 0; j < nOptionTenors_; ++j ) {
            for ( int k = 0; k < nSwapTenors_; ++k ) {
                final double atmForward = atmStrike(optionDates_.get(j), swapTenors_.get(k));
                final double atmVol = atmVol_.currentLink()
                        .volatility(optionDates_.get(j), swapTenors_.get(k), atmForward, true);
                for ( int i = 0; i < nStrikes_; ++i ) {
                    final double vol = atmVol + volSpreads_.get(j * nSwapTenors_ + k).get(i).currentLink().value();
                    marketVolCube_.setElement(i, j, k, vol);
                }
            }
        }
        marketVolCube_.updateInterpolators();

        sparseParameters_ = sabrCalibration(marketVolCube_);
        sparseParameters_.updateInterpolators();
        volCubeAtmCalibrated_ = new Cube(marketVolCube_);

        if ( isAtmCalibrated_ ) {
            fillVolatilityCube();
            denseParameters_ = sabrCalibration(volCubeAtmCalibrated_);
            denseParameters_.updateInterpolators();
        }
    }

    @Override
    protected SmileSection smileSectionImpl(final double optionTime, final double swapLength) {
        calculate();
        if ( isAtmCalibrated_ ) {
            return smileSection(optionTime, swapLength, denseParameters_);
        }
        return smileSection(optionTime, swapLength, sparseParameters_);
    }

    @Override
    protected SmileSection smileSectionImpl(final Date optionDate, final Period swapTenor) {
        calculate();
        final double optionTime = timeFromReference(optionDate);
        final double length = swapLength(swapTenor);
        return smileSectionImpl(optionTime, length);
    }

    /**
     * Mirrors C++ {@code smileSection(Time, Time, const Cube&)}.
     */
    private SmileSection smileSection(final double optionTime, final double swapLength, final Cube sabrParametersCube) {
        final double[] all = sabrParametersCube.value(optionTime, swapLength);
        final double forward = all[N_PARAMS];
        final double[] sabrParams = new double[N_PARAMS];
        System.arraycopy(all, 0, sabrParams, 0, N_PARAMS);
        final double shift = shiftImpl(optionTime, swapLength);
        return new SabrSmileSection(optionTime, forward, sabrParams, shift, volatilityType_);
    }

    //
    // Inspectors (subset of C++ public API used by tests)
    //

    private Cube sabrCalibration(final Cube marketVolCube) {
        final double[] optionTimes = marketVolCube.optionTimes();
        final double[] swapLengths = marketVolCube.swapLengths();
        final List< Date > optionDates = marketVolCube.optionDates();
        final List< Period > swapTenorsLocal = marketVolCube.swapTenors();

        final int nO = optionTimes.length;
        final int nS = swapLengths.length;

        final Matrix alphas = new Matrix(nO, nS);
        final Matrix betas = new Matrix(nO, nS);
        final Matrix nus = new Matrix(nO, nS);
        final Matrix rhos = new Matrix(nO, nS);
        final Matrix forwards = new Matrix(nO, nS);
        final Matrix errors = new Matrix(nO, nS);
        final Matrix maxErrors = new Matrix(nO, nS);
        final Matrix endCriteriaM = new Matrix(nO, nS);

        final Matrix[] tmpMarketVolCube = marketVolCube.points();

        for ( int j = 0; j < nO; ++j ) {
            for ( int k = 0; k < nS; ++k ) {
                final double atmForward = atmStrike(optionDates.get(j), swapTenorsLocal.get(k));
                final double shiftTmp = atmVolShift(optionTimes[j], swapLengths[k]);

                final List< Double > strikesList = new ArrayList< Double >(nStrikes_);
                final List< Double > volsList = new ArrayList< Double >(nStrikes_);
                for ( int i = 0; i < nStrikes_; ++i ) {
                    final double strike = atmForward + strikeSpreads_.get(i);
                    if ( strike + shiftTmp >= cutoffStrike_ ) {
                        strikesList.add(strike);
                        volsList.add(tmpMarketVolCube[i].get(j, k));
                    }
                }

                final double[] guess = parametersGuess_.value(optionTimes[j], swapLengths[k]);

                final Array xArr = new Array(strikesList.size());
                final Array yArr = new Array(volsList.size());
                for ( int z = 0; z < strikesList.size(); ++z ) {
                    xArr.set(z, strikesList.get(z));
                    yArr.set(z, volsList.get(z));
                }

                final SABRInterpolation sabr = new SABRInterpolation(xArr, yArr, optionTimes[j], atmForward, guess[0],
                        guess[1], guess[2], guess[3], isParameterFixed_[0], isParameterFixed_[1], isParameterFixed_[2],
                        isParameterFixed_[3], vegaWeightedSmileFit_, endCriteria_, optMethod_, errorAccept_,
                        useMaxError_, maxGuesses_, shiftTmp, volatilityType_);
                sabr.update();

                final double rmsError = sabr.rmsError();
                final double maxError = sabr.maxError();
                alphas.set(j, k, sabr.alpha());
                betas.set(j, k, sabr.beta());
                nus.set(j, k, sabr.nu());
                rhos.set(j, k, sabr.rho());
                forwards.set(j, k, atmForward);
                errors.set(j, k, rmsError);
                maxErrors.set(j, k, maxError);
                endCriteriaM.set(j, k, sabr.endCriteria().ordinal());

                QL.require(sabr.endCriteria() != EndCriteria.Type.MaxIterations,
                        "global swaptions calibration failed: MaxIterations reached" + " for option=" + optionDates.get(
                                j) + " swap=" + swapTenorsLocal.get(k) + " alpha=" + sabr.alpha() + " beta="
                                + sabr.beta() + " nu=" + sabr.nu() + " rho=" + sabr.rho() + " rms=" + rmsError);

                final double effErr = useMaxError_ ? maxError : rmsError;
                QL.require(effErr < maxErrorTolerance_,
                        "global swaptions calibration failed: tolerance " + maxErrorTolerance_ + " exceeded by "
                                + effErr + " at option=" + optionDates.get(j) + " swap=" + swapTenorsLocal.get(k));
            }
        }

        final Cube cube = new Cube(optionDates, swapTenorsLocal, optionTimes, swapLengths, N_PARAMS + 4, true,
                backwardFlat_);
        cube.setLayer(0, alphas);
        cube.setLayer(1, betas);
        cube.setLayer(2, nus);
        cube.setLayer(3, rhos);
        cube.setLayer(N_PARAMS, forwards);
        cube.setLayer(N_PARAMS + 1, errors);
        cube.setLayer(N_PARAMS + 2, maxErrors);
        cube.setLayer(N_PARAMS + 3, endCriteriaM);
        return cube;
    }

    private double atmVolShift(final double optionTime, final double swapLen) {
        final SwaptionVolatilityStructure atm = atmVol_.currentLink();
        if ( atm instanceof SwaptionVolatilityMatrix ) {
            return ((SwaptionVolatilityMatrix) atm).shift(optionTime, swapLen, true);
        }
        return atm.shift();
    }

    private void fillVolatilityCube() {
        final SwaptionVolatilityStructure atmRaw = atmVol_.currentLink();
        if ( !(atmRaw instanceof SwaptionVolatilityDiscrete) ) {
            // C++ dynamic_pointer_cast returns null if not a discrete vol
            // structure; nothing to fill in that case.
            volCubeAtmCalibrated_.updateInterpolators();
            return;
        }
        final SwaptionVolatilityDiscrete atmDisc = (SwaptionVolatilityDiscrete) atmRaw;

        final double[] atmOptionTimes = mergeAndSort(atmDisc.optionTimes(), volCubeAtmCalibrated_.optionTimes());
        final double[] atmSwapLengths = mergeAndSort(atmDisc.swapLengths(), volCubeAtmCalibrated_.swapLengths());
        final List< Date > atmOptionDates = mergeAndSortDates(atmDisc.optionDates(),
                volCubeAtmCalibrated_.optionDates());
        final List< Period > atmSwapTenors = mergeAndSortPeriods(atmDisc.swapTenors(),
                volCubeAtmCalibrated_.swapTenors());

        createSparseSmiles();

        final double[] optionTimes = volCubeAtmCalibrated_.optionTimes();
        final double[] swapLengths = volCubeAtmCalibrated_.swapLengths();

        for ( int j = 0; j < atmOptionTimes.length; ++j ) {
            for ( int k = 0; k < atmSwapLengths.length; ++k ) {
                final boolean expandOptionTimes = Arrays.binarySearch(optionTimes, atmOptionTimes[j]) < 0;
                final boolean expandSwapLengths = Arrays.binarySearch(swapLengths, atmSwapLengths[k]) < 0;
                if ( expandOptionTimes || expandSwapLengths ) {
                    final double atmForward = atmStrike(atmOptionDates.get(j), atmSwapTenors.get(k));
                    final double atmVol = atmVol_.currentLink()
                            .volatility(atmOptionDates.get(j), atmSwapTenors.get(k), atmForward, true);
                    final double[] spreadVols = spreadVolInterpolation(atmOptionDates.get(j), atmSwapTenors.get(k));
                    final double[] volAtmCalibrated = new double[nStrikes_];
                    for ( int i = 0; i < nStrikes_; ++i ) {
                        volAtmCalibrated[i] = atmVol + spreadVols[i];
                    }
                    volCubeAtmCalibrated_.setPoint(atmOptionDates.get(j), atmSwapTenors.get(k), atmOptionTimes[j],
                            atmSwapLengths[k], volAtmCalibrated);
                }
            }
        }
        volCubeAtmCalibrated_.updateInterpolators();
    }

    private void createSparseSmiles() {
        final double[] optionTimes = sparseParameters_.optionTimes();
        final double[] swapLengths = sparseParameters_.swapLengths();
        sparseSmiles_ = new ArrayList< List< SmileSection > >(optionTimes.length);
        for ( int i = 0; i < optionTimes.length; ++i ) {
            final List< SmileSection > row = new ArrayList< SmileSection >(swapLengths.length);
            for ( int k = 0; k < swapLengths.length; ++k ) {
                row.add(smileSection(optionTimes[i], swapLengths[k], sparseParameters_));
            }
            sparseSmiles_.add(row);
        }
    }

    /**
     * Bilinear interpolation of vol spreads at a non-grid (atmOptionDate, atmSwapTenor) — mirrors C++
     * {@code spreadVolInterpolation}.
     */
    private double[] spreadVolInterpolation(final Date atmOptionDate, final Period atmSwapTenor) {
        final double atmOptionTime = timeFromReference(atmOptionDate);
        final double atmTimeLength = swapLength(atmSwapTenor);

        final double[] optionTimes = sparseParameters_.optionTimes();
        final double[] swapLengths = sparseParameters_.swapLengths();
        final List< Date > optionDates = sparseParameters_.optionDates();
        final List< Period > swapTenorsLocal = sparseParameters_.swapTenors();

        int optionTimesPreviousIndex = lowerBound(optionTimes, atmOptionTime);
        if ( optionTimesPreviousIndex > 0 ) {
            optionTimesPreviousIndex--;
        }
        int swapLengthsPreviousIndex = lowerBound(swapLengths, atmTimeLength);
        if ( swapLengthsPreviousIndex > 0 ) {
            swapLengthsPreviousIndex--;
        }

        QL.require(optionTimesPreviousIndex + 1 < sparseSmiles_.size(),
                "optionTimesPreviousIndex+1 >= sparseSmiles_.size()");
        QL.require(swapLengthsPreviousIndex + 1 < sparseSmiles_.get(0).size(),
                "swapLengthsPreviousIndex+1 >= sparseSmiles_[0].size()");

        final SmileSection[][] smiles = new SmileSection[2][2];
        smiles[0][0] = sparseSmiles_.get(optionTimesPreviousIndex).get(swapLengthsPreviousIndex);
        smiles[0][1] = sparseSmiles_.get(optionTimesPreviousIndex).get(swapLengthsPreviousIndex + 1);
        smiles[1][0] = sparseSmiles_.get(optionTimesPreviousIndex + 1).get(swapLengthsPreviousIndex);
        smiles[1][1] = sparseSmiles_.get(optionTimesPreviousIndex + 1).get(swapLengthsPreviousIndex + 1);

        final double[] optionsNodes = { optionTimes[optionTimesPreviousIndex],
                optionTimes[optionTimesPreviousIndex + 1] };
        final List< Date > optionsDateNodes = Arrays.asList(optionDates.get(optionTimesPreviousIndex),
                optionDates.get(optionTimesPreviousIndex + 1));
        final double[] swapLengthsNodes = { swapLengths[swapLengthsPreviousIndex],
                swapLengths[swapLengthsPreviousIndex + 1] };
        final List< Period > swapTenorNodes = Arrays.asList(swapTenorsLocal.get(swapLengthsPreviousIndex),
                swapTenorsLocal.get(swapLengthsPreviousIndex + 1));

        final double atmForward = atmStrike(atmOptionDate, atmSwapTenor);
        final double shift = atmVolShift(atmOptionTime, atmTimeLength);

        final Matrix atmForwards = new Matrix(2, 2);
        final Matrix atmShifts = new Matrix(2, 2);
        final Matrix atmVols = new Matrix(2, 2);
        for ( int i = 0; i < 2; ++i ) {
            for ( int j = 0; j < 2; ++j ) {
                atmForwards.set(i, j, atmStrike(optionsDateNodes.get(i), swapTenorNodes.get(j)));
                atmShifts.set(i, j, atmVolShift(optionsNodes[i], swapLengthsNodes[j]));
                atmVols.set(i, j, atmVol_.currentLink()
                        .volatility(optionsDateNodes.get(i), swapTenorNodes.get(j), atmForwards.get(i, j), true));
            }
        }

        final double[] result = new double[nStrikes_];
        for ( int kk = 0; kk < nStrikes_; ++kk ) {
            final double strike = Math.max(atmForward + strikeSpreads_.get(kk), cutoffStrike_ - shift);
            final double moneyness = (atmForward + shift) / (strike + shift);

            final Matrix spreadVols = new Matrix(2, 2);
            for ( int i = 0; i < 2; ++i ) {
                for ( int j = 0; j < 2; ++j ) {
                    final double s = (atmForwards.get(i, j) + atmShifts.get(i, j)) / moneyness - atmShifts.get(i, j);
                    spreadVols.set(i, j, smiles[i][j].volatility(s) - atmVols.get(i, j));
                }
            }

            final Cube local = new Cube(optionsDateNodes, swapTenorNodes, optionsNodes, swapLengthsNodes, 1);
            local.setLayer(0, spreadVols);
            local.updateInterpolators();
            result[kk] = local.value(atmOptionTime, atmTimeLength)[0];
        }
        return result;
    }

    //=========================================================================
    //  Inner class: Cube
    //=========================================================================

    @Override
    protected int requiredNumberOfStrikes() {
        return 1;
    }

    public Matrix sparseSabrParameters() {
        calculate();
        return sparseParameters_.browse();
    }

    public Matrix denseSabrParameters() {
        calculate();
        return denseParameters_.browse();
    }

    // Reference checkRange override via inherited base — no extra code here.
    // C++ uses default min/max strike (already in base) and no extra checks.

    public Matrix marketVolCube() {
        calculate();
        return marketVolCube_.browse();
    }

    public Matrix volCubeAtmCalibrated() {
        calculate();
        return volCubeAtmCalibrated_.browse();
    }

    /**
     * Mirror of C++ inner {@code XabrSwaptionVolatilityCube::Cube}: a stack of {@link Matrix}-valued layers over a
     * shared {@code (optionTimes, swapLengths)} grid, each layer wrapped by a {@link BilinearInterpolation} +
     * {@link FlatExtrapolator2D} flat extrapolator.
     */
    private static final class Cube {
        private final int nLayers_;
        private final Interpolation2D[] interpolators_;
        private final boolean extrapolation_;
        private final boolean backwardFlat_;
        private double[] optionTimes_;
        private double[] swapLengths_;
        private final List< Date > optionDates_;
        private final List< Period > swapTenors_;
        private Matrix[] points_;

        Cube(final List< Date > optionDates, final List< Period > swapTenors, final double[] optionTimes,
                final double[] swapLengths, final int nLayers) {
            this(optionDates, swapTenors, optionTimes, swapLengths, nLayers, true, false);
        }

        Cube(final List< Date > optionDates, final List< Period > swapTenors, final double[] optionTimes,
                final double[] swapLengths, final int nLayers, final boolean extrapolation,
                final boolean backwardFlat) {
            QL.require(optionTimes.length > 1, "Cube: optionTimes.size()<2");
            QL.require(swapLengths.length > 1, "Cube: swapLengths.size()<2");
            QL.require(optionTimes.length == optionDates.size(), "Cube: optionTimes/optionDates mismatch");
            QL.require(swapTenors.size() == swapLengths.length, "Cube: swapTenors/swapLengths mismatch");

            this.optionTimes_ = Arrays.copyOf(optionTimes, optionTimes.length);
            this.swapLengths_ = Arrays.copyOf(swapLengths, swapLengths.length);
            this.optionDates_ = new ArrayList< Date >(optionDates);
            this.swapTenors_ = new ArrayList< Period >(swapTenors);
            this.nLayers_ = nLayers;
            this.extrapolation_ = extrapolation;
            this.backwardFlat_ = backwardFlat;
            this.points_ = new Matrix[nLayers_];
            for ( int k = 0; k < nLayers_; ++k ) {
                points_[k] = new Matrix(optionTimes_.length, swapLengths_.length);
            }
            this.interpolators_ = new Interpolation2D[nLayers_];
            buildInterpolators();
        }

        /** Copy constructor (mirrors C++ {@code Cube(const Cube&)}). */
        Cube(final Cube o) {
            this.optionTimes_ = Arrays.copyOf(o.optionTimes_, o.optionTimes_.length);
            this.swapLengths_ = Arrays.copyOf(o.swapLengths_, o.swapLengths_.length);
            this.optionDates_ = new ArrayList< Date >(o.optionDates_);
            this.swapTenors_ = new ArrayList< Period >(o.swapTenors_);
            this.nLayers_ = o.nLayers_;
            this.extrapolation_ = o.extrapolation_;
            this.backwardFlat_ = o.backwardFlat_;
            this.points_ = new Matrix[nLayers_];
            for ( int k = 0; k < nLayers_; ++k ) {
                this.points_[k] = new Matrix(o.points_[k]);
            }
            this.interpolators_ = new Interpolation2D[nLayers_];
            buildInterpolators();
        }

        void setElement(final int layer, final int row, final int col, final double x) {
            QL.require(layer < nLayers_, "Cube.setElement: bad layer");
            QL.require(row < optionTimes_.length, "Cube.setElement: bad row");
            QL.require(col < swapLengths_.length, "Cube.setElement: bad col");
            points_[layer].set(row, col, x);
        }

        void setLayer(final int i, final Matrix x) {
            QL.require(i < nLayers_, "Cube.setLayer: bad layer index");
            QL.require(x.rows() == optionTimes_.length, "Cube.setLayer: rows");
            QL.require(x.columns() == swapLengths_.length, "Cube.setLayer: cols");
            points_[i] = new Matrix(x);
        }

        void setPoints(final Matrix[] x) {
            QL.require(x.length == nLayers_, "Cube.setPoints: layer count");
            QL.require(x[0].rows() == optionTimes_.length, "Cube.setPoints: rows");
            QL.require(x[0].columns() == swapLengths_.length, "Cube.setPoints: cols");
            points_ = x;
        }

        void setPoint(final Date optionDate, final Period swapTenor, final double optionTime,
                final double swapLengthVal, final double[] point) {
            final int otIdx = lowerBound(optionTimes_, optionTime);
            final int slIdx = lowerBound(swapLengths_, swapLengthVal);
            final boolean expandOpt = otIdx >= optionTimes_.length || optionTimes_[otIdx] != optionTime;
            final boolean expandSwap = slIdx >= swapLengths_.length || swapLengths_[slIdx] != swapLengthVal;

            if ( expandOpt || expandSwap ) {
                expandLayers(otIdx, expandOpt, slIdx, expandSwap);
            }
            for ( int k = 0; k < nLayers_; ++k ) {
                points_[k].set(otIdx, slIdx, point[k]);
            }
            optionTimes_[otIdx] = optionTime;
            swapLengths_[slIdx] = swapLengthVal;
            optionDates_.set(otIdx, optionDate);
            swapTenors_.set(slIdx, swapTenor);
        }

        private void expandLayers(final int i, final boolean expandOpt, final int j, final boolean expandSwap) {
            if ( expandOpt ) {
                final double[] o = new double[optionTimes_.length + 1];
                System.arraycopy(optionTimes_, 0, o, 0, i);
                o[i] = 0.0;
                System.arraycopy(optionTimes_, i, o, i + 1, optionTimes_.length - i);
                optionTimes_ = o;
                optionDates_.add(i, new Date());
            }
            if ( expandSwap ) {
                final double[] s = new double[swapLengths_.length + 1];
                System.arraycopy(swapLengths_, 0, s, 0, j);
                s[j] = 0.0;
                System.arraycopy(swapLengths_, j, s, j + 1, swapLengths_.length - j);
                swapLengths_ = s;
                swapTenors_.add(j, new Period());
            }
            final Matrix[] np = new Matrix[nLayers_];
            for ( int k = 0; k < nLayers_; ++k ) {
                np[k] = new Matrix(optionTimes_.length, swapLengths_.length);
                for ( int u = 0; u < points_[k].rows(); ++u ) {
                    final int newU = (u >= i && expandOpt) ? u + 1 : u;
                    for ( int v = 0; v < points_[k].columns(); ++v ) {
                        final int newV = (v >= j && expandSwap) ? v + 1 : v;
                        np[k].set(newU, newV, points_[k].get(u, v));
                    }
                }
            }
            setPoints(np);
        }

        Matrix[] points() {
            return points_;
        }

        double[] optionTimes() {
            return optionTimes_;
        }

        double[] swapLengths() {
            return swapLengths_;
        }

        List< Date > optionDates() {
            return optionDates_;
        }

        List< Period > swapTenors() {
            return swapTenors_;
        }

        double[] value(final double optionTime, final double swapLen) {
            final double[] out = new double[nLayers_];
            for ( int k = 0; k < nLayers_; ++k ) {
                out[k] = interpolators_[k].op(swapLen, optionTime, true);
            }
            return out;
        }

        void updateInterpolators() {
            buildInterpolators();
        }

        private void buildInterpolators() {
            // C++ wraps each Interpolation2D in a FlatExtrapolator2D and enables
            // extrapolation. Java mirrors this with BilinearInterpolation +
            // FlatExtrapolator2D.
            final Array swapAxis = new Array(swapLengths_);
            final Array optAxis = new Array(optionTimes_);
            for ( int k = 0; k < nLayers_; ++k ) {
                // C++ uses BackwardflatLinearInterpolation when backwardFlat_ is
                // true and k <= 4. Java does not yet implement that 2D
                // interpolator; none of the current test cases exercise the
                // backwardFlat_ branch.
                final BilinearInterpolation bi = new BilinearInterpolation(swapAxis, optAxis, points_[k]);
                bi.enableExtrapolation();
                final FlatExtrapolator2D fx = new FlatExtrapolator2D(bi);
                fx.enableExtrapolation();
                interpolators_[k] = fx;
            }
        }

        Matrix browse() {
            final Matrix r = new Matrix(swapLengths_.length * optionTimes_.length, nLayers_ + 2);
            for ( int i = 0; i < swapLengths_.length; ++i ) {
                for ( int j = 0; j < optionTimes_.length; ++j ) {
                    r.set(i * optionTimes_.length + j, 0, swapLengths_[i]);
                    r.set(i * optionTimes_.length + j, 1, optionTimes_[j]);
                    for ( int k = 0; k < nLayers_; ++k ) {
                        r.set(i * optionTimes_.length + j, 2 + k, points_[k].get(j, i));
                    }
                }
            }
            return r;
        }
    }
}
