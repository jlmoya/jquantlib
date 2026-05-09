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
 Copyright (C) 2015 Thema Consulting SA
*/

package org.jquantlib.experimental.barrieroption;

import org.jquantlib.QL;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.lang.reflect.ReflectConstants;
import org.jquantlib.math.Constants;
import org.jquantlib.pricingengines.GenericEngine;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.util.Observer;
/**
 * Double Barrier option on a single asset.
 * <p>
 * Mirrors {@code QuantLib::DoubleBarrierOption} from
 * {@code ql/instruments/doublebarrieroption.hpp} (v1.42.1).
 *
 * <p>The implied-volatility helper present in C++ is omitted in this initial port
 * (Phase 4e.5 may add it once {@code AnalyticDoubleBarrierEngine} ships in
 * {@code org.jquantlib.pricingengines.barrier}).
 *
 * @author JQuantLib migration
 */
public class DoubleBarrierOption extends OneAssetOption {

    //
    // protected fields
    //

    protected DoubleBarrierType barrierType;
    protected double barrier_lo;
    protected double barrier_hi;
    protected double rebate;


    //
    // public constructors
    //

    public DoubleBarrierOption(
            final DoubleBarrierType barrierType,
            final double barrier_lo,
            final double barrier_hi,
            final double rebate,
            final StrikedTypePayoff payoff,
            final Exercise exercise) {
        super(payoff, exercise);
        this.barrierType = barrierType;
        this.barrier_lo = barrier_lo;
        this.barrier_hi = barrier_hi;
        this.rebate = rebate;
    }


    //
    // overrides OneAssetOption
    //

    @Override
    public void setupArguments(final PricingEngine.Arguments arguments) {
        super.setupArguments(arguments);
        QL.require(DoubleBarrierOption.Arguments.class.isAssignableFrom(arguments.getClass()),
                ReflectConstants.WRONG_ARGUMENT_TYPE);
        final DoubleBarrierOption.ArgumentsImpl a = (DoubleBarrierOption.ArgumentsImpl) arguments;
        a.barrierType = barrierType;
        a.barrier_lo = barrier_lo;
        a.barrier_hi = barrier_hi;
        a.rebate = rebate;
    }


    //
    // inner interfaces / classes
    //

    public interface Arguments extends OneAssetOption.Arguments { /* marking interface */ }

    public interface Results extends OneAssetOption.Results { /* marking interface */ }

    public interface Engine extends PricingEngine, Observer { /* marking interface */ }


    /**
     * Arguments for double barrier option calculation.
     */
    public static class ArgumentsImpl extends OneAssetOption.ArgumentsImpl
            implements DoubleBarrierOption.Arguments {

        public DoubleBarrierType barrierType;
        public double barrier_lo;
        public double barrier_hi;
        public double rebate;

        public ArgumentsImpl() {
            this.barrierType = null;
            this.barrier_lo = Constants.NULL_REAL;
            this.barrier_hi = Constants.NULL_REAL;
            this.rebate = Constants.NULL_REAL;
        }

        @Override
        public void validate() {
            super.validate();

            QL.require(barrierType == DoubleBarrierType.KnockIn
                    || barrierType == DoubleBarrierType.KnockOut
                    || barrierType == DoubleBarrierType.KIKO
                    || barrierType == DoubleBarrierType.KOKI,
                    "Invalid barrier type");
            QL.require(!Double.isNaN(barrier_lo), "no low barrier given");
            QL.require(!Double.isNaN(barrier_hi), "no high barrier given");
            QL.require(!Double.isNaN(rebate), "no rebate given");
        }
    }


    public static class ResultsImpl extends OneAssetOption.ResultsImpl
            implements DoubleBarrierOption.Results { /* marking class */ }


    /**
     * Double-Barrier-option engine base class.
     */
    public abstract static class EngineImpl
            extends GenericEngine<DoubleBarrierOption.Arguments, OneAssetOption.Results> {

        private final DoubleBarrierOption.ArgumentsImpl a;

        protected EngineImpl() {
            super(new ArgumentsImpl(), new ResultsImpl());
            this.a = (DoubleBarrierOption.ArgumentsImpl) arguments_;
        }

        protected EngineImpl(final DoubleBarrierOption.Arguments arguments,
                             final OneAssetOption.Results results) {
            super(arguments, results);
            this.a = (DoubleBarrierOption.ArgumentsImpl) arguments_;
        }

        /**
         * Mirrors {@code DoubleBarrierOption::engine::triggered} (v1.42.1):
         * underlying is at or beyond either barrier.
         */
        protected boolean triggered(final double underlying) {
            return underlying <= a.barrier_lo || underlying >= a.barrier_hi;
        }

        /**
         * Convenience accessor for subclasses that need the typed arguments
         * without re-casting.
         */
        protected DoubleBarrierOption.ArgumentsImpl args() {
            return a;
        }
    }

}
