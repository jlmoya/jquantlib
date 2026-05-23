/*
 Copyright (C) 2007 Richard Gomes

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
 Copyright (C) 2003, 2004 Ferdinando Ametrano
 Copyright (C) 2004, 2007 StatPro Italia srl

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

/*! \file asianoption.hpp
 \brief Asian option on a single asset
 */

package org.jquantlib.instruments;

import org.jquantlib.QL;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.lang.reflect.ReflectConstants;
import org.jquantlib.pricingengines.GenericEngine;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.time.Date;

/**
 * Description of the terms and conditions of a discrete average out fixed strike option.
 *
 * @author <Richard Gomes>
 */
public class ContinuousAveragingAsianOption extends OneAssetOption {

    protected AverageType averageType;
    protected Date startDate;

    /**
     * Unseasoned (fresh) continuously-averaging Asian option — averaging has not yet started.
     */
    public ContinuousAveragingAsianOption(final AverageType averageType, final StrikedTypePayoff payoff,
            final Exercise exercise) {
        super(payoff, exercise);
        this.averageType = averageType;
        this.startDate = null;
    }

    /**
     * Seasoned continuously-averaging Asian option — averaging has already started.  The {@code startDate} is a
     * contract term specifying when averaging began; the current average (market data) should be provided to the
     * pricing engine.
     *
     * <p>Port of {@code ContinuousAveragingAsianOption(Average::Type, Date,
     * StrikedTypePayoff, Exercise)} from QuantLib v1.42.1.
     */
    public ContinuousAveragingAsianOption(final AverageType averageType, final Date startDate,
            final StrikedTypePayoff payoff, final Exercise exercise) {
        super(payoff, exercise);
        this.averageType = averageType;
        this.startDate = startDate;
    }

    @Override
    public void setupArguments(final PricingEngine.Arguments arguments) /* @ReadOnly */ {
        super.setupArguments(arguments);
        QL.require(ContinuousAveragingAsianOption.Arguments.class.isAssignableFrom(arguments.getClass()),
                ReflectConstants.WRONG_ARGUMENT_TYPE); // QA:[RG]::verified
        final ContinuousAveragingAsianOption.ArgumentsImpl a = (ContinuousAveragingAsianOption.ArgumentsImpl) arguments;
        a.averageType = averageType;
        a.startDate = startDate;
    }

    //
    // public inner classes
    //

    public static class ArgumentsImpl extends OneAssetOption.ArgumentsImpl
            implements ContinuousAveragingAsianOption.Arguments {

        private static final String UNSPECIFIED_AVERAGE_TYPE = "unspecified average type";

        //
        // public fields
        //

        public AverageType averageType;
        public Date startDate;

        //
        // public constructors
        //

        public ArgumentsImpl() {
            super();
        }

        //
        // public methods
        //

        @Override
        public void validate() /*@ReadOnly*/ {
            super.validate();
            QL.require(averageType != null, UNSPECIFIED_AVERAGE_TYPE); // QA:[RG]::verified
        }

    }

    public static class ResultsImpl extends OneAssetOption.ResultsImpl
            implements ContinuousAveragingAsianOption.Results { /* marking interface */
    }

    /**
     * Asian option on a single asset
     * <p>
     * Description of the terms and conditions of a continuous average out fixed strike option.
     *
     * @author <Richard Gomes>
     */
    static public abstract class EngineImpl extends
            GenericEngine< ContinuousAveragingAsianOption.ArgumentsImpl, ContinuousAveragingAsianOption.ResultsImpl >
            implements ContinuousAveragingAsianOption.Results {

        /**
         * Extra arguments for single-asset continuous-average Asian option
         */
        protected EngineImpl() {
            super(new ArgumentsImpl(), new ResultsImpl());
        }

    }

}
