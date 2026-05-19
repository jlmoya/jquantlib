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
 Copyright (C) 2025 William Day
*/

package org.jquantlib.experimental.exoticoptions;

import org.jquantlib.QL;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.BarrierType;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.lang.reflect.ReflectConstants;
import org.jquantlib.math.Constants;
import org.jquantlib.pricingengines.GenericEngine;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.util.Observer;

/**
 * Soft barrier option on a single asset.
 * <p>
 * A soft barrier option gets knocked in/out proportionally over the barrier range instead of being knocked in/out in
 * full at a hard barrier. Currently only available with European payoff style.
 * <p>
 * Mirrors {@code QuantLib::SoftBarrierOption} from {@code ql/instruments/softbarrieroption.hpp} (v1.42.1).
 *
 * @author JQuantLib migration
 */
public class SoftBarrierOption extends OneAssetOption {

    //
    // protected fields
    //

    protected BarrierType barrierType;
    protected double barrierLo;
    protected double barrierHi;

    //
    // public constructors
    //

    public SoftBarrierOption(final BarrierType barrierType, final double barrierLo, final double barrierHi,
            final StrikedTypePayoff payoff, final Exercise exercise) {
        super(payoff, exercise);
        this.barrierType = barrierType;
        this.barrierLo = barrierLo;
        this.barrierHi = barrierHi;
    }

    //
    // overrides OneAssetOption
    //

    @Override
    public void setupArguments(final PricingEngine.Arguments arguments) {
        super.setupArguments(arguments);
        QL.require(SoftBarrierOption.Arguments.class.isAssignableFrom(arguments.getClass()),
                ReflectConstants.WRONG_ARGUMENT_TYPE);
        final SoftBarrierOption.ArgumentsImpl a = (SoftBarrierOption.ArgumentsImpl) arguments;
        a.barrierType = barrierType;
        a.barrierLo = barrierLo;
        a.barrierHi = barrierHi;
    }

    //
    // inner interfaces / classes
    //

    public interface Arguments extends OneAssetOption.Arguments { /* marking interface */
    }

    public interface Results extends OneAssetOption.Results { /* marking interface */
    }

    public interface Engine extends PricingEngine, Observer { /* marking interface */
    }

    /**
     * Arguments for soft barrier option calculation.
     */
    public static class ArgumentsImpl extends OneAssetOption.ArgumentsImpl implements SoftBarrierOption.Arguments {

        public BarrierType barrierType;
        public double barrierLo;
        public double barrierHi;

        public ArgumentsImpl() {
            this.barrierType = null;
            this.barrierLo = Constants.NULL_REAL;
            this.barrierHi = Constants.NULL_REAL;
        }
    }

    public static class ResultsImpl extends OneAssetOption.ResultsImpl
            implements SoftBarrierOption.Results { /* marking class */
    }

    /**
     * Soft barrier-option engine base class.
     */
    public abstract static class EngineImpl
            extends GenericEngine< SoftBarrierOption.Arguments, OneAssetOption.Results > {

        protected EngineImpl() {
            super(new ArgumentsImpl(), new ResultsImpl());
        }

        protected SoftBarrierOption.ArgumentsImpl args() {
            return (SoftBarrierOption.ArgumentsImpl) arguments_;
        }
    }
}
