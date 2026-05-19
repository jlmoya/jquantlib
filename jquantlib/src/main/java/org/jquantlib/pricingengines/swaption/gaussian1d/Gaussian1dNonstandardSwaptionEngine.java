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
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.*;
import org.jquantlib.math.interpolations.CubicInterpolation;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.transcendental.JQuantMath;
import org.jquantlib.model.shortrate.onefactormodels.gaussian1d.Gaussian1dModel;
import org.jquantlib.pricingengines.swaption.BasketGeneratingEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;

import java.util.List;

/**
 * One-factor Gaussian1d-model pricing engine for {@link NonstandardSwaption}.
 *
 * <p>Java port of C++ QuantLib v1.42.1
 * {@code ql/pricingengines/swaption/gaussian1dnonstandardswaptionengine.{hpp,cpp}} (commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}). Phase 2j.5 Track A.3.
 *
 * <p>Backward-induction integration on a Gaussian1d state grid: at each
 * exercise date the conditional continuation value is interpolated with a natural cubic spline (Lagrange end
 * conditions) and then convolved with the conditional Gaussian transition density via
 * {@link Gaussian1dModel#gaussianShiftedPolynomialIntegral}. The exercise value at each grid point is the
 * (sign-adjusted) net NPV of the non-standard swap divided by the model numeraire, evaluated at that state.
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 * <li>The C++ engine also extends {@code BasketGeneratingEngine}. In Java that
 *     class has not yet been ported (Phase 2j.5 deferred). The four virtual
 *     methods required by {@code BasketGeneratingEngine}
 *     ({@code underlyingNpv}, {@code underlyingType}, {@code underlyingLastDate},
 *     {@code initialGuess}) are implemented as private helpers for completeness
 *     but are not wired to any public interface — the basket-generation path is
 *     not exercised in Phase 2j.5 scope.
 * <li>{@code RebatedExercise} is not yet ported; the Java engine performs the
 *     same null-check the C++ does and omits the rebate when unavailable
 *     (i.e., rebate = 0). No in-scope caller in Phase 2j.5 uses rebated exercise.
 * <li>OAS spread handling ({@code oas_}) is fully ported. Since the probe uses
 *     an empty {@code Handle<Quote>} (no OAS), the {@code JQuantMath.exp()} call
 *     only triggers when a non-empty OAS handle is supplied.
 * <li>The probability-distribution branches ({@code Naive}, {@code Digital}) are
 *     not implemented — same precedent as {@link Gaussian1dSwaptionEngine}.
 *     Requesting any mode other than {@link Probabilities#None} throws
 *     {@code UnsupportedOperationException}.
 * <li>OpenMP parallelism is omitted (no analogue in the JQuantLib engine layer).
 * </ul>
 *
 * @see Gaussian1dModel
 * @see NonstandardSwaption
 * @see Gaussian1dSwaptionEngine
 */
public class Gaussian1dNonstandardSwaptionEngine extends NonstandardSwaption.EngineImpl {

    private final Gaussian1dModel model_;

    // ── fields ────────────────────────────────────────────────────────────────
    private final int integrationPoints_;
    private final double stddevs_;
    private final boolean extrapolatePayoff_;
    private final boolean flatPayoffExtrapolation_;
    private final Handle< YieldTermStructure > discountCurve_;
    private final Handle< Quote > oas_;
    private final Probabilities probabilities_;
    /**
     * Defaults: 64 pts, 7.0 stddevs, extrapolate=true, flatExtrapolation=false, empty OAS, empty discountCurve,
     * Probabilities.None.
     */
    public Gaussian1dNonstandardSwaptionEngine(final Gaussian1dModel model) {
        this(model, 64, 7.0, true, false, new Handle< Quote >(), new Handle< YieldTermStructure >(),
                Probabilities.None);
    }

    // ── constructors ──────────────────────────────────────────────────────────

    /** Common ctor: integration pts + stddevs only. */
    public Gaussian1dNonstandardSwaptionEngine(final Gaussian1dModel model, final int integrationPoints,
            final double stddevs) {
        this(model, integrationPoints, stddevs, true, false, new Handle< Quote >(), new Handle< YieldTermStructure >(),
                Probabilities.None);
    }

    /** Five-arg form: extrapolation flags, no OAS / discountCurve. */
    public Gaussian1dNonstandardSwaptionEngine(final Gaussian1dModel model, final int integrationPoints,
            final double stddevs, final boolean extrapolatePayoff, final boolean flatPayoffExtrapolation) {
        this(model, integrationPoints, stddevs, extrapolatePayoff, flatPayoffExtrapolation, new Handle< Quote >(),
                new Handle< YieldTermStructure >(), Probabilities.None);
    }

    /** Full constructor. Mirrors both C++ shared_ptr and Handle ctors. */
    public Gaussian1dNonstandardSwaptionEngine(final Gaussian1dModel model, final int integrationPoints,
            final double stddevs, final boolean extrapolatePayoff, final boolean flatPayoffExtrapolation,
            final Handle< Quote > oas, final Handle< YieldTermStructure > discountCurve,
            final Probabilities probabilities) {
        super();
        QL.require(model != null, "no model specified");
        this.model_ = model;
        this.integrationPoints_ = integrationPoints;
        this.stddevs_ = stddevs;
        this.extrapolatePayoff_ = extrapolatePayoff;
        this.flatPayoffExtrapolation_ = flatPayoffExtrapolation;
        this.oas_ = (oas != null) ? oas : new Handle< Quote >();
        this.discountCurve_ = (discountCurve != null) ? discountCurve : new Handle< YieldTermStructure >();
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
     * Mirrors {@code std::upper_bound(begin, end, value) - begin}: returns lowest index i such that dates.get(i) &gt;
     * value.
     */
    private static int upperBoundIndex(final List< Date > dates, final Date value) {
        final int n = dates.size();
        for ( int i = 0; i < n; i++ ) {
            if ( dates.get(i).gt(value) ) {
                return i;
            }
        }
        return n;
    }

    // ── accessors ─────────────────────────────────────────────────────────────

    /**
     * Natural cubic spline with Lagrange end conditions — same as used by {@link Gaussian1dSwaptionEngine}.
     */
    private static CubicInterpolation newCubicSpline(final Array x, final Array y) {
        return new CubicInterpolation(x, y, CubicInterpolation.DerivativeApprox.Spline, true,
                CubicInterpolation.BoundaryCondition.Lagrange, 0.0, CubicInterpolation.BoundaryCondition.Lagrange, 0.0);
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

    /** Wraps a primitive double[] into an Array (for CubicInterpolation). */
    private static Array doublesArray(final double[] data, final int size) {
        final Array a = new Array(size);
        for ( int i = 0; i < size; i++ ) {
            a.set(i, data[i]);
        }
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

    public Handle< YieldTermStructure > discountCurve() {
        return discountCurve_;
    }

    // ── calibrationBasket() ───────────────────────────────────────────────────

    public Handle< Quote > oas() {
        return oas_;
    }

    // ── BasketGeneratingEngine hooks ──────────────────────────────────────────
    // These implement the four virtual methods from C++ BasketGeneratingEngine.
    // Promoted to package-private (from private) so that the BGE adapter inner
    // class can access them (see calibrationBasket method below).

    public Probabilities probabilities() {
        return probabilities_;
    }

    @Override
    public void calculate() /* @ReadOnly */ {
        final NonstandardSwaption.ArgumentsImpl args = (NonstandardSwaption.ArgumentsImpl) arguments_;
        final Instrument.ResultsImpl results = (Instrument.ResultsImpl) results_;

        QL.require(args.settlementMethod != Settlement.Method.ParYieldCurve,
                "cash settled (ParYieldCurve) swaptions not priced with " + "Gaussian1dNonstandardSwaptionEngine");

        if ( probabilities_ != Probabilities.None ) {
            throw new UnsupportedOperationException(
                    "Gaussian1dNonstandardSwaptionEngine: Probabilities." + probabilities_ + " mode is not yet ported "
                            + "(only None is implemented)");
        }

        final Date settlement = model_.termStructure().currentLink().referenceDate();

        final Exercise exercise = args.exercise;
        final List< Date > exerciseDates = exercise.dates();

        // Expired?
        if ( exerciseDates.get(exerciseDates.size() - 1).le(settlement) ) {
            results.value = 0.0;
            return;
        }

        // C++ index bookkeeping
        int idx = exerciseDates.size() - 1;
        int minIdxAlive = upperBoundIndex(exerciseDates, settlement);

        // Option type: Payer swaption → Call, Receiver → Put
        // (C++: type = arguments_.type == Swap::Payer ? Option::Call : Option::Put)
        final Option.Type type = (args.type == VanillaSwap.Type.Payer) ? Option.Type.Call : Option.Type.Put;

        // Leg data from ArgumentsImpl (populated by NonstandardSwap.setupArguments)
        final List< Date > fixedResetDates = args.fixedResetDates;
        final List< Date > fixedPayDates = args.fixedPayDates;
        final List< Double > fixedCoupons = args.fixedCoupons;

        final List< Date > floatingResetDates = args.floatingResetDates;
        final List< Date > floatingPayDates = args.floatingPayDates;
        final List< Date > floatingFixingDates = args.floatingFixingDates;
        final List< Double > floatingAccrualTimes = args.floatingAccrualTimes;
        final List< Double > floatingSpreads = args.floatingSpreads;
        final List< Double > floatingGearings = args.floatingGearings;
        final List< Double > floatingCoupons = args.floatingCoupons;
        final boolean[] floatingIsRedemption = args.floatingIsRedemptionFlow;

        final NonstandardSwap swap = args.swap;

        // State grid
        final int gridSize = 2 * integrationPoints_ + 1;
        final double[] npv0 = new double[gridSize];
        final double[] npv1 = new double[gridSize];
        final Array z = model_.yGrid(stddevs_, integrationPoints_);
        final double[] zArr = arrayToDoubles(z);
        final double[] p = new double[gridSize];

        // Sentinel: NaN ≡ C++ Null<Real>() meaning "no expiry1Time yet"
        double expiry1Time = Double.NaN;
        Date expiry0;
        double expiry0Time;

        do {
            if ( idx == minIdxAlive - 1 ) {
                expiry0 = settlement;
            } else {
                expiry0 = exerciseDates.get(idx);
            }
            expiry0Time = Math.max(model_.termStructure().currentLink().timeFromReference(expiry0), 0.0);

            // upper_bound(fixedResetDates, expiry0 - 1)
            final Date justBefore = expiry0.add(-1);
            final int j1 = upperBoundIndex(fixedResetDates, justBefore);
            final int k1 = upperBoundIndex(floatingResetDates, justBefore);

            final boolean afterSettlement = expiry0.gt(settlement);
            final int kMax = afterSettlement ? gridSize : 1;

            for ( int k = 0; k < kMax; k++ ) {
                double price = 0.0;

                // ── Continuation value convolution ─────────────────────────
                if ( !Double.isNaN(expiry1Time) ) {
                    final double zSpreadDf = oas_.empty()
                            ? 1.0
                            : JQuantMath.exp(-oas_.currentLink().value() * (expiry1Time - expiry0Time));

                    final Array yg = model_.yGrid(stddevs_, integrationPoints_, expiry1Time, expiry0Time,
                            afterSettlement ? zArr[k] : 0.0);

                    final CubicInterpolation payoff0 = newCubicSpline(z, doublesArray(npv1, gridSize));
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
                                aCoef.get(i), p[i], zArr[i], zArr[i], zArr[i + 1]) * zSpreadDf;
                    }
                    if ( extrapolatePayoff_ ) {
                        if ( flatPayoffExtrapolation_ ) {
                            price += Gaussian1dModel.gaussianShiftedPolynomialIntegral(0.0, 0.0, 0.0, 0.0,
                                    p[gridSize - 2], zArr[gridSize - 2], zArr[gridSize - 1], 100.0) * zSpreadDf;
                            price +=
                                    Gaussian1dModel.gaussianShiftedPolynomialIntegral(0.0, 0.0, 0.0, 0.0, p[0], zArr[0],
                                            -100.0, zArr[0]) * zSpreadDf;
                        } else {
                            if ( type == Option.Type.Call ) {
                                price += Gaussian1dModel.gaussianShiftedPolynomialIntegral(0.0, cCoef.get(gridSize - 2),
                                        bCoef.get(gridSize - 2), aCoef.get(gridSize - 2), p[gridSize - 2],
                                        zArr[gridSize - 2], zArr[gridSize - 1], 100.0) * zSpreadDf;
                            }
                            if ( type == Option.Type.Put ) {
                                price += Gaussian1dModel.gaussianShiftedPolynomialIntegral(0.0, cCoef.get(0),
                                        bCoef.get(0), aCoef.get(0), p[0], zArr[0], -100.0, zArr[0]) * zSpreadDf;
                            }
                        }
                    }
                }

                npv0[k] = price;

                // ── Exercise value at state z[k] ───────────────────────────
                if ( afterSettlement ) {

                    // Floating leg NPV
                    double floatingLegNpv = 0.0;
                    for ( int l = k1; l < floatingCoupons.size(); l++ ) {
                        final double zSpreadDf;
                        if ( oas_.empty() ) {
                            zSpreadDf = 1.0;
                        } else {
                            final double yf = model_.termStructure().currentLink().dayCounter()
                                    .yearFraction(expiry0, floatingPayDates.get(l));
                            zSpreadDf = JQuantMath.exp(-oas_.currentLink().value() * yf);
                        }

                        final double amount;
                        if ( floatingIsRedemption[l] ) {
                            amount = floatingCoupons.get(l);
                        } else {
                            final double fwd = model_.forwardRate(floatingFixingDates.get(l), expiry0, zArr[k],
                                    swap.iborIndex());
                            amount =
                                    (floatingGearings.get(l) * fwd + floatingSpreads.get(l)) * floatingAccrualTimes.get(
                                            l)
                                            // C++ uses floatingNominal[l]; Java accesses it
                                            // via the args field directly
                                            * args.floatingNominal[l];
                        }

                        floatingLegNpv +=
                                amount * model_.zerobond(floatingPayDates.get(l), expiry0, zArr[k], discountCurve_)
                                        * zSpreadDf;
                    }

                    // Fixed leg NPV
                    double fixedLegNpv = 0.0;
                    for ( int l = j1; l < fixedCoupons.size(); l++ ) {
                        final double zSpreadDf;
                        if ( oas_.empty() ) {
                            zSpreadDf = 1.0;
                        } else {
                            final double yf = model_.termStructure().currentLink().dayCounter()
                                    .yearFraction(expiry0, fixedPayDates.get(l));
                            zSpreadDf = JQuantMath.exp(-oas_.currentLink().value() * yf);
                        }

                        fixedLegNpv += fixedCoupons.get(l) * model_.zerobond(fixedPayDates.get(l), expiry0, zArr[k],
                                discountCurve_) * zSpreadDf;
                    }

                    // Rebate (RebatedExercise not yet ported → rebate = 0)
                    final double rebate = 0.0;

                    final double exerciseValue =
                            ((type == Option.Type.Call ? 1.0 : -1.0) * (floatingLegNpv - fixedLegNpv) + rebate)
                                    / model_.numeraire(expiry0Time, zArr[k], discountCurve_);

                    npv0[k] = Math.max(npv0[k], exerciseValue);
                }
            }

            // Swap npv0 → npv1 (C++ uses std::vector::swap; Java: copy)
            System.arraycopy(npv0, 0, npv1, 0, gridSize);

            expiry1Time = expiry0Time;

        } while ( --idx >= minIdxAlive - 1 );

        results.value = npv1[0] * model_.numeraire(0.0, 0.0, discountCurve_);
    }

    /**
     * Generates a calibration basket for a given exercise schedule.
     *
     * <p>Mirrors C++ {@code NonstandardSwaption::calibrationBasket()} which
     * delegates to {@code BasketGeneratingEngine::calibrationBasket()}. The four BGE abstract hooks
     * ({@code underlyingNpv}, {@code underlyingType}, {@code underlyingLastDate}, {@code initialGuess}) are satisfied
     * by this engine's package-private helper methods. Phase 2k Track B wiring.
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

        // Build an anonymous BGE subclass that routes the 4 abstract hooks
        // back into this engine's package-private methods.
        final Gaussian1dNonstandardSwaptionEngine self = this;
        final BasketGeneratingEngine bge = new BasketGeneratingEngine(model_, oas_, discountCurve_) {
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

    /**
     * Mirrors C++ {@code underlyingNpv(expiry, y)}. Computes the NPV of the non-standard swap cashflows conditional on
     * {@code y} at the expiry date.
     */
    double underlyingNpv(final Date expiry, final double y) {
        final NonstandardSwaption.ArgumentsImpl args = (NonstandardSwaption.ArgumentsImpl) arguments_;

        final int fixedIdx = upperBoundIndex(args.fixedResetDates, expiry.add(-1));
        final int floatIdx = upperBoundIndex(args.floatingResetDates, expiry.add(-1));

        final double swapType = (args.type == VanillaSwap.Type.Payer) ? 1.0 : -1.0;

        double npv = 0.0;
        for ( int i = fixedIdx; i < args.fixedCoupons.size(); i++ ) {
            final double zSpreadDf = oas_.empty()
                    ? 1.0
                    : JQuantMath.exp(-oas_.currentLink().value() * model_.termStructure().currentLink().dayCounter()
                            .yearFraction(expiry, args.fixedPayDates.get(i)));
            npv -= args.fixedCoupons.get(i) * model_.zerobond(args.fixedPayDates.get(i), expiry, y, discountCurve_)
                    * zSpreadDf;
        }

        for ( int i = floatIdx; i < args.floatingCoupons.size(); i++ ) {
            final double amount;
            if ( !args.floatingIsRedemptionFlow[i] ) {
                final double fwd = model_.forwardRate(args.floatingFixingDates.get(i), expiry, y,
                        args.swap.iborIndex());
                amount = (args.floatingGearings.get(i) * fwd + args.floatingSpreads.get(i))
                        * args.floatingAccrualTimes.get(i) * args.floatingNominal[i];
            } else {
                amount = args.floatingCoupons.get(i);
            }
            final double zSpreadDf = oas_.empty()
                    ? 1.0
                    : JQuantMath.exp(-oas_.currentLink().value() * model_.termStructure().currentLink().dayCounter()
                            .yearFraction(expiry, args.floatingPayDates.get(i)));
            npv += amount * model_.zerobond(args.floatingPayDates.get(i), expiry, y, discountCurve_) * zSpreadDf;
        }

        return swapType * npv;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Mirrors C++ {@code underlyingType()}: returns the swap direction.
     */
    VanillaSwap.Type underlyingType() {
        return ((NonstandardSwaption.ArgumentsImpl) arguments_).swap.type();
    }

    /**
     * Mirrors C++ {@code underlyingLastDate()}: last fixed-leg pay date.
     */
    Date underlyingLastDate() {
        final List< Date > dates = ((NonstandardSwaption.ArgumentsImpl) arguments_).fixedPayDates;
        return dates.get(dates.size() - 1);
    }

    /**
     * Mirrors C++ {@code initialGuess(expiry)}: returns (nominalAvg, maturityTime, weightedRate).
     */
    double[] initialGuess(final Date expiry) {
        final NonstandardSwaption.ArgumentsImpl args = (NonstandardSwaption.ArgumentsImpl) arguments_;
        final int fixedIdx = upperBoundIndex(args.fixedResetDates, expiry.add(-1));

        double nominalSum = 0.0;
        double weightedRate = 0.0;
        double ind = 0.0;
        for ( int i = fixedIdx; i < args.fixedResetDates.size(); i++ ) {
            final double nom = args.fixedNominal[i];
            double rate = args.fixedRate[i];
            nominalSum += nom;
            if ( Math.abs(rate) < 1e-15 )
                rate = 0.03; // better than zero
            weightedRate += nom * rate;
            if ( nom > 1e-8 )
                ind += 1.0;
        }
        QL.require(nominalSum > 0.0, "sum of nominals on fixed leg must be positive (" + nominalSum + ")");
        final double nominalAvg = nominalSum / ind;
        weightedRate /= nominalSum;
        final double matTime =
                model_.termStructure().currentLink().timeFromReference(underlyingLastDate()) - model_.termStructure()
                        .currentLink().timeFromReference(expiry);
        return new double[] { nominalAvg, matTime, weightedRate };
    }

    /**
     * Probability-table mode (mirrors C++ {@code enum Probabilities}). Only {@link #None} is implemented — see
     * class-level deviation note.
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
