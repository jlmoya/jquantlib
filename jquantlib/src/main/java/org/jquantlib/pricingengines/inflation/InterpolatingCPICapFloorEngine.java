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
 Copyright (C) 2011 Chris Kenyon

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.pricingengines.inflation;

import org.jquantlib.QL;
import org.jquantlib.experimental.inflation.CPICapFloorTermPriceSurface;
import org.jquantlib.indexes.CPI;
import org.jquantlib.instruments.CPICapFloor;
import org.jquantlib.instruments.Option;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.InflationTermStructure;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.util.Pair;

/**
 * Engine for CPI cap/floors based on a price surface.
 *
 * <p>Mirrors C++ v1.42.1 {@code QuantLib::InterpolatingCPICapFloorEngine}
 * ({@code ql/experimental/inflation/cpicapfloorengines.{hpp,cpp}}).
 *
 * <p>This engine only adds timing functionality (e.g. different lag) w.r.t. an
 * existing interpolated price surface.
 *
 * @author JQuantLib migration team (Phase 2s C.2)
 */
public class InterpolatingCPICapFloorEngine extends CPICapFloor.Engine {

    protected Handle<CPICapFloorTermPriceSurface> priceSurf_;

    public InterpolatingCPICapFloorEngine(final Handle<CPICapFloorTermPriceSurface> priceSurf) {
        this.priceSurf_ = priceSurf;
        if (priceSurf_ != null) {
            priceSurf_.addObserver(this);
        }
    }

    public String name() {
        return "InterpolatingCPICapFloorEngine";
    }

    @Override
    public void calculate() {
        final CPICapFloor.ArgumentsImpl arguments =
                (CPICapFloor.ArgumentsImpl) arguments_;
        final CPICapFloor.ResultsImpl results =
                (CPICapFloor.ResultsImpl) results_;

        double npv = 0.0;

        // Lag-difference between surface and arguments
        final Period lagDiff = arguments.observationLag.sub(priceSurf_.currentLink().observationLag());
        QL.require(lagDiff.ge(new Period(0, TimeUnit.Months)),
                "InterpolatingCPICapFloorEngine: lag difference must be non-negative: " + lagDiff);

        // Effective maturity
        final Date effectiveMaturity = arguments.payDate.sub(lagDiff);

        if (arguments.observationInterpolation == CPI.InterpolationType.AsIndex) {
            if (arguments.type == Option.Type.Call) {
                npv = priceSurf_.currentLink().capPrice(effectiveMaturity, arguments.strike);
            } else {
                npv = priceSurf_.currentLink().floorPrice(effectiveMaturity, arguments.strike);
            }
        } else {
            final Pair<Date, Date> dd = InflationTermStructure.inflationPeriod(
                    effectiveMaturity, arguments.index.frequency());
            double priceStart;
            if (arguments.type == Option.Type.Call) {
                priceStart = priceSurf_.currentLink().capPrice(dd.first(), arguments.strike);
            } else {
                priceStart = priceSurf_.currentLink().floorPrice(dd.first(), arguments.strike);
            }

            if (arguments.observationInterpolation == CPI.InterpolationType.Flat) {
                npv = priceStart;
            } else {
                // Linear interpolation
                final Date oneDayAfterEnd = dd.second().add(new Period(1, TimeUnit.Days));
                double priceEnd;
                if (arguments.type == Option.Type.Call) {
                    priceEnd = priceSurf_.currentLink().capPrice(oneDayAfterEnd, arguments.strike);
                } else {
                    priceEnd = priceSurf_.currentLink().floorPrice(oneDayAfterEnd, arguments.strike);
                }
                final long num = effectiveMaturity.sub(dd.first());
                final long den = oneDayAfterEnd.sub(dd.first());
                npv = priceStart + (priceEnd - priceStart) * (double) num / (double) den;
            }
        }

        results.value = npv;
    }
}
