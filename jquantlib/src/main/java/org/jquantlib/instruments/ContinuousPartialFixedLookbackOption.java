/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2014 Francois Botha

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

package org.jquantlib.instruments;

import org.jquantlib.QL;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.lang.reflect.ReflectConstants;
import org.jquantlib.pricingengines.GenericEngine;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.time.Date;

/**
 * Continuous-partial-fixed lookback option.
 *
 * <p>For a partial-time fixed-strike lookback option, the lookback period
 * starts at a predetermined date after the initialization date of the option. Heynen-Kat (1994) analytic pricing.
 *
 * <p>Port of QuantLib v1.42.1
 * {@code QuantLib::ContinuousPartialFixedLookbackOption} ({@code ql/instruments/lookbackoption.hpp}).
 */
public class ContinuousPartialFixedLookbackOption extends ContinuousFixedLookbackOption {

    protected Date lookbackPeriodStart;

    public ContinuousPartialFixedLookbackOption(final Date lookbackPeriodStart, final StrikedTypePayoff payoff,
            final Exercise exercise) {
        super(0.0, payoff, exercise);
        this.lookbackPeriodStart = lookbackPeriodStart;
    }

    @Override
    public void setupArguments(final PricingEngine.Arguments arguments) {
        super.setupArguments(arguments);
        QL.require(ContinuousPartialFixedLookbackOption.Arguments.class.isAssignableFrom(arguments.getClass()),
                ReflectConstants.WRONG_ARGUMENT_TYPE);
        final ContinuousPartialFixedLookbackOption.ArgumentsImpl a = (ContinuousPartialFixedLookbackOption.ArgumentsImpl) arguments;
        a.lookbackPeriodStart = lookbackPeriodStart;
    }

    //
    // public inner classes
    //

    public static class ArgumentsImpl extends ContinuousFixedLookbackOption.ArgumentsImpl
            implements ContinuousPartialFixedLookbackOption.Arguments {

        public Date lookbackPeriodStart;

        public ArgumentsImpl() {
            super();
        }

        @Override
        public void validate() {
            super.validate();

            QL.require(exercise instanceof EuropeanExercise, "European exercise required for partial-fixed lookback");
            final EuropeanExercise euro = (EuropeanExercise) exercise;
            QL.require(lookbackPeriodStart.le(euro.lastDate()),
                    "lookback start date must be earlier than exercise date");
        }
    }

    public static class ResultsImpl extends ContinuousFixedLookbackOption.ResultsImpl
            implements ContinuousPartialFixedLookbackOption.Results { /* marking */
    }

    public static abstract class EngineImpl extends
            GenericEngine< ContinuousPartialFixedLookbackOption.ArgumentsImpl, ContinuousPartialFixedLookbackOption.ResultsImpl > {

        protected EngineImpl() {
            super(new ArgumentsImpl(), new ResultsImpl());
        }
    }
}
