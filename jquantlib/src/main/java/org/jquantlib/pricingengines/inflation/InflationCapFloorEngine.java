/*
 Copyright (C) 2026 JQuantLib migration contributors.

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
 Copyright (C) 2009 Chris Kenyon

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.pricingengines.inflation;

import org.jquantlib.QL;
import org.jquantlib.indexes.YoYInflationIndex;
import org.jquantlib.instruments.InflationCapFloor;
import org.jquantlib.instruments.Option;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.YoYInflationTermStructure;
import org.jquantlib.termstructures.volatility.inflation.YoYOptionletVolatilitySurface;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;

/**
 * Base YoY-inflation cap/floor pricing engine.
 *
 * <p>Mirrors C++ v1.42.1 {@code QuantLib::YoYInflationCapFloorEngine}
 * ({@code ql/pricingengines/inflation/inflationcapfloorengines.{hpp,cpp}}).
 *
 * <p>The base class doesn't know what sort of vol it operates with —
 * {@link YoYInflationBlackCapFloorEngine},
 * {@link YoYInflationUnitDisplacedBlackCapFloorEngine}, and
 * {@link YoYInflationBachelierCapFloorEngine} supply the optionlet pricer
 * by overriding {@link #optionletImpl}.
 *
 * <p>The inflation index passed in must be linked to a
 * {@link YoYInflationTermStructure}, used to forecast the per-coupon YoY
 * forward rates.
 *
 * @author JQuantLib migration team (Phase 2r C.2)
 */
public abstract class InflationCapFloorEngine extends InflationCapFloor.Engine {

    private YoYInflationIndex index_;
    private Handle<YoYOptionletVolatilitySurface> volatility_;
    private final Handle<YieldTermStructure> nominalTermStructure_;

    protected InflationCapFloorEngine(final YoYInflationIndex index,
                                      final Handle<YoYOptionletVolatilitySurface> volatility,
                                      final Handle<YieldTermStructure> nominalTermStructure) {
        this.index_ = index;
        this.volatility_ = volatility;
        this.nominalTermStructure_ = nominalTermStructure;
        if (index_ != null) {
            index_.addObserver(this);
        }
        if (volatility_ != null) {
            volatility_.addObserver(this);
        }
        if (nominalTermStructure_ != null) {
            nominalTermStructure_.addObserver(this);
        }
    }

    public YoYInflationIndex index() {
        return index_;
    }

    public Handle<YoYOptionletVolatilitySurface> volatility() {
        return volatility_;
    }

    public Handle<YieldTermStructure> nominalTermStructure() {
        return nominalTermStructure_;
    }

    public void setVolatility(final Handle<YoYOptionletVolatilitySurface> v) {
        if (volatility_ != null && !volatility_.empty()) {
            volatility_.deleteObserver(this);
        }
        volatility_ = v;
        if (volatility_ != null) {
            volatility_.addObserver(this);
        }
        update();
    }

    /**
     * Subclasses must override to supply Black/UnitDisplacedBlack/Bachelier
     * formula via {@code (Option.Type, strike, forward, stdDev, d)}.
     */
    protected abstract double optionletImpl(Option.Type type, double strike,
                                            double forward, double stdDev,
                                            double d);

    /**
     * Mirrors C++ {@code calculate()} —
     * inflationcapfloorengines.cpp:51-128.
     */
    @Override
    public void calculate() {
        final InflationCapFloor.ArgumentsImpl arguments =
                (InflationCapFloor.ArgumentsImpl) arguments_;
        final InflationCapFloor.ResultsImpl results =
                (InflationCapFloor.ResultsImpl) results_;

        QL.require(nominalTermStructure_ != null && !nominalTermStructure_.empty(),
                "no nominal term structure");
        QL.require(index_ != null, "no inflation index");
        QL.require(volatility_ != null && !volatility_.empty(),
                "no inflation vol surface");

        double value = 0.0;
        final int optionlets = arguments.startDates.length;
        final double[] values = new double[optionlets];
        final double[] stdDevs = new double[optionlets];
        final double[] forwards = new double[optionlets];
        final InflationCapFloor.Type type = arguments.type;

        final YoYInflationTermStructure yoyTS =
                index_.yoyInflationTermStructure().currentLink();
        final Date settlement = nominalTermStructure_.currentLink().referenceDate();

        for (int i = 0; i < optionlets; ++i) {
            final Date paymentDate = arguments.payDates[i];
            if (paymentDate.gt(settlement)) {
                final double d = arguments.nominals[i]
                        * arguments.gearings[i]
                        * nominalTermStructure_.currentLink().discount(paymentDate)
                        * arguments.accrualTimes[i];

                forwards[i] = yoyTS.yoyRate(arguments.fixingDates[i]);
                final double forward = forwards[i];

                final Date fixingDate = arguments.fixingDates[i];
                double sqrtTime = 0.0;
                if (fixingDate.gt(volatility_.currentLink().baseDate())) {
                    sqrtTime = Math.sqrt(volatility_.currentLink().timeFromBase(fixingDate));
                }

                if (type == InflationCapFloor.Type.Cap
                        || type == InflationCapFloor.Type.Collar) {
                    final double strike = arguments.capRates[i];
                    if (sqrtTime > 0.0) {
                        stdDevs[i] = Math.sqrt(volatility_.currentLink()
                                .totalVariance(fixingDate, strike,
                                        new Period(0, TimeUnit.Days), false));
                    }
                    values[i] = optionletImpl(Option.Type.Call, strike,
                            forward, stdDevs[i], d);
                }
                if (type == InflationCapFloor.Type.Floor
                        || type == InflationCapFloor.Type.Collar) {
                    final double strike = arguments.floorRates[i];
                    if (sqrtTime > 0.0) {
                        stdDevs[i] = Math.sqrt(volatility_.currentLink()
                                .totalVariance(fixingDate, strike,
                                        new Period(0, TimeUnit.Days), false));
                    }
                    final double floorlet = optionletImpl(Option.Type.Put, strike,
                            forward, stdDevs[i], d);
                    if (type == InflationCapFloor.Type.Floor) {
                        values[i] = floorlet;
                    } else {
                        values[i] -= floorlet;
                    }
                }
                value += values[i];
            }
        }
        results.value = value;
        results.additionalResults().put("optionletsPrice", values);
        results.additionalResults().put("optionletsAtmForward", forwards);
        if (type != InflationCapFloor.Type.Collar) {
            results.additionalResults().put("optionletsStdDev", stdDevs);
        }
    }
}
