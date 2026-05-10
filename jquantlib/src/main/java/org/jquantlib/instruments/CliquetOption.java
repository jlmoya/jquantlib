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
 Copyright (C) 2003, 2004, 2007 StatPro Italia srl

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

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.math.Constants;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.time.Date;

/**
 * Cliquet (Ratchet) option.
 * <p>
 * A series of forward-starting (deferred-strike) options where the strike for each
 * forward-start option is set equal to a fixed percentage of the spot price at the
 * beginning of each period.
 * <p>
 * Mirrors C++ QuantLib v1.42.1 {@code CliquetOption} in
 * {@code ql/instruments/cliquetoption.{hpp,cpp}}.
 */
public class CliquetOption extends OneAssetOption {

    private final List<Date> resetDates;

    public CliquetOption(final PercentageStrikePayoff payoff,
                         final EuropeanExercise maturity,
                         final List<Date> resetDates) {
        super(payoff, maturity);
        this.resetDates = new ArrayList<Date>(resetDates);
    }

    @Override
    protected void setupArguments(final PricingEngine.Arguments args) {
        super.setupArguments(args);
        QL.require(args instanceof CliquetOption.ArgumentsImpl, "wrong engine type");
        final CliquetOption.ArgumentsImpl moreArgs = (CliquetOption.ArgumentsImpl) args;
        moreArgs.resetDates = new ArrayList<Date>(resetDates);
    }

    /**
     * Cliquet option arguments.
     * <p>
     * Mirrors C++ QuantLib v1.42.1 {@code CliquetOption::arguments}.
     */
    public static class ArgumentsImpl extends OneAssetOption.ArgumentsImpl
            implements OneAssetOption.Arguments {

        public /*@Real*/ double accruedCoupon;
        public /*@Real*/ double lastFixing;
        public /*@Real*/ double localCap;
        public /*@Real*/ double localFloor;
        public /*@Real*/ double globalCap;
        public /*@Real*/ double globalFloor;
        public List<Date> resetDates;

        public ArgumentsImpl() {
            this.accruedCoupon = Constants.NULL_REAL;
            this.lastFixing = Constants.NULL_REAL;
            this.localCap = Constants.NULL_REAL;
            this.localFloor = Constants.NULL_REAL;
            this.globalCap = Constants.NULL_REAL;
            this.globalFloor = Constants.NULL_REAL;
            this.resetDates = new ArrayList<Date>();
        }

        @Override
        public void validate() {
            super.validate();

            QL.require(payoff instanceof PercentageStrikePayoff, "wrong payoff type");
            final PercentageStrikePayoff moneyness = (PercentageStrikePayoff) payoff;
            QL.require(moneyness.strike() > 0.0, "negative or zero moneyness given");

            QL.require(accruedCoupon == Constants.NULL_REAL || accruedCoupon >= 0.0,
                       "negative accrued coupon");
            QL.require(localCap == Constants.NULL_REAL || localCap >= 0.0,
                       "negative local cap");
            QL.require(localFloor == Constants.NULL_REAL || localFloor >= 0.0,
                       "negative local floor");
            QL.require(globalCap == Constants.NULL_REAL || globalCap >= 0.0,
                       "negative global cap");
            QL.require(globalFloor == Constants.NULL_REAL || globalFloor >= 0.0,
                       "negative global floor");
            QL.require(!resetDates.isEmpty(), "no reset dates given");

            for (int i = 0; i < resetDates.size(); ++i) {
                QL.require(exercise.lastDate().gt(resetDates.get(i)),
                           "reset date greater or equal to maturity");
                QL.require(i == 0 || resetDates.get(i).gt(resetDates.get(i - 1)),
                           "unsorted reset dates");
            }
        }
    }
}
