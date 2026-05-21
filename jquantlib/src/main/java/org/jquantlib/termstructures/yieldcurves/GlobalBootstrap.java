/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is released under the BSD License.

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
 Copyright (C) 2019 SoftSolutions! S.r.l.
 Copyright (C) 2025 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.termstructures.yieldcurves;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.math.interpolations.Interpolation;
import org.jquantlib.math.interpolations.Interpolation.Interpolator;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.CostFunction;
import org.jquantlib.math.optimization.EndCriteria;
import org.jquantlib.math.optimization.LevenbergMarquardt;
import org.jquantlib.math.optimization.NoConstraint;
import org.jquantlib.math.optimization.OptimizationMethod;
import org.jquantlib.math.optimization.Problem;
import org.jquantlib.termstructures.Bootstrap;
import org.jquantlib.termstructures.RateHelper;
import org.jquantlib.time.Date;

/**
 * Global bootstrap for yield curves — solves all pillar values simultaneously via Levenberg-Marquardt rather than
 * pillar-by-pillar root finding.
 *
 * <p>Java port of QuantLib v1.42.1 {@code GlobalBootstrap<Curve>} template
 * ({@code ql/termstructures/globalbootstrap.{hpp,cpp}}) specialised for {@link PiecewiseYieldCurve}. Like the inflation
 * sibling at {@code org.jquantlib.termstructures.inflation.GlobalBootstrap}, this is a non-template Java class because
 * the C++ template-parameter {@code Curve} is resolved at runtime via {@link PiecewiseYieldCurve}'s class-token
 * constructor pattern.
 *
 * <h3>Algorithm</h3>
 * <ol>
 *   <li>Collect alive instruments (those with {@code latestDate &gt; baseDate}).</li>
 *   <li>Build a sorted, deduplicated date grid from {@code [baseDate, helper[0].latestDate(), ...,
 *       helper[n-1].latestDate(), additionalDates...]}.</li>
 *   <li>Seed the curve's internal {@code data_} with {@link Traits#initialValue}.</li>
 *   <li>Wire each helper to the curve being built.</li>
 *   <li>Build a {@link LevenbergMarquardt} {@link Problem} whose residual vector is
 *       {@code [helper[i].quoteError() * weight[i]] ++ additionalPenalties}.</li>
 *   <li>Run the optimiser, then leave the curve in the converged state.</li>
 * </ol>
 *
 * <h3>Differences from the C++ original</h3>
 * <ul>
 *   <li>The C++ traits expose {@code transformDirect}/{@code transformInverse}: identity for ZeroYield and ForwardRate;
 *       {@code exp/log} for Discount. The Java {@link Traits} interface does not expose these. To preserve the
 *       expected positivity invariants on discount-curve fits, this port detects {@link Discount}-typed traits and
 *       internally applies {@code exp(x)} when writing to the curve and {@code log(x)} when reading from it
 *       (identity for {@link ZeroYield}/{@link ForwardRate}).</li>
 *   <li>{@code MultiCurveBootstrap} parent-coordinator path is not ported — none of the v1.42.1 yield-curve tests
 *       exercise it.</li>
 *   <li>{@link AdditionalBootstrapVariables} (model-parameter optimisation) is ported as of Phase 1.1-A-562-sqv;
 *       see {@link SimpleQuoteVariables} for the concrete implementation. When non-null, the variables-supplied
 *       guesses are appended to the curve-pillar guesses and {@link AdditionalBootstrapVariables#update(Array)}
 *       is invoked on every cost-function call.</li>
 *   <li>This class uses {@code latestDate()} as the pillar date (matches Java's {@link RateHelper}; the C++ original
 *       reads {@code pillarDate()} which is not yet exposed in Java BootstrapHelper).</li>
 * </ul>
 *
 * @see PiecewiseYieldCurve
 * @see Traits
 */
public class GlobalBootstrap< Curve extends PiecewiseYieldCurve > implements Bootstrap< Curve > {

    //
    // private fields
    //

    private final Class< ? > typeCurve;
    private final double accuracy;
    private final OptimizationMethod optimizer;
    private final EndCriteria endCriteria;
    private final List< RateHelper > additionalHelpers;
    private final AdditionalDatesProvider additionalDatesProvider;
    private final AdditionalPenalties additionalPenalties;
    private final AdditionalBootstrapVariables additionalVariables;
    private final double[] instrumentWeights;

    private Curve ts;
    private boolean validCurve = false;

    //
    // SAM interfaces — Java stand-ins for the C++ std::function callbacks.
    //

    /** Provider for additional pillar dates beyond those of rate helpers. */
    public interface AdditionalDatesProvider {
        List< Date > get();
    }

    /**
     * Additional penalty terms appended to the residual vector. C++ form: {@code Array(times, data)}; the Java port
     * passes them as primitive double[] for consistency with the rest of the package.
     */
    public interface AdditionalPenalties {
        Array evaluate(double[] times, double[] data);
    }

    //
    // public constructors
    //

    /** Mirror of the simplest C++ ctor — used by {@link PiecewiseYieldCurve#constructBootstrap}. */
    public GlobalBootstrap(final Class< ? > typeCurve) {
        this(typeCurve, 1.0e-12);
    }

    public GlobalBootstrap(final Class< ? > typeCurve, final double accuracy) {
        this(typeCurve, accuracy, null, null, null);
    }

    public GlobalBootstrap(final Class< ? > typeCurve, final double accuracy,
            final OptimizationMethod optimizer, final EndCriteria endCriteria,
            final double[] instrumentWeights) {
        this(typeCurve, accuracy, optimizer, endCriteria, instrumentWeights, null, null, null);
    }

    /**
     * Full constructor. {@code additionalHelpers}, {@code additionalDatesProvider}, {@code additionalPenalties},
     * {@code instrumentWeights} are optional and may be {@code null}. Equivalent to invoking the
     * {@link #GlobalBootstrap(Class, double, OptimizationMethod, EndCriteria, double[], List,
     * AdditionalDatesProvider, AdditionalPenalties, AdditionalBootstrapVariables)} overload with
     * {@code additionalVariables = null}.
     */
    public GlobalBootstrap(final Class< ? > typeCurve, final double accuracy,
            final OptimizationMethod optimizer, final EndCriteria endCriteria,
            final double[] instrumentWeights,
            final List< RateHelper > additionalHelpers,
            final AdditionalDatesProvider additionalDatesProvider,
            final AdditionalPenalties additionalPenalties) {
        this(typeCurve, accuracy, optimizer, endCriteria, instrumentWeights, additionalHelpers,
                additionalDatesProvider, additionalPenalties, null);
    }

    /**
     * Full constructor with {@link AdditionalBootstrapVariables} support. Mirrors C++
     * {@code GlobalBootstrap<Curve>(additionalHelpers, additionalDates, additionalPenalties, accuracy,
     * optimizer, endCriteria, additionalVariables, instrumentWeights)} at
     * {@code ql/termstructures/globalbootstrap.hpp:116}.
     */
    public GlobalBootstrap(final Class< ? > typeCurve, final double accuracy,
            final OptimizationMethod optimizer, final EndCriteria endCriteria,
            final double[] instrumentWeights,
            final List< RateHelper > additionalHelpers,
            final AdditionalDatesProvider additionalDatesProvider,
            final AdditionalPenalties additionalPenalties,
            final AdditionalBootstrapVariables additionalVariables) {

        QL.require(typeCurve != null, "GlobalBootstrap: typeCurve is null");
        this.typeCurve = typeCurve;
        this.accuracy = Double.isNaN(accuracy) ? 1.0e-12 : accuracy;
        // Defer optimizer / endCriteria default construction until accuracy from the curve is known (setup()).
        this.optimizer = optimizer;
        this.endCriteria = endCriteria;
        this.additionalHelpers = additionalHelpers == null ? new ArrayList<>() : new ArrayList<>(additionalHelpers);
        this.additionalDatesProvider = additionalDatesProvider;
        this.additionalPenalties = additionalPenalties;
        this.additionalVariables = additionalVariables;
        this.instrumentWeights = instrumentWeights == null ? null : instrumentWeights.clone();
    }

    //
    // Bootstrap<Curve>
    //

    @Override
    public void setup(final Curve ts) {
        QL.ensure(ts != null, "TermStructure cannot be null");
        this.ts = ts;

        // Register the curve as observer of the helpers + additional helpers.
        final RateHelper[] instruments = ts.instruments();
        for ( final RateHelper rh : instruments ) {
            rh.addObserver(ts);
        }
        for ( final RateHelper rh : additionalHelpers ) {
            rh.addObserver(ts);
        }

        QL.require(instrumentWeights == null || instrumentWeights.length == instruments.length,
                "GlobalBootstrap: number of instrument weights (" + (instrumentWeights == null ? 0
                        : instrumentWeights.length) + ") must match number of instruments ("
                        + instruments.length + ")");
    }

    @Override
    public void calculate() {

        final double curveAccuracy = ts.accuracy();
        final double effectiveAccuracy = !Double.isNaN(this.accuracy) ? this.accuracy : curveAccuracy;

        // Late default construction of optimiser & criteria — mirrors C++ which honours the curve's accuracy_.
        final OptimizationMethod opt = optimizer != null ? optimizer
                : new LevenbergMarquardt(effectiveAccuracy, effectiveAccuracy, effectiveAccuracy);
        final EndCriteria ec = endCriteria != null ? endCriteria
                : new EndCriteria(1000, 10, effectiveAccuracy, effectiveAccuracy, effectiveAccuracy);

        final Traits traits = ts.traits();
        final Interpolator interpolator = ts.interpolator();
        final RateHelper[] instruments = ts.instruments();

        // Step 1: alive instruments + weights
        final Date firstDate = traits.initialDate(ts);
        final List< RateHelper > alive = new ArrayList<>();
        final List< Double > aliveWeights = new ArrayList<>();
        final double[] weights = instrumentWeights != null ? instrumentWeights
                : filledWith(instruments.length, 1.0);
        for ( int i = 0; i < instruments.length; ++i ) {
            if ( instruments[i].latestDate().gt(firstDate) ) {
                alive.add(instruments[i]);
                aliveWeights.add(weights[i]);
            }
        }
        // Align to v1.42.1 (ql/termstructures/globalbootstrap.hpp:246-254 + :290-294):
        // C++ does NOT require aliveInstruments_ to be non-empty — when the caller passes
        // an empty instrument list together with non-empty additionalHelpers /
        // additionalDates (e.g. the second curve in
        // testGlobalBootstrapInstrumentWeights@cpp:1742), the bootstrap proceeds and is
        // anchored on additionalDates + additionalHelpers alone. The downstream
        // dates.size() >= requiredPoints check below is the actual gate.

        // Step 1b: alive additional helpers
        final List< RateHelper > aliveAdditional = new ArrayList<>();
        for ( final RateHelper rh : additionalHelpers ) {
            if ( rh.latestDate().gt(firstDate) ) {
                aliveAdditional.add(rh);
            }
        }

        // Step 2: additional dates (filtered to > firstDate)
        List< Date > addDates = additionalDatesProvider != null ? additionalDatesProvider.get() : new ArrayList<>();
        if ( addDates == null ) {
            addDates = new ArrayList<>();
        }
        final List< Date > filteredDates = new ArrayList<>();
        for ( final Date d : addDates ) {
            if ( d.gt(firstDate) ) {
                filteredDates.add(d);
            }
        }

        // Step 3: dates vector: firstDate + alive pillars + additional dates -> sort + unique
        final List< Date > dateList = new ArrayList<>();
        dateList.add(firstDate);
        for ( final RateHelper rh : alive ) {
            dateList.add(rh.latestDate());
        }
        dateList.addAll(filteredDates);
        dateList.sort(Date::compareTo);
        // Deduplicate consecutive duplicates.
        final List< Date > uniqueDates = new ArrayList<>();
        Date prev = null;
        for ( final Date d : dateList ) {
            if ( prev == null || !d.eq(prev) ) {
                uniqueDates.add(d);
                prev = d;
            }
        }

        QL.require(uniqueDates.size() >= interpolator.requiredPoints(),
                "GlobalBootstrap: not enough curve points (" + uniqueDates.size()
                        + ") for interpolation requiring at least " + interpolator.requiredPoints());

        // Step 4: build times[]
        final int nDates = uniqueDates.size();
        final Date[] dates = uniqueDates.toArray(new Date[nDates]);
        final double[] times = new double[nDates];
        for ( int i = 0; i < nDates; ++i ) {
            times[i] = ts.timeFromReference(dates[i]);
        }

        ts.setDates(dates);
        ts.setTimes(times);

        // Step 5: install initial data — only if curve cannot be used as guess.
        double[] data;
        if ( !validCurve || ts.data().length != nDates ) {
            data = new double[nDates];
            Arrays.fill(data, traits.initialValue(ts));
            ts.setData(data);
            validCurve = false;
        } else {
            data = ts.data();
        }

        // Step 6: wire helpers
        for ( final RateHelper rh : alive ) {
            QL.require(rh.quoteIsValid(),
                    "instrument has an invalid quote (pillar: " + rh.latestDate() + ")");
            rh.setTermStructure(ts);
        }
        for ( final RateHelper rh : aliveAdditional ) {
            QL.require(rh.quoteIsValid(),
                    "additional instrument has an invalid quote (pillar: " + rh.latestDate() + ")");
            rh.setTermStructure(ts);
        }

        // Step 7: install interpolation (if not yet valid)
        if ( !validCurve ) {
            ts.setInterpolation(interpolator.interpolate(new Array(times), new Array(data)));
        }

        // Step 8: initial guess for the optimiser — one variable per curve pillar i=1..n-1 (data[0] is the anchor,
        // controlled by Traits::initialValue), followed by any AdditionalBootstrapVariables guesses. Apply
        // transformInverse for Discount (log) and SimpleZeroYield (log(x - offset(t_i))); identity otherwise on the
        // curve-pillar slice — the additional-variables slice handles its own transformInverse internally.
        final boolean isDiscountTraits = traits instanceof Discount;
        final boolean isSimpleZeroTraits = traits instanceof SimpleZeroYield;
        final int nVars = nDates - 1;
        final Array additionalGuesses = additionalVariables != null ? additionalVariables.initialize(validCurve)
                : new Array(0);
        final int nAdditional = additionalGuesses.size();
        final Array guess = new Array(nVars + nAdditional);
        for ( int i = 0; i < nVars; ++i ) {
            // Mirror C++ behaviour: invoke Traits::guess for each i+1 and then call updateGuess so subsequent calls see
            // a sane state. For now, leverage initialValue as the seed — the per-pillar guess() helpers depend on
            // ts.discount(d), which itself depends on the in-progress interpolation; the LM is robust enough to start
            // from a flat seed.
            final double initVal = traits.initialValue(ts);
            traits.updateGuess(data, initVal, i + 1);
            final double xVar;
            if ( isDiscountTraits ) {
                xVar = Math.log(initVal);
            } else if ( isSimpleZeroTraits ) {
                // transformInverse: log(initVal - (-1/t + 1E-8))
                xVar = Math.log(initVal - SimpleZeroYield.transformOffset(times[i + 1]));
            } else {
                xVar = initVal;
            }
            guess.set(i, xVar);
        }
        for ( int i = 0; i < nAdditional; ++i ) {
            guess.set(nVars + i, additionalGuesses.get(i));
        }

        // Step 9: cost function — for each alive helper, residual = quoteError * weight; followed by additional
        // penalties if a provider is supplied. The additional-variables suffix of x (if any) is pushed back into
        // their backing state via AdditionalBootstrapVariables.update() before helpers are re-evaluated.
        final CostFunction cost = new GlobalCostFunction(ts, alive, aliveWeights, isDiscountTraits, isSimpleZeroTraits,
                times, additionalPenalties, additionalVariables, nVars);

        final NoConstraint noConstraint = new NoConstraint();
        final Problem problem = new Problem(cost, noConstraint, guess);
        final EndCriteria.Type endType = opt.minimize(problem, ec);
        QL.require(EndCriteria.succeeded(endType),
                "global bootstrap failed to minimize to required accuracy: " + endType);

        // Align to v1.42.1 (ql/termstructures/globalbootstrap.hpp:425-428): C++ does NOT
        // perform a post-minimize residual check — the EndCriteria.succeeded(endType)
        // guard above is the sole convergence gate. The previously inserted defensive
        // residual check (maxResid > max(accuracy*1e3, 1e-6) → throw) was added during the
        // initial port and is incorrect for the over-determined case (e.g. two helpers at
        // the same pillar date with different quotes weighted by instrumentWeights, which
        // is the explicit purpose of testGlobalBootstrapInstrumentWeights@cpp:1742): the
        // optimal solution leaves a non-zero residual proportional to the quote spread,
        // by design. Removed to match upstream.

        validCurve = true;
    }

    //
    // helpers
    //

    private static double[] filledWith(final int n, final double v) {
        final double[] a = new double[n];
        Arrays.fill(a, v);
        return a;
    }

    /**
     * Inner cost function — one residual per alive helper plus optional additional penalties.
     *
     * <p>When {@code additionalVariables != null}, the optimiser-supplied {@code x} vector is wider than the
     * curve-pillar count: the first {@code nCurvePillars} entries drive the curve data via
     * {@link Traits#updateGuess}, while the remaining entries are handed to
     * {@link AdditionalBootstrapVariables#update(Array)} so that any helpers referencing those quotes pick up
     * the updated values on the next {@link RateHelper#quoteError()} call.
     */
    private static final class GlobalCostFunction extends CostFunction {

        private final PiecewiseYieldCurve curve;
        private final List< RateHelper > alive;
        private final List< Double > aliveWeights;
        private final boolean isDiscountTraits;
        private final boolean isSimpleZeroTraits;
        private final double[] times;
        private final AdditionalPenalties additionalPenalties;
        private final AdditionalBootstrapVariables additionalVariables;
        private final int nCurvePillars;
        private final Traits traits;
        private final Interpolation interpolation;

        GlobalCostFunction(final PiecewiseYieldCurve curve, final List< RateHelper > alive,
                final List< Double > aliveWeights, final boolean isDiscountTraits, final boolean isSimpleZeroTraits,
                final double[] times, final AdditionalPenalties additionalPenalties,
                final AdditionalBootstrapVariables additionalVariables, final int nCurvePillars) {
            this.curve = curve;
            this.alive = alive;
            this.aliveWeights = aliveWeights;
            this.isDiscountTraits = isDiscountTraits;
            this.isSimpleZeroTraits = isSimpleZeroTraits;
            this.times = times;
            this.additionalPenalties = additionalPenalties;
            this.additionalVariables = additionalVariables;
            this.nCurvePillars = nCurvePillars;
            this.traits = curve.traits();
            this.interpolation = curve.interpolation();
        }

        @Override
        public double value(final Array x) {
            final Array v = values(x);
            double sum = 0.0;
            for ( int i = 0; i < v.size(); ++i ) {
                sum += v.get(i) * v.get(i);
            }
            return 0.5 * sum;
        }

        @Override
        public Array values(final Array x) {
            // Update curve data: for i=0..nCurvePillars-1, data[i+1] = transformDirect(x[i]).
            // Discount: exp(x); SimpleZeroYield: exp(x) + (-1/t + 1E-8); identity otherwise.
            final double[] data = curve.data();
            for ( int i = 0; i < nCurvePillars; ++i ) {
                final double v;
                if ( isDiscountTraits ) {
                    v = Math.exp(x.get(i));
                } else if ( isSimpleZeroTraits ) {
                    v = Math.exp(x.get(i)) + SimpleZeroYield.transformOffset(times[i + 1]);
                } else {
                    v = x.get(i);
                }
                traits.updateGuess(data, v, i + 1);
            }
            interpolation.update();

            // Push the trailing additional-variables slice back into their backing state. Mirrors C++
            // globalbootstrap.hpp:386-389 — {Array(x.begin() + nCurvePillars, x.end())}.
            if ( additionalVariables != null && x.size() > nCurvePillars ) {
                final Array suffix = new Array(x.size() - nCurvePillars);
                for ( int i = 0; i < suffix.size(); ++i ) {
                    suffix.set(i, x.get(nCurvePillars + i));
                }
                additionalVariables.update(suffix);
            }

            // Compute additional penalties first to size the result.
            Array addErrors = null;
            int addSize = 0;
            if ( additionalPenalties != null ) {
                addErrors = additionalPenalties.evaluate(times, data);
                addSize = addErrors == null ? 0 : addErrors.size();
            }

            final int n = alive.size();
            final Array result = new Array(n + addSize);
            for ( int i = 0; i < n; ++i ) {
                result.set(i, alive.get(i).quoteError() * aliveWeights.get(i));
            }
            for ( int i = 0; i < addSize; ++i ) {
                result.set(n + i, addErrors.get(i));
            }
            return result;
        }
    }
}
