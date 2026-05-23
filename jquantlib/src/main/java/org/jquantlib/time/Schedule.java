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
import org.jquantlib.Settings;
import org.jquantlib.lang.annotation.QualityAssurance;
import org.jquantlib.lang.annotation.QualityAssurance.Quality;
import org.jquantlib.lang.annotation.QualityAssurance.Version;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.time.calendars.NullCalendar;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Payment schedule
 *
 * @author Zahid Hussain
 */
@QualityAssurance( quality = Quality.Q0_UNFINISHED, version = Version.V097, reviewers = "Richard Gomes" )
public class Schedule {

    //
    // private final fields
    //

    private final boolean fullInterface_;
    private final Calendar calendar_;
    private final BusinessDayConvention convention_;
    /**
     * Termination-date BDC. Phase 1-cert-D5-A: relaxed from {@code final} so {@link #until(Date)} and {@link #after(Date)}
     * can mutate the cloned schedule's convention, mirroring C++ {@code Schedule::until} / {@code Schedule::after}
     * (ql/time/schedule.cpp:424-491) which assign {@code result.terminationDateConvention_ = Unadjusted} or the original
     * {@code convention_} depending on whether the truncation date is on-grid.
     */
    private BusinessDayConvention terminationDateConvention_;
    private final boolean endOfMonth_;
    private final boolean finalIsRegular_;
    private final List< Date > dates_;
    private final List< Boolean > isRegular_;

    //
    // private fields
    //

    private Period tenor_;
    private DateGeneration.Rule rule_;
    private Date firstDate_;
    private Date nextToLastDate_;

    //
    // public methods
    //

    public Schedule(final List< Date > dates) {
        this(dates, new NullCalendar(), BusinessDayConvention.Unadjusted);
    }

    public Schedule(final List< Date > dates, final Calendar calendar) {
        this(dates, calendar, BusinessDayConvention.Unadjusted);
    }

    public Schedule(final List< Date > dates, final Calendar calendar, final BusinessDayConvention convention) {
        this.dates_ = dates;
        this.isRegular_ = new ArrayList<>(); // TODO: use a data structure backed by primitive types instead

        this.calendar_ = calendar;
        this.convention_ = convention;

        //Default values
        this.fullInterface_ = false;
        this.tenor_ = new Period();
        this.terminationDateConvention_ = convention;
        this.rule_ = DateGeneration.Rule.Forward;
        this.endOfMonth_ = false;
        this.finalIsRegular_ = true;
    }

    /**
     * Mirror of C++
     * {@code Schedule(dates, calendar, convention, terminationDateConvention, tenor, rule, endOfMonth, isRegular)}
     * (ql/time/schedule.cpp:53-74). Preserves the meta-info (tenor, rule, EOM, isRegular per-period) from an existing
     * schedule while replacing the date vector — used by date-vector "clone" patterns such as the South-African R2048
     * bond test where individual dates are adjusted (e.g. 29-Feb -> 28-Feb) but the schedule's tenor / rule /
     * regularity metadata should be retained.
     *
     * <p>Phase 5e.5b-CFC-d-93.
     */
    public Schedule(final List< Date > dates, final Calendar calendar, final BusinessDayConvention convention,
            final BusinessDayConvention terminationDateConvention, final Period tenor, final DateGeneration.Rule rule,
            final boolean endOfMonth, final List< Boolean > isRegular) {
        this.dates_ = dates;
        this.isRegular_ = (isRegular == null) ? new ArrayList<>() : new ArrayList<>(isRegular);

        this.calendar_ = calendar;
        this.convention_ = convention;
        this.terminationDateConvention_ = terminationDateConvention;

        // Mirrors C++ schedule.cpp:65-68 — if tenor doesn't allow EOM the
        // flag is forced false. {@code allowsEndOfMonth} is conditional on
        // tenor.length() >= 1 Month; we approximate the C++ behaviour by
        // honouring the caller's flag when tenor is non-empty.
        this.tenor_ = (tenor == null) ? new Period() : tenor;
        this.endOfMonth_ = this.tenor_.length() != 0 && endOfMonth;

        this.rule_ = (rule == null) ? DateGeneration.Rule.Forward : rule;

        // Has-tenor / hasIsRegular metadata derives from the supplied
        // arguments — flagging the schedule as "full interface" so
        // downstream callers (FixedRateLeg, FixedRateBond,
        // ActualActual ISMA) can read tenor / rule / isRegular without
        // throwing.
        this.fullInterface_ = (this.tenor_.length() != 0);
        this.finalIsRegular_ = this.isRegular_.isEmpty() || this.isRegular_.get(this.isRegular_.size() - 1);

        // Mirror C++ schedule.cpp:70-73 isRegular size invariant.
        QL.require(this.isRegular_.isEmpty() || this.isRegular_.size() == dates.size() - 1,
                "isRegular size (" + this.isRegular_.size() + ") must be zero or equal to the number of dates minus 1 ("
                        + (dates.size() - 1) + ")");
    }

    public Schedule(final Date effectiveDate, final Date terminationDate, final Period tenor, final Calendar calendar,
            final BusinessDayConvention convention, final BusinessDayConvention terminationDateConvention,
            final DateGeneration.Rule rule, final boolean endOfMonth) {
        this(effectiveDate, terminationDate, tenor, calendar, convention, terminationDateConvention, rule, endOfMonth,
                new Date(), new Date());
    }

    public Schedule(final Date effectiveDate, final Date terminationDate, final Period tenor, final Calendar calendar,
            final BusinessDayConvention convention, final BusinessDayConvention terminationDateConvention,
            final DateGeneration.Rule rule, final boolean endOfMonth, final Date firstDate, final Date nextToLastDate) {

        this.dates_ = new ArrayList<>(); // TODO: use a data structure backed by primitive types instead
        this.isRegular_ = new ArrayList<>(); // TODO: use a data structure backed by primitive types instead

        this.fullInterface_ = true;
        this.tenor_ = tenor;
        this.calendar_ = calendar;
        this.convention_ = convention;
        this.terminationDateConvention_ = terminationDateConvention;
        this.rule_ = rule;
        this.endOfMonth_ = endOfMonth;
        this.firstDate_ = firstDate;
        this.nextToLastDate_ = nextToLastDate;
        this.finalIsRegular_ = true;

        // sanity checks
        QL.require(effectiveDate != null && !effectiveDate.isNull(), "null effective date"); // TODO: message
        QL.require(terminationDate != null && !terminationDate.isNull(), "null termination date"); // TODO: message
        QL.require(effectiveDate.lt(terminationDate),
                "effective date (" + effectiveDate + ") later than or equal to termination date (" + terminationDate
                        + ")"); // TODO: message

        if ( tenor.length() == 0 ) {
            rule_ = DateGeneration.Rule.Zero;
        } else {
            QL.require(tenor.length() > 0, "non positive tenor (" + tenor + ") not allowed"); // TODO: message
        }

        if ( firstDate != null && !firstDate.isNull() ) {
            switch ( rule_ ) {
            case Backward:
            case Forward:
                // Phase 1-cert-D5-A: match C++ schedule.cpp:126-130 which
                // allows firstDate == terminationDate (right bound inclusive).
                // Required by testFirstDateOnMaturity.
                QL.require(firstDate.gt(effectiveDate) && firstDate.le(terminationDate),
                        "first date (" + firstDate + ") out of effective-termination date range (" + effectiveDate
                                + ", " + terminationDate + "]"); // TODO: message
                break;
            case ThirdWednesday:
                QL.require(IMM.isIMMdate(firstDate, false),
                        "first date (" + firstDate + ") is not an IMM date"); // TODO: message
                break;
            case Zero:
            case Twentieth:
            case TwentiethIMM:
            case OldCDS:
            case CDS:
            case CDS2015:
                String errMsg = "first date incompatible with " + rule_ + " date generation rule";
                throw new LibraryException(errMsg); // TODO: message
            default:
                errMsg = "unknown Rule (" + rule_ + ")";
                throw new LibraryException(errMsg); // TODO: message
            }
        }
        if ( nextToLastDate != null && !nextToLastDate.isNull() ) {
            switch ( rule_ ) {
            case Backward:
            case Forward:
                // Phase 1-cert-D5-A: match C++ schedule.cpp:155-158 which
                // allows nextToLastDate == effectiveDate (left bound inclusive).
                // Required by testNextToLastDateOnStart.
                QL.require(nextToLastDate.ge(effectiveDate) && nextToLastDate.lt(terminationDate),
                        "next to last date (" + nextToLastDate + ") out of [effective (" + effectiveDate
                                + "), termination (" + terminationDate + ")) date range"); // TODO: message
                break;
            case ThirdWednesday:
                QL.require(IMM.isIMMdate(nextToLastDate, false),
                        "first date (" + firstDate + ") is not an IMM date"); // TODO: message
            case Zero:
            case Twentieth:
            case TwentiethIMM:
            case OldCDS:
            case CDS:
            case CDS2015:
                String errMsg = "next to last date incompatible with " + rule_ + " date generation rule";
                throw new LibraryException(errMsg); // TODO: message
            default:
                errMsg = "unknown Rule (" + rule_ + ")";
                throw new LibraryException(errMsg); // TODO: message
            }
        }

        // calendar needed for endOfMonth adjustment
        final Calendar nullCalendar = new NullCalendar();
        int periods = 1;
        Date seed = new Date();
        Date exitDate;
        switch ( rule_ ) {

        case Zero:
            tenor_ = new Period(0, TimeUnit.Days);
            dates_.add(effectiveDate);
            dates_.add(terminationDate);
            isRegular_.add(Boolean.TRUE);
            break;

        case Backward:

            dates_.add(terminationDate);

            seed = terminationDate.clone();
            if ( nextToLastDate != null && !nextToLastDate.isNull() ) {
                dates_.add(0, nextToLastDate);
                final Date temp = nullCalendar.advance(seed, tenor_.mul(periods).negative(), convention, endOfMonth);
                if ( temp.ne(nextToLastDate) ) {
                    isRegular_.add(0, Boolean.FALSE);
                } else {
                    isRegular_.add(0, Boolean.TRUE);
                }
                seed = nextToLastDate.clone();
            }

            exitDate = effectiveDate.clone();
            if ( firstDate != null && !firstDate.isNull() ) {
                exitDate = firstDate.clone();
            }

            while ( true ) {
                final Date temp = nullCalendar.advance(seed, tenor_.mul(periods).negative(), convention, endOfMonth);
                if ( temp.lt(exitDate) ) {
                    break;
                } else {
                    // Skip dates that would result in duplicates after BDC
                    // adjustment (mirrors C++ schedule.cpp:229-233 dedup
                    // check inside the backward loop). Without this, a
                    // 1-day tenor on a business calendar generates one entry
                    // per calendar day; consecutive non-business days then
                    // collapse onto the same adjusted date during post-loop
                    // BDC application, leaving silent duplicates.
                    if ( calendar.adjust(dates_.get(0), convention).ne(calendar.adjust(temp, convention)) ) {
                        dates_.add(0, temp);
                        isRegular_.add(0, Boolean.TRUE);
                    }
                    ++periods;
                }
            }

            // Mirrors C++ ql/time/schedule.cpp:238-245 — use the ORIGINAL
            // {@code convention} parameter here (do NOT mutate it to Preceding
            // when {@code endOfMonth && calendar.isEndOfMonth(seed)}). The
            // EOM end-of-month interior snapping is applied separately in the
            // post-loop adjustment block (mirrors C++ schedule.cpp:381-388).
            // Phase 5e.5b-CFC-d-137: removing the in-branch convention
            // mutation that previously caused first-date Preceding-snaps on
            // EOM-flagged backward schedules (e.g. 30-Sep-2017 -> 29-Sep-2017
            // under USGovBond when the seed was 30-Sep-2022).
            //
            // Phase 1-cert-D5-A: compute isRegular for the prepended
            // effectiveDate via {@code nullCalendar.advance(dates_[1], -tenor, ...)
            // == effectiveDate} (mirrors C++ schedule.cpp:240-244). Required
            // for testBackwardRegularFirstPeriodWithFirstDate where the
            // Schedule(start=30-Sep-2017, term=30-Sep-2024, 6M, first=31-Mar-2018,
            // backward, eom) was incorrectly flagging the first period
            // irregular even though 30-Sep + 6M == 31-Mar (with eom).
            if ( calendar.adjust(dates_.get(0), convention).ne(calendar.adjust(effectiveDate, convention)) ) {
                dates_.add(0, effectiveDate);
                final Date probe = nullCalendar.advance(dates_.get(1), tenor_.negative(), convention, endOfMonth);
                isRegular_.add(0, Boolean.valueOf(probe.equals(effectiveDate)));
            }
            break;

        case Twentieth:
        case TwentiethIMM:
        case ThirdWednesday:
        case ThirdWednesdayInclusive:
        case OldCDS:
        case CDS:
        case CDS2015:
            QL.require(!endOfMonth,
                    "endOfMonth convention incompatible with " + rule_ + " date generation rule"); // TODO: message
            // fall through
        case Forward:

            // Mirrors C++ schedule.cpp lines 263-272: for CDS / CDS2015 rules,
            // the schedule may begin with the previous 20th of the month.
            if ( rule_ == DateGeneration.Rule.CDS || rule_ == DateGeneration.Rule.CDS2015 ) {
                final Date prev20th = previousTwentieth(effectiveDate, rule_);
                if ( calendar.adjust(prev20th, convention).gt(effectiveDate) ) {
                    dates_.add(prev20th.sub(new Period(3, TimeUnit.Months)));
                    isRegular_.add(Boolean.TRUE);
                }
                dates_.add(prev20th);
            } else {
                dates_.add(effectiveDate);
            }

            seed = dates_.get(dates_.size() - 1).clone();

            if ( firstDate != null && !firstDate.isNull() ) {
                dates_.add(firstDate);
                final Date temp = nullCalendar.advance(seed, tenor_.mul(periods), convention, endOfMonth);
                if ( temp.ne(firstDate) ) {
                    isRegular_.add(Boolean.FALSE);
                } else {
                    isRegular_.add(Boolean.TRUE);
                }
                seed = firstDate.clone();
            } else if ( rule_ == DateGeneration.Rule.Twentieth || rule_ == DateGeneration.Rule.TwentiethIMM
                    || rule_ == DateGeneration.Rule.OldCDS || rule_ == DateGeneration.Rule.CDS
                    || rule_ == DateGeneration.Rule.CDS2015 ) {
                Date next20th = nextTwentieth(effectiveDate, rule_);
                if ( rule_ == DateGeneration.Rule.OldCDS ) {
                    // distance rule enforced in natural days
                    final long stubDays = 30L;
                    if ( next20th.sub(effectiveDate) < stubDays ) {
                        // +1 will skip this one and get the next
                        next20th = nextTwentieth(next20th.add(1), rule_);
                    }
                }
                if ( next20th.ne(effectiveDate) ) {
                    dates_.add(next20th);
                    isRegular_.add(rule_ == DateGeneration.Rule.CDS || rule_ == DateGeneration.Rule.CDS2015);
                    seed = next20th.clone();
                }
            }

            exitDate = terminationDate.clone();
            if ( nextToLastDate != null && !nextToLastDate.isNull() ) {
                exitDate = nextToLastDate.clone();
            }

            while ( true ) {
                final Date temp = nullCalendar.advance(seed, tenor_.mul(periods), convention, endOfMonth);
                if ( temp.gt(exitDate) ) {
                    // Mirror C++ schedule.cpp:312-322: when the forward loop
                    // overshoots a non-default nextToLastDate, push the
                    // nextToLastDate explicitly (dedup-guarded) and compute
                    // isRegular against an advance-probe. Required by
                    // testBackwardRegularFirstPeriodWithFirstDate forward
                    // off-grid nextToLastDate case. Phase 1-cert-D5-A.
                    if ( nextToLastDate != null && !nextToLastDate.isNull()
                            && calendar.adjust(dates_.get(dates_.size() - 1), convention)
                                    .ne(calendar.adjust(nextToLastDate, convention)) ) {
                        dates_.add(nextToLastDate);
                        final Date probe = nullCalendar.advance(dates_.get(dates_.size() - 2), tenor_, convention,
                                endOfMonth);
                        isRegular_.add(Boolean.valueOf(probe.equals(nextToLastDate)));
                    }
                    break;
                } else {
                    // Skip dates that would result in duplicates after BDC
                    // adjustment (mirrors C++ schedule.cpp:326-330 dedup
                    // check inside the forward loop).
                    if ( calendar.adjust(dates_.get(dates_.size() - 1), convention)
                            .ne(calendar.adjust(temp, convention)) ) {
                        dates_.add(temp);
                        isRegular_.add(Boolean.TRUE);
                    }
                    ++periods;
                }
            }

            // Mirrors C++ ql/time/schedule.cpp:335-348 — use the original
            // {@code terminationDateConvention} for the termination-date
            // comparison (do NOT mutate {@code convention} to Preceding when
            // {@code endOfMonth && calendar.isEndOfMonth(seed)} as the prior
            // Java impl did). Interior EOM snapping happens in the unified
            // post-loop adjustment block. Phase 5e.5b-CFC-d-137.
            if ( calendar.adjust(dates_.get(dates_.size() - 1), terminationDateConvention)
                    .ne(calendar.adjust(terminationDate, terminationDateConvention)) )
                if ( rule_ == DateGeneration.Rule.Twentieth || rule_ == DateGeneration.Rule.TwentiethIMM
                        || rule_ == DateGeneration.Rule.OldCDS || rule_ == DateGeneration.Rule.CDS
                        || rule_ == DateGeneration.Rule.CDS2015 ) {
                    dates_.add(nextTwentieth(terminationDate, rule_));
                    isRegular_.add(Boolean.valueOf(true));
                } else {
                    dates_.add(terminationDate);
                    isRegular_.add(Boolean.valueOf(false));
                }

            break;

        default:
            final String errMsg = "unknown Rule (" + rule_ + ")";
            throw new LibraryException(errMsg); // TODO: message
        }

        // adjustments
        if ( rule_ == DateGeneration.Rule.ThirdWednesday ) {
            for ( int i = 1; i < dates_.size() - 1; ++i ) {
                dates_.set(i, Date.nthWeekday(3, Weekday.Wednesday, dates_.get(i).month(), dates_.get(i).year()));
            }
        } else if ( rule_ == DateGeneration.Rule.ThirdWednesdayInclusive ) {
            // Mirrors C++ schedule.cpp:362-364 — adjust ALL dates (including
            // first and last) to the third Wednesday of their month.
            for ( int i = 0; i < dates_.size(); ++i ) {
                dates_.set(i, Date.nthWeekday(3, Weekday.Wednesday, dates_.get(i).month(), dates_.get(i).year()));
            }
        }

        // first date not adjusted for OldCDS schedules — mirrors C++ schedule.cpp:367
        if ( convention != BusinessDayConvention.Unadjusted && rule_ != DateGeneration.Rule.OldCDS ) {
            dates_.set(0, calendar.adjust(dates_.get(0), convention));
        }

        // termination date is NOT adjusted as per ISDA
        // specifications, unless otherwise specified in the
        // confirmation of the deal or unless we're creating a CDS
        // schedule
        // (moved BEFORE the interior-date adjustment block to mirror the
        //  ordering of C++ schedule.cpp:374-388, where the EOM interior
        //  adjustment is the last per-date pass.)
        if ( terminationDateConvention != BusinessDayConvention.Unadjusted && rule_ != DateGeneration.Rule.CDS
                && rule_ != DateGeneration.Rule.CDS2015 ) {
            dates_.set(dates_.size() - 1, calendar.adjust(dates_.get(dates_.size() - 1), terminationDateConvention));
        }

        // Interior-date adjustment — mirrors C++ schedule.cpp:381-388.
        // When {@code endOfMonth && calendar.isEndOfMonth(seed)} we snap
        // each interior date to its calendar end-of-month BEFORE applying
        // the BDC, so e.g. 30-Sep stays 30-Sep instead of being knocked
        // back to a prior business day. Otherwise just apply the BDC.
        // The previous Java impl unconditionally adjusted with the BDC
        // (after mutating {@code convention=Preceding} when EOM held),
        // which both mis-snapped the first/last dates and skipped the
        // end-of-month rollover. Phase 5e.5b-CFC-d-137.
        if ( endOfMonth && seed != null && !seed.isNull() && calendar.isEndOfMonth(seed) ) {
            for ( int i = 1; i < dates_.size() - 1; ++i ) {
                dates_.set(i, calendar.adjust(Date.endOfMonth(dates_.get(i)), convention));
            }
        } else {
            for ( int i = 1; i < dates_.size() - 1; ++i ) {
                dates_.set(i, calendar.adjust(dates_.get(i), convention));
            }
        }

        // Final safety checks to remove extra next-to-last date, if
        // necessary. It can happen to be equal or later than the end
        // date due to EOM adjustments (see the Schedule test suite
        // for an example). Mirrors C++ ql/time/schedule.cpp:390-410
        // (the dedup block immediately after the EOM interior
        // adjustment). Phase 1-cert-D5-A.
        if ( dates_.size() >= 2 && dates_.get(dates_.size() - 2).ge(dates_.get(dates_.size() - 1)) ) {
            // there might be two dates only, then isRegular_ has size one
            if ( isRegular_.size() >= 2 ) {
                isRegular_.set(isRegular_.size() - 2,
                        Boolean.valueOf(dates_.get(dates_.size() - 2).equals(dates_.get(dates_.size() - 1))));
            }
            dates_.set(dates_.size() - 2, dates_.get(dates_.size() - 1));
            dates_.remove(dates_.size() - 1);
            if ( !isRegular_.isEmpty() ) {
                isRegular_.remove(isRegular_.size() - 1);
            }
        }
        if ( dates_.size() >= 2 && dates_.get(1).le(dates_.get(0)) ) {
            isRegular_.set(1, Boolean.valueOf(dates_.get(1).equals(dates_.get(0))));
            dates_.set(1, dates_.get(0));
            dates_.remove(0);
            isRegular_.remove(0);
        }
    }

    /**
     * Returns the date on or after {@code d} that is the 20th of the month and obeys the given date-generation
     * {@code rule} if it is relevant. Mirrors the C++ free function
     * {@code QuantLib::nextTwentieth(Date, DateGeneration::Rule)} declared in {@code ql/time/schedule.cpp} (anonymous
     * namespace) and exposed for parity with the public C++ {@code previousTwentieth} declaration.
     */
    public static Date nextTwentieth(final Date d, final DateGeneration.Rule rule) {
        final Date result = new Date(20, d.month(), d.year());
        if ( result.lt(d) ) {
            result.addAssign(new Period(1, TimeUnit.Months)); //result +=1*Months
        }
        if ( rule == DateGeneration.Rule.TwentiethIMM || rule == DateGeneration.Rule.OldCDS
                || rule == DateGeneration.Rule.CDS || rule == DateGeneration.Rule.CDS2015 ) {
            final Month m = result.month();
            final int mVal = m.value();
            if ( mVal % 3 != 0 ) { // not a main IMM month
                final int skip = 3 - mVal % 3;
                result.addAssign(new Period(skip, TimeUnit.Months));
            }
        }
        return result;
    }

    /**
     * Returns the date on or before {@code d} that is the 20th of the month and obeys the given date-generation
     * {@code rule} if it is relevant. Mirrors C++ {@code QuantLib::previousTwentieth(Date, DateGeneration::Rule)}
     * declared in {@code ql/time/schedule.hpp} (helper for CDS/CDS2015/OldCDS schedule construction).
     */
    public static Date previousTwentieth(final Date d, final DateGeneration.Rule rule) {
        final Date result = new Date(20, d.month(), d.year());
        if ( result.gt(d) ) {
            result.subAssign(new Period(1, TimeUnit.Months));
        }
        if ( rule == DateGeneration.Rule.TwentiethIMM || rule == DateGeneration.Rule.OldCDS
                || rule == DateGeneration.Rule.CDS || rule == DateGeneration.Rule.CDS2015 ) {
            final Month m = result.month();
            final int mVal = m.value();
            if ( mVal % 3 != 0 ) { // not a main IMM month
                final int skip = mVal % 3;
                result.subAssign(new Period(skip, TimeUnit.Months));
            }
        }
        return result;
    }

    /**
     * Private copy constructor used by {@link #until(Date)} and {@link #after(Date)} to build a mutable clone of the
     * schedule whose dates / isRegular / firstDate / nextToLastDate / terminationDateConvention can be trimmed.
     *
     * <p>Mirrors C++ {@code Schedule result = *this;} at ql/time/schedule.cpp:425 and :459.
     *
     * <p>Phase 1-cert-D5-A.
     */
    private Schedule(final Schedule other) {
        this.fullInterface_ = other.fullInterface_;
        this.calendar_ = other.calendar_;
        this.convention_ = other.convention_;
        this.terminationDateConvention_ = other.terminationDateConvention_;
        this.endOfMonth_ = other.endOfMonth_;
        this.finalIsRegular_ = other.finalIsRegular_;
        this.dates_ = new ArrayList<>(other.dates_);
        this.isRegular_ = new ArrayList<>(other.isRegular_);
        this.tenor_ = other.tenor_;
        this.rule_ = other.rule_;
        this.firstDate_ = other.firstDate_;
        this.nextToLastDate_ = other.nextToLastDate_;
    }

    /**
     * Truncate the schedule to a sub-schedule ending at {@code truncationDate}. Mirrors C++ {@code Schedule::until} at
     * ql/time/schedule.cpp:458-490.
     *
     * <p>If {@code truncationDate} is greater than or equal to the last schedule date the schedule is returned as-is.
     * Otherwise later dates are dropped and {@code truncationDate} appended if not already present (with an irregular
     * final period). {@code firstDate_} / {@code nextToLastDate_} are reset to null when they fall on or after the
     * truncation date.
     *
     * <p>Phase 1-cert-D5-A — required by Phase-1 certification D5 testTruncation port.
     */
    public Schedule until(final Date truncationDate) /* @ReadOnly */ {
        final Schedule result = new Schedule(this);
        QL.require(truncationDate.gt(result.dates_.get(0)),
                "truncation date " + truncationDate + " must be later than schedule first date " + result.dates_.get(0));
        if ( truncationDate.lt(result.dates_.get(result.dates_.size() - 1)) ) {
            // remove later dates
            while ( result.dates_.get(result.dates_.size() - 1).gt(truncationDate) ) {
                result.dates_.remove(result.dates_.size() - 1);
                if ( !result.isRegular_.isEmpty() ) {
                    result.isRegular_.remove(result.isRegular_.size() - 1);
                }
            }
            // add truncationDate if missing
            if ( !truncationDate.equals(result.dates_.get(result.dates_.size() - 1)) ) {
                result.dates_.add(truncationDate);
                result.isRegular_.add(Boolean.FALSE);
                result.terminationDateConvention_ = BusinessDayConvention.Unadjusted;
            } else {
                result.terminationDateConvention_ = this.convention_;
            }

            if ( result.nextToLastDate_ != null && !result.nextToLastDate_.isNull()
                    && result.nextToLastDate_.ge(truncationDate) ) {
                result.nextToLastDate_ = new Date();
            }
            if ( result.firstDate_ != null && !result.firstDate_.isNull() && result.firstDate_.ge(truncationDate) ) {
                result.firstDate_ = new Date();
            }
        }
        return result;
    }

    /**
     * Truncate the schedule to a sub-schedule starting at {@code truncationDate}. Mirrors C++ {@code Schedule::after} at
     * ql/time/schedule.cpp:424-456.
     *
     * <p>If {@code truncationDate} is less than or equal to the first schedule date the schedule is returned as-is.
     * Otherwise earlier dates are dropped and {@code truncationDate} prepended if not already present (with an irregular
     * leading period). {@code firstDate_} / {@code nextToLastDate_} are reset to null when they fall on or before the
     * truncation date.
     *
     * <p>Phase 1-cert-D5-A — required by Phase-1 certification D5 testTruncation port.
     */
    public Schedule after(final Date truncationDate) /* @ReadOnly */ {
        final Schedule result = new Schedule(this);
        QL.require(truncationDate.lt(result.dates_.get(result.dates_.size() - 1)),
                "truncation date " + truncationDate + " must be before the last schedule date "
                        + result.dates_.get(result.dates_.size() - 1));
        if ( truncationDate.gt(result.dates_.get(0)) ) {
            // remove earlier dates
            while ( result.dates_.get(0).lt(truncationDate) ) {
                result.dates_.remove(0);
                if ( !result.isRegular_.isEmpty() ) {
                    result.isRegular_.remove(0);
                }
            }
            // add truncationDate if missing
            if ( !truncationDate.equals(result.dates_.get(0)) ) {
                result.dates_.add(0, truncationDate);
                result.isRegular_.add(0, Boolean.FALSE);
                result.terminationDateConvention_ = BusinessDayConvention.Unadjusted;
            } else {
                result.terminationDateConvention_ = this.convention_;
            }

            if ( result.nextToLastDate_ != null && !result.nextToLastDate_.isNull()
                    && result.nextToLastDate_.le(truncationDate) ) {
                result.nextToLastDate_ = new Date();
            }
            if ( result.firstDate_ != null && !result.firstDate_.isNull() && result.firstDate_.le(truncationDate) ) {
                result.firstDate_ = new Date();
            }
        }
        return result;
    }

    // Date access
    public int size() /* @ReadOnly */ {
        return dates_.size();
    }

    public final Date at(final int i) /* @ReadOnly */ {
        return dates_.get(i);
    }

    public final Date date(final int i) /* @ReadOnly */ {
        return dates_.get(i);
    }

    public Date previousDate(final Date refDate) /* @ReadOnly */ {
        final int index = Date.lowerBound(dates_, refDate);
        if ( index > 0 )
            return dates_.get(index - 1).clone();
        else
            return new Date();
    }

    public Date nextDate(final Date refDate) /* @ReadOnly */ {
        final int index = Date.lowerBound(dates_, refDate);
        if ( index < dates_.size() )
            return dates_.get(index).clone();
        else
            return new Date();
    }

    public List< Date > dates() /* @ReadOnly */ {
        return dates_;
    }

    public boolean isRegular(final int i) /* @ReadOnly */ {
        QL.require(hasIsRegular(), "full interface (isRegular) not available"); // mirrors C++ schedule.cpp
        QL.require(i <= isRegular_.size() && i > 0,
                "index (" + i + ") must be in [1, " + isRegular_.size() + "]"); // TODO: message
        return isRegular_.get(i - 1);
    }

    /**
     * Mirror of C++ {@code Schedule::isRegular()} (ql/time/schedule.hpp:170). Returns the full per-period regularity
     * vector — used by the metadata-preserving
     * {@link #Schedule(List, Calendar, BusinessDayConvention, BusinessDayConvention, Period, DateGeneration.Rule,
     * boolean, List)} ctor when callers (e.g. R2048 South-African bond test) need to clone a schedule with adjusted
     * dates. Phase 5e.5b-CFC-d-93.
     */
    public List< Boolean > isRegular() /* @ReadOnly */ {
        QL.require(hasIsRegular(), "full interface (isRegular) not available");
        return isRegular_;
    }

    /**
     * Whether this schedule was constructed with a tenor / rule / EOM meta-information block. Mirrors C++
     * {@code Schedule::hasTenor()} (ql/time/schedule.hpp:88, schedule.hpp:206-208). The C++ accessor tests
     * {@code static_cast<bool>(tenor_)}; the Java port's {@code fullInterface_} flag is set true iff the rule-based
     * constructor was used (i.e. tenor was supplied), so the two are equivalent for our purposes.
     */
    public boolean hasTenor() /* @ReadOnly */ {
        return fullInterface_;
    }

    // Other inspectors

    /**
     * Whether per-period regularity flags are available. Mirrors C++ {@code Schedule::hasIsRegular()}
     * (ql/time/schedule.hpp:82). Returns true iff the {@link #isRegular(int)} accessor can be invoked without
     * throwing.
     */
    public boolean hasIsRegular() /* @ReadOnly */ {
        return fullInterface_ && !isRegular_.isEmpty();
    }

    /**
     * Whether the schedule was built with the full meta-information interface (rule-based constructor) versus the
     * date-vector constructor. Exposes the existing {@code fullInterface_} field so callers (e.g.
     * {@link org.jquantlib.cashflow.FixedRateLeg}, {@link org.jquantlib.instruments.bonds.FixedRateBond}) can branch on
     * the C++ {@code hasTenor()} / {@code hasIsRegular()} fall-back logic without catching
     * {@link org.jquantlib.lang.exceptions.LibraryException}.
     */
    public boolean fullInterface() /* @ReadOnly */ {
        return fullInterface_;
    }

    public boolean empty() /* @ReadOnly */ {
        return dates_.isEmpty();
    }

    public final Calendar calendar() /* @ReadOnly */ {
        return calendar_;
    }

    public final Date startDate() /* @ReadOnly */ {
        return dates_.isEmpty() ? null : dates_.get(0);
    }

    public final Date endDate() /* @ReadOnly */ {
        return dates_.isEmpty() ? null : dates_.get(dates_.size() - 1);
    }

    public final Period tenor() /* @ReadOnly */ {
        QL.require(hasTenor(), "full interface (tenor) not available"); // mirrors C++ schedule.hpp:211-212
        return tenor_;
    }

    public BusinessDayConvention businessDayConvention() /* @ReadOnly */ {
        return convention_;
    }

    public BusinessDayConvention terminationDateBusinessDayConvention() /* @ReadOnly */ {
        QL.require(fullInterface_, "full interface not available"); // TODO: message
        return terminationDateConvention_;
    }

    // Iterators

    public DateGeneration.Rule rule() /* @ReadOnly */ {
        QL.require(fullInterface_, "full interface not available"); // TODO: message
        return rule_;
    }

    public boolean endOfMonth() /* @ReadOnly */ {
        QL.require(fullInterface_, "full interface not available"); // TODO: message
        return endOfMonth_;
    }

    /**
     * @deprecated C++ iterator-pair API; use {@link #dates()} and standard
     *             {@link java.util.List} iteration instead. Throws
     *             unconditionally because the C++ begin()/end() return
     *             {@code const_iterator}s into the internal vector — Java
     *             has no analogous concept and no caller exists. Retained
     *             only for signature/audit parity.
     */
    @Deprecated
    public Iterator< Date > begin() /* @ReadOnly */ {
        throw new UnsupportedOperationException("Schedule.begin: use dates().iterator() instead");
    }

    /** See {@link #begin()} — same rationale. */
    @Deprecated
    public Iterator< Date > end() /* @ReadOnly */ {
        throw new UnsupportedOperationException("Schedule.end: use dates().iterator() instead");
    }

    //TODO :: operator Schedule() const;

    public int lowerBound() /* @ReadOnly */ {
        return lowerBound(new Date());
    }

    public int lowerBound(final Date refDate) /* @ReadOnly */ {
        final Date d = (refDate.isNull() ? new Settings().evaluationDate() : refDate);
        return Date.lowerBound(dates_, d.clone());
    }

    /**
     * Standard C++ Library Reference lower_bound Finds the position of the first element in an ordered range that has a
     * value greater than or equivalent to a specified value, where the ordering criterion may be specified by a binary
     * predicate.
     *
     * @see http://www.sgi.com/tech/stl/lower_bound.html
     */
    // FIXME: http://bugs.jquantlib.org/view.php?id=67
    private Iterator< Date > std_lower_bound(final Date date) {

        final List< Date > ldates = new ArrayList<>();

        if ( dates_.size() > 0 ) {
            int index = -1;
            for ( int i = 0; i < dates_.size(); i++ ) {
                final Date d = dates_.get(i);
                if ( d.equals(date) ) {
                    index = i;
                    break;
                }
            }
            if ( index > 0 ) {
                for ( int i = index; i < dates_.size(); i++ ) {
                    ldates.add(dates_.get(i));
                }
                return ldates.iterator();
            }
        }
        return ldates.iterator();
    }

    public Iterator< Date > getDatesAfter(final Date date) {
        return std_lower_bound(date);
    }
}
