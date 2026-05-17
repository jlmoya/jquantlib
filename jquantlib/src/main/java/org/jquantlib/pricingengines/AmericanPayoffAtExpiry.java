/*
 Copyright (C) 2009 Jose Coll
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.
 */

/*
 Copyright (C) 2004 Ferdinando Ametrano
 */

package org.jquantlib.pricingengines;

import org.jquantlib.QL;
import org.jquantlib.instruments.AssetOrNothingPayoff;
import org.jquantlib.instruments.CashOrNothingPayoff;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.math.Constants;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;

/**
 * Analytical formula for American exercise with payoff at expiry.
 *
 * <p>Java port of QuantLib v1.42.1 {@code AmericanPayoffAtExpiry} —
 * implements both knock-in and knock-out branches, exactly mirroring the
 * C++ reference (sign-flipped d1/d2 via eta/phi, {@code Y *= -1} for
 * knock-out).
 *
 * <p>Replaces the 2009 Jose Coll port (knock-in only).
 *
 * @author Jose Coll (original 2009 port)
 * @author JQuantLib migration (C++ v1.42.1 port, 2026)
 */
public class AmericanPayoffAtExpiry {

    private final double spot;
    private final double discount;
    private final double dividendDiscount;
    private final double variance;
    private final double forward;
    private final double stdDev;
    private final double strike;
    private double K;
    private double mu;
    private final double log_H_S;
    private double D1, D2;
    private double cum_d1, cum_d2;
    private double n_d1, n_d2;
    private final boolean inTheMoney;
    private double X, Y;
    private final boolean knock_in;

    public AmericanPayoffAtExpiry(
            final double spot, final double discount,
            final double dividendDiscount, final double variance,
            final StrikedTypePayoff strikedTypePayoff) {
        this(spot, discount, dividendDiscount, variance, strikedTypePayoff, true);
    }

    public AmericanPayoffAtExpiry(
            final double spot, final double discount,
            final double dividendDiscount, final double variance,
            final StrikedTypePayoff strikedTypePayoff,
            final boolean knock_in) {

        QL.require(spot > 0.0, "positive spot value required");
        QL.require(discount > 0.0, "positive discount required");
        QL.require(dividendDiscount > 0.0, "positive dividend discount required");
        QL.require(variance >= 0.0, "negative variance not allowed");

        this.spot = spot;
        this.discount = discount;
        this.dividendDiscount = dividendDiscount;
        this.variance = variance;
        this.knock_in = knock_in;
        this.stdDev = Math.sqrt(variance);

        final Option.Type type = strikedTypePayoff.optionType();
        this.strike = strikedTypePayoff.strike();
        this.forward = spot * dividendDiscount / discount;

        this.mu = Math.log(dividendDiscount / discount) / variance - 0.5;

        if (strikedTypePayoff instanceof CashOrNothingPayoff) {
            this.K = ((CashOrNothingPayoff) strikedTypePayoff).getCashPayoff();
        }
        if (strikedTypePayoff instanceof AssetOrNothingPayoff) {
            this.K = this.forward;
            this.mu += 1.0;
        }

        this.log_H_S = Math.log(strike / spot);
        final double log_S_H = Math.log(spot / strike);

        final double eta;
        final double phi;
        switch (type) {
            case Call:
                if (knock_in) { eta = -1.0; phi =  1.0; }
                else          { eta = -1.0; phi = -1.0; }
                break;
            case Put:
                if (knock_in) { eta =  1.0; phi = -1.0; }
                else          { eta =  1.0; phi =  1.0; }
                break;
            default:
                throw new IllegalArgumentException("invalid option type");
        }

        if (variance >= Constants.QL_EPSILON) {
            this.D1 = phi * (log_S_H / stdDev + mu * stdDev);
            this.D2 = eta * (log_H_S / stdDev + mu * stdDev);
            final CumulativeNormalDistribution f = new CumulativeNormalDistribution();
            this.cum_d1 = f.op(D1);
            this.cum_d2 = f.op(D2);
            this.n_d1 = f.derivative(D1);
            this.n_d2 = f.derivative(D2);
        } else {
            this.cum_d1 = (log_S_H * phi > 0) ? 1.0 : 0.0;
            this.cum_d2 = (log_H_S * eta > 0) ? 1.0 : 0.0;
            this.n_d1 = 0.0;
            this.n_d2 = 0.0;
        }

        switch (type) {
            case Call:
                if (strike <= spot) {
                    if (knock_in) { this.cum_d1 = 0.5; this.cum_d2 = 0.5; }
                    else          { this.cum_d1 = 0.0; this.cum_d2 = 0.0; }
                    this.n_d1 = 0.0;
                    this.n_d2 = 0.0;
                }
                break;
            case Put:
                if (strike >= spot) {
                    if (knock_in) { this.cum_d1 = 0.5; this.cum_d2 = 0.5; }
                    else          { this.cum_d1 = 0.0; this.cum_d2 = 0.0; }
                    this.n_d1 = 0.0;
                    this.n_d2 = 0.0;
                }
                break;
            default:
                throw new IllegalArgumentException("invalid option type");
        }

        this.inTheMoney = (type == Option.Type.Call && strike < spot)
                || (type == Option.Type.Put && strike > spot);
        if (inTheMoney) {
            this.X = 1.0;
            this.Y = 1.0;
        } else {
            this.X = 1.0;
            this.Y = (cum_d2 == 0.0) ? 0.0 : Math.pow(strike / spot, 2.0 * mu);
        }
        if (!knock_in) {
            this.Y *= -1.0;
        }
    }

    public double value() {
        return discount * K * (X * cum_d1 + Y * cum_d2);
    }

}
