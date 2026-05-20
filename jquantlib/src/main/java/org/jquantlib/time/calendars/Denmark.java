/*
 Copyright (C) 2008

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

package org.jquantlib.time.calendars;

import org.jquantlib.lang.annotation.QualityAssurance;
import org.jquantlib.lang.annotation.QualityAssurance.Quality;
import org.jquantlib.lang.annotation.QualityAssurance.Version;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Weekday;

import static org.jquantlib.time.Month.*;

/**
 * Danish calendar Holidays:
 *  <ul>
 *  <li>Saturdays</li>
 *  <li>Sundays</li>
 *  <li>Maunday Thursday</li>
 *  <li>Good Friday</li>
 *  <li>Easter Monday</li>
 *  <li>General Prayer Day, 25 days after Easter Monday (until 2023)</li>
 *  <li>Ascension</li>
 *  <li>Day after Ascension (since 2009)</li>
 *  <li>Whit (Pentecost) Monday </li>
 *  <li>New Year's Day, JANUARY 1st</li>
 *  <li>Constitution Day, June 5th</li>
 *  <li>Christmas Eve, December 24th</li>
 *  <li>Christmas, December 25th</li>
 *  <li>Boxing Day, December 26th</li>
 *  <li>New Year's Eve, December 31st</li>
 *  </ul>
 *
 * <p>Aligned to C++ QuantLib v1.42.1
 * ({@code ql/time/calendars/denmark.cpp}). Compared to the legacy 2008-era
 * JQuantLib rules, the following v1.42.1 changes are folded in:
 * <ul>
 *   <li>"Day after Ascension" (em+39) is observed for all years
 *       {@code y >= 2009} (previously only the one-off 22 May 2009 was
 *       hardcoded);</li>
 *   <li>"General Prayer Day" (em+25) is dropped from 2024 onwards
 *       ({@code y <= 2023}) per the 2023 Danish reform;</li>
 *   <li>24 December (Christmas Eve) and 31 December (New Year's Eve) are
 *       always observed as closed (previously only the 2007/2008/2009 special
 *       cases).</li>
 * </ul>
 *
 * @author Jia Jia
 * @author Zahid Hussain
 */

@QualityAssurance( quality = Quality.Q3_DOCUMENTATION, version = Version.V097, reviewers = { "Zahid Hussain" } )

public class Denmark extends Calendar {

    //
    // public constructors
    //

    public Denmark() {
        impl = new Impl();
    }

    //
    // private final inner classes
    //

    private final class Impl extends WesternImpl {

        @Override
        public String name() {
            return "Denmark";
        }

        @Override
        public boolean isBusinessDay(final Date date) {
            final Weekday w = date.weekday();
            final int d = date.dayOfMonth(), dd = date.dayOfYear();
            final Month m = date.month();
            final int y = date.year();
            final int em = easterMonday(y);
            return !isWeekend(w)
                    // Maunday Thursday
                    && (dd != em - 4)
                    // Good Friday
                    && (dd != em - 3)
                    // Easter Monday
                    && (dd != em)
                    // General Prayer Day (abolished from 2024)
                    && !(dd == em + 25 && y <= 2023)
                    // Ascension
                    && (dd != em + 38)
                    // Day after Ascension (since 2009)
                    && !(dd == em + 39 && y >= 2009)
                    // Whit Monday
                    && (dd != em + 49)
                    // New Year's Day
                    && (d != 1 || m != January)
                    // Constitution Day, June 5th
                    && (d != 5 || m != June)
                    // Christmas Eve, December 24th (always closed)
                    && (d != 24 || m != December)
                    // Christmas, December 25th
                    && (d != 25 || m != December)
                    // Boxing Day, December 26th
                    && (d != 26 || m != December)
                    // New Year's Eve, December 31st (always closed)
                    && (d != 31 || m != December);
        }
    }
}
