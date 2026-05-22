/*
 Copyright (C) 2025 JQuantLib Migration Project

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.
 */

/*
 Copyright (C) 2009, 2011 Ferdinando Ametrano
 Copyright (C) 2015 Paolo Mazzocchi

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.
*/

package org.jquantlib.time;

import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

import org.jquantlib.QL;
import org.jquantlib.Settings;

/**
 * European Central Bank reserve maintenance dates.
 *
 * <p>Port of v1.42.1 {@code ql/time/ecb.{hpp,cpp}}.
 *
 * <p>The {@link #knownDates()} catalogue (serial-number form) is copied
 * verbatim from {@code ecb.cpp:76-156}, sourced from the ECB's reserve
 * maintenance calendar.
 */
public final class ECB {

    private ECB() { /* static-only utility */ }

    private static final SortedSet<Date> KNOWN_DATES = buildKnownDates();

    private static SortedSet<Date> buildKnownDates() {
        final long[] serials = {
                // 2005
                38371, 38391, 38420, 38455, 38483, 38511,
                38546, 38574, 38602, 38637, 38665, 38692,
                // 2006
                38735, 38756, 38784, 38819, 38847, 38883,
                38910, 38938, 38966, 39001, 39029, 39064,
                // 2007
                39099, 39127, 39155, 39190, 39217, 39246,
                39274, 39302, 39337, 39365, 39400, 39428,
                // 2008
                39463, 39491, 39519, 39554, 39582, 39610,
                39638, 39673, 39701, 39729, 39764, 39792,
                // 2009
                39834, 39855, 39883, 39911, 39946, 39974,
                40002, 40037, 40065, 40100, 40128, 40155,
                // 2010
                40198, 40219, 40247, 40282, 40310, 40345,
                40373, 40401, 40429, 40464, 40492, 40520,
                // 2011
                40562, 40583, 40611, 40646, 40674, 40709,
                40737, 40765, 40800, 40828, 40856, 40891,
                // 2012
                40926, 40954, 40982, 41010, 41038, 41073,
                41101, 41129, 41164, 41192, 41227, 41255,
                // 2013
                41290, 41318, 41346, 41374, 41402, 41437,
                41465, 41493, 41528, 41556, 41591, 41619,
                // 2014
                41654, 41682, 41710, 41738, 41773, 41801,
                41829, 41864, 41892, 41920, 41955, 41983,
                // 2015
                42032, 42074, 42116, 42165, 42207, 42256,
                42305, 42347,
                // 2016
                42396, 42445, 42487, 42529, 42578, 42627,
                42669, 42718,
                // 2017
                42760, 42809, 42858, 42900, 42942, 42991,
                43040, 43089,
                // 2018
                43131, 43167, 43216, 43265, 43307, 43356,
                43398, 43447,
                // 2019
                43495, 43537, 43572, 43628, 43677, 43726,
                43768, 43817,
                // 2020
                43859, 43908, 43957, 43992, 44034, 44090,
                44139, 44181,
                // 2021
                44223, 44272, 44314, 44363, 44405, 44454,
                44503, 44552,
                // 2022
                44601, 44636, 44671, 44727, 44769, 44818,
                44867, 44916,
                // 2023
                44965, 45007, 45056, 45098, 45140, 45189,
                45231, 45280,
                // 2024
                45322, 45364, 45399, 45455, 45497, 45553,
                45588, 45644
        };
        final SortedSet<Date> set = new TreeSet<>(new java.util.Comparator<Date>() {
            @Override
            public int compare(final Date a, final Date b) {
                return Long.compare(a.serialNumber(), b.serialNumber());
            }
        });
        for (final long s : serials) {
            set.add(new Date(s));
        }
        return set;
    }

    private static final String[] MONTH_CODES = {
            "JAN", "FEB", "MAR", "APR", "MAY", "JUN",
            "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"
    };

    /** Unmodifiable view of the known ECB maintenance-start date set. */
    public static SortedSet<Date> knownDates() {
        return Collections.unmodifiableSortedSet(KNOWN_DATES);
    }

    public static void addDate(final Date d) {
        KNOWN_DATES.add(d);
    }

    public static void removeDate(final Date d) {
        KNOWN_DATES.remove(d);
    }

    /** Maintenance period start date in the given month/year. */
    public static Date date(final Month m, final int y) {
        return nextDate(new Date(1, m, y).sub(1));
    }

    /**
     * Returns the ECB date for the given ECB code.
     */
    public static Date date(final String ecbCode, final Date referenceDate) {
        QL.require(isECBcode(ecbCode), ecbCode + " is not a valid ECB code");

        final String monthCode = ecbCode.substring(0, 3).toUpperCase();
        final Month m = monthFromCode(monthCode);

        int y = toDigit(ecbCode.charAt(3)) * 10 + toDigit(ecbCode.charAt(4));
        final Date refDate;
        if (referenceDate == null || referenceDate.isNull()) {
            refDate = new Settings().evaluationDate();
        } else {
            refDate = referenceDate;
        }
        final int referenceYear = refDate.year() % 100;
        y += refDate.year() - referenceYear;
        if (y < Date.minDate().year()) {
            return nextDate(Date.minDate());
        }
        return nextDate(new Date(1, m, y).sub(1));
    }

    public static Date date(final String ecbCode) {
        return date(ecbCode, new Date());
    }

    /** Returns the ECB code for the given date. */
    public static String code(final Date ecbDate) {
        QL.require(isECBdate(ecbDate), ecbDate + " is not a valid ECB date");

        final String month = MONTH_CODES[ecbDate.month().value() - 1];
        final int y = ecbDate.year() % 100;
        return String.format("%s%02d", month, y);
    }

    /** Next maintenance period start date following the given date. */
    public static Date nextDate(final Date d) {
        final Date ref;
        if (d == null || d.isNull()) {
            ref = new Settings().evaluationDate();
        } else {
            ref = d;
        }
        // std::upper_bound: first element strictly greater than ref.
        for (final Date kd : KNOWN_DATES) {
            if (kd.gt(ref)) {
                return kd;
            }
        }
        QL.require(false, "ECB dates after " + KNOWN_DATES.last() + " are unknown");
        return null; // unreachable
    }

    public static Date nextDate() {
        return nextDate(new Date());
    }

    public static Date nextDate(final String ecbCode, final Date referenceDate) {
        return nextDate(date(ecbCode, referenceDate));
    }

    public static Date nextDate(final String ecbCode) {
        return nextDate(date(ecbCode, new Date()));
    }

    /** Next maintenance period start dates following the given date. */
    public static java.util.List<Date> nextDates(final Date d) {
        final Date ref;
        if (d == null || d.isNull()) {
            ref = new Settings().evaluationDate();
        } else {
            ref = d;
        }
        final java.util.List<Date> result = new java.util.ArrayList<Date>();
        for (final Date kd : KNOWN_DATES) {
            if (kd.gt(ref)) {
                result.add(kd);
            }
        }
        QL.require(!result.isEmpty(), "ECB dates after " + KNOWN_DATES.last() + " are unknown");
        return result;
    }

    public static java.util.List<Date> nextDates() {
        return nextDates(new Date());
    }

    public static java.util.List<Date> nextDates(final String ecbCode, final Date referenceDate) {
        return nextDates(date(ecbCode, referenceDate));
    }

    /** Whether the given date is a maintenance period start date. */
    public static boolean isECBdate(final Date d) {
        final Date next = nextDate(d.sub(1));
        return d.eq(next);
    }

    /** Whether the given string is a valid ECB code. */
    public static boolean isECBcode(final String in) {
        if (in == null || in.length() != 5) {
            return false;
        }
        final String month = in.substring(0, 3).toUpperCase();
        boolean validMonth = false;
        for (final String mc : MONTH_CODES) {
            if (mc.equals(month)) {
                validMonth = true;
                break;
            }
        }
        if (!validMonth) {
            return false;
        }
        return Character.isDigit(in.charAt(3)) && Character.isDigit(in.charAt(4));
    }

    /** Next ECB code following the given date. */
    public static String nextCode(final Date d) {
        return code(nextDate(d));
    }

    public static String nextCode() {
        return nextCode(new Date());
    }

    /** Next ECB code following the given code. */
    public static String nextCode(final String ecbCode) {
        QL.require(isECBcode(ecbCode), ecbCode + " is not a valid ECB code");
        final String month = ecbCode.substring(0, 3).toUpperCase();
        final Month m = monthFromCode(month);

        final StringBuilder sb = new StringBuilder(5);
        if (m != Month.December) {
            // next month, same year digits
            sb.append(MONTH_CODES[m.value()]); // m.value() is 1..12, index = m.value() also gives next when m != Dec? careful: array is 0-based, JAN=index 0
            // We want the month after m, so index = m.value() (since JAN=index 0, value=1; next month is index 1)
            sb.setLength(0);
            sb.append(MONTH_CODES[m.value()]);
            sb.append(ecbCode.charAt(3));
            sb.append(ecbCode.charAt(4));
        } else {
            sb.append("JAN");
            char c3 = ecbCode.charAt(3);
            char c4 = ecbCode.charAt(4);
            // increment year's last digit; if overflow, also bump second digit
            if (c4 == '9') {
                c4 = '0';
                if (c3 == '9') {
                    c3 = '0';
                } else {
                    c3 = (char) (c3 + 1);
                }
            } else {
                c4 = (char) (c4 + 1);
            }
            sb.append(c3);
            sb.append(c4);
        }
        return sb.toString();
    }

    private static Month monthFromCode(final String upper) {
        for (int i = 0; i < MONTH_CODES.length; i++) {
            if (MONTH_CODES[i].equals(upper)) {
                return Month.valueOf(i + 1);
            }
        }
        throw new IllegalArgumentException("invalid month code: " + upper);
    }

    private static int toDigit(final char c) {
        final int i = c - '0';
        QL.require(i >= 0 && i <= 9, "Character does not represent a digit. char: " + c);
        return i;
    }
}
