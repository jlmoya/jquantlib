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
 Copyright (C) 2012 Master IMAFA - Polytech'Nice Sophia - Université de Nice Sophia Antipolis
*/

package org.jquantlib.experimental.exoticoptions;

import org.jquantlib.QL;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.BarrierType;
import org.jquantlib.instruments.Instrument;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.lang.reflect.ReflectConstants;
import org.jquantlib.math.Constants;
import org.jquantlib.pricingengines.GenericEngine;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.util.Observer;

/**
 * Barrier option on two assets.
 * <p>
 * The value of the first asset is compared to the strike to determine the payoff,
 * while the value of the second asset is monitored to check if the barrier is hit.
 * <p>
 * Mirrors {@code QuantLib::TwoAssetBarrierOption} from
 * {@code ql/instruments/twoassetbarrieroption.hpp} (v1.42.1).
 *
 * @author JQuantLib migration
 */
public class TwoAssetBarrierOption extends Option {

    //
    // protected fields
    //

    protected BarrierType barrierType;
    protected double barrier;


    //
    // public constructors
    //

    public TwoAssetBarrierOption(
            final BarrierType barrierType,
            final double barrier,
            final StrikedTypePayoff payoff,
            final Exercise exercise) {
        super(payoff, exercise);
        this.barrierType = barrierType;
        this.barrier = barrier;
    }


    //
    // overrides Option
    //

    @Override
    public boolean isExpired() {
        // Mirrors OneAssetOption.isExpired() — same semantics as
        // C++ detail::simple_event(exercise->lastDate()).hasOccurred().
        return exercise.lastDate().lt(new org.jquantlib.Settings().evaluationDate());
    }

    @Override
    public void setupArguments(final PricingEngine.Arguments arguments) {
        super.setupArguments(arguments);
        QL.require(TwoAssetBarrierOption.Arguments.class.isAssignableFrom(arguments.getClass()),
                ReflectConstants.WRONG_ARGUMENT_TYPE);
        final TwoAssetBarrierOption.ArgumentsImpl a = (TwoAssetBarrierOption.ArgumentsImpl) arguments;
        a.barrierType = barrierType;
        a.barrier = barrier;
    }


    //
    // inner interfaces / classes
    //

    public interface Arguments extends Option.Arguments { /* marking interface */ }

    public interface Results extends Instrument.Results, Option.Greeks, Option.MoreGreeks { /* marking interface */ }

    public interface Engine extends PricingEngine, Observer { /* marking interface */ }


    /**
     * Arguments for two-asset barrier option calculation.
     */
    public static class ArgumentsImpl extends Option.ArgumentsImpl
            implements TwoAssetBarrierOption.Arguments {

        public BarrierType barrierType;
        public double barrier;

        public ArgumentsImpl() {
            this.barrierType = null;
            this.barrier = Constants.NULL_REAL;
        }

        @Override
        public void validate() {
            super.validate();
            switch (barrierType) {
              case DownIn:
              case UpIn:
              case DownOut:
              case UpOut:
                break;
              default:
                throw new LibraryException("unknown type");
            }
            QL.require(!Double.isNaN(barrier), "no barrier given");
        }
    }


    public static class ResultsImpl extends Instrument.ResultsImpl
            implements TwoAssetBarrierOption.Results {

        private final Option.GreeksImpl greeks = new Option.GreeksImpl();
        private final Option.MoreGreeksImpl moreGreeks = new Option.MoreGreeksImpl();

        public Option.GreeksImpl greeks() {
            return greeks;
        }

        public Option.MoreGreeksImpl moreGreeks() {
            return moreGreeks;
        }

        @Override
        public void reset() {
            super.reset();
            greeks.reset();
            moreGreeks.reset();
        }
    }


    /**
     * Two-asset barrier-option engine base class.
     */
    public abstract static class EngineImpl
            extends GenericEngine<TwoAssetBarrierOption.Arguments, TwoAssetBarrierOption.Results> {

        protected EngineImpl() {
            super(new ArgumentsImpl(), new ResultsImpl());
        }

        protected TwoAssetBarrierOption.ArgumentsImpl args() {
            return (TwoAssetBarrierOption.ArgumentsImpl) arguments_;
        }

        /**
         * Mirrors {@code TwoAssetBarrierOption::engine::triggered}.
         */
        protected boolean triggered(final double underlying) {
            final TwoAssetBarrierOption.ArgumentsImpl a = args();
            switch (a.barrierType) {
              case DownIn:
              case DownOut:
                return underlying < a.barrier;
              case UpIn:
              case UpOut:
                return underlying > a.barrier;
              default:
                throw new LibraryException("unknown type");
            }
        }
    }
}
