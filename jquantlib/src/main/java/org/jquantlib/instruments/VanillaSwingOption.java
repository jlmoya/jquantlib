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
/*
 Copyright (C) 2010 Klaus Spanderen
 */
package org.jquantlib.instruments;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.lang.reflect.ReflectConstants;
import org.jquantlib.pricingengines.PricingEngine;

/**
 * Vanilla swing option.
 *
 * <p>Java port of v1.42.1
 * {@code ql/instruments/vanillaswingoption.{hpp,cpp}::VanillaSwingOption}.
 *
 * <p>A swing option is an early-exercise option with a fixed number of
 * exercise opportunities, of which between {@code minExerciseRights} and
 * {@code maxExerciseRights} must be exercised. Exercise dates come from
 * a {@link SwingExercise} (a Bermudan-style exercise grid with fractional
 * day support).
 *
 * <p>The terminal payoff at each exercise opportunity is given by a
 * {@link StrikedTypePayoff} — typically a {@link VanillaForwardPayoff}
 * (signed forward) rather than a {@link PlainVanillaPayoff} (option floor),
 * because once an exercise right is consumed at a chosen date, the cash
 * flow is realised without an opt-out at that date.
 *
 * @author Phase 5e.5b-CFC-d-170 port
 */
public class VanillaSwingOption extends OneAssetOption {

    private final int minExerciseRights_;
    private final int maxExerciseRights_;

    public VanillaSwingOption(final Payoff payoff,
                              final SwingExercise ex,
                              final int minExerciseRights,
                              final int maxExerciseRights) {
        super(payoff, ex);
        this.minExerciseRights_ = minExerciseRights;
        this.maxExerciseRights_ = maxExerciseRights;
    }

    @Override
    public boolean isExpired() /* @ReadOnly */ {
        return exercise.lastDate().lt(new Settings().evaluationDate());
    }

    @Override
    protected void setupArguments(final PricingEngine.Arguments args) /* @ReadOnly */ {
        // C++ VanillaSwingOption::setupArguments downcasts the engine's
        // arguments to VanillaSwingOption::arguments and writes the payoff,
        // exercise, and exercise-rights bounds directly — it does NOT delegate
        // to the OneAssetOption / Option base setupArguments.
        QL.require(VanillaSwingOption.ArgumentsImpl.class.isAssignableFrom(args.getClass()),
                ReflectConstants.WRONG_ARGUMENT_TYPE);
        final VanillaSwingOption.ArgumentsImpl a = (VanillaSwingOption.ArgumentsImpl) args;
        a.payoff             = (StrikedTypePayoff) payoff;
        a.exercise           = (SwingExercise) exercise;
        a.minExerciseRights  = minExerciseRights_;
        a.maxExerciseRights  = maxExerciseRights_;
    }

    //
    // public inner classes
    //

    public interface Arguments extends OneAssetOption.Arguments { /* marking */ }

    public interface Results extends OneAssetOption.Results { /* marking */ }

    /**
     * Swing-option arguments. Mirrors C++
     * {@code VanillaSwingOption::arguments}.
     */
    public static class ArgumentsImpl implements VanillaSwingOption.Arguments {

        public StrikedTypePayoff payoff;
        public SwingExercise exercise;
        public int minExerciseRights;
        public int maxExerciseRights;

        @Override
        public void validate() /* @ReadOnly */ {
            QL.require(payoff != null, "no payoff given");
            QL.require(exercise != null, "no exercise given");
            QL.require(minExerciseRights <= maxExerciseRights,
                    "minExerciseRights <= maxExerciseRights");
            QL.require(exercise.dates().size() >= maxExerciseRights,
                    "number of exercise rights exceeds number of exercise dates");
        }
    }

    /**
     * Swing-option results. Same shape as
     * {@link OneAssetOption.ResultsImpl}.
     */
    public static class ResultsImpl extends OneAssetOption.ResultsImpl
            implements VanillaSwingOption.Results { /* marking */ }
}
