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
 Copyright (C) 2000-2007 RiskMap srl / StatPro Italia srl
 Copyright (C) 2004-2006 Ferdinando Ametrano
 Copyright (C) 2006 Katiuscia Manzoni
 Copyright (C) 2015 Maddalena Zanzi

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.
*/

package org.jquantlib.time;

import org.jquantlib.QL;
import org.jquantlib.Settings;

/**
 * Main cycle of the Australian Securities Exchange (ASX) months.
 *
 * <p>Port of v1.42.1 ql/time/asx.{hpp,cpp}.
 */
public final class ASX {

    private ASX() { /* static-only utility */ }

    /** ASX month codes (1-based, indexed by Month.value()). */
    private static final String ALL_MONTH_CODES = "FGHJKMNQUVXZ";

    /** Whether the given date is an ASX date. */
    public static boolean isASXdate(final Date date, final boolean mainCycle) {
        if (date.weekday() != Weekday.Friday) {
            return false;
        }
        final int d = date.dayOfMonth();
        if (d < 8 || d > 14) {
            return false;
        }
        if (!mainCycle) {
            return true;
        }
        switch (date.month()) {
        case March:
        case June:
        case September:
        case December:
            return true;
        default:
            return false;
        }
    }

    public static boolean isASXdate(final Date date) {
        return isASXdate(date, true);
    }

    /** Whether the given string is a valid ASX code. */
    public static boolean isASXcode(final String in, final boolean mainCycle) {
        if (in == null || in.length() != 2) {
            return false;
        }
        if (!Character.isDigit(in.charAt(1))) {
            return false;
        }
        final String valid = mainCycle ? "HMUZ" : ALL_MONTH_CODES;
        return valid.indexOf(Character.toUpperCase(in.charAt(0))) >= 0;
    }

    public static boolean isASXcode(final String in) {
        return isASXcode(in, true);
    }

    /** Returns the ASX code for the given date (e.g. "M5" for 12-Jun-2015). */
    public static String code(final Date date) {
        QL.require(isASXdate(date, false), date + " is not an ASX date");
        final char monthCode = ALL_MONTH_CODES.charAt(date.month().value() - 1);
        final char yearDigit = (char) ('0' + (date.year() % 10));
        return new String(new char[] { monthCode, yearDigit });
    }

    /** Returns the ASX date for the given ASX code. */
    public static Date date(final String asxCode, final Date referenceDate) {
        QL.require(isASXcode(asxCode, false), asxCode + " is not a valid ASX code");

        final Date refDate = (referenceDate == null || referenceDate.isNull()) ?
                new Settings().evaluationDate() : referenceDate;

        final char ms = Character.toUpperCase(asxCode.charAt(0));
        final int idxZeroBased = ALL_MONTH_CODES.indexOf(ms);
        QL.require(idxZeroBased >= 0, "invalid ASX month letter. code: " + asxCode);

        // QuantLib's Month is 1-based
        final Month m = Month.valueOf(idxZeroBased + 1);

        // convert 2nd char to year digit
        int y = asxCode.charAt(1) - '0';
        QL.require(y >= 0 && y <= 9, "invalid ASX year digit. code: " + asxCode);

        // year<1900 invalid -> add 10 years if needed
        if (y == 0 && refDate.year() <= 1909) {
            y += 10;
        }
        final int refYearMod = refDate.year() % 10;
        y += refDate.year() - refYearMod;
        Date result = nextDate(new Date(1, m, y), false);
        if (result.lt(refDate)) {
            result = nextDate(new Date(1, m, y + 10), false);
        }
        return result;
    }

    public static Date date(final String asxCode) {
        return date(asxCode, new Date());
    }

    /** Next ASX date following the given date. */
    public static Date nextDate(final Date date, final boolean mainCycle) {
        final Date refDate = (date == null || date.isNull()) ?
                new Settings().evaluationDate() : date;
        int y = refDate.year();
        int m = refDate.month().value();

        final int offset = mainCycle ? 3 : 1;
        int skipMonths = offset - (m % offset);
        if (skipMonths != offset || refDate.dayOfMonth() > 14) {
            skipMonths += m;
            if (skipMonths <= 12) {
                m = skipMonths;
            } else {
                m = skipMonths - 12;
                y += 1;
            }
        }

        Date result = Date.nthWeekday(2, Weekday.Friday, Month.valueOf(m), y);
        if (result.le(refDate)) {
            result = nextDate(new Date(15, Month.valueOf(m), y), mainCycle);
        }
        return result;
    }

    public static Date nextDate(final Date date) {
        return nextDate(date, true);
    }

    public static Date nextDate() {
        return nextDate(new Date(), true);
    }

    public static Date nextDate(final String asxCode, final boolean mainCycle, final Date referenceDate) {
        final Date asxDate = date(asxCode, referenceDate);
        return nextDate(asxDate.add(1), mainCycle);
    }

    public static Date nextDate(final String asxCode, final boolean mainCycle) {
        return nextDate(asxCode, mainCycle, new Date());
    }

    /** Next ASX code following the given date. */
    public static String nextCode(final Date d, final boolean mainCycle) {
        return code(nextDate(d, mainCycle));
    }

    public static String nextCode(final Date d) {
        return nextCode(d, true);
    }

    public static String nextCode() {
        return nextCode(new Date(), true);
    }

    /** Next ASX code following the given code. */
    public static String nextCode(final String asxCode, final boolean mainCycle, final Date referenceDate) {
        return code(nextDate(asxCode, mainCycle, referenceDate));
    }
}
