/*
 Copyright (C) 2026 JQuantLib migration contributors.

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
package org.jquantlib.cashflow;

import org.jquantlib.QL;
import org.jquantlib.instruments.Option;
import org.jquantlib.math.Closeness;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.pricingengines.BlackFormula;
import org.jquantlib.termstructures.SwaptionVolatilityStructure;
import org.jquantlib.termstructures.volatilities.SmileSection;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;

/**
 * Vanilla-option pricer driven by a swaption smile section.
 * <p>
 * Mirrors C++ QuantLib v1.42.1 {@code MarketQuotedOptionPricer} in
 * {@code ql/cashflows/conundrumpricer.{hpp,cpp}}. Used internally by
 * Hagan/CMS pricers (e.g. {@code AnalyticHaganPricer},
 * {@code NumericHaganPricer}) to evaluate the conundrum integrand.
 * <p>
 * Construction asserts the supplied volatility surface is either
 * {@link VolatilityType#Normal} or {@link VolatilityType#ShiftedLognormal}
 * with zero shift -- the only types the conundrum machinery handles
 * without ambiguity (matches C++ {@code QL_REQUIRE} on lines 55-58 of
 * {@code conundrumpricer.cpp}).
 */
public class MarketQuotedOptionPricer implements VanillaOptionPricer {

    private static final String MISSING_VOL =
            "VanillaOptionPricer: a normal or a zero-shift lognormal volatility is required";

    private final double forwardValue_;
    private final Date expiryDate_;
    private final Period swapTenor_;
    private final SwaptionVolatilityStructure volatilityStructure_;
    private final SmileSection smile_;

    public MarketQuotedOptionPricer(final double forwardValue,
                                    final Date expiryDate,
                                    final Period swapTenor,
                                    final SwaptionVolatilityStructure volatilityStructure) {
        this.forwardValue_ = forwardValue;
        this.expiryDate_ = expiryDate;
        this.swapTenor_ = swapTenor;
        this.volatilityStructure_ = volatilityStructure;

        QL.require(volatilityStructure != null, "null volatility structure");

        final boolean isNormal = volatilityStructure.volatilityType() == VolatilityType.Normal;
        final boolean isZeroShiftLognormal =
                volatilityStructure.volatilityType() == VolatilityType.ShiftedLognormal
                && Closeness.isCloseEnough(volatilityStructure.shift(), 0.0);
        QL.require(isNormal || isZeroShiftLognormal, MISSING_VOL);

        // Java has no smileSection(Date, Period) without extrapolate flag;
        // pass extrapolate=false to match the C++ default behaviour.
        this.smile_ = volatilityStructure.smileSection(expiryDate, swapTenor, false);
    }

    @Override
    public double evaluate(final double strike,
                           final Option.Type optionType,
                           final double deflator) {
        final double variance = smile_.variance(strike);
        final double stdDev = Math.sqrt(variance);
        if (volatilityStructure_.volatilityType() == VolatilityType.ShiftedLognormal) {
            return deflator
                    * BlackFormula.blackFormula(optionType, strike, forwardValue_, stdDev);
        }
        return deflator
                * BlackFormula.bachelierBlackFormula(optionType, strike, forwardValue_, stdDev);
    }

    public double forwardValue() {
        return forwardValue_;
    }

    public Date expiryDate() {
        return expiryDate_;
    }

    public Period swapTenor() {
        return swapTenor_;
    }
}
