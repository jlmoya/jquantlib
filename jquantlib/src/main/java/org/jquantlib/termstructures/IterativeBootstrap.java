/*
Copyright (C) 2011 Richard Gomes

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
package org.jquantlib.termstructures;

import org.jquantlib.QL;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.lang.reflect.ReflectConstants;
import org.jquantlib.math.interpolations.Interpolation;
import org.jquantlib.math.interpolations.Interpolation.Interpolator;
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.solvers1D.Brent;
import org.jquantlib.termstructures.yieldcurves.PiecewiseCurve;
import org.jquantlib.termstructures.yieldcurves.PiecewiseYieldCurve;
import org.jquantlib.termstructures.yieldcurves.Traits;
import org.jquantlib.time.Date;

import java.util.Arrays;

/**
 * Universal piecewise-term-structure boostrapper.
 *
 * @author Richard Gomes
 */

@SuppressWarnings("unchecked")
public class IterativeBootstrap< Curve extends PiecewiseYieldCurve > implements Bootstrap< Curve > {

    //
    // private fields
    //

    final private Class< ? > typeCurve;
    private boolean validCurve;
    /**
     * Whether the convergence loop is required. Seeded from {@code Interpolator::global} and forced to {@code true}
     * when any helper's pillar date falls before its latest relevant date.
     * <p>
     * Mirrors C++ v1.43 {@code IterativeBootstrap::loopRequired_}
     * ({@code ql/termstructures/iterativebootstrap.hpp:113, 210-212}).
     */
    private boolean loopRequired;
    private PiecewiseCurve ts;
    private RateHelper[] instruments;
    private Traits traits;
    private Interpolator interpolator;

    //
    // final private fields
    //
    private Interpolation interpolation;

    //
    // public constructors
    //

    public IterativeBootstrap(final Class< ? > typeCurve) {

        if ( typeCurve == null ) {
            throw new LibraryException("null PiecewiseCurve");
        }
        if ( !PiecewiseCurve.class.isAssignableFrom(typeCurve) ) {
            throw new LibraryException(ReflectConstants.WRONG_ARGUMENT_TYPE);
        }
        this.typeCurve = typeCurve;

        this.validCurve = false;
        this.ts = null;
    }

    //
    // implements Bootstrap
    //

    @Override
    public void setup(final Curve ts) {

        QL.ensure(ts != null, "TermStructure cannot be null");
        if ( !this.typeCurve.isAssignableFrom(ts.getClass()) ) {
            throw new LibraryException(ReflectConstants.WRONG_ARGUMENT_TYPE);
        }

        this.ts = ts;
        this.interpolator = ts.interpolator();
        this.interpolation = ts.interpolation();
        this.traits = ts.traits();
        this.instruments = ts.instruments();

        final int n = instruments.length;
        QL.require(n + 1 >= ts.interpolator().requiredPoints(), "not enough instruments provided");

        for ( int i = 0; i < n; ++i ) {
            instruments[i].addObserver(ts);
        }
    }

    @Override
    public void calculate() {

        final int n = instruments.length;
        Date[] dates = ts.dates();
        /*@Time*/
        double[] times = ts.times();
        double[] data = ts.data();

        // ensure rate helpers are sorted (by pillar date — C++ v1.43
        // iterativebootstrap.hpp:158-159 via detail::BootstrapHelperSorter)
        Arrays.sort(instruments, new BootstrapHelperSorter());

        // check that there is no instruments with invalid quote
        for ( int i = 0; i < n; ++i ) {
            QL.require(instruments[i].quoteIsValid(), " instrument has an invalid quote");
        }

        // setup instruments
        for ( int i = 0; i < n; ++i ) {
            // don't try this at home!
            // This call creates instruments, and removes "const".
            // There is a significant interaction with observability.
            instruments[i].setTermStructure(ts);
        }

        // calculate dates and times
        // Port of C++ v1.43 iterativebootstrap.hpp:181-215. Three distinct
        // dates per helper matter here:
        //   - pillarDate()        : where the curve node is placed
        //   - latestRelevantDate(): how far the curve must actually reach
        //   - latestDate()        : the legacy single date (== pillar for most
        //                           helpers)
        // The curve's max date is the accumulated latestRelevantDate, NOT the
        // last pillar: an instrument whose last cashflow pays after its pillar
        // (payment lag, payment-calendar adjustment) still needs discounting
        // past the last node.
        dates = new Date[n + 1];
        times = new /*@Time*/ double[n + 1];
        dates[0] = traits.initialDate(ts);
        times[0] = ts.timeFromReference(dates[0]);
        // loopRequired_ is seeded from the interpolator's globality and forced
        // true below when a pillar precedes its latest relevant date.
        loopRequired = interpolator.global();
        Date maxDate = dates[0];
        for ( int i = 0; i < n; ++i ) {
            dates[i + 1] = instruments[i].pillarDate();
            times[i + 1] = ts.timeFromReference(dates[i + 1]);

            // check for duplicated pillars (C++ iterativebootstrap.hpp:187-188)
            QL.require(!dates[i].eq(dates[i + 1]), "more than one instrument with pillar " + dates[i + 1]);

            final Date latestRelevantDate = instruments[i].latestRelevantDate();
            // check that the helper is really extending the curve, i.e. that
            // pillar-sorted helpers are also sorted by latestRelevantDate
            // (C++ iterativebootstrap.hpp:190-198)
            QL.require(latestRelevantDate.gt(maxDate),
                    (i + 1) + " instrument (pillar: " + dates[i + 1] + ") has latestRelevantDate ("
                            + latestRelevantDate + ") before or equal to previous instrument's latestRelevantDate ("
                            + maxDate + ")");
            maxDate = Date.max(dates[i + 1], latestRelevantDate);

            // when a pillar date is before the last relevant date the
            // convergence loop is required even if the Interpolator is local
            // (C++ iterativebootstrap.hpp:201-203)
            if ( dates[i + 1].lt(latestRelevantDate) ) {
                loopRequired = true;
            }
        }
        ts.setDates(dates);
        ts.setTimes(times);
        // C++ iterativebootstrap.hpp:205 — ts_->maxDate_ = maxDate;
        ts.setMaxDate(maxDate);

        // set initial guess only if the current curve cannot be used as guess
        if ( validCurve ) {
            QL.ensure(ts.data().length == n + 1, "dimension mismatch");
        } else {
            data = new /*@Rate*/ double[n + 1];
            data[0] = traits.initialValue(ts);
            for ( int i = 0; i < n; ++i ) {
                data[i + 1] = traits.initialGuess();
            }
            ts.setData(data);
        }

        final Brent solver = new Brent();
        final int maxIterations = traits.maxIterations();

        for ( int iteration = 0; ; ++iteration ) {
            // only read safe to use as a reference
            final double[] previousData = data.clone();
            // restart from the previous interpolation
            if ( validCurve ) {
                ts.setInterpolation(interpolator.interpolate(new Array(times), new Array(data)));
            }

            for ( int i = 1; i < n + 1; ++i ) {
                /*
                for (int k = 0; k < data.size(); ++ k)
                {
                    StringBuilder sb = new StringBuilder ();
                    sb.append ("Date: ");
                    sb.append (dates[k]);
                    sb.append ("\t Time: ");
                    sb.append (df.format (times.get (k)));
                    sb.append ("\t Discount: ");
                    sb.append (df.format (data.get(k)));
                    QL.debug (sb.toString ());
                }
                */

                // calculate guess before extending interpolation
                // to ensure that any extrapolation is performed
                // using the curve bootstrapped so far and no more
                final RateHelper instrument = instruments[i - 1];
                double guess = 0.0;
                if ( validCurve || iteration > 0 ) {
                    guess = ts.data()[i];
                } else if ( i == 1 ) {
                    guess = traits.initialGuess();
                } else {
                    // most traits extrapolate
                    guess = traits.guess(ts, dates[i]);
                }

                //QL.debug (" Guess : " + ((Double)(guess)).toString());

                // bracket
                // Phase Bug-Fix-Curve: pass times[] and validData to traits so
                // pillar-aware bounds (Discount/ZeroYield/ForwardRate) can
                // mirror C++ v1.42.1. Per bootstraptraits.hpp + iterativebootstrap.hpp,
                // validData = (validCurve_ at start) || (iteration > 0); Java
                // tracks the same condition as `validCurve || iteration > 0`.
                final boolean validData = validCurve || iteration > 0;
                final double min = traits.minValueAfter(i, data, validData, times);
                final double max = traits.maxValueAfter(i, data, validData, times);

                if ( guess <= min || guess >= max ) {
                    guess = (min + max) / 2.0;
                }

                if ( !validCurve && iteration == 0 ) {
                    // extend interpolation a point at a time
                    // Phase 3e: align to v1.42.1 — use first i+1 entries of
                    // BOTH times AND data; previous Java code passed full
                    // `data` array which mismatched the partial times.
                    try {
                        ts.setInterpolation(interpolator.interpolate(new Array(times, i + 1), new Array(data, i + 1)));
                    } catch ( final Exception e ) {
                        // Phase Bug-Fix-3: align to v1.42.1 — C++ pattern at
                        // iterativebootstrap.hpp:301-309 is:
                        //   if (!Interpolator::global) throw;  // no chance to fix it in a later iteration
                        //   else use Linear while target is not usable yet
                        // The previous Java code had the condition INVERTED
                        // (threw when global() was true) which broke any
                        // global interpolator (LogCubic, Cubic) bootstrap on
                        // partial data (testLogCubicDiscountConsistency).
                        if ( !ts.interpolator().global() ) {
                            throw new LibraryException("no chance to fix it in a later iteration");
                        }

                        // otherwise use Linear while the target interpolation
                        // is not usable yet
                        ts.setInterpolation(new Linear().interpolate(new Array(times, i + 1), new Array(data, i + 1)));
                    }
                }
                // required because we just changed the data
                // is it really required?
                ts.interpolation().update();

                try {
                    final BootstrapError error = new BootstrapError(traits, ts, instrument, i);
                    final double r = solver.solve(error, ts.accuracy(), guess, min, max);
                    // redundant assignment (as it has been already performed
                    // by BootstrapError in solve procedure), but safe
                    data[i] = r;
                } catch ( final Exception e ) {
                    // Phase Bug-Fix-3: align to v1.42.1 — C++ pattern is:
                    //   if (validCurve_) { invalidate, recurse, return; }
                    //   else throw with descriptive message.
                    // The previous Java code silently logged "could not bootstrap"
                    // via QL.error and continued — leaving data[i] uninitialized,
                    // which silently produced garbage curves and broke downstream
                    // consistency tests (testSplineZeroConsistency,
                    // testSplineForwardConsistency, testLogCubicDiscountConsistency,
                    // testLiborFixing, testJpyLibor).
                    if ( validCurve ) {
                        // the previous curve state might have been a bad guess,
                        // so we retry without using it.
                        validCurve = false;
                        calculate();
                        return;
                    }
                    throw new LibraryException(
                            "iteration " + (iteration + 1) + ": failed at " + i + "th alive instrument" + ", pillar "
                                    + instrument.pillarDate() + ", maturity " + instrument.maturityDate()
                                    + ", reference date " + ts.dates()[0] + ": " + e.getMessage());
                }
            }

            // C++ v1.43 iterativebootstrap.hpp:344-345 — the convergence loop
            // is skipped only when loopRequired_ is false; a local interpolator
            // is no longer sufficient on its own, because a pillar placed
            // before its instrument's latest relevant date makes later pillars
            // feed back into earlier ones.
            if ( !loopRequired ) {
                break; // no need for convergence loop
            } else if ( !validCurve && iteration == 0 ) {
                // ensure the target interpolation is used
                ts.setInterpolation(interpolator.interpolate(new Array(times), new Array(data)));

                // at least one more iteration is needed to check convergence
                continue;
            }

            // exit conditions
            double improvement = 0.0;
            for ( int i = 1; i < n + 1; ++i ) {
                improvement = Math.max(improvement, Math.abs(data[i] - previousData[i]));
            }
            //QL.debug ("improvement :" + ((Double) improvement).toString());
            if ( improvement <= ts.accuracy() ) {
                // convergence reached
                break;
            }

            QL.require(iteration + 1 < maxIterations,
                    "convergence not reached after " + ((Integer) (iteration + 1))
                            + " iterations; last improvement " + ((Double) (improvement))
                            + ", required accuracy " + ((Double) (ts.accuracy())));

        }
        validCurve = true;
    }

}
