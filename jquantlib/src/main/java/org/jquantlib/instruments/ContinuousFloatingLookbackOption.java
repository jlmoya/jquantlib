/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2006 Warren Chou
 Copyright (C) 2007 StatPro Italia srl
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
import org.jquantlib.exercise.Exercise;
import org.jquantlib.lang.reflect.ReflectConstants;
import org.jquantlib.math.Constants;
import org.jquantlib.pricingengines.GenericEngine;
import org.jquantlib.pricingengines.PricingEngine;

/**
 * Continuous-floating lookback option.
 *
 * <p>Port of QuantLib v1.42.1 {@code QuantLib::ContinuousFloatingLookbackOption}
 * ({@code ql/instruments/lookbackoption.hpp}).
 */
public class ContinuousFloatingLookbackOption extends OneAssetOption {

    protected double minmax;

    public ContinuousFloatingLookbackOption(final double currentMinmax, final TypePayoff payoff,
            final Exercise exercise) {
        super(payoff, exercise);
        this.minmax = currentMinmax;
    }

    @Override
    public void setupArguments(final PricingEngine.Arguments arguments) {
        super.setupArguments(arguments);
        QL.require(ContinuousFloatingLookbackOption.Arguments.class.isAssignableFrom(arguments.getClass()),
                ReflectConstants.WRONG_ARGUMENT_TYPE);
        final ContinuousFloatingLookbackOption.ArgumentsImpl a = (ContinuousFloatingLookbackOption.ArgumentsImpl) arguments;
        a.minmax = minmax;
    }

    //
    // public inner classes
    //

    /**
     * Arguments for the continuous-floating lookback option.
     */
    public static class ArgumentsImpl extends OneAssetOption.ArgumentsImpl
            implements ContinuousFloatingLookbackOption.Arguments {

        public double minmax;

        public ArgumentsImpl() {
            this.minmax = Constants.NULL_REAL;
        }

        @Override
        public void validate() {
            super.validate();
            QL.require(minmax != Constants.NULL_REAL, "null prior extremum");
            QL.require(minmax >= 0.0, "nonnegative prior extremum required: " + minmax + " not allowed");
        }
    }

    public static class ResultsImpl extends OneAssetOption.ResultsImpl
            implements ContinuousFloatingLookbackOption.Results { /* marking */
    }

    /**
     * Continuous-floating lookback option engine base class.
     */
    public static abstract class EngineImpl extends
            GenericEngine< ContinuousFloatingLookbackOption.ArgumentsImpl, ContinuousFloatingLookbackOption.ResultsImpl > {

        protected EngineImpl() {
            super(new ArgumentsImpl(), new ResultsImpl());
        }
    }
}
