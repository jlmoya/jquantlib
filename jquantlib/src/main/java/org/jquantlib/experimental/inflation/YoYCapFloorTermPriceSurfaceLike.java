/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is released under the BSD License.

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
package org.jquantlib.experimental.inflation;

import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.YoYInflationIndex;
import org.jquantlib.termstructures.YoYInflationTermStructure;
import org.jquantlib.time.*;

import java.util.List;

/**
 * Forward-declared interface placeholder for {@code YoYCapFloorTermPriceSurface}.
 *
 * <p>The concrete C++ class
 * {@code ql/experimental/inflation/yoycapfloortermpricesurface.hpp} is scheduled to land in Phase 2s Track C. Track B
 * (this track) needs to reference its API surface for {@link YoYOptionletStripper},
 * {@link InterpolatedYoYOptionletStripper}, and {@link KInterpolatedYoYOptionletVolatilitySurface}.
 *
 * <p>Strategy 1 pattern: define the interface here, in the same package as
 * Track B, capturing exactly the methods Track B needs. When Track C lands, its concrete
 * {@code YoYCapFloorTermPriceSurface} implements this interface (declaring
 * {@code implements YoYCapFloorTermPriceSurfaceLike}). No further refactoring is needed in Track B.
 *
 * <p>This mirrors the same pattern used in Phase 2r Track C for the
 * {@code YoYInflationCapFloorEngine} interface.
 *
 * @author JQuantLib migration team (Phase 2s Track B)
 */
public interface YoYCapFloorTermPriceSurfaceLike {

    /** Reference date of the surface (cf. {@code TermStructure::referenceDate()}). */
    Date referenceDate();

    /** Day counter used to compute year fractions. */
    DayCounter dayCounter();

    /** Calendar (cf. {@code TermStructure::calendar()}). */
    Calendar calendar();

    /** Time-from-reference for a given date (cf. {@code TermStructure::timeFromReference()}). */
    double timeFromReference(Date d);

    /** Business-day convention used for date adjustments. */
    BusinessDayConvention businessDayConvention();

    /** YoY index this surface is linked to. */
    YoYInflationIndex yoyIndex();

    /** Implied YoY term structure (extracted from cap/floor data + nominal curve). */
    YoYInflationTermStructure YoYTS();

    /** Observation lag for the YoY rate (typically 2-3 months). */
    Period observationLag();

    /** Sampling frequency of the underlying YoY rate. */
    Frequency frequency();

    /** Whether the underlying index is point-in-time interpolated. */
    boolean indexIsInterpolated();

    /** Number of fixing days for the YoY index. */
    int fixingDays();

    /** Surface base date (typically baseDate of the YoY index). */
    Date baseDate();

    /** Cap strikes (sorted ascending). */
    List< Double > capStrikes();

    /** Floor strikes (sorted ascending). */
    List< Double > floorStrikes();

    /** All strikes (caps and floors merged + sorted). */
    List< Double > strikes();

    /** Available cap/floor maturities (tenors). */
    List< Period > maturities();

    /** Smallest maturity present in the surface. */
    Period minMaturity();

    /** Convert a tenor to a YoY option date. */
    Date yoyOptionDateFromTenor(Period p);

    /** Cap price for a given (tenor, strike). */
    double capPrice(Period d, double strike);

    /** Floor price for a given (tenor, strike). */
    double floorPrice(Period d, double strike);

    /** Cap price for a given (date, strike). */
    double capPrice(Date d, double strike);

    /** Floor price for a given (date, strike). */
    double floorPrice(Date d, double strike);
}
