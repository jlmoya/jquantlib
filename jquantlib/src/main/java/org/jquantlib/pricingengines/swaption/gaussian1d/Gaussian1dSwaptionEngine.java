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
 Copyright (C) 2013 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.
*/
package org.jquantlib.pricingengines.swaption.gaussian1d;

import org.jquantlib.QL;
import org.jquantlib.cashflow.FixedRateCoupon;
import org.jquantlib.cashflow.FloatingRateCoupon;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.Settlement;
import org.jquantlib.instruments.Swaption;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.math.Constants;
import org.jquantlib.math.interpolations.CubicInterpolation;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.model.shortrate.onefactormodels.gaussian1d.Gaussian1dModel;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;
import org.jquantlib.time.Schedule;

import java.util.ArrayList;
import java.util.List;

/**
 * One-factor Gaussian1d-model swaption engine (standard).
 * <p>
 * Java port of C++ QuantLib v1.42.1 {@code ql/pricingengines/swaption/gaussian1dswaptionengine.{hpp,cpp}} (commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}). Phase 2j WI-2.1.
 *
 * <p>Backward-induction integration on a Gaussian1d state grid: at each
 * exercise date the conditional continuation value is interpolated with a natural cubic spline (Lagrange end
 * conditions) and then convolved with the conditional Gaussian transition density via
 * {@link Gaussian1dModel#gaussianShiftedPolynomialIntegral}. The exercise value at each grid point is the
 * (sign-adjusted) net swap NPV divided by the model numeraire, evaluated at that state.
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 * <li>The C++ engine extends
 *     {@code GenericModelEngine<Gaussian1dModel, Swaption::arguments,
 *     Swaption::results>} which carries the model handle, observer wiring and
 *     refresh contract. The Java {@link Gaussian1dModel} hierarchy does not
 *     yet expose a {@code GenericModelEngine} mirror, so this class extends
 *     {@link Swaption.EngineImpl} (the existing Java base for swaption
 *     engines) and stores the model + discount-curve handles directly,
 *     mirroring {@link
 *     org.jquantlib.pricingengines.swaption.JamshidianSwaptionEngine}.
 * <li>The C++ engine accepts both {@code shared_ptr<Gaussian1dModel>} and
 *     {@code Handle<Gaussian1dModel>} via overloaded ctors. Java collapses to
 *     the single direct-reference form (Java has no "handle of model"
 *     pattern in current use).
 * <li>The probability-distribution branches (C++ {@code Probabilities::Naive}
 *     and {@code Digital}) are <strong>not implemented</strong> in this port
 *     — they are an additional-results decoration that no Java caller in
 *     scope of Phase 2j depends on. The {@link Probabilities#None} default
 *     branch is fully ported; the other two will fail-fast with an
 *     {@code UnsupportedOperationException} if requested. Tracked as a
 *     follow-up for Phase 2j WI-3.x once a non-{@code None} caller appears.
 * <li>OpenMP parallelism around the inner state-grid loop is omitted (no
 *     analogue exists in the JQuantLib engine layer). The serial loop matches
 *     the C++ semantics exactly.
 * <li>Java {@link Swaption.ArgumentsImpl} stores the underlying
 *     {@link VanillaSwap} reference directly (see Swaption.java and the
 *     analogous note on {@link
 *     org.jquantlib.pricingengines.swaption.JamshidianSwaptionEngine}) — so
 *     fixed/floating coupon data are read from
 *     {@code args.swap.fixedLeg()} / {@code args.swap.floatingLeg()} rather
 *     than from the C++ {@code arguments_.fixedCoupons / fixedPayDates /
 *     floatingFixingDates / floatingPayDates / floatingAccrualTimes /
 *     floatingSpreads / nominal} projections that {@code FixedVsFloatingSwap}
 *     would otherwise populate.
 * </ul>
 *
 * <p><strong>Settlement contract.</strong> Mirrors C++:
 * {@link Settlement.Method#ParYieldCurve} (cash settled) is rejected.
 * Physical and {@link Settlement.Method#CollateralizedCashPrice} are accepted.
 *
 * @see Gaussian1dModel
 * @see Swaption
 */
public class Gaussian1dSwaptionEngine extends Swaption.EngineImpl {

    private final Gaussian1dModel model_;
    private final int integrationPoints_;
    private final double stddevs_;
    private final boolean extrapolatePayoff_;
    private final boolean flatPayoffExtrapolation_;
    private final Handle< YieldTermStructure > discountCurve_;
    private final Probabilities probabilities_;
    /**
     * Defaults: 64 integration points, 7.0 stddevs, extrapolate=true, flatExtrapolation=false, empty discountCurve,
     * Probabilities.None.
     */
    public Gaussian1dSwaptionEngine(final Gaussian1dModel model) {
        this(model, 64, 7.0, true, false, new Handle< YieldTermStructure >(), Probabilities.None);
    }

    //
    // ──────────────────────────────────────────────────────────────────────
    //   Constructors (mirror gaussian1dswaptionengine.hpp 1:1, defaults
    //   collapsed via overloads)
    // ──────────────────────────────────────────────────────────────────────
    //

    /**
     * Override integration density / std-dev cap; extrapolate=true, flatExtrapolation=false, empty discountCurve,
     * Probabilities.None.
     */
    public Gaussian1dSwaptionEngine(final Gaussian1dModel model, final int integrationPoints, final double stddevs) {
        this(model, integrationPoints, stddevs, true, false, new Handle< YieldTermStructure >(), Probabilities.None);
    }

    /** Common five-arg form (defaults discountCurve and probabilities). */
    public Gaussian1dSwaptionEngine(final Gaussian1dModel model, final int integrationPoints, final double stddevs,
            final boolean extrapolatePayoff, final boolean flatPayoffExtrapolation) {
        this(model, integrationPoints, stddevs, extrapolatePayoff, flatPayoffExtrapolation,
                new Handle< YieldTermStructure >(), Probabilities.None);
    }

    /** Six-arg form: explicit discount curve, probabilities default to None. */
    public Gaussian1dSwaptionEngine(final Gaussian1dModel model, final int integrationPoints, final double stddevs,
            final boolean extrapolatePayoff, final boolean flatPayoffExtrapolation,
            final Handle< YieldTermStructure > discountCurve) {
        this(model, integrationPoints, stddevs, extrapolatePayoff, flatPayoffExtrapolation, discountCurve,
                Probabilities.None);
    }

    /** Full ctor — mirrors C++ shared_ptr constructor. */
    public Gaussian1dSwaptionEngine(final Gaussian1dModel model, final int integrationPoints, final double stddevs,
            final boolean extrapolatePayoff, final boolean flatPayoffExtrapolation,
            final Handle< YieldTermStructure > discountCurve, final Probabilities probabilities) {
        super();
        QL.require(model != null, "no model specified");
        this.model_ = model;
        this.integrationPoints_ = integrationPoints;
        this.stddevs_ = stddevs;
        this.extrapolatePayoff_ = extrapolatePayoff;
        this.flatPayoffExtrapolation_ = flatPayoffExtrapolation;
        this.discountCurve_ = (discountCurve != null) ? discountCurve : new Handle< YieldTermStructure >();
        this.probabilities_ = probabilities;

        this.model_.addObserver(this);
        if ( !this.discountCurve_.empty() ) {
            this.discountCurve_.addObserver(this);
        }
    }

    /**
     * Mirrors {@code std::upper_bound(begin, end, value) - begin}: returns the lowest index {@code i} such that
     * {@code dates[i] > value}. When the value is &gt;= all elements, returns {@code dates.size()}.
     */
    private static int upperBoundIndex(final List< Date > dates, final Date value) {
        // Linear scan — date lists are short (≤ ~50 elements typical).
        // Mirrors C++ semantics exactly: strict greater-than.
        int n = dates.size();
        for ( int i = 0; i < n; i++ ) {
            if ( dates.get(i).gt(value) ) {
                return i;
            }
        }
        return n;
    }

    //
    // ──────────────────────────────────────────────────────────────────────
    //   Accessors
    // ──────────────────────────────────────────────────────────────────────
    //

    /**
     * Builds a natural cubic spline interpolation with Lagrange end conditions, matching the C++ engine's
     * {@code CubicInterpolation(begin,end,begin,Spline,true,Lagrange,0.0, Lagrange,0.0)} construction.
     * <p>
     * The {@code monotonic=true} flag in the C++ ctor is preserved (Java {@link CubicInterpolation} treats it
     * identically — it only triggers monotonicity adjustment for monotone source data).
     */
    private static CubicInterpolation newCubicSpline(final Array x, final Array y) {
        return new CubicInterpolation(x, y, CubicInterpolation.DerivativeApprox.Spline, true,
                CubicInterpolation.BoundaryCondition.Lagrange, 0.0, CubicInterpolation.BoundaryCondition.Lagrange, 0.0);
    }

    /** Convenience wrapper: constructs an Array view over a primitive double[]. */
    private static Array npv1Array(final double[] data, final int size) {
        final Array a = new Array(size);
        for ( int i = 0; i < size; i++ ) {
            a.set(i, data[i]);
        }
        return a;
    }

    /** Snapshots an Array into a primitive double[] for fast indexing. */
    private static double[] arrayToDoubles(final Array a) {
        final int n = a.size();
        final double[] r = new double[n];
        for ( int i = 0; i < n; i++ ) {
            r[i] = a.get(i);
        }
        return r;
    }

    /**
     * Backwards-compat factory wrapper kept for an eventual probabilities-mode port (currently unused outside future
     * {@link Probabilities} branches).
     */
    @SuppressWarnings( "unused" )
    private static List< double[] > emptyProbabilityVectors() {
        return new ArrayList<>();
    }

    public Gaussian1dModel model() {
        return model_;
    }

    public int integrationPoints() {
        return integrationPoints_;
    }

    public double stddevs() {
        return stddevs_;
    }

    //
    // ──────────────────────────────────────────────────────────────────────
    //   Engine: calculate()
    // ──────────────────────────────────────────────────────────────────────
    //

    public boolean extrapolatePayoff() {
        return extrapolatePayoff_;
    }

    //
    // ──────────────────────────────────────────────────────────────────────
    //   Helpers
    // ──────────────────────────────────────────────────────────────────────
    //

    public boolean flatPayoffExtrapolation() {
        return flatPayoffExtrapolation_;
    }

    public Handle< YieldTermStructure > discountCurve() {
        return discountCurve_;
    }

    public Probabilities probabilities() {
        return probabilities_;
    }

    @Override
    public void calculate() /* @ReadOnly */ {
        final Swaption.ArgumentsImpl args = (Swaption.ArgumentsImpl) arguments_;
        final Swaption.ResultsImpl results = (Swaption.ResultsImpl) results_;

        // C++ QL_REQUIREs (gaussian1dswaptionengine.cpp lines 28-33).
        QL.require(args.settlementMethod != Settlement.Method.ParYieldCurve,
                "cash settled (ParYieldCurve) swaptions not priced with " + "Gaussian1dSwaptionEngine");

        final VanillaSwap swap = args.swap;
        final double nominal = swap.nominal();
        QL.require(!Double.isNaN(nominal) && nominal != Constants.NULL_REAL,
                "non-constant nominals are not supported yet");

        if ( probabilities_ != Probabilities.None ) {
            throw new UnsupportedOperationException(
                    "Gaussian1dSwaptionEngine: Probabilities." + probabilities_ + " mode is not yet ported "
                            + "(Phase 2j follow-up; only None is implemented)");
        }

        final Date settlement = model_.termStructure().currentLink().referenceDate();

        // ── Expired? ──
        final Exercise exercise = args.exercise;
        final List< Date > exerciseDates = exercise.dates();
        if ( exerciseDates.get(exerciseDates.size() - 1).le(settlement) ) {
            results.value = 0.0;
            return;
        }

        // ── Index bookkeeping ──
        // C++: idx = exerciseDates.size() - 1, walked downward.
        int idx = exerciseDates.size() - 1;
        // C++: minIdxAlive = upper_bound(exerciseDates, settlement) - begin().
        // I.e. the lowest index whose exercise date is strictly after settlement.
        int minIdxAlive = upperBoundIndex(exerciseDates, settlement);

        final Option.Type type = (swap.type() == VanillaSwap.Type.Payer) ? Option.Type.Call : Option.Type.Put;

        final Schedule fixedSchedule = swap.fixedSchedule();
        final Schedule floatSchedule = swap.floatingSchedule();
        final List< Date > fixedDates = fixedSchedule.dates();
        final List< Date > floatDates = floatSchedule.dates();

        // Pre-extract leg cashflows once so we don't pay per-iteration cast
        // costs. Sized once: same as fixed/float cashflow counts (C++ uses
        // arguments_.fixedCoupons / floatingCoupons / floatingAccrualTimes /
        // floatingSpreads — Java pulls equivalents from the legs).
        final Leg fixedLeg = swap.fixedLeg();
        final Leg floatLeg = swap.floatingLeg();
        final int nFixed = fixedLeg.size();
        final int nFloat = floatLeg.size();
        final double[] fixedAmounts = new double[nFixed];
        final Date[] fixedPayDates = new Date[nFixed];
        for ( int i = 0; i < nFixed; i++ ) {
            final FixedRateCoupon c = (FixedRateCoupon) fixedLeg.get(i);
            fixedAmounts[i] = c.amount();
            fixedPayDates[i] = c.date();
        }
        final double[] floatAccrual = new double[nFloat];
        final double[] floatSpreads = new double[nFloat];
        final Date[] floatFixingDates = new Date[nFloat];
        final Date[] floatPayDates = new Date[nFloat];
        IborIndex iborIdx = null;
        for ( int i = 0; i < nFloat; i++ ) {
            final FloatingRateCoupon c = (FloatingRateCoupon) floatLeg.get(i);
            floatAccrual[i] = c.accrualPeriod();
            floatSpreads[i] = c.spread();
            floatFixingDates[i] = c.fixingDate();
            floatPayDates[i] = c.date();
            if ( iborIdx == null ) {
                iborIdx = (IborIndex) c.index();
            }
        }
        // Fall back to swap.iborIndex() if the leg yielded nothing useful.
        if ( iborIdx == null ) {
            iborIdx = swap.iborIndex();
        }

        // ── State-grid arrays ──
        final int gridSize = 2 * integrationPoints_ + 1;
        final double[] npv0 = new double[gridSize];
        final double[] npv1 = new double[gridSize];
        final Array z = model_.yGrid(stddevs_, integrationPoints_);
        final double[] zArr = arrayToDoubles(z);
        final double[] p = new double[gridSize];

        // C++ uses Null<Real>() as a sentinel for "no expiry1Time set yet".
        // We use NaN — equivalently checked via Double.isNaN — because
        // Constants.NULL_REAL == Double.MAX_VALUE which is a valid time.
        final double SENTINEL = Double.NaN;
        double expiry1Time = SENTINEL;
        Date expiry0;
        double expiry0Time;

        do {
            if ( idx == minIdxAlive - 1 ) {
                expiry0 = settlement;
            } else {
                expiry0 = exerciseDates.get(idx);
            }
            expiry0Time = Math.max(model_.termStructure().currentLink().timeFromReference(expiry0), 0.0);

            // upper_bound(fixedDates / floatDates, expiry0 - 1)
            final Date justBefore = expiry0.add(-1);
            final int j1 = upperBoundIndex(fixedDates, justBefore);
            final int k1 = upperBoundIndex(floatDates, justBefore);

            final boolean afterSettlement = expiry0.gt(settlement);
            final long kMax = afterSettlement ? (long) gridSize : 1L;

            for ( long kL = 0; kL < kMax; kL++ ) {
                final int k = (int) kL;
                double price = 0.0;

                // Continuation-value convolution from the previously-computed
                // npv1 grid (only valid once we have an expiry1Time).
                if ( !Double.isNaN(expiry1Time) ) {
                    final Array yg = model_.yGrid(stddevs_, integrationPoints_, expiry1Time, expiry0Time,
                            afterSettlement ? zArr[k] : 0.0);
                    final CubicInterpolation payoff0 = newCubicSpline(z, npv1Array(npv1, gridSize));
                    payoff0.enableExtrapolation();
                    for ( int i = 0; i < yg.size(); i++ ) {
                        p[i] = payoff0.op(yg.get(i), true);
                    }
                    final CubicInterpolation payoff1 = newCubicSpline(z, new Array(p));
                    payoff1.enableExtrapolation();
                    final Array aCoef = payoff1.aCoefficients();
                    final Array bCoef = payoff1.bCoefficients();
                    final Array cCoef = payoff1.cCoefficients();
                    for ( int i = 0; i < gridSize - 1; i++ ) {
                        price += Gaussian1dModel.gaussianShiftedPolynomialIntegral(0.0, cCoef.get(i), bCoef.get(i),
                                aCoef.get(i), p[i], zArr[i], zArr[i], zArr[i + 1]);
                    }
                    if ( extrapolatePayoff_ ) {
                        if ( flatPayoffExtrapolation_ ) {
                            price += Gaussian1dModel.gaussianShiftedPolynomialIntegral(0.0, 0.0, 0.0, 0.0,
                                    p[gridSize - 2], zArr[gridSize - 2], zArr[gridSize - 1], 100.0);
                            price += Gaussian1dModel.gaussianShiftedPolynomialIntegral(0.0, 0.0, 0.0, 0.0, p[0],
                                    zArr[0], -100.0, zArr[0]);
                        } else {
                            if ( type == Option.Type.Call ) {
                                price += Gaussian1dModel.gaussianShiftedPolynomialIntegral(0.0, cCoef.get(gridSize - 2),
                                        bCoef.get(gridSize - 2), aCoef.get(gridSize - 2), p[gridSize - 2],
                                        zArr[gridSize - 2], zArr[gridSize - 1], 100.0);
                            }
                            if ( type == Option.Type.Put ) {
                                price += Gaussian1dModel.gaussianShiftedPolynomialIntegral(0.0, cCoef.get(0),
                                        bCoef.get(0), aCoef.get(0), p[0], zArr[0], -100.0, zArr[0]);
                            }
                        }
                    }
                }

                npv0[k] = price;

                if ( afterSettlement ) {
                    // Floating-leg NPV at state z[k].
                    double floatingLegNpv = 0.0;
                    for ( int l = k1; l < nFloat; l++ ) {
                        final double fwd = model_.forwardRate(floatFixingDates[l], expiry0, zArr[k], iborIdx);
                        floatingLegNpv +=
                                nominal * floatAccrual[l] * (floatSpreads[l] + fwd) * model_.zerobond(floatPayDates[l],
                                        expiry0, zArr[k], discountCurve_);
                    }
                    // Fixed-leg NPV at state z[k].
                    double fixedLegNpv = 0.0;
                    for ( int l = j1; l < nFixed; l++ ) {
                        fixedLegNpv +=
                                fixedAmounts[l] * model_.zerobond(fixedPayDates[l], expiry0, zArr[k], discountCurve_);
                    }
                    final double exerciseValue =
                            (type == Option.Type.Call ? 1.0 : -1.0) * (floatingLegNpv - fixedLegNpv) / model_.numeraire(
                                    expiry0Time, zArr[k], discountCurve_);

                    npv0[k] = Math.max(npv0[k], exerciseValue);
                }
            }

            // Swap roles: npv1 becomes "yesterday's" continuation grid for the
            // next iteration. C++ uses npv1.swap(npv0); — we copy npv0 into
            // npv1 and zero npv0 for the next pass.
            System.arraycopy(npv0, 0, npv1, 0, gridSize);
            // npv0 will be overwritten next iteration — no zero needed for
            // semantics, but reset to keep state predictable for k=0 branch.

            expiry1Time = expiry0Time;

        } while ( --idx >= minIdxAlive - 1 );

        results.value = npv1[0] * model_.numeraire(0.0, 0.0, discountCurve_);
    }

    /**
     * Probability-table mode (mirrors C++ {@code enum Probabilities}). Only {@link #None} is implemented in this Java
     * port — see class-level deviation note.
     */
    public enum Probabilities {
        /** No probability table emitted (default). */
        None,
        /** "Naive" exercise indicator probabilities. */
        Naive,
        /** "Digital" exercise indicator probabilities. */
        Digital
    }
}
