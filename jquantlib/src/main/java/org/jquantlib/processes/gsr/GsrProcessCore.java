/*
 Copyright (C) 2015 Peter Caspers

 Ported to Java from QuantLib v1.42.1 ql/processes/gsrprocesscore.hpp + .cpp
 (commit 099987f0ca2c11c505dc4348cdb9ce01a598e1e5) per Phase 2j WI-1.2.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/
 Released under the BSD License.
*/
package org.jquantlib.processes.gsr;

import org.jquantlib.QL;
import org.jquantlib.math.transcendental.JQuantMath;

import java.util.HashMap;
import java.util.Map;

/**
 * Core computations for the GSR (Gaussian Short Rate) stochastic process.
 *
 * <p>Provides analytical drift, diffusion, expectation and variance in both
 * the risk-neutral and T-forward measures.
 *
 * <p><strong>Warning:</strong> results are cached for performance. Call
 * {@link #flushCache()} after any parameter change.
 *
 * <p>Ported from QuantLib v1.42.1 {@code ql/processes/gsrprocesscore.{hpp,cpp}}
 * (commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}) per Phase 2j WI-1.2.
 *
 * @author Phase 2j WI-1.2 port (Peter Caspers original C++ author)
 */
public class GsrProcessCore {

    // Caches — keyed by (w, t) pairs.
    //
    // ALIGNMENT (Phase 2j WI-1.3): the original WI-1.2 implementation packed
    // (w, t) into a single `long` via `la * 1000003L ^ lb`. That's not
    // collision-free across the whole 128-bit space — e.g. with the standard
    // probe grid (3.0, 2.0) and (1.5, 1.0) collide. The collision was masked
    // by the WI-1.2 GsrProcessTest fixture but trips the Gsr-level zerobond
    // tests (Gaussian1dModelTest fm_073 etc.), so we now key on a real
    // {@link DoublePair} object whose equals/hashCode honours both members.
    // y(t) (cache4_) uses a single double and stays on Long.
    private final Map< DoublePair, Double > cache1_ = new HashMap<>();  // expectation_x0dep_part A(w,t)
    private final Map< DoublePair, Double > cache2a_ = new HashMap<>();  // expectation_rn_part
    private final Map< DoublePair, Double > cache2b_ = new HashMap<>();  // expectation_tf_part
    private final Map< DoublePair, Double > cache3_ = new HashMap<>();  // variance
    private final Map< Long, Double > cache4_ = new HashMap<>();  // y(t) — single arg
    private final Map< DoublePair, Double > cache5_ = new HashMap<>();  // G(t,w)
    // Parameter arrays  (package-accessible so GsrProcess.Gsr friend can mutate)
    protected double[] times_;
    protected double[] vols_;
    protected double[] reversions_;
    private final double T_;
    private boolean[] revZero_;

    /**
     * Creates the core with piecewise-constant parameters.
     *
     * @param times      breakpoint times (size = vols.length - 1)
     * @param vols       piecewise volatilities (size = times.length + 1)
     * @param reversions piecewise mean reversions (size 1 = constant, or times.length + 1)
     * @param T          T-forward measure horizon
     */
    public GsrProcessCore(final double[] times, final double[] vols, final double[] reversions, final double T) {
        this.times_ = times.clone();
        this.vols_ = vols.clone();
        this.reversions_ = reversions.clone();
        this.T_ = T;
        this.revZero_ = new boolean[reversions.length];
        flushCache();
        checkTimesVolsReversions();
    }

    // -----------------------------------------------------------------------
    // Package-private setters (called by GsrProcess / Gsr friend)
    // -----------------------------------------------------------------------

    void setTimes(final double[] times) {
        times_ = times.clone();
        checkTimesVolsReversions();
    }

    void setVols(final double[] vols) {
        vols_ = vols.clone();
        checkTimesVolsReversions();
    }

    void setReversions(final double[] reversions) {
        reversions_ = reversions.clone();
        revZero_ = new boolean[reversions_.length];
        checkTimesVolsReversions();
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /** @return σ(t): piecewise-constant volatility at time t */
    public double sigma(final double t) {
        return vol(lowerIndex(t));
    }

    /** @return κ(t): piecewise-constant mean reversion at time t */
    public double reversion(final double t) {
        return rev(lowerIndex(t));
    }

    /**
     * Conditional expectation — x0-dependent part: A(w, w+dt) * x(w).
     *
     * @param w  start time
     * @param xw state at w
     * @param dt time step
     * @return A(w, t) * xw
     */
    public double expectation_x0dep_part(final double w, final double xw, final double dt) {
        final double t = w + dt;
        final DoublePair key = new DoublePair(w, t);
        final Double cached = cache1_.get(key);
        if ( cached != null ) {
            return xw * cached;
        }
        // A(w,t) = prod_{i=lowerIndex(w)}^{upperIndex(t)-1} exp(-kappa_i * delta_i)
        double res2 = 1.0;
        for ( int i = lowerIndex(w); i <= upperIndex(t) - 1; i++ ) {
            res2 *= JQuantMath.exp(-rev(i) * (cappedTime(i + 1, t) - flooredTime(i, w)));
        }
        cache1_.put(key, res2);
        return res2 * xw;
    }

    /**
     * Conditional expectation — risk-neutral drift integral.
     *
     * @param w  start time
     * @param dt time step
     * @return ∫A(s,t) y(s) ds
     */
    public double expectation_rn_part(final double w, final double dt) {
        final double t = w + dt;
        final DoublePair key = new DoublePair(w, t);
        final Double cached = cache2a_.get(key);
        if ( cached != null ) {
            return cached;
        }

        double res = 0.0;
        for ( int k = lowerIndex(w); k <= upperIndex(t) - 1; k++ ) {
            // l < k
            for ( int l = 0; l <= k - 1; l++ ) {
                double res2 = 1.0;
                // alpha_l
                if ( revZero(l) ) {
                    res2 *= vol(l) * vol(l) * (time2(l + 1) - time2(l));
                } else {
                    res2 *= vol(l) * vol(l) / (2.0 * rev(l)) * (1.0 - JQuantMath.exp(
                            -2.0 * rev(l) * (time2(l + 1) - time2(l))));
                }
                // zeta_i (i > k)
                for ( int i = k + 1; i <= upperIndex(t) - 1; i++ ) {
                    res2 *= JQuantMath.exp(-rev(i) * (cappedTime(i + 1, t) - time2(i)));
                }
                // beta_j (l < j < k)
                for ( int j = l + 1; j <= k - 1; j++ ) {
                    res2 *= JQuantMath.exp(-2.0 * rev(j) * (time2(j + 1) - time2(j)));
                }
                // zeta_k * beta_k
                if ( revZero(k) ) {
                    res2 *= 2.0 * time2(k) - flooredTime(k, w) - cappedTime(k + 1, t) - 2.0 * (time2(k) - cappedTime(
                            k + 1, t));
                } else {
                    res2 *= (JQuantMath.exp(rev(k) * (2.0 * time2(k) - flooredTime(k, w) - cappedTime(k + 1, t)))
                            - JQuantMath.exp(2.0 * rev(k) * (time2(k) - cappedTime(k + 1, t)))) / rev(k);
                }
                res += res2;
            }
            // l = k
            double res2 = 1.0;
            // alpha_k * zeta_k
            if ( revZero(k) ) {
                // Phase 2n A.2: JQuantMath.pow (CORE-MATH cr_pow) now active here.
                final double capped = cappedTime(k + 1, t);
                final double floored = flooredTime(k, w);
                res2 *= vol(k) * vol(k) / 4.0 * (4.0 * JQuantMath.pow(capped - time2(k), 2.0) - (
                        JQuantMath.pow(floored - 2.0 * time2(k) + capped, 2.0) + JQuantMath.pow(capped - floored,
                                2.0)));
            } else {
                res2 *= vol(k) * vol(k) / (2.0 * rev(k) * rev(k)) * (
                        JQuantMath.exp(-2.0 * rev(k) * (cappedTime(k + 1, t) - time2(k))) + 1.0 - (
                                JQuantMath.exp(-rev(k) * (flooredTime(k, w) - 2.0 * time2(k) + cappedTime(k + 1, t)))
                                        + JQuantMath.exp(-rev(k) * (cappedTime(k + 1, t) - flooredTime(k, w)))));
            }
            // zeta_i (i > k)
            for ( int i = k + 1; i <= upperIndex(t) - 1; i++ ) {
                res2 *= JQuantMath.exp(-rev(i) * (cappedTime(i + 1, t) - time2(i)));
            }
            res += res2;
        }

        cache2a_.put(key, res);
        return res;
    }

    /**
     * Conditional expectation — T-forward measure drift adjustment.
     *
     * @param w  start time
     * @param dt time step
     * @return T-forward drift correction
     */
    public double expectation_tf_part(final double w, final double dt) {
        final double t = w + dt;
        final DoublePair key = new DoublePair(w, t);
        final Double cached = cache2b_.get(key);
        if ( cached != null ) {
            return cached;
        }

        double res = 0.0;
        for ( int k = lowerIndex(w); k <= upperIndex(t) - 1; k++ ) {
            double res2 = 0.0;
            // l > k
            for ( int l = k + 1; l <= upperIndex(T_) - 1; l++ ) {
                double res3 = 1.0;
                // eta_l
                if ( revZero(l) ) {
                    res3 *= cappedTime(l + 1, T_) - time2(l);
                } else {
                    res3 *= (1.0 - JQuantMath.exp(-rev(l) * (cappedTime(l + 1, T_) - time2(l)))) / rev(l);
                }
                // zeta_i (i > k)
                for ( int i = k + 1; i <= upperIndex(t) - 1; i++ ) {
                    res3 *= JQuantMath.exp(-rev(i) * (cappedTime(i + 1, t) - time2(i)));
                }
                // gamma_j (k < j < l)
                for ( int j = k + 1; j <= l - 1; j++ ) {
                    res3 *= JQuantMath.exp(-rev(j) * (time2(j + 1) - time2(j)));
                }
                // zeta_k * gamma_k
                if ( revZero(k) ) {
                    res3 *= (cappedTime(k + 1, t) - time2(k + 1) - (2.0 * flooredTime(k, w) - cappedTime(k + 1, t)
                            - time2(k + 1))) / 2.0;
                } else {
                    res3 *= (JQuantMath.exp(rev(k) * (cappedTime(k + 1, t) - time2(k + 1))) - JQuantMath.exp(
                            rev(k) * (2.0 * flooredTime(k, w) - cappedTime(k + 1, t) - time2(k + 1)))) / (2.0 * rev(k));
                }
                res2 += res3;
            }
            // l = k
            double res3 = 1.0;
            // eta_k * zeta_k
            if ( revZero(k) ) {
                // Phase 2n A.2: JQuantMath.pow (CORE-MATH cr_pow) now active here (see expectation_rn_part).
                final double capped_t = cappedTime(k + 1, t);
                final double capped_T = cappedTime(k + 1, T_);
                final double floored = flooredTime(k, w);
                res3 *= (-JQuantMath.pow(capped_t - capped_T, 2.0) - 2.0 * JQuantMath.pow(capped_t - floored, 2.0)
                        + JQuantMath.pow(2.0 * floored - capped_T - capped_t, 2.0)) / 4.0;
            } else {
                res3 *= (2.0 - JQuantMath.exp(rev(k) * (cappedTime(k + 1, t) - cappedTime(k + 1, T_))) - (
                        2.0 * JQuantMath.exp(-rev(k) * (cappedTime(k + 1, t) - flooredTime(k, w))) - JQuantMath.exp(
                                rev(k) * (2.0 * flooredTime(k, w) - cappedTime(k + 1, T_) - cappedTime(k + 1, t))))) / (
                        2.0 * rev(k) * rev(k));
            }
            // zeta_i (i > k)
            for ( int i = k + 1; i <= upperIndex(t) - 1; i++ ) {
                res3 *= JQuantMath.exp(-rev(i) * (cappedTime(i + 1, t) - time2(i)));
            }
            res2 += res3;
            res += -vol(k) * vol(k) * res2;
        }

        cache2b_.put(key, res);
        return res;
    }

    /**
     * Conditional variance Var[X(w+dt) | X(w)].
     *
     * @param w  start time
     * @param dt time step
     * @return conditional variance
     */
    public double variance(final double w, final double dt) {
        final double t = w + dt;
        final DoublePair key = new DoublePair(w, t);
        final Double cached = cache3_.get(key);
        if ( cached != null ) {
            return cached;
        }

        double res = 0.0;
        for ( int k = lowerIndex(w); k <= upperIndex(t) - 1; k++ ) {
            double res2 = vol(k) * vol(k);
            // zeta_k^2
            if ( revZero(k) ) {
                res2 *= -(flooredTime(k, w) - cappedTime(k + 1, t));
            } else {
                res2 *= (1.0 - JQuantMath.exp(2.0 * rev(k) * (flooredTime(k, w) - cappedTime(k + 1, t)))) / (2.0 * rev(
                        k));
            }
            // zeta_i^2 (i > k)
            for ( int i = k + 1; i <= upperIndex(t) - 1; i++ ) {
                res2 *= JQuantMath.exp(-2.0 * rev(i) * (cappedTime(i + 1, t) - time2(i)));
            }
            res += res2;
        }

        cache3_.put(key, res);
        return res;
    }

    /**
     * y(t) = ∫_0^t A(s,t)^2 σ(s)^2 ds — the variance of the Gaussian distribution of x(t) conditional on x(0) = 0.
     *
     * @param t time
     * @return y(t)
     */
    public double y(final double t) {
        final long key = Double.doubleToRawLongBits(t);
        final Double cached = cache4_.get(key);
        if ( cached != null ) {
            return cached;
        }

        double res = 0.0;
        for ( int i = 0; i <= upperIndex(t) - 1; i++ ) {
            double res2 = 1.0;
            for ( int j = i + 1; j <= upperIndex(t) - 1; j++ ) {
                res2 *= JQuantMath.exp(-2.0 * rev(j) * (cappedTime(j + 1, t) - time2(j)));
            }
            if ( revZero(i) ) {
                res2 *= vol(i) * vol(i) * (cappedTime(i + 1, t) - time2(i));
            } else {
                res2 *= vol(i) * vol(i) / (2.0 * rev(i)) * (1.0 - JQuantMath.exp(
                        -2.0 * rev(i) * (cappedTime(i + 1, t) - time2(i))));
            }
            res += res2;
        }

        cache4_.put(key, res);
        return res;
    }

    /**
     * G(t, w) = ∫_t^w A(t, s) ds — the annuity-like integral used in the T-forward drift.
     *
     * @param t start time
     * @param w end time (w >= t)
     * @return G(t, w)
     */
    public double G(final double t, final double w) {
        final DoublePair key = new DoublePair(w, t); // note: cache5_ uses (w,t) as in C++ cache5_ key
        final Double cached = cache5_.get(key);
        if ( cached != null ) {
            return cached;
        }

        double res = 0.0;
        for ( int i = lowerIndex(t); i <= upperIndex(w) - 1; i++ ) {
            double res2 = 1.0;
            for ( int j = lowerIndex(t); j <= i - 1; j++ ) {
                res2 *= JQuantMath.exp(-rev(j) * (time2(j + 1) - flooredTime(j, t)));
            }
            if ( revZero(i) ) {
                res2 *= cappedTime(i + 1, w) - flooredTime(i, t);
            } else {
                res2 *= (1.0 - JQuantMath.exp(-rev(i) * (cappedTime(i + 1, w) - flooredTime(i, t)))) / rev(i);
            }
            res += res2;
        }

        cache5_.put(key, res);
        return res;
    }

    /** Invalidate all caches and recompute the revZero_ flags. */
    public void flushCache() {
        for ( int i = 0; i < reversions_.length; i++ ) {
            revZero_[i] = Math.abs(reversions_[i]) < 1.0e-4;
        }
        cache1_.clear();
        cache2a_.clear();
        cache2b_.clear();
        cache3_.clear();
        cache4_.clear();
        cache5_.clear();
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Lower-index: returns the segment index k such that times_[k-1] < t <= times_[k] (or 0 if t <= times_[0]).
     */
    private int lowerIndex(final double t) {
        // upper_bound equivalent: first index i where times_[i] > t
        int lo = 0, hi = times_.length;
        while ( lo < hi ) {
            int mid = (lo + hi) >>> 1;
            if ( times_[mid] <= t )
                lo = mid + 1;
            else
                hi = mid;
        }
        return lo; // same as C++ std::upper_bound - begin
    }

    /**
     * Upper-index: returns the segment index for the open interval ending at t. Special-cased so that t == 0 returns
     * 0.
     */
    private int upperIndex(final double t) {
        if ( t < Double.MIN_VALUE ) { // QL_MIN_POSITIVE_REAL = min normal double
            return 0;
        }
        // upper_bound on (t - epsilon) + 1
        final double tEps = t - Math.ulp(1.0); // QL_EPSILON is machine epsilon for double
        int lo = 0, hi = times_.length;
        while ( lo < hi ) {
            int mid = (lo + hi) >>> 1;
            if ( times_[mid] <= tEps )
                lo = mid + 1;
            else
                hi = mid;
        }
        return lo + 1;
    }

    /**
     * time2(index): returns the time associated with segment boundary index. index 0 => 0.0; index > times_.length =>
     * T_; otherwise times_[index-1].
     */
    private double time2(final int index) {
        if ( index == 0 ) {
            return 0.0;
        }
        if ( index > times_.length ) {
            return T_;
        }
        return times_[index - 1];
    }

    /** Capped segment time: min(cap, time2(index)) if cap is finite. */
    private double cappedTime(final int index, final double cap) {
        final double t2 = time2(index);
        return Double.isNaN(cap) ? t2 : Math.min(cap, t2);
    }

    /** Floored segment time: max(floor, time2(index)) if floor is finite. */
    private double flooredTime(final int index, final double floor) {
        final double t2 = time2(index);
        return Double.isNaN(floor) ? t2 : Math.max(floor, t2);
    }

    private double vol(final int index) {
        if ( index >= vols_.length ) {
            return vols_[vols_.length - 1];
        }
        return vols_[index];
    }

    private double rev(final int index) {
        if ( index >= reversions_.length ) {
            return reversions_[reversions_.length - 1];
        }
        return reversions_[index];
    }

    private boolean revZero(final int index) {
        if ( index >= revZero_.length ) {
            return revZero_[revZero_.length - 1];
        }
        return revZero_[index];
    }

    private void checkTimesVolsReversions() {
        QL.require(times_.length == vols_.length - 1,
                "number of volatilities (%d) compared to number of times (%d) must be bigger by one", vols_.length,
                times_.length);
        QL.require(times_.length == reversions_.length - 1 || reversions_.length == 1,
                "number of reversions (%d) compared to number of times (%d) must be bigger by one, "
                        + "or exactly 1 reversion must be given", reversions_.length, times_.length);
        for ( int i = 0; i < times_.length - 1; i++ ) {
            QL.require(times_[i] < times_[i + 1], "times must be increasing (times[%d]=%f, times[%d]=%f)", i, times_[i],
                    i + 1, times_[i + 1]);
        }
    }

    /**
     * Hash key holding a 128-bit (a, b) double pair without collisions.
     * <p>
     * The previous {@code pairKey(a, b) = la * 1000003L ^ lb} encoding was not collision-free over 128 bits — e.g.
     * (3.0, 2.0) and (1.5, 1.0) collided, which silently corrupted G(t, w) cache lookups across Gsr-driven test grids.
     */
    private static final class DoublePair {
        private final long la;
        private final long lb;

        DoublePair(final double a, final double b) {
            this.la = Double.doubleToRawLongBits(a);
            this.lb = Double.doubleToRawLongBits(b);
        }

        @Override
        public boolean equals(final Object o) {
            if ( this == o )
                return true;
            if ( !(o instanceof DoublePair) )
                return false;
            final DoublePair p = (DoublePair) o;
            return la == p.la && lb == p.lb;
        }

        @Override
        public int hashCode() {
            // 32-bit fold of the 128-bit pair — collisions in hashCode are
            // OK (HashMap chains via equals); collisions in equals are the
            // bug we're fixing.
            final long mixed = la * 0x9E3779B97F4A7C15L + lb;
            return (int) (mixed ^ (mixed >>> 32));
        }
    }
}
