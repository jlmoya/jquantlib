/*
 Copyright (C) 2013, 2015 Peter Caspers

 Ported to Java from QuantLib v1.42.1 ql/processes/gsrprocess.hpp + .cpp
 (commit 099987f0ca2c11c505dc4348cdb9ce01a598e1e5) per Phase 2j WI-1.2.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/
 Released under the BSD License.
*/
package org.jquantlib.processes.gsr;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.processes.ForwardMeasureProcess1D;
import org.jquantlib.time.Date;

/**
 * GSR (Gaussian Short Rate) stochastic process.
 *
 * <p>Implements a piecewise-constant volatility and mean-reversion Hull-White
 * model expressed in the T-forward measure. The process is:
 * <pre>
 *   dx(t) = [y(t) - G(t,T) σ(t)² - κ(t) x(t)] dt  +  σ(t) dW_T
 * </pre>
 * where the drift is the T-forward measure drift, y(t) is the variance integral, and G(t,T) is the annuity-like
 * discount factor. All closed-form quantities are delegated to {@link GsrProcessCore}.
 *
 * <p>If a single reversion value is provided it is treated as constant.
 * Results are cached; call {@link #flushCache()} after parameter changes.
 *
 * <p>Ported from QuantLib v1.42.1 {@code ql/processes/gsrprocess.{hpp,cpp}}
 * (commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}) per Phase 2j WI-1.2.
 *
 * @author Phase 2j WI-1.2 port (Peter Caspers original C++ author)
 * @see GsrProcessCore
 */
public class GsrProcess extends ForwardMeasureProcess1D {

    private final GsrProcessCore core_;
    private final Date referenceDate_;
    private final DayCounter dc_;

    /**
     * Creates the GSR process without date/day-count support. The {@link #time(Date)} method will throw if called.
     *
     * @param times      breakpoint times (size = vols.length - 1)
     * @param vols       piecewise volatilities
     * @param reversions piecewise mean reversions (size 1 = constant)
     * @param T          T-forward measure horizon
     */
    public GsrProcess(final double[] times, final double[] vols, final double[] reversions, final double T) {
        this(times, vols, reversions, T, null, null);
    }

    /**
     * Creates the GSR process with optional date/day-count support.
     *
     * @param times         breakpoint times
     * @param vols          piecewise volatilities
     * @param reversions    piecewise mean reversions (size 1 = constant)
     * @param T             T-forward measure horizon
     * @param referenceDate reference date for {@link #time(Date)} (may be null)
     * @param dc            day counter for {@link #time(Date)} (may be null)
     */
    public GsrProcess(final double[] times, final double[] vols, final double[] reversions, final double T,
            final Date referenceDate, final DayCounter dc) {
        super(T);
        this.core_ = new GsrProcessCore(times, vols, reversions, T);
        this.referenceDate_ = referenceDate;
        this.dc_ = dc;
        flushCache();
    }

    // -----------------------------------------------------------------------
    // StochasticProcess1D interface
    // -----------------------------------------------------------------------

    @Override
    public double x0() {
        return 0.0;
    }

    /**
     * Drift μ(t, x) in the T-forward measure. = y(t) - G(t, T) σ(t)² - κ(t) x
     */
    @Override
    public double drift(final double t, final double x) {
        return core_.y(t) - core_.G(t, getForwardMeasureTime()) * sigma(t) * sigma(t) - reversion(t) * x;
    }

    /**
     * Diffusion σ(t) (state-independent for GSR).
     */
    @Override
    public double diffusion(final double t, final double x) {
        checkT(t);
        return sigma(t);
    }

    /**
     * Closed-form conditional expectation E[X(t+dt) | X(t) = x].
     */
    @Override
    public double expectation(final double w, final double xw, final double dt) {
        checkT(w + dt);
        return core_.expectation_x0dep_part(w, xw, dt) + core_.expectation_rn_part(w, dt) + core_.expectation_tf_part(w,
                dt);
    }

    /**
     * Closed-form conditional standard deviation.
     */
    @Override
    public double stdDeviation(final double t0, final double x0, final double dt) {
        return Math.sqrt(variance(t0, x0, dt));
    }

    /**
     * Closed-form conditional variance Var[X(w+dt) | X(w)].
     */
    @Override
    public double variance(final double w, final double x, final double dt) {
        checkT(w + dt);
        return core_.variance(w, dt);
    }

    /**
     * Convert a calendar Date to a year-fraction time.
     *
     * @param d date to convert
     * @return year fraction from referenceDate to d
     * @throws org.jquantlib.lang.exceptions.LibraryException if no referenceDate/dc set
     */
    public double time(final Date d) {
        QL.require(referenceDate_ != null && dc_ != null,
                "time can not be computed without reference date and day counter");
        return dc_.yearFraction(referenceDate_, d);
    }

    // -----------------------------------------------------------------------
    // ForwardMeasureProcess1D interface
    // -----------------------------------------------------------------------

    @Override
    public void setForwardMeasureTime(final double t) {
        flushCache();
        super.setForwardMeasureTime(t);
    }

    // -----------------------------------------------------------------------
    // Additional inspectors
    // -----------------------------------------------------------------------

    /** @return σ(t): piecewise volatility at time t */
    public double sigma(final double t) {
        return core_.sigma(t);
    }

    /** @return κ(t): piecewise mean reversion at time t */
    public double reversion(final double t) {
        return core_.reversion(t);
    }

    /**
     * y(t) = Var[x(t) | x(0) = 0] — the variance integral.
     *
     * @param t time (must be in [0, T])
     * @return y(t)
     */
    public double y(final double t) {
        checkT(t);
        return core_.y(t);
    }

    /**
     * G(t, T, x) = G(t, T) — the annuity-like discount factor.
     *
     * @param t start time
     * @param w end time (w >= t, w <= T)
     * @param x state (ignored; present for API compatibility with C++)
     * @return G(t, w)
     */
    public double G(final double t, final double w, final double x) {
        QL.require(w >= t, "G(t,w) should be called with w (%f) not lesser than t (%f)", w, t);
        QL.require(t >= 0.0 && w <= getForwardMeasureTime(),
                "G(t,w) should be called with (t,w)=(%f,%f) in Range [0,%f]", t, w, getForwardMeasureTime());
        return core_.G(t, w);
    }

    /** Flush all internal caches (forward to core). */
    public void flushCache() {
        core_.flushCache();
    }

    // -----------------------------------------------------------------------
    // Setters used by the Gsr model (friend pattern in C++).
    // C++ keeps these private with `friend class Gsr`. Java has no friend
    // mechanism, and Gsr lives in a different package
    // (org.jquantlib.model.shortrate.onefactormodels.gaussian1d), so these
    // are surfaced as public. Callers must call flushCache() before reuse;
    // Gsr does so in updateVolatility/updateReversion/generateArguments.
    // -----------------------------------------------------------------------

    public void setTimes(final double[] times) {
        core_.setTimes(times);
    }

    public void setVols(final double[] vols) {
        core_.setVols(vols);
    }

    public void setReversions(final double[] reversions) {
        core_.setReversions(reversions);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private void checkT(final double t) {
        QL.require(t <= getForwardMeasureTime() && t >= 0.0,
                "t (%f) must not be greater than forward measure time (%f) and non-negative", t,
                getForwardMeasureTime());
    }
}
