/*
 Copyright (C) 2009 Zahid Hussain

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

package org.jquantlib.time;

import org.jquantlib.QL;
import org.jquantlib.lang.annotation.QualityAssurance;
import org.jquantlib.lang.annotation.QualityAssurance.Quality;
import org.jquantlib.lang.annotation.QualityAssurance.Version;
import org.jquantlib.time.calendars.NullCalendar;

/**
 * Helper class
 * <p>
 * This class provides a more comfortable interface to the argument list of Schedule's constructor.
 *
 * <p>Mirrors C++ {@code MakeSchedule} (ql/time/schedule.{hpp,cpp} v1.42.1).
 * The original Java port required all five core fields at construction time;
 * Phase 5e.5b-CFC-d-97 added the C++-style no-arg constructor + the
 * {@code .from(...).to(...).withTenor(...).withFrequency(...).withCalendar(...)
 * .withConvention(...)} fluent setters so legacy and C++-style call sites
 * coexist. The original 5-arg constructor is retained for backwards
 * compatibility with the JQuantLib test-suite.
 *
 * @author Zahid Hussain
 */
@QualityAssurance(quality = Quality.Q0_UNFINISHED, version = Version.V097, reviewers = "Richard Gomes")
public class MakeSchedule implements Cloneable {
	private Calendar calendar_;
	private Date effectiveDate_;
	private Date terminationDate_;
	private Period tenor_;
	private BusinessDayConvention convention_;
	private BusinessDayConvention terminationDateConvention_;
	private DateGeneration.Rule rule_;
	private boolean endOfMonth_;
	private Date firstDate_;
	private Date nextToLastDate_;
	/** Phase 5e.5b-CFC-d-97 — tracks whether the caller explicitly set
	 *  {@link #convention_} via {@link #withConvention(BusinessDayConvention)}
	 *  (true) versus inheriting the default at construction time (false).
	 *  Mirrors C++ {@code ext::optional<BusinessDayConvention>} semantics in
	 *  {@code MakeSchedule::operator Schedule()}: if no convention was set,
	 *  default to {@code Following} (calendar non-empty) or {@code Unadjusted}. */
	private boolean conventionExplicit_;
	/** Phase 5e.5b-CFC-d-97 — tracks whether
	 *  {@link #withTerminationDateConvention(BusinessDayConvention)} was
	 *  called explicitly. When false the schedule constructor receives the
	 *  effective convention (matching ISDA-default C++ behavior). */
	private boolean terminationConventionExplicit_;

	/**
	 * No-arg constructor — matches C++ {@code MakeSchedule()} (schedule.hpp:128).
	 * All fields are filled in via the fluent setters below before calling
	 * {@link #schedule()}.
	 *
	 * <p>Phase 5e.5b-CFC-d-97.
	 */
	public MakeSchedule() {
		this.calendar_ = new Calendar();
		this.effectiveDate_ = new Date();
		this.terminationDate_ = new Date();
		this.tenor_ = null;
		this.convention_ = BusinessDayConvention.Following;
		this.terminationDateConvention_ = BusinessDayConvention.Following;
		this.rule_ = DateGeneration.Rule.Backward;
		this.endOfMonth_ = false;
		this.firstDate_ = new Date();
		this.nextToLastDate_ = new Date();
		this.conventionExplicit_ = false;
		this.terminationConventionExplicit_ = false;
	}

	public MakeSchedule(final Date effectiveDate, final Date terminationDate,
			final Period tenor, final Calendar calendar,
			final BusinessDayConvention convention) {
		this.calendar_ = calendar;
		this.effectiveDate_ = effectiveDate;
		this.terminationDate_ = terminationDate;
		this.tenor_ = tenor;
		this.convention_ = convention;
		this.terminationDateConvention_ = convention;
		this.rule_ = DateGeneration.Rule.Backward;
		this.endOfMonth_ = false;
		this.firstDate_ = new Date();
		this.nextToLastDate_ = new Date();
		this.conventionExplicit_ = true;
		this.terminationConventionExplicit_ = true;
	}

	/** Mirror of C++ {@code MakeSchedule::from} (schedule.cpp:531-534).
	 *  Phase 5e.5b-CFC-d-97. */
	public MakeSchedule from(final Date effectiveDate) {
		this.effectiveDate_ = effectiveDate;
		return this;
	}

	/** Mirror of C++ {@code MakeSchedule::to} (schedule.cpp:536-539).
	 *  Phase 5e.5b-CFC-d-97. */
	public MakeSchedule to(final Date terminationDate) {
		this.terminationDate_ = terminationDate;
		return this;
	}

	/** Mirror of C++ {@code MakeSchedule::withTenor} (schedule.cpp:541-544).
	 *  Phase 5e.5b-CFC-d-97. */
	public MakeSchedule withTenor(final Period tenor) {
		this.tenor_ = tenor;
		return this;
	}

	/** Mirror of C++ {@code MakeSchedule::withFrequency} (schedule.cpp:546-549).
	 *  Phase 5e.5b-CFC-d-97. */
	public MakeSchedule withFrequency(final Frequency frequency) {
		this.tenor_ = new Period(frequency);
		return this;
	}

	/** Mirror of C++ {@code MakeSchedule::withCalendar} (schedule.cpp:551-554).
	 *  Phase 5e.5b-CFC-d-97. */
	public MakeSchedule withCalendar(final Calendar calendar) {
		this.calendar_ = calendar;
		return this;
	}

	/** Mirror of C++ {@code MakeSchedule::withConvention} (schedule.cpp:556-559).
	 *  Phase 5e.5b-CFC-d-97. */
	public MakeSchedule withConvention(final BusinessDayConvention conv) {
		this.convention_ = conv;
		this.conventionExplicit_ = true;
		return this;
	}

	/** Mirror of C++ {@code MakeSchedule::withTerminationDateConvention}
	 *  (schedule.cpp:561-565). Phase 5e.5b-CFC-d-97 — clone() removed so
	 *  successive fluent calls mutate the same instance, matching the C++
	 *  {@code return *this} idiom. */
	public MakeSchedule withTerminationDateConvention(
			final BusinessDayConvention conv) {
		this.terminationDateConvention_ = conv;
		this.terminationConventionExplicit_ = true;
		return this;
	}

	public MakeSchedule withRule(final DateGeneration.Rule r) {
		this.rule_ = r;
		return this;
	}

	public MakeSchedule forwards() {
		this.rule_ = DateGeneration.Rule.Forward;
		return this;
	}

	public MakeSchedule backwards() {
		this.rule_ = DateGeneration.Rule.Backward;
		return this;
	}

	public MakeSchedule endOfMonth() {
		return endOfMonth(true);
	}

	public MakeSchedule endOfMonth(final boolean flag) {
		this.endOfMonth_ = flag;
		return this;
	}

	public MakeSchedule withFirstDate(final Date d) {
		this.firstDate_ = d;
		return this;
	}

	public MakeSchedule withNextToLastDate(final Date d) {
		this.nextToLastDate_ = d;
		return this;
	}

	/**
	 * Build the schedule from the accumulated settings. Mirrors C++
	 * {@code MakeSchedule::operator Schedule() const} (schedule.cpp:597-637).
	 *
	 * <p>Default behavior when fluent fields are left unset:
	 * <ul>
	 *   <li>{@code calendar} → {@link NullCalendar} when no calendar was set
	 *   <li>{@code convention} → {@code Following} if a calendar was set,
	 *       {@code Unadjusted} otherwise
	 *   <li>{@code terminationDateConvention} → inherits the effective
	 *       {@code convention} (per ISDA specification)
	 * </ul>
	 *
	 * <p>An empty {@code effectiveDate}, empty {@code terminationDate}, or
	 * unset {@code tenor} is a usage error and triggers a QL.require
	 * failure (mirrors C++ {@code QL_REQUIRE}).
	 */
	public Schedule schedule() {
		QL.require(!effectiveDate_.isNull(), "effective date not provided");
		QL.require(!terminationDate_.isNull(), "termination date not provided");
		QL.require(tenor_ != null, "tenor/frequency not provided");

		// Mirrors C++ schedule.cpp:604-616 — derive the effective convention
		// from explicit setter, calendar presence, or Unadjusted default.
		final boolean calendarEmpty = (calendar_ == null) || calendar_.empty();
		final BusinessDayConvention convention;
		if (conventionExplicit_) {
			convention = convention_;
		} else if (!calendarEmpty) {
			convention = BusinessDayConvention.Following;
		} else {
			convention = BusinessDayConvention.Unadjusted;
		}

		// Mirrors C++ schedule.cpp:618-625 — termination convention inherits
		// effective convention when not explicitly set.
		final BusinessDayConvention termConvention = terminationConventionExplicit_
				? terminationDateConvention_
				: convention;

		// Mirrors C++ schedule.cpp:627-632 — fall back to NullCalendar when
		// no calendar was set. The Java base {@link Calendar} class is the
		// "empty" sentinel here; C++ uses {@code Calendar::empty()}.
		final Calendar calendar = calendarEmpty ? new NullCalendar() : calendar_;

		return new Schedule(effectiveDate_, terminationDate_, tenor_,
				calendar, convention, termConvention, rule_,
				endOfMonth_, firstDate_, nextToLastDate_);
	}

	@Override
	public MakeSchedule clone() {
		final MakeSchedule c = new MakeSchedule();
		c.calendar_ = calendar_;
		c.effectiveDate_ = effectiveDate_ == null ? new Date() : effectiveDate_.clone();
		c.terminationDate_ = terminationDate_ == null ? new Date() : terminationDate_.clone();
		c.tenor_ = tenor_;
		c.convention_ = convention_;
		c.terminationDateConvention_ = terminationDateConvention_;
		c.rule_ = rule_;
		c.endOfMonth_ = endOfMonth_;
		c.firstDate_ = firstDate_ == null ? new Date() : firstDate_.clone();
		c.nextToLastDate_ = nextToLastDate_ == null ? new Date() : nextToLastDate_.clone();
		c.conventionExplicit_ = conventionExplicit_;
		c.terminationConventionExplicit_ = terminationConventionExplicit_;
		return c;
	}
}
