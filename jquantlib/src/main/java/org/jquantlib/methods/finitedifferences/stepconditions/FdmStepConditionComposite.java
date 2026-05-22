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

import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.DividendSchedule;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.finitedifferences.StepCondition;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.utilities.FdmDividendHandler;
import org.jquantlib.methods.finitedifferences.utilities.FdmInnerValueCalculator;
import org.jquantlib.time.Date;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

/**
 * Composite of FDM step conditions, applied in sequence between time steps.
 * <p>
 * Java port of v1.42.1 {@code ql/methods/finitedifferences/stepconditions/fdmstepconditioncomposite.{hpp,cpp}}.
 * Implements {@link StepCondition} of {@link Array} so it can be passed directly to the rollback driver. The composite
 * holds:
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
 * an {@link FdmDividendHandler} when the schedule is non-empty.
 * American exercise adds {@link FdmAmericanStepCondition};
 * Bermudan adds {@link FdmBermudanStepCondition}.
 *
 * @author Phase 2h WI-1 port; Phase 2l Track B — American/Bermudan/dividend branches wired
 */
public class FdmStepConditionComposite implements StepCondition< Array > {

    private final List< Double > stoppingTimes_;
    private final Conditions conditions_;
    /**
     * Build a composite from a list of per-condition stopping-time vectors and the conditions themselves. The stopping
     * times are unioned, sorted ascending, and deduplicated.
     */
    public FdmStepConditionComposite(final List< List< Double > > stoppingTimes, final Conditions conditions) {
        this.conditions_ = conditions;
        final var all = new TreeSet< Double >();
        for ( final List< Double > v : stoppingTimes ) {
            all.addAll(v);
        }
        this.stoppingTimes_ = new ArrayList<>(all);
    }

    /**
     * Build a composite that overlays a snapshot-only condition {@code c1} onto an existing composite {@code c2}.
     * Mirrors C++ v1.42.1 {@code FdmStepConditionComposite::joinConditions}: the resulting composite's stopping times
     * are {@code c2.stoppingTimes() ∪ {c1.getTime()}} and its applied conditions are {@code [c2, c1]} (in that order).
     *
     * <p>This helper is used by {@code Fdm1DimSolver} / {@code Fdm2DimSolver}
     * to inject a finite-difference theta snapshot just before the first stopping time of the user-supplied composite.
     */
    public static FdmStepConditionComposite joinConditions(final FdmSnapshotCondition c1,
            final FdmStepConditionComposite c2) {
        final List< List< Double > > stoppingTimes = new ArrayList<>();
        stoppingTimes.add(new ArrayList<>(c2.stoppingTimes()));
        final List< Double > single = new ArrayList<>(1);
        single.add(c1.getTime());
        stoppingTimes.add(single);

        final Conditions conditions = new Conditions();
        conditions.add(c2);
        conditions.add(c1);

        return new FdmStepConditionComposite(stoppingTimes, conditions);
    }

    /**
     * Build a vanilla-payoff composite covering dividends and the three exercise types (European, American, Bermudan).
     * <p>
     * Mirrors C++ v1.42.1 {@code FdmStepConditionComposite::vanillaComposite}. If the schedule is non-empty, the
     * dividends are filtered to [{@code refDate}, {@code exercise.lastDate()}] and an {@link FdmDividendHandler} is
     * prepended. Two sets of stopping-time offsets are added per the C++ smoother-convergence pattern ({@code t} and
     * {@code t + 1e-5}, both clamped to maturity).
     * <p>
     * American exercise adds an {@link FdmAmericanStepCondition} with
     * {@code exerciseStart = dayCounter.yearFraction(refDate, exercise.date(0))}. Bermudan exercise adds an
     * {@link FdmBermudanStepCondition} and appends its exercise times to the stopping-time list. European exercise
     * produces no additional condition.
     *
     * @param schedule   dividend schedule (may be {@code null} or empty).
     * @param exercise   exercise (European, American, or Bermudan).
     * @param mesher     FDM mesh.
     * @param calculator inner-value calculator used by exercise conditions to evaluate intrinsic value.
     * @param refDate    reference date for time-fraction computation.
     * @param dayCounter day-counter used for time fractions.
     */
    public static FdmStepConditionComposite vanillaComposite(final DividendSchedule schedule, final Exercise exercise,
            final FdmMesher mesher, final FdmInnerValueCalculator calculator, final Date refDate,
            final DayCounter dayCounter) {

        final List< List< Double > > stoppingTimes = new ArrayList<>();
        final Conditions stepConditions = new Conditions();

        if ( schedule != null && !schedule.isEmpty() ) {
            // Filter dividends to [refDate, maturityDate].
            final Date maturityDate = exercise.lastDate();
            final DividendSchedule filteredDivs = new DividendSchedule();
            for ( final org.jquantlib.cashflow.Dividend div : schedule ) {
                final Date d = div.date();
                if ( d.ge(refDate) && d.le(maturityDate) ) {
                    filteredDivs.add(div);
                }
            }

            final FdmDividendHandler dividendCondition = new FdmDividendHandler(filteredDivs, mesher, refDate,
                    dayCounter, 0);
            stepConditions.add(dividendCondition);

            // C++ adds dividend stopping times twice: at t and at t + 1e-5,
            // both clamped to maturity — ensures smoother grid alignment.
            final double maturityTime = dayCounter.yearFraction(refDate, exercise.lastDate());
            final List< Double > divTimes1 = new ArrayList<>();
            final List< Double > divTimes2 = new ArrayList<>();
            for ( final double dt : dividendCondition.dividendTimes() ) {
                divTimes1.add(Math.min(maturityTime, dt));
                divTimes2.add(Math.min(maturityTime, dt + 1e-5));
            }
            stoppingTimes.add(divTimes1);
            stoppingTimes.add(divTimes2);
        }

        final Exercise.Type type = exercise.type();
        if ( type == Exercise.Type.European ) {
            // No additional step condition for European exercise.
        } else if ( type == Exercise.Type.American ) {
            final double exerciseStart = dayCounter.yearFraction(refDate, exercise.date(0));
            stepConditions.add(new FdmAmericanStepCondition(mesher, calculator, exerciseStart));
        } else if ( type == Exercise.Type.Bermudan ) {
            final FdmBermudanStepCondition bermudanCondition = new FdmBermudanStepCondition(exercise.dates(), refDate,
                    dayCounter, mesher, calculator);
            stepConditions.add(bermudanCondition);
            stoppingTimes.add(new ArrayList<>(bermudanCondition.exerciseTimes()));
        } else {
            throw new LibraryException("exercise type is not supported: " + type);
        }

        return new FdmStepConditionComposite(stoppingTimes, stepConditions);
    }

    /** Sorted, deduplicated union of all per-condition stopping times. */
    public List< Double > stoppingTimes() {
        return stoppingTimes_;
    }

    /** Underlying conditions, applied in order. */
    public Conditions conditions() {
        return conditions_;
    }

    @Override
    public void applyTo(final Array a, final double t) {
        for ( final StepCondition< Array > condition : conditions_ ) {
            condition.applyTo(a, t);
        }
    }

    /** Type alias matching the C++ {@code Conditions} typedef. */
    public static final class Conditions extends ArrayList< StepCondition< Array > > {
        private static final long serialVersionUID = 1L;

        public Conditions() {
            super();
        }

        public Conditions(final Collection< ? extends StepCondition< Array > > initial) {
            super(initial);
        }
    }
}
