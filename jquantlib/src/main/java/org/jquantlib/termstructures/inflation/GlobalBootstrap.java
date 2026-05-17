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
 Copyright (C) 2019 SoftSolutions! S.r.l.
 Copyright (C) 2025 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.termstructures.inflation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.CostFunction;
import org.jquantlib.math.optimization.EndCriteria;
import org.jquantlib.math.optimization.LevenbergMarquardt;
import org.jquantlib.math.optimization.NoConstraint;
import org.jquantlib.math.optimization.Problem;
import org.jquantlib.time.Date;

/**
 * Global bootstrap strategy for {@link PiecewiseZeroInflationCurve} — solves
 * all pillar values simultaneously rather than one helper at a time.
 *
 * <p>Java port of QuantLib v1.42.1 {@code GlobalBootstrap<Curve>} template
 * ({@code ql/termstructures/globalbootstrap.{hpp,cpp}}), specialized for
 * {@link PiecewiseZeroInflationCurve}.
 *
 * <h3>Algorithm</h3>
 * <ol>
 *   <li>Collect alive helpers (pillarDate &gt; baseDate).</li>
 *   <li>Build dates from {@code [baseDate, helper[0].pillarDate, ...,
 *       helper[n-1].pillarDate]} (sorted, deduplicated).</li>
 *   <li>Set initial data{@code [i]} = {@link InflationTraits#AVG_INFLATION}
 *       for all pillars (data[0] is the curve's base — special-cased).</li>
 *   <li>Wire each helper to the curve (so its
 *       {@link org.jquantlib.termstructures.BootstrapHelper#impliedQuote()}
 *       reads from the curve being built).</li>
 *   <li>Run {@link LevenbergMarquardt} on the cost function whose
 *       {@code values(x)} returns the vector
 *       {@code [helper[0].quoteError(), ..., helper[n-1].quoteError()]} where
 *       {@code data[i+1] = x[i]} (and {@code data[0] = x[0]} per
 *       {@link InflationTraits#updateGuess updateGuess}).</li>
 * </ol>
 *
 * <h3>Differences from the C++ original</h3>
 * <ul>
 *   <li>Specialized to {@link PiecewiseZeroInflationCurve} — the C++ form is a
 *       fully-generic class template applicable to yield curves too. The Java
 *       yield-curve bootstrap framework has its own
 *       {@link org.jquantlib.termstructures.IterativeBootstrap}; reuse there
 *       is out of scope.</li>
 *   <li>{@code additionalHelpers}, {@code additionalDates},
 *       {@code additionalPenalties}, {@code additionalVariables},
 *       {@code instrumentWeights} and the {@code MultiCurveBootstrap}
 *       parent path are not ported — none are exercised by the inflation
 *       test surface in v1.42.1.</li>
 * </ul>
 *
 * <h3>Why a pillar-vs-pillar simultaneous solve?</h3>
 * <p>Iterative bootstrap can be unstable in cases where helpers depend on
 * neighbouring pillars (e.g. CPI::Linear with overlapping observation
 * windows). The simultaneous LM solve treats all curves as a coupled system
 * and converges on a self-consistent set of pillar values, deduplicating
 * pillars that collide on the same date.
 *
 * @see PiecewiseZeroInflationCurve
 * @see InflationTraits
 */
public final class GlobalBootstrap {

    //
    // private fields
    //

    private final double accuracy;
    private final LevenbergMarquardt optimizer;
    private final EndCriteria endCriteria;

    //
    // public constructors
    //

    /** Default optimizer / criteria — accuracy 1e-12. */
    public GlobalBootstrap() {
        this(1.0e-12);
    }

    /** Default optimizer / criteria with custom accuracy. */
    public GlobalBootstrap(final double accuracy) {
        this(accuracy,
                new LevenbergMarquardt(accuracy, accuracy, accuracy),
                new EndCriteria(1000, 10, accuracy, accuracy, accuracy));
    }

    /** Full constructor — explicit optimizer + end criteria. */
    public GlobalBootstrap(final double accuracy,
                            final LevenbergMarquardt optimizer,
                            final EndCriteria endCriteria) {
        this.accuracy = accuracy;
        this.optimizer = optimizer;
        this.endCriteria = endCriteria;
    }

    //
    // public API
    //

    /**
     * Run the global-bootstrap solve on the supplied piecewise inflation
     * curve, using the supplied helpers (in the order provided; sort is done
     * here).
     *
     * <p>Mutates the curve's internal {@code dates_/times_/data_} arrays and
     * its base rate. Mirrors C++
     * {@code GlobalBootstrap<Curve>::calculate()}
     * ({@code ql/termstructures/globalbootstrap.hpp:405-429}).
     *
     * @param curve       the piecewise inflation curve to bootstrap
     * @param instruments the bootstrap helpers (will be sorted by pillarDate)
     */
    public void calculate(final PiecewiseZeroInflationCurve<?> curve,
                          final List<ZeroCouponInflationSwapHelper> instruments) {
        QL.require(curve != null, "null piecewise inflation curve");
        QL.require(instruments != null && !instruments.isEmpty(),
                "no helpers provided to GlobalBootstrap");

        final InflationTraits traits = new InflationTraits();

        // Sort instruments by pillar date — mirrors C++ assumption that
        // instruments are monotonically arranged along the time axis.
        final List<ZeroCouponInflationSwapHelper> sorted = new ArrayList<>(instruments);
        sorted.sort((a, b) -> a.pillarDate().compareTo(b.pillarDate()));

        // Validate quotes.
        for (final ZeroCouponInflationSwapHelper h : sorted) {
            QL.require(h.quoteIsValid(),
                    "instrument has an invalid quote (pillar: " + h.pillarDate() + ")");
        }

        // Build dates: [baseDate, pillar[0], ..., pillar[n-1]] dedup. Use
        // pillarDate() (not latestDate()) — matches C++ IterativeBootstrap and
        // is required for CPI::Linear with sub-annual helpers, where two
        // consecutive helpers may share a right-node latestDate but resolve to
        // different left/right pillars via the issue-#2454 weight calculation.
        final Date baseDate = traits.initialDate(curve);
        final List<Date> dateList = new ArrayList<>();
        dateList.add(baseDate);
        for (int i = 0; i < sorted.size(); ++i) {
            final Date d = sorted.get(i).pillarDate();
            QL.require(d.gt(baseDate) || d.eq(baseDate) || d.le(baseDate) == false,
                    "instrument pillar must be on or after baseDate: " + d);
            // Deduplicate consecutive equal pillar dates (rare but possible
            // with CPI::Linear collisions — mirrors C++ std::unique).
            if (!dateList.get(dateList.size() - 1).eq(d)) {
                dateList.add(d);
            }
        }
        final int nDates = dateList.size();
        QL.require(nDates >= 2, "GlobalBootstrap: at least 2 dates required (baseDate + 1 pillar)");

        final Date[] newDates = dateList.toArray(new Date[nDates]);
        final double[] newTimes = new double[nDates];
        for (int i = 0; i < nDates; ++i) {
            newTimes[i] = curve.timeFromReference(newDates[i]);
        }

        // Initial guess: avgInflation everywhere. The LM will solve for all
        // pillars simultaneously.
        final double[] newData = new double[nDates];
        Arrays.fill(newData, traits.initialValue(curve));

        // Compute the rightmost latestDate across all helpers — the GlobalBootstrap
        // pillar grid uses pillarDate (may be a left node for CPI::Linear); the
        // curve's maxDate must still cover the right interpolation node so that
        // impliedQuote() can forecast at fixingPeriod.second+1.
        Date maxLatest = sorted.get(0).latestDate();
        for (int i = 1; i < sorted.size(); ++i) {
            final Date d = sorted.get(i).latestDate();
            if (d.gt(maxLatest)) maxLatest = d;
        }
        curve.installGlobalBootstrapState(newDates, newTimes, newData, maxLatest);

        // Wire helpers to the curve being built so their impliedQuote()
        // reads from the curve.
        for (final ZeroCouponInflationSwapHelper h : sorted) {
            h.setTermStructure(curve);
        }

        // Run LM. The cost-function dimension is nInstruments (one residual
        // per helper); the variable dimension is nDates - 1 (data[0] is
        // mirrored from data[1] inside updateGuess for i==1).
        final int nVars = nDates - 1;
        final Array x0 = new Array(nVars);
        for (int i = 0; i < nVars; ++i) {
            x0.set(i, traits.initialValue(curve));
        }

        final CostFunction cost = new GlobalBootstrapCostFunction(curve, sorted);
        final NoConstraint noConstraint = new NoConstraint();
        final Problem problem = new Problem(cost, noConstraint, x0);
        final EndCriteria.Type endType = optimizer.minimize(problem, endCriteria);
        QL.require(EndCriteria.Type.None != endType,
                "GlobalBootstrap: optimizer reported End=None");
        // Mirror C++ check that we converged — we don't fail on suboptimal
        // EndCriteria because LM may legitimately stop on StationaryPoint
        // when the curve is flat. Verify residual is acceptable instead.
        final Array resid = cost.values(problem.currentValue());
        double maxResid = 0.0;
        for (int i = 0; i < resid.size(); ++i) {
            maxResid = Math.max(maxResid, Math.abs(resid.get(i)));
        }
        if (maxResid > Math.max(accuracy * 1.0e3, 1.0e-6)) {
            throw new LibraryException(
                    "GlobalBootstrap failed to converge: max residual = " + maxResid +
                    " (accuracy = " + accuracy + ", endCriteria = " + endType + ")");
        }

        // Final data already mutated via cost-function side effects.
        curve.overrideBaseRateForGlobalBootstrap(curve.data()[0]);
    }

    /**
     * Cost function that evaluates one helper.quoteError() per dimension.
     * Each LM step writes the candidate {@code x} into the curve's data slots
     * via {@link InflationTraits#updateGuess updateGuess}.
     */
    private static final class GlobalBootstrapCostFunction extends CostFunction {

        private final PiecewiseZeroInflationCurve<?> curve;
        private final List<ZeroCouponInflationSwapHelper> helpers;
        private final InflationTraits traits = new InflationTraits();

        GlobalBootstrapCostFunction(final PiecewiseZeroInflationCurve<?> curve,
                                     final List<ZeroCouponInflationSwapHelper> helpers) {
            this.curve = curve;
            this.helpers = helpers;
        }

        @Override
        public double value(final Array x) {
            final Array v = values(x);
            double sum = 0.0;
            for (int i = 0; i < v.size(); ++i) {
                sum += v.get(i) * v.get(i);
            }
            return 0.5 * sum;
        }

        @Override
        public Array values(final Array x) {
            // Update curve data: data[i+1] = x[i] for i=0..nVars-1.
            // updateGuess(data, x[0], 1) also propagates to data[0].
            final double[] data = curve.data();
            for (int i = 0; i < x.size(); ++i) {
                traits.updateGuess(data, x.get(i), i + 1);
            }
            curve.refreshInterpolationForGlobalBootstrap();

            // One residual per helper. The number of helpers may exceed
            // nVars (when pillar deduplication shrinks the date grid).
            final Array result = new Array(helpers.size());
            for (int i = 0; i < helpers.size(); ++i) {
                result.set(i, helpers.get(i).quoteError());
            }
            return result;
        }
    }
}
