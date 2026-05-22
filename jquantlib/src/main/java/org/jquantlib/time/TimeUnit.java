/*
 Copyright (C) 2008 Richard Gomes

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
 Copyright (C) 2004, 2005, 2006 Ferdinando Ametrano
 Copyright (C) 2006 Katiuscia Manzoni
 Copyright (C) 2000, 2001, 2002, 2003 RiskMap srl
 Copyright (C) 2003, 2004, 2005, 2006 StatPro Italia srl

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

package org.jquantlib.time;

/**
 * Time units
 *
 * <p>Phase 1.3 D5-D-intraday: extended with {@code Hours}, {@code Minutes},
 * {@code Seconds}, {@code Milliseconds}, {@code Microseconds} to mirror
 * C++ v1.42.1 {@code ql/time/timeunit.hpp:37-46}. The sub-day units are
 * only used by intraday-aware Date arithmetic
 * ({@link Period} + {@link Date} {@code QL_HIGH_RESOLUTION_DATE} branch).
 *
 * @author Richard Gomes
 */
public enum TimeUnit {
    Days, Weeks, Months, Years, Hours, Minutes, Seconds, Milliseconds, Microseconds;

    /**
     * Returns the name of time unit in long format (e.g. "week")
     *
     * @return the name of time unit in long format (e.g. "week")
     */
    public String getLongFormat() {
        return getLongFormatString();
    }

    /**
     * Returns the name of time unit in short format (e.g. "w")
     *
     * @return the name of time unit in short format (e.g. "w")
     */
    public String getShortFormat() {
        return getShortFormatString();
    }

    /**
     * Output time units in long format (e.g. "week")
     *
     * @note message in singular form
     */
    private String getLongFormatString() {
        StringBuilder sb = new StringBuilder();
        sb.append(toString().toLowerCase());
        sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    /**
     * Output time units in short format (e.g. "W"). Mirrors C++ short-period
     * formatter (ql/time/period.cpp:421-441): sub-day units render as
     * lowercase {@code "h"/"m"/"s"} (no millisecond/microsecond letters in
     * the C++ formatter, so we fall back to {@code "ms"/"us"}).
     */
    private String getShortFormatString() {
        switch (this) {
            case Hours:        return "h";
            case Minutes:      return "min"; // disambiguate from Months "M"
            case Seconds:      return "s";
            case Milliseconds: return "ms";
            case Microseconds: return "us";
            default:           return String.valueOf(toString().charAt(0));
        }
    }

}
