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
 Copyright (C) 2011 Master IMAFA - Polytech'Nice Sophia - Université de Nice Sophia Antipolis

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <http://quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
*/

package org.jquantlib.instruments;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.pricingengines.PricingEngine;

/**
 * Writer-extensible option.
 * <p>
 * If out of the money at the original exercise date, this option is extended until a later exercise date with an
 * amended strike.
 * <p>
 * Mirrors C++ QuantLib v1.42.1 {@code WriterExtensibleOption} in
 * {@code ql/instruments/writerextensibleoption.{hpp,cpp}}.
 */
public class WriterExtensibleOption extends OneAssetOption {

    private final StrikedTypePayoff payoff2;
    private final Exercise exercise2;

    public WriterExtensibleOption(final PlainVanillaPayoff payoff1, final Exercise exercise1,
            final PlainVanillaPayoff payoff2, final Exercise exercise2) {
        super(payoff1, exercise1);
        this.payoff2 = payoff2;
        this.exercise2 = exercise2;
    }

    public Payoff payoff2() {
        return payoff2;
    }

    public Exercise exercise2() {
        return exercise2;
    }

    @Override
    public boolean isExpired() {
        return exercise2.lastDate().lt(new Settings().evaluationDate());
    }

    @Override
    protected void setupArguments(final PricingEngine.Arguments args) {
        super.setupArguments(args);
        QL.require(args instanceof WriterExtensibleOption.ArgumentsImpl, "wrong arguments type");
        final WriterExtensibleOption.ArgumentsImpl otherArguments = (WriterExtensibleOption.ArgumentsImpl) args;
        otherArguments.payoff2 = payoff2;
        otherArguments.exercise2 = exercise2;
    }

    /**
     * Writer-extensible option arguments.
     * <p>
     * Mirrors C++ QuantLib v1.42.1 {@code WriterExtensibleOption::arguments}.
     */
    public static class ArgumentsImpl extends OneAssetOption.ArgumentsImpl implements OneAssetOption.Arguments {

        public Payoff payoff2;
        public Exercise exercise2;

        @Override
        public void validate() {
            super.validate();
            QL.require(payoff2 != null, "no second payoff given");
            QL.require(exercise2 != null, "no second exercise given");
            QL.require(exercise2.lastDate().gt(exercise.lastDate()),
                    "second exercise date is not later than the first");
        }
    }
}
