/*
 Copyright (C) 2026 JQuantLib contributors

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

package org.jquantlib.model.shortrate.onefactormodels.gaussian1d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.indexes.SwapIndex;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.math.Constants;
import org.jquantlib.math.ErrorFunction;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.transcendental.JQuantMath;
import org.jquantlib.processes.StochasticProcess1D;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.util.LazyObject;

/**
 * One-factor Gaussian short-rate model abstract base class.
 * <p>
 * Java port of QuantLib v1.42.1
 * {@code ql/models/shortrate/onefactormodels/gaussian1dmodel.{hpp,cpp}}
 * (commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}). Phase 2j WI-1.1.
 * <p>
 * The C++ class inherits both {@code TermStructureConsistentModel} and
 * {@code LazyObject} via multiple inheritance. Java has no multiple class
 * inheritance, so this port extends {@link LazyObject} and holds the yield
 * term structure as a field, exposing {@link #termStructure()} to mirror the
 * {@code TermStructureConsistentModel} accessor. (See decision in
 * {@code docs/migration/phase2j-design.md}.)
 * <p>
 * The only methods that must be implemented by subclasses are
 * {@link #numeraireImpl} and {@link #zerobondImpl}, both consuming the
 * standardized state variable {@code y} (zero mean, unit variance). All
 * non-virtual public methods (forwardRate, swapRate, swapAnnuity,
 * zerobondOption, etc.) call into those abstract hooks.
 *
 * @author Peter Caspers (C++ original)
 * @author JQuantLib contributors (Java port)
 */
public abstract class Gaussian1dModel extends LazyObject {

    //
    // ──────────────────────────────────────────────────────────────────────
    //   Fields (mirror C++ Gaussian1dModel members)
    // ──────────────────────────────────────────────────────────────────────
    //

    /** Yield term structure handle (was held by C++ TermStructureConsistentModel). */
    private final Handle<YieldTermStructure> termStructure_;

    /** State process — protected so subclasses can install their own. */
    protected StochasticProcess1D stateProcess_;

    /** Cached evaluation date (refreshed in performCalculations). */
    protected Date evaluationDate_;

    /** Cached "enforces today's historic fixings" flag. */
    protected boolean enforcesTodaysHistoricFixings_;

    /**
     * Cache of underlying swaps generated from {@link SwapIndex} templates.
     * <p>
     * In C++ the cache is keyed on (index name, fixing serial, tenor units,
     * tenor length). We use a String key with the same components.
     */
    private final Map<String, VanillaSwap> swapCache_ = new HashMap<String, VanillaSwap>();

    //
    // ──────────────────────────────────────────────────────────────────────
    //   Constructor (protected — concrete subclasses register with TS)
    // ──────────────────────────────────────────────────────────────────────
    //

    protected Gaussian1dModel(final Handle<YieldTermStructure> yieldTermStructure) {
        super();
        this.termStructure_ = yieldTermStructure;
        // C++: registerWith(Settings::instance().evaluationDate());
        // Java Settings.evaluationDate() is the Date value; the analogous
        // mechanism is to leave registration to subclasses (HullWhite et al.
        // do the same — see TermStructureConsistentModelClass usage). The
        // performCalculations() refresh of evaluationDate_ keeps state fresh.
    }

    //
    // ──────────────────────────────────────────────────────────────────────
    //   Public accessors (TermStructureConsistentModel surface)
    // ──────────────────────────────────────────────────────────────────────
    //

    /** @return the yield term structure handle this model is calibrated against. */
    public final Handle<YieldTermStructure> termStructure() {
        return termStructure_;
    }

    /** @return the underlying 1-D state process; must be non-null. */
    public StochasticProcess1D stateProcess() {
        QL.require(stateProcess_ != null, "state process not set");
        return stateProcess_;
    }

    //
    // ──────────────────────────────────────────────────────────────────────
    //   Numeraire / zerobond — Time-based + Date-based overloads
    // ──────────────────────────────────────────────────────────────────────
    //

    /** {@code N(t, y)} under the model's forward measure. */
    public final double numeraire(final double t, final double y, final Handle<YieldTermStructure> yts) {
        return numeraireImpl(t, y, yts);
    }

    public final double numeraire(final double t, final double y) {
        return numeraireImpl(t, y, new Handle<YieldTermStructure>());
    }

    public final double numeraire(final double t) {
        return numeraireImpl(t, 0.0, new Handle<YieldTermStructure>());
    }

    public final double numeraire(final Date referenceDate, final double y, final Handle<YieldTermStructure> yts) {
        return numeraire(termStructure_.currentLink().timeFromReference(referenceDate), y, yts);
    }

    public final double numeraire(final Date referenceDate, final double y) {
        return numeraire(referenceDate, y, new Handle<YieldTermStructure>());
    }

    /** {@code P(t, T; y)} zero-coupon bond price under the model's forward measure. */
    public final double zerobond(final double T, final double t, final double y, final Handle<YieldTermStructure> yts) {
        return zerobondImpl(T, t, y, yts);
    }

    public final double zerobond(final double T, final double t, final double y) {
        return zerobondImpl(T, t, y, new Handle<YieldTermStructure>());
    }

    public final double zerobond(final double T, final double t) {
        return zerobondImpl(T, t, 0.0, new Handle<YieldTermStructure>());
    }

    public final double zerobond(final double T) {
        return zerobondImpl(T, 0.0, 0.0, new Handle<YieldTermStructure>());
    }

    public final double zerobond(
            final Date maturity,
            final Date referenceDate,
            final double y,
            final Handle<YieldTermStructure> yts) {
        final double T = termStructure_.currentLink().timeFromReference(maturity);
        final double t = (referenceDate != null && !referenceDate.isNull())
                ? termStructure_.currentLink().timeFromReference(referenceDate)
                : 0.0;
        return zerobond(T, t, y, yts);
    }

    public final double zerobond(final Date maturity, final Date referenceDate, final double y) {
        return zerobond(maturity, referenceDate, y, new Handle<YieldTermStructure>());
    }

    public final double zerobond(final Date maturity) {
        return zerobond(maturity, null, 0.0, new Handle<YieldTermStructure>());
    }

    //
    // ──────────────────────────────────────────────────────────────────────
    //   Abstract hooks (concrete models implement these)
    // ──────────────────────────────────────────────────────────────────────
    //

    protected abstract double numeraireImpl(double t, double y, Handle<YieldTermStructure> yts);

    protected abstract double zerobondImpl(double T, double t, double y, Handle<YieldTermStructure> yts);

    //
    // ──────────────────────────────────────────────────────────────────────
    //   LazyObject / observable hooks
    // ──────────────────────────────────────────────────────────────────────
    //

    @Override
    protected void performCalculations() {
        evaluationDate_ = new Settings().evaluationDate();
        enforcesTodaysHistoricFixings_ = new Settings().isEnforcesTodaysHistoricFixings();
    }

    /**
     * Mirrors C++ {@code Gaussian1dModel::generateArguments}: forces a
     * recalculation and notifies observers. Subclasses should call this
     * after mutating model parameters.
     */
    protected void generateArguments() {
        calculate();
        notifyObservers();
    }

    //
    // ──────────────────────────────────────────────────────────────────────
    //   forwardRate / swapRate / swapAnnuity (non-virtual public API)
    // ──────────────────────────────────────────────────────────────────────
    //

    /**
     * Convenience {@link #forwardRate(Date, Date, double, IborIndex)} with
     * default {@code referenceDate=null}, {@code y=0}.
     */
    public final double forwardRate(final Date fixing, final IborIndex iborIdx) {
        return forwardRate(fixing, null, 0.0, iborIdx);
    }

    /**
     * Forward-measure forward rate at fixing date.
     *
     * @param fixing      the IBOR fixing date
     * @param referenceDate observation reference date ({@code null} ⇒ today)
     * @param y           standardized state-variable value
     * @param iborIdx     the IBOR index defining tenor / day count / calendar
     * @return the model-implied forward rate
     */
    public double forwardRate(
            final Date fixing,
            final Date referenceDate,
            final double y,
            final IborIndex iborIdx) {

        QL.require(iborIdx != null, "no ibor index given");

        calculate();

        // C++: fixing <= (evaluationDate_ + (enforcesTodaysHistoricFixings_ ? 0 : -1))
        final int offset = enforcesTodaysHistoricFixings_ ? 0 : -1;
        if (fixing.le(evaluationDate_.add(offset))) {
            return iborIdx.fixing(fixing);
        }

        // Java IborIndex exposes only termStructure() (not forwardingTermStructure()).
        // Behaviorally equivalent: the index's discounting curve is its forecasting curve.
        final Handle<YieldTermStructure> yts = iborIdx.termStructure();

        final Date valueDate = iborIdx.valueDate(fixing);
        final Date endDate = iborIdx.fixingCalendar().advance(
                valueDate, iborIdx.tenor(), iborIdx.businessDayConvention(), iborIdx.endOfMonth());
        final double dcf = iborIdx.dayCounter().yearFraction(valueDate, endDate);

        return (zerobond(valueDate, referenceDate, y, yts)
                - zerobond(endDate, referenceDate, y, yts))
                / (dcf * zerobond(endDate, referenceDate, y, yts));
    }

    /**
     * Forward-measure par swap rate.
     *
     * @param fixing        the swap's fixing date
     * @param tenor         swap maturity (length-of-swap), used to clone the index
     *                      template if the underlying SwapIndex differs
     * @param referenceDate observation reference date ({@code null} ⇒ today)
     * @param y             standardized state-variable value
     * @param swapIdx       the SwapIndex defining conventions
     * @return the model-implied par swap rate
     */
    public double swapRate(
            final Date fixing,
            final Period tenor,
            final Date referenceDate,
            final double y,
            final SwapIndex swapIdx) {

        QL.require(swapIdx != null, "no swap index given");

        calculate();

        final int offset = enforcesTodaysHistoricFixings_ ? 0 : -1;
        if (fixing.le(evaluationDate_.add(offset))) {
            return swapIdx.fixing(fixing);
        }

        // Java SwapIndex has a single termStructure() (== ibor.termStructure()).
        // The C++ split into forwarding (ibor) vs discounting (swap) maps to a
        // single curve here; both ytsf and ytsd resolve to the same handle.
        final Handle<YieldTermStructure> ytsf = swapIdx.iborIndex().termStructure();
        final Handle<YieldTermStructure> ytsd = swapIdx.termStructure();

        final VanillaSwap underlying = underlyingSwap(swapIdx, fixing, tenor);
        final Schedule sched = underlying.fixedSchedule();

        // C++ short-circuits OvernightIndexedSwapIndex to use the same schedule
        // for both legs. JQuantLib does not yet have that subclass — fall through
        // to the standard floating-leg path.
        final Schedule floatSched = underlying.floatingSchedule();

        final double annuity = swapAnnuity(fixing, tenor, referenceDate, y, swapIdx);

        double floatleg = 0.0;
        if (ytsf.empty() && ytsd.empty()) {
            // simple 100-formula in single-curve setup
            final List<Date> dates = sched.dates();
            final Date front = dates.get(0);
            final Date back = sched.calendar().adjust(
                    dates.get(dates.size() - 1), underlying.paymentConvention());
            floatleg = zerobond(front, referenceDate, y) - zerobond(back, referenceDate, y);
        } else {
            for (int i = 1; i < floatSched.size(); i++) {
                final Date prev = floatSched.date(i - 1);
                final Date cur = floatSched.date(i);
                final Date paid = floatSched.calendar().adjust(cur, underlying.paymentConvention());
                floatleg += (zerobond(prev, referenceDate, y, ytsf)
                        / zerobond(cur, referenceDate, y, ytsf) - 1.0)
                        * zerobond(paid, referenceDate, y, ytsd);
            }
        }
        return floatleg / annuity;
    }

    /**
     * Forward-measure annuity for the same swap conventions as
     * {@link #swapRate(Date, Period, Date, double, SwapIndex)}.
     */
    public double swapAnnuity(
            final Date fixing,
            final Period tenor,
            final Date referenceDate,
            final double y,
            final SwapIndex swapIdx) {

        QL.require(swapIdx != null, "no swap index given");

        calculate();

        final Handle<YieldTermStructure> ytsd = swapIdx.termStructure();

        final VanillaSwap underlying = underlyingSwap(swapIdx, fixing, tenor);
        final Schedule sched = underlying.fixedSchedule();

        double annuity = 0.0;
        for (int j = 1; j < sched.size(); j++) {
            final Date paid = sched.calendar().adjust(
                    sched.date(j), underlying.paymentConvention());
            annuity += zerobond(paid, referenceDate, y, ytsd)
                    * swapIdx.dayCounter().yearFraction(sched.date(j - 1), sched.date(j));
        }
        return annuity;
    }

    //
    // ──────────────────────────────────────────────────────────────────────
    //   zerobondOption (forward-measure caplet/floorlet on a zero bond)
    // ──────────────────────────────────────────────────────────────────────
    //

    /**
     * Forward-measure call/put on a zero-coupon bond with cubic-spline payoff
     * extrapolation. This method is currently <strong>unsupported in Java</strong>:
     * the C++ implementation depends on
     * {@code org.jquantlib.math.interpolations.CubicInterpolation} with full
     * {@code aCoefficients()/bCoefficients()/cCoefficients()} accessors that
     * do not yet exist on the Java side. Will be revisited in WI-1.4 when the
     * pricing-engine sub-layer needs it.
     *
     * @throws UnsupportedOperationException always (Phase 2j WI-1.1 stub)
     */
    public double zerobondOption(
            final Option.Type type,
            final Date expiry,
            final Date valueDate,
            final Date maturity,
            final double strike,
            final Date referenceDate,
            final double y,
            final Handle<YieldTermStructure> yts,
            final double yStdDevs,
            final int yGridPoints,
            final boolean extrapolatePayoff,
            final boolean flatPayoffExtrapolation) {
        throw new UnsupportedOperationException(
                "Gaussian1dModel.zerobondOption: deferred to Phase 2j WI-1.4 / WI-2 "
                        + "(requires CubicInterpolation a/b/c coefficient accessors).");
    }

    //
    // ──────────────────────────────────────────────────────────────────────
    //   Static helpers — Gaussian polynomial integrals
    // ──────────────────────────────────────────────────────────────────────
    //

    /**
     * Computes
     * {@latex[ {2\pi}^{-1/2} \int_{x_0}^{x_1} p(x) \exp(-x^2/2) \, dx }
     * with {@latex$ p(x) = a x^4 + b x^3 + c x^2 + d x + e }.
     */
    public static double gaussianPolynomialIntegral(
            final double a, final double b, final double c, final double d, final double e,
            final double y0, final double y1) {

        final double aa = 4.0 * a;
        final double ba = 2.0 * Constants.M_SQRT2 * b;
        final double ca = 2.0 * c;
        final double da = Constants.M_SQRT2 * d;
        final double x0 = y0 * Constants.M_SQRT_2;   // M_SQRT_2 = 1/sqrt(2)
        final double x1 = y1 * Constants.M_SQRT_2;

        final ErrorFunction erf = new ErrorFunction();
        final double erfX1 = erf.op(x1);
        final double erfX0 = erf.op(x0);
        // M_1_SQRTPI ≡ 1 / sqrt(pi). The C++ formula uses 1 / (4*sqrt(pi)).
        final double invFourSqrtPi = 0.25 * Constants.M_1_SQRTPI;

        return (0.125 * (3.0 * aa + 2.0 * ca + 4.0 * e) * erfX1
                - invFourSqrtPi * JQuantMath.exp(-x1 * x1) *
                        (2.0 * aa * x1 * x1 * x1 + 3.0 * aa * x1
                                + 2.0 * ba * (x1 * x1 + 1.0) + 2.0 * ca * x1 + 2.0 * da))
                - (0.125 * (3.0 * aa + 2.0 * ca + 4.0 * e) * erfX0
                        - invFourSqrtPi * JQuantMath.exp(-x0 * x0) *
                                (2.0 * aa * x0 * x0 * x0 + 3.0 * aa * x0
                                        + 2.0 * ba * (x0 * x0 + 1.0) + 2.0 * ca * x0 + 2.0 * da));
    }

    /**
     * Computes
     * {@latex[ {2\pi}^{-1/2} \int_{x_0}^{x_1} p(x) \exp(-x^2/2) \, dx }
     * with {@latex$ p(x) = a (x-h)^4 + b (x-h)^3 + c (x-h)^2 + d (x-h) + e }
     * by reducing to {@link #gaussianPolynomialIntegral(double, double, double, double, double, double, double)}.
     */
    public static double gaussianShiftedPolynomialIntegral(
            final double a, final double b, final double c, final double d, final double e,
            final double h, final double x0, final double x1) {
        return gaussianPolynomialIntegral(
                a,
                -4.0 * a * h + b,
                6.0 * a * h * h - 3.0 * b * h + c,
                -4.0 * a * h * h * h + 3.0 * b * h * h - 2.0 * c * h + d,
                a * h * h * h * h - b * h * h * h + c * h * h - d * h + e,
                x0, x1);
    }

    //
    // ──────────────────────────────────────────────────────────────────────
    //   yGrid — grid in standardized state-variable space
    // ──────────────────────────────────────────────────────────────────────
    //

    /**
     * Generates a grid of values for the standardized state variable $y$ at
     * time $T$ conditional on $y(t) = y$, covering {@code stdDevs} standard
     * deviations consisting of {@code 2 * gridPoints + 1} points.
     */
    public Array yGrid(
            final double stdDevs, final int gridPoints,
            final double T, final double t, final double y) {

        QL.require(stateProcess_ != null, "state process not set");

        final Array result = new Array(2 * gridPoints + 1);

        final double stdDev_0_T = stateProcess_.stdDeviation(0.0, 0.0, T);
        final double e_0_T = stateProcess_.expectation(0.0, 0.0, T);

        double stdDev_t_T;
        double e_t_T;
        if (t < Constants.QL_EPSILON) {
            stdDev_t_T = stdDev_0_T;
            e_t_T = e_0_T;
        } else {
            final double stdDev_0_t = stateProcess_.stdDeviation(0.0, 0.0, t);
            stdDev_t_T = stateProcess_.stdDeviation(t, 0.0, T - t);
            final double e_0_t = stateProcess_.expectation(0.0, 0.0, t);
            final double x_t = y * stdDev_0_t + e_0_t;
            e_t_T = stateProcess_.expectation(t, x_t, T - t);
        }

        final double h = stdDevs / ((double) gridPoints);

        for (int j = -gridPoints; j <= gridPoints; j++) {
            result.set(j + gridPoints,
                    (e_t_T + stdDev_t_T * ((double) j) * h - e_0_T) / stdDev_0_T);
        }

        return result;
    }

    public Array yGrid(final double stdDevs, final int gridPoints, final double T) {
        return yGrid(stdDevs, gridPoints, T, 0.0, 0.0);
    }

    public Array yGrid(final double stdDevs, final int gridPoints) {
        return yGrid(stdDevs, gridPoints, 1.0, 0.0, 0.0);
    }

    //
    // ──────────────────────────────────────────────────────────────────────
    //   Underlying-swap cache (mirrors C++ swapCache_)
    // ──────────────────────────────────────────────────────────────────────
    //

    /**
     * Returns the {@link VanillaSwap} for the given index/expiry/tenor combo,
     * caching by (index name, fixing serial, tenor units, tenor length).
     * <p>
     * <b>Note:</b> The C++ implementation calls {@code index->clone(tenor)}
     * to retemplate the SwapIndex with the requested tenor before building
     * the underlying swap. The current Java {@link SwapIndex} API does not
     * yet expose {@code clone(Period)}, so this base implementation ignores
     * the {@code tenor} argument and uses the SwapIndex as-is. Concrete
     * Phase 2j subclasses (Gsr, MarkovFunctional) and engines that pass
     * non-template indices already configured for the desired tenor are
     * unaffected; callers using arbitrary tenors will need
     * {@code SwapIndex.clone(Period)} added in a follow-up (A15).
     */
    protected VanillaSwap underlyingSwap(
            final SwapIndex index, final Date expiry, final Period tenor) {
        final String key = index.name() + "|" + expiry.serialNumber()
                + "|" + tenor.units().ordinal() + "|" + tenor.length();
        VanillaSwap cached = swapCache_.get(key);
        if (cached == null) {
            // TODO (Phase 2j follow-up): when SwapIndex.clone(Period) lands,
            // call index.clone(tenor).underlyingSwap(expiry) instead.
            cached = index.underlyingSwap(expiry);
            swapCache_.put(key, cached);
        }
        return cached;
    }

    //
    // ──────────────────────────────────────────────────────────────────────
    //   Suppress unused-warning helpers (keep BusinessDayConvention import live
    //   even while paymentConvention() is the only consumer; future overloads
    //   will need the type directly).
    // ──────────────────────────────────────────────────────────────────────
    //

    @SuppressWarnings("unused")
    private static BusinessDayConvention __keepBdcImport() {
        return BusinessDayConvention.Following;
    }
}
