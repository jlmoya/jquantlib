/*
 Copyright (C) 2011 Chris Kenyon (C++)

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

package org.jquantlib.experimental.inflation;

import org.jquantlib.QL;
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
 * <p>Port of {@code ql/experimental/inflation/cpicapfloorengines.{hpp,cpp}}
 * from C++ QuantLib v1.42.1.
 *
 * <p>This engine only adds timing functionality (e.g. different lag) on top of
 * an existing interpolated price surface.
 *
 * @author Jose Moya
 */
public class InterpolatingCPICapFloorEngine extends CPICapFloor.Engine {

    protected final Handle<CPICapFloorTermPriceSurface> priceSurf_;

    public InterpolatingCPICapFloorEngine(final Handle<CPICapFloorTermPriceSurface> priceSurf) {
        super();
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
        double npv;

        final CPICapFloor.ArgumentsImpl args = (CPICapFloor.ArgumentsImpl) arguments_;

        // What is the difference between the observationLag of the surface and
        // the observationLag of the cap/floor? Will fail if units are different.
        final Period lagDiff = args.observationLag.sub(priceSurf_.currentLink().observationLag());
        QL.require(lagDiff.ge(new Period(0, TimeUnit.Months)),
                "InterpolatingCPICapFloorEngine: lag difference must be non-negative");

        // Effective maturity to look up in the price surface.
        final Date effectiveMaturity = args.payDate.sub(lagDiff);

        if (args.observationInterpolation == CPI.InterpolationType.AsIndex) {
            // Same as index — read price surface directly.
            if (args.type == Option.Type.Call) {
                npv = priceSurf_.currentLink().capPrice(effectiveMaturity, args.strike);
            } else {
                npv = priceSurf_.currentLink().floorPrice(effectiveMaturity, args.strike);
            }
        } else {
            final Pair<Date, Date> dd = InflationTermStructure.inflationPeriod(
                    effectiveMaturity, args.index.frequency());

            double priceStart;
            if (args.type == Option.Type.Call) {
                priceStart = priceSurf_.currentLink().capPrice(dd.first(), args.strike);
            } else {
                priceStart = priceSurf_.currentLink().floorPrice(dd.first(), args.strike);
            }

            if (args.observationInterpolation == CPI.InterpolationType.Flat) {
                // Flat: price for the first day in the period.
                npv = priceStart;
            } else {
                // Linear interpolation across the period.
                double priceEnd;
                final Date endPlusOne = dd.second().add(new Period(1, TimeUnit.Days));
                if (args.type == Option.Type.Call) {
                    priceEnd = priceSurf_.currentLink().capPrice(endPlusOne, args.strike);
                } else {
                    priceEnd = priceSurf_.currentLink().floorPrice(endPlusOne, args.strike);
                }
                final long num = effectiveMaturity.sub(dd.first());
                final long den = endPlusOne.sub(dd.first());
                npv = priceStart + (priceEnd - priceStart) * ((double) num) / ((double) den);
            }
        }

        ((CPICapFloor.ResultsImpl) results_).value = npv;
    }
}
