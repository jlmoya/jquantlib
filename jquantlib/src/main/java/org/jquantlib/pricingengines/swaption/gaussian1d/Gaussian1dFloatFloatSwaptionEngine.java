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
 Copyright (C) 2013, 2015 Peter Caspers

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
import org.jquantlib.exercise.Exercise;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.indexes.InterestRateIndex;
import org.jquantlib.indexes.SwapIndex;
import org.jquantlib.instruments.*;
import org.jquantlib.math.Constants;
import org.jquantlib.math.interpolations.CubicInterpolation;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.transcendental.JQuantMath;
import org.jquantlib.model.shortrate.onefactormodels.gaussian1d.Gaussian1dModel;
import org.jquantlib.pricingengines.swaption.BasketGeneratingEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;

import java.util.*;

/**
 * One-factor Gaussian1d-model pricing engine for {@link FloatFloatSwaption}.
 *
 * <p>Java port of C++ QuantLib v1.42.1
 * {@code ql/pricingengines/swaption/gaussian1dfloatfloatswaptionengine.{hpp,cpp}} (commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}). Phase 2j.5 Track B.3.
 *
 * <p>Backward-induction integration on a Gaussian1d state grid that tracks both
 * the option value and the underlying swap value simultaneously. Event dates include the union of exercise dates and
 * per-leg fixing dates; coupon estimations and capped/floored payoffs are computed at each event using
 * {@link Gaussian1dModel#forwardRate} (Ibor) and {@link Gaussian1dModel#swapRate} (CMS). The continuation value is
 * interpolated with a natural cubic spline (Lagrange end conditions) and convolved through the Gaussian transition
 * density via {@link Gaussian1dModel#gaussianShiftedPolynomialIntegral}. The exercise rule compares the running
 * underlying NPV to the option NPV at each grid node.
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 * <li>The C++ engine extends {@code BasketGeneratingEngine}. In Java that
 *     class is not yet ported. The four BasketGeneratingEngine virtuals
 *     ({@code underlyingNpv}, {@code underlyingType}, {@code underlyingLastDate},
 *     {@code initialGuess}) are implemented as private helpers but are not
 *     wired to a public interface.
 * <li>{@code SwapSpreadIndex} (CMS-spread index) is not present in this
 *     codebase; FloatFloatSwap restricts indices to IborIndex and SwapIndex,
 *     so the engine only handles those two cases. Throws
 *     {@code UnsupportedOperationException} if a CMS-spread index is somehow
 *     encountered.
 * <li>{@code RebatedExercise} is not yet ported; rebate is treated as zero.
 * <li>The probability-distribution branches ({@code Naive}, {@code Digital})
 *     are not implemented — same precedent as
 *     {@link Gaussian1dNonstandardSwaptionEngine}. Requesting any mode other
 *     than {@link Probabilities#None} throws {@code UnsupportedOperationException}.
 * <li>OAS spread handling is fully ported (uses {@link JQuantMath#exp}).
 * <li>OpenMP parallelism is omitted.
 * </ul>
 *
 * @see Gaussian1dModel
 * @see FloatFloatSwaption
 * @see Gaussian1dNonstandardSwaptionEngine
 */
public class Gaussian1dFloatFloatSwaptionEngine extends FloatFloatSwaption.EngineImpl {

    // Suppress unused-import warning in some toolchains
    @SuppressWarnings( "unused" )
    private static final double NULL_REAL = Constants.NULL_REAL;

    // ── fields ────────────────────────────────────────────────────────────────
    @SuppressWarnings( "unused" )
    private static final List< ? > UNUSED_COLLECTIONS = Collections.emptyList();
    private final Gaussian1dModel model_;
    private final int integrationPoints_;
    private final double stddevs_;
    private final boolean extrapolatePayoff_;
    private final boolean flatPayoffExtrapolation_;
    private final Handle< Quote > oas_;
    private final Handle< YieldTermStructure > discountCurve_;
    private final boolean includeTodaysExercise_;

    // ── constructors ──────────────────────────────────────────────────────────
    private final Probabilities probabilities_;

    /**
     * Defaults: 64 pts, 7.0 stddevs, extrapolate=true, flatExtrapolation=false, empty OAS, empty discountCurve,
     * includeTodaysExercise=false, Probabilities.None.
     */
    public Gaussian1dFloatFloatSwaptionEngine(final Gaussian1dModel model) {
        this(model, 64, 7.0, true, false, new Handle< Quote >(), new Handle< YieldTermStructure >(), false,
                Probabilities.None);
    }

    /** Common ctor: integration pts + stddevs only. */
    public Gaussian1dFloatFloatSwaptionEngine(final Gaussian1dModel model, final int integrationPoints,
            final double stddevs) {
        this(model, integrationPoints, stddevs, true, false, new Handle< Quote >(), new Handle< YieldTermStructure >(),
                false, Probabilities.None);
    }

    /** Five-arg form: extrapolation flags, no OAS / discountCurve. */
    public Gaussian1dFloatFloatSwaptionEngine(final Gaussian1dModel model, final int integrationPoints,
            final double stddevs, final boolean extrapolatePayoff, final boolean flatPayoffExtrapolation) {
        this(model, integrationPoints, stddevs, extrapolatePayoff, flatPayoffExtrapolation, new Handle< Quote >(),
                new Handle< YieldTermStructure >(), false, Probabilities.None);
    }

    // ── accessors ─────────────────────────────────────────────────────────────

    /** Full constructor. Mirrors both C++ shared_ptr and Handle ctors. */
    public Gaussian1dFloatFloatSwaptionEngine(final Gaussian1dModel model, final int integrationPoints,
            final double stddevs, final boolean extrapolatePayoff, final boolean flatPayoffExtrapolation,
            final Handle< Quote > oas, final Handle< YieldTermStructure > discountCurve,
            final boolean includeTodaysExercise, final Probabilities probabilities) {
        super();
        QL.require(model != null, "no model specified");
        this.model_ = model;
        this.integrationPoints_ = integrationPoints;
        this.stddevs_ = stddevs;
        this.extrapolatePayoff_ = extrapolatePayoff;
        this.flatPayoffExtrapolation_ = flatPayoffExtrapolation;
        this.oas_ = (oas != null) ? oas : new Handle< Quote >();
        this.discountCurve_ = (discountCurve != null) ? discountCurve : new Handle< YieldTermStructure >();
        this.includeTodaysExercise_ = includeTodaysExercise;
        this.probabilities_ = probabilities;

        this.model_.addObserver(this);
        if ( !this.oas_.empty() ) {
            this.oas_.addObserver(this);
        }
        if ( !this.discountCurve_.empty() ) {
            this.discountCurve_.addObserver(this);
        }
    }

    /**
     * Natural cubic spline with Lagrange end conditions — same as the rest of the Gaussian1d engine family.
     */
    private static CubicInterpolation newCubicSpline(final Array x, final Array y) {
        return new CubicInterpolation(x, y, CubicInterpolation.DerivativeApprox.Spline, true,
                CubicInterpolation.BoundaryCondition.Lagrange, 0.0, CubicInterpolation.BoundaryCondition.Lagrange, 0.0);
    }

    private static double[] arrayToDoubles(final Array a) {
        final int n = a.size();
        final double[] r = new double[n];
        for ( int i = 0; i < n; i++ )
            r[i] = a.get(i);
        return r;
    }

    private static Array doublesArray(final double[] data, final int size) {
        final Array a = new Array(size);
        for ( int i = 0; i < size; i++ )
            a.set(i, data[i]);
        return a;
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

    public boolean extrapolatePayoff() {
        return extrapolatePayoff_;
    }

    public boolean flatPayoffExtrapolation() {
        return flatPayoffExtrapolation_;
    }

    // ── calculate() ───────────────────────────────────────────────────────────

    public Handle< Quote > oas() {
        return oas_;
    }

    // ── core npvs() — returns (optionNpv, underlyingNpv) ──────────────────────

    public Handle< YieldTermStructure > discountCurve() {
        return discountCurve_.empty() ? model_.termStructure() : discountCurve_;
    }

    public boolean includeTodaysExercise() {
        return includeTodaysExercise_;
    }

    // ── calibrationBasket() ───────────────────────────────────────────────────

    public Probabilities probabilities() {
        return probabilities_;
    }

    // ── BasketGeneratingEngine hooks ──────────────────────────────────────────
    // Promoted to package-private so the anonymous BGE subclass can access them.

    @Override
    public void calculate() /* @ReadOnly */ {
        final FloatFloatSwaption.ArgumentsImpl args = (FloatFloatSwaption.ArgumentsImpl) arguments_;
        final Instrument.ResultsImpl results = (Instrument.ResultsImpl) results_;

        QL.require(args.settlementMethod != Settlement.Method.ParYieldCurve,
                "cash settled (ParYieldCurve) swaptions not priced with " + "Gaussian1dFloatFloatSwaptionEngine");

        if ( probabilities_ != Probabilities.None ) {
            throw new UnsupportedOperationException(
                    "Gaussian1dFloatFloatSwaptionEngine: Probabilities." + probabilities_ + " mode is not yet ported "
                            + "(only None is implemented)");
        }

        final Date settlement = model_.termStructure().currentLink().referenceDate();

        final List< Date > exerciseDates = args.exercise.dates();
        if ( exerciseDates.get(exerciseDates.size() - 1).le(settlement) ) {
            results.value = 0.0;
            return;
        }

        final double[] res = npvs(settlement, 0.0, includeTodaysExercise_);

        results.value = res[0];
        results.additionalResults().put("underlyingValue", Double.valueOf(res[1]));
    }

    /**
     * Mirrors C++ {@code npvs(expiry, y, includeExerciseOnExpiry, considerProbabilities)}. Returns
     * {@code [optionNpv, underlyingNpv]}.
     *
     * <p>The probability path is omitted (deferred — see class deviation note).
     */
    private double[] npvs(final Date expiry, final double y, final boolean includeExerciseOnExpiry) {
        final FloatFloatSwaption.ArgumentsImpl args = (FloatFloatSwaption.ArgumentsImpl) arguments_;
        final Exercise exercise = args.exercise;

        // ── Build event-date list (union of exercise + leg1 + leg2 fixing) ───
        final List< Date > exDates = exercise.dates();
        final TreeSet< Date > eventSet = new TreeSet< Date >(new java.util.Comparator< Date >() {
            @Override
            public int compare(final Date a, final Date b) {
                return a.compareTo(b);
            }
        });
        eventSet.addAll(exDates);
        for ( final Date d : args.leg1FixingDates )
            eventSet.add(d);
        for ( final Date d : args.leg2FixingDates )
            eventSet.add(d);

        // Drop events <= (expiry - (includeExerciseOnExpiry ? 1 : 0))
        // i.e., upper_bound(expiry - 1) when including, upper_bound(expiry) when not.
        final Date threshold = expiry.add(includeExerciseOnExpiry ? -1 : 0);
        final List< Date > events = new ArrayList< Date >();
        for ( final Date d : eventSet ) {
            if ( d.gt(threshold) )
                events.add(d);
        }

        int idx = events.size() - 1;

        final Option.Type type = (args.type == VanillaSwap.Type.Payer) ? Option.Type.Call : Option.Type.Put;

        final int gridSize = 2 * integrationPoints_ + 1;
        final double[] npv0 = new double[gridSize];
        final double[] npv1 = new double[gridSize];
        final double[] npv0a = new double[gridSize];
        final double[] npv1a = new double[gridSize];
        final Array z = model_.yGrid(stddevs_, integrationPoints_);
        final double[] zArr = arrayToDoubles(z);
        final double[] p = new double[gridSize];
        final double[] pa = new double[gridSize];

        // ── Index-type discrimination (per-leg) ──────────────────────────────
        final InterestRateIndex idx1 = args.index1;
        final InterestRateIndex idx2 = args.index2;
        final IborIndex ibor1 = (idx1 instanceof IborIndex) ? (IborIndex) idx1 : null;
        final SwapIndex cms1 = (ibor1 == null && idx1 instanceof SwapIndex) ? (SwapIndex) idx1 : null;
        final IborIndex ibor2 = (idx2 instanceof IborIndex) ? (IborIndex) idx2 : null;
        final SwapIndex cms2 = (ibor2 == null && idx2 instanceof SwapIndex) ? (SwapIndex) idx2 : null;
        QL.require(ibor1 != null || cms1 != null,
                "index1 must be IborIndex or SwapIndex (SwapSpreadIndex not supported in JQuantLib)");
        QL.require(ibor2 != null || cms2 != null,
                "index2 must be IborIndex or SwapIndex (SwapSpreadIndex not supported in JQuantLib)");

        // Pre-build hash sets for membership tests (mirrors std::find usage)
        final Set< Date > exSet = new HashSet< Date >(exDates);
        final Set< Date > leg1FixSet = new HashSet< Date >(args.leg1FixingDates);
        final Set< Date > leg2FixSet = new HashSet< Date >(args.leg2FixingDates);

        Date expiry0;
        double event0Time;
        // Sentinel: NaN ≡ C++ Null<Real>() meaning "no event1Time yet"
        double event1Time = Double.NaN;

        // do { ... } while (--idx >= -1)
        do {
            boolean isEventDate = true;
            if ( idx == -1 ) {
                expiry0 = expiry;
                isEventDate = false;
            } else {
                expiry0 = events.get(idx);
                if ( expiry0.equals(expiry) ) {
                    idx = -1; // avoid double rollback
                }
            }

            final boolean isExercise = exSet.contains(expiry0);
            final boolean isLeg1Fixing = leg1FixSet.contains(expiry0);
            final boolean isLeg2Fixing = leg2FixSet.contains(expiry0);

            event0Time = Math.max(model_.termStructure().currentLink().timeFromReference(expiry0), 0.0);

            final boolean afterExpiry = expiry0.gt(expiry);
            final int kMax = afterExpiry ? gridSize : 1;

            for ( int k = 0; k < kMax; k++ ) {

                double price = 0.0;
                double pricea = 0.0;

                // ── Continuation-value rollback ────────────────────────────
                if ( !Double.isNaN(event1Time) ) {
                    final double zSpreadDf = oas_.empty()
                            ? 1.0
                            : JQuantMath.exp(-oas_.currentLink().value() * (event1Time - event0Time));

                    final Array yg = model_.yGrid(stddevs_, integrationPoints_, event1Time, event0Time,
                            afterExpiry ? zArr[k] : y);

                    // Spline through option NPV
                    final CubicInterpolation payoff0 = newCubicSpline(z, doublesArray(npv1, gridSize));
                    payoff0.enableExtrapolation();
                    // Spline through underlying NPV
                    final CubicInterpolation payoff0a = newCubicSpline(z, doublesArray(npv1a, gridSize));
                    payoff0a.enableExtrapolation();

                    for ( int i = 0; i < yg.size(); i++ ) {
                        p[i] = payoff0.op(yg.get(i), true);
                        pa[i] = payoff0a.op(yg.get(i), true);
                    }

                    final CubicInterpolation payoff1 = newCubicSpline(z, new Array(p));
                    payoff1.enableExtrapolation();
                    final CubicInterpolation payoff1a = newCubicSpline(z, new Array(pa));
                    payoff1a.enableExtrapolation();

                    final Array aCoef = payoff1.aCoefficients();
                    final Array bCoef = payoff1.bCoefficients();
                    final Array cCoef = payoff1.cCoefficients();
                    final Array aCoefA = payoff1a.aCoefficients();
                    final Array bCoefA = payoff1a.bCoefficients();
                    final Array cCoefA = payoff1a.cCoefficients();

                    for ( int i = 0; i < gridSize - 1; i++ ) {
                        price += Gaussian1dModel.gaussianShiftedPolynomialIntegral(0.0, cCoef.get(i), bCoef.get(i),
                                aCoef.get(i), p[i], zArr[i], zArr[i], zArr[i + 1]) * zSpreadDf;
                        pricea += Gaussian1dModel.gaussianShiftedPolynomialIntegral(0.0, cCoefA.get(i), bCoefA.get(i),
                                aCoefA.get(i), pa[i], zArr[i], zArr[i], zArr[i + 1]) * zSpreadDf;
                    }

                    if ( extrapolatePayoff_ ) {
                        if ( flatPayoffExtrapolation_ ) {
                            price += Gaussian1dModel.gaussianShiftedPolynomialIntegral(0.0, 0.0, 0.0, 0.0,
                                    p[gridSize - 2], zArr[gridSize - 2], zArr[gridSize - 1], 100.0) * zSpreadDf;
                            price +=
                                    Gaussian1dModel.gaussianShiftedPolynomialIntegral(0.0, 0.0, 0.0, 0.0, p[0], zArr[0],
                                            -100.0, zArr[0]) * zSpreadDf;
                            pricea += Gaussian1dModel.gaussianShiftedPolynomialIntegral(0.0, 0.0, 0.0, 0.0,
                                    pa[gridSize - 2], zArr[gridSize - 2], zArr[gridSize - 1], 100.0) * zSpreadDf;
                            pricea += Gaussian1dModel.gaussianShiftedPolynomialIntegral(0.0, 0.0, 0.0, 0.0, pa[0],
                                    zArr[0], -100.0, zArr[0]) * zSpreadDf;
                        } else {
                            if ( type == Option.Type.Call ) {
                                price += Gaussian1dModel.gaussianShiftedPolynomialIntegral(0.0, cCoef.get(gridSize - 2),
                                        bCoef.get(gridSize - 2), aCoef.get(gridSize - 2), p[gridSize - 2],
                                        zArr[gridSize - 2], zArr[gridSize - 1], 100.0) * zSpreadDf;
                                pricea +=
                                        Gaussian1dModel.gaussianShiftedPolynomialIntegral(0.0, cCoefA.get(gridSize - 2),
                                                bCoefA.get(gridSize - 2), aCoefA.get(gridSize - 2), pa[gridSize - 2],
                                                zArr[gridSize - 2], zArr[gridSize - 1], 100.0) * zSpreadDf;
                            }
                            if ( type == Option.Type.Put ) {
                                price += Gaussian1dModel.gaussianShiftedPolynomialIntegral(0.0, cCoef.get(0),
                                        bCoef.get(0), aCoef.get(0), p[0], zArr[0], -100.0, zArr[0]) * zSpreadDf;
                                pricea += Gaussian1dModel.gaussianShiftedPolynomialIntegral(0.0, cCoefA.get(0),
                                        bCoefA.get(0), aCoefA.get(0), pa[0], zArr[0], -100.0, zArr[0]) * zSpreadDf;
                            }
                        }
                    }
                }

                npv0[k] = price;
                npv0a[k] = pricea;

                // ── Event date processing ──────────────────────────────────
                if ( isEventDate ) {
                    final double zk = afterExpiry ? zArr[k] : y;

                    if ( isLeg1Fixing ) {
                        applyLegFixing(args, true, expiry0, event0Time, zk, ibor1, cms1, k, npv0a, /*signFromLeg=*/-1);
                    }

                    if ( isLeg2Fixing ) {
                        applyLegFixing(args, false, expiry0, event0Time, zk, ibor2, cms2, k, npv0a, /*signFromLeg=*/+1);
                    }

                    if ( isExercise ) {
                        // Rebate not yet ported — treat as zero (see class deviation note)
                        final double rebate = 0.0;
                        final double zSpreadDf = 1.0;
                        // exerciseValue = (Call?+1:-1) * npv0a[k] + rebate * df / numeraire
                        final double exerciseValue = (type == Option.Type.Call ? 1.0 : -1.0) * npv0a[k]
                                + rebate * zSpreadDf / model_.numeraire(event0Time, zk, discountCurve_);

                        npv0[k] = Math.max(npv0[k], exerciseValue);
                    }
                }
            }

            // ── Swap arrays (npv0 → npv1) ──────────────────────────────────
            System.arraycopy(npv0, 0, npv1, 0, gridSize);
            System.arraycopy(npv0a, 0, npv1a, 0, gridSize);

            event1Time = event0Time;

        } while ( --idx >= -1 );

        // Final scale by initial numeraire
        final double num = model_.numeraire(event1Time, y, discountCurve_);
        final double sign = (type == Option.Type.Call ? 1.0 : -1.0);
        return new double[] { npv1[0] * num, npv1a[0] * num * sign };
    }

    /**
     * Apply a leg-fixing event at {@code expiry0} (state {@code zk}, grid index {@code k}) to {@code npv0a} (the
     * underlying NPV array).
     *
     * @param args        FloatFloatSwaption arguments
     * @param isLeg1      true if this is a leg-1 fixing (subtract: pays leg1), false if leg-2 (add: receives leg2 in
     *                    C++ convention)
     * @param expiry0     event date
     * @param event0Time  year-fraction from reference to event date
     * @param zk          state value
     * @param ibor        IborIndex if leg uses Ibor (else null)
     * @param cms         SwapIndex if leg uses CMS (else null)
     * @param k           grid index
     * @param npv0a       underlying NPV array (modified in place)
     * @param signFromLeg −1 for leg1 (subtract), +1 for leg2 (add)
     */
    private void applyLegFixing(final FloatFloatSwaption.ArgumentsImpl args, final boolean isLeg1, final Date expiry0,
            final double event0Time, final double zk, final IborIndex ibor, final SwapIndex cms, final int k,
            final double[] npv0a, final double signFromLeg) {

        final List< Date > fixDates = isLeg1 ? args.leg1FixingDates : args.leg2FixingDates;
        final List< Date > payDates = isLeg1 ? args.leg1PayDates : args.leg2PayDates;
        final List< Double > spreads = isLeg1 ? args.leg1Spreads : args.leg2Spreads;
        final List< Double > gearings = isLeg1 ? args.leg1Gearings : args.leg2Gearings;
        final List< Double > coupons = isLeg1 ? args.leg1Coupons : args.leg2Coupons;
        final List< Double > capped = isLeg1 ? args.leg1CappedRates : args.leg2CappedRates;
        final List< Double > floored = isLeg1 ? args.leg1FlooredRates : args.leg2FlooredRates;
        final List< Double > accrTimes = isLeg1 ? args.leg1AccrualTimes : args.leg2AccrualTimes;
        final boolean[] isRedemp = isLeg1 ? args.leg1IsRedemptionFlow : args.leg2IsRedemptionFlow;
        final double[] nominal = isLeg1 ? args.nominal1 : args.nominal2;

        // Find index j such that fixDates[j] == expiry0 (first match)
        int j = 0;
        while ( j < fixDates.size() && !expiry0.equals(fixDates.get(j)) )
            j++;
        if ( j == fixDates.size() )
            return; // shouldn't happen if we got here

        // C++ uses zSpreadDf computed once from event0 → payDates[j] for the
        // first iteration; new pay dates inside the loop reuse it (mirroring
        // C++ which captures it before the inner do/while).
        final double zSpreadDf;
        if ( oas_.empty() ) {
            zSpreadDf = 1.0;
        } else {
            final double yf = model_.termStructure().currentLink().dayCounter().yearFraction(expiry0, payDates.get(j));
            zSpreadDf = JQuantMath.exp(-oas_.currentLink().value() * yf);
        }

        boolean done = false;
        do {
            final double amount;
            if ( isRedemp[j] ) {
                amount = coupons.get(j);
            } else {
                double estFixing = 0.0;
                if ( ibor != null ) {
                    estFixing = model_.forwardRate(fixDates.get(j), expiry0, zk, ibor);
                }
                if ( cms != null ) {
                    estFixing = model_.swapRate(fixDates.get(j), cms.tenor(), expiry0, zk, cms);
                }

                double rate = spreads.get(j) + gearings.get(j) * estFixing;
                final double cap = capped.get(j);
                final double floor = floored.get(j);
                if ( cap != FloatFloatSwap.NULL_REAL )
                    rate = Math.min(cap, rate);
                if ( floor != FloatFloatSwap.NULL_REAL )
                    rate = Math.max(floor, rate);

                amount = rate * nominal[j] * accrTimes.get(j);
            }

            // signFromLeg = -1 for leg1 (subtract), +1 for leg2 (add)
            npv0a[k] += signFromLeg * amount * model_.zerobond(payDates.get(j), expiry0, zk, discountCurve_)
                    / model_.numeraire(event0Time, zk, discountCurve_) * zSpreadDf;

            if ( j < fixDates.size() - 1 ) {
                j++;
                done = !expiry0.equals(fixDates.get(j));
            } else {
                done = true;
            }
        } while ( !done );
    }

    /**
     * Generates a calibration basket for a given exercise schedule.
     *
     * <p>Mirrors C++ {@code FloatFloatSwaption::calibrationBasket()} which
     * delegates to {@code BasketGeneratingEngine::calibrationBasket()}. Phase 2k Track B wiring.
     *
     * @param exercise           exercise schedule
     * @param standardSwapBase   standard swap index for basket construction
     * @param swaptionVolatility vol surface
     * @param basketType         Naive or MaturityStrikeByDeltaGamma
     * @return list of calibration helper instruments
     */
    public java.util.List< org.jquantlib.model.BlackCalibrationHelper > calibrationBasket(
            final org.jquantlib.exercise.Exercise exercise, final org.jquantlib.indexes.SwapIndex standardSwapBase,
            final org.jquantlib.termstructures.SwaptionVolatilityStructure swaptionVolatility,
            final BasketGeneratingEngine.CalibrationBasketType basketType) {

        final Gaussian1dFloatFloatSwaptionEngine self = this;
        // discountCurve() accessor resolves to model ts if empty
        final Handle< YieldTermStructure > discTs = discountCurve_;
        final BasketGeneratingEngine bge = new BasketGeneratingEngine(model_, oas_, discTs) {
            @Override
            protected double underlyingNpv(final Date expiry, final double y) {
                return self.underlyingNpv(expiry, y);
            }

            @Override
            protected VanillaSwap.Type underlyingType() {
                return self.underlyingType();
            }

            @Override
            protected Date underlyingLastDate() {
                return self.underlyingLastDate();
            }

            @Override
            protected double[] initialGuess(final Date expiry) {
                return self.initialGuess(expiry);
            }
        };
        return bge.calibrationBasket(exercise, standardSwapBase, swaptionVolatility, basketType);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Mirrors C++ {@code underlyingNpv(expiry, y)}. */
    double underlyingNpv(final Date expiry, final double y) {
        return npvs(expiry, y, true)[1];
    }

    /** Mirrors C++ {@code underlyingType()}. */
    VanillaSwap.Type underlyingType() {
        return ((FloatFloatSwaption.ArgumentsImpl) arguments_).swap.type();
    }

    /** Mirrors C++ {@code underlyingLastDate()} — last leg pay date. */
    Date underlyingLastDate() {
        final FloatFloatSwaption.ArgumentsImpl args = (FloatFloatSwaption.ArgumentsImpl) arguments_;
        final Date l1 = args.leg1PayDates.get(args.leg1PayDates.size() - 1);
        final Date l2 = args.leg2PayDates.get(args.leg2PayDates.size() - 1);
        return l2.ge(l1) ? l2 : l1;
    }

    /**
     * Mirrors C++ {@code initialGuess(expiry)}: returns (nominalAvg1, weightedMaturity1, 0.03).
     */
    double[] initialGuess(final Date expiry) {
        final FloatFloatSwaption.ArgumentsImpl args = (FloatFloatSwaption.ArgumentsImpl) arguments_;
        final List< Date > resetDates = args.leg1ResetDates;
        final Date justBefore = expiry.add(-1);
        int idx1 = 0;
        while ( idx1 < resetDates.size() && !resetDates.get(idx1).gt(justBefore) )
            idx1++;

        double nominalSum1 = 0.0;
        for ( int i = idx1; i < resetDates.size(); i++ ) {
            nominalSum1 += args.nominal1[i];
        }
        final int countLeft = resetDates.size() - idx1;
        QL.require(countLeft > 0, "no leg1 resets after expiry");
        final double nominalAvg1 = nominalSum1 / countLeft;

        double weightedMaturity1 = 0.0;
        for ( int i = idx1; i < resetDates.size(); i++ ) {
            weightedMaturity1 += args.leg1AccrualTimes.get(i) * args.nominal1[i];
        }
        weightedMaturity1 /= nominalAvg1;

        return new double[] { nominalAvg1, weightedMaturity1, 0.03 };
    }
    /** Probability-table mode (mirrors C++ {@code enum Probabilities}). */
    public enum Probabilities {
        /** No probability table emitted (default). */
        None,
        /** "Naive" exercise indicator probabilities. */
        Naive,
        /** "Digital" exercise indicator probabilities. */
        Digital
    }
}
