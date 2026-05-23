/*
 Copyright (C) 2008 Srinivas Hasti

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

package org.jquantlib.time;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.util.Observable;
import org.jquantlib.util.Observer;

import java.io.Serializable;
import java.util.Calendar;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;

/**
 * Date class to represent time in days.
 *
 * @author Richard Gomes
 */
public class Date implements Observable, Comparable< Date >, Serializable, Cloneable {

    private static final long serialVersionUID = -7150540867519744332L;
    private static final int[] monthLength = { 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31 };
    private static final int[] monthLeapLength = { 31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31 };
    private static final int[] monthOffset = { 0, 31, 59, 90, 120, 151,        // Jan - Jun
            181, 212, 243, 273, 304, 334,   // Jun - Dec
            365 // used in dayOfMonth to bracket day
    };
    private static final int[] monthLeapOffset = { 0, 31, 60, 91, 121, 152,        // Jan - Jun
            182, 213, 244, 274, 305, 335,   // Jun - Dec
            366 // used in dayOfMonth to bracket day
    };
    // the list of all December 31st in the preceding year
    // e.g. for 1901 yearOffset[1] is 366, that is, December 31 1900
    private static final int[] yearOffset = {
            // 1900-1909
            0, 366, 731, 1096, 1461, 1827, 2192, 2557, 2922, 3288,
            // 1910-1919
            3653, 4018, 4383, 4749, 5114, 5479, 5844, 6210, 6575, 6940,
            // 1920-1929
            7305, 7671, 8036, 8401, 8766, 9132, 9497, 9862, 10227, 10593,
            // 1930-1939
            10958, 11323, 11688, 12054, 12419, 12784, 13149, 13515, 13880, 14245,
            // 1940-1949
            14610, 14976, 15341, 15706, 16071, 16437, 16802, 17167, 17532, 17898,
            // 1950-1959
            18263, 18628, 18993, 19359, 19724, 20089, 20454, 20820, 21185, 21550,
            // 1960-1969
            21915, 22281, 22646, 23011, 23376, 23742, 24107, 24472, 24837, 25203,
            // 1970-1979
            25568, 25933, 26298, 26664, 27029, 27394, 27759, 28125, 28490, 28855,
            // 1980-1989
            29220, 29586, 29951, 30316, 30681, 31047, 31412, 31777, 32142, 32508,
            // 1990-1999
            32873, 33238, 33603, 33969, 34334, 34699, 35064, 35430, 35795, 36160,
            // 2000-2009
            36525, 36891, 37256, 37621, 37986, 38352, 38717, 39082, 39447, 39813,
            // 2010-2019
            40178, 40543, 40908, 41274, 41639, 42004, 42369, 42735, 43100, 43465,
            // 2020-2029
            43830, 44196, 44561, 44926, 45291, 45657, 46022, 46387, 46752, 47118,
            // 2030-2039
            47483, 47848, 48213, 48579, 48944, 49309, 49674, 50040, 50405, 50770,
            // 2040-2049
            51135, 51501, 51866, 52231, 52596, 52962, 53327, 53692, 54057, 54423,
            // 2050-2059
            54788, 55153, 55518, 55884, 56249, 56614, 56979, 57345, 57710, 58075,
            // 2060-2069
            58440, 58806, 59171, 59536, 59901, 60267, 60632, 60997, 61362, 61728,
            // 2070-2079
            62093, 62458, 62823, 63189, 63554, 63919, 64284, 64650, 65015, 65380,
            // 2080-2089
            65745, 66111, 66476, 66841, 67206, 67572, 67937, 68302, 68667, 69033,
            // 2090-2099
            69398, 69763, 70128, 70494, 70859, 71224, 71589, 71955, 72320, 72685,
            // 2100-2109
            73050, 73415, 73780, 74145, 74510, 74876, 75241, 75606, 75971, 76337,
            // 2110-2119
            76702, 77067, 77432, 77798, 78163, 78528, 78893, 79259, 79624, 79989,
            // 2120-2129
            80354, 80720, 81085, 81450, 81815, 82181, 82546, 82911, 83276, 83642,
            // 2130-2139
            84007, 84372, 84737, 85103, 85468, 85833, 86198, 86564, 86929, 87294,
            // 2140-2149
            87659, 88025, 88390, 88755, 89120, 89486, 89851, 90216, 90581, 90947,
            // 2150-2159
            91312, 91677, 92042, 92408, 92773, 93138, 93503, 93869, 94234, 94599,
            // 2160-2169
            94964, 95330, 95695, 96060, 96425, 96791, 97156, 97521, 97886, 98252,
            // 2170-2179
            98617, 98982, 99347, 99713, 100078, 100443, 100808, 101174, 101539, 101904,
            // 2180-2189
            102269, 102635, 103000, 103365, 103730, 104096, 104461, 104826, 105191, 105557,
            // 2190-2199
            105922, 106287, 106652, 107018, 107383, 107748, 108113, 108479, 108844, 109209,
            // 2200
            109574 };
    private static final boolean[] yearIsLeap = {
            // 1900 is leap in agreement with Excel's bug
            // 1900 is out of valid date range anyway
            // 1900-1909
            true, false, false, false, true, false, false, false, true, false,
            // 1910-1919
            false, false, true, false, false, false, true, false, false, false,
            // 1920-1929
            true, false, false, false, true, false, false, false, true, false,
            // 1930-1939
            false, false, true, false, false, false, true, false, false, false,
            // 1940-1949
            true, false, false, false, true, false, false, false, true, false,
            // 1950-1959
            false, false, true, false, false, false, true, false, false, false,
            // 1960-1969
            true, false, false, false, true, false, false, false, true, false,
            // 1970-1979
            false, false, true, false, false, false, true, false, false, false,
            // 1980-1989
            true, false, false, false, true, false, false, false, true, false,
            // 1990-1999
            false, false, true, false, false, false, true, false, false, false,
            // 2000-2009
            true, false, false, false, true, false, false, false, true, false,
            // 2010-2019
            false, false, true, false, false, false, true, false, false, false,
            // 2020-2029
            true, false, false, false, true, false, false, false, true, false,
            // 2030-2039
            false, false, true, false, false, false, true, false, false, false,
            // 2040-2049
            true, false, false, false, true, false, false, false, true, false,
            // 2050-2059
            false, false, true, false, false, false, true, false, false, false,
            // 2060-2069
            true, false, false, false, true, false, false, false, true, false,
            // 2070-2079
            false, false, true, false, false, false, true, false, false, false,
            // 2080-2089
            true, false, false, false, true, false, false, false, true, false,
            // 2090-2099
            false, false, true, false, false, false, true, false, false, false,
            // 2100-2109
            false, false, false, false, true, false, false, false, true, false,
            // 2110-2119
            false, false, true, false, false, false, true, false, false, false,
            // 2120-2129
            true, false, false, false, true, false, false, false, true, false,
            // 2130-2139
            false, false, true, false, false, false, true, false, false, false,
            // 2140-2149
            true, false, false, false, true, false, false, false, true, false,
            // 2150-2159
            false, false, true, false, false, false, true, false, false, false,
            // 2160-2169
            true, false, false, false, true, false, false, false, true, false,
            // 2170-2179
            false, false, true, false, false, false, true, false, false, false,
            // 2180-2189
            true, false, false, false, true, false, false, false, true, false,
            // 2190-2199
            false, false, true, false, false, false, true, false, false, false,
            // 2200
            false };
    /**
     * Implements multiple inheritance via delegate pattern to an inner class.
     *
     * <p>Phase 2x A.4: switched to {@link
     * org.jquantlib.util.WeakReferenceObservable} to break the test-suite cascade slowdown — prior tests' term
     * structures / helpers / etc. register strong-ref observers on the {@code Settings.evaluationDate()} proxy and
     * never get GC'd between tests, growing the observer list unbounded. With weak refs the dead observers are
     * reclaimed lazily.
     *
     * <p>This is API-equivalent for any caller that holds its own strong
     * reference to the registered observer (the standard pattern); only "register-and-forget" observers see a behaviour
     * change, and those are not legitimate users of the observer pattern.
     */
    private final Observable delegatedObservable = new org.jquantlib.util.WeakReferenceObservable(this);

    //
    // public constructors
    //
    private /* @NonNegative */ long serialNumber;

    /**
     * Intraday time-of-day, expressed in nanoseconds since 00:00:00 of
     * {@link #serialNumber}'s calendar day. Always in
     * {@code [0, 86_400_000_000_000L)}.
     *
     * <p>Phase 5e.5b-CFC-d-304 (intraday support — mirrors C++
     * {@code QL_HIGH_RESOLUTION_DATE}, ql/time/date.cpp:540-548). For the
     * vast majority of constructors (day-only) this field stays at {@code 0}
     * and {@link Date} behaves bit-identically to the pre-intraday class.
     * Identity semantics ({@link #equals}, {@link #hashCode}, {@link
     * #serialNumber}, {@link #compareTo}) are deliberately day-only — they
     * ignore this field. That matches the C++ Calendar convention:
     * {@code Calendar::addHoliday(d)} explicitly normalises {@code d} to
     * {@code Date(d.dayOfMonth(), d.month(), d.year())} before inserting it
     * into the holiday set (ql/time/calendar.cpp:48-52). Day-only identity
     * therefore lets the Java port of intraday-aware {@code Calendar} tests
     * pass without touching {@link Calendar}.
     *
     * <p>Intraday content is preserved as supplementary metadata accessible
     * via {@link #hours()}, {@link #minutes()}, {@link #seconds()},
     * {@link #milliseconds()}, {@link #microseconds()},
     * {@link #fractionOfDay()}, and {@link #fractionOfSecond()}.
     */
    private long timeOfDayNanos;

    /** {@code 86_400 * 1_000_000_000L}, i.e. nanoseconds per 24-hour day. */
    private static final long NANOS_PER_DAY = 86_400_000_000_000L;

    /**
     * Default constructor returning a null date.
     */
    public Date() {
        this(0);
    }

    /**
     * Constructor taking a serial number as given by Applix or Excel.
     *
     * @param serialNumber
     * @return
     */
    public Date(final long serialNumber) {
        this.serialNumber = serialNumber;
        this.timeOfDayNanos = 0L;
    }

    /**
     * More traditional constructor.
     *
     * @param d
     * @param moreGreeks
     * @param y
     */
    public Date(final int day, final Month month, final int year) {
        this(fromDMY(day, month.value(), year));
    }

    /**
     * More traditional constructor.
     *
     * @param d
     * @param moreGreeks
     * @param y
     */
    public Date(final int day, final int month, final int year) {
        this(fromDMY(day, month, year));
    }

    /**
     * Intraday-aware constructor. Mirrors C++ v1.42.1
     * ql/time/date.hpp:140-145 / ql/time/date.cpp:540-548 — the
     * {@code QL_HIGH_RESOLUTION_DATE} branch.
     *
     * <p>The day/month/year component is encoded into {@link #serialNumber}
     * exactly as in the day-only constructor; the {@code hours},
     * {@code minutes}, {@code seconds}, {@code millisec}, and
     * {@code microsec} components are packed into {@link #timeOfDayNanos}.
     * The resulting Date compares ({@code ==}, {@code hashCode}) equal to a
     * day-only Date built from the same {@code (day, month, year)}; the
     * intraday metadata is recovered via the {@link #hours()} /
     * {@link #minutes()} / {@link #seconds()} accessors.
     *
     * @param day        day of month, 1-31
     * @param month      1-based month index (1 = January, 12 = December)
     * @param year       four-digit year, must be in [1901, 2199]
     * @param hours      hour of day, 0-23
     * @param minutes    minute of hour, 0-59
     * @param seconds    second of minute, 0-59
     * @param millisec   millisecond of second, 0-999
     * @param microsec   microsecond of millisecond, 0-999
     */
    public Date(final int day, final int month, final int year,
                final int hours, final int minutes, final int seconds,
                final int millisec, final int microsec) {
        this(fromDMY(day, month, year));
        // Phase 1.3 D5-D-intraday relaxation: C++ boost time_duration
        // (ql/time/date.cpp:540-548) tolerates fields > their natural
        // modular bound — fields beyond their range carry over into the
        // higher unit (e.g. 1234ms becomes 1s + 234ms). Java mirrors via
        // direct timeOfDayNanos accumulation plus floorMod normalization;
        // any overflow into >=24h carries the serialNumber forward.
        QL.require(hours >= 0, "hours must be >= 0");
        QL.require(minutes >= 0, "minutes must be >= 0");
        QL.require(seconds >= 0, "seconds must be >= 0");
        QL.require(millisec >= 0, "millisec must be >= 0");
        QL.require(microsec >= 0, "microsec must be >= 0");
        final long totalNanos =
                ((long) hours) * 3_600_000_000_000L
              + ((long) minutes) * 60_000_000_000L
              + ((long) seconds) * 1_000_000_000L
              + ((long) millisec) * 1_000_000L
              + ((long) microsec) * 1_000L;
        final long dayCarry = Math.floorDiv(totalNanos, NANOS_PER_DAY);
        this.timeOfDayNanos = Math.floorMod(totalNanos, NANOS_PER_DAY);
        if (dayCarry != 0L) {
            this.serialNumber += dayCarry;
            // Re-check after carry; intraday overflow must stay in range.
            QL.ensure(serialNumber >= minimumSerialNumber()
                    && serialNumber <= maximumSerialNumber(),
                    "Date's serial number outside allowed range after intraday carry");
        }
    }

    /** Intraday-aware constructor with default milli/microseconds (=0). */
    public Date(final int day, final int month, final int year,
                final int hours, final int minutes, final int seconds) {
        this(day, month, year, hours, minutes, seconds, 0, 0);
    }

    /** Intraday-aware {@link Month}-typed constructor convenience. */
    public Date(final int day, final Month month, final int year,
                final int hours, final int minutes, final int seconds,
                final int millisec, final int microsec) {
        this(day, month.value(), year, hours, minutes, seconds, millisec, microsec);
    }

    /** Intraday-aware {@link Month}-typed constructor convenience (no milli/micro). */
    public Date(final int day, final Month month, final int year,
                final int hours, final int minutes, final int seconds) {
        this(day, month.value(), year, hours, minutes, seconds, 0, 0);
    }

    //
    // public methods :: inspectors
    //

    /**
     * Allows interoperability with JDK Date
     * <p>
     * <b>NOTE</b>: Both java.util.Date and JQuantLib Date do not support time zones or high precision clocks.
     * In other words, a day <i>always</i> has exactly 84,600 seconds, or 84,600,000 milliseconds.
     *
     * @author Richard Gomes
     */
    public Date(final java.util.Date date) {
        //    	this(25569+(date.getTime()/86400000L));
        final Calendar c = Calendar.getInstance();
        c.setTime(date);
        final int d = c.get(Calendar.DAY_OF_MONTH);
        final int m = c.get(Calendar.MONTH);
        final int y = c.get(Calendar.YEAR);
        this.serialNumber = fromDMY(d, m + 1, y);
    }

    /**
     * Today's date.
     *
     * @return a new instance
     */
    public static final Date todaysDate() {
        final java.util.Calendar cal = java.util.Calendar.getInstance();
        final int d = cal.get(java.util.Calendar.DAY_OF_MONTH);
        final int m = cal.get(java.util.Calendar.MONTH);
        final int y = cal.get(java.util.Calendar.YEAR);
        return new Date(d, m + 1, y);
    }

    /**
     * Earliest allowed date
     *
     * @return a new instance
     */
    public static final Date minDate() {
        return new Date(minimumSerialNumber());
    }

    /**
     * Latest allowed date
     *
     * @return a new instance
     */
    public static final Date maxDate() {
        return new Date(maximumSerialNumber());
    }

    /**
     * Whether the given year is a leap one
     *
     * @param y
     * @return
     */
    public static final boolean isLeap(final int year) {
        return yearIsLeap[year - 1900];
    }

    /**
     * Last day of the month to which the given date belongs
     *
     * @return a new instance
     */
    public static final Date endOfMonth(final Date d) {
        final int m = d.month().value();
        final int y = d.year();
        return new Date(monthLength(m, isLeap(y)), m, y);
    }

    //
    // public methods :: name date algebra
    //

    /**
     * Whether a date is the last day of its month
     *
     * @return
     */
    public static final boolean isEndOfMonth(final Date d) {
        return (d.dayOfMonth() == monthLength(d.month().value(), isLeap(d.year())));
    }

    /**
     * First day of the month to which the given date belongs.
     * <p>
     * Mirrors C++ v1.42.1 ql/time/date.hpp:380-384.
     *
     * @return a new instance
     */
    public static final Date startOfMonth(final Date d) {
        return new Date(1, d.month().value(), d.year());
    }

    /**
     * Whether a date is the first day of its month.
     * <p>
     * Mirrors C++ v1.42.1 ql/time/date.hpp:386-388.
     */
    public static final boolean isStartOfMonth(final Date d) {
        return d.dayOfMonth() == 1;
    }

    /**
     * Next given weekday following or equal to the given date
     * <p>
     * E.g., the Friday following Tuesday, JANUARY 15th, 2002 was JANUARY 18th, 2002.
     *
     * @return a new instance
     * @see http://www.cpearson.com/excel/DateTimeWS.htm
     */
    public static final Date nextWeekday(final Date d, final Weekday w) {
        final int wd = d.weekday().value();
        final int dow = w.value();
        return new Date(d.serialNumber + (wd > dow ? 7 : 0) - wd + dow);
    }

    //    /**
    //     *  1-day pre-increment
    //     *
    //     *  @return this
    //     */
    //    //-- Date& operator++();
    //    Date inc();

    /**
     * n-th given weekday in the given month and year
     * <p>
     * E.g., the 4th Thursday of March, 1998 was March 26th, 1998.
     *
     * @param n
     * @param w
     * @param m
     * @param y
     * @return a new instance
     * @see http://www.cpearson.com/excel/DateTimeWS.htm
     */
    public static final Date nthWeekday(final int n, final Weekday w, final Month m, final int y) {
        return nthWeekday(n, w, m.value(), y);
    }

    //    /**
    //     *  1-day pre-decrement
    //     *
    //     *  @return this
    //     */
    //    //-- Date& operator--();
    //    Date dec();

    /**
     * Returns a new Date which is the n-th week day of a certain month/year
     *
     * @param nth       is the desired week
     * @param dayOfWeek is the desired week day
     * @param month     is the desired month
     * @param year      is the desired year
     * @return a new instance
     */
    public static final Date nthWeekday(final int nth, final Weekday dayOfWeek, final int month, final int year) {
        QL.require(nth > 0, "zeroth day of week in a given (month, year) is undefined");
        QL.require(nth < 6, "no more than 5 weekday in a given (month, year)");
        final int m = month;
        final int y = year;
        final int dow = dayOfWeek.value();
        // FIXME: code review
        final int first = new Date(1, m, y).weekday().value();
        final int skip = nth - (dow >= first ? 1 : 0);
        return new Date(1 + dow - first + skip * 7, m, y);
    }

    /**
     * Return the minimum Date in a range.
     */
    public static Date min(final Date... t) {
        QL.require(t != null, "argument cannot be null");
        if ( t.length == 0 )
            return new Date();
        else {
            Date min = t[0];
            for ( int i = 1; i < t.length; i++ ) {
                final Date curr = t[i];
                if ( curr.lt(min) ) {
                    min = curr;
                }
            }
            return min;
        }
    }

    /**
     * Return the maximum Date in a range.
     */
    public static Date max(final Date... t) {
        QL.require(t != null, "argument cannot be null");
        if ( t.length == 0 )
            return new Date();
        else {
            Date max = t[0];
            for ( int i = 1; i < t.length; i++ ) {
                final Date curr = t[i];
                if ( curr.gt(max) ) {
                    max = curr;
                }
            }
            return max;
        }
    }

    static private long minimumSerialNumber() {
        return 367;       // Jan 1st, 1901
    }

    static private long maximumSerialNumber() {
        return 109574;    // Dec 31st, 2199
    }

    /**
     * This method is intended to calculate the integer value of a (day, month, year)
     *
     * @param d is the day as a number
     * @param m is the month as a number
     * @param y is the year as a number
     * @return
     */
    private static final long fromDMY(final int d, final int m, final int y) {
        QL.require(y > 1900 && y <= 2199, "year(" + y + ") out of bound. It must be in [1901,2199]");
        QL.require(m > 0 && m < 13, "month outside JANUARY-December range [1,12]");
        final boolean leap = isLeap(y);
        final int len = monthLength(m, leap);
        final int offset = monthOffset(m, leap);
        QL.ensure(d > 0 && d <= len, "day outside month day-range");
        final long result = d + offset + yearOffset(y);
        return result;
    }

    //
    // public methods :: relates Date
    //

    /**
     * Returns the length of a certain month
     *
     * @param m        is the desired month, as a number
     * @param leapYear if <code>true</code> means a leap year
     * @return the length of a certain month
     */
    private static final int monthLength(final int m, final boolean leapYear) {
        return (leapYear ? monthLeapLength[m - 1] : monthLength[m - 1]);
    }

    /**
     * Returns the offset of a certain month
     *
     * @param m        is the desired month, as a number. If you specify 13, you will get the number of days of a year
     * @param leapYear if <code>true</code> means a leap year
     * @return the offset of a certain month or the length of an year
     * @see DefaultDate#yearOffset
     */
    private static final int monthOffset(final int m, final boolean leapYear) {
        return (leapYear ? monthLeapOffset[m - 1] : monthOffset[m - 1]);
    }

    /**
     * Returns the offset of a certain year
     *
     * @param y is the desired year
     * @return the offset of a certain year
     */
    private static final long yearOffset(final int year) {
        return yearOffset[year - 1900];
    }

    /**
     * This method is equivalent to std:lower_bound function Returns an index pointing to the first element in the
     * ordered collection is equal or greater than passed value
     *
     * @param dates order collection in ascending order
     * @param value Date to be compared
     * @return index to element which is >= passed value
     */
    public static int lowerBound(final List< Date > dates, final Date value) {
        int len = dates.size();
        int from = 0;
        int half;
        int middle;

        while ( len > 0 ) {
            half = len >> 1;
            middle = from;
            middle = middle + half;

            if ( value.compareTo(dates.get(middle)) == 1 ) { // value > 1
                from = middle;
                from++;
                len = len - half - 1;
            } else {
                len = half;
            }
        }
        return from;
    }

    /**
     * This method is equivalent to C++ sdt:upper_bound function Returns an index pointing to the first element in the
     * ordered collection which is greater than passed value
     *
     * @param dates order collection in ascending order
     * @param value Date to be compared
     * @return index to element which is > passed value
     */
    public static int upperBound(final List< Date > dates, final Date value) {

        int len = dates.size();
        int from = 0;
        int half;
        int middle;

        while ( len > 0 ) {
            half = len >> 1;
            middle = from;
            middle = middle + half;
            if ( value.compareTo(dates.get(middle)) == -1 ) {
                len = half;
            } else {
                from = middle;
                from++;
                len = len - half - 1;
            }
        }
        return from;
    }

    public Weekday weekday() /* @ReadOnly */ {
        final int w = (int) (serialNumber % 7);
        return Weekday.valueOf(w == 0L ? 7 : w);
    }

    //
    // public methods :: not originally defined in QuantLib/C++
    //

    public int dayOfMonth() /* @ReadOnly */ {
        return dayOfYear() - monthOffset(month().value(), isLeap(year()));
    }

    /**
     * One-based (Jan 1st = 1)
     *
     * @return
     */
    public int dayOfYear() /* @ReadOnly */ {
        return (int) (serialNumber - yearOffset(year()));
    }

    //
    // public methods :: provide access to inner classes
    //

    public Month month() /* @ReadOnly */ {
        final int d = dayOfYear(); // dayOfYear is 1 based
        int m = d / 30 + 1;
        final boolean leap = isLeap(year());
        while ( d <= monthOffset(m, leap) ) {
            --m;
        }
        while ( d > monthOffset(m + 1, leap) ) {
            ++m;
        }
        return Month.valueOf(m);
    }

    public int year() /* @ReadOnly */ {
        int y = (int) (serialNumber / 365) + 1900;
        if ( serialNumber <= yearOffset(y) ) {
            --y;
        }
        return y;
    }

    public long serialNumber() /* @ReadOnly */ {
        return this.serialNumber;
    }

    //
    // public methods :: intraday accessors (mirror C++ QL_HIGH_RESOLUTION_DATE)
    //

    /**
     * Hour-of-day (0-23). Mirrors C++ {@code Date::hours()}
     * (ql/time/date.cpp:578-580). For day-only Dates this always returns 0.
     */
    public int hours() /* @ReadOnly */ {
        return (int) (timeOfDayNanos / 3_600_000_000_000L);
    }

    /**
     * Minute-of-hour (0-59). Mirrors C++ {@code Date::minutes()}
     * (ql/time/date.cpp:582-584).
     */
    public int minutes() /* @ReadOnly */ {
        return (int) ((timeOfDayNanos / 60_000_000_000L) % 60L);
    }

    /**
     * Second-of-minute (0-59). Mirrors C++ {@code Date::seconds()}
     * (ql/time/date.cpp:586-588).
     */
    public int seconds() /* @ReadOnly */ {
        return (int) ((timeOfDayNanos / 1_000_000_000L) % 60L);
    }

    /**
     * Millisecond-of-second (0-999). Mirrors C++ {@code Date::milliseconds()}
     * (ql/time/date.cpp:605-608).
     */
    public int milliseconds() /* @ReadOnly */ {
        return (int) ((timeOfDayNanos / 1_000_000L) % 1000L);
    }

    /**
     * Microsecond-of-millisecond (0-999). Mirrors C++
     * {@code Date::microseconds()} (ql/time/date.cpp:610-614).
     */
    public int microseconds() /* @ReadOnly */ {
        return (int) ((timeOfDayNanos / 1_000L) % 1000L);
    }

    /**
     * Fractional part of the day in {@code [0.0, 1.0)}, computed from the
     * intraday {@code hh:mm:ss.SSSS}. Mirrors C++ {@code Date::fractionOfDay()}
     * (ql/time/date.cpp:590-598). For a day-only Date this returns 0.0.
     */
    public double fractionOfDay() /* @ReadOnly */ {
        return ((double) timeOfDayNanos) / (double) NANOS_PER_DAY;
    }

    /**
     * Fractional part of the current second in {@code [0.0, 1.0)}. Mirrors
     * C++ {@code Date::fractionOfSecond()} (ql/time/date.cpp:600-603).
     */
    public double fractionOfSecond() /* @ReadOnly */ {
        return ((double) (timeOfDayNanos % 1_000_000_000L)) / 1.0e9;
    }

    /**
     * Internal accessor for the time-of-day in nanoseconds since 00:00:00.
     * Always in {@code [0, 86_400_000_000_000L)}. Day-only Dates report 0.
     * Reserved for migration plumbing (e.g. propagating intraday across
     * {@code Settings.evaluationDate} assignments); not part of the C++ API.
     */
    public long timeOfDayNanos() /* @ReadOnly */ {
        return timeOfDayNanos;
    }

    //
    // implements Comparable<Date>
    //

    /**
     * increments <code>this</code> date by the given number of days
     *
     * @return this
     */
    //-- Date& operator+=(BigInteger days);
    public Date addAssign(final int days) {
        serialNumber += days;
        checkSerialNumber();
        delegatedObservable.notifyObservers();
        return this;
    }

    //
    // implements Observable
    //

    /**
     * increments <code>this</code> date by the given period
     *
     * @return this
     */
    //-- Date& operator+=(const Period&);
    public Date addAssign(final Period period) {
        if ( isIntradayUnit(period.units()) ) {
            final long[] result = advanceIntraday(this.serialNumber, this.timeOfDayNanos,
                    period.length(), period.units());
            this.serialNumber = result[0];
            this.timeOfDayNanos = result[1];
        } else {
            this.serialNumber = advance(this, period.length(), period.units());
        }
        checkSerialNumber();
        delegatedObservable.notifyObservers();
        return this;
    }

    /**
     * decrement <code>this</code> date by the given number of days
     *
     * @return this
     */
    //-- Date& operator-=(BigInteger days);
    public Date subAssign(final int days) {
        serialNumber -= days;
        checkSerialNumber();
        delegatedObservable.notifyObservers();
        return this;
    }

    /**
     * decrements <code>this</code> date by the given period
     *
     * @return this
     */
    //-- Date& operator-=(const Period&);
    public Date subAssign(final Period period) {
        if ( isIntradayUnit(period.units()) ) {
            final long[] result = advanceIntraday(this.serialNumber, this.timeOfDayNanos,
                    -1 * period.length(), period.units());
            this.serialNumber = result[0];
            this.timeOfDayNanos = result[1];
        } else {
            this.serialNumber = advance(this, -1 * period.length(), period.units());
        }
        checkSerialNumber();
        delegatedObservable.notifyObservers();
        return this;
    }

    /**
     * 1-day post-increment
     *
     * @return this
     */
    //-- Date operator++(int );
    public Date inc() {
        serialNumber++;
        checkSerialNumber();
        return this;
    }

    /**
     * 1-day post-decrement
     *
     * @return this
     */
    //-- Date operator--(int );
    public Date dec() {
        serialNumber--;
        checkSerialNumber();
        return this;
    }

    /**
     * returns a new date incremented by the given number of days
     *
     * @return a new instance
     */
    //-- Date operator+(BigInteger days) const;
    public Date add(final int days) /* @ReadOnly */ {
        return new Date(this.serialNumber + days);
    }

    /**
     * returns a new date incremented by the given period
     *
     * @return a new instance
     */
    //-- Date operator+(const Period&) const;
    public Date add(final Period period) /* @ReadOnly */ {
        if ( isIntradayUnit(period.units()) ) {
            final long[] result = advanceIntraday(this.serialNumber, this.timeOfDayNanos,
                    period.length(), period.units());
            final Date d = new Date(result[0]);
            d.timeOfDayNanos = result[1];
            return d;
        }
        return new Date(advance(this, period.length(), period.units()));
    }

    /**
     * returns a new date decremented by the given number of days
     *
     * @return a new instance
     */
    //-- Date operator-(BigInteger days) const;
    public Date sub(final int days) /* @ReadOnly */ {
        return new Date(this.serialNumber - days);
    }

    /**
     * returns a new date decremented by the given period
     *
     * @return a new instance
     */
    //-- Date operator-(const Period&) const;
    public Date sub(final Period period) /* @ReadOnly */ {
        if ( isIntradayUnit(period.units()) ) {
            final long[] result = advanceIntraday(this.serialNumber, this.timeOfDayNanos,
                    -1 * period.length(), period.units());
            final Date d = new Date(result[0]);
            d.timeOfDayNanos = result[1];
            return d;
        }
        return new Date(advance(this, -1 * period.length(), period.units()));
    }

    //
    // Overrides Object
    //

    /**
     * Difference in days between dates
     */
    public long sub(final Date another) {
        return serialNumber - another.serialNumber;
    }

    //-- bool operator==(const Date&, const Date&);
    public boolean eq(final Date another) {
        return serialNumber == another.serialNumber;
    }

    //-- bool operator!=(const Date&, const Date&);
    public boolean ne(final Date another) {
        return serialNumber != another.serialNumber;
    }

    //-- bool operator<(const Date&, const Date&);
    public boolean lt(final Date another) {
        return serialNumber < another.serialNumber;
    }

    //
    // implements Cloneable
    //

    //-- bool operator<=(const Date&, const Date&);
    public boolean le(final Date another) {
        return serialNumber <= another.serialNumber;
    }

    //
    // protected methods
    //
    // These methods were not declared in QuantLib/C++
    //

    //-- bool operator>(const Date&, const Date&);
    public boolean gt(final Date another) {
        return serialNumber > another.serialNumber;
    }

    //-- bool operator>=(const Date&, const Date&);
    public boolean ge(final Date another) {
        return serialNumber >= another.serialNumber;
    }

    //
    // private methods
    //

    /**
     * @return true if this Date is <i>null or non-valid date</i>
     */
    public boolean isNull() {
        return this.serialNumber <= 0;
    }

    public final boolean isToday() {
        final java.util.Calendar cal = java.util.Calendar.getInstance();
        final int d = cal.get(java.util.Calendar.DAY_OF_MONTH);
        final int m = cal.get(java.util.Calendar.MONTH);
        final int y = cal.get(java.util.Calendar.YEAR);
        return serialNumber == fromDMY(d, m + 1, y);
    }

    //
    // public static methods
    //

    /**
     * Retuirns a java.util.Date configured for a long date formatter
     * <p>
     * Method <code>toString</code> would return something like <i>Mon, dd yyyy</i>
     *
     * @return a java.util.Date configured for a long date formatter
     */
    public java.util.Date longDate() {
        return new LongDate();
    }

    /**
     * Returns a java.util.Date configured for a short date formatter
     * <p>
     * Method <code>toString</code> would return something like <i>mm/dd/yyyy</i>
     *
     * @return a java.util.Date configured for a short date formatter
     */
    public java.util.Date shortDate() {
        return new ShortDate();
    }

    /**
     * Returns a java.util.Date configured for a ISO date formatter
     * <p>
     * Method <code>toString</code> would return something like <i>yyyy-mm-yy</i>
     *
     * @return a java.util.Date configured for a ISO date formatter
     */
    public java.util.Date isoDate() {
        return new ISODate();
    }

    @Override
    public int compareTo(final Date o) {
        if ( this.equals(o) )
            return 0;
        if ( this.le(o) )
            return -1;
        return 1;
    }

    @Override
    public final void addObserver(final Observer observer) {
        delegatedObservable.addObserver(observer);
    }

    @Override
    public final int countObservers() {
        return delegatedObservable.countObservers();
    }

    @Override
    public final void deleteObserver(final Observer observer) {
        delegatedObservable.deleteObserver(observer);
    }

    @Override
    public final void notifyObservers() {
        delegatedObservable.notifyObservers();
    }

    @Override
    public final void notifyObservers(final Object arg) {
        delegatedObservable.notifyObservers(arg);
    }

    @Override
    public final void deleteObservers() {
        delegatedObservable.deleteObservers();
    }

    @Override
    public final List< Observer > getObservers() {
        return delegatedObservable.getObservers();
    }

    /**
     * Phase 2x A.4: lets {@link org.jquantlib.Settings.DateProxy} (and tests) explicitly drop GC-cleared observer
     * wrappers before fanning out a notification. No-op for non-{@link org.jquantlib.util.WeakReferenceObservable}
     * delegates.
     */
    protected final void compactObservers() {
        if ( delegatedObservable instanceof org.jquantlib.util.WeakReferenceObservable ) {
            ((org.jquantlib.util.WeakReferenceObservable) delegatedObservable).compact();
        }
    }

    @Override
    public int hashCode() {
        return (int) this.serialNumber;
    }

    //
    // private static methods
    //

    @Override
    public boolean equals(final Object anObject) {
        if ( this == anObject )
            return true;
        if ( anObject == null )
            return false;

        return anObject instanceof Date date && date.fEquals(this);
    }

    protected boolean fEquals(final Date other) {
        return eq(other);
    }

    @Override
    public String toString() {
        return longDate().toString();
    }

    @Override
    public Date clone() {
        try {
            return (Date) super.clone();
        } catch ( final CloneNotSupportedException e ) {
            throw new LibraryException(e);
        }
    }

    /**
     * Assigns the today's date to <code>this</code> instance
     *
     * @return this instance
     * @note Does not trigger notifications
     * @see
     */
    protected final long todaysSerialNumber() {
        final java.util.Calendar cal = java.util.Calendar.getInstance();
        final int d = cal.get(java.util.Calendar.DAY_OF_MONTH);
        final int m = cal.get(java.util.Calendar.MONTH);
        final int y = cal.get(java.util.Calendar.YEAR);
        return fromDMY(d, m + 1, y);
    }

    /**
     * Assigns a new serialNumber to <code>this</code> instance.
     *
     * @return this instance
     * @note Does not trigger notifications
     * @see inner class DateProxy in {@link Settings}
     */
    protected final Date assign(final long serialNumber) {
        this.serialNumber = serialNumber;
        return this;
    }

    private void checkSerialNumber() {
        QL.ensure((serialNumber >= minimumSerialNumber()) && (serialNumber <= maximumSerialNumber()),
                "Date's serial number is outside allowed range");
    }

    /**
     * Whether the given unit is sub-day (Hours/Minutes/Seconds/Milliseconds/
     * Microseconds). Phase 1.3 D5-D-intraday: helper for the day-vs-intraday
     * dispatch in Date.add/sub/addAssign/subAssign(Period).
     */
    private static boolean isIntradayUnit(final TimeUnit u) {
        switch (u) {
        case Hours:
        case Minutes:
        case Seconds:
        case Milliseconds:
        case Microseconds:
            return true;
        default:
            return false;
        }
    }

    /**
     * Advance (serialNumber, timeOfDayNanos) by {@code n} sub-day units,
     * propagating any overflow/underflow into the serialNumber via day
     * carry. Mirrors C++ {@code advance(ptime& dt, n, units)} for sub-day
     * cases (ql/time/date.cpp:490-504, QL_HIGH_RESOLUTION_DATE branch).
     *
     * <p>Returns a 2-element array {@code [newSerialNumber, newTimeOfDayNanos]}.
     */
    private static long[] advanceIntraday(final long sn, final long tod,
                                          final int n, final TimeUnit units) {
        final long delta = switch (units) {
            case Hours -> (long) n * 3_600_000_000_000L;
            case Minutes -> (long) n * 60_000_000_000L;
            case Seconds -> (long) n * 1_000_000_000L;
            case Milliseconds -> (long) n * 1_000_000L;
            case Microseconds -> (long) n * 1_000L;
            default -> throw new LibraryException("undefined time units");
        };
        long total = tod + delta;
        long dayCarry = Math.floorDiv(total, NANOS_PER_DAY);
        long newTod = Math.floorMod(total, NANOS_PER_DAY);
        return new long[] { sn + dayCarry, newTod };
    }

    /**
     * Underlying tick resolution. Mirrors C++ {@code Date::ticksPerSecond()}
     * (ql/time/date.cpp:616-618). For Java's nanosecond-backed
     * {@link #timeOfDayNanos} the resolution is 1_000_000_000.
     */
    public static long ticksPerSecond() {
        return 1_000_000_000L;
    }

    /**
     * Phase 1.3 D5-D-intraday: timeOfDayNanos-aware equality.
     *
     * <p>Unlike {@link #equals}, which is deliberately day-only to preserve
     * Calendar.holiday lookup semantics (see {@link #timeOfDayNanos} javadoc),
     * this method compares both {@link #serialNumber} <i>and</i>
     * {@link #timeOfDayNanos}. Use this in code that needs full intraday
     * resolution comparison (mirrors C++ {@code operator==} under
     * {@code QL_HIGH_RESOLUTION_DATE}).
     */
    public boolean equalsExact(final Date other) {
        if (other == null) return false;
        return this.serialNumber == other.serialNumber
                && this.timeOfDayNanos == other.timeOfDayNanos;
    }

    private long advance(final Date date, final int n, final TimeUnit units) {
        switch ( units ) {
        case Days:
            return (n + date.serialNumber);
        case Weeks:
            return (7L * n + date.serialNumber);
        case Months: {
            int d = date.dayOfMonth();
            int m = date.month().value() + n;
            int y = date.year();
            while ( m > 12 ) {
                m -= 12;
                y += 1;
            }
            while ( m < 1 ) {
                m += 12;
                y -= 1;
            }

            QL.ensure(y > 1900 && y <= 2199, "year out of bounds. It must be in [1901,2199]");
            final int length = monthLength(m, isLeap(y));
            if ( d > length ) {
                d = length;
            }
            final long result = fromDMY(d, m, y);
            return result;
        }
        case Years: {
            int d = date.dayOfMonth();
            final int m = date.month().value();
            final int y = date.year() + n;

            QL.ensure(y > 1900 && y <= 2199, "year out of bounds. It must be in [1901,2199]");
            if ( d == 29 && m == Month.February.value() && !isLeap(y) ) {
                d = 28;
            }

            final long result = fromDMY(d, m, y);
            return result;
        }
        default:
            throw new LibraryException("undefined time units");
        }
    }

    //
    // public inner classes
    //

    /**
     * This class provides a long output formatter, e.g: September 18, 2009
     * <p>
     * <b>NOTE</b>: Both java.util.Date and JQuantLib Date do not support time zones or high precision clocks.
     * In other words, a day <i>always</i> has exactly 84,600 seconds, or 84,600,000 milliseconds.
     */
    private final class LongDate extends java.util.Date {

        private static final long serialVersionUID = -8382775848256835100L;

        private LongDate() {
            super((serialNumber - 25569) * 86400000L);
        }

        @Override
        public String toString() {
            if ( isNull() )
                return "null date";
            else {
                final StringBuilder sb = new StringBuilder();
                final Formatter formatter = new Formatter(sb, Locale.US);
                formatter.format("%s %d, %d", month(), dayOfMonth(), year());
                return sb.toString();
            }
        }
    }

    /**
     * This class provides a short output formatter, e.g: 09/18/2009
     * <p>
     * <b>NOTE</b>: Both java.util.Date and JQuantLib Date do not support time zones or high precision clocks.
     * In other words, a day <i>always</i> has exactly 84,600 seconds, or 84,600,000 milliseconds.
     */
    private final class ShortDate extends java.util.Date {

        private static final long serialVersionUID = -4372510060405020533L;

        private ShortDate() {
            super((serialNumber - 25569) * 86400000L);
        }

        @Override
        public String toString() {
            if ( isNull() )
                return "null date";
            else {
                final StringBuilder sb = new StringBuilder();
                final Formatter formatter = new Formatter(sb, Locale.US);
                formatter.format("%02d/%02d/%4d", month().value(), dayOfMonth(), year());
                return sb.toString();
            }
        }
    }

    /**
     * This class provides an ISO date output formatter, e.g: 2009-09-18
     * <p>
     * <b>NOTE</b>: Both java.util.Date and JQuantLib Date do not support time zones or high precision clocks.
     * In other words, a day <i>always</i> has exactly 84,600 seconds, or 84,600,000 milliseconds.
     */
    private final class ISODate extends java.util.Date {

        private static final long serialVersionUID = 4824909887446169897L;

        private ISODate() {
            super((serialNumber - 25568) * 86400000L);
        }

        @Override
        public String toString() {
            if ( isNull() )
                return "null date";
            else {
                final StringBuilder sb = new StringBuilder();
                final Formatter formatter = new Formatter(sb, Locale.US);
                final Calendar c = Calendar.getInstance();
                c.setTime(this);
                formatter.format("%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1,
                        c.get(Calendar.DAY_OF_MONTH));
                return sb.toString();
            }
        }
    }

}
