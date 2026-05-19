/*
 Copyright (C) 2026 Jose Moya

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
 Copyright (C) 2014 Bernd Lewerenz

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.
 */

package org.jquantlib.experimental.exoticoptions;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.*;
import org.jquantlib.math.Rounding;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.finitedifferences.TridiagonalOperator;
import org.jquantlib.pricingengines.AnalyticEuropeanEngine;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;

/**
 * Vecer engine for continuous-averaging arithmetic Asian options.
 *
 * <p>See <a href="http://www.stat.columbia.edu/~vecer/asian-vecer.pdf">
 * Vecer (2001), Unified Asian pricing</a>.</p>
 *
 * <p>Ported from C++ QuantLib v1.42.1 ql/experimental/exoticoptions/
 * continuousarithmeticasianvecerengine.{hpp,cpp}.</p>
 *
 * @author Jose Moya
 */
public class ContinuousArithmeticAsianVecerEngine extends ContinuousAveragingAsianOption.EngineImpl {

    private static final String NOT_AN_ARITHMETIC_AVERAGE = "not an Arithmetic average option";
    private static final String NOT_AN_EUROPEAN_OPTION = "not an European Option";
    private static final String STRIKE_NOT_ON_GRID = "strike (0 for vecer fixed strike asian)  not on Grid";
    private static final String NON_PLAIN_PAYOFF = "non-plain payoff given";
    private static final String SEASONED_NOT_IMPLEMENTED = "Seasoned Asian not yet implemented";
    private static final String SPOT_NOT_ON_GRID = "spot not on grid";
    private static final String AVG_START_BEFORE_END = "Average Start must be before Average End";

    private final GeneralizedBlackScholesProcess process_;
    private final Handle< ? extends Quote > currentAverage_;
    private final Date startDate_;
    private final double z_min_;
    private final double z_max_;
    private final int timeSteps_;
    private final int assetSteps_;

    public ContinuousArithmeticAsianVecerEngine(final GeneralizedBlackScholesProcess process,
            final Handle< ? extends Quote > currentAverage, final Date startDate, final int timeSteps,
            final int assetSteps, final double z_min, final double z_max) {
        this.process_ = process;
        this.currentAverage_ = currentAverage;
        this.startDate_ = startDate;
        this.timeSteps_ = timeSteps;
        this.assetSteps_ = assetSteps;
        this.z_min_ = z_min;
        this.z_max_ = z_max;
        process_.addObserver(this);
        currentAverage_.addObserver(this);
    }

    /** Convenience overload mirroring the C++ default arguments. */
    public ContinuousArithmeticAsianVecerEngine(final GeneralizedBlackScholesProcess process,
            final Handle< ? extends Quote > currentAverage, final Date startDate) {
        this(process, currentAverage, startDate, 100, 100, -1.0, 1.0);
    }

    @Override
    public void calculate() /* @ReadOnly */ {
        QL.require(arguments_.averageType == AverageType.Arithmetic, NOT_AN_ARITHMETIC_AVERAGE);
        QL.require(arguments_.exercise.type() == Exercise.Type.European, NOT_AN_EUROPEAN_OPTION);

        final DayCounter rfdc = process_.riskFreeRate().currentLink().dayCounter();
        // divdc / voldc kept as in C++ (computed but not used downstream after refactor)
        final double S_0 = process_.stateVariable().currentLink().value();

        // payoff
        QL.require(arguments_.payoff instanceof StrikedTypePayoff, NON_PLAIN_PAYOFF);
        final StrikedTypePayoff payoff = (StrikedTypePayoff) arguments_.payoff;

        final Date maturity = arguments_.exercise.lastDate();

        final double X = payoff.strike();
        QL.require(z_min_ <= 0 && z_max_ >= 0, STRIKE_NOT_ON_GRID);

        final double sigma = process_.blackVolatility().currentLink().blackVol(maturity, X);

        final double r = process_.riskFreeRate().currentLink()
                .zeroRate(maturity, rfdc, Compounding.Continuous, Frequency.NoFrequency).rate();
        final double q = process_.dividendYield().currentLink()
                .zeroRate(maturity, rfdc, Compounding.Continuous, Frequency.NoFrequency).rate();

        final Date today = new Settings().evaluationDate();

        QL.require(startDate_.ge(today), SEASONED_NOT_IMPLEMENTED);

        // expiry in years
        final double T = rfdc.yearFraction(today, maturity);
        final double T1 = rfdc.yearFraction(today, startDate_);   // average begin
        final double T2 = T;                                      // average end (only maturity supported)

        if ( (T2 - T1) < 0.001 ) {
            // it's a vanilla option — use vanilla engine
            final VanillaOption europeanOption = new VanillaOption(payoff, arguments_.exercise);
            europeanOption.setPricingEngine(new AnalyticEuropeanEngine(process_));
            results_.value = europeanOption.NPV();
            return;
        }

        final double Theta = 0.5; // mixed scheme: 0.5 = Crank-Nicolson
        final double Z_0 = cont_strategy(0.0, T1, T2, q, r) - Math.exp(-r * T) * X / S_0;

        QL.require(Z_0 >= z_min_ && Z_0 <= z_max_, SPOT_NOT_ON_GRID);

        final double h = (z_max_ - z_min_) / assetSteps_; // space step size
        final double k = T / timeSteps_;                  // time step size

        final double sigma2 = sigma * sigma;

        final int gridSize = assetSteps_ + 1;
        final Array SVec = new Array(gridSize);
        final Array u_initial = new Array(gridSize);
        Array u = new Array(gridSize);
        Array rhs = new Array(gridSize);

        for ( int i = 0; i < gridSize; i++ ) {
            SVec.set(i, z_min_ + i * h); // value of underlying on the grid
        }

        // begin gamma construction
        final TridiagonalOperator gammaOp = new TridiagonalOperator(gridSize);
        gammaOp.setFirstRow(0.0, 0.0);
        gammaOp.setMidRows(1.0 / (h * h), -2.0 / (h * h), 1.0 / (h * h));
        gammaOp.setLastRow(0.0, 0.0);

        // Deep copies — C++ {@code Array} has value semantics; Java's
        // {@code TridiagonalOperator.upperDiagonal()} returns a reference to
        // the internal Array. Without copying, subsequent setMidRow calls
        // (which mutate gammaOp's internals) would corrupt the constant
        // gamma operator we need to keep around.
        final Array upperD = new Array(gammaOp.upperDiagonal());
        final Array lowerD = new Array(gammaOp.lowerDiagonal());
        final Array Dia = new Array(gammaOp.diagonal());

        for ( int i = 0; i < gridSize; i++ ) {
            u_initial.set(i, Math.max(SVec.get(i), 0.0)); // call payoff
        }

        // u = u_initial (deep copy)
        u = new Array(gridSize);
        for ( int i = 0; i < gridSize; i++ ) {
            u.set(i, u_initial.get(i));
        }

        // start time loop
        for ( int j = 1; j <= timeSteps_; j++ ) {
            if ( Theta != 1.0 ) { // explicit part
                for ( int i = 1; i <= gridSize - 2; i++ ) {
                    final double vecerTerm =
                            SVec.get(i) - Math.exp(-q * (T - (j - 1) * k)) * cont_strategy(T - (j - 1) * k, T1, T2, q,
                                    r);
                    final double vt2 = vecerTerm * vecerTerm;
                    gammaOp.setMidRow(i, 0.5 * sigma2 * vt2 * lowerD.get(i - 1), 0.5 * sigma2 * vt2 * Dia.get(i),
                            0.5 * sigma2 * vt2 * upperD.get(i));
                }
                // explicit_part = I + (1 - Theta) * k * gammaOp
                final TridiagonalOperator identity = gammaOp.identity(gridSize);
                final TridiagonalOperator scaled = (TridiagonalOperator) gammaOp.multiply((1 - Theta) * k);
                TridiagonalOperator explicit_part = (TridiagonalOperator) identity.add(scaled);
                explicit_part.setFirstRow(1.0, 0.0); // apply before applying
                explicit_part.setLastRow(-1.0, 1.0); // Neumann BC

                u = explicit_part.applyTo(u);

                // apply after applying (Neumann BC)
                u.set(assetSteps_, u.get(assetSteps_ - 1) + h);
                u.set(0, 0.0);
            } // end explicit part

            if ( Theta != 0.0 ) { // implicit part
                for ( int i = 1; i <= gridSize - 2; i++ ) {
                    final double vecerTerm =
                            SVec.get(i) - Math.exp(-q * (T - j * k)) * cont_strategy(T - j * k, T1, T2, q, r);
                    final double vt2 = vecerTerm * vecerTerm;
                    gammaOp.setMidRow(i, 0.5 * sigma2 * vt2 * lowerD.get(i - 1), 0.5 * sigma2 * vt2 * Dia.get(i),
                            0.5 * sigma2 * vt2 * upperD.get(i));
                }
                // implicit_part = I - Theta * k * gammaOp
                final TridiagonalOperator identity = gammaOp.identity(gridSize);
                final TridiagonalOperator scaled = (TridiagonalOperator) gammaOp.multiply(Theta * k);
                TridiagonalOperator implicit_part = (TridiagonalOperator) identity.subtract(scaled);

                implicit_part.setFirstRow(1.0, 0.0);
                implicit_part.setLastRow(-1.0, 1.0);

                rhs = new Array(gridSize);
                for ( int i = 0; i < gridSize; i++ ) {
                    rhs.set(i, u.get(i));
                }
                rhs.set(0, 0.0);          // lower BC
                rhs.set(assetSteps_, h);  // upper BC (Neumann) Delta = 1
                u = implicit_part.solveFor(rhs);
            } // end implicit part
        } // end time loop

        final Rounding.DownRounding rounding = new Rounding.DownRounding(0);
        final int lowerI = (int) rounding.operator((Z_0 - z_min_) / h);

        // interpolate solution
        final double pv = u.get(lowerI) + (u.get(lowerI + 1) - u.get(lowerI)) * (Z_0 - SVec.get(lowerI)) / h;
        results_.value = S_0 * pv;

        if ( payoff.optionType() == Option.Type.Put ) {
            // apply call-put parity for Asians
            final double expectedAverage;
            if ( r == q ) {
                expectedAverage = S_0;
            } else {
                expectedAverage = S_0 * (Math.exp((r - q) * T2) - Math.exp((r - q) * T1)) / ((r - q) * (T2 - T1));
            }
            final double asianForward = Math.exp(-r * T2) * (expectedAverage - X);
            results_.value = results_.value - asianForward;
        }
    }

    /**
     * Replication of average by holding this amount in assets.
     */
    protected double cont_strategy(final double t, final double T1, final double T2, final double v, final double r) {
        final double eps = 0.00001;
        QL.require(T1 <= T2, AVG_START_BEFORE_END);

        if ( Math.abs(t - T2) < eps ) {
            return 0.0;
        }
        if ( t < T1 ) {
            if ( Math.abs(r - v) >= eps ) {
                return Math.exp(v * (t - T2)) * (1.0 - Math.exp((v - r) * (T2 - T1))) / ((r - v) * (T2 - T1));
            }
            return Math.exp(v * (t - T2));
        }
        // t >= T1
        if ( Math.abs(r - v) >= eps ) {
            return Math.exp(v * (t - T2)) * (1.0 - Math.exp((v - r) * (T2 - t))) / ((r - v) * (T2 - T1));
        }
        return Math.exp(v * (t - T2)) * (T2 - t) / (T2 - T1);
    }
}
