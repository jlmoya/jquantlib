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
 Copyright (C) 2014 Master IMAFA - Polytech'Nice Sophia - Universite de Nice Sophia Antipolis

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.instruments;

import org.jquantlib.QL;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.time.Date;

/**
 * Complex chooser option.
 * <p>
 * Gives the holder the right to choose, at a future date prior to exercise, whether the option should be a call or a
 * put. The exercise date and strike are different for the call and put alternatives.
 * <p>
 * Mirrors C++ QuantLib v1.42.1 {@code ComplexChooserOption} in {@code ql/instruments/complexchooseroption.{hpp,cpp}}.
 *
 * @author Jose Moya
 */
public class ComplexChooserOption extends OneAssetOption {

    private final Date choosingDate;
    private final double strikeCall;
    private final double strikePut;
    private final Exercise exerciseCall;
    private final Exercise exercisePut;

    public ComplexChooserOption(final Date choosingDate, final double strikeCall, final double strikePut,
            final Exercise exerciseCall, final Exercise exercisePut) {
        super(new PlainVanillaPayoff(Option.Type.Call, strikeCall), exerciseCall);
        this.choosingDate = choosingDate;
        this.strikeCall = strikeCall;
        this.strikePut = strikePut;
        this.exerciseCall = exerciseCall;
        this.exercisePut = exercisePut;
    }

    @Override
    protected void setupArguments(final PricingEngine.Arguments args) {
        super.setupArguments(args);
        QL.require(args instanceof ComplexChooserOption.ArgumentsImpl, "wrong argument type");
        final ComplexChooserOption.ArgumentsImpl moreArgs = (ComplexChooserOption.ArgumentsImpl) args;
        moreArgs.choosingDate = choosingDate;
        moreArgs.strikeCall = strikeCall;
        moreArgs.strikePut = strikePut;
        moreArgs.exerciseCall = exerciseCall;
        moreArgs.exercisePut = exercisePut;
    }

    /**
     * Complex-chooser-option arguments.
     * <p>
     * Mirrors C++ QuantLib v1.42.1 {@code ComplexChooserOption::arguments}.
     */
    public static class ArgumentsImpl extends OneAssetOption.ArgumentsImpl implements OneAssetOption.Arguments {

        public Date choosingDate;
        public double strikeCall;
        public double strikePut;
        public Exercise exerciseCall;
        public Exercise exercisePut;

        @Override
        public void validate() {
            super.validate();
            QL.require(choosingDate != null && !choosingDate.isNull(), "no choosing date given");
            QL.require(choosingDate.lt(exerciseCall.lastDate()),
                    "choosing date later than or equal to Call maturity date");
            QL.require(choosingDate.lt(exercisePut.lastDate()),
                    "choosing date later than or equal to Put maturity date");
        }
    }
}
