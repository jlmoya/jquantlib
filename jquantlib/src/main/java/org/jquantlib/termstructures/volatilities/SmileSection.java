/*
 Copyright (C) 2009 Ueli Hofstetter
 Copyright (C) 2009 John Nichol

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
 Copyright (C) 2006 Mario Pucci

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

package org.jquantlib.termstructures.volatilities;

import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.Option;
import org.jquantlib.math.Constants;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.pricingengines.BlackFormula;
import org.jquantlib.time.Date;
import org.jquantlib.util.DefaultObservable;
import org.jquantlib.util.Observable;
import org.jquantlib.util.Observer;

/**
 * Smile section base class
 *
 * @author Ueli Hofstetter
 * @author John Nichol
 */
public abstract class SmileSection implements Observer, Observable {

    private Date exerciseDate_;
    private Date reference_;

    private final DayCounter dc_;
    private final boolean isFloating_;

    /** Mirrors C++ {@code volatilityType_}; defaults to ShiftedLognormal. */
    private final VolatilityType volatilityType_;
    /** Mirrors C++ {@code shift_}; defaults to 0.0. */
    private final double shift_;

    protected double exerciseTime_;


    //
    // public constructors
    //

    public SmileSection(
            final Date d,
            final DayCounter dc,
            final Date referenceDate) {
        this(d, dc, referenceDate, VolatilityType.ShiftedLognormal, 0.0);
    }

    public SmileSection(
            final Date d,
            final DayCounter dc,
            final Date referenceDate,
            final VolatilityType type,
            final double shift) {
    	exerciseDate_ = d;
    	dc_ = dc;
        volatilityType_ = type;
        shift_ = shift;
    	isFloating_ = referenceDate.isNull();
    	if (isFloating_) {
    		final Settings settings = new Settings();
    		settings.evaluationDate().addObserver(this);
    		reference_ = settings.evaluationDate();
    	} else
            reference_ = referenceDate;
    	initializeExerciseTime();
    }

    public SmileSection(
            final /* @Time */ double exerciseTime,
            final DayCounter dc) {
        this(exerciseTime, dc, VolatilityType.ShiftedLognormal, 0.0);
    }

    public SmileSection(
            final /* @Time */ double exerciseTime,
            final DayCounter dc,
            final VolatilityType type,
            final double shift) {
    	isFloating_ = false;
    	dc_ = dc;
        volatilityType_ = type;
        shift_ = shift;
    	exerciseTime_ = exerciseTime;
    	QL.require(exerciseTime_>=0.0,
    			"expiry time must be positive: " +
    			exerciseTime_ + " not allowed");
    }


    //
    // abstract methods
    //

    public abstract double minStrike();

    public abstract double maxStrike();

    public abstract double atmLevel();

    protected abstract /* @Real */ double volatilityImpl(/* @Rate */ double strike);


    //
    // public methods
    //

    public double variance() {
    	return variance(Constants.NULL_REAL);
    }

    public double volatility() {
    	return volatility(Constants.NULL_REAL);
    }

    public void initializeExerciseTime() {
        QL.require(exerciseDate_.ge(reference_),
                "expiry date (" + exerciseDate_ +
                ") must be greater than reference date (" +
                reference_ + ")");
     exerciseTime_ = dc_.yearFraction(reference_, exerciseDate_);

    }

    public double variance(double strike) {
        if (Double.isNaN(strike))
            strike = atmLevel();
        return varianceImpl(strike);
    }

    public double volatility(double strike) {
        if (Double.isNaN(strike))
            strike = atmLevel();
        return volatilityImpl(strike);
    }

    public Date exerciseDate() {
        return exerciseDate_;
    }

    public double exerciseTime() {
        return exerciseTime_;
    }

    public DayCounter dayCounter() {
        return dc_;
    }

    /**
     * Volatility type (ShiftedLognormal or Normal).
     * Mirrors C++ {@code SmileSection::volatilityType()}.
     */
    public VolatilityType volatilityType() {
        return volatilityType_;
    }

    /**
     * Displacement shift (zero for unshifted lognormal / normal).
     * Mirrors C++ {@code SmileSection::shift()}.
     */
    public double shift() {
        return shift_;
    }

    /**
     * Call or put option price using this smile section.
     * Mirrors C++ {@code SmileSection::optionPrice}.
     *
     * <p>For ShiftedLognormal: uses Black formula. For Normal: uses Bachelier formula.
     * Discount defaults to 1.0 (undiscounted).
     */
    public double optionPrice(final double strike, final Option.Type type, final double discount) {
        final double atm = atmLevel();
        QL.require(atm != Constants.NULL_REAL,
                "smile section must provide atm level to compute option price");
        if (volatilityType_ == VolatilityType.ShiftedLognormal) {
            // Mirror C++: if strike == -shift (at lower barrier) use default stddev=0.2
            final double stddev = Math.abs(strike + shift_) < Constants.QL_EPSILON
                    ? 0.2
                    : Math.sqrt(variance(strike));
            // When strike+shift == 0 (at-barrier), replicate C++ blackFormula inline:
            // strike_shifted = 0 → formula returns fwd*discount for Call, 0 for Put.
            if (Math.abs(strike + shift_) < Constants.QL_EPSILON) {
                final double fwd = atm + shift_;
                return type == Option.Type.Call ? fwd * discount : 0.0;
            }
            return BlackFormula.blackFormula(type, strike, atm, stddev, discount, shift_);
        } else {
            return BlackFormula.bachelierBlackFormula(type, strike, atm, Math.sqrt(variance(strike)), discount);
        }
    }

    /**
     * Convenience overload: undiscounted option price (discount=1.0).
     */
    public double optionPrice(final double strike, final Option.Type type) {
        return optionPrice(strike, type, 1.0);
    }

    /**
     * Digital call/put option price computed via a centred finite difference
     * on the call price function, mirroring C++
     * {@code SmileSection::digitalOptionPrice}:
     * <pre>
     *   m  = (volatilityType == ShiftedLognormal ? -shift() : -infinity)
     *   kl = max(strike - gap/2, m)
     *   kr = kl + gap
     *   D  = sign(type) * (P(kl, type) - P(kr, type)) / gap
     * </pre>
     * where {@code P(strike, type)} is {@link #optionPrice(double, Option.Type, double)}.
     *
     * @param strike   strike rate
     * @param type     {@link Option.Type#Call} or {@link Option.Type#Put}
     * @param discount discount factor applied to the call/put leg
     * @param gap      finite-difference gap (e.g. 1e-5)
     * @return digital option price
     */
    public double digitalOptionPrice(
            final double strike, final Option.Type type,
            final double discount, final double gap) {
        final double m = (volatilityType_ == VolatilityType.ShiftedLognormal)
                ? -shift_
                : -Double.MAX_VALUE;
        final double kl = Math.max(strike - 0.5 * gap, m);
        final double kr = kl + gap;
        final double sign = (type == Option.Type.Call) ? 1.0 : -1.0;
        return sign * (optionPrice(kl, type, discount) - optionPrice(kr, type, discount)) / gap;
    }

    /**
     * Convenience overload: undiscounted ({@code discount=1.0}) digital with
     * {@code gap=1e-5} (matches C++ default {@code marketRateAccuracy_} class).
     */
    public double digitalOptionPrice(final double strike, final Option.Type type) {
        return digitalOptionPrice(strike, type, 1.0, 1.0e-5);
    }

    //
    // protected methods
    //

    protected /* @Real */ double varianceImpl(/* @Rate */ final double strike) {
    	/* @Volatility */ final double v = volatilityImpl(strike);
        return v*v*exerciseTime();

    }


    //
    // implements Observer
    //

    @Override
    public void update() {
        if (isFloating_) {
            final Settings settings = new Settings();
            reference_ = settings.evaluationDate();
            initializeExerciseTime();
        }

    }

    //
    // implements Observable
    //

    /**
     * Implements multiple inheritance via delegate pattern to an inner class
     *
     * @see Observable
     * @see DefaultObservable
     */
    private final DefaultObservable delegatedObservable = new DefaultObservable(this);

    @Override
    public void addObserver(final Observer observer) {
        delegatedObservable.addObserver(observer);
    }

    @Override
    public int countObservers() {
        return delegatedObservable.countObservers();
    }

    @Override
    public void deleteObserver(final Observer observer) {
        delegatedObservable.deleteObserver(observer);
    }

    @Override
    public void notifyObservers() {
        delegatedObservable.notifyObservers();
    }

    @Override
    public void notifyObservers(final Object arg) {
        delegatedObservable.notifyObservers(arg);
    }

    @Override
    public void deleteObservers() {
        delegatedObservable.deleteObservers();
    }

    @Override
    public List<Observer> getObservers() {
        return delegatedObservable.getObservers();
    }

}
