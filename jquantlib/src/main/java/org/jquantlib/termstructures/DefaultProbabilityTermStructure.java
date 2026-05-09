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
 Copyright (C) 2008 Roland Lichters
 Copyright (C) 2008 Chris Kenyon
 Copyright (C) 2008 StatPro Italia srl
 Copyright (C) 2009 Ferdinando Ametrano

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.termstructures;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.lang.annotation.Natural;
import org.jquantlib.lang.annotation.Rate;
import org.jquantlib.lang.annotation.Time;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;

/**
 * Default probability term structure — Java port of QuantLib v1.42.1
 * {@code DefaultProbabilityTermStructure}
 * ({@code ql/termstructures/defaulttermstructure.{hpp,cpp}}).
 *
 * <p>Abstract base for credit term structures. Concrete implementations
 * implement the {@code survivalProbabilityImpl(Time)} and
 * {@code defaultDensityImpl(Time)} hooks; the public surface gives the standard
 * survival/default-probability/default-density/hazard-rate accessors with
 * date or time inputs.
 *
 * <p>Mirrors C++ jump-quote handling: optional time-of-default jumps reduce
 * survival probability multiplicatively at each jump time.
 *
 * @see HazardRateStructure
 * @see SurvivalProbabilityStructure
 */
public abstract class DefaultProbabilityTermStructure extends AbstractTermStructure {

    //
    // private fields
    //

    private final List<Handle<Quote>> jumps;
    private final List<Date> jumpDates;
    private final List<Double> jumpTimes;
    private final int nJumps;
    private Date latestReference;

    //
    // constructors
    //

    public DefaultProbabilityTermStructure(final DayCounter dc) {
        this(dc, new ArrayList<Handle<Quote>>(), new ArrayList<Date>());
    }

    public DefaultProbabilityTermStructure(
            final DayCounter dc,
            final List<Handle<Quote>> jumps,
            final List<Date> jumpDates) {
        super(dc);
        this.jumps = (jumps != null) ? new ArrayList<>(jumps) : new ArrayList<Handle<Quote>>();
        this.jumpDates = (jumpDates != null) ? new ArrayList<>(jumpDates) : new ArrayList<Date>();
        this.jumpTimes = new ArrayList<>(this.jumpDates.size());
        for (int i = 0; i < this.jumpDates.size(); ++i) {
            this.jumpTimes.add(0.0);
        }
        this.nJumps = this.jumps.size();
        setJumps();
        for (int i = 0; i < nJumps; ++i) {
            this.jumps.get(i).addObserver(this);
        }
    }

    public DefaultProbabilityTermStructure(
            final Date referenceDate,
            final Calendar cal,
            final DayCounter dc) {
        this(referenceDate, cal, dc, new ArrayList<Handle<Quote>>(), new ArrayList<Date>());
    }

    public DefaultProbabilityTermStructure(
            final Date referenceDate,
            final Calendar cal,
            final DayCounter dc,
            final List<Handle<Quote>> jumps,
            final List<Date> jumpDates) {
        super(referenceDate, cal, dc);
        this.jumps = (jumps != null) ? new ArrayList<>(jumps) : new ArrayList<Handle<Quote>>();
        this.jumpDates = (jumpDates != null) ? new ArrayList<>(jumpDates) : new ArrayList<Date>();
        this.jumpTimes = new ArrayList<>(this.jumpDates.size());
        for (int i = 0; i < this.jumpDates.size(); ++i) {
            this.jumpTimes.add(0.0);
        }
        this.nJumps = this.jumps.size();
        setJumps();
        for (int i = 0; i < nJumps; ++i) {
            this.jumps.get(i).addObserver(this);
        }
    }

    public DefaultProbabilityTermStructure(
            final @Natural int settlementDays,
            final Calendar cal,
            final DayCounter dc) {
        this(settlementDays, cal, dc, new ArrayList<Handle<Quote>>(), new ArrayList<Date>());
    }

    public DefaultProbabilityTermStructure(
            final @Natural int settlementDays,
            final Calendar cal,
            final DayCounter dc,
            final List<Handle<Quote>> jumps,
            final List<Date> jumpDates) {
        super(settlementDays, cal, dc);
        this.jumps = (jumps != null) ? new ArrayList<>(jumps) : new ArrayList<Handle<Quote>>();
        this.jumpDates = (jumpDates != null) ? new ArrayList<>(jumpDates) : new ArrayList<Date>();
        this.jumpTimes = new ArrayList<>(this.jumpDates.size());
        for (int i = 0; i < this.jumpDates.size(); ++i) {
            this.jumpTimes.add(0.0);
        }
        this.nJumps = this.jumps.size();
        setJumps();
        for (int i = 0; i < nJumps; ++i) {
            this.jumps.get(i).addObserver(this);
        }
    }

    //
    // private helpers — mirror C++ DefaultProbabilityTermStructure::setJumps()
    //

    private void setJumps() {
        if (jumpDates.isEmpty() && !jumps.isEmpty()) {
            // turn-of-year dates
            jumpDates.clear();
            jumpTimes.clear();
            final int y = referenceDate().year();
            for (int i = 0; i < nJumps; ++i) {
                jumpDates.add(new Date(31, Month.December, y + i));
                jumpTimes.add(0.0);
            }
        } else {
            QL.require(jumpDates.size() == nJumps,
                    "mismatch between number of jumps (" + nJumps +
                    ") and jump dates (" + jumpDates.size() + ")");
        }
        for (int i = 0; i < nJumps; ++i) {
            jumpTimes.set(i, timeFromReference(jumpDates.get(i)));
        }
        latestReference = referenceDate();
    }

    //
    // Survival probabilities
    //

    public double survivalProbability(final Date d, final boolean extrapolate) {
        return survivalProbability(timeFromReference(d), extrapolate);
    }

    public double survivalProbability(final Date d) {
        return survivalProbability(d, false);
    }

    public double survivalProbability(final @Time double t, final boolean extrapolate) {
        checkRange(t, extrapolate);

        if (!jumps.isEmpty()) {
            double jumpEffect = 1.0;
            for (int i = 0; i < nJumps && jumpTimes.get(i) < t; ++i) {
                final Handle<Quote> q = jumps.get(i);
                QL.require(!q.empty() && q.currentLink().isValid(),
                        "invalid jump quote at index " + i);
                final double thisJump = q.currentLink().value();
                QL.require(thisJump > 0.0 && thisJump <= 1.0,
                        "invalid jump value at index " + i + ": " + thisJump);
                jumpEffect *= thisJump;
            }
            return jumpEffect * survivalProbabilityImpl(t);
        }
        return survivalProbabilityImpl(t);
    }

    public double survivalProbability(final @Time double t) {
        return survivalProbability(t, false);
    }

    //
    // Default probabilities
    //

    public double defaultProbability(final Date d, final boolean extrapolate) {
        return 1.0 - survivalProbability(d, extrapolate);
    }

    public double defaultProbability(final Date d) {
        return defaultProbability(d, false);
    }

    public double defaultProbability(final @Time double t, final boolean extrapolate) {
        return 1.0 - survivalProbability(t, extrapolate);
    }

    public double defaultProbability(final @Time double t) {
        return defaultProbability(t, false);
    }

    public double defaultProbability(final Date d1, final Date d2, final boolean extrapolate) {
        QL.require(d1.le(d2),
                "initial date (" + d1 + ") later than final date (" + d2 + ")");
        final double p1 = d1.lt(referenceDate()) ? 0.0 : defaultProbability(d1, extrapolate);
        final double p2 = defaultProbability(d2, extrapolate);
        return p2 - p1;
    }

    public double defaultProbability(final Date d1, final Date d2) {
        return defaultProbability(d1, d2, false);
    }

    public double defaultProbability(final @Time double t1, final @Time double t2, final boolean extrapolate) {
        QL.require(t1 <= t2,
                "initial time (" + t1 + ") later than final time (" + t2 + ")");
        final double p1 = (t1 < 0.0) ? 0.0 : defaultProbability(t1, extrapolate);
        final double p2 = defaultProbability(t2, extrapolate);
        return p2 - p1;
    }

    public double defaultProbability(final @Time double t1, final @Time double t2) {
        return defaultProbability(t1, t2, false);
    }

    //
    // Default densities
    //

    public double defaultDensity(final Date d, final boolean extrapolate) {
        return defaultDensity(timeFromReference(d), extrapolate);
    }

    public double defaultDensity(final Date d) {
        return defaultDensity(d, false);
    }

    public double defaultDensity(final @Time double t, final boolean extrapolate) {
        checkRange(t, extrapolate);
        return defaultDensityImpl(t);
    }

    public double defaultDensity(final @Time double t) {
        return defaultDensity(t, false);
    }

    //
    // Hazard rates
    //

    public @Rate double hazardRate(final Date d, final boolean extrapolate) {
        return hazardRate(timeFromReference(d), extrapolate);
    }

    public @Rate double hazardRate(final Date d) {
        return hazardRate(d, false);
    }

    public @Rate double hazardRate(final @Time double t, final boolean extrapolate) {
        checkRange(t, extrapolate);
        return hazardRateImpl(t);
    }

    public @Rate double hazardRate(final @Time double t) {
        return hazardRate(t, false);
    }

    //
    // Jump inspectors
    //

    public List<Date> jumpDates() {
        return new ArrayList<>(jumpDates);
    }

    public List<Double> jumpTimes() {
        return new ArrayList<>(jumpTimes);
    }

    //
    // Observer interface — recompute jump times when reference date moves
    //

    @Override
    public void update() {
        super.update();
        if (referenceDate() == null) {
            return;
        }
        if (latestReference == null || !referenceDate().eq(latestReference)) {
            setJumps();
        }
    }

    //
    // calculation hooks (must be implemented by derived classes)
    //

    protected abstract double survivalProbabilityImpl(@Time double t);

    protected abstract double defaultDensityImpl(@Time double t);

    /**
     * Default hazard-rate implementation based on survival probability and
     * default density. Mirrors C++ default {@code hazardRateImpl(Time)}
     * inline definition: if {@code S(t) == 0} returns 0; otherwise returns
     * {@code defaultDensity(t,true) / S}.
     *
     * <p>Derived classes (e.g. {@link HazardRateStructure}) may override.
     */
    protected @Rate double hazardRateImpl(final @Time double t) {
        final double S = survivalProbability(t, true);
        return (S == 0.0) ? 0.0 : defaultDensity(t, true) / S;
    }
}
