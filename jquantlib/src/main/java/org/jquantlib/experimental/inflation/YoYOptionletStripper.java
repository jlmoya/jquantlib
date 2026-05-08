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

/*
 Copyright (C) 2009 Chris Kenyon

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/
package org.jquantlib.experimental.inflation;

import java.util.List;

import org.jquantlib.pricingengines.inflation.InflationCapFloorEngine;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Period;
import org.jquantlib.util.Pair;

/**
 * Interface for inflation cap stripping (i.e. from price surfaces).
 *
 * <p>Mirrors C++ v1.42.1 {@code QuantLib::YoYOptionletStripper}
 * ({@code ql/experimental/inflation/yoyoptionletstripper.hpp}).
 *
 * <p>Strippers return K slices of the volatility surface at a given T.
 * In {@link #initialize} they actually do the stripping along each K.
 *
 * <p>Java port note: the C++ class declares mutable protected members
 * ({@code YoYCapFloorTermPriceSurface_}, {@code p_}, {@code lag_},
 * {@code frequency_}, {@code indexIsInterpolated_}). We mirror these as
 * package-private protected fields for the same reason — concrete subclasses
 * (e.g. {@link InterpolatedYoYOptionletStripper}) populate them in
 * {@code initialize()} and read them in other operations.
 *
 * @author JQuantLib migration team (Phase 2s Track B)
 */
public abstract class YoYOptionletStripper {

    //
    // protected fields — populated by initialize()
    //

    /** Source price surface (set by {@link #initialize}). */
    protected YoYCapFloorTermPriceSurfaceLike yoyCapFloorTermPriceSurface_;

    /** Cap/floor pricing engine used during stripping. */
    protected InflationCapFloorEngine p_;

    /** Observation lag, copied from the surface for convenience. */
    protected Period lag_;

    /** Sampling frequency, copied from the surface. */
    protected Frequency frequency_;

    /** Whether the index is point-in-time interpolated, copied from the surface. */
    protected boolean indexIsInterpolated_;

    //
    // YoYOptionletStripper interface
    //

    /**
     * Mirrors C++ {@code virtual void initialize(...) const = 0;}.
     * Strips optionlets out of the price surface using the given cap/floor
     * engine and an initial slope assumption for the per-K base vol.
     */
    public abstract void initialize(YoYCapFloorTermPriceSurfaceLike capFloorPrices,
                                    InflationCapFloorEngine pricer,
                                    double slope);

    /** Smallest strike in the surface. */
    public abstract double minStrike();

    /** Largest strike in the surface. */
    public abstract double maxStrike();

    /** All strikes covered by the stripper (in surface order). */
    public abstract List<Double> strikes();

    /**
     * Return a (strikes, volatilities) slice of the stripped surface at
     * date {@code d}. Mirrors C++
     * {@code virtual std::pair<std::vector<Rate>, std::vector<Volatility>> slice(const Date&) const = 0;}.
     */
    public abstract Pair<List<Double>, List<Double>> slice(Date d);
}
