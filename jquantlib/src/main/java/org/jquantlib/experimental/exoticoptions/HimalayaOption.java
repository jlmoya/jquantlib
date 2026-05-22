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

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.
 */

package org.jquantlib.experimental.exoticoptions;

import org.jquantlib.QL;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.instruments.MultiAssetOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.lang.reflect.ReflectConstants;
import org.jquantlib.pricingengines.GenericEngine;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.time.Date;

import java.util.ArrayList;
import java.util.List;

/**
 * Himalaya option.
 *
 * <p>The payoff of a Himalaya option is computed in the following
 * way: Given a basket of N assets, and N time periods, at the end of each period the option who performed the best is
 * added to the average and then discarded from the basket. At the end of the N periods the option pays the max between
 * the strike and the average of the best performers.</p>
 *
 * <p>This implementation still does not manage seasoned options.</p>
 *
 * <p>Ported from C++ QuantLib v1.42.1
 * ql/experimental/exoticoptions/himalayaoption.{hpp,cpp}.</p>
 *
 * @author Jose Moya
 */
public class HimalayaOption extends MultiAssetOption {

    private final List< Date > fixingDates_;

    public HimalayaOption(final List< Date > fixingDates, final double strike) {
        super(new PlainVanillaPayoff(Option.Type.Call, strike),
                new EuropeanExercise(fixingDates.get(fixingDates.size() - 1)));
        // defensive copy to mirror C++ store-by-value semantics
        this.fixingDates_ = new ArrayList<>(fixingDates);
    }

    @Override
    protected void setupArguments(final PricingEngine.Arguments args) /* @ReadOnly */ {
        super.setupArguments(args);

        QL.require(HimalayaOption.ArgumentsImpl.class.isAssignableFrom(args.getClass()),
                ReflectConstants.WRONG_ARGUMENT_TYPE);
        final HimalayaOption.ArgumentsImpl arguments = (HimalayaOption.ArgumentsImpl) args;
        arguments.fixingDates = new ArrayList<>(fixingDates_);
    }

    //
    // public inner classes
    //

    public static class ArgumentsImpl extends MultiAssetOption.ArgumentsImpl implements MultiAssetOption.Arguments {

        public List< Date > fixingDates;

        @Override
        public void validate() /* @ReadOnly */ {
            super.validate();
            QL.require(fixingDates != null && !fixingDates.isEmpty(), "no fixing dates given");
        }
    }

    public static class ResultsImpl extends MultiAssetOption.ResultsImpl { /* marking */
    }

    public static abstract class EngineImpl
            extends GenericEngine< HimalayaOption.ArgumentsImpl, HimalayaOption.ResultsImpl >
            implements MultiAssetOption.Engine {

        public EngineImpl() {
            super(new ArgumentsImpl(), new ResultsImpl());
        }
    }
}
