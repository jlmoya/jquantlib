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
 Copyright (C) 2010 Master IMAFA - Polytech'Nice Sophia - Université de Nice Sophia Antipolis

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.instruments;

import org.jquantlib.QL;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.time.Date;

/**
 * Simple chooser option.
 * <p>
 * Gives the holder the right to choose, at a future date prior to exercise, whether the option is a call or a put. The
 * exercise date and strike are the same for both call and put alternatives.
 * <p>
 * Mirrors C++ QuantLib v1.42.1 {@code SimpleChooserOption} in {@code ql/instruments/simplechooseroption.{hpp,cpp}}.
 *
 * @author Jose Moya
 */
public class SimpleChooserOption extends OneAssetOption {

    private final Date choosingDate;

    public SimpleChooserOption(final Date choosingDate, final double strike, final Exercise exercise) {
        super(new PlainVanillaPayoff(Option.Type.Call, strike), exercise);
        this.choosingDate = choosingDate;
    }

    @Override
    protected void setupArguments(final PricingEngine.Arguments args) {
        super.setupArguments(args);
        QL.require(args instanceof SimpleChooserOption.ArgumentsImpl, "wrong argument type");
        final SimpleChooserOption.ArgumentsImpl moreArgs = (SimpleChooserOption.ArgumentsImpl) args;
        moreArgs.choosingDate = choosingDate;
    }

    /**
     * Simple-chooser-option arguments.
     * <p>
     * Mirrors C++ QuantLib v1.42.1 {@code SimpleChooserOption::arguments}.
     */
    public static class ArgumentsImpl extends OneAssetOption.ArgumentsImpl implements OneAssetOption.Arguments {

        public Date choosingDate;

        @Override
        public void validate() {
            super.validate();
            QL.require(choosingDate != null && !choosingDate.isNull(), "no choosing date given");
            QL.require(choosingDate.lt(exercise.lastDate()), "choosing date later than or equal to maturity date");
        }
    }
}
