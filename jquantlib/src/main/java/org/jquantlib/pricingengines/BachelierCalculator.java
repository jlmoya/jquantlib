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
 Copyright (C) 2003, 2004, 2005, 2006 Ferdinando Ametrano
 Copyright (C) 2006 StatPro Italia srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <http://quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
 */

package org.jquantlib.pricingengines;

import org.jquantlib.QL;
import org.jquantlib.instruments.AssetOrNothingPayoff;
import org.jquantlib.instruments.CashOrNothingPayoff;
import org.jquantlib.instruments.GapPayoff;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.Payoff;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.Closeness;
import org.jquantlib.math.Constants;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;

/**
 * Bachelier calculator class.
 *
 * <p>Java port of QuantLib v1.42.1 {@code ql/pricingengines/bacheliercalculator.{hpp,cpp}}.
 * Mirrors the Bachelier-model (normal absolute-vol) analogue of
 * {@link BlackCalculator}. All formulas use the single parameter
 * {@code d = (F - K) / sigma} (no log) and accept both positive and negative
 * forwards/strikes.
 *
 * <p>The class supports the same payoff hierarchy as
 * {@code BlackCalculator}: {@link PlainVanillaPayoff},
 * {@link CashOrNothingPayoff}, {@link AssetOrNothingPayoff}, and
 * {@link GapPayoff}, dispatched through the JQuantLib
 * {@link PolymorphicVisitor} pattern.
 */
public class BachelierCalculator {

    //
    // protected mutable fields (mirror C++ semantics — visitor callbacks
    // overwrite alpha/beta/x and the associated derivatives)
    //

    protected /* @Real */ double strike;
    protected /* @Real */ double forward;
    protected /* @StdDev */ double stdDev;
    protected /* @DiscountFactor */ double discount;
    protected /* @Variance */ double variance;
    /** Bachelier-model {@code d = (F - K) / sigma}. */
    protected double d;
    protected double alpha, beta;
    protected double DalphaDd, DbetaDd;
    protected double n_d, cum_d;
    protected double x;
    protected double DxDs;
    protected double DxDstrike;


    //
    // public constructors
    //

    /** Constructor taking a {@link StrikedTypePayoff}. */
    public BachelierCalculator(final StrikedTypePayoff payoff,
                               final double forward,
                               final double stdDev) {
        this(payoff, forward, stdDev, 1.0);
    }

    /**
     * Convenience constructor matching C++ v1.42.1 — internally builds a
     * {@link PlainVanillaPayoff}.
     */
    public BachelierCalculator(final Option.Type optionType,
                               final double strike,
                               final double forward,
                               final double stdDev) {
        this(new PlainVanillaPayoff(optionType, strike), forward, stdDev, 1.0);
    }

    /**
     * Convenience constructor matching C++ v1.42.1 — internally builds a
     * {@link PlainVanillaPayoff}.
     */
    public BachelierCalculator(final Option.Type optionType,
                               final double strike,
                               final double forward,
                               final double stdDev,
                               final double discount) {
        this(new PlainVanillaPayoff(optionType, strike), forward, stdDev, discount);
    }

    /** Primary constructor — port of C++ {@code BachelierCalculator}. */
    public BachelierCalculator(final StrikedTypePayoff payoff,
                               final double forward,
                               final double stdDev,
                               final double discount) {
        this.strike = payoff.strike();
        this.forward = forward;
        this.stdDev = stdDev;
        this.discount = discount;
        this.variance = stdDev * stdDev;
        initialize(payoff);
    }


    //
    // initialization (mirrors C++ initialize(p))
    //

    private void initialize(final StrikedTypePayoff payoff) {
        QL.require(stdDev >= 0.0, "stdDev (" + stdDev + ") must be non-negative");
        QL.require(discount > 0.0, "discount (" + discount + ") must be positive");

        // For the Bachelier model: d = (F - K) / sigma. No log; strikes and
        // forwards may be of either sign.
        if (stdDev >= Constants.QL_EPSILON) {
            d = (forward - strike) / stdDev;
            final CumulativeNormalDistribution f = new CumulativeNormalDistribution();
            cum_d = f.op(d);
            n_d = f.derivative(d);
        } else {
            // Zero-volatility limit (cf. C++ bacheliercalculator.cpp:72-87).
            if (Closeness.isClose(forward, strike)) {
                d = 0.0;
                cum_d = 0.5;
                n_d = Constants.M_SQRT_2 * Constants.M_1_SQRTPI;
            } else if (forward > strike) {
                d = Constants.QL_MAX_REAL;
                cum_d = 1.0;
                n_d = 0.0;
            } else {
                d = Constants.QL_MIN_REAL;
                cum_d = 0.0;
                n_d = 0.0;
            }
        }

        x = strike;
        DxDstrike = 1.0;
        DxDs = 0.0;

        // Plain-vanilla decomposition: discount * (forward * alpha + x * beta)
        // collapses to the Bachelier price via the inline branch in value().
        final Option.Type optionType = payoff.optionType();
        if (optionType == Option.Type.Call) {
            alpha = cum_d;          // N(d)
            DalphaDd = n_d;         // n(d)
            beta = -cum_d;          // -N(d)
            DbetaDd = -n_d;         // -n(d)
        } else if (optionType == Option.Type.Put) {
            alpha = cum_d - 1.0;    // N(d) - 1 = -N(-d)
            DalphaDd = n_d;         // n(d)
            beta = 1.0 - cum_d;     // 1 - N(d) = N(-d)
            DbetaDd = -n_d;         // -n(d)
        } else {
            throw new LibraryException("invalid option type");
        }

        // Dispatch to refine alpha/beta/x for non-vanilla payoffs.
        final Calculator calc = new Calculator(this);
        payoff.accept(calc);
    }


    //
    // public methods
    //

    /**
     * Option value:
     * <ul>
     *   <li>Call: {@code discount * [(F-K) N(d) + sigma n(d)]}</li>
     *   <li>Put : {@code discount * [(K-F) N(-d) + sigma n(d)]}</li>
     * </ul>
     */
    public /* @Real */ double value() /* @ReadOnly */ {
        // Mirror C++ v1.42.1 bacheliercalculator.cpp:169-189 exactly.
        // Note: C++ value() always uses the vanilla closed-form path,
        // branching only on the sign of alpha_. For cash-or-nothing /
        // asset-or-nothing / gap payoffs the visitor updates the
        // intermediate alpha/beta/x state used by Greek formulas, but
        // value() itself still reduces to the vanilla call/put price.
        final double intrinsic = forward - strike;
        double timeValue = 0.0;
        if (stdDev > Constants.QL_EPSILON) {
            timeValue = stdDev * n_d;
        }
        final double raw;
        if (alpha >= 0) { // Call (alpha = N(d) >= 0)
            raw = intrinsic * cum_d + timeValue;
        } else {          // Put (alpha = N(d) - 1 < 0)
            raw = -intrinsic * (1.0 - cum_d) + timeValue;
        }
        return discount * Math.max(raw, 0.0);
    }

    /**
     * Sensitivity to change in the underlying spot price.
     *
     * <p>{@code Delta = dV/dS = (dV/dF) * (dF/dS)} with {@code dF/dS = F/S}.
     */
    public /* @Real */ double delta(final double spot) /* @ReadOnly */ {
        final double DforwardDs = forward / spot;
        return deltaForward() * DforwardDs;
    }

    /**
     * Sensitivity to change in the underlying forward price.
     *
     * <p>Bachelier: {@code N(d)} for calls, {@code N(d) - 1} for puts.
     */
    public /* @Real */ double deltaForward() /* @ReadOnly */ {
        if (alpha >= 0) {
            return discount * cum_d;
        }
        return discount * (cum_d - 1.0);
    }

    /** Sensitivity in percent to a percent change in the underlying spot price. */
    public double elasticity(final double spot) /* @ReadOnly */ {
        final double val = value();
        final double del = delta(spot);
        if (val > Constants.QL_EPSILON) {
            return del / val * spot;
        } else if (Math.abs(del) < Constants.QL_EPSILON) {
            return 0.0;
        } else if (del > 0.0) {
            return Constants.QL_MAX_REAL;
        } else {
            return Constants.QL_MIN_REAL;
        }
    }

    /** Sensitivity in percent to a percent change in the underlying forward price. */
    public double elasticityForward() /* @ReadOnly */ {
        final double val = value();
        final double del = deltaForward();
        if (val > Constants.QL_EPSILON) {
            return del / val * forward;
        } else if (Math.abs(del) < Constants.QL_EPSILON) {
            return 0.0;
        } else if (del > 0.0) {
            return Constants.QL_MAX_REAL;
        } else {
            return Constants.QL_MIN_REAL;
        }
    }

    /**
     * Second order derivative with respect to change in the underlying
     * spot price.
     */
    public double gamma(final double spot) /* @ReadOnly */ {
        if (stdDev <= Constants.QL_EPSILON) {
            return 0.0;
        }
        final double DforwardDs = forward / spot;
        final double gammaFwd = n_d / stdDev;
        return discount * gammaFwd * DforwardDs * DforwardDs;
    }

    /**
     * Second order derivative with respect to change in the underlying
     * forward price.
     */
    public double gammaForward() /* @ReadOnly */ {
        if (stdDev <= Constants.QL_EPSILON) {
            return 0.0;
        }
        return discount * n_d / stdDev;
    }

    /**
     * Sensitivity to time to maturity.
     *
     * <p>Theta = -(ln(discount) * V + ln(F/S) * S * delta + 0.5 * variance * gamma) / maturity.
     */
    public double theta(final double spot, /* @Time */ final double maturity) /* @ReadOnly */ {
        QL.require(maturity >= 0.0, "maturity (" + maturity + ") must be non-negative");
        if (Closeness.isClose(maturity, 0.0)) {
            return 0.0;
        }
        return -(Math.log(discount) * value()
                + Math.log(forward / spot) * spot * delta(spot)
                + 0.5 * variance * gamma(spot)) / maturity;
    }

    /** Sensitivity to time to maturity per day, assuming 365 day per year. */
    public double thetaPerDay(final double spot, /* @Time */ final double maturity) /* @ReadOnly */ {
        return theta(spot, maturity) / 365.0;
    }

    /**
     * Sensitivity to volatility.
     *
     * <p>Bachelier closed-form: {@code Vega = discount * sqrt(T) * n(d)}.
     */
    public double vega(/* @Time */ final double maturity) /* @ReadOnly */ {
        QL.require(maturity >= 0.0, "negative maturity not allowed");
        if (maturity <= Constants.QL_EPSILON || stdDev <= Constants.QL_EPSILON) {
            return 0.0;
        }
        return discount * Math.sqrt(maturity) * n_d;
    }

    /**
     * Sensitivity to discounting rate.
     *
     * <p>{@code rho = T * (deltaForward * F - V)}.
     */
    public double rho(/* @Time */ final double maturity) /* @ReadOnly */ {
        QL.require(maturity >= 0.0, "negative maturity not allowed");
        final double deltaFwd = deltaForward();
        return maturity * (deltaFwd * forward - value());
    }

    /**
     * Sensitivity to dividend/growth rate.
     *
     * <p>{@code dividendRho = -T * discount * deltaForwardRaw * F}, where
     * {@code deltaForwardRaw} is {@code N(d)} for calls and {@code N(d) - 1}
     * for puts (i.e. the bare Bachelier forward delta without the discount).
     */
    public double dividendRho(/* @Time */ final double maturity) /* @ReadOnly */ {
        QL.require(maturity >= 0.0, "negative maturity not allowed");
        final double deltaFwdRaw = (alpha >= 0) ? cum_d : (cum_d - 1.0);
        return -maturity * discount * deltaFwdRaw * forward;
    }

    /**
     * Probability of being in the money in the bond (cash) martingale measure.
     *
     * <p>In the Bachelier model the cash and asset probabilities coincide
     * (no drift adjustment). Returns {@code N(d)} for calls, {@code N(-d)}
     * for puts.
     */
    public double itmCashProbability() /* @ReadOnly */ {
        if (alpha >= 0) {
            return cum_d;
        }
        return 1.0 - cum_d;
    }

    /**
     * Probability of being in the money in the asset martingale measure.
     * Same as {@link #itmCashProbability()} for the Bachelier model.
     */
    public double itmAssetProbability() /* @ReadOnly */ {
        if (alpha >= 0) {
            return cum_d;
        }
        return 1.0 - cum_d;
    }

    /**
     * Sensitivity to strike.
     *
     * <p>{@code dV/dK = -N(d)} for calls, {@code N(-d)} for puts.
     */
    public double strikeSensitivity() /* @ReadOnly */ {
        if (alpha >= 0) {
            return -discount * cum_d;
        }
        return discount * (1.0 - cum_d);
    }

    /**
     * Gamma w.r.t. strike: {@code d^2 V / dK^2 = n(d) / sigma}.
     * Same for calls and puts.
     */
    public double strikeGamma() /* @ReadOnly */ {
        if (stdDev <= Constants.QL_EPSILON) {
            return 0.0;
        }
        return discount * n_d / stdDev;
    }

    /**
     * Sensitivity of vega to forward (Vanna):
     * {@code -d * n(d) * sqrt(T) / sigma}.
     */
    public double vanna(/* @Time */ final double maturity) /* @ReadOnly */ {
        if (maturity <= Constants.QL_EPSILON || stdDev <= Constants.QL_EPSILON) {
            return 0.0;
        }
        return -d * n_d * Math.sqrt(maturity) / stdDev;
    }

    /**
     * Sensitivity of vega to volatility (Volga):
     * {@code (d^2 / sigma) * vega}.
     */
    public double volga(/* @Time */ final double maturity) /* @ReadOnly */ {
        if (maturity <= Constants.QL_EPSILON || stdDev <= Constants.QL_EPSILON) {
            return 0.0;
        }
        return (d * d / stdDev) * vega(maturity);
    }

    public double alpha() /* @ReadOnly */ {
        return alpha;
    }

    public double beta() /* @ReadOnly */ {
        return beta;
    }


    //
    // inner classes
    //

    private static class Calculator implements PolymorphicVisitor {

        private static final String INVALID_OPTION_TYPE = "invalid option type";
        private static final String INVALID_PAYOFF_TYPE = "invalid payoff type";

        private final BachelierCalculator bachelier;

        public Calculator(final BachelierCalculator bachelier) {
            this.bachelier = bachelier;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <Payoff> Visitor<Payoff> visitor(final Class<? extends Payoff> klass) {
            if (klass == PlainVanillaPayoff.class) {
                return (Visitor<Payoff>) plainVanillaPayoffVisitor;
            } else if (klass == CashOrNothingPayoff.class) {
                return (Visitor<Payoff>) cashOrNothingPayoffVisitor;
            } else if (klass == AssetOrNothingPayoff.class) {
                return (Visitor<Payoff>) assetOrNothingPayoffVisitor;
            } else if (klass == GapPayoff.class) {
                return (Visitor<Payoff>) gapPayoffVisitor;
            } else {
                throw new UnsupportedOperationException(INVALID_PAYOFF_TYPE + klass);
            }
        }

        // --- PlainVanillaPayoff: nothing to refine ---
        private final PlainVanillaPayoffVisitor plainVanillaPayoffVisitor = new PlainVanillaPayoffVisitor();

        private static final class PlainVanillaPayoffVisitor implements Visitor<Payoff> {
            @Override
            public void visit(final Payoff o) {
                // nothing — vanilla branch already set in initialize()
            }
        }

        // --- CashOrNothingPayoff ---
        private final CashOrNothingPayoffVisitor cashOrNothingPayoffVisitor = new CashOrNothingPayoffVisitor();

        private final class CashOrNothingPayoffVisitor implements Visitor<Payoff> {
            @Override
            public void visit(final Payoff o) {
                final CashOrNothingPayoff payoff = (CashOrNothingPayoff) o;
                bachelier.alpha = bachelier.DalphaDd = 0.0;
                bachelier.x = payoff.getCashPayoff();
                bachelier.DxDstrike = 0.0;
                final Option.Type optionType = payoff.optionType();
                if (optionType == Option.Type.Call) {
                    bachelier.beta = bachelier.cum_d;
                    bachelier.DbetaDd = bachelier.n_d;
                } else if (optionType == Option.Type.Put) {
                    bachelier.beta = 1.0 - bachelier.cum_d;
                    bachelier.DbetaDd = -bachelier.n_d;
                } else {
                    throw new IllegalArgumentException(INVALID_OPTION_TYPE);
                }
            }
        }

        // --- AssetOrNothingPayoff ---
        private final AssetOrNothingPayoffVisitor assetOrNothingPayoffVisitor = new AssetOrNothingPayoffVisitor();

        private final class AssetOrNothingPayoffVisitor implements Visitor<Payoff> {
            @Override
            public void visit(final Payoff o) {
                final AssetOrNothingPayoff payoff = (AssetOrNothingPayoff) o;
                bachelier.beta = bachelier.DbetaDd = 0.0;
                final Option.Type optionType = payoff.optionType();
                if (optionType == Option.Type.Call) {
                    bachelier.alpha = bachelier.cum_d;
                    bachelier.DalphaDd = bachelier.n_d;
                } else if (optionType == Option.Type.Put) {
                    bachelier.alpha = 1.0 - bachelier.cum_d;
                    bachelier.DalphaDd = -bachelier.n_d;
                } else {
                    throw new IllegalArgumentException(INVALID_OPTION_TYPE);
                }
            }
        }

        // --- GapPayoff ---
        private final GapPayoffVisitor gapPayoffVisitor = new GapPayoffVisitor();

        private final class GapPayoffVisitor implements Visitor<Payoff> {
            @Override
            public void visit(final Payoff o) {
                final GapPayoff payoff = (GapPayoff) o;
                bachelier.x = payoff.getSecondStrike();
                bachelier.DxDstrike = 0.0;
            }
        }
    }
}
