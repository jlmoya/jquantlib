/*
 Copyright (C) 2008 Renjith Nair

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
 Copyright (C) 2005, 2007, 2008, 2009, 2010, 2011 StatPro Italia srl
 Copyright (C) 2023, 2024 Skandinaviska Enskilda Banken AB (publ)

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <http://quantlib.org/license.shtml>.
 */

package org.jquantlib.time.calendars;

import org.jquantlib.lang.annotation.QualityAssurance;
import org.jquantlib.lang.annotation.QualityAssurance.Quality;
import org.jquantlib.lang.annotation.QualityAssurance.Version;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Weekday;

import static org.jquantlib.time.Month.*;

/**
 * Indian calendars Holidays for the National Stock Exchange (data from <http://www.nse-india.com/>):
 * <ul>
 * <li>Saturdays</li>
 * <li>Sundays</li>
 * <li>Republic Day, JANUARY 26th</li>
 * <li>Good Friday</li>
 * <li>Ambedkar Jayanti, April 14th</li>
 * <li>May Day, May 1st</li>
 * <li>Independence Day, August 15th</li>
 * <li>Gandhi Jayanti, October 2nd</li>
 * <li>Christmas, December 25th</li>
 * </ul>
 *
 * Other holidays for which no rule is given (data available for
 * 2005-2014 and 2019-2025):
 * <ul>
 * <li>Bakri Id</li>
 * <li>Moharram</li>
 * <li>Mahashivratri</li>
 * <li>Holi</li>
 * <li>Ram Navami</li>
 * <li>Mahavir Jayanti</li>
 * <li>Id-E-Milad</li>
 * <li>Maharashtra Day</li>
 * <li>Buddha Pournima</li>
 * <li>Ganesh Chaturthi</li>
 * <li>Dasara</li>
 * <li>Laxmi Puja</li>
 * <li>Bhaubeej</li>
 * <li>Ramzan Id</li>
 * <li>Guru Nanak Jayanti</li>
 * </ul>
 * in group calendars
 *
 * @author Renjith Nair
 * @author Zahid Hussain
 * @category calendars
 * @see <a href="http://www.nse-india.com/">National Stock Exchange of India</a>
 */

@QualityAssurance( quality = Quality.Q3_DOCUMENTATION, version = Version.V097, reviewers = { "Zahid Hussain" } )

public class India extends Calendar {

    public India() {
        this(Market.NSE);
    }

    //
    // public constructor
    //

    public India(final Market m) {
        switch ( m ) {
        case NSE:
            impl = new NseImpl();
            break;
        default:
            throw new LibraryException(UNKNOWN_MARKET);
        }
    }

    public enum Market {
        /**
         * National Stock Exchange
         */
        NSE
    }

    //
    // private final inner classes
    //

    private final class NseImpl extends WesternImpl {

        @Override
        public String name() {
            return "National Stock Exchange of India";
        }

        @Override
        public boolean isBusinessDay(final Date date) {
            final Weekday w = date.weekday();
            final int d = date.dayOfMonth();
            final Month m = date.month();
            final int y = date.year();
            final int dd = date.dayOfYear();
            final int em = easterMonday(y);

            if ( isWeekend(w)
                    // Republic Day
                    || (d == 26 && m == January)
                    // Good Friday
                    || (dd == em - 3)
                    // Ambedkar Jayanti
                    || (d == 14 && m == April)
                    // May Day
                    || (d == 1 && m == May)
                    // Independence Day
                    || (d == 15 && m == August)
                    // Gandhi Jayanti
                    || (d == 2 && m == October)
                    // Christmas
                    || (d == 25 && m == December) ) {
                return false;
            }
            if ( y == 2005 ) {
                // Moharram, Holi, Maharashtra Day, and Ramzan Id fall
                // on Saturday or Sunday in 2005
                if (// Bakri Id
                        (d == 21 && m == January)
                                // Ganesh Chaturthi
                                || (d == 7 && m == September)
                                // Dasara
                                || (d == 12 && m == October)
                                // Laxmi Puja
                                || (d == 1 && m == November)
                                // Bhaubeej
                                || (d == 3 && m == November)
                                // Guru Nanak Jayanti
                                || (d == 15 && m == November) ) {
                    return false;
                }
            }
            if ( y == 2006 ) {
                if (// Bakri Id
                        (d == 11 && m == January)
                                // Moharram
                                || (d == 9 && m == February)
                                // Holi
                                || (d == 15 && m == March)
                                // Ram Navami
                                || (d == 6 && m == April)
                                // Mahavir Jayanti
                                || (d == 11 && m == April)
                                // Maharashtra Day
                                || (d == 1 && m == May)
                                // Bhaubeej
                                || (d == 24 && m == October)
                                // Ramzan Id
                                || (d == 25 && m == October) ) {
                    return false;
                }
            }
            if ( y == 2007 ) {
                if (// Bakri Id
                        (d == 1 && m == January)
                                // Moharram
                                || (d == 30 && m == January)
                                // Mahashivratri
                                || (d == 16 && m == February)
                                // Ram Navami
                                || (d == 27 && m == March)
                                // Maharashtra Day
                                || (d == 1 && m == May)
                                // Buddha Pournima
                                || (d == 2 && m == May)
                                // Laxmi Puja
                                || (d == 9 && m == November)
                                // Bakri Id (again)
                                || (d == 21 && m == December) ) {
                    return false;
                }
            }
            if ( y == 2008 ) {
                if (// Mahashivratri
                        (d == 6 && m == March)
                                // Id-E-Milad
                                || (d == 20 && m == March)
                                // Mahavir Jayanti
                                || (d == 18 && m == April)
                                // Maharashtra Day
                                || (d == 1 && m == May)
                                // Buddha Pournima
                                || (d == 19 && m == May)
                                // Ganesh Chaturthi
                                || (d == 3 && m == September)
                                // Ramzan Id
                                || (d == 2 && m == October)
                                // Dasara
                                || (d == 9 && m == October)
                                // Laxmi Puja
                                || (d == 28 && m == October)
                                // Bhau bhij
                                || (d == 30 && m == October)
                                // Gurunanak Jayanti
                                || (d == 13 && m == November)
                                // Bakri Id
                                || (d == 9 && m == December) ) {
                    return false;
                }
            }
            if ( y == 2009 ) {
                if (// Moharram
                        (d == 8 && m == January)
                                // Mahashivratri
                                || (d == 23 && m == February)
                                // Id-E-Milad
                                || (d == 10 && m == March)
                                // Holi
                                || (d == 11 && m == March)
                                // Ram Navmi
                                || (d == 3 && m == April)
                                // Mahavir Jayanti
                                || (d == 7 && m == April)
                                // Maharashtra Day
                                || (d == 1 && m == May)
                                // Ramzan Id
                                || (d == 21 && m == September)
                                // Dasara
                                || (d == 28 && m == September)
                                // Bhau Bhij
                                || (d == 19 && m == October)
                                // Gurunanak Jayanti
                                || (d == 2 && m == November)
                                // Moharram (again)
                                || (d == 28 && m == December) ) {
                    return false;
                }
            }
            if ( y == 2010 ) {
                if (// New Year's Day
                        (d == 1 && m == January)
                                // Mahashivratri
                                || (d == 12 && m == February)
                                // Holi
                                || (d == 1 && m == March)
                                // Ram Navmi
                                || (d == 24 && m == March)
                                // Ramzan Id
                                || (d == 10 && m == September)
                                // Laxmi Puja
                                || (d == 5 && m == November)
                                // Bakri Id
                                || (d == 17 && m == November)
                                // Moharram
                                || (d == 17 && m == December) ) {
                    return false;
                }
            }
            if ( y == 2011 ) {
                if (// Mahashivratri
                        (d == 2 && m == March)
                                // Ram Navmi
                                || (d == 12 && m == April)
                                // Ramzan Id
                                || (d == 31 && m == August)
                                // Ganesh Chaturthi
                                || (d == 1 && m == September)
                                // Dasara
                                || (d == 6 && m == October)
                                // Laxmi Puja
                                || (d == 26 && m == October)
                                // Diwali - Balipratipada
                                || (d == 27 && m == October)
                                // Bakri Id
                                || (d == 7 && m == November)
                                // Gurunanak Jayanti
                                || (d == 10 && m == November)
                                // Moharram
                                || (d == 6 && m == December) ) {
                    return false;
                }
            }
            if ( y == 2012 ) {
                if (// Mahashivratri
                        (d == 20 && m == February)
                                // Holi
                                || (d == 8 && m == March)
                                // Mahavir Jayanti
                                || (d == 5 && m == April)
                                // Ramzan Id
                                || (d == 20 && m == August)
                                // Ganesh Chaturthi
                                || (d == 19 && m == September)
                                // Dasara
                                || (d == 24 && m == October)
                                // Diwali - Balipratipada
                                || (d == 14 && m == November)
                                // Gurunanak Jayanti
                                || (d == 28 && m == November) ) {
                    return false;
                }
            }
            if ( y == 2013 ) {
                if (// Holi
                        (d == 27 && m == March)
                                // Ram Navmi
                                || (d == 19 && m == April)
                                // Mahavir Jayanti
                                || (d == 24 && m == April)
                                // Ramzan Id
                                || (d == 9 && m == August)
                                // Ganesh Chaturthi
                                || (d == 9 && m == September)
                                // Bakri Id
                                || (d == 16 && m == October)
                                // Diwali - Balipratipada
                                || (d == 4 && m == November)
                                // Moharram
                                || (d == 14 && m == November) ) {
                    return false;
                }
            }
            if ( y == 2014 ) {
                if (// Mahashivratri
                        (d == 27 && m == February)
                                // Holi
                                || (d == 17 && m == March)
                                // Ram Navmi
                                || (d == 8 && m == April)
                                // Ramzan Id
                                || (d == 29 && m == July)
                                // Ganesh Chaturthi
                                || (d == 29 && m == August)
                                // Dasera
                                || (d == 3 && m == October)
                                // Bakri Id
                                || (d == 6 && m == October)
                                // Diwali - Balipratipada
                                || (d == 24 && m == October)
                                // Moharram
                                || (d == 4 && m == November)
                                // Gurunank Jayanti
                                || (d == 6 && m == November) ) {
                    return false;
                }
            }
            if ( y == 2019 ) {
                if (// Chatrapati Shivaji Jayanti
                        (d == 19 && m == February)
                                // Mahashivratri
                                || (d == 4 && m == March)
                                // Holi
                                || (d == 21 && m == March)
                                // Annual Bank Closing
                                || (d == 1 && m == April)
                                // Mahavir Jayanti
                                || (d == 17 && m == April)
                                // Parliamentary Elections
                                || (d == 29 && m == April)
                                // Ramzan Id
                                || (d == 5 && m == June)
                                // Bakri Id
                                || (d == 12 && m == August)
                                // Ganesh Chaturthi
                                || (d == 2 && m == September)
                                // Moharram
                                || (d == 10 && m == September)
                                // Dasera
                                || (d == 8 && m == October)
                                // General Assembly Elections in Maharashtra
                                || (d == 21 && m == October)
                                // Diwali - Balipratipada
                                || (d == 28 && m == October)
                                // Gurunank Jayanti
                                || (d == 12 && m == November) ) {
                    return false;
                }
            }
            if ( y == 2020 ) {
                if (// Chatrapati Shivaji Jayanti
                        (d == 19 && m == February)
                                // Mahashivratri
                                || (d == 21 && m == February)
                                // Holi
                                || (d == 10 && m == March)
                                // Gudi Padwa
                                || (d == 25 && m == March)
                                // Annual Bank Closing
                                || (d == 1 && m == April)
                                // Ram Navami
                                || (d == 2 && m == April)
                                // Mahavir Jayanti
                                || (d == 6 && m == April)
                                // Buddha Pournima
                                || (d == 7 && m == May)
                                // Ramzan Id
                                || (d == 25 && m == May)
                                // Id-E-Milad
                                || (d == 30 && m == October)
                                // Diwali - Balipratipada
                                || (d == 16 && m == November)
                                // Gurunank Jayanti
                                || (d == 30 && m == November) ) {
                    return false;
                }
            }
            if ( y == 2021 ) {
                if (// Chatrapati Shivaji Jayanti
                        (d == 19 && m == February)
                                // Mahashivratri
                                || (d == 11 && m == March)
                                // Holi
                                || (d == 29 && m == March)
                                // Gudi Padwa
                                || (d == 13 && m == April)
                                // Mahavir Jayanti
                                || (d == 14 && m == April)
                                // Ram Navami
                                || (d == 21 && m == April)
                                // Buddha Pournima
                                || (d == 26 && m == May)
                                // Bakri Id
                                || (d == 21 && m == July)
                                // Ganesh Chaturthi
                                || (d == 10 && m == September)
                                // Dasera
                                || (d == 15 && m == October)
                                // Id-E-Milad
                                || (d == 19 && m == October)
                                // Diwali - Balipratipada
                                || (d == 5 && m == November)
                                // Gurunank Jayanti
                                || (d == 19 && m == November) ) {
                    return false;
                }
            }
            if ( y == 2022 ) {
                if (// Mahashivratri
                        (d == 1 && m == March)
                                // Holi
                                || (d == 18 && m == March)
                                // Ramzan Id
                                || (d == 3 && m == May)
                                // Buddha Pournima
                                || (d == 16 && m == May)
                                // Ganesh Chaturthi
                                || (d == 31 && m == August)
                                // Dasera
                                || (d == 5 && m == October)
                                // Diwali - Balipratipada
                                || (d == 26 && m == October)
                                // Gurunank Jayanti
                                || (d == 8 && m == November) ) {
                    return false;
                }
            }
            if ( y == 2023 ) {
                if (// Holi
                        (d == 7 && m == March)
                                // Gudi Padwa
                                || (d == 22 && m == March)
                                // Ram Navami
                                || (d == 30 && m == March)
                                // Mahavir Jayanti
                                || (d == 4 && m == April)
                                // Buddha Pournima
                                || (d == 5 && m == May)
                                // Bakri Id
                                || (d == 29 && m == June)
                                // Parsi New year
                                || (d == 16 && m == August)
                                // Ganesh Chaturthi
                                || (d == 19 && m == September)
                                // Id-E-Milad (was moved to Friday 29th)
                                || (d == 29 && m == September)
                                // Dasera
                                || (d == 24 && m == October)
                                // Diwali - Balipratipada
                                || (d == 14 && m == November)
                                // Gurunank Jayanti
                                || (d == 27 && m == November) ) {
                    return false;
                }
            }
            if ( y == 2024 ) {
                if (// Special holiday
                        (d == 22 && m == January)
                                // Chatrapati Shivaji Jayanti
                                || (d == 19 && m == February)
                                // Mahashivratri
                                || (d == 8 && m == March)
                                // Holi
                                || (d == 25 && m == March)
                                // Annual Bank Closing
                                || (d == 1 && m == April)
                                // Gudi Padwa
                                || (d == 9 && m == April)
                                // Id-Ul-Fitr (Ramadan Eid)
                                || (d == 11 && m == April)
                                // Ram Navami
                                || (d == 17 && m == April)
                                // Mahavir Jayanti
                                || (d == 21 && m == April)
                                // General Parliamentary Elections
                                || (d == 20 && m == May)
                                // Buddha Pournima
                                || (d == 23 && m == May)
                                // Bakri Eid
                                || (d == 17 && m == June)
                                // Moharram
                                || (d == 17 && m == July)
                                // Eid-E-Milad (estimated Sunday 15th or Monday 16th)
                                || (d == 16 && m == September)
                                // Diwali-Laxmi Pujan
                                || (d == 1 && m == November)
                                // Gurunank Jayanti
                                || (d == 15 && m == November) ) {
                    return false;
                }
            }
            if ( y == 2025 ) {
                // Chatrapati Shivaji Jayanti
                return (d != 19 || m != February)
                        // Mahashivratri
                        && (d != 26 || m != February)
                        // Holi
                        && (d != 14 || m != March)
                        // Ramzan Id (estimated Sunday 30th or Monday 31st)
                        && (d != 31 || m != March)
                        // Mahavir Jayanti
                        && (d != 10 || m != April)
                        // Buddha Pournima
                        && (d != 12 || m != May)
                        // Id-E-Milad (estimated Thursday 4th or Friday 5th)
                        && (d != 5 || m != September)
                        // Diwali - Balipratipada
                        && (d != 22 || m != October)
                        // Gurunank Jayanti
                        && (d != 5 || m != November);
            }
            if ( y == 2026 ) {
                return (d != 15 || m != January)
                        && (d != 19 || m != February)
                        && (d != 3 || m != March)
                        && (d != 19 || m != March)
                        && (d != 26 || m != March)
                        && (d != 31 || m != March)
                        && (d != 1 || m != April)
                        && (d != 28 || m != May)
                        && (d != 26 || m != June)
                        && (d != 26 || m != August)
                        && (d != 14 || m != September)
                        && (d != 20 || m != October)
                        && (d != 10 || m != November)
                        && (d != 24 || m != November);
            }
            return true;
        }
    }
}
