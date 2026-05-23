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

/**
 * Helper class to parse Strings to Date
 *
 * @author Srinivas Hasti
 * @author Zahid Hussain
 * @Changes: Sep 2009: Used correct method to parse date and format strings in parse method.
 *
 */
// TODO: OSGi :: remove statics
public class DateParser {

    /**
     * Convert ISO format strings to Date. Ex: 2008-03-31
     *
     * @param str
     * @return Date
     */
    public static Date parseISO(final String str) {
        QL.require(str.length() == 10 && str.charAt(4) == '-' && str.charAt(7) == '-',
                "invalid format");

        final int year = Integer.parseInt(str.substring(0, 4));
        final int month = Integer.parseInt(str.substring(5, 7));
        final int day = Integer.parseInt(str.substring(8, 10));

        final Date date = new Date(day, Month.valueOf(month), year);
        // QL.debug(date.isoDate().toString());
        return date;
    }

    /**
     * Convert the String with separator '/' to Date using the format specified.
     *
     * For example: "2008/03/31", "yyyy/MM/dd"
     *
     * @param str
     * @param fmt
     * @return Date
     */
    public static Date parse(final String str, final String fmt) {
        String[] slist = null;
        String[] flist = null;
        int d = 0, m = 0, y = 0;

        slist = str.split("/");
        flist = fmt.split("/");

        Date date;

        if ( slist.length != flist.length ) {
            date = new Date();
        } else {
            for ( int i = 0; i < flist.length; i++ ) {
                final String sub = flist[i];
                if ( sub.equalsIgnoreCase("dd") ) {
                    d = Integer.parseInt(slist[i]);
                } else if ( sub.equalsIgnoreCase("mm") ) {
                    m = Integer.parseInt(slist[i]);
                } else if ( sub.equalsIgnoreCase("yyyy") ) {
                    y = Integer.parseInt(slist[i]);
                    if ( y < 100 ) {
                        y += 2000;
                    }
                }
            }
            date = new Date(d, m, y);
        }

        // QL.debug(date.isoDate().toString());
        return date;
    }

    /**
     * Convert a date string given a C-style {@code strftime(3)} format.
     *
     * <p>Port of v1.42.1 {@code DateParser::parseFormatted}
     * ({@code ql/utilities/dataparsers.cpp:90-104}). Supports the format
     * specifiers exercised by the C++ test-suite:
     * <ul>
     *  <li>{@code %Y} — 4-digit year</li>
     *  <li>{@code %m} — 2-digit month (1-12)</li>
     *  <li>{@code %d} — 2-digit day (1-31)</li>
     * </ul>
     * Any literal characters in the format (e.g. {@code '-'}, {@code '/'}) must
     * match the corresponding positions in the input.
     *
     * <p>This implementation builds an equivalent
     * {@link java.text.SimpleDateFormat} pattern by token-substitution, then
     * delegates to it. {@code SimpleDateFormat} is lenient by default; for
     * formats without separators (e.g. {@code "%Y%m%d"}) the lenient parser
     * correctly consumes 4+2+2 digit blocks.
     *
     * @param str  the date string
     * @param fmt  the strftime-style format
     * @return parsed {@link Date}
     */
    public static Date parseFormatted(final String str, final String fmt) {
        final StringBuilder pattern = new StringBuilder();
        int i = 0;
        while (i < fmt.length()) {
            final char c = fmt.charAt(i);
            if (c == '%' && i + 1 < fmt.length()) {
                final char tok = fmt.charAt(i + 1);
                switch (tok) {
                case 'Y':
                    pattern.append("yyyy");
                    break;
                case 'm':
                    pattern.append("MM");
                    break;
                case 'd':
                    pattern.append("dd");
                    break;
                case '%':
                    pattern.append('%');
                    break;
                default:
                    QL.require(false, "unsupported strftime specifier '%" + tok + "' in format: " + fmt);
                }
                i += 2;
            } else {
                // literal: escape SimpleDateFormat metacharacters
                if (Character.isLetter(c)) {
                    pattern.append('\'').append(c).append('\'');
                } else {
                    pattern.append(c);
                }
                i++;
            }
        }

        final java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(pattern.toString());
        sdf.setLenient(false);
        final java.util.Date parsed;
        try {
            parsed = sdf.parse(str);
        } catch (final java.text.ParseException ex) {
            throw new IllegalArgumentException("unable to parse '" + str + "' with format '" + fmt + "': " + ex.getMessage(), ex);
        }
        final java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTime(parsed);
        final int day = cal.get(java.util.Calendar.DAY_OF_MONTH);
        final int month = cal.get(java.util.Calendar.MONTH) + 1; // Calendar.MONTH is 0-based
        final int year = cal.get(java.util.Calendar.YEAR);
        return new Date(day, Month.valueOf(month), year);
    }

}
