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
 Copyright (C) 2006 Ferdinando Ametrano
 Copyright (C) 2006 Katiuscia Manzoni
 Copyright (C) 2015 Peter Caspers
 Copyright (C) 2023 Ignacio Anguita

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */
package org.jquantlib.termstructures.volatilities.swaption;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.indexes.SwapIndex;
import org.jquantlib.math.Rounding;
import org.jquantlib.math.interpolations.BilinearInterpolation;
import org.jquantlib.math.interpolations.Interpolation2D;
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.SwaptionVolatilityStructure;
import org.jquantlib.termstructures.volatilities.InterpolatedSmileSection;
import org.jquantlib.termstructures.volatilities.SmileSection;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;

/**
 * "Fit-later-interpolate-early" swaption volatility cube — bilinear
 * interpolation across the (option-time, swap-length) plane at each strike
 * spread, then a per-section {@link InterpolatedSmileSection} across strikes
 * at query time.
 *
 * <p>Port of C++ QuantLib v1.42.1 {@code
 * ql/termstructures/volatility/swaption/interpolatedswaptionvolatilitycube.{hpp,cpp}}.
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 *  <li>C++ instantiates one {@code InterpolatedSmileSection<Linear>} per
 *      query (template + factory). Java uses
 *      {@link InterpolatedSmileSection}'s raw-double ctor and a fresh
 *      {@link Linear} factory.</li>
 *  <li>C++ uses {@code Following} adjustment via the
 *      {@code swapIndex_->fixingCalendar()}. Java mirrors this with
 *      {@link org.jquantlib.time.Calendar#adjust(Date, BusinessDayConvention)}.</li>
 * </ul>
 */
public class InterpolatedSwaptionVolatilityCube extends SwaptionVolatilityCube {

    //
    // private state
    //

    private final Interpolation2D[] volSpreadsInterpolator_;
    private final Matrix[] volSpreadsMatrix_;

    //
    // public constructor
    //

    public InterpolatedSwaptionVolatilityCube(
            final Handle<SwaptionVolatilityStructure> atmVolStructure,
            final List<Period> optionTenors,
            final List<Period> swapTenors,
            final List<Double> strikeSpreads,
            final List<List<Handle<Quote>>> volSpreads,
            final SwapIndex swapIndexBase,
            final SwapIndex shortSwapIndexBase,
            final boolean vegaWeightedSmileFit) {
        super(atmVolStructure, optionTenors, swapTenors, strikeSpreads,
                volSpreads, swapIndexBase, shortSwapIndexBase,
                vegaWeightedSmileFit);

        this.volSpreadsInterpolator_ = new Interpolation2D[nStrikes_];
        this.volSpreadsMatrix_ = new Matrix[nStrikes_];
        for (int i = 0; i < nStrikes_; ++i) {
            this.volSpreadsMatrix_[i] = new Matrix(optionTenors.size(),
                    swapTenors.size());
        }
    }

    //
    // SwaptionVolatilityCube hooks
    //

    /**
     * Refresh per-strike spread matrices from the quote handles and (re)build
     * one {@link BilinearInterpolation} per strike.
     */
    @Override
    protected void performCalculations() {
        super.performCalculations();
        // Populate volSpreadsMatrix_[i][j,k] from volSpreads_[j*nSwapTenors_+k][i]
        for (int i = 0; i < nStrikes_; ++i) {
            for (int j = 0; j < nOptionTenors_; ++j) {
                for (int k = 0; k < nSwapTenors_; ++k) {
                    volSpreadsMatrix_[i].set(j, k,
                            volSpreads_.get(j * nSwapTenors_ + k).get(i)
                                    .currentLink().value());
                }
            }
        }
        // (Re)build bilinear interpolators
        final Array swapAxis = new Array(swapLengths_);
        final Array optAxis = new Array(optionTimes_);
        for (int i = 0; i < nStrikes_; ++i) {
            volSpreadsInterpolator_[i] = new BilinearInterpolation(
                    swapAxis, optAxis, volSpreadsMatrix_[i]);
            volSpreadsInterpolator_[i].enableExtrapolation();
        }
    }

    /**
     * Accessor (mirror C++ public inspector).
     */
    public Matrix volSpreads(final int i) {
        calculate();
        return volSpreadsMatrix_[i];
    }

    //
    // SwaptionVolatilityStructure hooks (smile sections)
    //

    @Override
    protected SmileSection smileSectionImpl(final double optionTime,
                                            final double swapLength) {
        calculate();
        Date optionDate = optionDateFromTime(optionTime);
        // C++: round to nearest month then build a Period of that many months
        final Rounding rounder = new Rounding(0);
        final int months = (int) rounder.operator(swapLength * 12.0);
        final Period swapTenor = new Period(months, TimeUnit.Months);
        // Ensure that option date is a valid fixing date
        if (swapTenor.gt(shortSwapIndexBase_.tenor())) {
            optionDate = swapIndexBase_.fixingCalendar()
                    .adjust(optionDate, BusinessDayConvention.Following);
        } else {
            optionDate = shortSwapIndexBase_.fixingCalendar()
                    .adjust(optionDate, BusinessDayConvention.Following);
        }
        return smileSectionImpl(optionDate, swapTenor);
    }

    @Override
    protected SmileSection smileSectionImpl(final Date optionDate,
                                            final Period swapTenor) {
        calculate();
        final double atmForward = atmStrike(optionDate, swapTenor);
        final double atmVol = atmVol_.currentLink()
                .volatility(optionDate, swapTenor, atmForward, true);
        final double optionTime = timeFromReference(optionDate);
        final double exerciseTimeSqrt = Math.sqrt(optionTime);
        final double length = swapLength(swapTenor);

        final double[] strikes = new double[nStrikes_];
        final double[] stdDevs = new double[nStrikes_];
        for (int i = 0; i < nStrikes_; ++i) {
            strikes[i] = atmForward + strikeSpreads_.get(i);
            stdDevs[i] = exerciseTimeSqrt
                    * (atmVol + volSpreadsInterpolator_[i].op(length, optionTime, true));
        }
        final double shift = shiftImpl(optionTime, length);
        return new InterpolatedSmileSection(
                optionTime,
                strikes,
                stdDevs,
                atmForward,
                new Linear(),
                new Actual365Fixed(),
                volatilityType(),
                shift,
                false /* flatStrikeExtrapolation */);
    }
}
