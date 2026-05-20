/*
 Copyright (C) 2026 Jose Moya (Java port)

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
 Copyright (C) 2003, 2004, 2005, 2006, 2007, 2008 StatPro Italia srl
 Copyright (C) 2009, 2015 Ferdinando Ametrano
 Copyright (C) 2015 Paolo Mazzocchi

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
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;

/**
 * Term structure based on linear interpolation of zero yields.
 *
 * <p>Faithful Java port of the C++ v1.42.1 typedef
 * {@code typedef InterpolatedZeroCurve<Linear> ZeroCurve;}
 * in {@code ql/termstructures/yield/zerocurve.hpp:112}.
 *
 * <p>Java cannot express the C++ typedef as a single declaration because
 * {@code InterpolatedZeroCurve} is a generic class needing a runtime
 * {@code Class<I>} witness. This wrapper hard-codes {@link Linear} and
 * provides the same simple constructor overloads as the C++ template.
 */
public class ZeroCurve extends InterpolatedZeroCurve< Linear > {

    //-- ZeroCurve(const std::vector<Date>& dates,
    //--          const std::vector<Rate>& yields,
    //--          const DayCounter& dayCounter, ...)
    //-- in ql/termstructures/yield/zerocurve.hpp:45 (typedef of InterpolatedZeroCurve<Linear>)
    public ZeroCurve(final Date[] dates, final double[] yields, final DayCounter dc) {
        super(Linear.class, dates, yields, dc);
    }

    public ZeroCurve(final Date[] dates, final double[] yields, final DayCounter dc, final Calendar calendar) {
        super(Linear.class, dates, yields, dc, calendar);
    }
}
