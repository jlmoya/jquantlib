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
 Copyright (C) 2015 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */
package org.jquantlib.termstructures.volatilities.swaption;

import org.jquantlib.QL;
import org.jquantlib.indexes.SwapIndex;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.SwaptionVolatilityStructure;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;

import java.util.List;

/**
 * Swaption-volatility cube (abstract).
 *
 * <p>Port of C++ QuantLib v1.42.1 {@code
 * ql/termstructures/volatility/swaption/swaptionvolcube.{hpp,cpp}}.
 *
 * <p>The cube is built on top of an ATM {@link SwaptionVolatilityStructure} and
 * a grid of additive volatility spreads at a set of {@code strikeSpreads} relative to the ATM strike. Concrete
 * subclasses (interpolated / SABR-fitted) decide how to combine the spreads with the ATM surface to produce a smile
 * section at any (option, swap) coordinate.
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 *  <li>C++ inherits both {@code SwaptionVolatilityDiscrete} and
 *      {@code LazyObject}. Java collapses both via the
 *      {@link SwaptionVolatilityDiscrete#calculate()} hook (LazyObject
 *      semantics already woven into the discrete base).</li>
 *  <li>{@link #atmStrike(Date, Period)} simplifies the C++ four-way switch
 *      on {@code exogenousDiscount} (Java {@link SwapIndex} does not yet
 *      track an exogenous discount handle). The behaviour matches the
 *      common case of {@code exogenousDiscount == false} for both the long
 *      and short index families.</li>
 * </ul>
 */
public abstract class SwaptionVolatilityCube extends SwaptionVolatilityDiscrete {

    //
    // protected fields
    //

    protected final Handle< SwaptionVolatilityStructure > atmVol_;
    protected final int nStrikes_;
    protected final List< Double > strikeSpreads_;
    protected final double[] localStrikes_;
    protected final double[] localSmile_;
    protected final List< List< Handle< Quote > > > volSpreads_;
    protected final SwapIndex swapIndexBase_;
    protected final SwapIndex shortSwapIndexBase_;
    protected final boolean vegaWeightedSmileFit_;

    //
    // public constructor
    //

    public SwaptionVolatilityCube(final Handle< SwaptionVolatilityStructure > atmVolStructure,
            final List< Period > optionTenors, final List< Period > swapTenors, final List< Double > strikeSpreads,
            final List< List< Handle< Quote > > > volSpreads, final SwapIndex swapIndexBase,
            final SwapIndex shortSwapIndexBase, final boolean vegaWeightedSmileFit) {
        super(optionTenors, swapTenors, 0, atmVolStructure.currentLink().calendar(),
                atmVolStructure.currentLink().businessDayConvention(), atmVolStructure.currentLink().dayCounter());

        this.atmVol_ = atmVolStructure;
        this.nStrikes_ = strikeSpreads.size();
        this.strikeSpreads_ = strikeSpreads;
        this.localStrikes_ = new double[nStrikes_];
        this.localSmile_ = new double[nStrikes_];
        this.volSpreads_ = volSpreads;
        this.swapIndexBase_ = swapIndexBase;
        this.shortSwapIndexBase_ = shortSwapIndexBase;
        this.vegaWeightedSmileFit_ = vegaWeightedSmileFit;

        QL.require(!atmVol_.empty(), "atm vol handle not linked to anything");
        for ( int i = 1; i < nStrikes_; ++i ) {
            QL.require(strikeSpreads_.get(i - 1) < strikeSpreads_.get(i),
                    "non increasing strike spreads at position " + i);
        }
        QL.require(!volSpreads_.isEmpty(), "empty vol spreads matrix");
        QL.require(nOptionTenors_ * nSwapTenors_ == volSpreads_.size(),
                "mismatch between number of option tenors * swap tenors (" + (nOptionTenors_ * nSwapTenors_)
                        + ") and number of rows (" + volSpreads_.size() + ")");
        for ( int i = 0; i < volSpreads_.size(); ++i ) {
            QL.require(nStrikes_ == volSpreads_.get(i).size(),
                    "mismatch between number of strikes (" + nStrikes_ + ") and number of columns (" + volSpreads_.get(
                            i).size() + ") in row " + (i + 1));
        }

        atmVol_.addObserver(this);
        atmVol_.currentLink().enableExtrapolation();

        if ( swapIndexBase_ != null ) {
            swapIndexBase_.addObserver(this);
        }
        if ( shortSwapIndexBase_ != null ) {
            shortSwapIndexBase_.addObserver(this);
        }

        QL.require(shortSwapIndexBase_.tenor().le(swapIndexBase_.tenor()),
                "short index tenor (" + shortSwapIndexBase_.tenor() + ") is not less or equal than index tenor ("
                        + swapIndexBase_.tenor() + ")");

        registerWithVolatilitySpread();
    }

    //
    // observer wiring
    //

    /**
     * Mirrors C++ {@code registerWithVolatilitySpread()}: register as an observer of every Quote in the spreads grid.
     */
    protected final void registerWithVolatilitySpread() {
        for ( int i = 0; i < nStrikes_; i++ ) {
            for ( int j = 0; j < nOptionTenors_; j++ ) {
                for ( int k = 0; k < nSwapTenors_; k++ ) {
                    final Handle< Quote > q = volSpreads_.get(j * nSwapTenors_ + k).get(i);
                    if ( q != null ) {
                        q.addObserver(this);
                    }
                }
            }
        }
    }

    //
    // TermStructure interface
    //
    //   Reference date / day counter / calendar are inherited from
    //   SwaptionVolatilityDiscrete (which copied them from
    //   atmVolStructure.calendar()/dayCounter()/businessDayConvention()
    //   in the super ctor). maxDate() falls through to the inherited
    //   "last option date" implementation. We do not override referenceDate()
    //   here because the base AbstractTermStructure already re-resolves it
    //   against Settings.evaluationDate() (matching C++ behaviour).
    //

    @Override
    public Date maxDate() {
        return atmVol_.currentLink().maxDate();
    }

    //
    // VolatilityTermStructure interface
    //

    @Override
    public double minStrike() {
        return -org.jquantlib.math.Constants.QL_MAX_REAL;
    }

    @Override
    public double maxStrike() {
        return org.jquantlib.math.Constants.QL_MAX_REAL;
    }

    @Override
    public Period maxSwapTenor() {
        return atmVol_.currentLink().maxSwapTenor();
    }

    //
    // Other inspectors
    //

    /**
     * Returns the ATM strike for a given option date and swap tenor. Mirrors C++
     * {@code SwaptionVolatilityCube::atmStrike(Date, Period)}.
     */
    public double atmStrike(final Date optionD, final Period swapTenor) {
        final SwapIndex indexToUse = swapTenor.gt(shortSwapIndexBase_.tenor()) ? swapIndexBase_ : shortSwapIndexBase_;
        return indexToUse.clone(swapTenor).fixing(optionD);
    }

    /**
     * Returns the ATM strike for a given option tenor and swap tenor.
     */
    public double atmStrike(final Period optionTenor, final Period swapTenor) {
        final Date optionDate = optionDateFromTenor(optionTenor);
        return atmStrike(optionDate, swapTenor);
    }

    public Handle< SwaptionVolatilityStructure > atmVol() {
        return atmVol_;
    }

    public List< Double > strikeSpreads() {
        return strikeSpreads_;
    }

    public List< List< Handle< Quote > > > volSpreads() {
        return volSpreads_;
    }

    public SwapIndex swapIndexBase() {
        return swapIndexBase_;
    }

    public SwapIndex shortSwapIndexBase() {
        return shortSwapIndexBase_;
    }

    public boolean vegaWeightedSmileFit() {
        return vegaWeightedSmileFit_;
    }

    @Override
    public VolatilityType volatilityType() {
        return atmVol_.currentLink().volatilityType();
    }

    /**
     * Minimum number of strikes the spreads grid must carry (defaults to 2; SABR-style derived classes may override).
     */
    protected int requiredNumberOfStrikes() {
        return 2;
    }

    //
    // SwaptionVolatilityStructure required overrides
    //

    @Override
    public double volatilityImpl(final double optionTime, final double swapLength, final double strike) {
        return smileSectionImpl(optionTime, swapLength).volatility(strike);
    }

    @Override
    protected double volatilityImpl(final Date optionDate, final Period swapTenor, final double strike) {
        return smileSectionImpl(optionDate, swapTenor).volatility(strike);
    }

    @Override
    public double blackVariance(final double optionTime, final double swapLength, final double strike,
            final boolean extrapolate) {
        final double v = volatility(optionTime, swapLength, strike, extrapolate);
        return v * v * optionTime;
    }

    /**
     * Shift at the given (optionTime, swapLength); forwards to the ATM surface (C++ {@code shiftImpl}).
     */
    protected double shiftImpl(final double optionTime, final double swapLength) {
        if (atmVol_.currentLink() instanceof SwaptionVolatilityMatrix m) {
            return m.shift(optionTime, swapLength, true);
        }
        return atmVol_.currentLink().shift();
    }

    //
    // LazyObject hook
    //

    @Override
    protected void performCalculations() {
        QL.require(nStrikes_ >= requiredNumberOfStrikes(),
                "too few strikes (" + nStrikes_ + ") — required are at least " + requiredNumberOfStrikes());
        super.performCalculations();
    }
}
