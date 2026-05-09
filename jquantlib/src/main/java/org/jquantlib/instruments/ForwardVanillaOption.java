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

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.instruments;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.pricingengines.GenericEngine;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.time.Date;

/**
 * Forward (strike-resetting) version of a vanilla option.
 *
 * <p>Phase 4a.5 A.5.3 port of {@code QuantLib::ForwardVanillaOption}
 * (v1.42.1 ql/instruments/forwardvanillaoption.{hpp,cpp}). Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>A forward-start option whose strike is set at {@code resetDate}
 * to {@code moneyness * S(resetDate)}. The exercise is European at the
 * payoff's terminal exercise date.
 *
 * <p>Because Java does not support C++ template member typedefs, the
 * {@code ForwardOptionArguments<ArgumentsType>} template is collapsed to
 * a single concrete subclass {@link ArgumentsImpl} extending
 * {@link OneAssetOption.ArgumentsImpl}.
 */
public class ForwardVanillaOption extends OneAssetOption {

    private final double moneyness_;
    private final Date   resetDate_;

    public ForwardVanillaOption(final double moneyness,
                                final Date resetDate,
                                final StrikedTypePayoff payoff,
                                final Exercise exercise) {
        super(payoff, exercise);
        this.moneyness_ = moneyness;
        this.resetDate_ = resetDate;
    }

    @Override
    protected void setupArguments(final PricingEngine.Arguments args) {
        super.setupArguments(args);
        QL.require(args instanceof ArgumentsImpl, "wrong argument type");
        final ArgumentsImpl a = (ArgumentsImpl) args;
        a.moneyness = moneyness_;
        a.resetDate = resetDate_;
    }

    @Override
    public void fetchResults(final PricingEngine.Results r) {
        super.fetchResults(r);
        // Results are inherited from OneAssetOption — no extra fields here.
    }

    public double moneyness() { return moneyness_; }
    public Date resetDate()   { return resetDate_; }


    //
    // public inner classes
    //

    /** Marking interface — extra fields in {@link ArgumentsImpl}. */
    public interface Arguments extends OneAssetOption.Arguments { /* marker */ }

    /** Marking interface — same shape as {@link OneAssetOption.Results}. */
    public interface Results extends OneAssetOption.Results { /* marker */ }

    /**
     * Arguments for forward (strike-resetting) option calculation.
     * Mirrors C++ {@code ForwardOptionArguments<OneAssetOption::arguments>}.
     */
    public static class ArgumentsImpl extends OneAssetOption.ArgumentsImpl
            implements ForwardVanillaOption.Arguments {

        public double moneyness = Double.NaN;
        public Date   resetDate;

        public ArgumentsImpl() { super(); }

        @Override
        public void validate() {
            super.validate();
            QL.require(!Double.isNaN(moneyness), "null moneyness given");
            QL.require(moneyness > 0.0, "negative or zero moneyness given");
            QL.require(resetDate != null, "null reset date given");
            QL.require(!resetDate.lt(new Settings().evaluationDate()),
                       "reset date in the past");
            QL.require(exercise.lastDate().gt(resetDate),
                       "reset date later or equal to maturity");
        }
    }

    /** Results — same as base {@link OneAssetOption.ResultsImpl}. */
    public static class ResultsImpl extends OneAssetOption.ResultsImpl
            implements ForwardVanillaOption.Results { /* marker */ }

    /**
     * Pricing-engine base for forward vanilla options. Mirrors C++
     * {@code GenericEngine<ForwardOptionArguments<VanillaOption::arguments>,
     * VanillaOption::results>}.
     */
    public abstract static class EngineImpl
            extends GenericEngine<ForwardVanillaOption.Arguments,
                                  ForwardVanillaOption.Results> {
        public EngineImpl() {
            super(new ArgumentsImpl(), new ResultsImpl());
        }
    }
}
