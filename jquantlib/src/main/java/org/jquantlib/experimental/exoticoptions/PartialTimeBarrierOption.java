/*
 Copyright (C) 2026 JQuantLib migration

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
 Copyright (C) 2014 Master IMAFA - Polytech'Nice Sophia - Université de Nice Sophia Antipolis
*/

package org.jquantlib.experimental.exoticoptions;

import org.jquantlib.QL;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.BarrierType;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.lang.reflect.ReflectConstants;
import org.jquantlib.math.Constants;
import org.jquantlib.pricingengines.GenericEngine;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.time.Date;
import org.jquantlib.util.Observer;

/**
 * Partial-time barrier option on a single asset.
 * <p>
 * A particular type of barrier option in which the barrier is only monitored for a part of
 * the option's lifetime; either from start to a so-called cover event, or from the cover event
 * to the exercise date.
 * <p>
 * Mirrors {@code QuantLib::PartialTimeBarrierOption} from
 * {@code ql/instruments/partialtimebarrieroption.hpp} (v1.42.1).
 *
 * @author JQuantLib migration
 */
public class PartialTimeBarrierOption extends OneAssetOption {

    //
    // protected fields
    //

    protected BarrierType barrierType;
    protected PartialBarrier barrierRange;
    protected double barrier;
    protected double rebate;
    protected Date coverEventDate;


    //
    // public constructors
    //

    public PartialTimeBarrierOption(
            final BarrierType barrierType,
            final PartialBarrier barrierRange,
            final double barrier,
            final double rebate,
            final Date coverEventDate,
            final StrikedTypePayoff payoff,
            final Exercise exercise) {
        super(payoff, exercise);
        this.barrierType = barrierType;
        this.barrierRange = barrierRange;
        this.barrier = barrier;
        this.rebate = rebate;
        this.coverEventDate = coverEventDate;
    }


    //
    // overrides OneAssetOption
    //

    @Override
    public void setupArguments(final PricingEngine.Arguments arguments) {
        super.setupArguments(arguments);
        QL.require(PartialTimeBarrierOption.Arguments.class.isAssignableFrom(arguments.getClass()),
                ReflectConstants.WRONG_ARGUMENT_TYPE);
        final PartialTimeBarrierOption.ArgumentsImpl a = (PartialTimeBarrierOption.ArgumentsImpl) arguments;
        a.barrierType = barrierType;
        a.barrierRange = barrierRange;
        a.barrier = barrier;
        a.rebate = rebate;
        a.coverEventDate = coverEventDate;
    }


    //
    // inner interfaces / classes
    //

    public interface Arguments extends OneAssetOption.Arguments { /* marking interface */ }

    public interface Results extends OneAssetOption.Results { /* marking interface */ }

    public interface Engine extends PricingEngine, Observer { /* marking interface */ }


    /**
     * Arguments for partial-time barrier option calculation.
     */
    public static class ArgumentsImpl extends OneAssetOption.ArgumentsImpl
            implements PartialTimeBarrierOption.Arguments {

        public BarrierType barrierType;
        public PartialBarrier barrierRange;
        public double barrier;
        public double rebate;
        public Date coverEventDate;

        public ArgumentsImpl() {
            this.barrierType = null;
            this.barrierRange = null;
            this.barrier = Constants.NULL_REAL;
            this.rebate = Constants.NULL_REAL;
            this.coverEventDate = null;
        }

        @Override
        public void validate() {
            super.validate();

            QL.require(!Double.isNaN(barrier), "no barrier given");
            QL.require(!Double.isNaN(rebate), "no rebate given");
            QL.require(coverEventDate != null, "no cover event date given");
            QL.require(coverEventDate.lt(exercise.lastDate()),
                    "cover event date equal or later than exercise date");

            switch (barrierType) {
              case DownIn:
              case UpIn:
              case DownOut:
              case UpOut:
                break;
              default:
                throw new LibraryException("unknown barrier type");
            }
        }
    }


    public static class ResultsImpl extends OneAssetOption.ResultsImpl
            implements PartialTimeBarrierOption.Results { /* marking class */ }


    /**
     * Partial-time barrier-option engine base class.
     */
    public abstract static class EngineImpl
            extends GenericEngine<PartialTimeBarrierOption.Arguments, OneAssetOption.Results> {

        protected EngineImpl() {
            super(new ArgumentsImpl(), new ResultsImpl());
        }

        protected PartialTimeBarrierOption.ArgumentsImpl args() {
            return (PartialTimeBarrierOption.ArgumentsImpl) arguments_;
        }
    }
}
