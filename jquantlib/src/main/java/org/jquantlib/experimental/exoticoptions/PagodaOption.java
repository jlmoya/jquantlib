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
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.instruments.MultiAssetOption;
import org.jquantlib.instruments.NullPayoff;
import org.jquantlib.lang.reflect.ReflectConstants;
import org.jquantlib.math.Constants;
import org.jquantlib.pricingengines.GenericEngine;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.time.Date;

import java.util.ArrayList;
import java.util.List;

/**
 * Roofed Asian option on a number of assets.
 *
 * <p>The payoff is a given fraction multiplied by the minimum
 * between a given roof and the positive portfolio performance. If the performance of the portfolio is below then the
 * payoff is null.</p>
 *
 * <p>This implementation still does not manage seasoned options.</p>
 *
 * <p>Ported from C++ QuantLib v1.42.1
 * ql/experimental/exoticoptions/pagodaoption.{hpp,cpp}.</p>
 *
 * @author Jose Moya
 */
public class PagodaOption extends MultiAssetOption {

    private final List< Date > fixingDates_;
    private final double roof_;
    private final double fraction_;

    public PagodaOption(final List< Date > fixingDates, final double roof, final double fraction) {
        super(new NullPayoff(), new EuropeanExercise(fixingDates.get(fixingDates.size() - 1)));
        // defensive copy mirroring C++ store-by-value semantics
        this.fixingDates_ = new ArrayList< Date >(fixingDates);
        this.roof_ = roof;
        this.fraction_ = fraction;
    }

    @Override
    protected void setupArguments(final PricingEngine.Arguments args) /* @ReadOnly */ {
        super.setupArguments(args);

        QL.require(PagodaOption.ArgumentsImpl.class.isAssignableFrom(args.getClass()),
                ReflectConstants.WRONG_ARGUMENT_TYPE);
        final PagodaOption.ArgumentsImpl arguments = (PagodaOption.ArgumentsImpl) args;
        arguments.fixingDates = new ArrayList< Date >(fixingDates_);
        arguments.roof = roof_;
        arguments.fraction = fraction_;
    }

    //
    // public inner classes
    //

    public static class ArgumentsImpl extends MultiAssetOption.ArgumentsImpl implements MultiAssetOption.Arguments {

        public List< Date > fixingDates;
        public double roof;
        public double fraction;

        public ArgumentsImpl() {
            this.roof = Constants.NULL_REAL;
            this.fraction = Constants.NULL_REAL;
        }

        @Override
        public void validate() /* @ReadOnly */ {
            super.validate();
            QL.require(fixingDates != null && !fixingDates.isEmpty(), "no fixingDates given");
            QL.require(roof != Constants.NULL_REAL, "no roof given");
            QL.require(fraction != Constants.NULL_REAL, "no fraction given");
        }
    }

    public static class ResultsImpl extends MultiAssetOption.ResultsImpl { /* marking */
    }

    public static abstract class EngineImpl
            extends GenericEngine< PagodaOption.ArgumentsImpl, PagodaOption.ResultsImpl >
            implements MultiAssetOption.Engine {

        public EngineImpl() {
            super(new ArgumentsImpl(), new ResultsImpl());
        }
    }
}
