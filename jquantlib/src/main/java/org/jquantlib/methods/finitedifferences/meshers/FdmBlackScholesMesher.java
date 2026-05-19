/*
 Copyright (C) 2009 Klaus Spanderen

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.methods.finitedifferences.meshers;

import org.jquantlib.QL;
import org.jquantlib.instruments.DividendSchedule;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.transcendental.JQuantMath;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.BlackConstantVol;
import org.jquantlib.time.calendars.NullCalendar;

import java.util.ArrayList;
import java.util.List;

/**
 * One-dimensional FDM mesher for the Black-Scholes equity process (log-space).
 * <p>
 * Java port of v1.42.1 {@code ql/methods/finitedifferences/meshers/fdmblackscholesmesher.{hpp,cpp}}.
 * <p>
 * The mesh is built in log(S) space. Forward prices are propagated through the dividend schedule (Spot model only —
 * Escrowed model deferred to Phase 2m.5) to derive the global min and max of the spot path. The grid boundaries are
 * then set to {@code log(min/max) ± sigmaSqrtT * normInvEps * scaleFactor}. The strike is used as an optional
 * concentration point (sinh mapping via {@link Concentrating1dMesher}).
 *
 * <h3>Quantified deviations from C++</h3>
 * <ul>
 *   <li>FdmQuantoHelper is not supported (Quanto option support deferred).</li>
 *   <li>{@code xMinConstraint}/{@code xMaxConstraint} support is included.</li>
 * </ul>
 *
 * @author Phase 2m Track A port
 */
public class FdmBlackScholesMesher extends Fdm1dMesher {

    /**
     * Full constructor mirroring C++ v1.42.1 {@code FdmBlackScholesMesher::FdmBlackScholesMesher}.
     *
     * @param size             number of grid points
     * @param process          GBS process (provides spot, r, q, vol)
     * @param maturity         option maturity in years
     * @param strike           option strike (used as concentration point)
     * @param xMinConstraint   lower log-space bound override (NaN = computed)
     * @param xMaxConstraint   upper log-space bound override (NaN = computed)
     * @param eps              tail percentile for boundary computation (e.g. 0.0001)
     * @param scaleFactor      boundary scaling factor (e.g. 1.5)
     * @param cPointValue      concentration point value (strike); NaN = no concentration
     * @param cPointDensity    concentration density (e.g. 0.1); NaN = no concentration
     * @param dividendSchedule discrete cash dividends (Spot model)
     * @param spotAdjustment   additive spot adjustment for Escrowed model (0.0 for Spot)
     */
    public FdmBlackScholesMesher(final int size, final GeneralizedBlackScholesProcess process, final double maturity,
            final double strike, final double xMinConstraint, final double xMaxConstraint, final double eps,
            final double scaleFactor, final double cPointValue, final double cPointDensity,
            final DividendSchedule dividendSchedule, final double spotAdjustment) {

        super(size);

        final double S = process.x0();
        QL.require(S > 0.0, "negative or null underlying given");

        // Collect (time, amount) pairs from dividends + intermediate time steps.
        // Mirrors C++ intermediateSteps logic.
        final List< double[] > intermediateSteps = new ArrayList<>();

        // dividend events
        final YieldTermStructure rTS = process.riskFreeRate().currentLink();
        if ( dividendSchedule != null ) {
            for ( final org.jquantlib.cashflow.Dividend div : dividendSchedule ) {
                final double t = process.time(div.date());
                if ( t >= 0.0 && t <= maturity ) {
                    intermediateSteps.add(new double[] { t, div.amount() });
                }
            }
        }

        // intermediate non-dividend time steps for forward propagation
        final int intermediateTimeSteps = Math.max(2, (int) (24.0 * maturity));
        for ( int i = 0; i < intermediateTimeSteps; ++i ) {
            intermediateSteps.add(new double[] { (i + 1) * maturity / intermediateTimeSteps, 0.0 });
        }

        // sort by time
        intermediateSteps.sort((a, b) -> Double.compare(a[0], b[0]));

        final YieldTermStructure qTS = process.dividendYield().currentLink();

        // propagate forward price to track min and max
        double lastDivTime = 0.0;
        double fwd = S + spotAdjustment;
        double mi = fwd;
        double ma = fwd;

        for ( final double[] step : intermediateSteps ) {
            final double divTime = step[0];
            final double divAmount = step[1];

            fwd = fwd / rTS.discount(divTime) * rTS.discount(lastDivTime) * qTS.discount(divTime) / qTS.discount(
                    lastDivTime);

            mi = Math.min(mi, fwd);
            ma = Math.max(ma, fwd);

            fwd -= divAmount;

            mi = Math.min(mi, fwd);
            ma = Math.max(ma, fwd);

            lastDivTime = divTime;
        }

        // grid boundaries in log-space
        final InverseCumulativeNormal icn = new InverseCumulativeNormal();
        final double normInvEps = icn.op(1.0 - eps);
        final double vol = process.blackVolatility().currentLink().blackVol(maturity, strike, true);
        final double sigmaSqrtT = vol * Math.sqrt(maturity);

        double xMin = JQuantMath.log(mi) - sigmaSqrtT * normInvEps * scaleFactor;
        double xMax = JQuantMath.log(ma) + sigmaSqrtT * normInvEps * scaleFactor;

        if ( !Double.isNaN(xMinConstraint) ) {
            xMin = xMinConstraint;
        }
        if ( !Double.isNaN(xMaxConstraint) ) {
            xMax = xMaxConstraint;
        }

        // build 1D helper (concentrating or uniform)
        final Fdm1dMesher helper;
        final double logCPoint = Double.isNaN(cPointValue) ? Double.NaN : JQuantMath.log(cPointValue);

        if ( !Double.isNaN(logCPoint) && logCPoint >= xMin && logCPoint <= xMax ) {
            helper = new Concentrating1dMesher(xMin, xMax, size, logCPoint, cPointDensity);
        } else {
            helper = new Uniform1dMesher(xMin, xMax, size);
        }

        // copy from helper
        System.arraycopy(helper.locations, 0, locations, 0, size);
        System.arraycopy(helper.dplus, 0, dplus, 0, size);
        System.arraycopy(helper.dminus, 0, dminus, 0, size);
    }

    /**
     * Convenience constructor matching C++ call from {@code FdBlackScholesVanillaEngine::calculate}. Forwards
     * concentration point as {@code (strike, 0.1)}.
     */
    public FdmBlackScholesMesher(final int size, final GeneralizedBlackScholesProcess process, final double maturity,
            final double strike, final DividendSchedule dividendSchedule, final double spotAdjustment) {
        this(size, process, maturity, strike, Double.NaN, Double.NaN,   // no min/max constraint
                0.0001, 1.5,              // eps, scaleFactor
                strike, 0.1,             // cPoint (strike, density=0.1)
                dividendSchedule, spotAdjustment);
    }

    /**
     * Build a constant-volatility GBS process — mirrors C++ {@code FdmBlackScholesMesher::processHelper}. Used by
     * hybrid engines (e.g., {@code FdHestonHullWhiteVanillaEngine}) to turn a Heston process's spot/rate handles into a
     * dummy GBS process for building the equity mesh.
     *
     * @param s0  spot quote handle
     * @param rTS risk-free rate term structure
     * @param qTS dividend yield term structure
     * @param vol constant Black-Scholes volatility
     */
    public static GeneralizedBlackScholesProcess processHelper(final Handle< Quote > s0,
            final Handle< YieldTermStructure > rTS, final Handle< YieldTermStructure > qTS, final double vol) {
        final YieldTermStructure rTSLink = rTS.currentLink();
        final Handle< BlackVolTermStructure > volH = new Handle< BlackVolTermStructure >(
                new BlackConstantVol(rTSLink.referenceDate(), new NullCalendar(), vol, rTSLink.dayCounter()));
        return new GeneralizedBlackScholesProcess(s0, qTS, rTS, volH);
    }
}
