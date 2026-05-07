/*
 Copyright (C) 2013 Peter Caspers

 Ported to Java from QuantLib v1.42.1 ql/processes/mfstateprocess.hpp + .cpp
 (commit 099987f0ca2c11c505dc4348cdb9ce01a598e1e5) per Phase 2j WI-4.0a.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/
 Released under the BSD License.
*/
package org.jquantlib.processes;

import org.jquantlib.QL;
import org.jquantlib.math.transcendental.JQuantMath;

/**
 * Markov functional state process.
 *
 * <p>Describes the process governed by
 * {@code dx = sigma(t) e^{at} dW(t)}, where {@code a} is the mean reversion
 * and {@code sigma(t)} is a piecewise-constant volatility.
 *
 * <p>Ported from QuantLib v1.42.1 {@code ql/processes/mfstateprocess.{hpp,cpp}}
 * (commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}) per Phase 2j WI-4.0a.
 *
 * @author Phase 2j WI-4.0a port (Peter Caspers original C++ author)
 * @see org.jquantlib.model.shortrate.onefactormodels.MarkovFunctional
 */
public class MfStateProcess extends StochasticProcess1D {

    // QL_EPSILON: machine epsilon for double (same as C++ QL_EPSILON ~2.22e-16)
    private static final double QL_EPSILON = Math.ulp(1.0);

    private double   reversion_;
    private boolean  reversionZero_;
    private double[] times_;
    private double[] vols_;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    /**
     * Creates a MfStateProcess with piecewise-constant volatilities.
     *
     * @param reversion mean-reversion speed {@code a}
     * @param times     breakpoint times; must be strictly increasing;
     *                  {@code times.length == vols.length - 1}
     * @param vols      piecewise volatilities; all non-negative;
     *                  {@code vols.length == times.length + 1}
     */
    public MfStateProcess(final double reversion,
                          final double[] times,
                          final double[] vols) {
        super();
        this.reversion_     = reversion;
        this.times_         = times.clone();
        this.vols_          = vols.clone();
        this.reversionZero_ = (reversion_ < QL_EPSILON && -reversion_ < QL_EPSILON);
        checkTimesVols();
    }

    // -----------------------------------------------------------------------
    // Public setters (called by MarkovFunctional as a "friend"; Java has no
    // friend mechanism and MarkovFunctional lives in a different package, so
    // these are public — Phase 2j.5 Track C.3 alignment).
    // -----------------------------------------------------------------------

    public void setTimes(final double[] times) {
        times_ = times.clone();
        checkTimesVols();
        notifyObservers();
    }

    public void setVols(final double[] vols) {
        vols_ = vols.clone();
        checkTimesVols();
        notifyObservers();
    }

    // -----------------------------------------------------------------------
    // Validation
    // -----------------------------------------------------------------------

    private void checkTimesVols() {
        QL.require(times_.length == vols_.length - 1,
                "number of volatilities (" + vols_.length
                + ") compared to number of times (" + times_.length
                + ") must be bigger by one");
        for (int i = 0; i < times_.length - 1; i++) {
            QL.require(times_[i] < times_[i + 1],
                    "times must be increasing (" + times_[i] + "@" + i
                    + " , " + times_[i + 1] + "@" + (i + 1) + ")");
        }
        for (int i = 0; i < vols_.length; i++) {
            QL.require(vols_[i] >= 0.0,
                    "volatilities must be non negative (" + vols_[i] + "@" + i + ")");
        }
    }

    // -----------------------------------------------------------------------
    // StochasticProcess1D interface
    // -----------------------------------------------------------------------

    /**
     * Initial state: always 0.
     */
    @Override
    public double x0() {
        return 0.0;
    }

    /**
     * Drift: always 0.0 (process is driftless under the appropriate measure).
     */
    @Override
    public double drift(final double t, final double x) {
        return 0.0;
    }

    /**
     * Instantaneous volatility: piecewise lookup by {@code std::upper_bound}
     * on {@code times_}.
     */
    @Override
    public double diffusion(final double t, final double x) {
        // upper_bound(times_, t) – begin   →   first index i s.t. times_[i] > t
        final int i = upperBound(times_, t);
        return vols_[i];
    }

    /**
     * Conditional expectation: always {@code x0} (driftless process).
     */
    @Override
    public double expectation(final double t0, final double x0, final double dt) {
        return x0;
    }

    /**
     * Standard deviation: {@code sqrt(variance(t0, x0, dt))}.
     */
    @Override
    public double stdDeviation(final double t0, final double x0, final double dt) {
        return Math.sqrt(variance(t0, x0, dt));
    }

    /**
     * Conditional variance of {@code X(t0+dt) | X(t0)}.
     *
     * <p>When {@code dt < QL_EPSILON} returns 0.  When {@code times_} is
     * empty falls back to the single-segment formula.  Otherwise integrates
     * the piecewise vol-squared over {@code [t0, t0+dt]}, using the
     * analytic integral of {@code sigma_k^2 * exp(2*a*s)} in each segment.
     */
    @Override
    public double variance(final double t0, final double x, final double dt) {
        if (dt < QL_EPSILON) {
            return 0.0;
        }
        if (times_.length == 0) {
            // single-segment (empty breakpoints)
            if (reversionZero_) {
                return dt;
            } else {
                return 1.0 / (2.0 * reversion_)
                        * (JQuantMath.exp(2.0 * reversion_ * (t0 + dt))
                           - JQuantMath.exp(2.0 * reversion_ * t0));
            }
        }

        // Segment indices from upper_bound
        final int i = upperBound(times_, t0);
        final int j = upperBound(times_, t0 + dt);

        double v = 0.0;

        // Segments k = i .. j-1 (fully contained within [t0, t0+dt])
        for (int k = i; k < j; k++) {
            final double segStart = Math.max(k > 0 ? times_[k - 1] : 0.0, t0);
            if (reversionZero_) {
                v += vols_[k] * vols_[k] * (times_[k] - segStart);
            } else {
                v += 1.0 / (2.0 * reversion_) * vols_[k] * vols_[k]
                        * (JQuantMath.exp(2.0 * reversion_ * times_[k])
                           - JQuantMath.exp(2.0 * reversion_ * segStart));
            }
        }

        // Last (or only) segment: ends at t0+dt
        final double lastSegStart = Math.max(j > 0 ? times_[j - 1] : 0.0, t0);
        if (reversionZero_) {
            v += vols_[j] * vols_[j] * (t0 + dt - lastSegStart);
        } else {
            v += 1.0 / (2.0 * reversion_) * vols_[j] * vols_[j]
                    * (JQuantMath.exp(2.0 * reversion_ * (t0 + dt))
                       - JQuantMath.exp(2.0 * reversion_ * lastSegStart));
        }

        return v;
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    /**
     * Returns the number of elements in {@code arr} that are {@code <= key},
     * i.e. the C++ {@code std::upper_bound(arr.begin(), arr.end(), key) - arr.begin()}.
     */
    private static int upperBound(final double[] arr, final double key) {
        int lo = 0, hi = arr.length;
        while (lo < hi) {
            final int mid = (lo + hi) >>> 1;
            if (arr[mid] <= key) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }
}
