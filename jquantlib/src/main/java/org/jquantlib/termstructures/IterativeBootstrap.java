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

        // ensure rate helpers are sorted
        Arrays.sort(instruments, new BootstrapHelperSorter());

        // check that there is no instruments with the same maturity
        for ( int i = 1; i < n; ++i ) {
            final Date m1 = instruments[i - 1].latestDate();
            final Date m2 = instruments[i].latestDate();
            QL.require(m1 != m2, "two instruments have the same maturity");
        }

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
        dates = new Date[n + 1];
        times = new /*@Time*/ double[n + 1];
        dates[0] = traits.initialDate(ts);
        times[0] = ts.timeFromReference(dates[0]);
        for ( int i = 0; i < n; ++i ) {
            dates[i + 1] = instruments[i].latestDate();
            times[i + 1] = ts.timeFromReference(dates[i + 1]);
        }
        ts.setDates(dates);
        ts.setTimes(times);

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
                                    + instrument.latestDate() + ", reference date " + ts.dates()[0] + ": "
                                    + e.getMessage());
                }
            }

            if ( !interpolator.global() ) {
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
