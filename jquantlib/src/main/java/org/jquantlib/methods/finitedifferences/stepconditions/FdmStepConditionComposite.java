/*
 Copyright (C) 2008 Andreas Gaida
 Copyright (C) 2008 Ralph Schreyer
 Copyright (C) 2008 Klaus Spanderen

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.methods.finitedifferences.stepconditions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.DividendSchedule;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.finitedifferences.StepCondition;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.utilities.FdmInnerValueCalculator;
import org.jquantlib.time.Date;

/**
 * Composite of FDM step conditions, applied in sequence between time
 * steps.
 * <p>
 * Java port of v1.42.1
 * {@code ql/methods/finitedifferences/stepconditions/fdmstepconditioncomposite.{hpp,cpp}}.
 * Implements {@link StepCondition} of {@link Array} so it can be passed
 * directly to the rollback driver. The composite holds:
 * <ul>
 *   <li>a list of stopping times — the union (sorted, deduplicated) of
 *       all per-condition stopping times, used by the time-stepping
 *       driver to align step boundaries with exercise / dividend
 *       events;</li>
 *   <li>a list of {@code StepCondition<Array>} instances applied in
 *       order during {@link #applyTo(Array, double)}.</li>
 * </ul>
 *
 * <h2>{@link #vanillaComposite} factory</h2>
 *
 * The static {@code vanillaComposite} factory mirrors C++ and builds a
 * standard composite for vanilla payoffs from a dividend schedule plus
 * an exercise type. It dispatches on
 * {@code Exercise.type() ∈ {European, American, Bermudan}} and prepends
 * a dividend handler when the schedule is non-empty.
 * <p>
 * <strong>Phase 2h scope:</strong> the underlying step-condition
 * helpers — {@code FdmDividendHandler}, {@code FdmAmericanStepCondition},
 * {@code FdmBermudanStepCondition} — are not yet ported. The factory
 * therefore supports only the European-with-no-dividends case (returns
 * an empty composite, which is what the C++ factory also does for that
 * input). Other branches throw {@link LibraryException} with a clear
 * pointer to the follow-up. {@code FdHullWhiteSwaptionEngine} /
 * {@code FdG2SwaptionEngine} (Phase 2h WI-2 / WI-3) typically pass a
 * European exercise with no dividends, matching the supported case.
 *
 * @author Phase 2h WI-1 port
 */
public class FdmStepConditionComposite implements StepCondition<Array> {

    /** Type alias matching the C++ {@code Conditions} typedef. */
    public static final class Conditions extends ArrayList<StepCondition<Array>> {
        private static final long serialVersionUID = 1L;

        public Conditions() {
            super();
        }

        public Conditions(final Collection<? extends StepCondition<Array>> initial) {
            super(initial);
        }
    }

    private final List<Double> stoppingTimes_;
    private final Conditions conditions_;

    /**
     * Build a composite from a list of per-condition stopping-time
     * vectors and the conditions themselves. The stopping times are
     * unioned, sorted ascending, and deduplicated.
     */
    public FdmStepConditionComposite(
            final List<List<Double>> stoppingTimes,
            final Conditions conditions) {
        this.conditions_ = conditions;
        final TreeSet<Double> all = new TreeSet<>();
        for (final List<Double> v : stoppingTimes) {
            all.addAll(v);
        }
        this.stoppingTimes_ = new ArrayList<>(all);
    }

    /** Sorted, deduplicated union of all per-condition stopping times. */
    public List<Double> stoppingTimes() {
        return stoppingTimes_;
    }

    /** Underlying conditions, applied in order. */
    public Conditions conditions() {
        return conditions_;
    }

    @Override
    public void applyTo(final Array a, final double t) {
        for (final StepCondition<Array> condition : conditions_) {
            condition.applyTo(a, t);
        }
    }

    /**
     * Build a vanilla-payoff composite covering dividends and the
     * three exercise types (European, American, Bermudan). See the
     * class-level scope note for what's currently implemented.
     *
     * @param schedule    dividend schedule (may be empty).
     * @param exercise    exercise (European, American, or Bermudan).
     * @param mesher      FDM mesh.
     * @param calculator  inner-value calculator used by exercise
     *                    conditions to evaluate intrinsic value.
     * @param refDate     reference date for time-fraction computation.
     * @param dayCounter  day-counter used for time fractions.
     */
    public static FdmStepConditionComposite vanillaComposite(
            final DividendSchedule schedule,
            final Exercise exercise,
            final FdmMesher mesher,
            final FdmInnerValueCalculator calculator,
            final Date refDate,
            final DayCounter dayCounter) {

        final List<List<Double>> stoppingTimes = new ArrayList<>();
        final Conditions stepConditions = new Conditions();

        if (schedule != null && !schedule.isEmpty()) {
            // C++ builds an FdmDividendHandler here and prepends it to
            // the conditions list. That class is not yet ported —
            // surface explicitly so callers know what's missing.
            throw new LibraryException(
                    "FdmStepConditionComposite.vanillaComposite: "
                  + "dividend handling requires FdmDividendHandler "
                  + "(unported, Phase 2h follow-up).");
        }

        final Exercise.Type type = exercise.type();
        if (type == Exercise.Type.European) {
            // Empty composite, matching C++ behaviour for
            // European-with-no-dividends input.
        } else if (type == Exercise.Type.American) {
            throw new LibraryException(
                    "FdmStepConditionComposite.vanillaComposite: "
                  + "American exercise requires FdmAmericanStepCondition "
                  + "(unported, Phase 2h follow-up).");
        } else if (type == Exercise.Type.Bermudan) {
            throw new LibraryException(
                    "FdmStepConditionComposite.vanillaComposite: "
                  + "Bermudan exercise requires FdmBermudanStepCondition "
                  + "(unported, Phase 2h follow-up).");
        } else {
            throw new LibraryException(
                    "exercise type is not supported: " + type);
        }

        return new FdmStepConditionComposite(stoppingTimes, stepConditions);
    }
}
