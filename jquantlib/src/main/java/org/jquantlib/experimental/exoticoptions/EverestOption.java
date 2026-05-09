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
 Copyright (C) 2008 Master IMAFA - Polytech'Nice Sophia - Universite de Nice Sophia Antipolis

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */

package org.jquantlib.experimental.exoticoptions;

import org.jquantlib.QL;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.MultiAssetOption;
import org.jquantlib.instruments.NullPayoff;
import org.jquantlib.lang.reflect.ReflectConstants;
import org.jquantlib.math.Constants;
import org.jquantlib.pricingengines.GenericEngine;
import org.jquantlib.pricingengines.PricingEngine;

/**
 * Everest option.
 *
 * <p>Ported from C++ QuantLib v1.42.1
 * ql/experimental/exoticoptions/everestoption.{hpp,cpp}.</p>
 *
 * @author Jose Moya
 */
public class EverestOption extends MultiAssetOption {

    private final double notional_;
    private final double guarantee_;
    private double yield_;

    public EverestOption(final double notional,
                         final double guarantee,
                         final Exercise exercise) {
        super(new NullPayoff(), exercise);
        this.notional_ = notional;
        this.guarantee_ = guarantee;
        this.yield_ = Constants.NULL_REAL;
    }

    public double yield() {
        calculate();
        QL.require(yield_ != Constants.NULL_REAL, "yield not provided");
        return yield_;
    }

    @Override
    protected void setupArguments(final PricingEngine.Arguments args) /* @ReadOnly */ {
        super.setupArguments(args);

        QL.require(EverestOption.ArgumentsImpl.class.isAssignableFrom(args.getClass()),
                ReflectConstants.WRONG_ARGUMENT_TYPE);
        final EverestOption.ArgumentsImpl arguments = (EverestOption.ArgumentsImpl) args;
        arguments.notional  = notional_;
        arguments.guarantee = guarantee_;
    }

    @Override
    public void fetchResults(final PricingEngine.Results r) /* @ReadOnly */ {
        super.fetchResults(r);

        QL.require(EverestOption.ResultsImpl.class.isAssignableFrom(r.getClass()),
                ReflectConstants.WRONG_ARGUMENT_TYPE);
        final EverestOption.ResultsImpl results = (EverestOption.ResultsImpl) r;
        QL.ensure(results != null, "no results returned from pricing engine");
        yield_ = results.yield;
    }

    //
    // public inner classes
    //

    public static class ArgumentsImpl extends MultiAssetOption.ArgumentsImpl
            implements MultiAssetOption.Arguments {

        public double notional;
        public double guarantee;

        public ArgumentsImpl() {
            this.notional  = Constants.NULL_REAL;
            this.guarantee = Constants.NULL_REAL;
        }

        @Override
        public void validate() /* @ReadOnly */ {
            super.validate();
            QL.require(notional != Constants.NULL_REAL, "no notional given");
            QL.require(notional != 0.0, "null notional given");
            QL.require(guarantee != Constants.NULL_REAL, "no guarantee given");
        }
    }

    public static class ResultsImpl extends MultiAssetOption.ResultsImpl {

        public double yield;

        @Override
        public void reset() /* @ReadOnly */ {
            super.reset();
            yield = Constants.NULL_REAL;
        }
    }

    public static abstract class EngineImpl
            extends GenericEngine<EverestOption.ArgumentsImpl, EverestOption.ResultsImpl>
            implements MultiAssetOption.Engine {

        public EngineImpl() {
            super(new ArgumentsImpl(), new ResultsImpl());
        }
    }
}
