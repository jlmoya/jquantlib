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
*/

package org.jquantlib.termstructures.volatilities;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.instruments.Option;
import org.jquantlib.math.Constants;
import org.jquantlib.math.Ops;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.distributions.Derivative;
import org.jquantlib.math.solvers1D.Brent;
import org.jquantlib.math.solvers1D.NewtonSafe;
import org.jquantlib.math.transcendental.JQuantMath;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.pricingengines.BlackFormula;

/**
 * Arbitrage-free smile section using Kahale C^1 inter- and extrapolation.
 *
 * <p>Mirrors C++ QuantLib v1.42.1 {@code KahaleSmileSection}
 * (kahalesmilesection.hpp/.cpp). Shifted-lognormal input only; Normal
 * input sections are rejected at construction.
 *
 * <p>Phase 2j WI-4.0c.
 *
 * @author JQuantLib migration contributors
 */
public class KahaleSmileSection extends SmileSection {

    // -------------------------------------------------------------------
    // Numerical constants (mirrors C++ macros)
    // -------------------------------------------------------------------
    private static final double KAHALE_FMAX = Double.MAX_VALUE;
    private static final double KAHALE_SMAX = 5.0;
    private static final double KAHALE_ACC  = 1e-12;

    // -------------------------------------------------------------------
    // High-precision inverse normal (with Halley refinement)
    // Mirrors boost::math::quantile(normal, x) precision used in C++ helpers.
    // QL's rational approximation has max error 1.15e-9; one Halley step
    // brings it to full machine precision.
    // -------------------------------------------------------------------
    private static final CumulativeNormalDistribution HP_PHI = new CumulativeNormalDistribution();
    private static final InverseCumulativeNormal HP_INV = new InverseCumulativeNormal();

    private static double invNormal(final double x) {
        double z = HP_INV.op(x);
        // One Halley iteration: r = (Phi(z)-x) / phi(z); z -= r/(1+0.5*z*r)
        // phi(z) = exp(-0.5*z^2) / sqrt(2*pi)  →  1/phi(z) = sqrt(2*pi)*exp(0.5*z^2)
        final double r = (HP_PHI.op(z) - x)
                * Constants.M_SQRT2 * Constants.M_SQRTPI
                * JQuantMath.exp(0.5 * z * z);
        z -= r / (1.0 + 0.5 * z * r);
        return z;
    }

    // -------------------------------------------------------------------
    // Inner helper: c-function used for both lognormal and exponential forms
    // -------------------------------------------------------------------

    /** Mirrors C++ {@code KahaleSmileSection::cFunction}. */
    static final class CFunction {
        final double f_, s_, a_, b_;
        final boolean exponential_;

        /** Lognormal (Black-Scholes) form: c(k) = BS(f,k,s) + a*k + b. */
        CFunction(final double f, final double s, final double a, final double b) {
            this.f_ = f;
            this.s_ = s;
            this.a_ = a;
            this.b_ = b;
            this.exponential_ = false;
        }

        /** Exponential form: c(k) = exp(-a*k + b). */
        CFunction(final double a, final double b) {
            this.f_ = 0;
            this.s_ = 0;
            this.a_ = a;
            this.b_ = b;
            this.exponential_ = true;
        }

        double eval(final double k) {
            if (exponential_) {
                return JQuantMath.exp(-a_ * k + b_);
            }
            if (s_ < Constants.QL_EPSILON) {
                return Math.max(f_ - k, 0.0) + a_ * k + b_;
            }
            final CumulativeNormalDistribution phi = new CumulativeNormalDistribution();
            final double d1 = JQuantMath.log(f_ / k) / s_ + s_ / 2.0;
            final double d2 = d1 - s_;
            // When d1 is very large (>~8.3), N(d1) == 1.0 in double precision
            // (Boost::math and other implementations return exactly 1.0).
            // Java's CumulativeNormalDistribution returns 1-eps, causing
            // catastrophic cancellation in f_*N(d1)-f_ when b_=-f_.
            // Use f_ directly when N(d1)==1.0 to match C++ Boost behaviour.
            // When d1 is very large (>~8.3), Boost::math::cdf(normal, d1) == 1.0
            // exactly in C++ (it saturates). Java's CumulativeNormalDistribution
            // returns 1 - eps, causing catastrophic cancellation when f_ is huge
            // (b_=-f_ in SHelper1). Match Boost's saturation behaviour.
            final double nd1 = (d1 > 8.2) ? 1.0 : phi.op(d1);
            return f_ * nd1 - k * phi.op(d2) + a_ * k + b_;
        }
    }

    // -------------------------------------------------------------------
    // Helper functors for Brent solve
    // -------------------------------------------------------------------

    /** Mirrors C++ {@code aHelper}: finds a that makes c-function fit two grid pts. */
    private static final class AHelper implements Ops.DoubleOp {
        private final double k0_, k1_, c0_, c1_, c0p_, c1p_;
        // mutable state filled after eval
        double s_, f_, b_;

        AHelper(final double k0, final double k1,
                final double c0, final double c1,
                final double c0p, final double c1p) {
            k0_ = k0; k1_ = k1; c0_ = c0; c1_ = c1; c0p_ = c0p; c1p_ = c1p;
        }

        @Override
        public double op(final double a) {
            final double d20 = invNormal(-c0p_ + a);
            final double d21 = invNormal(-c1p_ + a);
            final double alpha = (d20 - d21) / (JQuantMath.log(k0_) - JQuantMath.log(k1_));
            final double beta  = d20 - alpha * JQuantMath.log(k0_);
            s_ = -1.0 / alpha;
            f_ = JQuantMath.exp(s_ * (beta + s_ / 2.0));
            QL.require(f_ < KAHALE_FMAX, "dummy");
            final CFunction cTmp = new CFunction(f_, s_, a, 0.0);
            b_ = c0_ - cTmp.eval(k0_);
            final CFunction c = new CFunction(f_, s_, a, b_);
            return c.eval(k1_) - c1_;
        }
    }

    /** Mirrors C++ {@code sHelper}: finds s for right-wing extrapolation. */
    private static final class SHelper implements Ops.DoubleOp {
        private final double k0_, c0_, c0p_;
        // mutable state
        double f_;

        SHelper(final double k0, final double c0, final double c0p) {
            k0_ = k0; c0_ = c0; c0p_ = c0p;
        }

        @Override
        public double op(double s) {
            s = Math.max(s, 0.0);
            final double d20 = invNormal(-c0p_);
            f_ = k0_ * JQuantMath.exp(s * d20 + s * s / 2.0);
            QL.require(f_ < KAHALE_FMAX, "dummy");
            final CFunction c = new CFunction(f_, s, 0.0, 0.0);
            return c.eval(k0_) - c0_;
        }
    }

    /** Mirrors C++ {@code sHelper1}: finds s for left-wing extrapolation. */
    private static final class SHelper1 implements Ops.DoubleOp {
        private final double k1_, c0_, c1_, c1p_;
        // mutable state
        double f_, b_;

        SHelper1(final double k1, final double c0, final double c1, final double c1p) {
            k1_ = k1; c0_ = c0; c1_ = c1; c1p_ = c1p;
        }

        @Override
        public double op(double s) {
            s = Math.max(s, 0.0);
            final double d21 = invNormal(-c1p_);
            f_ = k1_ * JQuantMath.exp(s * d21 + s * s / 2.0);
            QL.require(f_ < KAHALE_FMAX, "dummy");
            b_ = c0_ - f_;
            final CFunction c = new CFunction(f_, s, 0.0, b_);
            return c.eval(k1_) - c1_;
        }
    }

    // -------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------
    private final SmileSection source_;
    private double[] moneynessGrid_;
    private double[] k_;
    private double[] c_;
    private double f_;
    private final double gap_;
    private int leftIndex_;
    private int rightIndex_;
    private final List<CFunction> cFunctions_;
    private final boolean interpolate_;
    private final boolean exponentialExtrapolation_;
    private final int forcedLeftIndex_;
    private final int forcedRightIndex_;
    private final SmileSectionUtils ssutils_;

    // -------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------

    /** Full constructor mirroring C++ v1.42.1 {@code KahaleSmileSection}. */
    public KahaleSmileSection(
            final SmileSection source,
            final double atm,
            final boolean interpolate,
            final boolean exponentialExtrapolation,
            final boolean deleteArbitragePoints,
            final double[] moneynessGrid,
            final double gap,
            final int forcedLeftIndex,
            final int forcedRightIndex) {

        super(source.exerciseTime(), source.dayCounter(), source.volatilityType(), source.shift());

        QL.require(source.volatilityType() == VolatilityType.ShiftedLognormal,
                "KahaleSmileSection only supports shifted lognormal source sections");

        source_                  = source;
        gap_                     = gap;
        interpolate_             = interpolate;
        exponentialExtrapolation_= exponentialExtrapolation;
        forcedLeftIndex_         = forcedLeftIndex;
        forcedRightIndex_        = forcedRightIndex;
        cFunctions_              = new ArrayList<>();

        ssutils_ = new SmileSectionUtils(source, moneynessGrid, atm, deleteArbitragePoints);

        moneynessGrid_ = ssutils_.moneyGrid();
        k_             = ssutils_.strikeGrid();
        c_             = ssutils_.callPrices();
        f_             = ssutils_.atmLevel();

        // For shifted smile sections, shift the forward and the strikes
        final double shift = source.shift();
        for (int i = 0; i < k_.length; i++) {
            k_[i] += shift;
        }
        f_ += shift;

        compute();
    }

    /** Convenience constructor with default moneyness grid and gap. */
    public KahaleSmileSection(
            final SmileSection source,
            final double atm,
            final boolean interpolate,
            final boolean exponentialExtrapolation,
            final boolean deleteArbitragePoints) {
        this(source, atm, interpolate, exponentialExtrapolation, deleteArbitragePoints,
             new double[0], 1e-5, -1, Integer.MAX_VALUE);
    }

    /** Convenience constructor with custom moneyness grid. */
    public KahaleSmileSection(
            final SmileSection source,
            final double atm,
            final boolean interpolate,
            final boolean exponentialExtrapolation,
            final boolean deleteArbitragePoints,
            final double[] moneynessGrid) {
        this(source, atm, interpolate, exponentialExtrapolation, deleteArbitragePoints,
             moneynessGrid, 1e-5, -1, Integer.MAX_VALUE);
    }

    // -------------------------------------------------------------------
    // SmileSection overrides
    // -------------------------------------------------------------------

    @Override
    public double minStrike() { return -source_.shift(); }

    @Override
    public double maxStrike() { return Double.MAX_VALUE; }

    @Override
    public double atmLevel() { return f_; }

    /**
     * Digital option price using THIS object's optionPrice.
     * Mirrors C++ {@code SmileSection::digitalOptionPrice} called on {@code this}.
     * Used only AFTER cFunctions_ are fully populated (sanity check).
     */
    private double digitalOptionPrice(final double strike, final Option.Type type,
                                      final double discount, final double gapParam) {
        final double m = -source_.shift();
        final double kl = Math.max(strike - gapParam / 2.0, m);
        final double kr = kl + gapParam;
        return (type == Option.Type.Call ? 1.0 : -1.0)
                * (optionPrice(kl, type, discount) - optionPrice(kr, type, discount)) / gapParam;
    }

    /**
     * Digital option price using the SOURCE section's optionPrice.
     * Mirrors C++ calls like {@code source_->digitalOptionPrice(...)}.
     * Used in compute() before cFunctions_ are populated.
     */
    private double sourceDigitalOptionPrice(final double strike, final Option.Type type,
                                             final double discount, final double gapParam) {
        final double m = -source_.shift();
        final double kl = Math.max(strike - gapParam / 2.0, m);
        final double kr = kl + gapParam;
        return (type == Option.Type.Call ? 1.0 : -1.0)
                * (source_.optionPrice(kl, type, discount) - source_.optionPrice(kr, type, discount))
                / gapParam;
    }

    @Override
    public double optionPrice(final double strike, final Option.Type type, final double discount) {
        final double shiftedStrike = Math.max(strike + source_.shift(), Constants.QL_EPSILON);
        final int i = index(shiftedStrike);
        if (interpolate_
                || (i == 0 || i == (rightIndex_ - leftIndex_ + 1))) {
            final double callPrice = cFunctions_.get(i).eval(shiftedStrike);
            return discount * (type == Option.Type.Call
                    ? callPrice
                    : callPrice + shiftedStrike - f_);
        }
        return source_.optionPrice(strike, type, discount);
    }

    @Override
    protected double volatilityImpl(final double strike) {
        final double shiftedStrike = Math.max(strike + source_.shift(), Constants.QL_EPSILON);
        final int i = index(shiftedStrike);
        if (!interpolate_ && !(i == 0 || i == (rightIndex_ - leftIndex_ + 1))) {
            return source_.volatility(strike);
        }
        final double c = cFunctions_.get(i).eval(shiftedStrike);
        double vol = 0.0;
        try {
            final Option.Type type = shiftedStrike >= f_ ? Option.Type.Call : Option.Type.Put;
            final double price = (type == Option.Type.Put) ? strike - f_ + c : c;
            // Use local implementation with maxStdDev=24.0 matching C++ QuantLib v1.42.1.
            // Java's BlackFormula.blackFormulaImpliedStdDev uses maxStdDev=3.0 which produces
            // a different bisection path than C++'s maxStdDev=24.0, causing ~1e-6 discrepancy.
            vol = blackFormulaImpliedStdDevKahale(type, shiftedStrike, f_, price)
                    / Math.sqrt(exerciseTime_);
        } catch (final Exception ignored) {
            // return 0 on numerical failure (mirrors C++ catch (...))
        }
        return vol;
    }

    /**
     * Mirrors C++ QuantLib v1.42.1 {@code blackFormulaImpliedStdDev} with
     * {@code maxStdDev=24.0} and default {@code accuracy=1e-6}.
     * Java's {@link BlackFormula#blackFormulaImpliedStdDev} uses maxStdDev=3.0,
     * which produces a different bisection path and different terminal value.
     */
    private static double blackFormulaImpliedStdDevKahale(
            final Option.Type optionType,
            final double strike,
            final double forward,
            final double blackPrice) {

        // Approximation initial guess (Corrado-Miller extended moneyness)
        final double guess = BlackFormula.blackFormulaImpliedStdDevApproximation(
                optionType, strike, forward, blackPrice, 1.0, 0.0);

        final BlackImpliedStdDevHelperLocal fHelper =
                new BlackImpliedStdDevHelperLocal(optionType, strike, forward, blackPrice);
        final NewtonSafe solver = new NewtonSafe();
        solver.setMaxEvaluations(100);
        // C++ hard-codes minStdDev=0.0, maxStdDev=24.0 (= 300% * sqrt(60))
        final double minStdDev = 0.0, maxStdDev = 24.0;
        return solver.solve(fHelper, 1e-6, guess, minStdDev, maxStdDev);
    }

    /**
     * Mirrors C++ {@code BlackImpliedStdDevHelper} (inner class in blackformula.cpp).
     * Used only for the local Kahale volatility inversion.
     */
    private static final class BlackImpliedStdDevHelperLocal implements Derivative {
        private final double halfOptionType_;
        private final double signedStrike_, signedForward_;
        private final double undiscountedBlackPrice_, signedMoneyness_;
        private final CumulativeNormalDistribution N_ = new CumulativeNormalDistribution();

        BlackImpliedStdDevHelperLocal(final Option.Type optionType,
                                      final double strike,
                                      final double forward,
                                      final double undiscountedBlackPrice) {
            final int ot = optionType.toInteger();
            halfOptionType_         = 0.5 * ot;
            signedStrike_           = ot * strike;
            signedForward_          = ot * forward;
            undiscountedBlackPrice_ = undiscountedBlackPrice;
            signedMoneyness_        = ot * Math.log(forward / strike);
        }

        @Override
        public double op(final double stddev) {
            if (stddev == 0.0) {
                return Math.max(signedForward_ - signedStrike_, 0.0) - undiscountedBlackPrice_;
            }
            final double temp   = halfOptionType_ * stddev;
            final double d      = signedMoneyness_ / stddev;
            final double sd1    = d + temp;
            final double sd2    = d - temp;
            final double result = signedForward_ * N_.op(sd1) - signedStrike_ * N_.op(sd2);
            return Math.max(0.0, result) - undiscountedBlackPrice_;
        }

        @Override
        public double derivative(final double stddev) {
            final double sd1 = signedMoneyness_ / stddev + halfOptionType_ * stddev;
            return signedForward_ * N_.derivative(sd1);
        }
    }

    // -------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------

    /** Returns [leftIndex, rightIndex] of the core arbitrage-free region. */
    public int[] coreIndices() {
        return new int[]{leftIndex_, rightIndex_};
    }

    /** Returns the left boundary strike of the core region. */
    public double leftCoreStrike() { return k_[leftIndex_]; }

    /** Returns the right boundary strike of the core region. */
    public double rightCoreStrike() { return k_[rightIndex_]; }

    // -------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------

    /** Maps a shifted strike to a cFunctions_ index (0-based). */
    private int index(final double strike) {
        // Upper-bound binary search in k_
        int lo = 0;
        int hi = k_.length;
        while (lo < hi) {
            final int mid = (lo + hi) >>> 1;
            if (k_[mid] <= strike) lo = mid + 1;
            else hi = mid;
        }
        // lo = upper_bound position
        int i = lo - leftIndex_;
        // Clamp to [0, rightIndex_ - leftIndex_ + 1]
        i = Math.max(0, Math.min(i, rightIndex_ - leftIndex_ + 1));
        return i;
    }

    /** Mirrors C++ {@code KahaleSmileSection::compute()}. */
    private void compute() {
        final int[] afIdx = ssutils_.arbitragefreeIndices();
        leftIndex_  = afIdx[0];
        rightIndex_ = afIdx[1];

        // Initialise cFunctions_ list to the right size
        final int sz = rightIndex_ - leftIndex_ + 2;
        for (int i = 0; i < sz; i++) cFunctions_.add(null);

        final Brent brent = new Brent();
        brent.setMaxEvaluations(10000);

        // ---------------------------------------------------------------
        // Extrapolation in the leftmost interval
        // ---------------------------------------------------------------
        boolean success;
        double secl = 0.0;
        do {
            success = true;
            try {
                final double k1  = k_[leftIndex_];
                final double c1  = c_[leftIndex_];
                final double c0  = c_[0];
                secl = (c_[leftIndex_] - c_[0]) / (k_[leftIndex_] - k_[0]);
                final double sec = (c_[leftIndex_ + 1] - c_[leftIndex_])
                                 / (k_[leftIndex_ + 1] - k_[leftIndex_]);
                final double c1p;
                if (interpolate_) {
                    c1p = (secl + sec) / 2.0;
                } else {
                    // Use SOURCE's digital option price (mirrors C++ source_->digitalOptionPrice)
                    c1p = -sourceDigitalOptionPrice(k1 - source_.shift() + gap_ / 2.0,
                                                    Option.Type.Call, 1.0, gap_);
                    QL.require(secl < c1p && c1p <= 0.0, "dummy");
                }

                final SHelper1 sh1 = new SHelper1(k1, c0, c1, c1p);
                final double s = brent.solve(sh1, KAHALE_ACC, 0.20, 0.0, KAHALE_SMAX);
                sh1.op(s); // fill mutable state

                final CFunction cFct1 = new CFunction(sh1.f_, s, 0.0, sh1.b_);
                cFunctions_.set(0, cFct1);

                // Sanity check: digital near k1/2 must be in [-c1p, 1]
                final double dig = digitalOptionPrice(
                        (k1 - source_.shift()) / 2.0, Option.Type.Call, 1.0, gap_);
                QL.require(dig >= -c1p && dig <= 1.0, "dummy");

                if (leftIndex_ < forcedLeftIndex_) {
                    leftIndex_++;
                    success = false;
                }
            } catch (final Exception e) {
                leftIndex_++;
                success = false;
            }
        } while (!success && leftIndex_ < rightIndex_);

        QL.require(leftIndex_ < rightIndex_,
                "can not extrapolate to left, right index of af region reached ("
                + rightIndex_ + ")");

        // ---------------------------------------------------------------
        // Interpolation
        // ---------------------------------------------------------------
        double cp0 = 0.0, cp1 = 0.0;
        if (interpolate_) {
            int i = leftIndex_;
            while (i < rightIndex_) {
                final double k0Local = k_[i];
                final double k1Local = k_[i + 1];
                final double c0Local = c_[i];
                final double c1Local = c_[i + 1];
                final double sec = (c_[i + 1] - c_[i]) / (k_[i + 1] - k_[i]);
                if (i == leftIndex_) {
                    cp0 = (leftIndex_ > 0) ? (secl + sec) / 2.0 : sec;
                }
                final double secr;
                if (i == rightIndex_ - 1) {
                    secr = 0.0;
                } else {
                    secr = (c_[i + 2] - c_[i + 1]) / (k_[i + 2] - k_[i + 1]);
                }
                cp1 = (sec + secr) / 2.0;

                final AHelper ah = new AHelper(k0Local, k1Local, c0Local, c1Local, cp0, cp1);
                boolean valid = false;
                double a = 0.0;
                try {
                    a = brent.solve(ah, KAHALE_ACC,
                                    0.5 * (cp1 + (1.0 + cp0)),
                                    cp1 + Constants.QL_EPSILON,
                                    1.0 + cp0 - Constants.QL_EPSILON);
                    valid = true;
                } catch (final Exception e) {
                    // Remove the right point of the interval
                    k_ = removeAt(k_, i + 1);
                    c_ = removeAt(c_, i + 1);
                    moneynessGrid_ = removeAt(moneynessGrid_, i + 1);
                    removeCFunction(i + 1);
                    rightIndex_--;
                    i--;
                }
                if (valid) {
                    ah.op(a); // fill mutable state
                    final CFunction cFct = new CFunction(ah.f_, ah.s_, a, ah.b_);
                    final int funcIdx = (leftIndex_ > 0) ? (i - leftIndex_ + 1) : 0;
                    cFunctions_.set(funcIdx, cFct);
                    cp0 = cp1;
                }
                i++;
            }
        }

        // ---------------------------------------------------------------
        // Extrapolation of right wing
        // ---------------------------------------------------------------
        do {
            success = true;
            try {
                final double k0Local = k_[rightIndex_];
                final double c0Local = c_[rightIndex_];
                final double cp0local;
                if (interpolate_) {
                    cp0local = 0.5 * (c_[rightIndex_] - c_[rightIndex_ - 1])
                                   / (k_[rightIndex_] - k_[rightIndex_ - 1]);
                } else {
                    // Use SOURCE's digital option price (mirrors C++ source_->digitalOptionPrice)
                    cp0local = -sourceDigitalOptionPrice(
                            k0Local - shift() - gap_ / 2.0, Option.Type.Call, 1.0, gap_);
                }
                final CFunction cFct;
                if (exponentialExtrapolation_) {
                    QL.require(-cp0local / c0Local > 0.0, "dummy");
                    cFct = new CFunction(-cp0local / c0Local,
                                         JQuantMath.log(c0Local) - cp0local / c0Local * k0Local);
                } else {
                    final SHelper sh = new SHelper(k0Local, c0Local, cp0local);
                    final double s = brent.solve(sh, KAHALE_ACC, 0.20, 0.0, KAHALE_SMAX);
                    sh.op(s); // fill mutable state
                    cFct = new CFunction(sh.f_, s, 0.0, 0.0);
                }
                cFunctions_.set(rightIndex_ - leftIndex_ + 1, cFct);
            } catch (final Exception e) {
                rightIndex_--;
                success = false;
            }
            if (rightIndex_ > forcedRightIndex_) {
                rightIndex_--;
                success = false;
            }
        } while (!success && rightIndex_ > leftIndex_);

        QL.require(leftIndex_ < rightIndex_,
                "can not extrapolate to right, left index of af region reached ("
                + leftIndex_ + ")");
    }

    // -------------------------------------------------------------------
    // Array helpers (simulate C++ std::vector::erase)
    // -------------------------------------------------------------------

    private static double[] removeAt(final double[] arr, final int pos) {
        final double[] newArr = new double[arr.length - 1];
        System.arraycopy(arr, 0, newArr, 0, pos);
        System.arraycopy(arr, pos + 1, newArr, pos, arr.length - pos - 1);
        return newArr;
    }

    private void removeCFunction(final int pos) {
        if (pos < cFunctions_.size()) {
            cFunctions_.remove(pos);
        }
    }
}
