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
 * Continuous-partial-floating lookback option.
 *
 * <p>For a partial-time floating-strike lookback option, the lookback period
 * starts at time zero and ends at an arbitrary date before expiration. Except for the partial lookback period, the
 * option is similar to a floating-strike lookback option. Heynen-Kat (1994) analytic pricing.
 *
 * <p>Port of QuantLib v1.42.1
 * {@code QuantLib::ContinuousPartialFloatingLookbackOption} ({@code ql/instruments/lookbackoption.hpp}).
 */
public class ContinuousPartialFloatingLookbackOption extends ContinuousFloatingLookbackOption {

    protected double lambda;
    protected Date lookbackPeriodEnd;

    public ContinuousPartialFloatingLookbackOption(final double currentMinmax, final double lambda,
            final Date lookbackPeriodEnd, final TypePayoff payoff, final Exercise exercise) {
        super(currentMinmax, payoff, exercise);
        this.lambda = lambda;
        this.lookbackPeriodEnd = lookbackPeriodEnd;
    }

    @Override
    public void setupArguments(final PricingEngine.Arguments arguments) {
        super.setupArguments(arguments);
        QL.require(ContinuousPartialFloatingLookbackOption.Arguments.class.isAssignableFrom(arguments.getClass()),
                ReflectConstants.WRONG_ARGUMENT_TYPE);
        final ContinuousPartialFloatingLookbackOption.ArgumentsImpl a = (ContinuousPartialFloatingLookbackOption.ArgumentsImpl) arguments;
        a.lambda = lambda;
        a.lookbackPeriodEnd = lookbackPeriodEnd;
    }

    //
    // public inner classes
    //

    public static class ArgumentsImpl extends ContinuousFloatingLookbackOption.ArgumentsImpl
            implements ContinuousPartialFloatingLookbackOption.Arguments {

        public double lambda;
        public Date lookbackPeriodEnd;

        public ArgumentsImpl() {
            super();
        }

        @Override
        public void validate() {
            super.validate();

            QL.require(exercise instanceof EuropeanExercise,
                    "European exercise required for partial-floating lookback");
            final EuropeanExercise euro = (EuropeanExercise) exercise;
            QL.require(lookbackPeriodEnd.le(euro.lastDate()), "lookback start date must be earlier than exercise date");

            QL.require(payoff instanceof FloatingTypePayoff, "Floating-type payoff required");
            final FloatingTypePayoff fp = (FloatingTypePayoff) payoff;
            if ( fp.optionType() == Option.Type.Call ) {
                QL.require(lambda >= 1.0, "lambda should be greater than or equal to 1 for calls");
            } else if ( fp.optionType() == Option.Type.Put ) {
                QL.require(lambda <= 1.0, "lambda should be smaller than or equal to 1 for puts");
            }
        }
    }

    public static class ResultsImpl extends ContinuousFloatingLookbackOption.ResultsImpl
            implements ContinuousPartialFloatingLookbackOption.Results { /* marking */
    }

    public static abstract class EngineImpl extends
            GenericEngine< ContinuousPartialFloatingLookbackOption.ArgumentsImpl, ContinuousPartialFloatingLookbackOption.ResultsImpl > {

        protected EngineImpl() {
            super(new ArgumentsImpl(), new ResultsImpl());
        }
    }
}
