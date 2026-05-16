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
 Copyright (C) 2010 Dimitri Reiswich

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/
package org.jquantlib.pricingengines;

import org.jquantlib.QL;
import org.jquantlib.experimental.fx.DeltaVolQuote;
import org.jquantlib.instruments.Option;
import org.jquantlib.math.Constants;
import org.jquantlib.math.Ops;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.solvers1D.Brent;

/**
 * Black delta calculator class.
 * <p>
 * Java port of QuantLib v1.42.1 {@code ql/pricingengines/blackdeltacalculator.{hpp,cpp}}.
 *
 * <p>The class includes many operations needed for different applications in
 * FX markets, which have special quotation mechanisms, since every price can
 * be expressed in both numeraires. It supports the four delta types from
 * {@link DeltaVolQuote.DeltaType} (Spot, Fwd, PaSpot, PaFwd) and the ATM
 * conventions from {@link DeltaVolQuote.AtmType}.
 *
 * <p>The constructor takes a {@code stdDev} parameter, NOT a volatility.
 * {@code stdDev = volatility * sqrt(timeToMaturity)}.
 */
public class BlackDeltaCalculator {

    //
    // private fields
    //

    private DeltaVolQuote.DeltaType dt;
    private Option.Type ot;
    private final double dDiscount;
    private final double fDiscount;
    private final double stdDev;
    private final double spot;
    private final double forward;
    private int phi;
    private final double fExpPos;
    private final double fExpNeg;


    //
    // public constructors
    //

    /**
     * Constructs a BlackDeltaCalculator object.
     *
     * @param ot         Option type (call or put)
     * @param dt         Delta type (spot, forward, premium-adjusted, etc.)
     * @param spot       Spot FX rate
     * @param dDiscount  Domestic discount factor
     * @param fDiscount  Foreign discount factor
     * @param stdDev     Standard deviation of the underlying, i.e. {@code volatility*sqrt(timeToMaturity)}
     */
    public BlackDeltaCalculator(final Option.Type ot,
                                final DeltaVolQuote.DeltaType dt,
                                final double spot,
                                final double dDiscount,
                                final double fDiscount,
                                final double stdDev) {
        this.dt = dt;
        this.ot = ot;
        this.dDiscount = dDiscount;
        this.fDiscount = fDiscount;
        this.stdDev = stdDev;
        this.spot = spot;
        this.forward = spot * fDiscount / dDiscount;
        this.phi = ot.toInteger();

        QL.require(spot > 0.0,
                "positive spot value required: " + spot + " not allowed");
        QL.require(dDiscount > 0.0,
                "positive domestic discount factor required: " + dDiscount + " not allowed");
        QL.require(fDiscount > 0.0,
                "positive foreign discount factor required: " + fDiscount + " not allowed");
        QL.require(stdDev >= 0.0,
                "non-negative standard deviation required: " + stdDev + " not allowed");

        this.fExpPos = forward * Math.exp(0.5 * stdDev * stdDev);
        this.fExpNeg = forward * Math.exp(-0.5 * stdDev * stdDev);
    }


    //
    // public methods
    //

    /**
     * Computes the option delta for a given strike under the convention set at construction.
     */
    public double deltaFromStrike(final double strike) {

        QL.require(strike >= 0.0,
                "positive strike value required: " + strike + " not allowed");

        double res = 0.0;

        switch (dt) {
        case Spot:
            res = phi * fDiscount * cumD1(strike);
            break;
        case Fwd:
            res = phi * cumD1(strike);
            break;
        case PaSpot:
            res = phi * fDiscount * cumD2(strike) * strike / forward;
            break;
        case PaFwd:
            res = phi * cumD2(strike) * strike / forward;
            break;
        default:
            QL.error("invalid delta type");
        }
        return res;
    }

    /**
     * Computes the strike corresponding to a given delta under the convention set at construction.
     */
    public double strikeFromDelta(final double delta) {
        return strikeFromDelta(delta, dt);
    }

    /**
     * Calculates the at-the-money (ATM) strike for the given ATM convention.
     */
    public double atmStrike(final DeltaVolQuote.AtmType atmT) {

        double res = 0.0;

        switch (atmT) {
        case AtmSpot:
            res = spot;
            break;
        case AtmDeltaNeutral:
            if (dt == DeltaVolQuote.DeltaType.Spot || dt == DeltaVolQuote.DeltaType.Fwd) {
                res = fExpPos;
            } else {
                res = fExpNeg;
            }
            break;
        case AtmFwd:
            res = forward;
            break;
        case AtmGammaMax:
        case AtmVegaMax:
            res = fExpPos;
            break;
        case AtmPutCall50:
            QL.require(dt == DeltaVolQuote.DeltaType.Fwd,
                    "|PutDelta|=CallDelta=0.50 only possible for forward delta.");
            res = fExpPos;
            break;
        default:
            QL.error("invalid atm type");
        }
        return res;
    }

    /**
     * Sets the delta calculation convention.
     */
    public void setDeltaType(final DeltaVolQuote.DeltaType dt) {
        this.dt = dt;
    }

    /**
     * Sets the option type (call or put).
     */
    public void setOptionType(final Option.Type ot) {
        this.ot = ot;
        this.phi = ot.toInteger();
    }


    //
    // package-private deprecated-internal accessors (C++ marks them deprecated;
    // kept package-private for the same role they play in C++).
    //

    /** {@code N(d1)} or {@code N(-d1)} according to option phi. */
    double cumD1(final double strike) {

        double cumD1Pos = 1.0; // N(d1)
        double cumD1Neg = 0.0; // N(-d1)

        final CumulativeNormalDistribution f = new CumulativeNormalDistribution();

        if (stdDev >= Constants.QL_EPSILON) {
            if (strike > 0) {
                final double d1 = Math.log(forward / strike) / stdDev + 0.5 * stdDev;
                return f.op(phi * d1);
            }
        } else {
            if (forward < strike) {
                cumD1Pos = 0.0;
                cumD1Neg = 1.0;
            } else if (forward == strike) {
                final double d1 = 0.5 * stdDev;
                return f.op(phi * d1);
            }
        }

        if (phi > 0) { // if Call
            return cumD1Pos;
        }
        return cumD1Neg;
    }

    /** {@code N(d2)} or {@code N(-d2)} according to option phi. */
    double cumD2(final double strike) {

        double cumD2Pos = 1.0; // N(d2)
        double cumD2Neg = 0.0; // N(-d2)

        final CumulativeNormalDistribution f = new CumulativeNormalDistribution();

        if (stdDev >= Constants.QL_EPSILON) {
            if (strike > 0) {
                final double d2 = Math.log(forward / strike) / stdDev - 0.5 * stdDev;
                return f.op(phi * d2);
            }
        } else {
            if (forward < strike) {
                cumD2Pos = 0.0;
                cumD2Neg = 1.0;
            } else if (forward == strike) {
                final double d2 = -0.5 * stdDev;
                return f.op(phi * d2);
            }
        }

        if (phi > 0) { // if Call
            return cumD2Pos;
        }
        return cumD2Neg;
    }

    /** {@code n(d1)} — derivative of {@code N} at {@code d1}. */
    double nD1(final double strike) {

        double nD1 = 0.0;

        if (stdDev >= Constants.QL_EPSILON) {
            if (strike > 0) {
                final double d1 = Math.log(forward / strike) / stdDev + 0.5 * stdDev;
                final CumulativeNormalDistribution f = new CumulativeNormalDistribution();
                nD1 = f.derivative(d1);
            }
        }
        return nD1;
    }

    /** {@code n(d2)} — derivative of {@code N} at {@code d2}. */
    double nD2(final double strike) {

        double nD2 = 0.0;

        if (stdDev >= Constants.QL_EPSILON) {
            if (strike > 0) {
                final double d2 = Math.log(forward / strike) / stdDev - 0.5 * stdDev;
                final CumulativeNormalDistribution f = new CumulativeNormalDistribution();
                nD2 = f.derivative(d2);
            }
        }
        return nD2;
    }


    //
    // private methods
    //

    /**
     * Alternative delta-type variant of {@link #strikeFromDelta(double)}.
     */
    private double strikeFromDelta(final double delta, final DeltaVolQuote.DeltaType dtArg) {
        double res = 0.0;
        double arg;
        final InverseCumulativeNormal invNorm = new InverseCumulativeNormal();

        QL.require(delta * phi >= 0.0, "Option type and delta are incoherent.");

        switch (dtArg) {
        case Spot:
            QL.require(Math.abs(delta) <= fDiscount, "Spot delta out of range.");
            arg = -phi * invNorm.op(phi * delta / fDiscount) * stdDev + 0.5 * stdDev * stdDev;
            res = forward * Math.exp(arg);
            break;

        case Fwd:
            QL.require(Math.abs(delta) <= 1.0, "Forward delta out of range.");
            arg = -phi * invNorm.op(phi * delta) * stdDev + 0.5 * stdDev * stdDev;
            res = forward * Math.exp(arg);
            break;

        case PaSpot:
        case PaFwd: {
            // This has to be solved numerically. One of the problems is that
            // the premium adjusted call delta is not monotonic in strike,
            // such that two solutions might occur. The one right to the max
            // of the delta is considered to be the correct strike. Some
            // proper interval bounds for the strike need to be chosen, the
            // numerics can otherwise be very unreliable and unstable. Brent
            // is preferred over Newton, since the interval can be specified
            // explicitly and the area on the left of the maximum can be
            // avoided. The put delta doesn't have this property and can be
            // solved without any problems, but also numerically.
            final Ops.DoubleOp f = new Ops.DoubleOp() {
                @Override
                public double op(final double strike) {
                    return deltaFromStrike(strike) - delta;
                }
            };

            final Brent solver = new Brent();
            solver.setMaxEvaluations(1000);
            final double accuracy = 1.0e-10;

            final double rightLimit;
            double leftLimit = 0.0;

            // Strike of non-premium-adjusted is always to the right of premium-adjusted
            if (dtArg == DeltaVolQuote.DeltaType.PaSpot) {
                rightLimit = strikeFromDelta(delta, DeltaVolQuote.DeltaType.Spot);
            } else {
                rightLimit = strikeFromDelta(delta, DeltaVolQuote.DeltaType.Fwd);
            }

            if (phi < 0) { // if put
                res = solver.solve(f, accuracy, rightLimit, 0.0, spot * 100.0);
                break;
            } else {

                // find out the left limit which is the strike corresponding
                // to the value where premium adjusted deltas have their
                // maximum
                final Ops.DoubleOp g = new Ops.DoubleOp() {
                    @Override
                    public double op(final double strike) {
                        return cumD2(strike) * stdDev - nD2(strike);
                    }
                };

                // Use a fresh solver for the inner bracket — Brent stores
                // per-solve state.
                final Brent solverMax = new Brent();
                solverMax.setMaxEvaluations(1000);
                leftLimit = solverMax.solve(g, accuracy, rightLimit * 0.5, 0.0, rightLimit);

                final double guess = leftLimit + (rightLimit - leftLimit) * 0.5;

                final Brent solverRoot = new Brent();
                solverRoot.setMaxEvaluations(1000);
                res = solverRoot.solve(f, accuracy, guess, leftLimit, rightLimit);
            }
            break;
        }
        default:
            QL.error("invalid delta type");
        }

        return res;
    }
}
