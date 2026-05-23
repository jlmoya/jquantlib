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
 Copyright (C) 2010, 2011 Klaus Spanderen
 */
package org.jquantlib.instruments;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.BermudanExercise;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;

import java.util.ArrayList;
import java.util.List;

/**
 * Swing exercise: a Bermudan-style exercise that supports fractional-day granularity via an array of "seconds-into-day"
 * offsets associated with each exercise date.
 *
 * <p>Ported from C++ QuantLib v1.42.1
 * {@code ql/instruments/vanillaswingoption.hpp::SwingExercise}.</p>
 *
 * <p>A swing option may be exercised at a discrete set of date-times. The
 * {@code seconds} array encodes the time-of-day for each exercise date, so that two entries with the same {@link Date}
 * but different {@code seconds} count as distinct exercise opportunities.</p>
 *
 * @author Phase 5e.5b-CFC-d-164 port
 */
public final class SwingExercise extends BermudanExercise {

    private static final int SEC_PER_DAY = 24 * 3600;

    private final int[] seconds_;

    /**
     * Construct from an explicit list of {@link Date}s. If {@code seconds} is {@code null} or empty, defaults to zero
     * seconds for every date.
     */
    public SwingExercise(final Date[] dates, final int[] seconds) {
        super(dates);
        if ( seconds == null || seconds.length == 0 ) {
            this.seconds_ = new int[dates.length];
        } else {
            QL.require(dates.length == seconds.length, "dates and seconds must have the same size");
            this.seconds_ = seconds.clone();
        }
        for ( int i = 0; i < dates.length; ++i ) {
            QL.require(this.seconds_[i] < SEC_PER_DAY, "a date can not have more than 24*3600 seconds");
            if ( i > 0 ) {
                final Date prev = dates[i - 1];
                final Date curr = dates[i];
                QL.require(prev.lt(curr) || (prev.eq(curr) && this.seconds_[i - 1] < this.seconds_[i]),
                        "date times must be sorted");
            }
        }
    }

    /**
     * Convenience constructor — exercise dates only, all seconds default to zero.
     */
    public SwingExercise(final Date[] dates) {
        this(dates, null);
    }

    /**
     * Construct an evenly-spaced grid from {@code from} to {@code to} (inclusive) with a step size of
     * {@code stepSizeSecs} seconds. Each 24-hour rollover advances the date by one day and resets the seconds offset
     * modulo {@code 86400}.
     */
    public SwingExercise(final Date from, final Date to, final int stepSizeSecs) {
        this(createDateTimesDates(from, to, stepSizeSecs), createDateTimesSeconds(from, to, stepSizeSecs));
    }

    /**
     * Build the dates array for the {@code (from, to, stepSizeSecs)} constructor. Mirrors the C++
     * {@code createDateTimes} helper.
     */
    private static Date[] createDateTimesDates(final Date from, final Date to, final int stepSizeSecs) {
        final List< Date > dates = new ArrayList<>();
        Date iterDate = from;
        int iterStepSize = 0;
        while ( iterDate.le(to) ) {
            dates.add(iterDate);
            iterStepSize += stepSizeSecs;
            if ( iterStepSize >= SEC_PER_DAY ) {
                iterDate = iterDate.add(1);
                iterStepSize %= SEC_PER_DAY;
            }
        }
        return dates.toArray(new Date[0]);
    }

    /**
     * Build the seconds array for the {@code (from, to, stepSizeSecs)} constructor. Must produce the exact same
     * iteration sequence as {@link #createDateTimesDates(Date, Date, int)}.
     */
    private static int[] createDateTimesSeconds(final Date from, final Date to, final int stepSizeSecs) {
        final List< Integer > secs = new ArrayList<>();
        Date iterDate = from;
        int iterStepSize = 0;
        while ( iterDate.le(to) ) {
            secs.add(iterStepSize);
            iterStepSize += stepSizeSecs;
            if ( iterStepSize >= SEC_PER_DAY ) {
                iterDate = iterDate.add(1);
                iterStepSize %= SEC_PER_DAY;
            }
        }
        final int[] out = new int[secs.size()];
        for ( int i = 0; i < out.length; ++i ) {
            out[i] = secs.get(i);
        }
        return out;
    }

    /**
     * Seconds-into-day for each exercise date (defensive copy).
     */
    public int[] seconds() {
        return seconds_.clone();
    }

    /**
     * Exercise times in year-fractions relative to {@code refDate}, with the seconds-into-day offset applied as a
     * linear fraction of the year-fraction of the following calendar day.
     */
    public double[] exerciseTimes(final DayCounter dc, final Date refDate) {
        final List< Date > ds = dates();
        final double[] times = new double[ds.size()];
        for ( int i = 0; i < ds.size(); ++i ) {
            double t = dc.yearFraction(refDate, ds.get(i));
            final double dt = dc.yearFraction(refDate, ds.get(i).add(new Period(1, TimeUnit.Days))) - t;
            t += dt * seconds_[i] / (24.0 * 3600.0);
            QL.require(t >= 0.0, "exercise dates must not contain past date");
            times[i] = t;
        }
        return times;
    }
}
