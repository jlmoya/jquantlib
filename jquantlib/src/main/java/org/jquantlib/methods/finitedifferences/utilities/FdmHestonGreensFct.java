/*
 Copyright (C) 2014 Klaus Spanderen
 Copyright (C) 2026 JQuantLib migration contributors.

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
package org.jquantlib.methods.finitedifferences.utilities;

import org.jquantlib.math.Constants;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpIterator;
import org.jquantlib.methods.finitedifferences.operators.FdmSquareRootFwdOp.TransformationType;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.time.Frequency;

/**
 * Heston Fokker-Planck Green's function used to seed the FDM forward
 * propagation in the SLV calibration loop (Phase 5h.5-SLV-b).
 *
 * <p>Java port of v1.42.1
 * {@code ql/methods/finitedifferences/utilities/fdmhestongreensfct.{hpp,cpp}}.
 *
 * <p>Provides three approximation algorithms, mirroring the C++ enum:
 * <ul>
 *   <li>{@link Algorithm#ZeroCorrelation} — product of independent log-normal
 *       (in {@code S}) and CIR (in {@code v}) marginals (rho=0 limit).</li>
 *   <li>{@link Algorithm#Gaussian} — bivariate Gaussian with mean drift in
 *       both directions and full {@code rho} coupling. Useful for short
 *       time-to-first-step on smooth volatility surfaces.</li>
 *   <li>{@link Algorithm#SemiAnalytical} — exact density via
 *       {@link HestonProcess#pdf(double, double, double, double)}. Currently
 *       <strong>not supported</strong> in JQuantLib because the underlying
 *       {@code HestonProcess.pdf()} Fourier-inversion routine has not been
 *       ported (carry-forward to a future phase).</li>
 * </ul>
 *
 * <p>The result density is multiplied by the Jacobian of the variance
 * transformation chosen via {@link TransformationType}.
 *
 * @author Phase 5h.5-SLV-b port
 */
public class FdmHestonGreensFct {

    /** Mirrors C++ {@code FdmHestonGreensFct::Algorithm} enum. */
    public enum Algorithm { ZeroCorrelation, Gaussian, SemiAnalytical }

    private final double l0;
    private final FdmMesher mesher;
    private final HestonProcess process;
    private final TransformationType trafoType;

    /** Convenience constructor — defaults {@code l0 = 1.0}. */
    public FdmHestonGreensFct(final FdmMesher mesher,
                              final HestonProcess process,
                              final TransformationType trafoType) {
        this(mesher, process, trafoType, 1.0);
    }

    public FdmHestonGreensFct(final FdmMesher mesher,
                              final HestonProcess process,
                              final TransformationType trafoType,
                              final double l0) {
        this.l0 = l0;
        this.mesher = mesher;
        this.process = process;
        this.trafoType = trafoType;
    }

    /**
     * Sample the Green's function at time {@code t} on every cell of the
     * 2D mesher, multiplied by the variance-Jacobian implied by
     * {@link #trafoType}.
     *
     * @param t        forward time (must be > 0)
     * @param algorithm closed-form approximation to use
     * @return         dense array of length {@code mesher.layout().size()},
     *                 indexed by {@link FdmLinearOpIterator#index()}
     */
    public Array get(final double t, final Algorithm algorithm) {

        final double r = process.riskFreeRate().currentLink()
                .forwardRate(0.0, t, Compounding.Continuous, Frequency.NoFrequency).rate();
        final double q = process.dividendYield().currentLink()
                .forwardRate(0.0, t, Compounding.Continuous, Frequency.NoFrequency).rate();

        final double s0    = process.s0().currentLink().value();
        final double v0    = process.v0().currentLink().value();
        final double x0    = Math.log(s0) + (r - q - 0.5 * v0 * l0 * l0) * t;

        final double rho   = process.rho().currentLink().value();
        final double theta = process.theta().currentLink().value();
        final double kappa = process.kappa().currentLink().value();
        final double sigma = process.sigma().currentLink().value();

        final Array p = new Array(mesher.layout().size());

        for (final FdmLinearOpIterator iter : mesher.layout()) {
            final double x = mesher.location(iter, 0);
            final double v;
            if (trafoType != TransformationType.Log) {
                v = mesher.location(iter, 1);
            } else {
                v = Math.exp(mesher.location(iter, 1));
            }

            double retVal;
            switch (algorithm) {
                case ZeroCorrelation: {
                    final double sd_x = l0 * Math.sqrt(v0 * t);
                    final double dx   = (x - x0) / sd_x;
                    final double p_x  = Constants.M_1_SQRTPI * Constants.M_SQRT1_2
                            / sd_x * Math.exp(-0.5 * dx * dx);
                    final double p_v  = new SquareRootProcessRNDCalculator(
                            v0, kappa, theta, sigma).pdf(v, t);
                    retVal = p_v * p_x;
                    break;
                }
                case SemiAnalytical:
                    // C++ uses HestonProcess::pdf(x, v, t, 1e-4) — a Fourier-inversion
                    // density routine that JQuantLib's HestonProcess does not yet
                    // expose. Carry-forward.
                    throw new UnsupportedOperationException(
                            "FdmHestonGreensFct.Algorithm.SemiAnalytical — "
                            + "HestonProcess.pdf() not yet ported (Phase 5h.5-SLV-b "
                            + "carry-forward); use Gaussian or ZeroCorrelation.");
                case Gaussian: {
                    final double sd_x = l0 * Math.sqrt(v0 * t);
                    final double sd_v = sigma * Math.sqrt(v0 * t);
                    final double z0   = v0 + kappa * (theta - v0) * t;
                    final double dx   = (x - x0) / sd_x;
                    final double dv   = (v - z0) / sd_v;
                    final double oneMR2 = 1.0 - rho * rho;
                    retVal = 1.0 / (Constants.M_TWOPI * sd_x * sd_v * Math.sqrt(oneMR2))
                            * Math.exp(-(dx * dx + dv * dv - 2.0 * rho * dx * dv)
                                       / (2.0 * oneMR2));
                    break;
                }
                default:
                    throw new IllegalArgumentException("unknown algorithm: " + algorithm);
            }

            switch (trafoType) {
                case Log:
                    retVal *= v;
                    break;
                case Plain:
                    // no Jacobian
                    break;
                case Power:
                    retVal *= Math.pow(v, 1.0 - 2.0 * kappa * theta / (sigma * sigma));
                    break;
                default:
                    throw new IllegalArgumentException("unknown transformation type: " + trafoType);
            }

            p.set(iter.index(), retVal);
        }
        return p;
    }
}
