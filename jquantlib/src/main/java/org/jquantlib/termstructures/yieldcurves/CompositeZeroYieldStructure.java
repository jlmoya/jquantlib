/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2000, 2001, 2002, 2003 RiskMap srl
 Copyright (C) 2007, 2008 StatPro Italia srl
 Copyright (C) 2017 Francois Botha

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
*/

package org.jquantlib.termstructures.yieldcurves;

import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.InterestRate;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;

/**
 * Composite zero term structure: combines two zero-yield curves by a user-supplied
 * binary function (e.g. addition for a spread curve, subtraction for a difference
 * curve).
 * <p>
 * Port of C++ QuantLib v1.42.1
 * {@code ql/termstructures/yield/compositezeroyieldstructure.hpp}. The C++ template
 * parameter {@code BinaryFunction} is realised in Java as the SAM interface
 * {@link BinaryFunction}.
 * <p>
 * The composite curve delegates all term-structure metadata (day-counter, calendar,
 * settlement days, reference date, max date / max time) to the first curve, and
 * blends the zero-rate at each time via the binary function before equivalent-rate
 * conversion to continuous compounding (matching C++ {@code zeroYieldImpl}).
 *
 * @author JQuantLib migration team
 * @category termstructures
 */
public class CompositeZeroYieldStructure extends ZeroYieldStructure {

    /**
     * SAM (single-abstract-method) interface representing the C++ template
     * parameter {@code BinaryFunction} (a binary function of two
     * {@code Real}s, e.g. addition for spread, subtraction for difference).
     */
    public interface BinaryFunction {
        double apply(double a, double b);
    }

    private final Handle< YieldTermStructure > curve1_;
    private final Handle< YieldTermStructure > curve2_;
    private final BinaryFunction f_;
    private final Compounding comp_;
    private final Frequency freq_;

    public CompositeZeroYieldStructure(final Handle< YieldTermStructure > h1, final Handle< YieldTermStructure > h2,
            final BinaryFunction f) {
        this(h1, h2, f, Compounding.Continuous, Frequency.NoFrequency);
    }

    public CompositeZeroYieldStructure(final Handle< YieldTermStructure > h1, final Handle< YieldTermStructure > h2,
            final BinaryFunction f, final Compounding comp) {
        this(h1, h2, f, comp, Frequency.NoFrequency);
    }

    public CompositeZeroYieldStructure(final Handle< YieldTermStructure > h1, final Handle< YieldTermStructure > h2,
            final BinaryFunction f, final Compounding comp, final Frequency freq) {
        super();
        this.curve1_ = h1;
        this.curve2_ = h2;
        this.f_ = f;
        this.comp_ = comp;
        this.freq_ = freq;

        if ( !curve1_.empty() && !curve2_.empty() ) {
            if ( curve1_.currentLink().allowsExtrapolation() && curve2_.currentLink().allowsExtrapolation() ) {
                enableExtrapolation();
            } else {
                disableExtrapolation();
            }
        }

        this.curve1_.addObserver(this);
        this.curve2_.addObserver(this);
    }

    //
    // YieldTermStructure interface
    //

    @Override
    public DayCounter dayCounter() {
        return curve1_.currentLink().dayCounter();
    }

    @Override
    public Calendar calendar() {
        return curve1_.currentLink().calendar();
    }

    @Override
    public int settlementDays() {
        return curve1_.currentLink().settlementDays();
    }

    @Override
    public Date referenceDate() {
        return curve1_.currentLink().referenceDate();
    }

    @Override
    public Date maxDate() {
        return curve1_.currentLink().maxDate();
    }

    @Override
    public double maxTime() {
        return curve1_.currentLink().maxTime();
    }

    //
    // Observer interface
    //

    @Override
    public void update() {
        if ( !curve1_.empty() && !curve2_.empty() ) {
            super.update();
            if ( curve1_.currentLink().allowsExtrapolation() && curve2_.currentLink().allowsExtrapolation() ) {
                enableExtrapolation();
            } else {
                disableExtrapolation();
            }
        } else {
            // The implementation inherited from YieldTermStructure asks for our
            // reference date, which we don't have since the original curves are
            // still not set. Therefore, we skip over that and just call the
            // base-class observer behavior (notify observers).
            super.update();
        }
    }

    //
    // ZeroYieldStructure interface
    //

    @Override
    protected double zeroYieldImpl(final double t) {
        // Mirror C++ v1.42.1 compositezeroyieldstructure.hpp:131-141:
        // sample each underlying curve at the same (comp_, freq_) basis,
        // blend rates via f_, then convert the composite InterestRate to
        // continuous/NoFrequency at time t.
        final double r1 = curve1_.currentLink().zeroRate(t, comp_, freq_, true).rate();
        final InterestRate r2 = curve2_.currentLink().zeroRate(t, comp_, freq_, true);
        final InterestRate compositeRate = new InterestRate(f_.apply(r1, r2.rate()), dayCounter(), comp_, freq_);
        return compositeRate.equivalentRate(t, Compounding.Continuous, Frequency.NoFrequency).rate();
    }
}
