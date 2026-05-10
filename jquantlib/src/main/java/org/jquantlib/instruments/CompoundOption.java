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
 Copyright (C) 2009 Dimitri Reiswich

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
import org.jquantlib.exercise.Exercise;
import org.jquantlib.pricingengines.PricingEngine;

/**
 * Compound option (i.e., option on option) on a single asset.
 * <p>
 * The mother option is the compound option; the daughter option is its underlying.
 * <p>
 * Mirrors C++ QuantLib v1.42.1 {@code CompoundOption} in
 * {@code ql/instruments/compoundoption.{hpp,cpp}}.
 */
public class CompoundOption extends OneAssetOption {

    private final StrikedTypePayoff daughterPayoff;
    private final Exercise daughterExercise;

    public CompoundOption(final StrikedTypePayoff motherPayoff,
                          final Exercise motherExercise,
                          final StrikedTypePayoff daughterPayoff,
                          final Exercise daughterExercise) {
        super(motherPayoff, motherExercise);
        this.daughterPayoff = daughterPayoff;
        this.daughterExercise = daughterExercise;
    }

    @Override
    protected void setupArguments(final PricingEngine.Arguments args) {
        super.setupArguments(args);
        QL.require(args instanceof CompoundOption.ArgumentsImpl, "wrong argument type");
        final CompoundOption.ArgumentsImpl moreArgs = (CompoundOption.ArgumentsImpl) args;
        moreArgs.daughterPayoff = daughterPayoff;
        moreArgs.daughterExercise = daughterExercise;
    }

    /**
     * Compound option arguments.
     * <p>
     * Mirrors C++ QuantLib v1.42.1 {@code CompoundOption::arguments}.
     */
    public static class ArgumentsImpl extends OneAssetOption.ArgumentsImpl
            implements OneAssetOption.Arguments {

        public StrikedTypePayoff daughterPayoff;
        public Exercise daughterExercise;

        @Override
        public void validate() {
            super.validate();
            QL.require(daughterPayoff != null, "no payoff given for underlying option");
            QL.require(daughterExercise != null, "no exercise given for underlying option");
            QL.require(exercise.lastDate().le(daughterExercise.lastDate()),
                       "maturity of compound option exceeds maturity of underlying option");
        }
    }
}
