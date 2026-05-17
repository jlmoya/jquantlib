/*
 Copyright (C) 2026 Jose Moya

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
 Copyright (C) 2012 Master IMAFA - Polytech'Nice Sophia - Université de Nice Sophia Antipolis

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.instruments;

import org.jquantlib.QL;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.lang.reflect.ReflectConstants;
import org.jquantlib.math.Constants;
import org.jquantlib.pricingengines.GenericEngine;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.util.Observer;

/**
 * Barrier option on two assets.
 * <p>
 * The value of the first asset is compared to the strike to determine the
 * payoff, while the value of the second asset is monitored to check if the
 * barrier is hit.
 * <p>
 * Mirrors C++ QuantLib v1.42.1 {@code TwoAssetBarrierOption} in
 * {@code ql/instruments/twoassetbarrieroption.{hpp,cpp}}.
 *
 * @author Jose Moya
 */
public class TwoAssetBarrierOption extends MultiAssetOption {

    //
    // protected fields
    //

    protected final BarrierType barrierType_;
    protected final double      barrier_;


    //
    // public constructors
    //

    public TwoAssetBarrierOption(final BarrierType barrierType,
                                 final double barrier,
                                 final StrikedTypePayoff payoff,
                                 final Exercise exercise) {
        super(payoff, exercise);
        this.barrierType_ = barrierType;
        this.barrier_     = barrier;
    }


    //
    // overrides MultiAssetOption / Option
    //

    @Override
    protected void setupArguments(final PricingEngine.Arguments args) /* @ReadOnly */ {
        super.setupArguments(args);
        QL.require(TwoAssetBarrierOption.ArgumentsImpl.class.isAssignableFrom(args.getClass()),
                ReflectConstants.WRONG_ARGUMENT_TYPE);
        final TwoAssetBarrierOption.ArgumentsImpl moreArgs =
                (TwoAssetBarrierOption.ArgumentsImpl) args;
        moreArgs.barrierType = barrierType_;
        moreArgs.barrier     = barrier_;
    }


    //
    // public inner classes
    //

    /**
     * Arguments for two-asset barrier option calculation.
     * <p>
     * Mirrors C++ QuantLib v1.42.1 {@code TwoAssetBarrierOption::arguments}.
     */
    public static class ArgumentsImpl extends MultiAssetOption.ArgumentsImpl
            implements MultiAssetOption.Arguments {

        // TODO: refactor messages
        private static final String UNKNOWN_TYPE = "unknown type";

        public BarrierType barrierType;
        public double      barrier;

        public ArgumentsImpl() {
            this.barrierType = BarrierType.Unknown;
            this.barrier     = Constants.NULL_REAL;
        }

        @Override
        public void validate() /* @ReadOnly */ {
            super.validate();

            switch (barrierType) {
              case DownIn:
              case UpIn:
              case DownOut:
              case UpOut:
                break;
              default:
                throw new LibraryException(UNKNOWN_TYPE);
            }

            QL.require(barrier != Constants.NULL_REAL, "no barrier given");
        }
    }

    public static class ResultsImpl extends MultiAssetOption.ResultsImpl { /* marking */ }

    /**
     * Two-asset barrier-option engine base class.
     * <p>
     * Mirrors C++ QuantLib v1.42.1 {@code TwoAssetBarrierOption::engine} and
     * provides the {@code triggered(underlying)} helper used by analytic
     * implementations.
     */
    public static abstract class EngineImpl
            extends GenericEngine<TwoAssetBarrierOption.ArgumentsImpl,
                                  TwoAssetBarrierOption.ResultsImpl>
            implements MultiAssetOption.Engine, Observer {

        protected EngineImpl() {
            super(new ArgumentsImpl(), new ResultsImpl());
        }

        protected boolean triggered(final double underlying) /* @ReadOnly */ {
            final TwoAssetBarrierOption.ArgumentsImpl a =
                    (TwoAssetBarrierOption.ArgumentsImpl) arguments_;
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
