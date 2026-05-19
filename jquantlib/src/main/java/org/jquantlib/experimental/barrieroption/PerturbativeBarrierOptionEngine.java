/*
 Copyright (C) 2026 JQuantLib migration

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
 Copyright (C) 2008 Lorella Fatone
 Copyright (C) 2008 Maria Cristina Recchioni
 Copyright (C) 2008 Francesco Zirilli
 Copyright (C) 2008 StatPro Italia srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
*/

package org.jquantlib.experimental.barrieroption;

import org.jquantlib.QL;
import org.jquantlib.instruments.BarrierOption;
import org.jquantlib.instruments.BarrierType;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Frequency;

/**
 * Perturbative barrier-option engine.
 * <p>
 * Java port of {@code ql/experimental/barrieroption/perturbativebarrieroptionengine.{hpp,cpp}}
 * (QuantLib v1.42.1, 099987f0ca2c11c505dc4348cdb9ce01a598e1e5).
 *
 * <p>This engine implements the approach described in
 * {@code http://www.econ.univpm.it/recchioni/finance/w3/} — a perturbative
 * (Taylor-style) expansion of the price of an up-and-out barrier option
 * with put payoff around the constant-rate / flat-volatility solution.
 *
 * <p>Restrictions inherited from C++:
 * <ul>
 *   <li>{@link BarrierType#UpOut} only;</li>
 *   <li>rebate must be zero;</li>
 *   <li>payoff must be a {@link PlainVanillaPayoff} of {@link Option.Type#Put}.</li>
 * </ul>
 *
 * <p>The numerics are a direct port of the C++ functions
 * {@code BarrierUPD}, {@code PHID}, {@code ND2}, {@code BVTL}, {@code STUDNT},
 * {@code PNTGND}, {@code KRNRDT}, {@code ADONET}, {@code SINCS}, {@code TVTMFN}
 * and the local Taylor-coefficient helpers {@code ff}, {@code v}, {@code llold},
 * {@code dvv}, {@code dff}, {@code dll}, {@code ddff}, {@code ddll}, {@code ddvv}
 * and {@code derivn3}. The C++ code uses file-scope mutable globals
 * ({@code H1, H2, H3, R23, RUA, RUB, AR, RUC, NUC}) to pass parameters from
 * {@code tvtl} into the adaptive integrator; in Java those become per-instance
 * fields on a non-shared helper, making the engine reentrant.
 *
 * <p>The C++ docstring warns "this was reported to fail tests on Mac OS X 10.8.4".
 * On modern macOS / OpenJDK the engine reproduces the published reference values
 * (0.897365 / 0.894374) to <strong>1e-6</strong> (the C++ test tolerance) — see
 * {@code DoubleBarrierOptionTest.testPerturbativeValues} and the reference probe
 * at {@code migration-harness/references/experimental/perturbative_barrier_engine.json}.
 *
 * @category barrierengines
 */
public class PerturbativeBarrierOptionEngine extends BarrierOption.EngineImpl {

    private static final double PI = 3.14159265358979324;

    private final GeneralizedBlackScholesProcess process;
    private final int order;
    private final boolean zeroGamma;
    private final BarrierOption.ArgumentsImpl a;
    private final BarrierOption.ResultsImpl r;

    /**
     * Default order = 1 and zeroGamma = false (C++ defaults).
     */
    public PerturbativeBarrierOptionEngine(final GeneralizedBlackScholesProcess process) {
        this(process, 1, false);
    }

    public PerturbativeBarrierOptionEngine(final GeneralizedBlackScholesProcess process,
                                           final int order, final boolean zeroGamma) {
        this.a = (BarrierOption.ArgumentsImpl) arguments_;
        this.r = (BarrierOption.ResultsImpl) results_;
        this.process = process;
        this.order = order;
        this.zeroGamma = zeroGamma;
        this.process.addObserver(this);
    }

    @Override
    public void calculate() {
        QL.require(a.barrierType == BarrierType.UpOut,
                "this engine only manages up-and-out options");
        QL.require(a.rebate == 0.0,
                "this engine does not manage non-null rebates");
        QL.require(a.payoff instanceof PlainVanillaPayoff,
                "this engine only manages plain-vanilla payoffs");
        final PlainVanillaPayoff payoff = (PlainVanillaPayoff) a.payoff;
        QL.require(payoff.optionType() == Option.Type.Put,
                "this engine only manages put options");
        QL.require(order <= 2, "order must be <= 2");

        final double stock = process.x0();
        final double kprice = payoff.strike();
        final double hbarr = a.barrier;
        final double tauMin = 0.0;
        final double tauMax = process.time(a.exercise.lastDate());

        final int igm = zeroGamma ? 0 : 1;

        final IntegralAdapters ad = new IntegralAdapters(process);
        r.value = barrierUPD(kprice, stock, hbarr, tauMin, tauMax,
                order, igm, ad);
    }

    // ===================================================================
    // Process adapters — these encapsulate r/q/sigma forward integrals
    // and pointwise values as in the C++ {integr_adapter, integalpha_adapter,
    // integs_adapter, alpha_adapter, sigmaq_adapter} structs.
    // ===================================================================

    private static final class IntegralAdapters {
        private final YieldTermStructure r;
        private final YieldTermStructure q;
        private final BlackVolTermStructure v;
        private final double s;

        IntegralAdapters(final GeneralizedBlackScholesProcess process) {
            this.r = process.riskFreeRate().currentLink();
            this.q = process.dividendYield().currentLink();
            this.v = process.blackVolatility().currentLink();
            this.s = process.x0();
        }

        /** integr_adapter: \int_{t1}^{t2} r(s) ds = forwardRate(t1,t2) * (t2-t1). */
        double integR(final double t1, final double t2) {
            return r.forwardRate(t1, t2, Compounding.Continuous, Frequency.NoFrequency, true).rate() * (t2 - t1);
        }

        /** integalpha_adapter: \int_{t1}^{t2} (r - q) ds. */
        double integAlpha(final double t1, final double t2) {
            final double rr = r.forwardRate(t1, t2, Compounding.Continuous, Frequency.NoFrequency, true).rate();
            final double qq = q.forwardRate(t1, t2, Compounding.Continuous, Frequency.NoFrequency, true).rate();
            return (rr - qq) * (t2 - t1);
        }

        /** integs_adapter: blackForwardVariance(t1, t2; s). */
        double integS(final double t1, final double t2) {
            return v.blackForwardVariance(t1, t2, s, true);
        }

        /** alpha_adapter: instantaneous (r - q) at t. */
        double alpha(final double t) {
            final double rr = r.forwardRate(t, t, Compounding.Continuous, Frequency.NoFrequency, true).rate();
            final double qq = q.forwardRate(t, t, Compounding.Continuous, Frequency.NoFrequency, true).rate();
            return rr - qq;
        }

        /** sigmaq_adapter: \sigma(t,s)^2 instantaneous. */
        double sigmaQ(final double t) {
            final double sigma = v.blackForwardVol(t, t, s, true);
            return sigma * sigma;
        }
    }

    // ===================================================================
    // PHID — standard normal CDF (Hart 5666 / Alan Miller approximation),
    // ~ 1e-15 absolute accuracy.
    // ===================================================================
    private static double phid(final double z) {
        final double p0 = 220.2068679123761;
        final double p1 = 221.2135961699311;
        final double p2 = 112.0792914978709;
        final double p3 = 33.91286607838300;
        final double p4 = 6.373962203531650;
        final double p5 = 0.7003830644436881;
        final double p6 = 0.03526249659989109;

        final double q0 = 440.4137358247522;
        final double q1 = 793.8265125199484;
        final double q2 = 637.3336333788311;
        final double q3 = 296.5642487796737;
        final double q4 = 86.78073220294608;
        final double q5 = 16.064177579206950;
        final double q6 = 1.7556671631826420;
        final double q7 = 0.088388347648318440;
        final double rootPi = 2.506628274631001;
        final double cutoff = 7.071067811865475;

        final double zabs = Math.abs(z);
        double p;
        if (zabs > 37.0) {
            p = 0.0;
        } else {
            final double expntl = Math.exp(-zabs * zabs / 2.0);
            if (zabs < cutoff) {
                p = expntl *
                        ((((((p6 * zabs + p5) * zabs + p4) * zabs + p3) * zabs + p2) * zabs + p1) * zabs + p0) /
                        (((((((q7 * zabs + q6) * zabs + q5) * zabs + q4) * zabs + q3) * zabs + q2) * zabs + q1) * zabs + q0);
            } else {
                p = expntl / (zabs + 1.0 / (zabs + 2.0 / (zabs + 3.0 / (zabs + 4.0 / (zabs + 0.65))))) / rootPi;
            }
        }
        if (z > 0.0) {
            p = 1.0 - p;
        }
        return p;
    }

    private static double sign(final double a, final double b) {
        return b > 0.0 ? Math.abs(a) : -Math.abs(a);
    }

    // ===================================================================
    // First-order helpers ff / v / llold
    // ===================================================================

    private static double ff(final double p, final double tt, final double a, final double b, final double gm) {
        double aa = -(b * p - b * tt + a) / Math.sqrt(2.0 * (tt - p));
        final double caux = 2.0 * Math.sqrt(PI) * phid(aa);
        aa = (b * b - (1.0 - gm) * (1.0 - gm)) / 4.0;
        return Math.exp(-0.5 * a * b) * Math.exp(aa * (tt - p)) * caux;
    }

    private static double vFun(final double p, final double tt, final double a, final double b, final double gm) {
        double aa = -(p * (a - b) + b * tt) / Math.sqrt(2.0 * p * tt * (tt - p));
        final double caux = phid(aa);
        aa = Math.exp(((a - b) * (a - b)) / (4.0 * tt))
                * Math.exp(((1.0 - gm) * (1.0 - gm)) * tt / 4.0)
                * Math.sqrt(tt);
        return caux / aa;
    }

    private static double llold(final double p, final double tt, final double a, final double b, final double c, final double gm) {
        final double xx = (-a + b * (tt - p)) / Math.sqrt(2.0 * (tt - p));
        final double yy = (-a + b * tt + c) / Math.sqrt(2.0 * tt);
        final double rho = Math.sqrt((tt - p) / tt);
        double aa = (b * b - (1.0 - gm) * (1.0 - gm)) / 4.0;
        final double caux = nd2(-xx, -yy, rho);
        return 2.0 * Math.sqrt(PI) * Math.exp(-a * b * 0.5) * Math.exp(aa * (tt - p)) * caux;
    }

    // ===================================================================
    // Second-order helpers dvv / dff / dll / ddff / ddll / ddvv
    // ===================================================================

    private static double dvv(final double s, final double p, final double tt, final double a, final double b, final double gm) {
        double aa = (a * p + b * (tt - p)) / Math.sqrt(2.0 * p * tt * (tt - p));
        double caux = phid(aa);

        aa = Math.exp(((a - b) * (a - b)) / (4.0 * tt))
                * Math.exp(((1.0 - gm) * (1.0 - gm)) * tt / 4.0)
                * Math.sqrt(tt);
        caux = -caux / aa;

        double xx = (a * p + b * (tt - p)) / Math.sqrt(2.0 * tt * p * (tt - p));
        double yy = (a * s + b * (tt - s)) / Math.sqrt(2.0 * tt * s * (tt - s));
        double rho = Math.sqrt((s * (tt - p)) / (p * (tt - s)));
        double caux1 = nd2(-xx, -yy, rho) / aa;

        aa = Math.exp(((a + b) * (a + b)) / (4.0 * tt))
                * Math.exp(((1.0 - gm) * (1.0 - gm)) * tt / 4.0)
                * Math.sqrt(tt);

        xx = (a * p - b * (tt - p)) / Math.sqrt(2.0 * tt * p * (tt - p));
        yy = (a * s - b * (tt - s)) / Math.sqrt(2.0 * tt * s * (tt - s));
        rho = Math.sqrt((s * (tt - p)) / (p * (tt - s)));
        double caux2 = nd2(-xx, -yy, rho) / aa;

        return (caux + caux1 + caux2) / (2.0 * Math.sqrt(PI));
    }

    private static double dff(final double s, final double p, final double tt, final double a, final double b, final double gm) {
        double xx = (a - b * (tt - p)) / Math.sqrt(2.0 * (tt - p));
        final double caux = -phid(xx) * Math.exp(-0.5 * a * b);

        xx = (a + b * (tt - p)) / Math.sqrt(2.0 * (tt - p));
        double yy = (a + b * (tt - s)) / Math.sqrt(2.0 * (tt - s));
        double rho = Math.sqrt((tt - p) / (tt - s));
        final double caux1 = Math.exp(0.5 * a * b) * nd2(-xx, -yy, rho);

        xx = (a - b * (tt - p)) / Math.sqrt(2.0 * (tt - p));
        yy = (a - b * (tt - s)) / Math.sqrt(2.0 * (tt - s));
        rho = Math.sqrt((tt - p) / (tt - s));
        final double caux2 = Math.exp(-0.5 * a * b) * nd2(-xx, -yy, rho);

        final double aa = Math.exp((b * b - (1.0 - gm) * (1.0 - gm)) * (tt - s) / 4.0);
        return (caux + caux1 + caux2) * aa;
    }

    private static double dll(final double s, final double p, final double tt, final double a, final double b, final double c, final double gm) {
        final double epsi = 1.e-12;
        final double[] limit = new double[4];
        final double[] sigmarho = new double[4];

        limit[1] = (a + b * (tt - p)) / Math.sqrt(2.0 * (tt - p));
        limit[2] = (a + b * (tt - s)) / Math.sqrt(2.0 * (tt - s));
        limit[3] = (a + b * tt + c) / Math.sqrt(2.0 * tt);
        sigmarho[1] = Math.sqrt((tt - p) / (tt - s));
        sigmarho[2] = Math.sqrt((tt - p) / tt);
        sigmarho[3] = Math.sqrt((tt - s) / tt);

        final double caux = Math.exp(0.5 * a * b) * tvtl(0, limit, sigmarho, epsi);

        limit[1] = (a - b * (tt - p)) / Math.sqrt(2.0 * (tt - p));
        limit[2] = (-a + b * (tt - s)) / Math.sqrt(2.0 * (tt - s));
        limit[3] = (-a + b * tt + c) / Math.sqrt(2.0 * tt);
        sigmarho[1] = -Math.sqrt((tt - p) / (tt - s));
        sigmarho[2] = -Math.sqrt((tt - p) / tt);
        sigmarho[3] = Math.sqrt((tt - s) / tt);

        final double caux1 = -Math.exp(-0.5 * a * b) * tvtl(0, limit, sigmarho, epsi);

        final double aa = Math.exp((b * b - (1.0 - gm) * (1.0 - gm)) * (tt - s) / 4.0);
        return (caux + caux1) * aa;
    }

    private static double ddff(final double s, final double p, final double tt, final double a, final double b, final double gm) {
        double xx = (a - b * (tt - p)) / Math.sqrt(2.0 * (tt - p));
        double caux = phid(xx) * Math.exp(-0.5 * a * b);

        xx = (a + b * (tt - p)) / Math.sqrt(2.0 * (tt - p));
        double yy = (a + b * (tt - s)) / Math.sqrt(2.0 * (tt - s));
        double rho = Math.sqrt((tt - p) / (tt - s));
        double caux1 = Math.exp(0.5 * a * b) * nd2(-xx, -yy, rho);

        xx = (a - b * (tt - p)) / Math.sqrt(2.0 * (tt - p));
        yy = (a - b * (tt - s)) / Math.sqrt(2.0 * (tt - s));
        rho = Math.sqrt((tt - p) / (tt - s));
        double caux2 = -Math.exp(-0.5 * a * b) * nd2(-xx, -yy, rho);

        caux = 0.5 * b * (caux + caux1 + caux2);

        xx = (a + b * (tt - p)) / Math.sqrt(2.0 * (tt - p));
        yy = b * Math.sqrt(p - s) / Math.sqrt(2.0);
        caux1 = Math.exp(-0.5 * xx * xx) * Math.exp(0.5 * a * b) * phid(yy)
                / (2.0 * Math.sqrt(PI * (tt - p)));

        xx = (a + b * (tt - s)) / Math.sqrt(2.0 * (tt - s));
        yy = a * Math.sqrt(p - s) / Math.sqrt(2.0 * (tt - p) * (tt - s));
        caux2 = Math.exp(-0.5 * xx * xx) * Math.exp(0.5 * a * b) * phid(yy)
                / (2.0 * Math.sqrt(PI * (tt - s)));

        xx = (a - b * (tt - p)) / Math.sqrt(2.0 * (tt - p));
        yy = b * Math.sqrt(p - s) / Math.sqrt(2.0);
        double caux3 = -Math.exp(-0.5 * xx * xx) * Math.exp(-0.5 * a * b) * phid(yy)
                / (2.0 * Math.sqrt(PI * (tt - p)));

        xx = (a - b * (tt - s)) / Math.sqrt(2.0 * (tt - s));
        yy = a * Math.sqrt(p - s) / Math.sqrt(2.0 * (tt - p) * (tt - s));
        double caux4 = Math.exp(-0.5 * xx * xx) * Math.exp(-0.5 * a * b) * phid(yy)
                / (2.0 * Math.sqrt(PI * (tt - s)));

        final double aa = Math.exp((b * b - (1.0 - gm) * (1.0 - gm)) * (tt - p) / 4.0);
        return (caux + caux1 + caux2 + caux3 + caux4) * aa;
    }

    private static double ddll(final double s, final double p, final double tt, final double ax, final double bx, final double c, final double gm) {
        final double epsi = 1.e-12;
        final double[] limit = new double[4];
        final double[] sigmarho = new double[4];

        limit[1] = (ax + bx * (tt - p)) / Math.sqrt(2.0 * (tt - p));
        limit[2] = (ax + bx * (tt - s)) / Math.sqrt(2.0 * (tt - s));
        limit[3] = (ax + bx * tt + c) / Math.sqrt(2.0 * tt);
        sigmarho[1] = Math.sqrt((tt - p) / (tt - s));
        sigmarho[2] = Math.sqrt((tt - p) / tt);
        sigmarho[3] = Math.sqrt((tt - s) / tt);

        double caux = 0.5 * bx * tvtl(0, limit, sigmarho, epsi);

        caux = caux + derivn3(limit, sigmarho, 1) / Math.sqrt(2.0 * (tt - p));
        caux = caux + derivn3(limit, sigmarho, 2) / Math.sqrt(2.0 * (tt - s));
        caux = caux + derivn3(limit, sigmarho, 3) / Math.sqrt(2.0 * tt);

        caux = Math.exp(0.5 * ax * bx) * caux;

        limit[1] = (ax - bx * (tt - p)) / Math.sqrt(2.0 * (tt - p));
        limit[2] = (-ax + bx * (tt - s)) / Math.sqrt(2.0 * (tt - s));
        limit[3] = (-ax + bx * tt + c) / Math.sqrt(2.0 * tt);
        sigmarho[1] = -Math.sqrt((tt - p) / (tt - s));
        sigmarho[2] = -Math.sqrt((tt - p) / tt);
        sigmarho[3] = Math.sqrt((tt - s) / tt);

        double caux1 = 0.5 * bx * tvtl(0, limit, sigmarho, epsi);

        caux1 = caux1 - derivn3(limit, sigmarho, 1) / Math.sqrt(2.0 * (tt - p));
        caux1 = caux1 + derivn3(limit, sigmarho, 2) / Math.sqrt(2.0 * (tt - s));
        caux1 = caux1 + derivn3(limit, sigmarho, 3) / Math.sqrt(2.0 * tt);

        caux1 = Math.exp(-0.5 * ax * bx) * caux1;

        final double aa = Math.exp((bx * bx - (1.0 - gm) * (1.0 - gm)) * (tt - s) / 4.0);
        return (caux + caux1) * aa;
    }

    private static double ddvv(final double s, final double p, final double tt, final double a, final double b, final double gm) {
        double aa = (a * p + b * (tt - p)) / Math.sqrt(2.0 * p * tt * (tt - p));
        double caux = phid(aa);

        aa = Math.exp(-(a - b) * (a - b) / (4.0 * tt)) / tt;
        caux = 0.5 * aa * caux * (a - b);

        double xx = (a * p + b * (tt - p)) / Math.sqrt(2.0 * tt * p * (tt - p));
        double yy = (a * s + b * (tt - s)) / Math.sqrt(2.0 * tt * s * (tt - s));
        double rho = Math.sqrt((s * (tt - p)) / (p * (tt - s)));
        double caux1 = nd2(-xx, -yy, rho);
        caux1 = -0.5 * aa * caux1 * (a - b);

        aa = Math.exp(-(a + b) * (a + b) / (4.0 * tt)) / tt;
        xx = (a * p - b * (tt - p)) / Math.sqrt(2.0 * tt * p * (tt - p));
        yy = (a * s - b * (tt - s)) / Math.sqrt(2.0 * tt * s * (tt - s));
        rho = Math.sqrt((s * (tt - p)) / (p * (tt - s)));
        double caux2 = nd2(-xx, -yy, rho);
        caux2 = -0.5 * aa * caux2 * (a + b);

        aa = -b * Math.sqrt((p - s) / Math.sqrt(2.0 * p * s));
        double aux = Math.sqrt(p / (PI * tt * (tt - p))) * phid(aa);

        xx = (a + b) * (a + b) / (4.0 * tt);
        yy = ((a * p - b * (tt - p)) * (a * p - b * (tt - p))) / (4.0 * p * tt * (tt - p));
        double caux3 = aux * Math.exp(-xx) * Math.exp(-yy) / 2.0;

        xx = (a - b) * (a - b) / (4.0 * tt);
        yy = ((a * p + b * (tt - p)) * (a * p + b * (tt - p))) / (4.0 * p * tt * (tt - p));
        double caux4 = aux * Math.exp(-xx) * Math.exp(-yy) / 2.0;

        aa = a * Math.sqrt((p - s) / Math.sqrt(2.0 * (tt - p) * (tt - s)));
        aux = Math.sqrt(s / (PI * tt * (tt - s))) * phid(aa);

        xx = (a + b) * (a + b) / (4.0 * tt);
        yy = ((a * s - b * (tt - s)) * (a * s - b * (tt - s))) / (4.0 * s * tt * (tt - s));
        double caux5 = aux * Math.exp(-xx) * Math.exp(-yy) / 2.0;

        xx = (a - b) * (a - b) / (4.0 * tt);
        yy = ((a * s + b * (tt - s)) * (a * s + b * (tt - s))) / (4.0 * s * tt * (tt - s));
        double caux6 = aux * Math.exp(-xx) * Math.exp(-yy) / 2.0;

        aux = Math.exp((1.0 - gm) * (1.0 - gm) * tt / 4.0) * Math.sqrt(tt);

        return (caux + caux1 + caux2 + caux3 + caux4 + caux5 + caux6) / (aux * 2.0 * Math.sqrt(PI));
    }

    // derivn3 — derivative of trivariate normal CDF wrt one of the integration limits.
    private static double derivn3(final double[] limit, final double[] sigmarho, final int idx) {
        final double sc = Math.sqrt(2.0 * PI);
        double aa;
        double xx;
        double yy;
        double rho;

        if (idx == 1) {
            aa = Math.exp(-0.5 * limit[1] * limit[1]);
            xx = (limit[3] - sigmarho[2] * limit[1]) / Math.sqrt(1.0 - sigmarho[2] * sigmarho[2]);
            yy = (limit[2] - sigmarho[1] * limit[1]) / Math.sqrt(1.0 - sigmarho[1] * sigmarho[1]);
            rho = (sigmarho[3] - sigmarho[1] * sigmarho[2])
                    / Math.sqrt((1.0 - sigmarho[1] * sigmarho[1]) * (1.0 - sigmarho[2] * sigmarho[2]));
        } else if (idx == 2) {
            aa = Math.exp(-0.5 * limit[2] * limit[2]);
            xx = (limit[1] - sigmarho[1] * limit[2]) / Math.sqrt(1.0 - sigmarho[1] * sigmarho[1]);
            yy = (limit[3] - sigmarho[3] * limit[2]) / Math.sqrt(1.0 - sigmarho[3] * sigmarho[3]);
            rho = (sigmarho[2] - sigmarho[1] * sigmarho[3])
                    / Math.sqrt((1.0 - sigmarho[1] * sigmarho[1]) * (1.0 - sigmarho[3] * sigmarho[3]));
        } else {
            // idx == 3
            aa = Math.exp(-0.5 * limit[3] * limit[3]);
            xx = (limit[1] - sigmarho[2] * limit[3]) / Math.sqrt(1.0 - sigmarho[2] * sigmarho[2]);
            yy = (limit[2] - sigmarho[3] * limit[3]) / Math.sqrt(1.0 - sigmarho[3] * sigmarho[3]);
            rho = (sigmarho[1] - sigmarho[2] * sigmarho[3])
                    / Math.sqrt((1.0 - sigmarho[2] * sigmarho[2]) * (1.0 - sigmarho[3] * sigmarho[3]));
        }
        return aa * nd2(-xx, -yy, rho) / sc;
    }

    // ===================================================================
    // tvtl — trivariate cumulative normal (Genz 2004 / Plackett 1954)
    //
    // Encapsulates the C++ file-scope state (H1, H2, H3, R23, RUA, RUB,
    // AR, RUC, NUC) into a single TvtlState instance that the adaptive
    // integrator can read without reaching for shared mutable globals.
    // ===================================================================

    private static final class TvtlState {
        double H1, H2, H3, R23, RUA, RUB, AR, RUC;
        int NUC;
    }

    private static double tvtl(final int nu, final double[] limit, final double[] sigmarho, final double epsi) {
        final double eps = Math.max(1.e-14, epsi);
        final double pt = PI / 2.0;
        final TvtlState st = new TvtlState();
        st.NUC = nu;
        st.H1 = limit[1];
        st.H2 = limit[2];
        st.H3 = limit[3];
        double R12 = sigmarho[1];
        double R13 = sigmarho[2];
        st.R23 = sigmarho[3];

        // Sort R's and check for special cases.
        if (Math.abs(R12) > Math.abs(R13)) {
            st.H2 = st.H3;
            st.H3 = limit[2];
            R12 = R13;
            R13 = sigmarho[1];
        }
        if (Math.abs(R13) > Math.abs(st.R23)) {
            st.H1 = st.H2;
            st.H2 = limit[1];
            st.R23 = R13;
            R13 = sigmarho[3];
        }

        double tvt = 0.0;
        if (Math.abs(st.H1) + Math.abs(st.H2) + Math.abs(st.H3) < eps) {
            tvt = (1.0 + (Math.asin(R12) + Math.asin(R13) + Math.asin(st.R23)) / pt) / 8.0;
        } else if (nu < 1 && (Math.abs(R12) + Math.abs(R13) < eps)) {
            tvt = phid(st.H1) * bvtl(nu, st.H2, st.H3, st.R23);
        } else if (nu < 1 && (Math.abs(R13) + Math.abs(st.R23) < eps)) {
            tvt = phid(st.H3) * bvtl(nu, st.H1, st.H2, R12);
        } else if (nu < 1 && (Math.abs(R12) + Math.abs(st.R23) < eps)) {
            tvt = phid(st.H2) * bvtl(nu, st.H1, st.H3, R13);
        } else if ((1.0 - st.R23) < eps) {
            tvt = bvtl(nu, st.H1, Math.min(st.H2, st.H3), R12);
        } else if ((st.R23 + 1.0) < eps) {
            if (st.H2 > -st.H3) {
                tvt = bvtl(nu, st.H1, st.H2, R12) - bvtl(nu, st.H1, -st.H3, R12);
            }
        } else {
            // Compute singular TVT value
            if (nu < 1) {
                tvt = bvtl(nu, st.H2, st.H3, st.R23) * phid(st.H1);
            } else if (st.R23 > 0.0) {
                tvt = bvtl(nu, st.H1, Math.min(st.H2, st.H3), 0.0);
            } else if (st.H2 > -st.H3) {
                tvt = bvtl(nu, st.H1, st.H2, 0.0) - bvtl(nu, st.H1, -st.H3, 0.0);
            }
            st.RUA = Math.asin(R12);
            st.RUB = Math.asin(R13);
            st.AR = Math.asin(st.R23);
            st.RUC = sign(pt, st.AR) - st.AR;
            tvt = tvt + adonet(0.0, 1.0, eps, st) / (4.0 * pt);
        }
        return Math.max(0.0, Math.min(tvt, 1.0));
    }

    // TVTMFN — Plackett formula integrand.
    private static double tvtmfn(final double x, final TvtlState st) {
        final double[] r12 = new double[1];
        final double[] rr2 = new double[1];
        final double[] r13 = new double[1];
        final double[] rr3 = new double[1];
        sincs(st.RUA * x, r12, rr2);
        sincs(st.RUB * x, r13, rr3);

        double result = 0.0;
        if (Math.abs(st.RUA) > 0.0) {
            result += st.RUA * pntgnd(st.NUC, st.H1, st.H2, st.H3, r13[0], st.R23, r12[0], rr2[0]);
        }
        if (Math.abs(st.RUB) > 0.0) {
            result += st.RUB * pntgnd(st.NUC, st.H1, st.H3, st.H2, r12[0], st.R23, r13[0], rr3[0]);
        }
        if (st.NUC > 0) {
            final double[] r = new double[1];
            final double[] rr = new double[1];
            sincs(st.AR + st.RUC * x, r, rr);
            result -= st.RUC * pntgnd(st.NUC, st.H2, st.H3, st.H1, 0.0, 0.0, r[0], rr[0]);
        }
        return result;
    }

    // SINCS — sin(X), cos(X)^2 with series approx for |X| near PI/2.
    private static void sincs(final double x, final double[] sx, final double[] cs) {
        final double pt = 1.57079632679489661923132169163975;
        final double ee = (pt - Math.abs(x)) * (pt - Math.abs(x));

        if (ee < 5e-5) {
            sx[0] = sign(1.0 - ee * (1.0 - ee / 12.0) / 2.0, x);
            cs[0] = ee * (1.0 - ee * (1.0 - 2.0 * ee / 15.0) / 3.0);
        } else {
            sx[0] = Math.sin(x);
            cs[0] = 1.0 - sx[0] * sx[0];
        }
    }

    // ADONET — one-dimensional globally adaptive integration.
    private static double adonet(final double a, final double b, final double tol, final TvtlState st) {
        final int nl = 100;
        final double[] ei = new double[nl + 1];
        final double[] ai = new double[nl + 1];
        final double[] bi = new double[nl + 1];
        final double[] fi = new double[nl + 1];

        ai[1] = a;
        bi[1] = b;
        double err = 1.0;
        int ip = 1;
        int im = 1;
        double fin = 0.0;
        while (4.0 * err > tol && im < nl) {
            im++;
            bi[im] = bi[ip];
            ai[im] = (ai[ip] + bi[ip]) / 2.0;
            bi[ip] = ai[im];
            final double[] eip = {ei[ip]};
            fi[ip] = krnrdt(ai[ip], bi[ip], st, eip);
            ei[ip] = eip[0];
            final double[] eim = {ei[im]};
            fi[im] = krnrdt(ai[im], bi[im], st, eim);
            ei[im] = eim[0];

            err = 0.0;
            fin = 0.0;
            for (int i = 1; i <= im; i++) {
                if (ei[i] > ei[ip]) {
                    ip = i;
                }
                fin += fi[i];
                err += ei[i] * ei[i];
            }
            err = Math.sqrt(err);
        }
        return fin;
    }

    // KRNRDT — Kronrod 23-point rule.
    private static double krnrdt(final double a, final double b, final TvtlState st, final double[] err) {
        final int n = 11;
        final double[] wg = {0.0,
                0.2729250867779007,
                0.05566856711617449,
                0.1255803694649048,
                0.1862902109277352,
                0.2331937645919914,
                0.2628045445102478};
        final double[] xgk = {0.0,
                0.0,
                0.9963696138895427,
                0.9782286581460570,
                0.9416771085780681,
                0.8870625997680953,
                0.8160574566562211,
                0.7301520055740492,
                0.6305995201619651,
                0.5190961292068118,
                0.3979441409523776,
                0.2695431559523450,
                0.1361130007993617};
        final double[] wgk = {0.0,
                0.1365777947111183,
                0.9765441045961290e-02,
                0.2715655468210443e-01,
                0.4582937856442671e-01,
                0.6309742475037484e-01,
                0.7866457193222764e-01,
                0.9295309859690074e-01,
                0.1058720744813894,
                0.1167395024610472,
                0.1251587991003195,
                0.1312806842298057,
                0.1351935727998845};

        final double wid = (b - a) / 2.0;
        final double cen = (b + a) / 2.0;
        double fc = tvtmfn(cen, st);
        double resg = fc * wg[1];
        double resk = fc * wgk[1];

        for (int j = 1; j <= n; j++) {
            final double t = wid * xgk[j + 1];
            fc = tvtmfn(cen - t, st) + tvtmfn(cen + t, st);
            resk += wgk[j + 1] * fc;
            if (j % 2 == 0) {
                resg += wg[1 + j / 2] * fc;
            }
        }
        final double result = wid * resk;
        err[0] = Math.abs(wid * (resk - resg));
        return result;
    }

    // STUDNT — Student t distribution function.
    private static double studnt(final int nu, final double t) {
        if (nu < 1) {
            return phid(t);
        }
        if (nu == 1) {
            return (1.0 + 2.0 * Math.atan(t) / PI) / 2.0;
        }
        if (nu == 2) {
            return (1.0 + t / Math.sqrt(2.0 + t * t)) / 2.0;
        }
        final double tt = t * t;
        final double cssthe = 1.0 / (1.0 + tt / nu);
        double polyn = 1.0;
        for (int j = nu - 2; j >= 2; j -= 2) {
            polyn = 1.0 + (j - 1.0) * cssthe * polyn / j;
        }
        double result;
        if (nu % 2 == 1) {
            final double rn = nu;
            final double ts = t / Math.sqrt(rn);
            result = (1.0 + 2.0 * (Math.atan(ts) + ts * cssthe * polyn) / PI) / 2.0;
        } else {
            final double snthe = t / Math.sqrt(nu + tt);
            result = (1.0 + snthe * polyn) / 2.0;
        }
        return Math.max(0.0, Math.min(result, 1.0));
    }

    // BVTL — bivariate t / normal probability (Dunnett-Sobel 1954).
    private static double bvtl(final int nu, final double dh, final double dk, final double r) {
        final double eps = 1e-15;
        if (nu < 1) {
            return nd2(-dh, -dk, r);
        }
        if ((1.0 - r) <= eps) {
            return studnt(nu, Math.min(dh, dk));
        }
        if ((r + 1.0) <= eps) {
            if (dh > -dk) {
                return studnt(nu, dh) - studnt(nu, -dk);
            }
            return 0.0;
        }
        final double tpi = 2.0 * PI;
        final double snu = Math.sqrt((double) nu);
        final double ors = 1.0 - r * r;
        final double hrk = dh - r * dk;
        final double krh = dk - r * dh;
        double xnhk;
        double xnkh;
        if (Math.abs(hrk) + ors > 0.0) {
            xnhk = hrk * hrk / (hrk * hrk + ors * (nu + dk * dk));
            xnkh = krh * krh / (krh * krh + ors * (nu + dh * dh));
        } else {
            xnhk = 0.0;
            xnkh = 0.0;
        }
        final int hs = (int) sign(1.0, dh - r * dk);
        final int ks = (int) sign(1.0, dk - r * dh);
        double bvt;
        if (nu % 2 == 0) {
            bvt = Math.atan2(Math.sqrt(ors), -r) / tpi;
            double gmph = dh / Math.sqrt(16.0 * (nu + dh * dh));
            double gmpk = dk / Math.sqrt(16.0 * (nu + dk * dk));
            double btnckh = 2.0 * Math.atan2(Math.sqrt(xnkh), Math.sqrt(1.0 - xnkh)) / PI;
            double btpdkh = 2.0 * Math.sqrt(xnkh * (1.0 - xnkh)) / PI;
            double btnchk = 2.0 * Math.atan2(Math.sqrt(xnhk), Math.sqrt(1.0 - xnhk)) / PI;
            double btpdhk = 2.0 * Math.sqrt(xnhk * (1.0 - xnhk)) / PI;
            for (int j = 1; j <= nu / 2; j++) {
                bvt += gmph * (1.0 + ks * btnckh);
                bvt += gmpk * (1.0 + hs * btnchk);
                btnckh += btpdkh;
                btpdkh = 2.0 * j * btpdkh * (1.0 - xnkh) / (2.0 * j + 1.0);
                btnchk += btpdhk;
                btpdhk = 2.0 * j * btpdhk * (1.0 - xnhk) / (2.0 * j + 1.0);
                gmph = gmph * (2.0 * j - 1.0) / (2.0 * j * (1.0 + dh * dh / nu));
                gmpk = gmpk * (2.0 * j - 1.0) / (2.0 * j * (1.0 + dk * dk / nu));
            }
        } else {
            final double qhrk = Math.sqrt(dh * dh + dk * dk - 2.0 * r * dh * dk + nu * ors);
            final double hkrn = dh * dk + r * nu;
            final double hkn = dh * dk - nu;
            final double hpk = dh + dk;
            bvt = Math.atan2(-snu * (hkn * qhrk + hpk * hkrn), hkn * hkrn - nu * hpk * qhrk) / tpi;
            if (bvt < -eps) {
                bvt += 1.0;
            }
            double gmph = dh / (tpi * snu * (1.0 + dh * dh / nu));
            double gmpk = dk / (tpi * snu * (1.0 + dk * dk / nu));
            double btnckh = Math.sqrt(xnkh);
            double btpdkh = btnckh;
            double btnchk = Math.sqrt(xnhk);
            double btpdhk = btnchk;
            for (int j = 1; j <= (nu - 1) / 2; j++) {
                bvt += gmph * (1.0 + ks * btnckh);
                bvt += gmpk * (1.0 + hs * btnchk);
                btpdkh = (2.0 * j - 1.0) * btpdkh * (1.0 - xnkh) / (2.0 * j);
                btnckh += btpdkh;
                btpdhk = (2.0 * j - 1.0) * btpdhk * (1.0 - xnhk) / (2.0 * j);
                btnchk += btpdhk;
                gmph = 2.0 * j * gmph / ((2.0 * j + 1.0) * (1.0 + dh * dh / nu));
                gmpk = 2.0 * j * gmpk / ((2.0 * j + 1.0) * (1.0 + dk * dk / nu));
            }
        }
        return bvt;
    }

    // PNTGND — Plackett formula integrand helper.
    private static double pntgnd(final int nuc, final double ba, final double bb, final double bc,
                                 final double ra, final double rb, final double r, final double rr) {
        double result = 0.0;
        final double dt = rr * (rr - (ra - rb) * (ra - rb) - 2.0 * ra * rb * (1.0 - r));
        if (dt > 0.0) {
            final double bt = (bc * rr + ba * (r * rb - ra) + bb * (r * ra - rb)) / Math.sqrt(dt);
            double ft = Math.sqrt(ba - r * bb) / rr + bb * bb;
            if (nuc < 1) {
                if (bt > -10.0 && ft < 100.0) {
                    result = Math.exp(-ft / 2.0);
                    if (bt < 10.0) {
                        result *= phid(bt);
                    }
                } else {
                    // C++ branch — only reachable when nuc >= 1.
                    ft = Math.sqrt(1.0 + ft / nuc);
                    result = studnt(nuc, bt / ft) / Math.pow(ft, nuc);
                }
            }
        }
        return result;
    }

    // ===================================================================
    // ND2 — bivariate normal CDF (Drezner-Wesolowsky 1989 + Genz).
    // ===================================================================
    private static double nd2(final double a, final double b, final double rho) {
        final double twopi = 6.283185307179586;
        final double[][] xl = new double[11][4];
        final double[][] wl = new double[11][4];

        // Gauss-Legendre N=6 (3 nodes on each side, listing the negative half).
        wl[1][1] = 0.1713244923791705;
        xl[1][1] = -0.9324695142031522;
        wl[2][1] = 0.3607615730481384;
        xl[2][1] = -0.6612093864662647;
        wl[3][1] = 0.4679139345726904;
        xl[3][1] = -0.2386191860831970;

        // N=12.
        wl[1][2] = 0.4717533638651177e-01;
        xl[1][2] = -0.9815606342467191;
        wl[2][2] = 0.1069393259953183;
        xl[2][2] = -0.9041172563704750;
        wl[3][2] = 0.1600783285433464;
        xl[3][2] = -0.7699026741943050;
        wl[4][2] = 0.2031674267230659;
        xl[4][2] = -0.5873179542866171;
        wl[5][2] = 0.2334925365383547;
        xl[5][2] = -0.3678314989981802;
        wl[6][2] = 0.2491470458134029;
        xl[6][2] = -0.1252334085114692;

        // N=20.
        wl[1][3] = 0.1761400713915212e-01;
        xl[1][3] = -0.9931285991850949;
        wl[2][3] = 0.4060142980038694e-01;
        xl[2][3] = -0.9639719272779138;
        wl[3][3] = 0.6267204833410906e-01;
        xl[3][3] = -0.9122344282513259;
        wl[4][3] = 0.8327674157670475e-01;
        xl[4][3] = -0.8391169718222188;
        wl[5][3] = 0.1019301198172404;
        xl[5][3] = -0.7463319064601508;
        wl[6][3] = 0.1181945319615184;
        xl[6][3] = -0.6360536807265150;
        wl[7][3] = 0.1316886384491766;
        xl[7][3] = -0.5108670019508271;
        wl[8][3] = 0.1420961093183821;
        xl[8][3] = -0.3737060887154196;
        wl[9][3] = 0.1491729864726037;
        xl[9][3] = -0.2277858511416451;
        wl[10][3] = 0.1527533871307259;
        xl[10][3] = -0.7652652113349733e-01;

        final double r = rho;
        final double dh = a;
        double dk = b;

        final int ng;
        final int lg;
        if (Math.abs(r) < 0.3) {
            ng = 1;
            lg = 3;
        } else if (Math.abs(r) < 0.75) {
            ng = 2;
            lg = 6;
        } else {
            ng = 3;
            lg = 10;
        }
        final double h = dh;
        double k = dk;
        double hk = h * k;
        double bvn = 0.0;

        if (Math.abs(r) < 0.925) {
            if (Math.abs(r) > 0.0) {
                final double hs = (h * h + k * k) / 2.0;
                final double asr = Math.asin(r);
                for (int i = 1; i <= lg; i++) {
                    for (int is = -1; is <= 1; is += 2) {
                        final double sn = Math.sin(asr * (is * xl[i][ng] + 1.0) / 2.0);
                        bvn += wl[i][ng] * Math.exp((sn * hk - hs) / (1.0 - sn * sn));
                    }
                }
                bvn = bvn * asr / (2.0 * twopi);
            }
            bvn = bvn + phid(-h) * phid(-k);
        } else {
            if (r < 0.0) {
                k = -k;
                hk = -hk;
            }
            if (Math.abs(r) < 1.0) {
                final double as = (1.0 - r) * (1.0 + r);
                double aa = Math.sqrt(as);
                final double bs = (h - k) * (h - k);
                final double c = (4.0 - hk) / 8.0;
                final double d = (12.0 - hk) / 16.0;
                double asr = -(bs / as + hk) / 2.0;
                if (asr > -100.0) {
                    bvn = aa * Math.exp(asr) * (1.0 - c * (bs - as) * (1.0 - d * bs / 5.0) / 3.0 + c * d * as * as / 5.0);
                }
                if (-hk < 100.0) {
                    final double bb = Math.sqrt(bs);
                    bvn = bvn - Math.exp(-hk / 2.0) * Math.sqrt(twopi) * phid(-bb / aa) * bb
                            * (1.0 - c * bs * (1.0 - d * bs / 5.0) / 3.0);
                }
                aa = aa / 2.0;
                for (int i = 1; i <= lg; i++) {
                    for (int is = -1; is <= 1; is += 2) {
                        final double xs = aa * (is * xl[i][ng] + 1.0);
                        final double xs2 = xs * xs;
                        final double rs = Math.sqrt(1.0 - xs2) * Math.sqrt(1.0 - xs2);
                        asr = -(bs / xs2 + hk) / 2.0;
                        if (asr > -100.0) {
                            bvn = bvn + aa * wl[i][ng] * Math.exp(asr)
                                    * (Math.exp(-hk * (1.0 - rs) / (2.0 * (1.0 + rs))) / rs
                                    - (1.0 + c * xs2 * (1.0 + d * xs2)));
                        }
                    }
                }
                bvn = -bvn / twopi;
            }
            if (r > 0.0) {
                bvn = bvn + phid(-Math.max(h, k));
            } else {
                bvn = -bvn;
                if (k > h) {
                    bvn = bvn + phid(k) - phid(h);
                }
            }
        }
        return bvn;
    }

    // ===================================================================
    // BarrierUPD — main pricing routine.
    // ===================================================================

    private static double barrierUPD(final double kprice, final double stock, final double hbarr,
                                     final double tauMin, final double tauMax,
                                     final int iord, final int igm,
                                     final IntegralAdapters ad) {
        double gm;
        if (igm == 0) {
            gm = 0.0;
        } else if (igm == 1) {
            gm = ad.integAlpha(tauMin, tauMax) / (0.5 * ad.integS(tauMin, tauMax));
        } else {
            gm = 0.0;
        }

        // xstar = min(0, log(kprice/hbarr))
        double xstar = Math.log(kprice / hbarr);
        if (xstar > 0.0) {
            xstar = 0.0;
        }
        final double sigmat = ad.integS(tauMin, tauMax);
        final double disc = -ad.integR(tauMin, tauMax);

        // Change of variable
        final double s0 = stock / hbarr;

        // ============== zero-th order term P_0 ==============
        final double d1 = (xstar - Math.log(s0) + (1.0 - gm) * 0.5 * sigmat) / Math.sqrt(sigmat);
        final double d2 = (xstar + Math.log(s0) + (1.0 - gm) * 0.5 * sigmat) / Math.sqrt(sigmat);
        final double d3 = (xstar - Math.log(s0) - (1.0 + gm) * 0.5 * sigmat) / Math.sqrt(sigmat);
        final double d4 = (xstar + Math.log(s0) - (1.0 + gm) * 0.5 * sigmat) / Math.sqrt(sigmat);

        final double e1 = phid(d1);
        final double e2 = phid(d2);
        final double e3 = phid(d3);
        final double e4 = phid(d4);

        double v0 = kprice * e1 - kprice * Math.pow(s0, 1.0 - gm) * e2;
        v0 = v0 + Math.exp(gm * 0.5 * sigmat) * (-hbarr * s0 * e3 + hbarr * Math.pow(s0, -gm) * e4);
        v0 = v0 * Math.exp(disc);
        if (iord == 0) {
            return v0;
        }

        // ============== first-order term P_1 ==============
        final int npoint = 1000;
        final int npoint2 = 100;
        final double dt = (tauMax - tauMin) / (double) npoint;
        final double tt = 0.5 * ad.integS(tauMin, tauMax);
        final double x = Math.log(s0);
        final double et = Math.exp(0.5 * (1.0 - gm) * x);
        final double dsqpi = Math.sqrt(PI);

        double v1 = 0.0;
        for (int i = 1; i <= npoint; i++) {
            double v1p = 0.0;
            final double tmp = tauMin + dt * (2 * i - 1) * 0.5;
            final double p = 0.5 * ad.integS(tmp, tauMax);

            // Function E
            double ccaux = vFun(p, tt, x, xstar, gm) + vFun(p, tt, x, -xstar, gm)
                    - vFun(p, tt, -x, xstar, gm) - vFun(p, tt, -x, -xstar, gm);
            double auxnew = ccaux * (-kprice * Math.exp(-xstar * 0.5 * (1.0 - gm))
                    + hbarr * Math.exp(xstar * 0.5 * (1.0 + gm)));
            v1p += auxnew;

            // Function L
            double b = gm - 1.0;
            double c = -xstar;
            ccaux = llold(p, tt, x, b, c, gm) - llold(p, tt, -x, b, c, gm);
            auxnew = kprice * (1.0 - gm) * ccaux;
            v1p += auxnew;

            b = -(gm + 1.0);
            c = xstar;
            ccaux = llold(p, tt, x, b, c, gm) - llold(p, tt, -x, b, c, gm);
            auxnew = -Math.exp(gm * p) * hbarr * ccaux;
            v1p += auxnew;

            b = (gm + 1.0);
            c = -xstar;
            ccaux = llold(p, tt, x, b, c, gm) - llold(p, tt, -x, b, c, gm);
            auxnew = Math.exp(gm * p) * hbarr * gm * ccaux;
            v1p += auxnew;

            // Function F
            b = gm - 1.0;
            auxnew = -kprice * (1.0 - gm) * (ff(p, tt, x, b, gm) - ff(p, tt, -x, b, gm));
            v1p += auxnew;

            b = gm + 1.0;
            auxnew = -Math.exp(gm * p) * gm * hbarr * (ff(p, tt, x, b, gm) - ff(p, tt, -x, b, gm));
            v1p += auxnew;

            v1 += (ad.alpha(tmp) - gm * 0.5 * ad.sigmaQ(tmp)) * v1p;
        }
        v1 = Math.exp(disc) * et * v1 * dt / (dsqpi * 2.0);
        if (iord == 1) {
            return v0 + v1;
        }

        // ============== second-order term P_2 ==============
        double v2 = 0.0;
        for (int i = 1; i <= npoint; i++) {
            double v2p = 0.0;
            final double tmp = tauMin + dt * (2 * i - 1) * 0.5;
            final double p = 0.5 * ad.integS(tmp, tauMax);
            final double dtp = (tauMax - tmp) / (double) npoint2;

            for (int j = 1; j <= npoint2; j++) {
                final double tmp1 = tmp + dtp * (2 * j - 1) * 0.5;
                final double sLocal = 0.5 * ad.integS(tmp1, tauMax);

                double caux = dll(sLocal, p, tt, -x, -1.0 + gm, -xstar, gm) - dll(sLocal, p, tt, x, -1.0 + gm, -xstar, gm);
                double v2pp = caux * kprice * (1.0 - gm);

                caux = dll(sLocal, p, tt, -x, -1.0 - gm, xstar, gm) - dll(sLocal, p, tt, x, -1.0 - gm, xstar, gm);
                v2pp = v2pp - Math.exp(gm * sLocal) * hbarr * caux;

                caux = dll(sLocal, p, tt, -x, 1.0 + gm, -xstar, gm) - dll(sLocal, p, tt, x, 1.0 + gm, -xstar, gm);
                v2pp = v2pp + Math.exp(gm * sLocal) * gm * hbarr * caux;

                caux = +dvv(sLocal, p, tt, -x, xstar, gm) - dvv(sLocal, p, tt, x, xstar, gm);
                caux = caux + (dvv(sLocal, p, tt, -x, -xstar, gm) - dvv(sLocal, p, tt, x, -xstar, gm));
                double caux2 = hbarr * Math.exp(0.5 * (1.0 + gm) * xstar) - kprice * Math.exp(-0.5 * (1.0 - gm) * xstar);
                v2pp = v2pp + caux2 * caux;

                caux = dff(sLocal, p, tt, -x, -1.0 + gm, gm) - dff(sLocal, p, tt, x, -1.0 + gm, gm);
                v2pp = v2pp - (1.0 - gm) * kprice * caux;

                caux = dff(sLocal, p, tt, -x, 1.0 + gm, gm) - dff(sLocal, p, tt, x, 1.0 + gm, gm);
                v2pp = v2pp - Math.exp(gm * sLocal) * gm * hbarr * caux;

                v2pp = v2pp * 0.5 * (1.0 - gm);

                caux = -ddll(sLocal, p, tt, -x, -1.0 + gm, -xstar, gm) + ddll(sLocal, p, tt, x, -1.0 + gm, -xstar, gm);
                v2pp = v2pp + caux * kprice * (1.0 - gm);

                caux = -ddll(sLocal, p, tt, -x, -1.0 - gm, xstar, gm) + ddll(sLocal, p, tt, x, -1.0 - gm, xstar, gm);
                v2pp = v2pp - Math.exp(gm * sLocal) * hbarr * caux;

                caux = -ddll(sLocal, p, tt, -x, 1.0 + gm, -xstar, gm) + ddll(sLocal, p, tt, x, 1.0 + gm, -xstar, gm);
                v2pp = v2pp + Math.exp(gm * sLocal) * gm * hbarr * caux;

                caux = -ddvv(sLocal, p, tt, -x, xstar, gm) + ddvv(sLocal, p, tt, x, xstar, gm);
                caux = caux + (-dvv(sLocal, p, tt, -x, -xstar, gm) + dvv(sLocal, p, tt, x, -xstar, gm));
                caux2 = hbarr * Math.exp(0.5 * (1.0 + gm) * xstar) - kprice * Math.exp(-0.5 * (1.0 - gm) * xstar);
                v2pp = v2pp + caux2 * caux;

                caux = -ddff(sLocal, p, tt, -x, -1.0 + gm, gm) + ddff(sLocal, p, tt, x, -1.0 + gm, gm);
                v2pp = v2pp - (1.0 - gm) * kprice * caux;

                caux = -ddff(sLocal, p, tt, -x, 1.0 + gm, gm) + ddff(sLocal, p, tt, x, 1.0 + gm, gm);
                v2pp = v2pp - Math.exp(gm * sLocal) * gm * hbarr * caux;

                v2p += (ad.alpha(tmp1) - gm * 0.5 * ad.sigmaQ(tmp1)) * v2pp;
            }
            v2 = v2 + v2p * (ad.alpha(tmp) - gm * 0.5 * ad.sigmaQ(tmp)) * dtp;
        }
        v2 = Math.exp(disc) * et * v2 * dt;
        return v0 + v1 + v2;
    }
}
