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
 Copyright (C) 2014 Master IMAFA - Polytech'Nice Sophia - Université de Nice Sophia Antipolis

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.experimental.exoticoptions;

import org.jquantlib.QL;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.MultiAssetOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.lang.reflect.ReflectConstants;
import org.jquantlib.math.Constants;
import org.jquantlib.pricingengines.PricingEngine;

/**
 * Two-asset correlation option (Zhang 1995).
 * <p>
 * Pays a payoff based on the value at exercise of the second asset and
 * its corresponding strike, but only if the first instrument is also
 * in the money with respect to its own strike; if not, the payoff is 0.
 * <p>
 * Mirrors C++ QuantLib v1.42.1 {@code TwoAssetCorrelationOption} in
 * {@code ql/instruments/twoassetcorrelationoption.{hpp,cpp}}.
 *
 * @author Jose Moya
 */
public class TwoAssetCorrelationOption extends MultiAssetOption {

    protected final double X2;

    public TwoAssetCorrelationOption(final Option.Type type,
                                     final double strike1,
                                     final double strike2,
                                     final Exercise exercise) {
        super(new PlainVanillaPayoff(type, strike1), exercise);
        this.X2 = strike2;
    }

    @Override
    protected void setupArguments(final PricingEngine.Arguments args) {
        super.setupArguments(args);
        QL.require(TwoAssetCorrelationOption.ArgumentsImpl.class.isAssignableFrom(args.getClass()),
                ReflectConstants.WRONG_ARGUMENT_TYPE);
        final TwoAssetCorrelationOption.ArgumentsImpl moreArgs =
                (TwoAssetCorrelationOption.ArgumentsImpl) args;
        moreArgs.X2 = X2;
    }

    /**
     * Two-asset correlation option arguments.
     * <p>
     * Mirrors C++ QuantLib v1.42.1 {@code TwoAssetCorrelationOption::arguments}.
     */
    public static class ArgumentsImpl extends MultiAssetOption.ArgumentsImpl
            implements MultiAssetOption.Arguments {

        public double X2;

        public ArgumentsImpl() {
            this.X2 = Constants.NULL_REAL;
        }

        @Override
        public void validate() {
            super.validate();
            QL.require(X2 != Constants.NULL_REAL, "no X2 given");
        }
    }
}
