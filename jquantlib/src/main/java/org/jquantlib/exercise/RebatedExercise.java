/*
 Copyright (C) 2007 Richard Gomes

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
 Copyright (C) 2013 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
*/

package org.jquantlib.exercise;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.lang.annotation.QualityAssurance;
import org.jquantlib.lang.annotation.QualityAssurance.Quality;
import org.jquantlib.lang.annotation.QualityAssurance.Version;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.NullCalendar;

/**
 * Rebated exercise.
 * <p>
 * In case of exercise the holder receives a rebate (if positive) or pays it (if negative)
 * on the rebate settlement date.
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/rebatedexercise.hpp} / {@code ql/rebatedexercise.cpp}
 * (Copyright 2013 Peter Caspers). It wraps a base {@link Exercise}, copying its type and
 * exercise dates, and augments it with per-date rebate amounts plus a rebate payment
 * calendar/convention used to derive the rebate payment dates.
 * <p>
 * <b>Divergence note (sealed hierarchy):</b> in C++, {@code RebatedExercise} derives
 * directly from {@code Exercise}. The JQuantLib {@code Exercise} base was modernized to a
 * JDK 25 {@code sealed} class (JEP 409); to preserve the C++ {@code : public Exercise}
 * relationship this type is listed in the {@code Exercise} {@code permits} clause and is
 * declared {@code final} (it has no further subtypes, mirroring the C++ leaf class).
 *
 * @author Peter Caspers (C++ original)
 */
@QualityAssurance( quality = Quality.Q1_TRANSLATION, version = Version.V097, reviewers = { "Jose Moya" } )
public final class RebatedExercise extends Exercise {

    //
    // private fields
    //

    private final List< /* @Real */ Double > rebates;
    private final /* @Natural */ int rebateSettlementDays;
    private final Calendar rebatePaymentCalendar;
    private final BusinessDayConvention rebatePaymentConvention;

    //
    // public constructors
    //

    /**
     * Constructs a rebated exercise with a single rebate broadcast to every exercise date,
     * a settlement of 0 days, a {@link NullCalendar} and the {@code Following} convention.
     * <p>
     * Mirrors the C++ default arguments
     * {@code RebatedExercise(exercise, rebate = 0.0, rebateSettlementDays = 0,
     * rebatePaymentCalendar = NullCalendar(), rebatePaymentConvention = Following)}.
     *
     * @param exercise the base exercise whose type and dates are copied
     */
    public RebatedExercise(final Exercise exercise) {
        this(exercise, 0.0, 0, new NullCalendar(), BusinessDayConvention.Following);
    }

    /**
     * Constructs a rebated exercise with a single rebate broadcast to every exercise date.
     * <p>
     * C++ {@code ql/rebatedexercise.cpp:25-33}: copies the base exercise and sets
     * {@code rebates_ = std::vector<Real>(dates().size(), rebate)} (broadcast). The scalar
     * constructor performs no exercise-type check.
     *
     * @param exercise                the base exercise whose type and dates are copied
     * @param rebate                  the rebate amount broadcast to every exercise date
     * @param rebateSettlementDays    the number of business days between exercise and rebate payment
     * @param rebatePaymentCalendar   the calendar used to advance the exercise date
     * @param rebatePaymentConvention the business-day convention used by the advance
     */
    public RebatedExercise(final Exercise exercise,
                           final /* @Real */ double rebate,
                           final /* @Natural */ int rebateSettlementDays,
                           final Calendar rebatePaymentCalendar,
                           final BusinessDayConvention rebatePaymentConvention) {
        super(exercise.type());
        copyDatesFrom(exercise);
        // C++ rebatedexercise.cpp:30 -> rebates_(std::vector<Real>(dates().size(), rebate))
        this.rebates = new ArrayList<>(super.dates.size());
        for ( int i = 0; i < super.dates.size(); i++ ) {
            this.rebates.add(rebate);
        }
        this.rebateSettlementDays = rebateSettlementDays;
        this.rebatePaymentCalendar = rebatePaymentCalendar;
        this.rebatePaymentConvention = rebatePaymentConvention;
    }

    /**
     * Constructs a rebated exercise with a vector of per-date rebates.
     * <p>
     * C++ {@code ql/rebatedexercise.cpp:35-53}: copies the base exercise, sets
     * {@code rebates_ = rebates}, then requires (in this order) that the exercise type is
     * {@code Bermudan} and that the rebates count equals the exercise dates count.
     *
     * @param exercise                the base exercise whose type and dates are copied
     * @param rebates                 one rebate per exercise date (must match the dates count)
     * @param rebateSettlementDays    the number of business days between exercise and rebate payment
     * @param rebatePaymentCalendar   the calendar used to advance the exercise date
     * @param rebatePaymentConvention the business-day convention used by the advance
     */
    public RebatedExercise(final Exercise exercise,
                           final List< /* @Real */ Double > rebates,
                           final /* @Natural */ int rebateSettlementDays,
                           final Calendar rebatePaymentCalendar,
                           final BusinessDayConvention rebatePaymentConvention) {
        super(exercise.type());
        copyDatesFrom(exercise);
        this.rebates = new ArrayList<>(rebates);
        this.rebateSettlementDays = rebateSettlementDays;
        this.rebatePaymentCalendar = rebatePaymentCalendar;
        this.rebatePaymentConvention = rebatePaymentConvention;

        // C++ rebatedexercise.cpp:44-46
        QL.require(super.type == Exercise.Type.Bermudan,
                "a rebate vector is allowed only for a bermudan style exercise");

        // C++ rebatedexercise.cpp:48-52
        QL.require(this.rebates.size() == super.dates.size(),
                "the number of rebates (%d) must be equal to the number of exercise dates (%d)",
                this.rebates.size(), super.dates.size());
    }

    //
    // private methods
    //

    /**
     * Copies (by clone) the exercise dates of the wrapped exercise into this exercise's
     * date list, reproducing the C++ {@code Exercise(exercise)} copy of {@code dates_}.
     */
    private void copyDatesFrom(final Exercise exercise) {
        QL.require(exercise != null, "null exercise");
        for ( final Date d : exercise.dates() ) {
            super.dates.add(d.clone());
        }
    }

    //
    // public methods
    //

    /**
     * Returns the rebate for the given exercise-date index.
     * <p>
     * C++ {@code ql/rebatedexercise.hpp:64-69}.
     *
     * @param index the exercise-date index (0-based)
     * @return the rebate amount at {@code index}
     */
    public /* @Real */ double rebate(final /* @Size */ int index) {
        QL.require(index < rebates.size(),
                "rebate with index %d does not exist (0...%d)", index, rebates.size() - 1);
        return rebates.get(index);
    }

    /**
     * Returns the rebate payment date for the given exercise-date index.
     * <p>
     * C++ {@code ql/rebatedexercise.hpp:71-78}: only valid for European or Bermudan styles;
     * the date is {@code rebatePaymentCalendar.advance(dates_[index], rebateSettlementDays,
     * Days, rebatePaymentConvention)}.
     *
     * @param index the exercise-date index (0-based)
     * @return the rebate payment date at {@code index}
     */
    public Date rebatePaymentDate(final /* @Size */ int index) {
        QL.require(super.type == Exercise.Type.European || super.type == Exercise.Type.Bermudan,
                "for american style exercises the rebate payment date has to be calculted in the client code");
        // C++ rebatedexercise.hpp:75-77 -> advance(date, n, Days, convention) [endOfMonth defaults false]
        return rebatePaymentCalendar.advance(super.dates.get(index), rebateSettlementDays,
                TimeUnit.Days, rebatePaymentConvention, false);
    }

    /**
     * Returns the (immutable view of the) rebates list.
     * <p>
     * C++ {@code ql/rebatedexercise.hpp:55}.
     *
     * @return the rebates, one per exercise date
     */
    public List< /* @Real */ Double > rebates() {
        return rebates;
    }

}
