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
*/

/*
 Copyright (C) 2001, 2002, 2003 Sadruddin Rejeb

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.pricingengines.capfloor;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.CapFloor;
import org.jquantlib.instruments.Option;
import org.jquantlib.model.AffineModel;
import org.jquantlib.model.shortrate.onefactormodels.TermStructureConsistentModel;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;

/**
 * Analytic engine for cap/floor.
 *
 * <p>Mirrors C++ QuantLib v1.42.1
 * {@code ql/pricingengines/capfloor/analyticcapfloorengine.{hpp,cpp}}
 * (the C++ class is {@code GenericModelEngine<AffineModel,
 * CapFloor::arguments, CapFloor::results>}). Java's
 * {@link CapFloor.Engine} already extends
 * {@code GenericEngine<CapFloor.Arguments, CapFloor.Results>}; this class
 * adds the {@link AffineModel} hook explicitly because Java does not have
 * {@code GenericModelEngine}.
 *
 * <p>Phase 2f WI-1.
 */
public class AnalyticCapFloorEngine extends CapFloor.Engine {

    private final AffineModel model_;
    private final Handle<YieldTermStructure> termStructure_;

    /**
     * @param model affine short-rate model providing
     *        {@link AffineModel#discount(double)} and
     *        {@link AffineModel#discountBondOption}
     * @param termStructure fallback yield curve, only consulted when
     *        {@code model} is not a {@link TermStructureConsistentModel}
     *        (e.g. CIR, BlackKarasinski). Hull-White / G2 carry their own.
     */
    public AnalyticCapFloorEngine(
            final AffineModel model,
            final Handle<YieldTermStructure> termStructure) {
        super();
        this.model_ = model;
        this.termStructure_ = termStructure;
        if (this.model_ != null) {
            this.model_.addObserver(this);
        }
        if (this.termStructure_ != null) {
            this.termStructure_.addObserver(this);
        }
    }

    /** Convenience overload used when the model carries its own term structure. */
    public AnalyticCapFloorEngine(final AffineModel model) {
        this(model, new Handle<YieldTermStructure>());
    }

    /**
     * Mirrors C++ analyticcapfloorengine.cpp::calculate(). The {@code
     * includeReferenceDateEvents} branch is not exposed by Java
     * {@link org.jquantlib.Settings}; we use the conservative
     * {@code paymentTime > 0.0} criterion which matches the C++ default
     * ({@code Settings::includeReferenceDateEvents() == false}).
     */
    @Override
    public void calculate() {
        QL.require(model_ != null, "null model");

        final Date referenceDate;
        final DayCounter dayCounter;
        if (model_ instanceof TermStructureConsistentModel) {
            final Handle<YieldTermStructure> ts =
                    ((TermStructureConsistentModel) model_).termStructure();
            referenceDate = ts.currentLink().referenceDate();
            dayCounter = ts.currentLink().dayCounter();
        } else if (model_ instanceof org.jquantlib.model.TermStructureConsistentModel) {
            final Handle<YieldTermStructure> ts =
                    ((org.jquantlib.model.TermStructureConsistentModel) model_).termStructure();
            referenceDate = ts.currentLink().referenceDate();
            dayCounter = ts.currentLink().dayCounter();
        } else {
            QL.require(termStructure_ != null && !termStructure_.empty(),
                    "no term structure given to non-TS-consistent model");
            referenceDate = termStructure_.currentLink().referenceDate();
            dayCounter = termStructure_.currentLink().dayCounter();
        }

        final CapFloor.ArgumentsImpl arguments = (CapFloor.ArgumentsImpl) arguments_;
        final CapFloor.ResultsImpl results = (CapFloor.ResultsImpl) results_;

        double value = 0.0;
        final CapFloor.Type type = arguments.type;
        final int nPeriods = arguments.endDates.length;

        for (int i = 0; i < nPeriods; i++) {
            final double fixingTime = dayCounter.yearFraction(
                    referenceDate, arguments.fixingDates[i]);
            final double paymentTime = dayCounter.yearFraction(
                    referenceDate, arguments.endDates[i]);

            // C++ uses includeReferenceDateEvents to choose between >= and >;
            // Java Settings doesn't expose that toggle, default behavior
            // matches C++ default (events on the ref date are excluded).
            final boolean notExpired = paymentTime > 0.0;
            if (!notExpired) {
                continue;
            }

            final double tenor = arguments.accrualTimes[i];
            final double fixing = arguments.forwards[i];

            if (fixingTime <= 0.0) {
                // Past fixing — collapse to a discounted intrinsic payoff.
                if (type == CapFloor.Type.Cap || type == CapFloor.Type.Collar) {
                    final double discount = model_.discount(paymentTime);
                    final double strike = arguments.capRates[i];
                    value += discount * arguments.nominals[i] * tenor
                            * arguments.gearings[i]
                            * Math.max(0.0, fixing - strike);
                }
                if (type == CapFloor.Type.Floor || type == CapFloor.Type.Collar) {
                    final double discount = model_.discount(paymentTime);
                    final double strike = arguments.floorRates[i];
                    final double mult = (type == CapFloor.Type.Floor) ? 1.0 : -1.0;
                    value += discount * arguments.nominals[i] * tenor
                            * mult * arguments.gearings[i]
                            * Math.max(0.0, strike - fixing);
                }
            } else {
                final double maturity = dayCounter.yearFraction(
                        referenceDate, arguments.startDates[i]);
                if (type == CapFloor.Type.Cap || type == CapFloor.Type.Collar) {
                    final double temp = 1.0 + arguments.capRates[i] * tenor;
                    value += arguments.nominals[i]
                            * arguments.gearings[i] * temp
                            * model_.discountBondOption(Option.Type.Put,
                                    1.0 / temp, maturity, paymentTime);
                }
                if (type == CapFloor.Type.Floor || type == CapFloor.Type.Collar) {
                    final double temp = 1.0 + arguments.floorRates[i] * tenor;
                    final double mult = (type == CapFloor.Type.Floor) ? 1.0 : -1.0;
                    value += arguments.nominals[i]
                            * arguments.gearings[i] * temp * mult
                            * model_.discountBondOption(Option.Type.Call,
                                    1.0 / temp, maturity, paymentTime);
                }
            }
        }

        results.value = value;
    }
}
