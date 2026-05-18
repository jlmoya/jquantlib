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
 Copyright (C) 2008 Yee Man Chan

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.pricingengines.vanilla;

import org.jquantlib.QL;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.Constants;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.model.equity.GjrGarchModel;
import org.jquantlib.pricingengines.GenericModelEngine;
import org.jquantlib.processes.GjrGarchProcess;
import org.jquantlib.time.Date;

/**
 * Analytic GJR-GARCH(1,1) Edgeworth-expansion engine for European vanilla
 * options.
 *
 * <p>Faithful Java port of C++ QuantLib v1.42.1
 * {@code ql/pricingengines/vanilla/analyticgjrgarchengine.{hpp,cpp}}.
 *
 * <p>Reference: Jin-Chuan Duan, Genevieve Gauthier, Jean-Guy Simonato,
 * Caroline Sasseville (2006). <i>Approximating the GJR-GARCH and EGARCH
 * option pricing models analytically.</i> Journal of Computational
 * Finance, Volume 9, Number 3, Spring 2006.
 *
 * <p>Caches the intermediate constants ({@code m1, m2, m3, v1, v2, v3,
 * z1, z2, x1}) and the moment statistics ({@code ex, sigma, k3, k4})
 * between calls so that successive {@link #calculate()} invocations with
 * the same parameter set short-circuit the heavy triple-sum.
 */
public class AnalyticGJRGARCHEngine
        extends GenericModelEngine<GjrGarchModel,
                                   OneAssetOption.Arguments,
                                   OneAssetOption.Results> {

    // stored parameters (sentinels; updated on first calculate())
    private boolean init_ = false;
    private double h1_;
    private double b0_;
    private double b1_;
    private double b2_;
    private double b3_;
    private double la_;
    private double r_;
    private int T_;

    // intermediate constants determined by b1, b2, b3, la
    private double m1_;
    private double m2_;
    private double m3_;
    private double v1_;
    private double v2_;
    private double v3_;
    private double z1_;
    private double z2_;
    private double x1_;

    // statistical data for the GJR-GARCH process determined by
    // h1, b0, b1, b2, b3, r, T
    private double ex_;
    private double sigma_;
    private double k3_;
    private double k4_;

    public AnalyticGJRGARCHEngine(final GjrGarchModel model) {
        super(model,
              new OneAssetOption.ArgumentsImpl(),
              new OneAssetOption.ResultsImpl());
        this.init_ = false;
    }

    @Override
    public void calculate() {
        final OneAssetOption.ArgumentsImpl args = (OneAssetOption.ArgumentsImpl) arguments_;

        QL.require(args.exercise.type() == Exercise.Type.European,
                "not an European option");
        QL.require(args.payoff instanceof StrikedTypePayoff,
                "non-striked payoff given");

        final StrikedTypePayoff payoff = (StrikedTypePayoff) args.payoff;
        final GjrGarchProcess process = model.process();

        final Date exerciseDate = args.exercise.lastDate();
        final double riskFreeDiscount = process.riskFreeRate().currentLink().discount(exerciseDate);
        final double dividendDiscount = process.dividendYield().currentLink().discount(exerciseDate);
        final double spotPrice = process.s0().currentLink().value();
        QL.require(spotPrice > 0.0, "negative or null underlying given");

        final double strikePrice = payoff.strike();
        final double term = process.time(exerciseDate);
        final int T = (int) Math.round(process.daysPerYear() * term);
        final double r = -Math.log(riskFreeDiscount / dividendDiscount)
                / (process.daysPerYear() * term);
        final double h1 = process.v0();
        final double b0 = process.omega();
        final double b2 = process.alpha();
        final double b1 = process.beta();
        final double b3 = process.gamma();
        final double la = process.lambda();

        final double N = new CumulativeNormalDistribution().op(la);
        final double n = Math.exp(-la * la / 2.0) / (Constants.M_SQRTPI * Constants.M_SQRT2);
        final double s = spotPrice;
        final double x = strikePrice;

        double m1;
        double m2;
        double m3;
        double v1;
        double v2;
        double z1;
        double x1;

        boolean constantsMatch = false;

        if (!init_ || b1 != b1_ || b2 != b2_ || b3 != b3_ || la != la_) {
            // compute the useful coefficients
            m1 = b1 + (b2 + b3 * N) * (1.0 + la * la) + b3 * la * n;
            m2 = b1 * b1 + b2 * b2 * (Math.pow(la, 4) + 6.0 * la * la + 3.0)
                    + (b3 * b3 + 2.0 * b2 * b3) * (Math.pow(la, 4) * N
                            + Math.pow(la, 3) * n + 6.0 * la * la * N + 5.0 * la * n + 3.0 * N)
                    + 2.0 * b1 * b2 * (1.0 + la * la)
                    + 2.0 * b3 * b1 * (la * la * N + la * n + N);
            m3 = Math.pow(b1, 3)
                    + (3.0 * b3 * b3 * b1 + 6.0 * b1 * b2 * b3)
                            * (Math.pow(la, 3) * n + 5.0 * la * n + 3.0 * N
                                    + Math.pow(la, 4) * N + 6.0 * la * la * N)
                    + Math.pow(b2, 3) * (15.0 + Math.pow(la, 6) + 15.0 * Math.pow(la, 4) + 45.0 * la * la)
                    + (Math.pow(b3, 3) + 3.0 * b2 * b2 * b3 + 3.0 * b3 * b3 * b2)
                            * (Math.pow(la, 5) * n + 14.0 * Math.pow(la, 3) * n + 33.0 * la * n
                                    + 15.0 * N + 15.0 * Math.pow(la, 4) * N + 45.0 * la * la * N
                                    + Math.pow(la, 6) * N)
                    + 3.0 * b1 * b1 * b2 * (1.0 + la * la)
                    + 3.0 * b1 * b1 * b3 * (la * n + N + la * la * N)
                    + 3.0 * b1 * b2 * b2 * (3.0 + Math.pow(la, 4) + 6.0 * la * la);
            v1 = -2.0 * b2 * la - 2.0 * b3 * (n + la * N);
            v2 = -4.0 * b2 * b2 * (3.0 * la + Math.pow(la, 3))
                    - (4.0 * b3 * b3 + 8.0 * b2 * b3)
                            * (la * la * n + 2.0 * n + Math.pow(la, 3) * N + 3.0 * la * N)
                    - 4.0 * b1 * b2 * la - 4.0 * b3 * b1 * (n + la * N);
            final double v3 = -12.0 * b3 * b1 * (b3 + 2.0 * b2)
                            * (la * la * n + 2.0 * n + Math.pow(la, 3) * N + 3.0 * la * N)
                    - 6.0 * Math.pow(b2, 3) * la * (15.0 + Math.pow(la, 4) + 10.0 * la * la)
                    - 6.0 * b3 * (b3 * b3 + 3.0 * b2 * b2 + 3.0 * b3 * b2)
                            * (9.0 * la * la * n + 8.0 * n + 15.0 * la * N + Math.pow(la, 4) * n
                                    + Math.pow(la, 5) * N + 10.0 * Math.pow(la, 3) * N)
                    - 6.0 * b1 * b1 * b2 * la - 6.0 * b3 * b1 * b1 * (n + la * N)
                    - 12.0 * b2 * b2 * b1 * (3.0 * la + Math.pow(la, 3));
            z1 = b1 + b2 * (3.0 + la * la) + b3 * (la * n + 3.0 * N + la * la * N);
            final double z2 = b1 * b1 + b2 * b2 * (15.0 + Math.pow(la, 4) + 18.0 * la * la)
                    + (b3 * b3 + 2.0 * b2 * b3)
                            * (Math.pow(la, 3) * n + 17.0 * la * n + 15.0 * N
                                    + Math.pow(la, 4) * N + 18.0 * la * la * N)
                    + 2.0 * b1 * b2 * (3.0 + la * la)
                    + 2.0 * b3 * b1 * (la * n + 3.0 * N + la * la * N);
            x1 = -6.0 * b2 * la - 2.0 * b3 * (4.0 * n + 3.0 * la * N);
            b1_ = b1; b2_ = b2; b3_ = b3; la_ = la;
            m1_ = m1; m2_ = m2; m3_ = m3;
            v1_ = v1; v2_ = v2; v3_ = v3; z1_ = z1; z2_ = z2; x1_ = x1;
        } else {
            constantsMatch = true;
            m1 = m1_; m2 = m2_; m3 = m3_;
            v1 = v1_; v2 = v2_; z1 = z1_; x1 = x1_;
        }

        double ex;
        double sigma;
        double k3;
        double k4;

        if (!init_ || !constantsMatch || b0 != b0_ || h1 != h1_ || T != T_) {
            // these reassignments mirror the C++ guard: ensure m1/m2/m3 etc.
            // hold the current b1/b2/b3/la set whether we computed or cached.
            m1 = m1_; m2 = m2_; m3 = m3_;
            v1 = v1_; v2 = v2_; z1 = z1_; x1 = x1_;

            final double[] m1ai = new double[T];
            final double[] m2ai = new double[T];
            final double[] m3ai = new double[T];
            m1ai[0] = 1.0;
            m2ai[0] = 1.0;
            m3ai[0] = 1.0;
            for (int i = 1; i < T; ++i) {
                m1ai[i] = m1ai[i - 1] * m1;
                m2ai[i] = m2ai[i - 1] * m2;
                m3ai[i] = m3ai[i - 1] * m3;
            }

            double sEh = 0.0;
            double sEh2 = 0.0;
            double sEhh = 0.0;
            double sEh1_2eh = 0.0;
            double sEhhh = 0.0;
            double sEh2h = 0.0;
            double sEhh2 = 0.0;
            double sEh3 = 0.0;
            double sEh1_2eh2 = 0.0;
            double sEh3_2eh = 0.0;
            double sEh1_2ehh = 0.0;
            double sEhh1_2eh = 0.0;
            double sEhe2h = 0.0;
            double sEh1_2eh1_2eh = 0.0;
            double sEh3_2e3h = 0.0;

            for (int i = 0; i < T; ++i) {
                final double m1i = m1ai[i];
                final double m2i = m2ai[i];
                final double m3i = m3ai[i];

                final double m1im2i = m1i - m2i;
                final double m1im3i = m1i - m3i;
                final double m2im3i = m2i - m3i;
                final double Eh = b0 * (1.0 - m1i) / (1.0 - m1) + m1i * h1;
                final double Eh2 = b0 * b0 * ((1.0 + m1) * (1.0 - m2i) / (1.0 - m2)
                        - 2.0 * m1 * m1im2i / (m1 - m2)) / (1.0 - m1)
                        + 2.0 * b0 * m1 * m1im2i * h1 / (m1 - m2)
                        + m2i * h1 * h1;
                final double Eh3 = Math.pow(b0, 3) * (
                        (1.0 - m3i) / (1.0 - m3)
                        + 3.0 * m2 * ((1.0 - m3i) / (1.0 - m3) - m2im3i / (m2 - m3)) / (1.0 - m2)
                        + 3.0 * m1 * ((1.0 - m3i) / (1.0 - m3) - m1im3i / (m1 - m3)) / (1.0 - m1)
                        + 6.0 * m1 * m2 * (
                                ((1.0 - m3i) / (1.0 - m3) - m2im3i / (m2 - m3)) / (1.0 - m2)
                                + (m2im3i / (m2 - m3) - m1im3i / (m1 - m3)) / (m1 - m2)
                        ) / (1.0 - m1))
                        + 3.0 * b0 * b0 * m1 * h1 * (m1im3i / (m1 - m3)
                                + 2.0 * m2 * (m1im3i / (m1 - m3) - m2im3i / (m2 - m3)) / (m1 - m2))
                        + 3.0 * b0 * m2 * h1 * h1 * m2im3i / (m2 - m3)
                        + m3i * h1 * h1 * h1;
                final double Eh3_2 = .375 * Math.pow(Eh, -0.5) * Eh2 + .625 * Math.pow(Eh, 1.5);
                final double Eh5_2 = 1.875 * Math.pow(Eh, 0.5) * Eh2 - .875 * Math.pow(Eh, 2.5);
                sEh += Eh;
                sEh2 += Eh2;
                sEh3 += Eh3;
                for (int j = 0; j < T - i - 1; ++j) {
                    final double Ehh = b0 * Eh * (1.0 - m1ai[j + 1]) / (1.0 - m1)
                            + Eh2 * m1ai[j + 1];
                    final double Ehh2 = b0 * b0 * Eh * ((1.0 + m1) * (1.0 - m2ai[j + 1]) / (1.0 - m2)
                            - 2.0 * m1 * (m1ai[j + 1] - m2ai[j + 1]) / (m1 - m2)) / (1.0 - m1)
                            + 2.0 * b0 * m1 * Eh2 * (m1ai[j + 1] - m2ai[j + 1]) / (m1 - m2)
                            + m2ai[j + 1] * Eh3;
                    final double Eh2h = b0 * Eh2 * (1.0 - m1ai[j + 1]) / (1.0 - m1)
                            + m1ai[j + 1] * Eh3;
                    final double Eh1_2eh = v1 * m1ai[j] * Eh3_2;
                    final double Eh1_2eh2 = 2.0 * b0 * v1 * (m1ai[j + 1] - m2ai[j + 1])
                            * Eh3_2 / (m1 - m2)
                            + v2 * m2ai[j] * Eh5_2;
                    final double Ehij = b0 * (1.0 - m1ai[i + j + 1]) / (1.0 - m1)
                            + m1ai[i + j + 1] * h1;
                    final double Ehh3_2 = 0.375 * Ehh2 / Math.sqrt(Ehij)
                            + 0.75 * Math.sqrt(Ehij) * Ehh
                            - 0.125 * Math.pow(Ehij, 1.5) * Eh;
                    final double Eh3_2eh = v1 * m1ai[j] * Eh5_2;
                    final double Eh3_2e3h = x1 * m1ai[j] * Eh5_2;
                    final double Eh1_2eh3_2 = 0.375 * Eh1_2eh2 / Math.sqrt(Ehij)
                            + 0.75 * Math.sqrt(Ehij) * Eh1_2eh;
                    sEhh += Ehh;
                    sEh1_2eh += Eh1_2eh;
                    sEhh2 += Ehh2;
                    sEh2h += Eh2h;
                    sEh1_2eh2 += Eh1_2eh2;
                    sEh3_2eh += Eh3_2eh;
                    sEhe2h += b0 * Eh * (1.0 - m1ai[j + 1]) / (1.0 - m1)
                            + z1 * m1ai[j] * Eh2;
                    sEh3_2e3h += Eh3_2e3h;
                    for (int k = 0; k < T - i - j - 2; ++k) {
                        final double Ehhh = b0 * Ehh * (1.0 - m1ai[k + 1]) / (1.0 - m1)
                                + m1ai[k + 1] * Ehh2;
                        final double Eh1_2ehh = b0 * Eh1_2eh * (1.0 - m1ai[k + 1]) / (1.0 - m1)
                                + m1ai[k + 1] * Eh1_2eh2;
                        sEhhh += Ehhh;
                        sEh1_2ehh += Eh1_2ehh;
                        sEhh1_2eh += v1 * m1ai[k] * Ehh3_2;
                        sEh1_2eh1_2eh += v1 * m1ai[k] * Eh1_2eh3_2;
                    }
                }
            }

            ex = T * r - 0.5 * sEh;
            final double SD1 = 2.0 * sEhh + sEh2;
            final double SD2 = sEh;
            final double SD3 = sEh1_2eh;
            final double ex2 = T * T * r * r - T * r * sEh + 0.25 * SD1 + SD2 - SD3;
            final double ST1 = 6.0 * sEhhh + (3.0 * sEhh2 + (3.0 * sEh2h + sEh3));
            final double ST2 = 3.0 * sEh1_2eh;
            final double ST3 = 2.0 * sEhh1_2eh + (2.0 * sEh1_2ehh + (2.0 * sEh3_2eh + sEh1_2eh2));
            final double ST4 = sEhe2h + (sEhh + (sEh2 + 2.0 * sEh1_2eh1_2eh));
            final double ex3 = Math.pow(T * r, 3) - 1.5 * T * T * r * r * sEh
                    + 3.0 * T * r * (SD1 / 4.0 + SD2 - SD3)
                    + (ST2 - ST1 / 8.0 + 3.0 * ST3 / 4.0 - 3.0 * ST4 / 2.0);
            final double SQ2 = 6.0 * sEhe2h + (12.0 * sEh1_2eh1_2eh + 3.0 * sEh2);
            final double SQ4 = 2.0 * sEhhh + 2.0 * sEhh2;
            final double SQ5 = 3.0 * sEhh1_2eh + 3.0 * sEh1_2ehh + 3.0 * sEh3_2eh
                    + 3.0 * sEh1_2eh2 + sEh3_2e3h;
            final double ex4 = Math.pow(T * r, 4) - 2.0 * Math.pow(T * r, 3) * sEh
                    + 6.0 * T * T * r * r * (SD1 / 4.0 + SD2 - SD3)
                    + T * r * (4.0 * ST2 - ST1 / 2.0 + 3.0 * ST3 - 6.0 * ST4)
                    + (SQ2 + 3.0 * SQ4 / 2.0 - 2.0 * SQ5);

            // compute variance, skewness, kurtosis
            sigma = ex2 - ex * ex;
            // 3rd central moment mu3
            k3 = ex3 - 3.0 * sigma * ex - ex * ex * ex;
            // 4th central moment mu4
            k4 = ex4 + 6.0 * ex * ex * ex2 - 3.0 * ex * ex * ex * ex - 4.0 * ex * ex3;
            k3 /= Math.pow(sigma, 1.5);
            k4 /= Math.pow(sigma, 2);
            ex_ = ex;
            sigma_ = sigma;
            k3_ = k3;
            k4_ = k4;
            r_ = r;
            T_ = T;
            b0_ = b0;
            h1_ = h1;
        } else {
            ex = ex_;
            sigma = sigma_;
            k3 = k3_;
            k4 = k4_;
        }

        // compute call option price (then convert to put if necessary)
        final double stdev = Math.sqrt(sigma);
        final double del = (ex - r * T + sigma / 2.0) / stdev;
        final double d = (Math.log(s / x) + (r * T + sigma / 2.0)) / stdev;
        final double d_ = d + del;
        final double C = s * Math.exp(del * stdev) * new CumulativeNormalDistribution().op(d_)
                - x * Math.exp(-r * T) * new CumulativeNormalDistribution().op(d_ - stdev);
        final double A3 = s * Math.exp(del * stdev) * stdev * ((2.0 * stdev - d_)
                * Math.exp(-d_ * d_ / 2.0) / Math.sqrt(2.0 * Constants.M_PI)
                + sigma * new CumulativeNormalDistribution().op(d_)) / 6.0;
        final double A4 = s * Math.exp(del * stdev) * stdev * (
                (d_ * d_ - 1.0 - 3.0 * stdev * (d_ - stdev))
                        * Math.exp(-d_ * d_ / 2.0) / Math.sqrt(2.0 * Constants.M_PI)
                - sigma * stdev * new CumulativeNormalDistribution().op(d_)) / 24.0;
        final double Capp = C + k3 * A3 + (k4 - 3.0) * A4;
        init_ = true;

        final OneAssetOption.ResultsImpl results = (OneAssetOption.ResultsImpl) results_;
        final Option.Type type = payoff.optionType();
        if (type == Option.Type.Call) {
            results.value = Capp;
        } else if (type == Option.Type.Put) {
            // put-call parity in this engine's compounded discount convention
            results.value = Capp + strikePrice * riskFreeDiscount / dividendDiscount - spotPrice;
        } else {
            throw new LibraryException("unknown option type");
        }
    }
}
