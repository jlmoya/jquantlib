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

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <http://quantlib.org/license.shtml>.
*/

package org.jquantlib.instruments;

import org.jquantlib.QL;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.time.Date;

/**
 * Holder-extensible option.
 * <p>
 * This option can be exercised on maturity date, or it can be extended by its holder until a second maturity date by
 * paying a premium to the writer of the option. In case of extension, the strike can also be changed.
 * <p>
 * Mirrors C++ QuantLib v1.42.1 {@code HolderExtensibleOption} in
 * {@code ql/instruments/holderextensibleoption.{hpp,cpp}}.
 */
public class HolderExtensibleOption extends OneAssetOption {

    private final double premium;
    private final Date secondExpiryDate;
    private final double secondStrike;

    public HolderExtensibleOption(final Option.Type type, final double premium, final Date secondExpiryDate,
            final double secondStrike, final StrikedTypePayoff payoff, final Exercise exercise) {
        super(payoff, exercise);
        this.premium = premium;
        this.secondExpiryDate = secondExpiryDate;
        this.secondStrike = secondStrike;
    }

    @Override
    protected void setupArguments(final PricingEngine.Arguments args) {
        super.setupArguments(args);
        QL.require(args instanceof HolderExtensibleOption.ArgumentsImpl, "wrong argument type");
        final HolderExtensibleOption.ArgumentsImpl moreArgs = (HolderExtensibleOption.ArgumentsImpl) args;
        moreArgs.premium = premium;
        moreArgs.secondExpiryDate = secondExpiryDate;
        moreArgs.secondStrike = secondStrike;
    }

    /**
     * Holder-extensible option arguments.
     * <p>
     * Mirrors C++ QuantLib v1.42.1 {@code HolderExtensibleOption::arguments}.
     */
    public static class ArgumentsImpl extends OneAssetOption.ArgumentsImpl implements OneAssetOption.Arguments {

        public double premium;
        public Date secondExpiryDate;
        public double secondStrike;

        @Override
        public void validate() {
            super.validate();
            QL.require(premium > 0, "negative premium not allowed");
            QL.require(secondExpiryDate != null && !secondExpiryDate.isNull(), "no extending date given");
            QL.require(secondExpiryDate.ge(exercise.lastDate()),
                    "extended date is earlier than or equal to first maturity date");
        }
    }
}
