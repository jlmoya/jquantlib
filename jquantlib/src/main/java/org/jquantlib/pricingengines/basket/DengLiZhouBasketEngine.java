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
 */

/*
 Copyright (C) 2024 Klaus Spanderen

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.
 */

package org.jquantlib.pricingengines.basket;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.AverageBasketPayoff;
import org.jquantlib.instruments.BasketOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.SpreadBasketPayoff;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.math.distributions.NormalDistribution;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.CholeskyDecomposition;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.matrixutilities.PseudoSqrt;
import org.jquantlib.math.matrixutilities.PseudoSqrt.SalvagingAlgorithm;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.time.Date;

/**
 * Multi-asset basket / spread option pricing engine using the
 * closed-form approximation of Deng, Li &amp; Zhou (2008),
 * "Multi-asset Spread Option Pricing and Hedging".
 *
 * <p>The typo in formula (37) for {@code J^2} is corrected (matches
 * upstream C++ correction).</p>
 *
 * <p>This pricing formula only works if exactly one asset weight is positive.
 * When more than one weight is positive the engine maps the sum of correlated
 * log-normal processes onto a single log-normal process using
 * C.F. Lo (2013) "WKB Approximation for the Sum of Two Correlated Lognormal
 * Random Variables".</p>
 *
 * <p>Ported from C++ QuantLib v1.42.1
 * {@code ql/pricingengines/basket/denglizhoubasketengine.{hpp,cpp}}.</p>
 *
 * @author Jose Moya
 */
public class DengLiZhouBasketEngine extends BasketOption.Engine {

    private final int n;
    private final List<GeneralizedBlackScholesProcess> processes;
    private final Matrix rho;

    public DengLiZhouBasketEngine(
            final List<GeneralizedBlackScholesProcess> processes,
            final Matrix rho) {
        QL.require(processes != null && !processes.isEmpty(),
                "No Black-Scholes process is given.");
        QL.require(rho != null && processes.size() == rho.rows()
                && rho.rows() == rho.columns(),
                "process and correlation matrix must have the same size.");

        this.n = processes.size();
        this.processes = new ArrayList<GeneralizedBlackScholesProcess>(processes);
        this.rho = rho.clone();

        for (final GeneralizedBlackScholesProcess p : this.processes) {
            p.addObserver(this);
        }
    }

    @Override
    public void calculate() {
        QL.require(arguments_.exercise.type() == Exercise.Type.European,
                "not an European exercise");
        final Date maturityDate = arguments_.exercise.lastDate();

        // Accept either AverageBasketPayoff or SpreadBasketPayoff. The latter
        // is mapped to an average payoff with weights {1, -1}.
        final AverageBasketPayoff avgPayoff;
        if (arguments_.payoff instanceof AverageBasketPayoff) {
            avgPayoff = (AverageBasketPayoff) arguments_.payoff;
        } else if (arguments_.payoff instanceof SpreadBasketPayoff) {
            avgPayoff = new AverageBasketPayoff(
                    ((SpreadBasketPayoff) arguments_.payoff).basePayoff(),
                    new double[] { 1.0, -1.0 });
        } else {
            avgPayoff = null;
        }
        QL.require(avgPayoff != null, "average or spread basket payoff expected");

        final double[] weights = avgPayoff.weights();
        QL.require(n == weights.length && n > 1,
                "wrong number of weights arguments in payoff");

        // Extract spot, dq, v from the vector of processes; verify common dr.
        final double[] s = new double[n];
        final double[] dq = new double[n];
        final double[] v = new double[n];
        final double dr0 = processes.get(0).riskFreeRate().currentLink()
                .discount(maturityDate);
        for (int i = 0; i < n; ++i) {
            final GeneralizedBlackScholesProcess p = processes.get(i);
            s[i] = p.stateVariable().currentLink().value();
            dq[i] = p.dividendYield().currentLink().discount(maturityDate);
            v[i] = p.blackVolatility().currentLink()
                    .blackVariance(maturityDate, s[i]);
            final double dri = p.riskFreeRate().currentLink().discount(maturityDate);
            QL.require(Math.abs(dri - dr0) <= 1.0e-14
                            * Math.max(1.0, Math.abs(dr0)),
                    "interest rates need to be the same for all underlyings");
        }

        QL.require(avgPayoff.basePayoff() instanceof PlainVanillaPayoff,
                "non-plain vanilla payoff given");
        final PlainVanillaPayoff payoff =
                (PlainVanillaPayoff) avgPayoff.basePayoff();

        // Tuples: (weight, original_index, spot, dq, v)
        final List<Tuple> p = new ArrayList<Tuple>(n + 1);
        for (int i = 0; i < n; ++i) {
            p.add(new Tuple(weights[i], i, s[i], dq[i], v[i]));
        }

        // Negative strike: append synthetic asset (-K, n, ?, dr0, 0)
        // so that the "extra" cash-like leg is folded into the basket.
        final Matrix rhoEff;
        if (payoff.strike() < 0.0) {
            p.add(new Tuple(1.0, n, -payoff.strike(), dr0, 0.0));
            rhoEff = new Matrix(n + 1, n + 1);
            for (int i = 0; i < n; ++i) {
                for (int j = 0; j < n; ++j) {
                    rhoEff.set(i, j, rho.get(i, j));
                }
                rhoEff.set(n, i, 0.0);
                rhoEff.set(i, n, 0.0);
            }
            rhoEff.set(n, n, 1.0);
        } else {
            rhoEff = rho;
        }

        final double strike = Math.max(0.0, payoff.strike());

        // Sort by descending weight: positive weights first.
        p.sort(new Comparator<Tuple>() {
            @Override
            public int compare(final Tuple a, final Tuple b) {
                return Double.compare(b.w, a.w);
            }
        });

        // M = count of strictly positive weights (C++ lower_bound with
        // strict-greater predicate yields the partition point).
        int M = 0;
        while (M < p.size() && p.get(M).w > 0.0) {
            ++M;
        }
        QL.require(M > 0, "at least one positive asset weight must be given");
        QL.require(M < p.size(), "at least one negative asset weight must be given");

        final int N = p.size() - M;

        final Matrix nRho = new Matrix(N + 1, N + 1);
        final double[] _s = new double[N + 1];
        final double[] _dq = new double[N + 1];
        final double[] _v = new double[N + 1];

        if (M > 1) {
            // Lo (2013) WKB approximation: collapse the M positive-weight
            // log-normals into one synthetic asset (index 0 of the new array).
            final double[] vol = new double[M];
            final double[] F = new double[M];
            for (int i = 0; i < M; ++i) {
                vol[i] = Math.sqrt(p.get(i).v);
                F[i] = p.get(i).w * p.get(i).s * p.get(i).dq / dr0;
            }

            double S0 = 0.0;
            for (int i = 0; i < M; ++i) {
                S0 += p.get(i).w * p.get(i).s;
            }
            double F0 = 0.0;
            for (int i = 0; i < M; ++i) {
                F0 += F[i];
            }
            final double dq_S0 = F0 / S0 * dr0;

            double v_s = 0.0;
            for (int i = 0; i < M; ++i) {
                for (int j = 0; j < M; ++j) {
                    v_s += vol[i] * vol[j] * F[i] * F[j]
                            * rhoEff.get(p.get(i).idx, p.get(j).idx);
                }
            }
            v_s /= (F0 * F0);

            _s[0] = S0;
            _dq[0] = dq_S0;
            _v[0] = v_s;

            nRho.set(0, 0, 1.0);
            for (int i = 0; i < N; ++i) {
                double rhoHat = 0.0;
                for (int j = 0; j < M; ++j) {
                    rhoHat += rhoEff.get(p.get(M + i).idx, p.get(j).idx)
                            * vol[j] * F[j];
                }
                final double v0 = Math.min(1.0,
                        Math.max(-1.0, rhoHat / (Math.sqrt(v_s) * F0)));
                nRho.set(i + 1, 0, v0);
                nRho.set(0, i + 1, v0);
            }
        } else {
            // Single positive weight: degenerate case, index 0 inherits its row.
            _s[0] = Math.abs(p.get(0).w * p.get(0).s);
            _dq[0] = p.get(0).dq;
            _v[0] = p.get(0).v;
            for (int i = 0; i < N + 1; ++i) {
                final double r0 = rhoEff.get(p.get(i).idx, p.get(0).idx);
                nRho.set(0, i, r0);
                nRho.set(i, 0, r0);
            }
        }

        for (int i = 0; i < N; ++i) {
            _s[i + 1] = Math.abs(p.get(M + i).w * p.get(M + i).s);
            _dq[i + 1] = p.get(M + i).dq;
            _v[i + 1] = p.get(M + i).v;

            final int idx = p.get(M + i).idx;
            for (int j = 0; j < N; ++j) {
                nRho.set(i + 1, j + 1, rhoEff.get(idx, p.get(M + j).idx));
            }
        }

        // Log spots — pass `Log(_s)` to the kernel.
        final Array logS = new Array(N + 1);
        for (int i = 0; i < N + 1; ++i) {
            logS.set(i, Math.log(_s[i]));
        }
        final Array dqA = new Array(_dq);
        final Array vA = new Array(_v);

        final double callValue =
                calculateVanillaCall(logS, dr0, dqA, vA, nRho, strike);

        if (payoff.optionType() == Option.Type.Call) {
            results_.value = Math.max(0.0, callValue);
        } else {
            // Put-call parity: put = call - forward.
            // forward = S0*dq0 - dr0*K - sum_{i>=1} S_i*dq_i
            double fwd = _s[0] * _dq[0] - dr0 * strike;
            for (int i = 1; i < N + 1; ++i) {
                fwd -= _s[i] * _dq[i];
            }
            results_.value = Math.max(0.0, callValue - fwd);
        }
    }

    /**
     * Third-order Taylor expansion kernel I(u; ...). Mirrors C++
     * {@code DengLiZhouBasketEngine::I} bit-for-bit (with the typo-corrected
     * formula (37) for J_2).
     */
    private static double I(
            final double u, final double tF2,
            final Matrix D, final Matrix DF, final int i) {
        // psi = 1 / (1 + |D_i|^2)
        double d2 = 0.0;
        for (int k = 0, n = D.columns(); k < n; ++k) {
            final double dik = D.get(i, k);
            d2 += dik * dik;
        }
        final double psi = 1.0 / (1.0 + d2);
        final double sqrtPsi = Math.sqrt(psi);

        final double n_uSqrtPsi = new NormalDistribution().op(u * sqrtPsi);
        final double J_0 = new CumulativeNormalDistribution().op(u * sqrtPsi);

        double vFv = 0.0;
        double vFFv = 0.0;
        for (int k = 0, n = DF.columns(); k < n; ++k) {
            final double dfik = DF.get(i, k);
            vFv += dfik * D.get(i, k);
            vFFv += dfik * dfik;
        }

        final double J_1 = psi * sqrtPsi * (psi * u * u - 1.0) * vFv * n_uSqrtPsi;

        final double psiU = psi * u;
        final double psiU2 = psiU * u; // psi * u^2
        final double sq = psiU2 * psiU2; // (psi*u^2)^2
        final double sqsq = sq * sq;     // ((psi*u^2)^2)^2  -- C++ squared(squared(psi*u))
        final double J_2 = u * psi * sqrtPsi * n_uSqrtPsi * (
                2.0 * tF2
                + vFv * vFv * (sqsq
                        - 10.0 * psi * psi * psi * u * u
                        + 15.0 * psi * psi)
                + vFFv * (4.0 * psi * psi * u * u - 12.0 * psi)
        );

        return J_0 + J_1 - 0.5 * J_2;
    }

    /**
     * Closed-form call value on the post-mapping (N+1)-asset basket.
     *
     * <p>{@code x} is {@code Log(_s)}, {@code rho} is the (N+1)x(N+1)
     * post-mapping correlation matrix, {@code K} is the (non-negative)
     * effective strike. Mirrors C++
     * {@code DengLiZhouBasketEngine::calculate_vanilla_call}.</p>
     */
    private static double calculateVanillaCall(
            final Array x, final double dr, final Array dq,
            final Array v, final Matrix rho, final double K) {
        final int sz = x.size();
        final int N = sz - 1;

        // mu = x + Log(dq/dr) - 0.5*v ; nu = Sqrt(v)
        final Array mu = new Array(sz);
        final Array nu = new Array(sz);
        for (int i = 0; i < sz; ++i) {
            mu.set(i, x.get(i) + Math.log(dq.get(i) / dr) - 0.5 * v.get(i));
            nu.set(i, Math.sqrt(v.get(i)));
        }

        double R = 0.0;
        for (int i = 1; i < sz; ++i) {
            R += Math.exp(mu.get(i));
        }

        // Sigma_11: NxN sub-block rho[1..N, 1..N]
        final Matrix sig11 = new Matrix(N, N);
        for (int i = 0; i < N; ++i) {
            for (int j = 0; j < N; ++j) {
                sig11.set(i, j, rho.get(i + 1, j + 1));
            }
        }
        // sig10: rho[0, 1..N]
        final Array sig10 = new Array(N);
        for (int j = 0; j < N; ++j) {
            sig10.set(j, rho.get(0, j + 1));
        }

        // pseudoSqrt(sig11, Principal) is the square-root factor used as a
        // similarity transform.
        final Matrix sqSig11 = PseudoSqrt.pseudoSqrt(sig11, SalvagingAlgorithm.Principal);

        // sig11Inv * sig10 via CholeskySolveFor.
        final Matrix L11 = CholeskyDecomposition.CholeskyDecomposition(sig11, true);
        final Array sig11Inv10 = CholeskyDecomposition.CholeskySolveFor(L11, sig10);

        final double sig_xy = 1.0 - sig10.dotProduct(sig11Inv10);
        QL.require(sig_xy > 0.0, "approximation loses validity");
        final double sqSig_xy = Math.sqrt(sig_xy);

        // E: NxN
        final double a = -0.5 / sqSig_xy;
        final Matrix E = new Matrix(N, N);
        for (int ii = 1; ii <= N; ++ii) {
            for (int jj = ii; jj <= N; ++jj) {
                final double diag = (ii == jj)
                        ? nu.get(jj) * nu.get(jj) * Math.exp(mu.get(jj))
                                / (nu.get(0) * (R + K))
                        : 0.0;
                final double off = nu.get(ii) * nu.get(jj)
                        * Math.exp(mu.get(ii) + mu.get(jj))
                        / (nu.get(0) * (R + K) * (R + K));
                final double val = a * (diag - off);
                E.set(ii - 1, jj - 1, val);
                E.set(jj - 1, ii - 1, val);
            }
        }

        // F = sqSig11 * E * sqSig11
        final Matrix F = sqSig11.mul(E).mul(sqSig11);

        // trF, trF2 (||F||_F^2)
        double trF = 0.0;
        double trF2 = 0.0;
        for (int i = 0; i < N; ++i) {
            trF += F.get(i, i);
            trF2 += F.get(i, i) * F.get(i, i);
            for (int j = i + 1; j < N; ++j) {
                trF2 += 2.0 * F.get(i, j) * F.get(i, j);
            }
        }

        final double c = -(Math.log(R + K) - mu.get(0)) / (nu.get(0) * sqSig_xy);

        // d = (sig11Inv*sig10 - exp(mu_{1..})*nu_{1..}/(nu_0*(R+K)))/sqSig_xy
        final Array d = new Array(N);
        for (int i = 0; i < N; ++i) {
            final double t = (sig11Inv10.get(i)
                    - Math.exp(mu.get(i + 1)) * nu.get(i + 1)
                            / (nu.get(0) * (R + K))) / sqSig_xy;
            d.set(i, t);
        }

        // Esig10 = E * sig10 ; Esig11 = E * sig11 ; sig11d = sig11 * d
        final Array Esig10 = E.mul(sig10);
        final Matrix Esig11 = E.mul(sig11);
        final Array sig11d = sig11.mul(d);

        final double[] C = new double[N + 2];
        C[0] = c + trF + nu.get(0) * sqSig_xy + nu.get(0) * sig10.dotProduct(d)
                + nu.get(0) * nu.get(0) * sig10.dotProduct(Esig10);
        C[N + 1] = c + trF;
        for (int k = 1; k < N + 1; ++k) {
            // sum over j of sig11[k-1, j] * Esig11[j, k-1]  =  (sig11 * Esig11_col)[k-1]
            double s2 = 0.0;
            for (int j = 0; j < N; ++j) {
                s2 += sig11.get(k - 1, j) * Esig11.get(j, k - 1);
            }
            C[k] = c + trF + nu.get(k) * sig11d.get(k - 1)
                    + nu.get(k) * nu.get(k) * s2;
        }

        // D[k]: Arrays of length N
        // D[0]     = sqSig11 * (d + 2*nu0 * Esig10)
        // D[k]     = sqSig11 * (d + 2*nu_k * Esig11[:, k-1]) for k = 1..N
        // D[N+1]   = sqSig11 * d
        final Array[] D = new Array[N + 2];
        {
            final Array tmp0 = new Array(N);
            for (int i = 0; i < N; ++i) {
                tmp0.set(i, d.get(i) + 2.0 * nu.get(0) * Esig10.get(i));
            }
            D[0] = sqSig11.mul(tmp0);
        }
        for (int k = 1; k < N + 1; ++k) {
            final Array tmp = new Array(N);
            for (int i = 0; i < N; ++i) {
                tmp.set(i, d.get(i) + 2.0 * nu.get(k) * Esig11.get(i, k - 1));
            }
            D[k] = sqSig11.mul(tmp);
        }
        D[N + 1] = sqSig11.mul(d);

        // Stack D[k] as rows of DM: (N+2) x N
        final Matrix DM = new Matrix(N + 2, N);
        for (int k = 0; k < N + 2; ++k) {
            for (int j = 0; j < N; ++j) {
                DM.set(k, j, D[k].get(j));
            }
        }

        final Matrix DF = DM.mul(F);

        double npv = dr * Math.exp(mu.get(0) + 0.5 * nu.get(0) * nu.get(0))
                * I(C[0], trF2, DM, DF, 0)
                - K * dr * I(C[N + 1], trF2, DM, DF, N + 1);
        for (int k = 1; k <= N; ++k) {
            npv -= dr * Math.exp(mu.get(k) + 0.5 * nu.get(k) * nu.get(k))
                    * I(C[k], trF2, DM, DF, k);
        }
        return npv;
    }

    // --- helpers ---

    private static final class Tuple {
        final double w;
        final int idx;
        final double s;
        final double dq;
        final double v;
        Tuple(final double w, final int idx, final double s,
                final double dq, final double v) {
            this.w = w;
            this.idx = idx;
            this.s = s;
            this.dq = dq;
            this.v = v;
        }
    }
}
