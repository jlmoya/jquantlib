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
 Copyright (C) 2008 Mark Joshi
*/

package org.jquantlib.model.marketmodels.evolvers.volprocesses;

import org.jquantlib.QL;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.model.marketmodels.evolvers.MarketModelVolProcess;

/**
 * Andersen-style square-root vol process.
 * <p>
 * Implements Andersen's QE / TG hybrid scheme for evolving an instantaneous
 * variance under the CIR/Heston dynamics. Used as the {@code volProcess_}
 * input of {@link org.jquantlib.model.marketmodels.evolvers.SVDDFwdRatePc}.
 *
 * @see "ql/models/marketmodels/evolvers/volprocesses/squarerootandersen.{hpp,cpp}" v1.42.1
 *
 * @author Jose Moya
 */
public class SquareRootAndersen extends MarketModelVolProcess {

    private final double theta_;       // mean level
    private final double k_;           // reversion speed
    private final double epsilon_;     // volvar
    private final double v0_;          // initial variance
    private final int numberSubSteps_; // sub steps per evolution time

    private final double[] dt_;        // time step lengths
    private final double[] eMinuskDt_; // exp(-k*dt)

    private final double w1_;          // variance weights across step
    private final double w2_;
    private final double psiC_;        // cut-off between two evolution regimes

    // evolving values
    private double v_;
    private int currentStep_;
    private int subStep_;
    private final double[] vPath_;
    private final double[] state_;

    private static final CumulativeNormalDistribution PHI = new CumulativeNormalDistribution();

    public SquareRootAndersen(final double meanLevel,
                              final double reversionSpeed,
                              final double volVar,
                              final double v0,
                              final double[] evolutionTimes,
                              final int numberSubSteps,
                              final double w1,
                              final double w2) {
        this(meanLevel, reversionSpeed, volVar, v0, evolutionTimes, numberSubSteps, w1, w2, 1.5);
    }

    public SquareRootAndersen(final double meanLevel,
                              final double reversionSpeed,
                              final double volVar,
                              final double v0,
                              final double[] evolutionTimes,
                              final int numberSubSteps,
                              final double w1,
                              final double w2,
                              final double cutPoint) {
        this.theta_ = meanLevel;
        this.k_ = reversionSpeed;
        this.epsilon_ = volVar;
        this.v0_ = v0;
        this.numberSubSteps_ = numberSubSteps;
        this.dt_ = new double[evolutionTimes.length * numberSubSteps];
        this.eMinuskDt_ = new double[evolutionTimes.length * numberSubSteps];
        this.w1_ = w1;
        this.w2_ = w2;
        this.psiC_ = cutPoint;
        this.vPath_ = new double[evolutionTimes.length * numberSubSteps + 1];
        this.state_ = new double[1];

        int j = 0;
        for (; j < numberSubSteps_; ++j) {
            dt_[j] = evolutionTimes[0] / numberSubSteps_;
        }

        for (int i = 1; i < evolutionTimes.length; ++i) {
            final double dt = (evolutionTimes[i] - evolutionTimes[i - 1]) / numberSubSteps_;
            final double ekdt = Math.exp(-k_ * dt);
            QL.require(dt > 0.0, "Steps must be of positive size.");

            for (int kk = 0; kk < numberSubSteps_; ++kk) {
                dt_[j] = dt;
                eMinuskDt_[j] = ekdt;
                ++j;
            }
        }
        vPath_[0] = v0_;
    }

    @Override
    public int variatesPerStep() {
        return numberSubSteps_;
    }

    @Override
    public int numberSteps() {
        return dt_.length * numberSubSteps_;
    }

    @Override
    public void nextPath() {
        v_ = v0_;
        currentStep_ = 0;
        subStep_ = 0;
    }

    private double doOneSubStep(final double vt0, final double z, final int j) {
        double vt = vt0;
        final double eminuskT = eMinuskDt_[j];
        final double m = theta_ + (vt - theta_) * eminuskT;
        final double s2 = vt * epsilon_ * epsilon_ * eminuskT * (1 - eminuskT) / k_
                + theta_ * epsilon_ * epsilon_ * (1 - eminuskT) * (1 - eminuskT) / (2 * k_);
        final double s = Math.sqrt(s2);
        final double psi = s * s / (m * m);
        if (psi <= psiC_) {
            final double psiinv = 1.0 / psi;
            final double b2 = 2.0 * psiinv - 1 + Math.sqrt(2 * psiinv * (2 * psiinv - 1.0));
            final double b = Math.sqrt(b2);
            final double a = m / (1 + b2);
            vt = a * (b + z) * (b + z);
        } else {
            final double p = (psi - 1.0) / (psi + 1.0);
            final double beta = (1.0 - p) / m;
            final double u = PHI.op(z);

            if (u < p) {
                vt = 0.0;
                return vt;
            }

            vt = Math.log((1.0 - p) / (1.0 - u)) / beta;
        }
        return vt;
    }

    @Override
    public double nextstep(final double[] variates) {
        for (int j = 0; j < numberSubSteps_; ++j) {
            v_ = doOneSubStep(v_, variates[j], subStep_);
            ++subStep_;
            vPath_[subStep_] = v_;
        }

        ++currentStep_;

        return 1.0; // no importance sampling here
    }

    @Override
    public double stepSd() {
        QL.require(currentStep_ > 0, "nextStep must be called before stepSd");
        double stepVariance = 0.0;
        final int lastStepStart = (currentStep_ - 1) * numberSubSteps_;
        for (int kk = 0; kk < numberSubSteps_; ++kk) {
            stepVariance += w1_ * vPath_[kk + lastStepStart] + w2_ * vPath_[kk + lastStepStart + 1];
        }

        stepVariance /= numberSubSteps_;

        return Math.sqrt(stepVariance);
    }

    @Override
    public double[] stateVariables() {
        state_[0] = v_;
        return state_;
    }

    @Override
    public int numberStateVariables() {
        return 1;
    }
}
